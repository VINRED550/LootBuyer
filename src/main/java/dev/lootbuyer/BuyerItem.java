package dev.lootbuyer;

import org.bukkit.Material;

/** Предмет из списка скупщика. limit <= 0 — без лимита. */
public record BuyerItem(String id, Material material, double minPrice, double maxPrice, int limit) {
}
