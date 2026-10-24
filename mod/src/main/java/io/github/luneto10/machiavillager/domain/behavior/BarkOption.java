package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;

/** Proposes saying a line to each player close enough to hear it. */
public final class BarkOption implements ActionOption {
    private static final int CALM_WEIGHT = 20;
    private static final int BORED_WEIGHT = 20;
    private static final int IRRITATED_WEIGHT = 35;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        int weight = weightFor(now.mood());
        for (NearbyPlayer player : now.playersWithin(Hearing.DISTANCE)) {
            candidates.add(new NpcAction.Bark(player.id()), weight);
        }
    }

    private static int weightFor(Mood mood) {
        return switch (mood) {
            case CALM -> CALM_WEIGHT;
            case BORED -> BORED_WEIGHT;
            case IRRITATED -> IRRITATED_WEIGHT;
        };
    }
}
