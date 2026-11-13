package dev.antme08.planetaryexpansion.weather;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiPredicate;

/**
 * Decide quién está expuesto a la tormenta. Funciona igual en servidor y cliente.
 *
 * - Daño / congelación: a la intemperie (sin techo encima, como la lluvia) y NO dentro de un
 *   compartimento con oxígeno de Create Deep Seas.
 * - Empuje directo del viento: a la intemperie y no dentro de una nave de Sable (en ese caso el
 *   viento empuja a la nave entera, no al jugador dentro de ella).
 */
public final class StormShelter {
    /** Lo rellena la compatibilidad con Deep Seas si está instalado. */
    private static volatile BiPredicate<Level, Vec3> sealedCheck = (level, pos) -> false;

    private StormShelter() {}

    public static void setSealedCheck(BiPredicate<Level, Vec3> check) {
        sealedCheck = check;
    }

    /** Sin bloques que tapen el cielo justo encima (mismo criterio que la lluvia de Minecraft). */
    public static boolean underOpenSky(Level level, Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bp.getX(), bp.getZ()) <= bp.getY();
    }

    public static boolean inOxygenatedShip(Level level, Vec3 eyePos) {
        try {
            return sealedCheck.test(level, eyePos);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean insideAnyShip(Level level, Vec3 pos) {
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return false;
            for (SubLevel sub : container.getAllSubLevels()) {
                BoundingBox3dc b = sub.boundingBox();
                if (pos.x >= b.minX() && pos.x <= b.maxX()
                        && pos.y >= b.minY() && pos.y <= b.maxY()
                        && pos.z >= b.minZ() && pos.z <= b.maxZ()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean exposedToHazards(Player player) {
        Vec3 eye = player.getEyePosition();
        return underOpenSky(player.level(), eye) && !inOxygenatedShip(player.level(), eye);
    }

    public static boolean exposedToWind(Entity entity) {
        Vec3 mid = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        return underOpenSky(entity.level(), mid) && !insideAnyShip(entity.level(), mid);
    }
}
