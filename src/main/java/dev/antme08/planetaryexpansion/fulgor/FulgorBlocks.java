package dev.antme08.planetaryexpansion.fulgor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.ConstantInt;
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
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fulgor (1.0.4): luna de tormentas eléctricas de Aurelia. Basalto de tormenta (con ladrillos, pulido, escaleras y losas),
 * polvo de tormenta, fulgurita (vidrio que funden los rayos, da luz) y menas conductoras: redstone, cobre y zinc. Texturas
 * de tools/designs/fulgor_textures.py; el resto (recetas, botín, terreno, biomas) en generate.py (build_fulgor); los
 * rayos en weather/FulgorStorms.
 */
public final class FulgorBlocks {
    private FulgorBlocks() {}

    static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties p) {
        DeferredBlock<B> b = BLOCKS.register(name, () -> f.apply(p));
        TAB.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static BlockBehaviour.Properties copy(Block b, MapColor c) {
        return BlockBehaviour.Properties.ofFullCopy(b).mapColor(c);
    }

    public static final DeferredBlock<Block> BASALT = block("fulgor_basalt", Block::new, copy(Blocks.STONE, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> BASALT_BRICKS = block("fulgor_basalt_bricks", Block::new, copy(Blocks.STONE_BRICKS, MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> POLISHED_BASALT = block("polished_fulgor_basalt", Block::new, copy(Blocks.POLISHED_ANDESITE, MapColor.COLOR_GRAY));
    public static final DeferredBlock<ColoredFallingBlock> STORM_DUST = block("storm_dust",
            p -> new ColoredFallingBlock(new ColorRGBA(0xFF9690B0), p), copy(Blocks.SAND, MapColor.COLOR_PURPLE));
    /** Vidrio de rayo: duro, con luz propia (nivel 9). */
    public static final DeferredBlock<Block> FULGURITE = block("fulgurite", Block::new,
            copy(Blocks.AMETHYST_BLOCK, MapColor.COLOR_LIGHT_GRAY).sound(SoundType.GLASS).lightLevel(s -> 9).strength(1.5f, 6.0f));

    static {
        stairsAndSlab("fulgor_basalt", BASALT, Blocks.STONE);
        stairsAndSlab("fulgor_basalt_brick", BASALT_BRICKS, Blocks.STONE_BRICKS);
        stairsAndSlab("polished_fulgor_basalt", POLISHED_BASALT, Blocks.POLISHED_ANDESITE);
    }

    private static void stairsAndSlab(String prefix, DeferredBlock<? extends Block> base, Block props) {
        block(prefix + "_stairs", p -> new CeruleaBlocks.Stairs(base.get().defaultBlockState(), p), BlockBehaviour.Properties.ofFullCopy(props));
        block(prefix + "_slab", SlabBlock::new, BlockBehaviour.Properties.ofFullCopy(props));
    }

    public static final DeferredBlock<DropExperienceBlock> COPPER_ORE = block("fulgor_copper_ore",
            p -> new DropExperienceBlock(ConstantInt.of(0), p), copy(Blocks.COPPER_ORE, MapColor.COLOR_GRAY));
    public static final DeferredBlock<DropExperienceBlock> ZINC_ORE = block("fulgor_zinc_ore",
            p -> new DropExperienceBlock(ConstantInt.of(0), p), copy(Blocks.IRON_ORE, MapColor.COLOR_GRAY));
    public static final DeferredBlock<RedStoneOreBlock> REDSTONE_ORE = block("fulgor_redstone_ore", RedStoneOreBlock::new,
            copy(Blocks.REDSTONE_ORE, MapColor.COLOR_GRAY));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(FulgorBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("world_tab"))) return;
        try {
            for (DeferredItem<? extends Item> item : TAB)
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Fulgor] No se pudieron añadir los bloques a la pestaña {}", tab, e);
        }
    }
}
