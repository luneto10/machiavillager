package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ComeAnswerTest {

    @Test
    void calmAndCaring_comes() {
        assertEquals(ComeAnswer.COMES, ComeAnswer.decide(Mood.CALM, true, true, false));
    }

    @Test
    void boredOrIrritated_refusesEvenAFriend() {
        assertEquals(ComeAnswer.REFUSES, ComeAnswer.decide(Mood.BORED, true, true, false));
        assertEquals(ComeAnswer.REFUSES, ComeAnswer.decide(Mood.IRRITATED, true, true, false));
    }

    @Test
    void calmButNotCaring_refuses() {
        assertEquals(ComeAnswer.REFUSES, ComeAnswer.decide(Mood.CALM, false, true, false));
    }

    @Test
    void otherDimension_winsOverEveryOtherRule() {
        assertEquals(ComeAnswer.OTHER_DIMENSION, ComeAnswer.decide(Mood.CALM, true, false, false));
        assertEquals(ComeAnswer.OTHER_DIMENSION, ComeAnswer.decide(Mood.IRRITATED, false, false, true));
    }

    @Test
    void calmAndCaring_butJustWent_saysHeJustCame() {
        assertEquals(ComeAnswer.JUST_CAME, ComeAnswer.decide(Mood.CALM, true, true, true));
    }

    @Test
    void notWanting_winsOverTheCooldown() {
        assertEquals(ComeAnswer.REFUSES, ComeAnswer.decide(Mood.IRRITATED, true, true, true));
        assertEquals(ComeAnswer.REFUSES, ComeAnswer.decide(Mood.CALM, false, true, true));
    }

    @Test
    void wireNames_roundTrip_andUnknownIsRefused() {
        for (ComeAnswer answer : ComeAnswer.values()) {
            assertEquals(answer, ComeAnswer.fromWire(answer.wireName()));
        }
        assertThrows(IllegalArgumentException.class, () -> ComeAnswer.fromWire("maybe"));
    }
}
