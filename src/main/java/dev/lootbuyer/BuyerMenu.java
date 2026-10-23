package dev.lootbuyer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Меню скупщика для одного игрока. */
public final class BuyerMenu implements InventoryHolder {
    private final LootBuyerPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, String> itemsBySlot = new HashMap<>();

    private BuyerMenu(LootBuyerPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, plugin.settings().menuRows * 9, plugin.messages().one("menu.title"));
        render();
    }

    public static void open(LootBuyerPlugin plugin, Player player) {
        player.openInventory(new BuyerMenu(plugin, player).getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    public void render() {
        Settings s = plugin.settings();
        inventory.clear();
        itemsBySlot.clear();

        if (!s.filler.isAir()) {
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

    /** Обновляет только часы с таймером — вызывается каждую секунду. */
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

        int amount = switch (click) {
            case LEFT -> 1;
            case RIGHT -> item.material().getMaxStackSize();
            case SHIFT_LEFT, SHIFT_RIGHT -> Integer.MAX_VALUE;
            default -> 0;
        };
        if (amount == 0) return;
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
                Placeholder.component("trend", trend(item)),
                Placeholder.unparsed("left", left == Integer.MAX_VALUE ? "∞" : Integer.toString(left)),
                Placeholder.unparsed("limit", item.limit() <= 0 ? "∞" : Integer.toString(item.limit())),
                Placeholder.unparsed("have", Integer.toString(plugin.sells().count(viewer, item.material()))));

        ItemStack icon = new ItemStack(item.material());
        icon.editMeta(meta -> meta.lore(m.lore("menu.item-lore", tags)));
        return icon;
    }

    private Component trend(BuyerItem item) {
        Double prev = plugin.prices().previousPrice(item.id());
        double cur = plugin.prices().price(item.id());
        if (prev == null || prev <= 0) return Component.empty();
        double percent = (cur - prev) / prev * 100;
        String key = Math.abs(percent) < 0.5 ? "menu.trend-same" : percent > 0 ? "menu.trend-up" : "menu.trend-down";
        return plugin.messages().one(key, Placeholder.unparsed("percent", Long.toString(Math.round(Math.abs(percent)))));
    }

    private ItemStack button(Material material, String nameKey, String loreKey, TagResolver... tags) {
        Messages m = plugin.messages();
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Messages.noItalic(m.one(nameKey, tags)));
            if (loreKey != null) meta.lore(m.lore(loreKey, tags));
        });
        return stack;
    }
}
