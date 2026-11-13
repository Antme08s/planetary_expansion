package dev.antme08.planetaryexpansion.dampener;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Núcleo del amortiguador inercial: va en el centro del cubo de 3x3x3. */
public class DampenerCoreBlock extends Block implements EntityBlock {
    public DampenerCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DampenerCoreBlockEntity(InertialDampener.CORE_BE.get(), pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != InertialDampener.CORE_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<DampenerCoreBlockEntity>) DampenerCoreBlockEntity::serverTick;
    }
}
