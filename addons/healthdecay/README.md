# HCGplugin-HealthDecay

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

Everyone's max health slowly ticks down until it bottoms out at the set amount of hearts. When one player kills another, directly or indirectly (knocking them into lava, player-lit TNT, trap kills that follow a fight) everyone's health is fully restored and the decay starts over.

## Commands

Aliases: `/hdecay`, `/hd`.

| Command | Effect |
| --- | --- |
| `/healthdecay` | Open the health decay settings menu. |
| `/healthdecay on\|off` | Start or stop the health decay game mode. |
| `/healthdecay status` | Show current max health, floor, and decay rate. |
| `/healthdecay restore` | Restore everyone's health now. |
| `/healthdecay interval <seconds>` | Set seconds between decay ticks. |
| `/healthdecay amount <hearts>` | Set hearts lost per decay tick. |

## Config

`plugins/HCGplugin-HealthDecay/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Whether the game mode is running (toggled by `/healthdecay on\|off`). |
| `decay.interval-seconds` | `60` | Seconds between each decay tick. |
| `decay.amount-hearts` | `0.5` | Hearts lost per tick. |
| `decay.minimum-hearts` | `3.0` | Health never drops below this. |
| `decay.maximum-hearts` | `10.0` | The health everyone is restored to. |
| `kill.credit-window-seconds` | `45` | Window in which a player can be credited for a kill. |

## Permission

`hcg.healthdecay`, default op.
