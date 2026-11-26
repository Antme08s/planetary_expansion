package dev.antme08.planetaryexpansion.titan.mobs;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/** Daga de hielo (1.0.4): colmillo de escarcha del acechador de bruma; congela un poco y ralentiza a lo que golpea. */
public class IceDaggerItem extends SwordItem {
    public IceDaggerItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        chill(target, 100, 40);
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Escarcha: suma ticks de congelación (como la nieve polvo) y ralentiza un momento. */
    static void chill(LivingEntity target, int frozen, int slow) {
        if (target.canFreeze()) target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + frozen));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 0));
    }
}
