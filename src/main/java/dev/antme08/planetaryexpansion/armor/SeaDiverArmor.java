package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.api.capability.JetpackFluidHandlerItemStack;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sea Diver Armor (0.7.24, idea y nombre del usuario): traje de buceo de latón y azul marino. Mismo modelo que la
 * Glacial con otros colores (vista previa aprobada antes de meterla).
 *
 * Casco, pechera y pantalones heredan de las piezas de Cosmonautics como los otros trajes (casco espacial con aire
 * de la pechera, jetpack, propulsores). Las botas son normales: las botas ancla te hunden y no dejarían nadar.
 * Además:
 *  - frío: protege igual que la Glacial (cuenta en GlacialArmor.coldPieces);
 *  - presión del mar de Create Deep Seas: con el traje completo aguanta hasta 600 m (el de cobre, 280 m); más hondo
 *    se aplican las reglas del traje de cobre de Deep Seas (ver mixin/DiverPressureMixin);
 *  - nado: +12.5 % de velocidad por pieza (+50 % con el traje completo).
 */
public final class SeaDiverArmor {
    private SeaDiverArmor() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);

    private static final ResourceLocation TEXTURE_LOC = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "sea_diver_armor");
    private static final int NETHERITE_DURABILITY = 37;
    public static final double SAFE_DEPTH = 600.0;
    private static final double SWIM_PER_PIECE = 0.125;

    public static final DeferredItem<SeaDiverHelmetItem> HELMET = ITEMS.register("sea_diver_helmet",
            () -> new SeaDiverHelmetItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.HELMET), TEXTURE_LOC));
    public static final DeferredItem<SeaDiverChestplateItem> CHESTPLATE = ITEMS.register("sea_diver_chestplate",
            () -> new SeaDiverChestplateItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<SeaDiverLeggingsItem> LEGGINGS = ITEMS.register("sea_diver_leggings",
            () -> new SeaDiverLeggingsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.LEGGINGS), TEXTURE_LOC));
    public static final DeferredItem<SeaDiverBootsItem> BOOTS = ITEMS.register("sea_diver_boots",
            () -> new SeaDiverBootsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.BOOTS)));

    private static Item.Properties piece(ArmorItem.Type type) {
        return new Item.Properties().stacksTo(1).fireResistant().durability(type.getDurability(NETHERITE_DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(SeaDiverArmor::registerCapabilities);
        // LOWEST: detrás de los otros trajes en la pestaña
        modBus.addListener(EventPriority.LOWEST, SeaDiverArmor::addToCreativeTab);
        NeoForge.EVENT_BUS.register(SeaDiverArmor.class);
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
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo añadir la Sea Diver Armor a la pestaña {}", tab, e);
        }
    }

    public static int pieces(LivingEntity entity) {
        int n = 0;
        if (entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.CHEST).is(CHESTPLATE.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.FEET).is(BOOTS.get())) n++;
        return n;
    }

    public static boolean fullSet(LivingEntity entity) {
        return pieces(entity) >= 4;
    }

    private static boolean isPiece(Item item) {
        return item == HELMET.get() || item == CHESTPLATE.get() || item == LEGGINGS.get() || item == BOOTS.get();
    }

    /** Velocidad de nado (atributo de NeoForge): +12.5 % por pieza. */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        Item item = event.getItemStack().getItem();
        if (!isPiece(item) || !(item instanceof ArmorItem armor)) return;
        event.addModifier(NeoForgeMod.SWIM_SPEED,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID,
                        "sea_diver_swim_" + armor.getType().getName()), SWIM_PER_PIECE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                EquipmentSlotGroup.bySlot(armor.getType().getSlot()));
    }
}
