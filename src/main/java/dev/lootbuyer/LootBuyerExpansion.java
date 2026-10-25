package dev.lootbuyer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * %lootbuyer_next%            — time until prices rotate
 * %lootbuyer_next_seconds%    — same, in seconds
 * %lootbuyer_price_<id>%      — current price per item
 * %lootbuyer_active_<id>%     — whether the item is bought right now (true/false)
 * %lootbuyer_left_<id>%       — how many more the player may sell
 */
public final class LootBuyerExpansion extends PlaceholderExpansion {
    private final LootBuyerPlugin plugin;

    public LootBuyerExpansion(LootBuyerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lootbuyer";
    }

    @Override
    public String getAuthor() {
        return "VINREDS";
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        PriceManager prices = plugin.prices();
        Messages m = plugin.messages();

        if (params.equals("next")) return m.time(prices.millisLeft());
        if (params.equals("next_seconds")) return Long.toString(prices.millisLeft() / 1000);

        if (params.startsWith("price_")) {
            BuyerItem item = plugin.settings().items.get(params.substring(6));
            return item == null ? "" : m.money(prices.price(item.id()));
        }
        if (params.startsWith("active_")) {
            return Boolean.toString(prices.isActive(params.substring(7)));
        }
        if (params.startsWith("left_")) {
            BuyerItem item = plugin.settings().items.get(params.substring(5));
            if (item == null || player == null) return "";
            Player online = player.getPlayer();
            int left = online != null ? prices.remaining(online, item) : prices.remaining(player.getUniqueId(), item);
            return left == Integer.MAX_VALUE ? "∞" : Integer.toString(left);
        }
        return null;
    }
}
