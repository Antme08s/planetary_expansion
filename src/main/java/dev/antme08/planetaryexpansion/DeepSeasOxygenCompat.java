package dev.antme08.planetaryexpansion;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

import java.util.EnumSet;

/**
 * Puente entre el aire de Cosmonautics y los difusores de oxígeno de Create Deep Seas.
 *
 * Cosmonautics (prioridad HIGHEST) marca canBreathe = false a toda entidad que esté en una
 * capa de atmósfera con la bandera "drowning" (Luna, Marte, Ignis, espacio...).
 *
 * Deep Seas considera "sellado" el interior de los compartimentos de una nave de Sable que
 * tiene un difusor de oxígeno activo, y ahí dentro anula el agua para que se pueda respirar.
 * Aquí aplicamos la misma regla al vacío: si los ojos de la entidad están dentro de un
 * compartimento sellado por Deep Seas, vuelve a poder respirar.
 *
 * Solo se actúa cuando la falta de aire viene de Cosmonautics (bandera "drowning"), así no
 * se interfiere con el agua normal ni con otros mods.
 */
public final class DeepSeasOxygenCompat {
    private DeepSeasOxygenCompat() {}

    /** Las naves con oxígeno de Deep Seas también protegen de los golpes de arena y del frío. */
    public static void installStormShelter() {
        dev.antme08.planetaryexpansion.weather.StormShelter.setSealedCheck(CompartmentTracker::isInSealedExact);
    }

    /**
     * HIGH: después de Cosmonautics (HIGHEST, quita el aire) y antes de Create (LOW), que gasta el aire
     * del traje cuando no se puede respirar. Así, dentro de una estructura con oxígeno el traje no gasta
     * aire (hasta 0.4.3 iba en LOW, después de Create, y el traje seguía gastando).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingBreathe(LivingBreatheEvent event) {
        if (event.canBreathe()) return;

        LivingEntity entity = event.getEntity();
        EnumSet<AtmosphereFlags> flags = GlobalSpacePhysicsHandler.getFlags(entity);
        if (flags == null || !flags.contains(AtmosphereFlags.DROWNING)) return;

        if (CompartmentTracker.isInSealedExact(entity.level(), entity.getEyePosition())) {
            event.setCanBreathe(true);
        }
    }
}
