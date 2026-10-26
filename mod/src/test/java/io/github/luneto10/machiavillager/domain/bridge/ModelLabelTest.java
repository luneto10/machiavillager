package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ModelLabelTest {

    @Test
    void format_keepsTheProviderAndTheNameAfterTheLastSlash() {
        assertEquals("AI: groq · qwen3.8-27b · 480 ms",
                ModelLabel.format("groq:qwen/qwen3.8-27b", OptionalInt.of(480)));
    }

    @Test
    void format_dropsTheVendorPrefixInsideTheModelName() {
        assertEquals("AI: groq · gpt-oss-120b", ModelLabel.format("groq:openai/gpt-oss-120b", OptionalInt.empty()));
        assertEquals("AI: nvidia · nemotron-3.5-bolt-e4b",
                ModelLabel.format("nvidia:nvidia/nemotron-3.5-bolt-e4b", OptionalInt.empty()));
    }

    @Test
    void format_leavesOutTheLatencyWhenAbsent() {
        assertEquals("AI: groq · llama", ModelLabel.format("groq:llama", OptionalInt.empty()));
    }

    @Test
    void format_returnsAMalformedModelAsItCame() {
        assertEquals("AI: no-colon-here", ModelLabel.format("no-colon-here", OptionalInt.empty()));
    }
}
