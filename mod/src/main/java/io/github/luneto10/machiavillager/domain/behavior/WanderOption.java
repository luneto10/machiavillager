package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;

/** Proposes a stroll. Always possible, so the catalog is never empty. */
public final class WanderOption implements ActionOption {
    private static final int CALM_WEIGHT = 25;
    private static final int BORED_WEIGHT = 45;
    private static final int IRRITATED_WEIGHT = 25;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        candidates.add(new NpcAction.Wander(), weightFor(now.mood()));
    }

    private static int weightFor(Mood mood) {
        return switch (mood) {
            case CALM -> CALM_WEIGHT;
            case BORED -> BORED_WEIGHT;
            case IRRITATED -> IRRITATED_WEIGHT;
        };
    }
}
