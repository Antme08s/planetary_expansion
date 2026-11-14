package dev.antme08.planetaryexpansion.cerulea.mobs;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sonidos propios de las criaturas de Cerúlea (1.0.3; el usuario pidió que sonaran más alienígenas que la vaca, el
 * lobo y el conejo). Sintetizados con tools/designs/cerulea_sounds.py; archivos en sounds/cerulea/&lt;criatura&gt;/.
 */
public final class CeruleaSounds {
    private CeruleaSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PlanetaryExpansion.MODID);

    private static DeferredHolder<SoundEvent, SoundEvent> s(String name) {
        return SOUNDS.register("entity." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "entity." + name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> GRAZER_AMBIENT = s("grazer.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAZER_HURT = s("grazer.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAZER_DEATH = s("grazer.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOPPER_AMBIENT = s("hopper.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOPPER_HURT = s("hopper.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOPPER_DEATH = s("hopper.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOPPER_HOP = s("hopper.hop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLIDER_AMBIENT = s("glider.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLIDER_HURT = s("glider.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLIDER_DEATH = s("glider.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLIDER_GLIDE = s("glider.glide");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_AMBIENT = s("lurker.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_HURT = s("lurker.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_DEATH = s("lurker.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> LURKER_ATTACK = s("lurker.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROAKER_CROAK = s("croaker.croak");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROAKER_HURT = s("croaker.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CROAKER_DEATH = s("croaker.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNNER_AMBIENT = s("runner.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNNER_GROWL = s("runner.growl");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNNER_HURT = s("runner.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNNER_DEATH = s("runner.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUNNER_ATTACK = s("runner.attack");

    public static final DeferredHolder<SoundEvent, SoundEvent> IRONJAW_AMBIENT = s("ironjaw.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> IRONJAW_HURT = s("ironjaw.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> IRONJAW_DEATH = s("ironjaw.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> IRONJAW_CHARGE = s("ironjaw.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> IRONJAW_BITE = s("ironjaw.bite");

    public static void register(IEventBus modBus) { SOUNDS.register(modBus); }
}
