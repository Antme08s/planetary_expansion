package dev.antme08.planetaryexpansion.engine;

import dev.antme08.planetaryexpansion.titan.TitanBlocks;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlockEntity;
import dev.devce.rocketnautics.registry.RocketTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Entidad del soporte de metano. Cambios respecto al soporte de Cosmonautics:
 * <ul>
 *   <li>los dos tanques solo aceptan metano o el oxidante de Cosmonautics (agua o aire), no el resto de combustibles;</li>
 *   <li>empuje máximo +25 %, consumo -40 % y calor -15 %, a medio camino entre el queroseno y el diésel de la mezcla
 *       metano-oxígeno (los multiplicadores de la tobera y el ciclo de tuberías se aplican igual que siempre).</li>
 * </ul>
 */
public class MethaneThrusterMountBlockEntity extends ThrusterMountBlockEntity {
    public static final float THRUST = 1.25f;
    public static final float EFFICIENCY = 0.6f;
    public static final float HEAT = 0.85f;

    public MethaneThrusterMountBlockEntity(BlockPos pos, BlockState state) {
        super(MethaneEngine.MOUNT_BE.get(), pos, state);
        tank1.setValidator(MethaneThrusterMountBlockEntity::accepts);
        tank2.setValidator(MethaneThrusterMountBlockEntity::accepts);
    }

    private static boolean accepts(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Fluid f = stack.getFluid();
        return f == TitanBlocks.METHANE.get() || f == TitanBlocks.METHANE_FLOWING.get()
                || stack.is(RocketTags.FluidTags.OXIDIZER.tag);
    }

    @Override
    public float getThrustModifier() {
        return super.getThrustModifier() * THRUST;
    }

    @Override
    public float getEfficiencyModifier() {
        return super.getEfficiencyModifier() * EFFICIENCY;
    }

    @Override
    public float getHeatModifier() {
        return super.getHeatModifier() * HEAT;
    }
}
