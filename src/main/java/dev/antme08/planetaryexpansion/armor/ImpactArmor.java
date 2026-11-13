package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.api.capability.JetpackFluidHandlerItemStack;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Armadura de impacto (Impact Armor): el traje de Marte, diseñado por el usuario en Blockbench.
 *
 * Cada pieza hereda de la pieza equivalente de Cosmonautics, que reconoce sus piezas con "instanceof":
 *  - casco → Casco espacial (respirar en el espacio con aire en la espalda, HUD);
 *  - pechera → Jetpack (vuelo con [G], combustible de lava, aire a presión para el casco);
 *  - pantalones → Propulsores de pierna de cobre;
 *  - botas → Botas ancla de cobre.
 * Además: cada pieza -25 % de daño de las tormentas de arena (traje completo: inmune) y -10 % de daño de explosiones
 * y proyectiles; las botas reducen el daño por caída un 60 %.
 * 0.7.64: ya no protege del fuego ni de la lava; eso pasó al traje térmico (ThermalSuit), el de Ignis.
 */
public final class ImpactArmor {
    private ImpactArmor() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);

    private static final ResourceLocation TEXTURE_LOC = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "impact_armor");
    private static final int NETHERITE_DURABILITY = 37;

    public static final DeferredItem<ImpactHelmetItem> HELMET = ITEMS.register("impact_helmet",
            () -> new ImpactHelmetItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.HELMET), TEXTURE_LOC));
    // Sin durabilidad, como el jetpack de Cosmonautics: su barra muestra el combustible.
    public static final DeferredItem<ImpactChestplateItem> CHESTPLATE = ITEMS.register("impact_chestplate",
            () -> new ImpactChestplateItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<ImpactLeggingsItem> LEGGINGS = ITEMS.register("impact_leggings",
            () -> new ImpactLeggingsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.LEGGINGS), TEXTURE_LOC));
    public static final DeferredItem<ImpactBootsItem> BOOTS = ITEMS.register("impact_boots",
            () -> new ImpactBootsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.BOOTS), TEXTURE_LOC));

    private static Item.Properties piece(ArmorItem.Type type) {
        return new Item.Properties().stacksTo(1).fireResistant().durability(type.getDurability(NETHERITE_DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ImpactArmor::registerCapabilities);
        modBus.addListener(ImpactArmor::addToCreativeTab);
        NeoForge.EVENT_BUS.register(ImpactArmor.class);
    }

    /** Recarga con lava, cubo o surtidor (Spout), igual que el jetpack de Cosmonautics. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new JetpackFluidHandlerItemStack(() -> RocketDataComponents.JETPACK_FUEL, stack, 8000, 4000),
                CHESTPLATE.get());
    }

    /**
     * En la pestaña "Cosmonautics World". En el jar 26.08.307 las piezas del traje de Cosmonautics no están en
     * ninguna pestaña (se registran antes de que su registrate elija pestaña), así que buscarlas no basta:
     * si alguna versión futura las pone en una pestaña, las nuestras van justo detrás; si no, al final de
     * "Cosmonautics World".
     */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics")) return;
        ItemStack after = null;
        for (ItemStack stack : event.getParentEntries()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id.getNamespace().equals("rocketnautics") && COSMONAUTICS_SUIT.contains(id.getPath())) {
                after = stack;
            }
        }
        boolean worldTab = tab.getPath().equals("world_tab");
        if (after == null && !worldTab) return;
        try {
            for (DeferredItem<? extends Item> item : new DeferredItem[]{HELMET, CHESTPLATE, LEGGINGS, BOOTS}) {
                ItemStack stack = item.get().getDefaultInstance();
                if (after != null) {
                    event.insertAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    after = stack;
                } else {
                    event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                }
            }
            PlanetaryExpansion.LOGGER.info("[Armaduras] Impact Armor en la pestaña {} ({})", tab,
                    after != null ? "junto al traje de Cosmonautics" : "al final");
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo añadir la Impact Armor a la pestaña {}", tab, e);
        }
    }

    private static final java.util.Set<String> COSMONAUTICS_SUIT =
            java.util.Set.of("space_helmet", "jetpack", "copper_leg_thrusters", "copper_anchor_boots");

    // ------------------------------------------------------------------ protecciones

    /** Número de piezas de impacto puestas (0-4). */
    public static int pieces(LivingEntity entity) {
        int n = 0;
        if (entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.CHEST).is(CHESTPLATE.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.FEET).is(BOOTS.get())) n++;
        return n;
    }

    /** Multiplicador del daño de las tormentas de arena de Marte: 1 sin traje, 0 con el traje completo. */
    public static float dustStormFactor(LivingEntity entity) {
        // 0.7.66: el filtro de polvo deja pasar solo el 40 %
        return Math.max(0f, 1f - 0.25f * pieces(entity)) * SurvivalKits.dustFactor(entity);
    }

    /** Golpes: cada pieza -10 % de daño de explosiones y proyectiles (máx. -40 %). */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION) && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        int n = pieces(event.getEntity());
        if (n > 0) event.setAmount(event.getAmount() * (1f - 0.10f * n));
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity().getItemBySlot(EquipmentSlot.FEET).is(BOOTS.get())) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.4f);
        }
    }
}
