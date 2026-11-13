package dev.antme08.planetaryexpansion.dampener;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Consume estrés como cualquier máquina de Create; el núcleo lee su velocidad para cargarse. */
public class DampenerDriveBlockEntity extends KineticBlockEntity {
    public DampenerDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        DampenerCoreBlockEntity core = InertialDampener.findCore(level, worldPosition);
        if (core != null) core.appendStatus(tooltip);
        return true;
    }
}
