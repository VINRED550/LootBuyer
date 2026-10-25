package dev.lootbuyer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Texts from lang/<language>.yml in MiniMessage format. */
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
        // Read as UTF-8 explicitly: a Windows server on Java 8–17 would otherwise use cp1251 and break Cyrillic
        cfg = new YamlConfiguration();
        if (file.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                cfg.load(r);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read " + path + ": " + e.getMessage());
            }
        }

        // Missing keys fall back to the bundled file of the same language, or English for custom languages
        String defaults = plugin.getResource(path) != null ? path : "lang/en.yml";
        try (InputStream in = plugin.getResource(defaults)) {
            if (in != null) {
                YamlConfiguration def = new YamlConfiguration();
                def.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(def);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read bundled " + defaults + ": " + e.getMessage());
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(separator("format.grouping-separator", ','));
        symbols.setDecimalSeparator(separator("format.decimal-separator", '.'));
        moneyFormat = new DecimalFormat("#,##0.##", symbols);
    }

    /**
     * Bukkit's getString(path, def) ignores the defaults section, so keys missing from an
     * older lang file would never fall back to the bundled text. Always go through here.
     */
    private String str(String key, String fallback) {
        String v = cfg.getString(key);
        return v != null ? v : fallback;
    }

    private char separator(String key, char fallback) {
        String s = str(key, "");
        return s.isEmpty() ? fallback : s.charAt(0);
    }

    private TagResolver tags(TagResolver... extra) {
        return TagResolver.resolver(Placeholder.parsed("prefix", str("prefix", "")), TagResolver.resolver(extra));
    }

    /** Unparsed MiniMessage text, for nesting one message inside another. */
    public String raw(String key) {
        return str(key, "");
    }

    public Component one(String key, TagResolver... extra) {
        return mm.deserialize(str(key, key), tags(extra));
    }

    /** §-string for titles and item names. */
    public String legacy(String key, TagResolver... extra) {
        return Chat.legacy(one(key, extra));
    }

    /** §-strings for item lore. */
    public List<String> lore(String key, TagResolver... extra) {
        TagResolver t = tags(extra);
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList(key)) out.add(Chat.legacy(mm.deserialize(line, t)));
        return out;
    }

    public void send(CommandSender to, String key, TagResolver... extra) {
        String raw = str(key, key);
        if (!raw.isEmpty()) Chat.send(to, mm.deserialize(raw, tags(extra)));
    }

    public void broadcast(String key, TagResolver... extra) {
        String raw = str(key, key);
        if (raw.isEmpty()) return;
        Component c = mm.deserialize(raw, tags(extra));
        for (Player p : Bukkit.getOnlinePlayers()) Chat.send(p, c);
        Chat.send(Bukkit.getConsoleSender(), c);
    }

    // PlaceholderAPI may call placeholders off the main thread, and DecimalFormat isn't thread-safe
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
        if (h > 0) pattern = str("time.hours", "<h>h <m>m");
        else if (m > 0) pattern = str("time.minutes", "<m>m <s>s");
        else pattern = str("time.seconds", "<s>s");
        return pattern
                .replace("<h>", Long.toString(h))
                .replace("<m>", h > 0 ? String.format("%02d", m) : Long.toString(m))
                .replace("<s>", String.format("%02d", s));
    }
}
