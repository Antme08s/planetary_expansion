package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente: aceleración que están dando los motores a la órbita de tu instancia (m/s², ejes del mundo). */
public record ThrustPayload(float ax, float ay, float az) implements CustomPacketPayload {
    public static final Type<ThrustPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "thrust"));

    public static final StreamCodec<ByteBuf, ThrustPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ThrustPayload::ax,
            ByteBufCodecs.FLOAT, ThrustPayload::ay,
            ByteBufCodecs.FLOAT, ThrustPayload::az,
            ThrustPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
