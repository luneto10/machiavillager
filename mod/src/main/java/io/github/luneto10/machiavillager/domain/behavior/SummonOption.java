package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;

/** Proposes pulling a far player to him for a meeting. Rare at first, a habit once he rules. */
public final class SummonOption implements ActionOption {
    // Closer than this, walking over is enough.
    static final double MIN_DISTANCE = 10.0;

    // Rare at first, a habit once he rules.
    private static final int PARTNER_WEIGHT = 6;
    private static final int SEEDS_WEIGHT = 8;
    private static final int PROPOSAL_WEIGHT = 12;
    private static final int REGIME_WEIGHT = 16;
    private static final int WAR_WEIGHT = 16;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        // The weight depends only on the story phase, not on the player.
        int weight = weightFor(now.phase());

        // Only far players who may be summoned; close ones are reached by walking.
        for (NearbyPlayer player : now.nearbyPlayers()) {
            if (player.distance() >= MIN_DISTANCE && now.isSummonable(player.id())) {
                candidates.add(new NpcAction.Summon(player.id()), weight);
            }
        }
    }

    private static int weightFor(Phase phase) {
        return switch (phase) {
            case PARTNER -> PARTNER_WEIGHT;
            case SEEDS -> SEEDS_WEIGHT;
            case PROPOSAL -> PROPOSAL_WEIGHT;
            case REGIME -> REGIME_WEIGHT;
            case WAR -> WAR_WEIGHT;
        };
    }
}
