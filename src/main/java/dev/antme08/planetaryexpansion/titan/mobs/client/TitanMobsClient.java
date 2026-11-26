package dev.antme08.planetaryexpansion.titan.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.creatures.client.CreatureRenderer;
import dev.antme08.planetaryexpansion.creatures.client.JsonMobModel;
import dev.antme08.planetaryexpansion.titan.mobs.IceArrow;
import dev.antme08.planetaryexpansion.titan.mobs.MethaneEel;
import dev.antme08.planetaryexpansion.titan.mobs.MistStalker;
import dev.antme08.planetaryexpansion.titan.mobs.TholinRuminant;
import dev.antme08.planetaryexpansion.titan.mobs.TitanMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Cliente de las criaturas de Titán (1.0.4): renderers y animaciones. */
public final class TitanMobsClient {
    private TitanMobsClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(TitanMobsClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TitanMobs.RUMINANT.get(), ctx -> new CreatureRenderer<TholinRuminant>(ctx, "tholin_ruminant",
                0.8F, 1.0F, CreatureRenderer.Swim.NONE, TitanMobsClient::ruminant, e -> 0.0));
        event.registerEntityRenderer(TitanMobs.EEL.get(), ctx -> new CreatureRenderer<MethaneEel>(ctx, "methane_eel",
                0.2F, 1.0F, CreatureRenderer.Swim.NONE, TitanMobsClient::eel, e -> 0.7 + 0.3 * Math.sin(e.tickCount * 0.1)));
        event.registerEntityRenderer(TitanMobs.STALKER.get(), StalkerRenderer::new);
        event.registerEntityRenderer(TitanMobs.ICE_ARROW_ENTITY.get(), ctx -> new ArrowRenderer<IceArrow>(ctx) {
            private final ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID,
                    "textures/entity/projectiles/ice_arrow.png");

            @Override
            public ResourceLocation getTextureLocation(IceArrow entity) { return tex; }
        });
    }

    private static void add(ModelPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot += x;
        p.yRot += y;
        p.zRot += z;
    }

    /** Rumiador: paso lento y pesado, cabeza que baja a pastar, velas que se mecen y cola. */
    private static void ruminant(JsonMobModel<TholinRuminant> m, TholinRuminant e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.55F;
        float a = Mth.cos(w) * 0.7F * la, b = Mth.cos(w + Mth.PI) * 0.7F * la;
        add(m.part("leg_l0"), a, 0, 0);
        add(m.part("leg_r1"), a, 0, 0);
        add(m.part("leg_r0"), b, 0, 0);
        add(m.part("leg_l1"), b, 0, 0);
        float eat = e.headDown(age - e.tickCount);
        ModelPart head = m.part("head");
        ModelPart neck = m.part("neck");
        if (eat > 0) {
            add(neck, 0.35F * eat, 0, 0);
            add(head, 0.45F * eat + Mth.sin(age * 0.9F) * 0.05F * eat, 0, 0);
            if (head != null) head.y += 1.6F * eat;   // la cabeza baja con el cuello
        } else {
            add(neck, hp * Mth.DEG_TO_RAD * 0.2F, hy * Mth.DEG_TO_RAD * 0.25F, 0);
            add(head, hp * Mth.DEG_TO_RAD * 0.4F, hy * Mth.DEG_TO_RAD * 0.4F, 0);
        }
        add(m.part("sails"), Mth.sin(age * 0.05F) * 0.03F, 0, Mth.sin(age * 0.09F) * 0.07F);
        add(m.part("tail"), 0, Mth.sin(age * 0.1F) * 0.25F, 0);
        add(m.part("body"), 0, 0, Mth.sin(w) * 0.03F * la);
        if (e.isBaby() && head != null) head.xScale = head.yScale = head.zScale = 1.3F;
    }

    /** Anguila: onda que recorre el cuerpo (más fuerte en la cola); fuera del metano, coletazos. */
    private static void eel(JsonMobModel<MethaneEel> m, MethaneEel e, float ls, float la, float age, float hy, float hp) {
        boolean wet = e.isInFluidType(dev.antme08.planetaryexpansion.titan.TitanBlocks.METHANE_TYPE.get());
        float speed = wet ? 0.35F : 1.1F;
        float amp = wet ? 0.16F : 0.4F;
        for (int i = 0; i < 6; i++) {
            ModelPart p = m.part("seg" + i);
            add(p, 0, Mth.sin(age * speed - i * 0.9F) * amp * (0.3F + i * 0.18F), 0);
        }
        if (wet) add(m.part("seg0"), e.getXRot() * Mth.DEG_TO_RAD, 0, 0);
    }

    /** Acechador: patas de araña en dos trípodes alternos; la linterna se mece; abre la boca al morder. */
    private static void stalker(JsonMobModel<MistStalker> m, MistStalker e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.9F;
        for (int k = 0; k < 3; k++) {
            float ph = (k % 2 == 0) ? 0F : Mth.PI;
            float swing = Mth.sin(w + ph) * 0.35F * la;
            float lift = Math.max(0F, Mth.cos(w + ph)) * 0.25F * la;
            add(m.part("leg_l" + k), 0, swing, lift);
            add(m.part("leg_r" + k), 0, swing, -lift);
        }
        float idle = Mth.sin(age * 0.07F);
        add(m.part("lure"), idle * 0.08F + (e.isCircling() ? Mth.sin(age * 0.3F) * 0.12F : 0F), 0, Mth.sin(age * 0.05F) * 0.06F);
        add(m.part("head"), hp * Mth.DEG_TO_RAD * 0.5F, hy * Mth.DEG_TO_RAD * 0.6F, 0);
        float bite = e.getAttackAnim(age - e.tickCount);
        add(m.part("jaw"), 0.15F + 0.7F * Mth.sin(bite * Mth.PI), 0, 0);
        add(m.part("body"), Mth.sin(age * 0.06F) * 0.02F, 0, 0);
    }

    /**
     * El acechador se funde con la bruma: casi opaco de cerca y casi invisible a más de 20 bloques (solo se ve su
     * linterna, que brilla siempre entera).
     */
    static class StalkerRenderer extends CreatureRenderer<MistStalker> {
        StalkerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, "mist_stalker", 0.0F, 1.0F, Swim.NONE, TitanMobsClient::stalker,
                    e -> e.isCircling() ? 0.75 + 0.25 * Math.sin(e.tickCount * 0.6) : 0.85);
        }

        @Override
        protected RenderType getRenderType(MistStalker entity, boolean bodyVisible, boolean translucent, boolean glowing) {
            return bodyVisible || translucent ? RenderType.entityTranslucent(getTextureLocation(entity)) : null;
        }

        @Override
        public void render(MistStalker entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double d = Math.sqrt(entity.distanceToSqr(cam));
            float a = (float) Mth.clamp(1.0 - (d - 6.0) / 16.0, 0.0, 1.0);
            getModel().alpha = 0.06F + 0.82F * a;
            super.render(entity, yaw, partialTick, pose, buffers, light);
            getModel().alpha = 1.0F;
        }
    }
}
