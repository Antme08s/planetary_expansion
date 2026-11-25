package dev.antme08.planetaryexpansion.transporter;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Transportador espacial (1.0.4, idea del usuario): sube la nave desde la superficie de un planeta a su órbita sin usar
 * los motores. Se elige la altura en el mapa de la consola de navegación. Se carga con potencia rotacional de Create y gasta
 * nitrógeno líquido, como el motor de distorsión. Ver {@link SpaceTransporterBlockEntity} y {@link SpaceLaunch}.
 */
public final class SpaceTransporter {
    private SpaceTransporter() {}

    /** Estrés por RPM. A 256 RPM son 8192 SU. */
    public static final double STRESS_IMPACT = 32.0;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PlanetaryExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlanetaryExpansion.MODID);

    public static final DeferredBlock<SpaceTransporterBlock> BLOCK = BLOCKS.register("space_transporter",
            () -> new SpaceTransporterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(s -> 5)));
    public static final DeferredItem<BlockItem> ITEM =
            ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties().rarity(Rarity.RARE));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpaceTransporterBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("space_transporter", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new SpaceTransporterBlockEntity(SpaceTransporter.BLOCK_ENTITY.get(), pos, state), BLOCK.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(BLOCK.get(), () -> STRESS_IMPACT)));
        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BLOCK_ENTITY.get(), (be, side) -> be.tank()));
        modBus.addListener(EventPriority.LOWEST, SpaceTransporter::addToCreativeTab);
        NeoForge.EVENT_BUS.register(SpaceLaunch.class);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            event.accept(ITEM.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Transportador] No se pudo añadir a la pestaña {}", tab, e);
        }
    }
}
