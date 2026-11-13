package dev.antme08.planetaryexpansion.weather;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nave congelada al desconectarse (0.7.17, idea aprobada por el usuario tras perder dos naves).
 *
 * Problema: si el jugador se sale del mundo con la nave en el aire, al volver la nave se carga con su velocidad
 * antes que el jugador y se va (o cae), y el jugador aparece flotando donde estaba.
 *
 * Qué hace (solo en planetas; en el espacio profundo la "velocidad" es la órbita y Cosmonautics ya la guarda):
 *  1. Al desconectarse, si iba en una nave que está en el aire y no queda nadie más a bordo, apunta la velocidad
 *     lineal y angular de la nave, dónde iba el jugador (y su asiento de Create), se la quita y la fija en el sitio
 *     con una restricción fija contra el mundo (lo mismo que hace la varita física de Simulated).
 *  2. La nave queda congelada en el mundo guardado. Cada vez que se carga se vuelve a fijar.
 *  3. Al volver el jugador, se le vuelve a poner en su asiento (o en su sitio), cuenta atrás de 3 s en la barra de
 *     acción y la nave se suelta con exactamente la velocidad que llevaba.
 */
public final class ShipFreeze {
    private ShipFreeze() {}

    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final double MIN_SPEED = 0.3;      // m/s: por debajo se considera quieta
    private static final double MIN_CLEARANCE = 2.0;  // bloques sobre el terreno para contar como "en el aire"
    private static final int COUNTDOWN = 60;          // 3 s
    private static final int SETTLE = 10;             // ticks recolocando al jugador antes de la cuenta atrás

    // ------------------------------------------------------------------ datos guardados en el mundo

    static final class Entry {
        UUID ship;
        UUID owner;
        String ownerName = "";
        final Vector3d lin = new Vector3d();
        final Vector3d ang = new Vector3d();
        Vector3d rel;      // posición del jugador relativa al punto de rotación de la nave (coordenadas de la nave)
        Vector3d seatRel;  // asiento de Create relativo al punto de rotación, o null
        // en memoria, no se guarda:
        int settle;
        int countdown = -1;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("ship", ship);
            t.putUUID("owner", owner);
            t.putString("ownerName", ownerName);
            putVec(t, "lin", lin);
            putVec(t, "ang", ang);
            if (rel != null) putVec(t, "rel", rel);
            if (seatRel != null) putVec(t, "seat", seatRel);
            return t;
        }

        static Entry load(CompoundTag t) {
            Entry e = new Entry();
            e.ship = t.getUUID("ship");
            e.owner = t.getUUID("owner");
            e.ownerName = t.getString("ownerName");
            e.lin.set(getVec(t, "lin"));
            e.ang.set(getVec(t, "ang"));
            if (t.contains("rel")) e.rel = getVec(t, "rel");
            if (t.contains("seat")) e.seatRel = getVec(t, "seat");
            return e;
        }

        private static void putVec(CompoundTag t, String k, Vector3d v) {
            ListTag l = new ListTag();
            CompoundTag c = new CompoundTag();
            c.putDouble("x", v.x);
            c.putDouble("y", v.y);
            c.putDouble("z", v.z);
            l.add(c);
            t.put(k, l);
        }

        private static Vector3d getVec(CompoundTag t, String k) {
            ListTag l = t.getList(k, Tag.TAG_COMPOUND);
            if (l.isEmpty()) return new Vector3d();
            CompoundTag c = l.getCompound(0);
            return new Vector3d(c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
        }
    }

