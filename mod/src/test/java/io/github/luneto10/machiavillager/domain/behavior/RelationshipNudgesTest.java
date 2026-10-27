package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges.Applied;
import io.github.luneto10.machiavillager.domain.behavior.RelationshipNudges.Rejected;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipNudgesTest {
    private static final UUID PLAYER = UUID.fromString("7f3c2a10-5b1e-4c8d-9a2f-1e2d3c4b5a69");
    private static final UUID OTHER = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final long RISE_INTERVAL = RelationshipNudges.RISE_INTERVAL_TICKS;
    private static final long DROP_INTERVAL = RelationshipNudges.DROP_INTERVAL_TICKS;
    private static final long RECOVERY = RelationshipNudges.RECOVERY_WINDOW_TICKS;
    private static final long WINDOW = RelationshipNudges.BUDGET_WINDOW_TICKS;
    // Five seconds at 20 ticks per second: well inside a 30 second wait.
    private static final long FIVE_SECONDS = 100;

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
    void secondRiseInsideThirtySeconds_isRateLimited() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 2, 0);

        assertEquals(new Rejected(RelationshipNudges.RATE_LIMITED), nudges.tryNudge(PLAYER, 2, RISE_INTERVAL - 1));
    }

    @Test
    void riseAtExactlyThirtySeconds_isAllowedAgain() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 2, 0);

        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 2, RISE_INTERVAL));
    }

    @Test
    void secondDropInsideThirtySeconds_isRateLimited() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -2, 0);

        assertEquals(new Rejected(RelationshipNudges.RATE_LIMITED), nudges.tryNudge(PLAYER, -2, FIVE_SECONDS));
    }

    @Test
    void twoRisesFiveSecondsApart_secondIsRateLimited() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 2, 0);

        assertEquals(new Rejected(RelationshipNudges.RATE_LIMITED), nudges.tryNudge(PLAYER, 2, FIVE_SECONDS));
    }

    @Test
    void dropThenRiseFiveSecondsLater_bothApply() {
        RelationshipNudges nudges = new RelationshipNudges();

        assertEquals(new Applied(-4), nudges.tryNudge(PLAYER, -4, 0));
        // The rise is capped at half of the -4 drop, so +2 is exactly what it may be.
        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 2, FIVE_SECONDS));
    }

    @Test
    void riseThenDropFiveSecondsLater_bothApply() {
        RelationshipNudges nudges = new RelationshipNudges();

        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 2, 0));
        assertEquals(new Applied(-2), nudges.tryNudge(PLAYER, -2, FIVE_SECONDS));
    }

    @Test
    void riseAfterDropOfMinusEight_isClampedToPlusFour() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -8, 0);

        assertEquals(new Applied(4), nudges.tryNudge(PLAYER, 5, FIVE_SECONDS));
    }

    @Test
    void riseAfterDropOfMinusOne_isClampedToPlusOne() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -1, 0);

        assertEquals(new Applied(1), nudges.tryNudge(PLAYER, 5, FIVE_SECONDS));
    }

    @Test
    void riseJustInsideTheRecoveryWindow_isStillClamped() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -8, 0);

        assertEquals(new Applied(4), nudges.tryNudge(PLAYER, 5, RECOVERY - 1));
    }

    @Test
    void riseAfterTheRecoveryWindow_isNotClamped() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -8, 0);

        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 5, RECOVERY + 1));
    }

    @Test
    void recoveryClampSurvivesTheBudgetWindowReset() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -8, WINDOW - FIVE_SECONDS);

        // The new budget window has started, but the drop is still recent, so the rise is still halved.
        assertEquals(new Applied(4), nudges.tryNudge(PLAYER, 5, WINDOW + FIVE_SECONDS));
    }

    @Test
    void risesInOneWindow_stopAtTheRiseLimit_andTheLastOneIsClamped() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 4, 0);
        nudges.tryNudge(PLAYER, 4, RISE_INTERVAL);

        // Eight of the ten are used: the next +5 is clamped to the two left.
        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 5, 2 * RISE_INTERVAL));
        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, 5, 3 * RISE_INTERVAL));
    }

    @Test
    void dropsInOneWindow_stopAtTheDropLimit_andTheLastOneIsClamped() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -8, 0);
        nudges.tryNudge(PLAYER, -8, DROP_INTERVAL);

        // Sixteen of the twenty are used: the next -10 is clamped to the four left.
        assertEquals(new Applied(-4), nudges.tryNudge(PLAYER, -10, 2 * DROP_INTERVAL));
        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, -1, 3 * DROP_INTERVAL));
    }

    @Test
    void riseBudgetFullDoesNotBlockDrops() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 5, 0);
        nudges.tryNudge(PLAYER, 5, RISE_INTERVAL);

        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, 3, 2 * RISE_INTERVAL));
        // The refused rise did not start the drop wait, so a drop one tick later is still allowed.
        assertEquals(new Applied(-2), nudges.tryNudge(PLAYER, -2, 2 * RISE_INTERVAL + 1));
    }

    @Test
    void dropBudgetFullDoesNotStartTheRiseWait() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, -10, 0);
        nudges.tryNudge(PLAYER, -10, DROP_INTERVAL);

        assertEquals(new Rejected(RelationshipNudges.CAPPED), nudges.tryNudge(PLAYER, -1, 2 * DROP_INTERVAL));
        // The refused drop did not start the rise wait; the +2 is only clamped by the recent -10 drop, to 5.
        assertEquals(new Applied(2), nudges.tryNudge(PLAYER, 2, 2 * DROP_INTERVAL + 1));
    }

    @Test
    void budgetResetsWhenTheWindowIsOver() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 5, 0);
        nudges.tryNudge(PLAYER, 5, RISE_INTERVAL);

        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 5, WINDOW));
    }

    @Test
    void differentPlayers_haveIndependentIntervalsAndBudgets() {
        RelationshipNudges nudges = new RelationshipNudges();
        nudges.tryNudge(PLAYER, 5, 0);
        nudges.tryNudge(PLAYER, 5, RISE_INTERVAL);

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
        assertEquals(new Applied(5), nudges.tryNudge(PLAYER, 5, RISE_INTERVAL + 1));
    }
}
