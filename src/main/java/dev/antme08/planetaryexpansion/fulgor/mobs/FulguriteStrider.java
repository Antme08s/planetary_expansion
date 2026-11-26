package dev.antme08.planetaryexpansion.fulgor.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Zancudo de fulgurita (1.0.4): ave-insecto de cristal de los campos de fulgurita de Fulgor. Pasivo y en grupos pequeños;
 * se cría con fragmentos de fulgurita y te sigue si llevas uno. Un rayo a menos de 10 bloques lo sobrecarga 3 minutos:
 * brilla, corre un 40 % más, da calambres (2 de daño) a lo que lo toque, y de vez en cuando suelta un fragmento de
 * fulgurita; si muere sobrecargado suelta además un filamento de cristal y un fragmento.
 */
public class FulguriteStrider extends Animal implements Chargeable {
    private static final EntityDataAccessor<Integer> OVER = SynchedEntityData.defineId(FulguriteStrider.class, EntityDataSerializers.INT);
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("planetary_expansion", "strider_overcharge");
    public static final int OVERCHARGE_TICKS = 20 * 60 * 3;

    public FulguriteStrider(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OVER, 0);
    }

    public int overcharge() { return entityData.get(OVER); }

    void setOvercharge(int v) { entityData.set(OVER, Math.max(0, v)); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.15, s -> s.is(FulgorMobs.FULGURITE_SHARD.get()), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) { return stack.is(FulgorMobs.FULGURITE_SHARD.get()); }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return FulgorMobs.STRIDER.get().create(level); }

    // ------------------------------------------------------------------ rayos: lo sobrecargan

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) { nearbyLightning(0); }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING) || super.isInvulnerableTo(source);
    }

    @Override
    public void nearbyLightning(double dist2) {
        if (dist2 > 10 * 10) return;
        if (overcharge() == 0) playSound(FulgorMobs.STRIDER_CHARGE.get(), 1.2F, 1.0F);
        setOvercharge(OVERCHARGE_TICKS);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        int o = overcharge();
        if (level().isClientSide) {
            if (o > 0 && random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.6), getY() + 0.8 + random.nextDouble() * 1.0,
                        getRandomZ(0.6), 0, 0.03, 0);
            }
            return;
        }
        if (o > 0) {
            setOvercharge(o - 1);
            if (tickCount % 10 == 0) shockTouching();
            if (!isBaby() && random.nextInt(1200) == 0) spawnAtLocation(FulgorMobs.FULGURITE_SHARD.get());
        }
        if (tickCount % 20 == 0) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                if (o > 0) speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                else speed.removeModifier(SPEED_ID);
            }
        }
    }

    /** Sobrecargado: calambre a lo que lo esté tocando (menos a otros zancudos). */
    private void shockTouching() {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.25),
                e -> e != this && !(e instanceof FulguriteStrider) && e.isAlive()
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (e.hurt(damageSources().lightningBolt(), 2.0F)) {
                Vec3 d = e.position().subtract(position());
                e.knockback(0.4, -d.x, -d.z);
                playSound(SoundEvents.COPPER_BULB_TURN_ON, 0.6F, 1.8F);
                if (level() instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (overcharge() > 0 && !isBaby()) {
            spawnAtLocation(FulgorMobs.CRYSTAL_FILAMENT.get());
            spawnAtLocation(FulgorMobs.FULGURITE_SHARD.get());
        }
    }

    // ------------------------------------------------------------------ guardar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Overcharge", overcharge());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOvercharge(tag.getInt("Overcharge"));
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    public int getAmbientSoundInterval() { return 220; }

    @Override
    protected SoundEvent getAmbientSound() { return FulgorMobs.STRIDER_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return FulgorMobs.STRIDER_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return FulgorMobs.STRIDER_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.AMETHYST_BLOCK_STEP, 0.4F, 1.3F); }
}
