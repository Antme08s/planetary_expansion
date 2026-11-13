package dev.antme08.planetaryexpansion.dampener;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entrada de redstone del amortiguador (0.7.32): solo una señal que llegue a este bloque lo dispara. Con un
 * comparador da la carga (0-14) y 15 cuando está cargado y frío, listo para usarse.
 */
public class DampenerRedstonePortBlock extends Block {
    public DampenerRedstonePortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        DampenerCoreBlockEntity core = InertialDampener.findCore(level, pos);
        return core == null ? 0 : core.comparatorLevel();
    }
}
