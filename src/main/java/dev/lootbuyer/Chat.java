package dev.lootbuyer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends the plugin's own (relocated) Adventure components on any 1.16+ server.
 * Players get BungeeCord chat components, which keeps translatable item names;
 * everything else gets a legacy §-string.
 */
final class Chat {
    // 1.16+ understands hex colors in §x§R§R§G§G§B§B form
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static final Map<NamedTextColor, Character> CODES = new HashMap<>();
    private static volatile boolean componentsUnavailable;

    static {
        CODES.put(NamedTextColor.BLACK, '0');
        CODES.put(NamedTextColor.DARK_BLUE, '1');
        CODES.put(NamedTextColor.DARK_GREEN, '2');
        CODES.put(NamedTextColor.DARK_AQUA, '3');
        CODES.put(NamedTextColor.DARK_RED, '4');
        CODES.put(NamedTextColor.DARK_PURPLE, '5');
        CODES.put(NamedTextColor.GOLD, '6');
        CODES.put(NamedTextColor.GRAY, '7');
        CODES.put(NamedTextColor.DARK_GRAY, '8');
        CODES.put(NamedTextColor.BLUE, '9');
        CODES.put(NamedTextColor.GREEN, 'a');
        CODES.put(NamedTextColor.AQUA, 'b');
        CODES.put(NamedTextColor.RED, 'c');
        CODES.put(NamedTextColor.LIGHT_PURPLE, 'd');
        CODES.put(NamedTextColor.YELLOW, 'e');
        CODES.put(NamedTextColor.WHITE, 'f');
    }

    private Chat() {
    }

    /** §-string for inventory titles, item names and lore. Hex colors are downsampled before 1.16. */
    static String legacy(Component component) {
        return LEGACY.serialize(component);
    }

    static void send(CommandSender to, Component component) {
        if (to instanceof Player && !componentsUnavailable) {
            try {
                ((Player) to).spigot().sendMessage(toBungee(component));
                return;
            } catch (Throwable t) {
                // Server without the BungeeCord chat API: fall back to plain strings from now on
                componentsUnavailable = true;
            }
        }
        to.sendMessage(legacy(component));
    }

    private static BaseComponent toBungee(Component c) {
        BaseComponent out;
        if (c instanceof TextComponent) {
            out = new net.md_5.bungee.api.chat.TextComponent(((TextComponent) c).content());
        } else if (c instanceof TranslatableComponent) {
            TranslatableComponent t = (TranslatableComponent) c;
            net.md_5.bungee.api.chat.TranslatableComponent tr = new net.md_5.bungee.api.chat.TranslatableComponent(t.key());
            for (Component arg : t.args()) tr.addWith(toBungee(arg));
            out = tr;
        } else {
            out = new net.md_5.bungee.api.chat.TextComponent("");
        }

        Style style = c.style();
        TextColor color = style.color();
        if (color != null) out.setColor(toBungee(color));
        Boolean b;
        if ((b = state(style, TextDecoration.BOLD)) != null) out.setBold(b);
        if ((b = state(style, TextDecoration.ITALIC)) != null) out.setItalic(b);
        if ((b = state(style, TextDecoration.UNDERLINED)) != null) out.setUnderlined(b);
        if ((b = state(style, TextDecoration.STRIKETHROUGH)) != null) out.setStrikethrough(b);
        if ((b = state(style, TextDecoration.OBFUSCATED)) != null) out.setObfuscated(b);

        for (Component child : c.children()) out.addExtra(toBungee(child));
        return out;
    }

    private static Boolean state(Style style, TextDecoration decoration) {
        TextDecoration.State s = style.decoration(decoration);
        return s == TextDecoration.State.NOT_SET ? null : s == TextDecoration.State.TRUE;
    }

    private static ChatColor toBungee(TextColor color) {
        try {
            return ChatColor.of(color.asHexString());
        } catch (Throwable ignored) {
            // fall through to the nearest named color
        }
        NamedTextColor named = color instanceof NamedTextColor ? (NamedTextColor) color : NamedTextColor.nearestTo(color);
        return ChatColor.getByChar(CODES.get(named));
    }
}
