package dev.antme08.planetaryexpansion.titan.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Flecha de hielo (1.0.4): punta de colmillo de escarcha; daño de flecha normal, pero congela y ralentiza al blanco. */
public class IceArrow extends AbstractArrow {
    public IceArrow(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public IceArrow(Level level, LivingEntity owner, ItemStack pickup, ItemStack weapon) {
        super(TitanMobs.ICE_ARROW_ENTITY.get(), owner, level, pickup, weapon);
    }

    public IceArrow(Level level, double x, double y, double z, ItemStack pickup, ItemStack weapon) {
        super(TitanMobs.ICE_ARROW_ENTITY.get(), x, y, z, level, pickup, weapon);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        IceDaggerItem.chill(target, 160, 80);
    }

    @Override
    protected ItemStack getDefaultPickupItem() { return new ItemStack(TitanMobs.ICE_ARROW.get()); }
}
