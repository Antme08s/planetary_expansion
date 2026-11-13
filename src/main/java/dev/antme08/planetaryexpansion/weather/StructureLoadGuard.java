package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Protección de estructuras de Sable al cargarse en los planetas de este mod.
 *
 * El 2026-11-05 la base de Marte del usuario se rompió entera unos 4 s después de volver a Marte (los objetos
 * tirados tenían ~220 ticks de edad y no había tormenta). La rompió el Max-Q de Cosmonautics, que hace explotar
 * bloques de cualquier estructura que va a más de 25 m/s, así que algo la lanzó al cargarse; el log no dice qué.
 *
 * Durante los primeros {@link #GRACE_TICKS} ticks desde que una estructura aparece (al cargar la zona o al
 * entrar a la dimensión), si estaba apoyada en el suelo:
 *  - si se mueve a más de {@link #MAX_LOAD_SPEED} m/s se frena (impulso contrario) y se avisa en el log;
 *  - el Max-Q de Cosmonautics no le rompe bloques (ver GlobalSpacePhysicsMixin).
 * Estructuras que llegan volando (cohetes que aterrizan) no se tocan.
 */
public final class StructureLoadGuard {
    private StructureLoadGuard() {}

    public static final int GRACE_TICKS = 100;
    /** Se considera "nueva" otra vez si llevaba este tiempo sin verse (descargada). */
    private static final int UNSEEN_RESET_TICKS = 40;
    private static final double MAX_LOAD_SPEED = 3.0;

    private static final class Info {
        long firstSeen;
        long lastSeen;
        boolean groundedAtLoad;
        boolean warned;
    }

    private static final Map<UUID, Info> INFO = new HashMap<>();
    private static final Map<UUID, Long> LAST_MAXQ_LOG = new HashMap<>();

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(StructureLoadGuard::physicsTick);
    }

    public static boolean isPlanet(ServerLevel level) {
        return level != null && level.dimension().location().getNamespace().equals(PlanetaryExpansion.MODID);
    }

    private static void physicsTick(SubLevelPhysicsSystem system, double timeStep) {
        ServerLevel level = system.getLevel();
        if (!isPlanet(level)) return;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        long now = level.getGameTime();

        for (SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
            Info info = INFO.get(ship.getUniqueId());
            if (info == null || now - info.lastSeen > UNSEEN_RESET_TICKS) {
                info = new Info();
                info.firstSeen = now;
                info.groundedAtLoad = StormPhysics.isGrounded(level, ship.boundingBox());
                INFO.put(ship.getUniqueId(), info);
            }
            info.lastSeen = now;
            if (!info.groundedAtLoad || now - info.firstSeen > GRACE_TICKS) continue;

            RigidBodyHandle handle = system.getPhysicsHandle(ship);
            if (handle == null || !handle.isValid()) continue;
            Vector3d v = handle.getLinearVelocity(new Vector3d());
            double speed = v.length();
            if (speed <= MAX_LOAD_SPEED) continue;

            double mass = Math.max(1.0, ship.getMassTracker().getMass());
            Vector3d impulse = new Vector3d(v).mul(-mass);
            ship.logicalPose().orientation().transformInverse(impulse);
            handle.applyLinearImpulse(impulse);
            if (!info.warned) {
                info.warned = true;
                BoundingBox3dc box = ship.boundingBox();
                PlanetaryExpansion.LOGGER.warn(
                        "[Estructuras] '{}' en {} ({}, {}, {}) se movió a {} m/s justo al cargarse; se frena para que no se rompa",
                        ship.getName(), level.dimension().location(),
                        (int) ((box.minX() + box.maxX()) / 2), (int) box.minY(), (int) ((box.minZ() + box.maxZ()) / 2),
                        String.format(java.util.Locale.ROOT, "%.1f", speed));
            }
        }

        // limpiar estructuras que ya no existen
        if (now % 200 == 0) {
            Iterator<Map.Entry<UUID, Info>> it = INFO.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().lastSeen > 1200) it.remove();
            }
            LAST_MAXQ_LOG.entrySet().removeIf(e -> now - e.getValue() > 1200);
        }
    }

    /** true si Cosmonautics no debe romperle bloques ahora (recién cargada y estaba en el suelo). */
    public static boolean protectFromMaxQ(ServerSubLevel ship, ServerLevel level) {
        if (!isPlanet(level)) return false;
        Info info = INFO.get(ship.getUniqueId());
        long now = level.getGameTime();
        if (info == null) return true; // todavía ni la hemos visto: acaba de aparecer
        return info.groundedAtLoad && now - info.firstSeen <= GRACE_TICKS;
    }

    /** Aviso (como mucho cada 5 s por estructura) cuando Cosmonautics empieza a romper una estructura por velocidad. */
    public static void logMaxQ(ServerSubLevel ship, ServerLevel level, double speed) {
        if (!isPlanet(level)) return;
        long now = level.getGameTime();
        Long last = LAST_MAXQ_LOG.get(ship.getUniqueId());
        if (last != null && now - last < 100) return;
        LAST_MAXQ_LOG.put(ship.getUniqueId(), now);
        BoundingBox3dc box = ship.boundingBox();
        PlanetaryExpansion.LOGGER.warn(
                "[Estructuras] '{}' va a {} m/s en {} ({}, {}, {}): Cosmonautics le rompe bloques por exceso de velocidad (Max-Q)",
                ship.getName(), String.format(java.util.Locale.ROOT, "%.1f", speed), level.dimension().location(),
                (int) ((box.minX() + box.maxX()) / 2), (int) box.minY(), (int) ((box.minZ() + box.maxZ()) / 2));
    }
}
