package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evita que Sable destruya una estructura entera al recargar la zona.
 *
 * Sable (2.0.6) elimina en cada tick las estructuras cuya masa es 0 o menos ("MassData.isInvalid"): llama a
 * ServerLevelPlot.destroyAllBlocks, que rompe TODOS sus bloques y los suelta como objetos, y luego borra la
 * estructura. La idea es limpiar estructuras vacías, pero a veces, al alejarse y volver (o morir y reaparecer),
 * la masa calculada queda en 0 aunque los bloques siguen ahí, y la base entera cae al suelo de golpe. Lo
 * confirmó la pila de llamadas del crash de 0.4.4 (2026-11-06 00:59): processSubLevelRemovals ->
 * destroyAllBlocks. Gadgets and Gizmos tiene una copia de la misma lógica.
 *
 * Aquí, justo antes de esa comprobación, a cada estructura con masa inválida se le recalcula la masa desde
 * sus bloques reales (ServerSubLevel.buildMassTracker). Si de verdad está vacía sigue siendo inválida y Sable
 * la elimina como siempre; si tiene bloques, se salva. Vale para cualquier dimensión (también la Tierra), porque
 * solo actúa cuando Sable iba a destruir bloques que existen.
 */
public final class MassGuard {
    private MassGuard() {}

    private static final Map<UUID, Long> LAST_LOG = new HashMap<>();

    private static boolean announced;

    public static void rescue(SubLevelContainer container) {
        if (!announced) {
            announced = true;
            PlanetaryExpansion.LOGGER.info("[Estructuras] Protección activa: Sable no destruirá estructuras con bloques por quedarse sin masa al cargar");
        }
        try {
            List<? extends SubLevel> all = container.getAllSubLevels();
            for (SubLevel sub : all) {
                if (!(sub instanceof ServerSubLevel ship) || ship.isRemoved()) continue;
                if (!ship.getMassTracker().isInvalid()) continue;
                ship.buildMassTracker();
                ship.updateMergedMassData(1.0f);
                if (!ship.getMassTracker().isInvalid()) {
                    log(ship, String.format(java.util.Locale.ROOT, "%.0f", ship.getMassTracker().getMass()));
                }
            }
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.error("[Estructuras] No se pudo comprobar la masa de las estructuras de Sable", t);
        }
    }

    private static void log(ServerSubLevel ship, String mass) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(ship.getUniqueId());
        if (last != null && now - last < 60_000) return;
        if (LAST_LOG.size() > 256) LAST_LOG.clear();
        LAST_LOG.put(ship.getUniqueId(), now);
        var box = ship.boundingBox();
        PlanetaryExpansion.LOGGER.warn("[Estructuras] Sable iba a destruir '{}' en {} ({}, {}, {}) porque su masa había quedado en 0; "
                        + "se ha recalculado desde sus bloques (masa {}) y la estructura se salva",
                ship.getName(), ship.getLevel().dimension().location(),
                (int) ((box.minX() + box.maxX()) / 2), (int) box.minY(), (int) ((box.minZ() + box.maxZ()) / 2), mass);
    }
}
