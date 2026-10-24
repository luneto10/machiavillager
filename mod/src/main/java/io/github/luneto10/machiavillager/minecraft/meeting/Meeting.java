package io.github.luneto10.machiavillager.minecraft.meeting;

import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.meeting.Briefing;
import io.github.luneto10.machiavillager.domain.meeting.MeetingRules;
import io.github.luneto10.machiavillager.domain.meeting.ReadingTime;
import io.github.luneto10.machiavillager.domain.meeting.SummonAgenda;
import io.github.luneto10.machiavillager.domain.meeting.Topic;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.minecraft.npc.NpcSpeech;
import io.github.luneto10.machiavillager.minecraft.npc.SafeTeleport;
import io.github.luneto10.machiavillager.minecraft.story.StoryData;
import io.github.luneto10.machiavillager.minecraft.world.VillageFinder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/**
 * One summoned meeting with ONE player. He warns them, pulls them to a safe spot next to him, talks through
 * the agenda, then sends them back. The brain ticks it every server tick. Server side only.
 */
public final class Meeting {
    private static final int TICKS_PER_SECOND = 20;
    private static final double BLOCK_CENTER = 0.5;
    private static final String NEVER_MIND_LINE = "Never mind. Later.";
    private static final String RUDE_LINE = "How rude.";

    /** Warning first, then talking. */
    private enum State { WARNING, MEETING }

    private final UUID target;
    private State state = State.WARNING;
    // Ticks spent in the current state; reset whenever the state changes.
    private int ticksInState;
    // Ticks since the summons started, across both states: the safety cap counts this one.
    private int totalTicks;
    private int ticksUntilNextLine;
    private Deque<String> lines = new ArrayDeque<>();
    // Set when he pulls the player; null during the warning, while the player has not moved yet.
    private ReturnPoint returnPoint;

    private Meeting(UUID target) {
        this.target = target;
    }

    /** A new meeting with this player. Nothing happens until the first tick. */
    public static Meeting start(UUID target) {
        return new Meeting(Objects.requireNonNull(target, "target"));
    }

    /** The player this meeting is about. */
    public UUID target() {
        return target;
    }

    /** True while the meeting talks: he holds still and looks at the player. */
    public boolean inMeeting() {
        return state == State.MEETING;
    }

    /**
     * Advances the meeting by one tick. Returns true while it is still active. A player who logged out or
     * died ends it silently, since there is nobody left to talk to.
     */
    public boolean tick(
            PathfinderMob npc,
            ServerLevel level,
            StoryData data,
            VillageFinder villages,
            BooleanSupplier stillAllowed) {
        // Looked up every tick: he may have logged out or died since the last one.
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        if (player == null || !player.isAlive()) {
            return false;
        }

        totalTicks++;
        return switch (state) {
            case WARNING -> tickWarning(npc, level, player, data, villages, stillAllowed);
            case MEETING -> tickMeeting(npc, player, data);
        };
    }

    /**
     * Ends the meeting now. If he already pulled the player, the player is sent back at once, so a meeting
     * cut short by the NPC being removed does not strand them away from home.
     */
    public void abort(ServerLevel level) {
        // Still warning: the player never moved, so there is nothing to undo.
        if (returnPoint == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        if (player != null) {
            returnPoint.sendBack(player);
        }
    }

    private boolean tickWarning(
            PathfinderMob npc,
            ServerLevel level,
            ServerPlayer player,
            StoryData data,
            VillageFinder villages,
            BooleanSupplier stillAllowed) {
        // Once a second, so the countdown on screen and the re-check move together.
        if (ticksInState % TICKS_PER_SECOND == 0) {
            // A fight or a flight that starts during the warning cancels the summons.
            if (!stillAllowed.getAsBoolean()) {
                NpcSpeech.say(npc, NEVER_MIND_LINE);
                return false;
            }
            MeetingTitles.countdown(player, (MeetingRules.WARNING_TICKS - ticksInState) / TICKS_PER_SECOND);
        }

        // Still counting down: nothing moves yet.
        ticksInState++;
        if (ticksInState < MeetingRules.WARNING_TICKS) {
            return true;
        }
        return pull(npc, level, player, data, villages);
    }

    private boolean pull(
            PathfinderMob npc, ServerLevel level, ServerPlayer player, StoryData data, VillageFinder villages) {
        // No free spot next to him: the summons is called off and nobody is moved.
        Optional<BlockPos> spot = SafeTeleport.findSpotNear(npc, npc.blockPosition());
        if (spot.isEmpty()) {
            NpcSpeech.say(npc, NEVER_MIND_LINE);
            return false;
        }

        // Remember exactly where he stands before moving him, so the way back is exact.
        returnPoint = ReturnPoint.of(player);
        BlockPos at = spot.get();
        player.teleportTo(level, at.getX() + BLOCK_CENTER, at.getY(), at.getZ() + BLOCK_CENTER,
                Set.of(), player.getYRot(), player.getXRot(), true);

        // The agenda is built once, from the facts at the moment the player arrives.
        startAgenda(player, data, villages);
        state = State.MEETING;
        ticksInState = 0;
        return true;
    }

    private void startAgenda(ServerPlayer player, StoryData data, VillageFinder villages) {
        // Read once, so the whole agenda talks about the same facts.
        Phase phase = data.progress().phase();
        Optional<BlockPos> village = villages.lastKnown();
        int affinity = data.relationships().affinityOf(target);
        int independence = data.relationships().independence();

        // The domain decides which topics come up; the text layer only words them.
        List<Topic> topics = SummonAgenda.build(
                new Briefing(phase, village.isPresent(), affinity, independence));
        lines = new ArrayDeque<>(AgendaLines.render(
                topics, player.getPlainTextName(), phase, village, affinity));
    }

    private boolean tickMeeting(PathfinderMob npc, ServerPlayer player, StoryData data) {
        // He keeps his eyes on the player; the meeting goal already stops his walking.
        npc.getLookControl().setLookAt(player);

        // A portal mid-meeting ends it politely: distances across dimensions mean nothing.
        if (player.level() != npc.level()) {
            returnPoint.sendBack(player);
            return false;
        }

        // Walking off mid-meeting is rude: it costs affinity and ends the talk right there.
        if (npc.distanceTo(player) > MeetingRules.LEAVE_DISTANCE) {
            NpcSpeech.say(npc, RUDE_LINE);
            loseAffinity(data);
            returnPoint.sendBack(player);
            return false;
        }

        // Safety cap: a meeting never runs forever, even if the agenda stalls.
        if (totalTicks > MeetingRules.MAX_MEETING_TICKS) {
            returnPoint.sendBack(player);
            return false;
        }
        return speakNext(npc, player);
    }

    private boolean speakNext(PathfinderMob npc, ServerPlayer player) {
        // The last line is still on screen: wait for the reader before going on.
        if (ticksUntilNextLine > 0) {
            ticksUntilNextLine--;
            return true;
        }

        // Agenda spoken and the closing line read: the player goes home.
        if (lines.isEmpty()) {
            returnPoint.sendBack(player);
            return false;
        }

        // Each line gets as long as it takes to read, so nothing scrolls past unread.
        String line = lines.poll();
        NpcSpeech.say(npc, line);
        ticksUntilNextLine = ReadingTime.ticksFor(line);
        return true;
    }

    private void loseAffinity(StoryData data) {
        Relationships relationships = data.relationships();
        int lowered = relationships.affinityOf(target) - MeetingRules.RUDE_EXIT_AFFINITY_LOSS;
        data.setRelationships(relationships.withAffinity(target, lowered));
    }
}
