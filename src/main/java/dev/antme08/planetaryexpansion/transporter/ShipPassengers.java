package dev.antme08.planetaryexpansion.transporter;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * 1.0.4.1: criaturas que viajan dentro de una nave cuando cambia de dimensión. Se marcan para que no desaparezcan por
 * estar lejos del jugador (los peces y otras criaturas que no son animales de granja desaparecen solas; durante el
 * cambio de dimensión pueden quedar un momento lejos de él).
 * <p>
 * Los registros de prueba (resumen de cada viaje y vigilancia de 30 s de muertes y desapariciones) se quitaron antes de
 * publicar, una vez confirmado que las criaturas llegan bien.
 */
public final class ShipPassengers {
    private ShipPassengers() {}

    public static void moving(Entity entity) {
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
    }
}
