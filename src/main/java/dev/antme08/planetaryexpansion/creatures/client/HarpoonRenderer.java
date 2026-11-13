package dev.antme08.planetaryexpansion.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.antme08.planetaryexpansion.creatures.Creatures;
import dev.antme08.planetaryexpansion.creatures.HarpoonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** Arpón en vuelo: el dibujo del objeto, con la punta (arriba a la derecha del icono) hacia donde vuela. */
public class HarpoonRenderer extends EntityRenderer<HarpoonEntity> {
    private final ItemRenderer items;

    public HarpoonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        items = ctx.getItemRenderer();
    }

    @Override
    public void render(HarpoonEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        double yr = Math.toRadians(Mth.lerp(partialTick, e.yRotO, e.getYRot()));
        double xr = Math.toRadians(Mth.lerp(partialTick, e.xRotO, e.getXRot()));
        float fx = (float) (Math.sin(yr) * Math.cos(xr)), fy = (float) Math.sin(xr), fz = (float) (Math.cos(yr) * Math.cos(xr));
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, fx, fy, fz));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        pose.scale(1.6F, 1.6F, 1.6F);
        items.renderStatic(new ItemStack(Creatures.HARPOON.get()), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, e.level(), e.getId());
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HarpoonEntity e) { return TextureAtlas.LOCATION_BLOCKS; }
}
