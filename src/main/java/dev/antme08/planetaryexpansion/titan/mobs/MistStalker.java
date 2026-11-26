package dev.antme08.planetaryexpansion.titan.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Acechador de bruma (1.0.4): monstruo de las tierras altas de hielo de Titán. Lo atrae la luz (lo contrario que el
 * acechador de Cerúlea): busca las zonas iluminadas (antorchas, lámparas, la nave) y da vueltas alrededor; si hay un
 * jugador con una luz en la mano o en un sitio iluminado, lo rodea un rato y se lanza de lado a morder. El mordisco
 * congela y ralentiza. En el cliente casi no se ve de lejos (se funde con la bruma): solo su linterna.
 */
public class MistStalker extends Monster {
    private static final EntityDataAccessor<Boolean> CIRCLING = SynchedEntityData.defineId(MistStalker.class, EntityDataSerializers.BOOLEAN);

    /** Luz que está rondando (sin presa). */
    BlockPos light;
    int lightUntil;

    public MistStalker(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CIRCLING, false);
    }

    /** Para la animación: está rondando una luz o a su presa. */
    public boolean isCircling() { return entityData.get(CIRCLING); }

    void setCircling(boolean v) { if (isCircling() != v) entityData.set(CIRCLING, v); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new StalkGoal(this));
        goalSelector.addGoal(3, new SeekLightGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> lit(e) || e.distanceToSqr(this) < 25));
    }

    /** ¿Lleva luz o está en un sitio iluminado? (o está muy cerca: a 5 bloques lo nota igual) */
    static boolean lit(LivingEntity e) {
        if (holdsLight(e.getMainHandItem()) || holdsLight(e.getOffhandItem())) return true;
        Level l = e.level();
        return l.getBrightness(LightLayer.BLOCK, e.blockPosition()) >= 8 || l.getBrightness(LightLayer.BLOCK, e.blockPosition().above()) >= 8;
    }

    static boolean holdsLight(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission() > 0;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le) {
            IceDaggerItem.chill(le, 160, 60);
            playSound(TitanMobs.STALKER_ATTACK.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        }
        return ok;
    }

    // ------------------------------------------------------------------ cazar: rodear y lanzarse de lado

    /**
     * Con presa: se acerca a unos 6 bloques y la rodea (entre 2 y 4 s), luego se lanza a morder y, tras el mordisco,
     * vuelve a alejarse y a rodearla.
     */
    static class StalkGoal extends Goal {
        private final MistStalker mob;
        private int phaseTicks;
        private boolean dashing;
        private double angle;
        private int dir = 1;
        private int attackCooldown;

        StalkGoal(MistStalker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public void start() {
            LivingEntity t = mob.getTarget();
            angle = Math.atan2(mob.getZ() - t.getZ(), mob.getX() - t.getX());
            dir = mob.getRandom().nextBoolean() ? 1 : -1;
            dashing = false;
            phaseTicks = 40 + mob.getRandom().nextInt(40);
        }

        @Override
        public void stop() {
            mob.setCircling(false);
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30F, 30F);
            attackCooldown = Math.max(0, attackCooldown - 1);
            double d2 = mob.distanceToSqr(t);
            if (dashing) {
                mob.setCircling(false);
                mob.getNavigation().moveTo(t, 1.7);
                double reach = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + t.getBbWidth();
                if (d2 <= reach + 1.5 && attackCooldown == 0) {
                    mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    mob.doHurtTarget(t);
                    attackCooldown = 20;
                    dashing = false;
                    phaseTicks = 40 + mob.getRandom().nextInt(40);
                    dir = -dir;
                } else if (--phaseTicks < -60) {          // no llega: vuelve a rodear
                    dashing = false;
                    phaseTicks = 40;
                }
                return;
            }
            mob.setCircling(true);
            if (d2 > 14 * 14) {                           // lejos: acercarse
                mob.getNavigation().moveTo(t, 1.1);
                return;
            }
            angle += dir * 0.05;
            double r = 6.0;
            double x = t.getX() + Math.cos(angle) * r, z = t.getZ() + Math.sin(angle) * r;
            if (mob.tickCount % 5 == 0) mob.getNavigation().moveTo(x, t.getY(), z, 1.15);
            if (mob.tickCount % 80 == 0) mob.playSound(TitanMobs.STALKER_LURE.get(), 0.8F, 1.0F);
            if (--phaseTicks <= 0) {
                dashing = true;
                phaseTicks = 0;
            }
        }
    }

    // ------------------------------------------------------------------ sin presa: buscar la luz y rondarla

    static class SeekLightGoal extends Goal {
        private final MistStalker mob;
        private double angle;
        private int scan;

        SeekLightGoal(MistStalker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null) return false;
            if (mob.light != null && mob.tickCount < mob.lightUntil) return true;
            if (--scan > 0) return false;
            scan = 40;
            mob.light = findLight();
            mob.lightUntil = mob.tickCount + 300;
            return mob.light != null;
        }

        /** Muestrea puntos cercanos y se queda con el más iluminado (luz de bloques ≥ 10). */
        private BlockPos findLight() {
            Level l = mob.level();
            BlockPos best = null;
            int bestLight = 9;
            BlockPos at = mob.blockPosition();
            for (int i = 0; i < 24; i++) {
                BlockPos p = at.offset(mob.getRandom().nextInt(41) - 20, mob.getRandom().nextInt(13) - 6, mob.getRandom().nextInt(41) - 20);
                int b = l.getBrightness(LightLayer.BLOCK, p);
                if (b > bestLight && l.isEmptyBlock(p)) {
                    bestLight = b;
                    best = p;
                }
            }
            return best;
        }

        @Override
        public void start() {
            angle = mob.getRandom().nextDouble() * Math.PI * 2;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && mob.light != null && mob.tickCount < mob.lightUntil;
        }

        @Override
        public void stop() {
            mob.setCircling(false);
            mob.light = null;
        }

        @Override
        public void tick() {
            BlockPos c = mob.light;
            if (c == null) return;
            mob.setCircling(true);
            angle += 0.03;
            double r = 5.0;
            if (mob.tickCount % 10 == 0)
                mob.getNavigation().moveTo(c.getX() + 0.5 + Math.cos(angle) * r, c.getY(), c.getZ() + 0.5 + Math.sin(angle) * r, 0.9);
            if (mob.tickCount % 100 == 0) mob.playSound(TitanMobs.STALKER_LURE.get(), 0.7F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ varios

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return super.causeFallDamage(Math.max(0, distance - 4), multiplier, source);   // patas largas
    }

    @Override
    public boolean canFreeze() { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return TitanMobs.STALKER_AMBIENT.get(); }

    @Override
    public int getAmbientSoundInterval() { return 160; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return TitanMobs.STALKER_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return TitanMobs.STALKER_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SPIDER_STEP, 0.15F, 0.7F); }
}
