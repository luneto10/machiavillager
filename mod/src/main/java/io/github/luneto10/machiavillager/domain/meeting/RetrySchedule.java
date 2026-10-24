package io.github.luneto10.machiavillager.domain.meeting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Retry timers for summons that were blocked or cancelled: retried every 5 minutes until it works; only
 * success or opt-out clears it. Pure Java: no Minecraft imports.
 * Mutable on purpose, like {@link io.github.luneto10.machiavillager.domain.time.Cooldown}.
 */
public final class RetrySchedule {
    /** A blocked or cancelled summons is retried after this many ticks. */
    public static final long RETRY_DELAY_TICKS = MeetingRules.SUMMON_RETRY_TICKS;

    // Due time per player. A player with an entry here is pending; takeDue removes the entry.
    private final Map<UUID, Long> dueTicks = new HashMap<>();

    /** Arms the next retry: due one delay from now. Called on every failure, and never gives up. */
    public void schedule(UUID player, long nowTicks) {
        dueTicks.put(Objects.requireNonNull(player, "player"), nowTicks + RETRY_DELAY_TICKS);
    }

    /** Returns and removes the players whose due time has come. A failed retry schedules them again. */
    public List<UUID> takeDue(long nowTicks) {
        List<UUID> due = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> entries = dueTicks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Long> entry = entries.next();
            if (nowTicks >= entry.getValue()) {
                due.add(entry.getKey());
                entries.remove();
            }
        }
        return due;
    }

    /** True while this player has a retry waiting for its due time. */
    public boolean isPending(UUID player) {
        return dueTicks.containsKey(player);
    }

    /** Forgets this player entirely: on success, or when they opted out. */
    public void clear(UUID player) {
        dueTicks.remove(player);
    }
}
