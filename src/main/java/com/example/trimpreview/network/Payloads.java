package com.example.trimpreview.network;

import com.example.trimpreview.TrimPreviewMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Payloads {
    private Payloads() {}

    /** Client -> server: "open the preview menu for me". No data. */
    public record OpenMenu() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenMenu> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TrimPreviewMod.MOD_ID, "open_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.unit(new OpenMenu());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: "apply this pattern + material to the armor in the input slot". */
    public record ApplyTrim(Identifier pattern, Identifier material) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ApplyTrim> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TrimPreviewMod.MOD_ID, "apply_trim"));
        public static final StreamCodec<ByteBuf, ApplyTrim> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, ApplyTrim::pattern,
                Identifier.STREAM_CODEC, ApplyTrim::material,
                ApplyTrim::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
