package dev.antme08.planetaryexpansion.ferra.mobs;

import dev.antme08.planetaryexpansion.creatures.AbyssalStalker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Rastreador ciego de Ferra (1.0.3, idea del usuario: "cuadrúpedos que cacen en manada, ciegos como el Warden, que
 * cacen por ruido"). No ve: oye a los jugadores que corren, caminan, minan o pelean a menos de 24 bloques (agachado o
 * quieto no hace ruido) y avisa a la manada. Sin presa va a mirar los ruidos recientes (bloques rotos, proyectiles que
 * caen: una bola de nieve lejos los distrae). Si no oye nada en 5 s se olvida, salvo que estés a menos de 2,5 bloques.
 * Mientras caza, sus órganos brillan más (lo dibuja el cliente).
 */
public class BlindStalker extends Monster {
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(BlindStalker.class, EntityDataSerializers.BOOLEAN);
    static final double HEAR_RANGE = 24.0;
    private static final int FORGET_TICKS = 100;
    private int quietTicks;

    public BlindStalker(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0).add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.ARMOR, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, HEAR_RANGE)
                .add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HUNTING, false);
    }

    public boolean isHunting() { return entityData.get(HUNTING); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(5, new InvestigateGoal(this));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 0.6, 100));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        long now = level().getGameTime();
        LivingEntity target = getTarget();
        if (tickCount % 5 == 0) {
            Player heard = null;
            double best = HEAR_RANGE * HEAR_RANGE;
            for (Player p : level().players()) {
                double d = p.distanceToSqr(this);
                if (d < best && p.isAlive() && AbyssalStalker.noisy(p, now)) { best = d; heard = p; }
            }
            if (heard != null) {
                if (target != heard) {
                    playSound(SoundEvents.WARDEN_ANGRY, 1.2F, 1.5F);
                    alertPack(heard);
                }
                setTarget(heard);
                quietTicks = 0;
            } else if (target != null) {
                if (target.distanceToSqr(this) > 6.25) quietTicks += 5;     // de muy cerca lo nota aunque estés quieto
                if (quietTicks >= FORGET_TICKS || !target.isAlive()) { setTarget(null); quietTicks = 0; }
            }
        }
        entityData.set(HUNTING, getTarget() != null);
    }

    /** Avisa a la manada (a menos de 16 bloques). */
    private void alertPack(LivingEntity prey) {
        for (BlindStalker s : level().getEntitiesOfClass(BlindStalker.class, getBoundingBox().inflate(16)))
            if (s != this && s.getTarget() == null) { s.setTarget(prey); s.quietTicks = 0; }
    }

    /** Sin presa: va al último ruido que oyó (bloque roto, proyectil que cae). */
    static class InvestigateGoal extends Goal {
        private final BlindStalker mob;
        private Vec3 spot;
        private int ticks;

        InvestigateGoal(BlindStalker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || mob.tickCount % 10 != 0) return false;
            FerraMobs.Noise n = FerraMobs.nearestNoise(mob.position(), HEAR_RANGE, mob.level().getGameTime());
            if (n == null || n.pos().distanceToSqr(mob.position()) < 4) return false;
            spot = n.pos();
            return true;
        }

        @Override
        public void start() {
            ticks = 160;
            mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.15);
            mob.playSound(SoundEvents.WARDEN_SNIFF, 1.0F, 1.5F);
        }

        @Override
        public boolean canContinueToUse() {
            return ticks-- > 0 && mob.getTarget() == null && !mob.getNavigation().isDone();
        }
    }

    @Override
    public float getVoicePitch() { return 1.45F + (random.nextFloat() - 0.5F) * 0.15F; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_SNIFF; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WARDEN_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.WOLF_STEP, 0.2F, 0.7F); }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean r = super.doHurtTarget(target);
        if (r) playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 0.8F, 1.4F);
        return r;
    }
}
