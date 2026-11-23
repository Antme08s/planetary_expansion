package dev.antme08.planetaryexpansion.mixin;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Metano líquido de Titán como combustible de cohete (1.0.4).
 *
 * Cosmonautics decide qué fluidos sirven de combustible con la etiqueta rocketnautics:rocket_fuel (el metano se añade
 * ahí desde los datos; con eso ya lo aceptan el motor modular, el propulsor directo y los tanques). En esta versión de
 * Cosmonautics solo el propulsor directo ajusta el rendimiento según el combustible, buscando trozos del nombre del
 * fluido ("kerosene", "diesel", "gasoline"...); cualquier otro cae en 1,0 / 1,0, igual que la lava. Aquí se le da al
 * metano un valor propio: +30 % de impulso específico y un 40 % menos de consumo que un combustible sin ajustar, a medio
 * camino entre el queroseno y el diésel como corresponde a una mezcla metano-oxígeno. El motor modular (soporte +
 * tuberías + tobera) no distingue combustibles en Cosmonautics, así que allí el metano rinde como el resto.
 *
 * remap = false: las clases de Cosmonautics no están ofuscadas.
 */
@Mixin(value = RocketThrusterBlockEntity.class, remap = false)
public abstract class RocketFuelPropertiesMixin {

    @Shadow public FluidTank fuelTank;
    @Shadow private float currentIspMultiplier;
    @Shadow private float currentEfficiencyMultiplier;

    @Inject(method = "updateFuelProperties", at = @At("TAIL"))
    private void planetaryExpansion$methane(CallbackInfo ci) {
        FluidStack fuel = fuelTank.getFluid();
        if (fuel.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fuel.getFluid());
        if (id.getNamespace().equals(PlanetaryExpansion.MODID) && id.getPath().contains("methane")) {
            currentIspMultiplier = 1.3f;
            currentEfficiencyMultiplier = 0.6f;
        }
    }
}
