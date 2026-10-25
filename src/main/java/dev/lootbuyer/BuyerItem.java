package dev.lootbuyer;

import org.bukkit.Material;

/** An item from the buyer list. limit <= 0 means no limit. */
public final class BuyerItem {
    private final String id;
    private final Material material;
    private final double minPrice;
    private final double maxPrice;
    private final int limit;

    public BuyerItem(String id, Material material, double minPrice, double maxPrice, int limit) {
        this.id = id;
        this.material = material;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.limit = limit;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public double minPrice() {
        return minPrice;
    }

    public double maxPrice() {
        return maxPrice;
    }

    public int limit() {
        return limit;
    }
}
