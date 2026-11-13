package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Cangrejo rey azul (0.7.61): gigante del fondo del mar, territorial, casi un minijefe. Camina despacio; si te acercas
 * por delante a menos de 8 bloques o lo golpeas, carga, golpea con las pinzas y empuja lejos. Por delante el caparazón
 * para el 60 % del daño (suena como un escudo): hay que rodearlo. Se olvida de ti a más de 20 bloques.
 */
public class KingCrab extends SeafloorCrab {
    public KingCrab(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        xpReward = 25;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0).add(Attributes.ARMOR, 12.0).add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0).add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.7, true));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.5, 100));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                e -> e.distanceToSqr(this) < 64.0 && inFront(e, 0.3) && !((Player) e).isCreative() && !e.isSpectator()));
    }

    /** ¿Está {@code e} delante del cangrejo? (producto escalar con la dirección del cuerpo) */
    public boolean inFront(Entity e, double min) {
        double yaw = Math.toRadians(getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-6) return true;
        return fwd.dot(to.normalize()) > min;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && !source.is(DamageTypeTags.BYPASSES_ARMOR) && inFront(attacker, 0.4)) {
            amount *= 0.4F; // el caparazón de delante
            playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F);
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        LivingEntity t = getTarget();
        if (t != null && (t.distanceToSqr(this) > 400.0 || !t.isAlive())) setTarget(null);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ARMADILLO_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.TURTLE_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.IRON_GOLEM_STEP, 0.4F, 0.6F); }

    @Override
    public float getVoicePitch() { return 0.6F; }
}
