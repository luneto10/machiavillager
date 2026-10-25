package io.github.luneto10.machiavillager.domain.bridge;

import java.util.Objects;

/** Sent by the mod after connecting. The protocol version is implied: this mod speaks only v1. */
public record Hello(String modVersion) implements BridgeMessage {

    /** Null check only; the length limit is enforced by the schema. */
    public Hello {
        Objects.requireNonNull(modVersion, "mod_version");
    }
}
