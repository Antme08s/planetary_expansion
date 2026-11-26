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
 * <p>
 * v38 (prueba del usuario: los peces seguían pegados a la pared con la nave quieta): Sable solo calcula la IA de una
 * criatura dentro de la nave (caminos, sitios al azar a los que ir) si la criatura "pertenece" a la nave, y eso solo lo
 * sabe por lo que pisa. Un pez no pisa nada, así que buscaba agua en el mundo, donde no hay, y no se movía. Antes de
 * cada tick se le dice a Sable que el pez pertenece a la nave en cuya agua está, y su IA busca agua dentro de la nave.
 */
@EventBusSubscriber(modid = PlanetaryExpansion.MODID)
public final class ShipSwimmers {
    private ShipSwimmers() {}

    private static boolean failed;

    /** La nave en cuya agua está la criatura, o null. */
    private static SubLevel waterShip(Mob mob) {
        Level level = mob.level();
        Vec3 c = mob.getBoundingBox().getCenter();
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(mob.getBoundingBox()))) {
            if (sub.isRemoved()) continue;
            Vec3 local = sub.logicalPose().transformPositionInverse(c);
            if (!level.getFluidState(BlockPos.containing(local)).isEmpty()) return sub;
        }
        return null;
    }

    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (failed || !(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide || !mob.isInWater() || mob.isPassenger()) return;
        try {
            if (Sable.HELPER.getTrackingSubLevel((Entity) mob) != null) return;
            SubLevel sub = waterShip(mob);
            if (sub != null)
                ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) mob).sable$setTrackingSubLevel(sub);
        } catch (Throwable t) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo llevar a las criaturas que nadan en las naves (¿cambió Sable?)", t);
        }
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (failed || !(event.getEntity() instanceof Mob mob)) return;
        Level level = mob.level();
        if (level.isClientSide || !mob.isInWater() || mob.isPassenger()) return;
        try {
            // tras moverse, Sable deja de asociarla a la nave si no pisa nada: si sigue sin pisar y está en su agua, la llevamos
            if (Sable.HELPER.getTrackingSubLevel((Entity) mob) != null) return;   // apoyada: ya la lleva Sable
            SubLevel sub = waterShip(mob);
            if (sub == null) return;
            Vec3 local = sub.logicalPose().transformPositionInverse(mob.getBoundingBox().getCenter());
            // movimiento de la nave en este punto durante el último tick
            Vec3 d = sub.logicalPose().transformPosition(local).subtract(sub.lastPose().transformPosition(local));
            if (d.lengthSqr() < 1.0E-8 || d.lengthSqr() > 400) return;          // quieta, o un salto (teletransporte)
            mob.setPos(mob.position().add(d));
        } catch (Throwable t) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo llevar a las criaturas que nadan en las naves (¿cambió Sable?)", t);
        }
    }
}
