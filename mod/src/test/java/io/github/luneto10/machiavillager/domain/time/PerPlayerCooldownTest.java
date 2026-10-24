package io.github.luneto10.machiavillager.domain.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerPlayerCooldownTest {
    private static final long DURATION = 100L;
    private static final long START = 1_000L;
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void freshPlayer_isReady() {
        assertTrue(new PerPlayerCooldown(DURATION).isReady(ALICE, START));
    }

    @Test
    void constructor_rejectsZeroDuration() {
        assertThrows(IllegalArgumentException.class, () -> new PerPlayerCooldown(0L));
    }

    @Test
    void notReadyBeforeDuration_readyAtExactlyDuration() {
        PerPlayerCooldown cooldown = new PerPlayerCooldown(DURATION);
        cooldown.markUsed(ALICE, START);

        assertFalse(cooldown.isReady(ALICE, START + DURATION - 1));
        assertTrue(cooldown.isReady(ALICE, START + DURATION));
    }

    @Test
    void tryUse_returnsFalseWhileCooling() {
        PerPlayerCooldown cooldown = new PerPlayerCooldown(DURATION);

        assertTrue(cooldown.tryUse(ALICE, START));
        assertFalse(cooldown.tryUse(ALICE, START + 1));
    }

    @Test
    void players_areIndependent() {
        PerPlayerCooldown cooldown = new PerPlayerCooldown(DURATION);
        cooldown.markUsed(ALICE, START);

        assertFalse(cooldown.isReady(ALICE, START));
        assertTrue(cooldown.isReady(BOB, START));
        assertTrue(cooldown.tryUse(BOB, START));
    }
}
