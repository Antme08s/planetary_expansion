package dev.antme08.planetaryexpansion.warp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Núcleo de distorsión estelar (1.0.3). Se hace con un objeto de cada planeta del sistema solar y se pone con clic
 * derecho sobre un motor de distorsión ya montado (ver {@link WarpDriveBlock#useItemOn}): el motor pasa a ser estelar y
 * puede saltar a otros sistemas. Si se rompe el motor, el núcleo se suelta con él.
 */
public class StellarCoreItem extends Item {
    public StellarCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.planetary_expansion.stellar_warp_core.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
