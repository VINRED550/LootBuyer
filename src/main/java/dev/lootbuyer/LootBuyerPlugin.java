package dev.lootbuyer;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LootBuyerPlugin extends JavaPlugin {
    private Settings settings;
    private Messages messages;
    private PriceManager prices;
    private SellService sells;
    private SalesHistory history;
    private Economy economy;
    private BukkitTask ticker;
    /** Open menus by player. Tracked here instead of asking InventoryView (see MenuListener). */
    private final Map<UUID, BuyerMenu> openMenus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String lang : new String[]{"lang/en.yml", "lang/ru.yml"}) {
            if (!new File(getDataFolder(), lang).exists()) saveResource(lang, false);
        }

        settings = new Settings(getConfig(), getLogger());
        registerMultiplierPermissions();
        messages = new Messages(this);
        sells = new SellService(this);
        // Started even when disabled in config, so /buyer reload can turn it on without a restart
        history = new SalesHistory(this);
        history.start();
        prices = new PriceManager(this);
        prices.load();

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        LootBuyerCommand command = new LootBuyerCommand(this);
        PluginCommand cmd = getCommand("lootbuyer");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LootBuyerExpansion(this).register();
            getLogger().info("PlaceholderAPI placeholders registered");
        }

        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("Loaded " + settings.items.size() + " items on Minecraft " + Compat.version());
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        closeAllMenus();
        if (prices != null) prices.save();
        if (history != null) history.shutdown();
    }

    public void reload() {
        // The menu size may change — close open menus instead of drawing into the old inventory
        closeAllMenus();
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        registerMultiplierPermissions();
        messages.reload();
        prices.reconcile();
        prices.save();
    }

    private void tick() {
        if (prices.rotationDue()) prices.rotate(settings.announceRotation);
        for (BuyerMenu menu : openMenus()) menu.renderInfo();
        prices.saveIfDirty();
        history.cleanupIfDue();
    }

    /**
     * Multiplier permissions are registered with default: false — otherwise Bukkit gives
     * unregistered permissions to operators, and admins would always get the top multiplier.
     */
    private void registerMultiplierPermissions() {
        PluginManager pm = getServer().getPluginManager();
        for (String node : settings.multipliers.keySet()) {
            if (pm.getPermission(node) == null) pm.addPermission(new Permission(node, PermissionDefault.FALSE));
        }
    }

    public double multiplier(Player player) {
        double best = 1.0;
        for (Map.Entry<String, Double> e : settings.multipliers.entrySet()) {
            if (player.hasPermission(e.getKey())) best = Math.max(best, e.getValue());
        }
        return best;
    }

    /** Looked up lazily: Essentials/CMI register their economy after plugins load. */
    public Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    void trackMenu(BuyerMenu menu) {
        openMenus.put(menu.viewer().getUniqueId(), menu);
    }

    void untrackMenu(BuyerMenu menu) {
        openMenus.remove(menu.viewer().getUniqueId(), menu);
    }

    void untrackMenu(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    /** Menus that are still open; drops entries whose inventory was closed in a way we didn't see. */
    private Iterable<BuyerMenu> openMenus() {
        ArrayList<BuyerMenu> list = new ArrayList<>();
        for (BuyerMenu menu : openMenus.values()) {
            if (menu.isOpen()) list.add(menu);
            else untrackMenu(menu);
        }
        return list;
    }

    public void refreshMenus() {
        for (BuyerMenu menu : openMenus()) menu.render();
    }

    private void closeAllMenus() {
        for (BuyerMenu menu : openMenus()) menu.viewer().closeInventory();
        openMenus.clear();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public PriceManager prices() {
        return prices;
    }

    public SellService sells() {
        return sells;
    }

    public SalesHistory history() {
        return history;
    }
}
