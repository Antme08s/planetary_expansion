package dev.antme08.planetaryexpansion.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.joml.Vector3d;

/**
 * Motor de distorsión (0.7.37): 4x4 bloques y 3 de alto. Se carga con mucha rotación de Create por sus dos entradas de
 * eje, se enfría con nitrógeno líquido y, desde la consola de navegación de la misma nave, salta a la órbita de
 * cualquier planeta. Ver {@link WarpDriveBlockEntity}.
 */
public class WarpDriveBlock extends WarpAnchorBlock {
    public WarpDriveBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<Footprint.Cell> cells() {
        return Footprint.DRIVE;
    }

    @Override
    protected BlockState partState(Footprint.Cell cell, Direction facing) {
        if (cell.equals(Footprint.INPUT_LEFT) || cell.equals(Footprint.INPUT_RIGHT)) {
            return Warp.DRIVE_INPUT.get().defaultBlockState().setValue(WarpDriveInputBlock.AXIS, Footprint.left(facing).getAxis());
        }
        return Warp.DRIVE_PART.get().defaultBlockState();
    }

    @Override
    public boolean isOwnPart(BlockState state) {
        return state.getBlock() == Warp.DRIVE_PART.get() || state.getBlock() == Warp.DRIVE_INPUT.get();
    }

    /**
     * Abajo, bloque completo (el cuerpo). En medio: los tanques de las esquinas y el centro (anillo). Arriba: solo el
     * centro. El resto queda libre para pasar por encima del cuerpo.
     */
    @Override
    public VoxelShape partShape(Footprint.Cell c) {
        boolean corner = (c.a() == 0 || c.a() == 3) && (c.b() == -1 || c.b() == 2);
        boolean center = (c.a() == 1 || c.a() == 2) && (c.b() == 0 || c.b() == 1);
        if (c.y() == 0) return Shapes.block();
        if (c.y() == 1) return corner || center ? Shapes.block() : Shapes.empty();
        return center ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // 1.0.3: núcleo de distorsión estelar: el motor pasa a ser estelar ahí mismo (no hay que desmontarlo)
        if (stack.is(Warp.STELLAR_CORE.get()) && level.getBlockEntity(pos) instanceof WarpDriveBlockEntity drive) {
            if (drive.isStellar()) {
                if (!level.isClientSide) player.displayClientMessage(
                        Component.translatable("message.planetary_expansion.warp_already_stellar").withStyle(ChatFormatting.GOLD), true);
                return ItemInteractionResult.CONSUME;
            }
            if (level instanceof ServerLevel sl && drive.makeStellar()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                sl.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 0.6f);
                sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f);
                sl.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.7f);
                Vector3d c = Warp.toWorld(sl, BlockPos.containing(drive.coreCenter().x, drive.coreCenter().y, drive.coreCenter().z));
                sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 70, 0.8, 0.8, 0.8, 0.12);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 90, 1.2, 1.0, 1.2, 0.3);
                player.displayClientMessage(Component.translatable("message.planetary_expansion.warp_upgraded")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBlockEntity(pos) instanceof WarpDriveBlockEntity drive && FluidUtil.getFluidHandler(stack).isPresent()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, drive.tank())) return ItemInteractionResult.sidedSuccess(level.isClientSide);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WarpDriveBlockEntity drive) {
            player.displayClientMessage(drive.summary(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** El motor estelar suelta también su núcleo al romperlo. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof WarpDriveBlockEntity drive && drive.isStellar()) {
            drops = new ArrayList<>(drops);
            drops.add(new ItemStack(Warp.STELLAR_CORE.get()));
        }
        return drops;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarpDriveBlockEntity(Warp.DRIVE_BE.get(), pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != Warp.DRIVE_BE.get()) return null;
        return level.isClientSide
                ? (l, p, s, be) -> ((WarpDriveBlockEntity) be).clientTick()
                : (l, p, s, be) -> ((WarpDriveBlockEntity) be).serverTick();
    }
}
