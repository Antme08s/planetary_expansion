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

    /** Adónde nada cada criatura dentro del agua de una nave (en coordenadas de la nave) y hasta cuándo. */
    private record Swim(SubLevel ship, Vec3 target, int until) {}
    private static final java.util.Map<Mob, Swim> SWIM = new java.util.WeakHashMap<>();

    /**
     * v40: la IA normal de los peces no sirve dentro de una nave (busca agua en el mundo, o con la nave de seguimiento
     * apunta a coordenadas internas de la nave, que están a millones de bloques, y los empuja contra una pared). Dentro
     * del agua de una nave se apaga su navegación y nadan con un paseo propio: eligen un punto de agua de la nave a
     * pocos bloques con el camino recto por agua, nadan hacia él, descansan un poco y eligen otro.
     */
    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (failed || !(event.getEntity() instanceof net.minecraft.world.entity.animal.WaterAnimal mob)) return;
        if (mob.level().isClientSide || !mob.isInWater() || mob.isPassenger()) return;
        try {
            if (waterShip(mob) == null) {
                SWIM.remove(mob);
                return;
            }
            mob.getNavigation().stop();
        } catch (Throwable t) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo llevar a las criaturas que nadan en las naves (¿cambió Sable?)", t);
        }
    }

    /** Punto de agua de la nave al que nadar desde {@code from} (coordenadas de la nave), o null. */
    private static Vec3 pickTarget(Level level, Vec3 from, net.minecraft.util.RandomSource random) {
        for (int i = 0; i < 12; i++) {
            BlockPos b = BlockPos.containing(from.add(random.nextInt(11) - 5, random.nextInt(5) - 2, random.nextInt(11) - 5));
            if (!level.getFluidState(b).is(net.minecraft.tags.FluidTags.WATER)) continue;
            Vec3 t = Vec3.atCenterOf(b);
            boolean clear = true;
            for (int k = 1; k <= 6 && clear; k++) {
                BlockPos s = BlockPos.containing(from.lerp(t, k / 6.0));
                clear = level.getFluidState(s).is(net.minecraft.tags.FluidTags.WATER);
            }
            if (clear && t.distanceToSqr(from) > 1.0) return t;
        }
        return null;
    }

    private static void swim(Mob mob, SubLevel ship) {
        Level level = mob.level();
        Vec3 center = mob.getBoundingBox().getCenter();
        Vec3 local = ship.logicalPose().transformPositionInverse(center);
        Swim s = SWIM.get(mob);
        if (s == null || s.ship() != ship || mob.tickCount > s.until()
                || (s.target() != null && s.target().distanceToSqr(local) < 0.36)) {
            boolean rest = s != null && s.target() != null && mob.getRandom().nextInt(3) == 0;
            Vec3 t = rest ? null : pickTarget(level, local, mob.getRandom());
            s = new Swim(ship, t, mob.tickCount + (t == null ? 20 + mob.getRandom().nextInt(40) : 100));
            SWIM.put(mob, s);
        }
        if (s.target() == null) return;
        Vec3 d = ship.logicalPose().transformPosition(s.target()).subtract(center);
        double len = d.length();
        if (len < 1.0E-3) return;
        double speed = 0.012 * mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / 0.1;
        Vec3 v = mob.getDeltaMovement().add(d.scale(Math.min(speed, len) / len));
        double max = 0.12;
        if (v.lengthSqr() > max * max) v = v.normalize().scale(max);
        mob.setDeltaMovement(v);
        float yaw = (float) (net.minecraft.util.Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90.0F;
        mob.setYRot(net.minecraft.util.Mth.approachDegrees(mob.getYRot(), yaw, 12.0F));
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.getYRot();
    }

    /**
     * Para encontrar el fallo de los peces (v39): cada 5 s, por cada pez cerca de una nave, una línea en el registro con
     * su estado (agua, nave de seguimiento, camino, movimiento).
     */
    private static void debug(Mob mob, String when) {
        if (!(mob instanceof net.minecraft.world.entity.animal.WaterAnimal) || (mob.tickCount + mob.getId()) % 100 != 0) return;
        Level level = mob.level();
        boolean nearShip = false;
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(mob.getBoundingBox().inflate(2)))) nearShip |= !sub.isRemoved();
        if (!nearShip) return;
        SubLevel water = null;
        try { water = waterShip(mob); } catch (Throwable ignored) {}
        var nav = mob.getNavigation();
        var path = nav.getPath();
        PlanetaryExpansion.LOGGER.info("[Peces] {} {} pos={} agua={} ojos={} naveAgua={} seguimiento={} navTerminada={} destino={} camino={} vel={} choque={} suelo={} sinAcción={}",
                when, mob.getType().getDescriptionId(), mob.position(), mob.isInWater(),
                mob.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), water != null,
                Sable.HELPER.getTrackingSubLevel((Entity) mob) != null, nav.isDone(), nav.getTargetPos(),
                path == null ? "-" : (path.getNodeCount() + " nodos, fin " + path.getTarget() + (path.canReach() ? "" : " (no llega)")),
                mob.getDeltaMovement(), mob.horizontalCollision, mob.onGround(), mob.getNoActionTime() + " paseo=" + (SWIM.get(mob) == null ? "-" : SWIM.get(mob).target()));
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (failed || !(event.getEntity() instanceof Mob mob)) return;
        Level level = mob.level();
        if (!level.isClientSide) try { debug(mob, "post"); } catch (Throwable ignored) {}
        if (level.isClientSide || !mob.isInWater() || mob.isPassenger()) return;
        try {
            // tras moverse, Sable deja de asociarla a la nave si no pisa nada: si sigue sin pisar y está en su agua, la llevamos
            SubLevel sub = waterShip(mob);
            if (sub == null) return;
            if (mob instanceof net.minecraft.world.entity.animal.WaterAnimal) swim(mob, sub);
            if (Sable.HELPER.getTrackingSubLevel((Entity) mob) != null) return;   // apoyada: ya la lleva Sable
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
