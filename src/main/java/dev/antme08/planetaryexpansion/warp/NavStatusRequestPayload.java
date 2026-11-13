package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente → servidor: el mapa de la consola pide el estado del motor (cada medio segundo mientras está abierto). */
public record NavStatusRequestPayload(BlockPos console) implements CustomPacketPayload {
    public static final Type<NavStatusRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "nav_status_request"));
    public static final StreamCodec<FriendlyByteBuf, NavStatusRequestPayload> CODEC =
            StreamCodec.ofMember((p, buf) -> buf.writeBlockPos(p.console), buf -> new NavStatusRequestPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
