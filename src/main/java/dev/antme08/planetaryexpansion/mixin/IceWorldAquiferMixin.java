package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.worldgen.IceWorldFluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mundo helado sin lava (0.7.42): los acuíferos profundos de Minecraft también pueden salir de lava (bolsas por debajo
 * de y -10). En el mundo helado (se reconoce por su selector de fluido, {@link IceWorldFluids.NoLava}) quedan vacíos.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class IceWorldAquiferMixin {
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;

    @Inject(method = "computeFluidType", at = @At("RETURN"), cancellable = true, require = 0)
    private void planetaryExpansion$noLava(int x, int y, int z, Aquifer.FluidStatus global, int surface, CallbackInfoReturnable<BlockState> cir) {
        if (globalFluidPicker instanceof IceWorldFluids.NoLava && cir.getReturnValue() != null && cir.getReturnValue().is(Blocks.LAVA)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
