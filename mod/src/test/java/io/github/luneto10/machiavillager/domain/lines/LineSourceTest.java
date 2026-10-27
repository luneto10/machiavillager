package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.behavior.Mood;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LineSourceTest {
    private static final LineKey KEY = new LineKey(LineKind.BARK, Phase.PARTNER, Mood.CALM);
    private static final long ASK = LineBank.ASK_INTERVAL_TICKS;
    private static final long NOW = 5_000;

    @Test
    void poolLine_winsOverFallback() {
        LineSource source = sourceWith(List.of("cached"));

        LineSource.Next next = source.next(KEY, false, true, NOW, new Random(1));

        assertEquals(Optional.of("cached"), next.template());
    }

    @Test
    void emptyPool_backendDownLongEnough_usesFallback() {
        LineSource source = sourceWith(List.of());

        LineSource.Next next = source.next(KEY, false, true, NOW, new Random(1));

        assertTrue(next.template().isPresent());
        assertEquals(FallbackLines.pick(LineKind.BARK, Phase.PARTNER, new Random(1)), next.template());
        assertFalse(next.askBackend());
    }

    @Test
    void emptyPool_backendDownShortly_staysSilentAndDoesNotAsk() {
        LineSource source = sourceWith(List.of());

        LineSource.Next next = source.next(KEY, false, false, NOW, new Random(1));

        assertEquals(Optional.empty(), next.template());
        assertFalse(next.askBackend());
    }

    @Test
    void emptyPool_backendUp_staysSilentAndAsks() {
        LineSource source = sourceWith(List.of());

        LineSource.Next next = source.next(KEY, true, false, NOW, new Random(1));

        assertEquals(Optional.empty(), next.template());
        assertTrue(next.askBackend());
    }

    @Test
    void backendUp_neverUsesFallback_evenIfAllowed() {
        LineSource source = sourceWith(List.of());

        LineSource.Next next = source.next(KEY, true, true, NOW, new Random(1));

        assertEquals(Optional.empty(), next.template());
        assertTrue(next.askBackend());
    }

    @Test
    void askBackend_onlyOnceInsideTheWindow() {
        LineSource source = sourceWith(List.of());
        Random random = new Random(1);

        assertTrue(source.next(KEY, true, false, NOW, random).askBackend());
        assertFalse(source.next(KEY, true, false, NOW + 100, random).askBackend());
        assertFalse(source.next(KEY, true, false, NOW + ASK - 1, random).askBackend());
        assertTrue(source.next(KEY, true, false, NOW + ASK, random).askBackend());
    }

    @Test
    void backendDown_neverAsks() {
        LineSource empty = sourceWith(List.of());
        LineSource used = sourceWith(List.of("a", "b", "c", "d", "e", "f"));
        Random random = new Random(1);

        for (int i = 0; i < 50; i++) {
            long now = NOW + i * ASK;
            assertFalse(empty.next(KEY, false, true, now, random).askBackend());
            assertFalse(used.next(KEY, false, false, now, random).askBackend());
        }
    }

    @Test
    void fallback_neverUsedWhilePoolHasLines() {
        LineSource source = sourceWith(List.of("cached"));
        Random random = new Random(3);

        for (int i = 0; i < 100; i++) {
            assertEquals(Optional.of("cached"), source.next(KEY, false, true, NOW, random).template());
        }
    }

    @Test
    void fallbackThreshold_isFiveMinutes() {
        assertEquals(6_000, LineSource.FALLBACK_AFTER_TICKS);
    }

    // A source over a bank that starts with the given lines for KEY; an empty list means no pool at all.
    private static LineSource sourceWith(List<String> lines) {
        return new LineSource(new LineBank(lines.isEmpty() ? Map.of() : Map.of(KEY, lines)));
    }
}
