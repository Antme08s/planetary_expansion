package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.api.capability.JetpackFluidHandlerItemStack;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Armadura glacial (Glacial Armor): el traje del planeta helado. Mismo modelo que la Impact Armor (diseño del
 * usuario en Blockbench) en blanco apagado con visor azul oscuro, elegido por el usuario con vista previa.
 *
 * Como la Impact, cada pieza hereda de la pieza equivalente de Cosmonautics (casco espacial, jetpack,
 * propulsores de pierna, botas ancla). Además:
 *  - frío: cada pieza -25 % de congelación en las ventiscas y -15 % de daño por congelación; el traje completo
 *    no se congela nunca (tampoco en nieve polvo);
 *  - viento: cada pieza -20 % de empuje del viento de las tormentas (traje completo: 20 % del empuje);
 *  - botas: se puede andar sobre la nieve polvo.
 */
public final class GlacialArmor {
    private GlacialArmor() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);

    private static final ResourceLocation TEXTURE_LOC = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "glacial_armor");
    private static final int NETHERITE_DURABILITY = 37;

    public static final DeferredItem<GlacialHelmetItem> HELMET = ITEMS.register("glacial_helmet",
            () -> new GlacialHelmetItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.HELMET), TEXTURE_LOC));
    public static final DeferredItem<GlacialChestplateItem> CHESTPLATE = ITEMS.register("glacial_chestplate",
            () -> new GlacialChestplateItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<GlacialLeggingsItem> LEGGINGS = ITEMS.register("glacial_leggings",
            () -> new GlacialLeggingsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.LEGGINGS), TEXTURE_LOC));
    public static final DeferredItem<GlacialBootsItem> BOOTS = ITEMS.register("glacial_boots",
            () -> new GlacialBootsItem(ArmorMaterials.NETHERITE, piece(ArmorItem.Type.BOOTS), TEXTURE_LOC));

    private static Item.Properties piece(ArmorItem.Type type) {
        return new Item.Properties().stacksTo(1).fireResistant().durability(type.getDurability(NETHERITE_DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(GlacialArmor::registerCapabilities);
        // LOW: después de la Impact Armor, para que en la pestaña salga detrás
        modBus.addListener(EventPriority.LOW, GlacialArmor::addToCreativeTab);
        NeoForge.EVENT_BUS.register(GlacialArmor.class);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new JetpackFluidHandlerItemStack(() -> RocketDataComponents.JETPACK_FUEL, stack, 8000, 4000),
                CHESTPLATE.get());
    }

    /** Detrás de la Impact Armor en "Cosmonautics World". */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            for (DeferredItem<? extends Item> item : new DeferredItem[]{HELMET, CHESTPLATE, LEGGINGS, BOOTS}) {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo añadir la Glacial Armor a la pestaña {}", tab, e);
        }
    }

    // ------------------------------------------------------------------ protecciones

    public static int pieces(LivingEntity entity) {
        int n = 0;
        if (entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.CHEST).is(CHESTPLATE.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.LEGS).is(LEGGINGS.get())) n++;
        if (entity.getItemBySlot(EquipmentSlot.FEET).is(BOOTS.get())) n++;
        return n;
    }

    /** Piezas que protegen del frío: las de la Glacial y, desde 0.7.24, las de la Sea Diver Armor. */
    public static int coldPieces(LivingEntity entity) {
        return Math.max(pieces(entity), SeaDiverArmor.pieces(entity));
    }

    /** Multiplicador de la congelación de las ventiscas: 1 sin traje, 0 con el traje completo. */
    public static float coldFactor(LivingEntity entity) {
        // 0.7.66: el calentador químico deja pasar solo el 40 %
        return Math.max(0f, 1f - 0.25f * coldPieces(entity)) * SurvivalKits.coldFactor(entity);
    }

    /** Multiplicador del empuje del viento de las tormentas: 1 sin traje, 0.2 con el traje completo. */
    public static float windFactor(LivingEntity entity) {
        return 1f - 0.2f * pieces(entity);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FREEZING)) return;
        int n = coldPieces(event.getEntity());
        if (n >= 4) {
            event.setCanceled(true);
        } else if (n > 0) {
            event.setAmount(event.getAmount() * (1f - 0.15f * n));
        }
    }

    /** Traje completo: no se acumula congelación (ni ventisca ni nieve polvo). */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (player.level().isClientSide || player.getTicksFrozen() <= 0) return;
        if (coldPieces(player) >= 4) player.setTicksFrozen(0);
    }
}
