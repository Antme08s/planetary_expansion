package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor → cliente: estado del motor de la nave de la consola. {@code orbit}: cuerpo que se orbita ahora ("" si no se sabe); {@code stellar}: motor estelar (1.0.3). */
public record NavStatusPayload(BlockPos console, int status, float charge, float heat, int nitrogen, float rpm, String orbit,
                               boolean stellar)
        implements CustomPacketPayload {
    public static final Type<NavStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "nav_status"));
    public static final StreamCodec<FriendlyByteBuf, NavStatusPayload> CODEC = StreamCodec.ofMember(NavStatusPayload::write, NavStatusPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(console);
        buf.writeVarInt(status);
        buf.writeFloat(charge);
        buf.writeFloat(heat);
        buf.writeVarInt(nitrogen);
        buf.writeFloat(rpm);
        buf.writeUtf(orbit, 64);
        buf.writeBoolean(stellar);
    }

    private static NavStatusPayload read(FriendlyByteBuf buf) {
        return new NavStatusPayload(buf.readBlockPos(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readFloat(), buf.readUtf(64), buf.readBoolean());
    }

    public WarpStatus warpStatus() {
        return WarpStatus.byId(status);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
