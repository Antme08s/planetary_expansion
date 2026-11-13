package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Arreglo para teletransportarse con un waystone a una nave que está descargada (0.7.36).
 *
 * <p>Cuando una nave se descarga, Sable la guarda en el "holding chunk" que se estaba descargando, pero el
 * {@link HoldingSubLevel} que queda en memoria conserva el puntero del guardado anterior. waystonessable usa ese
 * puntero para pedir {@code snatchAndLoad}, Sable busca la nave en otro chunk, no la encuentra y el teletransporte
 * falla con "The destination is outside the world bounds" (log: "the requested sub-level wasn't present in the
 * holding chunk"). Tras reiniciar funcionaba porque entonces se usa el puntero del tracking point, que sí está
 * actualizado.</p>
 *
 * <p>Aquí, si en el chunk del puntero no está la nave, se busca dónde está de verdad: primero entre los holding
 * chunks cargados en memoria y, si no, en el tracking point de esa nave. Si se encuentra, se repite la llamada con
 * el puntero bueno. Si no, Sable sigue como siempre.</p>
 */
@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SableSnatchPointerMixin {

    @Shadow @Final private Long2ObjectMap<SubLevelHoldingChunk> loadedHoldingChunks;
    @Shadow @Final private ServerLevel level;

    @Shadow
    public abstract void snatchAndLoad(GlobalSavedSubLevelPointer pointer, UUID id);

    @Unique
    private static boolean planetaryExpansion$redirecting;

    @Inject(method = "snatchAndLoad", at = @At("HEAD"), cancellable = true, require = 0)
    private void planetaryExpansion$fixPointer(GlobalSavedSubLevelPointer pointer, UUID id, CallbackInfo ci) {
        if (planetaryExpansion$redirecting || pointer == null || id == null) return;
        try {
            SubLevelHoldingChunk at = loadedHoldingChunks.get(pointer.chunkPos().toLong());
            if (at != null && planetaryExpansion$holds(at, id)) return; // el puntero es bueno

            GlobalSavedSubLevelPointer better = null;
            for (SubLevelHoldingChunk chunk : loadedHoldingChunks.values()) {
                if (chunk != at && planetaryExpansion$holds(chunk, id)) {
                    better = new GlobalSavedSubLevelPointer(chunk.getChunkPos(), pointer.storageIndex(), pointer.subLevelIndex());
                    break;
                }
            }
            if (better == null && at == null) {
                // no está en memoria: el tracking point tiene el último sitio donde Sable la guardó en disco
                for (Map.Entry<UUID, TrackingPoint> e : SubLevelTrackingPointSavedData.getOrLoad(level).getAllTrackingPoints()) {
                    TrackingPoint tp = e.getValue();
                    if (tp != null && tp.inSubLevel() && id.equals(tp.subLevelID())
                            && tp.lastSavedSubLevelPointer() != null && !pointer.equals(tp.lastSavedSubLevelPointer())) {
                        better = tp.lastSavedSubLevelPointer();
                        break;
                    }
                }
            }
            if (better == null) return;

            PlanetaryExpansion.LOGGER.info("[Naves] Puntero de la nave {} corregido para cargarla: {} -> {}", id, pointer, better);
            planetaryExpansion$redirecting = true;
            try {
                snatchAndLoad(better, id);
            } finally {
                planetaryExpansion$redirecting = false;
            }
            ci.cancel();
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.debug("[Naves] No se pudo corregir el puntero de la nave {}", id, t);
        }
    }

    @Unique
    private static boolean planetaryExpansion$holds(SubLevelHoldingChunk chunk, UUID id) {
        for (HoldingSubLevel h : chunk.getLoadedHoldingSubLevels()) {
            if (h != null && h.data() != null && id.equals(h.data().uuid())) return true;
        }
        return false;
    }
}
