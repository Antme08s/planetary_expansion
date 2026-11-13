package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.StructureLoadGuard;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Max-Q de Cosmonautics (rompe con explosiones bloques de estructuras a más de 25 m/s) en los planetas de este mod:
 *  - no actúa sobre estructuras recién cargadas que estaban en el suelo (ver {@link StructureLoadGuard});
 *  - deja un aviso en el log cuando empieza a romper una estructura, para saber qué pasó si se destruye algo.
 * En la Tierra, la Luna y el espacio no cambia nada.
 *
 * remap = false: las clases de Cosmonautics no están ofuscadas.
 */
@Mixin(value = GlobalSpacePhysicsHandler.class, remap = false)
public abstract class GlobalSpacePhysicsMixin {

    @Inject(method = "applyMaxQStress", at = @At("HEAD"), cancellable = true)
    private static void planetaryExpansion$guardMaxQ(ServerSubLevel ship, RigidBodyHandle handle, ServerLevel level,
                                                     Vector3d position, double timeStep, CallbackInfo ci) {
        if (!StructureLoadGuard.isPlanet(level) || handle == null) return;
        double speed = handle.getLinearVelocity(new Vector3d()).length();
        if (speed * speed <= 625.0) return; // por debajo de 25 m/s Cosmonautics no rompe nada
        if (StructureLoadGuard.protectFromMaxQ(ship, level)) {
            ci.cancel();
            return;
        }
        StructureLoadGuard.logMaxQ(ship, level, speed);
    }
}
