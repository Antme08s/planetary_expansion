package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arpón lanzado (0.7.61): como el tridente, pero apenas se frena bajo el agua. Hace 10 de daño, rebota al golpear y
 * se recoge como el tridente.
 */
public class HarpoonEntity extends AbstractArrow {
    private boolean dealtDamage;

    public HarpoonEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public HarpoonEntity(Level level, LivingEntity owner, ItemStack stack) {
        super(Creatures.HARPOON_ENTITY.get(), owner, level, stack, null);
    }

    @Override
    protected ItemStack getDefaultPickupItem() { return new ItemStack(Creatures.HARPOON.get()); }

    @Override
    protected float getWaterInertia() { return 0.99F; }

    @Override
    protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        return dealtDamage ? null : super.findHitEntity(start, end);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        DamageSource source = damageSources().trident(this, owner == null ? this : owner);
        dealtDamage = true;
        if (target.hurt(source, 10.0F) && target instanceof LivingEntity living && owner instanceof LivingEntity o) {
            living.setLastHurtByMob(o);
        }
        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() { return SoundEvents.TRIDENT_HIT_GROUND; }

    @Override
    public void tickDespawn() {
        if (pickup != Pickup.ALLOWED) super.tickDespawn();
    }

    @Override
    public boolean shouldRender(double x, double y, double z) { return true; }
}
