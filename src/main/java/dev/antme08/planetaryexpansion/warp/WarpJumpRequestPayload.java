package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente → servidor: "Viajar (orbitar)" en el mapa de la consola. altitudeKm: distancia de la órbita (<= 0: la de siempre). */
public record WarpJumpRequestPayload(BlockPos console, String target, double altitudeKm) implements CustomPacketPayload {
    public static final Type<WarpJumpRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "warp_jump"));
    public static final StreamCodec<FriendlyByteBuf, WarpJumpRequestPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeBlockPos(p.console);
                buf.writeUtf(p.target, 64);
                buf.writeDouble(p.altitudeKm);
            },
            buf -> new WarpJumpRequestPayload(buf.readBlockPos(), buf.readUtf(64), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
