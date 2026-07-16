# HCGplugin

Minecraft 1.21.x & 26.x plugin split into a **base plugin** plus **installable feature addons**. The base provides the `/hcg` help menu, the **Tweaks** category, and the always-on Admin, Item, and World commands; each game mode or feature (Health Decay, Lava Raise, NPCs, ...) ships as its own addon JAR that depends on the base. Install the addons you want. All commands default to op-only.

## Server software

Runs on **Paper** (including **Purpur**, **Pufferfish**, **Leaves**), and **Folia**. Spigot and CraftBukkit are not supported as of yet.

Two things Folia can't do, because its own docs list them as unimplemented:

| | On Folia |
| --- | --- |
| `/invsee <player>`, `/enderchest <player>` | One live inventory can't be shared across two region threads. |
| Health Share team name colours | Off. [Folia considers all scoreboard API broken](https://github.com/PaperMC/Folia); shared health itself works normally. |

Everything else behaves identically. Lava Raise's `damage-mobs` only burns mobs in chunks near a player
on Folia, since mobs elsewhere aren't ticking.

## Modules
<details>
<summary>Extra modules/addon plugins</summary>
Click an addon for its full docs (commands, config, permissions).

| Addon | What it adds | Commands |
| --- | --- | --- |
| [`HCGplugin-HealthDecay`](addons/healthdecay/README.md) | Health decay game mode | `/healthdecay` |
| [`HCGplugin-RandomDrops`](addons/randomdrops/README.md) | Random block drops game mode | `/randomdrops` |
| [`HCGplugin-LavaRaise`](addons/lavaraise/README.md) | Rising lava event | `/lavaraise`, `/volcano` |
| [`HCGplugin-HungerGames`](addons/hungergames/README.md) | Battle-royale border game | `/hungergames` |
| [`HCGplugin-NPCs`](addons/npcs/README.md) | Packet-based player NPCs | `/npc` |
| [`HCGplugin-Graves`](addons/graves/README.md) | Death graves | `/graves` |
| [`HCGplugin-HealthShare`](addons/healthshare/README.md) | Shared team health pools | `/healthshare` |
| [`HCGplugin-FirstTo`](addons/firstto/README.md) | First-to item race | `/firstto` |

Each addon hard-depends on the base. Every module has its own config and messages in its own `plugins/<name>/` folder.
</details>

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

<details>
<summary><b>Tweaks</b></summary>

Small gameplay changes you switch on individually. They live in the base plugin (not an addon) and are
all **off by default**. `/tweaks` opens a chest menu: left-click an icon to toggle it, right-click to
open its settings, where left-click steps an option forward and right-click steps it back. Everything
is also settable from chat or the console, and every change writes straight back to
`plugins/HCGplugin/config.yml`.

| Command                                    | Effect                                              |
| ------------------------------------------ | --------------------------------------------------- |
| `/tweaks`                                  | Open the tweaks chest menu                          |
| `/tweaks list`                             | List every tweak and its state in chat              |
| `/tweaks <tweak> [on\|off]`                | Check or set one tweak (works from the console)     |

---

<details>
<summary>Veinminer</summary>

### Veinminer

Break one block of an ore vein and the whole vein goes with it. A vein is the run of **same-material**
blocks touching each other, corners included, so coal next to iron stays two veins.

| Command                                    | Effect                                                        |
| ------------------------------------------ | ------------------------------------------------------------- |
| `/veinminer`                               | Status + settings menu                                        |
| `/veinminer on\|off`                       | Turn the tweak on or off                                      |
| `/veinminer mode <shift\|enchant\|both>`   | Who veinmines (see below)                                     |
| `/veinminer hunger <on\|off>`              | Whether the extra blocks cost hunger                          |
| `/veinminer durability <per-block\|single>`| One point per ore, or one point total                         |
| `/veinminer size <1-4096>`                 | Most extra blocks one break may take out (default 64)         |
| `/veinminer tool <on\|off>`                | Require a tool that actually drops the ore                    |
| `/veinminer sneak <on\|off>`               | Whether an enchanted tool must sneak too                      |
| `/veinminer chance <0-100>`                | Chance an enchanting table rolls Veinminer                    |
| `/veinminer minlevel <1-30>`               | Level the table option must cost before it can roll           |
| `/veinminer grant\|remove [player]`        | Add or remove the enchant on a held tool                      |
| `/veinminer gui`                           | Open just this tweak's settings menu                          |
| `/veinminer reload`                        | Re-read `config.yml`                                          |

**Modes**

| Mode      | Who veinmines                                                                    |
| --------- | -------------------------------------------------------------------------------- |
| `shift`   | Everyone, while sneaking                                                          |
| `enchant` | Only a tool carrying the Veinminer enchant (and sneaking too, unless `sneak off`) |
| `both`    | Either one                                                                        |

**The enchant.** Veinminer is *not* a real registry enchantment — registering one needs a Paper
bootstrapper and a `paper-plugin.yml`, which would change how the addons resolve the base's shared
classes. It is a persistent tag on the item plus a lore line, so it survives chests, restarts and
copies exactly like a real enchant, but it does **not** transfer through anvils, enchanted books or
grindstones. Enchanting tables roll it on the tools listed under `enchant.tools` in `config.yml`
(the six pickaxes by default) when the chosen option costs at least `enchant.min-level`.

**Costs.** The block you actually hit is broken by vanilla, which charges its one durability point and
its exhaustion as always. Everything below is only about the *extra* blocks:

- `durability: per-block` — one more point per extra ore. If the tool gives out mid-vein, the vein
  stops there.
- `durability: single` — nothing extra, so the whole vein costs the one point vanilla already took,
  as if you had broken a single block.
- `hunger: enabled` — adds `hunger.exhaustion-per-block` (default 0.05) of exhaustion per extra
  block. Creative and spectator pay neither cost.

Fortune and Silk Touch apply to the whole vein, since each block is broken with the tool in hand. Every
extra block fires a normal block-break event, so region-protection plugins get their say on each one.
`hcg.veinminer.use` decides who it works for and defaults to **everyone** (`shift` mode is meant as a
server-wide rule); negate it to carve players out. The `/tweaks` and `/veinminer` commands stay op-only.

</details>

---

</details>

<details>
<summary><b>Utility commands</b></summary>
  
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

---

</details>
  
<details>
<summary><b>Item commands</b></summary>
  
| Command                          | Effect                                                   |
| -------------------------------- | -------------------------------------------------------- |
| `/anvil`                         | Open a virtual anvil                                     |
| `/craft`                         | Open a virtual crafting table                            |
| `/enderchest [player]` (`/ec`)   | Open your (or another player's) ender chest              |
| `/enchant <enchantment> <level>` | Enchant held item, up to level 255 (0 removes)           |
| `/hat`                           | Wear your held item as a hat (swaps with current helmet) |
| `/name <text\|reset>`            | Rename held item; `&` color codes                        |
| `/lore <text\|reset>`            | Set held item lore; `&` colors, `\|` splits lines        |

---

</details>

<details>
<summary><b>World commands</b></summary>

| Command                           | Effect                                                                                     |
| --------------------------------- | ------------------------------------------------------------------------------------------ |
| `/lightning`                      | Strike lightning where you're looking                                                      |
| `/remove items`                   | Remove all loaded ground items                                                             |
| `/remove entities`                | Remove all loaded non-player entities                                                      |
| `/remove mobs [hostile\|neutral]` | Remove all mobs, or only hostile / neutral+passive ones                                    |
| `/spawner <mob>`                  | Change the mob type of the spawner you're looking at                                       |
| `/spawnmob <mob> [amount]`        | Spawn mobs at your location (max 1000)                                                     |

---

</details>
