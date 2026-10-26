package io.github.luneto10.machiavillager.domain.bridge;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/** Makes the ids of events the mod reports. Pure: no Minecraft types, so the domain tests cover it. */
public final class EventIds {
    // Event ids are "evt_" plus 16 hex digits, so 8 random bytes.
    private static final String PREFIX = "evt_";
    private static final int ID_BYTES = 8;

    private EventIds() {}

    /** A new event id. Random rather than sequential: the backend only needs ids to be unique. */
    public static String newEventId() {
        byte[] bytes = new byte[ID_BYTES];
        ThreadLocalRandom.current().nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }
}
