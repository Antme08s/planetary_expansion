package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.physics.AsteroidSpawner;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sin asteroides naturales en los planetas de este mod.
 *
 * Cosmonautics genera asteroides alrededor de cualquier jugador que esté por encima de y=5000, en cualquier
 * dimensión ("inSpace = ... || player.getY() > 5000"). En un planeta con gravedad el asteroide cae miles de
 * bloques, supera los 25 m/s y al chocar destroza lo que tenga debajo (le pasó a la base de Marte del
 * usuario el 2026-11-05, tras teletransportarse a y=6000). En el espacio profundo siguen apareciendo igual;
 * la Tierra y la Luna no se tocan. El comando /rn asteroid spawn sigue funcionando.
 *
 * remap = false: las clases de Cosmonautics no están ofuscadas.
 */
@Mixin(value = AsteroidSpawner.class, remap = false)
public abstract class AsteroidSpawnerMixin {

    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true)
    private static void planetaryExpansion$noAsteroidsOnPlanets(PlayerTickEvent.Post event, CallbackInfo ci) {
        if (event.getEntity().level().dimension().location().getNamespace().equals(PlanetaryExpansion.MODID)) {
            ci.cancel();
        }
    }
}
