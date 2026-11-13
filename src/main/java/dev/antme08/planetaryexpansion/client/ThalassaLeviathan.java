package dev.antme08.planetaryexpansion.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Leviatán de Thalassa (0.7.54; 0.7.55: el triple de grande, más detalle y luces más tenues; 0.7.56: más lejos, opaco, rugido y temblor de cámara; 0.7.57: modo shaders, ruge varias veces, temblor continuo). Idea de un amigo del usuario: a veces, buceando hondo, se ve pasar a lo lejos la
 * silueta de una serpiente marina gigante (~275 bloques) con un brillo tenue. Es SOLO un efecto visual del cliente:
 * no es una entidad, no hace daño y no se guarda. Pasa a 70-120 bloques, en el borde de la niebla del agua; si el
 * jugador se le acerca, se desvanece. Comando de cliente {@code /leviathan [cross|below|tail]} para verlo ya.
 */
public final class ThalassaLeviathan {
    private ThalassaLeviathan() {}

    private static final String DIM = "planetary_expansion:thalassa";
    // 0.7.55: el triple de grande (~275 bloques) y más detallado, a petición del usuario
    private static final int N = 64;              // segmentos
    private static final double SEG = 4.3;        // separación entre segmentos (~275 bloques de largo)
    private static final double WAVE = 120.0;     // longitud de onda de la ondulación
    private static final double AMP = 14.0;       // amplitud lateral
    private static final double VAMP = 5.0;       // amplitud vertical
    private static final double SPEED = 1.0;      // bloques por tick
    // 0.7.56: distancias fijas (antes dependían de la niebla de Minecraft, que bajo el agua puede ser corta y con
    // shaders no coincide con lo que se ve): se ve a lo lejos y se funde con el agua de 110 a 240 bloques
    private static final double VIS_FULL = 110.0, VIS_END = 240.0;
    private static final double FLEE_DIST = 50.0;
    private static final double MAX_Y = 40.0;     // solo por debajo de esta altura (el hielo está en ~67-75)

    enum Variant { CROSS, BELOW, TAIL }

    /** Un paso del leviatán: la cabeza avanza en línea recta desde {@code start}; el cuerpo la sigue ondulando. */
    private static final class Pass {
        final Vec3 start, dir, side;
        final double length;     // recorrido total de la cabeza
        final float phase;
        int age;
        int fleeing = -1;        // ticks desde que empezó a desvanecerse (-1 = no)
        boolean soundNear;
        int nextRoar = 160 + RNG.nextInt(120); // 0.7.57: ruge varias veces mientras pasa

        /** 0..1: aparición al principio y desvanecimiento al huir. */
        float presence(float pt) {
            return fleeing >= 0 ? Math.max(0f, 1f - (fleeing + pt) / 60f) : Math.min(1f, (age + pt) / 40f);
        }

        Pass(Vec3 start, Vec3 dir, double length, float phase) {
            this.start = start;
            this.dir = dir.normalize();
            Vec3 s = this.dir.cross(new Vec3(0, 1, 0));
            this.side = s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize();
            this.length = length;
            this.phase = phase;
        }

        double headS(float pt) { return (age + pt) * SPEED * (fleeing >= 0 ? 1.0 + fleeing / 20.0 : 1.0); }

        /** Posición del segmento i (0 = cabeza). */
        Vec3 point(int i, double headS) {
            double s = headS - i * SEG;
            double env = 0.3 + 0.7 * i / (double) N;           // la cola se mueve más que la cabeza
            double lat = AMP * Math.sin(s / WAVE * Math.PI * 2 + phase) * env;
            double ver = VAMP * Math.sin(s / (WAVE * 1.7) * Math.PI * 2 + phase * 0.7) * env;
            return start.add(dir.scale(s)).add(side.scale(lat)).add(0, ver, 0);
        }

        boolean done() { return headS(0) - N * SEG > length || (fleeing >= 0 && fleeing > 60); }
    }

