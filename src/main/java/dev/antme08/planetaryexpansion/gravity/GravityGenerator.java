package dev.antme08.planetaryexpansion.gravity;

import com.simibubi.create.api.stress.BlockStressValues;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Generador de gravedad (0.7.17, petición del usuario). Bloque cinético de Create: consume fuerza rotacional y,
 * mientras le llega suficiente, todo lo que esté dentro o encima de la misma nave (jugadores, mobs, objetos) tiene
 * la gravedad normal de la Tierra aunque esté en el espacio profundo. Ver {@link GravityFields} y
 * mixin/EntityGravityMixin.
 */
public final class GravityGenerator {
    private GravityGenerator() {}

    /** Unidades de estrés por RPM (una prensa mecánica son 8). A 64 RPM consume 1024 SU. */
    public static final double STRESS_IMPACT = 16.0;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PlanetaryExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlanetaryExpansion.MODID);

    public static final DeferredBlock<GravityGeneratorBlock> BLOCK = BLOCKS.register("gravity_generator",
            () -> new GravityGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW).strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));
    /** Parte de arriba del modelo de 1x2 (0.7.34): invisible y sin objeto. */
    public static final DeferredBlock<GravityGeneratorTopBlock> TOP = BLOCKS.register("gravity_generator_top",
            () -> new GravityGeneratorTopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW).strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityGeneratorBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("gravity_generator",
                    () -> BlockEntityType.Builder.of((pos, state) -> new GravityGeneratorBlockEntity(GravityGenerator.BLOCK_ENTITY.get(), pos, state),
                            BLOCK.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(BLOCK.get(), () -> STRESS_IMPACT)));
        modBus.addListener(EventPriority.LOWEST, GravityGenerator::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            event.accept(ITEM.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Gravedad] No se pudo añadir el generador a la pestaña {}", tab, e);
        }
    }
}
