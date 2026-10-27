package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeathRulesTest {

    @Test
    void highAffinityIsHalved() {
        assertEquals(49, DeathRules.affinityAfterKill(98));
        assertEquals(50, DeathRules.affinityAfterKill(100));
    }

    @Test
    void oddValueRoundsDown() {
        assertEquals(24, DeathRules.affinityAfterKill(49));
    }

    @Test
    void oneGoesToZero() {
        assertEquals(0, DeathRules.affinityAfterKill(1));
    }

    @Test
    void zeroStaysZero() {
        assertEquals(0, DeathRules.affinityAfterKill(0));
    }
}
