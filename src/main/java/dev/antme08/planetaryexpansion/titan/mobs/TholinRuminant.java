package dev.antme08.planetaryexpansion.titan.mobs;

import dev.antme08.planetaryexpansion.titan.TitanBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Rumiador de tolina (1.0.4): animal grande y lento de las dunas y las llanuras de Titán, en manadas. Si atacas a uno,
 * los adultos de la manada no huyen: se ponen entre el atacante y las crías y embisten al que se acerque (las crías
 * huyen). Se cría con bitumen y te sigue si lo llevas en la mano. De vez en cuando baja la cabeza a pastar la tolina.
 */
public class TholinRuminant extends Animal {
    private static final int ALERT_TICKS = 20 * 20;

    private int eatTicks;
    private LivingEntity threat;
    private int alertUntil;

    public TholinRuminant(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DefendHerdGoal(this));
        goalSelector.addGoal(2, new BabyPanicGoal(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.15, this::isFood, false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new GrazeGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) { return stack.is(TitanBlocks.BITUMEN.get().asItem()); }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return TitanMobs.RUMINANT.get().create(level); }

    /** Si atacan a uno, toda la manada (a 16 bloques) se pone en guardia contra el atacante. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            for (TholinRuminant g : level().getEntitiesOfClass(TholinRuminant.class, getBoundingBox().inflate(16.0))) {
                g.threat = attacker;
                g.alertUntil = g.tickCount + ALERT_TICKS;
                if (g.isBaby()) g.setLastHurtByMob(attacker);
            }
        }
        return r;
    }

    boolean alert() {
        return threat != null && threat.isAlive() && tickCount < alertUntil && distanceToSqr(threat) < 24 * 24
                && !(threat instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * Adultos en guardia: se colocan a unos bloques de las crías (o de la manada) en la dirección del atacante, y si el
     * atacante se acerca a menos de 3 bloques lo embisten (daño y empujón).
     */
    static class DefendHerdGoal extends Goal {
        private final TholinRuminant mob;
        private int cooldown;
        private int repath;

        DefendHerdGoal(TholinRuminant mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() { return !mob.isBaby() && mob.alert(); }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void stop() { mob.getNavigation().stop(); }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.threat;
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30F, 30F);
            cooldown = Math.max(0, cooldown - 1);
            double d2 = mob.distanceToSqr(t);
            if (d2 < 3.2 * 3.2) {
                mob.getNavigation().moveTo(t, 1.3);
                if (cooldown == 0 && d2 < 2.8 * 2.8) {
                    mob.swing(InteractionHand.MAIN_HAND);
                    if (mob.doHurtTarget(t)) {
                        Vec3 push = t.position().subtract(mob.position()).normalize();
                        t.knockback(1.2, -push.x, -push.z);
                    }
                    cooldown = 30;
                }
                return;
            }
            if (--repath > 0) return;
            repath = 10;
            // centro de lo que hay que proteger: las crías cercanas o, si no hay, la manada
            List<TholinRuminant> herd = mob.level().getEntitiesOfClass(TholinRuminant.class, mob.getBoundingBox().inflate(12.0));
            Vec3 center = Vec3.ZERO;
            int n = 0;
            for (TholinRuminant r : herd) if (r.isBaby()) { center = center.add(r.position()); n++; }
            if (n == 0) for (TholinRuminant r : herd) { center = center.add(r.position()); n++; }
            center = center.scale(1.0 / Math.max(1, n));
            Vec3 dir = t.position().subtract(center);
            double len = dir.length();
            Vec3 guard = len < 0.1 ? center : center.add(dir.scale(Math.min(3.0, len * 0.5) / len));
            // cada adulto un poco a un lado, para formar un arco delante de las crías
            double side = ((mob.getId() % 5) - 2) * 1.4;
            Vec3 perp = new Vec3(-dir.z, 0, dir.x).normalize().scale(side);
            Vec3 p = guard.add(perp);
            mob.getNavigation().moveTo(p.x, p.y, p.z, 1.2);
        }
    }

    /** Las crías huyen del atacante hacia los adultos. */
    static class BabyPanicGoal extends PanicGoal {
        BabyPanicGoal(TholinRuminant mob) { super(mob, 1.6); }

        @Override
        public boolean canUse() { return mob.isBaby() && super.canUse(); }
    }

    // ------------------------------------------------------------------ pastar (solo animación, no rompe el terreno)

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 10) eatTicks = 50;
        else super.handleEntityEvent(id);
    }

    @Override
    public void aiStep() {
        if (level().isClientSide) eatTicks = Math.max(0, eatTicks - 1);
        super.aiStep();
    }

    /** 0..1: cuánto baja la cabeza al pastar. */
    public float headDown(float partial) {
        if (eatTicks <= 0) return 0F;
        float t = eatTicks - partial;
        if (t > 44F || t < 6F) return Mth.clamp((t < 6F ? t : 50F - t) / 6F, 0F, 1F);
        return 1F;
    }

    static class GrazeGoal extends Goal {
        private final TholinRuminant mob;
        private int ticks;

        GrazeGoal(TholinRuminant mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (mob.getRandom().nextInt(mob.isBaby() ? 60 : 500) != 0) return false;
            BlockState below = mob.level().getBlockState(mob.blockPosition().below());
            return below.is(TitanBlocks.THOLIN_SAND.get()) || below.is(TitanBlocks.THOLIN_CRUST.get());
        }

        @Override
        public void start() {
            ticks = adjustedTickDelay(50);
            mob.level().broadcastEntityEvent(mob, (byte) 10);
            mob.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() { return ticks > 0; }

        @Override
        public void tick() {
            if (--ticks == adjustedTickDelay(6)) mob.ate();
        }
    }

    @Override
    public void ate() {
        super.ate();
        if (isBaby()) ageUp(60);
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    public int getAmbientSoundInterval() { return 240; }

    @Override
    protected float getSoundVolume() { return 1.4F; }

    @Override
    protected SoundEvent getAmbientSound() { return TitanMobs.RUMINANT_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return TitanMobs.RUMINANT_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return TitanMobs.RUMINANT_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.COW_STEP, 0.2F, 0.7F); }
}
