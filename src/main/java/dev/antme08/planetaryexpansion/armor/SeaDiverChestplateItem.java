package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.client.ArmorModels;
import dev.devce.rocketnautics.content.items.JetpackItem;
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
 * Pechera de la Sea Diver Armor: hereda del Jetpack de Cosmonautics (vuelo con [G], combustible de lava, aire para el casco).
 * Cosmonautics reconoce el jetpack con "instanceof JetpackItem", así que todo su comportamiento se aplica.
 * Su capa de dibujo del jetpack se salta para esta pieza (JetpackLayerMixin): se ve el diseño propio.
 */
public class SeaDiverChestplateItem extends JetpackItem implements CustomSuitPiece {
    public SeaDiverChestplateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ArmorModels.extensions(SeaDiverPiece.DESIGN));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return SeaDiverPiece.TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        SeaDiverPiece.tooltip("chestplate", tooltip);
    }
}
