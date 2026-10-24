package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.story.Phase;

/**
 * Proposes telling each nearby player about a real village. Hints are rare and only start once the
 * story has begun: before that, the NPC has nothing to say about the village yet.
 */
public final class HintOption implements ActionOption {
    static final long MIN_TICKS_BETWEEN_HINTS = 12_000; // 10 min of active play
    static final long MAX_TICKS_BETWEEN_HINTS = 24_000; // 20 min: after this a hint is almost certain

    private static final int NORMAL_WEIGHT = 10;
    private static final int OVERDUE_WEIGHT = 1_000;
    private static final Phase FIRST_HINT_PHASE = Phase.SEEDS;

    @Override
    public void propose(Situation now, WeightedDice.Builder<NpcAction> candidates) {
        // Hints only exist once the story has started: before that, he has nothing to say about a village.
        if (now.phase().ordinal() < FIRST_HINT_PHASE.ordinal()) {
            return;
        }

        // Too soon after the last hint: stay quiet.
        if (now.ticksSinceHint() < MIN_TICKS_BETWEEN_HINTS) {
            return;
        }

        // Overdue hints become almost certain, so nobody waits forever.
        int weight = now.ticksSinceHint() >= MAX_TICKS_BETWEEN_HINTS ? OVERDUE_WEIGHT : NORMAL_WEIGHT;

        // Only players close enough to hear him can receive a hint.
        for (NearbyPlayer player : now.playersWithin(Hearing.DISTANCE)) {
            candidates.add(new NpcAction.Hint(player.id()), weight);
        }
    }
}
