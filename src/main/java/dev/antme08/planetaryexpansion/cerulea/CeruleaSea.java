package dev.antme08.planetaryexpansion.cerulea;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Plantas del mar de Cerúlea (1.0.3; texturas de tools/designs/cerulea_sea_plants.py):
 * <ul>
 *   <li>Alga de cinta: como el kelp (crece en columna); se seca en el horno y se hace bloque (combustible).</li>
 *   <li>Alga de perlas: idea del usuario, "como las bayas luminosas pero marinas". Crece en columna; algunos trozos dan
 *   racimos de perlas que brillan (luz 14) y se cosechan con clic derecho; vuelven a salir con el tiempo. Las perlas se
 *   comen, se plantan y hacen tinta luminosa.</li>
 *   <li>Pasto marino cerúleo (bajo y alto): solo con tijeras.</li>
 *   <li>Tubos luminosos (luz 10) y abanico azul: decoración; se ponen también fuera del agua.</li>
 * </ul>
 * La generación (mar y ríos) la hace {@link SeaPlantsFeature}.
 */
public final class CeruleaSea {
    private CeruleaSea() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MODID);

    public static final BooleanProperty BERRIES = BlockStateProperties.BERRIES;

    private static BlockBehaviour.Properties water(MapColor c) {
        return BlockBehaviour.Properties.of().mapColor(c).noCollission().instabreak().sound(SoundType.WET_GRASS)
                .pushReaction(PushReaction.DESTROY);
    }

    // ------------------------------------------------------------------ alga de cinta

    public static class RibbonKelp extends KelpBlock {
        public static final MapCodec<RibbonKelp> CODEC = simpleCodec(RibbonKelp::new);

        public RibbonKelp(Properties p) { super(p); }

        @Override
        public MapCodec<KelpBlock> codec() { return (MapCodec) CODEC; }

        @Override
        protected Block getBodyBlock() { return RIBBON_KELP_PLANT.get(); }
    }

    public static class RibbonKelpPlant extends KelpPlantBlock {
        public static final MapCodec<RibbonKelpPlant> CODEC = simpleCodec(RibbonKelpPlant::new);

        public RibbonKelpPlant(Properties p) { super(p); }

        @Override
        public MapCodec<KelpPlantBlock> codec() { return (MapCodec) CODEC; }

        @Override
        protected GrowingPlantHeadBlock getHeadBlock() { return RIBBON_KELP.get(); }
    }

    // ------------------------------------------------------------------ alga de perlas

    static InteractionResult pick(BlockState state, Level level, BlockPos pos, Player player) {
        if (!state.getValue(BERRIES)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            Block.popResource(level, pos, new ItemStack(GLOW_PEARLS.get(), 1 + (level.random.nextFloat() < 0.35F ? 1 : 0)));
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.3F);
            level.setBlock(pos, state.setValue(BERRIES, false), 2);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static class PearlWeed extends KelpBlock {
        public static final MapCodec<PearlWeed> CODEC = simpleCodec(PearlWeed::new);

        public PearlWeed(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(BERRIES, false));
        }

        @Override
        public MapCodec<KelpBlock> codec() { return (MapCodec) CODEC; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            super.createBlockStateDefinition(b);
            b.add(BERRIES);
        }

        @Override
        protected Block getBodyBlock() { return PEARL_WEED_PLANT.get(); }

        @Override
        protected BlockState getGrowIntoState(BlockState state, RandomSource random) {
            return super.getGrowIntoState(state, random).setValue(BERRIES, random.nextFloat() < 0.25F);
        }

        @Override
        protected BlockState updateBodyAfterConvertedFromHead(BlockState head, BlockState body) {
            return body.setValue(BERRIES, head.getValue(BERRIES));
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (!state.getValue(BERRIES) && random.nextInt(10) == 0) {
                level.setBlock(pos, state.setValue(BERRIES, true), 2);
                return;
            }
            super.randomTick(state, level, pos, random);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return pick(state, level, pos, player);
        }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return new ItemStack(GLOW_PEARLS.get()); }
    }

    public static class PearlWeedPlant extends KelpPlantBlock {
        public static final MapCodec<PearlWeedPlant> CODEC = simpleCodec(PearlWeedPlant::new);

        public PearlWeedPlant(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(BERRIES, false));
        }

        @Override
        public MapCodec<KelpPlantBlock> codec() { return (MapCodec) CODEC; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            super.createBlockStateDefinition(b);
            b.add(BERRIES);
        }

        @Override
        protected GrowingPlantHeadBlock getHeadBlock() { return PEARL_WEED.get(); }

        @Override
        protected BlockState updateHeadAfterConvertedFromBody(BlockState body, BlockState head) {
            return head.setValue(BERRIES, body.getValue(BERRIES));
        }

        /** Las perlas vuelven a salir solas (unos minutos). */
        @Override
        protected boolean isRandomlyTicking(BlockState state) { return !state.getValue(BERRIES); }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (random.nextInt(10) == 0) level.setBlock(pos, state.setValue(BERRIES, true), 2);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return pick(state, level, pos, player);
        }
    }

    // ------------------------------------------------------------------ pasto marino

    public static class CeruleanSeagrass extends SeagrassBlock {
        public static final MapCodec<CeruleanSeagrass> CODEC = simpleCodec(CeruleanSeagrass::new);

        public CeruleanSeagrass(Properties p) { super(p); }

        @Override
        public MapCodec<SeagrassBlock> codec() { return (MapCodec) CODEC; }

        @Override
        public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
            BlockState lower = TALL_SEAGRASS.get().defaultBlockState();
            BlockPos above = pos.above();
            if (level.getBlockState(above).is(Blocks.WATER)) {
                level.setBlock(pos, lower, 2);
                level.setBlock(above, lower.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
            }
        }
    }

    public static class TallCeruleanSeagrass extends TallSeagrassBlock {
        public static final MapCodec<TallCeruleanSeagrass> CODEC = simpleCodec(TallCeruleanSeagrass::new);

        public TallCeruleanSeagrass(Properties p) { super(p); }

        @Override
        public MapCodec<TallSeagrassBlock> codec() { return (MapCodec) CODEC; }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) { return new ItemStack(SEAGRASS.get()); }
    }

    // ------------------------------------------------------------------ tubos y abanico

    /** Planta de decoración del fondo: dentro o fuera del agua (con agua dentro si la hay), necesita suelo firme. */
    public static class SeaDecor extends Block implements SimpleWaterloggedBlock {
        public static final MapCodec<SeaDecor> CODEC = simpleCodec(SeaDecor::new);
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

        public SeaDecor(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.WATERLOGGED, true));
        }

        @Override
        protected MapCodec<? extends Block> codec() { return CODEC; }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(BlockStateProperties.WATERLOGGED); }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            FluidState f = ctx.getLevel().getFluidState(ctx.getClickedPos());
            return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, f.getType() == Fluids.WATER);
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            if (state.getValue(BlockStateProperties.WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                    : super.updateShape(state, dir, other, level, pos, otherPos);
        }

        @Override
        protected FluidState getFluidState(BlockState state) {
            return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
        }
    }

    // ------------------------------------------------------------------ registros

    public static final DeferredBlock<RibbonKelp> RIBBON_KELP = BLOCKS.register("ribbon_kelp",
            () -> new RibbonKelp(water(MapColor.WATER).randomTicks()));
    public static final DeferredBlock<RibbonKelpPlant> RIBBON_KELP_PLANT = BLOCKS.register("ribbon_kelp_plant",
            () -> new RibbonKelpPlant(water(MapColor.WATER)));
    public static final DeferredBlock<PearlWeed> PEARL_WEED = BLOCKS.register("pearl_weed",
            () -> new PearlWeed(water(MapColor.WATER).randomTicks().lightLevel(s -> s.getValue(BERRIES) ? 14 : 0)));
    public static final DeferredBlock<PearlWeedPlant> PEARL_WEED_PLANT = BLOCKS.register("pearl_weed_plant",
            () -> new PearlWeedPlant(water(MapColor.WATER).randomTicks().lightLevel(s -> s.getValue(BERRIES) ? 14 : 0)));
    public static final DeferredBlock<CeruleanSeagrass> SEAGRASS = BLOCKS.register("cerulean_seagrass",
            () -> new CeruleanSeagrass(water(MapColor.WATER).replaceable()));
    public static final DeferredBlock<TallCeruleanSeagrass> TALL_SEAGRASS = BLOCKS.register("tall_cerulean_seagrass",
            () -> new TallCeruleanSeagrass(water(MapColor.WATER).replaceable()));
    public static final DeferredBlock<SeaDecor> GLOW_TUBES = BLOCKS.register("glow_tubes",
            () -> new SeaDecor(water(MapColor.COLOR_CYAN).sound(SoundType.CORAL_BLOCK).lightLevel(s -> 10)));
    public static final DeferredBlock<SeaDecor> AZURE_FAN = BLOCKS.register("azure_fan",
            () -> new SeaDecor(water(MapColor.COLOR_BLUE).sound(SoundType.CORAL_BLOCK)));
    public static final DeferredBlock<Block> DRIED_KELP_BLOCK = BLOCKS.registerSimpleBlock("dried_ribbon_kelp_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F, 2.5F).sound(SoundType.GRASS));

    public static final DeferredItem<BlockItem> RIBBON_KELP_ITEM = ITEMS.registerSimpleBlockItem(RIBBON_KELP);
    public static final DeferredItem<Item> GLOW_PEARLS = ITEMS.register("glow_pearls", () -> new ItemNameBlockItem(PEARL_WEED.get(),
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())));
    public static final DeferredItem<Item> DRIED_KELP = ITEMS.registerSimpleItem("dried_ribbon_kelp",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.3F).fast().build()));
    public static final DeferredItem<BlockItem> DRIED_KELP_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(DRIED_KELP_BLOCK);
    public static final DeferredItem<BlockItem> SEAGRASS_ITEM = ITEMS.registerSimpleBlockItem(SEAGRASS);
    public static final DeferredItem<BlockItem> GLOW_TUBES_ITEM = ITEMS.registerSimpleBlockItem(GLOW_TUBES);
    public static final DeferredItem<BlockItem> AZURE_FAN_ITEM = ITEMS.registerSimpleBlockItem(AZURE_FAN);

    public static final DeferredHolder<Feature<?>, SeaPlantsFeature> SEA_PLANTS = FEATURES.register("cerulea_sea_plants", SeaPlantsFeature::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(CeruleaSea::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab"))) return;
        try {
            for (DeferredItem<?> item : List.of(RIBBON_KELP_ITEM, DRIED_KELP, DRIED_KELP_BLOCK_ITEM, GLOW_PEARLS, SEAGRASS_ITEM,
                    GLOW_TUBES_ITEM, AZURE_FAN_ITEM))
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Cerúlea] No se pudieron añadir las plantas del mar a la pestaña", e);
        }
    }

    // ------------------------------------------------------------------ generación

    /**
     * Pone plantas del mar alrededor del origen (tries intentos en un radio): busca el fondo con el heightmap
     * OCEAN_FLOOR y solo planta en agua. kind: seagrass (bajo y alto), kelp (alga de cinta, 2–8 de alto si hay hondura),
     * pearl (alga de perlas, 2–6, con perlas al azar), tubes, fan.
     */
    public static class SeaPlantsFeature extends Feature<SeaPlantsFeature.Config> {
        public record Config(String kind, int tries, int radius) implements FeatureConfiguration {
            public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("kind").forGetter(Config::kind),
                    Codec.INT.fieldOf("tries").forGetter(Config::tries),
                    Codec.INT.fieldOf("radius").forGetter(Config::radius)).apply(i, Config::new));
        }

        public SeaPlantsFeature() { super(Config.CODEC); }

        private static boolean water(WorldGenLevel level, BlockPos p) {
            return level.getBlockState(p).is(Blocks.WATER) && level.getFluidState(p).isSource();
        }

        private static int depth(WorldGenLevel level, BlockPos p) {
            int d = 0;
            while (d < 24 && water(level, p.above(d))) d++;
            return d;
        }

        @Override
        public boolean place(FeaturePlaceContext<Config> ctx) {
            WorldGenLevel level = ctx.level();
            RandomSource random = ctx.random();
            Config cfg = ctx.config();
            BlockPos origin = ctx.origin();
            int placed = 0;
            for (int i = 0; i < cfg.tries(); i++) {
                int x = origin.getX() + random.nextInt(cfg.radius() * 2 + 1) - cfg.radius();
                int z = origin.getZ() + random.nextInt(cfg.radius() * 2 + 1) - cfg.radius();
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                BlockPos p = new BlockPos(x, y, z);
                if (!water(level, p) || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                int d = depth(level, p);
                switch (cfg.kind()) {
                    case "seagrass" -> {
                        if (random.nextFloat() < 0.3F && d >= 3) {
                            BlockState lower = TALL_SEAGRASS.get().defaultBlockState();
                            level.setBlock(p, lower, 2);
                            level.setBlock(p.above(), lower.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
                        } else {
                            level.setBlock(p, CeruleaSea.SEAGRASS.get().defaultBlockState(), 2);
                        }
                    }
                    case "kelp", "pearl" -> {
                        boolean kelp = cfg.kind().equals("kelp");
                        int h = Math.min(kelp ? 2 + random.nextInt(7) : 2 + random.nextInt(5), d - 2);
                        if (h < 2) continue;
                        for (int k = 0; k < h; k++) {
                            BlockPos q = p.above(k);
                            BlockState s;
                            if (k == h - 1) {
                                s = (kelp ? RIBBON_KELP.get() : PEARL_WEED.get()).defaultBlockState()
                                        .setValue(GrowingPlantHeadBlock.AGE, 20 + random.nextInt(5));
                            } else {
                                s = (kelp ? RIBBON_KELP_PLANT.get() : PEARL_WEED_PLANT.get()).defaultBlockState();
                            }
                            if (!kelp) s = s.setValue(BERRIES, random.nextFloat() < 0.35F);
                            level.setBlock(q, s, 2);
                        }
                    }
                    case "tubes" -> level.setBlock(p, GLOW_TUBES.get().defaultBlockState(), 2);
                    case "fan" -> level.setBlock(p, AZURE_FAN.get().defaultBlockState(), 2);
                    default -> { continue; }
                }
                placed++;
            }
            return placed > 0;
        }
    }
}
