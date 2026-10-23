package dev.lootbuyer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Блокирует любые перемещения предметов, пока открыто меню скупщика,
 * включая shift-клики и цифровые клавиши из инвентаря игрока.
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder(false) instanceof BuyerMenu menu)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        if (event.getWhoClicked() instanceof Player player) menu.handleClick(player, slot, event.getClick());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof BuyerMenu) event.setCancelled(true);
    }
}
