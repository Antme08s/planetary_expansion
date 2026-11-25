package dev.antme08.planetaryexpansion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.antme08.planetaryexpansion.armor.CustomSuitPiece;
import dev.devce.rocketnautics.client.render.JetpackLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmonautics dibuja su jetpack encima de cualquier pechera que sea un JetpackItem. La pechera de impacto lo es
 * (para heredar el vuelo y el aire), pero tiene su propio diseño: aquí se salta el dibujo del jetpack original.
 *
 * remap = false: las clases de Cosmonautics no están ofuscadas y NeoForge usa nombres de Mojang.
 */
@Mixin(value = JetpackLayer.class, remap = false)
public abstract class JetpackLayerMixin {

    @Inject(require = 0, method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void planetaryExpansion$skipForCustomSuits(PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                                      float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                                      float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof CustomSuitPiece) {
            ci.cancel();
        }
    }
}
