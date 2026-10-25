package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IndependenceDriftTest {
    // Minecraft runs 20 ticks per second: 1 minute = 1_200 ticks.
    private static final long TEN_MINUTES = 12_000;
    private static final long NINE_MINUTES = 10_800;
    private static final long ONE_MINUTE = 1_200;
    private static final long TWENTY_MINUTES = 24_000;
    private static final long TWELVE_MINUTES = 14_400;

    @Test
    void tenMinutesAlone_givesPlusOne() {
        assertEquals(1, new IndependenceDrift().tick(TEN_MINUTES, false));
    }

    @Test
    void nineMinutesAlone_givesNothingYet() {
        assertEquals(0, new IndependenceDrift().tick(NINE_MINUTES, false));
    }

    @Test
    void twentyMinutesWithCompany_givesMinusOne() {
        assertEquals(-1, new IndependenceDrift().tick(TWENTY_MINUTES, true));
    }

    @Test
    void mixedTime_accumulatesEachKindSeparately() {
        IndependenceDrift drift = new IndependenceDrift();

        assertEquals(0, drift.tick(NINE_MINUTES, false));
        assertEquals(0, drift.tick(TWELVE_MINUTES, true));
        assertEquals(1, drift.tick(ONE_MINUTE, false));
        assertEquals(-1, drift.tick(TWELVE_MINUTES, true));
    }
}
