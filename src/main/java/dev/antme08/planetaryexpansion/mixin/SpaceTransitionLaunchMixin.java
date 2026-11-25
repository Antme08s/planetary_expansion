package dev.antme08.planetaryexpansion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.antme08.planetaryexpansion.transporter.SpaceLaunch;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.physics.SpaceTransitionHandler;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Transportador espacial (1.0.4): en el tick de física de cada planeta, Cosmonautics mira si cada nave está por encima de la
 * altura de transición y, si lo está, la pasa al espacio profundo. Para las naves con subida pendiente se responde que sí
 * (altura infinita), y justo después de crear su órbita se cambia por la elegida en el mapa. Ver transporter/SpaceLaunch.
 */
@Mixin(value = SpaceTransitionHandler.class, remap = false)
public abstract class SpaceTransitionLaunchMixin {
    @WrapOperation(method = "lambda$init$2", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;y()D"))
    private static double planetaryExpansion$forceLaunch(Vector3d position, Operation<Double> original, @Local ServerSubLevel ship) {
        if (SpaceLaunch.forceTransition(ship)) return Double.POSITIVE_INFINITY;
        return original.call(position);
    }

    @WrapOperation(method = "lambda$init$2", require = 0,
            at = @At(value = "INVOKE", target = "Ldev/devce/rocketnautics/content/physics/SpaceTransitionHandler;initInstance(Ldev/devce/rocketnautics/content/orbit/DeepSpaceInstance;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Ldev/devce/rocketnautics/content/orbit/universe/CubePlanet;Lnet/minecraft/world/level/Level;)Lorg/joml/Quaterniond;"))
    private static Quaterniond planetaryExpansion$setOrbit(DeepSpaceInstance inst, Vector3dc pos, Vector3dc vel, CubePlanet planet, Level level,
                                                           Operation<Quaterniond> original, @Local ServerSubLevel ship) {
        Quaterniond q = original.call(inst, pos, vel, planet, level);
        // subida con el transportador: órbita elegida y la nave llega derecha (sin el giro de la cara del cubo)
        if (SpaceLaunch.onTransition(ship, inst)) {
            try {
                return SpaceLaunch.levelingRotation(ship.logicalPose().orientation());
            } catch (Throwable ignored) {
            }
        }
        return q;
    }

    /** Bajada con el transportador: que la nave llegue derecha al planeta (el giro lo aplica igual que el suyo). */
    @WrapOperation(method = "exitDeepSpace", require = 0,
            at = @At(value = "INVOKE", target = "Ldev/devce/rocketnautics/api/orbit/DeepSpaceHelper;globalPositionToLocalPositionAndRotation(Ldev/devce/rocketnautics/shaded/orekit/utils/TimeStampedPVCoordinates;Ldev/devce/rocketnautics/content/orbit/universe/CubePlanet;Lnet/minecraft/world/level/Level;)Lit/unimi/dsi/fastutil/Pair;"))
    private static Pair<Vector3d, Quaterniond> planetaryExpansion$levelOnLanding(TimeStampedPVCoordinates pv, CubePlanet planet, Level level,
                                                                                Operation<Pair<Vector3d, Quaterniond>> original,
                                                                                @Local(argsOnly = true) DeepSpaceInstance inst) {
        Pair<Vector3d, Quaterniond> out = original.call(pv, planet, level);
        Quaterniond fix = SpaceLaunch.descentRotation(inst);
        if (fix != null && out != null && out.right() != null) out.right().set(fix);
        return out;
    }
}
