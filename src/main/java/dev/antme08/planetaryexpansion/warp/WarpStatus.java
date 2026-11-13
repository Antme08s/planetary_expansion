package dev.antme08.planetaryexpansion.warp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Estado del motor tal como lo ve la consola. El orden importa: es el que viaja por la red. */
public enum WarpStatus {
    READY(ChatFormatting.GREEN),
    NO_DRIVE(ChatFormatting.RED),
    JUMPING(ChatFormatting.LIGHT_PURPLE),
    COOLING(ChatFormatting.AQUA),
    NO_COOLANT(ChatFormatting.RED),
    NO_ROTATION(ChatFormatting.RED),
    CHARGING(ChatFormatting.YELLOW),
    NEEDS_NITROGEN(ChatFormatting.RED),
    NOT_ON_SHIP(ChatFormatting.RED),
    NOT_IN_SPACE(ChatFormatting.GOLD);

    public final ChatFormatting color;

    WarpStatus(ChatFormatting color) {
        this.color = color;
    }

    public String key() {
        return "nav.planetary_expansion.status." + name().toLowerCase(Locale.ROOT);
    }

    public Component text() {
        return Component.translatable(key()).withStyle(color);
    }

    public static WarpStatus byId(int id) {
        WarpStatus[] v = values();
        return id >= 0 && id < v.length ? v[id] : NO_DRIVE;
    }
}
