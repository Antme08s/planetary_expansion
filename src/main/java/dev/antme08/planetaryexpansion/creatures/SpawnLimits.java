package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * 1.0.4: límites de aparición de las criaturas de Titán y Fulgor. Sus reglas solo piden suelo sólido (en la Tierra los
 * animales necesitan hierba y luz), así que casi todos los intentos salían bien y llenaban el mundo. Ahora:
 * <ul>
 *   <li>aparición natural: solo una fracción de los intentos, y nunca si ya hay {@code max} de esa criatura en
 *   {@code radius} bloques;</li>
 *   <li>generación del chunk: solo una fracción de los grupos (ahí no se pueden contar las que ya hay);</li>
 *   <li>huevos, comandos, cría, etc.: sin límite.</li>
 * </ul>
 */
public final class SpawnLimits {
    private SpawnLimits() {}

    public static boolean allow(ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random,
                                Class<? extends Entity> type, double radius, int max, float naturalChance, float chunkChance) {
        if (reason == MobSpawnType.CHUNK_GENERATION) return random.nextFloat() < chunkChance;
        if (reason != MobSpawnType.NATURAL) return true;
        if (random.nextFloat() >= naturalChance) return false;
        return level.getEntitiesOfClass(type, new AABB(pos).inflate(radius, 64.0, radius)).size() < max;
    }
}
