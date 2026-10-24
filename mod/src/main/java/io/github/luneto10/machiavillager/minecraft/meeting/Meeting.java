package io.github.luneto10.machiavillager.minecraft.meeting;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.domain.behavior.Relationships;
import io.github.luneto10.machiavillager.domain.behavior.SummonBlocker;
import io.github.luneto10.machiavillager.domain.meeting.Briefing;
import io.github.luneto10.machiavillager.domain.meeting.MeetingRules;
import io.github.luneto10.machiavillager.domain.meeting.ReadingTime;
import io.github.luneto10.machiavillager.domain.meeting.SummonAgenda;
import io.github.luneto10.machiavillager.domain.meeting.Topic;
import io.github.luneto10.machiavillager.domain.story.Phase;
import io.github.luneto10.machiavillager.minecraft.notice.NoticePayload;
import io.github.luneto10.machiavillager.minecraft.notice.NoticeStyle;
import io.github.luneto10.machiavillager.minecraft.notice.Notices;
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
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

/**
 * One summoned meeting with ONE player. He warns them, pulls them to a safe spot next to him, talks through
 * the agenda, then sends them back. The brain ticks it every server tick. Server side only.
 */
public final class Meeting {
    private static final int TICKS_PER_SECOND = 20;
    // Only his own tile is skipped, so the player may land right next to him.
    private static final int SUMMON_MIN_RING = 1;
    private static final String RUDE_LINE = "How rude.";
    private static final String SUMMON_TITLE = "Machiavillager";
    private static final String SUMMON_BODY = "is summoning you";
    private static final int FIRST_TICK = 0;

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

    /** True once he has pulled the player. A meeting called off before the pull never does. */
    public boolean pulled() {
        return returnPoint != null;
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
            Supplier<Optional<SummonBlocker>> blocker) {
        // Looked up every tick: he may have logged out or died since the last one.
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        if (player == null || !player.isAlive()) {
            return false;
        }

        totalTicks++;
        return switch (state) {
            case WARNING -> tickWarning(npc, level, player, data, villages, blocker);
            case MEETING -> tickMeeting(npc, player, data);
        };
    }

    /**
     * Ends the meeting now. If he already pulled the player, the player is sent back at once, so a meeting
     * cut short by the NPC being removed does not strand them away from home.
     */
    public void abort(ServerLevel level) {
        // Logged out: nobody to send back and no card to hide.
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        if (player == null) {
            return;
        }

        // Still warning: the player never moved, so only the card has to go.
        if (returnPoint == null) {
            Notices.show(player, NoticePayload.hide());
            return;
        }
        returnPoint.sendBack(player);
    }

    /** Calls the summons off before the pull. Silent toward the player: the card just goes away. */
    private void cancelWarning(ServerPlayer player, SummonBlocker blocker) {
        // The reason goes to the server log only, so a refused summons can be traced without telling the player.
        Machiavillager.LOGGER.info("Summon of {} cancelled: {}", player.getPlainTextName(), blocker);
        Notices.show(player, NoticePayload.hide());
    }

    private boolean tickWarning(
            PathfinderMob npc,
            ServerLevel level,
            ServerPlayer player,
            StoryData data,
            VillageFinder villages,
            Supplier<Optional<SummonBlocker>> blocker) {
        // The card is sent once, on the first tick; the client counts the seconds down by itself.
        if (ticksInState == FIRST_TICK) {
            Notices.show(player, new NoticePayload(
                    NoticeStyle.SUMMON, SUMMON_TITLE, SUMMON_BODY, MeetingRules.WARNING_TICKS));
        }

        // Once a second: a fight or a flight that starts during the warning cancels the summons.
        if (ticksInState % TICKS_PER_SECOND == 0) {
            Optional<SummonBlocker> refusal = blocker.get();
            if (refusal.isPresent()) {
                cancelWarning(player, refusal.get());
                return false;
            }
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
        // Same landing rule as his dog teleport: a safe block next to him, or his exact spot while he is grounded.
        Optional<Vec3> landing = SafeTeleport.landingNear(npc, npc, SUMMON_MIN_RING);
        if (landing.isEmpty()) {
            cancelWarning(player, SummonBlocker.NO_ROOM);
            return false;
        }

        // Remember exactly where he stands before moving him, so the way back is exact.
        returnPoint = ReturnPoint.of(player);
        Vec3 at = landing.get();
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);

        // The warning ends the moment he pulls, so the card goes now rather than when its timer runs out.
        Notices.show(player, NoticePayload.hide());

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
