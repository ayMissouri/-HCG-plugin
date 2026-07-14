# HCGplugin-HungerGames

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

`/hungergames` (alias `/hg`) runs a battle-royale style match around the vanilla world border:

1. Stand where you want each pedestal and run `/hg addspawn` (repeat for as many spawns as you need;
   `spawns`, `delspawn <#>`, `clearspawns` manage them).
2. `/hg setcenter` where the border should be centered, then `/hg scatter` teleports every online
   player to a random distinct spawn. If more players are online than spawns exist, it errors instead
   of doubling up.
3. `/hg start [seconds]` locks the border at the **start size**, shows an on-screen title countdown,
   then quickly expands the border to the **expanded size**. After that it works like the Fortnite
   storm: it holds for `stagetime` seconds, shrinks one stage over `shrinktime` seconds, and repeats
   until the **final size** is reached, with chat warnings and titles along the way. `/hg stop`
   cancels the sequence and resets the border to whatever it was before the game started.

Everything is configurable in-game (persisted to `config.yml`): `countdown`, `startsize`,
`expandsize`, `finalsize`, `expandtime`, `stages`, `stagetime`, `shrinktime`, e.g. `/hg stages 8`.

## Commands

| Command | Effect |
| --- | --- |
| `/hungergames addspawn` | Add a spawn point at your location. |
| `/hungergames spawns` | List all spawn points. |
| `/hungergames delspawn <#>` | Remove a spawn point by number. |
| `/hungergames clearspawns` | Remove all spawn points. |
| `/hungergames scatter` | Teleport all online players to random distinct spawns. |
| `/hungergames setcenter` | Set the world border center to where you stand. |
| `/hungergames start [seconds]` | Title countdown, border expands, then shrinks in stages. |
| `/hungergames stop` | Cancel the sequence and restore the previous border. |
| `/hungergames status` | Show phase, border sizes, and stage settings. |
| `/hungergames startsize <blocks>` | Border size during the countdown. |
| `/hungergames expandsize <blocks>` | Border size after the expansion. |
| `/hungergames finalsize <blocks>` | Border size after the last stage. |
| `/hungergames countdown <s>` | Length of the starting countdown. |
| `/hungergames expandtime <s>` | How long the expansion takes. |
| `/hungergames stages <n>` | Number of shrink stages. |
| `/hungergames stagetime <s>` | Hold time between stages. |
| `/hungergames shrinktime <s>` | How long each stage's shrink takes. |

## Config

`plugins/HCGplugin-HungerGames/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `hungergames.spawns` | `[]` | Spawn points, stored as `world;x;y;z;yaw;pitch`. |
| `hungergames.countdown-seconds` | `60` | On-screen title countdown before the border expands. |
| `hungergames.border.start-size` | `100` | Border size (blocks across) when the game begins. |
| `hungergames.border.expanded-size` | `1000` | Size the border quickly expands to after the countdown. |
| `hungergames.border.final-size` | `30` | Size after the last shrink stage. |
| `hungergames.border.expand-seconds` | `15` | Seconds the initial expansion takes. |
| `hungergames.stages` | `5` | Number of shrink stages down to final-size. |
| `hungergames.stage-hold-seconds` | `180` | Seconds the border holds still between stages. |
| `hungergames.stage-shrink-seconds` | `60` | Seconds each stage's shrink takes. |

## Permission

`hcg.hungergames`, default op.
