package io.github.luneto10.machiavillager.domain.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpeechRulesTest {
    private static final String VAR = SpeechRules.COOLDOWN_VARIABLE;

    @Test
    void missingVariable_usesTheDefault() {
        assertEquals(SpeechRules.UNPROMPTED_COOLDOWN_DEFAULT, SpeechRules.unpromptedCooldown(Map.of()));
    }

    @Test
    void wholeSeconds_areRead() {
        assertEquals(Duration.ofSeconds(45), SpeechRules.unpromptedCooldown(Map.of(VAR, "45")));
    }

    @Test
    void zero_isAccepted_andMeansNoLimit() {
        Duration wait = SpeechRules.unpromptedCooldown(Map.of(VAR, "0"));

        assertEquals(Duration.ZERO, wait);
        assertEquals(0L, SpeechRules.toTicks(wait));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "1.5", "abc", "", "   ", "1234567890", "+3"})
    void invalidValues_fallBackToTheDefault_withoutThrowing(String raw) {
        assertEquals(SpeechRules.UNPROMPTED_COOLDOWN_DEFAULT, SpeechRules.unpromptedCooldown(Map.of(VAR, raw)));
    }

    @Test
    void secondsConvertToTwentyTicksEach() {
        assertEquals(3_600L, SpeechRules.toTicks(Duration.ofSeconds(180)));
    }
}
