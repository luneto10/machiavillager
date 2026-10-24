package io.github.luneto10.machiavillager.domain.bridge;

/** Sent by the mod after connecting. The protocol version is implied: this mod speaks only v1. */
public record Hello(String modVersion) implements BridgeMessage {

    /** Keeps the mod version within the contract's 1 to 32 character limit. */
    public Hello {
        modVersion = ProtocolRules.requireText(
                modVersion, 1, ProtocolRules.MAX_MOD_VERSION_LENGTH, "mod_version");
    }
}
