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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Traje térmico (0.7.64), el traje de Ignis: tela aluminizada, visor dorado, placas cerámicas y radiadores.
 * Se consigue mejorando el traje básico con la plantilla térmica (concha ígnea y núcleo de magma de los caracoles).
 *  - cada pieza: -15 % de daño por fuego, lava y bloques de magma; el traje completo es inmune y no se queda ardiendo;
 *  - el casco deja ver un poco dentro de la lava (ThermalLavaVision, en el cliente);
 *  - el traje completo reduce a la mitad el calor de Venus (antes lo hacía la Impact).
 */
public final class ThermalSuit {
    private ThermalSuit() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);
    public static final SuitPieces.Info INFO = new SuitPieces.Info("thermal_armor", "thermal", true);
    private static final int NETHERITE_DURABILITY = 37;

    public static final DeferredItem<SuitPieces.Helmet> HELMET = ITEMS.register("thermal_helmet",
            () -> new SuitPieces.Helmet(INFO, ArmorMaterials.NETHERITE, piece(ArmorItem.Type.HELMET)));
    public static final DeferredItem<SuitPieces.Jetpack> CHESTPLATE = ITEMS.register("thermal_chestplate",
            () -> new SuitPieces.Jetpack(INFO, new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredItem<SuitPieces.Leggings> LEGGINGS = ITEMS.register("thermal_leggings",
            () -> new SuitPieces.Leggings(INFO, ArmorMaterials.NETHERITE, piece(ArmorItem.Type.LEGGINGS)));
    public static final DeferredItem<SuitPieces.Boots> BOOTS = ITEMS.register("thermal_boots",
            () -> new SuitPieces.Boots(INFO, ArmorMaterials.NETHERITE, piece(ArmorItem.Type.BOOTS)));

    private static Item.Properties piece(ArmorItem.Type type) {
        return new Item.Properties().stacksTo(1).fireResistant().durability(type.getDurability(NETHERITE_DURABILITY));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ThermalSuit::registerCapabilities);
        // LOWEST: detrás de los demás trajes
        modBus.addListener(EventPriority.LOWEST, ThermalSuit::addToCreativeTab);
        NeoForge.EVENT_BUS.register(ThermalSuit.class);
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
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo añadir el traje térmico a la pestaña {}", tab, e);
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

    public static boolean wearsHelmet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get());
    }

    /** Fuego, lava y bloques de magma (la etiqueta is_fire de Minecraft incluye los tres). */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) return;
        int n = pieces(event.getEntity());
        if (n >= 4) {
            event.setCanceled(true);
        } else if (n > 0) {
            event.setAmount(event.getAmount() * (1f - 0.15f * n));
        }
    }

    /** Traje completo: fuera de la lava se apaga enseguida (no se queda ardiendo al salir). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (player.level().isClientSide || player.getRemainingFireTicks() <= 0 || player.isInLava()) return;
        if (pieces(player) >= 4) player.clearFire();
    }
}
