package io.github.luneto10.machiavillager.domain.meeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummonAgendaTest {
    private static final int NEUTRAL_AFFINITY = 50;
    private static final int NO_INDEPENDENCE = 0;

    @Test
    void neutralBriefing_hasOnlyOpeningStatusAndClosing() {
        List<Topic> agenda = SummonAgenda.build(briefing(false, NEUTRAL_AFFINITY, NO_INDEPENDENCE));

        assertEquals(List.of(Topic.OPENING, Topic.STATUS, Topic.CLOSING), agenda);
    }

    @Test
    void everythingOn_hasAllSixTopicsInOrder() {
        List<Topic> agenda = SummonAgenda.build(
                briefing(true, SummonAgenda.CLOSE_BOND_AFFINITY, SummonAgenda.INDEPENDENT_FROM));

        assertEquals(List.of(Topic.OPENING, Topic.STATUS, Topic.VILLAGE, Topic.BOND,
                Topic.INDEPENDENCE, Topic.CLOSING), agenda);
    }

    @Test
    void bondTriggersAtCloseAndColdThresholds_butNotInBetween() {
        assertTrue(SummonAgenda.build(briefing(false, 70, 0)).contains(Topic.BOND));
        assertTrue(SummonAgenda.build(briefing(false, 30, 0)).contains(Topic.BOND));
        assertFalse(SummonAgenda.build(briefing(false, 50, 0)).contains(Topic.BOND));
    }

    @Test
    void alwaysStartsWithOpeningAndEndsWithClosing() {
        for (boolean knows : new boolean[] {false, true}) {
            for (int affinity : new int[] {0, 30, 50, 70, 100}) {
                for (int independence : new int[] {0, 30, 100}) {
                    List<Topic> agenda = SummonAgenda.build(briefing(knows, affinity, independence));

                    assertEquals(Topic.OPENING, agenda.get(0));
                    assertEquals(Topic.CLOSING, agenda.get(agenda.size() - 1));
                }
            }
        }
    }

    private static Briefing briefing(boolean knowsVillage, int affinity, int independence) {
        return new Briefing(Phase.SEEDS, knowsVillage, affinity, independence);
    }
}
