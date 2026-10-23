package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeleportPolicyTest {
    private static final double FAR = 20.0;
    private static final double JUST_OUTSIDE = TeleportPolicy.CATCH_UP_DISTANCE + 0.01;
    private static final boolean CARES = true;
    private static final boolean INDIFFERENT = false;

    @Test
    void calmFarFriendSameDimension_allowed() {
        assertTrue(TeleportPolicy.mayCatchUp(Mood.CALM, FAR, true, CARES));
    }

    @Test
    void boredFarFriend_allowed() {
        assertTrue(TeleportPolicy.mayCatchUp(Mood.BORED, FAR, true, CARES));
    }

    @Test
    void irritated_refused() {
        assertFalse(TeleportPolicy.mayCatchUp(Mood.IRRITATED, FAR, true, CARES));
    }

    @Test
    void distanceAtOrBelowThreshold_refused() {
        assertFalse(TeleportPolicy.mayCatchUp(Mood.CALM, TeleportPolicy.CATCH_UP_DISTANCE, true, CARES));
        assertFalse(TeleportPolicy.mayCatchUp(Mood.CALM, 5.0, true, CARES));
        assertTrue(TeleportPolicy.mayCatchUp(Mood.CALM, JUST_OUTSIDE, true, CARES));
    }

    @Test
    void otherDimension_refused() {
        assertFalse(TeleportPolicy.mayCatchUp(Mood.CALM, FAR, false, CARES));
    }

    @Test
    void notCaringAboutPlayer_refused() {
        assertFalse(TeleportPolicy.mayCatchUp(Mood.CALM, FAR, true, INDIFFERENT));
    }
}
