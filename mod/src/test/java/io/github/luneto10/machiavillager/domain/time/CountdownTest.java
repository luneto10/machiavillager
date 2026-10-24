package io.github.luneto10.machiavillager.domain.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CountdownTest {
    private static final long SEED = 42L;
    private static final int MIN_TICKS = 200;
    private static final int MAX_TICKS = 600;
    private static final int SAMPLES = 1000;

    @Test
    void new_firesOnFirstTick() {
        Countdown countdown = Countdown.every(5);

        assertTrue(countdown.tick());
    }

    @Test
    void every_firesExactlyEveryNTicks() {
        Countdown countdown = Countdown.every(3);
        List<Boolean> fired = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            fired.add(countdown.tick());
        }

        assertEquals(List.of(true, false, false, true, false, false, true), fired);
    }

    @Test
    void remainingTicks_countsDown() {
        Countdown countdown = Countdown.every(5);

        assertTrue(countdown.tick());
        assertEquals(5, countdown.remainingTicks());
        assertFalse(countdown.tick());
        assertEquals(4, countdown.remainingTicks());
        assertFalse(countdown.tick());
        assertEquals(3, countdown.remainingTicks());
    }

    @Test
    void expireNow_makesNextTickFire() {
        Countdown countdown = Countdown.every(100);
        assertTrue(countdown.tick());
        assertFalse(countdown.tick());

        countdown.expireNow();

        assertTrue(countdown.tick());
    }

    @Test
    void restart_startsFreshIntervalNow() {
        Countdown countdown = Countdown.every(5);
        assertTrue(countdown.tick());
        assertFalse(countdown.tick());
        List<Boolean> fired = new ArrayList<>();

        countdown.restart();
        for (int i = 0; i < 5; i++) {
            fired.add(countdown.tick());
        }

        assertEquals(List.of(false, false, false, false, true), fired);
    }

    @Test
    void randomBetween_intervalsStayWithinRange() {
        Countdown countdown = Countdown.randomBetween(MIN_TICKS, MAX_TICKS, new Random(SEED));
        assertTrue(countdown.tick());

        for (int i = 0; i < SAMPLES; i++) {
            int interval = ticksUntilFire(countdown);
            assertTrue(interval >= MIN_TICKS && interval <= MAX_TICKS, "interval was " + interval);
        }
    }

    @Test
    void randomBetween_sameSeed_sameIntervals() {
        List<Integer> first = intervals(Countdown.randomBetween(MIN_TICKS, MAX_TICKS, new Random(SEED)), 50);
        List<Integer> second = intervals(Countdown.randomBetween(MIN_TICKS, MAX_TICKS, new Random(SEED)), 50);

        assertEquals(first, second);
    }

    @Test
    void invalidArguments_throw() {
        assertThrows(IllegalArgumentException.class, () -> Countdown.every(0));
        assertThrows(IllegalArgumentException.class, () -> Countdown.randomBetween(0, 5, new Random(SEED)));
        assertThrows(IllegalArgumentException.class, () -> Countdown.randomBetween(10, 5, new Random(SEED)));
        assertThrows(NullPointerException.class, () -> Countdown.randomBetween(1, 5, null));
    }

    /** Ticks needed for the next fire, counting the firing tick. */
    private static int ticksUntilFire(Countdown countdown) {
        int ticks = 1;
        while (!countdown.tick()) {
            ticks++;
        }
        return ticks;
    }

    /** Fires the countdown once to start, then records the next {@code count} intervals. */
    private static List<Integer> intervals(Countdown countdown, int count) {
        countdown.tick();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(ticksUntilFire(countdown));
        }
        return result;
    }
}
