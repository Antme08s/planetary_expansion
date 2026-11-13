package dev.antme08.planetaryexpansion.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * MultiBufferSource que pasa todo tal cual menos {@code RenderType.lineStrip()}: cada vez que alguien pide ese tipo
 * recibe un trazo nuevo que se escribe como segmentos sueltos en {@code RenderType.lines()} (mismo formato y shader).
 * Así un trazo nunca se une con el anterior. Lo usa la mesa de hologramas (mixin/HologramLineStripMixin).
 */
public final class StripToLinesSource implements MultiBufferSource {
    private final MultiBufferSource delegate;

    public StripToLinesSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (type == RenderType.lineStrip()) {
            return new Strip(delegate.getBuffer(RenderType.lines()));
        }
        return delegate.getBuffer(type);
    }

    /**
     * Convierte vértices de tira (A, B, C, ...) en segmentos (A-B, B-C, ...). El vértice anterior se guarda
     * completo; al empezar uno nuevo se escribe el anterior y luego el nuevo, cuyos atributos (color, normal)
     * pasan directo al búfer de líneas.
     */
    private static final class Strip implements VertexConsumer {
        private final VertexConsumer out;
        private boolean hasPrev, writing;
        // vértice anterior
        private float px, py, pz, pnx, pny, pnz;
        private int pr = 255, pg = 255, pb = 255, pa = 255;
        // vértice actual (se completa con setColor/setNormal)
        private float cx, cy, cz, cnx, cny = 1f, cnz;
        private int cr = 255, cg = 255, cb = 255, ca = 255;
        private boolean hasCur;

        Strip(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (hasCur) {
                px = cx; py = cy; pz = cz; pnx = cnx; pny = cny; pnz = cnz;
                pr = cr; pg = cg; pb = cb; pa = ca;
                hasPrev = true;
            }
            cx = x; cy = y; cz = z;
            hasCur = true;
            writing = hasPrev;
            if (writing) {
                out.addVertex(px, py, pz).setColor(pr, pg, pb, pa).setNormal(pnx, pny, pnz);
                out.addVertex(x, y, z);
                // valores por defecto por si el que dibuja no pone color o normal
                out.setColor(cr, cg, cb, ca);
                out.setNormal(cnx, cny, cnz);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            cr = r; cg = g; cb = b; ca = a;
            if (writing) out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            cnx = x; cny = y; cnz = z;
            if (writing) out.setNormal(x, y, z);
            return this;
        }
    }
}
