package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.transporter.ShipHeightFix;
import dev.egg.registries.BlockEntityRegistry;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.0.4: arreglo del paso de naves entre dimensiones de alturas distintas (Dimensional Sable, dentro de Cosmonautics).
 * Ver {@link ShipHeightFix}. Si Cosmonautics cambia estos métodos, el arreglo se salta solo (require = 0).
 */
@Mixin(targets = "dev.egg.SubLevelTemplate", remap = false)
public abstract class SubLevelTemplateHeightMixin {
    @Inject(method = "save", at = @At("RETURN"), require = 0)
    private static void planetaryExpansion$recordSource(ServerLevelPlot plot, CallbackInfoReturnable<CompoundTag> cir) {
        ShipHeightFix.recordSource(plot, cir.getReturnValue());
        dev.antme08.planetaryexpansion.transporter.ShipGroups.noteSource(plot);
    }

    @Inject(method = "load", at = @At("HEAD"), require = 0)
    private static void planetaryExpansion$alignHeights(ServerLevelPlot plot, CompoundTag tag, BlockEntityRegistry.MoveInfo info, CallbackInfo ci) {
        ShipHeightFix.align(plot, tag, info);
        dev.antme08.planetaryexpansion.transporter.ShipGroups.moveRemnants(plot, info);
    }
}
