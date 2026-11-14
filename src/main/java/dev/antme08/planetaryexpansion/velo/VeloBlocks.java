package dev.antme08.planetaryexpansion.velo;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
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
 * Velo, la luna de cristal de Cerúlea (1.0.3): casi vacía, solo polvo blanco y cristal cerúleo (decisión del usuario).
 * Texturas de tools/designs/velo_textures.py; modelos, botín, recetas, etiquetas y terreno en generate.py (build_velo).
 */
public final class VeloBlocks {
    private VeloBlocks() {}

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

    // ------------------------------------------------------------------ suelo y roca
    public static final DeferredBlock<ColoredFallingBlock> DUST = block("velo_dust",
            p -> new ColoredFallingBlock(new ColorRGBA(0xEEEEF6FF), p), copy(Blocks.SAND, MapColor.SNOW).sound(SoundType.SAND));
    public static final DeferredBlock<Block> ROCK = block("velo_rock", Block::new, copy(Blocks.STONE, MapColor.QUARTZ));
    public static final DeferredBlock<Block> ROCK_BRICKS = block("velo_rock_bricks", Block::new, copy(Blocks.STONE_BRICKS, MapColor.QUARTZ));
    public static final DeferredBlock<Block> POLISHED_ROCK = block("polished_velo_rock", Block::new, copy(Blocks.POLISHED_DIORITE, MapColor.QUARTZ));

    static {
        stairsAndSlab("velo_rock", ROCK, Blocks.STONE);
        stairsAndSlab("velo_rock_brick", ROCK_BRICKS, Blocks.STONE_BRICKS);
        stairsAndSlab("polished_velo_rock", POLISHED_ROCK, Blocks.POLISHED_DIORITE);
    }

    private static void stairsAndSlab(String prefix, DeferredBlock<? extends Block> base, Block props) {
        block(prefix + "_stairs", p -> new CeruleaBlocks.Stairs(base.get().defaultBlockState(), p), BlockBehaviour.Properties.ofFullCopy(props));
        block(prefix + "_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(props));
    }

    // ------------------------------------------------------------------ cristal (como la amatista)
    public static final DeferredBlock<AmethystBlock> CRYSTAL_BLOCK = block("cerulean_crystal_block", AmethystBlock::new,
            copy(Blocks.AMETHYST_BLOCK, MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 3));
    public static final DeferredBlock<BuddingCrystalBlock> BUDDING = block("budding_cerulean_crystal", BuddingCrystalBlock::new,
            copy(Blocks.BUDDING_AMETHYST, MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 3));
    public static final DeferredBlock<AmethystClusterBlock> SMALL_BUD = block("small_cerulean_bud",
            p -> new AmethystClusterBlock(3.0F, 4.0F, p), copy(Blocks.SMALL_AMETHYST_BUD, MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 2));
    public static final DeferredBlock<AmethystClusterBlock> MEDIUM_BUD = block("medium_cerulean_bud",
            p -> new AmethystClusterBlock(4.0F, 3.0F, p), copy(Blocks.MEDIUM_AMETHYST_BUD, MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 4));
    public static final DeferredBlock<AmethystClusterBlock> LARGE_BUD = block("large_cerulean_bud",
            p -> new AmethystClusterBlock(5.0F, 3.0F, p), copy(Blocks.LARGE_AMETHYST_BUD, MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 6));
    public static final DeferredBlock<AmethystClusterBlock> CLUSTER = block("cerulean_crystal_cluster",
            p -> new AmethystClusterBlock(7.0F, 3.0F, p), copy(Blocks.AMETHYST_CLUSTER, MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 8));

    /** Lámpara de cristal: marco de roca de Velo pulida y cristal; se enciende con redstone como la normal. */
    public static final DeferredBlock<RedstoneLampBlock> LAMP = block("cerulean_crystal_lamp", RedstoneLampBlock::new,
            copy(Blocks.REDSTONE_LAMP, MapColor.COLOR_LIGHT_BLUE).sound(SoundType.AMETHYST)
                    .lightLevel(s -> s.getValue(RedstoneLampBlock.LIT) ? 15 : 0));

    // ------------------------------------------------------------------ minerales (bajo tierra)
    public static final DeferredBlock<DropExperienceBlock> IRON_ORE = ore("iron", 0, 0);
    public static final DeferredBlock<DropExperienceBlock> GOLD_ORE = ore("gold", 0, 0);
    public static final DeferredBlock<DropExperienceBlock> DIAMOND_ORE = ore("diamond", 3, 7);
    public static final DeferredBlock<DropExperienceBlock> CRYSTAL_ORE = ore("crystal", 3, 7);
    public static final DeferredBlock<DropExperienceBlock> TITANIUM_ORE = ore("titanium", 0, 0);

    private static DeferredBlock<DropExperienceBlock> ore(String name, int lo, int hi) {
        return block("velo_" + name + "_ore", p -> new DropExperienceBlock(hi > 0 ? UniformInt.of(lo, hi) : ConstantInt.of(0), p),
                copy(Blocks.IRON_ORE, MapColor.QUARTZ).lightLevel(s -> name.equals("crystal") ? 4 : 0));
    }

    // ------------------------------------------------------------------ terreno
    public static final DeferredHolder<Feature<?>, CrystalSpireFeature> SPIRE =
            FEATURES.register("velo_crystal_spire", () -> new CrystalSpireFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(VeloBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab"))) return;
        try {
            for (DeferredItem<? extends Item> item : TAB)
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Velo] No se pudieron añadir los bloques a la pestaña {}", tab, e);
        }
    }
}
