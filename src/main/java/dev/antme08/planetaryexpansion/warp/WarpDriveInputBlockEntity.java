package dev.antme08.planetaryexpansion.warp;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Consume estrés como cualquier máquina de Create; el motor lee su velocidad para cargarse. */
public class WarpDriveInputBlockEntity extends KineticBlockEntity {
    public WarpDriveInputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (level != null && level.getBlockEntity(Footprint.anchorOf(worldPosition, getBlockState())) instanceof WarpDriveBlockEntity drive) {
            drive.appendStatus(tooltip);
        }
        return true;
    }
}
