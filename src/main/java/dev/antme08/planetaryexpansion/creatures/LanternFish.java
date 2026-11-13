package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Pez farol de Thalassa (0.7.61): pacífico, en bancos de 4–8 cerca de las chimeneas y en el agua bajo el hielo.
 * Huye si te acercas rápido (lo hace AbstractFish) y se puede coger con una cubeta.
 */
public class LanternFish extends AbstractSchoolingFish {
    public LanternFish(EntityType<? extends AbstractSchoolingFish> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, 4.0);
    }

    @Override
    public int getMaxSchoolSize() { return 8; }

    @Override
    public ItemStack getBucketItemStack() { return new ItemStack(Creatures.LANTERN_FISH_BUCKET.get()); }

    @Override
    protected SoundEvent getFlopSound() { return SoundEvents.COD_FLOP; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.COD_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.COD_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.COD_DEATH; }
}
