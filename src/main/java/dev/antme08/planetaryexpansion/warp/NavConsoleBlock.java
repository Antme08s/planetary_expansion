package dev.antme08.planetaryexpansion.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * Consola de navegación (0.7.37): mesa de 2x2 con un sistema solar holográfico encima. Al usarla se abre el mapa del
 * sistema solar; desde ahí se ordena el salto al motor de distorsión de la misma nave.
 */
public class NavConsoleBlock extends WarpAnchorBlock {
    public NavConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<Footprint.Cell> cells() {
        return Footprint.CONSOLE;
    }

    @Override
    protected BlockState partState(Footprint.Cell cell, Direction facing) {
        return Warp.CONSOLE_PART.get().defaultBlockState();
    }

    @Override
    public boolean isOwnPart(BlockState state) {
        return state.getBlock() == Warp.CONSOLE_PART.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide && FMLEnvironment.dist.isClient()) {
            dev.antme08.planetaryexpansion.warp.client.WarpClient.openMap(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NavConsoleBlockEntity(Warp.CONSOLE_BE.get(), pos, state);
    }
}
