package io.github.luneto10.machiavillager.minecraft.lines;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.bridge.EventIds;
import io.github.luneto10.machiavillager.domain.bridge.LinesNeededEvent;
import io.github.luneto10.machiavillager.domain.lines.LineBank;
import io.github.luneto10.machiavillager.domain.lines.LineKey;
import io.github.luneto10.machiavillager.domain.lines.LineKind;
import io.github.luneto10.machiavillager.domain.lines.LineRules;
import io.github.luneto10.machiavillager.domain.lines.LineSource;
import io.github.luneto10.machiavillager.domain.lines.SpeechCap;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;

/**
 * The spoken lines of one server run: takes a line for the moment, and asks the backend for a new batch when a
 * pool runs low. Every method runs on the server thread and does no I/O: sends go through the bridge's queue.
 */
public final class NpcLines {
    private final MinecraftServer server;
    // Says whether the bridge link is up right now.
    private final BooleanSupplier backendUp;
    // Sends a lines_needed event; returns false when it was not sent (no connection).
    private final Predicate<LinesNeededEvent> sender;
    private final LineSource source;
    // Limits unprompted speech across all players, lines from the pools included.
    private final SpeechCap cap = new SpeechCap();
    // Tick the link was first seen down in the current outage; NOT_DOWN while it is up.
    private static final long NOT_DOWN = -1L;
    private long downSinceTick = NOT_DOWN;

    /** Starts from the pools saved with the world, so a restart keeps the batches already delivered. */
    public NpcLines(MinecraftServer server, BooleanSupplier backendUp, Predicate<LinesNeededEvent> sender) {
        this.server = server;
        this.backendUp = backendUp;
        this.sender = sender;
        this.source = new LineSource(new LineBank(StoryData.get(server).linePools()));
    }

    /** A line for the dice-chosen moment, or empty when he stays silent. */
    public Optional<String> take(LineKind kind, Phase phase, Mood mood) {
        return take(kind, phase, mood, false);
    }

    /**
     * A line for the moment, or empty when he stays silent. The global cap is checked first, so a refused speech
     * never uses up a line. A cached or fallback line counts as an unprompted speech. A forced line (dev) skips the
     * cap and is not counted, so a dev test never delays the real dice.
     */
    public Optional<String> take(LineKind kind, Phase phase, Mood mood, boolean forced) {
        long now = server.getTickCount();
        // Sampled before the cap check, so the outage clock sees every moment he could have spoken.
        boolean up = backendUp.getAsBoolean();
        boolean fallbackAllowed = trackOutage(up, now);
        if (!forced && !cap.canSpeak(now)) {
            return Optional.empty();
        }

        LineKey key = new LineKey(kind, phase, mood);
        LineSource.Next next = source.next(key, up, fallbackAllowed, now, ThreadLocalRandom.current());
        if (next.askBackend()) {
            requestBatch(key);
        }
        if (next.template().isEmpty()) {
            return Optional.empty();
        }

        // Counted only now, when the line is really handed out to be spoken; a forced line stays out of the cap.
        if (!forced) {
            cap.markSpoke(now);
        }
        return next.template();
    }

    /** Dev: empties every pool, in memory and in the saved story. Returns how many pools had lines. */
    public int clear() {
        int cleared = source.bank().clear();
        StoryData.get(server).setLinePools(source.bank().snapshot());
        return cleared;
    }

    /**
     * Stores a batch the backend sent for one key. Invalid lines are dropped; a batch with too few valid lines is
     * refused and nothing is stored. Returns whether the batch was stored.
     */
    public boolean deliver(LineKind kind, Phase phase, Mood mood, List<String> lines) {
        List<String> valid = LineRules.validLines(kind, lines);
        if (valid.size() < LineRules.MIN_VALID_LINES) {
            return false;
        }

        source.bank().deliver(new LineKey(kind, phase, mood), valid);
        // Saved with the world, so the pools survive a restart without asking again.
        StoryData.get(server).setLinePools(source.bank().snapshot());
        Machiavillager.LOGGER.info("deliver_lines stored kind={} n={}", kind.wire(), valid.size());
        return true;
    }

    // Starts the outage clock on the first down sample and stops it on an up sample. True once the outage is long.
    private boolean trackOutage(boolean up, long nowTicks) {
        if (up) {
            downSinceTick = NOT_DOWN;
            return false;
        }

        // A short reconnect never reaches the threshold, so it stays silent instead of speaking the fallbacks.
        if (downSinceTick == NOT_DOWN) {
            downSinceTick = nowTicks;
        }
        return nowTicks - downSinceTick >= LineSource.FALLBACK_AFTER_TICKS;
    }

    // Sends the request for a batch. Logged by kind, phase and mood only; no line text is involved.
    private void requestBatch(LineKey key) {
        LinesNeededEvent event = new LinesNeededEvent(
                EventIds.newEventId(),
                Instant.now(),
                key.kind().wire(),
                lower(key.phase().name()),
                lower(key.mood().name()));
        if (!sender.test(event)) {
            return;
        }
        Machiavillager.LOGGER.info("lines_needed sent kind={} phase={} mood={}",
                event.kind(), event.phase(), event.mood());
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
