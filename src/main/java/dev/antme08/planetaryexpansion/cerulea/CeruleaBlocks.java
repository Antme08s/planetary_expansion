package dev.antme08.planetaryexpansion.cerulea;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Bloques y objetos de Cerúlea, el planeta de la estrella Ámbar (1.0.3). Texturas de tools/designs/cerulea_assets.py;
 * modelos, botín, recetas y etiquetas en generate.py (build_cerulea).
 */
public final class CeruleaBlocks {
    private CeruleaBlocks() {}

    static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    /** Todo lo que va a la pestaña del creativo, en orden. */
    private static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties p) {
        DeferredBlock<B> b = BLOCKS.register(name, () -> f.apply(p));
        TAB.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static DeferredBlock<Block> simple(String name, BlockBehaviour.Properties p) {
        return block(name, Block::new, p);
    }

    private static BlockBehaviour.Properties copy(Block b, MapColor c) {
        return BlockBehaviour.Properties.ofFullCopy(b).mapColor(c);
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> tree(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(MODID, "cerulea/" + name));
    }

    // ------------------------------------------------------------------ suelo y piedra
    public static final DeferredBlock<Block> GRASS_BLOCK = simple("cerulean_grass_block", copy(Blocks.GRASS_BLOCK, MapColor.COLOR_CYAN));
    public static final DeferredBlock<Block> MEADOW_GRASS_BLOCK = simple("cerulean_meadow_grass_block", copy(Blocks.GRASS_BLOCK, MapColor.COLOR_LIGHT_GREEN));
    public static final DeferredBlock<Block> SOIL = simple("cerulean_soil", copy(Blocks.DIRT, MapColor.COLOR_PURPLE));
    public static final DeferredBlock<Block> MUD = simple("cerulean_mud", copy(Blocks.MUD, MapColor.WARPED_STEM));
    public static final DeferredBlock<ColoredFallingBlock> SAND = block("cerulean_sand",
            p -> new ColoredFallingBlock(new ColorRGBA(0xC6CCC6FF), p), copy(Blocks.SAND, MapColor.QUARTZ));
    public static final DeferredBlock<Block> STONE = simple("cerulean_stone", copy(Blocks.STONE, MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> COBBLESTONE = simple("cerulean_cobblestone", copy(Blocks.COBBLESTONE, MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> STONE_BRICKS = simple("cerulean_stone_bricks", copy(Blocks.STONE_BRICKS, MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> POLISHED_STONE = simple("polished_cerulean_stone", copy(Blocks.POLISHED_ANDESITE, MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> DEEP_STONE = simple("deep_cerulean_stone", copy(Blocks.DEEPSLATE, MapColor.COLOR_BLUE));

    // ------------------------------------------------------------------ minerales
    public static final Map<String, DeferredBlock<DropExperienceBlock>> ORES = new LinkedHashMap<>();
    private static final String[] ORE_NAMES = {"coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald", "zinc", "crystal"};

    private static IntProvider xp(String ore) {
        return switch (ore) {
            case "coal" -> UniformInt.of(0, 2);
            case "lapis" -> UniformInt.of(2, 5);
            case "redstone" -> UniformInt.of(1, 5);
            case "diamond", "emerald", "crystal" -> UniformInt.of(3, 7);
            default -> ConstantInt.of(0);
        };
    }

    static {
        for (String ore : ORE_NAMES) {
            ORES.put("cerulean_" + ore + "_ore", block("cerulean_" + ore + "_ore", p -> new DropExperienceBlock(xp(ore), p),
                    copy(Blocks.IRON_ORE, MapColor.COLOR_LIGHT_GRAY).lightLevel(s -> ore.equals("crystal") ? 4 : 0)));
        }
        for (String ore : ORE_NAMES) {
            ORES.put("deep_cerulean_" + ore + "_ore", block("deep_cerulean_" + ore + "_ore", p -> new DropExperienceBlock(xp(ore), p),
                    copy(Blocks.DEEPSLATE_IRON_ORE, MapColor.COLOR_BLUE).lightLevel(s -> ore.equals("crystal") ? 4 : 0)));
        }
    }

    // ------------------------------------------------------------------ árboles
    public static final DeferredBlock<RotatedPillarBlock> SILVERWOOD_LOG = block("silverwood_log", RotatedPillarBlock::new, copy(Blocks.BIRCH_LOG, MapColor.QUARTZ));
    public static final DeferredBlock<Block> SILVERWOOD_PLANKS = simple("silverwood_planks", copy(Blocks.BIRCH_PLANKS, MapColor.QUARTZ));
    public static final DeferredBlock<LeavesBlock> SILVERWOOD_LEAVES = block("silverwood_leaves", LeavesBlock::new, copy(Blocks.OAK_LEAVES, MapColor.COLOR_BLUE));
    public static final DeferredBlock<RotatedPillarBlock> DUSKWOOD_LOG = block("duskwood_log", RotatedPillarBlock::new, copy(Blocks.DARK_OAK_LOG, MapColor.COLOR_PURPLE));
    public static final DeferredBlock<Block> DUSKWOOD_PLANKS = simple("duskwood_planks", copy(Blocks.DARK_OAK_PLANKS, MapColor.COLOR_PURPLE));
    public static final DeferredBlock<CeruleaPlants.FruitLeaves> DUSKWOOD_LEAVES = block("duskwood_leaves", CeruleaPlants.FruitLeaves::new, copy(Blocks.OAK_LEAVES, MapColor.COLOR_CYAN));
    public static final DeferredBlock<CeruleaPlants.FruitLeaves> BLUE_DUSKWOOD_LEAVES = block("blue_duskwood_leaves", CeruleaPlants.FruitLeaves::new, copy(Blocks.OAK_LEAVES, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<RotatedPillarBlock> MIREWOOD_LOG = block("mirewood_log", RotatedPillarBlock::new, copy(Blocks.MANGROVE_LOG, MapColor.WARPED_STEM));
    public static final DeferredBlock<Block> MIREWOOD_PLANKS = simple("mirewood_planks", copy(Blocks.MANGROVE_PLANKS, MapColor.WARPED_STEM));
    public static final DeferredBlock<LeavesBlock> MIREWOOD_LEAVES = block("mirewood_leaves", LeavesBlock::new, copy(Blocks.OAK_LEAVES, MapColor.WARPED_NYLIUM));
    public static final DeferredBlock<LeavesBlock> BUSH = block("cerulean_bush", LeavesBlock::new, copy(Blocks.OAK_LEAVES, MapColor.COLOR_CYAN));

    private static BlockBehaviour.Properties sapling() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING);
    }

    public static final DeferredBlock<CeruleaPlants.Sapling> SILVERWOOD_SAPLING = block("silverwood_sapling",
            p -> new CeruleaPlants.Sapling(new TreeGrower("planetary_expansion_silverwood", Optional.empty(), Optional.of(tree("silverwood_tree")), Optional.empty()), p), sapling());
    public static final DeferredBlock<CeruleaPlants.Sapling> DUSKWOOD_SAPLING = block("duskwood_sapling",
            p -> new CeruleaPlants.Sapling(new TreeGrower("planetary_expansion_duskwood", Optional.empty(), Optional.of(tree("duskwood_tree")), Optional.empty()), p), sapling());
    public static final DeferredBlock<CeruleaPlants.Sapling> MIREWOOD_SAPLING = block("mirewood_sapling",
            p -> new CeruleaPlants.Sapling(new TreeGrower("planetary_expansion_mirewood", Optional.empty(), Optional.of(tree("mirewood_tree")), Optional.empty()), p), sapling());

    // ------------------------------------------------------------------ escaleras, losas, puertas y trampillas (1.0.3)
    /** El constructor de StairBlock es protegido. */
    public static class Stairs extends StairBlock {
        public Stairs(net.minecraft.world.level.block.state.BlockState base, BlockBehaviour.Properties p) { super(base, p); }
    }

    private static void stairsAndSlab(String prefix, DeferredBlock<? extends Block> base, Block props) {
        block(prefix + "_stairs", p -> new Stairs(base.get().defaultBlockState(), p), BlockBehaviour.Properties.ofFullCopy(props));
        block(prefix + "_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(props));
    }

    private static void doors(String wood, BlockSetType type, Block props) {
        DeferredBlock<DoorBlock> door = BLOCKS.register(wood + "_door",
                () -> new DoorBlock(type, BlockBehaviour.Properties.ofFullCopy(props).noOcclusion()));
        TAB.add(ITEMS.register(wood + "_door", () -> new DoubleHighBlockItem(door.get(), new Item.Properties())));
        block(wood + "_trapdoor", p -> new TrapDoorBlock(type, p), BlockBehaviour.Properties.ofFullCopy(props).noOcclusion());
    }

    static {
        stairsAndSlab("cerulean_stone", STONE, Blocks.STONE);
        stairsAndSlab("cerulean_cobblestone", COBBLESTONE, Blocks.COBBLESTONE);
        stairsAndSlab("cerulean_stone_brick", STONE_BRICKS, Blocks.STONE_BRICKS);
        stairsAndSlab("polished_cerulean_stone", POLISHED_STONE, Blocks.POLISHED_ANDESITE);
        stairsAndSlab("deep_cerulean_stone", DEEP_STONE, Blocks.DEEPSLATE);
        stairsAndSlab("silverwood", SILVERWOOD_PLANKS, Blocks.BIRCH_PLANKS);
        stairsAndSlab("duskwood", DUSKWOOD_PLANKS, Blocks.DARK_OAK_PLANKS);
        stairsAndSlab("mirewood", MIREWOOD_PLANKS, Blocks.MANGROVE_PLANKS);
        doors("silverwood", BlockSetType.BIRCH, Blocks.BIRCH_DOOR);
        doors("duskwood", BlockSetType.DARK_OAK, Blocks.DARK_OAK_DOOR);
        doors("mirewood", BlockSetType.MANGROVE, Blocks.MANGROVE_DOOR);
    }

    // ------------------------------------------------------------------ plantas
    private static BlockBehaviour.Properties plant(MapColor c) {
        return BlockBehaviour.Properties.of().mapColor(c).replaceable().noCollission().instabreak().sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XYZ).ignitedByLava().pushReaction(PushReaction.DESTROY);
    }

    public static final DeferredBlock<CeruleaPlants.Plant> SHORT_GRASS = block("cerulean_grass", CeruleaPlants.Plant::new, plant(MapColor.COLOR_CYAN));
    public static final DeferredBlock<DoublePlantBlock> TALL_GRASS = block("tall_cerulean_grass", DoublePlantBlock::new, plant(MapColor.COLOR_CYAN));
    public static final DeferredBlock<CeruleaPlants.Plant> AZURE_FLOWER = block("azure_flower", CeruleaPlants.Plant::new,
            plant(MapColor.COLOR_BLUE).offsetType(BlockBehaviour.OffsetType.XZ));
    public static final DeferredBlock<CeruleaPlants.Plant> GLOWBLOOM = block("glowbloom", CeruleaPlants.Plant::new,
            plant(MapColor.COLOR_LIGHT_BLUE).offsetType(BlockBehaviour.OffsetType.XZ).lightLevel(s -> 9));
    public static final DeferredBlock<CeruleaPlants.Plant> GLOWCAP = block("glowcap", CeruleaPlants.Plant::new,
            plant(MapColor.COLOR_CYAN).offsetType(BlockBehaviour.OffsetType.XZ).sound(SoundType.FUNGUS).lightLevel(s -> 11));
    public static final DeferredBlock<CeruleaPlants.HangingVine> MIRE_VINE = block("mire_vine", CeruleaPlants.HangingVine::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.WEEPING_VINES_PLANT).mapColor(MapColor.WARPED_NYLIUM).sound(SoundType.VINE));
    public static final DeferredBlock<CeruleaPlants.Lily> LILY = BLOCKS.register("azure_lily_pad",
            () -> new CeruleaPlants.Lily(BlockBehaviour.Properties.ofFullCopy(Blocks.LILY_PAD).mapColor(MapColor.COLOR_BLUE)));
    public static final DeferredItem<PlaceOnWaterBlockItem> LILY_ITEM = ITEMS.register("azure_lily_pad",
            () -> new PlaceOnWaterBlockItem(LILY.get(), new Item.Properties()));

    // ------------------------------------------------------------------ comestibles
    private static FoodProperties food(int n, float sat) {
        return new FoodProperties.Builder().nutrition(n).saturationModifier(sat).build();
    }

    public static final DeferredBlock<CeruleaPlants.BlueberryBush> BLUEBERRY_BUSH = BLOCKS.register("blueberry_bush",
            () -> new CeruleaPlants.BlueberryBush(BlockBehaviour.Properties.ofFullCopy(Blocks.SWEET_BERRY_BUSH).mapColor(MapColor.COLOR_CYAN)));
    public static final DeferredItem<ItemNameBlockItem> BLUEBERRIES = ITEMS.register("blueberries",
            () -> new ItemNameBlockItem(BLUEBERRY_BUSH.get(), new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).fast().build())));
    public static final DeferredBlock<CeruleaPlants.HangingFruit> DUSKFRUIT = BLOCKS.register("duskfruit",
            () -> new CeruleaPlants.HangingFruit(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollission().instabreak()
                    .sound(SoundType.CAVE_VINES).pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<Item> DUSKFRUIT_ITEM = ITEMS.register("duskfruit", () -> new Item(new Item.Properties().food(food(5, 0.6F))));
    public static final DeferredBlock<CeruleaPlants.Roots> ROOTS = BLOCKS.register("cerulean_roots",
            () -> new CeruleaPlants.Roots(BlockBehaviour.Properties.ofFullCopy(Blocks.POTATOES).mapColor(MapColor.COLOR_CYAN)));
    public static final DeferredItem<ItemNameBlockItem> CERULEAN_ROOT = ITEMS.register("cerulean_root",
            () -> new ItemNameBlockItem(ROOTS.get(), new Item.Properties().food(food(2, 0.3F))));
    public static final DeferredItem<Item> BAKED_ROOT = ITEMS.register("baked_cerulean_root", () -> new Item(new Item.Properties().food(food(7, 0.7F))));
    public static final DeferredBlock<CeruleaPlants.TallPlant> REED = BLOCKS.register("mire_reed",
            () -> new CeruleaPlants.TallPlant(plant(MapColor.WARPED_NYLIUM).offsetType(BlockBehaviour.OffsetType.XZ), true));
    public static final DeferredItem<ItemNameBlockItem> REED_POD = ITEMS.register("reed_pod",
            () -> new ItemNameBlockItem(REED.get(), new Item.Properties().food(food(3, 0.4F))));
    public static final DeferredItem<Item> GLOWCAP_STEW = ITEMS.register("glowcap_stew", () -> new Item(new Item.Properties().stacksTo(1)
            .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).usingConvertsTo(Items.BOWL)
                    .effect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200), 1.0F).build())));
    public static final DeferredItem<Item> CRYSTAL = ITEMS.registerSimpleItem("cerulean_crystal", new Item.Properties());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CeruleaTreeFeature.FEATURES.register(modBus);
        modBus.addListener(CeruleaBlocks::creativeTab);
        NeoForge.EVENT_BUS.addListener(CeruleaBlocks::till);
    }

    /** La azada convierte el pasto y la tierra de Cerúlea en tierra arada. */
    private static void till(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.HOE_TILL) return;
        var s = event.getState();
        if ((s.is(GRASS_BLOCK.get()) || s.is(MEADOW_GRASS_BLOCK.get()) || s.is(SOIL.get()))
                && event.getContext().getLevel().getBlockState(event.getPos().above()).isAir())
            event.setFinalState(Blocks.FARMLAND.defaultBlockState());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab"))) return;
        try {
            for (DeferredItem<? extends Item> item : TAB)
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            for (DeferredItem<? extends Item> item : List.of(LILY_ITEM, BLUEBERRIES, DUSKFRUIT_ITEM, CERULEAN_ROOT, BAKED_ROOT, REED_POD,
                    GLOWCAP_STEW, CRYSTAL))
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Cerúlea] No se pudieron añadir los bloques a la pestaña {}", tab, e);
        }
    }
}
