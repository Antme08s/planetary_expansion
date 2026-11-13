package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.InstanceList;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Órbitas perdidas al llegar al espacio profundo sin cohete (0.7.22, bug encontrado por el usuario con Waystones).
 *
 * Cosmonautics guarda la órbita de cada nave en una "instancia" del espacio profundo. Si el jugador se va de la
 * instancia sin volar (teletransporte, waystone, muerte...), la instancia puede quedarse vacía y Cosmonautics la
 * retira, aunque la nave siga allí. Al volver con un waystone el jugador y la nave están en el espacio profundo
 * pero fuera de cualquier órbita: no sale el HUD del casco (ORB/ALT/SPD/ECC), la mesa de hologramas no dibuja nada
 * y los motores no cambian ninguna órbita.
 *
 * Arreglo: cada segundo se guarda una copia de la órbita de cada instancia (cuerpo, fecha, posición y velocidad) en
 * el mundo. Si un jugador está en el espacio profundo en un sitio sin instancia y había una guardada ahí, se vuelve a
 * crear la instancia en el mismo hueco con su órbita (Cosmonautics la propaga hasta la fecha actual, como si nunca se
 * hubiera parado).
 */
public final class OrbitRescue {
    private OrbitRescue() {}

    private static boolean failed;

    // ------------------------------------------------------------------ copias guardadas

