package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Flecha de colmillo: la dispara el arco o la ballesta (está en #minecraft:arrows) y también el dispensador. */
public class FangArrowItem extends ArrowItem {
    public FangArrowItem(Properties props) {
        super(props);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        return new FangArrow(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        FangArrow a = new FangArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
        a.pickup = AbstractArrow.Pickup.ALLOWED;
        return a;
    }
}
