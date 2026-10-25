package dev.lootbuyer;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The buyer menu of one player. */
public final class BuyerMenu implements InventoryHolder {
    private final LootBuyerPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, String> itemsBySlot = new HashMap<>();

    private BuyerMenu(LootBuyerPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, plugin.settings().menuRows * 9, plugin.messages().legacy("menu.title"));
        render();
    }

    public static void open(LootBuyerPlugin plugin, Player player) {
        BuyerMenu menu = new BuyerMenu(plugin, player);
        player.openInventory(menu.getInventory());
        plugin.trackMenu(menu);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** Whether the player still has this menu open. */
    public boolean isOpen() {
        return viewer.isOnline() && inventory.getViewers().contains(viewer);
    }

    public void render() {
        Settings s = plugin.settings();
        inventory.clear();
        itemsBySlot.clear();

        if (s.filler != Material.AIR) {
            ItemStack filler = button(s.filler, "menu.filler-name", null);
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        }

        double multiplier = plugin.multiplier(viewer);
        List<BuyerItem> active = plugin.prices().activeItems();
        for (int i = 0; i < active.size() && i < s.itemSlots.size(); i++) {
            int slot = s.itemSlots.get(i);
            BuyerItem item = active.get(i);
            inventory.setItem(slot, icon(item, multiplier));
            itemsBySlot.put(slot, item.id());
        }

        if (s.sellAllSlot >= 0) inventory.setItem(s.sellAllSlot, button(s.sellAllMaterial, "menu.sell-all-name", "menu.sell-all-lore"));
        renderInfo();
    }

    /** Updates only the clock with the timer — called every second. */
    public void renderInfo() {
        Settings s = plugin.settings();
        if (s.infoSlot < 0) return;
        Messages m = plugin.messages();
        inventory.setItem(s.infoSlot, button(Material.CLOCK, "menu.info-name", "menu.info-lore",
                Placeholder.unparsed("time", m.time(plugin.prices().millisLeft())),
                Placeholder.unparsed("multiplier", m.multiplier(plugin.multiplier(viewer)))));
    }

    public void handleClick(Player player, int slot, ClickType click) {
        Settings s = plugin.settings();
        if (slot == s.sellAllSlot) {
            plugin.sells().sellAll(player);
            render();
            return;
        }
        String id = itemsBySlot.get(slot);
        BuyerItem item = id == null ? null : s.items.get(id);
        if (item == null) return;

        int amount;
        switch (click) {
            case LEFT:
                amount = 1;
                break;
            case RIGHT:
                amount = item.material().getMaxStackSize();
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                amount = Integer.MAX_VALUE;
                break;
            default:
                return;
        }
        plugin.sells().sell(player, item, amount);
        render();
    }

    private ItemStack icon(BuyerItem item, double multiplier) {
        PriceManager prices = plugin.prices();
        Messages m = plugin.messages();
        double price = prices.price(item.id()) * multiplier;
        int stack = item.material().getMaxStackSize();
        int left = prices.remaining(viewer, item);

        TagResolver tags = TagResolver.resolver(
                Placeholder.unparsed("price", m.money(price)),
                Placeholder.unparsed("price_stack", m.money(price * stack)),
                Placeholder.unparsed("stack", Integer.toString(stack)),
                Placeholder.parsed("trend", trend(item)),
                Placeholder.unparsed("left", left == Integer.MAX_VALUE ? "∞" : Integer.toString(left)),
                Placeholder.unparsed("limit", item.limit() <= 0 ? "∞" : Integer.toString(item.limit())),
                Placeholder.unparsed("have", Integer.toString(plugin.sells().count(viewer, item.material()))));

        ItemStack icon = new ItemStack(item.material());
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(m.lore("menu.item-lore", tags));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** MiniMessage text of the price change, e.g. "<green>▲ 12%". Empty before the first rotation. */
    private String trend(BuyerItem item) {
        Double prev = plugin.prices().previousPrice(item.id());
        double cur = plugin.prices().price(item.id());
        if (prev == null || prev <= 0) return "";
        double percent = (cur - prev) / prev * 100;
        String key = Math.abs(percent) < 0.5 ? "menu.trend-same" : percent > 0 ? "menu.trend-up" : "menu.trend-down";
        return plugin.messages().raw(key).replace("<percent>", Long.toString(Math.round(Math.abs(percent))));
    }

    private ItemStack button(Material material, String nameKey, String loreKey, TagResolver... tags) {
        Messages m = plugin.messages();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(m.legacy(nameKey, tags));
            if (loreKey != null) meta.setLore(m.lore(loreKey, tags));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
