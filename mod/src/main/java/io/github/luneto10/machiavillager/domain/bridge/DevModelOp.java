package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Locale;

/** What an operator asks of the model choice: pin one model, or go back to the backend's default. */
public enum DevModelOp {
    SET,
    RESET;

    /** The spelling on the wire: "set" or "reset", the enum values the schema accepts. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Reads the wire spelling back. Throws for any other text, which the schema should already have refused. */
    public static DevModelOp fromWire(String wire) {
        for (DevModelOp op : values()) {
            if (op.wireName().equals(wire)) {
                return op;
            }
        }
        throw new IllegalArgumentException("unknown dev_model op: " + wire);
    }
}
