package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor → cliente: estado de la tormenta de una dimensión.
 * windPush = multiplicador de empuje sobre el jugador (config del servidor), porque el movimiento
 * del jugador se calcula en su propio cliente.
 */
public record StormSyncPayload(ResourceLocation dimension, float intensity, float windAngle, float windPush,
                               boolean severe)
        implements CustomPacketPayload {

    public static final Type<StormSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "storm_sync"));

    public static final StreamCodec<ByteBuf, StormSyncPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, StormSyncPayload::dimension,
            ByteBufCodecs.FLOAT, StormSyncPayload::intensity,
            ByteBufCodecs.FLOAT, StormSyncPayload::windAngle,
            ByteBufCodecs.FLOAT, StormSyncPayload::windPush,
            ByteBufCodecs.BOOL, StormSyncPayload::severe,
            StormSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
