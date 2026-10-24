package io.github.luneto10.machiavillager.domain.meeting;

import java.util.ArrayList;
import java.util.List;

/** Builds the topics of one summoned meeting, in order. Pure: the game layer speaks them. */
public final class SummonAgenda {
    public static final int CLOSE_BOND_AFFINITY = 70;
    static final int COLD_BOND_AFFINITY = 30;
    static final int INDEPENDENT_FROM = 30;

    private SummonAgenda() {}

    /** The topics for this briefing: always opens, always has a status, always closes. */
    public static List<Topic> build(Briefing briefing) {
        // Every meeting has a reason, so the status topic is always there.
        List<Topic> agenda = new ArrayList<>();
        agenda.add(Topic.OPENING);
        agenda.add(Topic.STATUS);

        // The village only comes up once he has found one.
        if (briefing.knowsVillage()) {
            agenda.add(Topic.VILLAGE);
        }

        // Bond is only worth raising when he is clearly warm or clearly cold.
        if (briefing.affinity() >= CLOSE_BOND_AFFINITY || briefing.affinity() <= COLD_BOND_AFFINITY) {
            agenda.add(Topic.BOND);
        }

        // Independence comes up once he is independent enough to say so.
        if (briefing.independence() >= INDEPENDENT_FROM) {
            agenda.add(Topic.INDEPENDENCE);
        }

        // Every meeting ends with the close, so the player is never left hanging.
        agenda.add(Topic.CLOSING);
        return List.copyOf(agenda);
    }
}
