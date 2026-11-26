package dev.antme08.planetaryexpansion.fulgor.mobs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Pararrayos de tormenta (1.0.4): en Fulgor atrae los rayos desde 256 bloques (el normal, 128) y da un pulso de redstone
 * de nivel 15 durante un segundo cuando le cae uno. Los atrapa-rayos se alejan de él (y de los pararrayos normales).
 */
public class StormRodBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(5, 0, 5, 11, 2.5, 11), Block.box(6.4, 2.5, 6.4, 9.6, 16, 9.6));

    public StormRodBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POWERED); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) { return state.getValue(POWERED) ? 15 : 0; }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return state.getValue(POWERED) && dir == Direction.UP ? 15 : 0;
    }

    /** Le ha caído un rayo: pulso de redstone de 1 s y chispas. */
    public void struck(BlockState state, ServerLevel level, BlockPos pos) {
        level.setBlock(pos, state.setValue(POWERED, true), 3);
        level.updateNeighborsAt(pos.below(), this);
        level.scheduleTick(pos, this, 20);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 24, 0.25, 0.5, 0.25, 0.3);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 3);
            level.updateNeighborsAt(pos.below(), this);
        }
    }

    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, RandomSource random) {
        if (FulgorMobs.stormy(level) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + random.nextGaussian() * 0.1, pos.getY() + 1.0,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.1, 0, 0.05, 0);
        }
        if (state.getValue(POWERED)) level.playLocalSound(pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.3F, 1.6F, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("block.planetary_expansion.storm_rod.desc").withStyle(ChatFormatting.GRAY));
    }
}