    public static final class Data extends SavedData {
        static final String ID = "planetary_expansion_frozen_ships";
        final Map<UUID, Entry> entries = new LinkedHashMap<>();

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), ID);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("ships", Tag.TAG_COMPOUND)) {
                try {
                    Entry e = Entry.load((CompoundTag) t);
                    d.entries.put(e.ship, e);
                } catch (Throwable ignored) {
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag l = new ListTag();
            for (Entry e : entries.values()) l.add(e.save());
            tag.put("ships", l);
            return tag;
        }
    }

    /** Restricciones activas (en memoria; se rehacen cuando la nave se vuelve a cargar). */
    private static final Map<UUID, PhysicsConstraintHandle> HANDLES = new HashMap<>();

    // ------------------------------------------------------------------ 1) al salir: congelar

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            freezeFor(player);
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo congelar la nave de {} al salir", player.getName().getString(), t);
        }
    }

    private static void freezeFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension().location().equals(DEEP_SPACE)) return;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        List<ServerSubLevel> ships = container.getAllSubLevels();
        if (ships.isEmpty()) return;

        ServerSubLevel ship = shipAround(ships, player.position(), 1.5);
        if (ship == null || ship.isRemoved()) return;
        Data data = Data.get(level);
        if (data.entries.containsKey(ship.getUniqueId())) return;

        // si queda otro jugador a bordo, él sigue manejando: no se toca
        for (ServerPlayer other : level.players()) {
            if (other == player) continue;
            if (shipAround(ships, other.position(), 3.0) == ship) return;
        }

        RigidBodyHandle body = RigidBodyHandle.of(ship);
        if (body == null || !body.isValid()) return;
        Vector3d lin = body.getLinearVelocity(new Vector3d());
        Vector3d ang = body.getAngularVelocity(new Vector3d());

        BoundingBox3dc b = ship.boundingBox();
        int cx = (int) Math.floor((b.minX() + b.maxX()) / 2), cz = (int) Math.floor((b.minZ() + b.maxZ()) / 2);
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        boolean airborne = lin.length() > MIN_SPEED || b.minY() - ground > MIN_CLEARANCE;
        if (!airborne) return;

        Entry e = new Entry();
        e.ship = ship.getUniqueId();
        e.owner = player.getUUID();
        e.ownerName = player.getName().getString();
        e.lin.set(lin);
        e.ang.set(ang);
        Pose3dc pose = ship.logicalPose();
        Vector3d plot = pose.transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()), new Vector3d());
        e.rel = plot.sub(pose.rotationPoint(), new Vector3d());
        Entity vehicle = player.getVehicle();
        if (vehicle != null && container.inBounds(vehicle.blockPosition())) {
            BlockPos seat = vehicle.blockPosition();
            if (level.getBlockState(seat).getBlock() instanceof SeatBlock) {
                e.seatRel = new Vector3d(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5).sub(pose.rotationPoint());
            }
        }

        body.addLinearAndAngularVelocity(new Vector3d(lin).negate(), new Vector3d(ang).negate());
        lock(container, ship);
        data.entries.put(e.ship, e);
        data.setDirty();
        PlanetaryExpansion.LOGGER.info("[Naves] {} salió con la nave en el aire: nave congelada (velocidad {} m/s guardada)",
                e.ownerName, String.format("%.1f", lin.length()));
    }

    private static void lock(ServerSubLevelContainer container, ServerSubLevel ship) {
        PhysicsConstraintHandle old = HANDLES.remove(ship.getUniqueId());
        if (old != null && old.isValid()) old.remove();
        Pose3d pose = ship.logicalPose();
        PhysicsConstraintHandle h = container.physicsSystem().getPipeline().addConstraint(null, ship,
                new FixedConstraintConfiguration(pose.position(), pose.rotationPoint(), pose.orientation()));
        HANDLES.put(ship.getUniqueId(), h);
    }

    private static void unlock(UUID ship) {
        PhysicsConstraintHandle h = HANDLES.remove(ship);
        if (h != null && h.isValid()) h.remove();
    }

    // ------------------------------------------------------------------ 2) mientras tanto: mantenerla fija

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Data data;
            try {
                data = Data.get(level);
            } catch (Throwable t) {
                continue;
            }
            if (data.entries.isEmpty()) continue;
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            Iterator<Entry> it = data.entries.values().iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                try {
                    if (tickEntry(level, container, e)) {
                        it.remove();
                        data.setDirty();
                    }
                } catch (Throwable t) {
                    PlanetaryExpansion.LOGGER.warn("[Naves] Error con la nave congelada {}", e.ship, t);
                }
            }
        }
    }

    /** @return true si la nave ya se soltó (o ya no existe) y hay que borrar la entrada. */
    private static boolean tickEntry(ServerLevel level, ServerSubLevelContainer container, Entry e) {
        SubLevel sub = container.getSubLevel(e.ship);
        if (!(sub instanceof ServerSubLevel ship) || ship.isRemoved()) {
            HANDLES.remove(e.ship);
            e.settle = 0;
            e.countdown = -1;
            holdOwner(level, e); // que el dueño no caiga mientras la nave aún no ha cargado
            return false; // no está cargada: se fijará cuando se cargue
        }
        PhysicsConstraintHandle h = HANDLES.get(e.ship);
        if (h == null || !h.isValid()) {
            RigidBodyHandle body = RigidBodyHandle.of(ship);
            if (body != null && body.isValid()) {
                Vector3d lin = body.getLinearVelocity(new Vector3d()), ang = body.getAngularVelocity(new Vector3d());
                body.addLinearAndAngularVelocity(lin.negate(), ang.negate());
            }
            lock(container, ship);
        }

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(e.owner);
        if (owner == null || owner.serverLevel() != level) {
            e.settle = 0;
            e.countdown = -1;
            return false;
        }
        // espera congelada hasta que el dueño esté junto a su nave
        boolean near = shipAround(container.getAllSubLevels(), owner.position(), 6.0) == ship;
        if (!near && (e.rel == null || distanceToSpot(ship, e, owner) > 24)) {
            e.settle = 0;
            e.countdown = -1;
            return false;
        }
        HOLD.remove(e.owner);

        if (e.settle < SETTLE) {
            placePlayer(level, ship, e, owner);
            e.settle++;
            return false;
        }
        if (e.countdown < 0) e.countdown = COUNTDOWN;
        placePlayer(level, ship, e, owner);
        if (e.countdown % 20 == 0 && e.countdown > 0) {
            owner.displayClientMessage(Component.translatable("message.planetary_expansion.ship_unfreeze", e.countdown / 20), true);
        }
        if (--e.countdown > 0) return false;

        // soltar con la velocidad que llevaba
        unlock(e.ship);
        RigidBodyHandle body = RigidBodyHandle.of(ship);
        if (body != null && body.isValid()) body.addLinearAndAngularVelocity(e.lin, e.ang);
        if (!owner.isPassenger()) {
            owner.setDeltaMovement(e.lin.x / 20.0, e.lin.y / 20.0, e.lin.z / 20.0);
            owner.hurtMarked = true;
        }
        owner.displayClientMessage(Component.translatable("message.planetary_expansion.ship_released"), true);
        PlanetaryExpansion.LOGGER.info("[Naves] Nave de {} soltada con su velocidad ({} m/s)", e.ownerName,
                String.format("%.1f", e.lin.length()));
        return true;
    }

    // ------------------------------------------------------------------ al volver: que no caiga antes de tiempo

    private record Hold(Vec3 pos, int[] ticks) {}

    private static final Map<UUID, Hold> HOLD = new HashMap<>();

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            for (Entry e : Data.get(player.serverLevel()).entries.values()) {
                if (e.owner.equals(player.getUUID())) {
                    HOLD.put(player.getUUID(), new Hold(player.position(), new int[]{200}));
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void holdOwner(ServerLevel level, Entry e) {
        Hold h = HOLD.get(e.owner);
        if (h == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.owner);
        if (p == null || p.serverLevel() != level || --h.ticks()[0] < 0) {
            HOLD.remove(e.owner);
            return;
        }
        if (p.position().distanceToSqr(h.pos()) > 0.25) p.teleportTo(h.pos().x, h.pos().y, h.pos().z);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        p.hurtMarked = true;
    }

    private static double distanceToSpot(ServerSubLevel ship, Entry e, ServerPlayer p) {
        Pose3dc pose = ship.logicalPose();
        Vector3d target = pose.transformPosition(new Vector3d(pose.rotationPoint()).add(e.rel), new Vector3d());
        return target.distance(p.getX(), p.getY(), p.getZ());
    }

    /** Lo sienta en su asiento o lo pone donde estaba dentro de la nave, quieto respecto a ella. */
    private static void placePlayer(ServerLevel level, ServerSubLevel ship, Entry e, ServerPlayer player) {
        Pose3dc pose = ship.logicalPose();
        Vector3d rot = new Vector3d(pose.rotationPoint());
        if (e.seatRel != null) {
            Vector3d seatPlot = new Vector3d(rot).add(e.seatRel);
            BlockPos seat = BlockPos.containing(seatPlot.x, seatPlot.y, seatPlot.z);
            if (level.getBlockState(seat).getBlock() instanceof SeatBlock) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null && vehicle.blockPosition().equals(seat)) return;
                if (vehicle != null) player.stopRiding();
                if (!SeatBlock.isSeatOccupied(level, seat)) {
                    SeatBlock.sitDown(level, seat, player);
                    if (player.getVehicle() != null) return;
                }
            }
        }
        if (e.rel == null || player.isPassenger()) return;
        Vector3d target = pose.transformPosition(new Vector3d(rot).add(e.rel), new Vector3d());
        if (target.distanceSquared(player.getX(), player.getY(), player.getZ()) > 0.5 * 0.5) {
            player.teleportTo(target.x, target.y, target.z);
        }
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.hurtMarked = true;
    }

    private static ServerSubLevel shipAround(List<ServerSubLevel> ships, Vec3 p, double margin) {
        ServerSubLevel best = null;
        double bestSize = Double.MAX_VALUE;
        for (ServerSubLevel s : ships) {
            if (s.isRemoved()) continue;
            BoundingBox3dc b = s.boundingBox();
            if (p.x < b.minX() - margin || p.x > b.maxX() + margin || p.y < b.minY() - margin || p.y > b.maxY() + margin
                    || p.z < b.minZ() - margin || p.z > b.maxZ() + margin) continue;
            double dx = b.maxX() - b.minX(), dy = b.maxY() - b.minY(), dz = b.maxZ() - b.minZ();
            double sz = dx * dx + dy * dy + dz * dz;
            if (sz < bestSize) {
                bestSize = sz;
                best = s;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HANDLES.clear();
        HOLD.clear();
    }
}
