# HCGplugin-NPCs

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

`/npc` creates NPCs. They are packet-based so no real entity exists on the server, they can't be pushed, damaged, or killed, and they survive restarts via `npcs.yml`.

1. `/npc create <name>` spawns an NPC where you stand, facing the way you face.
2. `/npc skin <name> <player>` fetches any Minecraft account's skin straight from Mojang. `/npc skin
   <name> <image-url>` generates a skin from a texture image via MineSkin (`mineskin-api-key` in
   `config.yml`).
3. `/npc displayname <name> <text>` sets the floating name above the head (`&` colors, `|` for extra
   lines, `none` to hide). The real nametag is always hidden via a scoreboard team.
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

## Commands

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

## Config

`plugins/HCGplugin-NPCs/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `npc.mineskin-api-key` | `""` | Only needed for `/npc skin <name> <image-url>`. Free key from https://account.mineskin.org. |

NPCs are persisted to `plugins/HCGplugin-NPCs/npcs.yml`.

## Permission

`hcg.npc`, default op.
