package dev.antme08.planetaryexpansion.warp.client;

import dev.antme08.planetaryexpansion.warp.StarSystems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.Footprint;
import dev.antme08.planetaryexpansion.warp.NavConsoleBlockEntity;
import dev.antme08.planetaryexpansion.warp.WarpAnchorBlock;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.SkyHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.network.PlanetRenderRequestPayload;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesa de la consola (2x2) con el sistema solar holográfico encima.
 *
 * 0.7.40 (petición del usuario): el holograma es como el de la mesa de hologramas de Cosmonautics (mismos planetas
 * texturizados, dibujados con su render de holograma) pero siempre enseña el sistema solar entero y sin nombres:
 * cada astro en su posición real del momento y su órbita en puntos. Las distancias van en raíz cuadrada para que los
 * planetas interiores no se amontonen junto al Sol, y las lunas a una distancia fija de su planeta. Si Cosmonautics
 * aún no tiene la textura de un planeta, se pide al servidor y mientras tanto se dibuja un cubito de su color.
 * Sin datos del universo se dibuja el holograma animado de antes.
 */
public class NavConsoleRenderer implements BlockEntityRenderer<NavConsoleBlockEntity> {
    public NavConsoleRenderer(BlockEntityRendererProvider.Context context) {}

    private static final float HOLO_Y = 24.5f;      // px sobre la base de la consola
    private static final float MAX_R = 13.2f;       // px: radio de la órbita más lejana
    private static final float[] MOON_R = {1.7f, 2.6f, 3.4f};
    private static final Map<String, Float> SIZE = Map.of("sol", 1.25f, "gas_giant", 0.95f, "moon", 0.38f, "ignis", 0.38f, "thalassa", 0.42f);
    private static final Map<String, String> FALLBACK = Map.of("sol", "sun", "overworld", "earth", "moon", "ice", "venus", "venus",
            "mars", "mars", "gas_giant", "giant", "ice_world", "ice", "ignis", "ignis", "thalassa", "thalassa");
    private static final Map<Integer, Long> REQUESTED = new HashMap<>();
    private static boolean loggedError;

    /** Animación antigua (sin datos del universo). */
    private record Orbit(String body, float radius, float speed, float phase, float size) {}

    private static final Orbit[] ORBITS = {
            new Orbit("venus", 4f, 1.6f, 40f, 0.8f), new Orbit("earth", 6.5f, 1.0f, 160f, 1f),
            new Orbit("mars", 9f, 0.62f, 290f, 0.85f), new Orbit("giant", 11.5f, 0.3f, 80f, 1.35f),
            new Orbit("ice", 13.5f, 0.16f, 220f, 1f)};
    private static final Orbit[] MOONS = {new Orbit("ignis", 1.6f, 4f, 0f, 0.5f), new Orbit("thalassa", 2.5f, 2.6f, 120f, 0.6f)};

    private record Shown(CubePlanet planet, String id, float x, float z, float size, float orbitR, float ox, float oz) {}

    @Override
    public void render(NavConsoleBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction f = state.hasProperty(WarpAnchorBlock.FACING) ? state.getValue(WarpAnchorBlock.FACING) : Direction.NORTH;
        ms.pushPose();
        WarpDriveRenderer.toFootprint(ms, f, 0.5, 0.5, 2, 2);
        CachedBuffers.partial(WarpClient.CONSOLE_TABLE, state).light(light).renderInto(ms, buffers.getBuffer(RenderType.solid()));
        CachedBuffers.partial(WarpClient.CONSOLE_GLOW, state).light(LightTexture.FULL_BRIGHT).renderInto(ms, buffers.getBuffer(RenderType.solid()));
        ms.popPose();

        float t = AnimationTickHolder.getRenderTime(be.getLevel());
        float flicker = 0.85f + 0.15f * Mth.sin(t * 0.31f) * Mth.sin(t * 0.07f);
        // centro del holograma, alineado con los ejes (como la mesa de hologramas de Cosmonautics)
        Direction l = Footprint.left(f);
        ms.pushPose();
        ms.translate(0.5 + 0.5 * (f.getStepX() + l.getStepX()), HOLO_Y / 16.0, 0.5 + 0.5 * (f.getStepZ() + l.getStepZ()));
        boolean done = false;
        try {
            done = renderSystem(ms, buffers, state, pt, t, flicker);
        } catch (Throwable e) {
            if (!loggedError) {
                loggedError = true;
                PlanetaryExpansion.LOGGER.warn("[Consola] No se pudo dibujar el holograma del sistema solar", e);
            }
        }
        if (!done) renderAnimated(ms, buffers, state, t, flicker);
        ms.popPose();
    }

    // ------------------------------------------------------------------ sistema solar real

