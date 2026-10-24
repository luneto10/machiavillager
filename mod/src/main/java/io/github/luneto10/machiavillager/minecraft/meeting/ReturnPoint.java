package io.github.luneto10.machiavillager.minecraft.meeting;

import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Where a player stood before a summons pulled them away, so the meeting can send them back to the same place. */
record ReturnPoint(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
    private static final String BACK_LINE = "You are back where you were.";

    /** Records the player's current place and facing. */
    static ReturnPoint of(ServerPlayer player) {
        return new ReturnPoint(
                (ServerLevel) player.level(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot());
    }

    /** Puts the player back where they stood, and tells only them. */
    void sendBack(ServerPlayer player) {
        // Teleport first, so the message arrives where the player really is now.
        player.teleportTo(level, x, y, z, Set.of(), yRot, xRot, true);
        player.sendSystemMessage(Component.literal(BACK_LINE));
    }
}
