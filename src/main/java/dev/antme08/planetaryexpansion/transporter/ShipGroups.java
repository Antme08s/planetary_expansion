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
        int magnets = 0, carried = 0;
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
            PlanetaryExpansion.LOGGER.info("[Naves] Viajan juntas {} naves ({} sujetas con imanes, {} encima o dentro de otra)",
                    group.size(), magnets, carried);
        }
        return magnets == 0 && carried == 0 ? base : group;
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
        var map = info.oldToNewSubLevelMap();
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
}
