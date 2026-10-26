package io.github.luneto10.machiavillager.domain.behavior;

import io.github.luneto10.machiavillager.domain.time.PerPlayerCooldown;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Decides how far a suggested affinity change may move him. The AI proposes a delta; this class clamps it, spaces
 * the applied changes out per player, and caps how much each player can move him per window. Pure Java: no
 * Minecraft imports. Mutable on purpose: it remembers the last applied nudge and the spending per player in the current window.
 */
public final class RelationshipNudges {
    /** Largest rise one suggestion may apply. Feeling good is harder to earn than feeling bad. */
    public static final int MAX_RISE = 5;
    /** Largest drop one suggestion may apply, as a magnitude: a suggestion of -15 applies -10. */
    public static final int MAX_DROP = 10;
    /** Ticks between two applied nudges to the same player: 30 seconds. */
    public static final long INTERVAL_TICKS = 600;
    /** Length of the budget window: 15 minutes, counted from the first applied nudge of that window (fixed window). */
    public static final long BUDGET_WINDOW_TICKS = 18_000;
    /** Most affinity one player may add in one window. */
    public static final int WINDOW_RISE_LIMIT = 10;
    /** Most affinity one player may remove in one window, as a magnitude. */
    public static final int WINDOW_DROP_LIMIT = 20;

    /** Refused: a suggestion of zero moves nothing. */
    public static final String INVALID_DELTA = "invalid_delta";
    /** Refused: this player was nudged less than {@link #INTERVAL_TICKS} ago. */
    public static final String RATE_LIMITED = "rate_limited";
    /** Refused: this player's budget for this direction is used up in the current window. */
    public static final String CAPPED = "capped";

    /** What one suggestion did: the delta actually applied, or the reason it was refused. */
    public sealed interface Result permits Applied, Rejected {}

    /** The delta applied; it may be smaller than suggested when the budget was nearly used up. */
    public record Applied(int delta) implements Result {}

    /** The suggestion was refused; the reason is one of the constants above. */
    public record Rejected(String reason) implements Result {}

    // Only applied nudges start the interval, so a refused one never makes the player wait.
    private final PerPlayerCooldown interval = new PerPlayerCooldown(INTERVAL_TICKS);
    private final Map<UUID, Budget> budgets = new HashMap<>();

    /** Applies one suggestion if the limits allow it. Returns the delta applied, or the reason it was refused. */
    public Result tryNudge(UUID player, int delta, long nowTicks) {
        Objects.requireNonNull(player, "player");
        // A zero change is refused before any limit is read or spent.
        if (delta == 0) {
            return new Rejected(INVALID_DELTA);
        }
        // Checked, not used: the interval only starts once the nudge really applies.
        if (!interval.isReady(player, nowTicks)) {
            return new Rejected(RATE_LIMITED);
        }

        Budget budget = budgetOf(player, nowTicks);
        int clamped = Math.clamp(delta, -MAX_DROP, MAX_RISE);
        // Only the room left in this direction counts, so a partly used budget clamps the nudge instead of refusing it.
        int room = clamped > 0 ? WINDOW_RISE_LIMIT - budget.rise : WINDOW_DROP_LIMIT - budget.drop;
        int magnitude = Math.min(Math.abs(clamped), room);
        if (magnitude <= 0) {
            return new Rejected(CAPPED);
        }

        // Applied: spend the budget and start the interval together, after every check has passed.
        if (clamped > 0) {
            budget.rise += magnitude;
        } else {
            budget.drop += magnitude;
        }
        interval.markUsed(player, nowTicks);
        return new Applied(clamped > 0 ? magnitude : -magnitude);
    }

    // A new window starts when none exists yet or the last one is over; a refused nudge never starts one by spending.
    private Budget budgetOf(UUID player, long nowTicks) {
        Budget budget = budgets.get(player);
        if (budget == null || nowTicks - budget.windowStartTick >= BUDGET_WINDOW_TICKS) {
            budget = new Budget(nowTicks);
            budgets.put(player, budget);
        }
        return budget;
    }

    // Both totals are magnitudes, so the caps read the same way in each direction.
    private static final class Budget {
        private final long windowStartTick;
        private int rise;
        private int drop;

        private Budget(long windowStartTick) {
            this.windowStartTick = windowStartTick;
        }
    }
}
