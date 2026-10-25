package dev.lootbuyer;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Snapshot of config.yml. Recreated on /buyer reload. */
public final class Settings {
    public final long rotationMillis;
    public final boolean announceRotation;
    public final int itemsPerRotation;
    public final int priceDecimals;
    public final boolean onlyPlainItems;
    public final Map<String, Double> multipliers;
    public final String soundSell;
    public final String soundFail;

    public final boolean historyEnabled;
    public final int historyKeepDays;
    public final int historyPageSize;
    public final String historyDateFormat;

    public final int menuRows;
    public final Material filler;
    public final int infoSlot;
    public final int sellAllSlot;
    public final Material sellAllMaterial;
    public final List<Integer> itemSlots;

    public final Map<String, BuyerItem> items;

    public Settings(FileConfiguration c, Logger log) {
        rotationMillis = Math.max(1, c.getLong("rotation-minutes", 60)) * 60_000L;
        announceRotation = c.getBoolean("announce-rotation", true);
        itemsPerRotation = Math.max(0, c.getInt("items-per-rotation", 0));
        priceDecimals = clamp(c.getInt("price-decimals", 1), 0, 4);
        onlyPlainItems = c.getBoolean("only-plain-items", true);
        soundSell = c.getString("sounds.sell", "");
        soundFail = c.getString("sounds.fail", "");

        historyEnabled = c.getBoolean("history.enabled", true);
        historyKeepDays = Math.max(1, c.getInt("history.keep-days", 7));
        historyPageSize = clamp(c.getInt("history.page-size", 10), 1, 50);
        historyDateFormat = c.getString("history.date-format", "dd.MM HH:mm");

        Map<String, Double> mult = new LinkedHashMap<>();
        ConfigurationSection ms = c.getConfigurationSection("multipliers");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                double value = ms.getDouble(key, 1.0);
                if (value > 0) mult.put("lootbuyer.multiplier." + key, value);
            }
        }
        multipliers = Collections.unmodifiableMap(mult);

        menuRows = clamp(c.getInt("menu.rows", 6), 1, 6);
        int size = menuRows * 9;
        filler = material(c.getString("menu.filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE, log);
        infoSlot = slot(c.getInt("menu.info-slot", 4), size);
        sellAllSlot = slot(c.getInt("menu.sell-all-slot", 49), size);
        sellAllMaterial = material(c.getString("menu.sell-all-material", "HOPPER"), Material.HOPPER, log);

        List<Integer> slots = new ArrayList<>();
        List<Integer> configured = c.getIntegerList("menu.item-slots");
        if (configured.isEmpty()) {
            // Inner area without the border: rows 1..rows-2, columns 1..7
            for (int row = 1; row < menuRows - 1; row++) {
                for (int col = 1; col < 8; col++) slots.add(row * 9 + col);
            }
            if (slots.isEmpty()) for (int i = 0; i < size; i++) slots.add(i);
        } else {
            slots.addAll(configured);
        }
        List<Integer> valid = new ArrayList<>();
        for (Integer s : slots) {
            if (s != null && s >= 0 && s < size && s != infoSlot && s != sellAllSlot) valid.add(s);
        }
        itemSlots = Collections.unmodifiableList(valid);

        Map<String, BuyerItem> list = new LinkedHashMap<>();
        List<String> unavailable = new ArrayList<>();
        ConfigurationSection is = c.getConfigurationSection("items");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ConfigurationSection sec = is.getConfigurationSection(id);
                if (sec == null) continue;
                Material mat = Material.matchMaterial(sec.getString("material", id));
                if (!Compat.isSellable(mat)) {
                    // e.g. COPPER_INGOT on 1.16 — the item simply doesn't exist in that version
                    unavailable.add(id);
                    continue;
                }
                double min, max;
                if (sec.isList("price")) {
                    List<Double> p = sec.getDoubleList("price");
                    if (p.isEmpty()) {
                        log.warning("items." + id + ": empty price list, skipped");
                        continue;
                    }
                    min = p.get(0);
                    max = p.size() > 1 ? p.get(1) : p.get(0);
                } else {
                    min = max = sec.getDouble("price", -1);
                }
                if (min > max) {
                    double t = min;
                    min = max;
                    max = t;
                }
                if (min < 0) {
                    log.warning("items." + id + ": price is missing or negative, skipped");
                    continue;
                }
                list.put(id, new BuyerItem(id, mat, min, max, sec.getInt("limit", 0)));
            }
        }
        items = Collections.unmodifiableMap(list);

        if (!unavailable.isEmpty()) {
            log.info("Not available on Minecraft " + Compat.version() + ", skipped: " + String.join(", ", unavailable));
        }
        int shown = itemsPerRotation > 0 ? Math.min(itemsPerRotation, items.size()) : items.size();
        if (shown > itemSlots.size()) {
            log.warning("There are more items (" + shown + ") than menu slots (" + itemSlots.size()
                    + "). Extra items won't be shown: lower items-per-rotation or raise menu.rows.");
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int slot(int slot, int size) {
        return slot >= 0 && slot < size ? slot : -1;
    }

    private static Material material(String name, Material fallback, Logger log) {
        Material m = name == null ? null : Material.matchMaterial(name);
        if (m == null) {
            log.warning("Unknown material '" + name + "', using " + fallback);
            return fallback;
        }
        return m;
    }
}
