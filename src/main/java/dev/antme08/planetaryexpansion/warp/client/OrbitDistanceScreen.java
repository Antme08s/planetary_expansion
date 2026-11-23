package dev.antme08.planetaryexpansion.warp.client;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.warp.WarpDriveBlockEntity;
import dev.antme08.planetaryexpansion.warp.WarpJumpRequestPayload;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Selector de distancia de órbita (1.0.4): se abre desde "Viajar (orbitar)" del mapa de la consola. La altitud va de la
 * órbita más baja que libra el cubo del astro hasta el borde de su zona de influencia (en una estrella, 60 radios),
 * en escala logarítmica para poder pasar de 60 km a millones de km con el mismo control.
 */
public class OrbitDistanceScreen extends Screen {
    private static final String K = "nav." + PlanetaryExpansion.MODID + ".";

    private final Screen parent;
    private final BlockPos console;
    private final String body;
    private final String bodyName;
    private final boolean interstellar;
    private final WarpDriveBlockEntity.OrbitRange range;
    private final double minAlt, maxAlt, defAlt;
    private double alt;
    private AltSlider slider;

    public OrbitDistanceScreen(Screen parent, BlockPos console, String body, String bodyName, boolean interstellar) {
        super(Component.translatable(K + "orbit.title", bodyName));
        this.parent = parent;
        this.console = console;
        this.body = body;
        this.bodyName = bodyName;
        this.interstellar = interstellar;
        WarpDriveBlockEntity.OrbitRange r = null;
        try {
            r = WarpDriveBlockEntity.orbitRange(DeepSpaceHandler.getUniverse(), body);
        } catch (Throwable ignored) {
        }
        this.range = r;
        this.minAlt = r == null ? 1 : Math.max(1.0, r.altitudeKm(r.rMin()));
        this.maxAlt = r == null ? 2 : Math.max(minAlt * 1.01, r.altitudeKm(r.rMax()));
        this.defAlt = r == null ? 1 : Math.max(minAlt, Math.min(maxAlt, r.altitudeKm(r.rDefault())));
        this.alt = defAlt;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 28;
        slider = addRenderableWidget(new AltSlider(cx - 120, y, 240, 20));
        addRenderableWidget(Button.builder(Component.translatable(K + "orbit.closest"), b -> setAlt(minAlt)).bounds(cx - 120, y + 26, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(K + "orbit.default"), b -> setAlt(defAlt)).bounds(cx - 38, y + 26, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(K + "orbit.farthest"), b -> setAlt(maxAlt)).bounds(cx + 44, y + 26, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(K + (interstellar ? "orbit.go_interstellar" : "orbit.go")), b -> go())
                .bounds(cx - 120, y + 94, 116, 20).build()).active = range != null;
        addRenderableWidget(Button.builder(Component.translatable(K + "orbit.cancel"), b -> onClose()).bounds(cx + 4, y + 94, 116, 20).build());
    }

    private void setAlt(double v) {
        alt = Math.max(minAlt, Math.min(maxAlt, v));
        if (slider != null) slider.sync();
    }

    private void go() {
        try {
            PacketDistributor.sendToServer(new WarpJumpRequestPayload(console, body, alt));
        } catch (Throwable ignored) {
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String km(double v) {
        if (v >= 1.0e6) return String.format(Locale.ROOT, "%.2e", v);
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String time(double seconds) {
        if (seconds < 90) return String.format(Locale.ROOT, "%.0f s", seconds);
        if (seconds < 5400) return String.format(Locale.ROOT, "%.1f min", seconds / 60);
        if (seconds < 172800) return String.format(Locale.ROOT, "%.1f h", seconds / 3600);
        return String.format(Locale.ROOT, "%.1f d", seconds / 86400);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        parent.render(g, -1, -1, partial);
        g.fill(0, 0, width, height, 0x90000000);
        int cx = width / 2, top = height / 2 - 60;
        g.fill(cx - 136, top - 6, cx + 136, top + 170, 0xF0101828);
        g.renderOutline(cx - 136, top - 6, 272, 176, 0xFF4FA8E0);
        g.drawCenteredString(font, title, cx, top + 2, 0xFFD9A8FF);
        super.render(g, mouseX, mouseY, partial);
        if (range == null) return;
        double r = range.planetRadius() + alt * 1000.0;
        double v = Math.sqrt(range.mu() / r) / 1000.0;
        int y = height / 2 + 34 + 22;
        g.drawCenteredString(font, Component.translatable(K + "orbit.radii", String.format(Locale.ROOT, "%.2f", r / range.planetRadius())), cx, y - 22, 0xFFB0C0D0);
        g.drawCenteredString(font, Component.translatable(K + "orbit.period", time(range.period(r))), cx, y - 10, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable(K + "orbit.speed", String.format(Locale.ROOT, "%.2f", v)), cx, y + 2, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable(K + "orbit.range", km(minAlt), km(maxAlt)), cx, y + 18, 0xFF8090A0);
    }

    /** Control con escala logarítmica: valor 0..1 ↔ altitud mínima..máxima. */
    private class AltSlider extends AbstractSliderButton {
        AltSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), toValue(alt));
            updateMessage();
        }

        void sync() {
            this.value = toValue(alt);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(K + "orbit.altitude", km(alt)));
        }

        @Override
        protected void applyValue() {
            alt = Math.exp(Math.log(minAlt) + value * (Math.log(maxAlt) - Math.log(minAlt)));
        }
    }

    private double toValue(double a) {
        if (maxAlt <= minAlt) return 0;
        return Math.max(0, Math.min(1, (Math.log(a) - Math.log(minAlt)) / (Math.log(maxAlt) - Math.log(minAlt))));
    }
}
