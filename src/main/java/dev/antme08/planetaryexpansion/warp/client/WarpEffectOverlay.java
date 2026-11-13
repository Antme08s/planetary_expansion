package dev.antme08.planetaryexpansion.warp.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.WarpDriveBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Efecto del salto para todos los que van a bordo (aprobado por el usuario con la vista previa):
 * <ol>
 *   <li>0-3 s: las estrellas se estiran hacia los bordes (carga).</li>
 *   <li>3-3,6 s: destello blanco y violeta.</li>
 *   <li>3,6-5,6 s: túnel de distorsión. El servidor cambia la órbita a los 4 s, tapado por el túnel.</li>
 *   <li>5,6-8 s: se desvanece y aparece el nombre del planeta y la altura de la órbita.</li>
 * </ol>
 * Los tiempos van en ticks de 50 ms contados con el reloj real, así no dependen de la velocidad del servidor.
 */
public final class WarpEffectOverlay {
    private WarpEffectOverlay() {}

    private static final float STRETCH_END = 60, FLASH_END = 72, TUNNEL_END = 112, END = WarpDriveBlockEntity.END_TICK;
    private static final int STREAKS = 220;
    private static final float[] SEED_A = new float[STREAKS], SEED_R = new float[STREAKS], SEED_S = new float[STREAKS];

    static {
        java.util.Random r = new java.util.Random(77);
        for (int i = 0; i < STREAKS; i++) {
            SEED_A[i] = (float) (r.nextDouble() * Math.PI * 2);
            SEED_R[i] = 0.05f + r.nextFloat() * 0.95f;
            SEED_S[i] = 0.5f + r.nextFloat();
        }
    }

    private static long startNanos = -1;
    private static String target = "";
    private static int altitudeKm;
    private static boolean flashSound, arriveSound;
    private static boolean loggedError;

    public static void start(String targetName, int altKm) {
        startNanos = System.nanoTime();
        target = targetName;
        altitudeKm = altKm;
        flashSound = arriveSound = false;
        play(SoundEvents.PORTAL_TRIGGER, 0.7f, 0.6f);
        play(SoundEvents.BEACON_ACTIVATE, 0.6f, 1.0f);
    }

    public static void stop() {
        startNanos = -1;
    }

    public static boolean active() {
        return startNanos >= 0;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (startNanos < 0) return;
        float t = (System.nanoTime() - startNanos) / 50_000_000f;
        if (t >= END) {
            startNanos = -1;
            return;
        }
        try {
            draw(event.getGuiGraphics(), t);
        } catch (Throwable e) {
            if (!loggedError) {
                loggedError = true;
                PlanetaryExpansion.LOGGER.warn("[Distorsión] No se pudo dibujar el efecto del salto", e);
            }
        }
    }

    private static void draw(GuiGraphics g, float t) {
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h / 2f;
        float maxR = (float) Math.hypot(cx, cy) * 1.05f;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());

