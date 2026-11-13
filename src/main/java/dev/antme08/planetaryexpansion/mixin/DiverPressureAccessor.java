package dev.antme08.planetaryexpansion.mixin;

import com.maxenonyme.createsubmarine.submarine.system.DiverPressure;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Acceso a la profundidad que calcula Create Deep Seas para el jugador (método privado). */
@Mixin(value = DiverPressure.class, remap = false)
public interface DiverPressureAccessor {
    @Invoker("depthOf")
    static double planetaryExpansion$depthOf(ServerPlayer player) {
        throw new AssertionError();
    }
}
