package dev.antme08.planetaryexpansion.velo;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Aguja de cristal de los campos de cristal de Velo (vista previa aprobada): columna de cristal cerúleo que se
 * estrecha y se inclina un poco, con cristal brotante cerca de la base, un racimo en la punta y algunos brotes
 * alrededor en el polvo. Radio máximo 3 + inclinación 3, así no se sale de la zona que puede tocar un feature.
 */
public class CrystalSpireFeature extends Feature<NoneFeatureConfiguration> {
    public CrystalSpireFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos base = ctx.origin();
        BlockState ground = level.getBlockState(base.below());
        if (!ground.is(VeloBlocks.DUST.get()) && !ground.is(VeloBlocks.ROCK.get())) return false;
        int h = 5 + r.nextInt(10);
        float lx = (r.nextFloat() - 0.5F) * 0.45F, lz = (r.nextFloat() - 0.5F) * 0.45F;
        BlockState crystal = VeloBlocks.CRYSTAL_BLOCK.get().defaultBlockState();
        BlockState budding = VeloBlocks.BUDDING.get().defaultBlockState();
        int topX = base.getX(), topZ = base.getZ();
        for (int k = -2; k < h; k++) {
            float t = Math.max(0, k) / (float) h;
            float rad = (float) (2.4 * Math.pow(1 - t, 1.4) + 0.3);
            int cx = base.getX() + Math.round(lx * Math.max(0, k)), cz = base.getZ() + Math.round(lz * Math.max(0, k));
            int ri = (int) Math.ceil(rad);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > rad * rad + 0.5F) continue;
                    BlockPos p = new BlockPos(cx + dx, base.getY() + k, cz + dz);
                    boolean bud = k <= 1 && r.nextInt(4) == 0;
                    level.setBlock(p, bud ? budding : crystal, 2);
                }
            }
            topX = cx;
            topZ = cz;
        }
        BlockPos tip = new BlockPos(topX, base.getY() + h, topZ);
        if (level.isEmptyBlock(tip)) level.setBlock(tip, cluster(VeloBlocks.CLUSTER.get().defaultBlockState(), Direction.UP), 2);
        // brotes sueltos en el polvo de alrededor
        for (int i = 0; i < 6; i++) {
            BlockPos p = base.offset(r.nextInt(9) - 4, 0, r.nextInt(9) - 4);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos q = p.above(dy);
                if (level.isEmptyBlock(q) && level.getBlockState(q.below()).is(VeloBlocks.DUST.get())) {
                    BlockState s = switch (r.nextInt(4)) {
                        case 0 -> VeloBlocks.SMALL_BUD.get().defaultBlockState();
                        case 1 -> VeloBlocks.MEDIUM_BUD.get().defaultBlockState();
                        case 2 -> VeloBlocks.LARGE_BUD.get().defaultBlockState();
                        default -> VeloBlocks.CLUSTER.get().defaultBlockState();
                    };
                    level.setBlock(q, cluster(s, Direction.UP), 2);
                    break;
                }
            }
        }
        return true;
    }

    private static BlockState cluster(BlockState s, Direction d) {
        return s.setValue(AmethystClusterBlock.FACING, d).setValue(AmethystClusterBlock.WATERLOGGED, false);
    }
}
