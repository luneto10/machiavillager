package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;

/** Proposes walking after each player within reach. */
public final class FollowOption implements ActionOption {
    // Matches what he can see, so after a long Wander he still comes back for you.
    static final double MAX_FOLLOW_DISTANCE = 32.0;

    private static final int CALM_WEIGHT = 50;
    private static final int BORED_WEIGHT = 30;
    private static final int IRRITATED_WEIGHT = 20;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        int weight = weightFor(now.mood());
        for (NearbyPlayer player : now.playersWithin(MAX_FOLLOW_DISTANCE)) {
            candidates.add(new NpcAction.Follow(player.id()), weight);
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
