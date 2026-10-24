package io.github.luneto10.machiavillager.domain.bridge;

/** Sent by the backend in answer to hello when the protocol version is supported. */
public record Welcome() implements BridgeMessage {
}
