package dev.antme08.planetaryexpansion.titan.client;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.titan.TitanBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;

/** Cliente de Titán: texturas, tinte y niebla del metano líquido. */
public final class TitanClient {
    private TitanClient() {}

    private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "block/methane_still");
    private static final ResourceLocation FLOW = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "block/methane_flow");

    public static void register(IEventBus modBus) {
        modBus.addListener(TitanClient::extensions);
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }

            /** Dentro del metano: oscuro y azul verdoso. */
            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                           float darkenWorldAmount, Vector3f fluidFogColor) {
                return new Vector3f(0.05f, 0.14f, 0.17f);
            }
        }, TitanBlocks.METHANE_TYPE.get());
    }
}
