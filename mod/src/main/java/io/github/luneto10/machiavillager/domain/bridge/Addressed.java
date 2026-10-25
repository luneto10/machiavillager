package io.github.luneto10.machiavillager.domain.bridge;

/**
 * Why the mod forwarded a chat line. The wire names are the contract's values, set by the mod and read by the
 * backend. Pure Java: no Minecraft imports.
 */
public enum Addressed {
    /** The player used his name. */
    NAME("name"),
    /** The line continues a conversation the mod is tracking. */
    CONVERSATION("conversation"),
    /** Nearby speech without his name. */
    NEAR("near"),
    /** The player talked over him while he was speaking. */
    BUTT_IN("butt_in"),
    /** The @m channel: private text, never voice. */
    PRIVATE("private");

    private final String wire;

    Addressed(String wire) {
        this.wire = wire;
    }

    /** The lowercase name used on the wire, such as butt_in. */
    public String wireName() {
        return wire;
    }

    /** Finds the reason with this wire name. Throws on an unknown name, so a bad value never passes quietly. */
    public static Addressed fromWire(String name) {
        for (Addressed value : values()) {
            if (value.wire.equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown addressed value: " + name);
    }
}
