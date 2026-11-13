package dev.antme08.planetaryexpansion.weather;

import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;

/**
 * Hacia dónde empujan los motores en el espacio profundo (0.7.23, petición del usuario).
 *
 * Cosmonautics, cada paso de física, quita a la nave la velocidad que le dieron sus motores y se la pasa a la órbita
 * con {@code DeepSpaceInstance.applyVelocity(nave, velocidad, masa)} (ver mixin/DeepSpaceThrustMixin). Aquí se suma
 * durante medio segundo y se manda a los jugadores de esa instancia como aceleración (m/s²) en los ejes del mundo, los
 * mismos de la mesa de hologramas y del cielo.
 */
public final class ThrustTracker {
    private ThrustTracker() {}

    private static final int WINDOW = 10; // ticks
    private static final double MIN_ACCEL = 0.05; // m/s²: por debajo es ruido

    private static final class Acc {
        DeepSpaceInstance instance;
        double sx, sy, sz, mass;
        int calls;
    }

    private static final Map<Long, Acc> ACC = new HashMap<>();

    public static void record(DeepSpaceInstance instance, Vector3dc velocity, double mass) {
        if (velocity == null || !(mass > 0)) return;
        synchronized (ACC) {
            Acc a = ACC.computeIfAbsent(instance.getId(), k -> new Acc());
            a.instance = instance;
            a.sx += velocity.x() * mass;
            a.sy += velocity.y() * mass;
            a.sz += velocity.z() * mass;
            a.mass += mass;
            a.calls++;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % WINDOW != 0) return;
        Map<Long, Acc> copy;
        synchronized (ACC) {
            if (ACC.isEmpty()) return;
            copy = new HashMap<>(ACC);
            ACC.clear();
        }
        ServerLevel deep = null;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (DeepSpaceHelper.isDeepSpace(level)) deep = level;
        }
        if (deep == null) return;
        double seconds = WINDOW / 20.0;
        for (Acc a : copy.values()) {
            if (a.calls == 0 || a.instance == null) continue;
            double meanMass = a.mass / a.calls;
            // con una sola nave esto es la suma de los cambios de velocidad (igual que hace Cosmonautics)
            double ax = a.sx / meanMass / seconds, ay = a.sy / meanMass / seconds, az = a.sz / meanMass / seconds;
            if (Math.sqrt(ax * ax + ay * ay + az * az) < MIN_ACCEL) continue;
            ThrustPayload payload = new ThrustPayload((float) ax, (float) ay, (float) az);
            AABB box;
            try {
                box = a.instance.boundingBox();
            } catch (Throwable t) {
                continue;
            }
            for (ServerPlayer p : deep.players()) {
                if (box.contains(p.position())) PacketDistributor.sendToPlayer(p, payload);
            }
        }
    }
}
