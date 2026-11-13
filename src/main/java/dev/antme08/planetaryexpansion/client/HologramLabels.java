package dev.antme08.planetaryexpansion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.resources.language.I18n;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Marcadores y nombres de los cuerpos celestes sobre la mesa holográfica de Cosmonautics.
 *
 * Las posiciones se calculan igual que en DeepSpaceHandler.renderHologram: posición relativa del planeta
 * respecto a la mesa = -posInMyFrame(...), escalada por 1/scale. Cosmonautics solo dibuja los cuerpos que caben
 * en el holograma (distancia &lt; scaleTest). Desde 0.4.6 los que no caben también salen: un marcador más pequeño
 * y apagado en el borde del holograma, en su dirección, con la etiqueta "(fuera de escala)". Así se ve hacia dónde
 * queda, por ejemplo, el planeta helado desde Marte (está mucho más lejos que el resto).
 *
 * Los cuerpos que quedan muy juntos (Tierra y Luna a escala de sistema solar, etc.) forman un grupo con una
 * sola etiqueta de varias líneas, una por cuerpo, en vez de taparse. Tu posición (el centro del holograma,
 * donde Cosmonautics pone el cubito cian) también lleva etiqueta: "Tú".
 */
public final class HologramLabels {
    private HologramLabels() {}

    static final Map<String, float[]> COLORS = Map.of(
            "sol", new float[]{1.0f, 0.85f, 0.3f},
            "overworld", new float[]{0.35f, 0.65f, 1.0f},
            "moon", new float[]{0.8f, 0.8f, 0.85f},
            "mars", new float[]{0.95f, 0.45f, 0.2f},
            "gas_giant", new float[]{0.9f, 0.72f, 0.5f},
            "ice_world", new float[]{0.7f, 0.92f, 1.0f},
            "ignis", new float[]{1.0f, 0.3f, 0.1f},
            "thalassa", new float[]{0.55f, 0.82f, 1.0f},
            "venus", new float[]{0.95f, 0.8f, 0.4f});
    static final float[] DEFAULT_COLOR = {0.9f, 0.9f, 0.9f};
    private static final float[] YOU_COLOR = {0.0f, 1.0f, 0.8f}; // el mismo cian que el marcador de Cosmonautics

    /** Altura de una línea de texto en unidades de fuente. */
    private static final int LINE = 10;

    private static boolean loggedError;

    /** {@code scale}: tamaño del texto de esta línea (0.7.12: depende del tamaño del planeta dibujado). */
    /**
     * 0.7.13 (petición del usuario): solo se juntan en una etiqueta los cuerpos que están casi encima (Tierra y
     * Luna, un gigante y sus lunas). Antes era 5 líneas de distancia y juntaba el Sol con Venus o Marte con la
     * Tierra. Lo que aún se tape en pantalla lo separa el paso de pantalla (se empuja hacia arriba).
     */
    private static final float JOIN_LINES = 1.6f;

    private record Line(String text, float[] color, double priority, float scale) {}

    private static final class Body {
        final Vector3f pos;
        final float drawnRadius;
        final float marker;
        final Line line;

        Body(Vector3f pos, float drawnRadius, float marker, Line line) {
            this.pos = pos;
            this.drawnRadius = drawnRadius;
            this.marker = marker;
            this.line = line;
        }

        float top() {
            return pos.y + Math.max(drawnRadius, marker);
        }
    }

    private static final class Group {
        final List<Body> bodies = new ArrayList<>();
        final Vector3f anchor = new Vector3f();

        float maxScale() {
            float m = 0f;
            for (Body b : bodies) m = Math.max(m, b.line.scale());
            return m;
        }

        void add(Body b) {
            bodies.add(b);
            anchor.set(0);
            for (Body o : bodies) anchor.add(o.pos);
            anchor.div(bodies.size());
        }
    }

    public static void render(Vector3D posInFrame, Frame posFrame, UniverseDefinition universe, AbsoluteDate date,
                              double scale, double scaleTest, PoseStack ms, MultiBufferSource source) {
        try {
            doRender(posInFrame, posFrame, universe, date, scale, scaleTest, ms, source);
        } catch (Throwable t) {
            // nunca romper el renderizado de la mesa por las etiquetas
            if (!loggedError) {
                loggedError = true;
                PlanetaryExpansion.LOGGER.warn("[Holograma] No se pudieron dibujar las etiquetas de planetas", t);
            }
        }
    }

