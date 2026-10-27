package io.github.luneto10.machiavillager.domain.lines;

import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Picks the line to speak for a key. A cached line wins. With the backend down for at least
 * {@link #FALLBACK_AFTER_TICKS}, a hand-written fallback fills an empty pool; a shorter outage stays silent, so a
 * reconnect does not switch languages mid-game. With the backend up and the pool empty, he stays silent while the
 * first batch is on its way. Pure Java: callers pass nowTicks.
 */
public final class LineSource {
    /** How long the backend must be continuously down before the English fallbacks may speak: 5 minutes. */
    public static final long FALLBACK_AFTER_TICKS = 6_000;

    private final LineBank bank;

    /**
     * What to speak now, and whether the caller should ask the backend for a new batch.
     *
     * @param template the line to speak, or empty when he should stay silent
     * @param askBackend true when a batch is wanted now and the caller should send the request
     */
    public record Next(Optional<String> template, boolean askBackend) {
        /** Null check only: an empty template is a valid answer. */
        public Next {
            Objects.requireNonNull(template, "template");
        }
    }

    /** A source over the given bank. The bank holds the cached pools and the ask times. */
    public LineSource(LineBank bank) {
        this.bank = Objects.requireNonNull(bank, "bank");
    }

    /** The bank behind this source, so the caller can store a delivered batch. */
    public LineBank bank() {
        return bank;
    }

    /**
     * The line to speak for the key, and whether a batch should be asked for. Marks the ask when it says so.
     * {@code fallbackAllowed} says the backend has been down long enough for the fallbacks; it is ignored while up.
     */
    public Next next(LineKey key, boolean backendUp, boolean fallbackAllowed, long nowTicks, RandomGenerator random) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(random, "random");

        // A cached line always wins, so the fallback is only used while the pool is empty.
        Optional<String> template = bank.pick(key, random);
        if (template.isEmpty() && !backendUp && fallbackAllowed) {
            template = FallbackLines.pick(key.kind(), key.phase(), random);
        }

        // Asking is only possible with the backend up; the bank rules out asking too often.
        boolean askBackend = backendUp && bank.needsBatch(key, nowTicks);
        if (askBackend) {
            bank.markAsked(key, nowTicks);
        }
        return new Next(template, askBackend);
    }
}
