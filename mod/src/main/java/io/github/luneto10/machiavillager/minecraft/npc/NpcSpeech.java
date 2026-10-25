package io.github.luneto10.machiavillager.minecraft.npc;

import io.github.luneto10.machiavillager.domain.behavior.Hearing;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Makes an NPC "talk" or "mutter": chat lines heard by players within a range. */
public final class NpcSpeech {
    private static final double HEARING_RADIUS_SQR = Hearing.DISTANCE * Hearing.DISTANCE;
    private static final double WHISPER_RADIUS_SQR = Hearing.WHISPER_DISTANCE * Hearing.WHISPER_DISTANCE;

    private NpcSpeech() {}

    /** Server side only. Shows "<Name> text" to every player within hearing range. */
    public static void say(Entity speaker, String text) {
        // "chat.type.text" is vanilla's "<%s> %s" format, so it looks exactly like player chat.
        Component line = Component.translatable("chat.type.text", speaker.getDisplayName(), text);
        sendWithin(speaker, HEARING_RADIUS_SQR, line);
    }

    /** Server side only. Shows a gray italic "Name mutters: text" only to players very close to him. */
    public static void whisper(Entity speaker, String text) {
        Component line = Component.literal(speaker.getDisplayName().getString() + " mutters: " + text)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        sendWithin(speaker, WHISPER_RADIUS_SQR, line);
    }

    // One loop for every kind of speech: only the range and the formatting differ.
    private static void sendWithin(Entity speaker, double radiusSqr, Component line) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        for (var player : level.players()) {
            if (player.distanceToSqr(speaker) <= radiusSqr) {
                player.sendSystemMessage(line);
            }
        }
    }
}