        if (t < STRETCH_END) {
            // 1. estrellas estirándose y oscurecimiento violeta
            float p = t / STRETCH_END;
            fill(vc, m, 0, 0, w, h, argb(p * 0.55f, 0.05f, 0.0f, 0.12f));
            streaks(vc, m, cx, cy, maxR, t, p, 1f);
        } else if (t < FLASH_END) {
            // 2. destello: blanco a violeta
            float p = (t - STRETCH_END) / (FLASH_END - STRETCH_END);
            if (!flashSound) {
                flashSound = true;
                play(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.0f);
            }
            fill(vc, m, 0, 0, w, h, argb(1f, Mth.lerp(p, 1f, 0.55f), Mth.lerp(p, 1f, 0.25f), Mth.lerp(p, 1f, 0.9f)));
        } else if (t < TUNNEL_END) {
            // 3. túnel de distorsión
            float p = (t - FLASH_END) / (TUNNEL_END - FLASH_END);
            fill(vc, m, 0, 0, w, h, argb(1f, 0.07f, 0.01f, 0.16f));
            tunnel(vc, m, cx, cy, maxR, t);
            streaks(vc, m, cx, cy, maxR, t * 3f, 1f, 0.8f);
            if (p < 0.15f) fill(vc, m, 0, 0, w, h, argb(1f - p / 0.15f, 0.55f, 0.25f, 0.9f)); // resto del destello
        } else {
            // 4. se desvanece; nombre del planeta
            float p = (t - TUNNEL_END) / (END - TUNNEL_END);
            if (!arriveSound) {
                arriveSound = true;
                play(SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.0f);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.0f);
            }
            float a = 1f - Mth.clamp(p * 1.6f, 0f, 1f);
            fill(vc, m, 0, 0, w, h, argb(a, 0.07f, 0.01f, 0.16f));
            if (a > 0.02f) tunnel(vc, m, cx, cy, maxR, t);
        }
        g.flush();

        if (t >= TUNNEL_END - 6) {
            float p = (t - (TUNNEL_END - 6)) / (END - TUNNEL_END + 6);
            float alpha = p < 0.15f ? p / 0.15f : p > 0.8f ? (1f - p) / 0.2f : 1f;
            int ai = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
            if (ai > 8) {
                Font font = Minecraft.getInstance().font;
                String name = bodyName(target);
                String sub = I18n.get("nav." + PlanetaryExpansion.MODID + ".arrived",
                        String.format(Locale.ROOT, "%,d", altitudeKm).replace(',', ' '));
                centered(g, font, name, cx, cy - 26, 3f, (ai << 24) | 0xE8D8FF);
                centered(g, font, sub, cx, cy + 10, 1.3f, (ai << 24) | 0x9FE6FF);
            }
        }
    }

    private static String bodyName(String id) {
        String key = "hologram." + PlanetaryExpansion.MODID + ".body." + id;
        if (I18n.exists(key)) return I18n.get(key);
        return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
    }

    private static void centered(GuiGraphics g, Font font, String text, float cx, float y, float s, int color) {
        g.pose().pushPose();
        g.pose().translate(cx - font.width(text) * s / 2f, y, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Rayos de luz que salen del centro; {@code p} 0..1 alarga los rayos. */
    private static void streaks(VertexConsumer vc, Matrix4f m, float cx, float cy, float maxR, float t, float p, float alpha) {
        float stretch = p * p;
        for (int i = 0; i < STREAKS; i++) {
            float speed = SEED_S[i] * (0.004f + stretch * 0.05f);
            float r = ((SEED_R[i] + t * speed) % 1.15f) * maxR;
            float len = 2f + stretch * maxR * 0.45f * (0.3f + r / maxR);
            float a = SEED_A[i];
            float ca = Mth.cos(a), sa = Mth.sin(a);
            float r2 = r + len;
            float wdt = 0.6f + r / maxR * 1.6f;
            float nx = -sa * wdt, ny = ca * wdt;
            boolean violet = (i % 3) == 0;
            int inner = argb(0f, 1f, 1f, 1f);
            int outer = violet ? argb(alpha * (0.4f + 0.6f * p), 0.8f, 0.55f, 1f) : argb(alpha * (0.4f + 0.6f * p), 1f, 1f, 1f);
            NavMapScreen.quad(vc, m, cx + ca * r - nx, cy + sa * r - ny, cx + ca * r + nx, cy + sa * r + ny,
                    cx + ca * r2 + nx, cy + sa * r2 + ny, cx + ca * r2 - nx, cy + sa * r2 - ny, inner, outer);
        }
    }

    /** Anillos deformados que avanzan hacia la cámara. */
    private static void tunnel(VertexConsumer vc, Matrix4f m, float cx, float cy, float maxR, float t) {
        int rings = 14, seg = 72;
        float phase = (t * 0.035f) % 1f;
        for (int k = 0; k < rings; k++) {
            float d = (k + phase) / rings;          // 0 lejos, 1 cerca
            float base = d * d * maxR * 1.2f;
            float thick = 1f + d * d * 26f;
            float a = Mth.clamp(d * 1.3f, 0f, 1f) * (1f - Mth.clamp((d - 0.85f) / 0.15f, 0f, 1f));
            int col = (k % 2 == 0) ? argb(a * 0.85f, 0.62f, 0.3f, 1f) : argb(a * 0.6f, 0.9f, 0.85f, 1f);
            float px = 0, py = 0, qx = 0, qy = 0;
            for (int i = 0; i <= seg; i++) {
                float ang = i * Mth.TWO_PI / seg;
                float wob = 1f + 0.07f * Mth.sin(ang * 5f + t * 0.25f + k) + 0.04f * Mth.sin(ang * 3f - t * 0.17f);
                float r = base * wob;
                float x = cx + Mth.cos(ang) * r, y = cy + Mth.sin(ang) * r * 0.92f;
                float x2 = cx + Mth.cos(ang) * (r + thick), y2 = cy + Mth.sin(ang) * (r + thick) * 0.92f;
                if (i > 0) NavMapScreen.quad(vc, m, px, py, x, y, x2, y2, qx, qy, col, col);
                px = x;
                py = y;
                qx = x2;
                qy = y2;
            }
        }
    }

    private static void fill(VertexConsumer vc, Matrix4f m, float x1, float y1, float x2, float y2, int color) {
        NavMapScreen.quad(vc, m, x1, y1, x1, y2, x2, y2, x2, y1, color, color);
    }

    private static int argb(float a, float r, float g, float b) {
        return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | ((int) (Mth.clamp(r, 0f, 1f) * 255) << 16)
                | ((int) (Mth.clamp(g, 0f, 1f) * 255) << 8) | (int) (Mth.clamp(b, 0f, 1f) * 255);
    }
}