    private static AbsoluteDate date(float pt) {
        try {
            AbsoluteDate d = DeepSpaceHandler.getPredictedUniverseDate(pt);
            if (d != null) return d;
        } catch (Throwable ignored) {
        }
        try {
            return DeepSpaceHandler.getRenderDate(pt);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String parentOf(CubePlanet p) {
        try {
            var parent = p.frame().getParent();
            return parent == null ? null : parent.getName();
        } catch (Throwable e) {
            return null;
        }
    }

    private boolean renderSystem(PoseStack ms, MultiBufferSource buffers, BlockState state, float pt, float t, float flicker) {
        UniverseDefinition u = DeepSpaceHandler.getUniverse();
        AbsoluteDate date = date(pt);
        if (u == null || date == null) return false;
        // 1.0.3: solo el sistema solar en el que está la consola
        String starName = systemHere(u);
        Frame sol = u.getFrameByName(starName).orElse(null);
        if (sol == null) return false;

        Map<String, double[]> helio = new HashMap<>();
        List<CubePlanet> planets = new ArrayList<>();
        for (CubePlanet p : u.getPlanets()) if (StarSystems.systemOf(p).equals(starName)) planets.add(p);
        for (CubePlanet p : planets) {
            Vector3D v = p.getPosition(date, sol);
            helio.put(p.frame().getName(), new double[]{v.getX(), v.getZ()});
        }
        double maxD = 1;
        for (CubePlanet p : planets) {
            String parent = parentOf(p);
            if (starName.equals(parent)) {
                double[] h = helio.get(p.frame().getName());
                maxD = Math.max(maxD, Math.hypot(h[0], h[1]));
            }
        }
        // primero los que giran alrededor del Sol, luego las lunas (ordenadas por distancia a su planeta)
        planets.sort(Comparator.comparingInt((CubePlanet p) -> StarSystems.isStar(p) ? 0 : starName.equals(parentOf(p)) ? 1 : 2)
                .thenComparingDouble(p -> {
                    double[] h = helio.get(p.frame().getName());
                    double[] q = parentOf(p) == null ? null : helio.get(parentOf(p));
                    return q == null ? 0 : Math.hypot(h[0] - q[0], h[1] - q[1]);
                }));
        Map<String, Shown> shown = new HashMap<>();
        Map<String, Integer> moonIndex = new HashMap<>();
        List<Shown> list = new ArrayList<>();
        for (CubePlanet p : planets) {
            String id = p.frame().getName();
            String parent = parentOf(p);
            double[] h = helio.get(id);
            float size = SIZE.getOrDefault(id, 0.62f);
            Shown s;
            if (parent == null || StarSystems.isStar(p)) {
                s = new Shown(p, id, 0, 0, SIZE.getOrDefault(id, 1.1f), 0, 0, 0);
            } else if (parent.equals(starName) || !shown.containsKey(parent)) {
                double d = Math.hypot(h[0], h[1]);
                float r = (float) (Math.sqrt(d / maxD) * MAX_R);
                double a = Math.atan2(h[1], h[0]);
                s = new Shown(p, id, (float) Math.cos(a) * r, (float) Math.sin(a) * r, size, r, 0, 0);
            } else {
                Shown ps = shown.get(parent);
                double[] q = helio.get(parent);
                int idx = moonIndex.merge(parent, 1, Integer::sum) - 1;
                float r = MOON_R[Math.min(idx, MOON_R.length - 1)] + ps.size() - 0.6f;
                double a = Math.atan2(h[1] - q[1], h[0] - q[0]);
                s = new Shown(p, id, ps.x() + (float) Math.cos(a) * r, ps.z() + (float) Math.sin(a) * r, size, r, ps.x(), ps.z());
            }
            shown.put(id, s);
            list.add(s);
        }

        // órbitas en puntos
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ms.last().pose();
        int alpha = (int) (110 * flicker);
        for (Shown s : list) {
            if (s.orbitR() <= 0) continue;
            int n = Math.max(10, (int) (Mth.TWO_PI * s.orbitR() / 1.15f));
            float half = s.orbitR() < 4 ? 0.12f : 0.17f;
            for (int i = 0; i < n; i++) {
                float a = i * Mth.TWO_PI / n;
                float x = (s.ox() + Mth.cos(a) * s.orbitR()) / 16f, z = (s.oz() + Mth.sin(a) * s.orbitR()) / 16f;
                dot(vc, m, x, z, half / 16f, 0x60, 0xE8, 0xFF, alpha);
            }
        }

        // astros (con el render de holograma de Cosmonautics)
        long now = System.currentTimeMillis();
        for (Shown s : list) {
            ms.pushPose();
            ms.translate(s.x() / 16f, 0, s.z() / 16f);
            float half = s.size() / 16f;
            boolean missing = true;
            if (s.planet().radius() > 0) {
                missing = DeepSpaceHandler.renderHoloPlanet(s.planet(), Vector3D.ZERO, ms, date,
                        s.planet().radius() / half, buffers, 1f, 1f, 1f, 0.9f * flicker);
            }
            if (missing) {
                request(s.planet(), now);
                PartialModel cube = WarpClient.CONSOLE_BODIES.get(FALLBACK.getOrDefault(s.id(), StarSystems.isStar(s.planet()) ? "sun" : "earth"));
                if (cube != null) {
                    ms.mulPose(Axis.YP.rotationDegrees(t * 2f));
                    float k = s.size() / 0.7f; // los cubitos miden 1,4 px (2,2 el Sol)
                    if (StarSystems.isStar(s.planet())) k = s.size() / 1.1f;
                    ms.scale(k, k, k);
                    CachedBuffers.partial(cube, state).light(LightTexture.FULL_BRIGHT).color(255, 255, 255, (int) (220 * flicker))
                            .renderInto(ms, buffers.getBuffer(RenderType.translucent()));
                }
            }
            ms.popPose();
        }
        return true;
    }

    /** Sistema solar en el que está la consola (el de la nave en el espacio profundo o el del planeta). */
    private static String systemHere(UniverseDefinition u) {
        try {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null && level.dimension().location().toString().equals("rocketnautics:deep_space")
                    && DeepSpaceHandler.hasReceivedPosition())
                return StarSystems.systemOf(u, DeepSpaceHandler.getReceivedPosition().getFrame().getName());
            if (level != null) {
                CubePlanet p = u.getPlanetByDimension(level.dimension());
                if (p != null) return StarSystems.systemOf(p);
            }
        } catch (Throwable ignored) {
        }
        return "sol";
    }

    /** Pide a Cosmonautics la textura del planeta (como hace su mesa de hologramas), como mucho cada 10 s. */
    private static void request(CubePlanet planet, long now) {
        int id = planet.id();
        Long last = REQUESTED.get(id);
        if (last != null && now - last < 10_000) return;
        REQUESTED.put(id, now);
        try {
            PacketDistributor.sendToServer(new PlanetRenderRequestPayload(new int[]{id}, SkyHandler.getMaximumScale()));
        } catch (Throwable ignored) {
        }
    }

    /** Punto cuadrado horizontal, visible por arriba y por abajo. */
    private static void dot(VertexConsumer vc, Matrix4f m, float x, float z, float h, int r, int g, int b, int a) {
        vc.addVertex(m, x - h, 0, z - h).setColor(r, g, b, a);
        vc.addVertex(m, x - h, 0, z + h).setColor(r, g, b, a);
        vc.addVertex(m, x + h, 0, z + h).setColor(r, g, b, a);
        vc.addVertex(m, x + h, 0, z - h).setColor(r, g, b, a);
        vc.addVertex(m, x + h, 0, z - h).setColor(r, g, b, a);
        vc.addVertex(m, x + h, 0, z + h).setColor(r, g, b, a);
        vc.addVertex(m, x - h, 0, z + h).setColor(r, g, b, a);
        vc.addVertex(m, x - h, 0, z - h).setColor(r, g, b, a);
    }

    // ------------------------------------------------------------------ holograma animado (sin datos del universo)

    private static void renderAnimated(PoseStack ms, MultiBufferSource buffers, BlockState state, float t, float flicker) {
        int alpha = (int) (200 * flicker);
        body(ms, buffers, state, "sun", 0, 0, 1f, t * 1.5f, alpha);
        for (Orbit o : ORBITS) {
            double a = Math.toRadians(o.phase() + t * o.speed());
            float x = (float) Math.cos(a) * o.radius() / 16f, z = (float) Math.sin(a) * o.radius() / 16f;
            body(ms, buffers, state, o.body(), x, z, o.size(), t * 3f, alpha);
            if (o.body().equals("giant")) {
                for (Orbit mo : MOONS) {
                    double b = Math.toRadians(mo.phase() + t * mo.speed());
                    body(ms, buffers, state, mo.body(), x + (float) Math.cos(b) * mo.radius() / 16f,
                            z + (float) Math.sin(b) * mo.radius() / 16f, mo.size(), t * 4f, alpha);
                }
            }
        }
    }

    private static void body(PoseStack ms, MultiBufferSource buffers, BlockState state, String name, float x, float z,
                             float size, float spin, int alpha) {
        PartialModel model = WarpClient.CONSOLE_BODIES.get(name);
        if (model == null) return;
        ms.pushPose();
        ms.translate(x, 0, z);
        ms.mulPose(Axis.YP.rotationDegrees(spin));
        ms.scale(size, size, size);
        CachedBuffers.partial(model, state).light(LightTexture.FULL_BRIGHT).color(255, 255, 255, alpha)
                .renderInto(ms, buffers.getBuffer(RenderType.translucent()));
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(NavConsoleBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(NavConsoleBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3);
    }
}
