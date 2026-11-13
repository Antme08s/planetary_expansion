package dev.antme08.planetaryexpansion.creatures;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Base de los cangrejos (0.7.61): caminan por el fondo del mar como si fuera tierra. Respiran bajo el agua, el agua no
 * los frena (atributo de eficiencia de movimiento en agua = 1), no los arrastra la corriente y no intentan flotar.
 */
public abstract class SeafloorCrab extends PathfinderMob {
    protected SeafloorCrab(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    @Override
    public boolean isPushedByFluid() { return false; }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) { return level.isUnobstructed(this); }

    @Override
    protected int calculateFallDamage(float distance, float multiplier) {
        return isInWater() ? 0 : super.calculateFallDamage(distance, multiplier);
    }
}
