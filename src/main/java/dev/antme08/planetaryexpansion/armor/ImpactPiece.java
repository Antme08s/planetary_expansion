package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Cosas comunes a las cuatro piezas de la armadura de impacto (Impact Armor). */
final class ImpactPiece {
    private ImpactPiece() {}

    /** Textura del modelo 3D (sacada del diseño de Blockbench de tools/designs/impact_armor.bbmodel). */
    static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/models/armor/impact_armor.png");
    static final String DESIGN = "impact_armor";

    static void tooltip(String piece, List<Component> tooltip) {
        String base = "tooltip." + PlanetaryExpansion.MODID + ".impact.";
        tooltip.add(Component.translatable(base + piece).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(base + "piece").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(base + "set").withStyle(ChatFormatting.GOLD));
    }
}
