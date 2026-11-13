package dev.antme08.planetaryexpansion.diag;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnóstico de bases rotas en los planetas del mod.
 *
 * Cuando en pocos ticks aparecen muchos objetos tirados (una estructura que se rompe de golpe), o hay una
 * explosión que rompe bloques, escribe en el log quién lo hizo: la pila de llamadas del primer objeto dice
 * qué mod rompió los bloques (Sable, Cosmonautics, Deep Seas, IvSableBridge...).
 * Solo observa; no cambia nada del juego.
 */
public final class BlockLossLog {
    private BlockLossLog() {}

    private static final int WINDOW_TICKS = 10;
    private static final int THRESHOLD = 16;

    private static long windowStart = Long.MIN_VALUE;
    private static int count;
    private static boolean reported;
    private static StackTraceElement[] firstTrace;
    private static Vec3 firstPos;
    private static final Map<String, Integer> items = new LinkedHashMap<>();
    private static long lastExplosionLog = Long.MIN_VALUE;

    private static boolean ours(ServerLevel level) {
        return level.dimension().location().getNamespace().equals(PlanetaryExpansion.MODID);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        try {
            join(event);
        } catch (Throwable t) {
            // un diagnóstico nunca debe tirar el juego (0.4.4 crasheó aquí)
        }
    }

    private static void join(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof ItemEntity item)) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !ours(level)) return;
        long now = level.getGameTime();
        if (firstPos == null || now - windowStart > WINDOW_TICKS) {
            windowStart = now;
            count = 0;
            reported = false;
            items.clear();
            firstTrace = new Throwable().getStackTrace();
            firstPos = item.position();
        }
        count += 1;
        items.merge(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(), item.getItem().getCount(), Integer::sum);
        if (count >= THRESHOLD && !reported) {
            reported = true;
            PlanetaryExpansion.LOGGER.warn("[Estructuras] Se han roto muchos bloques de golpe en {} cerca de ({}, {}, {}): {} objetos en {} ticks: {}. Quién lo hizo:{}",
                    level.dimension().location(), (int) firstPos.x, (int) firstPos.y, (int) firstPos.z, count, now - windowStart,
                    items, trace(firstTrace));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        try {
            explosion(event);
        } catch (Throwable t) {
            // ídem
        }
    }

    private static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ours(level)) return;
        int blocks = event.getAffectedBlocks().size();
        if (blocks < 4) return;
        long now = level.getGameTime();
        if (now - lastExplosionLog < 100) return;
        lastExplosionLog = now;
        Vec3 c = event.getExplosion().center();
        var src = event.getExplosion().getDirectSourceEntity();
        PlanetaryExpansion.LOGGER.warn("[Estructuras] Explosión en {} ({}, {}, {}) que rompe {} bloques; causa: {}. Quién la hizo:{}",
                level.dimension().location(), (int) c.x, (int) c.y, (int) c.z, blocks,
                src == null ? "ninguna entidad" : BuiltInRegistries.ENTITY_TYPE.getKey(src.getType()),
                trace(new Throwable().getStackTrace()));
    }

    private static String trace(StackTraceElement[] st) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("net.neoforged.bus.")
                    || c.startsWith(BlockLossLog.class.getName())) continue;
            sb.append("\n    at ").append(c).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
            if (++shown >= 30) break;
        }
        return sb.toString();
    }
}