    private static Pass active;
    private static int underwaterTicks;
    private static int nextAt = -1;
    private static final RandomSource RNG = RandomSource.create();

    private static boolean inThalassa(ClientLevel level) {
        return level != null && level.dimension().location().toString().equals(DIM);
    }

    // ------------------------------------------------------------------ lógica

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LocalPlayer player = mc.player;
        if (player == null || !inThalassa(mc.level)) { active = null; return; }
        if (active != null) {
            active.age++;
            if (active.fleeing >= 0) active.fleeing++;
            else if (nearestDistance(active, player.getEyePosition(), 0) < FLEE_DIST) active.fleeing = 0;
            if (!active.soundNear && active.headS(0) > active.length * 0.45) {
                active.soundNear = true;
                Vec3 h = active.point(0, active.headS(0));
                roar(SoundEvents.ELDER_GUARDIAN_AMBIENT, h, 1.0f, 0.5f, 0);
                roar(SoundEvents.ELDER_GUARDIAN_AMBIENT, h, 1.0f, 0.45f, 2);
            }
            if (active.fleeing < 0 && --active.nextRoar <= 0) {
                active.nextRoar = 160 + RNG.nextInt(120); // cada 8-14 s
                roarBurst(active.point(0, active.headS(0)));
            }
            if (active.done()) active = null;
            if (shakeTicks > 0) shakeTicks--;
            return;
        }
        if (shakeTicks > 0) shakeTicks--;
        if (nextAt < 0) nextAt = 24000 + RNG.nextInt(24000); // 20-40 min de buceo
        if (player.isEyeInFluid(FluidTags.WATER) && player.getEyeY() < MAX_Y) {
            if (++underwaterTicks >= nextAt) {
                Variant[] order = shuffled();
                boolean ok = false;
                for (Variant v : order) if (trySpawn(v, mc.level, player, false)) { ok = true; break; }
                if (ok) { underwaterTicks = 0; nextAt = -1; }
                else underwaterTicks = nextAt - 600; // sin agua abierta alrededor: se reintenta en 30 s
            }
        }
    }

    private static Variant[] shuffled() {
        Variant[] v = Variant.values();
        for (int i = v.length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            Variant t = v[i]; v[i] = v[j]; v[j] = t;
        }
        return v;
    }

    private static boolean trySpawn(Variant variant, ClientLevel level, LocalPlayer player, boolean near) {
        Vec3 eye = player.getEyePosition();
        double dist = near ? 80 + RNG.nextDouble() * 20 : 115 + RNG.nextDouble() * 35;
        double ang = RNG.nextDouble() * Math.PI * 2;
        Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
        Pass p;
        switch (variant) {
            case CROSS -> {
                Vec3 closest = eye.add(out.scale(dist)).add(0, -15 + RNG.nextDouble() * 22, 0);
                Vec3 dir = new Vec3(-out.z, (RNG.nextDouble() - 0.5) * 0.12, out.x).scale(RNG.nextBoolean() ? 1 : -1);
                double span = Math.sqrt(Math.max(0, VIS_END * VIS_END - dist * dist)) + 40;
                p = new Pass(closest.subtract(dir.normalize().scale(span)), dir, span * 2, RNG.nextFloat() * 6.28f);
            }
            case BELOW -> {
                Vec3 closest = eye.add(out.scale(RNG.nextDouble() * 25)).add(0, -(45 + RNG.nextDouble() * 25), 0);
                Vec3 dir = new Vec3(Math.cos(ang + 1.6), 0, Math.sin(ang + 1.6));
                double span = 200;
                p = new Pass(closest.subtract(dir.scale(span)), dir, span * 2, RNG.nextFloat() * 6.28f);
            }
            default -> { // TAIL: la cola se pierde en la oscuridad, alejándose
                Vec3 dir = new Vec3(out.x, -0.08, out.z);
                Vec3 tailStart = eye.add(out.scale(dist * 0.85)).add(0, -6 + RNG.nextDouble() * 8, 0);
                Vec3 start = tailStart.add(dir.normalize().scale(N * SEG));
                p = new Pass(start, dir, 160, RNG.nextFloat() * 6.28f);
            }
        }
        if (!openWater(level, p, eye)) return false;
        active = p;
        Vec3 h = p.point(N / 2, p.headS(0));
        roar(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, h, 1.0f, 0.55f, 0);
        roar(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, h, 1.0f, 0.5f, 4);
        roarBurst(p.point(0, p.headS(0)));
        return true;
    }

    /**
     * Rugido fuerte (0.7.56; 0.7.57 más fuerte): varias capas a la vez, sin atenuación por distancia y en la categoría
     * MASTER (un solo sonido no pasa de volumen 1), y un golpe de temblor de cámara.
     */
    private static void roarBurst(Vec3 at) {
        // 0.7.60: más fuerte (el usuario lo pidió): el doble de capas, algunas con 2-3 ticks de retraso para que
        // el rugido suene más grueso y largo
        roar(SoundEvents.WARDEN_ROAR, at, 1.0f, 0.5f, 0);
        roar(SoundEvents.WARDEN_ROAR, at, 1.0f, 0.43f, 0);
        roar(SoundEvents.WARDEN_ROAR, at, 1.0f, 0.47f, 2);
        roar(SoundEvents.ENDER_DRAGON_GROWL, at, 1.0f, 0.55f, 0);
        roar(SoundEvents.ENDER_DRAGON_GROWL, at, 1.0f, 0.5f, 3);
        roar(SoundEvents.RAVAGER_ROAR, at, 1.0f, 0.5f, 0);
        roar(SoundEvents.RAVAGER_ROAR, at, 1.0f, 0.45f, 2);
        roar(SoundEvents.ELDER_GUARDIAN_CURSE, at, 1.0f, 0.5f, 1);
        shakeTicks = SHAKE_TICKS;
    }

    private static final int SHAKE_TICKS = 60;
    private static int shakeTicks;

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (Minecraft.getInstance().level == null) return;
        Pass p = active;
        float pt = (float) event.getPartialTick();
        // 0.7.57: temblor suave todo el tiempo que pasa, más un golpe fuerte con cada rugido
        double base = p != null && inThalassa(Minecraft.getInstance().level) ? 0.3 * p.presence(pt) : 0;
        double burst = shakeTicks > 0 ? 1.2 * Math.pow(shakeTicks / (double) SHAKE_TICKS, 1.5) : 0;
        double amp = base + burst; // grados
        if (amp <= 0.001) return;
        double t = (Minecraft.getInstance().level.getGameTime() % 100000) + pt;
        event.setYaw((float) (event.getYaw() + amp * 0.6 * Math.sin(t * 1.9)));
        event.setPitch((float) (event.getPitch() + amp * 0.5 * Math.sin(t * 2.7 + 1.0)));
        event.setRoll((float) (event.getRoll() + amp * 0.8 * Math.sin(t * 1.3 + 2.0)));
    }

    private static void roar(SoundEvent sound, Vec3 at, float volume, float pitch, int delay) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.MASTER,
                volume, pitch, RandomSource.create(), false, delay, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
    }

    /**
     * Agua en al menos el 70 % de los puntos del tramo central del recorrido, y línea de vista despejada (solo agua
     * entre el jugador y el punto) en al menos el 60 %. Sin esto, la roca que la niebla esconde tapaba el cuerpo y
     * se veía "cortado" (0.7.56).
     */
    private static boolean openWater(ClientLevel level, Pass p, Vec3 eye) {
        int total = 0, water = 0, seen = 0;
        double mid = p.length / 2;
        for (double s = Math.max(0, mid - 60); s <= Math.min(p.length + N * SEG, mid + 60); s += 10) {
            for (int i = 0; i < N; i += 8) {
                Vec3 v = p.point(i, s + i * SEG * 0.5);
                total++;
                if (!isWater(level, v)) continue;
                water++;
                if (clearSight(level, eye, v)) seen++;
            }
        }
        return total > 0 && water >= total * 0.7 && seen >= total * 0.6;
    }

    private static boolean isWater(ClientLevel level, Vec3 v) {
        return level.getFluidState(BlockPos.containing(v)).is(FluidTags.WATER);
    }

    private static boolean clearSight(ClientLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 step = d.scale(1.0 / len);
        for (double t = 4; t < len - 6; t += 5) if (!isWater(level, from.add(step.scale(t)))) return false;
        return true;
    }

    private static double nearestDistance(Pass p, Vec3 eye, float pt) {
        double hs = p.headS(pt), best = Double.MAX_VALUE;
        for (int i = 0; i < N; i += 2) best = Math.min(best, p.point(i, hs).distanceTo(eye));
        return best;
    }

    private static void playSound(SoundEvent sound, Vec3 at, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch,
                RandomSource.create(), at.x, at.y, at.z));
    }

    // ------------------------------------------------------------------ comando

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("leviathan").executes(c -> force(c, null));
        for (Variant v : Variant.values())
            root.then(Commands.literal(v.name().toLowerCase()).executes(c -> force(c, v)));
        event.getDispatcher().register(root);
    }

    private static int force(CommandContext<CommandSourceStack> ctx, Variant variant) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !inThalassa(mc.level)) {
            ctx.getSource().sendFailure(Component.literal("The leviathan only lives in Thalassa."));
            return 0;
        }
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            ctx.getSource().sendFailure(Component.literal("Dive into the water first."));
            return 0;
        }
        active = null;
        Variant[] order = variant != null ? new Variant[]{variant} : shuffled();
        for (int attempt = 0; attempt < 8; attempt++)
            for (Variant v : order)
                if (trySpawn(v, mc.level, player, attempt >= 5)) { // los últimos intentos, más cerca
                    ctx.getSource().sendSuccess(() -> Component.literal("Something huge is moving in the dark... (" + v.name().toLowerCase() + ")"), false);
                    return 1;
                }
        ctx.getSource().sendFailure(Component.literal("Not enough open water around here (try deeper or away from the floor)."));
        return 0;
    }

    // ------------------------------------------------------------------ dibujo

    private static double radius(int i) {
        if (i == 0) return 3.2;                 // hocico
        if (i <= 3) return 5.2;                 // cabeza
        if (i == 4) return 4.6;
        if (i <= 7) return 4.2;                 // cuello
        double t = (i - 7) / (double) (N - 8);
        if (t < 0.22) return 4.2 + t / 0.22 * 2.8;
        return Math.max(1.0, 7.0 * Math.pow(1 - (t - 0.22) / 0.78, 1.25) + 0.6);
    }

    private static Vec3 norm(Vec3 v) { return v.normalize(); }

    /** Lado y arriba para un eje {@code f} usando {@code hint} como "arriba" aproximado. */
    private static Vec3[] frame(Vec3 f, Vec3 hint) {
        Vec3 s = f.cross(hint);
        s = s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize();
        return new Vec3[]{f, s, s.cross(f).normalize()};
    }

    /** Distancia: 1 hasta 110 bloques, 0 a 240. */
    private static float distFade(double d) { return (float) Mth.clamp((VIS_END - d) / (VIS_END - VIS_FULL), 0, 1); }

    /**
     * Visibilidad del cuerpo por distancia (1 = color propio, 0 = color del agua). Con shaders siempre 1: el shader pone
     * su propia niebla y el color del agua de Minecraft no coincide con el suyo (se veía demasiado azul, 0.7.57).
     */
    private static float fade(double d, double fog) { return SHADERS ? 1f : distFade(d); }

    /** ¿Hay un pack de shaders de Iris activo? (por reflexión: Iris no es dependencia) */
    private static boolean SHADERS;
    private static java.lang.reflect.Method irisInUse;
    private static Object irisApi;
    private static boolean irisChecked;

    private static boolean irisShaders() {
        try {
            if (!irisChecked) {
                irisChecked = true;
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            }
            return irisInUse != null && (Boolean) irisInUse.invoke(irisApi);
        } catch (Throwable e) {
            irisInUse = null;
            return false;
        }
    }

    /** Color del agua del fotograma: los bloques lejanos se mezclan hacia él (opacos, sin transparencia). */
    private static float[] WATER = {0, 0, 0};

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Pass p = active;
        Minecraft mc = Minecraft.getInstance();
        if (p == null || !inThalassa(mc.level)) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        double fog = VIS_END;
        SHADERS = irisShaders();
        float[] fc = RenderSystem.getShaderFogColor();
        WATER = new float[]{fc[0], fc[1], fc[2]};
        float fade = p.fleeing >= 0 ? Math.max(0f, 1f - p.fleeing / 60f) : Math.min(1f, (p.age + pt) / 40f);
        if (fade <= 0) return;

        double hs = p.headS(pt);
        Vec3[] pts = new Vec3[N];
        for (int i = 0; i < N; i++) pts[i] = p.point(i, hs).subtract(cam);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        // 0.7.56: cuerpo OPACO y escribiendo profundidad (antes semitransparente: se veían las caras de dentro);
        // la distancia y la aparición se hacen mezclando el color hacia el del agua
        // con shaders: color propio y transparencia solo al aparecer y al huir (la niebla la pone el shader)
        if (SHADERS) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); } else RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // colores fijos, azul petróleo muy oscuro (antes salían del color de la niebla: con shaders quedaban azul vivo)
        float[] body = {0.040f, 0.066f, 0.082f};
        float[] belly = {0.066f, 0.104f, 0.122f};
        float[] dark = {0.028f, 0.048f, 0.060f};
        Vec3 UP = new Vec3(0, 1, 0);

        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < N - 1; i++) {
            Vec3 a = pts[i], c = a.add(pts[i + 1]).scale(0.5);
            float alpha = 1.0f * fade(c.length(), fog) * fade;
            if (alpha <= 0.01f) continue;
            Vec3[] fr = frame(norm(a.subtract(pts[i + 1])), UP);
            Vec3 f = fr[0], s = fr[1], u = fr[2];
            double r = radius(i);
            box(b, m, c, f, s, u, SEG * 0.68, r, r * 1.02, body, alpha);
            if (i >= 6 && i <= N - 8) // vientre
                box(b, m, c.subtract(u.scale(r * 0.98)), f, s, u, SEG * 0.55, r * 0.72, 0.3, belly, alpha);
            if (i >= 6 && i <= N - 8) { // placas dorsales inclinadas hacia atrás
                double hgt = (0.35 * r + 1.2) * (i % 2 == 0 ? 1.0 : 0.6);
                Vec3 d = norm(u.scale(0.85).subtract(f.scale(0.5)));
                box(b, m, c.add(u.scale(r * 1.02)).add(d.scale(hgt * 0.9)), d, s, norm(s.cross(d)), hgt, 0.3, SEG * 0.32, dark, alpha);
            }
            if (i >= 10 && i <= N - 14 && i % 4 == 2) for (int k = -1; k <= 1; k += 2) { // costillas-aleta
                Vec3 d = norm(s.scale(k * 0.8).subtract(f.scale(0.5)).subtract(u.scale(0.2)));
                box(b, m, c.add(s.scale(k * r)).add(d.scale(r * 0.45)), d, norm(u.cross(d)), u, r * 0.45, 1.1, 0.18, dark, alpha);
            }
            if (i == 8 || i == 30) for (int k = -1; k <= 1; k += 2) { // aletas pectorales
                double len = i == 8 ? 9.0 : 6.0;
                Vec3 d = norm(s.scale(k * 0.9).subtract(f.scale(0.45)).subtract(u.scale(0.3)));
                box(b, m, c.add(s.scale(k * r * 0.9)).add(d.scale(len * 0.5)), d, norm(u.cross(d)), u, len * 0.5, len * 0.22, 0.3, dark, alpha);
            }
        }
        { // cola: aleta vertical y horizontal
            Vec3 a = pts[N - 1];
            float alpha = 1.0f * fade(a.length(), fog) * fade;
            if (alpha > 0.01f) {
                Vec3[] fr = frame(norm(pts[N - 2].subtract(a)), UP);
                box(b, m, a.subtract(fr[0].scale(2.5)), fr[0], fr[1], fr[2], 4.5, 0.35, 6.5, dark, alpha);
                box(b, m, a.subtract(fr[0].scale(3.5)), fr[0], fr[1], fr[2], 3.5, 5.5, 0.35, dark, alpha);
            }
        }
        // cabeza monstruosa (0.7.55): boca abierta con colmillos, seis ojos, pico ganchudo y corona de púas
        float headAlpha = 1.0f * fade(pts[0].length(), fog) * fade;
        Head head = null;
        if (headAlpha > 0.01f) {
            float[] tooth = {0.19f, 0.23f, 0.24f};
            float[] mouth = {0.010f, 0.016f, 0.020f};
            double jaw = 22 + 8 * Math.sin((p.age + pt) * 0.04); // la mandíbula se abre y se cierra despacio
            head = new Head(pts[0], norm(pts[0].subtract(pts[1])), jaw);
            head.draw(b, m, body, dark, tooth, mouth, headAlpha);
        }
        MeshData bodyMesh = b.build();
        if (bodyMesh != null) BufferUploader.drawWithShader(bodyMesh);

        // luces tenues: puntos turquesa que miran a la cámara, mezcla aditiva, pulso lento
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        Vector3f left = camera.getLeftVector(), upv = camera.getUpVector();
        float pulse = 0.75f + 0.25f * Mth.sin((p.age + pt) * 0.05f);
        BufferBuilder g = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 8; i < N - 6; i += 3) {
            Vec3 a = pts[i], c = a.add(pts[i + 1]).scale(0.5);
            Vec3[] fr = frame(norm(a.subtract(pts[i + 1])), UP);
            double r = radius(i);
            for (int k = -1; k <= 1; k += 2)
                glow(g, m, c.add(fr[1].scale(k * (r + 0.1))).subtract(fr[2].scale(r * 0.25)), 0.9f, 1.0f, fog, fade * pulse, left, upv);
        }
        if (head != null) {
            for (Vec3 e : head.eyes()) glow(g, m, e, 0.5f, 0.75f, fog, fade * pulse, left, upv);
            for (Vec3 j : head.jawLights()) glow(g, m, j, 0.6f, 0.6f, fog, fade * pulse, left, upv);
        }
        MeshData glowMesh = g.build();
        if (glowMesh != null) BufferUploader.drawWithShader(glowMesh);

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    /** Cabeza en coordenadas locales: x adelante, y arriba, z a un lado; origen en el segmento 0. */
    private static final class Head {
        final Vec3 o, f, s, u, dj, pu, hinge;

        Head(Vec3 origin, Vec3 forward, double jawDeg) {
            Vec3[] fr = frame(forward, new Vec3(0, 1, 0));
            o = origin; f = fr[0]; s = fr[1]; u = fr[2];
            double th = Math.toRadians(jawDeg);
            dj = dir(Math.cos(th), -Math.sin(th), 0);
            pu = dir(Math.sin(th), Math.cos(th), 0);
            hinge = at(-10, -3.4, 0);
        }

        Vec3 at(double x, double y, double z) { return o.add(f.scale(x)).add(u.scale(y)).add(s.scale(z)); }
        Vec3 dir(double x, double y, double z) { return norm(f.scale(x).add(u.scale(y)).add(s.scale(z))); }

        void part(BufferBuilder b, Matrix4f m, Vec3 c, Vec3 d, Vec3 hint, double hl, double hs, double ht, float[] col, float a) {
            Vec3[] fr = frame(d, hint);
            box(b, m, c, fr[0], fr[1], fr[2], hl, hs, ht, col, a);
        }

        void spike(BufferBuilder b, Matrix4f m, Vec3 base, Vec3 d, double len, double t, float[] col, float a) {
            part(b, m, base.add(d.scale(len * 0.3)), d, u, len * 0.3, t, t, col, a);
            part(b, m, base.add(d.scale(len * 0.75)), d, u, len * 0.25, t * 0.5, t * 0.5, col, a);
        }

        void draw(BufferBuilder b, Matrix4f m, float[] body, float[] dark, float[] tooth, float[] mouth, float a) {
            part(b, m, at(4.0, 0.6, 0), f, u, 3.6, 3.0, 2.4, body, a);                       // hocico
            part(b, m, at(8.3, -1.0, 0), dir(0.45, -0.9, 0), u, 2.3, 1.3, 1.0, dark, a);      // pico ganchudo
            part(b, m, hinge.add(dj.scale(9.5)), dj, pu, 9.5, 3.3, 1.3, body, a);              // mandíbula abierta
            Vec3 tip = hinge.add(dj.scale(19)), hook = norm(dj.scale(0.4).add(pu.scale(0.9)));
            part(b, m, tip.add(hook.scale(1.6)), hook, u, 1.8, 1.1, 0.8, dark, a);             // gancho de la mandíbula
            part(b, m, hinge.add(dj.scale(8)).add(pu.scale(1.9)), dj, pu, 8.5, 2.8, 1.0, mouth, a); // interior de la boca
            double[] upper = {6.2, 3.8, 1.2, -1.4, -4.0, -6.6, -9.0};
            for (int i = 0; i < upper.length; i++)                                             // dientes de arriba
                for (int k = -1; k <= 1; k += 2)
                    spike(b, m, at(upper[i], -1.9, k * 2.5), dir(-0.15, -1, 0), i < 2 ? 3.0 : 1.7, 0.42, tooth, a);
            double[] lower = {17.0, 14.5, 12.0, 9.5, 7.0, 4.5};
            for (int i = 0; i < lower.length; i++)                                             // dientes de abajo
                for (int k = -1; k <= 1; k += 2)
                    spike(b, m, hinge.add(dj.scale(lower[i])).add(pu.scale(1.2)).add(s.scale(k * 2.5)),
                            norm(pu.add(dj.scale(0.15))), i < 2 ? 2.6 : 1.5, 0.42, tooth, a);
            double[][] crest = {{-7, 4.6, 3.0, -0.8, 0.5, 0.35, 11}, {-10, 4.3, 4.2, -0.85, 0.3, 0.55, 13},
                    {-13, 3.0, 5.0, -0.8, 0.05, 0.65, 12}, {-9, 5.1, 1.3, -0.7, 0.7, 0.15, 9},
                    {-11, -2.0, 5.0, -0.8, -0.2, 0.55, 8}, {-7.5, -2.8, 4.6, -0.7, -0.5, 0.5, 5.5}};
            for (int k = -1; k <= 1; k += 2) {
                part(b, m, at(-4.0, 3.9, k * 4.6), f, u, 4.2, 0.8, 0.7, dark, a);              // cejas
                for (double[] c : crest)                                                      // corona y mejillas
                    spike(b, m, at(c[0], c[1], k * c[2]), dir(c[3], c[4], k * c[5]), c[6], c[6] > 8 ? 0.9 : 0.7, dark, a);
                for (double[] e : new double[][]{{-1.5, 2.0}, {-4.0, 2.6}, {-6.5, 3.0}})        // cuencas de los ojos
                    part(b, m, at(e[0], e[1], k * 5.15), f, u, 0.8, 0.25, 0.6, mouth, a);
            }
            spike(b, m, at(-8, 5.3, 0), dir(-0.6, 0.8, 0), 7, 0.8, dark, a);
            for (double t : new double[]{6.0, 10.0})                                          // púas bajo la barbilla
                spike(b, m, hinge.add(dj.scale(t)).subtract(pu.scale(1.2)), norm(pu.scale(-0.8).subtract(dj.scale(0.6))), 3.5, 0.5, dark, a);
        }

        Vec3[] eyes() {
            Vec3[] e = new Vec3[6];
            double[][] pos = {{-1.5, 2.0}, {-4.0, 2.6}, {-6.5, 3.0}};
            int n = 0;
            for (int k = -1; k <= 1; k += 2) for (double[] q : pos) e[n++] = at(q[0], q[1], k * 5.45);
            return e;
        }

        Vec3[] jawLights() {
            Vec3[] j = new Vec3[4];
            int n = 0;
            for (double t : new double[]{6.0, 11.0})
                for (int k = -1; k <= 1; k += 2) j[n++] = hinge.add(dj.scale(t)).subtract(pu.scale(0.4)).add(s.scale(k * 3.4));
            return j;
        }
    }

    private static void glow(BufferBuilder g, Matrix4f m, Vec3 spot, float size, float strength, double fog, float mul,
                             Vector3f left, Vector3f up) {
        double d = spot.length();
        float alpha = 0.45f * strength * distFade(d) * mul; // 0.7.55: más tenues (antes 0.9)
        if (alpha <= 0.01f) return;
        billboard(g, m, spot, left, up, size, 0.45f, 0.95f, 0.85f, alpha);
        billboard(g, m, spot, left, up, size * 2.2f, 0.30f, 0.85f, 0.80f, alpha * 0.06f); // halo pequeño
    }

    private static void box(BufferBuilder b, Matrix4f m, Vec3 c, Vec3 f, Vec3 s, Vec3 u, double lf, double ls, double lu,
                            float[] col, float a) {
        Vec3[] v = new Vec3[8];
        int k = 0;
        for (int x = -1; x <= 1; x += 2)
            for (int y = -1; y <= 1; y += 2)
                for (int z = -1; z <= 1; z += 2)
                    v[k++] = c.add(f.scale(lf * x)).add(u.scale(lu * y)).add(s.scale(ls * z));
        // índices: x(f) bit 2, y(u) bit 1, z(s) bit 0
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        float[] shade = {0.8f, 0.8f, 0.7f, 1.15f, 0.9f, 0.9f}; // atrás, delante, abajo, arriba, lados
        // a = visibilidad. Sin shaders: 1 = color propio, 0 = color del agua (siempre opaco).
        // Con shaders: color propio con transparencia a (solo baja al aparecer y al huir).
        for (int fi = 0; fi < 6; fi++) {
            float sh = shade[fi];
            float r = Math.min(1f, col[0] * sh), g = Math.min(1f, col[1] * sh), bl = Math.min(1f, col[2] * sh), al = a;
            if (!SHADERS) {
                r = r * a + WATER[0] * (1 - a);
                g = g * a + WATER[1] * (1 - a);
                bl = bl * a + WATER[2] * (1 - a);
                al = 1f;
            }
            for (int idx : faces[fi]) {
                Vec3 p = v[idx];
                b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, bl, al);
            }
        }
    }

    private static void billboard(BufferBuilder b, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float size,
                                  float r, float g, float bl, float a) {
        float lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
        float ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        b.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, bl, a);
        b.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, bl, a);
        b.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, bl, a);
        b.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, bl, a);
    }
}
