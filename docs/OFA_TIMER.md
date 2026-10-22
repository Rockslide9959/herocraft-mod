# One For All -- the timer

v0.16.7 (user: "for one for all remove strain and output it should just be the 20 minute timer like how it was before").
One For All runs on the v0.12.39 timer rules, at 20 minutes, and nothing else. The Output percentage, the safe limit
(mastery) and the per-limb strain from earlier in v0.16.7 are gone (they replaced `docs/OFA_STRAIN.md`).

## The timer

| Rule | Value |
|---|---|
| Full timer | 20:00 of Power Form (`powerFormSeconds` 1200) |
| Power Form drain | 1 s every second |
| Base Form refill | 0.5 s every second (0:00 to 20:00 takes 40 min) |
| At 0:00 | forced back to the Base Form ("You are out of One For All") |
| H refused below | 0:04 (`minimumToTransformSeconds`) |
| Plus Ultra (C) | costs 3:20 once (refused without it), then 22 s |
| United States of Smash (hold Z) | costs 6:40 when the punch is cast (the hold is refused without it) |
| Steam / amber bar below | 2:00 (`lowSteamSeconds`) |
| Respawn | refills it |
| Borrowed One For All | the loan carries the lender's time left; the lender's own timer waits |

The numbers live in `config/projecthero_one_for_all.json` (`AllMightConfig.SPEC`, a `VersionedConfig`).
Code: `AllMight.tickTimer` / `exhaust` / `spendTimer`; the timer is saved as `ofa_timer` / `ofa_timer_max` in `AllMightState`.

## Damage

Every Smash lands its designed damage in the Power Form (x1). Plus Ultra makes them hit x1.3 for its 22 s and takes the
Leap's cooldown away; its cooldown is 20 s (the v0.12.39 value, back now that it costs timer again).

| Smash | Damage | Plus Ultra |
|---|---|---|
| Detroit (R) | 18 | 23.4 |
| Texas (G) | 24 | 31.2 |
| Carolina (V) | 20 | 26 |
| New Hampshire (Shift+R) | 20 | 26 |
| United States (hold Z) | 75 | 97.5 |
| Delaware (Shift+G) | 10 x3 | 13 x3 |
| Manchester (Shift+X) | 22 | 28.6 |
| Oklahoma (Shift+Z) | 16 | 20.8 |
| Missouri (Shift+V) | 20 | 26 |
| St. Louis (Shift+C) | 18 | 23.4 |

Shockwaves are their designed size (Output no longer widens them). Bosses still lose at most 10% of their max health per hit.

## HUD

The One For All block of the Quirk HUD: the status line (Base Form / Power Form / Transforming / PLUS ULTRA 18s) over the
timer row (a draining bar and m:ss -- green, amber below 2:00, blinking red below 0:04), and the United States of Smash
charge as a hairline under it while Z is held.

## Old saves

The removed keys (`output_pct`, `mastery`, `limb_strain`, `broken_limbs`, `last_hit_at`, and v0.16.6's `output`,
`strain`, `recoil_until`) are ignored when a save loads and dropped on the next save. Tests: `OneForAllTimerGameTests`.
