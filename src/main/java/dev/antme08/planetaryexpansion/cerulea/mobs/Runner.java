package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Corredor de Cerúlea (1.0.3, idea del usuario): depredador de manada de praderas y bosques que caza como los lobos y
 * acecha como los zorros: se agacha, se acerca despacio, se queda quieto mirando a la presa y le salta encima.
 * <ul>
 *   <li>De día solo caza animales (pastoreadores, saltarines); si un jugador se le acerca mucho, le da un golpe de
 *   aviso y se aleja.</li>
 *   <li>De noche también caza jugadores.</li>
 *   <li>Le teme a la luz, como el acechador.</li>
 *   <li>Se doma solo de día: le tiras carne de pastoreador y te alejas; se acerca con cuidado y se la come. Tras varias
 *   veces confía en quien se la tiró. Domado sigue a su dueño, se sienta y ataca a sus enemigos; no suelta nada.</li>
 * </ul>
 */
public class Runner extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> STALKING = SynchedEntityData.defineId(Runner.class, EntityDataSerializers.BOOLEAN);

    private int trust;
    private int trustNeeded;
    private UUID trusted;

    public Runner(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        xpReward = 6;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 18.0).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STALKING, false);
    }

    public boolean isStalking() { return entityData.get(STALKING); }

    void setStalking(boolean b) { entityData.set(STALKING, b); }

    static boolean night(Level level) { return !level.isDay(); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new Predators.AvoidLightGoal(this, 1.4));
        goalSelector.addGoal(3, new WarnSwipeGoal(this));
        goalSelector.addGoal(4, new StalkPounceGoal(this));
        goalSelector.addGoal(5, new EatThrownFoodGoal(this));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.2, 10.0F, 2.0F));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NonTameRandomTargetGoal<>(this, Grazer.class, false, null));
        targetSelector.addGoal(4, new NonTameRandomTargetGoal<>(this, Hopper.class, false, null));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> !isTame() && night(level()) && !Predators.lit(p)));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 10 != 0) return;
        LivingEntity t = getTarget();
        // un jugador: de día solo si te atacó; nunca si tiene luz (salvo que lo haya atacado él)
        if (t instanceof Player p && !isTame() && getLastHurtByMob() != p && (!night(level()) || Predators.lit(p))) setTarget(null);
        if (t == null && isStalking()) setStalking(false);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean r = super.doHurtTarget(target);
        if (r) playSound(SoundEvents.WOLF_GROWL, 0.8F, 1.3F);
        return r;
    }

    // ------------------------------------------------------------------ acecho y salto

    /**
     * Acecho como los zorros: agachado se acerca despacio hasta unos 5 bloques, se queda quieto mirando un momento y
     * salta encima; después pelea cuerpo a cuerpo. Si la presa se aleja mucho, vuelve a acechar.
     */
    static class StalkPounceGoal extends Goal {
        private enum Phase { APPROACH, PAUSE, LEAP, FIGHT }

        private final Runner mob;
        private Phase phase;
        private int timer;
        private int attackCooldown;

        StalkPounceGoal(Runner mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.isOrderedToSit();
        }

        @Override
        public boolean canContinueToUse() { return canUse() && mob.distanceToSqr(mob.getTarget()) < 32 * 32; }

        @Override
        public void start() {
            phase = Phase.APPROACH;
            timer = 0;
        }

        @Override
        public void stop() {
            mob.setStalking(false);
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            double d2 = mob.distanceToSqr(t);
            if (attackCooldown > 0) attackCooldown--;
            switch (phase) {
                case APPROACH -> {
                    mob.setStalking(true);
                    mob.getLookControl().setLookAt(t, 30F, 30F);
                    if (d2 < 5.5 * 5.5) {
                        phase = Phase.PAUSE;
                        timer = 20 + mob.random.nextInt(30);   // en manada, cada uno salta en su momento
                        mob.getNavigation().stop();
                    } else if (mob.tickCount % 10 == 0) {
                        mob.getNavigation().moveTo(t, 0.6);
                    }
                }
                case PAUSE -> {
                    mob.getNavigation().stop();
                    mob.getLookControl().setLookAt(t, 60F, 60F);
                    if (d2 > 9 * 9) phase = Phase.APPROACH;
                    else if (--timer <= 0 && mob.onGround()) {
                        Vec3 dir = t.position().subtract(mob.position());
                        Vec3 h = new Vec3(dir.x, 0, dir.z);
                        double dist = h.length();
                        h = h.normalize().scale(Math.min(1.3, 0.25 + dist * 0.18));
                        mob.setDeltaMovement(h.x, 0.48, h.z);
                        mob.hasImpulse = true;
                        mob.setStalking(false);
                        mob.playSound(SoundEvents.WOLF_GROWL, 1.0F, 1.1F);
                        phase = Phase.LEAP;
                        timer = 0;
                    }
                }
                case LEAP -> {
                    timer++;
                    if (mob.getBoundingBox().inflate(0.4).intersects(t.getBoundingBox()) && attackCooldown == 0) {
                        mob.doHurtTarget(t);
                        attackCooldown = 20;
                    }
                    if (timer > 4 && mob.onGround()) phase = Phase.FIGHT;
                }
                case FIGHT -> {
                    mob.getLookControl().setLookAt(t, 30F, 30F);
                    if (d2 > 12 * 12) {
                        phase = Phase.APPROACH;
                        return;
                    }
                    if (mob.tickCount % 5 == 0) mob.getNavigation().moveTo(t, 1.4);
                    double reach = mob.getBbWidth() * 2.0 * mob.getBbWidth() * 2.0 + t.getBbWidth();
                    if (d2 <= reach + 1.0 && attackCooldown == 0) {
                        mob.swing(InteractionHand.MAIN_HAND);
                        mob.doHurtTarget(t);
                        attackCooldown = 20;
                    }
                }
            }
        }
    }

    /** De día, si un jugador se le acerca mucho: un golpe de aviso y se aleja. */
    static class WarnSwipeGoal extends Goal {
        private final Runner mob;
        private Player player;
        private int flee;
        private int cooldown;

        WarnSwipeGoal(Runner mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (mob.isTame() || night(mob.level()) || mob.getTarget() instanceof Player) return false;
            Player p = mob.level().getNearestPlayer(mob, 2.3);
            if (p == null || p.isCreative() || p.isSpectator()) return false;
            player = p;
            return true;
        }

        @Override
        public void start() {
            mob.getLookControl().setLookAt(player, 60F, 60F);
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(player);
            flee = 60;
            Vec3 away = DefaultRandomPos.getPosAway(mob, 12, 5, player.position());
            if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
        }

        @Override
        public boolean canContinueToUse() { return flee > 0; }

        @Override
        public void tick() {
            flee--;
            if (mob.getNavigation().isDone() && player != null) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 12, 5, player.position());
                if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
            }
        }

        @Override
        public void stop() {
            cooldown = 100;
            player = null;
        }
    }

    // ------------------------------------------------------------------ doma

    /**
     * De día, sin domar: si hay carne de pastoreador tirada en el suelo y nadie cerca de ella, se acerca con cuidado
     * (si un jugador se le acerca, se va) y se la come. Cada vez confía más en quien la tiró hasta que se deja domar.
     */
    static class EatThrownFoodGoal extends Goal {
        private final Runner mob;
        private ItemEntity food;
        private int ticks;

        EatThrownFoodGoal(Runner mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean nobodyNear(Vec3 pos, double r) {
            return mob.level().getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(r),
                    p -> !p.isSpectator()).isEmpty();
        }

        @Override
        public boolean canUse() {
            if (mob.isTame() || night(mob.level()) || mob.getTarget() != null || mob.tickCount % 10 != 0) return false;
            List<ItemEntity> items = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(14.0),
                    i -> isMeat(i.getItem()) && nobodyNear(i.position(), 4.5));
            if (items.isEmpty()) return false;
            food = items.get(0);
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            mob.getNavigation().moveTo(food, 0.75);
        }

        @Override
        public boolean canContinueToUse() {
            return food != null && food.isAlive() && ticks < 400 && !night(mob.level()) && mob.getTarget() == null
                    && nobodyNear(food.position(), 4.0) && nobodyNear(mob.position(), 3.5);
        }

        @Override
        public void tick() {
            ticks++;
            mob.getLookControl().setLookAt(food, 30F, 30F);
            if (mob.distanceToSqr(food) < 1.8 * 1.8) {
                eat();
                food = null;
            } else if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(food, 0.75);
            }
        }

        private void eat() {
            ItemStack stack = food.getItem();
            if (mob.level() instanceof ServerLevel sl) {
                sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)),
                        food.getX(), food.getY() + 0.2, food.getZ(), 8, 0.15, 0.1, 0.15, 0.05);
            }
            mob.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
            Entity thrower = food.getOwner();
            stack.shrink(1);
            if (stack.isEmpty()) food.discard();
            mob.heal(4.0F);
            if (!(thrower instanceof Player player)) return;
            if (mob.trusted == null || !mob.trusted.equals(player.getUUID())) {
                mob.trusted = player.getUUID();
                mob.trust = 0;
                mob.trustNeeded = 3 + mob.random.nextInt(3);
            }
            mob.trust++;
            if (mob.trust >= mob.trustNeeded && !net.neoforged.neoforge.event.EventHooks.onAnimalTame(mob, player)) {
                mob.tame(player);
                mob.setOrderedToSit(false);
                mob.getNavigation().stop();
                mob.level().broadcastEntityEvent(mob, (byte) 7);   // corazones
            } else if (mob.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.0, mob.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    static boolean isMeat(ItemStack stack) {
        return stack.is(CeruleaMobs.GRAZER_MEAT.get()) || stack.is(CeruleaMobs.COOKED_GRAZER_MEAT.get());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isTame() && isOwnedBy(player)) {
            if (isMeat(stack) && getHealth() < getMaxHealth()) {
                stack.consume(1, player);
                heal(6.0F);
                playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (stack.isEmpty()) {
                if (!level().isClientSide) {
                    setOrderedToSit(!isOrderedToSit());
                    jumping = false;
                    getNavigation().stop();
                    setTarget(null);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    /** Domado no suelta nada. */
    @Override
    protected boolean shouldDropLoot() { return !isTame() && super.shouldDropLoot(); }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Runner r && r.isTame() && r.getOwner() == owner) return false;
        if (target instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p)) return false;
        return !(target instanceof TamableAnimal t && t.isTame() && t.getOwner() == owner);
    }

    @Override
    public boolean isFood(ItemStack stack) { return false; }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Trust", trust);
        tag.putInt("TrustNeeded", trustNeeded);
        if (trusted != null) tag.putUUID("Trusted", trusted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        trust = tag.getInt("Trust");
        trustNeeded = tag.getInt("TrustNeeded");
        if (tag.hasUUID("Trusted")) trusted = tag.getUUID("Trusted");
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    protected SoundEvent getAmbientSound() {
        if (isTame()) return SoundEvents.WOLF_AMBIENT;
        return night(level()) ? SoundEvents.WOLF_GROWL : SoundEvents.WOLF_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOLF_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WOLF_DEATH; }

    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.85F; }

    /** Para la animación: 0..1 hacia la postura de acecho (agachado). */
    public float crouch(float partial) { return isStalking() ? 1F : 0F; }

    public float tailWag(float age) { return Mth.sin(age * (isTame() ? 0.5F : 0.15F)) * (isTame() ? 0.5F : 0.15F); }
}
