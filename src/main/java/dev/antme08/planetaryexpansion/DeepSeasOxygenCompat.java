package dev.antme08.planetaryexpansion;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

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
        dev.antme08.planetaryexpansion.weather.StormShelter.setSealedCheck(DeepSeasOxygenCompat::sealed);
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

        if (sealed(entity.level(), entity.getEyePosition())) {
            event.setCanBreathe(true);
        }
    }

    /**
     * ¿Está este punto dentro de un compartimento con oxígeno?
     * <p>
     * 1.0.4.1: Deep Seas trata cada bloque con datos (copycats, etc.) como pared entera aunque sea un panel o un
     * escalón, así que si la cabeza de un jugador o de un animal entra en el hueco de uno (pegado a la pared o en una
     * esquina), ese hueco no cuenta como interior y se ahoga. Si el punto no está en el interior, se mira el hueco
     * vecino del mismo bloque de la nave por el lado donde está la cabeza: si ese es interior, se respira. Desde fuera
     * de la nave, el lado de la cabeza da al exterior, así que no se cuela aire.
     */
    public static boolean sealed(Level level, Vec3 p) {
        if (CompartmentTracker.isInSealedExact(level, p)) return true;
        try {
            for (Map.Entry<UUID, SubLevelAccess> e : CompartmentTracker.getSubsSnapshot().entrySet()) {
                SubLevelAccess sub = e.getValue();
                if (sub instanceof dev.ryanhcode.sable.sublevel.SubLevel s && s.getLevel() != level) continue;
                AABB box = CompartmentTracker.getWorldAABB(e.getKey());
                if (box == null || !box.inflate(1.5).contains(p)) continue;
                Pose3dc pose = sub.logicalPose();
                Vec3 local = pose.transformPositionInverse(p);
                Vec3 c = Vec3.atCenterOf(BlockPos.containing(local));
                Vec3 d = local.subtract(c);
                Direction dir = Direction.getNearest(d.x, d.y, d.z);
                Vec3 n = c.add(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                if (CompartmentTracker.isInSealedExact(level, pose.transformPosition(n))) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
