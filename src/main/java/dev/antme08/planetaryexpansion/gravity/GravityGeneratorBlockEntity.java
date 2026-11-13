package dev.antme08.planetaryexpansion.gravity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.List;

/**
 * Mientras recibe redstone y gira (sin sobrecarga en la red), avisa cada tick a {@link GravityFields} de que su nave tiene
 * gravedad. Corre en servidor y en cliente: el movimiento del jugador se calcula en el cliente.
 */
public class GravityGeneratorBlockEntity extends KineticBlockEntity {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.62f, 0.42f, 1.0f), 0.8f);

    /** Giro del núcleo flotante (solo cliente): acelera al encenderse y frena al apagarse. */
    public float coreAngle = 22.5f, prevCoreAngle = 22.5f, coreSpeed, coreGlow;

    public GravityGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean hasSignal() {
        BlockState state = getBlockState();
        return state.hasProperty(GravityGeneratorBlock.POWERED) && state.getValue(GravityGeneratorBlock.POWERED);
    }

    public boolean isActive() {
        return hasSignal() && getSpeed() != 0 && !isOverStressed();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        boolean active = isActive();
        if (level.isClientSide) {
            // encendido: rápido y brillante; con rotación pero sin redstone: lento y apagado (en espera); sin rotación: quieto
            float target = active ? 9.0f : (getSpeed() != 0 && !isOverStressed() ? 1.2f : 0.0f);
            coreSpeed += (target - coreSpeed) * 0.06f;
            coreGlow += ((active ? 1.0f : 0.0f) - coreGlow) * 0.1f;
            prevCoreAngle = coreAngle;
            coreAngle += coreSpeed;
            if (coreAngle > 360.0f) {
                coreAngle -= 360.0f;
                prevCoreAngle -= 360.0f;
            }
        } else if (level.getGameTime() % 40 == 0) {
            GravityGeneratorBlock.placeTop(level, worldPosition); // repone la parte de arriba si falta
        }
        if (!active) return;
        GravityFields.mark(level, worldPosition);
        if (level instanceof net.minecraft.server.level.ServerLevel sl && sl.getGameTime() % 40 == 0) {
            dev.antme08.planetaryexpansion.advancement.Achievements.awardNear(sl,
                    dev.antme08.planetaryexpansion.warp.Warp.toWorld(sl, worldPosition), 24, "artificial_gravity", "on");
        }
        if (level.isClientSide && level.random.nextInt(6) == 0) {
            double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.9;
            double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.9;
            level.addParticle(GLOW, x, worldPosition.getY() + 1.6, z, 0, -0.04, 0);
        }
    }

    @Override
    protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 1, 0); // el núcleo se dibuja en el bloque de arriba
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        String key = isActive() ? "gravity_on" : !hasSignal() ? "gravity_no_signal" : "gravity_off";
        tooltip.add(Component.literal("    ").append(Component.translatable("message.planetary_expansion." + key)
                .withStyle(isActive() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY)));
        return true;
    }
}
