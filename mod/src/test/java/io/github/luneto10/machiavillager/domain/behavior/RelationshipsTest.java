package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationshipsTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int ABOVE_MAX = 150;
    private static final int BELOW_MIN = -5;
    private static final int SOME_AFFINITY = 70;
    private static final int SOME_INDEPENDENCE = 30;

    @Test
    void unknownPlayer_hasDefaultAffinity() {
        assertEquals(Relationships.DEFAULT_AFFINITY, Relationships.initial().affinityOf(ALICE));
    }

    @Test
    void withAffinity_isImmutable_andClamped() {
        Relationships original = Relationships.initial();

        Relationships high = original.withAffinity(ALICE, ABOVE_MAX);
        Relationships low = original.withAffinity(ALICE, BELOW_MIN);

        assertEquals(Relationships.MAX, high.affinityOf(ALICE));
        assertEquals(Relationships.MIN, low.affinityOf(ALICE));
        assertEquals(Relationships.DEFAULT_AFFINITY, original.affinityOf(ALICE));
    }

    @Test
    void withIndependence_clamped() {
        Relationships original = Relationships.initial();

        assertEquals(Relationships.MAX, original.withIndependence(ABOVE_MAX).independence());
        assertEquals(Relationships.MIN, original.withIndependence(BELOW_MIN).independence());
        assertEquals(0, original.independence());
    }

    @Test
    void caresAbout_trueOnlyWhenAffinityAboveIndependence() {
        Relationships friendly = Relationships.initial().withAffinity(ALICE, SOME_AFFINITY);
        assertTrue(friendly.withIndependence(SOME_INDEPENDENCE).caresAbout(ALICE));

        Relationships equal = Relationships.initial().withAffinity(ALICE, SOME_AFFINITY)
                .withIndependence(SOME_AFFINITY);
        assertFalse(equal.caresAbout(ALICE));

        Relationships independent = Relationships.initial().withAffinity(ALICE, SOME_INDEPENDENCE)
                .withIndependence(SOME_AFFINITY);
        assertFalse(independent.caresAbout(ALICE));
    }

    @Test
    void initial_independenceIsZero() {
        assertEquals(0, Relationships.initial().independence());
    }

    @Test
    void whenHurtBy_lowersAttackerAffinity_andRaisesIndependence() {
        Relationships original = Relationships.initial();

        Relationships hurt = original.whenHurtBy(ALICE);

        assertEquals(Relationships.DEFAULT_AFFINITY - Relationships.HIT_AFFINITY_LOSS, hurt.affinityOf(ALICE));
        assertEquals(Relationships.HIT_INDEPENDENCE_GAIN, hurt.independence());
        assertEquals(Relationships.DEFAULT_AFFINITY, original.affinityOf(ALICE));
        assertEquals(0, original.independence());
    }

    @Test
    void restored_roundTripsAffinitiesAndIndependence() {
        Relationships original = Relationships.initial()
                .withAffinity(ALICE, SOME_AFFINITY)
                .withAffinity(BOB, BELOW_MIN)
                .withIndependence(SOME_INDEPENDENCE);

        Relationships restored = Relationships.restored(original.affinities(), original.independence());

        assertEquals(SOME_AFFINITY, restored.affinityOf(ALICE));
        assertEquals(Relationships.MIN, restored.affinityOf(BOB));
        assertEquals(SOME_INDEPENDENCE, restored.independence());
    }

    @Test
    void whenHurtBy_otherPlayersUnchanged() {
        Relationships relations = Relationships.initial().withAffinity(BOB, SOME_AFFINITY);

        Relationships hurt = relations.whenHurtBy(ALICE);

        assertEquals(SOME_AFFINITY, hurt.affinityOf(BOB));
    }
}
