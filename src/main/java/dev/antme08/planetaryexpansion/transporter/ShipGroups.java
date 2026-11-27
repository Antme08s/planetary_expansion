package dev.antme08.planetaryexpansion.transporter;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.egg.DimensionalSable;
import dev.egg.registries.BlockEntityAccessor;
import dev.egg.registries.BlockEntityRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 1.0.5: naves unidas que salen (o entran) juntas a un planeta.
 * <p>
 * Cosmonautics ya pasa de dimensión, junto con una nave, todas las que Sable sabe que están unidas a ella (resortes,
 * cuerdas, articulaciones…: bloques que declaran la unión), y trae arreglos para que las uniones de Create y Simulated
 * sigan apuntando a la nave correcta al llegar (cada nave cambia de id y de sitio al viajar). Aquí:
 * <ul>
 *   <li><b>Imanes</b>: dos imanes de redstone de Simulated que se atraen no son una unión para Sable, así que la nave
 *   sujeta con imanes se quedaba atrás. Se añaden al grupo las naves con imanes emparejados con los del grupo.</li>
 *   <li><b>Lo que va encima o dentro</b> (un vehículo de Aeronautics aparcado en la cubierta, una caja con física en
 *   la bodega): viaja con la nave que lo lleva, aunque no esté sujeto ({@link #resting}).</li>
 *   <li><b>Uniones de otros mods</b> (Gadgets &amp; Gizmos, Absolute Kinematics, Aeronautics Utility Objects): se
 *   registran arreglos para que, al llegar, apunten a la nueva id de la otra nave y a la nueva posición de su bloque.</li>
 *   <li>Cualquier otra referencia a una nave del grupo que quede en los datos de un bloque (su id) se cambia a la id
 *   nueva ({@link #remapIds}); las ids son únicas, así que solo cambia lo que de verdad apunta a esa nave.</li>
 * </ul>
 */
public final class ShipGroups {
    private ShipGroups() {}

    private static final int MAX_GROUP = 64;
    private static boolean magnetsFailed;
    private static Field magnetMapField, pairMapField, pos1Field, pos2Field, aliveField;

    /** El grupo que viaja con esta nave: las unidas según Sable, más las sujetas con imanes (y las unidas a esas). */
    public static Collection<SubLevel> chain(SubLevel start) {
        return group(start, true);
    }

    /** Igual que {@link #chain}, sin apuntarlo en el registro (para lo que se llama en cada tick). */
    public static Collection<SubLevel> group(SubLevel start, boolean log) {
        Collection<SubLevel> base = SubLevelHelper.getConnectedChain(start);
        Set<SubLevel> group = new LinkedHashSet<>(base);
        int magnets = 0, carried = 0, loaded = 0;
        Level level = start.getLevel();
        Map<SubLevel, Set<SubLevel>> pairs = Map.of();
        try {
            pairs = magnetPairs(level);
        } catch (Throwable t) {
            if (!magnetsFailed) {
                magnetsFailed = true;
                PlanetaryExpansion.LOGGER.warn("[Naves] No se pudieron mirar los imanes al cambiar de dimensión", t);
            }
        }
        ArrayDeque<SubLevel> todo = new ArrayDeque<>(group);
        while (!todo.isEmpty() && group.size() < MAX_GROUP) {
            SubLevel s = todo.poll();
            Set<SubLevel> next = new LinkedHashSet<>();
            for (SubLevel other : pairs.getOrDefault(s, Set.of()))
                if (!group.contains(other) && next.add(other)) magnets++;
            for (SubLevel other : remnantPieces(level, s))
                if (!group.contains(other) && next.add(other)) loaded++;
            // lo que la nave necesita cargado con ella (p. ej. las patas de un caminante de Let It Walk, que no
            // declaran unión pero sí que se cargan juntas)
            if (s instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ss) {
                try {
                    for (SubLevel other : SubLevelHelper.getLoadingDependencyChain(ss))
                        if (!group.contains(other) && next.add(other)) loaded++;
                } catch (Throwable ignored) {
                }
            }
            try {
                for (SubLevel other : resting(level, s))
                    if (!group.contains(other) && next.add(other)) carried++;
            } catch (Throwable t) {
                if (!restingFailed) {
                    restingFailed = true;
                    PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo mirar qué va encima de la nave", t);
                }
            }
            for (SubLevel other : next) {
                if (other == null || other.isRemoved() || other.getLevel() != level) continue;
                for (SubLevel linked : SubLevelHelper.getConnectedChain(other)) {
                    if (linked != null && !linked.isRemoved() && linked.getLevel() == level && group.add(linked))
                        todo.add(linked);
                }
            }
        }
        if (log && group.size() > 1) {
            PlanetaryExpansion.LOGGER.info("[Naves] Viajan juntas {} naves ({} unidas, {} con imanes, {} cargadas juntas, {} encima o dentro de otra)",
                    group.size(), base.size() - 1, magnets, loaded, carried);
        }
        return magnets == 0 && carried == 0 && loaded == 0 ? base : group;
    }

    private static boolean restingFailed;

    /**
     * Las naves (vehículos, bloques con física…) que van encima o dentro de esta, y la que la lleva a ella si es ella la
     * que va encima de otra: se tocan (las cajas, con medio bloque de margen), la pequeña tiene el centro sobre la planta
     * de la grande y no está por debajo de ella. "Pequeña" y "grande" por el tamaño de la caja.
     */
    private static Set<SubLevel> resting(Level level, SubLevel s) {
        Set<SubLevel> out = new LinkedHashSet<>();
        var box = s.boundingBox();
        if (box == null) return out;
        double m = 0.6;
        var search = new dev.ryanhcode.sable.companion.math.BoundingBox3d(new net.minecraft.world.phys.AABB(
                box.minX() - m, box.minY() - m, box.minZ() - m, box.maxX() + m, box.maxY() + m, box.maxZ() + m));
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, search)) {
            if (other == null || other == s || other.isRemoved() || other.getLevel() != level) continue;
            var ob = other.boundingBox();
            if (ob == null) continue;
            double vs = volume(box), vo = volume(ob);
            if (vo < vs ? carries(box, ob, m) : vo > vs && carries(ob, box, m)) out.add(other);
        }
        return out;
    }

    private static double volume(dev.ryanhcode.sable.companion.math.BoundingBox3dc b) {
        return Math.max(0, b.maxX() - b.minX()) * Math.max(0, b.maxY() - b.minY()) * Math.max(0, b.maxZ() - b.minZ());
    }

    /** ¿La caja grande {@code big} lleva a la pequeña {@code small}? */
    private static boolean carries(dev.ryanhcode.sable.companion.math.BoundingBox3dc big,
                                   dev.ryanhcode.sable.companion.math.BoundingBox3dc small, double m) {
        if (!small.intersects(big.minX() - m, big.minY() - m, big.minZ() - m, big.maxX() + m, big.maxY() + m, big.maxZ() + m))
            return false;
        double cx = (small.minX() + small.maxX()) / 2, cy = (small.minY() + small.maxY()) / 2, cz = (small.minZ() + small.maxZ()) / 2;
        return cx >= big.minX() && cx <= big.maxX() && cz >= big.minZ() && cz <= big.maxZ() && cy >= big.minY();
    }

    /** Naves unidas por pares de imanes de Simulated que se están atrayendo (en las dos direcciones). */
    @SuppressWarnings("unchecked")
    private static Map<SubLevel, Set<SubLevel>> magnetPairs(Level level) throws ReflectiveOperationException {
        Map<SubLevel, Set<SubLevel>> out = new HashMap<>();
        if (magnetsFailed) return out;
        if (magnetMapField == null) {
            Class<?> be;
            try {
                be = Class.forName("dev.simulated_team.simulated.content.blocks.redstone_magnet.RedstoneMagnetBlockEntity");
            } catch (ClassNotFoundException e) {
                magnetsFailed = true;   // sin Simulated
                return out;
            }
            magnetMapField = be.getField("GLOBAL_REDSTONE_MAGNET_MAP");
            Class<?> map = Class.forName("dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetMap");
            pairMapField = map.getField("pairMap");
            Class<?> pair = Class.forName("dev.simulated_team.simulated.content.blocks.redstone_magnet.MagnetPair");
            pos1Field = pair.getDeclaredField("blockPos1");
            pos2Field = pair.getDeclaredField("blockPos2");
            aliveField = pair.getField("alive");
            pos1Field.setAccessible(true);
            pos2Field.setAccessible(true);
        }
        Object magnetMap = magnetMapField.get(null);
        if (magnetMap == null) return out;
        Map<Object, Map<Object, Object>> byLevel = (Map<Object, Map<Object, Object>>) pairMapField.get(magnetMap);
        Map<Object, Object> pairs = byLevel == null ? null : byLevel.get(level);
        if (pairs == null) return out;
        for (Object p : pairs.values()) {
            if (p == null || !aliveField.getBoolean(p)) continue;
            SubLevel a = containing(level, (BlockPos) pos1Field.get(p));
            SubLevel b = containing(level, (BlockPos) pos2Field.get(p));
            if (a == null || b == null || a == b) continue;   // imán contra el mundo, o dentro de la misma nave
            out.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b);
            out.computeIfAbsent(b, k -> new LinkedHashSet<>()).add(a);
        }
        return out;
    }

    private static SubLevel containing(Level level, BlockPos pos) {
        if (pos == null) return null;
        SubLevelAccess s = Sable.HELPER.getContaining(level, (Vec3i) pos);
        return s instanceof SubLevel sub && !sub.isRemoved() ? sub : null;
    }

    // ---------------------------------------------------------------- referencias en los datos de los bloques

    /**
     * Arreglo de una unión: el bloque guarda la id de la otra nave en {@code idKey} y, en {@code posKeys}, posiciones de
     * bloques de esa nave (en sus coordenadas). Al llegar, la id pasa a ser la nueva y las posiciones se mueven con ella.
     * Si la otra nave no viaja en el grupo no se toca nada.
     */
    private static final class Link extends BlockEntityAccessor {
        private final String idKey;
        private final String[] posKeys;

        Link(String idKey, String... posKeys) {
            this.idKey = idKey;
            this.posKeys = posKeys;
        }

        @Override
        public CompoundTag modifyNBT(BlockEntityRegistry.BlockEntityInfo info) {
            CompoundTag tag = info.tag();
            try {
                if (tag != null && tag.hasUUID(idKey)) {
                    var to = info.moveInfo().oldToNewSubLevelMap().get(tag.getUUID(idKey));
                    if (to != null && to.first() != null) {
                        tag.putUUID(idKey, to.first());
                        Vec3i d = to.second();
                        if (d != null) for (String k : posKeys) offset(tag, k, d);
                    }
                }
            } catch (Throwable t) {
                PlanetaryExpansion.LOGGER.warn("[Naves] No se pudo arreglar la unión {} de {}", idKey, tag == null ? "?" : tag.getString("id"), t);
            }
            return tag;
        }
    }

    /** Mueve una posición guardada como lista de 3 enteros (lo normal), número largo o {X,Y,Z}. */
    private static void offset(CompoundTag tag, String key, Vec3i d) {
        if (!tag.contains(key)) return;
        Tag t = tag.get(key);
        if (t instanceof IntArrayTag arr && arr.getAsIntArray().length == 3) {
            int[] v = arr.getAsIntArray();
            tag.putIntArray(key, new int[]{v[0] + d.getX(), v[1] + d.getY(), v[2] + d.getZ()});
        } else if (t instanceof LongTag l) {
            tag.putLong(key, BlockPos.of(l.getAsLong()).offset(d).asLong());
        } else if (t instanceof CompoundTag c && c.contains("X") && c.contains("Y") && c.contains("Z")) {
            c.putInt("X", c.getInt("X") + d.getX());
            c.putInt("Y", c.getInt("Y") + d.getY());
            c.putInt("Z", c.getInt("Z") + d.getZ());
        }
    }

    /** Para una lista o compuesto anidado con pares {idKey, posKey} (p. ej. los enganches de la tirolesa). */
    private static final class NestedLink extends BlockEntityAccessor {
        private final String idKey, posKey;

        NestedLink(String idKey, String posKey) {
            this.idKey = idKey;
            this.posKey = posKey;
        }

        @Override
        public CompoundTag modifyNBT(BlockEntityRegistry.BlockEntityInfo info) {
            CompoundTag tag = info.tag();
            try {
                walk(tag, info.moveInfo().oldToNewSubLevelMap(), 0);
            } catch (Throwable ignored) {
            }
            return tag;
        }

        private void walk(Tag t, HashMap<UUID, DimensionalSable.Pair<UUID, Vec3i>> map, int depth) {
            if (depth > 12) return;
            if (t instanceof CompoundTag c) {
                if (c.hasUUID(idKey)) {
                    var to = map.get(c.getUUID(idKey));
                    if (to != null && to.first() != null) {
                        c.putUUID(idKey, to.first());
                        if (to.second() != null) offset(c, posKey, to.second());
                    }
                }
                for (String k : c.getAllKeys()) walk(c.get(k), map, depth + 1);
            } else if (t instanceof ListTag l) {
                for (Tag e : l) walk(e, map, depth + 1);
            }
        }
    }

    /**
     * Let It Walk: el núcleo del caminante guarda cada pata ("legs"; la construcción de cada una en "built"), con sus articulaciones: id de la pieza padre y de
     * la hija y el punto de la unión en las coordenadas de cada una ("pp" en la padre, "cp" en la hija, como ppx/ppy/ppz),
     * y el punto del pie ("footPlot") en la última pieza. Sin mover esos puntos, al llegar las uniones se rehacen en
     * otro sitio y la pata se rompe en piezas.
     */
    private static Vec3i delta(HashMap<UUID, DimensionalSable.Pair<UUID, Vec3i>> map, CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) return null;
        var to = map.get(tag.getUUID(key));
        return to == null ? null : to.second();
    }

    private static void offsetVec(CompoundTag tag, String key, Vec3i d) {
        if (d == null || !tag.contains(key + "x")) return;
        tag.putDouble(key + "x", tag.getDouble(key + "x") + d.getX());
        tag.putDouble(key + "y", tag.getDouble(key + "y") + d.getY());
        tag.putDouble(key + "z", tag.getDouble(key + "z") + d.getZ());
    }

    /** Mueve los puntos guardados de las patas de Let It Walk (lista "legs") con las piezas que viajan. */
    static void moveLegs(ListTag legs, HashMap<UUID, DimensionalSable.Pair<UUID, Vec3i>> map) {
        for (int i = 0; i < legs.size(); i++) {
            CompoundTag runtime = legs.getCompound(i);
            // v53: cada pata guarda su estado de marcha y, dentro, en "built", cómo está construida
            CompoundTag leg = runtime.contains("built", Tag.TAG_COMPOUND) ? runtime.getCompound("built") : runtime;
            // los puntos de apoyo del pie son del mundo de antes (otra dimensión, otro sitio): se olvidan y
            // el caminante vuelve a buscar apoyo al llegar, como recién armado (sin ellos quedan en 0,0,0)
            if (leg != runtime) {
                for (String k : new String[]{"a", "f", "t"})
                    for (String c : new String[]{"x", "y", "z"}) runtime.remove(k + c);
                runtime.putBoolean("grounded", false);
                runtime.putBoolean("swing", false);
            }
            ListTag joints = leg.getList("joints", Tag.TAG_COMPOUND);
            for (int j = 0; j < joints.size(); j++) {
                CompoundTag joint = joints.getCompound(j);
                offsetVec(joint, "pp", delta(map, joint, "parent"));
                offsetVec(joint, "cp", delta(map, joint, "child"));
            }
            ListTag segs = leg.getList("segments", Tag.TAG_COMPOUND);
            if (!segs.isEmpty()) offsetVec(leg, "footPlot", delta(map, segs.getCompound(segs.size() - 1), "id"));
        }
    }

    private static final class WalkerCore extends BlockEntityAccessor {
        @Override
        public CompoundTag modifyNBT(BlockEntityRegistry.BlockEntityInfo info) {
            CompoundTag tag = info.tag();
            try {
                moveLegs(tag.getList("legs", Tag.TAG_COMPOUND), info.moveInfo().oldToNewSubLevelMap());
            } catch (Throwable t) {
                PlanetaryExpansion.LOGGER.warn("[Naves] No se pudieron arreglar las patas del caminante de Let It Walk", t);
            }
            return tag;
        }

    }

    private static void link(String modid, String block, String idKey, String... posKeys) {
        BlockEntityRegistry.PublishCustomFixer(modid, block, new Link(idKey, posKeys));
    }

    /** Arreglos de las uniones de otros mods (los de Create y Simulated ya los trae Cosmonautics). */
    public static void registerFixers() {
        try {
            // Gadgets & Gizmos (createthrusters)
            String gg = "createthrusters";
            link(gg, "ship_coupler", "PartnerSubLevelId", "PartnerPosition");
            link(gg, "claw", "GrabbedSubLevelId", "GrabbedPos");
            link(gg, "scissor_piston", "MountedSubLevel", "MountedLocalPos");
            link(gg, "scissor_piston_arm", "ParentSubLevelId", "ParentPos");
            link(gg, "scissor_piston_link", "ParentSubLevelId", "ParentPos");
            link(gg, "physics_gantry_carriage", "AttachedShaftSubLevelId", "AttachedShaftPos");
            link(gg, "thruster_bearing_link", "ParentSubLevelId", "ParentPos");
            link(gg, "thruster_bearing_link", "ThrusterParentSubLevelId", "ThrusterParentPos");
            link(gg, "aileron_bearing", "MountedSubLevel", "MountedLocalPos");
            link(gg, "aileron_bearing_link", "ParentSubLevelId", "ParentPos");
            link(gg, "vector_bearing", "MountedSubLevel", "MountedLocalPos");
            link(gg, "vector_bearing_link", "ParentSubLevelId", "ParentPos");
            link(gg, "entity_launcher_anchor", "TargetKnotSubLevel", "TargetKnotPos");
            BlockEntityRegistry.PublishCustomFixer(gg, "powered_zipline", new NestedLink("SubLevelId", "BlockPos"));
            // Absolute Kinematics
            String ak = "absolute_kinematics";
            link(ak, "hinge", "SubLevelID", "JointPlate");
            link(ak, "ball_joint", "SubLevelID", "JointPlate");
            link(ak, "hinge_leaf", "ParentSubLevelId", "ParentPos");
            link(ak, "ball_joint_plate", "ParentSubLevelId", "ParentPos");
            // Aeronautics Utility Objects (junta universal, cabeza hidráulica)
            String tl = "aeronautics_utility_objects";
            link(tl, "universal_joint", "LinkedSubLevel", "LinkedPos");
            link(tl, "hydraulic_connection_head", "LinkedSubLevel", "LinkedPos");
            link(tl, "hydraulic_connection_head", "HingeSubLevel", "HingeLinkPos");
            link(tl, "hydraulic_connection_head", "HingeParentSubLevel", "HingeOwnerPos");
            // Let It Walk (las ids de las piezas las cambia remapIds después)
            BlockEntityRegistry.PublishCustomFixer("let_it_walk", "walker_core", new WalkerCore());
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudieron registrar los arreglos de uniones entre naves", t);
        }
    }

    /**
     * Después de todos los arreglos: cualquier id de una nave que viajó, guardada en los datos del bloque (como UUID o
     * como texto), pasa a la id nueva. Las posiciones no se tocan aquí (no se sabe a qué nave pertenecen).
     */
    public static void remapIds(CompoundTag tag, BlockEntityRegistry.MoveInfo info) {
        if (tag == null || info == null) return;
        remapIds(tag, info.oldToNewSubLevelMap());
    }

    static void remapIds(CompoundTag tag, HashMap<UUID, DimensionalSable.Pair<UUID, Vec3i>> map) {
        if (tag == null) return;
        if (map == null || map.isEmpty()) return;
        try {
            Map<UUID, UUID> ids = new HashMap<>();
            map.forEach((k, v) -> {
                if (k != null && v != null && v.first() != null) ids.put(k, v.first());
            });
            remap(tag, ids, 0);
        } catch (Throwable ignored) {
        }
    }

    private static void remap(Tag t, Map<UUID, UUID> ids, int depth) {
        if (depth > 16) return;
        if (t instanceof CompoundTag c) {
            for (String k : c.getAllKeys().toArray(new String[0])) {
                Tag v = c.get(k);
                if (v instanceof IntArrayTag arr && arr.size() == 4) {
                    UUID to = ids.get(NbtUtils.loadUUID(arr));
                    if (to != null) c.putUUID(k, to);
                } else if (v instanceof StringTag s && s.getAsString().length() == 36) {
                    UUID to = parse(s.getAsString(), ids);
                    if (to != null) c.putString(k, to.toString());
                } else {
                    remap(v, ids, depth + 1);
                }
            }
        } else if (t instanceof ListTag l) {
            for (int i = 0; i < l.size(); i++) {
                Tag v = l.get(i);
                if (v instanceof IntArrayTag arr && arr.size() == 4) {
                    UUID to = ids.get(NbtUtils.loadUUID(arr));
                    if (to != null) l.set(i, NbtUtils.createUUID(to));
                } else if (v instanceof StringTag s && s.getAsString().length() == 36) {
                    UUID to = parse(s.getAsString(), ids);
                    if (to != null) l.set(i, StringTag.valueOf(to.toString()));
                } else {
                    remap(v, ids, depth + 1);
                }
            }
        }
    }

    private static UUID parse(String s, Map<UUID, UUID> ids) {
        try {
            return ids.get(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- patas sueltas de Let It Walk

    /*
     * Cuando un caminante de Let It Walk pierde su núcleo (o se arma solo con patas), sus patas quedan "sueltas": las
     * piezas se mantienen unidas con articulaciones físicas que Let It Walk guarda aparte, en los datos de la dimensión
     * ("let_it_walk_remnants": por nave que las lleva, el núcleo y las patas, con el mismo formato que en el núcleo). Al
     * cambiar de dimensión, esos datos se quedaban en la dimensión de antes y en la nueva nadie volvía a unir las
     * piezas: llegaban separadas. Ahora viajan con la nave (y sus piezas viajan en el grupo).
     */

    private static java.lang.ref.WeakReference<net.minecraft.server.level.ServerLevel> lastSource;
    private static java.lang.ref.WeakReference<Object> remnantsMoved;
    private static boolean remnantsFailed;
    private static java.lang.reflect.Method remnantsGet, remnantsRelease;
    private static Field remnantsEntries;

    /** Al guardar una nave para el viaje: de qué dimensión sale. */
    public static void noteSource(dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot) {
        try {
            if (plot.getSubLevel().getLevel() instanceof net.minecraft.server.level.ServerLevel l)
                lastSource = new java.lang.ref.WeakReference<>(l);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, CompoundTag> remnants(net.minecraft.server.level.ServerLevel level) throws ReflectiveOperationException {
        if (remnantsGet == null) {
            Class<?> r = Class.forName("dev.letitwalk.walker.WalkerRemnants");
            remnantsGet = r.getDeclaredMethod("get", net.minecraft.server.level.ServerLevel.class);
            remnantsGet.setAccessible(true);
            remnantsEntries = r.getDeclaredField("entries");
            remnantsEntries.setAccessible(true);
            try {
                remnantsRelease = r.getDeclaredMethod("releaseRagdoll", UUID.class);
                remnantsRelease.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
            }
        }
        Object data = remnantsGet.invoke(null, level);
        return data == null ? null : (Map<UUID, CompoundTag>) remnantsEntries.get(data);
    }

    private static void dirty(net.minecraft.server.level.ServerLevel level) throws ReflectiveOperationException {
        Object data = remnantsGet.invoke(null, level);
        if (data instanceof net.minecraft.world.level.saveddata.SavedData sd) sd.setDirty();
    }

    /** Al cargar la primera nave del viaje en la dimensión nueva: pasa allí las patas sueltas de las naves del viaje. */
    public static void moveRemnants(dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot, BlockEntityRegistry.MoveInfo info) {
        if (remnantsFailed || info == null) return;
        var map = info.oldToNewSubLevelMap();
        if (map == null || map.isEmpty() || (remnantsMoved != null && remnantsMoved.get() == map)) return;
        remnantsMoved = new java.lang.ref.WeakReference<>(map);
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("let_it_walk")) {
                remnantsFailed = true;
                return;
            }
            var src = lastSource == null ? null : lastSource.get();
            if (src == null || !(plot.getSubLevel().getLevel() instanceof net.minecraft.server.level.ServerLevel dst)) return;
            Map<UUID, CompoundTag> from = remnants(src), to = remnants(dst);
            if (from == null || to == null) return;
            int moved = 0;
            for (UUID hull : new ArrayList<>(from.keySet())) {
                var pair = map.get(hull);
                if (pair == null || pair.first() == null) continue;
                CompoundTag data = from.remove(hull).copy();
                if (data.contains("core", Tag.TAG_LONG) && pair.second() != null)
                    data.putLong("core", BlockPos.of(data.getLong("core")).offset(pair.second()).asLong());
                moveLegs(data.getList("legs", Tag.TAG_COMPOUND), map);
                remapIds(data, map);
                to.put(pair.first(), data);
                if (remnantsRelease != null) {
                    try {
                        remnantsRelease.invoke(null, hull);
                    } catch (Throwable ignored) {
                    }
                }
                moved++;
            }
            if (moved > 0) {
                dirty(src);
                dirty(dst);
                PlanetaryExpansion.LOGGER.info("[Naves] Patas sueltas de Let It Walk que viajan con su nave: {}", moved);
            }
        } catch (Throwable t) {
            remnantsFailed = true;
            PlanetaryExpansion.LOGGER.warn("[Naves] No se pudieron pasar las patas sueltas de Let It Walk a la nueva dimensión", t);
        }
    }

    /** Las piezas de las patas sueltas que lleva esta nave (para que viajen en el grupo). */
    private static Set<SubLevel> remnantPieces(Level level, SubLevel hull) {
        Set<SubLevel> out = new LinkedHashSet<>();
        if (remnantsFailed || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return out;
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("let_it_walk")) return out;
            Map<UUID, CompoundTag> all = remnants(sl);
            CompoundTag data = all == null ? null : all.get(hull.getUniqueId());
            if (data == null) return out;
            Set<UUID> ids = new LinkedHashSet<>();
            ListTag legs = data.getList("legs", Tag.TAG_COMPOUND);
            for (int i = 0; i < legs.size(); i++) {
                CompoundTag leg = legs.getCompound(i);
                if (leg.contains("built", Tag.TAG_COMPOUND)) leg = leg.getCompound("built");
                ListTag segs = leg.getList("segments", Tag.TAG_COMPOUND);
                for (int j = 0; j < segs.size(); j++) if (segs.getCompound(j).hasUUID("id")) ids.add(segs.getCompound(j).getUUID("id"));
                ListTag joints = leg.getList("joints", Tag.TAG_COMPOUND);
                for (int j = 0; j < joints.size(); j++) {
                    CompoundTag jt = joints.getCompound(j);
                    if (jt.hasUUID("parent")) ids.add(jt.getUUID("parent"));
                    if (jt.hasUUID("child")) ids.add(jt.getUUID("child"));
                }
            }
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            for (UUID id : ids) {
                if (container.getSubLevel(id) instanceof SubLevel piece && !piece.isRemoved()) out.add(piece);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
