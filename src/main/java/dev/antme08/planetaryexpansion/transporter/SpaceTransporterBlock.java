package dev.antme08.planetaryexpansion.transporter;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Transportador espacial: bloque cinético de Create (eje que lo atraviesa) con un tanque de nitrógeno líquido. */
public class SpaceTransporterBlock extends RotatedPillarKineticBlock implements IBE<SpaceTransporterBlockEntity> {
    public SpaceTransporterBlock(Properties properties) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SpaceTransporterBlockEntity be && FluidUtil.getFluidHandler(stack).isPresent()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, be.tank())) return ItemInteractionResult.sidedSuccess(level.isClientSide);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpaceTransporterBlockEntity be) {
            player.displayClientMessage(be.summary(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<SpaceTransporterBlockEntity> getBlockEntityClass() {
        return SpaceTransporterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpaceTransporterBlockEntity> getBlockEntityType() {
        return SpaceTransporter.BLOCK_ENTITY.get();
    }
}
