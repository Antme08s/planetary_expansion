package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Caracol de magma (0.7.64): criatura pasiva de Ignis que vive cerca de la lava. Concha de obsidiana en espiral,
 * cuerpo de roca fundida. Lento; no le hace nada el fuego ni la lava (se desliza por encima de ella). Al recibir un
 * golpe se esconde en la concha 5 s: no se mueve y solo le llega el 25 % del daño. Deja un rastro de brasas.
 */
public class MagmaSnail extends PathfinderMob {
    private static final EntityDataAccessor<Integer> HIDE = SynchedEntityData.defineId(MagmaSnail.class, EntityDataSerializers.INT);
    /** Progreso de esconderse en el cliente (0 fuera, 1 dentro), para la animación. */
    private float hideAnim, hideAnimO;

    public MagmaSnail(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.LAVA, 4.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0).add(Attributes.ARMOR, 4.0).add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HIDE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0, 0.01F) {
            @Override public boolean canUse() { return !isHiding() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isHiding() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F) {
            @Override public boolean canUse() { return !isHiding() && super.canUse(); }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return !isHiding() && super.canUse(); }
        });
    }

    public boolean isHiding() { return entityData.get(HIDE) > 0; }

    /** Cuánto tiempo se esconde al recibir un golpe (ticks). */
    protected int hideTicks() { return 100; }

    /** ¿Se esconde al recibir un golpe? (el titán no, si tiene a quién escupir) */
    protected boolean hidesWhenHurt(DamageSource source) { return true; }

    public float hideProgress(float partialTick) { return hideAnimO + (hideAnim - hideAnimO) * partialTick; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isHiding() && !source.is(DamageTypeTags.BYPASSES_ARMOR)) amount *= 0.25F; // dentro de la concha
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && hidesWhenHurt(source) && !source.is(DamageTypeTags.IS_FIRE)) {
            if (!isHiding()) playSound(SoundEvents.SHULKER_CLOSE, 1.0F, 0.7F);
            entityData.set(HIDE, hideTicks());
            getNavigation().stop();
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            hideAnimO = hideAnim;
            hideAnim += ((isHiding() ? 1F : 0F) - hideAnim) * 0.25F;
            if (random.nextFloat() < 0.12F) {   // rastro de brasas
                Vec3 p = position();
                level().addParticle(random.nextFloat() < 0.3F ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE,
                        p.x + (random.nextDouble() - 0.5) * getBbWidth(), p.y + 0.05, p.z + (random.nextDouble() - 0.5) * getBbWidth(),
                        0, 0.01, 0);
            }
            return;
        }
        int h = entityData.get(HIDE);
        if (h > 0) {
            entityData.set(HIDE, h - 1);
            if (h == 1) playSound(SoundEvents.SHULKER_OPEN, 0.8F, 0.7F);
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        }
    }

    /** Se desliza por encima de la lava, como el strider. */
    @Override
    public boolean canStandOnFluid(FluidState fluid) { return fluid.is(FluidTags.LAVA); }

    @Override
    public boolean isSensitiveToWater() { return true; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isHiding() ? SoundEvents.SHULKER_HURT_CLOSED : SoundEvents.MAGMA_CUBE_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.MAGMA_CUBE_DEATH_SMALL; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.15F, 0.6F); }

    @Override
    public float getVoicePitch() { return 0.7F; }
}
