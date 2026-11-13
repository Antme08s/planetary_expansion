package dev.antme08.planetaryexpansion.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Bolsas subterráneas de nitrógeno líquido (0.7.42, petición del usuario: comunes en el mundo helado y poco comunes
 * en Thalassa, de tamaños variados). Un elipsoide hueco: la mitad de abajo llena de nitrógeno líquido de Create
 * Nuclear, el resto de aire, y un borde de hielo azul donde el nitrógeno toca la roca (como la piedra que rodea los
 * lagos de lava). Solo se pone si todo el hueco y su borde son roca maciza: nunca se abre a una cueva, al mar ni al
 * vacío, así que el nitrógeno no se derrama.
 */
public class NitrogenPocketFeature extends Feature<NitrogenPocketFeature.Config> {
    public static final DeferredHolder<Feature<?>, NitrogenPocketFeature> POCKET =
            VenusLavaVentFeature.FEATURES.register("nitrogen_pocket", NitrogenPocketFeature::new);
    private static final ResourceLocation NITROGEN = ResourceLocation.fromNamespaceAndPath("createnuclear", "nitrogen");

    static void touch() {}

    /** Radios (horizontales) de las bolsas pequeñas y grandes y probabilidad de que salga grande. */
    public record Config(float smallMin, float smallMax, float bigMin, float bigMax, float bigChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("small_min").forGetter(Config::smallMin),
                Codec.FLOAT.fieldOf("small_max").forGetter(Config::smallMax),
                Codec.FLOAT.fieldOf("big_min").forGetter(Config::bigMin),
                Codec.FLOAT.fieldOf("big_max").forGetter(Config::bigMax),
                Codec.FLOAT.fieldOf("big_chance").forGetter(Config::bigChance)
        ).apply(i, Config::new));
    }

    public NitrogenPocketFeature() {
        super(Config.CODEC);
    }

    private static float between(RandomSource r, float a, float b) {
        return a + r.nextFloat() * Math.max(0f, b - a);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        Block n2 = BuiltInRegistries.BLOCK.get(NITROGEN);
        if (n2 == Blocks.AIR) return false;
        BlockState fluid = n2.defaultBlockState();
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        Config c = ctx.config();
        BlockPos o = ctx.origin();

        boolean big = r.nextFloat() < c.bigChance();
        float rx = big ? between(r, c.bigMin(), c.bigMax()) : between(r, c.smallMin(), c.smallMax());
        rx = Math.min(rx, 12f); // que no se salga de los chunks vecinos que se pueden tocar
        float rz = Math.min(12f, rx * (0.7f + 0.5f * r.nextFloat()));
        float ry = Math.max(2.2f, rx * (0.4f + 0.15f * r.nextFloat()));
        double surface = o.getY() + 0.5 + ry * 0.15; // nivel del nitrógeno: algo más de la mitad del hueco

        int ex = (int) Math.ceil(rx) + 1, ey = (int) Math.ceil(ry) + 1, ez = (int) Math.ceil(rz) + 1;
        if (o.getY() - ey <= level.getMinBuildHeight() + 1 || o.getY() + ey >= level.getMaxBuildHeight() - 1) return false;

        // 1) comprobar: el hueco y su borde tienen que ser roca maciza (nada de aire, agua, lava ni roca madre)
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -ex; dx <= ex; dx++)
            for (int dy = -ey; dy <= ey; dy++)
                for (int dz = -ez; dz <= ez; dz++) {
                    double k = sq(dx / (rx + 1.0)) + sq(dy / (ry + 1.0)) + sq(dz / (rz + 1.0));
                    if (k >= 1.0) continue;
                    p.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK) || !s.isSolid()) return false;
                }
        // 2) excavar y llenar
        for (int dx = -ex; dx <= ex; dx++)
            for (int dy = -ey; dy <= ey; dy++)
                for (int dz = -ez; dz <= ez; dz++) {
                    double inner = sq(dx / (double) rx) + sq(dy / (double) ry) + sq(dz / (double) rz);
                    double outer = sq(dx / (rx + 1.0)) + sq(dy / (ry + 1.0)) + sq(dz / (rz + 1.0));
                    p.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    double y = o.getY() + dy + 0.5;
                    if (inner < 1.0) {
                        level.setBlock(p, y < surface ? fluid : Blocks.AIR.defaultBlockState(), 2);
                    } else if (outer < 1.0 && y < surface + 1.0) {
                        level.setBlock(p, Blocks.BLUE_ICE.defaultBlockState(), 2); // borde frío alrededor del líquido
                    }
                }
        return true;
    }

    private static double sq(double v) {
        return v * v;
    }
}
