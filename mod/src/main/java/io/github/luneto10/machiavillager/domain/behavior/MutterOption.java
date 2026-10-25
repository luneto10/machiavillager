package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;

/** Proposes muttering to himself, but only when a player is close enough to overhear it. */
public final class MutterOption implements ActionOption {
    // Rare on purpose: a mutter is a flavour beat, not a conversation.
    private static final int CALM_WEIGHT = 3;
    private static final int BORED_WEIGHT = 6;
    private static final int IRRITATED_WEIGHT = 4;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        // Nobody close enough to overhear him: muttering would be wasted.
        if (now.playersWithin(Hearing.WHISPER_DISTANCE).isEmpty()) {
            return;
        }
        candidates.add(new NpcAction.Mutter(), weightFor(now.mood()));
    }

    private static int weightFor(Mood mood) {
        return switch (mood) {
            case CALM -> CALM_WEIGHT;
            case BORED -> BORED_WEIGHT;
            case IRRITATED -> IRRITATED_WEIGHT;
        };
    }
}
