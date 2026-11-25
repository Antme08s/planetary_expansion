package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.freezer.SpaceTimeFreeze;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Congelador del tiempo espacial (1.0.4): mientras una instancia está congelada, su propagación la hace
 * SpaceTimeFreeze.propagateFrozen (el reloj avanza y la nave se recoloca en el mismo punto respecto a su cuerpo dominante).
 * Con {@code timescale <= 0} en cualquier otro caso (el cliente, o el comando de Cosmonautics) no hay nada que propagar, y
 * su búsqueda de raíz no admite un intervalo vacío.
 */
@Mixin(value = DeepSpacePosition.class, remap = false)
public abstract class DeepSpacePositionFreezeMixin {
    @Inject(method = "propagate", at = @At("HEAD"), cancellable = true, require = 0)
    private void planetaryExpansion$skipWhenFrozen(UniverseDefinition universe, CallbackInfoReturnable<DeepSpacePosition> cir) {
        DeepSpacePosition self = (DeepSpacePosition) (Object) this;
        if (SpaceTimeFreeze.propagateFrozen(self, universe) || self.getTimescale() <= 0) {
            cir.setReturnValue(self);
        }
    }
}
