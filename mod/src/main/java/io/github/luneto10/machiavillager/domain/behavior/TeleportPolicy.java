package io.github.luneto10.machiavillager.domain.behavior;

/** Pure rules for when the NPC may teleport to a friend, like a dog catching up with its owner. */
public final class TeleportPolicy {
    /** Same distance vanilla wolves teleport from. */
    public static final double CATCH_UP_DISTANCE = 12.0;

    private TeleportPolicy() {}

    /**
     * Dog-style catch-up: may the NPC teleport to the player it is following? {@code caresAbout} comes from
     * {@link Relationships#caresAbout}, which fades as he grows independent.
     */
    public static boolean mayCatchUp(Mood mood, double distance, boolean sameDimension, boolean caresAbout) {
        return mood != Mood.IRRITATED && distance > CATCH_UP_DISTANCE && sameDimension && caresAbout;
    }
}
