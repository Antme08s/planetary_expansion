package dev.antme08.planetaryexpansion.fulgor.mobs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.List;

/**
 * Lámpara de arco (1.0.4): la glándula del arco entre cobre y vidrio. Da luz 11 y, cuando hay tormenta eléctrica (la de
 * Fulgor o una de la Tierra), luz 15 con una textura que parpadea y chispas. Se comprueba cada 5 s.
 */
public class ArcLampBlock extends Block {
    public static final BooleanProperty STORM = BooleanProperty.create("storm");

    public ArcLampBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(STORM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(STORM); }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean s = FulgorMobs.stormy(level);
        if (s != state.getValue(STORM)) level.setBlock(pos, state.setValue(STORM, s), 3);
        level.scheduleTick(pos, this, 100);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STORM) && random.nextInt(3) == 0) {
            var d = net.minecraft.core.Direction.getRandom(random);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + d.getStepX() * 0.55, pos.getY() + 0.5 + d.getStepY() * 0.55,
                    pos.getZ() + 0.5 + d.getStepZ() * 0.55, 0, 0, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("block.planetary_expansion.arc_lamp.desc").withStyle(ChatFormatting.GRAY));
    }
}
