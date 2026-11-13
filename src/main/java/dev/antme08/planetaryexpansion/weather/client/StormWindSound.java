package dev.antme08.planetaryexpansion.weather.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Sonido de viento en bucle (sonido de vuelo de élitros de Minecraft), con volumen según la tormenta. */
final class StormWindSound extends AbstractTickableSoundInstance {
    private final float basePitch;

    StormWindSound(float basePitch) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
        this.basePitch = basePitch;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.pitch = basePitch;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float shown = ClientStormEffects.shown();
        if (shown < 0.01f) {
            stop();
            return;
        }
        float v = shown * (0.65f + 0.35f * ClientStormEffects.shownFog()) * (ClientStormEffects.playerSheltered() ? 0.35f : 0.9f);
        this.volume = Math.max(0.01f, v);
        this.pitch = basePitch + shown * 0.15f;
    }
}
