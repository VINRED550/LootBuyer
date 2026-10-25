package dev.lootbuyer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** /buyer [sellall | reload | rotate | open <player> | history <player> [page]] */
public final class LootBuyerCommand implements TabExecutor {
    private final LootBuyerPlugin plugin;

    public LootBuyerCommand(LootBuyerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                m.send(sender, "usage-admin");
                return true;
            }
            if (!sender.hasPermission("lootbuyer.use")) {
                m.send(sender, "no-permission");
                return true;
            }
            BuyerMenu.open(plugin, (Player) sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sellall":
                if (!(sender instanceof Player)) {
                    m.send(sender, "only-players");
                } else if (!sender.hasPermission("lootbuyer.sellall")) {
                    m.send(sender, "no-permission");
                } else {
                    plugin.sells().sellAll((Player) sender);
                }
                break;
            case "reload":
                if (admin(sender)) {
                    plugin.reload();
                    m.send(sender, "reloaded");
                }
                break;
            case "rotate":
                if (admin(sender)) {
                    plugin.prices().rotate(true);
                    m.send(sender, "rotated");
                }
                break;
            case "open":
                // For NPCs and menus: /buyer open %player% from the console
                if (!admin(sender)) break;
                if (args.length < 2) {
                    m.send(sender, "usage-admin");
                    break;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) m.send(sender, "player-not-found");
                else BuyerMenu.open(plugin, target);
                break;
            case "history":
                history(sender, args);
                break;
            default:
                m.send(sender, "usage");
                if (sender.hasPermission("lootbuyer.admin")) m.send(sender, "usage-admin");
        }
        return true;
    }

    /** /buyer history <player> [page] */
    private void history(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        Settings s = plugin.settings();
        if (!sender.hasPermission("lootbuyer.history")) {
            m.send(sender, "no-permission");
            return;
        }
        if (!s.historyEnabled) {
            m.send(sender, "history.disabled");
            return;
        }
        if (!plugin.history().available()) {
            m.send(sender, "history.unavailable");
            return;
        }
        if (args.length < 2) {
            m.send(sender, "history.usage");
            return;
        }
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                m.send(sender, "history.usage");
                return;
            }
        }
        String days = Integer.toString(s.historyKeepDays);
        m.send(sender, "history.loading");
        plugin.history().query(args[1], page, result -> {
            if (result == null) {
                m.send(sender, "history.unavailable");
                return;
            }
            if (result.entries.isEmpty()) {
                m.send(sender, "history.empty", Placeholder.unparsed("player", result.player), Placeholder.unparsed("days", days));
                return;
            }
            m.send(sender, "history.header",
                    Placeholder.unparsed("player", result.player),
                    Placeholder.unparsed("days", days),
                    Placeholder.unparsed("amount", Long.toString(result.totalAmount)),
                    Placeholder.unparsed("money", m.money(result.totalMoney)));
            SimpleDateFormat format = dateFormat(s.historyDateFormat);
            for (SalesHistory.Entry e : result.entries) {
                Material material = Material.matchMaterial(e.item);
                Component name = material != null ? SellService.itemName(material) : Component.text(e.item);
                m.send(sender, "history.line",
                        Placeholder.unparsed("date", format.format(new Date(e.time))),
                        Placeholder.unparsed("amount", Integer.toString(e.amount)),
                        Placeholder.component("item", name),
                        Placeholder.unparsed("money", m.money(e.money)));
            }
            if (result.page < result.pages) {
                m.send(sender, "history.footer",
                        Placeholder.unparsed("page", Integer.toString(result.page)),
                        Placeholder.unparsed("pages", Integer.toString(result.pages)),
                        Placeholder.unparsed("player", result.player),
                        Placeholder.unparsed("next", Integer.toString(result.page + 1)));
            } else if (result.pages > 1) {
                m.send(sender, "history.last-page",
                        Placeholder.unparsed("page", Integer.toString(result.page)),
                        Placeholder.unparsed("pages", Integer.toString(result.pages)));
            }
        });
    }

    private SimpleDateFormat dateFormat(String pattern) {
        try {
            return new SimpleDateFormat(pattern);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid history.date-format '" + pattern + "', using dd.MM HH:mm");
            return new SimpleDateFormat("dd.MM HH:mm");
        }
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("lootbuyer.admin")) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("lootbuyer.sellall")) options.add("sellall");
            if (sender.hasPermission("lootbuyer.admin")) options.addAll(Arrays.asList("reload", "rotate", "open"));
            if (sender.hasPermission("lootbuyer.history") && plugin.settings().historyEnabled) options.add("history");
        } else if (args.length == 2
                && ((args[0].equalsIgnoreCase("open") && sender.hasPermission("lootbuyer.admin"))
                || (args[0].equalsIgnoreCase("history") && sender.hasPermission("lootbuyer.history")))) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (Iterator<String> it = options.iterator(); it.hasNext(); ) {
            if (!it.next().toLowerCase(Locale.ROOT).startsWith(prefix)) it.remove();
        }
        return options;
    }
}
