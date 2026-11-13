package dev.antme08.planetaryexpansion.warp.client;

import dev.antme08.planetaryexpansion.warp.StarSystems;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.NavStatusPayload;
import dev.antme08.planetaryexpansion.warp.NavStatusRequestPayload;
import dev.antme08.planetaryexpansion.warp.WarpDriveBlockEntity;
import dev.antme08.planetaryexpansion.warp.WarpJumpRequestPayload;
import dev.antme08.planetaryexpansion.warp.WarpStatus;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Mapa del sistema solar de la consola de navegación (pantalla completa).
 *
 * <ul>
 *   <li>Rueda: zoom hacia el ratón. Arrastrar con clic izquierdo: mover la cámara.</li>
 *   <li>Ratón encima de un astro: nombre y ficha (gravedad, aire, temperatura, peligros, armadura, recursos, distancia).</li>
 *   <li>Clic derecho en un astro: menú con "Viajar (orbitar)" (solo con el motor de la nave listo) y "Leer descripción".</li>
 * </ul>
 * Las posiciones son las reales del universo de Cosmonautics (heliocéntricas, vistas desde arriba con algo de
 * inclinación). Las lunas se separan un mínimo de su planeta para que se puedan señalar a cualquier zoom.
 */
public class NavMapScreen extends Screen {
    private static final String K = "nav." + PlanetaryExpansion.MODID + ".";
    private static final float TILT = 0.55f;
    private static final int PANEL_W = 196;
    private static final Map<String, Integer> COLORS = Map.of(
            "sol", 0xFFFFD75E, "overworld", 0xFF4F8FE0, "moon", 0xFFCACAD2, "mars", 0xFFF07338, "gas_giant", 0xFFE6B880,
            "ice_world", 0xFFB3EBFF, "ignis", 0xFFFF4D1A, "thalassa", 0xFF8CD1FF, "venus", 0xFFF2CC66, "cerulea", 0xFF4DB08A);

    private final BlockPos console;
    private double zoom = -1;     // píxeles por metro
    private double camX, camZ;    // centro de la vista (metros, heliocéntrico)
    private String follow = null;
    /** 1.0.3: sistema solar que se está viendo (nombre de su estrella); null = el de donde estamos. */
    private String system = null;
    /** Estrella del sistema dibujado en el último layout. */
    private String currentStar = "sol";
    private Button systemButton;
    private boolean dragging;
    private double dragDist;
    private String hovered;
    private String pinned;
    private String popup;
    private int popupX, popupY;
    private int ticks;
    private final List<Body> bodies = new ArrayList<>();
    private Marker you;
    /** Órbita de la nave en pantalla (0.7.40): puntos de la trayectoria alrededor del astro que la controla. */
    private final List<double[]> shipOrbit = new ArrayList<>();
    private boolean shipOrbitClosed;
    private final int[] stars;

    private record Body(String id, CubePlanet planet, String parent, double x, double z, double sx, double sy, double rPx,
                        double radius, double mu, int color, boolean star) {}

    private record Marker(double sx, double sy, String body, double hx, double hz) {}

    public NavMapScreen(BlockPos console) {
        super(Component.translatable(K + "title"));
        this.console = console;
        Random r = new Random(1234);
        stars = new int[600];
        for (int i = 0; i < stars.length; i++) stars[i] = r.nextInt();
    }

    @Override
    protected void init() {
        int y = height - 46;
        addRenderableWidget(Button.builder(Component.translatable(K + "center_sun"), b -> {
            follow = currentStar;
            zoom = -1;
        }).bounds(8, y, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(K + "center_me"), b -> centerOnMe()).bounds(82, y, 70, 18).build());
        // 1.0.3: cambiar de sistema solar (solo se ve si hay más de una estrella)
        systemButton = addRenderableWidget(Button.builder(Component.empty(), b -> nextSystem()).bounds(156, y, 120, 18).build());
        systemButton.visible = false;
        requestStatus();
    }

