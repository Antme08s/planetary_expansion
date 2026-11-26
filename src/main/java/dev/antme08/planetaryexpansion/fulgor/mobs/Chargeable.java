package dev.antme08.planetaryexpansion.fulgor.mobs;

/** Criaturas de Fulgor que se cargan con los rayos que caen cerca (FulgorMobs.lightning avisa a todas a 24 bloques). */
public interface Chargeable {
    /** Ha caído un rayo a sqrt(dist2) bloques (0 si les ha dado de lleno). */
    void nearbyLightning(double dist2);
}
