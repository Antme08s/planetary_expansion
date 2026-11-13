package dev.antme08.planetaryexpansion.dampener;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * Puerto de refrigerante. No guarda líquido: pasa todo al tanque del núcleo. El manejador busca el núcleo en cada
 * llamada, así las tuberías siguen funcionando aunque el núcleo se ponga o se quite después.
 */
public class DampenerCoolantPortBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private final IFluidHandler handler = new IFluidHandler() {
        private IFluidHandler core() {
            DampenerCoreBlockEntity core = InertialDampener.findCore(level, worldPosition);
            return core == null ? null : core.tank();
        }

        @Override
        public int getTanks() {
            return core() == null ? 0 : 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler c = core();
            return c == null ? FluidStack.EMPTY : c.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler c = core();
            return c == null ? 0 : c.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return core() != null && DampenerCoreBlockEntity.isCoolant(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler c = core();
            return c == null ? 0 : c.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler c = core();
            return c == null ? FluidStack.EMPTY : c.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler c = core();
            return c == null ? FluidStack.EMPTY : c.drain(maxDrain, action);
        }
    };

    public DampenerCoolantPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IFluidHandler fluidHandler() {
        return handler;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        DampenerCoreBlockEntity core = InertialDampener.findCore(level, worldPosition);
        if (core == null) return false;
        core.appendStatus(tooltip);
        return true;
    }
}
