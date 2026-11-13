package dev.antme08.planetaryexpansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundInstances;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Sin los sonidos "de ballena" del mar de la Tierra en Thalassa (0.7.58) y en todos los demás planetas salvo la
 * Tierra (0.7.59), pedido por el usuario. Bajo el agua,
 * Minecraft añade al azar {@code ambient.underwater.loop.additions.rare} y {@code .ultra_rare} (cantos lejanos); en
 * Thalassa sonaban a cada rato y no encajan. Se quitan solo esos, y solo los del ambiente de Minecraft
 * ({@code UnderwaterAmbientSoundInstances.SubSound}): el murmullo del mar y las burbujas se quedan, y el canto con el
 * que aparece el leviatán (un {@code SimpleSoundInstance}) también.
 */
public final class ThalassaAmbience {
    private ThalassaAmbience() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (!(sound instanceof UnderwaterAmbientSoundInstances.SubSound)) return;
        String path = sound.getLocation().getPath();
        if (!path.equals("ambient.underwater.loop.additions.rare") && !path.equals("ambient.underwater.loop.additions.ultra_rare")) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // 0.7.59: en todos los planetas (los del mod y los de Cosmonautics), no solo en Thalassa; la Tierra no cambia
        String ns = level.dimension().location().getNamespace();
        if (ns.equals("planetary_expansion") || ns.equals("rocketnautics")) event.setSound(null);
    }
}
