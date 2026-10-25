package io.github.luneto10.machiavillager.bridge;

import io.github.luneto10.machiavillager.domain.bridge.BridgeMessage;
import io.github.luneto10.machiavillager.domain.bridge.ProtocolRules;
import java.net.http.WebSocket;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Receives frames for one socket. Text may arrive in parts, so parts are joined until the last one. Runs on
 * the bridge thread and hands each decoded message back to {@link BridgeClient}.
 */
final class BridgeListener implements WebSocket.Listener {
    // Longest frame accepted, in characters. A bigger one is refused, so a bad backend cannot exhaust memory.
    static final int MAX_FRAME_CHARS = 65_536;
    // Longest detail sent back in a refusal, well inside the protocol's 500-character limit.
    static final int MAX_REFUSAL_DETAIL = 200;

    private static final String TOO_LARGE = "frame too large";

    private final BridgeClient client;
    private final StringBuilder frame = new StringBuilder();
    private boolean oversized;

    BridgeListener(BridgeClient client) {
        this.client = client;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        collect(data);
        if (last) {
            finishFrame(webSocket);
        }
        // Ask for the next part only now, so a slow handler applies back-pressure to the socket.
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        BridgeClient.LOGGER.info("Bridge: backend closed the connection (code {})", statusCode);
        client.runOnBridge(() -> client.onSocketLost(webSocket));
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        BridgeClient.LOGGER.warn("Bridge: connection error ({})", BridgeClient.describe(error));
        client.runOnBridge(() -> client.onSocketLost(webSocket));
    }

    // Keeps the parts of one frame; once a frame is too big, the rest of it is dropped.
    private void collect(CharSequence data) {
        if (oversized) {
            return;
        }
        if (frame.length() + data.length() > MAX_FRAME_CHARS) {
            oversized = true;
            frame.setLength(0);
            return;
        }
        frame.append(data);
    }

    // Decodes a complete frame, or refuses it with a bad_message error. Resets for the next frame either way.
    private void finishFrame(WebSocket webSocket) {
        String text = frame.toString();
        boolean tooLarge = oversized;
        frame.setLength(0);
        oversized = false;
        if (tooLarge) {
            client.runOnBridge(() -> client.refuse(webSocket, TOO_LARGE));
            return;
        }

        // Decoding is pure, so it runs here; the client's state is touched only on the bridge thread.
        try {
            BridgeMessage message = MessageCodec.decode(text);
            client.runOnBridge(() -> client.onFrame(webSocket, message));
        } catch (MessageCodec.DecodeException rejected) {
            refuseFrame(webSocket, text, String.valueOf(rejected.getMessage()));
        }
    }

    // An action that fails to decode is rejected under its own id, so the backend can tell which request was refused.
    // Anything else is a plain bad_message error.
    private void refuseFrame(WebSocket webSocket, String text, String detail) {
        Optional<String> requestId = MessageCodec.peekActionRequestId(text);
        if (requestId.isPresent()) {
            String reason = actionReason(detail);
            client.runOnBridge(() -> client.rejectAction(webSocket, requestId.get(), reason));
            return;
        }
        client.runOnBridge(() -> client.refuse(webSocket, detail));
    }

    // Shows the schema's first error as the refusal reason, cut to the protocol limit. It names the rule, never the value.
    static String actionReason(String detail) {
        String reason = "invalid action: " + detail;
        int limit = ProtocolRules.MAX_REASON_LENGTH;
        // Cut by code points, so a surrogate pair is never split and the length matches the contract's count.
        int units = reason.offsetByCodePoints(0, Math.min(reason.codePointCount(0, reason.length()), limit));
        return reason.substring(0, units);
    }
}
