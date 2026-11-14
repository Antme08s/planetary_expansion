package dev.antme08.planetaryexpansion.velo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/** Cristal cerúleo brotante: como la amatista brotante, le salen brotes que crecen hasta ser un racimo. */
public class BuddingCrystalBlock extends AmethystBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    public BuddingCrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction d = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos p = pos.relative(d);
        BlockState there = level.getBlockState(p);
        Block next = null;
        if (BuddingAmethystBlock.canClusterGrowAtState(there)) next = VeloBlocks.SMALL_BUD.get();
        else if (there.is(VeloBlocks.SMALL_BUD.get()) && there.getValue(AmethystClusterBlock.FACING) == d) next = VeloBlocks.MEDIUM_BUD.get();
        else if (there.is(VeloBlocks.MEDIUM_BUD.get()) && there.getValue(AmethystClusterBlock.FACING) == d) next = VeloBlocks.LARGE_BUD.get();
        else if (there.is(VeloBlocks.LARGE_BUD.get()) && there.getValue(AmethystClusterBlock.FACING) == d) next = VeloBlocks.CLUSTER.get();
        if (next != null) {
            level.setBlockAndUpdate(p, next.defaultBlockState().setValue(AmethystClusterBlock.FACING, d)
                    .setValue(AmethystClusterBlock.WATERLOGGED, there.getFluidState().getType() == Fluids.WATER));
        }
    }
}
