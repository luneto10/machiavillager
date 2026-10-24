package io.github.luneto10.machiavillager.domain.behavior;

/**
 * How far his voice carries, in blocks. Every "can this player hear him" check uses this one value, so
 * deciding to talk and actually being heard never disagree.
 */
public final class Hearing {
    public static final double DISTANCE = 24.0;

    private Hearing() {}
}
