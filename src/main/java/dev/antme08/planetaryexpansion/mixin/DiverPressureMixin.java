package dev.antme08.planetaryexpansion.mixin;

import com.maxenonyme.createsubmarine.submarine.system.DiverPressure;
import dev.antme08.planetaryexpansion.armor.SeaDiverArmor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Presión del mar de Create Deep Seas con la Sea Diver Armor (0.7.24): con el traje completo no hay daño por
 * presión hasta {@link SeaDiverArmor#SAFE_DEPTH} (600 m). Más hondo, Deep Seas aplica sus reglas tratándolo como su
 * traje de buceo de cobre (que aguanta 280 m), así que las fosas más profundas siguen siendo peligrosas.
 */
@Mixin(value = DiverPressure.class, remap = false)
public abstract class DiverPressureMixin {

    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true, require = 0)
    private static void planetaryExpansion$seaDiverSafe(PlayerTickEvent.Post event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SeaDiverArmor.fullSet(player)) return;
        try {
            if (DiverPressureAccessor.planetaryExpansion$depthOf(player) < SeaDiverArmor.SAFE_DEPTH) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "wearsCopperSuit", at = @At("HEAD"), cancellable = true, require = 0)
    private static void planetaryExpansion$countsAsSuit(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (SeaDiverArmor.fullSet(player)) cir.setReturnValue(true);
    }
}
