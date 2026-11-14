package dev.antme08.planetaryexpansion.cerulea.mobs;

import dev.antme08.planetaryexpansion.creatures.AbyssalStalker;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ironjaw (1.0.3, idea del usuario: "como el dunkleosteus pero más alienígena"): depredador acorazado del mar hondo de
 * Cerúlea.
 * <ul>
 *   <li>Lo atrae el ruido: nadar rápido, minar o pelear en el agua (igual que el acechador abisal de Thalassa) hasta a
 *   32 bloques. Si te metes en el agua cerca de él (12 bloques) también te persigue aunque vayas en silencio.</li>
 *   <li>Embiste: se aleja, da la vuelta y se lanza a toda velocidad con la boca abierta; si te alcanza muerde fuerte y
 *   sigue de largo, luego vuelve a dar la vuelta. También caza peces de cinta.</li>
 *   <li>La coraza de delante recibe poco daño (40 %); por los lados y por detrás es piel.</li>
 *   <li>No teme a la luz. Fuera del agua se ahoga poco a poco.</li>
 * </ul>
 */
public class Ironjaw extends Monster {
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(Ironjaw.class, EntityDataSerializers.BOOLEAN);
    private static final double HEAR_RANGE = 32.0;
    private static final double TERRITORY = 12.0;
    private int quietTicks;
    /** Cliente: apertura de la boca 0..1. */
    private float mouth, mouthO;

