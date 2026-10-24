package io.github.luneto10.machiavillager.domain.meeting;

/** One part of a summoned meeting, in the order it is spoken. */
public enum Topic {
    /** Greeting. */
    OPENING,
    /** What he is up to; depends on the story phase. */
    STATUS,
    /** The village he found. */
    VILLAGE,
    /** How he feels about you. */
    BOND,
    /** That he needs nobody. */
    INDEPENDENCE,
    /** The meeting is over. */
    CLOSING
}
