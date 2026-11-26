package dev.antme08.planetaryexpansion.transporter;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.0.4.1: criaturas que viajan dentro de una nave cuando cambia de dimensión.
 * <ul>
 *   <li>Se marcan para que no desaparezcan por estar lejos del jugador (los peces y otras criaturas que no son
 *   animales de granja desaparecen solas; durante el cambio de dimensión pueden quedar un momento lejos de él).</li>
 *   <li>Durante 30 s después del viaje, si alguna muere o desaparece se apunta en el registro por qué (para encontrar
 *   fallos).</li>
 * </ul>
 */
@EventBusSubscriber(modid = PlanetaryExpansion.MODID)
public final class ShipPassengers {
    private ShipPassengers() {}

    private static final long WATCH_MS = 30_000;
    private static final Map<UUID, Long> WATCH = new ConcurrentHashMap<>();

    public static void moving(Entity entity) {
        if (entity == null) return;
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        WATCH.put(entity.getUUID(), System.currentTimeMillis() + WATCH_MS);
    }

    private static boolean watched(Entity e) {
        Long until = WATCH.get(e.getUUID());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            WATCH.remove(e.getUUID());
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !watched(event.getEntity())) return;
        PlanetaryExpansion.LOGGER.warn("[Naves] {} que viajaba en la nave murió en {} ({}): {}",
                event.getEntity().getType().getDescriptionId(), event.getEntity().level().dimension().location(),
                event.getEntity().blockPosition(), event.getSource().getMsgId());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide() || !watched(e)) return;
        Entity.RemovalReason r = e.getRemovalReason();
        if (r == null || r == Entity.RemovalReason.CHANGED_DIMENSION || r == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || r == Entity.RemovalReason.UNLOADED_WITH_PLAYER || r == Entity.RemovalReason.KILLED) return;
        PlanetaryExpansion.LOGGER.warn("[Naves] {} que viajaba en la nave desapareció de {} ({}): {}",
                e.getType().getDescriptionId(), e.level().dimension().location(), e.blockPosition(), r);
    }
}
