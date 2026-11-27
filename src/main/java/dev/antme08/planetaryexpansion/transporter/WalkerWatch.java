package dev.antme08.planetaryexpansion.transporter;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0.5, TEMPORAL (para encontrar el fallo): después de que un caminante de Let It Walk cambie de dimensión dentro de
 * un grupo de naves, durante 40 s se apunta cada 2 s cómo está su núcleo (si tiene patas, si está esperando a
 * reconstruirse, si avanza su reloj interno, si su nave tiene cuerpo físico). Quitar antes de publicar.
 */
@EventBusSubscriber(modid = PlanetaryExpansion.MODID)
public final class WalkerWatch {
    private WalkerWatch() {}

    private record Watch(BlockPos pos, long until, int[] seen) {}

    private static final List<Watch> WATCH = new ArrayList<>();

    public static void expect(BlockPos pos) {
        synchronized (WATCH) {
            WATCH.add(new Watch(pos.immutable(), -1, new int[]{0}));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<Watch> copy;
        synchronized (WATCH) {
            if (WATCH.isEmpty()) return;
            copy = new ArrayList<>(WATCH);
        }
        var server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 40 != 0) return;
        for (Watch w : copy) {
            long until = w.until() < 0 ? now + 800 : w.until();
            if (w.until() < 0) {
                synchronized (WATCH) {
                    WATCH.remove(w);
                    WATCH.add(new Watch(w.pos(), until, w.seen()));
                }
            }
            if (now > until) {
                synchronized (WATCH) {
                    WATCH.removeIf(x -> x.pos().equals(w.pos()));
                }
                continue;
            }
            boolean found = false;
            for (ServerLevel level : server.getAllLevels()) {
                try {
                    if (!level.isLoaded(w.pos())) continue;
                    BlockEntity be = level.getBlockEntity(w.pos());
                    if (be == null || !be.getClass().getName().equals("dev.letitwalk.content.WalkerCoreBlockEntity")) continue;
                    found = true;
                    Class<?> c = be.getClass();
                    Object legs = c.getField("legs").get(be);
                    var sub = Sable.HELPER.getContaining(level, (Vec3i) w.pos());
                    String body = "-";
                    String tracked = "-";
                    if (sub instanceof ServerSubLevel ss) {
                        var h = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ss);
                        body = ss.getUniqueId() + (h == null ? " sinCuerpo" : h.isValid() ? " cuerpoOK" : " cuerpoNoVálido")
                                + (ss.isRemoved() ? " QUITADA" : "");
                        Class<?> tr = Class.forName("dev.letitwalk.walker.WalkerTracker");
                        tracked = String.valueOf(tr.getMethod("coreOf", dev.ryanhcode.sable.sublevel.SubLevel.class).invoke(null, ss));
                    }
                    PlanetaryExpansion.LOGGER.info("[Caminante] {} en {}: patas={} reconstruir={} quieto={} sinSujetar={} aire={} sinApoyo={} recuperando={} nave={} seguido={} quitado={}",
                            w.pos(), level.dimension().location(), legs instanceof List<?> l ? l.size() : legs,
                            c.getField("rebuildQueued").get(be), c.getField("idleTicks").get(be), c.getField("unheldTicks").get(be),
                            c.getField("airTicks").get(be), c.getField("unsupportedTicks").get(be), c.getField("recoverTicks").get(be),
                            body, tracked, be.isRemoved());
                } catch (Throwable t) {
                    PlanetaryExpansion.LOGGER.info("[Caminante] {}: no se pudo leer ({})", w.pos(), t.toString());
                    found = true;
                }
            }
            if (!found && w.seen()[0]++ < 3) {
                PlanetaryExpansion.LOGGER.info("[Caminante] {}: núcleo no encontrado (aún sin cargar o en otro sitio)", w.pos());
            }
        }
    }
}
