package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.api.capability.JetpackFluidHandlerItemStack;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/**
 * Hazard Armor: el traje de Venus (nombre elegido por el usuario). Mismo modelo que la Impact, casi negro con
 * visor verde tóxico, símbolo de radiación en el visor y una señal de radiación en el pecho.
 *
 * Cada pieza hereda de la pieza equivalente de Cosmonautics, como los otros trajes. Además:
 *  - calor y lluvia ácida de Venus: -25 % por pieza, inmune con el traje completo (ver weather/VenusHazards);
 *  - radiación de Create Nuclear: +0.25 del atributo createnuclear:generic.irradiated_resistance por pieza, lo mismo
 *    que da su traje antirradiación (4 piezas = protección total). Create Nuclear es obligatorio desde 0.7.2.
 */
public final class HazardArmor {
    private HazardArmor() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);

    private static final ResourceLocation TEXTURE_LOC = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "hazard_armor");
    private static final int NETHERITE_DURABILITY = 37;
    private static final ResourceLocation RADIATION_ATTRIBUTE = ResourceLocation.fromNamespaceAndPath("createnuclear", "generic.irradiated_resistance");
    private static final double RADIATION_PER_PIECE = 0.25;

    public static final DeferredItem<HazardHelmetItem> HELMET = ITEMS.register("hazard_helmet",
            () -> new HazardHelmetItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.HELMET), TEXTURE_LOC));
    public static final DeferredItem<HazardChestplateItem> CHESTPLATE = ITEMS.register("hazard_chestplate",
            () -> new HazardChestplateItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<HazardLeggingsItem> LEGGINGS = ITEMS.register("hazard_leggings",
            () -> new HazardLeggingsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.LEGGINGS), TEXTURE_LOC));
    public static final DeferredItem<HazardBootsItem> BOOTS = ITEMS.register("hazard_boots",
            () -> new HazardBootsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.BOOTS), TEXTURE_LOC));

    /** Lámina de plomo (prensa de Create con plomo de Create Nuclear): ingrediente de la Hazard Armor. */
    public static final DeferredItem<Item> LEAD_SHEET = ITEMS.registerSimpleItem("lead_sheet");

    private static Item.Properties piece(ArmorItem.Type type) {
        return new Item.Properties().stacksTo(1).fireResistant().durability(type.getDurability(NETHERITE_DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(HazardArmor::registerCapabilities);
        // LOWEST: detrás de la Impact y la Glacial en la pestaña
        modBus.addListener(EventPriority.LOWEST, HazardArmor::addToCreativeTab);
        NeoForge.EVENT_BUS.register(HazardArmor.class);
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
            for (DeferredItem<? extends Item> item : new DeferredItem[]{LEAD_SHEET, HELMET, CHESTPLATE, LEGGINGS, BOOTS}) {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo añadir la Hazard Armor a la pestaña {}", tab, e);
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

    public static boolean isPiece(Item item) {
        return item == HELMET.get() || item == CHESTPLATE.get() || item == LEGGINGS.get() || item == BOOTS.get();
    }

    /** Fracción del calor y la lluvia ácida de Venus que llega: 1 sin traje, 0 con el traje completo. */
    public static float venusFactor(LivingEntity entity) {
        return Math.max(0f, 1f - 0.25f * pieces(entity));
    }

    private static Optional<Holder.Reference<Attribute>> radiationAttribute;

    /** Resistencia a la radiación de Create Nuclear, como su traje antirradiación (si el mod está). */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        Item item = event.getItemStack().getItem();
        if (!isPiece(item) || !(item instanceof ArmorItem armor)) return;
        if (radiationAttribute == null) {
            radiationAttribute = BuiltInRegistries.ATTRIBUTE.getHolder(RADIATION_ATTRIBUTE);
        }
        radiationAttribute.ifPresent(attr -> event.addModifier(attr,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID,
                        "hazard_radiation_" + armor.getType().getName()), RADIATION_PER_PIECE, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.bySlot(armor.getType().getSlot())));
    }
}
