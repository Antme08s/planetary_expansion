package dev.antme08.planetaryexpansion.ferra.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.antme08.planetaryexpansion.creatures.client.CreatureRenderer;
import dev.antme08.planetaryexpansion.creatures.client.JsonMobModel;
import dev.antme08.planetaryexpansion.ferra.mobs.BlindStalker;
import dev.antme08.planetaryexpansion.ferra.mobs.FerraMobs;
import dev.antme08.planetaryexpansion.ferra.mobs.RustWorm;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Cliente de las criaturas de Ferra: renderers y animaciones. */
public final class FerraMobsClient {
    private FerraMobsClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FerraMobsClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FerraMobs.RUST_WORM.get(), WormRenderer::new);
        event.registerEntityRenderer(FerraMobs.BLIND_STALKER.get(), ctx -> new CreatureRenderer<BlindStalker>(ctx, "blind_stalker", 0.5F, 1.0F,
                CreatureRenderer.Swim.NONE, FerraMobsClient::stalker,
                e -> e.isHunting() ? 0.85 + 0.15 * Math.sin(e.tickCount * 0.5) : 0.3 + 0.1 * Math.sin(e.tickCount * 0.08)));
    }

    private static void add(ModelPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot += x;
        p.yRot += y;
        p.zRot += z;
    }

    /**
     * Gusano: el modelo es horizontal (cabeza hacia -z); fuera de la tierra se dibuja casi de pie, con la mitad de atrás
     * enterrada, y al salir o meterse sube o baja desde debajo del suelo.
     */
    static class WormRenderer extends CreatureRenderer<RustWorm> {
        private static final float PIVOT_Z = 2.2F, TILT = 70F, DEPTH = 3.8F;

        WormRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, "rust_worm", 0.0F, 1.0F, Swim.NONE, FerraMobsClient::worm, e -> 0.65 + 0.35 * Math.sin(e.tickCount * 0.15));
        }

        static float out(RustWorm e, float pt) {
            float t = e.phaseTick() + pt;
            return switch (e.phase()) {
                case BURIED -> 0F;
                case EMERGING -> Mth.clamp(t / RustWorm.EMERGE_TICKS, 0F, 1F);
                case OUT -> 1F;
                case DIVING -> 1F - Mth.clamp(t / RustWorm.DIVE_TICKS, 0F, 1F);
            };
        }

        @Override
        protected void setupRotations(RustWorm e, PoseStack pose, float bob, float yBodyRot, float pt, float scale) {
            super.setupRotations(e, pose, bob, yBodyRot, pt, scale);
            float o = out(e, pt);
            float ease = 1F - (1F - o) * (1F - o);
            pose.translate(0, -(1F - ease) * DEPTH, 0);
            pose.translate(0, 0.5F, 0);
            pose.mulPose(Axis.XP.rotationDegrees(TILT + Mth.sin(bob * 0.08F) * 4F));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(bob * 0.05F) * 5F));
            pose.translate(0, -0.5F, -PIVOT_Z);
        }
    }

    /** Anillos que ondulan; al morder, la cabeza da un golpe hacia delante. */
    private static void worm(JsonMobModel<RustWorm> m, RustWorm e, float ls, float la, float age, float hy, float hp) {
        for (int i = 0; i < 8; i++) {
            ModelPart p = m.part("seg" + i);
            add(p, Mth.sin(age * 0.18F - i * 0.8F) * 0.05F, Mth.sin(age * 0.22F - i * 0.7F) * 0.10F * (0.5F + i * 0.12F), 0);
        }
        ModelPart head = m.part("head");
        float bite = e.getAttackAnim(age - e.tickCount);
        add(head, -0.5F * Mth.sin(bite * Mth.PI), Mth.sin(age * 0.22F + 0.7F) * 0.08F, 0);
        if (head != null) {
            float pulse = 1F + 0.06F * Mth.sin(age * 0.6F) + 0.12F * Mth.sin(bite * Mth.PI);
            head.xScale = head.yScale = pulse;
        }
    }

    /** Rastreador: patas al trote; sin presa mueve la cabeza a los lados escuchando; cazando abre las "orejas" y la boca. */
    private static void stalker(JsonMobModel<BlindStalker> m, BlindStalker e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.75F;
        float a = Mth.cos(w) * 1.1F * la, b = Mth.cos(w + Mth.PI) * 1.1F * la;
        add(m.part("leg_l0"), a, 0, 0);
        add(m.part("leg_r1"), a, 0, 0);
        add(m.part("leg_r0"), b, 0, 0);
        add(m.part("leg_l1"), b, 0, 0);
        float hunt = e.isHunting() ? 1F : 0F;
        ModelPart head = m.part("head");
        if (hunt > 0) {
            add(head, hp * Mth.DEG_TO_RAD * 0.5F, hy * Mth.DEG_TO_RAD * 0.6F, 0);
        } else {
            add(head, 0.12F, Mth.sin(age * 0.05F) * 0.45F, Mth.sin(age * 0.05F) * 0.12F);   // escucha
        }
        float twitch = Mth.sin(age * 0.9F) * 0.06F * (1F - hunt);
        add(m.part("ear_l"), 0, 0, -0.35F * hunt + twitch);
        add(m.part("ear_r"), 0, 0, 0.35F * hunt - twitch);
        float bite = e.getAttackAnim(age - e.tickCount);
        add(m.part("jaw"), 0.25F * hunt + 0.6F * Mth.sin(bite * Mth.PI), 0, 0);
        add(m.part("tail"), -0.15F * hunt, Mth.sin(age * 0.15F) * 0.2F, 0);
        add(m.part("neck"), 0.15F * hunt, 0, 0);
        // grito de alerta: levanta la cabeza, abre la boca del todo, despliega las "orejas" y tiembla
        if (e.screamTicks() > 0) {
            float left = (e.screamTicks() - (age - e.tickCount)) / BlindStalker.SCREAM_TICKS;
            float k = Mth.sin(Mth.PI * Mth.clamp(1F - left, 0F, 1F));
            float shake = Mth.sin(age * 2.3F) * 0.07F * k;
            add(m.part("neck"), -0.75F * k, shake, 0);
            add(m.part("head"), -0.55F * k, shake, shake);
            add(m.part("jaw"), 1.0F * k + Mth.sin(age * 3.1F) * 0.08F * k, 0, 0);
            add(m.part("ear_l"), 0, 0, -0.9F * k);
            add(m.part("ear_r"), 0, 0, 0.9F * k);
            add(m.part("body"), -0.12F * k, 0, 0);
            add(m.part("tail"), -0.5F * k, shake * 2F, 0);
        }
    }
}
