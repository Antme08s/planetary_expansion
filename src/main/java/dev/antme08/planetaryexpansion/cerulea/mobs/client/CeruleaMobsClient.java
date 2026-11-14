package dev.antme08.planetaryexpansion.cerulea.mobs.client;

import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import dev.antme08.planetaryexpansion.cerulea.mobs.CeruleaMobs;
import dev.antme08.planetaryexpansion.cerulea.mobs.Croaker;
import dev.antme08.planetaryexpansion.cerulea.mobs.FangArrow;
import dev.antme08.planetaryexpansion.cerulea.mobs.Glider;
import dev.antme08.planetaryexpansion.cerulea.mobs.Grazer;
import dev.antme08.planetaryexpansion.cerulea.mobs.Hopper;
import dev.antme08.planetaryexpansion.cerulea.mobs.Ironjaw;
import dev.antme08.planetaryexpansion.cerulea.mobs.Lurker;
import dev.antme08.planetaryexpansion.cerulea.mobs.Ribbonfin;
import dev.antme08.planetaryexpansion.cerulea.mobs.Runner;
import dev.antme08.planetaryexpansion.creatures.client.CreatureRenderer;
import dev.antme08.planetaryexpansion.creatures.client.JsonMobModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente de las criaturas de Cerúlea: renderers (modelo JSON + capa que brilla) y animaciones. */
public final class CeruleaMobsClient {
    private CeruleaMobsClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CeruleaMobsClient::renderers);
    }

    private static double night(Entity e) { return e.level().isNight() ? 1.0 : 0.0; }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CeruleaMobs.GRAZER.get(), ctx -> new CreatureRenderer<Grazer>(ctx, "grazer", 0.6F, 1.0F,
                CreatureRenderer.Swim.NONE, CeruleaMobsClient::grazer, e -> 0.35 + 0.65 * night(e)));
        event.registerEntityRenderer(CeruleaMobs.HOPPER.get(), ctx -> new CreatureRenderer<Hopper>(ctx, "hopper", 0.3F, 1.0F,
                CreatureRenderer.Swim.NONE, CeruleaMobsClient::hopper, e -> 0.0));
        event.registerEntityRenderer(CeruleaMobs.GLIDER.get(), ctx -> new CreatureRenderer<Glider>(ctx, "glider", 0.35F, 1.0F,
                CreatureRenderer.Swim.NONE, CeruleaMobsClient::glider, e -> 0.25 + 0.75 * night(e)));
        event.registerEntityRenderer(CeruleaMobs.LURKER.get(), ctx -> new CreatureRenderer<Lurker>(ctx, "lurker", 0.6F, 1.0F,
                CreatureRenderer.Swim.NONE, CeruleaMobsClient::lurker,
                e -> (e.getTarget() != null || e.isAggressive() ? 1.0 : 0.75 + 0.25 * Math.sin(e.tickCount * 0.06))));
        event.registerEntityRenderer(CeruleaMobs.CROAKER.get(), ctx -> new CreatureRenderer<Croaker>(ctx, "croaker", 0.3F, 1.0F,
                CreatureRenderer.Swim.NONE, CeruleaMobsClient::croaker, e -> 0.0));
        event.registerEntityRenderer(CeruleaMobs.RIBBONFIN.get(), RibbonfinRenderer::new);
        event.registerEntityRenderer(CeruleaMobs.RUNNER.get(), ctx -> new CreatureRenderer<Runner>(ctx, "runner", 0.45F, 1.0F,
                CreatureRenderer.Swim.NONE, CeruleaMobsClient::runner, e -> 0.25 + 0.65 * night(e)));
        event.registerEntityRenderer(CeruleaMobs.IRONJAW.get(), ctx -> new CreatureRenderer<Ironjaw>(ctx, "ironjaw", 0.9F, 1.0F,
                CreatureRenderer.Swim.SWIMMER, CeruleaMobsClient::ironjaw, e -> e.isCharging() ? 1.0 : 0.55 + 0.2 * Math.sin(e.tickCount * 0.05)));
        event.registerEntityRenderer(CeruleaMobs.FANG_ARROW_ENTITY.get(), ctx -> new ArrowRenderer<FangArrow>(ctx) {
            private final ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID,
                    "textures/entity/projectiles/fang_arrow.png");

            @Override
            public ResourceLocation getTextureLocation(FangArrow entity) { return tex; }
        });
    }

    /** Pez de cinta: el mismo modelo con la textura de su color. */
    static class RibbonfinRenderer extends CreatureRenderer<Ribbonfin> {
        private static final ResourceLocation AZURE = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/entity/ribbonfin.png");
        private static final ResourceLocation MIRE = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "textures/entity/ribbonfin_mire.png");

        RibbonfinRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, "ribbonfin", 0.2F, 1.0F, Swim.FISH, CeruleaMobsClient::fish, e -> 0.0);
        }

        @Override
        public ResourceLocation getTextureLocation(Ribbonfin entity) { return entity.variant() == 1 ? MIRE : AZURE; }
    }

    // ------------------------------------------------------------------ animaciones

    private static void add(ModelPart p, float x, float y, float z) {
        if (p == null) return;
        p.xRot += x;
        p.yRot += y;
        p.zRot += z;
    }

    private static void down(ModelPart p, float dy, float dz) {
        if (p == null) return;
        p.y += dy;
        p.z += dz;
    }

    private static <T extends Mob> void look(JsonMobModel<T> m, float hy, float hp, float k) {
        add(m.part("head"), hp * Mth.DEG_TO_RAD * k, hy * Mth.DEG_TO_RAD * k, 0);
    }

    /** Patas en trípode alterno (seis patas): l0, r1, l2 contra r0, l1, r2. */
    private static void grazer(JsonMobModel<Grazer> m, Grazer e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.6662F;
        for (int i = 0; i < 3; i++) {
            float a = Mth.cos(w + ((i % 2 == 0) ? 0 : Mth.PI)) * 1.1F * la;
            add(m.part("leg_l" + i), a, 0, 0);
            add(m.part("leg_r" + i), -a, 0, 0);
        }
        float eat = e.headDown(age - e.tickCount);
        if (eat > 0) {
            add(m.part("neck"), 0.55F * eat, 0, 0);
            down(m.part("head"), 6.5F * eat, -1.0F * eat);
            add(m.part("head"), 0.7F * eat + Mth.sin(age * 1.1F) * 0.08F * eat, 0, 0);
        } else {
            look(m, hy, hp, 0.6F);
        }
        add(m.part("tail"), 0, Mth.sin(age * 0.12F) * 0.25F, 0);
        if (e.isBaby()) {
            ModelPart head = m.part("head");
            if (head != null) head.xScale = head.yScale = head.zScale = 1.25F;
        }
    }

    /** Saltarín: al estar en el aire estira las patas traseras y echa las orejas hacia atrás. */
    private static void hopper(JsonMobModel<Hopper> m, Hopper e, float ls, float la, float age, float hy, float hp) {
        boolean air = !e.onGround();
        float jump = air ? 1F : 0F;
        add(m.part("leg_l0"), 0.9F * jump, 0, 0);
        add(m.part("leg_r0"), 0.9F * jump, 0, 0);
        add(m.part("arm_l"), -0.4F * jump, 0, 0);
        add(m.part("arm_r"), -0.4F * jump, 0, 0);
        float flop = Mth.sin(age * 0.09F) * 0.06F;
        add(m.part("ear_l"), 0.5F * jump + flop, 0, 0);
        add(m.part("ear_r"), 0.5F * jump - flop, 0, 0);
        look(m, hy, hp, 0.7F);
        ModelPart head = m.part("head");
        if (head != null && !air) head.y += Mth.sin(age * 0.5F) * 0.15F * la;   // olfatea
    }

    /** Planeador: membranas abiertas al planear, plegadas posado; patas al caminar; cola. */
    private static void glider(JsonMobModel<Glider> m, Glider e, float ls, float la, float age, float hy, float hp) {
        float s = e.spread(age - e.tickCount);
        for (String w : new String[]{"wing_l", "wing_r"}) {
            ModelPart p = m.part(w);
            if (p == null) continue;
            p.xScale = 0.28F + 0.72F * s;                          // plegada: pegada al cuerpo
            float flap = s > 0.5F ? Mth.sin(age * 0.35F) * 0.05F : Mth.cos(ls * 0.9F) * 0.25F * la;
            add(p, 0, (w.endsWith("l") ? 1 : -1) * flap, (w.endsWith("l") ? -1 : 1) * (0.25F * (1F - s)));
        }
        add(m.part("tail"), s > 0.5F ? 0.1F : 0, Mth.sin(age * (s > 0.5F ? 0.2F : 0.1F)) * 0.25F, 0);
        look(m, hy, hp, 0.6F);
        // 1.0.3: patas que caminan como un lagarto (en diagonal); al planear se recogen hacia atrás
        float walk = Mth.cos(ls * 0.9F) * 1.0F * la * (1F - s);
        add(m.part("leg_l0"), walk + 1.3F * s, 0, 0);
        add(m.part("leg_r1"), walk + 1.3F * s, 0, 0);
        add(m.part("leg_r0"), -walk + 1.3F * s, 0, 0);
        add(m.part("leg_l1"), -walk + 1.3F * s, 0, 0);
        add(m.part("body"), 0, Mth.sin(ls * 0.9F) * 0.08F * la * (1F - s), 0);
    }

    /** Acechador: patas en diagonal, cola que se mueve, mandíbula que se abre al atacar. */
    private static void lurker(JsonMobModel<Lurker> m, Lurker e, float ls, float la, float age, float hy, float hp) {
        float w = ls * 0.6F;
        float a = Mth.cos(w) * 0.9F * la;
        add(m.part("leg_l0"), a, 0, 0);
        add(m.part("leg_r1"), a, 0, 0);
        add(m.part("leg_r0"), -a, 0, 0);
        add(m.part("leg_l1"), -a, 0, 0);
        add(m.part("tail"), 0, Mth.sin(age * 0.1F + ls * 0.3F) * 0.3F, 0);
        add(m.part("body"), 0, Mth.sin(ls * 0.3F) * 0.04F * la, 0);
        look(m, hy, hp, 0.5F);
        float hit = Mth.sin(m.attackTime * Mth.PI);
        float open = (e.isAggressive() ? 0.25F : 0.05F + 0.04F * (Mth.sin(age * 0.07F) + 1)) + hit * 0.5F;
        add(m.part("jaw"), open, 0, 0);   // la mandíbula cuelga de la cabeza (JsonMobModel)
    }

    /** Croador: saco vocal que se infla al croar; patas traseras estiradas al saltar. */
    private static void croaker(JsonMobModel<Croaker> m, Croaker e, float ls, float la, float age, float hy, float hp) {
        float sac = e.sac(age - e.tickCount);
        ModelPart p = m.part("sac");
        if (p != null) {
            p.xScale = 1F + 0.25F * sac;
            p.yScale = p.zScale = 1F + 0.7F * sac;
        }
        float jump = e.onGround() ? 0F : 1F;
        add(m.part("leg_l1"), 0.9F * jump, 0, 0);
        add(m.part("leg_r1"), 0.9F * jump, 0, 0);
        add(m.part("leg_l0"), -0.5F * jump, 0, 0);
        add(m.part("leg_r0"), -0.5F * jump, 0, 0);
        if (e.isInWater()) {   // nada: patadas
            float k = Mth.sin(age * 0.5F) * 0.6F;
            add(m.part("leg_l1"), k, 0, 0);
            add(m.part("leg_r1"), -k, 0, 0);
        }
    }

    private static void fish(JsonMobModel<Ribbonfin> m, Ribbonfin e, float ls, float la, float age, float hy, float hp) {
        float speed = e.isInWater() ? 0.5F : 1.6F;
        float s = Mth.sin(age * speed);
        add(m.part("tail"), 0, s * 0.45F, 0);
        add(m.part("body"), 0, -s * 0.07F, 0);
    }

    /**
     * Ironjaw: la cola y el cuerpo ondulan al nadar (más rápido al embestir), las aletas laterales baten y la boca de
     * cuatro pétalos se abre al embestir (en el diseño, abrir = +104° en x arriba/abajo y −106° en y a los lados; el
     * modelo del juego invierte x e y).
     */
    private static void ironjaw(JsonMobModel<Ironjaw> m, Ironjaw e, float ls, float la, float age, float hy, float hp) {
        float open = e.mouth(age - e.tickCount);
        float r = Mth.DEG_TO_RAD;
        add(m.part("jaw_t"), -104F * r * open, 0, 0);
        add(m.part("jaw_b"), 104F * r * open, 0, 0);
        add(m.part("jaw_l"), 0, 106F * r * open, 0);
        add(m.part("jaw_r"), 0, -106F * r * open, 0);
        float speed = e.isCharging() ? 0.55F : 0.18F;
        float amp = e.isCharging() ? 0.35F : 0.22F;
        add(m.part("tail"), 0, Mth.sin(age * speed) * amp, 0);
        add(m.part("body"), 0, -Mth.sin(age * speed) * amp * 0.25F, 0);
        float flap = Mth.sin(age * 0.12F) * 0.18F;
        add(m.part("fin_l"), 0, 0, flap);
        add(m.part("fin_r"), 0, 0, -flap);
    }

    /** Corredor: galope, agachado al acechar, sentado si se lo ordenan, cola, mandíbula al morder. */
    private static void runner(JsonMobModel<Runner> m, Runner e, float ls, float la, float age, float hy, float hp) {
        float crouch = e.crouch(age - e.tickCount);
        boolean sit = e.isInSittingPose();
        float w = ls * 0.6662F;
        float a = Mth.cos(w) * 1.2F * la * (1F - 0.5F * crouch);
        add(m.part("leg_l0"), a, 0, 0);
        add(m.part("leg_r0"), -a, 0, 0);
        add(m.part("leg_l1"), -a, 0, 0);
        add(m.part("leg_r1"), a, 0, 0);
        if (crouch > 0 && !sit) {   // agachado: el cuerpo baja y las patas se doblan
            for (String g : new String[]{"body", "neck", "head", "tail"}) down(m.part(g), 2.6F * crouch, 0);
            for (String g : new String[]{"leg_l0", "leg_r0", "leg_l1", "leg_r1"}) {
                ModelPart p = m.part(g);
                if (p != null) {
                    p.y += 2.6F * crouch;
                    p.yScale = 1F - 0.2F * crouch;
                }
            }
            add(m.part("tail"), -0.3F * crouch, 0, 0);
        }
        if (sit) {
            // sentado como un perro: el cuerpo baja 6 y se inclina hacia arriba (la grupa toca el suelo), las patas de
            // delante se acortan para seguir apoyadas y las de atrás quedan dobladas hacia delante en el suelo
            float tilt = 0.45F, sin = Mth.sin(tilt);
            ModelPart body = m.part("body");
            if (body != null) {
                body.y += 6F;
                body.xRot -= tilt;
            }
            for (String g : new String[]{"neck", "head"}) down(m.part(g), 6F - 8F * sin, 0);
            for (String g : new String[]{"leg_l0", "leg_r0"}) {
                ModelPart p = m.part(g);
                if (p == null) continue;
                float drop = 6F - 5F * sin;
                p.y += drop;
                p.yScale = (13F - drop) / 13F;
                p.xRot = -0.15F;
            }
            for (String g : new String[]{"leg_l1", "leg_r1"}) {
                ModelPart p = m.part(g);
                if (p == null) continue;
                p.y += 6F + 4.5F * sin + 1.5F;
                p.yScale = 0.6F;
                p.xRot = -1.45F;
            }
            ModelPart tail = m.part("tail");
            if (tail != null) {
                tail.y += 6F + 7F * sin + 1F;
                tail.xRot += 0.5F;
            }
        }
        add(m.part("tail"), 0, e.tailWag(age), 0);
        look(m, hy, hp, 0.8F);
        float hit = Mth.sin(m.attackTime * Mth.PI);
        add(m.part("jaw"), 0.05F + hit * 0.6F, 0, 0);
    }
}
