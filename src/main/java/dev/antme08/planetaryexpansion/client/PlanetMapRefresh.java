package dev.antme08.planetaryexpansion.client;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.client.SkyHandler;
import dev.devce.rocketnautics.network.PlanetMapRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Arreglo para Cosmonautics (0.7.11): el "planeta de abajo" que se ve desde lo alto se guarda en el cliente y solo
 * se vuelve a pedir al servidor cuando cambia la altura o te alejas de la zona. No mira la dimensión: si subes en la
 * Tierra y luego en otro planeta a la misma posición, se sigue viendo la Tierra (bug que vio el usuario en todos
 * los planetas). Al cambiar de dimensión pedimos la textura otra vez, así llega la del planeta en el que estás.
 */
public final class PlanetMapRefresh {
    private PlanetMapRefresh() {}

    private static ResourceKey<Level> lastDimension;
    private static int pendingTicks = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            lastDimension = null;
            pendingTicks = -1;
            return;
        }
        ResourceKey<Level> dim = mc.level.dimension();
        if (!dim.equals(lastDimension)) {
            lastDimension = dim;
            // un segundo para que el servidor termine de ponerte en la nueva dimensión. También al entrar a un
            // mundo: la textura vieja sigue en memoria aunque cambies de mundo.
            pendingTicks = 20;
        }
        if (pendingTicks > 0 && --pendingTicks == 0) {
            pendingTicks = -1;
            try {
                int size = Math.min(SkyDataHandler.targetSizeForHeight(mc.player.getY()), SkyHandler.getMaximumScale());
                PacketDistributor.sendToServer(new PlanetMapRequestPayload(size));
            } catch (Throwable t) {
                PlanetaryExpansion.LOGGER.warn("[Mapa] No se pudo pedir la textura del planeta tras cambiar de dimensión", t);
            }
        }
    }
}
