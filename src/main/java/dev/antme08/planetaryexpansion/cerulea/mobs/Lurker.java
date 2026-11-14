package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.EnumSet;

/**
 * Acechador de Cerúlea (1.0.3): depredador nocturno del pantano. Sale de noche; se queda quieto medio sumergido, solo
 * con los ojos y las manchas brillando, y cuando te acercas salta y te ataca. Le teme a la luz (antorchas, fogatas,
 * un jugador con una luz en la mano): no se acerca y se va a la oscuridad. De día se marcha (desaparece si no hay nadie
 * cerca).
 */
public class Lurker extends Monster {
    public Lurker(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
        setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 7.0).add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 20.0)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Predators.AvoidLightGoal(this, 1.4));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.35, false));
        goalSelector.addGoal(5, new LurkGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7, 120));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> !Predators.lit(p)));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 20 != 0) return;
        LivingEntity t = getTarget();
        if (t != null && Predators.lit(t) && getLastHurtByMob() != t) setTarget(null);   // la luz lo protege
        // de día se va: si no hay nadie cerca, desaparece
        if (level().isDay() && !isPersistenceRequired() && t == null && level().getNearestPlayer(this, 24.0) == null
                && random.nextInt(6) == 0) discard();
    }

    /** Sin presa: se queda quieto un rato (al acecho), sobre todo en el agua. */
    static class LurkGoal extends Goal {
        private final Lurker mob;
        private int ticks;

        LurkGoal(Lurker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null && mob.random.nextInt(mob.isInWater() ? 60 : 200) == 0;
        }

        @Override
        public void start() {
            ticks = 100 + mob.random.nextInt(200);
            mob.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() { return ticks-- > 0 && mob.getTarget() == null; }
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.AXOLOTL_IDLE_AIR; }

    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.45F; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.HOGLIN_STEP, 0.15F, 1.3F); }
}
