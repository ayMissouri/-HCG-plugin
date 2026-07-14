# HCGplugin-FirstTo

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

A first-to item race. A GUI rolls a random target item and the first player to [get/craft] it wins:

- `/firstto craft` rolls a random **craftable** item, the first player to craft it wins.
- `/firstto obtain` rolls any **survival-obtainable** item, the first player to obtain one, however they get it, wins.

When someone wins, everyone gets a title and broadcast (and is teleported to spawn if `tpspawn` is on). Toggle whether nether-only and end-only items are eligible targets.

## Commands

Aliases: `/ftc`, `/firsttocraft`.

| Command | Effect |
| --- | --- |
| `/firstto` | Open the first-to settings menu. |
| `/firstto craft` | Roll a random craftable item, first to craft it wins. |
| `/firstto obtain` | Roll any survival item, first to get one wins. |
| `/firstto stop` | Cancel the current round. |
| `/firstto status` | Show the current target and toggles. |
| `/firstto nether <on\|off>` | Allow nether-only items as targets. |
| `/firstto end <on\|off>` | Allow end-only items as targets. |
| `/firstto tpspawn <on\|off>` | Teleport everyone to spawn when someone wins. |

## Config

`plugins/HCGplugin-FirstTo/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `first-to.include-nether` | `true` | Whether nether-only items can be rolled as targets. |
| `first-to.include-end` | `true` | Whether end-only items can be rolled as targets. |
| `first-to.tp-spawn-on-win` | `false` | Teleport everyone to the main world spawn when someone wins. |

## Permission

`hcg.firstto`, default op.
