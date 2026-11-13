package dev.antme08.planetaryexpansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Sonido en el espacio profundo (1.0.3). Cosmonautics ({@code SkyHandler.onPlaySound}) silencia todos los sonidos
 * menos el viento cuando la cámara está entre y = 1000 y 2000, pensado para la capa de viento fuerte de la atmósfera
 * de la Tierra. Pero también lo hace en el espacio profundo, donde las naves suelen quedar justo a esa altura
 * (~1730), y entonces no se oía nada al entrar al mundo. Aquí, solo en el espacio profundo y solo en esa franja,
 * se devuelve el sonido original. En los planetas no se toca nada.
 */
public final class DeepSpaceSound {
    private DeepSpaceSound() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() != null) return;
        SoundInstance original = event.getOriginalSound();
        if (original == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.level.dimension().location().toString().equals("rocketnautics:deep_space")) return;
        double y = mc.gameRenderer.getMainCamera().getPosition().y;
        if (y < 1000.0 || y >= 2000.0) return;   // solo deshace lo que silencia Cosmonautics en esa franja
        event.setSound(original);
    }
}
