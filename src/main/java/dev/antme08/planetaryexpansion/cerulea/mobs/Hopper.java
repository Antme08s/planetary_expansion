package dev.antme08.planetaryexpansion.cerulea.mobs;

import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Saltarín de Cerúlea (1.0.3): bichito de las praderas que siempre se mueve a saltos. Muy asustadizo: huye si te
 * acercas corriendo o si llegas muy cerca sin agacharte (agachado puedes acercarte). Le gustan las bayas azules: te
 * sigue si las llevas y se cría con ellas. Es presa de los corredores.
 */
public class Hopper extends Animal {
    private int hopCooldown;

    public Hopper(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 5.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.JUMP_STRENGTH, 0.36);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 2.0));
        goalSelector.addGoal(2, new BreedGoal(this, 0.9));
        goalSelector.addGoal(3, new TemptGoal(this, 1.0, this::isFood, false));
        goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.6, 2.0,
                p -> !p.isSpectator() && !((Player) p).isCreative() && !p.isCrouching()
                        && (p.isSprinting() || p.distanceToSqr(this) < 12.0)));
        goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Runner.class, 10.0F, 1.6, 2.0, e -> !((Runner) e).isTame()));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Se mueve a saltos: mientras camina, salta cada pocos ticks. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (hopCooldown > 0) hopCooldown--;
        Vec3 v = getDeltaMovement();
        boolean moving = !getNavigation().isDone() || v.horizontalDistanceSqr() > 0.0004;
        if (moving && onGround() && hopCooldown == 0 && !isInWater()) {
            jumpFromGround();
            playSound(SoundEvents.RABBIT_JUMP, 0.3F, 1.2F);
            hopCooldown = 6 + random.nextInt(5);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) { return stack.is(CeruleaBlocks.BLUEBERRIES.get()); }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return CeruleaMobs.HOPPER.get().create(level); }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.RABBIT_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RABBIT_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.RABBIT_DEATH; }
}
