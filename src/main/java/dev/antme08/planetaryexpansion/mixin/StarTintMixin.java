package dev.antme08.planetaryexpansion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Map;

/**
 * Color propio de las estrellas (1.0.3). Cosmonautics dibuja todas las estrellas igual (la misma textura de plasma
 * amarilla y el mismo resplandor, en blanco), sin mirar su textura. Aquí se tiñen los colores de los vértices de las
 * estrellas que tienen tinte: Ámbar (enana naranja) sale más naranja y rojiza. El Sol no cambia.
 * Los argumentos 5, 6 y 7 de {@code renderCubeOrSphere} son el rojo, el verde y el azul.
 */
@Mixin(value = DeepSpaceHandler.class, remap = false)
public abstract class StarTintMixin {
    private static final Map<String, float[]> TINT = Map.of("amber", new float[]{1.0f, 0.52f, 0.22f});

    @ModifyArgs(method = "renderPlanet", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false,
                    target = "Ldev/devce/rocketnautics/client/DeepSpaceHandler;renderCubeOrSphere(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;FFFFFFFZ)V"))
    private static void planetaryExpansion$tintStar(Args args, CubePlanet planet, Vector3D pos, PoseStack poseStack,
                                                    AbsoluteDate date, float a, float b) {
        float[] t = TINT.get(planet.frame().getName());
        if (t == null) return;
        args.set(5, (float) args.get(5) * t[0]);
        args.set(6, (float) args.get(6) * t[1]);
        args.set(7, (float) args.get(7) * t[2]);
    }
}
