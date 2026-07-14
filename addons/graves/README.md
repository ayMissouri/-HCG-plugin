# HCGplugin-Graves

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

`/graves on|off` toggles death graves. When enabled, a player's items and full XP are stored in a player-head grave at their death location instead of dropping everywhere. Only the owner can collect their grave, right-click or break the head and everything goes back into their inventory (overflow drops at the grave). Graves are immune to explosions, pistons, and flowing liquids, and survive restarts via `graves.yml`.

OPs can look at a grave and run `/graves remove` to force remove it, dropping its items and XP on the floor.

## Commands

| Command | Effect |
| --- | --- |
| `/graves` | Open the graves settings menu. |
| `/graves on\|off` | Store death drops and XP in a grave only the owner can open. |
| `/graves status` | Show whether graves are on and how many exist. |
| `/graves remove` | Force remove the grave you're looking at, spilling its contents. |

## Config

`plugins/HCGplugin-Graves/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `graves.enabled` | `false` | Whether death graves are active. |

Placed graves are persisted to `plugins/HCGplugin-Graves/graves.yml`.

## Permission

`hcg.graves`, default op.
