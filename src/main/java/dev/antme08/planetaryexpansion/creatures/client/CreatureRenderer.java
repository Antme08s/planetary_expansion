package dev.antme08.planetaryexpansion.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import java.util.function.ToDoubleFunction;

/**
 * Renderer de las criaturas de Thalassa: modelo JSON, textura, capa que brilla en la oscuridad (textura _glow con
 * RenderType.eyes) y, para los nadadores, inclinación según hacia dónde nadan (o coletazos fuera del agua).
 */
public class CreatureRenderer<T extends Mob> extends MobRenderer<T, JsonMobModel<T>> {
    public enum Swim { NONE, FISH, SWIMMER }

    private final ResourceLocation texture;
    private final float scale;
    private final Swim swim;

    public CreatureRenderer(EntityRendererProvider.Context ctx, String name, float shadow, float scale, Swim swim,
                            JsonMobModel.Animator<T> animator, ToDoubleFunction<T> glow) {
        super(ctx, JsonMobModel.load(ctx.getResourceManager(), name, animator), shadow * scale);
        this.texture = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/entity/" + name + ".png");
        this.scale = scale;
        this.swim = swim;
        addLayer(new Glow<>(this, ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/entity/" + name + "_glow.png"), glow));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) { return texture; }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        if (scale != 1.0F) pose.scale(scale, scale, scale);
    }

    @Override
    protected void setupRotations(T entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
        if (swim == Swim.NONE) return;
        if (!entity.isInWater()) {
            if (swim == Swim.FISH) { // coletazos en el suelo, como el bacalao
                pose.translate(0.1F, 0.1F, -0.1F);
                pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }
            return;
        }
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        if (swim == Swim.FISH) pose.mulPose(Axis.YP.rotationDegrees(4.3F * Mth.sin(0.6F * bob)));
    }

    /** Capa de brillo: solo los píxeles de la textura _glow, a toda luz. */
    static class Glow<T extends Mob> extends RenderLayer<T, JsonMobModel<T>> {
        private final ResourceLocation tex;
        private final ToDoubleFunction<T> strength;

        Glow(RenderLayerParent<T, JsonMobModel<T>> parent, ResourceLocation tex, ToDoubleFunction<T> strength) {
            super(parent);
            this.tex = tex;
            this.strength = strength;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float headYaw, float headPitch) {
            if (entity.isInvisible()) return;   // 1.0.3: el gusano enterrado (invisible) no deja sus fosas brillando en el aire
            float a = (float) Mth.clamp(strength == null ? 1.0 : strength.applyAsDouble(entity), 0.0, 1.0);
            if (a <= 0.01F) return;
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.colorFromFloat(1.0F, a, a, a));
        }
    }
}
