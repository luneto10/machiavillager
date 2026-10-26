package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.domain.behavior.Hearing;
import io.github.luneto10.machiavillager.minecraft.speech.NpcChatPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Makes an NPC talk, mutter or whisper. Each line is built as a styled chat component and sent to the players who
 * should hear it: players with the mod get it as a payload the client draws as chat, the rest get a system message.
 */
public final class NpcSpeech {
    private static final double HEARING_RADIUS_SQR = Hearing.DISTANCE * Hearing.DISTANCE;
    private static final double WHISPER_RADIUS_SQR = Hearing.WHISPER_DISTANCE * Hearing.WHISPER_DISTANCE;

    // The emote form reads as something he does, not something he says to you.
    private static final String MUTTER_OPEN = "* ";
    private static final String MUTTER_VERB = " mutters: ";

    // Vanilla keys: chat.type.text is "<%s> %s" and commands.message.display.incoming is "%s whispers to you: %s".
    private static final String CHAT_KEY = "chat.type.text";
    private static final String PRIVATE_KEY = "commands.message.display.incoming";

    private NpcSpeech() {}

    /** Server side only. Shows "<Name> text" to every player within hearing range, like player chat. */
    public static void say(Entity speaker, String text) {
        Component line = Component.translatable(CHAT_KEY, speaker.getDisplayName(), text);
        sendWithin(speaker, HEARING_RADIUS_SQR, line);
    }

    /** Server side only. Shows "* Name mutters: text" in gray italic only to players very close to him. */
    public static void whisper(Entity speaker, String text) {
        Component muttered = Component.empty()
                .append(MUTTER_OPEN)
                .append(speaker.getDisplayName())
                .append(MUTTER_VERB + text)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        sendWithin(speaker, WHISPER_RADIUS_SQR, muttered);
    }

    /**
     * Server side only. Shows "Name whispers to you: text" in gray italic to one player, at any distance. Never
     * voice.
     */
    public static void tellPrivately(Entity speaker, ServerPlayer player, String text) {
        Component whisper = Component.translatable(PRIVATE_KEY, speaker.getDisplayName(), text)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        send(player, whisper);
    }

    /** Server side only. Shows the text on the action bar, above the hotbar, to players who can hear him. Dev only. */
    public static void showOverlayNear(Entity speaker, String text) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        Component line = Component.literal(text);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= HEARING_RADIUS_SQR) {
                // The action bar is the bar above the hotbar, not chat: the same players hear him, nobody else sees it.
                player.sendOverlayMessage(line);
            }
        }
    }

    // One loop for every kind of nearby speech: only the range and the line differ.
    private static void sendWithin(Entity speaker, double radiusSqr, Component line) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(speaker) <= radiusSqr) {
                send(player, line);
            }
        }
    }

    // Players with the mod get the payload; vanilla players get a system message, which shows the gray bar.
    private static void send(ServerPlayer player, Component line) {
        if (ServerPlayNetworking.canSend(player, NpcChatPayload.TYPE)) {
            ServerPlayNetworking.send(player, new NpcChatPayload(line));
            return;
        }
        player.sendSystemMessage(line);
    }
}
