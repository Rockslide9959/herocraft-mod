# One For All -- Output, the Safe Limit and Limb Strain (v0.16.7)

User: "come up with a better strain system for one for all and then build it ... also remove one for alls arm glow and
add it to the quirk framework". This replaces v0.16.6's 20 / 45 / 100% Output levels, the whole-body Strain bar and
the "arms give out" recoil. The forms (H), every Smash, the hold moves (United States of Smash), Plus Ultra, the Leap,
Symbol of Peace, the costume and Passing On / Lending are unchanged.

Code: `allmight/AllMightStrain.java` (the rules), `AllMight.java` (Output), `AllMightConfig.java` (every number below),
`client/allmight/AllMightInjuryLayer.java` + `AllMightPose.applyInjuries` (the look),
`client/allmight/OneForAllClientKit.java` (the HUD block). Tests: `OneForAllStrainGameTests`.

## The One For All timer (back in v0.16.7, 20 minutes)

User: "give one for all the old timer system again but raise the time limit to 20 minutes this time". The v0.12.39
timer (removed in v0.16.6) is back, **alongside** everything below -- the timer is how long you can stay in the Power
Form, the safe limit is how hard you can hit in it. Every old number is scaled x4 (the old timer was 300 s):

| | v0.12.39 | v0.16.7 |
| --- | --- | --- |
| Full timer | 300 s (5:00) | **1200 s (20:00)** |
| Drain in the Power Form | 1 s / s | 1 s / s |
| Refill (Base Form only) | 1 every 2 s | **0.5 s / s** -- 0:00 to full takes **40 min** (was 10): twice the timer, as before |
| Plus Ultra (C), paid on press | 50 (1/6) | **200 s (3:20)**, refused below 201 s |
| United States of Smash, paid on cast | 100 (1/3) | **400 s (6:40)**, the hold is refused below 400 s |
| H refused below | 1 | **4 s** |
| Steam vents below | 30 | **120 s** |

* Drains once a second while `fullPower` is set (the Power Form / Full Cowl, Plus Ultra included -- Plus Ultra's extra
  is its one-off cost, as before). The Smashes and the Leap cost nothing from it.
* **At 0:00**: forced back to the Base Form with the old hiss and the red action-bar line "You are out of One For All --
  back to your Base Form!"; H then says "Not enough One For All: 0:04 needed, you have 0:01." until it has 4 s again.
* A respawn refills it (as in v0.12.39); a new holder (grant, serum, Pass On) starts full.
* Save data: `AllMightState.ofaTimer` (key `ofa_timer`, seconds left; missing = full, so existing saves start full;
  the pre-0.16.6 `ofa` key stays ignored) and `ofa_timer_max` (the full timer, synced for the HUD bar).
* Config: `config/projecthero_one_for_all.json` (`AllMightConfig.SPEC` / `timer()`, a `VersionedConfig`): the `timer`
  section is balance-owned, and v1 resets it, so a file written before the timer came back moves up to 20 minutes while
  a v1 file keeps a server's own choice.
* **Borrowed One For All**: the timer applies to a borrower too. The loan carries whatever the lender had left (you
  cannot lend more than you have); it drains and refills like anyone's and running it out forces the borrower back to
  the Base Form (it does not end the loan). The lender's own timer is frozen while they have no power and is theirs
  again, untouched, when it comes back.
* Code: `AllMight.tickTimer / exhaust / timeLeft / spendTimer`, the costs in `AllMightAbilities.plusUltra /
  unitedStatesPress / fireUnitedStates`, the loan in `AllMightPassOn.lend`. Tests: `OneForAllStrainGameTests`
  (`theTimer*`, `atZero*`, `plusUltraAndTheUnitedStatesOfSmashSpendTheTimer`), `ConfigMigrationGameTests`
  (`aStaleOneForAllTimerGetsTwentyMinutes`).

## The idea

Like the anime: One For All is free as long as you use only what your body can take. Use more and the arm (or legs)
that threw the punch pays for it -- bruised, then badly hurt, then broken until it mends. Training raises what your
body can take.

## Output

* A percentage, 0-100, shown on the HUD gauge.
* Landing hits charges it: **+8** per Smash (once per Smash, however many it hits), **+3** per punch (at most once
  every 6 ticks). It charges **up to your safe limit** -- never past it on its own.
* After **8 s** without a hit it eases down **5 a second** to the Full Cowl floor of **5%**.
* Every change of form starts again at 5%.
* Damage multiplier: **0.9 + 0.9 x Output/100** (x0.95 at 5%, x1.08 at 20%, x1.31 at 45%, x1.8 at 100%).
  Shockwave size: **0.95 + 0.45 x Output/100** (x1.04 at 20%, x1.4 at 100%).

## The safe limit (mastery)

* Starts at **20%** (the old base level). Max **100%**.
* Every Smash thrown in the Power Form: **+0.15**. Thrown past the limit: **+0.35 more** (the body adapts).
  So roughly 500 ordinary Smashes, or far fewer reckless ones, take you from 20% to 100%.
* "Your body can take N% now" every 5%.
* Kept when you lose One For All and get it back, and on death. Not passed on: a new holder starts at 20%.

## Past the limit

Only two things push Output past the limit:

* **Plus Ultra (C)**: Output to **100%** for **22 s** (45 s cooldown after). Every Smash in it is past the limit, and
  Full Cowl itself strains every limb by **0.8/s x (Output - limit)/100**. Nothing heals while past the limit.
  When it ends Output drops back to the limit.
