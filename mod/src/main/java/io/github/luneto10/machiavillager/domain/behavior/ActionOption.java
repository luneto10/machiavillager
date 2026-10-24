package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.WeightedDice;

/** Proposes candidate actions for the current situation. Proposes nothing when the action is impossible. */
public interface ActionOption {
    /** Adds this option's candidate actions, with weights, to the given builder. */
    void propose(Situation now, WeightedDice.Builder<NpcAction> candidates);
}
