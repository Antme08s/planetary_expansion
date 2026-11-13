package dev.antme08.planetaryexpansion.client;

import dev.antme08.planetaryexpansion.armor.ThermalSuit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 0.7.64: con el casco térmico se ve un poco dentro de la lava (Minecraft deja ~1 bloque, o 5 con resistencia al
 * fuego). Casco solo: 7 bloques; traje completo: 12.
 */
public final class ThermalLavaVision {
    private ThermalLavaVision() {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.LAVA) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !ThermalSuit.wearsHelmet(player)) return;
        float far = ThermalSuit.pieces(player) >= 4 ? 12f : 7f;
        if (event.getFarPlaneDistance() >= far) return;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}
