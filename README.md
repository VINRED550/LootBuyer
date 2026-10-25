# LootBuyer — resource buyer GUI for Paper/Purpur

[Русская версия](README.ru.md)

![Buyer menu](images/1-menu.png) ![Price tooltip](images/2-tooltip.png)

**LootBuyer** is a resource buyer GUI for Paper and Purpur. Players open `/buyer` and sell their loot to the server. Prices rotate on a timer, every item has its own per-player limit, ranks can earn more, and admins can see who sold what.

## Features

- **Clean menu.** Every item shows its price, the change since the last rotation (▲/▼ in %), how many you can still sell and how many you have.
- **Fast selling.** Left click sells 1, right click sells a stack, shift-click sells everything. "Sell all" button and `/buyer sellall` command.
- **Rotating prices.** A random price within the range you set, rotated on a timer with a chat announcement.
- **Anarchy mode.** Optionally buy only a random set of items each rotation (`items-per-rotation`).
- **Per-player limits** that reset with every rotation, so farms can't flood your economy.
- **Rank multipliers.** Give `lootbuyer.multiplier.vip` and that player earns ×1.1 (configurable).
- **Sales history for admins.** `/buyer history <player>` shows every sale with date, item, amount and money, plus totals. Choose how many days to keep it, or turn it off.
- **Safe by default.** Only plain items are bought: renamed, enchanted or custom items from other plugins are left alone. Armor and off-hand are never sold, and items can't be taken out of the menu.
- **Persistent.** Prices and limits survive restarts.
- **Works across versions.** Paper and Purpur 1.16.5 – 26.x. Items that don't exist in your version are skipped automatically.
- **Localized.** English and Russian included, plus your own languages. Item names show in each player's client language where the server supports it. Every line is configurable with MiniMessage (gradients, hex colors).
- **PlaceholderAPI** support and works with **Citizens** NPCs.

## Requirements

- Paper or Purpur **1.16.5 – 26.x** (forks of Paper work too)
- Java 8 or newer, whatever your server version requires
- [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin (EssentialsX, CMI, …)
  - On 1.16.5 use EssentialsX **2.20.1**: newer EssentialsX doesn't support 1.16.5
- PlaceholderAPI — optional

Not supported: plain Spigot, Folia.

## Commands and permissions

| Command | Permission | Default | Description |
|---|---|---|---|
| `/buyer` (`/lootbuyer`, `/skupka`, `/skup`) | `lootbuyer.use` | everyone | Open the menu |
| `/buyer sellall` | `lootbuyer.sellall` | everyone | Sell everything the buyer takes |
| `/buyer history <player> [page]` | `lootbuyer.history` | op | A player's sales history |
| `/buyer reload` | `lootbuyer.admin` | op | Reload config and messages |
| `/buyer rotate` | `lootbuyer.admin` | op | Rotate prices now |
| `/buyer open <player>` | `lootbuyer.admin` | op | Open the menu for a player (NPCs, menus) |
| — | `lootbuyer.bypasslimit` | nobody | Sell without limits |
| — | `lootbuyer.multiplier.<name>` | nobody | Price multiplier from `config.yml` |

## Sales history

```
Buyer » Sales of Steve in the last 7 days: 328 items for $10,593.6
 26.10 00:41 64 × Diamond for $7,193.6
 26.10 00:41 128 × Iron Ingot for $1,292.8
 ...
Page 1/3. Next: /buyer history Steve 2
```

```yaml
history:
  enabled: true        # false = off
  keep-days: 7         # older records are deleted automatically
  page-size: 10
  date-format: "dd.MM HH:mm"
```

## Placeholders

| Placeholder | Value |
|---|---|
| `%lootbuyer_next%` | Time until the next rotation, e.g. `12m 05s` |
| `%lootbuyer_next_seconds%` | Same, in seconds |
| `%lootbuyer_price_<id>%` | Current price per item, e.g. `%lootbuyer_price_diamond%` |
| `%lootbuyer_active_<id>%` | `true` if the item is being bought right now |
| `%lootbuyer_left_<id>%` | How many the player can still sell |

## Buyer NPC (Citizens)

```
/npc create Buyer
/npc command add -p buyer
```

Or run it from the console, without giving players any permission:

```
/npc command add lootbuyer open <p>
```

## Configuring items

```yaml
language: en            # en, ru or your own lang/<code>.yml
rotation-minutes: 60
items-per-rotation: 0   # 0 = all items, 7 = random 7 each rotation
items:
  diamond: { material: DIAMOND, price: [90, 140], limit: 128 }
  bone:    { material: BONE,    price: 2,         limit: 0 }   # fixed price, no limit
```

Run `/buyer reload` after editing. If you change an item's price range, a new price is picked right away.
