package dev.antme08.planetaryexpansion.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.antme08.planetaryexpansion.PlanetaryExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Modelos 3D de armadura hechos en Blockbench.
 *
 * tools/generate.py convierte el .bbmodel en assets/planetary_expansion/armor_models/&lt;diseño&gt;.json (coordenadas de
 * Minecraft, una lista de cubos por pieza y por parte del cuerpo). Aquí se construye un HumanoidModel por pieza
 * (casco, pechera, pantalones, botas) la primera vez que se dibuja. Minecraft copia cada frame la postura del
 * jugador (copyPropertiesTo), así que las piezas siguen al cuerpo, al agacharse, etc.
 */
public final class ArmorModels {
    private ArmorModels() {}

    private static final String[] PARTS = {"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final float[][] PIVOTS = {
            {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};

    private static final Map<String, Map<EquipmentSlot, HumanoidModel<LivingEntity>>> CACHE = new HashMap<>();
    private static boolean loggedError;

    /** Se borra al recargar recursos (F3+T), para ver un diseño nuevo sin reiniciar. */
    public static void clearCache() {
        CACHE.clear();
    }

    public static void onRegisterReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> clearCache());
    }

    public static IClientItemExtensions extensions(String design) {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                HumanoidModel<LivingEntity> model = get(design, slot);
                return model != null ? model : original;
            }
        };
    }

    private static HumanoidModel<LivingEntity> get(String design, EquipmentSlot slot) {
        Map<EquipmentSlot, HumanoidModel<LivingEntity>> models = CACHE.computeIfAbsent(design, ArmorModels::load);
        return models.get(slot);
    }

    private static Map<EquipmentSlot, HumanoidModel<LivingEntity>> load(String design) {
        Map<EquipmentSlot, HumanoidModel<LivingEntity>> models = new EnumMap<>(EquipmentSlot.class);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PlanetaryExpansion.MODID, "armor_models/" + design + ".json");
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) throw new IllegalStateException("no existe " + id);
            JsonObject json;
            try (Reader reader = resource.get().openAsReader()) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            JsonArray texture = json.getAsJsonArray("texture");
            int texW = texture.get(0).getAsInt(), texH = texture.get(1).getAsInt();
            JsonObject pieces = json.getAsJsonObject("pieces");
            models.put(EquipmentSlot.HEAD, build(pieces.getAsJsonObject("helmet"), texW, texH));
            models.put(EquipmentSlot.CHEST, build(pieces.getAsJsonObject("chestplate"), texW, texH));
            models.put(EquipmentSlot.LEGS, build(pieces.getAsJsonObject("leggings"), texW, texH));
            models.put(EquipmentSlot.FEET, build(pieces.getAsJsonObject("boots"), texW, texH));
        } catch (Exception e) {
            if (!loggedError) {
                loggedError = true;
                PlanetaryExpansion.LOGGER.error("[Armaduras] No se pudo cargar el modelo {}; se usa el modelo normal", id, e);
            }
        }
        return models;
    }

    private static HumanoidModel<LivingEntity> build(JsonObject piece, int texW, int texH) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int p = 0; p < PARTS.length; p++) {
            PartDefinition part = root.addOrReplaceChild(PARTS[p], CubeListBuilder.create(),
                    PartPose.offset(PIVOTS[p][0], PIVOTS[p][1], PIVOTS[p][2]));
            if (piece == null || !piece.has(PARTS[p])) continue;
            int n = 0;
            for (JsonElement nodeEl : piece.getAsJsonArray(PARTS[p])) {
                JsonObject node = nodeEl.getAsJsonObject();
                CubeListBuilder cubes = CubeListBuilder.create();
                for (JsonElement boxEl : node.getAsJsonArray("boxes")) {
                    JsonObject box = boxEl.getAsJsonObject();
                    JsonArray uv = box.getAsJsonArray("uv"), min = box.getAsJsonArray("min"), size = box.getAsJsonArray("size");
                    cubes.texOffs(Math.round(uv.get(0).getAsFloat()), Math.round(uv.get(1).getAsFloat()))
                            .mirror(box.get("mirror").getAsBoolean())
                            .addBox(min.get(0).getAsFloat(), min.get(1).getAsFloat(), min.get(2).getAsFloat(),
                                    size.get(0).getAsFloat(), size.get(1).getAsFloat(), size.get(2).getAsFloat(),
                                    new CubeDeformation(box.get("inflate").getAsFloat()));
                }
                JsonArray off = node.getAsJsonArray("offset"), rot = node.getAsJsonArray("rotation");
                part.addOrReplaceChild("n" + (n++), cubes, PartPose.offsetAndRotation(
                        off.get(0).getAsFloat(), off.get(1).getAsFloat(), off.get(2).getAsFloat(),
                        rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat()));
            }
        }
        ModelPart baked = LayerDefinition.create(mesh, texW, texH).bakeRoot();
        return new HumanoidModel<>(baked);
    }
}
