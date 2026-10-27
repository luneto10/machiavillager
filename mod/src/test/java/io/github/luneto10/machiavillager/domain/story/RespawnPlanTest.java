package io.github.luneto10.machiavillager.domain.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RespawnPlanTest {
    private static final long DEATH = 5_000L;
    private static final long DUE = DEATH + RespawnPlan.RESPAWN_DELAY_TICKS;

    @Test
    void notDueBeforeTheDelay() {
        RespawnPlan plan = RespawnPlan.after(DEATH, "minecraft:overworld", 1.5, 64.0, -3.25);

        assertFalse(plan.isDue(DUE - 1));
    }

    @Test
    void dueAtExactlyTheDelay() {
        RespawnPlan plan = RespawnPlan.after(DEATH, "minecraft:overworld", 1.5, 64.0, -3.25);

        assertTrue(plan.isDue(DUE));
    }

    @Test
    void dueAfterTheDelay() {
        RespawnPlan plan = RespawnPlan.after(DEATH, "minecraft:overworld", 1.5, 64.0, -3.25);

        assertTrue(plan.isDue(DUE + 100));
    }

    @Test
    void factoryAddsTheDelayAndKeepsTheSpot() {
        RespawnPlan plan = RespawnPlan.after(DEATH, "minecraft:the_nether", 1.5, 64.0, -3.25);

        assertEquals(DUE, plan.dueTick());
        assertEquals("minecraft:the_nether", plan.dimension());
        assertEquals(1.5, plan.x());
        assertEquals(64.0, plan.y());
        assertEquals(-3.25, plan.z());
    }
}
