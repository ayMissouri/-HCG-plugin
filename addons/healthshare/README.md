# HCGplugin-HealthShare

> Addon for **[HCGplugin](../../README.md)**, requires the base plugin to be installed.

`/healthshare <players-per-team>` shuffles everyone online into teams of that size, and each team then **shares a single health pool**: damage to one member drains the whole team, and when one member dies the entire team goes down with them. If the player count doesn't divide evenly, the leftover player gets a solo team then players who join mid-round are folded into a team and start sharing its health.

`/healthshare stop` disbands the teams and gives everyone their own health back.

## Commands

Alias: `/hshare`.

| Command | Effect |
| --- | --- |
| `/healthshare <players-per-team>` | Shuffle everyone into teams that share one health pool. |
| `/healthshare teams` | List the teams, their members, and their shared health. |
| `/healthshare status` | Show whether health share is running. |
| `/healthshare stop` | Disband the teams and stop sharing health. |

## Config

This addon has no config file, everything is controlled with the command.

## Permission

`hcg.healthshare`, default op.
