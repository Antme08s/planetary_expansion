package dev.antme08.planetaryexpansion.gravity;

import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Generador de gravedad: bloque cinético de Create. Desde 0.7.18 además necesita una señal de redstone para
 * encenderse (petición del usuario).
 *
 * Desde 0.7.34 es un modelo 3D de 1 bloque de ancho por 2 de alto (petición del usuario): este bloque es la base,
 * con el eje atravesándola en horizontal, y encima va {@link GravityGeneratorTopBlock}, invisible, que solo ocupa el
 * sitio de la jaula con el núcleo. El modelo entero se dibuja desde la base.
 */
public class GravityGeneratorBlock extends HorizontalAxisKineticBlock implements IBE<GravityGeneratorBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16), Block.box(1, 3, 1, 15, 11, 15), Block.box(2, 11, 2, 14, 16, 14));

    public GravityGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        // necesita un bloque libre encima para la jaula
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return state.setValue(POWERED, level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeTop(level, pos);
    }

    /** Pone la parte de arriba si el hueco está libre. @return true si la parte de arriba está en su sitio. */
    public static boolean placeTop(Level level, BlockPos base) {
        BlockPos above = base.above();
        BlockState there = level.getBlockState(above);
        if (there.getBlock() instanceof GravityGeneratorTopBlock) return true;
        if (!there.isAir() && !there.canBeReplaced()) return false;
        return level.setBlock(above, GravityGenerator.TOP.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (state.getBlock() == newState.getBlock() || level.isClientSide) return;
        BlockPos above = pos.above();
        if (level.getBlockState(above).getBlock() instanceof GravityGeneratorTopBlock) level.removeBlock(above, false);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        updatePowered(level, pos);
    }

    /**
     * Actualiza la propiedad POWERED. Desde 0.7.35 cuenta la señal que llegue a cualquiera de las dos partes
     * (la base o la jaula de arriba), para que la palanca se pueda poner donde sea.
     */
    public static void updatePowered(Level level, BlockPos base) {
        if (level.isClientSide) return;
        BlockState state = level.getBlockState(base);
        if (!(state.getBlock() instanceof GravityGeneratorBlock)) return;
        boolean powered = level.hasNeighborSignal(base)
                || (level.getBlockState(base.above()).getBlock() instanceof GravityGeneratorTopBlock && level.hasNeighborSignal(base.above()));
        if (powered != state.getValue(POWERED)) level.setBlock(base, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
    }

    /** Cara de apoyo completa: así se pueden pegar palancas, botones o antorchas de redstone al generador. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<GravityGeneratorBlockEntity> getBlockEntityClass() {
        return GravityGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GravityGeneratorBlockEntity> getBlockEntityType() {
        return GravityGenerator.BLOCK_ENTITY.get();
    }
}
