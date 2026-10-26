package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NearChatCooldownTest {
    private static final String VAR = NearChatCooldown.COOLDOWN_VARIABLE;
    private static final long COOLDOWN = 100L;
    private static final long START = 1_000L;
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void firstLine_isAllowed() {
        assertTrue(new NearChatCooldown(COOLDOWN).tryAcquire(ALICE, START));
    }

    @Test
    void secondLineInsideWindow_isRefused() {
        NearChatCooldown cooldown = new NearChatCooldown(COOLDOWN);
        cooldown.tryAcquire(ALICE, START);

        assertFalse(cooldown.tryAcquire(ALICE, START + COOLDOWN - 1));
    }

    @Test
    void allowedAgain_exactlyAtTheBoundary() {
        NearChatCooldown cooldown = new NearChatCooldown(COOLDOWN);
        cooldown.tryAcquire(ALICE, START);

        assertTrue(cooldown.tryAcquire(ALICE, START + COOLDOWN));
    }

    @Test
    void players_areIndependent() {
        NearChatCooldown cooldown = new NearChatCooldown(COOLDOWN);
        cooldown.tryAcquire(ALICE, START);

        assertTrue(cooldown.tryAcquire(BOB, START));
    }

    @Test
    void zeroCooldown_alwaysAllows() {
        NearChatCooldown cooldown = new NearChatCooldown(0L);

        assertTrue(cooldown.tryAcquire(ALICE, START));
        assertTrue(cooldown.tryAcquire(ALICE, START));
        assertTrue(cooldown.tryAcquire(ALICE, START + 1));
    }

    @Test
    void refusedLine_doesNotExtendTheWindow() {
        NearChatCooldown cooldown = new NearChatCooldown(COOLDOWN);
        cooldown.tryAcquire(ALICE, START);
        // Refused at halfway; if it had restarted the wait, the boundary below would be refused.
        assertFalse(cooldown.tryAcquire(ALICE, START + COOLDOWN / 2));

        assertTrue(cooldown.tryAcquire(ALICE, START + COOLDOWN));
    }

    @Test
    void cooldownTicks_missingVariable_usesTheDefault() {
        assertEquals(100L, NearChatCooldown.cooldownTicks(Map.of()));
    }

    @Test
    void cooldownTicks_wholeSeconds_areConvertedToTicks() {
        assertEquals(140L, NearChatCooldown.cooldownTicks(Map.of(VAR, "7")));
    }

    @Test
    void cooldownTicks_zero_meansNoLimit() {
        assertEquals(0L, NearChatCooldown.cooldownTicks(Map.of(VAR, "0")));
    }

    @Test
    void cooldownTicks_surroundingSpaces_areStripped() {
        assertEquals(240L, NearChatCooldown.cooldownTicks(Map.of(VAR, " 12 ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-3", "abc", "", "1.5", "+3"})
    void cooldownTicks_invalidValues_fallBackToTheDefault(String raw) {
        assertEquals(100L, NearChatCooldown.cooldownTicks(Map.of(VAR, raw)));
    }
}
