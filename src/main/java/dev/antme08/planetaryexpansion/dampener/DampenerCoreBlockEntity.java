package dev.antme08.planetaryexpansion.dampener;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Cerebro del amortiguador inercial: comprueba la estructura, se carga con las entradas de rotación, guarda el
 * refrigerante, se enfría y, con un flanco de subida de redstone, frena la nave.
 * Desde 0.7.32 solo lo disparan las entradas de redstone, que además dan la carga a un comparador.
 */
public class DampenerCoreBlockEntity extends BlockEntity {
    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final TagKey<Fluid> NITROGEN = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "nitrogen"));

    public static final int TANK_CAPACITY = 8000;
    /** Ticks para cargar del todo con una entrada a 256 RPM (30 s). Escala con la velocidad. */
    private static final double FULL_CHARGE_TICKS_AT_256 = 600;

    /** Refrigerantes: ticks para enfriarse del todo y mB gastados en un enfriamiento completo. */
    public enum Coolant {
        AIR(12000, 0), WATER(3600, 4000), NITROGEN(900, 1000);
        final double ticks, mbPerCycle;

        Coolant(double ticks, double mbPerCycle) {
            this.ticks = ticks;
            this.mbPerCycle = mbPerCycle;
        }
    }

    // estado (guardado)
    private double charge;   // 0..1
    private double heat;     // 0..1
    private double drainDebt;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY, DampenerCoreBlockEntity::isCoolant) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            dirtySync = true;
        }
    };

    // estado de la estructura (se recalcula; también se envía al cliente para las goggles)
    private int shellCount;
    private int drives;
    private int ports;
    private int redstonePorts;
    private final List<BlockPos> drivePositions = new ArrayList<>();
    private final List<BlockPos> redstonePositions = new ArrayList<>();
    private int lastComparator = -1;
    private boolean lastPowered;
    private int reapplyOrbit;
    private boolean dirtySync;
    private double lastSentCharge = -1, lastSentHeat = -1;

    public DampenerCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static boolean isCoolant(FluidStack stack) {
        return coolantOf(stack) != Coolant.AIR;
    }

    public static Coolant coolantOf(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return Coolant.AIR;
        if (stack.is(NITROGEN)) return Coolant.NITROGEN;
        if (stack.is(FluidTags.WATER)) return Coolant.WATER;
        return Coolant.AIR;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public boolean isFormed() {
        return shellCount == 26;
    }

    public boolean isReady() {
        return isFormed() && charge >= 1.0 && heat <= 0.0;
    }

    // ------------------------------------------------------------------ tick (servidor)

    public static void serverTick(Level level, BlockPos pos, BlockState state, DampenerCoreBlockEntity be) {
        be.tickServer((ServerLevel) level);
    }

    private void tickServer(ServerLevel level) {
        if (level.getGameTime() % 10 == 0 || drivePositions.isEmpty() && shellCount == 0) scanStructure(level);

        // carga
        if (isFormed() && charge < 1.0) {
            double rpm = 0;
            for (BlockPos p : drivePositions) {
                if (level.getBlockEntity(p) instanceof DampenerDriveBlockEntity drive && !drive.isOverStressed()) {
                    rpm += Math.abs(drive.getSpeed());
                }
            }
            if (rpm > 0) {
                charge = Math.min(1.0, charge + rpm / 256.0 / FULL_CHARGE_TICKS_AT_256);
                setChanged();
            }
        }

        // enfriamiento
        if (heat > 0) {
            Coolant c = coolantOf(tank.getFluid());
            double step = 1.0 / c.ticks;
            if (c != Coolant.AIR) {
                double need = c.mbPerCycle * Math.min(step, heat);
                if (tank.getFluidAmount() >= Math.ceil(drainDebt + need)) {
                    drainDebt += need;
                    int whole = (int) drainDebt;
                    if (whole > 0) {
                        tank.drain(whole, IFluidHandler.FluidAction.EXECUTE);
                        drainDebt -= whole;
                    }
                } else {
                    step = 1.0 / Coolant.AIR.ticks; // se acabó el líquido: solo aire
                }
            }
            heat = Math.max(0.0, heat - step);
            setChanged();
        }

        // re-aplicar la órbita circular un par de ticks (absorbe los impulsos que ya estaban en cola)
        if (reapplyOrbit > 0) {
            reapplyOrbit--;
            try {
                SubLevelAccess s = Sable.HELPER.getContaining(level, (net.minecraft.core.Vec3i) worldPosition);
                if (s instanceof ServerSubLevel ship) circularize(level, ship);
            } catch (Throwable ignored) {
            }
        }

        // redstone: flanco de subida en una entrada de redstone
        boolean powered = anyPowered(level);
        if (powered && !lastPowered) fire(level);
        lastPowered = powered;
        updateComparators(level);

        // sincronizar con el cliente (goggles)
        if (dirtySync || Math.abs(charge - lastSentCharge) > 0.005 || Math.abs(heat - lastSentHeat) > 0.005
                || (charge >= 1.0) != (lastSentCharge >= 1.0) || (heat <= 0) != (lastSentHeat <= 0)) {
            if (level.getGameTime() % 5 == 0 || dirtySync) {
                dirtySync = false;
                lastSentCharge = charge;
                lastSentHeat = heat;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void scanStructure(Level level) {
        int count = 0, d = 0, p = 0, r = 0;
        drivePositions.clear();
        redstonePositions.clear();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos at = worldPosition.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(at);
                    if (!InertialDampener.isShell(s)) continue;
                    count++;
                    if (s.getBlock() instanceof DampenerDriveBlock) {
                        d++;
                        drivePositions.add(at);
                    } else if (s.getBlock() instanceof DampenerCoolantPortBlock) {
                        p++;
                    } else if (s.getBlock() instanceof DampenerRedstonePortBlock) {
                        r++;
                        redstonePositions.add(at);
                    }
                }
        if (count != shellCount || d != drives || p != ports || r != redstonePorts) {
            shellCount = count;
            drives = d;
            ports = p;
            redstonePorts = r;
            dirtySync = true;
        }
    }

    /** Solo cuentan las entradas de redstone (desde 0.7.32). */
    private boolean anyPowered(Level level) {
        for (BlockPos at : redstonePositions) {
            if (level.hasNeighborSignal(at)) return true;
        }
        return false;
    }

    /** Señal para comparadores en las entradas de redstone: carga 0-14, 15 = listo para usarse. */
    public int comparatorLevel() {
        if (isReady()) return 15;
        if (!isFormed()) return 0;
        return (int) Math.min(14, Math.floor(charge * 14));
    }

    private void updateComparators(Level level) {
        int now = comparatorLevel();
        if (now == lastComparator) return;
        lastComparator = now;
        for (BlockPos at : redstonePositions) level.updateNeighbourForOutputSignal(at, level.getBlockState(at).getBlock());
    }

    // ------------------------------------------------------------------ disparo

    private void fire(ServerLevel level) {
        SubLevelAccess access = null;
        try {
            access = Sable.HELPER.getContaining(level, (net.minecraft.core.Vec3i) worldPosition);
        } catch (Throwable ignored) {
        }
        ServerSubLevel ship = access instanceof ServerSubLevel s ? s : null;
        Vector3d world = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        if (ship != null) world = ship.logicalPose().transformPosition(world, new Vector3d());

        if (!isFormed()) {
            tell(level, world, Component.translatable("message.planetary_expansion.dampener_incomplete", shellCount).withStyle(ChatFormatting.RED));
            return;
        }
        if (charge < 1.0) {
            tell(level, world, Component.translatable("message.planetary_expansion.dampener_not_charged", pct(charge)).withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (heat > 0.0) {
            tell(level, world, Component.translatable("message.planetary_expansion.dampener_cooling", pct(heat)).withStyle(ChatFormatting.GOLD));
            return;
        }
        if (ship == null || ship.isRemoved()) {
            tell(level, world, Component.translatable("message.planetary_expansion.dampener_no_ship").withStyle(ChatFormatting.RED));
            return;
        }

        Component result;
        try {
            if (level.dimension().location().equals(DEEP_SPACE)) {
                double altitudeKm = circularize(level, ship);
                if (altitudeKm < 0) {
                    tell(level, world, Component.translatable("message.planetary_expansion.dampener_no_orbit").withStyle(ChatFormatting.RED));
                    return;
                }
                reapplyOrbit = 2;
                result = Component.translatable("message.planetary_expansion.dampener_orbit", String.format("%,.0f", altitudeKm));
            } else {
                RigidBodyHandle body = RigidBodyHandle.of(ship);
                if (body == null || !body.isValid()) return;
                Vector3d lin = body.getLinearVelocity(new Vector3d());
                Vector3d ang = body.getAngularVelocity(new Vector3d());
                body.addLinearAndAngularVelocity(lin.negate(), ang.negate());
                result = Component.translatable("message.planetary_expansion.dampener_stopped");
            }
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Amortiguador] Falló al disparar en {}", worldPosition, t);
            return;
        }

        charge = 0;
        heat = 1.0;
        drainDebt = 0;
        dirtySync = true;
        setChanged();
        tell(level, world, result.copy().withStyle(ChatFormatting.AQUA));
        dev.antme08.planetaryexpansion.advancement.Achievements.awardNear(level, world, 48, "full_stop", "fired");
        if (level.dimension().location().equals(DEEP_SPACE)) {
            dev.antme08.planetaryexpansion.advancement.Achievements.awardNear(level, world, 48, "perfect_circle", "orbit");
        }
        level.playSound(null, world.x, world.y, world.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.6f);
        level.playSound(null, world.x, world.y, world.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.5f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, world.x, world.y, world.z, 60, 1.6, 1.6, 1.6, 0.15);
        level.sendParticles(ParticleTypes.SNOWFLAKE, world.x, world.y, world.z, 40, 1.4, 1.4, 1.4, 0.05);
        PlanetaryExpansion.LOGGER.info("[Amortiguador] Disparado en la nave {}: {}", ship.getUniqueId(), result.getString());
    }

    /**
     * Espacio profundo: deja la órbita circular a la distancia actual del cuerpo que la controla, conservando el plano
     * y el sentido de giro, y para el movimiento y el giro de la nave dentro de la instancia.
     *
     * @return altitud sobre la superficie en km, o -1 si la nave no está en ninguna órbita.
     */
    private double circularize(ServerLevel level, ServerSubLevel ship) {
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        if (data == null || data.getUniverse() == null) return -1;
        org.joml.Vector3dc p = ship.logicalPose().position();
        DeepSpaceInstance inst = data.getInstanceForPos((int) p.x(), (int) p.z());
        if (inst == null || inst.isCorrupted()) return -1;

        RigidBodyHandle body = RigidBodyHandle.of(ship);
        if (body != null && body.isValid()) {
            Vector3d lin = body.getLinearVelocity(new Vector3d());
            Vector3d ang = body.getAngularVelocity(new Vector3d());
            body.addLinearAndAngularVelocity(lin.negate(), ang.negate());
        }

        DeepSpacePosition pos = inst.getPosition();
        TimeStampedPVCoordinates coords = pos.getCurrentPVCoords();
        double mu = pos.getOrbit().getMu();
        Vector3D r = coords.getPosition();
        Vector3D v = coords.getVelocity();
        double rn = r.getNorm();
        if (!(mu > 0) || !(rn > 0)) return -1;
        Vector3D h = r.crossProduct(v);
        if (h.getNorm() < 1e-9 * rn * Math.max(1.0, v.getNorm())) {
            // movimiento casi radial: se elige un plano cualquiera que contenga el radio
            h = r.crossProduct(Vector3D.PLUS_J);
            if (h.getNorm() < 1e-9 * rn) h = r.crossProduct(Vector3D.PLUS_I);
        }
        Vector3D dir = h.crossProduct(r).normalize();
        Vector3D vc = dir.scalarMultiply(Math.sqrt(mu / rn));
        pos.init(data.getUniverse(), pos.getFrame(), new TimeStampedPVCoordinates(coords.getDate(), r, vc));
        data.setDirty();
        inst.forceClientSync();

        double radius = 0;
        for (var planet : data.getUniverse().getPlanets()) {
            if (planet.frame().getName().equals(pos.getFrame().getName())) radius = planet.radius();
        }
        return Math.max(0, rn - radius) / 1000.0;
    }

    private static void tell(ServerLevel level, Vector3d at, Component msg) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) < 48 * 48) player.displayClientMessage(msg, true);
        }
    }

    private static String pct(double v) {
        return String.valueOf((int) Math.floor(v * 100));
    }

    // ------------------------------------------------------------------ goggles

    /** Líneas de estado para las goggles de Create (núcleo, entradas de rotación y puertos de refrigerante). */
    public void appendStatus(List<Component> tooltip) {
        String k = "message.planetary_expansion.";
        tooltip.add(Component.literal("    ").append(Component.translatable("block.planetary_expansion.inertial_dampener_core").withStyle(ChatFormatting.WHITE)));
        if (!isFormed()) {
            tooltip.add(line(Component.translatable(k + "dampener_structure", shellCount), ChatFormatting.RED));
            return;
        }
        if (drives == 0) tooltip.add(line(Component.translatable(k + "dampener_no_drive"), ChatFormatting.RED));
        if (redstonePorts == 0) tooltip.add(line(Component.translatable(k + "dampener_no_redstone"), ChatFormatting.RED));
        tooltip.add(line(Component.translatable(k + "dampener_charge", pct(charge)), charge >= 1.0 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        tooltip.add(line(Component.translatable(k + "dampener_heat", pct(heat)), heat > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            tooltip.add(line(Component.translatable(k + "dampener_coolant_none"), ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(line(Component.translatable(k + "dampener_coolant", fluid.getHoverName(), fluid.getAmount(), TANK_CAPACITY), ChatFormatting.BLUE));
        }
        if (isReady()) tooltip.add(line(Component.translatable(k + "dampener_ready"), ChatFormatting.GREEN));
    }

    private static Component line(Component c, ChatFormatting style) {
        return Component.literal("     ").append(c.copy().withStyle(style));
    }

    // ------------------------------------------------------------------ guardado y sincronización

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeState(tag, registries);
        tag.putDouble("DrainDebt", drainDebt);
        tag.putBoolean("LastPowered", lastPowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = tag.getDouble("Charge");
        heat = tag.getDouble("Heat");
        shellCount = tag.getInt("Shell");
        drives = tag.getInt("Drives");
        ports = tag.getInt("Ports");
        redstonePorts = tag.getInt("RedstonePorts");
        tank.setFluid(FluidStack.EMPTY);
        if (tag.contains("Tank")) tank.readFromNBT(registries, tag.getCompound("Tank"));
        if (tag.contains("DrainDebt")) drainDebt = tag.getDouble("DrainDebt");
        if (tag.contains("LastPowered")) lastPowered = tag.getBoolean("LastPowered");
    }

    private void writeState(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("Charge", charge);
        tag.putDouble("Heat", heat);
        tag.putInt("Shell", shellCount);
        tag.putInt("Drives", drives);
        tag.putInt("Ports", ports);
        tag.putInt("RedstonePorts", redstonePorts);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeState(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
