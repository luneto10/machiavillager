package io.github.luneto10.machiavillager.bridge;

import io.github.luneto10.machiavillager.domain.bridge.ActionStatus;
import io.github.luneto10.machiavillager.domain.bridge.Backoff;
import io.github.luneto10.machiavillager.domain.bridge.BridgeConfig;
import io.github.luneto10.machiavillager.domain.bridge.BridgeError;
import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.BridgeState;
import io.github.luneto10.machiavillager.domain.bridge.Hello;
import io.github.luneto10.machiavillager.domain.bridge.Welcome;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket client for the backend. Every socket callback and every send runs on one daemon thread, so the
 * game thread never waits on the network. Messages reach the game only through the game executor.
 * Reconnects with {@link Backoff}. Knows nothing about Minecraft: it is testable on its own.
 */
public final class BridgeClient {
    static final Logger LOGGER = LoggerFactory.getLogger("machiavillager");

    // Thread name, so a thread dump shows the bridge at a glance.
    static final String THREAD_NAME = "machiavillager-bridge";
    // Longest wait for the TCP and WebSocket handshake.
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // The backend has this long to answer hello before the socket is dropped and retried.
    static final Duration WELCOME_TIMEOUT = Duration.ofSeconds(10);
    // Most the game thread waits for the close frame at shutdown, so a stop never stalls the tick.
    static final Duration CLOSE_WAIT = Duration.ofSeconds(1);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SHUTDOWN_REASON = "server stopping";
    private static final String RETRY_LINE = "Bridge disconnected, retrying in {} s";

    private final BridgeConfig config;
    private final Hello hello;
    private final Executor gameThread;
    private final Consumer<BridgeMessage> onMessage;
    private final ScheduledExecutorService bridgeExecutor;
    private final HttpClient httpClient;
    private final Backoff backoff = new Backoff();

    // Read from any thread, written on the bridge thread only.
    private volatile BridgeState state = BridgeState.OFF;
    private volatile Instant connectedSince;
    private volatile Instant nextAttemptAt;
    private volatile boolean stopping;

    // Bridge thread only: the live socket, the welcome timer, and the tail of the send queue.
    private WebSocket socket;
    private ScheduledFuture<?> welcomeTimer;
    private CompletableFuture<Void> lastSend = CompletableFuture.completedFuture(null);

    /**
     * Builds the client. Nothing connects until {@link #start()}.
     *
     * @param config where to connect and the token; without a token the client stays off
     * @param modVersion sent in hello, 1 to 32 characters
     * @param gameThread receives every decoded message that is not handshake or error traffic
     * @param onMessage handles each message on the game thread
     */
    public BridgeClient(
            BridgeConfig config, String modVersion, Executor gameThread, Consumer<BridgeMessage> onMessage) {
        this.config = Objects.requireNonNull(config, "config");
        this.hello = new Hello(modVersion);
        this.gameThread = Objects.requireNonNull(gameThread, "gameThread");
        this.onMessage = Objects.requireNonNull(onMessage, "onMessage");
        // The HttpClient shares this executor, so every network callback lands on the one bridge thread.
        this.bridgeExecutor = Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofPlatform().name(THREAD_NAME).daemon(true).unstarted(runnable));
        this.httpClient = HttpClient.newBuilder()
                .executor(bridgeExecutor)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /** Connects in the background, or stays off when no token is set. Never blocks the caller. */
    public void start() {
        if (!config.enabled()) {
            state = BridgeState.OFF;
            LOGGER.info("Bridge off: no MACHIAVILLAGER_BRIDGE_TOKEN set");
            return;
        }
        bridgeExecutor.execute(this::connect);
    }

    /**
     * Queues a message for the backend. Safe from any thread and returns at once. The message is dropped
     * unless the link is up.
     */
    public void send(BridgeMessage message) {
        try {
            bridgeExecutor.execute(() -> sendIfConnected(message));
        } catch (RejectedExecutionException stopped) {
            LOGGER.debug("Bridge: dropped {}, client stopped", message.getClass().getSimpleName());
        }
    }

