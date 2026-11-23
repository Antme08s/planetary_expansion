package dev.antme08.planetaryexpansion.warp;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import dev.devce.rocketnautics.shaded.orekit.utils.TimeStampedPVCoordinates;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cerebro del motor de distorsión.
 *
 * <ul>
 *   <li>Se carga con la rotación de sus dos entradas de eje (una entrada a 256 RPM: 60 s; las dos: 30 s).</li>
 *   <li>Necesita nitrógeno líquido (Create Nuclear) en su tanque: cada salto gasta {@value #JUMP_MB} mB y después el
 *       motor queda caliente; para enfriarlo gasta otros {@value #COOL_MB} mB en un minuto. Sin nitrógeno no se
 *       enfría.</li>
 *   <li>Salta solo desde el espacio profundo y montado en una nave: la instancia entera (la nave, sus jugadores y
 *       todo lo que haya alrededor) pasa a una órbita circular del planeta elegido.</li>
 * </ul>
 * El salto dura {@value #END_TICK} ticks: los jugadores ven el efecto (estrellas que se estiran, destello, túnel y el
 * nombre del planeta) y la órbita cambia a mitad del túnel, en el tick {@value #TELEPORT_TICK}.
 *
 * <p>1.0.3, motor estelar: con el núcleo de distorsión estelar puesto la carga llega al 200 %. Un motor normal solo
 * salta dentro de su sistema solar (y siempre puede volver al Sol, para que nadie se quede atrapado). Un salto a otra
 * estrella gasta {@value #INTERSTELLAR_MB} mB, el 200 % de la carga (el motor normal que vuelve al Sol, el 100 %) y
 * dura el doble ({@value #STELLAR_END_TICK} ticks).
 */
public class WarpDriveBlockEntity extends BlockEntity {
    static final ResourceLocation DEEP_SPACE = ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space");
    private static final TagKey<Fluid> NITROGEN = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "nitrogen"));

    public static final int TANK_CAPACITY = 32000;
    public static final int JUMP_MB = 4000;
    public static final int COOL_MB = 8000;
    private static final double COOL_TICKS = 1200;
    private static final double FULL_CHARGE_TICKS_AT_256 = 1200;
    public static final int TELEPORT_TICK = 80;
    public static final int END_TICK = 160;
    public static final int INTERSTELLAR_MB = 12000;
    public static final int STELLAR_TELEPORT_TICK = 160;
    public static final int STELLAR_END_TICK = 320;

    /** Motores cargados en el servidor (para que la consola encuentre el de su nave). */
    private static final Set<WarpDriveBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    // estado (guardado)
    private double charge;
    private double heat;
    private double drainDebt;
    private int jumpTick = -1;
    private String target = "";
    /** Altitud de la órbita de llegada pedida en el mapa (km sobre el centro de las caras; <= 0: la de siempre). 1.0.4 */
    private double targetAlt;
    private boolean stellar;
    private boolean interstellarJump;
    private final FluidTank tank = new FluidTank(TANK_CAPACITY, s -> s.is(NITROGEN)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            dirtyTank = true; // el nivel de los tanques se envía cada 5 ticks (al enfriarse cambia cada tick)
        }
    };

    // servidor
    private double lastRpm;
    private int reapplyStop;
    private FluidStack refund = FluidStack.EMPTY;
    private boolean dirtySync, dirtyTank;
    private double sentCharge = -1, sentHeat = -1;

    // cliente (animación del núcleo)
    public float coreAngle, prevCoreAngle, coreSpeed, coreGlow;
    /** Nivel de nitrógeno que se ve en los tanques (0..1), suavizado. */
    public float shownFill = -1, prevShownFill;
    private boolean clientJumping;

    public WarpDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IFluidHandler tank() {
        return tank;
    }

    public double charge() {
        return charge;
    }

    public double heat() {
        return heat;
    }

    public int nitrogen() {
        return tank.getFluidAmount();
    }

    public double rpm() {
        return lastRpm;
    }

    public boolean isStellar() {
        return stellar;
    }

    /** Carga máxima: 100 % (normal) o 200 % (estelar). */
    public double maxCharge() {
        return stellar ? 2.0 : 1.0;
    }

    /** Pone el núcleo estelar. false si ya lo tenía. */
    public boolean makeStellar() {
        if (stellar) return false;
        stellar = true;
        dirtySync = true;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    /**
     * Motivo por el que no se puede saltar de un sistema a otro (null si se puede o si es el mismo sistema). Lo usa el
     * servidor al saltar y la consola para avisar antes.
     */
    public static Component interstellarProblem(boolean stellar, double charge, int nitrogen, String fromSystem, String toSystem) {
        if (fromSystem == null || toSystem == null || fromSystem.equals(toSystem)) return null;
        String k = "message.planetary_expansion.";
        if (!stellar && !toSystem.equals("sol")) return Component.translatable(k + "warp_needs_stellar").withStyle(ChatFormatting.RED);
        if (stellar && charge < 2.0 - 1e-6)
            return Component.translatable(k + "warp_needs_overcharge", (int) Math.floor(charge * 100)).withStyle(ChatFormatting.GOLD);
        if (nitrogen < INTERSTELLAR_MB)
            return Component.translatable(k + "warp_needs_n2_interstellar", INTERSTELLAR_MB).withStyle(ChatFormatting.RED);
        return null;
    }

    public boolean isJumping() {
        return level != null && level.isClientSide ? clientJumping : jumpTick >= 0;
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(WarpAnchorBlock.FACING) ? s.getValue(WarpAnchorBlock.FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------------ registro de motores cargados

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    /** Motor de la misma nave que la consola (o, fuera de una nave, el más cercano a 48 bloques). */
    public static WarpDriveBlockEntity find(ServerLevel level, BlockPos console) {
        ServerSubLevel ship = Warp.shipAt(level, console);
        WarpDriveBlockEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (WarpDriveBlockEntity d : LOADED) {
            if (d.isRemoved() || d.level != level) continue;
            ServerSubLevel other = Warp.shipAt(level, d.worldPosition);
            boolean same = ship != null
                    ? other != null && other.getUniqueId().equals(ship.getUniqueId())
                    : other == null && d.worldPosition.distSqr(console) < 48 * 48;
            if (!same) continue;
            double score = d.worldPosition.distSqr(console) - (d.status(level) == WarpStatus.READY ? 1e9 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    /**
     * Motor que se usaría para viajar a {@code targetName} (1.0.4): con varios motores en la nave se prefiere uno que
     * pueda hacer ese salto ahora mismo (si el destino es otra estrella, uno estelar y con carga de sobra; si es del
     * mismo sistema, el normal, para no gastar la carga del estelar), después uno listo aunque no pueda, y por último el
     * más cercano a la consola. Sin destino, el de siempre.
     */
    public static WarpDriveBlockEntity findFor(ServerLevel level, BlockPos console, String targetName) {
        if (targetName == null || targetName.isEmpty()) return find(level, console);
        ServerSubLevel ship = Warp.shipAt(level, console);
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        var universe = data == null ? null : data.getUniverse();
        String to = StarSystems.systemOf(universe, targetName);
        WarpDriveBlockEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (WarpDriveBlockEntity d : LOADED) {
            if (d.isRemoved() || d.level != level) continue;
            ServerSubLevel other = Warp.shipAt(level, d.worldPosition);
            boolean same = ship != null
                    ? other != null && other.getUniqueId().equals(ship.getUniqueId())
                    : other == null && d.worldPosition.distSqr(console) < 48 * 48;
            if (!same) continue;
            String from = StarSystems.systemOf(universe, d.currentOrbit(level));
            boolean ready = d.status(level) == WarpStatus.READY;
            boolean able = ready && interstellarProblem(d.stellar, d.charge, d.tank.getFluidAmount(), from, to) == null;
            boolean interstellar = !from.equals(to);
            double score = d.worldPosition.distSqr(console)
                    + (able ? 0 : ready ? 1e12 : 2e12)
                    + (able && d.stellar != interstellar ? 1e9 : 0);   // a igualdad de capacidad, el normal para viajes cortos
            if (score < bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ tick

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 40 == 0 && getBlockState().getBlock() instanceof WarpAnchorBlock anchor) {
            anchor.placeParts(sl, worldPosition, getBlockState()); // repone las partes que falten
        }

        // carga
        double rpm = 0;
        Direction f = facing();
        for (Footprint.Cell c : new Footprint.Cell[]{Footprint.INPUT_LEFT, Footprint.INPUT_RIGHT}) {
            if (sl.getBlockEntity(Footprint.pos(worldPosition, f, c)) instanceof WarpDriveInputBlockEntity in && !in.isOverStressed()) {
                rpm += Math.abs(in.getSpeed());
            }
        }
        if (rpm != lastRpm) {
            lastRpm = rpm;
        }
        if (jumpTick < 0 && charge < maxCharge() && rpm > 0) {
            charge = Math.min(maxCharge(), charge + rpm / 256.0 / FULL_CHARGE_TICKS_AT_256);
            setChanged();
        }

        // enfriamiento: solo con nitrógeno
        if (jumpTick < 0 && heat > 0) {
            double need = COOL_MB / COOL_TICKS;
            if (tank.getFluidAmount() >= Math.ceil(drainDebt + need)) {
                drainDebt += need;
                int whole = (int) drainDebt;
                if (whole > 0) {
                    tank.drain(whole, IFluidHandler.FluidAction.EXECUTE);
                    drainDebt -= whole;
                }
                heat = Math.max(0.0, heat - 1.0 / COOL_TICKS);
                setChanged();
            }
        }

        // salto en curso
        if (jumpTick >= 0) {
            jumpTick++;
            if (jumpTick == (interstellarJump ? STELLAR_TELEPORT_TICK : TELEPORT_TICK)) teleport(sl);
            if (jumpTick >= (interstellarJump ? STELLAR_END_TICK : END_TICK)) {
                jumpTick = -1;
                target = "";
                targetAlt = 0;
                interstellarJump = false;
                dirtySync = true;
            }
            setChanged();
        }
        if (reapplyStop > 0) {
            reapplyStop--;
            stopShip(Warp.shipAt(sl, worldPosition));
        }

        // sincronizar con el cliente (goggles y animación)
        if (dirtyTank && sl.getGameTime() % 5 == 0) dirtySync = true;
        if (dirtySync || Math.abs(charge - sentCharge) > 0.01 || Math.abs(heat - sentHeat) > 0.01
                || (charge >= 1.0) != (sentCharge >= 1.0) || (heat <= 0) != (sentHeat <= 0)) {
            if (dirtySync || sl.getGameTime() % 10 == 0) {
                dirtySync = false;
                dirtyTank = false;
                sentCharge = charge;
                sentHeat = heat;
                sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    void clientTick() {
        float targetFill = tank.getFluidAmount() / (float) TANK_CAPACITY;
        if (shownFill < 0) shownFill = targetFill;
        prevShownFill = shownFill;
        shownFill += (targetFill - shownFill) * 0.08f;
        float targetSpeed = clientJumping ? 40f : heat > 0 ? 1.0f : charge >= 2.0 ? 10f : charge >= 1.0 ? 6f : (float) (1.0 + 3.0 * charge);
        coreSpeed += (targetSpeed - coreSpeed) * (clientJumping ? 0.08f : 0.04f);
        float glow = clientJumping ? 1f : heat > 0 ? 0.25f : (float) (0.35 + 0.65 * charge);
        coreGlow += (glow - coreGlow) * 0.08f;
        prevCoreAngle = coreAngle;
        coreAngle += coreSpeed;
        if (coreAngle > 360f) {
            coreAngle -= 360f;
            prevCoreAngle -= 360f;
        }
        if (level != null && (clientJumping || (charge >= 1.0 && heat <= 0)) && level.random.nextInt(clientJumping ? 1 : 5) == 0) {
            // chispas violetas alrededor del núcleo
            Vector3d c = coreCenter();
            double a = level.random.nextDouble() * Math.PI * 2, r = 0.6 + level.random.nextDouble() * 0.8;
            level.addParticle(stellar && level.random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + (level.random.nextDouble() - 0.5), c.z + Math.sin(a) * r, 0, 0, 0);
        }
        if (level != null && heat > 0 && level.random.nextInt(4) == 0) {
            Vector3d c = coreCenter();
            level.addParticle(ParticleTypes.SNOWFLAKE, c.x + (level.random.nextDouble() - 0.5) * 3, c.y - 0.6, c.z + (level.random.nextDouble() - 0.5) * 3, 0, 0.02, 0);
        }
    }

    /** Centro del núcleo flotante (coordenadas del bloque, dentro de la nave si la hay). */
    public Vector3d coreCenter() {
        Direction f = facing(), l = Footprint.left(f);
        double cx = worldPosition.getX() + 0.5 + 1.5 * f.getStepX() + 0.5 * l.getStepX();
        double cz = worldPosition.getZ() + 0.5 + 1.5 * f.getStepZ() + 0.5 * l.getStepZ();
        return new Vector3d(cx, worldPosition.getY() + 31.0 / 16.0, cz);
    }

    // ------------------------------------------------------------------ estado

    public WarpStatus status(ServerLevel level) {
        if (jumpTick >= 0) return WarpStatus.JUMPING;
        if (heat > 0) return tank.getFluidAmount() > 0 ? WarpStatus.COOLING : WarpStatus.NO_COOLANT;
        if (charge < 1.0) return lastRpm > 0 ? WarpStatus.CHARGING : WarpStatus.NO_ROTATION;
        if (tank.getFluidAmount() < JUMP_MB) return WarpStatus.NEEDS_NITROGEN;
        if (Warp.shipAt(level, worldPosition) == null) return WarpStatus.NOT_ON_SHIP;
        if (!level.dimension().location().equals(DEEP_SPACE)) return WarpStatus.NOT_IN_SPACE;
        return WarpStatus.READY;
    }

    /** Nombre del cuerpo que la instancia de la nave está orbitando ("" si no se sabe). */
    public String currentOrbit(ServerLevel level) {
        DeepSpaceInstance inst = instance(level, Warp.shipAt(level, worldPosition));
        if (inst == null) return "";
        try {
            return inst.getPosition().getFrame().getName();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Distancia de la nave al centro del astro que orbita (0 si no se sabe). */
    private static double currentOrbitRadius(DeepSpaceInstance inst) {
        try {
            return inst.getPosition().getCurrentPVCoords().getPosition().getNorm();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static DeepSpaceInstance instance(ServerLevel level, ServerSubLevel ship) {
        if (ship == null || !level.dimension().location().equals(DEEP_SPACE)) return null;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        if (data == null || data.getUniverse() == null) return null;
        org.joml.Vector3dc p = ship.logicalPose().position();
        DeepSpaceInstance inst = data.getInstanceForPos((int) Math.floor(p.x()), (int) Math.floor(p.z()));
        return inst == null || inst.isCorrupted() ? null : inst;
    }

    // ------------------------------------------------------------------ salto

    /**
     * Órbita de llegada: circular, en el ecuador, a max(200 km, 0,3 radios) por encima de las esquinas del planeta
     * (cubo) y, si cabe, dentro de la zona de influencia. La altura que se muestra es sobre el centro de las caras.
     */
    public record Arrival(Frame frame, double mu, double planetRadius, double radius) {
        public double altitudeKm() {
            return (radius - planetRadius) / 1000.0;
        }
    }

    /**
     * Órbitas posibles alrededor de un astro (1.0.4, selector de distancia del mapa). Radios medidos desde el centro del
     * astro: {@code rMin} libra las esquinas del cubo con margen, {@code rMax} queda dentro de la zona de influencia
     * (o, en una estrella, a 60 radios) y {@code rDefault} es la órbita de siempre.
     */
    public record OrbitRange(Frame frame, double mu, double planetRadius, double rMin, double rMax, double rDefault, boolean star) {
        public double altitudeKm(double r) {
            return (r - planetRadius) / 1000.0;
        }

        public double radiusFor(double altitudeKm) {
            return Math.max(rMin, Math.min(rMax, planetRadius + altitudeKm * 1000.0));
        }

        /** Período de una órbita circular de radio r, en segundos. */
        public double period(double r) {
            return 2.0 * Math.PI * Math.sqrt(r * r * r / mu);
        }
    }

    public static OrbitRange orbitRange(UniverseDefinition universe, String name) {
        if (universe == null || name == null || name.isEmpty()) return null;
        CubePlanet planet = null;
        for (CubePlanet p : universe.getPlanets()) if (p.frame().getName().equals(name)) planet = p;
        PointGravitySource source = null;
        for (PointGravitySource s : universe.getGravitySources()) if (s.frame().getName().equals(name)) source = s;
        if (planet == null || source == null || !(source.mu() > 0)) return null;
        boolean star = StarSystems.isStar(planet);   // 1.0.4: las estrellas (Sol y Ámbar) también se pueden orbitar
        Frame frame = universe.getFrameByName(name).orElse(null);
        if (frame == null) frame = planet.frame().getOrekitFrame();
        double big = planet.radius();
        // 0.7.38: los planetas de Cosmonautics son cubos (el radio es medio lado). En 0.7.37 la órbita era un círculo
        // de radio R + altura y, al pasar por las diagonales, la nave quedaba "dentro" del cubo: Cosmonautics la
        // bajaba al planeta en el acto y el jugador acababa fuera de la nave. Ahora el círculo libra las esquinas
        // (medio lado · √2, con margen) más la altura.
        boolean sphere = false;
        try {
            sphere = dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isSphereShape();
        } catch (Throwable ignored) {
        }
        double corner = sphere ? big : big * Math.sqrt(2.0) * 1.02;
        double rMin = corner + 60_000.0;
        double rDef = corner + Math.max(200_000.0, 0.3 * big);
        double roi = source.roi() > 0 && Double.isFinite(source.roi()) ? 0.9 * source.roi() : Double.POSITIVE_INFINITY;
        rDef = Math.max(Math.min(rDef, roi), rMin);
        double rMax = Double.isFinite(roi) ? roi : big * 60.0;
        rMax = Math.max(rMax, rDef);
        return new OrbitRange(frame, source.mu(), big, rMin, rMax, rDef, star);
    }

    public static Arrival arrival(UniverseDefinition universe, String name) {
        return arrival(universe, name, 0);
    }

    /** @param altitudeKm altitud pedida sobre el centro de las caras; <= 0: la órbita de siempre */
    public static Arrival arrival(UniverseDefinition universe, String name, double altitudeKm) {
        OrbitRange range = orbitRange(universe, name);
        if (range == null) return null;
        double r = altitudeKm > 0 ? range.radiusFor(altitudeKm) : range.rDefault();
        return new Arrival(range.frame(), range.mu(), range.planetRadius(), r);
    }

    /** Lo pide un jugador desde la consola. */
    public void tryJump(ServerPlayer player, String targetName, double altitudeKm) {
        if (!(level instanceof ServerLevel sl)) return;
        WarpStatus st = status(sl);
        if (st != WarpStatus.READY) {
            player.displayClientMessage(Component.translatable("message.planetary_expansion.warp_not_ready", st.text()), true);
            return;
        }
        DeepSpaceData data = DeepSpaceData.getInstance(sl.getServer());
        Arrival arrival = data == null ? null : arrival(data.getUniverse(), targetName, altitudeKm);
        if (arrival == null) {
            player.displayClientMessage(Component.translatable("message.planetary_expansion.warp_bad_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        DeepSpaceInstance inst = instance(sl, Warp.shipAt(sl, worldPosition));
        if (inst == null) {
            player.displayClientMessage(WarpStatus.NOT_IN_SPACE.text(), true);
            return;
        }
        // 1.0.4: orbitar el mismo astro a otra distancia sí se puede; solo se rechaza si ya estás (casi) a esa distancia
        if (targetName.equals(currentOrbit(sl))) {
            double now = currentOrbitRadius(inst);
            if (now > 0 && Math.abs(now - arrival.radius()) < 0.01 * arrival.radius()) {
                player.displayClientMessage(Component.translatable("message.planetary_expansion.warp_already_there").withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
        // 1.0.3: ¿salto a otra estrella?
        String from = StarSystems.systemOf(data.getUniverse(), currentOrbit(sl));
        String to = StarSystems.systemOf(data.getUniverse(), targetName);
        Component problem = interstellarProblem(stellar, charge, tank.getFluidAmount(), from, to);
        if (problem != null) {
            player.displayClientMessage(problem, true);
            return;
        }
        interstellarJump = !from.equals(to);
        refund = tank.drain(interstellarJump ? INTERSTELLAR_MB : JUMP_MB, IFluidHandler.FluidAction.EXECUTE).copy();
        jumpTick = 0;
        target = targetName;
        targetAlt = altitudeKm;
        dirtySync = true;
        setChanged();
        WarpEffectPayload effect = new WarpEffectPayload(interstellarJump ? WarpEffectPayload.START_STELLAR : WarpEffectPayload.START,
                targetName, (int) Math.round(arrival.altitudeKm()));
        for (ServerPlayer p : playersAboard(sl, inst)) PacketDistributor.sendToPlayer(p, effect);
        PlanetaryExpansion.LOGGER.info("[Distorsión] {} inicia el salto {}a {} desde {}", player.getScoreboardName(),
                interstellarJump ? "interestelar (" + from + " -> " + to + ") " : "", targetName, worldPosition);
    }

    private static List<ServerPlayer> playersAboard(ServerLevel level, DeepSpaceInstance inst) {
        List<ServerPlayer> out = new ArrayList<>();
        try {
            inst.collectOnlinePlayers(level.getServer(), out::add);
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.warn("[Distorsión] No se pudieron listar los jugadores de la instancia", t);
        }
        return out;
    }

    private void teleport(ServerLevel sl) {
        ServerSubLevel ship = Warp.shipAt(sl, worldPosition);
        DeepSpaceInstance inst = instance(sl, ship);
        DeepSpaceData data = DeepSpaceData.getInstance(sl.getServer());
        Arrival arrival = data == null ? null : arrival(data.getUniverse(), target, targetAlt);
        if (inst == null || arrival == null) {
            abort(sl, inst);
            return;
        }
        try {
            DeepSpacePosition pos = inst.getPosition();
            AbsoluteDate date = pos.getCurrentPVCoords().getDate();
            double phi = sl.random.nextDouble() * Math.PI * 2;
            double r = arrival.radius();
            double v = Math.sqrt(arrival.mu() / r);
            Vector3D rv = new Vector3D(r * Math.cos(phi), 0, r * Math.sin(phi));
            Vector3D vv = new Vector3D(-v * Math.sin(phi), 0, v * Math.cos(phi));
            pos.init(data.getUniverse(), arrival.frame(), new TimeStampedPVCoordinates(date, rv, vv));
            data.setDirty();
            inst.forceClientSync();
            stopShip(ship);
            reapplyStop = 3;
        } catch (Throwable t) {
            PlanetaryExpansion.LOGGER.error("[Distorsión] Falló el salto a {}", target, t);
            abort(sl, inst);
            return;
        }
        // el normal gasta todo (100 %); el estelar, 100 % dentro del sistema y 200 % entre estrellas
        charge = interstellarJump ? 0 : Math.max(0, charge - 1.0);
        heat = 1.0;
        drainDebt = 0;
        refund = FluidStack.EMPTY;
        dirtySync = true;
        setChanged();
        for (ServerPlayer p : playersAboard(sl, inst)) {
            dev.antme08.planetaryexpansion.advancement.Achievements.award(p, "warp_speed", "jumped");
            dev.antme08.planetaryexpansion.advancement.Achievements.award(p, "interplanetary_commuter", target);
            if (interstellarJump) dev.antme08.planetaryexpansion.advancement.Achievements.award(p, "beyond_the_sun", "jumped");
        }
        Vector3d c = Warp.toWorld(sl, worldPosition);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1.5, c.z, 120, 2.0, 1.5, 2.0, 0.4);
        if (interstellarJump) sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.5, c.z, 80, 2.5, 1.5, 2.5, 0.25);
        PlanetaryExpansion.LOGGER.info("[Distorsión] Nave en órbita de {} a {} km", target, String.format("%,.0f", arrival.altitudeKm()));
    }

    private void abort(ServerLevel sl, DeepSpaceInstance inst) {
        WarpEffectPayload stop = new WarpEffectPayload(WarpEffectPayload.ABORT, target, 0);
        if (inst != null) {
            for (ServerPlayer p : playersAboard(sl, inst)) PacketDistributor.sendToPlayer(p, stop);
        } else {
            Vector3d c = Warp.toWorld(sl, worldPosition);
            for (ServerPlayer p : sl.players()) if (p.distanceToSqr(c.x, c.y, c.z) < 96 * 96) PacketDistributor.sendToPlayer(p, stop);
        }
        if (!refund.isEmpty()) tank.fill(refund, IFluidHandler.FluidAction.EXECUTE); // se devuelve el nitrógeno
        refund = FluidStack.EMPTY;
        jumpTick = -1;
        target = "";
        targetAlt = 0;
        interstellarJump = false;
        dirtySync = true;
        setChanged();
        PlanetaryExpansion.LOGGER.warn("[Distorsión] Salto cancelado en {}", worldPosition);
    }

    private static void stopShip(ServerSubLevel ship) {
        if (ship == null || ship.isRemoved()) return;
        try {
            RigidBodyHandle body = RigidBodyHandle.of(ship);
            if (body == null || !body.isValid()) return;
            Vector3d lin = body.getLinearVelocity(new Vector3d());
            Vector3d ang = body.getAngularVelocity(new Vector3d());
            body.addLinearAndAngularVelocity(lin.negate(), ang.negate());
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ textos

    private static String pct(double v) {
        return String.valueOf((int) Math.floor(v * 100));
    }

    /** Resumen de una línea (clic derecho en el motor). */
    public Component summary() {
        String k = "message.planetary_expansion.";
        return Component.translatable(k + "warp_summary", pct(charge), pct(heat), tank.getFluidAmount(), TANK_CAPACITY)
                .withStyle(charge >= 1.0 && heat <= 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
    }

    /** Líneas para las goggles de Create (en el cliente, con los datos sincronizados). */
    public void appendStatus(List<Component> tooltip) {
        String k = "message.planetary_expansion.";
        tooltip.add(Component.literal("    ").append(Component.translatable(stellar ? k + "warp_stellar_name" : "block.planetary_expansion.warp_drive")
                .withStyle(stellar ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE)));
        tooltip.add(line(Component.translatable(k + "warp_charge", pct(charge)), charge >= 1.0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        tooltip.add(line(Component.translatable(k + "warp_heat", pct(heat)), heat > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        tooltip.add(line(Component.translatable(k + "warp_nitrogen", tank.getFluidAmount(), TANK_CAPACITY),
                tank.getFluidAmount() >= JUMP_MB ? ChatFormatting.AQUA : ChatFormatting.RED));
        if (heat > 0 && tank.isEmpty()) tooltip.add(line(Component.translatable(k + "warp_needs_coolant"), ChatFormatting.RED));
        if (charge >= 1.0 && heat <= 0 && tank.getFluidAmount() >= JUMP_MB) {
            tooltip.add(line(Component.translatable(k + "warp_ready"), ChatFormatting.GREEN));
        }
        if (stellar && charge >= 2.0 && heat <= 0 && tank.getFluidAmount() >= INTERSTELLAR_MB) {
            tooltip.add(line(Component.translatable(k + "warp_ready_interstellar"), ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static Component line(Component c, ChatFormatting style) {
        return Component.literal("     ").append(c.copy().withStyle(style));
    }

    // ------------------------------------------------------------------ guardado y sincronización

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSync(tag, registries);
        tag.putDouble("DrainDebt", drainDebt);
        tag.putInt("JumpTick", jumpTick);
        tag.putString("Target", target);
        tag.putDouble("TargetAlt", targetAlt);
        tag.putBoolean("Interstellar", interstellarJump);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = tag.getDouble("Charge");
        heat = tag.getDouble("Heat");
        tank.setFluid(FluidStack.EMPTY);
        if (tag.contains("Tank")) tank.readFromNBT(registries, tag.getCompound("Tank"));
        clientJumping = tag.getBoolean("Jumping");
        if (tag.contains("DrainDebt")) drainDebt = tag.getDouble("DrainDebt");
        if (tag.contains("Target")) target = tag.getString("Target");
        targetAlt = tag.getDouble("TargetAlt");
        stellar = tag.getBoolean("Stellar");
        if (tag.contains("Interstellar")) interstellarJump = tag.getBoolean("Interstellar");
        // un salto a medias al cargar el mundo se termina (el efecto de los jugadores ya no existe)
        int tp = interstellarJump ? STELLAR_TELEPORT_TICK : TELEPORT_TICK;
        if (tag.contains("JumpTick")) jumpTick = tag.getInt("JumpTick") >= 0 ? Math.max(tag.getInt("JumpTick"), tp - 1) : -1;
    }

    private void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("Charge", charge);
        tag.putDouble("Heat", heat);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Jumping", jumpTick >= 0);
        tag.putBoolean("Stellar", stellar);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSync(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
