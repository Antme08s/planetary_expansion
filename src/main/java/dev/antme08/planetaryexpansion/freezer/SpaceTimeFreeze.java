package dev.antme08.planetaryexpansion.freezer;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.advancement.Achievements;
import dev.antme08.planetaryexpansion.warp.Warp;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;

import java.util.HashMap;
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
 * {@link #propagateFrozen} con el ritmo de antes. Si ningún congelador lo refresca en más de 3 ticks (señal apagada, bloque
 * roto, nave descargada) se libera solo.
 */
public final class SpaceTimeFreeze {
    private SpaceTimeFreeze() {}

    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final int GRACE_TICKS = 3;

    private static final class Held {
        final DeepSpaceInstance inst;
        final DeepSpacePosition position;
        final int restore;
        int lastTick;
        Frame frame;
        Vector3D pos;
        Vector3D vel;

        Held(DeepSpaceInstance inst, int restore, int lastTick) {
            this.inst = inst;
            this.position = inst.getPosition();
            this.restore = restore;
            this.lastTick = lastTick;
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

    /** Lo llama cada tick un congelador activo. */
    static void hold(ServerLevel level, DeepSpaceInstance inst) {
        int now = level.getServer().getTickCount();
        try {
            DeepSpacePosition pos = inst.getPosition();
            Held h = HELD.get(inst.getId());
            if (h == null || h.inst != inst || h.position != pos) {
                if (h != null) BY_POSITION.remove(h.position);
                int ts = pos.getTimescale();
                h = new Held(inst, ts <= 0 ? 1 : ts, now);
                capture(level, h);
                HELD.put(inst.getId(), h);
                BY_POSITION.put(pos, h);
                pos.setTimescale(0);
                PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} fijada respecto a su cuerpo dominante", inst.getId());
                inst.collectOnlinePlayers(level.getServer(), p -> Achievements.award(p, "time_freezer", "froze"));
            } else {
                h.lastTick = now;
                if (pos.getTimescale() != 0) pos.setTimescale(0); // otro código (el comando /rn timescale) lo cambió
            }
            inst.forceClientSync(); // una sincronización por tick: es lo que hace avanzar el cielo en el cliente
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo congelar la instancia", t);
        }
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
            if (h.pos == null || h.frame == null || !frame.getName().equals(h.frame.getName())) {
                // primera vez, o el cuerpo dominante cambió (un planeta te "atrapó" al pasar): fijarse al nuevo
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
            PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} liberada (sigue su órbita)", h.inst.getId());
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo liberar la instancia", t);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HELD.isEmpty()) return;
        int now = event.getServer().getTickCount();
        for (Iterator<Held> it = HELD.values().iterator(); it.hasNext(); ) {
            Held h = it.next();
            if (now - h.lastTick > GRACE_TICKS) {
                release(h);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Held h : HELD.values()) release(h);
        HELD.clear();
        BY_POSITION.clear();
    }
}