    public Ironjaw(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 25;
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.5F, false);
        lookControl = new SmoothSwimmingLookControl(this, 10);
        setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0).add(Attributes.ARMOR, 6.0).add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.MOVEMENT_SPEED, 1.2).add(Attributes.FOLLOW_RANGE, HEAR_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING, false);
    }

    public boolean isCharging() { return entityData.get(CHARGING); }

    void setCharging(boolean b) { entityData.set(CHARGING, b); }

    public float mouth(float partial) { return Mth.lerp(partial, mouthO, mouth); }

    @Override
    protected PathNavigation createNavigation(Level level) { return new WaterBoundPathNavigation(this, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ChargeGoal(this));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.7, 60));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Ribbonfin.class, 200, true, false, null));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            mouthO = mouth;
            mouth = Mth.approach(mouth, isCharging() ? 1F : 0F, isCharging() ? 0.12F : 0.06F);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        long now = level().getGameTime();
        LivingEntity target = getTarget();
        if (tickCount % 5 == 0 && isInWater()) {
            Player heard = null;
            double best = HEAR_RANGE * HEAR_RANGE;
            for (Player p : level().players()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator() || !p.isInWater()) continue;
                double d = p.distanceToSqr(this);
                boolean near = d < TERRITORY * TERRITORY;
                if (d < best && (near || AbyssalStalker.noisy(p, now))) {
                    best = d;
                    heard = p;
                }
            }
            if (heard != null) {
                if (!(target instanceof Player)) playSound(CeruleaSounds.IRONJAW_CHARGE.get(), 2.0F, 0.8F);
                setTarget(heard);
                quietTicks = 0;
            } else if (target instanceof Player p) {
                if (!p.isInWater() || p.isCreative()) quietTicks += 5;
                if (target.distanceToSqr(this) > TERRITORY * TERRITORY) quietTicks += 5;
                if (quietTicks >= 160 || !target.isAlive()) {
                    setTarget(null);
                    quietTicks = 0;
                }
            }
        }
        if (!isInWaterOrBubble() && tickCount % 20 == 0) hurt(damageSources().dryOut(), 2.0F);
    }

    /** La coraza de delante: un golpe que llega de frente hace solo el 40 %. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        Vec3 from = source.getSourcePosition();
        if (from != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            Vec3 look = Vec3.directionFromRotation(0, getYRot());
            Vec3 to = from.subtract(position());
            to = new Vec3(to.x, 0, to.z);
            if (to.lengthSqr() > 1e-4 && look.dot(to.normalize()) > 0.5) {
                amount *= 0.4F;
                playSound(SoundEvents.ANVIL_LAND, 0.5F, 1.6F);
            }
        }
        return super.hurt(source, amount);
    }

    /**
     * Embestida: se aleja hasta 10–14 bloques, da la vuelta y se lanza recto con la boca abierta (≈1 bloque por tick);
     * muerde al chocar y sigue de largo unos ticks; luego repite. Fuera del agua no embiste.
     */
    static class ChargeGoal extends Goal {
        private enum Phase { RETREAT, AIM, CHARGE, PASS }

        private final Ironjaw mob;
        private Phase phase;
        private Vec3 dir;
        private Vec3 retreatTo;
        private int timer;
        private boolean hit;

        ChargeGoal(Ironjaw mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.isInWater();
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void start() {
            phase = mob.distanceTo(mob.getTarget()) < 9 ? Phase.RETREAT : Phase.AIM;
            timer = 0;
            retreatTo = null;
        }

        @Override
        public void stop() {
            mob.setCharging(false);
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            timer++;
            switch (phase) {
                case RETREAT -> {
                    if (retreatTo == null) {
                        Vec3 away = mob.position().subtract(t.position());
                        away = new Vec3(away.x, 0, away.z);
                        if (away.lengthSqr() < 1e-3) away = new Vec3(1, 0, 0);
                        double a = (mob.random.nextDouble() - 0.5) * 1.6;   // de lado, para dar la vuelta en curva
                        away = away.normalize().yRot((float) a).scale(12);
                        retreatTo = t.position().add(away).add(0, (mob.random.nextDouble() - 0.5) * 4, 0);
                    }
                    mob.getNavigation().moveTo(retreatTo.x, retreatTo.y, retreatTo.z, 1.0);
                    if (mob.position().distanceToSqr(retreatTo) < 4.0 || timer > 100) {
                        phase = Phase.AIM;
                        timer = 0;
                    }
                }
                case AIM -> {
                    mob.getNavigation().stop();
                    mob.getLookControl().setLookAt(t, 30F, 30F);
                    turnTowards(t.position());
                    if (timer > 15) {
                        dir = t.getEyePosition().subtract(mob.position().add(0, mob.getBbHeight() / 2, 0)).normalize();
                        mob.setCharging(true);
                        mob.playSound(CeruleaSounds.IRONJAW_CHARGE.get(), 2.0F, 1.0F);
                        phase = Phase.CHARGE;
                        timer = 0;
                        hit = false;
                    }
                }
                case CHARGE -> {
                    // corrige un poco el rumbo hacia la presa (no gira en seco)
                    Vec3 want = t.position().add(0, t.getBbHeight() / 2, 0).subtract(mob.position().add(0, mob.getBbHeight() / 2, 0)).normalize();
                    dir = dir.scale(0.85).add(want.scale(0.15)).normalize();
                    mob.setDeltaMovement(dir.scale(0.9));
                    turnTowards(mob.position().add(dir));
                    if (!hit && mob.getBoundingBox().inflate(0.6).intersects(t.getBoundingBox())) {
                        mob.doHurtTarget(t);
                        t.knockback(1.2, -dir.x, -dir.z);
                        mob.playSound(CeruleaSounds.IRONJAW_BITE.get(), 1.6F, 1.0F);
                        hit = true;
                        phase = Phase.PASS;
                        timer = 0;
                    } else if (timer > 30 || mob.horizontalCollision) {
                        phase = Phase.PASS;
                        timer = 0;
                    }
                }
                case PASS -> {
                    if (timer < 12 && !mob.horizontalCollision) mob.setDeltaMovement(dir.scale(0.6));
                    if (timer == 12) mob.setCharging(false);
                    if (timer > 25) {
                        phase = Phase.RETREAT;
                        retreatTo = null;
                        timer = 0;
                    }
                }
            }
        }

        private void turnTowards(Vec3 p) {
            Vec3 d = p.subtract(mob.position());
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
            mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 12F));
            mob.yBodyRot = mob.getYRot();
            mob.yHeadRot = mob.getYRot();
            mob.setXRot(Mth.approachDegrees(mob.getXRot(), pitch, 8F));
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean r = super.doHurtTarget(target);
        if (r) playSound(CeruleaSounds.IRONJAW_BITE.get(), 1.4F, 1.0F);
        return r;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) { return level.isUnobstructed(this); }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public int getMaxHeadXRot() { return 1; }

    @Override
    public int getMaxHeadYRot() { return 1; }

    @Override
    protected SoundEvent getAmbientSound() { return CeruleaSounds.IRONJAW_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return CeruleaSounds.IRONJAW_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return CeruleaSounds.IRONJAW_DEATH.get(); }

    @Override
    public int getAmbientSoundInterval() { return 220; }

    @Override
    protected float getSoundVolume() { return 2.0F; }
}
