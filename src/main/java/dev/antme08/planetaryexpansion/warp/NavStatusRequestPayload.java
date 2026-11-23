package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente → servidor: el mapa de la consola pide el estado del motor (cada medio segundo mientras está abierto).
 * {@code target} (1.0.4): astro al que se piensa viajar ("" si ninguno); con varios motores en la nave, el estado que se
 * devuelve es el del motor que se usaría para ese destino.
 */
public record NavStatusRequestPayload(BlockPos console, String target) implements CustomPacketPayload {
    public static final Type<NavStatusRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "nav_status_request"));
    public static final StreamCodec<FriendlyByteBuf, NavStatusRequestPayload> CODEC =
            StreamCodec.ofMember((p, buf) -> {
                buf.writeBlockPos(p.console);
                buf.writeUtf(p.target, 64);
            }, buf -> new NavStatusRequestPayload(buf.readBlockPos(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
