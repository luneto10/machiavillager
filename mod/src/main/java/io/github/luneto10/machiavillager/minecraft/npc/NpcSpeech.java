package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.domain.behavior.Hearing;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Makes an NPC "talk", "mutter" or whisper privately. Lines go out as disguised chat: the client renders them
 * like a player's chat (no gray system-message bar), with his name from the chat type decoration.
 */
public final class NpcSpeech {
    private static final double HEARING_RADIUS_SQR = Hearing.DISTANCE * Hearing.DISTANCE;
    private static final double WHISPER_RADIUS_SQR = Hearing.WHISPER_DISTANCE * Hearing.WHISPER_DISTANCE;

    private static final String MUTTER_PREFIX = "mutters: ";

    // Chat messages are never filtered for profanity here; the backend owns that setting (#59).
    private static final boolean NOT_FILTERED = false;

    private NpcSpeech() {}

    /** Server side only. Shows "<Name> text" to every player within hearing range, like player chat. */
    public static void say(Entity speaker, String text) {
        // ChatType.CHAT decorates with vanilla's "<%s> %s", so it looks exactly like a player talking.
        sendWithin(speaker, HEARING_RADIUS_SQR, ChatType.CHAT, Component.literal(text));
    }

    /** Server side only. Shows "* Name mutters: text" in gray italic only to players very close to him. */
    public static void whisper(Entity speaker, String text) {
        // The emote format ("* Name ...") reads as something he does, not something he says to you.
        Component muttered = Component.literal(MUTTER_PREFIX + text)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        sendWithin(speaker, WHISPER_RADIUS_SQR, ChatType.EMOTE_COMMAND, muttered);
    }

    /**
     * Server side only. Shows vanilla's private-message line "Name whispers to you: text" to one player, at any
     * distance. Never voice.
     */
    public static void tellPrivately(Entity speaker, ServerPlayer player, String text) {
        // The incoming /msg chat type gives the gray italic private look players already know.
        send(player, ChatType.bind(ChatType.MSG_COMMAND_INCOMING, speaker), Component.literal(text));
    }

    // One loop for every kind of nearby speech: only the range, the chat type and the content differ.
    private static void sendWithin(Entity speaker, double radiusSqr, ResourceKey<ChatType> type, Component content) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        ChatType.Bound bound = ChatType.bind(type, speaker);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= radiusSqr) {
                send(player, bound, content);
            }
        }
    }

    // A system (unsigned) message is sent as disguised chat: rendered like chat, with no system-message bar.
    private static void send(ServerPlayer player, ChatType.Bound bound, Component content) {
        PlayerChatMessage message = PlayerChatMessage.system(content.getString()).withUnsignedContent(content);
        player.sendChatMessage(OutgoingChatMessage.create(message), NOT_FILTERED, bound);
    }
}
