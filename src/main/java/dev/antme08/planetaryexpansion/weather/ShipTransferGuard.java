package dev.antme08.planetaryexpansion.weather;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arreglo para Cosmonautics (0.7.16): al cruzar entre un planeta y el espacio profundo, Cosmonautics teletransporta
 * al jugador por un lado y copia la nave por otro (nave nueva, con otro identificador), sin atarlos. El jugador
 * acababa fuera de la nave y, si iba sentado, flotando en pose de sentado mientras la nave se iba (lo vio el usuario
 * al salir y al entrar, con los motores encendidos).
 *
 * Qué hace:
 *  1. Cada tick apunta en qué nave está cada jugador y dónde dentro de ella (relativo al centro de masa, que se
 *     conserva al copiar la nave) y, si va sentado en un asiento de Create, en qué bloque de asiento.
 *  2. Cuando el jugador cambia de dimensión desde o hacia el espacio profundo, durante 4 s busca la copia de la
 *     nave en la nueva dimensión (misma talla, cerca) y:
 *       - si iba sentado, lo vuelve a sentar en el mismo asiento de la nave nueva;
 *       - si iba de pie, lo mantiene en su sitio dentro de la nave y con la velocidad de la nave.
 *
 * 0.7.33 (el usuario salió desprendido otra vez al subir manejando de pie, rápido):
 *  - la nave del jugador se toma primero de Sable (la nave que lo lleva encima), y si no, de la caja de la nave con
 *    un margen de 10 bloques, el mismo que usa Cosmonautics para llevarse a los jugadores. Antes el margen era de
 *    1.5 bloques: a mucha velocidad el jugador quedaba un poco detrás de la caja y no se apuntaba;
 *  - la copia de la nave se reconoce también por su masa y se busca más lejos;
 *  - red de seguridad: si un jugador queda flotando en el espacio profundo fuera de cualquier instancia (se fue a la
 *    deriva), a los 3 s se le devuelve a su última nave.
 */
public final class ShipTransferGuard {
    private ShipTransferGuard() {}

    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final int GLUE_TICKS = 80;

    private record OnShip(UUID ship, Vector3d rel, Vector3d seatRel, double size, double mass, long tick) {}

    private static final class Pending {
        final OnShip info;
        int ticks = GLUE_TICKS;
        SubLevel ship;
        boolean announced;

        Pending(OnShip info) {
            this.info = info;
        }
    }

    private static final Map<UUID, OnShip> LAST = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Última nave de cada jugador (no caduca; se actualiza al cambiar de dimensión con la copia nueva). */
    private static final Map<UUID, OnShip> HOME = new HashMap<>();
    private static final Map<UUID, Integer> STRANDED = new HashMap<>();
    private static final double CAPTURE_MARGIN = 10.0;
    private static long tickCounter;

