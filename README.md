# HCGplugin

PaperMC 1.21.x & 26.x plugin split into a **base plugin** plus **installable feature addons**. The base
provides the `/hcg` help menu and the always-on Admin, Item, and World commands; each game mode or
feature (Health Decay, Lava Raise, NPCs, ...) ships as its own addon JAR that depends on the base.
Install only the addons you want. All commands default to op-only.

## Modules

Click an addon for its full docs (commands, config, permissions).

| JAR | What it adds | Commands |
| --- | --- | --- |
| `HCGplugin` (base, **required**) | `/hcg` help, Admin/Item/World commands | fly, flyspeed, gmc/gms/gmsp/gma, tpall, freeze, invsee, heal, feed, god, vanish, burn, nickname, sudo, anvil, craft, enderchest, enchant, hat, name, lore, lightning, remove, spawner, spawnmob |
| [`HCGplugin-HealthDecay`](addons/healthdecay/README.md) | Health decay game mode | `/healthdecay` |
| [`HCGplugin-RandomDrops`](addons/randomdrops/README.md) | Random block drops game mode | `/randomdrops` |
| [`HCGplugin-LavaRaise`](addons/lavaraise/README.md) | Rising lava event | `/lavaraise`, `/volcano` |
| [`HCGplugin-HungerGames`](addons/hungergames/README.md) | Battle-royale border game | `/hungergames` |
| [`HCGplugin-NPCs`](addons/npcs/README.md) | Packet-based player NPCs | `/npc` |
| [`HCGplugin-Graves`](addons/graves/README.md) | Death graves | `/graves` |
| [`HCGplugin-HealthShare`](addons/healthshare/README.md) | Shared team health pools | `/healthshare` |
| [`HCGplugin-FirstTo`](addons/firstto/README.md) | First-to item race | `/firstto` |

Each addon hard-depends on the base. Every module has its own config and messages in its own `plugins/<name>/` folder.

## Building

```
mvn package
```

This is a multi-module Maven build (requires JDK 21). Every module's JAR is collected into a
`dist/` folder at the repo root (each module also keeps its own copy under `target/`):

- `dist/HCGplugin-<version>.jar` (base)
- `dist/HCGplugin-<Feature>-<version>.jar` (one per addon)

Drop `HCGplugin-<version>.jar` into your server's `plugins/` folder (it is required), then add
whichever addon JARs you want, and restart. To build a single addon and the base only:
`mvn -pl addons/lavaraise -am package`.

## Help

`/hcg help` or `/hcg` shows the clickable category list.
Click a category or type `/hcg help <category> [page]` to see its commands, paginated with Back/Prev/Next arrows and click-to-fill command entries. Category names are matched loosely: `/hcg help admin` works.

## Base commands

These ship with the base `HCGplugin` and are always available. The game modes and other features live
in the addon docs linked under [Modules](#modules) above.

### Utility commands

| Command                                 | Effect                                                           |
| --------------------------------------- | ---------------------------------------------------------------- |
| `/hcg help [category] [page]`           | Categorized help menu with clickable navigation                  |
| `/flyspeed <1-10>`                      | Set your fly speed (vanilla default is 1)                        |
| `/fly`                                  | Toggle flight in any gamemode (survival, adventure, ...)         |
| `/gmc` `/gms` `/gmsp` `/gma` `[player]` | Gamemode shortcuts: creative, survival, spectator, adventure     |
| `/tpall here`                           | Teleport all other players to you                                |
| `/tpall looking`                        | Teleport all other players to the block you're looking at        |
| `/tpall <player>`                       | Teleport all other players to that player                        |
| `/freeze <player>`                      | Toggle freeze on a player (can look around, can't move or pearl) |
| `/freeze all`                           | Freeze everyone except you; run again to unfreeze                |
| `/invsee <player>`                      | Open another player's inventory                                  |
| `/heal [player\|all]`                   | Heal yourself, a player, or everyone (also extinguishes fire)    |
| `/feed [player\|all]`                   | Restore hunger and saturation                                    |
| `/god [player]`                         | Toggle invulnerability                                           |
| `/burn <player> [seconds]`              | Set a player on fire (default 5s)                                |
| `/nickname [player] <text\|reset>`      | Set a display name for chat + tab list; `&` colors               |
| `/sudo <player> <msg or /cmd>`          | Make a player chat a message or run a command                    |

### Item commands

| Command                          | Effect                                                   |
| -------------------------------- | -------------------------------------------------------- |
| `/anvil`                         | Open a virtual anvil                                     |
| `/craft`                         | Open a virtual crafting table                            |
| `/enderchest [player]` (`/ec`)   | Open your (or another player's) ender chest              |
| `/enchant <enchantment> <level>` | Enchant held item, up to level 255 (0 removes)           |
| `/hat`                           | Wear your held item as a hat (swaps with current helmet) |
| `/name <text\|reset>`            | Rename held item; `&` color codes                        |
| `/lore <text\|reset>`            | Set held item lore; `&` colors, `\|` splits lines        |

### World commands

| Command                           | Effect                                                                                     |
| --------------------------------- | ------------------------------------------------------------------------------------------ |
| `/lightning`                      | Strike lightning where you're looking                                                      |
| `/remove items`                   | Remove all loaded ground items                                                             |
| `/remove entities`                | Remove all loaded non-player entities                                                      |
| `/remove mobs [hostile\|neutral]` | Remove all mobs, or only hostile / neutral+passive ones                                    |
| `/spawner <mob>`                  | Change the mob type of the spawner you're looking at                                       |
| `/spawnmob <mob> [amount]`        | Spawn mobs at your location (max 1000)                                                     |
