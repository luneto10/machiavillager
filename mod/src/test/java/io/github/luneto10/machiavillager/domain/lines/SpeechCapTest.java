package io.github.luneto10.machiavillager.domain.lines;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeechCapTest {

    @Test
    void firstUnpromptedLine_isAllowed() {
        assertTrue(new SpeechCap(100).canSpeak(0));
    }

    @Test
    void afterASpeech_theGapMustPassBeforeTheNextOne() {
        SpeechCap cap = new SpeechCap(100);
        cap.markSpoke(1_000);

        assertFalse(cap.canSpeak(1_099));
        assertTrue(cap.canSpeak(1_100));
    }

    @Test
    void defaultConstructor_usesSixtySeconds() {
        SpeechCap cap = new SpeechCap();
        cap.markSpoke(0);

        assertFalse(cap.canSpeak(SpeechCap.DEFAULT_MIN_GAP_TICKS - 1));
        assertTrue(cap.canSpeak(SpeechCap.DEFAULT_MIN_GAP_TICKS));
    }

    @Test
    void negativeGap_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SpeechCap(-1));
    }
}
