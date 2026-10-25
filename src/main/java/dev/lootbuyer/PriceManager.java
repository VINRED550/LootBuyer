package dev.lootbuyer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Current and previous prices, the set of items being bought and how much each player has sold.
 * Everything is kept in data.yml, so prices and limits survive restarts.
 */
public final class PriceManager {
    private static final long SAVE_INTERVAL = 60_000L;

    private final LootBuyerPlugin plugin;
    private final File file;
    private final Map<String, Double> current = new HashMap<>();
    private final Map<String, Double> previous = new HashMap<>();
    private final List<String> active = new ArrayList<>();
    private final Map<UUID, Map<String, Integer>> sold = new HashMap<>();
    private long nextRotation;
    private boolean dirty;
    private long lastSave;

    public PriceManager(LootBuyerPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            nextRotation = y.getLong("next-rotation", 0);
            readPrices(y.getConfigurationSection("prices"), current);
            readPrices(y.getConfigurationSection("previous"), previous);
            active.addAll(y.getStringList("active"));
            ConfigurationSection soldSec = y.getConfigurationSection("sold");
            if (soldSec != null) {
                for (String uuid : soldSec.getKeys(false)) {
                    ConfigurationSection ps = soldSec.getConfigurationSection(uuid);
                    if (ps == null) continue;
                    try {
                        Map<String, Integer> map = new HashMap<>();
                        for (String id : ps.getKeys(false)) map.put(id, ps.getInt(id));
                        sold.put(UUID.fromString(uuid), map);
                    } catch (IllegalArgumentException ignored) {
                        // broken entry, skip it
                    }
                }
            }
        }
        if (nextRotation <= 0) {
            rotate(false);
        } else {
            reconcile();
            save();
        }
    }

    private static void readPrices(ConfigurationSection sec, Map<String, Double> into) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) into.put(id, sec.getDouble(id));
    }

    /** Brings saved data in line with config.yml after loading or /buyer reload. */
    public void reconcile() {
        Map<String, BuyerItem> items = plugin.settings().items;
        current.keySet().retainAll(items.keySet());
        previous.keySet().retainAll(items.keySet());
        for (BuyerItem item : items.values()) {
            Double p = current.get(item.id());
            // New item, or the admin changed its range — pick a new price
            if (p == null || p < item.minPrice() - 1e-6 || p > item.maxPrice() + 1e-6) {
                current.put(item.id(), randomPrice(item));
            }
        }
        active.retainAll(items.keySet());
        int perRotation = plugin.settings().itemsPerRotation;
        int want = perRotation <= 0 ? items.size() : Math.min(perRotation, items.size());
        if (active.size() != want || perRotation <= 0) pickActive();
        dirty = true;
    }

    public void rotate(boolean announce) {
        Settings s = plugin.settings();
        previous.clear();
        previous.putAll(current);
        for (BuyerItem item : s.items.values()) current.put(item.id(), randomPrice(item));
        pickActive();
        sold.clear();
        nextRotation = System.currentTimeMillis() + s.rotationMillis;
        save();
        if (announce) plugin.messages().broadcast("rotation-announce");
        plugin.refreshMenus();
    }

    private void pickActive() {
        List<String> ids = new ArrayList<>(plugin.settings().items.keySet());
        int n = plugin.settings().itemsPerRotation;
        active.clear();
        if (n <= 0 || n >= ids.size()) {
            active.addAll(ids);
            return;
        }
        List<String> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled);
        Set<String> chosen = new HashSet<>(shuffled.subList(0, n));
        // Keep config order so the menu doesn't jump around
        for (String id : ids) if (chosen.contains(id)) active.add(id);
    }

    private double randomPrice(BuyerItem item) {
        double v = item.minPrice() >= item.maxPrice()
                ? item.minPrice()
                : ThreadLocalRandom.current().nextDouble(item.minPrice(), item.maxPrice());
        double f = Math.pow(10, plugin.settings().priceDecimals);
        return Math.round(v * f) / f;
    }

    public boolean rotationDue() {
        return System.currentTimeMillis() >= nextRotation;
    }

    public long millisLeft() {
        return Math.max(0, nextRotation - System.currentTimeMillis());
    }

    public double price(String id) {
        Double p = current.get(id);
        return p == null ? 0.0 : p;
    }

    public Double previousPrice(String id) {
        return previous.get(id);
    }

    public boolean isActive(String id) {
        return active.contains(id);
    }

    public List<BuyerItem> activeItems() {
        Map<String, BuyerItem> items = plugin.settings().items;
        List<BuyerItem> out = new ArrayList<>();
        for (String id : active) {
            BuyerItem item = items.get(id);
            if (item != null) out.add(item);
        }
        return out;
    }

    /** How many more the player may sell before the next rotation. Integer.MAX_VALUE means unlimited. */
    public int remaining(Player player, BuyerItem item) {
        if (player.hasPermission("lootbuyer.bypasslimit")) return Integer.MAX_VALUE;
        return remaining(player.getUniqueId(), item);
    }

    public int remaining(UUID player, BuyerItem item) {
        if (item.limit() <= 0) return Integer.MAX_VALUE;
        Map<String, Integer> map = sold.get(player);
        Integer done = map == null ? null : map.get(item.id());
        return Math.max(0, item.limit() - (done == null ? 0 : done));
    }

    public void addSold(UUID player, String id, int amount) {
        sold.computeIfAbsent(player, k -> new HashMap<>()).merge(id, amount, Integer::sum);
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty && System.currentTimeMillis() - lastSave >= SAVE_INTERVAL) save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-rotation", nextRotation);
        for (Map.Entry<String, Double> e : current.entrySet()) y.set("prices." + e.getKey(), e.getValue());
        for (Map.Entry<String, Double> e : previous.entrySet()) y.set("previous." + e.getKey(), e.getValue());
        y.set("active", active);
        for (Map.Entry<UUID, Map<String, Integer>> e : sold.entrySet()) {
            for (Map.Entry<String, Integer> s : e.getValue().entrySet()) {
                y.set("sold." + e.getKey() + "." + s.getKey(), s.getValue());
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
        dirty = false;
        lastSave = System.currentTimeMillis();
    }
}
