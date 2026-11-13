package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.MassGuard;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lo mismo que SableRemovalMassGuardMixin, en la otra llamada a processSubLevelRemovals (física de Sable). */
@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SablePhysicsRemovalMassGuardMixin {

    @Inject(method = "tickPipelinePhysics", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;processSubLevelRemovals()V"))
    private void planetaryExpansion$rescueMass(ServerSubLevelContainer container, CallbackInfo ci) {
        MassGuard.rescue(container);
    }
}
