package io.github.luneto10.machiavillager.domain.behavior;

/** What killing him costs the killer. Pure Java: no Minecraft imports. */
public final class DeathRules {
    // Killing him halves the killer's affinity.
    private static final int KILL_DIVISOR = 2;

    private DeathRules() {}

    /** The killer's affinity after the kill: half of it, rounded down (98 becomes 49, 1 becomes 0). */
    public static int affinityAfterKill(int before) {
        return before / KILL_DIVISOR;
    }
}
