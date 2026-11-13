package dev.antme08.planetaryexpansion.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Parte de arriba del generador de gravedad (0.7.34): invisible, sin objeto ni botín. Solo ocupa el sitio de la
 * jaula para que no se pueda poner nada ahí y se pueda golpear; el modelo lo dibuja la base.
 *
 * Si un jugador la rompe, se rompe la base (que suelta el generador). Si desaparece por otra causa, la base la
 * vuelve a poner sola; así no se cae el generador cuando otro mod mueve los bloques de uno en uno (por ejemplo al
 * ensamblar una nave).
 */
public class GravityGeneratorTopBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 14, 14), Block.box(5, 14, 5, 11, 16, 11));

    public GravityGeneratorTopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Cara de apoyo completa: se pueden pegar palancas o botones a la jaula. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** La señal de redstone que llega a la jaula también enciende el generador. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        GravityGeneratorBlock.updatePowered(level, pos.below());
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos below = pos.below();
        BlockState base = level.getBlockState(below);
        if (!level.isClientSide && base.getBlock() instanceof GravityGeneratorBlock) {
            if (player.isCreative()) {
                level.levelEvent(player, 2001, below, Block.getId(base));
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            } else {
                level.destroyBlock(below, true, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(GravityGenerator.ITEM.get());
    }
}
