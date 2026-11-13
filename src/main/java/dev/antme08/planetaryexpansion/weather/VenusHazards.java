package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.armor.HazardArmor;
import dev.antme08.planetaryexpansion.armor.ThermalSuit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Peligros de Venus (0.7.0, diseño acordado con el usuario):
 *
 * - Calor: en todo el planeta, salvo dentro de un compartimento sellado con oxígeno (Create Deep Seas).
 * - Lluvia ácida: durante la tormenta, solo a la intemperie.
 * - Hazard Armor: -25 % de calor y lluvia por pieza; con el traje completo no pasa nada. La Impact Armor completa
 *   reduce el calor a la mitad (resiste el fuego).
 * - Lo que queda primero desgasta la armadura que lleves puesta (cualquiera que tenga durabilidad y no sea Hazard).
 *   Cuando se rompe toda, empieza a quitar vida. Mientras se desgasta sale un aviso en la barra de acción
 *   diciendo si es por el calor, por la lluvia ácida o por las dos cosas.
 * - La lluvia ácida corroe la lana, la madera y las plantas a la intemperie (etiqueta acid_rain_corrodes), sin
 *   soltar el bloque. Los cofres sí sueltan lo que tienen dentro.
 * - En la tormenta fuerte caen rayos solo visuales (no queman ni hacen daño).
 */
public final class VenusHazards {
    private VenusHazards() {}

