# HCGplugin

All-in-one PaperMC 1.21.1+ plugin with utility commands, minigames and helpers. All commands default to op-only.

## Building

```
mvn package
```

The jar is produced at `target/HCGplugin-<version>.jar`. Drop it into your server's `plugins/` folder and restart.

## Help

`/hcg help` or `/hcg` shows the clickable category list.
Click a category or type `/hcg help <category> [page]` to see its commands, paginated with Back/Prev/Next arrows and click-to-fill command entries. Category names are matched loosely: `/hcg help admin` works.

## Health decay game mode

Everyone's max health slowly ticks down until it bottoms out at 3 hearts. When one player kills another, directly or indirectly (knocking them into lava, player-lit TNT, trap kills that follow a fight) everyone's health is fully restored and the decay starts over.

## Random drops game mode

While enabled, every block broken in survival drops one random survival-obtainable item or block instead of its normal drop (creative-/command-only things like command blocks, barriers, spawn eggs, and the debug stick are excluded). XP and container contents still drop normally.

Two modes:

- **dynamic** (default): every break rolls a fresh random drop.
- **static**: each block type is assigned one fixed random drop (grass -> obsidian means grass _always_ drops obsidian). The table is derived from a seed saved in `config.yml`, so it survives restarts; `/randomdrops reroll` generates a new table.

## Lava raise game mode

While enabled, every Minecraft day at the configured world-clock **start time**, lava rises layer by layer from bedrock up to the configured **max Y** over the configured **travel time**; it holds there until the **end time**, then drains back down over the same travel time.

Implementation notes:

- **The lava is a client-side illusion.** It's sent as block-change packets, so the clients render real animated lava (fog, swim physics included) while the server world never changes, nothing can burn, flow, or need cleanup.
- Burning is server-side: players at or below the lava level catch fire and take lava-rate damage (water protects them unless `replace-water` is on). Creative/spectator players are unaffected; mobs burn too if `damage-mobs` is on.
- With `burn-placed-blocks` on (default), burnable blocks that were **placed by a player** (tracked from placement, persisted across restarts) burn away as the lava passes them.
- Packets are paced (`blocks-per-tick`, default 40000 positions/tick) and sent for all chunks each player can see (`render-radius`, default = full view distance). The region is the world border, capped at `max-region` blocks.

## Hunger Games

`/hungergames` (alias `/hg`) runs a battle-royale style match around the vanilla world border:

1. Stand where you want each pedestal and run `/hg addspawn` (repeat for as many spawns as you need; `spawns`, `delspawn <#>`, `clearspawns` manage them).
2. `/hg setcenter` where the border should be centered, then `/hg scatter` teleports every online player to a random distinct spawn. If more players are online than spawns exist, it errors instead of doubling up.
3. `/hg start [seconds]` locks the border at the **start size**, shows an on-screen title countdown, then quickly expands the border to the **expanded size**. After that it works like the Fortnite storm: it holds for `stagetime` seconds, shrinks one stage over `shrinktime` seconds, and repeats until the **final size** is reached, with chat warnings and titles along the way. `/hg stop` cancels the sequence and resets the border to whatever it was before the game started.

Everything is configurable in-game (persisted to `config.yml`): `countdown`, `startsize`, `expandsize`, `finalsize`, `expandtime`, `stages`, `stagetime`, `shrinktime`, e.g. `/hg stages 8`.

## NPCs

`/npc` creates NPCs. They are packet-based so no real entity exists on the server, they can't be pushed, damaged, or killed, and they survive restarts via `npcs.yml`.

1. `/npc create <name>` spawns an NPC where you stand, facing the way you face.
2. `/npc skin <name> <player>` fetches any Minecraft account's skin straight from Mojang. `/npc skin <name> <image-url>` generates a skin from a texture image via MineSkin (`mineskin-api-key` in `config.yml`).
3. `/npc displayname <name> <text>` sets the floating name above the head (`&` colors, `|` for extra lines, `none` to hide). The real nametag is always hidden via a scoreboard team.
4. `/npc action <name> <trigger> add <type> <...>` makes clicks do things.

Triggers: `left_click`, `right_click`, `any_click`. Action types:

| Action                          | Effect                                                       |
| ------------------------------- | ------------------------------------------------------------ |
| `message <text>`                | Send the clicker a message (`&` colors, `{player}`)          |
| `player_command <cmd>`          | Run a command as the clicker                                 |
| `console_command <cmd>`         | Run a command from console (`{player}` placeholder)          |
| `sound <key> [volume] [pitch]`  | Play a sound to the clicker (e.g. `entity.villager.yes`)     |
| `wait <ticks>`                  | Pause before the remaining actions run                       |

Actions run in order per trigger; `/npc action <name> <trigger> list|remove <#>|clear` manages them, and `/npc cooldown <name> <seconds>` rate-limits clicks per player.

| Command                                    | Effect                                                     |
| ------------------------------------------ | ---------------------------------------------------------- |
| `/npc create <name>` / `remove <name>`     | Create or delete an NPC                                    |
| `/npc list` / `info <name>`                | List NPCs, or show one NPC's settings                      |
| `/npc skin <name> <player\|url\|reset>`    | Set the skin from a player name or image URL               |
| `/npc displayname <name> <text\|none>`     | Floating name; `&` colors, `\|` for lines                  |
| `/npc equipment <name> set <slot>`         | Equip your held item (empty hand clears the slot)          |
| `/npc equipment <name> <clear\|list>`      | Clear a slot or list equipment                             |
| `/npc glowing <name> <color\|off>`         | Glow outline in any team color                             |
| `/npc collidable <name> <on\|off>`         | Whether players collide with the NPC                       |
| `/npc showintab <name> <on\|off>`          | Show the NPC in the tab list                               |
| `/npc movehere <name>` / `teleport <name>` | Move the NPC to you / teleport yourself to it              |
| `/npc rotate <name> <yaw> <pitch>`         | Set its facing                                             |
| `/npc turntoplayer <name> <on\|off> [dist]`| Head-tracks each nearby player (default 5 blocks)          |
| `/npc cooldown <name> <seconds>`           | Per-player delay between click actions                     |
| `/npc action <name> <trigger> ...`         | `add <type> <...>`, `remove <#>`, `list`, `clear`          |

## Utility commands

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

## Item commands

| Command                          | Effect                                                   |
| -------------------------------- | -------------------------------------------------------- |
| `/anvil`                         | Open a virtual anvil                                     |
| `/craft`                         | Open a virtual crafting table                            |
| `/enderchest [player]` (`/ec`)   | Open your (or another player's) ender chest              |
| `/enchant <enchantment> <level>` | Enchant held item, up to level 255 (0 removes)           |
| `/hat`                           | Wear your held item as a hat (swaps with current helmet) |
| `/name <text\|reset>`            | Rename held item; `&` color codes                        |
| `/lore <text\|reset>`            | Set held item lore; `&` colors, `\|` splits lines        |

## World commands

| Command                           | Effect                                                                                     |
| --------------------------------- | ------------------------------------------------------------------------------------------ |
| `/lightning`                      | Strike lightning where you're looking                                                      |
| `/remove items`                   | Remove all loaded ground items                                                             |
| `/remove entities`                | Remove all loaded non-player entities                                                      |
| `/remove mobs [hostile\|neutral]` | Remove all mobs, or only hostile / neutral+passive ones                                    |
| `/spawner <mob>`                  | Change the mob type of the spawner you're looking at                                       |
| `/spawnmob <mob> [amount]`        | Spawn mobs at your location (max 1000)                                                     |
