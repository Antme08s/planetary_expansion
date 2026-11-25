package dev.antme08.planetaryexpansion.freezer;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Congelador del tiempo espacial. POWERED = hay señal de redstone; ACTIVE = de verdad está congelando una instancia del
 * espacio profundo (lo pone el servidor; cambia la textura y da luz).
 */
public class TimeFreezerBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<TimeFreezerBlock> CODEC = simpleCodec(TimeFreezerBlock::new);

    public TimeFreezerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl) {
            String key = TimeFreezerBlockEntity.status(sl, pos, state);
            player.displayClientMessage(Component.translatable("message.planetary_expansion." + key)
                    .withStyle(key.equals("freezer_on") ? ChatFormatting.AQUA : key.equals("freezer_off") ? ChatFormatting.GRAY : ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    /** Cara de apoyo completa: se pueden pegar palancas y antorchas de redstone. */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimeFreezerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, TimeFreezer.BLOCK_ENTITY.get(), TimeFreezerBlockEntity::serverTick);
    }
}
