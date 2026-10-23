package dev.lootbuyer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Продажа предметов: проверка лимитов, списание из инвентаря и зачисление денег через Vault. */
public final class SellService {
    public enum Status { OK, NOT_BUYING, NO_ECONOMY, NOTHING, LIMIT, ECONOMY_ERROR }

    public record Result(Status status, int amount, double money) {
        static Result of(Status status) {
            return new Result(status, 0, 0);
        }
    }

    private final LootBuyerPlugin plugin;

    public SellService(LootBuyerPlugin plugin) {
        this.plugin = plugin;
    }

    /** Продать до {@code wanted} штук предмета и сообщить игроку результат. */
    public void sell(Player player, BuyerItem item, int wanted) {
        Result r = trySell(player, item, wanted);
        Messages m = plugin.messages();
        Component name = itemName(item.material());
        switch (r.status()) {
            case OK -> m.send(player, "sold",
                    Placeholder.unparsed("amount", Integer.toString(r.amount())),
                    Placeholder.component("item", name),
                    Placeholder.unparsed("money", m.money(r.money())));
            case NOT_BUYING -> m.send(player, "not-buying");
            case NO_ECONOMY -> m.send(player, "no-economy");
            case NOTHING -> m.send(player, "nothing-to-sell", Placeholder.component("item", name));
            case LIMIT -> m.send(player, "limit-reached",
                    Placeholder.component("item", name),
                    Placeholder.unparsed("time", m.time(plugin.prices().millisLeft())));
            case ECONOMY_ERROR -> m.send(player, "economy-error");
        }
        playSound(player, r.status() == Status.OK);
    }

    /** Продать всё, что скупщик сейчас берёт. */
    public void sellAll(Player player) {
        Messages m = plugin.messages();
        int amount = 0, kinds = 0;
        double money = 0;
        for (BuyerItem item : plugin.prices().activeItems()) {
            Result r = trySell(player, item, Integer.MAX_VALUE);
            if (r.status() == Status.NO_ECONOMY) {
                m.send(player, "no-economy");
                playSound(player, false);
                return;
            }
            if (r.status() == Status.OK) {
                amount += r.amount();
                money += r.money();
                kinds++;
            }
        }
        if (amount == 0) {
            m.send(player, "sell-all-nothing");
            playSound(player, false);
            return;
        }
        m.send(player, "sell-all",
                Placeholder.unparsed("amount", Integer.toString(amount)),
                Placeholder.unparsed("kinds", Integer.toString(kinds)),
                Placeholder.unparsed("money", m.money(money)));
        playSound(player, true);
    }

    private Result trySell(Player player, BuyerItem item, int wanted) {
        PriceManager prices = plugin.prices();
        if (!prices.isActive(item.id())) return Result.of(Status.NOT_BUYING);
        Economy economy = plugin.economy();
        if (economy == null) return Result.of(Status.NO_ECONOMY);

        int have = count(player, item.material());
        if (have == 0) return Result.of(Status.NOTHING);
        int left = prices.remaining(player, item);
        if (left == 0) return Result.of(Status.LIMIT);

        int amount = Math.min(have, Math.min(wanted, left));
        double money = Math.round(amount * prices.price(item.id()) * plugin.multiplier(player) * 100) / 100.0;

        remove(player, item.material(), amount);
        EconomyResponse response = economy.depositPlayer(player, money);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning("Vault rejected a deposit of " + money + " to " + player.getName() + ": " + response.errorMessage);
            giveBack(player, item.material(), amount);
            return Result.of(Status.ECONOMY_ERROR);
        }
        prices.addSold(player.getUniqueId(), item.id(), amount);
        return new Result(Status.OK, amount, money);
    }

    /** Сколько подходящих предметов в основном инвентаре (броня и вторая рука не считаются). */
    public int count(Player player, Material material) {
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (matches(it, material)) n += it.getAmount();
        }
        return n;
    }

    private boolean matches(ItemStack it, Material material) {
        if (it == null || it.getType() != material) return false;
        return !plugin.settings().onlyPlainItems || it.isSimilar(new ItemStack(material));
    }

    private void remove(Player player, Material material, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (!matches(it, material)) continue;
            int take = Math.min(amount, it.getAmount());
            amount -= take;
            if (take == it.getAmount()) contents[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        inv.setStorageContents(contents);
    }

    private void giveBack(Player player, Material material, int amount) {
        player.getInventory().addItem(new ItemStack(material, amount)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void playSound(Player player, boolean success) {
        String sound = success ? plugin.settings().soundSell : plugin.settings().soundFail;
        if (sound != null && !sound.isEmpty()) player.playSound(player.getLocation(), sound, 0.7f, 1.0f);
    }

    /** Название предмета, которое клиент покажет на своём языке. */
    @SuppressWarnings("deprecation")
    public static Component itemName(Material material) {
        return Component.translatable(material.translationKey());
    }
}
