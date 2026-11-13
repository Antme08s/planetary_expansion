package dev.antme08.planetaryexpansion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import net.minecraft.client.Camera;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sin planetas del universo de Cosmonautics bajo el agua, la lava o la nieve polvo (0.7.52). Cosmonautics los dibuja
 * después del cielo y sin niebla, así que bajo el mar de Thalassa se veían a través del agua (sobre todo con
 * shaders). Solo en los planetas: en la Tierra no se cambia nada.
 */
@Mixin(value = DeepSpaceHandler.class, remap = false)
public abstract class UniverseUnderwaterMixin {
    @Inject(method = "renderUniverseForLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void planetaryExpansion$hideUnderwater(Level level, Vec3 position, PoseStack poseStack, float partialDelta,
                                                         float partialTick, Camera camera, CallbackInfo ci) {
        if (level.dimension() == Level.OVERWORLD) return;
        if (camera.getFluidInCamera() != FogType.NONE) ci.cancel();
    }
}
