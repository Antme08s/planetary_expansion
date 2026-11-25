package dev.antme08.planetaryexpansion.transporter;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.WarpDriveBlockEntity;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.joml.Vector3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subidas pendientes del transportador espacial. Cosmonautics pasa una nave al espacio profundo cuando sube por encima de
 * la altura de transición del planeta (SpaceTransitionHandler, en el tick de física de Sable). Para una nave con subida
 * pendiente, mixin/SpaceTransitionLaunchMixin hace que esa comprobación dé "sí" aunque esté en el suelo; Cosmonautics hace
 * todo lo demás (nueva instancia, mover la nave y a los jugadores) y, justo después de crear la órbita, {@link #onTransition}
 * la cambia por una órbita circular a la altura elegida, encima del punto desde el que se despegó.
 */
public final class SpaceLaunch {
    private SpaceLaunch() {}

    /** Si en este tiempo no ha ocurrido la transición, se cancela y se devuelve el nitrógeno. */
    private static final int TIMEOUT_TICKS = 60;
    private static final int STOP_TICKS = 5;
    private static final ResourceKey<Level> DEEP_SPACE = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space"));

    private record Pending(ResourceKey<Level> dim, BlockPos transporter, String planet, double altitudeKm, FluidStack fuel, int deadline) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /** Naves recién subidas a las que se les quita la velocidad unos ticks (como al saltar con el motor de distorsión). */
    private static final Map<UUID, Integer> STOP = new ConcurrentHashMap<>();
    private static boolean warned;

    public static boolean isPending(ServerSubLevel ship) {
        return ship != null && PENDING.containsKey(ship.getUniqueId());
    }

    static void request(ServerLevel level, ServerSubLevel ship, BlockPos transporter, String planet, double altitudeKm, FluidStack fuel) {
        PENDING.put(ship.getUniqueId(), new Pending(level.dimension(), transporter.immutable(), planet, altitudeKm, fuel,
                level.getServer().getTickCount() + TIMEOUT_TICKS));
        stop(ship);
    }

    /** Lo llama el mixin: ¿hay que forzar la transición al espacio de esta nave? */
    public static boolean forceTransition(ServerSubLevel ship) {
        return isPending(ship);
    }

    /** Lo llama el mixin justo después de que Cosmonautics cree la órbita de la nave al salir del planeta. */
    public static void onTransition(ServerSubLevel ship, DeepSpaceInstance inst) {
        if (ship == null || inst == null) return;
        Pending p = PENDING.remove(ship.getUniqueId());
        if (p == null) return;
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            DeepSpaceData data = DeepSpaceData.getInstance(server);
            WarpDriveBlockEntity.Arrival a = WarpDriveBlockEntity.arrival(data.getUniverse(), p.planet(), p.altitudeKm());
            if (a == null) {
                PlanetaryExpansion.LOGGER.warn("[Transportador] No se encontró {}; la nave se queda con la órbita de Cosmonautics", p.planet());
                return;
            }
            DeepSpacePosition pos = inst.getPosition();
            TimeStampedPVCoordinates cur = pos.getCurrentPVCoords();
            // encima del punto desde el que se despegó (si la órbita de Cosmonautics ya es alrededor de ese planeta)
            Vector3D dir = null;
            if (pos.getFrame().getName().equals(a.frame().getName()) && cur.getPosition().getNorm() > 1) {
                dir = cur.getPosition().normalize();
            }
            if (dir == null) {
                double phi = Math.random() * Math.PI * 2;
                dir = new Vector3D(Math.cos(phi), 0, Math.sin(phi));
            }
            Vector3D axis = Vector3D.PLUS_J;
            Vector3D t = axis.crossProduct(dir);
            if (t.getNorm() < 0.2) t = Vector3D.PLUS_I.crossProduct(dir);   // despegue desde un polo
            // misma dirección de giro que las órbitas del motor de distorsión
            Vector3D tangent = t.normalize().negate();
            double r = a.radius();
            double v = Math.sqrt(a.mu() / r);
            pos.init(data.getUniverse(), a.frame(), new TimeStampedPVCoordinates(cur.getDate(), dir.scalarMultiply(r), tangent.scalarMultiply(v)));
            data.setDirty();
            inst.forceClientSync();
            STOP.put(ship.getUniqueId(), STOP_TICKS);
            PlanetaryExpansion.LOGGER.info("[Transportador] Nave {} en órbita de {} a {} km", ship.getUniqueId(), p.planet(),
                    String.format("%,.0f", a.altitudeKm()));
            inst.collectOnlinePlayers(server, pl -> {
                pl.displayClientMessage(Component.translatable("message.planetary_expansion.transporter_arrived",
                        String.format("%,.0f", a.altitudeKm())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                dev.antme08.planetaryexpansion.advancement.Achievements.award(pl, "space_elevator", "launched");
            });
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.error("[Transportador] Falló al poner la nave en órbita", t);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() && STOP.isEmpty()) return;
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending p = e.getValue();
            if (now < p.deadline()) continue;
            it.remove();
            ServerLevel level = server.getLevel(p.dim());
            if (level != null && level.isLoaded(p.transporter())
                    && level.getBlockEntity(p.transporter()) instanceof SpaceTransporterBlockEntity be) be.refund(p.fuel());
            if (level != null) {
                Vector3d c = dev.antme08.planetaryexpansion.warp.Warp.toWorld(level, p.transporter());
                for (ServerPlayer pl : level.players())
                    if (pl.distanceToSqr(c.x, c.y, c.z) < 64 * 64)
                        pl.displayClientMessage(Component.translatable("message.planetary_expansion.transporter_failed").withStyle(ChatFormatting.RED), true);
            }
            if (!warned) {
                warned = true;
                PlanetaryExpansion.LOGGER.warn("[Transportador] La nave {} no pasó al espacio a tiempo (¿cambió Cosmonautics?). Se devuelve el nitrógeno.", e.getKey());
            } else {
                PlanetaryExpansion.LOGGER.warn("[Transportador] La nave {} no pasó al espacio a tiempo", e.getKey());
            }
        }
        if (STOP.isEmpty()) return;
        ServerLevel space = server.getLevel(DEEP_SPACE);
        for (Iterator<Map.Entry<UUID, Integer>> it = STOP.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> e = it.next();
            try {
                SubLevel s = space == null ? null : SubLevelContainer.getContainer(space).getSubLevel(e.getKey());
                if (s instanceof ServerSubLevel ship) stop(ship);
            } catch (Throwable ignored) {
            }
            if (e.getValue() <= 1) it.remove();
            else e.setValue(e.getValue() - 1);
        }
    }

    private static void stop(ServerSubLevel ship) {
        if (ship == null || ship.isRemoved()) return;
        try {
            RigidBodyHandle body = RigidBodyHandle.of(ship);
            if (body == null || !body.isValid()) return;
            Vector3d lin = body.getLinearVelocity(new Vector3d());
            Vector3d ang = body.getAngularVelocity(new Vector3d());
            body.addLinearAndAngularVelocity(lin.negate(), ang.negate());
        } catch (Throwable ignored) {
        }
    }
}
