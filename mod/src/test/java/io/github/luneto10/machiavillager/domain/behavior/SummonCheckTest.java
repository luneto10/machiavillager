package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SummonCheckTest {

    @Test
    void allGood_allowed() {
        assertTrue(allGood().allowed());
    }

    @Test
    void otherDimension_blocks() {
        assertFalse(new SummonCheck(false, false, false, false, false, true, false).allowed());
    }

    @Test
    void inCombat_blocks() {
        assertFalse(new SummonCheck(true, true, false, false, false, true, false).allowed());
    }

    @Test
    void flying_blocks() {
        assertFalse(new SummonCheck(true, false, true, false, false, true, false).allowed());
    }

    @Test
    void inVehicle_blocks() {
        assertFalse(new SummonCheck(true, false, false, true, false, true, false).allowed());
    }

    @Test
    void optedOut_blocks() {
        assertFalse(new SummonCheck(true, false, false, false, true, true, false).allowed());
    }

    @Test
    void cooldownNotReady_blocks() {
        assertFalse(new SummonCheck(true, false, false, false, false, false, false).allowed());
    }

    @Test
    void npcInDanger_blocks() {
        assertFalse(new SummonCheck(true, false, false, false, false, true, true).allowed());
    }

    private static SummonCheck allGood() {
        return new SummonCheck(true, false, false, false, false, true, false);
    }
}
