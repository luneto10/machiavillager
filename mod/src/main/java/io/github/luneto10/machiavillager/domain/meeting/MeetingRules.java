package io.github.luneto10.machiavillager.domain.meeting;

/** Rules that shape one summoned meeting. Constants only, so the tuning lives in one place. */
public final class MeetingRules {
    /** Safety cap: a meeting never lasts longer than 2 minutes. */
    public static final int MAX_MEETING_TICKS = 2_400;

    /** Walking farther than this from him ends the meeting early, in blocks. */
    public static final double LEAVE_DISTANCE = 16.0;

    /** Affinity lost when the player walks off in the middle of the meeting. */
    public static final int RUDE_EXIT_AFFINITY_LOSS = 5;

    /** The warning before the meeting ends: 3 seconds. */
    public static final int WARNING_TICKS = 60;

    /** Minimum wait before he can summon the same player again: 20 minutes. */
    public static final int SUMMON_COOLDOWN_TICKS = 24_000;

    private MeetingRules() {}
}
