package io.github.luneto10.machiavillager.domain.meeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetryScheduleTest {
    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);
    private static final long START = 1_000L;
    private static final long DELAY = RetrySchedule.RETRY_DELAY_TICKS;
    // Enough failures to show the schedule does not stop after a few.
    private static final int MANY_FAILURES = 10;

    @Test
    void notDueBeforeFiveMinutes() {
        RetrySchedule retries = new RetrySchedule();
        retries.schedule(ALICE, START);

        assertTrue(retries.isPending(ALICE));
        assertEquals(List.of(), retries.takeDue(START + DELAY - 1));
    }

    @Test
    void dueAtExactlyFiveMinutesAndRemovedAfterTake() {
        RetrySchedule retries = new RetrySchedule();
        retries.schedule(ALICE, START);

        assertEquals(List.of(ALICE), retries.takeDue(START + DELAY));
        assertFalse(retries.isPending(ALICE));
        assertEquals(List.of(), retries.takeDue(START + DELAY));
    }

    @Test
    void manyFailures_neverGiveUp() {
        RetrySchedule retries = new RetrySchedule();
        long now = START;
        for (int failure = 1; failure <= MANY_FAILURES; failure++) {
            retries.schedule(ALICE, now);
            now += DELAY;
            assertEquals(List.of(ALICE), retries.takeDue(now));

            // Failed again: still pending, so the retry keeps coming.
            retries.schedule(ALICE, now);
            assertTrue(retries.isPending(ALICE));
        }
    }

    @Test
    void clearRemovesThePlayer() {
        RetrySchedule retries = new RetrySchedule();
        retries.schedule(ALICE, START);

        retries.clear(ALICE);

        assertFalse(retries.isPending(ALICE));
        assertEquals(List.of(), retries.takeDue(START + DELAY));
    }

    @Test
    void playersAreIndependent() {
        RetrySchedule retries = new RetrySchedule();
        retries.schedule(ALICE, START);
        retries.schedule(BOB, START + 10);

        retries.clear(ALICE);

        assertFalse(retries.isPending(ALICE));
        assertTrue(retries.isPending(BOB));
        assertEquals(List.of(BOB), retries.takeDue(START + 10 + DELAY));
    }
}
