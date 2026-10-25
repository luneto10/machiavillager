package io.github.luneto10.machiavillager.domain.bridge;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The mod reports that a player spoke. No reply is expected from the mod's side.
 *
 * @param eventId the event id, prefixed evt_
 * @param occurredAt when the chat happened, in UTC
 * @param playerUuid the speaker's UUID
 * @param playerName the speaker's name
 * @param text what was said
 * @param addressed why the mod forwarded the line
 * @param conversationId optional conversation this message belongs to
 */
public record PlayerChatEvent(
        String eventId,
        Instant occurredAt,
        UUID playerUuid,
        String playerName,
        String text,
        Addressed addressed,
        Optional<String> conversationId) implements BridgeMessage {

    /** Null checks only; the limits are enforced by the schema. */
    public PlayerChatEvent {
        Objects.requireNonNull(eventId, "event_id");
        Objects.requireNonNull(occurredAt, "occurred_at");
        Objects.requireNonNull(playerUuid, "player_uuid");
        Objects.requireNonNull(playerName, "player_name");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(addressed, "addressed");
        Objects.requireNonNull(conversationId, "conversation_id");
    }
}
