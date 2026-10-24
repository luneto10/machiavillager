package io.github.luneto10.machiavillager.application;

import io.github.luneto10.machiavillager.domain.WeightedDice;
import io.github.luneto10.machiavillager.domain.behavior.ActionOption;
import io.github.luneto10.machiavillager.domain.behavior.BarkOption;
import io.github.luneto10.machiavillager.domain.behavior.FollowOption;
import io.github.luneto10.machiavillager.domain.behavior.HintOption;
import io.github.luneto10.machiavillager.domain.behavior.NpcAction;
import io.github.luneto10.machiavillager.domain.behavior.Situation;
import io.github.luneto10.machiavillager.domain.behavior.SummonOption;
import io.github.luneto10.machiavillager.domain.behavior.WanderOption;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Use case: decide what the NPC should do next. */
public final class ChooseNextAction {
    private final List<ActionOption> catalog;

    /**
     * Creates the use case from a catalog of options. The catalog must not be empty. It must also
     * always have a possible option (such as a wander), or {@link #choose} will fail.
     */
    public ChooseNextAction(List<ActionOption> catalog) {
        Objects.requireNonNull(catalog, "catalog");
        this.catalog = List.copyOf(catalog);
        if (this.catalog.isEmpty()) {
            throw new IllegalArgumentException("Catalog needs at least one option");
        }
    }

    /** Picks the next action for the situation, weighted by mood and what is nearby. */
    public NpcAction choose(Situation now, RandomGenerator random) {
        return odds(now).roll(random);
    }

    /** The weighted odds of every action the situation allows right now. Used by choose and by dev tools. */
    public WeightedDice<NpcAction> odds(Situation now) {
        // Each option proposes its candidates; impossible options propose nothing.
        WeightedDice.Builder<NpcAction> candidates = WeightedDice.builder();
        for (ActionOption option : catalog) {
            option.propose(now, candidates);
        }
        return candidates.build();
    }

    /** Use case with the Follow, Wander, Bark, Hint and Summon options, in that order. */
    public static ChooseNextAction withDefaultCatalog() {
        return new ChooseNextAction(
                List.of(
                        new FollowOption(),
                        new WanderOption(),
                        new BarkOption(),
                        new HintOption(),
                        new SummonOption()));
    }
}
