package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.MassGuard;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Justo antes de que Sable procese las estructuras a eliminar (SubLevelContainer.tick), recalcula la masa de
 * las que la tienen en 0 pero aún tienen bloques. Ver MassGuard.
 *
 * Se engancha en la llamada (INVOKE) y no dentro de processSubLevelRemovals porque Gadgets and Gizmos
 * (createthrusters) reemplaza ese método con su propio @Inject en HEAD.
 */
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class SableRemovalMassGuardMixin {

    @Inject(method = "tick", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;processSubLevelRemovals()V"))
    private void planetaryExpansion$rescueMass(CallbackInfo ci) {
        MassGuard.rescue((SubLevelContainer) (Object) this);
    }
}
