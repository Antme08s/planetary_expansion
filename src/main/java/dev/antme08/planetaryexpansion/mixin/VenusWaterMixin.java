package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.weather.VenusHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Venus (0.7.0, petición del usuario): el agua se evapora al ponerla (como en el Nether) salvo dentro de una
 * estructura física de Sable. La dimensión no es "ultracaliente" para que lo demás funcione normal; aquí se decide
 * bloque a bloque. Lo usa el cubo de Minecraft y cualquier mod que pregunte a NeoForge.
 */
@Mixin(value = FluidType.class, remap = false)
public abstract class VenusWaterMixin {

    @Inject(method = "isVaporizedOnPlacement", at = @At("HEAD"), cancellable = true, require = 0)
    private void planetaryExpansion$venusWater(Level level, BlockPos pos, FluidStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!level.dimension().equals(VenusHazards.VENUS)) return;
        if ((Object) this != NeoForgeMod.WATER_TYPE.value()) return;
        cir.setReturnValue(!VenusHazards.inPhysicsStructure(level, pos));
    }
}
