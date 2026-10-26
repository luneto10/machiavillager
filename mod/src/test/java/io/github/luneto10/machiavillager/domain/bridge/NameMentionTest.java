package io.github.luneto10.machiavillager.domain.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameMentionTest {

    @ParameterizedTest
    @ValueSource(strings = {"machi", "hey Machi!", "MACHIAVILLAGER, come here", "ok machi's house", "machiavillager"})
    void matches_nameAsWholeWord(String text) {
        assertTrue(NameMention.matches(text));
    }

    // "machine" and friends share a prefix with the name but are other words.
    @ParameterizedTest
    @ValueSource(strings = {"machine", "machinery", "xmachi", "hello there", ""})
    void matches_rejectsOtherWords(String text) {
        assertFalse(NameMention.matches(text));
    }

    @Test
    void matches_rejectsNull() {
        assertThrows(NullPointerException.class, () -> NameMention.matches(null));
    }
}
