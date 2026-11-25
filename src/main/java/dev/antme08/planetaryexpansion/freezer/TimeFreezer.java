package dev.antme08.planetaryexpansion.freezer;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Congelador del tiempo espacial (1.0.4, idea del usuario): registros. La lógica está en {@link SpaceTimeFreeze}. */
public final class TimeFreezer {
    private TimeFreezer() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PlanetaryExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlanetaryExpansion.MODID);

    public static final DeferredBlock<TimeFreezerBlock> BLOCK = BLOCKS.register("time_freezer",
            () -> new TimeFreezerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(TimeFreezerBlock.ACTIVE) ? 7 : 0)));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TimeFreezerBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("time_freezer", () ->
                    BlockEntityType.Builder.of(TimeFreezerBlockEntity::new, BLOCK.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TimeFreezer::addToCreativeTab);
        NeoForge.EVENT_BUS.register(SpaceTimeFreeze.class);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            event.accept(ITEM.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Congelador] No se pudo añadir el bloque a la pestaña {}", tab, e);
        }
    }
}
