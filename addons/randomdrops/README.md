# HCGplugin-RandomDrops

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

While enabled, every block broken in survival drops one random survival-obtainable item or block instead of its normal drop (creative/command-only things like command blocks, barriers, spawn eggs, and the debug stick are excluded). XP and container contents still drop normally.

Two modes:

- **dynamic** (default): every break rolls a fresh random drop.
- **static**: each block type is assigned one fixed random drop (grass -> obsidian means grass
  _always_ drops obsidian). The table is derived from a seed saved in `config.yml`, so it survives
  restarts; `/randomdrops reroll` generates a new table.

## Commands

Aliases: `/rdrops`, `/rd`.

| Command | Effect |
| --- | --- |
| `/randomdrops` | Open the random drops settings menu. |
| `/randomdrops on\|off` | Enable or disable random block drops. |
| `/randomdrops status` | Show whether random drops are on. |
| `/randomdrops mode <dynamic\|static>` | Fresh roll per break, or one fixed drop per block type. |
| `/randomdrops enchants <on\|off>` | Give every drop 1-3 random enchantments. |
| `/randomdrops mobs <on\|off>` | Mobs also drop random items instead of loot. |
| `/randomdrops reroll` | Randomize the static drop table again. |

## Config

`plugins/HCGplugin-RandomDrops/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `random-drops.enabled` | `false` | Whether random drops are on. |
| `random-drops.mode` | `dynamic` | `dynamic` = fresh roll per break; `static` = fixed drop per block type. |
| `random-drops.enchanted` | `false` | Give every drop 1-3 random enchantments at random levels. |
| `random-drops.mobs` | `false` | Mobs also drop a random item instead of their normal loot. |

## Permission

`hcg.randomdrops`x default op.
