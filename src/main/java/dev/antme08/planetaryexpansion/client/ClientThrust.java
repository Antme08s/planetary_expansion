package dev.antme08.planetaryexpansion.client;

import dev.antme08.planetaryexpansion.weather.ThrustPayload;
import org.joml.Vector3d;

/** Último empuje recibido del servidor; se da por apagado si no llega nada en 1 segundo. */
public final class ClientThrust {
    private ClientThrust() {}

    private static final Vector3d ACCEL = new Vector3d();
    private static long lastMillis;

    public static void receive(ThrustPayload p) {
        ACCEL.set(p.ax(), p.ay(), p.az());
        lastMillis = System.currentTimeMillis();
    }

    /** Aceleración actual en m/s² (ejes del mundo), o null si los motores no empujan. */
    public static Vector3d current() {
        if (System.currentTimeMillis() - lastMillis > 1000) return null;
        return new Vector3d(ACCEL);
    }
}
