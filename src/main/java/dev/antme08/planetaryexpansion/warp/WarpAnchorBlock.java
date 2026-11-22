package dev.antme08.planetaryexpansion.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.List;

/**
 * Ancla de un bloque grande (motor de distorsión o consola de navegación): el bloque que pone el jugador. Al ponerlo
 * llena el resto de la huella con partes invisibles; al quitarlo (o romper cualquier parte) se quita todo.
 * El modelo 3D completo lo dibuja el BlockEntityRenderer del ancla.
 */
public abstract class WarpAnchorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected WarpAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Celdas de la huella sin contar el ancla. */
    public abstract List<Footprint.Cell> cells();

    /** Estado de la parte que va en la celda (sin el desplazamiento, lo pone {@link Footprint#withOffset}). */
    protected abstract BlockState partState(Footprint.Cell cell, Direction facing);

    /** ¿Es una parte de este tipo de bloque grande? */
    public abstract boolean isOwnPart(BlockState state);

    /** Forma de la parte en la celda (por defecto, bloque completo). */
    public net.minecraft.world.phys.shapes.VoxelShape partShape(Footprint.Cell cell) {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        Level level = context.getLevel();
        BlockPos anchor = context.getClickedPos();
        for (Footprint.Cell c : cells()) {
            BlockPos p = Footprint.pos(anchor, facing, c);
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeParts(level, pos, state);
    }

    /** Pone las partes que falten (solo en huecos libres). */
    public void placeParts(Level level, BlockPos anchor, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (Footprint.Cell c : cells()) {
            BlockPos p = Footprint.pos(anchor, facing, c);
            BlockState there = level.getBlockState(p);
            if (isOwnPart(there) && Footprint.anchorOf(p, there).equals(anchor)) continue;
            if (!there.isAir() && !there.canBeReplaced()) continue;
            level.setBlock(p, Footprint.withOffset(partState(c, facing), anchor, p), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !isMoving && state.getBlock() != newState.getBlock()) {
            Direction facing = state.getValue(FACING);
            for (Footprint.Cell c : cells()) {
                BlockPos p = Footprint.pos(pos, facing, c);
                BlockState there = level.getBlockState(p);
                if (isOwnPart(there) && Footprint.anchorOf(p, there).equals(pos)) level.removeBlock(p, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        // las partes guardan su desplazamiento hasta el ancla y lo giran ellas mismas (Footprint.rotateOffset)
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }
}
