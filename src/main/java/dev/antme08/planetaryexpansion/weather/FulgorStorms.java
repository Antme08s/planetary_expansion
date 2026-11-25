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
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Rayos de la tormenta eléctrica de Fulgor (1.0.4). Con la tormenta activa caen rayos de verdad (con daño y fuego, no solo
 * el destello) a entre 8 y 56 bloques de cada jugador; cuanto más intensa la tormenta más caen, y la fuerte triplica el
 * ritmo. Cada rayo compara varios puntos del terreno y cae en el más alto; si hay un pararrayos a menos de 128 bloques de
 * ese punto (como en la Tierra) lo atrae y protege lo de alrededor. Donde cae sobre polvo de tormenta lo funde y deja
 * fulgurita.
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

    /** Puntos de terreno que se comparan para cada rayo: el rayo cae en el más alto (como en la realidad, busca lo alto). */
    private static final int CANDIDATES = 7;
    /** Alcance de atracción de un pararrayos, igual que en las tormentas de la Tierra. */
    private static final int ROD_RANGE = 128;

    private static void strike(ServerLevel level, ServerPlayer player, RandomSource rng) {
        // 1) Varios puntos al azar entre 8 y 56 bloques del jugador; se queda con el de superficie más alta.
        BlockPos best = null;
        for (int i = 0; i < CANDIDATES; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 8 + rng.nextDouble() * 48;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            if (best == null || top.getY() > best.getY()) best = top;
        }
        if (best == null) return;
        // 2) Un pararrayos a menos de 128 bloques de ese punto atrae el rayo (solo si es lo más alto de su columna).
        BlockPos target = level.getPoiManager().findClosest(
                poi -> poi.is(PoiTypes.LIGHTNING_ROD),
                pos -> pos.getY() == level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1,
                best, ROD_RANGE, PoiManager.Occupancy.ANY).map(BlockPos::above).orElse(best);
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
