package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.client.ArmorModels;
import dev.devce.rocketnautics.content.items.LegThrustersItem;
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

/** Pantalones hazard: heredan de los Propulsores de pierna de cobre de Cosmonautics. */
public class HazardLeggingsItem extends LegThrustersItem implements CustomSuitPiece {
    public HazardLeggingsItem(Holder<ArmorMaterial> material, Properties properties, ResourceLocation textureLoc) {
        super(material, properties, textureLoc);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ArmorModels.extensions(HazardPiece.DESIGN));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return HazardPiece.TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        HazardPiece.tooltip("leggings", tooltip);
    }
}
