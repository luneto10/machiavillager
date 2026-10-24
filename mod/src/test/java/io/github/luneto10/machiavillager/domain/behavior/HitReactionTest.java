package io.github.luneto10.machiavillager.domain.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HitReactionTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final long START = 0L;
    private static final long ONE_TICK = 1L;
    private static final int HITS_IN_A_FLURRY = 10;

    @Test
    void firstHit_lowersAffinity_andRaisesIndependence() {
        Relationships before = Relationships.initial();

        Relationships after = new HitReaction().apply(before, ALICE, START);

        assertEquals(Relationships.DEFAULT_AFFINITY - HitReaction.AFFINITY_LOSS, after.affinityOf(ALICE));
        assertEquals(HitReaction.INDEPENDENCE_GAIN, after.independence());
        assertEquals(Relationships.DEFAULT_AFFINITY, before.affinityOf(ALICE));
        assertEquals(0, before.independence());
    }

    @Test
    void spamHits_countOncePerCooldown() {
        HitReaction reaction = new HitReaction();
        Relationships relations = Relationships.initial();

        for (int i = 0; i < HITS_IN_A_FLURRY; i++) {
            relations = reaction.apply(relations, ALICE, START + i);
        }

        assertEquals(Relationships.DEFAULT_AFFINITY - HitReaction.AFFINITY_LOSS, relations.affinityOf(ALICE));
        assertEquals(HitReaction.INDEPENDENCE_GAIN, relations.independence());
    }

    @Test
    void afterAffinityCooldown_affinityDropsAgain_butIndependenceStillCooling() {
        HitReaction reaction = new HitReaction();
        Relationships relations = reaction.apply(Relationships.initial(), ALICE, START);

        relations = reaction.apply(relations, ALICE, START + HitReaction.AFFINITY_COOLDOWN_TICKS);

        assertEquals(Relationships.DEFAULT_AFFINITY - 2 * HitReaction.AFFINITY_LOSS, relations.affinityOf(ALICE));
        assertEquals(HitReaction.INDEPENDENCE_GAIN, relations.independence());
    }

    @Test
    void twoPlayers_shareIndependenceCooldown() {
        HitReaction reaction = new HitReaction();
        Relationships relations = reaction.apply(Relationships.initial(), ALICE, START);

        relations = reaction.apply(relations, BOB, START + ONE_TICK);

        assertEquals(Relationships.DEFAULT_AFFINITY - HitReaction.AFFINITY_LOSS, relations.affinityOf(BOB));
        assertEquals(HitReaction.INDEPENDENCE_GAIN, relations.independence());
    }

    @Test
    void otherPlayersAffinityUnchanged() {
        HitReaction reaction = new HitReaction();

        Relationships relations = reaction.apply(Relationships.initial(), ALICE, START);

        assertEquals(Relationships.DEFAULT_AFFINITY, relations.affinityOf(BOB));
    }
}
