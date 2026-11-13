package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acechador abisal (0.7.61): depredador ciego de lo más hondo de Thalassa (por debajo de y −150). Caza por
 * vibraciones, como el Warden: le atraen nadar rápido, minar y pelear. Si te agachas o te quedas quieto no te detecta,
 * y si estás en silencio unos 5 s se olvida de ti. Mientras caza, sus barbillas brillan más (lo dibuja el cliente).
 */
public class AbyssalStalker extends Monster {
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(AbyssalStalker.class, EntityDataSerializers.BOOLEAN);
    /** Último ruido "fuerte" (minar, golpear) de cada jugador, en ticks del juego. Lo rellenan los eventos de Creatures. */
    static final Map<UUID, Long> LOUD = new ConcurrentHashMap<>();
    private static final double HEAR_RANGE = 32.0;
    private static final int FORGET_TICKS = 100;

    private int quietTicks;

    public AbyssalStalker(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 20;
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.5F, false);
        lookControl = new SmoothSwimmingLookControl(this, 10);
        setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0).add(Attributes.ARMOR, 4.0).add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 1.1).add(Attributes.FOLLOW_RANGE, HEAR_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HUNTING, false);
    }

    public boolean isHunting() { return entityData.get(HUNTING); }

    @Override
    protected PathNavigation createNavigation(Level level) { return new WaterBoundPathNavigation(this, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.8, 40));
    }

    /** ¿Hace ruido este jugador? (en silencio si va agachado o casi quieto y no ha minado ni golpeado hace poco) */
    public static boolean noisy(Player p, long now) {
        if (p.isCreative() || p.isSpectator()) return false;
        Long loud = LOUD.get(p.getUUID());
        if (loud != null && now - loud < 40) return true;
        if (p.isShiftKeyDown()) return false;
        return p.getDeltaMovement().length() > 0.13;
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
                if (d < best && p.isAlive() && noisy(p, now)) { best = d; heard = p; }
            }
            if (heard != null) {
                if (target != heard) playSound(SoundEvents.WARDEN_ANGRY, 1.5F, 0.6F);
                setTarget(heard);
                quietTicks = 0;
            } else if (target != null) {
                // de cerca (a 3 bloques) lo nota aunque estés quieto
                if (target.distanceToSqr(this) > 9.0) quietTicks += 5;
                if (quietTicks >= FORGET_TICKS || !target.isAlive()) { setTarget(null); quietTicks = 0; }
            }
        }
        entityData.set(HUNTING, getTarget() != null);
        if (!isInWaterOrBubble() && tickCount % 20 == 0) hurt(damageSources().dryOut(), 2.0F);
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
    protected SoundEvent getAmbientSound() { return SoundEvents.GUARDIAN_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.GUARDIAN_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.GUARDIAN_DEATH; }

    @Override
    public float getVoicePitch() { return 0.5F; }

    @Override
    public int getAmbientSoundInterval() { return 240; }

    static boolean isDeep(ServerLevel level, double y) { return y < -150; }
}