    /**
     * Closes the link and stops the bridge thread. Call once at shutdown. Waits at most {@link #CLOSE_WAIT}
     * for the close frame, so the game thread is never held longer than that.
     */
    public void stop() {
        stopping = true;
        try {
            CompletableFuture.supplyAsync(this::closeSocket, bridgeExecutor)
                    .thenCompose(Function.identity())
                    .get(CLOSE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException notConfirmed) {
            LOGGER.debug("Bridge: close frame not confirmed ({})", notConfirmed.getClass().getSimpleName());
        } finally {
            // The HttpClient owns selector threads of its own; release them with the bridge thread.
            httpClient.shutdownNow();
            bridgeExecutor.shutdownNow();
            state = BridgeState.OFF;
        }
    }

    /** Where the client is right now. Safe to read from any thread. */
    public BridgeState state() {
        return state;
    }

    /**
     * Runs a task on the bridge thread. The listener calls back through here, because the JDK does not
     * promise which thread delivers socket events, and the socket fields below belong to the bridge thread.
     */
    void runOnBridge(Runnable task) {
        try {
            bridgeExecutor.execute(task);
        } catch (RejectedExecutionException stopped) {
            LOGGER.debug("Bridge: ignored a socket event after stop");
        }
    }

    /** When the link last came up, or empty while it is down. */
    public Optional<Instant> connectedSince() {
        return Optional.ofNullable(connectedSince);
    }

    /** When the next connection attempt is due, or empty when none is scheduled. */
    public Optional<Instant> nextAttemptAt() {
        return Optional.ofNullable(nextAttemptAt);
    }

    // Bridge thread. The token travels only in the header: it is never logged, and never put in the URL.
    private void connect() {
        if (stopping || !config.enabled()) {
            return;
        }
        state = BridgeState.CONNECTING;
        nextAttemptAt = null;
        String authorization = BEARER_PREFIX + config.token().orElseThrow();
        httpClient.newWebSocketBuilder()
                .header(AUTH_HEADER, authorization)
                .buildAsync(config.url(), new BridgeListener(this))
                .whenCompleteAsync(this::onConnectResult, bridgeExecutor);
    }

    // Bridge thread. A good socket gets hello and a welcome timer; a failed one is retried later.
    private void onConnectResult(WebSocket opened, Throwable failure) {
        if (failure != null) {
            LOGGER.warn("Bridge could not connect to {} ({})", config.url(), describe(failure));
            scheduleReconnect();
            return;
        }
        if (stopping) {
            opened.abort();
            return;
        }
        socket = opened;
        transmit(opened, hello);
        welcomeTimer = schedule(() -> onWelcomeTimeout(opened), WELCOME_TIMEOUT);
    }

    // Bridge thread. The backend never answered hello: drop this socket and retry.
    private void onWelcomeTimeout(WebSocket timedOut) {
        if (timedOut != socket || state == BridgeState.CONNECTED) {
            return;
        }
        LOGGER.warn("Bridge: no welcome from backend within {} s", WELCOME_TIMEOUT.toSeconds());
        timedOut.abort();
        onSocketLost(timedOut);
    }

    // Bridge thread. Called by the listener. The backend accepted hello, so the backoff starts over.
    void onWelcome(WebSocket from) {
        if (from != socket) {
            return;
        }
        cancelWelcomeTimer();
        state = BridgeState.CONNECTED;
        connectedSince = Instant.now();
        nextAttemptAt = null;
        backoff.reset();
        LOGGER.info("Bridge connected to {}", config.url());
    }

    // Bridge thread. Welcome and backend errors stay here; every other message goes to the game thread.
    void onFrame(WebSocket from, BridgeMessage message) {
        if (message instanceof Welcome) {
            onWelcome(from);
        } else if (message instanceof BridgeError error) {
            LOGGER.warn("Bridge: backend reported {}: {}", error.code().wireName(), error.detail());
        } else {
            gameThread.execute(() -> onMessage.accept(message));
        }
    }

    // Bridge thread. Tells the backend a frame was refused; the connection itself stays open.
    void refuse(WebSocket from, String detail) {
        transmit(from, new BridgeError(BridgeError.Code.BAD_MESSAGE, shorten(detail)));
    }

    // Bridge thread. An action that could not be decoded is refused by its own id, so the backend knows which request failed.
    void rejectAction(WebSocket from, String requestId, String reason) {
        transmit(from, new ActionStatus.Rejected(requestId, reason));
    }

    // Bridge thread. The link is gone (closed, errored, or timed out). A stale socket is ignored.
    void onSocketLost(WebSocket lost) {
        if (lost != socket) {
            return;
        }
        socket = null;
        connectedSince = null;
        cancelWelcomeTimer();
        scheduleReconnect();
    }

    // Bridge thread. Waits out the next backoff delay, then connects again. A stop ends the loop instead.
    private void scheduleReconnect() {
        if (stopping) {
            state = BridgeState.OFF;
            return;
        }
        Duration delay = backoff.next();
        nextAttemptAt = Instant.now().plus(delay);
        state = BridgeState.BACKING_OFF;
        LOGGER.info(RETRY_LINE, delay.toSeconds());
        schedule(this::connect, delay);
    }

    // Bridge thread. Null only when the executor is already shut down, and then nothing is left to run.
    private ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        try {
            return bridgeExecutor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shutDown) {
            return null;
        }
    }

