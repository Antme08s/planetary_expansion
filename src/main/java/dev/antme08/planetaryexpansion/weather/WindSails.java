package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Potencia de cada tipo de vela/ala en los molinos de Create.
 *
 * Create cuenta 1 por cada bloque de la etiqueta create:windmill_sails, y las RPM del molino son
 * velas ÷ windmillSailsPerRPM (entre 1 y 16). Aquí cada bloque puede valer otra cantidad según la
 * etiqueta planetary_expansion:windmill_sail_power/&lt;valor&gt; en la que esté. Un bloque que esté en una
 * de estas etiquetas cuenta aunque no esté en la de Create (así funcionan velas de otros mods).
 */
public final class WindSails {
    private WindSails() {}

    public static final TagKey<Block> CREATE_SAILS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "windmill_sails"));

    /** Valores disponibles; el nombre de la etiqueta es el valor ("1.5" → windmill_sail_power/1.5). */
    public static final double[] POWERS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0};
    private static final TagKey<Block>[] TAGS = createTags();

    @SuppressWarnings("unchecked")
    private static TagKey<Block>[] createTags() {
        TagKey<Block>[] tags = new TagKey[POWERS.length];
        for (int i = 0; i < POWERS.length; i++) {
            tags[i] = TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "windmill_sail_power/" + tagName(POWERS[i])));
        }
        return tags;
    }

    public static String tagName(double power) {
        return power == Math.rint(power) ? String.valueOf((int) power) : String.valueOf(power);
    }

    /** Potencia del bloque, o -1 si ninguna etiqueta de este mod lo menciona (lo decide Create). */
    public static double power(BlockState state) {
        for (int i = TAGS.length - 1; i >= 0; i--) {
            if (state.is(TAGS[i])) return POWERS[i];
        }
        return -1;
    }
}
