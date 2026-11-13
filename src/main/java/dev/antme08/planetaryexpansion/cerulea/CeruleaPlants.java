package dev.antme08.planetaryexpansion.cerulea;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Plantas de Cerúlea (1.0.3). Todas crecen sobre bloques de la etiqueta #minecraft:dirt (el pasto y la tierra de
 * Cerúlea están en ella), como las de Minecraft. */
public final class CeruleaPlants {
    private CeruleaPlants() {}

    /** Planta pequeña normal (pasto bajo, flores, hongo luminoso). */
    public static class Plant extends BushBlock {
        public static final MapCodec<Plant> CODEC = simpleCodec(Plant::new);
        private final VoxelShape shape;

        public Plant(BlockBehaviour.Properties p) {
            this(p, Block.box(2, 0, 2, 14, 13, 14));
        }

        public Plant(BlockBehaviour.Properties p, VoxelShape shape) {
            super(p);
            this.shape = shape;
        }

        @Override
        protected MapCodec<? extends BushBlock> codec() { return CODEC; }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) { return shape; }
    }

    /** Planta de dos bloques que también crece sobre arena (el junco del pantano). */
    public static class TallPlant extends DoublePlantBlock {
        private final boolean sand;

        public TallPlant(BlockBehaviour.Properties p, boolean sand) {
            super(p);
            this.sand = sand;
        }

        @Override
        protected boolean mayPlaceOn(BlockState s, BlockGetter l, BlockPos pos) {
            return super.mayPlaceOn(s, l, pos) || sand && s.is(BlockTags.SAND);
        }
    }

    /** Liana que cuelga de las hojas o troncos (o de otra liana), como las enredaderas llorosas. */
    public static class HangingVine extends Block {
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

        public HangingVine(BlockBehaviour.Properties p) { super(p); }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) { return SHAPE; }

        @Override
        protected boolean canSurvive(BlockState s, LevelReader l, BlockPos pos) {
            BlockState up = l.getBlockState(pos.above());
            return up.is(this) || up.is(BlockTags.LEAVES) || up.is(BlockTags.LOGS);
        }

        @Override
        protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos pos, BlockPos np) {
            return d == Direction.UP && !canSurvive(s, l, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(s, d, n, l, pos, np);
        }
    }

    /** Nenúfar azul. */
    public static class Lily extends WaterlilyBlock {
        public Lily(BlockBehaviour.Properties p) { super(p); }
    }

    /** Retoños de los tres árboles. */
    public static class Sapling extends SaplingBlock {
        public Sapling(TreeGrower grower, BlockBehaviour.Properties p) { super(grower, p); }
    }

    /** Arbusto de bayas azules: como el de bayas dulces, pero no pincha ni frena, y da bayas azules. */
    public static class BlueberryBush extends SweetBerryBushBlock {
        public BlueberryBush(BlockBehaviour.Properties p) { super(p); }

        @Override
        public ItemStack getCloneItemStack(LevelReader l, BlockPos pos, BlockState s) {
            return new ItemStack(CeruleaBlocks.BLUEBERRIES.get());
        }

        @Override
        protected void entityInside(BlockState s, Level l, BlockPos pos, Entity e) {}

        @Override
        protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            int age = s.getValue(AGE);
            if (age < 2) return InteractionResult.PASS;
            int n = 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0);
            popResource(level, pos, new ItemStack(CeruleaBlocks.BLUEBERRIES.get(), n));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            level.setBlock(pos, s.setValue(AGE, 1), 2);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Raíz cerúlea: cultivo de cuatro etapas. Crece en tierra arada y también (más despacio) en pasto o tierra. */
    public static class Roots extends CropBlock {
        public static final MapCodec<Roots> CODEC = simpleCodec(Roots::new);
        public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

        public Roots(BlockBehaviour.Properties p) { super(p); }

        @Override
        public MapCodec<? extends CropBlock> codec() { return CODEC; }

        @Override
        protected IntegerProperty getAgeProperty() { return AGE; }

        @Override
        public int getMaxAge() { return 3; }

        @Override
        protected ItemLike getBaseSeedId() { return CeruleaBlocks.CERULEAN_ROOT.get(); }

        @Override
        protected boolean mayPlaceOn(BlockState s, BlockGetter l, BlockPos pos) {
            return super.mayPlaceOn(s, l, pos) || s.is(BlockTags.DIRT);
        }

        @Override
        protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
            if (r.nextInt(3) != 0) super.randomTick(s, level, pos, r);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AGE); }
    }

    /** Fruta que cuelga bajo las hojas del árbol violeta. Se cosecha con clic derecho o rompiéndola. */
    public static class HangingFruit extends Block {
        private static final VoxelShape SHAPE = Block.box(4, 3, 4, 12, 16, 12);

        public HangingFruit(BlockBehaviour.Properties p) { super(p); }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) { return SHAPE; }

        @Override
        protected boolean canSurvive(BlockState s, LevelReader l, BlockPos pos) {
            return l.getBlockState(pos.above()).is(BlockTags.LEAVES);
        }

        @Override
        protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos pos, BlockPos np) {
            return d == Direction.UP && !canSurvive(s, l, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(s, d, n, l, pos, np);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            popResource(level, pos, new ItemStack(CeruleaBlocks.DUSKFRUIT_ITEM.get()));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CAVE_VINES_PICK_BERRIES,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            level.removeBlock(pos, false);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Hojas del árbol violeta: de vez en cuando les sale una fruta debajo (si hay aire). */
    public static class FruitLeaves extends LeavesBlock {
        public FruitLeaves(BlockBehaviour.Properties p) { super(p); }

        @Override
        protected boolean isRandomlyTicking(BlockState s) { return true; }

        @Override
        protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
            super.randomTick(s, level, pos, r);
            if (!level.getBlockState(pos).is(this)) return;          // se ha deshecho
            if (r.nextInt(90) == 0 && level.isEmptyBlock(pos.below()) && level.isEmptyBlock(pos.below(2)))
                level.setBlock(pos.below(), CeruleaBlocks.DUSKFRUIT.get().defaultBlockState(), 3);
        }
    }
}
