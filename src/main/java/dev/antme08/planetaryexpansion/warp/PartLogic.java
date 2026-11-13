package dev.antme08.planetaryexpansion.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Comportamiento común de las partes (relleno invisible y entradas de eje): todo se le pasa al ancla. */
final class PartLogic {
    private PartLogic() {}

    static BlockPos anchor(BlockPos part, BlockState state) {
        return Footprint.anchorOf(part, state);
    }

    /** Estado del ancla si existe y esta parte le pertenece; si no, null. */
    static BlockState anchorState(BlockGetter level, BlockPos part, BlockState state) {
        BlockState a = level.getBlockState(anchor(part, state));
        if (a.getBlock() instanceof WarpAnchorBlock anchor && anchor.isOwnPart(state)) return a;
        return null;
    }

    static VoxelShape shape(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState a = anchorState(level, pos, state);
        if (a == null) return Shapes.block();
        Footprint.Cell c = Footprint.cellOf(pos, state, a.getValue(WarpAnchorBlock.FACING));
        return ((WarpAnchorBlock) a.getBlock()).partShape(c);
    }

    static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        BlockState a = anchorState(level, pos, state);
        if (a == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockPos ap = anchor(pos, state);
        return a.useItemOn(stack, level, player, hand, hit.withPosition(ap));
    }

    static InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockState a = anchorState(level, pos, state);
        if (a == null) return InteractionResult.PASS;
        return a.useWithoutItem(level, player, hit.withPosition(anchor(pos, state)));
    }

    /** Romper una parte rompe el ancla (que suelta el objeto y quita el resto). */
    static void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide) return;
        BlockState a = anchorState(level, pos, state);
        if (a == null) return;
        BlockPos ap = anchor(pos, state);
        if (player.isCreative()) {
            level.levelEvent(player, 2001, ap, Block.getId(a));
            level.setBlock(ap, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        } else {
            level.destroyBlock(ap, true, player);
        }
    }

    static ItemStack cloneItem(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState a = anchorState(level, pos, state);
        return a == null ? ItemStack.EMPTY : new ItemStack(a.getBlock());
    }
}
