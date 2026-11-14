package dev.antme08.planetaryexpansion.ferra;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Chimenea de hierro de Ferra (vista previa aprobada): columna de roca de 6 a 17 bloques, en franjas de roca y ocre,
 * con vetas de hierro y menas de hierro y titanio a la vista y polvo en la cima. Radio máximo 3,6.
 */
public class RustPillarFeature extends Feature<NoneFeatureConfiguration> {
    public RustPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos base = ctx.origin();
        if (!level.getBlockState(base.below()).is(FerraBlocks.DUST.get()) && !level.getBlockState(base.below()).is(FerraBlocks.ROCK.get()))
            return false;
        int h = 6 + r.nextInt(12);
        float rad = 2.0F + r.nextFloat() * 1.6F;
        BlockState rock = FerraBlocks.ROCK.get().defaultBlockState(), ochre = FerraBlocks.OCHRE.get().defaultBlockState();
        BlockState vein = FerraBlocks.IRON_VEIN.get().defaultBlockState(), iron = FerraBlocks.IRON_ORE.get().defaultBlockState();
        BlockState titanium = FerraBlocks.TITANIUM_ORE.get().defaultBlockState(), dust = FerraBlocks.DUST.get().defaultBlockState();
        for (int k = -3; k < h; k++) {
            float rr = rad * (k < h - 2 ? 1.0F : 0.8F) + (k < 2 ? 0.6F : 0.0F);
            int ri = (int) Math.ceil(rr);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > rr * rr) continue;
                    float p = r.nextFloat();
                    BlockState s = p < 0.10F ? vein : p < 0.16F ? iron : p < 0.19F ? titanium : ((k + 3) / 3) % 2 == 0 ? ochre : rock;
                    if (k == h - 1) s = dust;
                    level.setBlock(base.offset(dx, k, dz), s, 2);
                }
            }
        }
        return true;
    }
}
