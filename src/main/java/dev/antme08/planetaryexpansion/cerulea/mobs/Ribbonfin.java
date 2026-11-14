package dev.antme08.planetaryexpansion.cerulea.mobs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Pez de cinta de Cerúlea (1.0.3): nada en cardúmenes en los ríos y el mar. Dos colores: azul con aletas verde agua
 * (más en el mar) y verde con rayas oscuras (más en los ríos). Se coge con cubeta (guarda el color).
 */
public class Ribbonfin extends AbstractSchoolingFish {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Ribbonfin.class, EntityDataSerializers.INT);

    public Ribbonfin(EntityType<? extends AbstractSchoolingFish> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    /** 0 azul, 1 verde rayado. */
    public int variant() { return entityData.get(VARIANT); }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data);
        if (reason != MobSpawnType.BUCKET) {
            boolean river = level.getBiome(blockPosition()).unwrapKey().map(k -> k.location().getPath().contains("river")).orElse(false);
            entityData.set(VARIANT, random.nextFloat() < (river ? 0.75F : 0.25F) ? 1 : 0);
        }
        return d;
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> tag.putInt("Variant", variant()));
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        if (tag.contains("Variant")) entityData.set(VARIANT, tag.getInt("Variant"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Variant"));
    }

    @Override
    public int getMaxSchoolSize() { return 8; }

    @Override
    public ItemStack getBucketItemStack() { return new ItemStack(CeruleaMobs.RIBBONFIN_BUCKET.get()); }

    @Override
    protected SoundEvent getFlopSound() { return SoundEvents.SALMON_FLOP; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.SALMON_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SALMON_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.SALMON_DEATH; }
}
