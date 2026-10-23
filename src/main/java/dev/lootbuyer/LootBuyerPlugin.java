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
import java.util.Map;
import java.util.function.Consumer;

public final class LootBuyerPlugin extends JavaPlugin {
    private Settings settings;
    private Messages messages;
    private PriceManager prices;
    private SellService sells;
    private Economy economy;
    private BukkitTask ticker;

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
        prices = new PriceManager(this);
        prices.load();

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
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
        getLogger().info("Loaded " + settings.items.size() + " items");
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        forEachOpenMenu(menu -> menu.viewer().closeInventory());
        if (prices != null) prices.save();
    }

    public void reload() {
        // Размер меню мог измениться — закрываем открытые, чтобы не рисовать в старый инвентарь
        forEachOpenMenu(menu -> menu.viewer().closeInventory());
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        registerMultiplierPermissions();
        messages.reload();
        prices.reconcile();
        prices.save();
    }

    private void tick() {
        if (prices.rotationDue()) prices.rotate(settings.announceRotation);
        forEachOpenMenu(BuyerMenu::renderInfo);
        prices.saveIfDirty();
    }

    /**
     * Права множителей регистрируются с default: false — иначе Bukkit по умолчанию
     * выдаёт незарегистрированные права операторам, и у админов всегда максимальный множитель.
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

    /** Экономика ищется лениво: Essentials/CMI регистрируют её уже после загрузки плагинов. */
    public Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    public void refreshMenus() {
        forEachOpenMenu(BuyerMenu::render);
    }

    private void forEachOpenMenu(Consumer<BuyerMenu> action) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof BuyerMenu menu) action.accept(menu);
        }
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
}
