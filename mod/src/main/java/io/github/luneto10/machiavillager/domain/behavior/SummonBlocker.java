package io.github.luneto10.machiavillager.domain.behavior;

/** The first fairness rule that refused a summons. Listed in the order the rules are checked. */
public enum SummonBlocker {
    /** The player is in another dimension, out of reach of one teleport. */
    OTHER_DIMENSION,
    /** The player was hit recently and is in a fight. */
    IN_COMBAT,
    /** The player is flying or gliding. */
    FLYING,
    /** The player is riding a boat, a horse or anything else. */
    IN_VEHICLE,
    /** The player turned summons off. */
    OPTED_OUT,
    /** The last summons for this player was too recent. */
    COOLDOWN,
    /** The NPC himself is on fire, in lava or being hit. */
    NPC_IN_DANGER,
    /** No safe landing next to him at the moment of the pull (he is in the air or in water). */
    NO_ROOM
}
