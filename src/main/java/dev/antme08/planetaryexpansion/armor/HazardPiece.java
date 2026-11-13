package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Cosas comunes a las cuatro piezas de la Hazard Armor. */
final class HazardPiece {
    private HazardPiece() {}

    /** Textura del modelo 3D (sacada del diseño de Blockbench de tools/designs/hazard_armor.bbmodel). */
    static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/models/armor/hazard_armor.png");
    static final String DESIGN = "hazard_armor";

    static void tooltip(String piece, List<Component> tooltip) {
        String base = "tooltip." + PlanetaryExpansion.MODID + ".hazard.";
        tooltip.add(Component.translatable(base + piece).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(base + "piece").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(base + "set").withStyle(ChatFormatting.GOLD));
    }
}
