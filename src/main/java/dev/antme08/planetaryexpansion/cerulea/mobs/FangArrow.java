package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Flecha de colmillo (1.0.3): punta de colmillo de corredor o de acechador; hace más daño que una flecha normal (3 en vez de 2). */
public class FangArrow extends AbstractArrow {
    public FangArrow(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        setBaseDamage(3.0);
    }

    public FangArrow(Level level, LivingEntity owner, ItemStack pickup, ItemStack weapon) {
        super(CeruleaMobs.FANG_ARROW_ENTITY.get(), owner, level, pickup, weapon);
        setBaseDamage(3.0);
    }

    public FangArrow(Level level, double x, double y, double z, ItemStack pickup, ItemStack weapon) {
        super(CeruleaMobs.FANG_ARROW_ENTITY.get(), x, y, z, level, pickup, weapon);
        setBaseDamage(3.0);
    }

    @Override
    protected ItemStack getDefaultPickupItem() { return new ItemStack(CeruleaMobs.FANG_ARROW.get()); }
}
