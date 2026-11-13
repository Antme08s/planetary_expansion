package dev.antme08.planetaryexpansion.weather;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Viento sobre las estructuras físicas de Sable (naves, cohetes...).
 *
 * Fuerza ≈ fuerzaPorÁrea × superficie expuesta al viento × intensidad, y se reduce a medida que la
 * nave alcanza la velocidad del viento. Se limita la aceleración para que nada salga disparado.
 * - Con ruedas (etiqueta planetary_expansion:wind_wheels): se empujan siempre; en el suelo ruedan
 *   más despacio (hasta el 40 % de la velocidad del viento).
 * - Sin ruedas y apoyadas en el suelo (bases, cohetes aterrizados): no se mueven.
 * - Sin ruedas y en el aire (naves volando): se empujan (config pushFlyingShips).
 * - Con patas de Let it Walk! (etiqueta planetary_expansion:wind_legs): cuentan como apoyadas, no se mueven.
 */
public final class StormPhysics {
    /** Aceleración máxima que puede causar el viento (m/s²). */
    private static final double MAX_ACCEL = 3.0;

    private StormPhysics() {}

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(StormPhysics::prePhysicsTick);
    }

    /** Bloques que cuentan como ruedas: etiqueta de datos, ampliable sin recompilar. */
    public static final TagKey<Block> WIND_WHEELS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "wind_wheels"));
    /**
     * Bloques de patas (Let it Walk!): una estructura que los tenga cuenta siempre como apoyada en el suelo y el
     * viento no la empuja (0.7.5, petición del usuario). Etiqueta de datos, ampliable sin recompilar.
     */
    public static final TagKey<Block> WIND_LEGS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "wind_legs"));
    /** Caché por estructura: [instante de la última revisión, 1 si tiene ruedas]. */
    private static final Map<UUID, long[]> WHEEL_CACHE = new HashMap<>();
    private static final Map<UUID, long[]> LEG_CACHE = new HashMap<>();

    /**
     * ¿La estructura tiene algún bloque de la etiqueta wind_wheels? Se revisa cada 2 s. Solo se mira
     * la paleta de cada sección (la lista de tipos de bloque), así que es muy barato.
     */
    private static boolean hasWheels(ServerSubLevel ship, long gameTime) {
        return hasTagged(ship, gameTime, WIND_WHEELS, WHEEL_CACHE);
    }

    /** ¿Tiene patas de Let it Walk! (núcleo, articulaciones o pies)? */
    private static boolean hasLegs(ServerSubLevel ship, long gameTime) {
        return hasTagged(ship, gameTime, WIND_LEGS, LEG_CACHE);
    }

    private static boolean hasTagged(ServerSubLevel ship, long gameTime, TagKey<Block> tag, Map<UUID, long[]> cache) {
        UUID id = ship.getUniqueId();
        long[] cached = cache.get(id);
        if (cached != null && gameTime - cached[0] < 40) return cached[1] == 1;
        boolean found = false;
        try {
            for (PlotChunkHolder holder : ship.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk == null) continue;
                for (LevelChunkSection section : chunk.getSections()) {
                    if (section == null || section.hasOnlyAir()) continue;
                    if (section.getStates().maybeHas(state -> state.is(tag))) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
        } catch (Throwable ignored) {
        }
        if (cache.size() > 4096) cache.clear();
        cache.put(id, new long[]{gameTime, found ? 1 : 0});
        return found;
    }

    /** Hay terreno (o un bloque del mundo) a menos de ~1 bloque bajo alguna esquina o el centro. */
    static boolean isGrounded(ServerLevel level, BoundingBox3dc box) {
        double minY = box.minY();
        double[][] points = {
                {box.minX(), box.minZ()}, {box.maxX(), box.minZ()},
                {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()},
                {(box.minX() + box.maxX()) * 0.5, (box.minZ() + box.maxZ()) * 0.5}};
        for (double[] p : points) {
            BlockPos column = new BlockPos(Mth.floor(p[0]), Mth.floor(minY), Mth.floor(p[1]));
            if (!level.isLoaded(column)) return true; // por seguridad: si no se sabe, no empujar
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            if (top >= minY - 1.0) return true;
        }
        return false;
    }

    /**
     * Apoyada o enganchada a otra estructura de Sable: la caja de otra estructura se solapa en planta con
     * esta y llega hasta su base (o se cruza con ella). El terreno del mundo no ve las estructuras (están
     * en el "plot" de Sable), así que isGrounded no las cuenta.
     */
    static boolean restsOnOtherStructure(ServerSubLevel ship, BoundingBox3dc box, java.util.List<ServerSubLevel> all) {
        for (ServerSubLevel other : all) {
            if (other == ship) continue;
            BoundingBox3dc o = other.boundingBox();
            boolean overlapXZ = o.minX() <= box.maxX() + 1.0 && o.maxX() >= box.minX() - 1.0
                    && o.minZ() <= box.maxZ() + 1.0 && o.maxZ() >= box.minZ() - 1.0;
            if (!overlapXZ) continue;
            if (o.maxY() >= box.minY() - 1.5 && o.minY() <= box.minY() + 0.5) return true;
        }
        return false;
    }

    /** Un aviso por estructura y tormenta cuando el viento la empuja, para poder seguir qué se movió. */
    private static final java.util.Set<UUID> LOGGED_PUSH = new java.util.HashSet<>();
    private static long lastPushLogClear;

    private static void logPushed(ServerSubLevel ship, boolean grounded, boolean wheels, ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastPushLogClear > 20 * 60 * 10) {
            LOGGED_PUSH.clear();
            lastPushLogClear = now;
        }
        if (LOGGED_PUSH.add(ship.getUniqueId())) {
            PlanetaryExpansion.LOGGER.info("[Clima] El viento empuja '{}' en {} ({})", ship.getName(),
                    level.dimension().location(), wheels ? (grounded ? "vehículo con ruedas en el suelo" : "vehículo con ruedas en el aire") : "en el aire");
        }
    }

    private static void prePhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        ServerLevel level = system.getLevel();
        if (level == null) return;
        StormType type = StormType.forDimension(level.dimension());
        if (type == null || !StormConfig.get(StormConfig.PUSH_SHIPS)) return;
        double wind = StormConfig.get(StormConfig.WIND_STRENGTH);
        float intensity = StormManager.intensity(type);
        if (wind <= 0 || intensity <= 0.01f) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        double angle = StormManager.windAngle(type);
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double gust = StormManager.gust(level.getGameTime());
        double targetSpeed = type.windSpeed * intensity * gust;

        java.util.List<ServerSubLevel> ships = new java.util.ArrayList<>();
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub instanceof ServerSubLevel ship && !ship.isRemoved()) ships.add(ship);
        }

        for (ServerSubLevel ship : ships) {
            BoundingBox3dc box = ship.boundingBox();

            int cx = Mth.floor((box.minX() + box.maxX()) * 0.5);
            int cz = Mth.floor((box.minZ() + box.maxZ()) * 0.5);
            // No tocar estructuras junto a terreno sin cargar (y no forzar la carga de chunks).
            if (!level.isLoaded(new BlockPos(cx, Mth.floor(box.minY()), cz))) continue;
            // Protegida si está bajo techo del mundo (hangar, cueva).
            if (box.maxY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz)) continue;

            // Apoyada en el suelo: solo se empuja si tiene ruedas (vehículos). Las bases y cohetes
            // aterrizados sin ruedas se quedan quietos; si no, el viento podría arrastrarlos hasta un
            // borde y al caer superarían los 25 m/s, y Cosmonautics rompe bloques por "Max-Q".
            // Una estructura montada sobre otra (p. ej. un molino sobre un rodamiento giratorio de
            // Simulated encima de la base) no toca el terreno, pero no está volando: si se empujara
            // como una nave, tiraría de la unión y rompería la base.
            // Máquinas con patas (Let it Walk!): el casco va en alto sobre las patas, pero está de pie en el suelo.
            if (hasLegs(ship, level.getGameTime())) continue;
            boolean grounded = isGrounded(level, box) || restsOnOtherStructure(ship, box, ships);
            boolean wheels = hasWheels(ship, level.getGameTime());
            if (grounded && !wheels) continue;
            if (!grounded && !wheels && !StormConfig.get(StormConfig.PUSH_FLYING_SHIPS)) continue;
            logPushed(ship, grounded, wheels, level);
            // En el suelo las ruedas ruedan, pero más despacio que una nave en el aire.
            double groundSpeed = grounded ? 0.4 : 1.0;
            double groundForce = grounded ? 0.6 : 1.0;

            RigidBodyHandle handle = system.getPhysicsHandle(ship);
            if (handle == null || !handle.isValid()) continue;
            double mass = ship.getMassTracker().getMass();
            if (!(mass > 0)) continue;

            double area = box.height() * (Math.abs(dx) * box.length() + Math.abs(dz) * box.width());
            Vector3dc v = handle.getLinearVelocity(new Vector3d());
            double along = v.x() * dx + v.z() * dz;
            double relative = Math.max(0.0, 1.0 - along / Math.max(0.1, targetSpeed * groundSpeed));
            double force = type.shipForcePerArea * area * intensity * wind * gust * relative * groundForce;
            force = Math.min(force, MAX_ACCEL * mass);
            if (force <= 0) continue;

            // Sable espera el impulso en el sistema de coordenadas local de la nave.
            Vector3d impulse = new Vector3d(dx * force * timeStep, 0.0, dz * force * timeStep);
            ship.logicalPose().orientation().transformInverse(impulse);
            handle.applyLinearImpulse(impulse);
        }
    }
}
