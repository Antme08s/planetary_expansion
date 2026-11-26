package dev.antme08.planetaryexpansion.creatures.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Modelo de criatura leído de {@code assets/planetary_expansion/mob_models/<nombre>.json} (lo genera
 * tools/designs/thalassa_mobs.py a partir de los diseños). Cada grupo es una parte con su pivote, para animarla; los
 * cubos girados van en sub-partes. Las animaciones las pone un {@link Animator} por criatura.
 */
public class JsonMobModel<T extends Entity> extends EntityModel<T> {
    public interface Animator<T extends Entity> {
        void animate(JsonMobModel<T> model, T entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch);
    }

    private final ModelPart root;
    private final Map<String, ModelPart> groups = new HashMap<>();
    private final Animator<T> animator;

    private JsonMobModel(ModelPart root, Animator<T> animator, java.util.Collection<String> names) {
        this.root = root;
        this.animator = animator;
        for (String name : names)   // 1.0.3: todos los grupos del JSON (cuello, orejas, alas…)
            if (root.hasChild(name)) groups.put(name, root.getChild(name));
        if (root.hasChild("head") && root.getChild("head").hasChild("jaw")) groups.put("jaw", root.getChild("head").getChild("jaw"));
        for (String name : new String[]{"body", "head", "jaw", "tail", "lure", "legs", "claw_r", "claw_l", "eyes", "spines", "shell"})
            if (root.hasChild(name)) groups.put(name, root.getChild(name));
        for (int i = 0; i < 8; i++) {   // 0.7.65: patas sueltas (leg_l0…, leg_r0…), cada una gira desde la cadera
            for (String side : new String[]{"leg_l", "leg_r"})
                if (root.hasChild(side + i)) groups.put(side + i, root.getChild(side + i));
        }
    }

    /** La parte de un grupo, o null si este modelo no lo tiene. */
    public ModelPart part(String name) { return groups.get(name); }

    public ModelPart root() { return root; }

    public static <T extends Entity> JsonMobModel<T> load(ResourceManager resources, String name, Animator<T> animator) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "mob_models/" + name + ".json");
        MeshDefinition mesh = new MeshDefinition();
        java.util.List<String> names = new java.util.ArrayList<>();
        int texW = 64, texH = 64;
        try (Reader r = new InputStreamReader(resources.getResourceOrThrow(id).open(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            JsonArray tex = json.getAsJsonArray("texture");
            texW = tex.get(0).getAsInt();
            texH = tex.get(1).getAsInt();
            PartDefinition rootDef = mesh.getRoot();
            JsonObject parts = json.getAsJsonObject("parts");
            // 1.0.3: la mandíbula cuelga de la cabeza (gira con ella; antes se despegaba al voltear la cabeza)
            java.util.List<Map.Entry<String, JsonElement>> order = new java.util.ArrayList<>(parts.entrySet());
            order.sort(java.util.Comparator.comparingInt(en -> en.getKey().equals("jaw") ? 1 : 0));
            JsonArray headPiv = parts.has("head") ? parts.getAsJsonObject("head").getAsJsonArray("pivot") : null;
            PartDefinition headDef = null;
            for (Map.Entry<String, JsonElement> e : order) {
                JsonObject partJson = e.getValue().getAsJsonObject();
                names.add(e.getKey());
                JsonArray piv = partJson.getAsJsonArray("pivot");
                PartDefinition part;
                if (e.getKey().equals("jaw") && headDef != null) {
                    part = headDef.addOrReplaceChild("jaw", CubeListBuilder.create(), PartPose.offset(
                            piv.get(0).getAsFloat() - headPiv.get(0).getAsFloat(), piv.get(1).getAsFloat() - headPiv.get(1).getAsFloat(),
                            piv.get(2).getAsFloat() - headPiv.get(2).getAsFloat()));
                } else {
                    part = rootDef.addOrReplaceChild(e.getKey(), CubeListBuilder.create(),
                            PartPose.offset(piv.get(0).getAsFloat(), piv.get(1).getAsFloat(), piv.get(2).getAsFloat()));
                    if (e.getKey().equals("head")) headDef = part;
                }
                int n = 0;
                for (JsonElement nodeEl : partJson.getAsJsonArray("nodes")) {
                    JsonObject node = nodeEl.getAsJsonObject();
                    CubeListBuilder cubes = CubeListBuilder.create();
                    for (JsonElement boxEl : node.getAsJsonArray("boxes")) {
                        JsonObject box = boxEl.getAsJsonObject();
                        JsonArray uv = box.getAsJsonArray("uv"), min = box.getAsJsonArray("min"), size = box.getAsJsonArray("size");
                        cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(box.get("mirror").getAsBoolean())
                                .addBox(min.get(0).getAsFloat(), min.get(1).getAsFloat(), min.get(2).getAsFloat(),
                                        size.get(0).getAsFloat(), size.get(1).getAsFloat(), size.get(2).getAsFloat());
                    }
                    JsonArray off = node.getAsJsonArray("offset"), rot = node.getAsJsonArray("rotation");
                    part.addOrReplaceChild("n" + (n++), cubes, PartPose.offsetAndRotation(
                            off.get(0).getAsFloat(), off.get(1).getAsFloat(), off.get(2).getAsFloat(),
                            rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat()));
                }
            }
        } catch (Exception ex) {
            PlanetaryExpansion.LOGGER.error("[Criaturas] No se pudo cargar el modelo {}", id, ex);
        }
        return new JsonMobModel<>(LayerDefinition.create(mesh, texW, texH).bakeRoot(), animator, names);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (animator != null) animator.animate(this, entity, limbSwing, limbSwingAmount, age, headYaw, headPitch);
    }

    /** 1.0.4: transparencia de todo el modelo (el acechador de bruma se funde con la bruma); 1 = opaco. */
    public float alpha = 1.0F;

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        if (alpha < 0.999F) {
            int a = Math.round(((color >>> 24) & 0xFF) * alpha);
            color = (a << 24) | (color & 0x00FFFFFF);
        }
        root.render(pose, buffer, light, overlay, color);
    }
}
