# LootBuyer — resource buyer GUI for Paper/Purpur 1.21.x

[Русская версия](README.ru.md)

![Buyer menu](images/1-menu.png) ![Price tooltip](images/2-tooltip.png)

A menu where players sell resources to the server. Prices rotate on a timer, every item
has its own per-player limit, and ranks can get a price multiplier.

## Features

- 6-row menu showing the price, the change since the last rotation (▲/▼ in %), the remaining limit and how many items the player has.
- Left click sells 1, right click sells a stack, shift-click sells everything. "Sell all" button and command.
- Prices rotate on a timer: a random price within a range, announced in chat.
- Anarchy mode: a random subset of items is bought each rotation (`items-per-rotation`).
- Per-player limits until the next rotation.
- Rank multipliers via `lootbuyer.multiplier.<name>` permissions.
- Safe: only plain items are bought (no custom names, enchantments or extra data). Armor and off-hand are never sold. Every click in the menu is cancelled, so items can't be taken out or put in.
- Prices and limits are saved to `data.yml` and survive restarts.
- Item names in messages use the player's client language.
- English and Russian messages: `language: en` or `ru` in `config.yml`. Texts live in `lang/<code>.yml` (MiniMessage: gradients, hex colors). Add your own language by creating a new file.
- PlaceholderAPI support (optional).

## Requirements

- Paper or Purpur 1.21.4+, Java 21
- Vault + an economy plugin (EssentialsX, CMI, …)
- PlaceholderAPI — optional

## Commands and permissions

| Command | Permission | Default | Description |
|---|---|---|---|
| `/buyer` (`/lootbuyer`, `/skupka`, `/skup`) | `lootbuyer.use` | everyone | Open the menu |
| `/buyer sellall` | `lootbuyer.sellall` | everyone | Sell everything the buyer takes |
| `/buyer reload` | `lootbuyer.admin` | op | Reload config and messages |
| `/buyer rotate` | `lootbuyer.admin` | op | Rotate prices now |
| `/buyer open <player>` | `lootbuyer.admin` | op | Open the menu for a player (NPCs, menus) |
| — | `lootbuyer.bypasslimit` | nobody | Sell without limits |
| — | `lootbuyer.multiplier.<name>` | nobody | Price multiplier from `config.yml` |

## Buyer NPC (Citizens)

```
/npc create Buyer
/npc command add -p buyer
```

Or run it from the console, without giving players any permission:

```
/npc command add lootbuyer open <p>
```

## Placeholders

| Placeholder | Value |
|---|---|
| `%lootbuyer_next%` | Time until the next rotation, e.g. `12m 05s` |
| `%lootbuyer_next_seconds%` | Same, in seconds |
| `%lootbuyer_price_<id>%` | Current price per item, e.g. `%lootbuyer_price_diamond%` |
| `%lootbuyer_active_<id>%` | `true` if the item is being bought right now |
| `%lootbuyer_left_<id>%` | How many the player can still sell |

## Configuring items

```yaml
items:
  diamond: { material: DIAMOND, price: [90, 140], limit: 128 }
  bone:    { material: BONE,    price: 2,         limit: 0 }   # fixed price, no limit
```

Then run `/buyer reload`. If you change an item's price range, a new price is picked right away.

## Building from source

Requires Java 21 and Gradle.

```
gradle build
```

The jar ends up in `build/libs/LootBuyer-<version>.jar`.

## License

[MIT](LICENSE)
