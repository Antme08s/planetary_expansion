package dev.antme08.planetaryexpansion.transporter;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 1.0.4.1: peces (y cualquier criatura) nadando en el agua de una nave en movimiento.
 * <p>
 * Sable arrastra con la nave a lo que está apoyado en sus bloques, pero lo que nada en su agua no toca nada: la nave
 * se mueve y la criatura se queda quieta en el mundo, así que la pared del acuario la empuja y queda pegada a ella.
 * Aquí, a cada criatura que está en el agua de una nave sin estar apoyada en ella se le aplica el mismo movimiento que
 * hizo la nave en ese punto durante el tick (de la pose anterior a la actual), como si el agua la llevara.
 */
@EventBusSubscriber(modid = PlanetaryExpansion.MODID)
public final class ShipSwimmers {
    private ShipSwimmers() {}

    private static boolean failed;

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (failed || !(event.getEntity() instanceof Mob mob)) return;
        Level level = mob.level();
        if (level.isClientSide || !mob.isInWater() || mob.isPassenger()) return;
        try {
            if (Sable.HELPER.getTrackingSubLevel((Entity) mob) != null) return;   // apoyada: ya la lleva Sable
            Vec3 c = mob.getBoundingBox().getCenter();
            for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(mob.getBoundingBox()))) {
                if (sub.isRemoved()) continue;
                Vec3 local = sub.logicalPose().transformPositionInverse(c);
                if (level.getFluidState(BlockPos.containing(local)).isEmpty()) continue;
                // movimiento de la nave en este punto durante el último tick
                Vec3 before = sub.lastPose().transformPosition(local);
                Vec3 now = sub.logicalPose().transformPosition(local);
                Vec3 d = now.subtract(before);
                if (d.lengthSqr() < 1.0E-8 || d.lengthSqr() > 400) return;      // quieta, o un salto (teletransporte)
                mob.setPos(mob.position().add(d));
                return;
            }
        } catch (Throwable t) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo llevar a las criaturas que nadan en las naves (¿cambió Sable?)", t);
        }
    }
}
