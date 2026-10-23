package io.github.luneto10.machiavillager.minecraft.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Makes an NPC "talk": a chat line formatted like a player's, heard by players nearby. */
public final class NpcSpeech {
    private static final double HEARING_RADIUS = 24.0;
    private static final double HEARING_RADIUS_SQR = HEARING_RADIUS * HEARING_RADIUS;

    private NpcSpeech() {}

    /** Server side only. Shows "<Name> text" to every player within hearing range. */
    public static void say(Entity speaker, String text) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        // "chat.type.text" is vanilla's "<%s> %s" format, so it looks exactly like player chat.
        Component line = Component.translatable("chat.type.text", speaker.getDisplayName(), text);
        for (var player : level.players()) {
            if (player.distanceToSqr(speaker) <= HEARING_RADIUS_SQR) {
                player.sendSystemMessage(line);
            }
        }
    }
}
