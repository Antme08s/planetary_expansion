package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente: empieza (o se cancela) el efecto del salto para un jugador a bordo. */
public record WarpEffectPayload(int stage, String target, int altitudeKm) implements CustomPacketPayload {
    public static final int START = 0, ABORT = 1;
    public static final Type<WarpEffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "warp_effect"));
    public static final StreamCodec<FriendlyByteBuf, WarpEffectPayload> CODEC = StreamCodec.ofMember(WarpEffectPayload::write, WarpEffectPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(stage);
        buf.writeUtf(target, 64);
        buf.writeVarInt(altitudeKm);
    }

    private static WarpEffectPayload read(FriendlyByteBuf buf) {
        return new WarpEffectPayload(buf.readVarInt(), buf.readUtf(64), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
