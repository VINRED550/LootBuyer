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

/** Снимок config.yml. Пересоздаётся при /buyer reload. */
public final class Settings {
    public final long rotationMillis;
    public final boolean announceRotation;
    public final int itemsPerRotation;
    public final int priceDecimals;
    public final boolean onlyPlainItems;
    public final Map<String, Double> multipliers;
    public final String soundSell;
    public final String soundFail;

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
        priceDecimals = Math.clamp(c.getInt("price-decimals", 1), 0, 4);
        onlyPlainItems = c.getBoolean("only-plain-items", true);
        soundSell = c.getString("sounds.sell", "");
        soundFail = c.getString("sounds.fail", "");

        Map<String, Double> mult = new LinkedHashMap<>();
        ConfigurationSection ms = c.getConfigurationSection("multipliers");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                double value = ms.getDouble(key, 1.0);
                if (value > 0) mult.put("lootbuyer.multiplier." + key, value);
            }
        }
        multipliers = Collections.unmodifiableMap(mult);

        menuRows = Math.clamp(c.getInt("menu.rows", 6), 1, 6);
        int size = menuRows * 9;
        filler = material(c.getString("menu.filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE, log);
        infoSlot = slot(c.getInt("menu.info-slot", 4), size);
        sellAllSlot = slot(c.getInt("menu.sell-all-slot", 49), size);
        sellAllMaterial = material(c.getString("menu.sell-all-material", "HOPPER"), Material.HOPPER, log);

        List<Integer> slots = new ArrayList<>();
        List<Integer> configured = c.getIntegerList("menu.item-slots");
        if (configured.isEmpty()) {
            // Середина меню без рамки: ряды 1..rows-2, колонки 1..7
            for (int row = 1; row < menuRows - 1; row++) {
                for (int col = 1; col < 8; col++) slots.add(row * 9 + col);
            }
            if (slots.isEmpty()) for (int i = 0; i < size; i++) slots.add(i);
        } else {
            slots.addAll(configured);
        }
        slots.removeIf(s -> s < 0 || s >= size || s == infoSlot || s == sellAllSlot);
        itemSlots = List.copyOf(slots);

        Map<String, BuyerItem> list = new LinkedHashMap<>();
        ConfigurationSection is = c.getConfigurationSection("items");
        if (is != null) {
            for (String id : is.getKeys(false)) {
                ConfigurationSection sec = is.getConfigurationSection(id);
                if (sec == null) continue;
                Material mat = Material.matchMaterial(sec.getString("material", id));
                if (mat == null || !mat.isItem() || mat.isAir()) {
                    log.warning("items." + id + ": unknown material '" + sec.getString("material", id) + "', skipped");
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
                    double t = min; min = max; max = t;
                }
                if (min < 0) {
                    log.warning("items." + id + ": price is missing or negative, skipped");
                    continue;
                }
                list.put(id, new BuyerItem(id, mat, min, max, sec.getInt("limit", 0)));
            }
        }
        items = Collections.unmodifiableMap(list);

        if (items.size() > itemSlots.size()) {
            int shown = itemsPerRotation > 0 ? Math.min(itemsPerRotation, items.size()) : items.size();
            if (shown > itemSlots.size()) {
                log.warning("There are more items (" + shown + ") than menu slots (" + itemSlots.size()
                        + "). Extra items won't be shown: lower items-per-rotation or raise menu.rows.");
            }
        }
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
