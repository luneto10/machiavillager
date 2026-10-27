package io.github.luneto10.machiavillager.minecraft.bridge;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.bridge.BridgeClient;
import io.github.luneto10.machiavillager.domain.bridge.ActionStatus;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges;
import io.github.luneto10.machiavillager.domain.bridge.DeliverLinesAction;
import io.github.luneto10.machiavillager.domain.bridge.SpeakAction;
import io.github.luneto10.machiavillager.domain.bridge.SuggestRelationshipAction;
import io.github.luneto10.machiavillager.domain.lines.LineKind;
import io.github.luneto10.machiavillager.domain.speech.SpeechQueue;
import io.github.luneto10.machiavillager.domain.speech.SpeechRules;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.domain.time.Cooldown;
import io.github.luneto10.machiavillager.minecraft.lines.NpcLines;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerLookup;
import io.github.luneto10.machiavillager.minecraft.npc.NpcSpeech;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Carries out bridge actions on the server thread. Only speak exists so far. Each request is checked in order,
 * answered through the bridge, then paced one tick at a time. Every method runs on the server thread.
 */
public final class ActionExecutor {
    static final String NPC_UNAVAILABLE = "npc_unavailable";
    static final String BUSY = "busy";
    static final String PLAYER_OFFLINE = "player_offline";
    static final String RATE_LIMITED = "rate_limited";
    static final String INVALID_LINES = "invalid_lines";

    private final MinecraftServer server;
    private final BridgeClient bridge;
    // Empty when the wait is zero, which means no limit.
    private final Optional<Cooldown> unpromptedCooldown;
    // Holds the model label of the last accepted reply, for the dev boss bar.
    private final ModelLine lastReply;
    // Applies suggested affinity changes; shared with the dev nudge command so both use the same limits.
    private final RelationshipNudger nudger;
    // The spoken lines: the pools the backend fills, and the fallbacks used while it is down.
    private final NpcLines lines;
    // The speech in progress, or null while he is silent.
    private ActiveSpeech speech;

    /** Creates the executor for one server session. The wait is the minimum gap between unprompted speeches. */
    public ActionExecutor(
            MinecraftServer server,
            BridgeClient bridge,
            Duration unpromptedWait,
            ModelLine lastReply,
            RelationshipNudger nudger,
            NpcLines lines) {
        this.server = Objects.requireNonNull(server, "server");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.lastReply = Objects.requireNonNull(lastReply, "lastReply");
        this.nudger = Objects.requireNonNull(nudger, "nudger");
        this.lines = Objects.requireNonNull(lines, "lines");
        long ticks = SpeechRules.toTicks(Objects.requireNonNull(unpromptedWait, "unpromptedWait"));
        this.unpromptedCooldown = ticks > 0 ? Optional.of(new Cooldown(ticks)) : Optional.empty();
    }

    /**
     * A speak request from the backend. Checks run in order and stop at the first refusal: the NPC must be loaded,
     * he must not already be busy, a private recipient must be online, and the rate limit must allow it.
     */
    public void speak(SpeakAction action) {
        String id = action.requestId();
        Optional<MachiavillagerEntity> npc = MachiavillagerLookup.find(server);
        if (npc.isEmpty()) {
            reject(id, NPC_UNAVAILABLE);
            return;
        }
        // One speech or meeting at a time: a second request waits for the backend to try again.
        if (speech != null || npc.get().inMeeting()) {
            reject(id, BUSY);
            return;
        }
        // Checked before the rate limit, so a refused private speech does not use up the unprompted allowance.
        if (action.to().isPresent() && server.getPlayerList().getPlayer(action.to().get()) == null) {
            reject(id, PLAYER_OFFLINE);
            return;
        }
        // Last check on purpose: it starts the cooldown, so it must run only when the speech really goes ahead.
        if (!allowedByRateLimit(action)) {
            reject(id, RATE_LIMITED);
            return;
        }

        bridge.send(new ActionStatus.Accepted(id));
        bridge.send(new ActionStatus.Running(id));
        // Dev only: the boss bar shows the model of the last reply. A canned line has no model, so it says so.
        lastReply.record(action.model(), action.latencyMs());
        npc.get().setSpeaking(true);
        Optional<ServerPlayer> recipient = action.to().map(server.getPlayerList()::getPlayer);
        speech = new ActiveSpeech(id, npc.get(), new SpeechQueue(action.lines()), recipient);
    }

