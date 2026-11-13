package dev.antme08.planetaryexpansion.client;

import dev.antme08.planetaryexpansion.armor.CustomSuitPiece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Oculta las partes del jugador que quedan dentro de un traje del mod (Impact Armor...), para que el pelo,
 * la chaqueta, las mangas o los pantalones de la skin no asomen por encima de la armadura.
 *
 * PlayerRenderer.render llama a setModelProperties (que vuelve a poner todo visible) justo antes de lanzar
 * RenderPlayerEvent.Pre, así que basta con cambiar la visibilidad aquí: en el siguiente fotograma, o al
 * quitarse la pieza, se restaura sola. Las capas de armadura usan su propio modelo y no se ven afectadas.
 * En primera persona (la mano) no se toca nada, igual que en vanilla.
 *
 * 0.4.3: al acabar de dibujar al jugador (RenderPlayerEvent.Post) se vuelven a poner visibles las partes
 * ocultadas. Algunos mods (TaCZ, los shaders de Iris con su pasada de sombras...) dibujan la mano en primera
 * persona con el mismo modelo sin pasar por setModelProperties, y veían el brazo oculto: la mano
 * desaparecía con la pechera puesta o parpadeaba.
 *
 * 0.4.4: con la pechera la mano seguía sin verse, así que algo dibuja la mano mientras se dibuja el cuerpo
 * del jugador. En primera persona (el propio jugador, sin ninguna pantalla abierta) ya no se oculta nada:
 * desde dentro del casco no se ve la skin. Además, al dibujar el brazo en primera persona se fuerza que
 * sea visible.
 */
public final class SuitBodyHider {
    private SuitBodyHider() {}

    /** Partes ocultadas en el último Pre, para devolverlas tal cual en el Post. */
    private static final ModelPart[] hidden = new ModelPart[14];
    private static int hiddenCount;

    private static void hide(ModelPart part) {
        if (part.visible && hiddenCount < hidden.length) {
            part.visible = false;
            hidden[hiddenCount++] = part;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        restore();
    }

    private static void restore() {
        for (int i = 0; i < hiddenCount; i++) {
            hidden[i].visible = true;
            hidden[i] = null;
        }
        hiddenCount = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        restore(); // por si un Pre anterior no tuvo su Post
        if (event.isCanceled()) return;
        Player player = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        // Primera persona: no tocar el modelo (la mano usa los mismos brazos). Solo se oculta en la vista previa
        // del jugador dentro del inventario (InventoryScreen.renderEntityInInventory, que usan también otros mods).
        // 0.4.4 miraba "mc.screen == null" (la mano desaparecía con el chat abierto); 0.4.7 miraba la proyección
        // ortográfica, pero la pasada de sombras de Iris también es ortográfica y la mano volvía a desaparecer.
        if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson() && !inInventoryPreview()) {
            return;
        }
        PlayerModel<?> model = event.getRenderer().getModel();

        if (isSuit(player, EquipmentSlot.HEAD)) {
            hide(model.head);
            hide(model.hat);
        }
        if (isSuit(player, EquipmentSlot.CHEST)) {
            hide(model.body);
            hide(model.jacket);
            hide(model.rightArm);
            hide(model.leftArm);
            hide(model.rightSleeve);
            hide(model.leftSleeve);
        }
        boolean legs = isSuit(player, EquipmentSlot.LEGS);
        boolean feet = isSuit(player, EquipmentSlot.FEET);
        if (legs || feet) {
            // La capa exterior de la pierna sobresale de los pantalones y de las botas.
            hide(model.rightPants);
            hide(model.leftPants);
        }
        if (legs && feet) {
            // Pantalones (parte de arriba) + botas (parte de abajo) cubren la pierna entera.
            hide(model.rightLeg);
            hide(model.leftLeg);
        }
    }

    private static final StackWalker WALKER = StackWalker.getInstance();

    private static boolean inInventoryPreview() {
        if (Minecraft.getInstance().screen == null) return false;
        return WALKER.walk(frames -> frames.limit(40).anyMatch(f -> f.getMethodName().startsWith("renderEntityInInventory")));
    }

    private static boolean loggedHiddenArm;

    /** Brazo en primera persona: siempre visible (si algo lo dejó oculto, se avisa una vez en el log). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderArm(RenderArmEvent event) {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(event.getPlayer()) instanceof PlayerRenderer renderer)) return;
        PlayerModel<?> model = renderer.getModel();
        ModelPart arm = event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        if (!arm.visible) {
            arm.visible = true;
            if (!loggedHiddenArm) {
                loggedHiddenArm = true;
                dev.antme08.planetaryexpansion.PlanetaryExpansion.LOGGER.warn("[Armaduras] El brazo en primera persona estaba oculto; se vuelve a mostrar");
            }
        }
    }

    private static boolean loggedCanceled;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderArmLast(RenderArmEvent event) {
        if (event.isCanceled() && !loggedCanceled && isSuit(event.getPlayer(), EquipmentSlot.CHEST)) {
            loggedCanceled = true;
            dev.antme08.planetaryexpansion.PlanetaryExpansion.LOGGER.warn("[Armaduras] Otro mod ha cancelado el dibujo del brazo en primera persona con la pechera puesta");
        }
    }

    private static boolean isSuit(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getItem() instanceof CustomSuitPiece;
    }
}
