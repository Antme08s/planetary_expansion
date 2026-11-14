package dev.antme08.planetaryexpansion.ferra.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Gusano de óxido de Ferra (1.0.3, idea del usuario: "monstruos gusanos de tierra"; enemigo normal, no común, en las
 * zonas despejadas). Vive enterrado (invisible e intocable). Si nota pasos a menos de 20 bloques (caminar, correr, minar,
 * pelear; agachado o quieto no), avanza bajo el polvo hacia el jugador dejando un rastro de polvo y un temblor, y a
 * 2 bloques sale de golpe: muerde a todo lo que tenga encima y se queda fuera unos segundos mordiendo. Después (o si
 * deja de oír) se vuelve a enterrar.
 */
public class RustWorm extends Monster {
    public enum Phase { BURIED, EMERGING, OUT, DIVING }

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(RustWorm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(RustWorm.class, EntityDataSerializers.INT);
    public static final int EMERGE_TICKS = 14, DIVE_TICKS = 20;
    private static final double HEAR_RANGE = 20.0;
    private int attackCooldown, quietTicks, outTicks;
    /** Ya mordió en esta salida: se mete enseguida (el usuario: "que te golpee y se esconda"). */
    private boolean bit;
    /** Ticks enterrado antes de poder volver a salir; mientras tanto se aleja un poco por debajo. */
    private int hideTicks;
    /** Ticks fuera tras morder, antes de meterse (para que se vea el mordisco y se le pueda devolver el golpe). */
    private static final int AFTER_BITE_TICKS = 10;
    /** Si no consigue morder a su presa en 20 s se rinde (el usuario: "si no es capaz de alcanzarte por más de cierto
     *  tiempo, que se rinda y se vaya"): no le hace caso durante 60 s y se aleja bajo el polvo. */
    private static final int GIVE_UP_TICKS = 400, IGNORE_TICKS = 1200;
    private int frustration, leaveTicks;
    private LivingEntity chased;
    private java.util.UUID ignored;
    private long ignoredUntil;
    private Vec3 leaveFrom;

    public RustWorm(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 15;
        noCulling = true;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0).add(Attributes.ARMOR, 6.0).add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.FOLLOW_RANGE, HEAR_RANGE).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, Phase.BURIED.ordinal());
        builder.define(PHASE_TICK, 0);
    }

    public Phase phase() { return Phase.values()[entityData.get(PHASE)]; }

    /** Ticks en la fase actual (para la animación de salir y meterse). */
    public int phaseTick() { return entityData.get(PHASE_TICK); }

    private void setPhase(Phase p) {
        entityData.set(PHASE, p.ordinal());
        entityData.set(PHASE_TICK, 0);
        setInvisible(p == Phase.BURIED);
    }

    public boolean buried() { return phase() == Phase.BURIED; }

    @Override
    public boolean isPickable() { return !buried() && super.isPickable(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (buried() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected void registerGoals() {}   // lo maneja todo customServerAiStep

    private double lastX = Double.NaN, lastZ;

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
        if (!level().isClientSide) {
            entityData.set(PHASE_TICK, phaseTick() + 1);
        } else if (buried()) {
            // en el cliente: polvo que salta en el suelo mientras avanza enterrado
            if (!Double.isNaN(lastX) && (Math.abs(getX() - lastX) + Math.abs(getZ() - lastZ)) > 0.02) {
                BlockState below = level().getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ()));
                if (!below.isAir()) {
                    for (int i = 0; i < 4; i++) {
                        level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below),
                                getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 1.2,
                                (random.nextDouble() - 0.5) * 0.2, 0.15 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.2);
                    }
                }
            }
            lastX = getX();
            lastZ = getZ();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        long now = sl.getGameTime();
        LivingEntity target = getTarget();
        if (tickCount % 5 == 0) {
            Player heard = null;
            double best = HEAR_RANGE * HEAR_RANGE;
            for (Player p : sl.players()) {
                double d = p.distanceToSqr(this);
                if (now < ignoredUntil && p.getUUID().equals(ignored)) continue;   // ya lo dio por imposible
                if (d < best && p.isAlive() && FerraMobs.heard(p, now)) { best = d; heard = p; }
            }
            if (heard != null) { setTarget(heard); quietTicks = 0; }
            else if (target != null && (quietTicks += 5) > 100) { setTarget(null); quietTicks = 0; }
        }
        target = getTarget();
        if (target != chased) { chased = target; frustration = 0; }
        if (target != null && ++frustration > GIVE_UP_TICKS) { giveUp(target, now); target = null; }
        switch (phase()) {
            case BURIED -> {
                if (hideTicks > 0) hideTicks--;
                if (leaveTicks > 0) {
                    // se ha rendido: se aleja bajo el polvo (hasta unos 40 bloques de donde estaba la presa)
                    leaveTicks--;
                    Vec3 away = position().subtract(leaveFrom);
                    double d = Math.sqrt(away.x * away.x + away.z * away.z);
                    if (d < 40.0) burrow(sl, d < 1e-3 ? 1 : away.x / d, d < 1e-3 ? 0 : away.z / d, 0.3);
                    return;
                }
                if (target == null) return;
                Vec3 to = target.position().subtract(position());
                double dist = Math.sqrt(to.x * to.x + to.z * to.z);
                if (hideTicks > 0) {
                    // recién escondido: no sale aunque sigas encima; se aparta bajo el polvo y vuelve después
                    if (dist < 7.0) burrow(sl, dist < 1e-3 ? 1 : -to.x / dist, dist < 1e-3 ? 0 : -to.z / dist, 0.22);
                    return;
                }
                if (dist < 2.0) {
                    setPhase(Phase.EMERGING);
                    playSound(FerraSounds.WORM_EMERGE.get(), 2.0F, 0.95F + random.nextFloat() * 0.1F);
                    burst(sl, 60);
                    return;
                }
                burrow(sl, to.x / dist, to.z / dist, Math.min(0.32, dist));
            }
            case EMERGING -> {
                if (phaseTick() == EMERGE_TICKS / 2) {
                    // mordisco al salir: a lo que tenga encima
                    for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.2, 0.5, 1.2), x -> x != this)) {
                        if (doHurtTarget(e)) { e.push(0, 0.7, 0); bit = true; attackCooldown = 25; }
                    }
                }
                if (phaseTick() >= EMERGE_TICKS) { setPhase(Phase.OUT); outTicks = bit ? AFTER_BITE_TICKS : 60 + random.nextInt(61); }   // si falla: 3–6 s fuera (el usuario)
            }
            case OUT -> {
                if (target != null) {
                    getLookControl().setLookAt(target, 30, 30);
                    setYRot(Mth.approachDegrees(getYRot(), (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90F, 10F));
                    yBodyRot = getYRot();
                    if (--attackCooldown <= 0 && distanceToSqr(target) < 12.0) {
                        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        if (doHurtTarget(target)) { bit = true; outTicks = Math.min(outTicks, AFTER_BITE_TICKS); }
                        attackCooldown = 25;
                    }
                }
                if (--outTicks <= 0 || target == null || distanceToSqr(target) > 100) {
                    setPhase(Phase.DIVING);
                    playSound(FerraSounds.WORM_DIVE.get(), 1.4F, 1.0F);
                }
            }
            case DIVING -> {
                if (phaseTick() % 3 == 0) burst(sl, 10);
                if (phaseTick() >= DIVE_TICKS) { setPhase(Phase.BURIED); bit = false; hideTicks = 60 + random.nextInt(40); }
            }
        }
    }

    /** Avanza bajo el polvo, a ras del suelo, dejando rastro (el usuario: "debería soltar partículas por donde se está
     *  moviendo"); el cliente además echa sus propias partículas (ver tick). */
    private void burrow(ServerLevel sl, double dirX, double dirZ, double step) {
        double nx = getX() + dirX * step, nz = getZ() + dirZ * step;
        int top = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(nx), Mth.floor(nz));
        setPos(nx, top, nz);
        BlockState below = sl.getBlockState(BlockPos.containing(nx, top - 1, nz));
        if (!below.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), nx, top + 0.15, nz, 10, 0.45, 0.15, 0.45, 0.12);
            if (tickCount % 3 == 0)
                sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, below), nx, top + 0.05, nz, 6, 0.35, 0.0, 0.35, 0.1);
        }
        if (tickCount % 14 == 0) playSound(FerraSounds.WORM_DIG.get(), 1.3F, 0.9F + random.nextFloat() * 0.2F);
    }

    /** Se rinde con esta presa: gruñe, la ignora un rato y se va (si está fuera, se mete primero: ver OUT). */
    private void giveUp(LivingEntity target, long now) {
        ignored = target.getUUID();
        ignoredUntil = now + IGNORE_TICKS;
        leaveFrom = target.position();
        leaveTicks = 100 + random.nextInt(60);
        setTarget(null);
        chased = null;
        frustration = 0;
        playSound(FerraSounds.WORM_AMBIENT.get(), 1.6F, 0.7F);
    }

    private void burst(ServerLevel sl, int n) {
        BlockState below = sl.getBlockState(blockPosition().below());
        if (below.isAir()) return;
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.3, getZ(), n, 0.8, 0.5, 0.8, 0.15);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean r = super.doHurtTarget(target);
        if (r) frustration = 0;   // la ha alcanzado: no se rinde
        if (r) playSound(FerraSounds.WORM_BITE.get(), 1.2F, 0.9F + random.nextFloat() * 0.2F);
        return r;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WormPhase", phase().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPhase(Phase.BURIED);   // al cargar siempre vuelve enterrado
    }

    @Override
    public boolean removeWhenFarAway(double dist) { return buried() && super.removeWhenFarAway(dist); }

    @Override
    protected SoundEvent getAmbientSound() { return buried() ? null : FerraSounds.WORM_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return FerraSounds.WORM_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return FerraSounds.WORM_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) { return false; }
}
