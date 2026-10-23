package io.github.luneto10.machiavillager.domain.story;

/** Story phase of the NPC, driven by his ambition. Ordered from the start of the story to its end. */
public enum Phase {
    PARTNER,
    SEEDS,
    PROPOSAL,
    REGIME,
    WAR;

    /** The phase after this one; {@link #WAR} is its own next phase. */
    public Phase next() {
        return this == WAR ? WAR : values()[ordinal() + 1];
    }

    /** The phase before this one; {@link #PARTNER} is its own previous phase. */
    public Phase previous() {
        return this == PARTNER ? PARTNER : values()[ordinal() - 1];
    }
}
