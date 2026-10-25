package io.github.luneto10.machiavillager.domain.bridge;

import java.util.regex.Pattern;

/**
 * Wire constants the mod needs at runtime. The rules themselves live in shared/protocol.schema.json, generated
 * from backend/src/machiavillager/domain/protocol.py, and the mod checks every incoming message against that file.
 * Pure Java: no Minecraft imports.
 */
public final class ProtocolRules {
    /** The only protocol version this mod speaks. */
    public static final int PROTOCOL_VERSION = 1;

    /** Longest reason the mod sends back, in characters. Same limit the schema sets for a reason. */
    public static final int MAX_REASON_LENGTH = 200;

    // Needed before the full decode: a refused action is named by its id even when the rest of it is broken.
    // The id is checked here by format alone, because the schema cannot run on a message it could not parse.
    /** Action request id, such as act_Ab12Cd34. */
    public static final Pattern ACTION_ID = Pattern.compile("act_[A-Za-z0-9]{8,32}");

    private ProtocolRules() {}
}
