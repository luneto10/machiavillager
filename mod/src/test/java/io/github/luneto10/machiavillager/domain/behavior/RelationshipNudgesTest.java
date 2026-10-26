package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges.Applied;
import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges.Rejected;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipNudgesTest {
    private static final UUID PLAYER = UUID.fromString("7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69");
    private static final UUID OTHER = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final long INTERVAL = RelationshipNudges.INTERVAL_TICKS;
    private static final long HOUR = RelationshipNudges.BUDGET_WINDOW_TICKS;

    @Test
    void positiveDeltaAboveTheRiseLimit_isClampedToFive() {
        RelationshipNudges nudges = new RelationshipNudges();

        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 9, 0));
    }

    @Test
    void negativeDeltaBelowTheDropLimit_isClampedToMinusTen() {
        RelationshipNudges nudges = new RelationshipNudges();

        assertEquals(new Applied(-10), nudges.tryNudge(PLAYER, -15, 0));
    }

    @Test
    void secondNudgeInsideTwoMinutes_isRateLimited() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 2, 0);

        assertEquals(new Rejected(RelationshipNudges.RATE_LIMITED), nudges.tryNudge(PLAYER, 2, INTERVAL - 1));
    }

    @Test
    void nudgeAtExactlyTwoMinutes_isAllowedAgain() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 2, 0);

        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 2, INTERVAL));
    }

    @Test
    void risesInOneHour_stopAtTheRiseLimit_andTheLastOneIsClamped() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 4, 0);
        nudges.tryNudge(PLAYER, 4, INTERVAL);

        // Eight of the ten are used: the next +5 is clamped to the two left.
        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 5, 2 * INTERVAL));
        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, 5, 3 * INTERVAL));
    }

    @Test
    void dropsInOneHour_stopAtTheDropLimit_andTheLastOneIsClamped() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -8, 0);
        nudges.tryNudge(PLAYER, -8, INTERVAL);

        // Sixteen of the twenty are used: the next -10 is clamped to the four left.
        assertEquals(new Applied(-4), nudges.tryNudge(PLAYER, -10, 2 * INTERVAL));
        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, -1, 3 * INTERVAL));
    }

    @Test
    void riseBudgetFullDoesNotBlockDrops() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 5, 0);
        nudges.tryNudge(PLAYER, 5, INTERVAL);

        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, 3, 2 * INTERVAL));
        // The refused rise did not use the interval, so a drop one tick later is still allowed.
        assertEquals(new Applied(-2), nudges.tryNudge(PLAYER, -2, 2 * INTERVAL + 1));
    }

    @Test
    void budgetResetsWhenTheHourWindowIsOver() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 5, 0);
        nudges.tryNudge(PLAYER, 5, INTERVAL);

        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 5, HOUR));
    }

    @Test
    void differentPlayers_haveIndependentIntervalsAndBudgets() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 5, 0);
        nudges.tryNudge(PLAYER, 5, INTERVAL);

        assertEquals(new Applied(5), nudges.tryNudge(OTHER, 5, 1));
    }

    @Test
    void zeroDelta_isInvalid() {
        RelationshipNudges nudges = new RelationshipNudges();

        assertEquals(new Rejected(RelationshipNudges.INVALID_DELTA), nudges.tryNudge(PLAYER, 0, 0));
    }

    @Test
    void rejectedNudges_doNotConsumeTheIntervalOrTheBudget() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 0, 0);
        // A zero change must not start the interval, so the real nudge right after it applies.
        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 5, 1));

        // A rate-limited attempt must not spend budget: if it had, only 0 would be left after the wait, not 5.
        nudges.tryNudge(PLAYER, 5, 2);
        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 5, INTERVAL + 1));
    }
}
