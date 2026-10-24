package io.github.luneto10.machiavillager.minecraft.meeting;

import io.github.luneto10.machiavillager.domain.Barks;
import io.github.luneto10.machiavillager.domain.meeting.SummonAgenda;
import io.github.luneto10.machiavillager.domain.meeting.Topic;
import io.github.luneto10.machiavillager.domain.story.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;

/** The spoken lines of a summoned meeting, one per agenda topic. Text only: no world access. */
public final class AgendaLines {
    private static final Barks OPENING_LINES = new Barks(
            "%s! Mandatory meeting. Agenda: me.",
            "Ah, %s. Sit. Metaphorically.");
    private static final String STATUS_PARTNER = "I've been thinking about friendship. And logistics.";
    private static final String STATUS_SEEDS = "I've been thinking about organization. Leadership. Hats.";
    private static final String STATUS_PROPOSAL = "I have a proposal for you. Think big. Think me.";
    private static final String STATUS_REGIME = "The village runs smoothly now. Mostly because of me.";
    private static final String STATUS_WAR = "There are traitors among us. Not you. Probably.";
    private static final String VILLAGE_LINE = "The village at %d, %d. Too many emeralds, too few leaders.";
    private static final String VILLAGE_UNKNOWN_LINE = "Which village? Never mind.";
    private static final String BOND_CLOSE_LINE = "You are one of the few people I tolerate. Don't ruin it.";
    private static final String BOND_COLD_LINE = "We need to talk about your attitude. Later. In writing.";
    private static final String INDEPENDENCE_LINE =
            "I've been doing things on my own lately. I don't need a babysitter.";
    private static final Barks CLOSING_LINES = new Barks(
            "That's all. You may go. For now.",
            "Meeting adjourned. Tell no one.");

    private AgendaLines() {}

    /** One line per topic, in agenda order. */
    public static List<String> render(
            List<Topic> topics, String playerName, Phase phase, Optional<BlockPos> village, int affinity) {
        // Each topic becomes exactly one line, so the meeting reads the agenda as written.
        List<String> lines = new ArrayList<>();
        for (Topic topic : topics) {
            lines.add(lineFor(topic, playerName, phase, village, affinity));
        }
        return List.copyOf(lines);
    }

    private static String lineFor(
            Topic topic, String playerName, Phase phase, Optional<BlockPos> village, int affinity) {
        // Exhaustive: a new topic must get its words before this compiles.
        return switch (topic) {
            case OPENING -> OPENING_LINES.pick(ThreadLocalRandom.current()).formatted(playerName);
            case STATUS -> statusLine(phase);
            case VILLAGE -> villageLine(village);
            case BOND -> affinity >= SummonAgenda.CLOSE_BOND_AFFINITY ? BOND_CLOSE_LINE : BOND_COLD_LINE;
            case INDEPENDENCE -> INDEPENDENCE_LINE;
            case CLOSING -> CLOSING_LINES.pick(ThreadLocalRandom.current());
        };
    }

    // What he is up to depends on how far the story has gone.
    private static String statusLine(Phase phase) {
        return switch (phase) {
            case PARTNER -> STATUS_PARTNER;
            case SEEDS -> STATUS_SEEDS;
            case PROPOSAL -> STATUS_PROPOSAL;
            case REGIME -> STATUS_REGIME;
            case WAR -> STATUS_WAR;
        };
    }

    private static String villageLine(Optional<BlockPos> village) {
        // The agenda only lists the village when one is known, but the text must still be safe without it.
        return village
                .map(at -> VILLAGE_LINE.formatted(at.getX(), at.getZ()))
                .orElse(VILLAGE_UNKNOWN_LINE);
    }
}
