package io.github.luneto10.machiavillager.minecraft.notice;

import io.github.luneto10.machiavillager.Machiavillager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A notice the server asks the client to show: what to say and for how long. The client decides how it looks.
 * A duration of {@link #HIDE_DURATION_TICKS} hides the current notice at once.
 */
public record NoticePayload(NoticeStyle style, String title, String body, int durationTicks)
        implements CustomPacketPayload {
    /** Sent with zero duration: the client drops the notice it is showing. */
    public static final int HIDE_DURATION_TICKS = 0;

    /** Packet id, shared by the server sender and the client receiver. */
    public static final CustomPacketPayload.Type<NoticePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Machiavillager.MOD_ID, "notice"));

    /** Wire format: style as its ordinal, then the two texts, then the duration. Both ends run this mod version. */
    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(index -> NoticeStyle.values()[index], NoticeStyle::ordinal),
            NoticePayload::style,
            ByteBufCodecs.STRING_UTF8,
            NoticePayload::title,
            ByteBufCodecs.STRING_UTF8,
            NoticePayload::body,
            ByteBufCodecs.VAR_INT,
            NoticePayload::durationTicks,
            NoticePayload::new);

    /** A notice that clears whatever the client is showing. */
    public static NoticePayload hide() {
        // The style and texts are unused on hide; they only need to be valid for the codec.
        return new NoticePayload(NoticeStyle.SUMMON, "", "", HIDE_DURATION_TICKS);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
