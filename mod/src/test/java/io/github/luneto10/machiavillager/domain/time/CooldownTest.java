package io.github.luneto10.machiavillager.domain.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownTest {
    private static final long DURATION = 100L;
    private static final long START = 1_000L;

    @Test
    void freshCooldown_isReady() {
        assertTrue(new Cooldown(DURATION).isReady(START));
    }

    @Test
    void notReadyBeforeDuration() {
        Cooldown cooldown = new Cooldown(DURATION);
        cooldown.markUsed(START);

        assertFalse(cooldown.isReady(START + DURATION - 1));
    }

    @Test
    void readyAgainAtExactlyDuration() {
        Cooldown cooldown = new Cooldown(DURATION);
        cooldown.markUsed(START);

        assertTrue(cooldown.isReady(START + DURATION));
    }

    @Test
    void tryUse_returnsFalseWhileCooling() {
        Cooldown cooldown = new Cooldown(DURATION);

        assertTrue(cooldown.tryUse(START));
        assertFalse(cooldown.tryUse(START + 1));
    }

    @Test
    void tryUse_refusedUseDoesNotRestartTheWait() {
        Cooldown cooldown = new Cooldown(DURATION);
        cooldown.markUsed(START);

        cooldown.tryUse(START + DURATION - 1);

        assertTrue(cooldown.isReady(START + DURATION));
    }

    @Test
    void zeroDuration_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Cooldown(0));
    }
}
