package dev.antme08.planetaryexpansion.armor;

import dev.antme08.planetaryexpansion.client.ArmorModels;
import dev.devce.rocketnautics.content.items.AnchorBootsItem;
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

/** Botas glaciales: heredan de las Botas ancla de cobre de Cosmonautics; además se puede andar sobre la nieve polvo. */
public class GlacialBootsItem extends AnchorBootsItem implements CustomSuitPiece {
    public GlacialBootsItem(Holder<ArmorMaterial> material, Properties properties, ResourceLocation textureLoc) {
        super(material, properties, textureLoc);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ArmorModels.extensions(GlacialPiece.DESIGN));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return GlacialPiece.TEXTURE;
    }

    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, net.minecraft.world.entity.LivingEntity wearer) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        GlacialPiece.tooltip("boots", tooltip);
    }
}
