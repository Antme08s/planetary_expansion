package dev.antme08.planetaryexpansion.gravity;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Qué naves tienen un generador de gravedad funcionando. Cada generador activo se apunta cada tick; una nave
 * cuenta como "con gravedad" si algún generador suyo se apuntó en los últimos 10 ticks. Hay un registro para el
 * servidor y otro para el cliente (en un mundo de un jugador conviven los dos en el mismo proceso).
 *
 * Una entidad está en el campo si va encima o dentro de esa nave (Sable la sigue, va montada en ella o está
 * dentro de la caja de la nave).
 */
public final class GravityFields {
    private GravityFields() {}

    private static final long TTL = 10;
    private static final Map<UUID, Long> SERVER = new HashMap<>();
    private static final Map<UUID, Long> CLIENT = new HashMap<>();

    private static Map<UUID, Long> side(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    static void mark(Level level, BlockPos generator) {
        SubLevelAccess ship;
        try {
            ship = Sable.HELPER.getContaining(level, (net.minecraft.core.Vec3i) generator);
        } catch (Throwable t) {
            return;
        }
        if (ship == null) return; // fuera de una nave no hace nada (en el terreno ya hay gravedad)
        Map<UUID, Long> map = side(level);
        synchronized (map) {
            map.put(ship.getUniqueId(), level.getGameTime());
        }
    }

    private static boolean active(Map<UUID, Long> map, UUID ship, long now) {
        Long t = map.get(ship);
        return t != null && now - t <= TTL && now >= t - TTL;
    }

    /** ¿La entidad está en una nave con gravedad artificial? */
    public static boolean inField(Entity entity) {
        Level level = entity.level();
        Map<UUID, Long> map = side(level);
        if (map.isEmpty()) return false;
        long now = level.getGameTime();
        try {
            synchronized (map) {
                SubLevelAccess s = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
                if (s != null && active(map, s.getUniqueId(), now)) return true;
                BoundingBox3dc box = new BoundingBox3d(entity.getX() - 0.5, entity.getY() - 0.5, entity.getZ() - 0.5,
                        entity.getX() + 0.5, entity.getY() + entity.getBbHeight() + 0.5, entity.getZ() + 0.5);
                for (SubLevelAccess ship : Sable.HELPER.getAllIntersecting(level, box)) {
                    if (active(map, ship.getUniqueId(), now)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Limpieza de naves que ya no tienen generadores activos (llamado de vez en cuando). */
    static void prune(Level level) {
        Map<UUID, Long> map = side(level);
        long now = level.getGameTime();
        synchronized (map) {
            map.values().removeIf(t -> Math.abs(now - t) > 200);
        }
    }
}
