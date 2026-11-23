package dev.antme08.planetaryexpansion.engine;

import com.mojang.serialization.MapCodec;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlock;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Soporte de motor de metano (1.0.4). Es el soporte modular de Cosmonautics con su propio bloque y entidad: usa las mismas
 * tuberías y toberas, pero solo acepta metano líquido (más el oxidante de Cosmonautics) y rinde más. Todo el manejo
 * (colocar tuberías y tobera con el objeto en la mano, quitarlas con la llave, romperlo) lo hereda del soporte original.
 */
public class MethaneThrusterMountBlock extends ThrusterMountBlock {
    public static final MapCodec<MethaneThrusterMountBlock> CODEC = simpleCodec(MethaneThrusterMountBlock::new);

    public MethaneThrusterMountBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public Class<ThrusterMountBlockEntity> getBlockEntityClass() {
        return ThrusterMountBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ThrusterMountBlockEntity> getBlockEntityType() {
        return MethaneEngine.MOUNT_BE.get();
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == getBlockEntityType()
                ? (l, pos, st, be) -> ThrusterMountBlockEntity.tick(l, pos, st, (ThrusterMountBlockEntity) be)
                : null;
    }
}
