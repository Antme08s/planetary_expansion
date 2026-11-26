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
        } else if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            report(player);
        }
    }

    private static final Map<UUID, Long> LAST_REPORT = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 1.0.4.1, para encontrar fallos: si un jugador se queda sin aire pegado a una nave que tiene algún compartimento
     * con oxígeno, se apunta en el registro qué bloque tiene en la cabeza y qué hay alrededor (como mucho cada 10 s).
     */
    private static void report(net.minecraft.server.level.ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_REPORT.get(player.getUUID());
        if (last != null && now - last < 200) return;
        try {
            Level level = player.level();
            Vec3 p = player.getEyePosition();
            for (Map.Entry<UUID, SubLevelAccess> e : CompartmentTracker.getSubsSnapshot().entrySet()) {
                if (!(e.getValue() instanceof dev.ryanhcode.sable.sublevel.SubLevel sub) || sub.getLevel() != level) continue;
                AABB box = CompartmentTracker.getWorldAABB(e.getKey());
                if (box == null || !box.inflate(1.5).contains(p) || !CompartmentTracker.hasAnySealed(e.getKey())) continue;
                LAST_REPORT.put(player.getUUID(), now);
                Vec3 local = sub.logicalPose().transformPositionInverse(p);
                BlockPos cell = BlockPos.containing(local);
                StringBuilder around = new StringBuilder();
                for (Direction d : Direction.values()) {
                    BlockPos n = cell.relative(d);
                    boolean in = CompartmentTracker.isInSealedExact(level, sub.logicalPose().transformPosition(Vec3.atCenterOf(n)));
                    around.append(' ').append(d.getName()).append('=').append(level.getBlockState(n).getBlock().getDescriptionId())
                            .append(in ? "(aire)" : "");
                }
                PlanetaryExpansion.LOGGER.info("[Oxígeno] {} sin aire junto a una nave con oxígeno: cabeza en {} local {} ({}),{}",
                        player.getName().getString(), level.getBlockState(cell), cell,
                        String.format("%.2f %.2f %.2f", local.x - cell.getX(), local.y - cell.getY(), local.z - cell.getZ()), around);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * ¿Está este punto dentro de un compartimento con oxígeno?
     * <p>
     * 1.0.4.1: Deep Seas trata cada bloque con datos (copycats, etc.) como pared entera aunque sea un panel, un escalón o
     * una viga, así que si la cabeza de un jugador o de un animal entra en el hueco libre de uno (pegado a la pared o en
     * una esquina), ese hueco no cuenta como interior y se ahoga. Si el punto cae dentro de un bloque así de la nave, se
     * miran los huecos vecinos por las caras que ese bloque no tapa del todo (según su forma real, con su material): si
     * alguno es interior, se respira. Las caras tapadas por el bloque no cuentan, así que desde fuera del casco no se
     * cuela aire.
     */
    public static boolean sealed(Level level, Vec3 p) {
        if (CompartmentTracker.isInSealedExact(level, p)) return true;
        try {
            for (Map.Entry<UUID, SubLevelAccess> e : CompartmentTracker.getSubsSnapshot().entrySet()) {
                if (!(e.getValue() instanceof dev.ryanhcode.sable.sublevel.SubLevel sub) || sub.getLevel() != level) continue;
                AABB box = CompartmentTracker.getWorldAABB(e.getKey());
                if (box == null || !box.inflate(1.5).contains(p)) continue;
                Pose3dc pose = sub.logicalPose();
                Vec3 local = pose.transformPositionInverse(p);
                BlockPos cell = BlockPos.containing(local);
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(cell);
                if (st.isAir()) continue;
                net.minecraft.world.phys.shapes.VoxelShape shape = st.getCollisionShape(level, cell);
                if (shape.isEmpty() || net.minecraft.world.level.block.Block.isShapeFullBlock(shape)) continue;
                Vec3 c = Vec3.atCenterOf(cell);
                for (Direction dir : Direction.values()) {
                    // 1.0.4.1 (v35): solo se descartan las caras tapadas del todo. Una rampa (copycat slope) o una
                    // escalera tocan todas las caras, pero dejan libre parte de algunas, y por ahí sigue el aire
                    if (net.minecraft.world.level.block.Block.isFaceFull(shape, dir)) continue;
                    Vec3 n = c.add(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                    if (CompartmentTracker.isInSealedExact(level, pose.transformPosition(n))) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
