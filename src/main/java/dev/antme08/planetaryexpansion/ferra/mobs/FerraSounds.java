package dev.antme08.planetaryexpansion.ferra.mobs;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sonidos propios del gusano de óxido y el rastreador ciego (1.0.3). Sintetizados con tools/designs/ferra_sounds.py. */
public final class FerraSounds {
    private FerraSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PlanetaryExpansion.MODID);

    private static DeferredHolder<SoundEvent, SoundEvent> s(String name) {
        return SOUNDS.register("entity." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "entity." + name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_DIG = s("rust_worm.dig");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_EMERGE = s("rust_worm.emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_AMBIENT = s("rust_worm.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_BITE = s("rust_worm.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_HURT = s("rust_worm.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_DEATH = s("rust_worm.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORM_DIVE = s("rust_worm.dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_AMBIENT = s("blind_stalker.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_LISTEN = s("blind_stalker.listen");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_SCREAM = s("blind_stalker.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_ATTACK = s("blind_stalker.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_HURT = s("blind_stalker.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> STALKER_DEATH = s("blind_stalker.death");

    public static void register(IEventBus modBus) { SOUNDS.register(modBus); }
}
