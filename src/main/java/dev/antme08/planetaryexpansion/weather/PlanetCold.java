package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.armor.GlacialArmor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Frío permanente en la superficie de Thalassa (sin tormenta): estando a la intemperie fuera del agua te vas
 * congelando poco a poco (~70 s hasta congelarte del todo, luego el daño normal de congelación). Bajo techo,
 * dentro de una nave con oxígeno o en el agua no. La armadura glacial lo reduce un 25 % por pieza y con el traje
 * completo no pasa nada. Usa el mismo contador que la nieve polvo: al resguardo te descongelas solo.
 * Durante una ventisca de Thalassa no actúa: congela la ventisca.
 */
public final class PlanetCold {
    private PlanetCold() {}

    private static final ResourceLocation THALASSA = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "thalassa");
    private static final float RATE = 0.1f;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().location().equals(THALASSA)) return;
        // durante una ventisca ya congela StormManager (más rápido); no sumar los dos
        if (StormManager.intensity(StormType.THALASSA_BLIZZARD) > 0.01f) return;
        RandomSource rng = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive() || !player.canFreeze()) continue;
            if (player.isInWater() || !StormShelter.exposedToHazards(player)) continue;
            float cold = GlacialArmor.coldFactor(player);
            if (cold <= 0f) continue;
            int required = player.getTicksRequiredToFreeze();
            int add = 2 + (rng.nextFloat() < RATE * cold ? 1 : 0);
            player.setTicksFrozen(Math.min(required + 2, player.getTicksFrozen() + add));
        }
    }
}
