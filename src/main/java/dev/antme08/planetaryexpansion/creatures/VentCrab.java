package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cangrejo de chimenea (0.7.61): neutral. Vive junto a una chimenea hidrotermal (su "casa", se pone al generarse la
 * chimenea) y no se aleja de ella. Ataca con la pinza si te acercas a menos de 4 bloques, si lo golpeas o si rompes
 * bloques de su chimenea; si te alejas más de 12 bloques de su casa, se olvida de ti y vuelve.
 */
public class VentCrab extends SeafloorCrab {
    private BlockPos home;

    public VentCrab(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0).add(Attributes.ARMOR, 10.0).add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, 8);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6, 60));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                e -> e.distanceToSqr(this) < 16.0 && !((Player) e).isCreative() && !e.isSpectator()));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (home == null) setHome(blockPosition());
        LivingEntity t = getTarget();
        if (t != null && (t.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 144.0 || !t.isAlive())) setTarget(null);
    }

    public boolean guards(BlockPos pos) {
        return home != null && home.distSqr(pos) < 64.0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putLong("Home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) setHome(BlockPos.of(tag.getLong("Home")));
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.TURTLE_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.TURTLE_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.4F); }
}
