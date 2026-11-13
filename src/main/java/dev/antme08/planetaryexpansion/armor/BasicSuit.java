package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.api.capability.JetpackFluidHandlerItemStack;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Traje espacial básico (0.7.64, idea de un amigo del usuario): el traje de inicio. Hace lo mismo que el equipo de
 * Cosmonautics (respirar con aire en la espalda, jetpack, propulsores, botas ancla) pero no protege de nada especial.
 * Se fabrica en la Tierra; con una plantilla de cada planeta y un lingote de netherita se convierte, en la mesa de
 * herrería, en el traje de ese planeta (Impact, Glacial, Hazard, Sea Diver o térmico).
 */
public final class BasicSuit {
    private BasicSuit() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final SuitPieces.Info INFO = new SuitPieces.Info("basic_armor", "basic", false);
    /** Entre el hierro (15) y el diamante (33). */
    private static final int DURABILITY = 22;

    public static final DeferredItem<SuitPieces.Helmet> HELMET = ITEMS.register("basic_helmet",
            () -> new SuitPieces.Helmet(INFO, ArmorMaterials.IRON, piece(ArmorItem.Type.HELMET)));
    public static final DeferredItem<SuitPieces.Jetpack> CHESTPLATE = ITEMS.register("basic_chestplate",
            () -> new SuitPieces.Jetpack(INFO, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SuitPieces.Leggings> LEGGINGS = ITEMS.register("basic_leggings",
            () -> new SuitPieces.Leggings(INFO, ArmorMaterials.IRON, piece(ArmorItem.Type.LEGGINGS)));
    public static final DeferredItem<SuitPieces.Boots> BOOTS = ITEMS.register("basic_boots",
            () -> new SuitPieces.Boots(INFO, ArmorMaterials.IRON, piece(ArmorItem.Type.BOOTS)));

    private static Item.Properties piece(ArmorItem.Type type) {
        return new Item.Properties().stacksTo(1).durability(type.getDurability(DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BasicSuit::registerCapabilities);
        // HIGH: el primero de los trajes en la pestaña
        modBus.addListener(EventPriority.HIGH, BasicSuit::addToCreativeTab);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new JetpackFluidHandlerItemStack(() -> RocketDataComponents.JETPACK_FUEL, stack, 8000, 4000),
                CHESTPLATE.get());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            for (DeferredItem<? extends Item> item : new DeferredItem[]{HELMET, CHESTPLATE, LEGGINGS, BOOTS}) {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo añadir el traje básico a la pestaña {}", tab, e);
        }
    }
}
