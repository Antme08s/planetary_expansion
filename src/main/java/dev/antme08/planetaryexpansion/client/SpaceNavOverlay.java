package dev.antme08.planetaryexpansion.client;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.HelmetHudHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.shaded.hipparchus.geometry.euclidean.threed.Vector3D;
import dev.devce.rocketnautics.shaded.orekit.frames.Frame;
import dev.devce.rocketnautics.shaded.orekit.time.AbsoluteDate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Navegación en el espacio profundo (0.7.20, petición del usuario): con el HUD del casco de Cosmonautics encendido
 * (tecla Y), pone el nombre y la distancia de cada planeta sobre él en el cielo, y una flecha en el borde de la
 * pantalla para los que quedan fuera de la vista ("Sun &gt;"). También marca hacia dónde avanzas (prógrado) y hacia
 * dónde frenarías (retrógrado), con flecha en el borde si no se ven: útil con la cámara en tercera persona de los
 * asientos, donde no se ve hacia dónde va la nave.
 *
 * Las direcciones salen de los mismos datos que usa Cosmonautics: posición de cada planeta = -posInMyFrame(...)
 * con la posición recibida de la instancia (igual que la mesa de hologramas, alineada con los ejes del mundo), y el
 * prógrado = HelmetHudHandler.getLocalVelocityDir(), el mismo que su marcador cuadrado.
 */
public final class SpaceNavOverlay {
    private SpaceNavOverlay() {}

    private static final int CYAN = 0xFF00E5FF;
    private static final int RETRO = 0xFFFF9A3C;
    private static final int THRUST = 0xFF5CFF6A;
    private static final int GRAY = 0xFFB0B0B0;
    private static final int MARGIN = 18;
    /** 0.7.21 (petición del usuario): texto más pequeño para que no tape tanto. */
    private static final float NAME_SCALE = 0.6f;
    private static final float SUB_SCALE = 0.5f;

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static boolean haveMatrices;
    private static boolean loggedError;

