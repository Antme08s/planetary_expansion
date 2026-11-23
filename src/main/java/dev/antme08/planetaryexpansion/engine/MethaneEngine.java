package dev.antme08.planetaryexpansion.engine;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Motor modular de metano (1.0.4): bloque, entidad y capacidad de fluidos del soporte de metano. La receta, los modelos y
 * los textos salen de generate.py (build_methane_engine).
 */
public final class MethaneEngine {
    private MethaneEngine() {}

    private static final String MODID = PlanetaryExpansion.MODID;
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<MethaneThrusterMountBlock> MOUNT = BLOCKS.register("methane_thruster_mount",
            () -> new MethaneThrusterMountBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredItem<BlockItem> MOUNT_ITEM = ITEMS.registerSimpleBlockItem(MOUNT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MethaneThrusterMountBlockEntity>> MOUNT_BE =
            BLOCK_ENTITIES.register("methane_thruster_mount", () ->
                    BlockEntityType.Builder.of(MethaneThrusterMountBlockEntity::new, MOUNT.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(MethaneEngine::capabilities);
        modBus.addListener(MethaneEngine::creativeTab);
    }

    /** Igual que el soporte de Cosmonautics: sin lado, los dos tanques; por los lados de entrada, un tanque cada uno. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MOUNT_BE.get(), (be, side) -> {
            if (side == null) {
                return be.combinedFluidHandler;
            }
            Direction facing = be.getBlockState().getValue(ThrusterMountBlock.FACING);
            if (side == be.getInput1(facing)) {
                return be.tank1;
            }
            if (side == be.getInput2(facing)) {
                return be.tank2;
            }
            return null;
        });
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!(tab.getNamespace().equals("rocketnautics") && tab.getPath().equals("resource_tab"))) {
            return;
        }
        try {
            event.accept(MOUNT_ITEM.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (Exception e) {
            PlanetaryExpansion.LOGGER.error("[Motor de metano] No se pudo añadir el soporte a la pestaña {}", tab, e);
        }
    }
}
