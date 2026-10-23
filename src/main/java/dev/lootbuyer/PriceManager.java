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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Текущие и прошлые цены, набор скупаемых предметов и сколько продал каждый игрок.
 * Всё хранится в data.yml, поэтому цены и лимиты переживают перезапуск сервера.
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
                        // повреждённая запись, пропускаем
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

    /** Приводит сохранённые данные в соответствие с config.yml после загрузки или /buyer reload. */
    public void reconcile() {
        Map<String, BuyerItem> items = plugin.settings().items;
        current.keySet().retainAll(items.keySet());
        previous.keySet().retainAll(items.keySet());
        for (BuyerItem item : items.values()) {
            Double p = current.get(item.id());
            // Новый предмет или админ поменял диапазон — выбираем цену заново
            if (p == null || p < item.minPrice() - 1e-6 || p > item.maxPrice() + 1e-6) {
                current.put(item.id(), randomPrice(item));
            }
        }
        active.retainAll(items.keySet());
        int want = plugin.settings().itemsPerRotation <= 0 ? items.size() : Math.min(plugin.settings().itemsPerRotation, items.size());
        if (active.size() != want || plugin.settings().itemsPerRotation <= 0) pickActive();
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
        // Порядок как в конфиге, чтобы меню не перемешивалось хаотично
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
        return current.getOrDefault(id, 0.0);
    }

    public Double previousPrice(String id) {
        return previous.get(id);
    }

    public boolean isActive(String id) {
        return active.contains(id);
    }

    public List<BuyerItem> activeItems() {
        Map<String, BuyerItem> items = plugin.settings().items;
        return active.stream().map(items::get).filter(Objects::nonNull).toList();
    }

    /** Сколько ещё может продать игрок до смены цен. Integer.MAX_VALUE — без ограничений. */
    public int remaining(Player player, BuyerItem item) {
        if (player.hasPermission("lootbuyer.bypasslimit")) return Integer.MAX_VALUE;
        return remaining(player.getUniqueId(), item);
    }

    public int remaining(UUID player, BuyerItem item) {
        if (item.limit() <= 0) return Integer.MAX_VALUE;
        int done = sold.getOrDefault(player, Map.of()).getOrDefault(item.id(), 0);
        return Math.max(0, item.limit() - done);
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
        y.options().setHeader(List.of("LootBuyer data: current prices and player limits. Do not edit while the server is running."));
        y.set("next-rotation", nextRotation);
        current.forEach((id, p) -> y.set("prices." + id, p));
        previous.forEach((id, p) -> y.set("previous." + id, p));
        y.set("active", active);
        sold.forEach((uuid, map) -> map.forEach((id, n) -> y.set("sold." + uuid + "." + id, n)));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
        dirty = false;
        lastSave = System.currentTimeMillis();
    }
}
