package dev.antme08.planetaryexpansion.weather;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.fulgor.FulgorBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Rayos de la tormenta eléctrica de Fulgor (1.0.4). Con la tormenta activa caen rayos de verdad (con daño y fuego, no solo
 * el destello) a entre 8 y 56 bloques de cada jugador en el exterior; cuanto más intensa la tormenta más caen, y la fuerte
 * triplica el ritmo. Cada rayo usa la misma búsqueda de objetivo que las tormentas de la Tierra: un pararrayos cercano
 * (el cobre de Fulgor sirve) lo atrae y protege lo que haya a su alrededor. Donde cae sobre polvo de tormenta lo funde y
 * deja fulgurita.
 */
public final class FulgorStorms {
    private FulgorStorms() {}

    private static final ResourceKey<Level> FULGOR = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "fulgor"));
    /** Probabilidad por tick y jugador a plena intensidad: ~1 rayo cada 12 s (normal) o cada 4 s (fuerte). */
    private static final float NORMAL = 1.0f / 240f;
    private static final float SEVERE = 1.0f / 80f;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(FULGOR)) return;
        float intensity = StormManager.intensity(StormType.FULGOR_STORM);
        if (intensity < 0.3f) return;
        float chance = (StormManager.severe(StormType.FULGOR_STORM) ? SEVERE : NORMAL) * intensity;
        RandomSource rng = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (rng.nextFloat() < chance) strike(level, player, rng);
        }
    }

    private static void strike(ServerLevel level, ServerPlayer player, RandomSource rng) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double dist = 8 + rng.nextDouble() * 48;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        BlockPos target = top;   // el punto más alto del lugar elegido
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(target));
        level.addFreshEntity(bolt);
        BlockPos ground = target.below();
        if (level.getBlockState(ground).is(FulgorBlocks.STORM_DUST.get())) {
            level.setBlock(ground, FulgorBlocks.FULGURITE.get().defaultBlockState(), 3);   // el rayo funde el polvo en vidrio
        }
    }
}
