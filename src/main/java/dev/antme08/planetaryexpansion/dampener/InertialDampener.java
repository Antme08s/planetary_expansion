package dev.antme08.planetaryexpansion.dampener;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Amortiguador inercial (0.7.29, idea del usuario). Multibloque de 3x3x3: núcleo en el centro y 26 bloques de
 * carcasa alrededor (carcasa, entrada de rotación o puerto de refrigerante).
 *
 * <ul>
 *   <li>Se carga con rotación de Create por las entradas de rotación.</li>
 *   <li>Una señal de redstone en una entrada de redstone lo dispara si está cargado y frío (antes de 0.7.32 valía
 *       cualquier bloque). La entrada de redstone también da la carga a un comparador.</li>
 *   <li>En un planeta detiene la nave en seco; en el espacio profundo deja la órbita circular a la altura actual.
 *       En los dos casos la nave deja de girar.</li>
 *   <li>Después se calienta y tiene que enfriarse; con agua o nitrógeno líquido (Create Nuclear) se enfría antes,
 *       como los reactores de Create Nuclear.</li>
 * </ul>
 * Ver {@link DampenerCoreBlockEntity}.
 */
public final class InertialDampener {
    private InertialDampener() {}

    /** Unidades de estrés por RPM de cada entrada de rotación. A 64 RPM son 1536 SU. */
    public static final double STRESS_IMPACT = 24.0;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PlanetaryExpansion.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PlanetaryExpansion.MODID);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5.0f, 1200.0f)
                .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<DampenerCoreBlock> CORE = BLOCKS.register("inertial_dampener_core",
            () -> new DampenerCoreBlock(props().lightLevel(s -> 10)));
    public static final DeferredBlock<DampenerCasingBlock> CASING = BLOCKS.register("inertial_dampener_casing",
            () -> new DampenerCasingBlock(props()));
    public static final DeferredBlock<DampenerDriveBlock> DRIVE = BLOCKS.register("inertial_dampener_drive",
            () -> new DampenerDriveBlock(props()));
    public static final DeferredBlock<DampenerCoolantPortBlock> COOLANT_PORT = BLOCKS.register("inertial_dampener_coolant_port",
            () -> new DampenerCoolantPortBlock(props()));

    public static final DeferredBlock<DampenerRedstonePortBlock> REDSTONE_PORT = BLOCKS.register("inertial_dampener_redstone_port",
            () -> new DampenerRedstonePortBlock(props()));

    public static final DeferredItem<BlockItem> CORE_ITEM =
            ITEMS.registerSimpleBlockItem(CORE, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> CASING_ITEM = ITEMS.registerSimpleBlockItem(CASING, new Item.Properties());
    public static final DeferredItem<BlockItem> DRIVE_ITEM = ITEMS.registerSimpleBlockItem(DRIVE, new Item.Properties());
    public static final DeferredItem<BlockItem> COOLANT_PORT_ITEM = ITEMS.registerSimpleBlockItem(COOLANT_PORT, new Item.Properties());
    public static final DeferredItem<BlockItem> REDSTONE_PORT_ITEM = ITEMS.registerSimpleBlockItem(REDSTONE_PORT, new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DampenerCoreBlockEntity>> CORE_BE =
            BLOCK_ENTITIES.register("inertial_dampener_core", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new DampenerCoreBlockEntity(InertialDampener.CORE_BE.get(), pos, state), CORE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DampenerDriveBlockEntity>> DRIVE_BE =
            BLOCK_ENTITIES.register("inertial_dampener_drive", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new DampenerDriveBlockEntity(InertialDampener.DRIVE_BE.get(), pos, state), DRIVE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DampenerCoolantPortBlockEntity>> COOLANT_PORT_BE =
            BLOCK_ENTITIES.register("inertial_dampener_coolant_port", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new DampenerCoolantPortBlockEntity(InertialDampener.COOLANT_PORT_BE.get(), pos, state), COOLANT_PORT.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(DRIVE.get(), () -> STRESS_IMPACT)));
        modBus.addListener(InertialDampener::registerCapabilities);
        modBus.addListener(EventPriority.LOWEST, InertialDampener::addToCreativeTab);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COOLANT_PORT_BE.get(), (be, side) -> be.fluidHandler());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        for (Supplier<? extends Item> item : java.util.List.<Supplier<? extends Item>>of(CORE_ITEM, CASING_ITEM, DRIVE_ITEM, COOLANT_PORT_ITEM, REDSTONE_PORT_ITEM)) {
            try {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } catch (RuntimeException e) {
                PlanetaryExpansion.LOGGER.error("[Amortiguador] No se pudo añadir {} a la pestaña {}", item, tab, e);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades de la estructura

    /** ¿El bloque puede formar parte de la carcasa (los 26 bloques alrededor del núcleo)? */
    public static boolean isShell(BlockState state) {
        Block b = state.getBlock();
        return b instanceof DampenerCasingBlock || b instanceof DampenerDriveBlock || b instanceof DampenerCoolantPortBlock
                || b instanceof DampenerRedstonePortBlock;
    }

    /** Núcleo de la estructura a la que pertenece este bloque de carcasa (el núcleo está a 1 bloque en cada eje). */
    public static DampenerCoreBlockEntity findCore(BlockGetter level, BlockPos shell) {
        if (level == null) return null;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockEntity be = level.getBlockEntity(shell.offset(dx, dy, dz));
                    if (be instanceof DampenerCoreBlockEntity core) return core;
                }
        return null;
    }
}
