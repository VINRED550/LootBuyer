package dev.lootbuyer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Тексты из messages.yml в формате MiniMessage. */
public final class Messages {
    private final LootBuyerPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration cfg;
    private DecimalFormat moneyFormat;

    public Messages(LootBuyerPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        String path = "lang/" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists() && plugin.getResource(path) == null) {
            plugin.getLogger().warning("Language file " + path + " not found, falling back to en");
            path = "lang/en.yml";
            file = new File(plugin.getDataFolder(), path);
        }
        cfg = YamlConfiguration.loadConfiguration(file);

        // Missing keys fall back to the bundled file of the same language, or English for custom languages
        String defaults = plugin.getResource(path) != null ? path : "lang/en.yml";
        try (InputStream in = plugin.getResource(defaults)) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read bundled " + defaults + ": " + e.getMessage());
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(separator("format.grouping-separator", ','));
        symbols.setDecimalSeparator(separator("format.decimal-separator", '.'));
        moneyFormat = new DecimalFormat("#,##0.##", symbols);
    }

    private char separator(String key, char fallback) {
        String s = cfg.getString(key, "");
        return s.isEmpty() ? fallback : s.charAt(0);
    }

    private TagResolver tags(TagResolver... extra) {
        return TagResolver.resolver(Placeholder.parsed("prefix", cfg.getString("prefix", "")), TagResolver.resolver(extra));
    }

    public Component one(String key, TagResolver... extra) {
        return mm.deserialize(cfg.getString(key, key), tags(extra));
    }

    /** Строки для описания предмета, без курсива по умолчанию. */
    public List<Component> lore(String key, TagResolver... extra) {
        TagResolver t = tags(extra);
        return cfg.getStringList(key).stream().map(line -> noItalic(mm.deserialize(line, t))).toList();
    }

    public void send(CommandSender to, String key, TagResolver... extra) {
        String raw = cfg.getString(key, key);
        if (!raw.isEmpty()) to.sendMessage(mm.deserialize(raw, tags(extra)));
    }

    public void broadcast(String key, TagResolver... extra) {
        String raw = cfg.getString(key, key);
        if (!raw.isEmpty()) Bukkit.broadcast(mm.deserialize(raw, tags(extra)));
    }

    // PlaceholderAPI может вызывать плейсхолдеры не из главного потока, а DecimalFormat не потокобезопасен
    public synchronized String money(double value) {
        return moneyFormat.format(value);
    }

    public String multiplier(double value) {
        return "x" + money(value);
    }

    public String time(long millis) {
        long total = Math.max(0, millis / 1000);
        long h = total / 3600, m = total % 3600 / 60, s = total % 60;
        String pattern;
        if (h > 0) pattern = cfg.getString("time.hours", "<h>h <m>m");
        else if (m > 0) pattern = cfg.getString("time.minutes", "<m>m <s>s");
        else pattern = cfg.getString("time.seconds", "<s>s");
        return pattern
                .replace("<h>", Long.toString(h))
                .replace("<m>", h > 0 ? String.format("%02d", m) : Long.toString(m))
                .replace("<s>", String.format("%02d", s));
    }

    public static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