    private void cancelWelcomeTimer() {
        if (welcomeTimer != null) {
            welcomeTimer.cancel(false);
            welcomeTimer = null;
        }
    }

    // Bridge thread. Sends only when the link is up; otherwise the message is dropped at debug level.
    private void sendIfConnected(BridgeMessage message) {
        WebSocket live = socket;
        if (state != BridgeState.CONNECTED || live == null) {
            LOGGER.debug("Bridge: dropped {} while {}", message.getClass().getSimpleName(), state);
            return;
        }
        transmit(live, message);
    }

    // Bridge thread. Encodes one frame and queues it.
    private void transmit(WebSocket to, BridgeMessage message) {
        String json = MessageCodec.encode(message);
        enqueue(() -> to.sendText(json, true));
    }

    // Bridge thread, run by stop(). The close frame waits behind any pending send.
    private CompletableFuture<Void> closeSocket() {
        WebSocket live = socket;
        if (live == null) {
            return CompletableFuture.completedFuture(null);
        }
        return enqueue(() -> live.sendClose(WebSocket.NORMAL_CLOSURE, SHUTDOWN_REASON));
    }

    // Bridge thread. Chains one send after the previous, so two sends never overlap on the socket. A failed
    // send is logged and does not block the ones behind it.
    private CompletableFuture<Void> enqueue(Supplier<CompletableFuture<?>> send) {
        lastSend = lastSend.exceptionally(this::logSendFailure)
                .thenCompose(ignored -> send.get().thenAccept(done -> { }));
        return lastSend;
    }

    private Void logSendFailure(Throwable failure) {
        LOGGER.warn("Bridge: send failed ({})", describe(failure));
        return null;
    }

    // Keeps a refusal detail inside the protocol's length limit.
    private static String shorten(String detail) {
        String text = String.valueOf(detail);
        return text.length() > BridgeListener.MAX_REFUSAL_DETAIL
                ? text.substring(0, BridgeListener.MAX_REFUSAL_DETAIL)
                : text;
    }

    /** Class name and message of a failure, without a stack trace. */
    static String describe(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        String message = cause.getMessage();
        String name = cause.getClass().getSimpleName();
        return message == null ? name : name + ": " + message;
    }
}
