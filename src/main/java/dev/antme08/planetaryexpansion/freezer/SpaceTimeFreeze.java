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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Congelador del tiempo espacial (1.0.4): para el reloj de la instancia del espacio profundo en la que está la nave.
 *
 * Cómo funciona Cosmonautics: cada instancia lleva su propia órbita (kepleriana) y su propio reloj
 * ({@code localUniverseTicks}); en cada tick el reloj avanza {@code timescale} unidades, y el empuje de los motores se suma a
 * la velocidad en la fecha local actual, que crea una órbita nueva por el mismo punto. Con {@code timescale = 0} la nave se
 * queda en su sitio, el cielo no se mueve (el cliente extrapola con el mismo valor, que viaja en la sincronización de la
 * posición) y el empuje sigue cambiando la órbita: al soltar la señal, continúa desde donde estaba con la órbita nueva. La
 * rotación de la nave no se toca, así que se puede apuntar mientras está congelada.
 *
 * El valor no se guarda en disco (se reinicia a 1), así que cada tick que el congelador esté activo lo vuelve a poner, y si
 * deja de actualizarse (señal apagada, bloque roto, nave descargada) la instancia recupera su ritmo a los pocos ticks.
 */
public final class SpaceTimeFreeze {
    private SpaceTimeFreeze() {}

    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final int GRACE_TICKS = 3;

    private static final class Held {
        final DeepSpaceInstance inst;
        final int restore;
        int lastTick;

        Held(DeepSpaceInstance inst, int restore, int lastTick) {
            this.inst = inst;
            this.restore = restore;
            this.lastTick = lastTick;
        }
    }

    private static final Map<Long, Held> HELD = new HashMap<>();

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
            if (h == null || h.inst != inst) {
                int ts = pos.getTimescale();
                h = new Held(inst, ts <= 0 ? 1 : ts, now);
                HELD.put(inst.getId(), h);
                pos.setTimescale(0);
                inst.forceClientSync();
                PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} congelada", inst.getId());
                inst.collectOnlinePlayers(level.getServer(), p -> Achievements.award(p, "time_freezer", "froze"));
            } else {
                h.lastTick = now;
                if (pos.getTimescale() != 0) { // otro código (el comando /rn timescale, una recarga) lo cambió
                    pos.setTimescale(0);
                    inst.forceClientSync();
                }
            }
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Congelador] No se pudo congelar la instancia", t);
        }
    }

    private static void release(Held h) {
        try {
            h.inst.getPosition().setTimescale(h.restore);
            h.inst.forceClientSync();
            PlanetaryExpansion.LOGGER.info("[Congelador] Instancia {} liberada", h.inst.getId());
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
    }
}
