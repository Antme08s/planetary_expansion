package dev.antme08.planetaryexpansion.ferra;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Ferra, la luna de óxido de Cerúlea (1.0.3): hostil, con tormentas de polvo como Marte y muchísimo hierro, titanio,
 * cobre, redstone y oro (ideas del usuario). Texturas de tools/designs/ferra_textures.py; el resto en generate.py
 * (build_ferra).
 */
public final class FerraBlocks {
    private FerraBlocks() {}

    static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);
    private static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties p) {
        DeferredBlock<B> b = BLOCKS.register(name, () -> f.apply(p));
        TAB.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static BlockBehaviour.Properties copy(Block b, MapColor c) {
        return BlockBehaviour.Properties.ofFullCopy(b).mapColor(c);
    }

    public static final DeferredBlock<ColoredFallingBlock> DUST = block("ferra_dust",
            p -> new ColoredFallingBlock(new ColorRGBA(0xB45A30FF), p), copy(Blocks.RED_SAND, MapColor.COLOR_ORANGE));
    public static final DeferredBlock<Block> ROCK = block("ferra_rock", Block::new, copy(Blocks.STONE, MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<Block> ROCK_BRICKS = block("ferra_rock_bricks", Block::new, copy(Blocks.STONE_BRICKS, MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<Block> POLISHED_ROCK = block("polished_ferra_rock", Block::new, copy(Blocks.POLISHED_GRANITE, MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<Block> OCHRE = block("ferra_ochre", Block::new, copy(Blocks.TERRACOTTA, MapColor.TERRACOTTA_ORANGE));
    public static final DeferredBlock<Block> VERDIGRIS = block("ferra_verdigris", Block::new,
            copy(Blocks.STONE, MapColor.WARPED_NYLIUM).sound(SoundType.COPPER));
    /** Veta de hierro: suelta 3-6 hierros crudos. */
    public static final DeferredBlock<DropExperienceBlock> IRON_VEIN = block("ferra_iron_vein",
            p -> new DropExperienceBlock(UniformInt.of(1, 3), p), copy(Blocks.RAW_IRON_BLOCK, MapColor.RAW_IRON).sound(SoundType.METAL));

    static {
        stairsAndSlab("ferra_rock", ROCK, Blocks.STONE);
        stairsAndSlab("ferra_rock_brick", ROCK_BRICKS, Blocks.STONE_BRICKS);
        stairsAndSlab("polished_ferra_rock", POLISHED_ROCK, Blocks.POLISHED_GRANITE);
    }

    private static void stairsAndSlab(String prefix, DeferredBlock<? extends Block> base, Block props) {
        block(prefix + "_stairs", p -> new CeruleaBlocks.Stairs(base.get().defaultBlockState(), p), BlockBehaviour.Properties.ofFullCopy(props));
        block(prefix + "_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(props));
    }

    public static final DeferredBlock<DropExperienceBlock> IRON_ORE = ore("iron", 0);
    public static final DeferredBlock<DropExperienceBlock> COPPER_ORE = ore("copper", 0);
    public static final DeferredBlock<DropExperienceBlock> GOLD_ORE = ore("gold", 0);
    public static final DeferredBlock<DropExperienceBlock> TITANIUM_ORE = ore("titanium", 0);
    public static final DeferredBlock<RedStoneOreBlock> REDSTONE_ORE = block("ferra_redstone_ore", RedStoneOreBlock::new,
            copy(Blocks.REDSTONE_ORE, MapColor.TERRACOTTA_BROWN));

    private static DeferredBlock<DropExperienceBlock> ore(String name, int xp) {
        return block("ferra_" + name + "_ore", p -> new DropExperienceBlock(xp > 0 ? UniformInt.of(0, xp) : ConstantInt.of(0), p),
                copy(Blocks.IRON_ORE, MapColor.TERRACOTTA_BROWN));
    }

    public static final DeferredHolder<Feature<?>, RustPillarFeature> PILLAR =
            FEATURES.register("ferra_rust_pillar", () -> new RustPillarFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(FerraBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab"))) return;
        try {
            for (DeferredItem<? extends Item> item : TAB)
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Ferra] No se pudieron añadir los bloques a la pestaña {}", tab, e);
        }
    }
}