    private record Mark(String name, String sub, int color, Vector3d dir, boolean square, int priority) {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        VIEW.set(event.getModelViewMatrix());
        PROJ.set(event.getProjectionMatrix());
        haveMatrices = true;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!haveMatrices) return;
        try {
            if (!HelmetHudHandler.hudEnabled || !HelmetHudHandler.isHelmetWornInDeepSpace()) return;
            draw(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                PlanetaryExpansion.LOGGER.warn("[HUD] No se pudo dibujar la navegación espacial", t);
            }
        }
    }

    private static void draw(GuiGraphics g, float partial) {
        UniverseDefinition universe = DeepSpaceHandler.getUniverse();
        if (universe == null || !DeepSpaceHandler.hasReceivedPosition()) return;
        DeepSpacePosition pos = DeepSpaceHandler.getReceivedPosition();
        AbsoluteDate date = DeepSpaceHandler.getRenderDate(partial);
        Frame frame = pos.getFrame();
        Vector3D us = pos.getPosition(date);

        List<Mark> marks = new ArrayList<>();
        for (CubePlanet planet : universe.getPlanets()) {
            Vector3D rel = planet.posInMyFrame(date, us, frame); // nosotros vistos desde el planeta
            double d = rel.getNorm();
            if (!(d > 0) || Double.isNaN(d)) continue;
            String id = planet.frame().getName();
            float[] c = HologramLabels.COLORS.getOrDefault(id, HologramLabels.DEFAULT_COLOR);
            int color = 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            double alt = Math.max(0, d - planet.radius());
            marks.add(new Mark(HologramLabels.bodyName(id), HologramLabels.formatDistance(alt), color,
                    new Vector3d(-rel.getX(), -rel.getY(), -rel.getZ()).normalize(), false, 0));
        }
        org.joml.Vector3d pro = HelmetHudHandler.getLocalVelocityDir();
        if (pro != null && pro.lengthSquared() > 1e-8) {
            Vector3d p = new Vector3d(pro).normalize();
            marks.add(new Mark(I18n.get("hud." + PlanetaryExpansion.MODID + ".prograde"), null, CYAN, p, true, 2));
            marks.add(new Mark(I18n.get("hud." + PlanetaryExpansion.MODID + ".retrograde"), null, RETRO, new Vector3d(p).negate(), true, 1));
        }

        Vector3d thrust = ClientThrust.current();
        if (thrust != null && thrust.lengthSquared() > 1e-8) {
            String label = I18n.get("hud." + PlanetaryExpansion.MODID + ".thrust", String.format("%.1f", thrust.length()));
            marks.add(new Mark(label, null, THRUST, new Vector3d(thrust).normalize(), false, 3));
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        List<int[]> used = new ArrayList<>(); // cajas de texto ya dibujadas, para no encimar
        marks.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        for (Mark m : marks) {
            Vector4f clip = PROJ.transform(VIEW.transform(new Vector4f((float) m.dir().x, (float) m.dir().y, (float) m.dir().z, 0f)));
            boolean onScreen = false;
            float sx = 0, sy = 0;
            if (clip.w > 1e-4f) {
                float nx = clip.x / clip.w, ny = clip.y / clip.w;
                sx = (nx + 1f) / 2f * w;
                sy = (1f - ny) / 2f * h;
                onScreen = sx >= MARGIN && sx <= w - MARGIN && sy >= MARGIN && sy <= h - MARGIN;
            }
            if (onScreen) {
                drawMarker(g, (int) sx, (int) sy, m.color(), m.square());
                drawText(g, font, used, m.name(), m.sub(), (int) sx, (int) sy + 5, m.color(), w, h);
            } else {
                // dirección en pantalla aunque esté detrás: del espacio de la vista (x derecha, y arriba)
                Vector4f view = VIEW.transform(new Vector4f((float) m.dir().x, (float) m.dir().y, (float) m.dir().z, 0f));
                float dx = view.x, dy = -view.y;
                if (Math.abs(dx) < 1e-5f && Math.abs(dy) < 1e-5f) dy = 1f; // justo detrás: abajo
                float cx = w / 2f, cy = h / 2f;
                float kx = dx == 0 ? Float.MAX_VALUE : (cx - MARGIN) / Math.abs(dx);
                float ky = dy == 0 ? Float.MAX_VALUE : (cy - MARGIN) / Math.abs(dy);
                float k = Math.min(kx, ky);
                int ex = (int) (cx + dx * k), ey = (int) (cy + dy * k);
                String arrow = Math.abs(dx) * (cy - MARGIN) >= Math.abs(dy) * (cx - MARGIN)
                        ? (dx > 0 ? ">" : "<") : (dy > 0 ? "v" : "^");
                String text = arrow.equals("<") || arrow.equals("^") ? arrow + " " + m.name() : m.name() + " " + arrow;
                drawText(g, font, used, text, null, ex, ey - 3, m.color(), w, h);
            }
        }
    }

    private static void drawMarker(GuiGraphics g, int x, int y, int color, boolean square) {
        if (square) {
            // cuadrado hueco, como el marcador de Cosmonautics, con una cruz
            g.fill(x - 4, y - 4, x + 5, y - 3, color);
            g.fill(x - 4, y + 4, x + 5, y + 5, color);
            g.fill(x - 4, y - 3, x - 3, y + 4, color);
            g.fill(x + 4, y - 3, x + 5, y + 4, color);
            g.fill(x, y - 1, x + 1, y + 2, color);
            g.fill(x - 1, y, x + 2, y + 1, color);
        } else {
            // rombo pequeño
            for (int i = 0; i <= 3; i++) {
                g.fill(x - i, y - 3 + i, x + i + 1, y - 2 + i, color);
                g.fill(x - i, y + 3 - i, x + i + 1, y + 4 - i, color);
            }
        }
    }

    private static void drawText(GuiGraphics g, Font font, List<int[]> used, String name, String sub,
                                 int cx, int top, int color, int w, int h) {
        float nameW = font.width(name) * NAME_SCALE;
        float subW = sub == null ? 0 : font.width(sub) * SUB_SCALE;
        int bw = (int) Math.ceil(Math.max(nameW, subW));
        int nameH = (int) Math.ceil(9 * NAME_SCALE);
        int bh = sub == null ? nameH : nameH + 1 + (int) Math.ceil(9 * SUB_SCALE);
        int x0 = Math.max(2, Math.min(w - bw - 2, cx - bw / 2));
        int y0 = Math.max(2, Math.min(h - bh - 2, top));
        // si se encima con otra etiqueta, se baja (o se sube si no cabe)
        for (int tries = 0; tries < 8; tries++) {
            boolean hit = false;
            for (int[] r : used) {
                if (x0 < r[2] && x0 + bw > r[0] && y0 < r[3] && y0 + bh > r[1]) {
                    y0 = r[3] + 1;
                    hit = true;
                }
            }
            if (!hit) break;
            if (y0 + bh > h - 2) y0 = Math.max(2, top - bh - 10);
        }
        used.add(new int[]{x0, y0, x0 + bw, y0 + bh});
        drawScaled(g, font, name, x0 + (bw - nameW) / 2f, y0, NAME_SCALE, color);
        if (sub != null) drawScaled(g, font, sub, x0 + (bw - subW) / 2f, y0 + nameH + 1, SUB_SCALE, GRAY);
    }

    private static void drawScaled(GuiGraphics g, Font font, String text, float x, float y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }
}