    public static final ResourceKey<Level> VENUS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "venus"));
    public static final ResourceKey<DamageType> HEAT_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "venus_heat"));
    public static final ResourceKey<DamageType> ACID_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "acid_rain"));
    public static final TagKey<Block> CORRODES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "acid_rain_corrodes"));

    /** Durabilidad que pierde cada pieza por segundo por cada punto de "dosis" (1.0 = calor sin protección). */
    private static final float ARMOR_WEAR = 3.0f;
    private static final int CORRODE_RADIUS = 48;
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Desgaste acumulado (fracciones de punto de durabilidad) por jugador. */
    private static final Map<UUID, Float> WEAR = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(VENUS)) return;
        RandomSource rng = level.getRandom();
        float rain = StormManager.intensity(StormType.ACID_RAIN);
        boolean severe = StormManager.severe(StormType.ACID_RAIN);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            // el daño, una vez por segundo (repartido entre jugadores según su id)
            if ((level.getGameTime() + player.getId()) % 20 == 0) {
                hurt(level, player, rain, severe);
            }
            evaporate(level, player, rng);
            if (rain > 0.3f) {
                if (StormConfig.get(StormConfig.ACID_CORROSION)) corrode(level, player, rng, rain, severe);
                if (severe && StormConfig.get(StormConfig.ACID_LIGHTNING)
                        && rng.nextFloat() < 0.0025f * rain) lightning(level, player, rng);
            }
        }
    }

    // ------------------------------------------------------------------ daño

    private static void hurt(ServerLevel level, ServerPlayer player, float rain, boolean severe) {
        Vec3 eye = player.getEyePosition();
        float hazard = HazardArmor.venusFactor(player);
        if (hazard <= 0f) return;

        float heat = 0f;
        if (!StormShelter.inOxygenatedShip(level, eye)) {
            heat = (float) StormConfig.get(StormConfig.VENUS_HEAT) * hazard;
            if (ThermalSuit.pieces(player) == 4) heat *= 0.5f; // 0.7.64: antes la Impact
            heat *= dev.antme08.planetaryexpansion.armor.SurvivalKits.heatFactor(player); // 0.7.66: gel refrigerante
        }
        float acid = 0f;
        if (rain > 0.2f && StormShelter.exposedToHazards(player)) {
            acid = (float) StormConfig.get(StormConfig.ACID_RAIN_DAMAGE) * (severe ? 1.0f : 0.5f) * rain * hazard;
        }
        float dose = heat + acid;
        if (dose <= 0.001f) return;
        String cause = heat > 0.001f && acid > 0.001f ? "both" : acid > 0.001f ? "acid" : "heat";

        List<EquipmentSlot> worn = wornArmor(player);
        if (!worn.isEmpty()) {
            // 1) la armadura se desgasta primero
            float acc = WEAR.getOrDefault(player.getUUID(), 0f) + dose * ARMOR_WEAR;
            int points = (int) acc;
            WEAR.put(player.getUUID(), acc - points);
            if (points > 0) {
                for (EquipmentSlot slot : worn) {
                    player.getItemBySlot(slot).hurtAndBreak(points, player, slot);
                }
            }
            warn(player, "armor_" + cause, ChatFormatting.GOLD);
            return;
        }
        // 2) sin armadura que gastar: vida
        ResourceKey<DamageType> type = acid > heat ? ACID_DAMAGE : HEAT_DAMAGE;
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
        player.hurt(source, dose);
        warn(player, "hurt_" + cause, ChatFormatting.RED);
    }

    /** Piezas puestas que se pueden desgastar (con durabilidad) y que no son de la Hazard Armor. */
    private static List<EquipmentSlot> wornArmor(ServerPlayer player) {
        List<EquipmentSlot> out = new ArrayList<>(4);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && !HazardArmor.isPiece(stack.getItem())) out.add(slot);
        }
        return out;
    }

    private static void warn(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("message." + PlanetaryExpansion.MODID + ".venus." + key)
                .withStyle(color), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WEAR.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ corrosión

    /**
     * Prueba unas cuantas columnas al azar alrededor del jugador: si el bloque más alto (el que recibe la lluvia)
     * es lana, madera o una planta, a veces se deshace. Normal: ~3 columnas por tick; fuerte: ~6.
     * Cada bloque expuesto aguanta de media unos minutos.
     */
    private static void corrode(ServerLevel level, ServerPlayer player, RandomSource rng, float rain, boolean severe) {
        int tries = Math.round((severe ? 6 : 3) * rain);
        BlockPos center = player.blockPosition();
        for (int i = 0; i < tries; i++) {
            int x = center.getX() + rng.nextInt(CORRODE_RADIUS * 2 + 1) - CORRODE_RADIUS;
            int z = center.getZ() + rng.nextInt(CORRODE_RADIUS * 2 + 1) - CORRODE_RADIUS;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (top < level.getMinBuildHeight()) continue;
            BlockPos pos = new BlockPos(x, top, z);
            BlockState state = level.getBlockState(pos);
            if (!state.is(CORRODES) || rng.nextFloat() >= 0.3f) continue;
            level.destroyBlock(pos, false);
            level.sendParticles(ParticleTypes.SMOKE, x + 0.5, top + 0.6, z + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.4f + rng.nextFloat() * 0.4f);
        }
    }

    // ------------------------------------------------------------------ agua

    /** Bloque dentro de una estructura física de Sable (sus bloques viven en el "plot", fuera del terreno). */
    public static boolean inPhysicsStructure(Level level, BlockPos pos) {
        try {
            return dev.ryanhcode.sable.Sable.HELPER.getContaining(level, (net.minecraft.core.Vec3i) pos) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * El agua que acabe a la intemperie en el terreno (hielo derretido, tuberías de Create, etc.) se evapora poco a
     * poco. Solo mira el bloque más alto de cada columna, así que nunca toca el agua de las estructuras físicas.
     */
    private static void evaporate(ServerLevel level, ServerPlayer player, RandomSource rng) {
        BlockPos center = player.blockPosition();
        for (int i = 0; i < 4; i++) {
            int x = center.getX() + rng.nextInt(CORRODE_RADIUS * 2 + 1) - CORRODE_RADIUS;
            int z = center.getZ() + rng.nextInt(CORRODE_RADIUS * 2 + 1) - CORRODE_RADIUS;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (top < level.getMinBuildHeight()) continue;
            BlockPos pos = new BlockPos(x, top, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false), 3);
            } else if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            } else {
                continue;
            }
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, top + 0.8, z + 0.5, 3, 0.3, 0.1, 0.3, 0.01);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 2.0f + rng.nextFloat() * 0.4f);
        }
    }

    // ------------------------------------------------------------------ rayos

    private static void lightning(ServerLevel level, ServerPlayer player, RandomSource rng) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 24 + rng.nextDouble() * 56;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(x + 0.5, y, z + 0.5);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }
}
