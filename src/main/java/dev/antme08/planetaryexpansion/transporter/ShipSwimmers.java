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

    /**
     * Paseo de una criatura en el agua de una nave, todo en coordenadas de la nave: dónde está, adónde va y hasta cuándo.
     * {@code last} es la posición del mundo donde se la dejó el tick anterior, para notar si algo la movió (un golpe).
     */
    private static final class Swim {
        SubLevel ship;
        Vec3 pos, target, last;
        int until;
    }
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

    /**
     * v41: en v40 los peces intentaban nadar pero algo los seguía empujando contra la pared del acuario (lo que Sable
     * hace con las criaturas dentro de su agua). Ahora el paseo no usa la física: la posición del pez se lleva en
     * coordenadas de la nave, se avanza hacia el objetivo a velocidad de pez y cada tick se coloca al pez en el punto
     * del mundo que le corresponde. Así no lo empuja nada, no atraviesa paredes (los objetivos se eligen con camino recto
     * por agua) y además sigue a la nave cuando se mueve o gira.
     */
    private static void swim(Mob mob, SubLevel ship) {
        Level level = mob.level();
        double half = mob.getBbHeight() / 2.0;
        Vec3 center = mob.position().add(0, half, 0);
        Swim s = SWIM.get(mob);
        if (s == null || s.ship != ship || s.last == null || s.last.distanceToSqr(mob.position()) > 1.0) {
            s = new Swim();
            s.ship = ship;
            s.pos = ship.logicalPose().transformPositionInverse(center);
            SWIM.put(mob, s);
        }
        if (!level.getFluidState(BlockPos.containing(s.pos)).is(net.minecraft.tags.FluidTags.WATER)) {
            SWIM.remove(mob);                                     // salió del agua de la nave: vuelve a lo normal
            return;
        }
        if (mob.tickCount > s.until || (s.target != null && s.target.distanceToSqr(s.pos) < 0.01)) {
            boolean rest = s.target != null && mob.getRandom().nextInt(3) == 0;
            s.target = rest ? null : pickTarget(level, s.pos, mob.getRandom());
            s.until = mob.tickCount + (s.target == null ? 20 + mob.getRandom().nextInt(40) : 120);
        }
        Vec3 dir = Vec3.ZERO;
        if (s.target != null) {
            Vec3 d = s.target.subtract(s.pos);
            double len = d.length();
            double speed = 0.05 * mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / 0.1;
            speed = Math.min(Math.max(speed, 0.02), 0.12);
            double step = Math.min(len, speed * Math.min(1.0, 0.3 + len));    // frena al llegar
            if (len > 1.0E-4) {
                dir = d.scale(1.0 / len);
                s.pos = s.pos.add(dir.scale(step));
            }
        }
        Vec3 world = ship.logicalPose().transformPosition(s.pos).subtract(0, half, 0);
        mob.setPos(world);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0;
        s.last = world;
        if (dir != Vec3.ZERO) {
            Vec3 wd = ship.logicalPose().transformNormal(dir);
            float yaw = (float) (net.minecraft.util.Mth.atan2(wd.z, wd.x) * (180F / Math.PI)) - 90.0F;
            mob.setYRot(net.minecraft.util.Mth.approachDegrees(mob.getYRot(), yaw, 15.0F));
            mob.yBodyRot = mob.getYRot();
            mob.yHeadRot = mob.getYRot();
        }
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
                mob.getDeltaMovement(), mob.horizontalCollision, mob.onGround(), mob.getNoActionTime() + " paseo=" + (SWIM.get(mob) == null ? "-" : SWIM.get(mob).pos + " -> " + SWIM.get(mob).target));
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
            if (mob instanceof net.minecraft.world.entity.animal.WaterAnimal) {
                swim(mob, sub);
                if (SWIM.containsKey(mob)) return;                // el paseo ya la coloca respecto a la nave
            }
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