* **United States of Smash (hold Z 5 s)**: always fires at **100%** Output.

A Smash thrown past the limit strains **the limb that threw it**:

    strain = weight x (Output - limit) x 0.75

and costs **strain / 12 HP** (never the last heart; not in creative).

| Key | Move | Limb | Weight |
| --- | --- | --- | --- |
| R | Detroit Smash | right arm | 1.0 |
| Shift+R | New Hampshire Smash | legs | 0.9 |
| G | Texas Smash | left arm | 1.0 |
| Shift+G | Delaware Smash | left arm | 0.5 |
| X | Leap | none (not a Smash) | - |
| Shift+X | Manchester Smash | legs | 1.0 |
| Z (hold) | United States of Smash | right arm | 1.5 |
| Shift+Z | Oklahoma Smash | legs | 0.9 |
| V | Carolina Smash | both arms | 0.7 each |
| Shift+V | Missouri Smash | right arm | 0.9 |
| C | Plus Ultra | none | - |
| Shift+C | St. Louis Smash | legs | 0.9 |

Examples at the starting 20% limit (80 points over): a Plus Ultra Detroit Smash = **60** right-arm strain and 5 HP;
the United States of Smash = **90** (one more push breaks the arm). At a 60% limit the same Detroit is 30; at 100% it
is free.

## Limbs

Three limbs -- right arm, left arm, legs -- each 0-100.

| Strain | Tier | Look | Effect |
| --- | --- | --- | --- |
| 0-39 | fine | - | - |
| 40-69 | Strained | faint bruises | that limb's Smashes x0.85 |
| 70-99 | Hurt | dark purple bruising, scrapes; the arm swings 1/3 as far | x0.65 |
| 100 | **Broken** | bandaged; a broken arm hangs limp and the other hand cradles it; broken legs limp | that limb's Smashes are **refused** until it heals to **50**; a broken arm gives Weakness I (both: II), broken legs Slowness I |

A move using two limbs (Carolina) takes the weaker one. The Leap and punches are never refused. The multiplier is
fixed when the Smash is thrown, so a Smash is not weakened by its own strain.

### Healing (per limb, per second)

* Base Form **1.0**; Power Form at or under the limit **0.5**; past the limit **nothing**.
* A broken limb heals at **x0.6**.
* **Regeneration**: +1.5 per level. **Sleeping in a bed**: +20 (a night's sleep mends anything).
* **Eating**: 2.5 per hunger point at once (a steak = 20 off every limb) -- Recovery Girl's snacks.
* **Respawning**: a whole new body, all limbs at 0 (the limit is kept).

## Borrowed One For All (Lend)

A fixed **15%** limit, Output never past **45%** (even in Plus Ultra), limb strain **x2**, no mastery gain. A
broken limb ends the loan early (it goes back to the lender). The loan carries the lender's remaining One For All
timer (see the timer section).

## HUD (the Quirk HUD, bottom-right)

    One For All
    Power Form [H]                      (or PLUS ULTRA 18s / "Right arm broken: 87, usable at 50", wrapped)
    [===============-----]     18:42    the One For All timer: green, amber under 2:00, blinking red under 0:04
    Output 45%              Limit 20%   (gold; blinking red past the limit)
    [=====|##########------]            gold to the limit, red past it; the white tick is the limit
    R arm     L arm     Legs            a bar each: green / yellow / orange / blinking red (broken)
    [R][G][Z][X][C][V]                  dimmed in the Base Form; a red x on a key whose limb is broken

On a narrow window (where the keys would sit on the right end of the hotbar) the whole Quirk HUD lifts above the
hotbar and the food bar.

## One For All in the Quirk framework

* `Quirk.ONE_FOR_ALL` is the eighth Quirk: `standalone` (it keeps its own engine and `AllMightState` save data) with
  hero key `all_might`, so old saves, `/projecthero power grant all_might`, the Vestige, random serums, Pass On and the
  guidebook id all keep working. Holding it is read from the All Might state, so every existing holder is a Quirk holder
  with no migration.
* `Quirks.grant / revoke / quirk / holds / hasPower` handle it; `QuirkKits` has its kit (`OneForAllKit`); the keys go
  through the Quirk core (debounce, Erased) to the kit; `QuirkItems` gives it a serum (`quirk_serum_one_for_all`: glass
  bottle, diamond, amethyst shard, gold ingot, golden apple) and it is in the Quirk Trigger / loot / spawn-calling pools.
* Framework extensions (small, default-off): `QuirkKit.cooldownLeft / keyLocked / abilityLangKey`,
  `QuirkClientKit.drawStatus` (a kit's own HUD block instead of the meter row), `Quirk.kitQuirks()`.
* Guidebook: listed with the other Quirks in the Quirks section and the overview; its chapter is the All Might one
  (rewritten for this system). The per-Quirk chapter indices did not move.

## Removed

* `AllMightGlowLayer` -- the gold glow shells around both arms at every Output level, the jagged gold arcs on the arms at
  100%, and the Plus Ultra torso light (all part of that one layer). Kept: the gold sparks / dust and arm steam around
  the body at high Output (server particles), shockwaves, wind, transformation steam.
* The whole-body Strain bar, the recoil (Smashes locked 20 s, Weakness II + Mining Fatigue, both arms limp) and the old
  "Plus Ultra sets Strain to 75" rule; the 3-level Output and hits-per-level charging.
