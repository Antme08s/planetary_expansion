package dev.antme08.planetaryexpansion.client;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 1.0.4.1: atravesar las paredes de una nave corriendo (prueba del usuario en el espacio profundo, no siempre pasa).
 * <p>
 * Dentro de una nave de Sable el choque del jugador con los bloques de la nave se calcula aparte del choque con el
 * mundo, y a veces, al correr contra una pared, el jugador acaba al otro lado (sobre todo si la nave se mueve o gira un
 * poco). El movimiento del jugador lo calcula su propio cliente, así que aquí, en el cliente, se mira el paso de cada
 * tick en las coordenadas de la nave: si la línea que recorre el centro del cuerpo cruza un bloque sólido de la nave, el
 * jugador vuelve a donde estaba (en la nave) y se le quita la velocidad horizontal. Si ya empezaba dentro de un bloque no
 * se toca, para no dejarlo atrapado; tampoco a quien va sentado, en espectador o tumbado (al reptar el centro queda
 * tan bajo que cruzaría los escalones al subirlos).
 */
public final class ShipWallGuard {
    private ShipWallGuard() {}

    private static SubLevel ship;
    private static Vec3 before;
    private static long lastLog = Long.MIN_VALUE;
    private static boolean failed;

    private static Vec3 center(LocalPlayer p) {
        return p.position().add(0, p.getBbHeight() * 0.5, 0);
    }

    private static boolean inside(SubLevel s, Vec3 local, double margin) {
        var b = s.getPlot().getBoundingBox();
        return local.x >= b.minX() - margin && local.y >= b.minY() - margin && local.z >= b.minZ() - margin
                && local.x <= b.maxX() + 1 + margin && local.y <= b.maxY() + 1 + margin && local.z <= b.maxZ() + 1 + margin;
    }

    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (failed || !(event.getEntity() instanceof LocalPlayer p)) return;
        ship = null;
        try {
            if (p.isSpectator() || p.noPhysics || p.isPassenger() || p.getAbilities().flying) return;
            if (p.getPose() != Pose.STANDING && p.getPose() != Pose.CROUCHING) return;
            SubLevelAccess t = Sable.HELPER.getTrackingSubLevel(p);
            if (t == null) t = Sable.HELPER.getLastTrackingSubLevel(p);
            if (!(t instanceof SubLevel s) || s.isRemoved() || s.getLevel() != p.level()) return;
            Vec3 local = s.logicalPose().transformPositionInverse(center(p));
            if (!inside(s, local, 1.0)) return;
            ship = s;
            before = local;
        } catch (Throwable e) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] Protección de paredes desactivada", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (failed || ship == null || !(event.getEntity() instanceof LocalPlayer p)) return;
        SubLevel s = ship;
        ship = null;
        try {
            if (s.isRemoved() || s.getLevel() != p.level() || p.isPassenger()) return;
            var pose = s.logicalPose();
            Vec3 now = pose.transformPositionInverse(center(p));
            double d2 = now.distanceToSqr(before);
            if (d2 < 1.0e-6 || d2 > 16.0) return;   // quieto, o un salto grande (teletransporte, cambio de nave)
            BlockHitResult hit = p.level().clip(new ClipContext(before, now, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) return;
            Vec3 back = pose.transformPosition(before).subtract(0, p.getBbHeight() * 0.5, 0);
            p.setPos(back.x, back.y, back.z);
            p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
            long t = p.level().getGameTime();
            if (t - lastLog > 200) {
                lastLog = t;
                PlanetaryExpansion.LOGGER.info("[Naves] Evitado atravesar {} de la nave al moverse ({} bloques en un tick)",
                        p.level().getBlockState(hit.getBlockPos()), String.format("%.2f", Math.sqrt(d2)));
            }
        } catch (Throwable e) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] Protección de paredes desactivada", e);
        }
    }
}
