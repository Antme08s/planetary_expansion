package dev.antme08.planetaryexpansion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Chimenea hidrotermal de las cuevas de Thalassa (0.7.26, idea del usuario: raras y sin luz).
 * Sobre el suelo de una cueva inundada: chimenea hueca de basalto y piedra negra de 3-7 bloques, arena de almas en
 * el fondo y una columna de burbujas que sube hasta el techo. Sin magma, para que las cuevas sigan totalmente oscuras.
 */
public class ThalassaVentFeature extends Feature<NoneFeatureConfiguration> {
    public static final DeferredHolder<Feature<?>, ThalassaVentFeature> VENT =
            VenusLavaVentFeature.FEATURES.register("thalassa_vent", ThalassaVentFeature::new);

    /** Se llama desde VenusLavaVentFeature.register para que el registro diferido ya tenga esta entrada. */
    public static void touch() {}

    public ThalassaVentFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    private static boolean water(WorldGenLevel level, BlockPos p) {
        return level.getFluidState(p).is(Fluids.WATER) && level.getBlockState(p).is(Blocks.WATER);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos base = ctx.origin();
        // origen: primer bloque de agua encima del suelo de la cueva
        if (!water(level, base) || !level.getBlockState(base.below()).isSolid()) return false;
        int height = 3 + random.nextInt(5);
        for (int i = 0; i <= height + 1; i++) if (!water(level, base.above(i))) return false;

        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState black = Blocks.BLACKSTONE.defaultBlockState();
        BlockState smooth = Blocks.SMOOTH_BASALT.defaultBlockState();
        // mancha oscura alrededor de la base
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 9 || random.nextInt(4) == 0) continue;
                BlockPos g = base.offset(dx, -1, dz);
                if (level.getBlockState(g).isSolid()) level.setBlock(g, random.nextBoolean() ? smooth : black, 2);
            }
        }
        // chimenea hueca (anillo 3x3 sin esquinas), más estrecha arriba
        for (int i = 0; i < height; i++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) + Math.abs(dz) == 2 && i >= height - 2) continue;
                    BlockPos p = base.offset(dx, i, dz);
                    if (water(level, p)) level.setBlock(p, random.nextInt(3) == 0 ? black : basalt, 2);
                }
            }
        }
        // fondo de arena de almas y columna de burbujas hacia arriba hasta el techo
        level.setBlock(base.below(), Blocks.SOUL_SAND.defaultBlockState(), 2);
        BlockState bubbles = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false);
        BlockPos.MutableBlockPos p = base.mutable();
        for (int i = 0; i < 80 && water(level, p); i++) {
            level.setBlock(p, bubbles, 2);
            p.move(0, 1, 0);
        }
        // 0.7.61: cangrejos de chimenea alrededor
        dev.antme08.planetaryexpansion.creatures.Creatures.spawnVentCrabs(level, base, random);
        return true;
    }
}
