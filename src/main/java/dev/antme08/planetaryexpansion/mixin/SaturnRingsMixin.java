package dev.antme08.planetaryexpansion.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anillos de Saturno (1.0.4). Cosmonautics solo sabe dibujar una esfera con textura, así que aquí se añade el disco:
 * <ol>
 *   <li>tras abrir la pose del planeta se inclina el eje de Saturno (el giro del planeta se aplica después, así que la
 *       inclinación es fija y las bandas no se bambolean);</li>
 *   <li>antes de dibujar la esfera se dibuja la mitad de los anillos que queda detrás del planeta (la esfera la tapa);</li>
 *   <li>al terminar el planeta (después de su sombra y su atmósfera) se dibuja la mitad que queda delante, con la pose
 *       guardada en el paso 2. En 1.0.4 se dibujaba justo después de la esfera, pero la sombra del lado oscuro se pintaba
 *       encima y parecía que el planeta atravesaba el anillo.</li>
 * </ol>
 * "Delante" y "detrás" salen de la posición del espectador en el espacio del planeta (la inversa de la matriz de la
 * pose aplicada al origen): un punto del anillo está delante si apunta hacia el espectador. Cosmonautics dibuja con la
 * profundidad sin escribir, así que este orden de pintor sustituye al z-buffer.
 */
@Mixin(value = DeepSpaceHandler.class, remap = false)
public abstract class SaturnRingsMixin {
    private static final String SATURN = "saturn";
    private static final ResourceLocation RINGS = ResourceLocation.fromNamespaceAndPath("planetary_expansion", "textures/planet/saturn_rings.png");
    /** Radios interior y exterior de los anillos, en radios del planeta. */
    private static final float INNER = 1.22f, OUTER = 2.30f;
    private static final float TILT_DEGREES = 26.7f;
    private static final int SEGMENTS = 120;

    private static boolean planetaryExpansion$isSaturn(CubePlanet planet) {
        return SATURN.equals(planet.frame().getName());
    }

    @Inject(method = "renderPlanet", remap = false, require = 0, cancellable = false,
            at = @At(value = "INVOKE", remap = false, ordinal = 0, shift = At.Shift.AFTER,
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private static void planetaryExpansion$tilt(CubePlanet planet, Vector3D pos, PoseStack poseStack, AbsoluteDate date,
                                               float a, float b, CallbackInfoReturnable<Boolean> cir) {
        if (planetaryExpansion$isSaturn(planet)) poseStack.mulPose(Axis.ZP.rotationDegrees(TILT_DEGREES));
    }

    @Inject(method = "renderPlanet", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false, ordinal = 0,
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;enableBlend()V"))
    private static void planetaryExpansion$ringsBehind(CubePlanet planet, Vector3D pos, PoseStack poseStack, AbsoluteDate date,
                                                      float a, float b, CallbackInfoReturnable<Boolean> cir) {
        if (planetaryExpansion$isSaturn(planet)) planetaryExpansion$drawRings(planet, pos, poseStack, false);
    }

    /** Pose del planeta (con la inclinación y el giro) guardada al dibujar la mitad de atrás, para la de delante. */
    private static Matrix4f planetaryExpansion$saved;

    @Inject(method = "renderPlanet", remap = false, require = 0, at = @At("RETURN"))
    private static void planetaryExpansion$ringsFront(CubePlanet planet, Vector3D pos, PoseStack poseStack, AbsoluteDate date,
                                                     float a, float b, CallbackInfoReturnable<Boolean> cir) {
        Matrix4f m = planetaryExpansion$saved;
        planetaryExpansion$saved = null;
        if (m == null || !planetaryExpansion$isSaturn(planet)) return;
        planetaryExpansion$drawRings(planet, pos, m, true);
        // el mismo estado en el que Cosmonautics deja todo al terminar el planeta
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void planetaryExpansion$drawRings(CubePlanet planet, Vector3D pos, PoseStack poseStack, boolean front) {
        planetaryExpansion$saved = new Matrix4f(poseStack.last().pose());
        planetaryExpansion$drawRings(planet, pos, poseStack.last().pose(), front);
    }

    private static void planetaryExpansion$drawRings(CubePlanet planet, Vector3D pos, Matrix4f m, boolean front) {
        float radius = (float) (planet.radius() * (100.0 / Math.max(1.0, pos.getNorm())));
        // espectador en el espacio del planeta
        Vector3f eye = m.invert(new Matrix4f()).transformPosition(new Vector3f());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, RINGS);

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // Cosmonautics dibuja los planetas por defecto como CUBOS (planetShape = CUBE) de semilado = radio: las esquinas llegan a
        // 1,73 radios. Si el anillo empezara en 1,22 radios quedaría metido en el cubo y se vería descentrado y corto, así que
        // con cubos el anillo se mide desde el radio de la esfera que rodea al cubo.
        boolean sphere = RocketConfig.SERVER.planetShape.get() == RocketConfig.PlanetShape.SPHERE;
        float base = radius * (sphere ? 1.0f : 1.75f);
        float in = INNER * base, out = OUTER * base;
        int quads = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS, mid = (a0 + a1) / 2;
            // el punto medio del segmento apunta hacia el espectador -> mitad delantera
            boolean near = Math.cos(mid) * eye.x + Math.sin(mid) * eye.z > 0;
            if (near != front) continue;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            buf.addVertex(m, c0 * in, 0, s0 * in).setUv(0f, 0.5f).setColor(255, 255, 255, 255);
            buf.addVertex(m, c0 * out, 0, s0 * out).setUv(1f, 0.5f).setColor(255, 255, 255, 255);
            buf.addVertex(m, c1 * out, 0, s1 * out).setUv(1f, 0.5f).setColor(255, 255, 255, 255);
            buf.addVertex(m, c1 * in, 0, s1 * in).setUv(0f, 0.5f).setColor(255, 255, 255, 255);
            quads++;
        }
        MeshData mesh = buf.build(); // null si no hay vértices
        if (mesh != null && quads > 0) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
    }
}
