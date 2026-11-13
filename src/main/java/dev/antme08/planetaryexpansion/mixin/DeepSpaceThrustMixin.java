package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.ThrustTracker;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Apunta el empuje que cada nave le da a su órbita (ver weather/ThrustTracker). Solo observa: no cambia nada. */
@Mixin(value = DeepSpaceInstance.class, remap = false)
public abstract class DeepSpaceThrustMixin {
    @Inject(method = "applyVelocity", at = @At("HEAD"), require = 0)
    private void planetaryExpansion$recordThrust(UUID ship, Vector3dc velocity, double mass, CallbackInfo ci) {
        try {
            ThrustTracker.record((DeepSpaceInstance) (Object) this, velocity, mass);
        } catch (Throwable ignored) {
        }
    }
}