    private record Snapshot(int power, int idWithin, int negX, int negZ, int side, String frame, double seconds,
                            double px, double py, double pz, double vx, double vy, double vz) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("power", power);
            t.putInt("id", idWithin);
            t.putInt("negX", negX);
            t.putInt("negZ", negZ);
            t.putInt("side", side);
            t.putString("frame", frame);
            t.putDouble("t", seconds);
            t.putDouble("px", px);
            t.putDouble("py", py);
            t.putDouble("pz", pz);
            t.putDouble("vx", vx);
            t.putDouble("vy", vy);
            t.putDouble("vz", vz);
            return t;
        }

        static Snapshot load(CompoundTag t) {
            return new Snapshot(t.getInt("power"), t.getInt("id"), t.getInt("negX"), t.getInt("negZ"), t.getInt("side"),
                    t.getString("frame"), t.getDouble("t"), t.getDouble("px"), t.getDouble("py"), t.getDouble("pz"),
                    t.getDouble("vx"), t.getDouble("vy"), t.getDouble("vz"));
        }

        boolean contains(double x, double z) {
            return x >= negX && x < negX + side && z >= negZ && z < negZ + side;
        }
    }

    public static final class Data extends SavedData {
        static final String ID = "planetary_expansion_orbit_snapshots";
        final Map<Long, Snapshot> snapshots = new HashMap<>();
        /** Último planeta del que vino cada jugador (para la órbita de emergencia). */
        final Map<java.util.UUID, String> lastPlanet = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), ID);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("orbits", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                try {
                    d.snapshots.put(c.getLong("key"), Snapshot.load(c));
                } catch (Throwable ignored) {
                }
            }
            CompoundTag lp = tag.getCompound("lastPlanet");
            for (String k : lp.getAllKeys()) {
                try {
                    d.lastPlanet.put(java.util.UUID.fromString(k), lp.getString(k));
                } catch (Throwable ignored) {
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag l = new ListTag();
            snapshots.forEach((k, s) -> {
                CompoundTag c = s.save();
                c.putLong("key", k);
                l.add(c);
            });
            tag.put("orbits", l);
            CompoundTag lp = new CompoundTag();
            lastPlanet.forEach((k, v) -> lp.putString(k.toString(), v));
            tag.put("lastPlanet", lp);
            return tag;
        }
    }

    // ------------------------------------------------------------------ acceso a los campos privados de Cosmonautics

    private static Field dataInstances, listInstances, listFreed, listPower;

    private static void initReflection() throws ReflectiveOperationException {
        if (dataInstances != null) return;
        dataInstances = DeepSpaceData.class.getDeclaredField("instances");
        listInstances = InstanceList.class.getDeclaredField("instances");
        listFreed = InstanceList.class.getDeclaredField("freedIDs");
        listPower = InstanceList.class.getDeclaredField("chunkPowerSize");
        for (Field f : new Field[]{dataInstances, listInstances, listFreed, listPower}) f.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private static Int2ObjectMap<InstanceList> lists(DeepSpaceData data) throws IllegalAccessException {
        return (Int2ObjectMap<InstanceList>) dataInstances.get(data);
    }

    @SuppressWarnings("unchecked")
    private static Int2ObjectAVLTreeMap<DeepSpaceInstance> instances(InstanceList list) throws IllegalAccessException {
        return (Int2ObjectAVLTreeMap<DeepSpaceInstance>) listInstances.get(list);
    }

    // ------------------------------------------------------------------ cada segundo

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (failed) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) return;
        try {
            initReflection();
            DeepSpaceData data = DeepSpaceData.getInstance(server);
            if (data == null || data.getUniverse() == null) return;
            Data saved = Data.get(server);
            snapshotAll(data, saved);
            rescuePlayers(server, data, saved);
        } catch (Throwable t) {
            failed = true;
            PlanetaryExpansion.LOGGER.warn("[Órbitas] Desactivado el rescate de órbitas (cambió Cosmonautics?)", t);
        }
    }

    private static void snapshotAll(DeepSpaceData data, Data saved) throws IllegalAccessException {
        boolean dirty = false;
        for (InstanceList list : lists(data).values()) {
            int power = listPower.getInt(list);
            for (Int2ObjectMap.Entry<DeepSpaceInstance> e : instances(list).int2ObjectEntrySet()) {
                DeepSpaceInstance inst = e.getValue();
                if (inst.isCorrupted()) continue;
                DeepSpacePosition pos = inst.getPosition();
                TimeStampedPVCoordinates pv = pos.getCurrentPVCoords();
                if (pv == null || pos.getFrame() == null) continue;
                Vector3D p = pv.getPosition(), v = pv.getVelocity();
                saved.snapshots.put(inst.getId(), new Snapshot(power, e.getIntKey(), inst.getNegXCorner(), inst.getNegZCorner(),
                        inst.getSideLength(), pos.getFrame().getName(), pv.getDate().durationFrom(DeepSpaceHelper.EPOCH),
                        p.getX(), p.getY(), p.getZ(), v.getX(), v.getY(), v.getZ()));
                dirty = true;
            }
        }
        if (dirty) saved.setDirty();
    }

    private static void rescuePlayers(MinecraftServer server, DeepSpaceData data, Data saved) throws IllegalAccessException {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (DeepSpaceHelper.isDeepSpace(level)) players.addAll(level.players());
        }
        for (ServerPlayer player : players) {
            if (data.getInstanceForPos(player.getBlockX(), player.getBlockZ()) != null) continue;
            boolean done = false;
            for (Map.Entry<Long, Snapshot> e : saved.snapshots.entrySet()) {
                Snapshot s = e.getValue();
                if (!s.contains(player.getX(), player.getZ())) continue;
                done = true;
                if (restore(data, s, e.getKey())) {
                    PlanetaryExpansion.LOGGER.info("[Órbitas] {} estaba en el espacio profundo sin órbita: restaurada la instancia {} (órbita guardada alrededor de {})",
                            player.getName().getString(), e.getKey(), s.frame());
                }
                break;
            }
            if (!done) emergencyOrbit(data, saved, player);
        }
    }

    /**
     * No hay copia guardada (la instancia se perdió antes de existir este arreglo): órbita circular baja (100 km)
     * alrededor del último planeta del que vino el jugador, o de la Tierra si no se sabe.
     */
    private static void emergencyOrbit(DeepSpaceData data, Data saved, ServerPlayer player) throws IllegalAccessException {
        int[] slot = DeepSpaceData.getChunkPowerSizeIdWithinSizeForParameters(player.getBlockX(), player.getBlockZ());
        if (slot == null || slot.length < 2) return;
        // 0.7.33: solo si el jugador quedaría dentro de la caja de esa instancia. Si está a la deriva entre
        // instancias (o por debajo de y 1000), crearla no sirve: Cosmonautics la retira y se repetía cada segundo.
        // A esos jugadores los devuelve a su nave ShipTransferGuard.
        ChunkPos corner = DeepSpaceData.getMinCornerForParameters(slot[0], slot[1]);
        int side = (2 << slot[0]) * 16;
        if (player.getBlockX() < corner.getMinBlockX() || player.getBlockX() >= corner.getMinBlockX() + side
                || player.getBlockZ() < corner.getMinBlockZ() || player.getBlockZ() >= corner.getMinBlockZ() + side
                || player.getY() < DeepSpaceData.LOGICAL_INSTANCE_HEIGHT) return;
        UniverseDefinition universe = data.getUniverse();
        String body = saved.lastPlanet.getOrDefault(player.getUUID(), null);
        dev.devce.rocketnautics.content.orbit.universe.CubePlanet planet = null;
        for (var p : universe.getPlanets()) {
            if (p.frame().getName().equals(body)) planet = p;
        }
        if (planet == null) planet = universe.getPlanetByDimension(net.minecraft.world.level.Level.OVERWORLD);
        if (planet == null) return;
        double mu = -1;
        for (var g : universe.getGravitySources()) {
            if (g.frame().getName().equals(planet.frame().getName())) mu = g.mu();
        }
        if (!(mu > 0)) return;
        double r = planet.radius() + 100_000.0;
        double v = Math.sqrt(mu / r);
        Snapshot s = new Snapshot(slot[0], slot[1], 0, 0, 0, planet.frame().getName(),
                data.getUniverseTime().durationFrom(DeepSpaceHelper.EPOCH), r, 0, 0, 0, 0, v);
        if (restore(data, s, DeepSpaceData.pack(slot[0], slot[1]))) {
            PlanetaryExpansion.LOGGER.info("[Órbitas] {} estaba en el espacio profundo sin órbita y sin copia guardada: órbita de emergencia a 100 km de {}",
                    player.getName().getString(), planet.frame().getName());
        }
    }

    /** Recuerda de qué planeta sale cada jugador hacia el espacio profundo. */
    @SubscribeEvent
    public static void onChangeDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            DeepSpaceData data = DeepSpaceData.getInstance(player.server);
            if (data == null || data.getUniverse() == null) return;
            var planet = data.getUniverse().getPlanetByDimension(event.getFrom());
            if (planet == null) return;
            Data saved = Data.get(player.server);
            saved.lastPlanet.put(player.getUUID(), planet.frame().getName());
            saved.setDirty();
        } catch (Throwable ignored) {
        }
    }

    private static boolean restore(DeepSpaceData data, Snapshot s, long packed) throws IllegalAccessException {
        Int2ObjectMap<InstanceList> all = lists(data);
        InstanceList list = all.get(s.power());
        if (list == null) {
            list = new InstanceList(s.power());
            all.put(s.power(), list);
        }
        Int2ObjectAVLTreeMap<DeepSpaceInstance> map = instances(list);
        if (map.containsKey(s.idWithin())) return false; // el hueco ya lo usa otra instancia

        // sacar el id de la cola de huecos libres (no tiene "remove": se rehace sin él)
        IntHeapPriorityQueue freed = (IntHeapPriorityQueue) listFreed.get(list);
        IntHeapPriorityQueue rebuilt = new IntHeapPriorityQueue();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        while (!freed.isEmpty()) {
            int id = freed.dequeueInt();
            if (id != s.idWithin() && seen.add(id)) rebuilt.enqueue(id);
        }
        // Cosmonautics da a las instancias nuevas el id = tamaño de la lista cuando no hay huecos libres: los ids
        // por debajo del que ocupamos y que no existen se marcan como libres para que no se pise el nuestro.
        for (int id = 0; id < s.idWithin(); id++) {
            if (!map.containsKey(id) && seen.add(id)) rebuilt.enqueue(id);
        }
        listFreed.set(list, rebuilt);

        ChunkPos corner = DeepSpaceData.getMinCornerForParameters(s.power(), s.idWithin());
        DeepSpaceInstance inst = new DeepSpaceInstance(data, 2 << s.power(), corner, DeepSpaceData.pack(s.power(), s.idWithin()));
        UniverseDefinition universe = data.getUniverse();
        inst.getPosition().init(universe, s.frame(), new TimeStampedPVCoordinates(DeepSpaceHelper.EPOCH.shiftedBy(s.seconds()),
                new Vector3D(s.px(), s.py(), s.pz()), new Vector3D(s.vx(), s.vy(), s.vz())));
        map.put(s.idWithin(), inst);
        inst.forceClientSync();
        data.setDirty();
        return true;
    }
}
