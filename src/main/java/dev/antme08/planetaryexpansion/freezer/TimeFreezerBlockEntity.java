package dev.antme08.planetaryexpansion.freezer;

import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Con señal de redstone y en el espacio profundo, sujeta la instancia de su nave (ver {@link SpaceTimeFreeze}).
 * Guarda una clave propia ("dueño") y la instancia que está sujetando, para soltarla aunque el bloque haya cambiado de sitio
 * (la nave aterrizó). Descargar el chunk no suelta nada: la nave sigue fija.
 */
public class TimeFreezerBlockEntity extends BlockEntity {
    private long owner;
    private long frozenId;
    private boolean holding;
    private boolean unloading;

    public TimeFreezerBlockEntity(BlockPos pos, BlockState state) {
        super(TimeFreezer.BLOCK_ENTITY.get(), pos, state);
    }

    private long owner() {
        if (owner == 0) {
            owner = java.util.concurrent.ThreadLocalRandom.current().nextLong();
            if (owner == 0) owner = 1;
            setChanged();
        }
        return owner;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TimeFreezerBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        DeepSpaceInstance inst = state.getValue(TimeFreezerBlock.POWERED) ? SpaceTimeFreeze.instanceOf(sl, pos) : null;
        if (inst != null) {
            if (be.holding && be.frozenId != inst.getId()) SpaceTimeFreeze.disengage(sl.getServer(), be.frozenId, be.owner());
            SpaceTimeFreeze.engage(sl, inst, be.owner());
            if (!be.holding || be.frozenId != inst.getId()) {
                be.holding = true;
                be.frozenId = inst.getId();
                be.setChanged();
            }
        } else if (be.holding) {
            SpaceTimeFreeze.disengage(sl.getServer(), be.frozenId, be.owner());
            be.holding = false;
            be.setChanged();
        }
        boolean frozen = inst != null;
        if (frozen != state.getValue(TimeFreezerBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(TimeFreezerBlock.ACTIVE, frozen), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // roto o movido (no descargado): soltar la nave
        if (!unloading && holding && level instanceof ServerLevel sl) {
            SpaceTimeFreeze.disengage(sl.getServer(), frozenId, owner());
            holding = false;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != 0) tag.putLong("FreezerOwner", owner);
        if (holding) tag.putLong("FrozenInstance", frozenId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.getLong("FreezerOwner");
        holding = tag.contains("FrozenInstance");
        frozenId = tag.getLong("FrozenInstance");
    }

    /** Estado para el mensaje al usar el bloque. */
    public static String status(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(TimeFreezerBlock.POWERED)) return "freezer_off";
        return SpaceTimeFreeze.instanceOf(level, pos) == null ? "freezer_no_space" : "freezer_on";
    }
}
