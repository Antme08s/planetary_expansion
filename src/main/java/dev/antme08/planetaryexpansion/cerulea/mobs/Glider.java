package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Planeador de Cerúlea (1.0.3): reptil del bosque gigante. Vive en las copas de los árboles plateados: posado pliega
 * las membranas y camina un poco; cada cierto tiempo salta y planea hasta la copa de otro árbol (baja poco a poco,
 * como una ardilla voladora), y si acaba en el suelo trepa por el tronco hasta arriba otra vez. Si te acercas (sin
 * agacharte) o lo golpeas, se lanza a planear lejos de ti. No ataca y no recibe daño por caída.
 */
public class Glider extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> GLIDING = SynchedEntityData.defineId(Glider.class, EntityDataSerializers.BOOLEAN);
    private static final double GLIDE_SPEED = 0.34;
    private static final double SINK = -0.11;

    private Vec3 glideTarget;
    private int glideTicks;
    private int glideCooldown = 100;
    private boolean onLeaves;
    /** Cliente: 0 plegado .. 1 abierto. */
    private float spread, spreadO;

    public Glider(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GLIDING, false);
    }

    public boolean isGliding() { return entityData.get(GLIDING); }

    private void setGliding(boolean g) { entityData.set(GLIDING, g); }

    @Override
    protected PathNavigation createNavigation(Level level) { return new WallClimberNavigation(this, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GlideGoal(this));
        goalSelector.addGoal(2, new ClimbTreeGoal(this));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6, 160));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Trepa (como las arañas) cuando no está en la copa y choca con un tronco u hojas. */
    @Override
    public boolean onClimbable() {
        return !onLeaves && !isGliding() && horizontalCollision;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spreadO = spread;
            spread = Mth.approach(spread, isGliding() ? 1F : 0F, 0.15F);
        }
    }

    public float spread(float partial) { return Mth.lerp(partial, spreadO, spread); }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (onGround()) onLeaves = level().getBlockState(blockPosition().below()).is(BlockTags.LEAVES)
                || level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ())).is(BlockTags.LEAVES);
        if (glideCooldown > 0) glideCooldown--;
        if (isGliding()) {
            glideTicks++;
            if ((onGround() && glideTicks > 5) || isInWater() || glideTicks > 400) {
                setGliding(false);
                glideTarget = null;
                glideCooldown = 200 + random.nextInt(400);
                return;
            }
            Vec3 dir = glideTarget == null ? Vec3.directionFromRotation(0, getYRot()) : glideTarget.subtract(position());
            Vec3 h = new Vec3(dir.x, 0, dir.z);
            if (h.lengthSqr() < 0.25 && glideTarget != null) h = Vec3.directionFromRotation(0, getYRot());
            h = h.normalize().scale(GLIDE_SPEED);
            Vec3 v = getDeltaMovement();
            setDeltaMovement(Mth.lerp(0.25, v.x, h.x), Math.max(v.y, SINK), Mth.lerp(0.25, v.z, h.z));
            float yaw = (float) (Mth.atan2(h.z, h.x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            fallDistance = 0;
        } else if (!onGround() && fallDistance > 2.0F && !isInWater()) {
            launch(null, false);   // se cae de una rama: abre las membranas y planea hacia delante
        }
    }

    /** Salta y empieza a planear hacia {@code target} (o hacia delante). */
    void launch(Vec3 target, boolean jump) {
        glideTarget = target;
        glideTicks = 0;
        setGliding(true);
        getNavigation().stop();
        Vec3 h = target == null ? Vec3.directionFromRotation(0, getYRot()) : target.subtract(position());
        h = new Vec3(h.x, 0, h.z).normalize().scale(GLIDE_SPEED);
        setDeltaMovement(h.x, jump ? 0.42 : getDeltaMovement().y, h.z);
        hasImpulse = true;
        playSound(CeruleaSounds.GLIDER_GLIDE.get(), 0.6F, 0.9F + random.nextFloat() * 0.2F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && !isGliding() && source.getEntity() instanceof LivingEntity attacker) {
            Vec3 t = GlideGoal.findPerch(this, attacker.position());
            launch(t, onGround());
        }
        return r;
    }

    /** Planear de árbol en árbol (y huir planeando del jugador). */
    static class GlideGoal extends Goal {
        private final Glider mob;

        GlideGoal(Glider mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        /** Una copa (hojas con aire encima) más baja que el planeador, a 8–24 bloques, lejos de {@code away}. */
        static Vec3 findPerch(Glider mob, Vec3 away) {
            Level level = mob.level();
            Vec3 best = null;
            double bestScore = -1e9;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 24; i++) {
                double a = mob.random.nextDouble() * Math.PI * 2, d = 8 + mob.random.nextDouble() * 16;
                int x = Mth.floor(mob.getX() + Math.cos(a) * d), z = Mth.floor(mob.getZ() + Math.sin(a) * d);
                for (int y = Mth.floor(mob.getY()) - 1; y > mob.getY() - 26; y--) {
                    p.set(x, y, z);
                    if (level.getBlockState(p).is(BlockTags.LEAVES)) {
                        if (level.isEmptyBlock(p.above()) && level.isEmptyBlock(p.above(2))) {
                            double drop = mob.getY() - (y + 1);
                            double score = -Math.abs(drop - d * 0.33) + (away == null ? 0 : Math.sqrt(
                                    (x - away.x) * (x - away.x) + (z - away.z) * (z - away.z)) * 0.6);
                            if (score > bestScore) {
                                bestScore = score;
                                best = new Vec3(x + 0.5, y + 1, z + 0.5);
                            }
                        }
                        break;
                    }
                    if (!level.isEmptyBlock(p)) break;
                }
            }
            if (best == null && away != null) {   // no hay copa: huye planeando en dirección contraria
                Vec3 dir = mob.position().subtract(away);
                best = mob.position().add(new Vec3(dir.x, 0, dir.z).normalize().scale(16));
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (mob.isGliding() || !mob.onGround() || !mob.onLeaves) return false;
            Player p = mob.level().getNearestPlayer(mob, 7.0);
            boolean scared = p != null && !p.isCrouching() && !p.isSpectator() && !p.isCreative();
            if (!scared && (mob.glideCooldown > 0 || mob.random.nextInt(200) != 0)) return false;
            Vec3 t = findPerch(mob, scared ? p.position() : null);
            if (t == null) return false;
            mob.launch(t, true);
            return false;   // el vuelo lo lleva aiStep
        }
    }

    /** En el suelo: va al tronco más cercano y trepa (onClimbable) hasta la copa. */
    static class ClimbTreeGoal extends Goal {
        private final Glider mob;
        private BlockPos log;
        private int ticks;

        private int noTreeUntil;

        ClimbTreeGoal(Glider mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.isGliding() || mob.onLeaves || !mob.onGround() || mob.random.nextInt(20) != 0) return false;
            // 1.0.4: sin árbol cerca no vuelve a buscar en 10 s (antes recorría 3125 bloques cada segundo)
            if (mob.tickCount < noTreeUntil) return false;
            BlockPos base = mob.blockPosition();
            // del más cercano al más lejano: se para en el primer tronco alto que encuentra
            log = BlockPos.findClosestMatch(base, 12, 4, q -> q.getY() >= base.getY() - 1 && q.getY() <= base.getY() + 3
                    && mob.level().getBlockState(q).is(BlockTags.LOGS) && mob.level().getBlockState(q.above(4)).is(BlockTags.LOGS))
                    .map(BlockPos::immutable).orElse(null);
            if (log == null) noTreeUntil = mob.tickCount + 200;
            return log != null;
        }

        @Override
        public void start() {
            ticks = 0;
            mob.getNavigation().moveTo(log.getX() + 0.5, log.getY(), log.getZ() + 0.5, 0.9);
        }

        @Override
        public boolean canContinueToUse() { return !mob.onLeaves && !mob.isGliding() && ticks < 600; }

        @Override
        public void tick() {
            ticks++;
            // pegado al tronco: sigue empujando hacia él para que el choque lo haga trepar
            double dx = log.getX() + 0.5 - mob.getX(), dz = log.getZ() + 0.5 - mob.getZ();
            if (dx * dx + dz * dz < 9.0) {
                mob.getMoveControl().setWantedPosition(log.getX() + 0.5, mob.getY() + 1, log.getZ() + 0.5, 0.9);
            } else if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(log.getX() + 0.5, log.getY(), log.getZ() + 0.5, 0.9);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("OnLeaves", onLeaves);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        onLeaves = tag.getBoolean("OnLeaves");
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return CeruleaSounds.GLIDER_AMBIENT.get(); }

    @Override
    public int getAmbientSoundInterval() { return 240; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return CeruleaSounds.GLIDER_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return CeruleaSounds.GLIDER_DEATH.get(); }

}