    private static void doRender(Vector3D posInFrame, Frame posFrame, UniverseDefinition universe, AbsoluteDate date,
                                 double scale, double scaleTest, PoseStack ms, MultiBufferSource source) {
        if (universe == null || date == null || scale <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Camera camera = mc.gameRenderer.getMainCamera();
        int holoSize = Math.max(1, (int) Math.round(2 * scaleTest / scale));

        float baseMarker = 0.010f * (float) Math.sqrt(holoSize) + 0.008f;
        float textScale = 0.0032f + 0.0007f * holoSize; // tamaño máximo: ~0.05–0.1 bloques de alto por línea
        // 0.7.12 (petición del usuario): el texto se adapta al tamaño con que se ve el planeta. Un planeta dibujado
        // grande (zoom) lleva el texto a tamaño completo; los cubitos pequeños, texto pequeño (mínimo 35 %).
        float fullAt = baseMarker * 3.5f;

        List<Body> bodies = new ArrayList<>();
        // 1.0.3: los astros de otro sistema solar no se marcan "fuera de escala" (salían todos amontonados)
        String here = dev.antme08.planetaryexpansion.warp.StarSystems.systemOf(universe, posFrame.getName());
        for (CubePlanet planet : universe.getPlanets()) {
            Vector3D us = planet.posInMyFrame(date, posInFrame, posFrame); // nuestra posición vista desde el planeta
            double distance = us.getNorm();
            if (!(distance > 0) || Double.isNaN(distance)) continue;
            boolean far = !(distance < scaleTest);
            if (far && !dev.antme08.planetaryexpansion.warp.StarSystems.systemOf(planet).equals(here)) continue;
            String id = planet.frame().getName();
            Vector3f pos;
            float drawnRadius;
            if (far) {
                // en el borde del holograma, en la dirección del cuerpo
                float edge = (float) (scaleTest / scale) * 0.95f;
                pos = new Vector3f((float) (-us.getX() / distance), (float) (-us.getY() / distance), (float) (-us.getZ() / distance)).mul(edge);
                drawnRadius = 0;
            } else {
                pos = new Vector3f((float) (-us.getX() / scale), (float) (-us.getY() / scale), (float) (-us.getZ() / scale));
                drawnRadius = (float) (planet.radius() / scale);
            }
            // marcador más grande cuanto más grande es el cuerpo (Sol > planetas > lunas)
            float sizeFactor = (float) Math.max(0.5, Math.min(1.4, (Math.log10(Math.max(planet.radius(), 1)) - 5.0) / 2.0));
            String name = bodyName(id);
            String text = distance < planet.radius() * 2.0 ? name : name + "  " + formatDistance(distance);
            float[] color = COLORS.getOrDefault(id, DEFAULT_COLOR);
            if (far) {
                text = text + " " + I18n.get("hologram." + PlanetaryExpansion.MODID + ".far");
                color = new float[]{color[0] * 0.65f, color[1] * 0.65f, color[2] * 0.65f};
            }
            float marker = baseMarker * sizeFactor * (far ? 0.6f : 1f);
            float shown = Math.max(drawnRadius, marker);
            float ts = textScale * Math.max(far ? 0.35f : 0.4f, Math.min(1f, shown / fullAt));
            bodies.add(new Body(pos, drawnRadius, marker,
                    new Line(text, color, far ? planet.radius() * 1e-6 : planet.radius(), ts)));
        }
        // 0.7.23: flecha verde con la dirección en la que empujan los motores, saliendo de "Tú"
        org.joml.Vector3d thrust = ClientThrust.current();
        if (thrust != null && thrust.lengthSquared() > 1e-8) drawThrustArrow(ms, source, camera, thrust);

        // "Tú": el centro del holograma, encima de todo lo demás en su grupo
        bodies.add(new Body(new Vector3f(), 0, baseMarker * 0.5f,
                new Line(I18n.get("hologram." + PlanetaryExpansion.MODID + ".you"), YOU_COLOR, Double.MAX_VALUE, textScale * 0.6f)));

        for (Body b : bodies) {
            if (b.line.priority() == Double.MAX_VALUE) continue; // el cubito cian ya lo dibuja Cosmonautics
            if (b.drawnRadius < b.marker) {
                float[] c = b.line.color();
                Vector3f p = b.pos;
                DebugRenderer.renderFilledBox(ms, source, p.x - b.marker, p.y - b.marker, p.z - b.marker,
                        p.x + b.marker, p.y + b.marker, p.z + b.marker, c[0], c[1], c[2], 0.9f);
            }
        }

        // Agrupar los cuerpos cuyas etiquetas se taparían (la distancia depende del tamaño del texto)
        bodies.sort(Comparator.comparingDouble((Body b) -> b.line.priority()).reversed());
        List<Group> groups = new ArrayList<>();
        for (Body b : bodies) {
            Group target = null;
            for (Group g : groups) {
                if (g.anchor.distance(b.pos) < JOIN_LINES * LINE * Math.max(g.maxScale(), b.line.scale())) {
                    target = g;
                    break;
                }
            }
            if (target == null) {
                target = new Group();
                groups.add(target);
            }
            target.add(b);
        }
        // fusionar grupos que hayan quedado demasiado cerca
        boolean merged = true;
        while (merged) {
            merged = false;
            outer:
            for (int i = 0; i < groups.size(); i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    if (groups.get(i).anchor.distance(groups.get(j).anchor)
                            < JOIN_LINES * LINE * Math.max(groups.get(i).maxScale(), groups.get(j).maxScale())) {
                        for (Body b : groups.get(j).bodies) groups.get(i).add(b);
                        groups.remove(j);
                        merged = true;
                        break outer;
                    }
                }
            }
        }

        // 0.7.3: además de agruparlas en 3D, se separan en pantalla. Dos etiquetas lejanas entre sí pueden
        // quedar una encima de otra según desde dónde mires (p. ej. Marte delante de las lunas "fuera de escala").
        groups.sort(Comparator.comparingDouble((Group g) -> -maxPriority(g)));
        Matrix4f pose = new Matrix4f(ms.last().pose());
        Quaternionf local = new Matrix3f(ms.last().normal()).getNormalizedRotation(new Quaternionf());
        Quaternionf toView = new Quaternionf(camera.rotation()).conjugate();
        Vector3f upLocal = local.conjugate(new Quaternionf()).transform(new Vector3f(camera.getUpVector()));
        List<float[]> placed = new ArrayList<>(); // rectángulos en pantalla: x0, x1, y0, y1
        for (Group g : groups) {
            g.bodies.sort(Comparator.comparingDouble((Body b) -> b.line.priority()).reversed());
            float top = Float.NEGATIVE_INFINITY;
            for (Body b : g.bodies) top = Math.max(top, b.top());
            Vector3f at = new Vector3f(g.anchor.x, top + LINE * g.maxScale() * 0.3f, g.anchor.z);
            List<Line> lines = new ArrayList<>();
            float widest = 0f, height = 0f;
            for (Body b : g.bodies) {
                lines.add(b.line);
                widest = Math.max(widest, (font.width(b.line.text()) + 4) * b.line.scale());
                height += LINE * b.line.scale();
            }
            Vector3f view = toView.transform(pose.transformPosition(new Vector3f(at)));
            float depth = -view.z;
            if (depth > 0.05f) {
                float sx = view.x / depth, sy = view.y / depth;
                float w = widest / depth * 0.5f;
                float h = height / depth;
                float shift = 0f;
                for (int tries = 0; tries < 24; tries++) {
                    float y0 = sy + shift, y1 = y0 + h;
                    float push = Float.NaN;
                    for (float[] r : placed) {
                        if (sx + w > r[0] && sx - w < r[1] && y1 > r[2] && y0 < r[3]) {
                            push = r[3] - y0 + h * 0.08f;
                            break;
                        }
                    }
                    if (Float.isNaN(push)) break;
                    shift += push;
                }
                placed.add(new float[]{sx - w, sx + w, sy + shift, sy + shift + h});
                if (shift > 0f) at.add(new Vector3f(upLocal).mul(shift * depth));
            }
            drawLabel(ms, source, font, camera, at, upLocal, lines);
        }
    }

