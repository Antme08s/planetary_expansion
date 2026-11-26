package dev.antme08.planetaryexpansion.fulgor.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Atrapa-rayos (1.0.4): monstruo alto de basalto y cobre de las agujas de Fulgor. Sin presa, sube a lo más alto que
 * encuentre cerca y se queda allí con la antena al cielo. Ataca a los jugadores dando saltos largos y con las garras.
 * Su antena atrae los rayos si no hay ningún pararrayos que los atraiga; un rayo a menos de 6 bloques (o encima) lo
 * recarga: se cura 10, brilla y pega un 50 % más durante 2 min. No soporta los pararrayos: si hay uno a menos de 16
 * bloques se aleja de él (sirven para proteger la base).
 */
public class SpireCatcher extends Monster implements Chargeable {
    private static final EntityDataAccessor<Integer> SUPER = SynchedEntityData.defineId(SpireCatcher.class, EntityDataSerializers.INT);
    private static final ResourceLocation DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("planetary_expansion", "catcher_supercharge");
    public static final int SUPERCHARGE_TICKS = 20 * 60 * 2;

    public SpireCatcher(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 32.0).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3).add(Attributes.ARMOR, 4.0).add(Attributes.SAFE_FALL_DISTANCE, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SUPER, 0);
    }

    public int supercharge() { return entityData.get(SUPER); }

    void setSupercharge(int v) { entityData.set(SUPER, Math.max(0, v)); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidRodGoal(this));
        goalSelector.addGoal(2, new LeapGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(5, new PerchGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ rayos

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) { nearbyLightning(0); }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING) || super.isInvulnerableTo(source);
    }

    @Override
    public void nearbyLightning(double dist2) {
        if (dist2 > 6 * 6) return;
        playSound(FulgorMobs.CATCHER_CHARGE.get(), 1.5F, 1.0F);
        heal(10.0F);
        setSupercharge(SUPERCHARGE_TICKS);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.addOrUpdateTransientModifier(new AttributeModifier(DAMAGE_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        int s = supercharge();
        if (level().isClientSide) {
            if (s > 0 && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + random.nextGaussian() * 0.08, getY() + getBbHeight() + 0.15,
                        getZ() + random.nextGaussian() * 0.08, 0, 0.04, 0);
            }
            return;
        }
        if (s > 0) {
            setSupercharge(s - 1);
            if (s == 1) {
                var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
                if (dmg != null) dmg.removeModifier(DAMAGE_ID);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && supercharge() > 0 && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.4, 0.3, 0.15);
        return ok;
    }

    // ------------------------------------------------------------------ comportamiento

    /** Se aleja de cualquier pararrayos a menos de 16 bloques (aunque esté persiguiendo a alguien). */
    static class AvoidRodGoal extends Goal {
        private final SpireCatcher mob;
        private int check;
        private Vec3 away;

        AvoidRodGoal(SpireCatcher mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (--check > 0 || !(mob.level() instanceof ServerLevel sl)) return false;
            check = 20;
            BlockPos rod = FulgorMobs.nearestRod(sl, mob.blockPosition(), 16).orElse(null);
            if (rod == null) return false;
            away = DefaultRandomPos.getPosAway(mob, 20, 7, Vec3.atCenterOf(rod));
            return away != null;
        }

        @Override
        public void start() { mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3); }

        @Override
        public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }
    }

    /** Salto largo hacia la presa cuando está entre 3 y 9 bloques. */
    static class LeapGoal extends Goal {
        private final SpireCatcher mob;
        private int cooldown;

        LeapGoal(SpireCatcher mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) { cooldown--; return false; }
            LivingEntity t = mob.getTarget();
            if (t == null || !mob.onGround()) return false;
            double d2 = mob.distanceToSqr(t);
            return d2 > 9 && d2 < 81 && mob.getRandom().nextInt(10) == 0 && mob.hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() { return !mob.onGround(); }

        @Override
        public void start() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            Vec3 d = t.position().subtract(mob.position());
            Vec3 h = new Vec3(d.x, 0, d.z);
            double len = h.length();
            Vec3 v = len < 1e-3 ? Vec3.ZERO : h.scale(Math.min(1.1, 0.16 * len) / len);
            mob.setDeltaMovement(v.x, 0.55 + Math.max(0, d.y) * 0.06, v.z);
            mob.getLookControl().setLookAt(t);
            mob.playSound(FulgorMobs.CATCHER_LEAP.get(), 1.0F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
            cooldown = 60;
        }
    }

    /** Sin presa: busca lo más alto a menos de 12 bloques, sube y se queda allí un rato con la antena al cielo. */
    static class PerchGoal extends Goal {
        private final SpireCatcher mob;
        private BlockPos spot;
        private int stay;

        PerchGoal(SpireCatcher mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || mob.getRandom().nextInt(160) != 0) return false;
            Level l = mob.level();
            BlockPos best = null;
            for (int i = 0; i < 14; i++) {
                int x = mob.getBlockX() + mob.getRandom().nextInt(25) - 12;
                int z = mob.getBlockZ() + mob.getRandom().nextInt(25) - 12;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (best == null || y > best.getY()) best = new BlockPos(x, y, z);
            }
            if (best == null || best.getY() < mob.getBlockY() + 2) return false;
            spot = best;
            return true;
        }

        @Override
        public void start() {
            stay = 300 + mob.getRandom().nextInt(300);
            mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() { return mob.getTarget() == null && --stay > 0; }

        @Override
        public void tick() {
            if (mob.getNavigation().isDone() && mob.blockPosition().distSqr(spot) > 4 && stay % 40 == 0)
                mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }

        @Override
        public void stop() { mob.getNavigation().stop(); }
    }

    // ------------------------------------------------------------------ guardar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Supercharge", supercharge());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSupercharge(tag.getInt("Supercharge"));
        if (supercharge() > 0) {
            var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.addOrUpdateTransientModifier(new AttributeModifier(DAMAGE_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    public int getAmbientSoundInterval() { return 160; }

    @Override
    protected SoundEvent getAmbientSound() { return FulgorMobs.CATCHER_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return FulgorMobs.CATCHER_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return FulgorMobs.CATCHER_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.IRON_GOLEM_STEP, 0.25F, 1.4F); }
}
