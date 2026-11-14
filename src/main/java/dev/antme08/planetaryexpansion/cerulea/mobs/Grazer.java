package dev.antme08.planetaryexpansion.cerulea.mobs;

import dev.antme08.planetaryexpansion.cerulea.CeruleaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.EnumSet;

/**
 * Pastoreador de Cerúlea (1.0.3): herbívoro de manada de seis patas de las praderas. Pasta (se come el pasto
 * cerúleo y deja tierra, como las ovejas), sale en estampida con toda la manada si atacas a uno o si ve corredores, se
 * cría con raíz cerúlea y te sigue si la llevas en la mano. Las bolitas de sus antenas brillan de noche (cliente).
 */
public class Grazer extends Animal {
    private int eatTicks;

    public Grazer(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 18.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.8));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Runner.class, 10.0F, 1.4, 1.8, e -> !((Runner) e).isTame()));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, this::isFood, false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new GrazeGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) { return stack.is(CeruleaBlocks.CERULEAN_ROOT.get()); }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return CeruleaMobs.GRAZER.get().create(level); }

    /** Estampida: si atacan a uno, toda la manada cercana huye. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            for (Grazer g : level().getEntitiesOfClass(Grazer.class, getBoundingBox().inflate(14.0), g -> g != this))
                g.setLastHurtByMob(attacker);
        }
        return r;
    }

    // ------------------------------------------------------------------ pastar

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 10) eatTicks = 40;
        else super.handleEntityEvent(id);
    }

    @Override
    public void aiStep() {
        if (level().isClientSide) eatTicks = Math.max(0, eatTicks - 1);
        super.aiStep();
    }

    /** 0..1: cuánto baja la cabeza al pastar (para la animación). */
    public float headDown(float partial) {
        if (eatTicks <= 0) return 0F;
        float t = eatTicks - partial;
        if (t > 36F || t < 4F) return Mth.clamp((t < 4F ? t : 40F - t) / 4F, 0F, 1F);
        return 1F;
    }

    static class GrazeGoal extends Goal {
        private final Grazer mob;
        private int ticks;

        GrazeGoal(Grazer mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private static boolean edible(BlockState s) {
            return s.is(CeruleaBlocks.SHORT_GRASS.get()) || s.is(CeruleaBlocks.TALL_GRASS.get());
        }

        private static boolean grassBlock(BlockState s) {
            return s.is(CeruleaBlocks.GRASS_BLOCK.get()) || s.is(CeruleaBlocks.MEADOW_GRASS_BLOCK.get());
        }

        @Override
        public boolean canUse() {
            if (mob.getRandom().nextInt(mob.isBaby() ? 50 : 700) != 0) return false;
            BlockPos p = mob.blockPosition();
            return edible(mob.level().getBlockState(p)) || grassBlock(mob.level().getBlockState(p.below()));
        }

        @Override
        public void start() {
            ticks = adjustedTickDelay(40);
            mob.level().broadcastEntityEvent(mob, (byte) 10);
            mob.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() { return ticks > 0; }

        @Override
        public void tick() {
            ticks = Math.max(0, ticks - 1);
            if (ticks != adjustedTickDelay(4)) return;
            Level level = mob.level();
            BlockPos p = mob.blockPosition();
            boolean griefing = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
            BlockState here = level.getBlockState(p);
            if (edible(here)) {
                if (griefing) level.destroyBlock(p, false);
                mob.ate();
            } else if (grassBlock(level.getBlockState(p.below()))) {
                if (griefing) {
                    level.levelEvent(2001, p.below(), Block.getId(level.getBlockState(p.below())));
                    level.setBlock(p.below(), CeruleaBlocks.SOIL.get().defaultBlockState(), 2);
                }
                mob.ate();
            }
        }
    }

    @Override
    public void ate() {
        super.ate();
        if (isBaby()) ageUp(60);
        gameEvent(GameEvent.EAT);
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    protected SoundEvent getAmbientSound() { return CeruleaSounds.GRAZER_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return CeruleaSounds.GRAZER_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return CeruleaSounds.GRAZER_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.COW_STEP, 0.15F, 0.9F); }
}
