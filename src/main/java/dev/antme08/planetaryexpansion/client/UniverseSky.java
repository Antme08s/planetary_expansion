package dev.antme08.planetaryexpansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

import java.util.Set;

/**
 * Planetas que dibujan el universo de Cosmonautics en su cielo (0.7.51: {@code render_universe_in_dimension: true}
 * en todos). Ahí el Sol, las lunas y las estrellas son los de Cosmonautics, así que se quitan el sol, la luna y las
 * estrellas de Minecraft (si no, salían dos soles). En los planetas con atmósfera la nebulosa y las estrellas de
 * Cosmonautics se desvanecen de día, como las estrellas normales; el Sol y los planetas siguen viéndose.
 */
public final class UniverseSky {
    private UniverseSky() {}

    public static final Set<String> UNIVERSE_DIMS = Set.of(
            "planetary_expansion:mars", "planetary_expansion:ice_world", "planetary_expansion:ignis",
            "planetary_expansion:thalassa", "planetary_expansion:venus", "rocketnautics:moon");

    /** Con atmósfera: de día el cielo tiene color y no se ven las estrellas. */
    public static final Set<String> ATMOSPHERE_DIMS = Set.of(
            "planetary_expansion:mars", "planetary_expansion:thalassa", "planetary_expansion:venus");

    public static boolean drawsUniverse(ClientLevel level) {
        return level != null && UNIVERSE_DIMS.contains(level.dimension().location().toString());
    }

    /** Factor (0..1) para la nebulosa y las estrellas de Cosmonautics en el planeta actual. */
    public static float starFade() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !ATMOSPHERE_DIMS.contains(level.dimension().location().toString())) return 1.0f;
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        return Mth.clamp(level.getStarBrightness(pt) * 2.0f, 0.0f, 1.0f); // 0,5 = noche cerrada
    }
}
