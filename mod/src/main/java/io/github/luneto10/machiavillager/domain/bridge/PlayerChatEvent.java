package io.github.luneto10.machiavillager.domain.bridge;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The mod reports that a player spoke in normal chat. No reply is expected from the mod's side.
 *
 * @param eventId the event id, prefixed evt_
 * @param occurredAt when the chat happened, in UTC
 * @param playerUuid the speaker's UUID
 * @param playerName the speaker's name, 1 to 16 characters
 * @param text what was said, 1 to 256 characters
 * @param conversationId optional conversation this message belongs to
 */
public record PlayerChatEvent(
        String eventId,
        Instant occurredAt,
        UUID playerUuid,
        String playerName,
        String text,
        Optional<String> conversationId) implements BridgeMessage {

    /** Validates every field against the contract limits. */
    public PlayerChatEvent {
        eventId = ProtocolRules.requireEventId(eventId);
        Objects.requireNonNull(occurredAt, "occurred_at");
        Objects.requireNonNull(playerUuid, "player_uuid");
        playerName = ProtocolRules.requireText(
                playerName, 1, ProtocolRules.MAX_PLAYER_NAME_LENGTH, "player_name");
        text = ProtocolRules.requireText(text, 1, ProtocolRules.MAX_CHAT_TEXT_LENGTH, "text");
        conversationId = ProtocolRules.requireConversationId(conversationId);
    }
}
