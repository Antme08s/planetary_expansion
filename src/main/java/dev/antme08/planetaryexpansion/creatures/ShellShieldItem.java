package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/** Escudo de caparazón de rey (0.7.61): como el escudo normal, con el triple de durabilidad. Se repara con caparazón. */
public class ShellShieldItem extends ShieldItem {
    public ShellShieldItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Creatures.KING_SHELL.get());
    }
}