    // ------------------------------------------------------------------ 1) apuntar dónde va cada jugador

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        tickCounter++;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) continue;
            List<? extends SubLevel> ships;
            try {
                ships = SubLevelContainer.getContainer(level).getAllSubLevels();
            } catch (Throwable t) {
                continue;
            }
            if (ships.isEmpty()) continue;
            for (ServerPlayer player : players) {
                if (PENDING.containsKey(player.getUUID())) continue; // mientras lo recolocamos no lo pisamos
                SubLevel ship = shipOf(player, ships);
                if (ship == null || ship.isRemoved()) continue;
                Pose3dc pose = ship.logicalPose();
                Vector3d plot = pose.transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()), new Vector3d());
                Vector3d rel = plot.sub(pose.rotationPoint(), new Vector3d());
                Vector3d seatRel = null;
                Entity vehicle = player.getVehicle();
                if (vehicle != null && SubLevelContainer.getContainer(level).inBounds(vehicle.blockPosition())) {
                    BlockPos seat = vehicle.blockPosition();
                    if (level.getBlockState(seat).getBlock() instanceof SeatBlock) {
                        seatRel = new Vector3d(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5).sub(pose.rotationPoint());
                    }
                }
                OnShip info = new OnShip(ship.getUniqueId(), rel, seatRel, size(ship.boundingBox()), mass(ship), tickCounter);
                LAST.put(player.getUUID(), info);
                HOME.put(player.getUUID(), info);
            }
        }
        if (LAST.size() > 256) LAST.values().removeIf(o -> tickCounter - o.tick() > 40);
    }

    /** La nave que Sable dice que lleva al jugador; si no, la más pequeña cuya caja (con margen) lo contiene. */
    private static SubLevel shipOf(ServerPlayer player, List<? extends SubLevel> ships) {
        try {
            SubLevelAccess tracked = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
            if (tracked != null) {
                for (SubLevel s : ships) {
                    if (!s.isRemoved() && s.getUniqueId().equals(tracked.getUniqueId())) return s;
                }
            }
        } catch (Throwable ignored) {
        }
        SubLevel near = shipAround(ships, player.position(), 1.5);
        return near != null ? near : shipAround(ships, player.position(), CAPTURE_MARGIN);
    }

    private static double mass(SubLevel ship) {
        try {
            if (ship instanceof ServerSubLevel s && s.getMassTracker() != null) return s.getMassTracker().getMass();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** Nave cuya caja (agrandada {@code margin}) contiene la posición; la más pequeña si hay varias. */
    private static SubLevel shipAround(List<? extends SubLevel> ships, Vec3 p, double margin) {
        SubLevel best = null;
        double bestSize = Double.MAX_VALUE;
        for (SubLevel s : ships) {
            if (s.isRemoved()) continue;
            BoundingBox3dc b = s.boundingBox();
            if (p.x < b.minX() - margin || p.x > b.maxX() + margin || p.y < b.minY() - margin || p.y > b.maxY() + margin
                    || p.z < b.minZ() - margin || p.z > b.maxZ() + margin) continue;
            double sz = size(b);
            if (sz < bestSize) {
                bestSize = sz;
                best = s;
            }
        }
        return best;
    }

    private static double size(BoundingBox3dc b) {
        double dx = b.maxX() - b.minX(), dy = b.maxY() - b.minY(), dz = b.maxZ() - b.minZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ 2) tras cruzar, volver a ponerlo en la nave

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean space = event.getFrom().location().equals(DEEP_SPACE) || event.getTo().location().equals(DEEP_SPACE);
        if (!space) return;
        OnShip info = LAST.remove(player.getUUID());
        if (info == null || tickCounter - info.tick() > 20) {
            PlanetaryExpansion.LOGGER.info("[Naves] {} cruzó a {} sin ir en ninguna nave", player.getName().getString(), event.getTo().location());
            return;
        }
        PENDING.put(player.getUUID(), new Pending(info));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
        HOME.remove(event.getEntity().getUUID());
        STRANDED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 13) {
            try {
                rescueStranded(event.getServer());
            } catch (Throwable t) {
                PlanetaryExpansion.LOGGER.debug("[Naves] Fallo al buscar jugadores a la deriva", t);
            }
        }
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending p = e.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || --p.ticks < 0) {
                if (player != null && p.ship == null) {
                    PlanetaryExpansion.LOGGER.warn("[Naves] {} cruzó de dimensión pero no se encontró la copia de su nave (tamaño {}, masa {})",
                            player.getName().getString(), String.format("%.1f", p.info.size()), String.format("%.0f", p.info.mass()));
                }
                it.remove();
                continue;
            }
            try {
                if (glue(player, p)) it.remove();
            } catch (Throwable t) {
                PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo recolocar a {} en su nave tras cambiar de dimensión", player.getName().getString(), t);
                it.remove();
            }
        }
    }

    /** @return true si ya terminó (sentado otra vez en su asiento). */
    private static boolean glue(ServerPlayer player, Pending p) {
        ServerLevel level = player.serverLevel();
        if (p.ship == null || p.ship.isRemoved() || p.ship.getLevel() != level) {
            p.ship = findCopy(level, player.position(), p.info.size(), p.info.mass());
            if (p.ship == null) return false;
            if (!p.announced) {
                p.announced = true;
                OnShip i = p.info;
                HOME.put(player.getUUID(), new OnShip(p.ship.getUniqueId(), i.rel(), i.seatRel(), i.size(), i.mass(), tickCounter));
                PlanetaryExpansion.LOGGER.info("[Naves] {} cruzó a {}: se le vuelve a poner en su nave", player.getName().getString(), level.dimension().location());
            }
        }
        Pose3dc pose = p.ship.logicalPose();
        Vector3d rot = new Vector3d(pose.rotationPoint());

        if (p.info.seatRel() != null) {
            Vector3d seatPlot = rot.add(p.info.seatRel(), new Vector3d());
            BlockPos seat = BlockPos.containing(seatPlot.x, seatPlot.y, seatPlot.z);
            if (level.getBlockState(seat).getBlock() instanceof SeatBlock) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null && vehicle.blockPosition().equals(seat)) return true; // ya está sentado bien
                // 0.7.39: si el jugador aún está lejos del asiento (p. ej. viene de coordenadas absurdas), primero se le
                // lleva junto al asiento y se le sienta en el siguiente tick. Sentarlo a millones de bloques hacía que
                // la caja jugador+asiento midiera millones de bloques y el juego se colgaba buscando entidades en ella.
                Vector3d seatWorld = pose.transformPosition(new Vector3d(seat.getX() + 0.5, seat.getY() + 1.0, seat.getZ() + 0.5), new Vector3d());
                if (player.distanceToSqr(seatWorld.x, seatWorld.y, seatWorld.z) > 16 * 16) {
                    if (vehicle != null) player.stopRiding();
                    player.teleportTo(seatWorld.x, seatWorld.y, seatWorld.z);
                    return false;
                }
                if (vehicle != null) player.stopRiding();
                if (!SeatBlock.isSeatOccupied(level, seat)) {
                    SeatBlock.sitDown(level, seat, player);
                    if (player.getVehicle() != null) return true;
                }
            }
            // si el asiento no aparece, al menos que no se quede atrás
        }

        Vector3d target = pose.transformPosition(rot.add(p.info.rel(), new Vector3d()), new Vector3d());
        double dx = target.x - player.getX(), dy = target.y - player.getY(), dz = target.z - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 0.6 * 0.6) {
            if (player.isPassenger()) player.stopRiding();
            player.teleportTo(target.x, target.y, target.z);
        }
        // misma velocidad que la nave para que no se aleje en el siguiente instante
        Pose3dc last = p.ship.lastPose();
        if (last != null) {
            Vector3d v = new Vector3d(pose.position()).sub(last.position());
            player.setDeltaMovement(v.x, v.y, v.z);
            player.hurtMarked = true;
        }
        return false;
    }

    /**
     * La copia de la nave en la nueva dimensión: de tamaño parecido (y de masa parecida si se conoce) y la más
     * cercana al jugador. Si hay una con la misma masa, gana aunque esté más lejos.
     */
    private static SubLevel findCopy(ServerLevel level, Vec3 near, double size, double mass) {
        SubLevel best = null, bestMass = null;
        double bestDist = Double.MAX_VALUE, bestMassDist = Double.MAX_VALUE;
        for (SubLevel s : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (s.isRemoved()) continue;
            BoundingBox3dc b = s.boundingBox();
            if (Math.abs(size(b) - size) > Math.max(3.0, size * 0.15)) continue;
            double cx = (b.minX() + b.maxX()) / 2, cy = (b.minY() + b.maxY()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
            double d = near.distanceTo(new Vec3(cx, cy, cz));
            if (d > 512 + size) continue;
            double m = mass(s);
            if (mass > 0 && m > 0 && Math.abs(m - mass) <= Math.max(1.0, mass * 0.02)) {
                if (d < bestMassDist) {
                    bestMassDist = d;
                    bestMass = s;
                }
            } else if (d < 64 + size && d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return bestMass != null ? bestMass : best;
    }

    // ------------------------------------------------------------------ 3) red de seguridad: jugador a la deriva

    /**
     * Un jugador en el espacio profundo fuera de cualquier instancia está a la deriva (se separó de la nave al cruzar).
     * A los 3 s se le devuelve a su última nave si está en el espacio profundo.
     */
    private static void rescueStranded(net.minecraft.server.MinecraftServer server) {
        ServerLevel deep = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().equals(DEEP_SPACE)) deep = l;
        }
        if (deep == null || deep.players().isEmpty()) {
            STRANDED.clear();
            return;
        }
        DeepSpaceData data = DeepSpaceData.getInstance(server);
        if (data == null) return;
        for (ServerPlayer player : deep.players()) {
            UUID id = player.getUUID();
            if (player.isSpectator() || PENDING.containsKey(id)) continue;
            DeepSpaceInstance inst = data.getInstanceForPos(player.getBlockX(), player.getBlockZ());
            boolean inside = inst != null && !inst.isCorrupted() && inst.boundingBox().contains(player.position());
            if (inside) {
                STRANDED.remove(id);
                continue;
            }
            int n = STRANDED.merge(id, 1, Integer::sum);
            if (n < 3) continue;
            OnShip home = HOME.get(id);
            SubLevel ship = null;
            if (home != null) {
                for (SubLevel s : SubLevelContainer.getContainer(deep).getAllSubLevels()) {
                    if (!s.isRemoved() && s.getUniqueId().equals(home.ship())) ship = s;
                }
            }
            if (ship == null) {
                if (n == 3) PlanetaryExpansion.LOGGER.warn("[Naves] {} está a la deriva en el espacio profundo y no se encontró su nave", player.getName().getString());
                continue;
            }
            Pose3dc pose = ship.logicalPose();
            Vector3d target = pose.transformPosition(new Vector3d(pose.rotationPoint()).add(home.rel()), new Vector3d());
            if (player.isPassenger()) player.stopRiding();
            player.teleportTo(target.x, target.y, target.z);
            player.setDeltaMovement(0, 0, 0);
            player.hurtMarked = true;
            STRANDED.remove(id);
            PENDING.put(id, new Pending(home)); // 4 s pegado a la nave (y de vuelta a su asiento si iba sentado)
            PlanetaryExpansion.LOGGER.info("[Naves] {} estaba a la deriva en el espacio profundo: devuelto a su nave", player.getName().getString());
        }
    }
}
