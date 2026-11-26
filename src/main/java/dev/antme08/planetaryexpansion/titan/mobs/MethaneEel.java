package dev.antme08.planetaryexpansion.titan.mobs;

import dev.antme08.planetaryexpansion.titan.TitanBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Anguila de metano (1.0.4): nada en cardúmenes pequeños en los lagos de metano de Titán. "Nadar" aquí es en el metano
 * (los peces de Minecraft solo saben nadar en agua), así que se mueve sola: elige un punto cercano dentro del metano y va
 * hacia él, se acerca a las demás anguilas y huye de los jugadores que se acercan sin agacharse. Fuera del metano da
 * coletazos y al rato se muere, como un pez fuera del agua.
 */
public class MethaneEel extends PathfinderMob {
    private static final int DRY_TICKS = 20 * 15;

    private Vec3 target;
    private int retarget;
    private int dry;

    public MethaneEel(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.6);
    }

    public boolean inMethane() {
        return isInFluidType(TitanBlocks.METHANE_TYPE.get());
    }

    private boolean methaneAt(Vec3 p) {
        BlockPos b = BlockPos.containing(p);
        return TitanMobs.isMethane(level(), b);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        boolean wet = inMethane();
        setNoGravity(wet);
        if (wet) {
            dry = 0;
            swim();
        } else {
            target = null;
            if (onGround() && random.nextInt(8) == 0) {   // coletazos
                setDeltaMovement(getDeltaMovement().add((random.nextFloat() - 0.5) * 0.2, 0.35, (random.nextFloat() - 0.5) * 0.2));
                setYRot(random.nextFloat() * 360F);
                playSound(SoundEvents.SALMON_FLOP, 0.6F, 0.8F);
            }
            if (++dry > DRY_TICKS && tickCount % 20 == 0) hurt(damageSources().dryOut(), 1.0F);
        }
    }

    private void swim() {
        Player threat = level().getNearestPlayer(this, 7.0);
        if (threat != null && (threat.isCreative() || threat.isSpectator() || threat.isSteppingCarefully())) threat = null;
        if (threat != null) {
            Vec3 away = position().subtract(threat.position()).normalize();
            Vec3 p = position().add(away.scale(6.0)).add(0, (random.nextFloat() - 0.5) * 2, 0);
            if (methaneAt(p)) target = p;
            retarget = 10;
        }
        if (target == null || --retarget <= 0 || position().distanceToSqr(target) < 1.0) pickTarget();
        if (target == null) {
            setDeltaMovement(getDeltaMovement().scale(0.9));
            return;
        }
        Vec3 dir = target.subtract(position());
        double len = dir.length();
        if (len < 1e-3) return;
        double speed = threat != null ? 0.16 : 0.07;
        Vec3 want = dir.scale(speed / len);
        setDeltaMovement(getDeltaMovement().scale(0.75).add(want.scale(0.25)).add(0, 0, 0));
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1e-5) {
            float yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12F));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        setXRot((float) (-Mth.atan2(v.y, Math.sqrt(v.horizontalDistanceSqr())) * Mth.RAD_TO_DEG));
    }

    /** Punto nuevo dentro del metano; con otras anguilas cerca, hacia el centro del cardumen. */
    private void pickTarget() {
        retarget = 60 + random.nextInt(60);
        List<MethaneEel> school = level().getEntitiesOfClass(MethaneEel.class, getBoundingBox().inflate(8.0), e -> e != this);
        Vec3 bias = Vec3.ZERO;
        if (!school.isEmpty()) {
            Vec3 c = Vec3.ZERO;
            for (MethaneEel e : school) c = c.add(e.position());
            bias = c.scale(1.0 / school.size()).subtract(position()).scale(0.5);
        }
        for (int i = 0; i < 12; i++) {
            Vec3 p = position().add(bias).add((random.nextFloat() - 0.5) * 14, (random.nextFloat() - 0.5) * 5, (random.nextFloat() - 0.5) * 14);
            if (methaneAt(p) && methaneAt(p.add(0, 0.6, 0))) {
                target = p;
                return;
            }
        }
        target = null;
    }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return !hasCustomName(); }

    @Override
    public int getMaxSpawnClusterSize() { return 4; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Dry", dry);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dry = tag.getInt("Dry");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SALMON_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.SALMON_DEATH; }

    @Override
    protected SoundEvent getSwimSound() { return SoundEvents.FISH_SWIM; }
}
