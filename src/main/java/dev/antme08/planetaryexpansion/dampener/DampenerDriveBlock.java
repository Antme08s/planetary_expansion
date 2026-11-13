package dev.antme08.planetaryexpansion.dampener;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Entrada de rotación: parte de la carcasa por la que entra un eje de Create para cargar el amortiguador. */
public class DampenerDriveBlock extends RotatedPillarKineticBlock implements IBE<DampenerDriveBlockEntity> {
    public DampenerDriveBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public Class<DampenerDriveBlockEntity> getBlockEntityClass() {
        return DampenerDriveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DampenerDriveBlockEntity> getBlockEntityType() {
        return InertialDampener.DRIVE_BE.get();
    }
}
