package dev.antme08.planetaryexpansion.freezer;

import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Cada tick, con señal de redstone y en el espacio profundo, mantiene congelada la instancia de su nave. */
public class TimeFreezerBlockEntity extends BlockEntity {
    public TimeFreezerBlockEntity(BlockPos pos, BlockState state) {
        super(TimeFreezer.BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TimeFreezerBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        boolean frozen = false;
        if (state.getValue(TimeFreezerBlock.POWERED)) {
            DeepSpaceInstance inst = SpaceTimeFreeze.instanceOf(sl, pos);
            if (inst != null) {
                SpaceTimeFreeze.hold(sl, inst);
                frozen = true;
            }
        }
        if (frozen != state.getValue(TimeFreezerBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(TimeFreezerBlock.ACTIVE, frozen), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Estado para el mensaje al usar el bloque. */
    public static String status(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(TimeFreezerBlock.POWERED)) return "freezer_off";
        return SpaceTimeFreeze.instanceOf(level, pos) == null ? "freezer_no_space" : "freezer_on";
    }
}
