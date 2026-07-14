# HCGplugin-LavaRaise

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

Two related world-event features: a daily rising **lava raise** flood and manual/scheduled **volcano** eruptions.

## Lava raise

While enabled, every Minecraft day at the configured world-clock **start time**, lava rises layer by layer from bedrock up to the configured **max Y** over the configured **travel time**; it holds there until the **end time**, then drains back down over the same travel time.

Implementation notes:

- **The lava is a client-side illusion.** It's sent as block-change packets, so the clients render
  real animated lava (fog, swim physics included) while the server world never changes, nothing can
  burn, flow, or need cleanup.
- Burning is server-side: players at or below the lava level catch fire and take lava-rate damage
  (water protects them unless `replace-water` is on). Creative/spectator players are unaffected; mobs
  burn too if `damage-mobs` is on.
- With `burn-placed-blocks` on (default), burnable blocks that were **placed by a player** (tracked
  from placement, persisted across restarts) burn away as the lava passes them.
- Packets are paced (`blocks-per-tick`, default 40000 positions/tick) and sent for all chunks each
  player can see (`render-radius`, default = full view distance). The region is the world border,
  capped at `max-region` blocks.

## Volcano

`/volcano setcenter` marks the crater at the block you're looking at, then `/volcano erupt [seconds]` throws debris and particles (with optional screen shake for nearby players). `/volcano schedule <time|off>` makes it erupt daily at a world-clock time, and `/volcano stop` calms it immediately.

## Commands

`/lavaraise` alias: `/lr`.

| Command | Effect |
| --- | --- |
| `/lavaraise` | Open the lava raise settings menu. |
| `/lavaraise on\|off` | Arm or disarm the daily rising lava event. |
| `/lavaraise status` | Show phase, current lava level, and settings. |
| `/lavaraise start <time>` | World-clock time the lava starts rising. |
| `/lavaraise end <time>` | World-clock time the lava starts draining. |
| `/lavaraise duration <s>` | Seconds to travel bedrock <-> max level. |
| `/lavaraise maxy <y>` | The Y level the lava rises to. |
| `/lavaraise water <on\|off>` | Whether oceans fill with lava too. |
| `/lavaraise blocks <on\|off>` | Whether player-placed burnables burn away. |
| `/lavaraise mobs <on\|off>` | Whether mobs burn in the lava too. |
| `/lavaraise cancel` | Drain the lava immediately. |
| `/lavaraise purge <y>` | Remove REAL lava blocks in the region up to Y. |
| `/volcano setcenter` | Mark the crater at the block you're looking at. |
| `/volcano erupt [seconds]` | Eruption: debris, particles, screen shake. |
| `/volcano schedule <time\|off>` | Erupt daily at a world-clock time. |
| `/volcano stop` | Calm the volcano immediately. |

## Config

`plugins/HCGplugin-LavaRaise/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `lava-raise.enabled` | `false` | Whether the daily lava event is armed. |
| `lava-raise.start-time` | `0` | World-clock tick (0-23999) the lava starts rising. |
| `lava-raise.end-time` | `12000` | World-clock tick the lava starts draining. |
| `lava-raise.rise-duration-seconds` | `300` | Seconds to travel bedrock ↔ max-y. |
| `lava-raise.max-y` | `62` | Y level the lava stops rising at. |
| `lava-raise.replace-water` | `false` | Oceans look flooded too and stop protecting from burning. |
| `lava-raise.burn-placed-blocks` | `true` | Player-placed burnables burn as the lava passes them. |
| `lava-raise.damage-mobs` | `false` | Mobs in the lava burn and take damage like players. |
| `lava-raise.blocks-per-tick` | `40000` | Positions scanned/sent per tick across all players. |
| `lava-raise.max-region` | `512` | Region cap in blocks across. |
| `lava-raise.render-radius` | `32` | Chunks around each player that get the lava visual (capped at view distance). |
| `volcano.duration-seconds` | `20` | Default eruption length in seconds. |
| `volcano.shake-enabled` | `false` | Whether eruptions shake nearby players' screens. |
| `volcano.shake-radius` | `150` | Players within this many blocks of the center get screen shake. |
| `volcano.schedule-enabled` | `false` | Daily scheduled eruption. |
| `volcano.erupt-time` | `13000` | World-clock tick of the daily eruption. |

## Permissions

`hcg.lavaraise` and `hcg.volcano`, both default op.