    private void requestStatus() {
        try {
            PacketDistributor.sendToServer(new NavStatusRequestPayload(console));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 10 == 0) requestStatus();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ datos

    private static String name(String id) {
        String key = "hologram." + PlanetaryExpansion.MODID + ".body." + id;
        if (I18n.exists(key)) return I18n.get(key);
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String parentOf(CubePlanet p) {
        try {
            var parent = p.frame().getParent();
            return parent == null ? null : parent.getName();
        } catch (Throwable t) {
            return null;
        }
    }

    private static AbsoluteDate date(float partial) {
        try {
            AbsoluteDate d = DeepSpaceHandler.getPredictedUniverseDate(partial);
            if (d != null) return d;
        } catch (Throwable ignored) {
        }
        try {
            return DeepSpaceHandler.getRenderDate(partial);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calcula las posiciones en pantalla. @return false si aún no hay datos del universo. */
    private boolean layout(float partial) {
        bodies.clear();
        you = null;
        shipOrbit.clear();
        UniverseDefinition u = DeepSpaceHandler.getUniverse();
        AbsoluteDate date = date(partial);
        if (u == null || date == null) return false;
        List<String> starList = StarSystems.stars(u);
        String starName = system != null && starList.contains(system) ? system : autoSystem(u);
        currentStar = starName;
        if (systemButton != null) {
            systemButton.visible = starList.size() > 1;
            systemButton.setMessage(Component.translatable(K + "system", name(starName)));
        }
        Frame sol = u.getFrameByName(starName).orElse(null);
        CubePlanet sun = null;
        for (CubePlanet p : u.getPlanets()) if (p.frame().getName().equals(starName)) sun = p;
        if (sol == null && sun != null) sol = sun.frame().getOrekitFrame();
        if (sol == null) return false;

        Map<String, Double> mus = new HashMap<>();
        for (PointGravitySource s : u.getGravitySources()) mus.put(s.frame().getName(), s.mu());
        Map<String, double[]> helio = new HashMap<>();
        List<CubePlanet> planets = new ArrayList<>();
        for (CubePlanet p : u.getPlanets()) if (StarSystems.systemOf(p).equals(starName)) planets.add(p);
        for (CubePlanet p : planets) {
            Vector3D v = p.getPosition(date, sol);
            helio.put(p.frame().getName(), new double[]{v.getX(), v.getZ()});
        }
        if (zoom <= 0) {
            double max = 1;
            for (double[] h : helio.values()) max = Math.max(max, Math.hypot(h[0], h[1]));
            zoom = Math.min(width - PANEL_W, height) * 0.42 / max;
        }
        double[] f = follow == null ? null : helio.get(follow);
        if (f != null) {
            camX = f[0];
            camZ = f[1];
        }
        // primero los que orbitan el Sol (o no tienen padre), luego las lunas, ordenadas por distancia a su planeta
        planets.sort(Comparator.comparingInt((CubePlanet p) -> isMoon(p) ? 1 : 0)
                .thenComparingDouble(p -> distTo(helio, p)));
        Map<String, Integer> moonIndex = new HashMap<>();
        Map<String, Body> byId = new HashMap<>();
        for (CubePlanet p : planets) {
            String id = p.frame().getName();
            String parent = parentOf(p);
            double[] h = helio.get(id);
            double sx, sy;
            boolean star = StarSystems.isStar(p);
            double rPx = Math.max(star ? 7 : id.equals("gas_giant") ? 5 : 3.5, p.radius() * zoom);
            Body pb = parent == null ? null : byId.get(parent);
            if (pb != null && !pb.star()) {
                double rx = h[0] - pb.x(), rz = h[1] - pb.z();
                double d = Math.hypot(rx, rz) * zoom;
                int idx = moonIndex.merge(parent, 1, Integer::sum);
                double min = pb.rPx() + 6 + 9 * idx + rPx;
                double k = d < min && d > 0 ? min / d : 1;
                sx = pb.sx() + rx * zoom * k;
                sy = pb.sy() + rz * zoom * k * TILT;
            } else {
                sx = toSx(h[0]);
                sy = toSy(h[1]);
            }
            Body b = new Body(id, p, parent, h[0], h[1], sx, sy, rPx, p.radius(), mus.getOrDefault(id, 0.0),
                    COLORS.getOrDefault(id, star ? 0xFFFFA040 : 0xFFDDDDDD), star);
            bodies.add(b);
            byId.put(id, b);
        }
        // nosotros (si estamos en el espacio profundo)
        try {
            if (DeepSpaceHandler.hasReceivedPosition() && sun != null && isDeepSpace()) {
                DeepSpacePosition pos = DeepSpaceHandler.getReceivedPosition();
                Frame frame = pos.getFrame();
                if (!StarSystems.systemOf(u, frame.getName()).equals(starName)) throw new IllegalStateException("otro sistema");
                Vector3D us = pos.getPosition(date);
                Vector3D h = sun.posInMyFrame(date, us, frame);
                Body near = byId.get(frame.getName());
                double sx = toSx(h.getX()), sy = toSy(h.getZ());
                double k = 1, ox = toSx(0), oy = toSy(0);   // centro de la órbita en pantalla y escala (k > 1: agrandada)
                if (near != null) {
                    ox = near.sx();
                    oy = near.sy();
                }
                if (near != null && !near.star()) {
                    double rx = h.getX() - near.x(), rz = h.getZ() - near.z();
                    double d = Math.hypot(rx, rz) * zoom;
                    double min = near.rPx() + 7;
                    k = d < min && d > 0 ? min / d : 1;
                    sx = near.sx() + rx * zoom * k;
                    sy = near.sy() + rz * zoom * k * TILT;
                }
                you = new Marker(sx, sy, near == null ? null : near.id(), h.getX(), h.getZ());
                orbitPoints(pos, ox, oy, k);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * Trayectoria de la nave: la órbita kepleriana que manda Cosmonautics, muestreada a lo largo de un periodo (o hacia
     * delante si es de escape), alrededor del astro que la controla y con la misma escala que el marcador "Tú".
     */
    private void orbitPoints(DeepSpacePosition pos, double ox, double oy, double k) {
        var orbit = pos.getOrbit();
        if (orbit == null) return;
        double e = orbit.getE();
        int n = 160;
        if (e < 0.98) {
            double period = orbit.getKeplerianPeriod();
            if (!(period > 0) || !Double.isFinite(period)) return;
            for (int i = 0; i <= n; i++) add(orbit.shiftedBy(period * i / n).getPosition(), ox, oy, k);
            shipOrbitClosed = true;
        } else {
            Vector3D r0 = orbit.getPosition();
            double v0 = Math.max(1.0, orbit.getPVCoordinates().getVelocity().getNorm());
            double step = r0.getNorm() / v0 * 0.05;
            for (int i = 0; i <= n; i++) {
                Vector3D p = orbit.shiftedBy(step * i).getPosition();
                add(p, ox, oy, k);
                if (p.getNorm() > r0.getNorm() * 6) break;
            }
            shipOrbitClosed = false;
        }
    }

    private void add(Vector3D p, double ox, double oy, double k) {
        shipOrbit.add(new double[]{ox + p.getX() * zoom * k, oy + p.getZ() * zoom * k * TILT});
    }

    private static boolean isMoon(CubePlanet p) {
        try {
            var parent = p.frame().getParent();
            return parent != null && parent.getParent() != null && !StarSystems.isStar(p) && !StarSystems.isStar(parent);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Sistema en el que estamos: el de la nave en el espacio, el del planeta que orbitamos o el de la dimensión. */
    private String autoSystem(UniverseDefinition u) {
        try {
            if (DeepSpaceHandler.hasReceivedPosition() && isDeepSpace())
                return StarSystems.systemOf(u, DeepSpaceHandler.getReceivedPosition().getFrame().getName());
        } catch (Throwable ignored) {
        }
        NavStatusPayload st = WarpClient.lastStatus;
        if (st != null && !st.orbit().isEmpty()) return StarSystems.systemOf(u, st.orbit());
        try {
            if (minecraft != null && minecraft.level != null) {
                CubePlanet p = u.getPlanetByDimension(minecraft.level.dimension());
                if (p != null) return StarSystems.systemOf(p);
            }
        } catch (Throwable ignored) {
        }
        return "sol";
    }

    private void nextSystem() {
        List<String> list = StarSystems.stars(DeepSpaceHandler.getUniverse());
        if (list.size() < 2) return;
        int i = list.indexOf(currentStar);
        system = list.get((i + 1) % list.size());
        follow = system;
        zoom = -1;
        pinned = null;
        popup = null;
    }

    private static double distTo(Map<String, double[]> helio, CubePlanet p) {
        double[] h = helio.get(p.frame().getName());
        String parent = parentOf(p);
        double[] q = parent == null ? null : helio.get(parent);
        if (q == null) return Math.hypot(h[0], h[1]);
        return Math.hypot(h[0] - q[0], h[1] - q[1]);
    }

    private double mapW() {
        return width - PANEL_W;
    }

    private double toSx(double x) {
        return mapW() / 2 + (x - camX) * zoom;
    }

    private double toSy(double z) {
        return height / 2.0 + (z - camZ) * zoom * TILT;
    }

    private boolean isDeepSpace() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.dimension().location().equals(ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space"));
    }

    private Body body(String id) {
        if (id == null) return null;
        for (Body b : bodies) if (b.id().equals(id)) return b;
        return null;
    }

    /** Cuerpo en el que estamos (planeta de la dimensión actual, o el que orbitamos en el espacio). */
    private String currentBody() {
        NavStatusPayload st = WarpClient.lastStatus;
        if (you != null && you.body() != null) return you.body();
        if (st != null && !st.orbit().isEmpty()) return st.orbit();
        try {
            UniverseDefinition u = DeepSpaceHandler.getUniverse();
            if (u != null && minecraft != null && minecraft.level != null) {
                CubePlanet p = u.getPlanetByDimension(minecraft.level.dimension());
                if (p != null) return p.frame().getName();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void centerOnMe() {
        String cur = currentBody();
        Body b = body(cur);
        if (b == null) return;
        follow = cur;
        zoom = Mth.clamp(80.0 / Math.max(1, b.radius()), minZoom(), maxZoom());
    }

    private double minZoom() {
        double max = 1;
        for (Body b : bodies) max = Math.max(max, Math.hypot(b.x(), b.z()));
        return Math.min(width - PANEL_W, height) * 0.1 / max;
    }

    private double maxZoom() {
        double min = Double.MAX_VALUE;
        for (Body b : bodies) min = Math.min(min, b.radius());
        return 250.0 / Math.max(1e4, min == Double.MAX_VALUE ? 1e6 : min);
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xFF04050C);
        for (int i = 0; i < stars.length; i++) {
            int s = stars[i];
            int x = Math.floorMod(s, Math.max(1, width)), y = Math.floorMod(s >> 11, Math.max(1, height));
            int b = 70 + Math.floorMod(s >> 20, 150);
            if (((s >> 7) & 7) == 0) b = Math.min(255, b + 60);
            g.fill(x, y, x + 1, y + 1, 0xFF000000 | (b << 16) | (b << 8) | Math.min(255, b + 20));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        boolean ok = layout(partial);
        if (!ok) {
            g.drawCenteredString(font, Component.translatable(K + "no_data"), width / 2, height / 2, 0xFFB0B0B0);
            for (var r : renderables) r.render(g, mouseX, mouseY, partial);
            return;
        }
        drawMap(g);
        hovered = popup == null ? pick(mouseX, mouseY) : null;
        drawLabels(g);
        g.flush();
        // los botones encima del mapa
        for (var r : renderables) r.render(g, mouseX, mouseY, partial);
        drawPanel(g, hovered != null ? hovered : pinned);
        drawStatusBar(g);
        if (popup != null) drawPopup(g, mouseX, mouseY);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), 8, 8, 0xFF7FE8FF);
        g.drawString(font, Component.translatable(K + "hint"), 8, 20, 0xFF8090A0);
    }

    private void drawMap(GuiGraphics g) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        // órbitas (elipses alrededor del padre)
        for (Body b : bodies) {
            if (b.parent() == null) continue;
            Body p = body(b.parent());
            if (p == null) continue;
            double rx = b.sx() - p.sx(), ry = (b.sy() - p.sy()) / TILT;
            double r = Math.hypot(rx, ry);
            if (r < 2) continue;
            int col = (b.color() & 0x00FFFFFF) | 0x50000000;
            if (b.id().equals(hovered) || b.id().equals(pinned)) col = (b.color() & 0x00FFFFFF) | 0xB0000000;
            ellipse(vc, m, p.sx(), p.sy(), r, r * TILT, col);
        }
        // órbita de la nave (cian, más visible que las de los planetas)
        for (int i = 1; i < shipOrbit.size(); i++) {
            double[] a = shipOrbit.get(i - 1), b = shipOrbit.get(i);
            boolean visible = !(Math.max(a[0], b[0]) < -2 || Math.min(a[0], b[0]) > mapW() + 2 || Math.max(a[1], b[1]) < -2 || Math.min(a[1], b[1]) > height + 2);
            if (visible) line(vc, m, a[0], a[1], b[0], b[1], 0xC000FFCC);
        }
        g.flush();
        // astros
        for (Body b : bodies) {
            if (b.sx() + b.rPx() < 0 || b.sx() - b.rPx() > mapW() || b.sy() + b.rPx() < 0 || b.sy() - b.rPx() > height) continue;
            if (b.star()) {
                for (int k = 4; k >= 1; k--) disc(g, b.sx(), b.sy(), b.rPx() * (1 + 0.45 * k), 0x14000000 | (b.color() & 0xFFFFFF));
                disc(g, b.sx(), b.sy(), b.rPx(), b.color());
                disc(g, b.sx(), b.sy(), b.rPx() * 0.6, 0xFFFFF6D0);
            } else {
                Body sun = body(currentStar);
                double lx = sun == null ? 0 : sun.sx() - b.sx(), ly = sun == null ? 0 : sun.sy() - b.sy();
                double ln = Math.hypot(lx, ly);
                disc(g, b.sx(), b.sy(), b.rPx(), shade(b.color(), 0.45f));
                if (ln > 0) {
                    double off = b.rPx() * 0.28;
                    disc(g, b.sx() + lx / ln * off, b.sy() + ly / ln * off, b.rPx() * 0.78, b.color());
                }
                if (b.id().equals("gas_giant") && b.rPx() > 6) {
                    // bandas
                    for (int k = -2; k <= 2; k++) {
                        int yy = (int) (b.sy() + k * b.rPx() * 0.3);
                        double half = Math.sqrt(Math.max(0, b.rPx() * b.rPx() - Math.pow(yy - b.sy(), 2))) * 0.92;
                        g.fill((int) (b.sx() - half), yy, (int) (b.sx() + half), yy + Math.max(1, (int) (b.rPx() * 0.08)), shade(b.color(), 0.75f));
                    }
                }
            }
            if (b.id().equals(hovered) || b.id().equals(pinned)) ring(g, b.sx(), b.sy(), b.rPx() + 3, 0xFFFFFFFF);
        }
        // nosotros
        if (you != null) {
            int x = (int) you.sx(), y = (int) you.sy();
            int c = 0xFF00FFCC;
            g.fill(x - 1, y - 4, x + 2, y - 3, c);
            g.fill(x - 2, y - 3, x + 3, y - 2, c);
            g.fill(x - 3, y - 2, x + 4, y - 1, c);
            g.fill(x - 4, y - 1, x + 5, y, c);
            float s = 0.75f;
            String t = I18n.get("hologram." + PlanetaryExpansion.MODID + ".you");
            drawScaled(g, t, (float) (x - font.width(t) * s / 2), y + 2, s, c);
        }
    }

    private void drawLabels(GuiGraphics g) {
        List<int[]> used = new ArrayList<>();
        // primero los grandes (Sol, planetas), luego las lunas
        for (Body b : bodies) {
            if (b.sx() < -50 || b.sx() > mapW() + 50 || b.sy() < -50 || b.sy() > height + 50) continue;
            String n = name(b.id());
            boolean moon = !b.star() && b.parent() != null && !b.parent().equals(currentStar);
            float s = moon ? 0.6f : 0.8f;
            int w = (int) (font.width(n) * s);
            int x = (int) (b.sx() - w / 2.0), y = (int) (b.sy() + b.rPx() + 3);
            boolean hit = false;
            for (int[] r : used) if (x < r[2] && x + w > r[0] && y < r[3] && y + 8 > r[1]) hit = true;
            if (hit && !b.id().equals(hovered)) continue;
            used.add(new int[]{x, y, x + w, y + 8});
            drawScaled(g, n, x, y, s, b.id().equals(hovered) ? 0xFFFFFFFF : shade(b.color(), 0.9f));
        }
    }

    private void drawPanel(GuiGraphics g, String id) {
        int x0 = width - PANEL_W, x = x0 + 8;
        g.fill(x0, 0, width, height, 0xE00A1020);
        g.fill(x0, 0, x0 + 1, height, 0xFF2A4A6A);
        Body b = body(id);
        if (b == null) {
            int y = 40;
            for (FormattedCharSequence line : font.split(Component.translatable(K + "panel_hint"), PANEL_W - 16)) {
                g.drawString(font, line, x, y, 0xFF8090A0);
                y += 10;
            }
            return;
        }
        int y = 10;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, Component.literal(name(b.id())).withStyle(ChatFormatting.BOLD), 0, 0, b.color());
        g.pose().popPose();
        y += 17;
        String base = K + "body." + b.id() + ".";
        if (I18n.exists(base + "type")) {
            g.drawString(font, I18n.get(base + "type"), x, y, 0xFFA0B0C0);
            y += 12;
        }
        if (b.id().equals(currentBody())) {
            g.drawString(font, Component.translatable(K + (isDeepSpace() ? "orbiting_here" : "you_here")).withStyle(ChatFormatting.AQUA), x, y, 0xFF00FFCC);
            y += 12;
        }
        y += 2;
        if (I18n.exists(base + "desc")) {
            for (FormattedCharSequence line : font.split(Component.translatable(base + "desc"), PANEL_W - 16)) {
                g.drawString(font, line, x, y, 0xFFE0E0E0);
                y += 10;
            }
            y += 4;
        }
        if (!b.star() && b.radius() > 0 && b.mu() > 0) {
            y = field(g, x, y, "gravity", String.format(Locale.ROOT, "%.1f m/s²", b.mu() / (b.radius() * b.radius())));
        }
        y = field(g, x, y, "radius", String.format(Locale.ROOT, "%,.0f km", b.radius() / 1000.0).replace(',', ' '));
        for (String f : new String[]{"air", "temp", "hazards", "armor", "resources"}) {
            if (I18n.exists(base + f)) y = field(g, x, y, f, I18n.get(base + f));
        }
        String d = distanceText(b);
        if (d != null) y = field(g, x, y, "distance", d);
    }

    private int field(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, Component.translatable(K + "label." + label), x, y, 0xFF7FB8E0);
        y += 10;
        for (FormattedCharSequence line : font.split(Component.literal(value), PANEL_W - 22)) {
            g.drawString(font, line, x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
        return y + 3;
    }

    /** Distancia a la superficie desde la nave (en el espacio) o desde el planeta en el que estamos. */
    private String distanceText(Body target) {
        double fx, fz;
        String cur = currentBody();
        if (you != null) {
            fx = you.hx();
            fz = you.hz();
        } else {
            Body from = body(cur);
            if (from == null) return null;
            if (from.id().equals(target.id())) return null;
            fx = from.x();
            fz = from.z();
        }
        double d = Math.max(0, Math.hypot(target.x() - fx, target.z() - fz) - target.radius());
        double km = d / 1000.0;
        if (km < 1_000_000) return String.format(Locale.ROOT, "%,.0f km", km).replace(',', ' ');
        if (km < 1_000_000_000) return String.format(Locale.ROOT, "%.1f M km", km / 1_000_000);
        return String.format(Locale.ROOT, "%.2e km", km);
    }

    private void drawStatusBar(GuiGraphics g) {
        int y = height - 24, x1 = (int) mapW();
        g.fill(0, y - 1, x1, height, 0xD0060A14);
        g.fill(0, y - 1, x1, y, 0xFF2A4A6A);
        NavStatusPayload st = WarpClient.lastStatus;
        Component line;
        if (st == null || !st.console().equals(console)) {
            line = Component.translatable(K + "status_wait").withStyle(ChatFormatting.GRAY);
        } else {
            WarpStatus s = st.warpStatus();
            line = Component.translatable(K + "drive").withStyle(ChatFormatting.GRAY).append(" ").append(s.text());
            if (s != WarpStatus.NO_DRIVE) {
                line = line.copy().append(Component.literal("   "))
                        .append(Component.translatable(K + "charge", (int) (st.charge() * 100)).withStyle(st.charge() >= 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE))
                        .append(Component.literal("   "))
                        .append(Component.translatable(K + "heat", (int) Math.ceil(st.heat() * 100)).withStyle(st.heat() > 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                        .append(Component.literal("   "))
                        .append(Component.translatable(K + "nitrogen", st.nitrogen(), WarpDriveBlockEntity.TANK_CAPACITY)
                                .withStyle(st.nitrogen() >= WarpDriveBlockEntity.JUMP_MB ? ChatFormatting.AQUA : ChatFormatting.RED))
                        .append(Component.literal("   "))
                        .append(Component.translatable(K + "rpm", (int) st.rpm()).withStyle(ChatFormatting.WHITE));
            }
        }
        g.drawString(font, line, 8, y + 8, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ menú del clic derecho

    private List<Component> popupItems() {
        List<Component> items = new ArrayList<>();
        items.add(Component.translatable(K + "travel"));
        items.add(Component.translatable(K + "read"));
        return items;
    }

    /** Motivo por el que no se puede viajar (o null si se puede). */
    private Component travelBlocked(String id) {
        Body target = body(id);
        if (target != null && target.star()) return Component.translatable(K + "blocked_sun");
        NavStatusPayload st = WarpClient.lastStatus;
        if (st == null || !st.console().equals(console)) return Component.translatable(K + "status_wait");
        if (id.equals(st.orbit()) || (isDeepSpace() && id.equals(currentBody()))) return Component.translatable(K + "blocked_here");
        if (st.warpStatus() != WarpStatus.READY) return st.warpStatus().text();
        return null;
    }

    private int popupW() {
        int w = 0;
        for (Component c : popupItems()) w = Math.max(w, font.width(c));
        return w + 16;
    }

    private int popupHit(double mx, double my) {
        int w = popupW();
        if (mx < popupX || mx > popupX + w) return -1;
        int i = (int) Math.floor((my - popupY - 14) / 14);
        return i >= 0 && i < popupItems().size() ? i : -1;
    }

    private void drawPopup(GuiGraphics g, int mx, int my) {
        List<Component> items = popupItems();
        int w = popupW(), h = 16 + items.size() * 14;
        g.fill(popupX, popupY, popupX + w, popupY + h, 0xF0101828);
        g.renderOutline(popupX, popupY, w, h, 0xFF4FA8E0);
        Body b = body(popup);
        g.drawString(font, name(popup), popupX + 6, popupY + 4, b == null ? 0xFFFFFFFF : b.color());
        int sel = popupHit(mx, my);
        Component blocked = travelBlocked(popup);
        for (int i = 0; i < items.size(); i++) {
            int y = popupY + 16 + i * 14;
            boolean disabled = i == 0 && blocked != null;
            if (i == sel && !disabled) g.fill(popupX + 1, y - 2, popupX + w - 1, y + 11, 0x603FA0FF);
            g.drawString(font, items.get(i), popupX + 8, y, disabled ? 0xFF606870 : i == 0 ? 0xFFD9A8FF : 0xFFFFFFFF);
        }
        if (sel == 0 && blocked != null) g.renderTooltip(font, blocked, mx, my);
    }

    // ------------------------------------------------------------------ ratón

    private String pick(double mx, double my) {
        if (mx > mapW()) return null;
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (Body b : bodies) {
            double d = Math.hypot(mx - b.sx(), my - b.sy());
            if (d <= b.rPx() + 5 && d < bestD) {
                bestD = d;
                best = b.id();
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (popup != null) {
            int i = popupHit(mx, my);
            String id = popup;
            popup = null;
            if (i == 0 && travelBlocked(id) == null) {
                PacketDistributor.sendToServer(new WarpJumpRequestPayload(console, id));
                return true;
            }
            if (i == 1) {
                pinned = id;
                return true;
            }
            if (i >= 0) return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        String hit = pick(mx, my);
        if (button == 1 && hit != null) {
            popup = hit;
            popupX = (int) Math.min(mx, mapW() - popupW() - 4);
            popupY = (int) Math.min(my, height - 60);
            return true;
        }
        if (button == 0 && mx < mapW()) {
            dragging = true;
            dragDist = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            if (dragDist < 3) {
                String hit = pick(mx, my);
                if (hit != null) pinned = hit; // clic izquierdo en un astro: fija su ficha
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0 && zoom > 0) {
            dragDist += Math.abs(dx) + Math.abs(dy);
            if (dragDist >= 3) {
                follow = null;
                camX -= dx / zoom;
                camZ -= dy / (zoom * TILT);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (zoom <= 0 || mx > mapW() || scrollY == 0) return false;
        double old = zoom;
        zoom = Mth.clamp(zoom * Math.pow(1.2, scrollY), minZoom(), maxZoom());
        if (follow == null) {
            // el punto bajo el ratón se queda quieto
            double wx = camX + (mx - mapW() / 2) / old, wz = camZ + (my - height / 2.0) / (old * TILT);
            camX = wx - (mx - mapW() / 2) / zoom;
            camZ = wz - (my - height / 2.0) / (zoom * TILT);
        }
        return true;
    }

    // ------------------------------------------------------------------ primitivas

    private void drawScaled(GuiGraphics g, String text, float x, float y, float s, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    private static int shade(int argb, float k) {
        int r = (int) (((argb >> 16) & 255) * k), gg = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return (argb & 0xFF000000) | (Math.min(255, r) << 16) | (Math.min(255, gg) << 8) | Math.min(255, b);
    }

    private void disc(GuiGraphics g, double cx, double cy, double r, int color) {
        if (r < 0.5) {
            g.fill((int) cx, (int) cy, (int) cx + 1, (int) cy + 1, color);
            return;
        }
        int y0 = (int) Math.max(0, Math.floor(cy - r)), y1 = (int) Math.min(height, Math.ceil(cy + r));
        for (int y = y0; y < y1; y++) {
            double dy = y + 0.5 - cy;
            double half = Math.sqrt(Math.max(0, r * r - dy * dy));
            int xa = (int) Math.max(-1, Math.round(cx - half)), xb = (int) Math.min(width + 1, Math.round(cx + half));
            if (xb > xa) g.fill(xa, y, xb, y + 1, color);
        }
    }

    private void ring(GuiGraphics g, double cx, double cy, double r, int color) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        ellipse(vc, g.pose().last().pose(), cx, cy, r, r, color);
        g.flush();
    }

    private void ellipse(VertexConsumer vc, Matrix4f m, double cx, double cy, double rx, double ry, int color) {
        int n = (int) Mth.clamp(rx * 0.6, 48, 4096);
        double px = cx + rx, py = cy;
        for (int i = 1; i <= n; i++) {
            double a = i * Math.PI * 2 / n;
            double x = cx + Math.cos(a) * rx, y = cy + Math.sin(a) * ry;
            boolean visible = !(Math.max(px, x) < -2 || Math.min(px, x) > mapW() + 2 || Math.max(py, y) < -2 || Math.min(py, y) > height + 2);
            if (visible) line(vc, m, px, py, x, y, color);
            px = x;
            py = y;
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        float nx = (float) (-dy / len * 0.5), ny = (float) (dx / len * 0.5);
        quad(vc, m, (float) x1 + nx, (float) y1 + ny, (float) x1 - nx, (float) y1 - ny,
                (float) x2 - nx, (float) y2 - ny, (float) x2 + nx, (float) y2 + ny, color, color);
    }

    /**
     * Cuadrilátero con el mismo sentido de giro que GuiGraphics.fill (si no, la GUI lo descarta por la cara de atrás).
     * c1: color de los dos primeros vértices, c2: de los dos últimos.
     */
    static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
                     int c1, int c2) {
        float cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx);
        if (cross > 0) {
            vc.addVertex(m, dx, dy, 0).setColor(c2);
            vc.addVertex(m, cx, cy, 0).setColor(c2);
            vc.addVertex(m, bx, by, 0).setColor(c1);
            vc.addVertex(m, ax, ay, 0).setColor(c1);
        } else {
            vc.addVertex(m, ax, ay, 0).setColor(c1);
            vc.addVertex(m, bx, by, 0).setColor(c1);
            vc.addVertex(m, cx, cy, 0).setColor(c2);
            vc.addVertex(m, dx, dy, 0).setColor(c2);
        }
    }
}
