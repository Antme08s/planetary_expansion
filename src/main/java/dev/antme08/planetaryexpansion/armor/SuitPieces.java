package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.client.ArmorModels;
import dev.devce.rocketnautics.content.items.AnchorBootsItem;
import dev.devce.rocketnautics.content.items.JetpackItem;
import dev.devce.rocketnautics.content.items.LegThrustersItem;
import dev.devce.rocketnautics.content.items.SpaceHelmetItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

/**
 * Piezas genéricas de los trajes nuevos (0.7.64: traje espacial básico y traje térmico). Igual que las de los otros
 * trajes, cada una hereda de la pieza equivalente de Cosmonautics (casco espacial, jetpack, propulsores de pierna y
 * botas ancla), que las reconoce con "instanceof"; el modelo 3D sale de armor_models/&lt;diseño&gt;.json.
 */
public final class SuitPieces {
    private SuitPieces() {}

    /** Diseño (modelo y textura) y claves de los textos de un traje. {@code setLine}: añade la línea del traje completo. */
    public record Info(String design, String tooltipKey, boolean setLine) {
        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/models/armor/" + design + ".png");
        }

        void tooltip(String piece, List<Component> tooltip) {
            String base = "tooltip." + PlanetaryExpansion.MODID + "." + tooltipKey + ".";
            tooltip.add(Component.translatable(base + piece).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(base + "piece").withStyle(ChatFormatting.GOLD));
            if (setLine) tooltip.add(Component.translatable(base + "set").withStyle(ChatFormatting.GOLD));
        }
    }

    public static class Helmet extends SpaceHelmetItem implements CustomSuitPiece {
        private final Info info;

        public Helmet(Info info, Holder<ArmorMaterial> material, Properties properties) {
            super(material, properties, ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, info.design()));
            this.info = info;
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(ArmorModels.extensions(info.design()));
        }

        @Override
        public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
            return info.texture();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            info.tooltip("helmet", tooltip);
        }
    }

    /** Pechera: jetpack de Cosmonautics (vuelo, combustible de lava, aire para el casco). Sin durabilidad, como él. */
    public static class Jetpack extends JetpackItem implements CustomSuitPiece {
        private final Info info;

        public Jetpack(Info info, Properties properties) {
            super(properties);
            this.info = info;
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(ArmorModels.extensions(info.design()));
        }

        @Override
        public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
            return info.texture();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            info.tooltip("chestplate", tooltip);
        }
    }

    public static class Leggings extends LegThrustersItem implements CustomSuitPiece {
        private final Info info;

        public Leggings(Info info, Holder<ArmorMaterial> material, Properties properties) {
            super(material, properties, ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, info.design()));
            this.info = info;
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(ArmorModels.extensions(info.design()));
        }

        @Override
        public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
            return info.texture();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            info.tooltip("leggings", tooltip);
        }
    }

    public static class Boots extends AnchorBootsItem implements CustomSuitPiece {
        private final Info info;

        public Boots(Info info, Holder<ArmorMaterial> material, Properties properties) {
            super(material, properties, ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, info.design()));
            this.info = info;
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(ArmorModels.extensions(info.design()));
        }

        @Override
        public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
            return info.texture();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            info.tooltip("boots", tooltip);
        }
    }
}
