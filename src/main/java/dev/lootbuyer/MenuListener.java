package dev.lootbuyer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Blocks every item move while the buyer menu is open, including shift-clicks and number keys
 * from the player's own inventory.
 * InventoryView is never touched: it changed from a class to an interface in 1.21,
 * so calling it would break either old or new servers.
 */
public final class MenuListener implements Listener {
    private final LootBuyerPlugin plugin;

    public MenuListener(LootBuyerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof BuyerMenu)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        if (event.getWhoClicked() instanceof Player) {
            ((BuyerMenu) top.getHolder()).handleClick((Player) event.getWhoClicked(), slot, event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BuyerMenu) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BuyerMenu) {
            plugin.untrackMenu((BuyerMenu) event.getInventory().getHolder());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.untrackMenu(event.getPlayer());
    }
}
