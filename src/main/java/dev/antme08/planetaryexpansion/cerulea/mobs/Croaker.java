package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Croador de Cerúlea (1.0.3): anfibio de los nenúfares del pantano. Salta de nenúfar en nenúfar y nada bien; de noche
 * infla el saco vocal y croa. Pacífico: si te acercas sin agacharte se mete al agua. Se atrapa con una cubeta de agua,
 * como los ajolotes.
 */
public class Croaker extends Animal implements Bucketable {
    private boolean fromBucket;
    private int hopCooldown;
    /** Cliente: ticks que le quedan al saco inflado. */
    private int croakTicks;

    public Croaker(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.JUMP_STRENGTH, 0.5);
    }

    @Override
    protected PathNavigation createNavigation(Level level) { return new AmphibiousPathNavigation(this, level); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.2, 1.5,
                p -> !p.isSpectator() && !((Player) p).isCreative() && !p.isCrouching()));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8, 80));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            croakTicks = Math.max(0, croakTicks - 1);
            return;
        }
        if (hopCooldown > 0) hopCooldown--;
        boolean moving = !getNavigation().isDone();
        if (moving && onGround() && hopCooldown == 0 && !isInWater()) {   // salta como las ranas
            jumpFromGround();
            hopCooldown = 12 + random.nextInt(10);
        }
    }

    /** De noche croa con el saco inflado (más seguido); de día casi no. */
    @Override
    public void playAmbientSound() {
        if (level().isNight() || random.nextInt(4) == 0) {
            level().broadcastEntityEvent(this, (byte) 64);
            playSound(SoundEvents.FROG_AMBIENT, 1.0F, 0.75F + random.nextFloat() * 0.15F);
        }
    }

    @Override
    public int getAmbientSoundInterval() { return level().isNight() ? 120 : 400; }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 64) croakTicks = 30;
        else super.handleEntityEvent(id);
    }

    /** 0..1: cuánto está inflado el saco (para la animación). */
    public float sac(float partial) {
        if (croakTicks <= 0) return 0F;
        float t = (30 - croakTicks + partial) / 30F;
        return Mth.sin(t * Mth.PI);
    }

    @Override
    public boolean isFood(ItemStack stack) { return false; }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    // ------------------------------------------------------------------ cubeta

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
    }

    @Override
    public boolean fromBucket() { return fromBucket; }

    @Override
    public void setFromBucket(boolean b) { fromBucket = b; }

    @Override
    public void saveToBucketTag(ItemStack stack) { Bucketable.saveDefaultDataToBucketTag(this, stack); }

    @Override
    public void loadFromBucketTag(CompoundTag tag) { Bucketable.loadDefaultDataFromBucketTag(this, tag); }

    @Override
    public ItemStack getBucketItemStack() { return new ItemStack(CeruleaMobs.CROAKER_BUCKET.get()); }

    @Override
    public SoundEvent getPickupSound() { return SoundEvents.BUCKET_FILL_AXOLOTL; }

    @Override
    public boolean requiresCustomPersistence() { return super.requiresCustomPersistence() || fromBucket; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("FromBucket", fromBucket);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fromBucket = tag.getBoolean("FromBucket");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.FROG_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.FROG_DEATH; }
}
