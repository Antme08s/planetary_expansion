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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/**
 * Arco (1.0.4): escarabajo-reptil pequeño de la ceniza y el basalto de Fulgor. Neutral: si lo golpeas te suelta una
 * descarga (2 de daño más 1,5 por cada pieza de armadura de metal que lleves, el doble si está cargado) que te empuja, y
 * huye. Un rayo a menos de 16 bloques lo carga durante 1 min (brilla, corre más y descarga el doble). Durante las
 * tormentas se mueve más deprisa y va a juntarse junto a los pararrayos. Se cría con redstone.
 */
public class ArcCrawler extends Animal implements Chargeable {
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(ArcCrawler.class, EntityDataSerializers.INT);
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath("planetary_expansion", "arc_charge");
    private static final Set<String> METALS = Set.of("iron", "chainmail", "gold", "netherite", "copper");

    private int zapCooldown;

    public ArcCrawler(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, 0);
    }

    public int charge() { return entityData.get(CHARGE); }

    void setCharge(int v) { entityData.set(CHARGE, Math.max(0, v)); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.2, FulgorMobs::isRedstone, false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.2));
        goalSelector.addGoal(5, new SeekRodGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) { return stack.is(Items.REDSTONE); }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) { return FulgorMobs.ARC.get().create(level); }

    // ------------------------------------------------------------------ descarga

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && zapCooldown == 0 && source.getDirectEntity() instanceof LivingEntity a
                && a.distanceToSqr(this) < 4.5 * 4.5 && !(a instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            discharge(a);
        }
        return r;
    }

    private void discharge(LivingEntity target) {
        int metal = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = target.getItemBySlot(slot);
            if (s.getItem() instanceof ArmorItem ai && ai.getMaterial().unwrapKey()
                    .map(k -> METALS.contains(k.location().getPath())).orElse(false)) metal++;
        }
        float dmg = 2.0F + 1.5F * metal;
        if (charge() > 0) dmg *= 2.0F;
        target.hurt(damageSources().lightningBolt(), dmg);
        Vec3 d = target.position().subtract(position());
        target.knockback(0.5 + 0.1 * metal, -d.x, -d.z);
        zapCooldown = 20;
        playSound(FulgorMobs.ARC_ZAP.get(), 1.0F, 0.9F + random.nextFloat() * 0.3F);
        if (level() instanceof ServerLevel sl) {
            Vec3 a = position().add(0, 0.3, 0), b = target.position().add(0, target.getBbHeight() * 0.5, 0);
            for (int i = 0; i <= 8; i++) {                                        // chispas a lo largo del arco
                Vec3 p = a.lerp(b, i / 8.0);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.05);
            }
        }
    }

    // ------------------------------------------------------------------ rayos: no le hacen daño, lo cargan

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) { nearbyLightning(0); }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING) || super.isInvulnerableTo(source);
    }

    @Override
    public void nearbyLightning(double dist2) {
        if (dist2 > 16 * 16) return;
        if (charge() == 0) playSound(FulgorMobs.ARC_ZAP.get(), 0.8F, 1.4F);
        setCharge(20 * 60);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (charge() > 0 && random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.5), getY() + 0.3 + random.nextDouble() * 0.2,
                        getRandomZ(0.5), 0, 0.02, 0);
            }
            return;
        }
        if (zapCooldown > 0) zapCooldown--;
        if (charge() > 0) setCharge(charge() - 1);
        if (tickCount % 20 == 0) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            double bonus = (charge() > 0 ? 0.35 : 0) + (FulgorMobs.stormy(level()) ? 0.25 : 0);
            if (speed != null) {
                if (bonus > 0) speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                else speed.removeModifier(SPEED_ID);
            }
        }
    }

    /** Tormenta (o cargado): va a juntarse cerca de un pararrayos a menos de 24 bloques; sin tormenta, de vez en cuando. */
    static class SeekRodGoal extends Goal {
        private final ArcCrawler mob;
        private BlockPos rod;
        private int ticks;

        SeekRodGoal(ArcCrawler mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.isBaby() && mob.getRandom().nextBoolean()) return false;
            boolean storm = FulgorMobs.stormy(mob.level());
            if (mob.getRandom().nextInt(storm ? 40 : 400) != 0 || !(mob.level() instanceof ServerLevel sl)) return false;
            rod = FulgorMobs.nearestRod(sl, mob.blockPosition(), 24).orElse(null);
            return rod != null && rod.distSqr(mob.blockPosition()) > 9;
        }

        @Override
        public void start() {
            ticks = 200;
            BlockPos p = rod.offset(mob.getRandom().nextInt(5) - 2, 0, mob.getRandom().nextInt(5) - 2);
            mob.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.1);
        }

        @Override
        public boolean canContinueToUse() { return --ticks > 0 && !mob.getNavigation().isDone(); }
    }

    // ------------------------------------------------------------------ guardar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ArcCharge", charge());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCharge(tag.getInt("ArcCharge"));
    }

    // ------------------------------------------------------------------ sonidos

    @Override
    public int getAmbientSoundInterval() { return 200; }

    @Override
    protected SoundEvent getAmbientSound() { return FulgorMobs.ARC_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return FulgorMobs.ARC_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return FulgorMobs.ARC_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SPIDER_STEP, 0.12F, 1.6F); }
}
