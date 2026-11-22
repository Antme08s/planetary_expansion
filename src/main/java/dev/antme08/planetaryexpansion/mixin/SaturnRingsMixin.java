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
 *   <li>después de la esfera se dibuja la mitad que queda delante.</li>
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

    @Inject(method = "renderPlanet", remap = false, require = 0,
            at = @At(value = "INVOKE", remap = false, ordinal = 0, shift = At.Shift.AFTER,
                    target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private static void planetaryExpansion$ringsFront(CubePlanet planet, Vector3D pos, PoseStack poseStack, AbsoluteDate date,
                                                     float a, float b, CallbackInfoReturnable<Boolean> cir) {
        if (planetaryExpansion$isSaturn(planet)) planetaryExpansion$drawRings(planet, pos, poseStack, true);
    }

    private static void planetaryExpansion$drawRings(CubePlanet planet, Vector3D pos, PoseStack poseStack, boolean front) {
        float radius = (float) (planet.radius() * (100.0 / Math.max(1.0, pos.getNorm())));
        Matrix4f m = poseStack.last().pose();
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
        float in = INNER * radius, out = OUTER * radius;
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
