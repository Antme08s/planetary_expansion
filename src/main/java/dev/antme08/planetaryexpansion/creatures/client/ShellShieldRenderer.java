package dev.antme08.planetaryexpansion.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Escudo de caparazón de rey: el modelo del escudo de Minecraft con su propia textura. */
public class ShellShieldRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/entity/king_shell_shield.png");
    private ShieldModel model;

    public ShellShieldRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (model == null) model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
        pose.pushPose();
        pose.scale(1.0F, -1.0F, -1.0F);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, model.renderType(TEX), true, stack.hasFoil());
        model.handle().render(pose, vc, light, overlay);
        model.plate().render(pose, vc, light, overlay);
        pose.popPose();
    }

    @Override
    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
        model = null;
    }
}