    /**
     * A suggested affinity change from the backend. It is instant: the limits decide, the change is written at once,
     * and the statuses say whether it was applied. Server thread only.
     */
    public void suggestRelationship(SuggestRelationshipAction action) {
        String id = action.requestId();
        RelationshipNudges.Result result = nudger.suggest(action.player(), action.affinityDelta(), action.reason());
        if (result instanceof RelationshipNudges.Rejected rejected) {
            reject(id, rejected.reason());
            return;
        }
        // Same statuses as a speech that ends at once: accepted, running, then succeeded.
        bridge.send(new ActionStatus.Accepted(id));
        bridge.send(new ActionStatus.Running(id));
        bridge.send(new ActionStatus.Succeeded(id));
    }

    /**
     * A batch of lines from the backend for one kind, phase and mood. It is stored, never spoken here. Unknown
     * spellings or too few valid lines are refused as invalid_lines. Server thread only.
     */
    public void deliverLines(DeliverLinesAction action) {
        String id = action.requestId();
        Optional<LineKind> kind = LineKind.fromWire(action.kind());
        Optional<Phase> phase = wireValue(Phase.class, action.phase());
        Optional<Mood> mood = wireValue(Mood.class, action.mood());
        // Short-circuit on purpose: the values are read only once all three are known.
        if (kind.isEmpty() || phase.isEmpty() || mood.isEmpty()
                || !lines.deliver(kind.get(), phase.get(), mood.get(), action.lines())) {
            reject(id, INVALID_LINES);
            return;
        }

        // Same statuses as a speech that ends at once: accepted, running, then succeeded.
        bridge.send(new ActionStatus.Accepted(id));
        bridge.send(new ActionStatus.Running(id));
        bridge.send(new ActionStatus.Succeeded(id));
        // Logged by id and count only, never the line text.
        Machiavillager.LOGGER.info("deliver_lines id={} n={}", id, action.lines().size());
    }

    /** Called at the end of every server tick: says the next line when its turn comes, or ends the speech. */
    public void tick() {
        ActiveSpeech current = speech;
        if (current == null) {
            return;
        }
        // A removed NPC cannot finish his lines, so the request fails rather than hanging.
        if (current.npc().isRemoved()) {
            abort(current, NPC_UNAVAILABLE);
            return;
        }
        // Private lines have nobody to reach once the recipient is gone, so the request fails the same way.
        if (current.recipientLeft(server)) {
            abort(current, PLAYER_OFFLINE);
            return;
        }
        // Same order as the meeting: the closing line is read before the speech is called finished.
        if (current.queue().finished()) {
            finish(current);
            return;
        }
        current.queue().tick().ifPresent(line -> speakLine(current, line));
    }

    /**
     * A normal chat message from a player. Within hearing range it cuts normal speech after the current line.
     * Commands never reach here; BridgeLifecycle filters them out. Private speech is never cut short.
     */
    public void onPlayerChat(ServerPlayer sender) {
        ActiveSpeech current = speech;
        if (current == null || current.interrupted() || !current.interruptible() || !current.hears(sender)) {
            return;
        }
        current.interrupt();
    }

    // Private lines go only to the recipient; normal lines are heard by everyone nearby.
    private static void speakLine(ActiveSpeech current, String line) {
        current.recipient().ifPresentOrElse(
                player -> NpcSpeech.tellPrivately(current.npc(), player, line),
                () -> NpcSpeech.say(current.npc(), line));
    }

    // Reports how the speech ended and frees him for the brain again.
    private void finish(ActiveSpeech current) {
        speech = null;
        current.npc().setSpeaking(false);
        if (current.interrupted()) {
            bridge.send(new ActionStatus.Interrupted(
                    current.requestId(), current.queue().spoken(), current.queue().total()));
        } else {
            bridge.send(new ActionStatus.Succeeded(current.requestId()));
        }
    }

    // Ends a speech that cannot finish, and says why. Frees him for the brain again.
    private void abort(ActiveSpeech current, String reason) {
        speech = null;
        current.npc().setSpeaking(false);
        bridge.send(new ActionStatus.Failed(current.requestId(), reason));
    }

    // A reply answers a player who spoke to him, so it is not unprompted: it skips the limit and never uses it up.
    private boolean allowedByRateLimit(SpeakAction action) {
        return action.inReplyTo().isPresent() || takeRateLimit();
    }

    // Every other speak counts as unprompted, private ones included.
    private boolean takeRateLimit() {
        long now = server.getTickCount();
        return unpromptedCooldown.map(cooldown -> cooldown.tryUse(now)).orElse(true);
    }

    // The enum constant with this wire name (lowercase on the wire), or empty when there is none.
    private static <E extends Enum<E>> Optional<E> wireValue(Class<E> type, String wire) {
        try {
            return Optional.of(Enum.valueOf(type, wire.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    // Logs the refusal reason (never the chat text), so a missing reply can be found in the game log.
    private void reject(String requestId, String reason) {
        Machiavillager.LOGGER.info("action {} rejected: {}", requestId, reason);
        bridge.send(new ActionStatus.Rejected(requestId, reason));
    }
}
