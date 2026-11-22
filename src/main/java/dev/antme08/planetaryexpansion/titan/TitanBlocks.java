package dev.antme08.planetaryexpansion.titan;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Titán (1.0.4): luna de Saturno. Bloques (arena y corteza de tolina, hielo de roca, bitumen y menas de combustibles:
 * carbón, redstone, lapislázuli y azufre) y el metano líquido de sus lagos. Texturas de tools/designs/titan_textures.py;
 * el resto (recetas, botín, terreno, biomas) en generate.py (build_titan).
 */
public final class TitanBlocks {
    private TitanBlocks() {}

    static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);
    private static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties p) {
        DeferredBlock<B> b = BLOCKS.register(name, () -> f.apply(p));
        TAB.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static BlockBehaviour.Properties copy(Block b, MapColor c) {
        return BlockBehaviour.Properties.ofFullCopy(b).mapColor(c);
    }

    // ------------------------------------------------------------------ bloques

    public static final DeferredBlock<ColoredFallingBlock> THOLIN_SAND = block("tholin_sand",
            p -> new ColoredFallingBlock(new ColorRGBA(0xFFB8783A), p), copy(Blocks.RED_SAND, MapColor.COLOR_ORANGE));
    public static final DeferredBlock<Block> THOLIN_CRUST = block("tholin_crust", Block::new, copy(Blocks.COARSE_DIRT, MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<Block> ICE_ROCK = block("titan_ice_rock", Block::new, copy(Blocks.STONE, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<Block> ICE_ROCK_BRICKS = block("titan_ice_rock_bricks", Block::new, copy(Blocks.STONE_BRICKS, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<Block> POLISHED_ICE_ROCK = block("polished_titan_ice_rock", Block::new, copy(Blocks.POLISHED_ANDESITE, MapColor.COLOR_LIGHT_BLUE));
    /** Bitumen: bloque de combustible (ver el mapa de datos de combustibles en generate.py). */
    public static final DeferredBlock<Block> BITUMEN = block("bitumen", Block::new, copy(Blocks.COAL_BLOCK, MapColor.COLOR_BLACK).sound(SoundType.MUD));

    static {
        stairsAndSlab("titan_ice_rock", ICE_ROCK, Blocks.STONE);
        stairsAndSlab("titan_ice_rock_brick", ICE_ROCK_BRICKS, Blocks.STONE_BRICKS);
        stairsAndSlab("polished_titan_ice_rock", POLISHED_ICE_ROCK, Blocks.POLISHED_ANDESITE);
    }

    private static void stairsAndSlab(String prefix, DeferredBlock<? extends Block> base, Block props) {
        block(prefix + "_stairs", p -> new CeruleaBlocks.Stairs(base.get().defaultBlockState(), p), BlockBehaviour.Properties.ofFullCopy(props));
        block(prefix + "_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(props));
    }

    public static final DeferredBlock<DropExperienceBlock> COAL_ORE = ore("coal", 2);
    public static final DeferredBlock<DropExperienceBlock> LAPIS_ORE = ore("lapis", 3);
    public static final DeferredBlock<DropExperienceBlock> SULFUR_ORE = ore("sulfur", 2);
    public static final DeferredBlock<RedStoneOreBlock> REDSTONE_ORE = block("titan_redstone_ore", RedStoneOreBlock::new,
            copy(Blocks.REDSTONE_ORE, MapColor.COLOR_LIGHT_BLUE));

    private static DeferredBlock<DropExperienceBlock> ore(String name, int xp) {
        return block("titan_" + name + "_ore", p -> new DropExperienceBlock(xp > 0 ? UniformInt.of(0, xp) : ConstantInt.of(0), p),
                copy(Blocks.IRON_ORE, MapColor.COLOR_LIGHT_BLUE));
    }

    // ------------------------------------------------------------------ metano líquido

    public static final DeferredHolder<FluidType, FluidType> METHANE_TYPE = FLUID_TYPES.register("methane", () -> new FluidType(
            FluidType.Properties.create().descriptionId("block." + MODID + ".methane")
                    .canSwim(true).canDrown(true).canPushEntity(true).supportsBoating(true)
                    .density(430).viscosity(1200).temperature(111).fallDistanceModifier(0.6F)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> METHANE =
            FLUIDS.register("methane", () -> new BaseFlowingFluid.Source(TitanBlocks.methaneProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> METHANE_FLOWING =
            FLUIDS.register("flowing_methane", () -> new BaseFlowingFluid.Flowing(TitanBlocks.methaneProperties()));

    public static final DeferredBlock<LiquidBlock> METHANE_BLOCK = BLOCKS.register("methane", () -> new LiquidBlock(METHANE.get(),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_CYAN)));

    public static final DeferredItem<BucketItem> METHANE_BUCKET = ITEMS.register("methane_bucket",
            () -> new BucketItem(METHANE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static BaseFlowingFluid.Properties methaneProperties() {
        return new BaseFlowingFluid.Properties(METHANE_TYPE, METHANE, METHANE_FLOWING)
                .block(METHANE_BLOCK).bucket(METHANE_BUCKET).slopeFindDistance(3).levelDecreasePerBlock(2).tickRate(12);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        modBus.addListener(TitanBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab"))) return;
        try {
            for (DeferredItem<? extends Item> item : TAB)
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(METHANE_BUCKET.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Titán] No se pudieron añadir los bloques a la pestaña {}", tab, e);
        }
    }
}
