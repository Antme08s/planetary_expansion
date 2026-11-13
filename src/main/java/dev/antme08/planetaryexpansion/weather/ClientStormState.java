package dev.antme08.planetaryexpansion.weather;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Último estado de tormenta recibido del servidor, por dimensión. Sin clases exclusivas del
 * cliente, para que el manejador del paquete se pueda registrar en ambos lados.
 */
public final class ClientStormState {
    private static final Map<ResourceLocation, StormSyncPayload> LAST = new ConcurrentHashMap<>();

    private ClientStormState() {}

    public static void receive(StormSyncPayload payload) {
        LAST.put(payload.dimension(), payload);
    }

    public static StormSyncPayload get(ResourceLocation dimension) {
        return LAST.get(dimension);
    }

    public static void clear() {
        LAST.clear();
    }
}
