package io.github.luneto10.machiavillager.minecraft.speech;

import io.github.luneto10.machiavillager.Machiavillager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One line of NPC speech for the client to show as chat. Players with the mod get it through this payload, so the
 * client can add it without the gray system-message bar. Vanilla players never receive it.
 */
public record NpcChatPayload(Component line) implements CustomPacketPayload {
    /** Packet id, shared by the server sender and the client receiver. */
    public static final CustomPacketPayload.Type<NpcChatPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Machiavillager.MOD_ID, "npc_chat"));

    /** Wire format: one chat component, written with vanilla's component codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcChatPayload> CODEC =
            StreamCodec.composite(ComponentSerialization.STREAM_CODEC, NpcChatPayload::line, NpcChatPayload::new);

    /** Registers the payload type. Call once from the common entrypoint, before any player joins. */
    public static void register() {
        // Must be registered on both ends before the server can send it.
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
