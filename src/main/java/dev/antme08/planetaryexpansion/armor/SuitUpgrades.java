package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SmithingTemplateItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Progresión de trajes (0.7.64, idea de un amigo del usuario): una plantilla de mejora por planeta y los materiales que
 * solo existen allí. La primera plantilla se fabrica con materiales del planeta (y una lámina de aleación de titanio);
 * después se duplica con 7 diamantes y 1 material del planeta. En la mesa de herrería: plantilla + pieza del traje
 * básico + lingote de netherita → pieza del traje del planeta.
 *
 * Materiales (las recetas y los botines están en generate.py):
 *  - fragmento de meteorito: del basalto lunar envejecido de los núcleos de los meteoritos de Marte;
 *  - cristal criogénico: a veces, del hielo azul o compacto minado en el mundo helado;
 *  - azufre venusiano: a veces, del basalto minado en Venus;
 *  - Thalassa usa caparazón de rey y colmillo abisal; Ignis, concha ígnea y núcleo de magma (caracoles de magma).
 */
public final class SuitUpgrades {
    private SuitUpgrades() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PlanetaryExpansion.MODID);

    public static final DeferredItem<Item> METEORITE_FRAGMENT = ITEMS.registerSimpleItem("meteorite_fragment");
    public static final DeferredItem<Item> CRYO_CRYSTAL = ITEMS.registerSimpleItem("cryo_crystal");
    public static final DeferredItem<Item> VENUSIAN_SULFUR = ITEMS.registerSimpleItem("venusian_sulfur");

    public static final DeferredItem<SmithingTemplateItem> IMPACT_TEMPLATE = template("impact");
    public static final DeferredItem<SmithingTemplateItem> GLACIAL_TEMPLATE = template("glacial");
    public static final DeferredItem<SmithingTemplateItem> HAZARD_TEMPLATE = template("hazard");
    public static final DeferredItem<SmithingTemplateItem> SEA_DIVER_TEMPLATE = template("sea_diver");
    public static final DeferredItem<SmithingTemplateItem> THERMAL_TEMPLATE = template("thermal");

    private static final List<ResourceLocation> EMPTY_ARMOR = List.of(
            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"),
            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots"));
    private static final List<ResourceLocation> EMPTY_INGOT = List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"));

    /** Como la plantilla de netherita de Minecraft, con sus textos en la descripción. */
    private static DeferredItem<SmithingTemplateItem> template(String suit) {
        String k = "item." + PlanetaryExpansion.MODID + ".smithing_template.";
        return ITEMS.register(suit + "_upgrade_template", () -> new SmithingTemplateItem(
                Component.translatable(k + "applies_to").withStyle(ChatFormatting.BLUE),
                Component.translatable(k + "ingredients").withStyle(ChatFormatting.BLUE),
                Component.translatable(k + suit + ".upgrade").withStyle(ChatFormatting.GRAY),
                Component.translatable(k + "base_slot_description"),
                Component.translatable(k + "additions_slot_description"),
                EMPTY_ARMOR, EMPTY_INGOT));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(EventPriority.LOWEST, SuitUpgrades::addToCreativeTab);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation tab = event.getTabKey().location();
        if (!tab.getNamespace().equals("rocketnautics") || !tab.getPath().equals("world_tab")) return;
        try {
            for (DeferredItem<? extends Item> item : List.of(METEORITE_FRAGMENT, CRYO_CRYSTAL, VENUSIAN_SULFUR, IMPACT_TEMPLATE,
                    GLACIAL_TEMPLATE, HAZARD_TEMPLATE, SEA_DIVER_TEMPLATE, THERMAL_TEMPLATE)) {
                event.accept(item.get().getDefaultInstance(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (RuntimeException e) {
            PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudieron añadir las plantillas a la pestaña {}", tab, e);
        }
    }
}
