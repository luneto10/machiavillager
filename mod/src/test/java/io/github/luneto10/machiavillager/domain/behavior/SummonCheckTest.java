package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SummonCheckTest {

    @Test
    void allGood_allowedWithNoBlocker() {
        assertTrue(allGood().allowed());
        assertEquals(Optional.empty(), allGood().blocker());
    }

    @Test
    void otherDimension_blocks() {
        SummonCheck check = new SummonCheck(false, false, false, false, false, true, false);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.OTHER_DIMENSION), check.blocker());
    }

    @Test
    void inCombat_blocks() {
        SummonCheck check = new SummonCheck(true, true, false, false, false, true, false);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.IN_COMBAT), check.blocker());
    }

    @Test
    void flying_blocks() {
        SummonCheck check = new SummonCheck(true, false, true, false, false, true, false);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.FLYING), check.blocker());
    }

    @Test
    void inVehicle_blocks() {
        SummonCheck check = new SummonCheck(true, false, false, true, false, true, false);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.IN_VEHICLE), check.blocker());
    }

    @Test
    void optedOut_blocks() {
        SummonCheck check = new SummonCheck(true, false, false, false, true, true, false);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.OPTED_OUT), check.blocker());
    }

    @Test
    void cooldownNotReady_blocks() {
        SummonCheck check = new SummonCheck(true, false, false, false, false, false, false);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.COOLDOWN), check.blocker());
    }

    @Test
    void npcInDanger_blocks() {
        SummonCheck check = new SummonCheck(true, false, false, false, false, true, true);
        assertFalse(check.allowed());
        assertEquals(Optional.of(SummonBlocker.NPC_IN_DANGER), check.blocker());
    }

    @Test
    void severalRulesFail_firstInOrderWins() {
        // Another dimension and combat both fail: the dimension rule is checked first.
        SummonCheck check = new SummonCheck(false, true, true, true, true, false, true);
        assertEquals(Optional.of(SummonBlocker.OTHER_DIMENSION), check.blocker());
    }

    private static SummonCheck allGood() {
        return new SummonCheck(true, false, false, false, false, true, false);
    }
}
