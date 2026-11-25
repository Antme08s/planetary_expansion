package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente → servidor (1.0.4): "Bajar al planeta" en el mapa de la consola, con el transportador espacial. */
public record DescendRequestPayload(BlockPos console, String target) implements CustomPacketPayload {
    public static final Type<DescendRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "transporter_descend"));
    public static final StreamCodec<FriendlyByteBuf, DescendRequestPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeBlockPos(p.console);
                buf.writeUtf(p.target, 64);
            },
            buf -> new DescendRequestPayload(buf.readBlockPos(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
