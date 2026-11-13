package dev.antme08.planetaryexpansion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.antme08.planetaryexpansion.client.UniverseSky;
import dev.devce.rocketnautics.RocketNauticsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * En los planetas que dibujan el universo de Cosmonautics (ver {@link UniverseSky}) se quitan el sol, la luna y las
 * estrellas de Minecraft: el Sol, las lunas y las estrellas ya los dibuja Cosmonautics en su posición real (0.7.51).
 */
@Mixin(LevelRenderer.class)
public abstract class UniverseSkyMixin {
    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"))
    private float planetaryExpansion$noVanillaStars(ClientLevel level, float partialTick, Operation<Float> original) {
        if (UniverseSky.drawsUniverse(level)) return 0.0f;
        return original.call(level, partialTick);
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private void planetaryExpansion$noVanillaSunMoon(int slot, ResourceLocation texture, Operation<Void> original) {
        if (texture != null && UniverseSky.drawsUniverse(Minecraft.getInstance().level)) {
            String path = texture.getPath();
            if (path.equals("textures/environment/sun.png") || path.equals("textures/environment/moon_phases.png")) {
                original.call(slot, RocketNauticsClient.EMPTY_TEXTURE);
                return;
            }
        }
        original.call(slot, texture);
    }
}
