package dev.antme08.planetaryexpansion.transporter;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.NavStatusPayload;
import dev.antme08.planetaryexpansion.warp.Warp;
import dev.antme08.planetaryexpansion.warp.WarpDriveBlockEntity;
import dev.antme08.planetaryexpansion.warp.WarpStatus;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Rotation;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3d;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transportador espacial. Se carga con un eje de Create (a 256 RPM tarda {@value #FULL_CHARGE_TICKS_AT_256} ticks) y cada
 * subida gasta {@value #LAUNCH_MB} mB de nitrógeno líquido (el salto del motor de distorsión, 4000). Solo funciona en la
 * superficie de un planeta, montado en una nave, y solo para subir a la órbita de ese mismo planeta: el destino y la altura
 * se eligen en el mapa de la consola de navegación de la nave.
 */
public class SpaceTransporterBlockEntity extends KineticBlockEntity {
    private static final TagKey<Fluid> NITROGEN = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "nitrogen"));
    private static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");

    public static final int TANK_CAPACITY = 8000;
    public static final int LAUNCH_MB = 1000;
    public static final double FULL_CHARGE_TICKS_AT_256 = 600;

    private static final Set<SpaceTransporterBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    private double charge;
    private double lastRpm;
    private double sentCharge = -1;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY, s -> s.is(NITROGEN)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) sendData();
        }
    };

    public SpaceTransporterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IFluidHandler tank() {
        return tank;
    }

    public int nitrogen() {
        return tank.getFluidAmount();
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        LOADED.add(this);
        double rpm = isOverStressed() ? 0 : Math.abs(getSpeed());
        lastRpm = rpm;
        if (charge < 1.0 && rpm > 0 && !SpaceLaunch.isPending(Warp.shipAt(sl, worldPosition))) {
            charge = Math.min(1.0, charge + rpm / 256.0 / FULL_CHARGE_TICKS_AT_256);
            setChanged();
        }
        if (Math.abs(charge - sentCharge) >= 0.01 || (charge >= 1.0 && sentCharge < 1.0)) {
            sentCharge = charge;
            sendData();
        }
    }

    @Override
    public void remove() {
        LOADED.remove(this);
        super.remove();
    }

    /** Transportador de la nave de la consola (el más cercano), o null. */
    public static SpaceTransporterBlockEntity findFor(ServerLevel level, BlockPos console) {
        ServerSubLevel ship = Warp.shipAt(level, console);
        if (ship == null) return null;
        SpaceTransporterBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (SpaceTransporterBlockEntity t : LOADED) {
            if (t.isRemoved() || t.level != level) {
                if (t.isRemoved()) LOADED.remove(t);
                continue;
            }
            ServerSubLevel other = Warp.shipAt(level, t.worldPosition);
            if (other == null || !other.getUniqueId().equals(ship.getUniqueId())) continue;
            double d = t.worldPosition.distSqr(console);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ estado

    /** Planeta de la dimensión en la que está el bloque (null en el espacio o en una dimensión sin planeta). */
    static CubePlanet planetHere(ServerLevel level) {
        if (level.dimension().location().equals(DEEP_SPACE)) return null;
        try {
            DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
            return data == null || data.getUniverse() == null ? null : data.getUniverse().getPlanetByDimension(level.dimension());
        } catch (Throwable t) {
            return null;
        }
    }

    static String nameOf(CubePlanet planet) {
        try {
            return planet.frame().getName();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean allowsSpace(CubePlanet planet) {
        try {
            return planet.linkedDimension() == null || planet.linkedDimension().allowedTransfer().allowToSpace();
        } catch (Throwable t) {
            return true;
        }
    }

    public WarpStatus status(ServerLevel level, String target) {
        ServerSubLevel ship = Warp.shipAt(level, worldPosition);
        if (ship != null && SpaceLaunch.isPending(ship)) return WarpStatus.JUMPING;
        if (charge < 1.0) return lastRpm > 0 ? WarpStatus.CHARGING : WarpStatus.NO_ROTATION;
        if (tank.getFluidAmount() < LAUNCH_MB) return WarpStatus.NEEDS_NITROGEN;
        if (ship == null) return WarpStatus.NOT_ON_SHIP;
        CubePlanet planet = planetHere(level);
        if (planet == null || !allowsSpace(planet)) return WarpStatus.NO_PLANET;
        if (target != null && !target.isEmpty() && !target.equals(nameOf(planet))) return WarpStatus.ONLY_THIS_PLANET;
        return WarpStatus.READY;
    }

    public NavStatusPayload statusFor(ServerLevel level, BlockPos console, String target) {
        return new NavStatusPayload(console, status(level, target).ordinal(), (float) charge, 0f, tank.getFluidAmount(),
                (float) lastRpm, "", false, true, -1);
    }

    /** Lo pide un jugador desde el mapa de la consola. */
    public void tryLaunch(ServerPlayer player, String target, double altitudeKm) {
        if (!(level instanceof ServerLevel sl)) return;
        WarpStatus st = status(sl, target);
        if (st != WarpStatus.READY) {
            player.displayClientMessage(Component.translatable("message.planetary_expansion.transporter_not_ready", st.text()), true);
            return;
        }
        CubePlanet planet = planetHere(sl);
        String name = nameOf(planet);
        DeepSpaceData data = DeepSpaceData.getInstance(sl.getServer());
        if (data == null || WarpDriveBlockEntity.arrival(data.getUniverse(), name, altitudeKm) == null) {
            player.displayClientMessage(Component.translatable("message.planetary_expansion.warp_bad_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerSubLevel ship = Warp.shipAt(sl, worldPosition);
        net.neoforged.neoforge.fluids.FluidStack drained = tank.drain(LAUNCH_MB, IFluidHandler.FluidAction.EXECUTE).copy();
        charge = 0;
        sentCharge = -1;
        setChanged();
        SpaceLaunch.request(sl, ship, worldPosition, name, altitudeKm, drained);
        Vector3d c = Warp.toWorld(sl, worldPosition);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.5, c.z, 80, 1.5, 1.0, 1.5, 0.3);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
        PlanetaryExpansion.LOGGER.info("[Transportador] {} sube la nave {} a la órbita de {} ({} km)", player.getScoreboardName(),
                ship.getUniqueId(), name, altitudeKm > 0 ? String.format("%,.0f", altitudeKm) : "predeterminada");
    }

    // ------------------------------------------------------------------ bajar al planeta (1.0.4)

    private DeepSpaceInstance instanceOf(ServerLevel level, ServerSubLevel ship) {
        if (ship == null || !level.dimension().location().equals(DEEP_SPACE)) return null;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        if (data == null || data.getUniverse() == null) return null;
        org.joml.Vector3dc p = ship.logicalPose().position();
        DeepSpaceInstance inst = data.getInstanceForPos((int) Math.floor(p.x()), (int) Math.floor(p.z()));
        return inst == null || inst.isCorrupted() ? null : inst;
    }

    private static CubePlanet planetNamed(UniverseDefinition universe, String name) {
        if (universe == null || name == null) return null;
        for (CubePlanet p : universe.getPlanets()) if (nameOf(p).equals(name)) return p;
        return null;
    }

    private static boolean allowsLanding(CubePlanet planet) {
        try {
            return planet != null && planet.linkedDimension() != null && planet.linkedDimension().allowedTransfer().allowToDimension();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Estado para "Bajar al planeta" (en órbita). {@code target}: astro del menú ("": el que se orbita). */
    public WarpStatus descendStatus(ServerLevel level, String target) {
        ServerSubLevel ship = Warp.shipAt(level, worldPosition);
        if (ship == null) return WarpStatus.NOT_ON_SHIP;
        if (SpaceLaunch.isDescending(ship)) return WarpStatus.JUMPING;
        if (charge < 1.0) return lastRpm > 0 ? WarpStatus.CHARGING : WarpStatus.NO_ROTATION;
        if (tank.getFluidAmount() < LAUNCH_MB) return WarpStatus.NEEDS_NITROGEN;
        DeepSpaceInstance inst = instanceOf(level, ship);
        if (inst == null) return WarpStatus.NOT_ON_SHIP;
        String orbiting;
        try {
            orbiting = inst.getPosition().getFrame().getName();
        } catch (Throwable t) {
            return WarpStatus.NOT_ORBITING;
        }
        if (target != null && !target.isEmpty() && !target.equals(orbiting)) return WarpStatus.NOT_ORBITING;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        CubePlanet planet = planetNamed(data == null ? null : data.getUniverse(), orbiting);
        if (planet == null) return WarpStatus.NOT_ORBITING;   // orbitando una estrella
        if (!allowsLanding(planet)) return WarpStatus.NO_LANDING;
        return WarpStatus.READY;
    }

    /**
     * Baja la nave al planeta que orbita: la coloca justo debajo de donde está, por dentro de la altura de transición del
     * planeta, y Cosmonautics la pasa a la dimensión del planeta como si hubiera bajado con motores (aparece a la altura de
     * transición menos 1000 bloques: 19 000 en la Tierra).
     */
    public void tryDescend(ServerPlayer player, String target) {
        if (!(level instanceof ServerLevel sl)) return;
        WarpStatus st = descendStatus(sl, target);
        if (st != WarpStatus.READY) {
            player.displayClientMessage(Component.translatable("message.planetary_expansion.transporter_not_ready", st.text()), true);
            return;
        }
        ServerSubLevel ship = Warp.shipAt(sl, worldPosition);
        DeepSpaceInstance inst = instanceOf(sl, ship);
        DeepSpaceData data = DeepSpaceData.getInstance(sl.getServer());
        try {
            UniverseDefinition universe = data.getUniverse();
            DeepSpacePosition pos = inst.getPosition();
            String name = pos.getFrame().getName();
            CubePlanet planet = planetNamed(universe, name);
            WarpDriveBlockEntity.OrbitRange range = WarpDriveBlockEntity.orbitRange(universe, name);
            TimeStampedPVCoordinates cur = pos.getCurrentPVCoords();
            Rotation rot = planet.getRotationAtTime(cur.getDate());
            Vector3D body = rot.applyInverseTo(cur.getPosition());   // en ejes del planeta (el cubo gira con él)
            if (body.getNorm() < 1) body = Vector3D.PLUS_I;
            double r = planet.radius();
            double th = planet.linkedDimension().transitionHeight();
            double h = Math.max(100.0, Math.min(th * 0.5, th - 2000.0));   // altura sobre la superficie, dentro de la transición
            boolean sphere = false;
            try {
                sphere = dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isSphereShape();
            } catch (Throwable ignored) {
            }
            Vector3D b;
            if (sphere) {
                b = body.normalize().scalarMultiply(r + h);
            } else {
                double m = Math.max(Math.abs(body.getX()), Math.max(Math.abs(body.getY()), Math.abs(body.getZ())));
                b = body.scalarMultiply((r + h) / m);
                double lim = 0.97 * r;   // lejos de las aristas del cubo, sobre la cara de debajo
                double x = b.getX(), y = b.getY(), z = b.getZ();
                if (Math.abs(x) < r + h - 1) x = Math.max(-lim, Math.min(lim, x));
                if (Math.abs(y) < r + h - 1) y = Math.max(-lim, Math.min(lim, y));
                if (Math.abs(z) < r + h - 1) z = Math.max(-lim, Math.min(lim, z));
                b = new Vector3D(x, y, z);
            }
            Vector3D p = rot.applyTo(b);
            // una pizca de velocidad lateral para que la órbita no sea degenerada (solo dura hasta el tick siguiente)
            Vector3D t = Vector3D.PLUS_J.crossProduct(p);
            if (t.getNorm() < 0.2 * p.getNorm()) t = Vector3D.PLUS_I.crossProduct(p);
            double mu = range != null ? range.mu() : 1.0;
            Vector3D v = t.normalize().scalarMultiply(0.05 * Math.sqrt(mu / p.getNorm()));
            pos.init(universe, pos.getFrame(), new TimeStampedPVCoordinates(cur.getDate(), p, v));
            data.setDirty();
            inst.forceClientSync();
            net.neoforged.neoforge.fluids.FluidStack drained = tank.drain(LAUNCH_MB, IFluidHandler.FluidAction.EXECUTE).copy();
            charge = 0;
            sentCharge = -1;
            setChanged();
            SpaceLaunch.descending(ship, inst.getId(), planet.linkedDimension().key());
            inst.collectOnlinePlayers(sl.getServer(), pl -> pl.displayClientMessage(Component.translatable(
                    "message.planetary_expansion.transporter_descending", name).withStyle(ChatFormatting.LIGHT_PURPLE), true));
            Vector3d c = Warp.toWorld(sl, worldPosition);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.5, c.z, 80, 1.5, 1.0, 1.5, 0.3);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
            PlanetaryExpansion.LOGGER.info("[Transportador] {} baja la nave {} a {} ({} m sobre la superficie; gasta {} mB)",
                    player.getScoreboardName(), ship.getUniqueId(), name, (int) h, drained.getAmount());
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.error("[Transportador] Falló la bajada", t);
            player.displayClientMessage(Component.translatable("message.planetary_expansion.transporter_failed").withStyle(ChatFormatting.RED), true);
        }
    }

    /** La subida no llegó a ocurrir: devolver el nitrógeno y la carga. */
    void refund(net.neoforged.neoforge.fluids.FluidStack fluid) {
        if (fluid != null && !fluid.isEmpty()) tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        charge = 1.0;
        sentCharge = -1;
        setChanged();
    }

    // ------------------------------------------------------------------ textos

    private static String pct(double v) {
        return String.valueOf((int) Math.floor(v * 100));
    }

    public Component summary() {
        return Component.translatable("message.planetary_expansion.transporter_summary", pct(charge), tank.getFluidAmount(), TANK_CAPACITY)
                .withStyle(charge >= 1.0 && tank.getFluidAmount() >= LAUNCH_MB ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        String k = "message.planetary_expansion.";
        tooltip.add(Component.literal("    ").append(Component.translatable("block.planetary_expansion.space_transporter").withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(Component.translatable(k + "warp_charge", pct(charge)), charge >= 1.0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        tooltip.add(line(Component.translatable(k + "warp_nitrogen", tank.getFluidAmount(), TANK_CAPACITY),
                tank.getFluidAmount() >= LAUNCH_MB ? ChatFormatting.AQUA : ChatFormatting.RED));
        if (charge >= 1.0 && tank.getFluidAmount() >= LAUNCH_MB)
            tooltip.add(line(Component.translatable(k + "transporter_ready"), ChatFormatting.GREEN));
        return true;
    }

    private static Component line(Component c, ChatFormatting style) {
        return Component.literal("     ").append(c.copy().withStyle(style));
    }

    // ------------------------------------------------------------------ guardado y sincronización

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("Charge", charge);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        charge = tag.getDouble("Charge");
        tank.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        if (tag.contains("Tank")) tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
