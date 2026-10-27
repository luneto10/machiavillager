package io.github.luneto10.machiavillager.domain.behavior;

import java.util.Objects;

/**
 * What he does when a player asks him to come. One rule, used twice: for the player_chat snapshot, so the AI
 * answers in line with it, and again when the come_to action arrives. Pure Java: no Minecraft imports.
 */
public enum ComeAnswer {
    /** He goes: calm, and he cares about the player. */
    COMES("comes"),
    /** He stays: not calm, or he does not care enough about the player. */
    REFUSES("refuses"),
    /** He never crosses a portal for anyone, so another dimension is always a no. */
    OTHER_DIMENSION("other_dimension"),
    /** He would go, but he just went to this player; the per-player cooldown is still running. */
    JUST_CAME("just_came");

    private final String wire;

    ComeAnswer(String wire) {
        this.wire = wire;
    }

    /** The lowercase name used on the wire, such as other_dimension. */
    public String wireName() {
        return wire;
    }

    /**
     * The answer for this request. {@code caresAbout} comes from {@link Relationships#caresAbout};
     * {@code cameRecently} is true while his cooldown for this player runs.
     */
    public static ComeAnswer decide(Mood mood, boolean caresAbout, boolean sameDimension, boolean cameRecently) {
        Objects.requireNonNull(mood, "mood");
        // The dimension comes first: even a friend gets the refusal that says where he is.
        if (!sameDimension) {
            return OTHER_DIMENSION;
        }
        // Not wanting to wins over the cooldown: an irritated refusal stays a refusal.
        if (mood != Mood.CALM || !caresAbout) {
            return REFUSES;
        }
        return cameRecently ? JUST_CAME : COMES;
    }

    /** Finds the answer with this wire name. Throws on an unknown name, so a bad value never passes quietly. */
    public static ComeAnswer fromWire(String name) {
        for (ComeAnswer value : values()) {
            if (value.wire.equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown come answer: " + name);
    }
}
