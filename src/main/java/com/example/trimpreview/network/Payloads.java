package com.example.trimpreview.network;

import com.example.trimpreview.TrimPreviewMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class Payloads {
    private Payloads() {}

    /** Client -> server: "open the preview menu for me". No data. */
    public record OpenMenu() implements CustomPayload {
        public static final CustomPayload.Id<OpenMenu> ID =
                new CustomPayload.Id<>(Identifier.of(TrimPreviewMod.MOD_ID, "open_menu"));
        public static final PacketCodec<ByteBuf, OpenMenu> CODEC = PacketCodec.unit(new OpenMenu());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: "apply this pattern + material to the armor in the input slot". */
    public record ApplyTrim(Identifier pattern, Identifier material) implements CustomPayload {
        public static final CustomPayload.Id<ApplyTrim> ID =
                new CustomPayload.Id<>(Identifier.of(TrimPreviewMod.MOD_ID, "apply_trim"));
        public static final PacketCodec<ByteBuf, ApplyTrim> CODEC = PacketCodec.tuple(
                Identifier.PACKET_CODEC, ApplyTrim::pattern,
                Identifier.PACKET_CODEC, ApplyTrim::material,
                ApplyTrim::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