    private static double maxPriority(Group g) {
        double m = Double.NEGATIVE_INFINITY;
        for (Body b : g.bodies) m = Math.max(m, b.line.priority());
        return m;
    }

    static String bodyName(String id) {
        String key = "hologram." + PlanetaryExpansion.MODID + ".body." + id;
        if (I18n.exists(key)) return I18n.get(key);
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String formatDistance(double meters) {
        double km = meters / 1000.0;
        if (km < 1_000_000) return String.format(Locale.ROOT, "%,.0f km", km).replace(',', ' ');
        if (km < 1_000_000_000) return String.format(Locale.ROOT, "%.1f M km", km / 1_000_000);
        return String.format(Locale.ROOT, "%.2e km", km);
    }

    /**
     * Etiqueta de varias líneas mirando a la cámara; su borde inferior queda en {@code at}. Cada línea tiene su
     * tamaño y se apilan hacia arriba de la pantalla (la primera, arriba del todo).
     */
    private static void drawLabel(PoseStack ms, MultiBufferSource source, Font font, Camera camera,
                                  Vector3f at, Vector3f upLocal, List<Line> lines) {
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
        float stacked = 0f;
        for (int i = lines.size() - 1; i >= 0; i--) {
            Line line = lines.get(i);
            float s = line.scale();
            Vector3f base = new Vector3f(upLocal).mul(stacked).add(at);
            stacked += LINE * s;
            ms.pushPose();
            ms.translate(base.x, base.y, base.z);
            // quitar cualquier rotación acumulada (p. ej. la de una nave de Sable) y mirar a la cámara
            Quaternionf current = new Matrix3f(ms.last().normal()).getNormalizedRotation(new Quaternionf());
            ms.mulPose(current.conjugate());
            ms.mulPose(camera.rotation());
            ms.scale(s, -s, s);
            Matrix4f matrix = ms.last().pose();
            float[] c = line.color();
            int color = 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            float x = -font.width(line.text()) / 2f;
            // en coordenadas de fuente y crece hacia abajo: la línea termina justo encima de "base"
            font.drawInBatch(line.text(), x, -LINE, color, false, matrix, source, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
            ms.popPose();
        }
    }

    /** Flecha verde de longitud 1 (como las flechas moradas de velocidad de Cosmonautics), con segmentos sueltos. */
    private static void drawThrustArrow(PoseStack ms, MultiBufferSource source, Camera camera, org.joml.Vector3d thrust) {
        Vector3f dir = new Vector3f((float) thrust.x, (float) thrust.y, (float) thrust.z).normalize();
        Quaternionf local = new Matrix3f(ms.last().normal()).getNormalizedRotation(new Quaternionf());
        Vector3f look = local.conjugate(new Quaternionf()).transform(new Vector3f(camera.getLookVector()));
        Vector3f side = new Vector3f(dir).cross(look);
        if (side.lengthSquared() < 1e-6f) side = new Vector3f(dir).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
        side.normalize();
        Vector3f tip = new Vector3f(dir);
        Vector3f h1 = new Vector3f(dir).mul(0.8f).add(new Vector3f(side).mul(0.1f));
        Vector3f h2 = new Vector3f(dir).mul(0.8f).add(new Vector3f(side).mul(-0.1f));
        var buf = source.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        segment(ms, buf, new Vector3f(), tip);
        segment(ms, buf, tip, h1);
        segment(ms, buf, tip, h2);
        segment(ms, buf, h1, h2);
    }

    private static void segment(PoseStack ms, com.mojang.blaze3d.vertex.VertexConsumer buf, Vector3f a, Vector3f b) {
        Vector3f n = new Vector3f(b).sub(a);
        if (n.lengthSquared() < 1e-8f) return;
        n.normalize();
        var pose = ms.last();
        buf.addVertex(pose, a.x, a.y, a.z).setColor(0.36f, 1f, 0.42f, 1f).setNormal(pose, n.x, n.y, n.z);
        buf.addVertex(pose, b.x, b.y, b.z).setColor(0.36f, 1f, 0.42f, 1f).setNormal(pose, n.x, n.y, n.z);
    }
}
