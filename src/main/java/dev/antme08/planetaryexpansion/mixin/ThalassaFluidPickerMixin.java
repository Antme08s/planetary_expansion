package dev.antme08.planetaryexpansion.mixin;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cuevas submarinas de Thalassa (0.7.26). Sin acuíferos, Minecraft llena de lava todo hueco por debajo de
 * min(-54, nivel del mar). En Thalassa (nivel del mar 75, fondo de la dimensión en y -320) las cuevas bajan hasta
 * y -300 y tienen que quedar llenas de agua: para esa configuración el selector de fluido devuelve siempre el agua
 * del mar. Se reconoce por sus valores (ningún otro planeta tiene nivel del mar 75 con mínimo -320).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class ThalassaFluidPickerMixin {
    @Inject(method = "createFluidPicker", at = @At("RETURN"), cancellable = true, require = 0)
    private static void planetaryExpansion$thalassaWater(NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (settings == dev.antme08.planetaryexpansion.worldgen.IceWorldFluids.SETTINGS && cir.getReturnValue() != null) {
            // 0.7.42: mundo helado sin lava; los huecos profundos quedan vacíos
            cir.setReturnValue(new dev.antme08.planetaryexpansion.worldgen.IceWorldFluids.NoLava(cir.getReturnValue(), settings.noiseSettings().minY()));
            return;
        }
        if (settings.seaLevel() != 75 || settings.noiseSettings().minY() != -320) return;
        Aquifer.FluidStatus sea = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
        cir.setReturnValue((x, y, z) -> sea);
    }
}
