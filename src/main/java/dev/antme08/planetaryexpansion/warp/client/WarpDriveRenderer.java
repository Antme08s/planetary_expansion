package dev.antme08.planetaryexpansion.warp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.antme08.planetaryexpansion.warp.Footprint;
import dev.antme08.planetaryexpansion.warp.WarpDriveBlockEntity;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Dibuja el motor completo (4x4x3) desde el ancla. El modelo está exportado a media escala (Minecraft no admite
 * modelos de más de 3 bloques) y aquí se dibuja al doble, girado según hacia dónde se puso.
 */
public class WarpDriveRenderer implements BlockEntityRenderer<WarpDriveBlockEntity> {
    /** Altura (px del mundo) del fondo del nitrógeno; la misma que FLUID_Y0 en tools/designs/jump_drive_model.py. */
    private static final float FLUID_Y0 = 7.05f;

    public WarpDriveRenderer(BlockEntityRendererProvider.Context context) {}

    /** Lleva la pose del ancla al origen del modelo de la huella (esquina, sin escalar). */
    static void toFootprint(PoseStack ms, Direction f, double alongA, double alongB, double sizeA, double sizeB) {
        Direction l = Footprint.left(f);
        ms.translate(0.5 + alongA * f.getStepX() + alongB * l.getStepX(), 0, 0.5 + alongA * f.getStepZ() + alongB * l.getStepZ());
        ms.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        ms.translate(-sizeB / 2, 0, -sizeA / 2);
    }

    @Override
    public void render(WarpDriveBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        ms.pushPose();
        toFootprint(ms, be.facing(), 1.5, 0.5, 4, 4);
        ms.scale(2f, 2f, 2f);
        SuperByteBuffer frame = CachedBuffers.partial(WarpClient.DRIVE_FRAME, be.getBlockState());
        frame.light(light).renderInto(ms, buffers.getBuffer(RenderType.solid()));

        float glow = Mth.clamp(be.coreGlow, 0f, 1f);
        int c = (int) (90 + 165 * glow);
        SuperByteBuffer lit = CachedBuffers.partial(WarpClient.DRIVE_GLOW, be.getBlockState());
        lit.light(LightTexture.FULL_BRIGHT).color(c, c, c, 255).renderInto(ms, buffers.getBuffer(RenderType.solid()));

        // 0.7.38: nitrógeno de los 4 tanques (sube y baja con el del motor) y el cristal por encima
        float fill = Mth.clamp(Mth.lerp(pt, be.prevShownFill, be.shownFill), 0f, 1f);
        if (fill > 0.005f) {
            ms.pushPose();
            ms.translate(0, FLUID_Y0 / 32.0, 0);
            ms.scale(1f, fill, 1f);
            SuperByteBuffer n2 = CachedBuffers.partial(WarpClient.DRIVE_FLUID, be.getBlockState());
            n2.light(LightTexture.pack(Math.max(LightTexture.block(light), 11), LightTexture.sky(light)))
                    .renderInto(ms, buffers.getBuffer(RenderType.translucent()));
            ms.popPose();
        }
        SuperByteBuffer glass = CachedBuffers.partial(WarpClient.DRIVE_GLASS, be.getBlockState());
        glass.light(light).renderInto(ms, buffers.getBuffer(RenderType.translucent()));

        // núcleo: gira (más rápido cargado y muchísimo durante el salto) y flota
        float t = AnimationTickHolder.getRenderTime(be.getLevel());
        float angle = Mth.lerp(pt, be.prevCoreAngle, be.coreAngle);
        float bob = Mth.sin(t * 0.07f) * 0.6f / 32f;
        float pulse = be.isJumping() ? 1f + 0.15f * Mth.sin(t * 0.9f) : 1f;
        ms.pushPose();
        ms.translate(1.0, 31.0 / 32.0 + bob, 1.0);
        ms.mulPose(Axis.YP.rotationDegrees(angle));
        ms.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.02f) * 12f));
        ms.scale(pulse, pulse, pulse);
        SuperByteBuffer core = CachedBuffers.partial(WarpClient.DRIVE_CORE, be.getBlockState());
        core.light(LightTexture.FULL_BRIGHT).color(c, c, c, 255).renderInto(ms, buffers.getBuffer(RenderType.solid()));
        ms.popPose();
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WarpDriveBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(WarpDriveBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5);
    }
}
