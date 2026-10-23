package dev.lootbuyer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /buyer [sellall | reload | rotate | open <игрок>] */
public final class LootBuyerCommand implements TabExecutor {
    private final LootBuyerPlugin plugin;

    public LootBuyerCommand(LootBuyerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        Messages m = plugin.messages();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                m.send(sender, "usage-admin");
                return true;
            }
            if (!player.hasPermission("lootbuyer.use")) {
                m.send(sender, "no-permission");
                return true;
            }
            BuyerMenu.open(plugin, player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sellall" -> {
                if (!(sender instanceof Player player)) {
                    m.send(sender, "only-players");
                } else if (!player.hasPermission("lootbuyer.sellall")) {
                    m.send(sender, "no-permission");
                } else {
                    plugin.sells().sellAll(player);
                }
            }
            case "reload" -> {
                if (admin(sender)) {
                    plugin.reload();
                    m.send(sender, "reloaded");
                }
            }
            case "rotate" -> {
                if (admin(sender)) {
                    plugin.prices().rotate(true);
                    m.send(sender, "rotated");
                }
            }
            case "open" -> {
                // Для NPC и меню: /buyer open %player% от имени консоли
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    m.send(sender, "usage-admin");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) m.send(sender, "player-not-found");
                else BuyerMenu.open(plugin, target);
            }
            default -> {
                m.send(sender, "usage");
                if (sender.hasPermission("lootbuyer.admin")) m.send(sender, "usage-admin");
            }
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("lootbuyer.admin")) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("lootbuyer.sellall")) options.add("sellall");
            if (sender.hasPermission("lootbuyer.admin")) options.addAll(List.of("reload", "rotate", "open"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("open") && sender.hasPermission("lootbuyer.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
