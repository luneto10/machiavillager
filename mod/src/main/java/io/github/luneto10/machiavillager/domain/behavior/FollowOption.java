package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;

/** Proposes walking after each player within reach, and after far players he cares about. */
public final class FollowOption implements ActionOption {
    // Matches what he can see, so after a long Wander he still comes back for you.
    static final double MAX_FOLLOW_DISTANCE = 32.0;

    private static final int CALM_WEIGHT = 50;
    private static final int BORED_WEIGHT = 30;
    private static final int IRRITATED_WEIGHT = 20;

    // Far players are proposed only when he cares about them. He goes looking for friends he lost,
    // and the dog-style catch-up in the follow goal then brings him over. Lower than near weights on purpose.
    private static final int CALM_FAR_WEIGHT = 20;
    private static final int BORED_FAR_WEIGHT = 10;
    private static final int IRRITATED_FAR_WEIGHT = 5;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        for (NearbyPlayer player : now.nearbyPlayers()) {
            if (player.distance() <= MAX_FOLLOW_DISTANCE) {
                candidates.add(new NpcAction.Follow(player.id()), weightFor(now.mood()));
            } else if (now.caresFor(player.id())) {
                candidates.add(new NpcAction.Follow(player.id()), farWeightFor(now.mood()));
            }
        }
    }

    private static int weightFor(Mood mood) {
        return switch (mood) {
            case CALM -> CALM_WEIGHT;
            case BORED -> BORED_WEIGHT;
            case IRRITATED -> IRRITATED_WEIGHT;
        };
    }

    private static int farWeightFor(Mood mood) {
        return switch (mood) {
            case CALM -> CALM_FAR_WEIGHT;
            case BORED -> BORED_FAR_WEIGHT;
            case IRRITATED -> IRRITATED_FAR_WEIGHT;
        };
    }
}
