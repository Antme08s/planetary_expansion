package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Caracol titán (0.7.64): variedad gigante y rara del caracol de magma (~2,6 bloques), con la concha cubierta de oro.
 * No ataca solo; si lo atacas, se gira hacia ti y escupe ráfagas de 3 bolas de magma que prenden fuego (como las del
 * blaze) mientras te vea a menos de 24 bloques. Solo se esconde en la concha si no tiene a quién escupir.
 * Se olvida de ti a más de 32 bloques.
 */
public class MagmaTitan extends MagmaSnail {
    private static final EntityDataAccessor<Boolean> SPITTING = SynchedEntityData.defineId(MagmaTitan.class, EntityDataSerializers.BOOLEAN);

    public MagmaTitan(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        xpReward = 30;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 90.0).add(Attributes.ARMOR, 14.0).add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPITTING, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new SpitGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public boolean isSpitting() { return entityData.get(SPITTING); }

    @Override
    protected int hideTicks() { return 40; }

    @Override
    protected boolean hidesWhenHurt(DamageSource source) { return source.getEntity() == null && getTarget() == null; }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || t.distanceToSqr(this) > 32 * 32 || (t instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null);
        }
    }

    /** Ráfagas de 3 bolas de magma cada 2-3 s mientras ve a su objetivo. */
    private class SpitGoal extends Goal {
        private int cooldown, shots, gap;

        SpitGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && t.distanceToSqr(MagmaTitan.this) < 24 * 24;
        }

        @Override
        public void start() { cooldown = 20; shots = 0; }

        @Override
        public void stop() { entityData.set(SPITTING, false); }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getNavigation().stop();
            getLookControl().setLookAt(t, 10.0F, 10.0F);
            boolean sees = getSensing().hasLineOfSight(t);
            if (shots > 0) {
                if (--gap > 0) return;
                spit(t);
                gap = 6;
                if (--shots == 0) {
                    cooldown = 40 + random.nextInt(20);
                    entityData.set(SPITTING, false);
                }
                return;
            }
            if (--cooldown <= 0 && sees) {
                shots = 3;
                gap = 10;   // abre la boca un momento antes del primer disparo
                entityData.set(SPITTING, true);
                playSound(SoundEvents.BLAZE_SHOOT, 1.2F, 0.5F);
            }
        }

        private void spit(LivingEntity t) {
            double yaw = Math.toRadians(getYRot());
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 mouth = position().add(fwd.scale(getBbWidth() * 0.9)).add(0, 0.8, 0);
            Vec3 aim = t.getEyePosition().subtract(mouth);
            double spread = 0.08 * Math.sqrt(aim.length());
            Vec3 dir = new Vec3(aim.x + random.triangle(0, spread), aim.y + random.triangle(0, spread), aim.z + random.triangle(0, spread)).normalize();
            SmallFireball ball = new SmallFireball(level(), MagmaTitan.this, dir);
            ball.setPos(mouth.x, mouth.y, mouth.z);
            level().addFreshEntity(ball);
            playSound(SoundEvents.MAGMA_CUBE_JUMP, 1.0F, 0.6F);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isHiding() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.MAGMA_CUBE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.MAGMA_CUBE_DEATH; }

    @Override
    public float getVoicePitch() { return 0.45F; }
}
