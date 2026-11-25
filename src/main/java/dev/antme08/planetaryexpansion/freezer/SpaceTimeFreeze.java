package dev.antme08.planetaryexpansion.freezer;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.advancement.Achievements;
import dev.antme08.planetaryexpansion.warp.Warp;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Congelador del tiempo espacial (1.0.4, versión 2): la nave queda fija respecto al cuerpo que orbita, sin parar el tiempo.
 *
 * Cosmonautics guarda para cada instancia del espacio profundo una órbita kepleriana alrededor del cuerpo dominante (el de
 * la zona de influencia en la que está) y un reloj propio que avanza al mismo ritmo que el del universo. Aquí el reloj sigue
 * avanzando igual que para todos (mismos planetas, mismo día y noche), pero en cada tick la nave vuelve a colocarse en la
 * misma posición respecto a ese cuerpo y con la misma velocidad guardada. Así, si orbitas un planeta viajas con él alrededor
 * del Sol, y si orbitas el Sol te quedas quieto viendo pasar los planetas.
 *
 * El empuje de los motores se aplica antes de propagar (DeepSpaceInstance.tick) y cambia la velocidad en ese instante; aquí se
 * lee y pasa a ser la nueva velocidad guardada. Al soltar la señal la nave sigue con esa velocidad desde el punto donde está.
 * La rotación de la nave (física de Sable) no se toca.
 *
 * Para el cliente se manda {@code timescale = 0} (no extrapola la órbita entre sincronizaciones, así la nave no tiembla) y se
 * fuerza una sincronización por tick, que es la que hace avanzar el cielo. En el servidor el reloj lo avanza
 * {@link #propagateFrozen} con el ritmo de antes.
 *
 * Persistente: Cosmonautics hace avanzar todas las instancias cada tick aunque sus chunks no estén cargados, así que la nave
 * sigue fija aunque nadie esté cerca, y el estado se guarda en el mundo (se recupera al reiniciar). Cada congelador es un
 * "dueño" con una clave propia guardada en su bloque; la instancia se libera cuando no queda ningún dueño: señal apagada,
 * bloque roto, o el bloque ya no está en esa instancia (la nave aterrizó o cambió de instancia). Descargar el chunk NO libera.
 */
public final class SpaceTimeFreeze {
    private SpaceTimeFreeze() {}

    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final int VALIDATE_EVERY = 20;

    private static final class Held {
        final long id;
        final int cx, cz;
        final Set<Long> owners = new HashSet<>();
        DeepSpaceInstance inst;
        DeepSpacePosition position;
        int restore;
        Frame frame;
        Vector3D pos;
        Vector3D vel;

        Held(DeepSpaceInstance inst, int restore) {
            this.id = inst.getId();
            org.joml.Vector3dc c = inst.getCenter();
            this.cx = (int) Math.floor(c.x());
            this.cz = (int) Math.floor(c.z());
            this.inst = inst;
            this.position = inst.getPosition();
            this.restore = restore;
        }
    }

    private static final Map<Long, Held> HELD = new HashMap<>();
    private static final Map<DeepSpacePosition, Held> BY_POSITION = new IdentityHashMap<>();
    private static boolean warned;

    /** Instancia del espacio profundo en la que está el bloque (dentro de una nave o suelto en la dimensión), o null. */
    public static DeepSpaceInstance instanceOf(ServerLevel level, BlockPos pos) {
        if (!level.dimension().location().equals(DEEP_SPACE)) return null;
        try {
            DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
            if (data == null || data.getUniverse() == null) return null;
            ServerSubLevel ship = Warp.shipAt(level, pos);
            int x = pos.getX(), z = pos.getZ();
            if (ship != null) {
                org.joml.Vector3dc p = ship.logicalPose().position();
                x = (int) Math.floor(p.x());
                z = (int) Math.floor(p.z());
            }
            DeepSpaceInstance inst = data.getInstanceForPos(x, z);
            return inst == null || inst.isCorrupted() ? null : inst;
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.debug("[Congelador] No se pudo buscar la instancia", t);
            return null;
        }
    }

    public static boolean isFrozen(DeepSpaceInstance inst) {
        return inst != null && HELD.containsKey(inst.getId());
    }

    /** Lo llama cada tick un congelador con señal dentro de una instancia. {@code owner} es la clave propia del bloque. */
    static void engage(ServerLevel level, DeepSpaceInstance inst, long owner) {
        try {
            Held h = HELD.get(inst.getId());
            boolean changed = false;
            if (h == null) {
                int ts = inst.getPosition().getTimescale();
                h = new Held(inst, ts == 0 ? 1 : ts);
                capture(level, h);
                HELD.put(h.id, h);
                BY_POSITION.put(h.position, h);
                h.position.setTimescale(0);
                inst.forceClientSync();
                changed = true;
                PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} fijada respecto a su cuerpo dominante", h.id);
                inst.collectOnlinePlayers(level.getServer(), p -> Achievements.award(p, "time_freezer", "froze"));
            } else if (h.inst != inst || h.position != inst.getPosition()) {
                rebind(h, inst);
            }
            if (h.owners.add(owner)) changed = true;
            if (changed) persist(level.getServer());
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo congelar la instancia", t);
        }
    }

    /** El congelador {@code owner} deja de sujetar la instancia {@code id} (sin señal, roto, o ya no está en ella). */
    static void disengage(MinecraftServer server, long id, long owner) {
        Held h = HELD.get(id);
        if (h == null || !h.owners.remove(owner)) return;
        if (h.owners.isEmpty()) {
            release(h);
            HELD.remove(id);
        }
        persist(server);
    }

    /** La instancia se recreó (rescate de órbita, recarga): volver a engancharse a la posición nueva. */
    private static void rebind(Held h, DeepSpaceInstance inst) {
        BY_POSITION.remove(h.position);
        h.inst = inst;
        h.position = inst.getPosition();
        h.pos = null;
        BY_POSITION.put(h.position, h);
        h.position.setTimescale(0);
        inst.forceClientSync();
    }

    /**
     * Guarda posición y velocidad respecto al cuerpo dominante. Si el reloj de la instancia se había separado del del
     * universo (la primera versión del congelador lo paraba), lo vuelve a juntar sin mover la nave respecto a ese cuerpo.
     */
    private static void capture(ServerLevel level, Held h) {
        DeepSpacePosition pos = h.position;
        TimeStampedPVCoordinates cur = pos.getCurrentPVCoords();
        h.frame = pos.getFrame();
        h.pos = cur.getPosition();
        h.vel = cur.getVelocity();
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        if (h.restore == 1 && data != null && data.getUniverse() != null) {
            long global = data.getUniverseTicks();
            long local = pos.getLocalUniverseTicks();
            if (local != global) {
                pos.setLocalUniverseTicks(global);
                pos.init(data.getUniverse(), h.frame, new TimeStampedPVCoordinates(pos.getLocalUniverseTime(), h.pos, h.vel));
                PlanetaryExpansion.LOGGER.info("[Congelador] Reloj de la instancia {} resincronizado con el universo ({} ticks de diferencia)",
                        h.inst.getId(), global - local);
            }
        }
    }

    /**
     * Sustituye a DeepSpacePosition.propagate mientras la instancia está congelada (mixin/DeepSpacePositionFreezeMixin).
     * Devuelve false si esta posición no está congelada.
     */
    public static boolean propagateFrozen(DeepSpacePosition position, UniverseDefinition universe) {
        Held h = BY_POSITION.get(position);
        if (h == null) return false;
        try {
            Frame frame = position.getFrame();
            TimeStampedPVCoordinates cur = position.getCurrentPVCoords();
            boolean moved = h.pos != null
                    && cur.getPosition().distance(h.pos) > 1e-7 * (1.0 + h.pos.getNorm());
            if (h.pos == null || h.frame == null || moved || !frame.getName().equals(h.frame.getName())) {
                // primera vez, el cuerpo dominante cambió (un planeta te "atrapó" al pasar) o la nave cambió de sitio
                // (salto del motor de distorsión, rescate de órbita): fijarse al punto nuevo
                h.frame = frame;
                h.pos = cur.getPosition();
                h.vel = cur.getVelocity();
            } else {
                // el empuje de este tick ya está aplicado: la diferencia de velocidad es lo que cambió el jugador
                Vector3D dv = cur.getVelocity().subtract(h.vel);
                if (dv.getNorm() > 1e-9 * (1.0 + h.vel.getNorm())) h.vel = cur.getVelocity();
            }
            position.setLocalUniverseTicks(position.getLocalUniverseTicks() + h.restore);
            position.init(universe, frame, new TimeStampedPVCoordinates(position.getLocalUniverseTime(), h.pos, h.vel));
            return true;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo mantener la nave fija", t);
            }
            return false;
        }
    }

    private static void release(Held h) {
        try {
            BY_POSITION.remove(h.position);
            h.position.setTimescale(h.restore);
            h.inst.forceClientSync();
            PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} liberada (sigue su órbita)", h.id);
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo liberar la instancia", t);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HELD.isEmpty()) return;
        MinecraftServer server = event.getServer();
        boolean validate = server.getTickCount() % VALIDATE_EVERY == 0;
        DeepSpaceData data = validate ? DeepSpaceData.getInstance(server) : null;
        boolean changed = false;
        for (Iterator<Held> it = HELD.values().iterator(); it.hasNext(); ) {
            Held h = it.next();
            try {
                if (data != null) {
                    DeepSpaceInstance cur = data.getInstanceForPos(h.cx, h.cz);
                    if (cur == null || cur.getId() != h.id || cur.isCorrupted()) {
                        // la instancia ya no existe (la nave aterrizó y se retiró): no hay nada que liberar
                        BY_POSITION.remove(h.position);
                        it.remove();
                        changed = true;
                        PlanetaryExpansion.LOGGER.info("[Congelador] La instancia {} ya no existe; se olvida", h.id);
                        continue;
                    }
                    if (cur != h.inst || cur.getPosition() != h.position) rebind(h, cur);
                }
                int ts = h.position.getTimescale();
                if (ts != 0) {
                    // el comando /rn timescale lo cambió con la nave congelada: se adopta como ritmo del reloj
                    h.restore = ts;
                    h.position.setTimescale(0);
                    changed = true;
                    PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {}: ritmo del tiempo cambiado a x{}", h.id, ts);
                }
                h.inst.forceClientSync(); // una sincronización por tick: es lo que hace avanzar el cielo en el cliente
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    PlanetaryExpansion.LOGGER.warn("[Congelador] Error al mantener la instancia {}", h.id, t);
                }
            }
        }
        if (changed) persist(server);
    }

    // ------------------------------------------------------------------ guardado en el mundo

    private static void persist(MinecraftServer server) {
        try {
            Data d = Data.get(server.overworld());
            d.entries.clear();
            for (Held h : HELD.values()) d.entries.put(h.id, new Entry(h.id, h.cx, h.cz, h.restore, new HashSet<>(h.owners)));
            d.setDirty();
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo guardar el estado", t);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HELD.clear();
        BY_POSITION.clear();
        MinecraftServer server = event.getServer();
        try {
            Data d = Data.get(server.overworld());
            if (d.entries.isEmpty()) return;
            DeepSpaceData data = DeepSpaceData.getInstance(server);
            ServerLevel overworld = server.overworld();
            for (Entry e : d.entries.values()) {
                DeepSpaceInstance inst = data == null ? null : data.getInstanceForPos(e.cx, e.cz);
                if (inst == null || inst.getId() != e.id || inst.isCorrupted()) {
                    PlanetaryExpansion.LOGGER.info("[Congelador] La instancia guardada {} ya no existe; se olvida", e.id);
                    continue;
                }
                Held h = new Held(inst, e.restore);
                h.owners.addAll(e.owners);
                capture(overworld, h);
                HELD.put(h.id, h);
                BY_POSITION.put(h.position, h);
                h.position.setTimescale(0);
                inst.forceClientSync();
                PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} sigue fijada (recuperada del guardado)", h.id);
            }
            persist(server);
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo recuperar el estado guardado", t);
        }
    }

    /** Al cerrar no se libera nada: el estado ya está guardado y se recupera al volver a abrir el mundo. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HELD.clear();
        BY_POSITION.clear();
    }

    private record Entry(long id, int cx, int cz, int restore, Set<Long> owners) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", id);
            t.putInt("X", cx);
            t.putInt("Z", cz);
            t.putInt("Timescale", restore);
            t.putLongArray("Owners", owners.stream().mapToLong(Long::longValue).toArray());
            return t;
        }

        static Entry load(CompoundTag t) {
            Set<Long> o = new HashSet<>();
            for (long l : t.getLongArray("Owners")) o.add(l);
            int ts = t.getInt("Timescale");
            return new Entry(t.getLong("Id"), t.getInt("X"), t.getInt("Z"), ts == 0 ? 1 : ts, o);
        }
    }

    public static final class Data extends SavedData {
        static final String ID = "planetary_expansion_time_freezers";
        final Map<Long, Entry> entries = new LinkedHashMap<>();

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), ID);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("instances", Tag.TAG_COMPOUND)) {
                try {
                    Entry e = Entry.load((CompoundTag) t);
                    d.entries.put(e.id(), e);
                } catch (Throwable ignored) {
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag l = new ListTag();
            for (Entry e : entries.values()) l.add(e.save());
            tag.put("instances", l);
            return tag;
        }
    }
}
