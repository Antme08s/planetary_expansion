package dev.antme08.planetaryexpansion.mixin;

import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Congelador del tiempo espacial (1.0.4): con {@code timescale} en 0 el reloj de la instancia no avanza, así que no hay
 * nada que propagar. Sin esto, propagate seguiría haciendo su comprobación de zona de influencia con un paso de tiempo
 * de cero (su búsqueda de raíz no admite un intervalo vacío). Ver freezer/SpaceTimeFreeze.
 */
@Mixin(value = DeepSpacePosition.class, remap = false)
public abstract class DeepSpacePositionFreezeMixin {
    @Inject(method = "propagate", at = @At("HEAD"), cancellable = true, require = 0)
    private void planetaryExpansion$skipWhenFrozen(UniverseDefinition universe, CallbackInfoReturnable<DeepSpacePosition> cir) {
        DeepSpacePosition self = (DeepSpacePosition) (Object) this;
        if (self.getTimescale() <= 0) {
            cir.setReturnValue(self);
        }
    }
}
