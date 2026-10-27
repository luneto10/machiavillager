package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FallbackLinesTest {
    private static final int DRAWS = 200;
    // Words that would make the early, friendly chatter sound like his scheming.
    private static final List<String> SCHEMING_WORDS =
            List.of("emerald", "empire", "ambition", "crown", "power", "rule");

    @Test
    void everyFallbackLine_passesTheRulesOfItsKind_forEveryPhase() {
        Random random = new Random(7);
        for (LineKind kind : LineKind.values()) {
            for (Phase phase : Phase.values()) {
                for (int i = 0; i < DRAWS; i++) {
                    Optional<String> line = FallbackLines.pick(kind, phase, random);
                    line.ifPresent(text ->
                            assertTrue(LineRules.isValidLine(kind, text), kind + " " + phase + ": " + text));
                }
            }
        }
    }

    @Test
    void everyAvailableKey_hasAtLeastTwoDistinctLines() {
        Random random = new Random(7);
        for (LineKind kind : LineKind.values()) {
            for (Phase phase : Phase.values()) {
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < DRAWS; i++) {
                    FallbackLines.pick(kind, phase, random).ifPresent(seen::add);
                }
                // A hint in the partner phase has no line by design, so it is skipped.
                if (!seen.isEmpty()) {
                    assertTrue(seen.size() >= 2, kind + " " + phase + " has " + seen.size() + " line(s)");
                }
            }
        }
    }

    @Test
    void partnerAndSeedsBarks_neverMentionPowerTalk() {
        Random random = new Random(7);
        for (Phase phase : List.of(Phase.PARTNER, Phase.SEEDS)) {
            for (int i = 0; i < DRAWS; i++) {
                String line = FallbackLines.pick(LineKind.BARK, phase, random).orElseThrow().toLowerCase();
                for (String word : SCHEMING_WORDS) {
                    assertFalse(line.contains(word), phase + " bark contains '" + word + "': " + line);
                }
            }
        }
    }

    @Test
    void partnerHint_isEmpty() {
        assertTrue(FallbackLines.pick(LineKind.HINT, Phase.PARTNER, new Random(7)).isEmpty());
    }
}
