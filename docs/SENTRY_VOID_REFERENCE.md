# Sentry & the Void (v0.17.5) — reference

Design: `docs/SENTRY_VOID_DESIGN.md` (approved 2026-11-15, reworked 2026-11-16; moveset 2 for both forms + the takeover's 2 s attack pace 2026-11-18). Code: `com.projecthero.mod.sentry` (server/common),
`com.projecthero.mod.client.sentry` (client). Every number lives in `SentryConfig`. Tests: `SentryGameTests`,
`VoidTakeoverGameTests`. Assets: `node scratchpad/gen_sentry_v0175.js` (the original pixel art: serum, vat, blocks, the original
suit / Void Form textures); `node scratchpad/sentry/make_suit_textures.js` (the rework: suit textures from the two borrowed skins, capes,
the takeover body, suit icons, preview sheet). Lang: `node scratchpad/lang_sentry_v0175.js`, then
`node scratchpad/sentry/lang_sentry_rework_v0175.js` (key by key). Films/screens: `scratchpad/SentryHarness.v0175.java.txt` ->
`docs/screenshots/v0175/sentry/`; rework: `scratchpad/SentryHarness2.v0175.java.txt` -> `docs/screenshots/v0175/sentry2/`; moveset 2:
`scratchpad/SentryMovesHarness.v0175.java.txt` -> `docs/screenshots/v0175/sentry_moves/` (`node scratchpad/sentry/films_moves.js`), lang
`node scratchpad/sentry/lang_sentry_moveset2_v0175.js`.

## Round 3 (2026-11-18, user) -- the current layout and rules

Design + the user's words: `docs/SENTRY_VOID_DESIGN.md` ("Round 3"). Lang `node scratchpad/sentry/lang_sentry_round3_v0175.js`; films / stills
`scratchpad/SentryRound3Harness.v0175.java.txt` -> `docs/screenshots/v0175/sentry_round3/` (`node scratchpad/sentry/films_round3.js`).
Where a table further down disagrees, this section wins (the moveset-2 tables are kept for the moves' own numbers).

| Key | Sentry (id) | Numbers |
|---|---|---|
| H | Let It Out (`let_it_out`) | the trial (unchanged); sent as `SentryActionPayload.LET_IT_OUT` -> `SentryAbilityManager.pressH`; merged: Return; Shift+H = power wheel |
| R | Golden Fury | unchanged |
| Shift+R | **Solar Flare** (`solar_flare`, new) | r 8: 24 at the centre -> 12 at the edge, undead x1.5 + 4 s fire, knockback 1.6 (x0.6 at the edge) + 0.3 up, mobs Blindness 3 s, everything Glowing 3 s; 18 Sun; +0.6 Void; 200-tick cd; pose POSE_FLARE; fx FLARE (rays, rings, a warm flash within 20) |
| Hold G | **Solar Gaze** | Heat Vision parity (`KryptonianConfig.HEAT_*`): 4 every 5 ticks (16/s), 32 blocks, 5 s fire, sets a block alight every second when it hits no creature (griefing rule); **no time cap** (ends on key-up or no Sun: 5 Sun/s, +0.17 Void/s); an **exact ray** (`SentryMoves.gazeHit`, margin 0, client draws the same); the 75-Void aim drift is off while it runs; orange after 40 ticks (looks only); 60-tick cd when it ends |
| Hold Shift+G | Disassemble | unchanged numbers |
| Z / Shift+Z | Sunfall / A Million Suns | unchanged |
| C | Golden Guardian | unchanged |
| Shift+C | Skyward Hurl | unchanged numbers |
| X | Sonic Boom (ground) / **Flight Boost** (flying, `flight_boost`) | boost: flight speed x1.5 for 600 ticks (33 b/s, 66 sprinting; `DirectionalFlight` reads the synced `boostUntil`), a sonic-boom ring + BOOM + a 1.6 b/t shove at the start, a golden contrail (client ribbons + server sparks); 10 Sun; 900-tick cd from the start; the X box counts the seconds down, lit |
| V | Molecular Shield | unchanged numbers |
| Shift+V | **Golden Sense** (`golden_sense`, new; replaces Calm) | every living thing within 40 (not him): Glowing 200 ticks (outlines through walls; seen by every viewer); him: Night Vision 1200 ticks; 10 Sun; 600-tick cd; no Void; pose POSE_SENSE; fx SENSE |

| Key | The Void, merged (id) | Numbers |
|---|---|---|
| H | Return | below 90 Void |
| R / Shift+R / G / Shift+G / Z / Shift+Z / C / Shift+C / X | Tendril Lash / Erase / Shadow Pool / Swallow / Nightmare / Shadow Eclipse / Blot Out / Shadow Clones / Shadow Step | unchanged |
| V | **Umbral Shroud** (`umbral_shroud`, new) | 160 ticks: damage taken x0.6 on top of the 75%, hostile projectiles within 3.5 discarded, a melee attacker within 5 gets Wither I + Darkness 3 s (mobs also Blindness); 400-tick cd (x0.75 merged); +1 Void |
| Shift+V | **Shadow Meld** (`shadow_meld`, new) | 120 ticks: Invisibility + Speed II, every Mob within 32 targeting him drops the target (re-checked every 10 ticks); a Void attack ends it; 600-tick cd (x0.75); +0.6 Void |

The takeover's brain still picks only from `VOID_ATTACKS` (no utilities).

**Merged:** every hit x1.5 (`MERGED_DAMAGE`, was 1.25); Void cooldowns x0.75; Void gains x1.5.

**The Void meter** (`Sentry.tickMeters`, `SentryCalmTasks`): it rises only from the powers.

| | Void a second / a chunk |
|---|---|
| Not merged, anywhere (night, dark, shade) | -0.05/s |
| Direct sunlight | -0.30/s (-0.05 - 0.25) |
| Merged with the Void | +0.5/s of its own (x1.5 -> 0.75; the suit x0.6) |
| Asleep (`isSleeping`) | -1/s more |
| A night slept through (`EntitySleepEvents.STOP_SLEEPING`, day time 23500..2000) | -40 at once |
| A fishing bobber of his in the water | -0.15/s more |
| A fish (or anything) reeled in (`Stats.FISH_CAUGHT`) | -2 |
| A ripe crop harvested (wheat / carrots / potatoes / beetroot / nether wart / cocoa / sweet berries at full age; `PlayerBlockBreakEvents.AFTER`) | -0.4 |
| A trade (`Stats.TRADED_WITH_VILLAGER`), animals bred (`ANIMALS_BRED`), a disc played (`PLAY_RECORD`) | -1.5 / -1.5 / -1 |
| Each move | its own gain (moveset-2 table below; Solar Flare +0.6, the Void's utilities +1 / +0.6) |
| Golden Guardian | -8, -1 per other helped |
| Removed | night +0.08/s, dark +0.17/s, heavy hits, kills, Calm |

Drains are never scaled; gains x0.6 in the suit, x1.5 merged. The stats are compared once a second against a session map (UUID -> ints;
first look only records). Creative: the Void never climbs.

**The look** (`Sentry.look`, client-safe; the suit layer, cape and tests use it): NONE / SUIT / VOID_OVER_SUIT / VOID_OVER_SKIN / BLACK from the
armour worn right now and the fade. Merged without the suit: the **Void suit** (`Look.VOID_SUIT`: the borrowed skin with the head cleared,
or the fallback `void_form` with the head cleared) dissolves in over the bare wearer (his face and hair show; the hat layer is kept, jacket /
sleeves / trousers layers hidden) and the black cape unrolls from the collar with the fade (`SentryCapeLayer.unfurl`); on return it fades
away to his own skin. With the suit on: comics suit <-> Void suit as before. A never-merged state (`formChangedAt` 0) shows no fade.
Armour is still drawn over it (it is a visual, not an item). First-person arm: the same textures.

**The takeover's eyes:** no eye texels any more (`void_body.png` regenerated); `SentrySuitLayer.eyeSpecks` draws two full-bright specks 0.32 px
wide at face pixels 2 and 5, row 4, just in front of the face, each with a 1.1 px / 0.55 px breathing halo (additive, in the head's frame).

**Shadow figures** (`ShadeFigures`): the clones (`SHADE` with owner = `VoidMoves.CLONE_FX_ID` + zone id, every 2 ticks) and the Nightmare
figures (owner -1) are real slim player models scaled 0.6 wide / deep, a generated near-black smoky translucent texture (alpha ~0.82 x a
breathing 0.85..1), a stalking walk by distance moved (clones slide between updates, yaw eased), arms reaching, and the eye specks.

**The bars** (`SentryHud.xpBar`): the vanilla `hud/experience_bar_background` (untinted) and `hud/experience_bar_progress` tinted by the
shader colour (green #7EFC20 -> Sun #F5C518; Void #E23030 -> #8E0F12 -> #3A0505 by the fill), cropped from the left to the bar width (end
cap kept, <= 182), 5 px; the 50/75/90 marks on the Void bar. `SentryHud.oldBars` (harness only) draws the old bevelled bar.

**The landing** (`HeroLandingStyle.SENTRY` id 50, `SENTRY_VOID` id 51, both FIST pose): a self-flying Sentry claims it; on a fast touchdown the
shared effects play plus `SentryMoves.landing`: 8 + 14 x strength within 3 + 2.5 x strength, knockback 0.9 + 0.8 x s, light damage (void
damage merged); he stops dead where he lands (else a dive's momentum slides him out of the kneel); golden crater + ring, or a shadow pool merged.

**The flight shake** (shared: `FlightPoseHelper`, `HumanoidModelMixin`): the flight-feel limbs used `sin(ageInTicks * (0.16 + 0.22 * pace))`
for the leg scissor and `sin(ageInTicks * (0.11 + 0.2 * pace) + 0.7)` for the arm flutter; whenever the pace changed (take-off, setting off,
stopping) the phase jumped by `age x dPace` (tens of radians a tick once a player had been in the world a while): the limbs shook until the
pace settled. The phases are now integrated a tick at a time per player (`scissorPhase`, `flutterPhase`, interpolated), and the two
on/off gates (the hero-flight arm pin at 5 degrees of lean, the flutter at hover > 0.5) are ramps. Every flier with the shared feel gets it.

**Removed:** Calm (`calm`, its lang, HUD tag, pose, the attack block); `SentryState.calming` stays in saves, always false.

Key `sentry`. Hero-Tier Primary power (claims the Primary slot like every grant; `projectheroUniquePowers` applies).
Not from random serums (`PowerGrants.NOT_FROM_SERUMS`); the operator grant still works.

## Rework 2026-11-16 (user)

> "dont put the suit on automatically when the player gets the power, make the suit craftable and make it repair itself over time. Add
> this suit to the game as the sentry suit ... If the player is wearing the sentry suit and uses the let it out move make the players
> suit look like its slowly fading into this suit ... Remove the skin and hair from these suits ... Add a blue cape to the regular suit
> and a black cape to the void suit. Make his max hp 40. Make the void bar fill up a lot slower and make the let it out move a toggle
> ability ... minigame ... Make the sentry armour make void bar go up slower and sunlight recharged faster. When the void consumes the
> players body make the entire players body go black ... tiny glowing white pixels for eyes" / "Make double tap space bar his flight
> ability and make it the same for the void power. Make X just a dash ability for regular sentry."

| Item | What changed |
|---|---|
| Suit | The power draws nothing any more. **Sentry Suit** = `sentry_suit_top` (chest: body, arms, blue cape) + `sentry_suit_trousers` (legs). No helmet: face and hair always show. Anyone can wear it. |
| Recipe | Top: `B B / GHG / BGB`; trousers: `GHG / B B / B B` (B blue wool, G gold ingot, H hero alloy ingot). |
| Protection | Diamond-level: top 8, trousers 6, toughness 2.5, diamond durability (x33), fire-resistant item. |
| Self-repair | 1 durability every 60 ticks (3 s) of game time, worn or anywhere in the inventory (`SentrySuitItem.inventoryTick` -> `selfRepair`). Never needs an anvil (repair ingredient gold kept, harmless). |
| Suit bonus | Top + trousers on the Sentry: every Void gain x0.6, Sun per second x1.5. |
| Textures | From the two PlanetMinecraft skins (see Credits), head + hair + bare skin cleared. Held back from git (see Licence). |
| Capes | Blue on the Sentry suit, black on the Void suit, blending through the fade; flat black in the takeover. `SentryCapeLayer` on the shared `FlowingCapeLayer`; the wearer's own cape hidden (`GyroCoatCapeMixin`). |
| Let It Out fade | Wearing the suit, the comics suit dissolves into the Void suit over 50 ticks (2.5 s), per pixel outward from the heart, with a violet-white burning edge (glow pass); reverse when leaving. Without the suit, the original Void Form body fades in over the bare wearer. |
| Max health | 40 total in both forms (+20 on the base 20; was +30 / +40). |
| Void gains | Cut to about a third (table below); merged x1.5; suit x0.6. |
| Let It Out | Toggle with a trial (minigame); no Void gate any more. |
| Takeover look | Whole body black (head, skin, suit, armour, cape) with two 1-pixel white eyes drawn full bright. |
| Flight | Double-tap Space (both forms). X = Sonic Boom dash only (regular Sentry). Merged X = Shadow Step. |

## Getting it

**Sentry Serum** (`sentry_serum`, epic, stacks to 1, drink 32 ticks, leaves a bottle). Only source: the **O.X.E. serum vat**
(`oxe_serum_vat`, unbreakable, facing + `open`) in The Vault's vault room, `VaultBuilder.SERUM_VAT` = local (0, F3+1, 21),
on a gold plinth between the arena door and the three Dungeon Vaults. It opens with the Dungeon Vaults when Kross dies
(`VaultClearing.unsealVaults` -> `SerumVatBlock.open`), or when used with no Kross within 96 (marks the dungeon cleared, same
rule as the seals). Open, it gives **one serum per player** (`SerumVatBlockEntity` keeps the UUIDs). No recipe, no loot table.
The grant equips nothing (test `grantDoesNotEquipTheSuit`).

## Meters (0..100, synced in `SentryState`, attachment `projecthero:sentry_state`)

Every positive Void change goes through `Sentry.voidGainScale`: x0.6 with the suit, x1.5 merged (both: x0.9). The trial's loss (+15)
is flat. Daylight cooling, Calm and Healing Light are never scaled.

**HUD bars** (rework, user: "make the sun bar yellow and make the void bar fill up with a red and the more full the bar gets the darker
it becomes, make them both minecraft style bars"): neither is one of the four named styles any more (they were a Gauge and a Hairline);
both are a new **vanilla-style bevelled bar** (`SentryHud.bevelBar`, drawn with fills, crisp at every GUI scale), XP-bar-like: 1-px
near-black outline, dark trough with a recessed top row, a 5-px fill (light top row, body, a second lighter row, dark bottom row x0.62,
darker edge column), ten XP-bar notches. Sun: body #F5C518, top row x1.18 (pulsing up to x1.30 when full), number #FFE14D. Void: one
colour for the whole fill, by the fill: #E23030 at 0 -> #8E0F12 at 55% -> #3A0505 at 100%; the 50 / 75 / 90 notches a little stronger;
the top row beats past 90. Both bars line up (the wider label decides). Screenshot: `docs/screenshots/v0175/sentry2/hud_bars_void_10_50_95.png`.

| | Sun (yellow bar) | Void (red bar under it) | was |
|---|---|---|---|
| Direct sunlight | +6/s (suit +9) | -1/s (not merged) | |
| Daytime shade | +2/s (suit +3) | 0 | |
| Night, under the sky | +0.5/s (suit +0.75) | +0.08/s | 0.25 |
| Dark (no sky, block + sky light < 8; Nether/End) | 0 | +0.17/s | 0.5 |
| Calm (hold V) | | -6/s | |
| Merged with the Void | | +0.5/s of its own (then x1.5) | 1.0 |
| Heavy hit taken (>= 8 after the cut) | | +0.13 per point, max 2 a hit | 0.4, 6 |
| Kill (player) | | +0.7 (+2) | 2 (6) |
| Solar Gaze (moveset 2) | -5/s | +0.17/s | |
| A Million Suns | -60 | +8 | 25 |
| Disassemble coming apart | (10 to start) | +1.7 | |
| Golden Guardian | -20 | -8, -1 per other helped | |
| Molecular Shield | -15, +2 per projectile turned to light | | |
| Void moves (moveset 2) | | Lash +0.3 a swing, Erase +2, Pool +1, Swallow +1.7, Nightmare +1.7, Eclipse +3, Blot Out +1, Clones +2, Step +0.35 | |
| Let It Out trial lost | | +15 (flat) | (new) |

Sun-fed healing: 1 HP every 40 ticks while Sun > 0 (0.5 Sun each). Creative: no costs, the Void never climbs.
In practice: a night of fighting in the dark now takes well over 10 minutes to reach 100 without Calm; the takeover stays reachable
(A Million Suns spam at night, a lost trial or two, staying merged) but rare.

| Void | Effect |
|---|---|
| 50 | whispers (soul sand valley mood, every 10-25 s), black veins creep from the screen edges, the suit's emissive glow flickers dark (20-65% of frames) |
| 75 | 12% of Sentry attack presses (R, Shift+R, G, Shift+G, Z, Shift+Z) misfire into a Void move (moveset 2: R keys -> Tendril Lash, G keys -> Shadow Pool, Z keys -> Nightmare); client aim drifts up to ~0.8°/tick toward the nearest living thing within 12 ahead; veins thicker + branching |
| 90 | heartbeat (warden heartbeat every 24 ticks) + darkness pulse; client sky dims to 45%; every hit leaves a burnt shadow |
| 100 | the takeover (not creative / spectator; inside spawn protection it holds at 99) |

## Body

| | Sentry | Merged with the Void / takeover |
|---|---|---|
| Max health | 40 (+20) | 40 (+20) |
| Damage taken | -70% | -75% |
| Melee | +12 | +16 |
| Speed | +30% | +30% |
| Hits (`Sentry.hit`) | | x1.25 merged (not the takeover) |
| Void move cooldowns | | x0.75 merged (`VoidMoves.cooldownOf`; not the takeover's brain) |
| Extra cuts (moveset 2) | x0.5 while A Million Suns charges; x0.6 under the Molecular Shield | |

No hunger. No fall damage while flying, diving or in the takeover. (Kryptonian: -80%, punch 32, Solar Flare 120.)

## Sentry kit -- moveset 2 (2026-11-18; H = power selector only; N unused)

User: "Sentry: Create a new moveset based on the sentries powers in the movies and comics". Comics (Bob Reynolds: flying punches that
send foes through mountains, light projection, molecular disassembly, healing others, "a million exploding suns") and Thunderbolts*
(overwhelming strength, molecular disintegration / deflection, the golden aura). Code: `SentryMoves`; numbers `SentryConfig`.

| Key | Move (id) | Numbers |
|---|---|---|
| R | Golden Fury (`golden_fury`) | 3-punch combo (window 30 ticks): 22, 22, then the **Haymaker** 34; reach 4.5; a target 4.5-9 out (in sight) is closed on with a golden lunge (he is thrown at it, up to 2.4 b/t) and still hit; the Haymaker launches a liftable target 3.0 b/t flat along his look + 0.75 up, tracked 30 ticks: slamming into a wall (horizontal collision) = +12 and a ring; a Haymaker kill disintegrates into light; 8-tick cd; 2 Sun a punch |
| Hold Shift+R | Solar Gaze (`solar_gaze`) | twin beams from his eyes, 40 blocks, 7 every 4 ticks + 3 s fire; held 40+ ticks: x1.5 (10.5) and orange-hot; max 100 ticks; 60-tick cd when it ends; 5 Sun/s, +0.17 Void/s; `BeamClash.report(power 1.5, gold)` every live tick from the eyes; renderer checks `BeamClashClient.hides` |
| Hold G | Disassemble (`disassemble`) | a creature in sight within 20: lifted in a golden field 4 ahead (>= 300 HP or > 4 tall: pinned); unravels 2 every 5 ticks; held 40 ticks: comes apart, 50 (+1.7 Void); a kill turns it into golden motes (`DISINTEGRATE`); let go before: thrown 2.4 b/t, 12; max 100 ticks; 10 Sun; 80-tick cd when it ends |
| Shift+G | Skyward Hurl (`skyward_hurl`) | the creature in reach (6): 16 and thrown straight up 3.2 b/t (~40 blocks; the fall does the rest), comet trail; bosses (not liftable): 30 + Slowness III 3 s instead; 14 Sun; 200-tick cd |
| Z | Sunfall (`sunfall`) | on the ground: a leap (1.5 b/t up, up to 14 ticks, until it slows), then the dive; in the air: the dive at once (2.8 b/t, fist first, max 60 ticks); impact: 34 within 6 (+knock-up), 12 from 6 to 11 (the sonic-boom ring); no fall damage while it runs; 12 Sun; 140-tick cd |
| Shift+Z | A Million Suns (`million_suns`, id kept) | 80-tick wind-up (4 s) rising 0.12 b/t for 1 s then hanging; damage taken x0.5 meanwhile; then 150 at the centre -> 70 at r 14, knockback, 5 s fire; kills disintegrate; 60 Sun; +8 Void; 1200-tick cd |
| C | Golden Guardian (`golden_guardian`) | r 10: him, squadmates, own pets, villagers, golems: +8 HP, harmful effects cleared, fire out, Regeneration I 6 s, Absorption I 10 s, a pillar of light each; the undead (`isInvertedHealAndHarm`) in range: 12 + 3 s fire; -8 Void, -1 per other helped; 20 Sun; 240-tick cd |
| Shift+C | Molecular Shield (`molecular_shield`) | golden aura r 3.5 for 160 ticks (8 s): enemy projectiles reaching it are turned to light (discarded, +2 Sun each); damage taken x0.6 on top; melee from 5 or closer deflected (6 + knockback, once per hit window); 15 Sun; 400-tick cd |
| X | Sonic Boom (`sonic_dash`, kept) | 18 blocks along the look in 8 ticks, 14 to each thing passed once; ground or air; never starts flight; 5 Sun; 80-tick cd; blocked during Calm |
| 2x Space | Flight | unchanged (22 b/s, 44 sprinting) |
| Hold V | Calm | unchanged (-6 Void/s, no attacks) |
| Shift+V | Let It Out | unchanged (the trial) |

Poses (`SentryState.pose` / `poseAt` / `poseTicks`, synced; `SentryPose`): the Haymaker's drive, Hurl (arms up), the Sunfall leap and
fist-first dive, Guardian (arms spread, face up), Shield (arms crossed, flung open); held moves read their own state (gaze: chest out,
fists back; Disassemble: both hands clawed out, pulling apart; A Million Suns: arms rising, trembling at the end).
Sounds: vanilla only (beacon, amethyst, explosion, warden sonic boom, firework launch, wind charge).

Moveset 1 (removed 2026-11-18): Golden Strike, Sunbeam, Molecular Grip, Unmake, Sun Dive, Healing Light, Sentinel Shield. Their ids
(`golden_strike`, `sunbeam`, `molecular_grip`, `unmake`, `sun_dive`, `healing_light`, `sentinel_shield`) are gone from the code and
the lang; an old save's cooldown map may still hold them: they load, are never read (max cooldown 1) and expire on their own
(test `keysMapBothNewKits`). The state's save keys (`beam_start`, `grip_target`, `shield_until`, `nightfall_until`) are kept and reused.

## Let It Out: the trial (`LetItOut`, `LetItOutScreen`)

- Shift+V as the Sentry: `letOutTrialStart` set + synced; the owner's client opens **Hold the Light** (`SentryClient.openTrial`,
  once per trial). A dark shaft; a gold sun drifts up and down (half-height 0.18 of the shaft, minus 0.06 x Void/100; faster with Void);
  his light (a white bead) falls (-2.2/s²) unless Space or left mouse is held (+2.6/s²). Inside the sun the Light bar fills +26/s,
  outside it drains -12/s; starts at 30. Full = won, empty / 8 s / Esc = lost. Never pauses; text wraps (`font.split`).
- Server (`LetItOut.report`): only while a trial runs; a win counts only >= 50 ticks into it (30 -> 100 at 26/s takes 54), else it is
  a loss; no report by 160 + 100 ticks: lost (`LetItOut.tick`). Death / world change / logout clear a running trial.
- **Lost:** Void +15 (flat), nothing else. **Won:** merged with the Void (`Sentry.setVoidForm`): the Void Form kit + flight, +25%
  damage, Void move cooldowns x0.75, Void gains x1.5 + 0.5/s.
- **Back:** Shift+V (or V) while merged, only below 90 Void (`VoidMoves.returnToSentry`). The old 50-Void gate is gone.

## The Void's kit -- moveset 2 (merged, and the takeover; no Sun cost)

User: "also create a new moveset for the void". Comics (shadow tendrils, mind horror, nightmare manifestations, absorbing people into
darkness, regeneration) and Thunderbolts* (people turned into shadow imprints, the shadow spreading over surfaces, the black void spaces).
Code: `VoidMoves` (running effects are `Zone`s: owner UUID + dimension key + position + entity id, never a world or an entity; cleared by
`SentryMod.clearSessionState` from `ServerStateReset`).

| Key | Move (id) | Numbers (before the merged +25% damage / x0.75 cooldowns) |
|---|---|---|
| R | Tendril Lash (`tendril_lash`) | combo (window 30): two whips, 16 to all in a 100° cone out to 6 (alternating sweeps); the 3rd is the **Impale**: one creature in line within 9 (else the nearest in front), 30, Slowness IV 1 s, dragged in; 10-tick cd; +0.3 Void a swing. The takeover spears (Impale) whatever is out past 6 |
| Shift+R | Erase (`erase`, kept) | the looked-at creature within 24 is seized (frozen, darkening) for 15 ticks, then 110 void damage; a kill leaves a burnt shadow imprint; 400-tick cd; +2 Void |
| G | Shadow Pool (`shadow_pool`) | on the ground where he looks (24; the takeover: under the target): radius 2 -> 6 over 40 ticks, 120 ticks; inside (-1..+2 of it): Slowness IV, no jumping (y velocity held down), dragged; 4 every 10 ticks; 240-tick cd; +1 Void |
| Shift+G | Swallow (`void_sphere`) | the creature in sight within 16 is shut in a black void sphere for 60 ticks: held in place (bosses slowed), Blindness + Darkness, 6 every 10 ticks, then spat out (18 + knockback); dying inside = absorbed (+6 HP); 360-tick cd; +1.7 Void |
| Z | Nightmare (`nightmare`) | r 12: 8 each, Weakness I 5 s; players: Darkness + Nausea + Slowness I 5 s; mobs: drop their target and flee for 100 ticks (repathed every 10 ticks 10 blocks away); 2-4 shadow figures stand among them (`SHADE`, 2 s); 400-tick cd; +1.7 Void |
| Shift+Z | Shadow Eclipse (`shadow_eclipse`) | 30-tick wind-up (he rises 0.12 b/t), then a black sun with a gold corona 7 above him for 200 ticks: within 16 of him 6 every 20 ticks + Darkness, he heals 2 every 20; at the end the collapse: 40 to all inside, pulled in; 1200-tick cd; +3 Void; the HUD tag "Eclipse" (`nightfallUntil`, save key kept) |
| C | Blot Out (`blot_out`) | r 10: 10 each + Darkness 2 s, life-drain tendrils to him; he heals 3 per one drained (max 15) + all his remaining Sun burns into health (1 per 5 Sun; Sun -> 0); 300-tick cd; +1 Void |
| Shift+C | Shadow Clones (`shadow_clones`) | 3 shadow doubles for 160 ticks, each hunting the nearest enemy within 16 of him at 0.35 b/t, striking 10 every 20 ticks within 2.4; drawn as black figures with two white pixel eyes (`SHADE`, re-sent every 2 ticks); 600-tick cd; +2 Void |
| X | Shadow Step (`shadow_step`, kept, reworked) | a blink up to 24 along the look, stopping 0.8 short of a wall (backs off 0.5 at a time until the body fits); no light rule any more; everything within 1.5 of the path: 10 + Darkness 1.5 s; a smear of shadow; 60-tick cd; +0.35 Void |
| 2x Space | Flight | as the Sentry's |
| V / Shift+V | Return | only below 90 Void |

Poses: Erase (hand out, fingers spread, trembling), Pool (pointing down), Swallow (a fist closing), Nightmare (arms flung wide, head back),
Eclipse (arms raised in a V), Blot Out (arms out, drawing in), Clones (palms out low), Impale (a thrust); they also play in the takeover
(the puppet pose otherwise). Sounds: vanilla (warden, sculk, elder guardian curse, portal, enderman).

Moveset 1 (removed): Shadow Lash, Grasp, Void Rift, Nightfall (ids `shadow_lash`, `grasp`, `void_rift`, `nightfall`); the payload kinds
`RIFT` / `NIGHTFALL` stay drawable (never sent).

## Effects (client, `SentryFxClient`)

New `SentryFxPayload` kinds (moveset 2): `DISINTEGRATE` (48 golden motes rising off the body's box, its outline of light thinning),
`POOL` (concentric dark rings spreading on the ground, violet rim, 7 tendrils writhing up), `SPHERE` (a camera-facing black disc over the
creature, gold / violet rings), `NIGHTMARE` (a dark wave ring with radial streaks), `ECLIPSE` (the dark gathering in, then a black disc
7 above him with a 3-ring gold corona and 16 flickering rays, a faint dark dome), `SHADE` (a shadow figure: legs mid-stride, reaching
arms, a wisp off the head, white pixel eyes), `PILLAR` (Golden Guardian's light on each one helped), `COMET` (the Haymaker / Hurl trail
behind the flying creature). The gaze is drawn from the synced `beamStart` (two beams from the eyes converging on the hit, an eye glint,
wider and orange past the ramp).

## The look (`SentrySuitLayer`, `SentrySuitTextures`, `SentryCapeLayer`)

- Modes: **NONE** (no suit, not merged), **SUIT** (any suit piece: top draws body + arms + sleeves, trousers the legs + trousers layer;
  head never), **BARE** (merged without the suit: the original Void Form body, head included, fades in), **BLACK** (the takeover).
- Fade: `voidFade` = (now - `formChangedAt`) / 50 ticks, rising when merged, falling when not (the takeover end also stamps it, so the
  suit fades back). 17 steps; step k is a 64x64 `DynamicTexture`: each texel flips from look A to B when its order < k/16; order =
  0.75 x (distance on the model from the chest front, x 0.5, y 4 px) / max + 0.25 x hash noise, overlay texels 0.6 px later than the
  base under them. Texels flipped in the latest step glow violet-white (ABGR 0xFFFF9AC8) in the emissive pass.
- Glow: the Sentry suit's glow flickers dark from 50 Void; past half-way through the fade the Void's slow pulse. Takeover eyes steady.
- Cape: blue -> black in 9 steps by `voidFade`; takeover: flat near-black.
- Outer skin layers (`PlayerModelMixin` -> `SentrySuitLayer.hideOverlay`): the top hides jacket + sleeves, the trousers the trouser
  layer; BARE and BLACK hide everything incl. the hat. Armour: `QuirkSuitArmourMixin` skips Sentry Suit pieces always, and every armour
  piece on a body in the takeover.
- First person (`SentrySuitLayer.renderArm` from `SuitFirstPersonArmMixin`; 2026-11-18 user: "update the first person hand models to match
  the new suits"): the arm matches third person -- no suit / no Void: the normal arm; the suit top: the comics sleeve (the bare hand shows the
  wearer's own skin through the cleared texels, the wearer's outer sleeve hidden by `hideOverlay` via the hand's `setupAnim`); merged
  in the suit: the same per-pixel dissolve into the Void suit sleeve (now with the frame's partial tick, so it is smooth); merged without
  the suit: the Void Form body's arm; the takeover: all black (the takeover forces third person, so it is never seen). Slim / wide each
  have their own textures (checked on a slim dev skin). Stills: `docs/screenshots/v0175/sentry_moves/fp*.png`, `first_person_arms_sheet.png`.

## Credits and licence (borrowed skins)

| File | Source | Credit / licence |
|---|---|---|
| `docs/reference/sentry/sentry_comics.png` | https://www.planetminecraft.com/skin/the-sentry-comics/ | Gillygogs (shading), ATROX (original Sentry skin). Page: "Ask me before using it in a heropack or addon" -> **ask before a public release** |
| `docs/reference/sentry/sentry_void.png` | https://www.planetminecraft.com/skin/the-sentry-merged-with-the-void/ | licence not checked: check / ask before a public release |

Also in `docs/reference/sentry/SOURCE.txt`. **Held back** like the Gyro suit skin (v0.17.4): the processed textures
`textures/entity/sentry/sentry_comics_*.png` and `sentry_void_*.png` are in `.gitignore`, so CI / release builds don't contain them, and
`SentrySuitTextures` falls back on its own to the original art (`sentry_suit*.png` for the suit, `void_form*.png` with the head cleared
for the Void suit). Local builds have them after `node scratchpad/sentry/make_suit_textures.js` (run it in the checkout you build from).
To ship them once permission is given: remove the two `.gitignore` lines, commit the PNGs, add the credit to the CurseForge description.

Processing (`make_suit_textures.js`): head + hat rows 0-15 cleared (no mask on either skin: 463 / 465 texels); skin tones cleared on
body / arms / legs (hue 5-35°, saturation 0.12-0.62, value >= 0.30, not saturated gold: 92 / 96 texels, the bare hands); arm-side rows
that were mostly skin cleared whole (the hands' highlights: 4 / 0); slim copies by dropping each 4-wide arm face's most-alike middle
column; glow = near-white and palest-gold texels (17 / 17: the emblem). Check sheets: `docs/screenshots/v0175/sentry2/suit_texture_preview.png`
(front / back over a plain test skin) and `skin_processing_diff.png` (source | result, cleared texels magenta), made by
`scratchpad/sentry/diff_sheet.js`.

## The takeover (`VoidTakeover`)

- Starts at 100 Void: Void Form, flying (mayfly), Void held at 100, keys dead (`SentryAbilityManager`), own attacks / uses /
  block breaking refused (`SentryMod` callbacks). Others within 96 get a chat warning. A running trial is dropped.
- **Targets everything**: `canHit = HeroTargets.canHarm(null, target)` minus the body (squadmates, pets, villagers, players;
  never spectators / creative / armour stands). `Squads.areAllies`, `shields` and `protectsPet` are broken for a body in the
  takeover, both ways, like a rampaging Hulk. Server PvP off still stops player damage (vanilla).
- Brain (rework 2, 2026-11-17, user: "he destroys some blocks and then flies down into his own hole ... gets stuck in his own hole ...
  make it so that the void lifts up into the air (50 blocks above the ground) and looks down on the destruction ... only he goes down
  slowly to the ground start fighting players and strong enemies"). Two modes (`VoidTakeover.Mode`):
  - **HOVER** (no strong target): rises to the original ground under it + 50 (`TAKEOVER_HOVER_HEIGHT`; the disc's column record is
    used, so the crater doesn't pull it down; clamped to build height - 3), up at most 0.6 b/t easing in over the last ~10 blocks
    (~5-6 s from the ground). It orbits 3 blocks around a hover point at 0.012 rad/tick and turns 0.35°/tick, pitched ~58-61° down.
    The hover point drifts 0.02 b/t (0.4 b/s) toward the nearest player within 128, else a village meeting POI within 128, else a
    random 24-block heading (re-picked every 200 ticks).
  - **ENGAGE**: retarget every 10 ticks -- the last helper (60 ticks) first, else the nearest **strong** target within 64 horizontally
    (80 below / 40 above; kept to x1.25 of that). **Strong** (`isStrong`) = any player (not creative), `#c:bosses`, max health >= 40
    or armour >= 15. It closes to 3 blocks: horizontal 0.55 b/t, down at most 0.25 b/t (`TAKEOVER_DESCEND_SPEED`: from 50 up, ~10 s),
    up 0.6. Rise first: when the highest ground on the straight line to the target is above the body and the target stands up on it
    (`climbHeight`), it climbs to that height + 1.5 with only 0.08 b/t across.
    **Attack pace (moveset 2, user: "when the void takes control only make him attack once every 2 seconds with an ability or an M1"):**
    one shared cooldown, `TAKEOVER_ATTACK_INTERVAL` = 40 ticks, for every attack -- a Void move or a fist (18 within 3.5) alike
    (`Brain.nextAttack`; the times are kept for the tests, `attackTimes`). Up close it alternates a fist and a move; out of reach it
    waits for a move. Moves (`pickMove`), each also on its own takeover cooldown: Shadow Eclipse (1200; 30 s into the takeover, 3+
    victims within 12 or a player / boss target, within 11), Blot Out (300; under 70% health, within 10), Erase (240; in sight, <= 20),
    Shadow Clones (600; 2+ victims within 12), Nightmare (400; 2+ victims, <= 12), Tendril Lash (40; <= 9 -- the Impale past 6), Swallow
    (300; 4..16 in sight), Shadow Pool (200; <= 24, target on the ground). Not attacks (unchanged): the decay disc, the weak-mob
    consumption in it (6 a second), the carving, and the ticking of a running effect (a pool / sphere / eclipse / clones already cast).
    No strong target left: back to HOVER (rises again).
  - **Never stuck** (`steer`, direct steering flight, no gravity, no pathing): every 4 ticks, if the body's box moved 1 block along its
    velocity collides, those blocks are carved (`VoidMatter.carveBox`, <= 32, the fast fade); if it hasn't moved 0.6 blocks for 20
    ticks while it wants to move (> 0.08 b/t), a 2.5-radius sphere 1.2 blocks toward where it wants to go is carved (<= 96, every 10
    ticks while still stuck). Carved blocks are recorded like the rest.
  - **Weak mobs** inside the disc (within its radius horizontally, from 14 under the original ground up to the body) take 6 void
    damage every second (`consumeWeak`, <= 24 a second); kills are absorbed (+4 HP).
- **World consumption** (`VoidMatter`, rework 2): a **disc** on the ground under the body, radius 8 + 0.4/s, max 56 (reached after
  120 s; ~9,850 columns). Each tick 160 column tries (55% in the 5-block band at the edge, 45% uniform over the area), at most 48
  blocks start. A column goes from its top (trees, plants, builds) down to its original ground (first solid non-log, non-leaf,
  non-plant block from the top, cached per world) minus a depth of 3 at the edge to 10 under the centre (quadratic bowl,
  `depthAt`); water/lava stops a column (no air pockets under the sea); finished columns are remembered with their depth. Never:
  bedrock & unbreakables (barriers too), any block entity (all containers skipped), fluids, portals/frames, spawn protection,
  `!mayInteract`, unloaded chunks.
- **The decay** (rework 2, user: "make it so that the blocks slowly fade to black before dissolving quickly"): a block is recorded,
  marked decaying (per-world, not saved) and stays real while it fades: 40 ticks + 0..10 by position (`decayTicks`, the same hash on
  both sides) for the disc, 14 + 0..3 for carved blocks. One `VoidDecayPayload` batch a tick (packed longs, <= 2048 a packet) goes to
  players within 192. Then the server sets air (no neighbour updates: torches/sand stay, water doesn't flow in). Client
  (`VoidDecayClient`): a black shell (faint violet, smoothstep alpha to 96%) over the open faces of each block (faces rechecked every
  4 ticks, not every frame), one translucent POSITION_COLOR buffer, <= 12,000 shells within 160 blocks a frame; when the block turns
  to air the shell stays black and shrinks away in 6 ticks with a puff of ink + smoke (<= 48 bursts a tick, within 64). The old
  `void_matter` block stays registered (old worlds) but is never placed.
- **Restore**: per-dimension SavedData `projecthero_void_matter`, now a palette file (`palette`, `owners`, `pos` / `st` / `own` / `due`
  arrays; the first v0.17.5 per-entry list still loads). On end, the owner's entries are due in 1200 ticks and join a due-ordered
  queue; 64 a tick come back from its front into air / void matter / replaceable / still-fading only; an entry whose chunk isn't
  loaded, or whose space a creature stands in (it would be buried), goes to the back and waits (<= 256 looked at a tick). Orphans (owner offline or not in a takeover: a crash) are adopted every
  100 ticks. `projectheroVoidGriefing` (default false): nothing recorded, nothing returns (entries of that owner are dropped at end).
- **Cost** (`VoidMatter.tickLevel`): disc + carve + fades + batches + restore are timed per world; every 200 busy ticks one log line
  "[void] <dim> decay work: avg / max ms a tick ..." (INFO; WARN with "[watchdog]" if a tick passed 5 ms); the world tick is also
  wrapped in `TickWatchdog`. Measured in the harness (normal world, render distance 8): while eating (disc + carve + fades + batches) avg 0.24-0.50 ms a tick per 200-tick window, max 0.7-5 ms in most windows, one-off spikes of 9.9-11.8 ms (a window at the takeover start and the one where it ends); the restore 0.11-0.32 ms avg (max 2.3 ms) at 64 blocks a tick; ~41,000 blocks eaten in ~90 s, all back in ~650 ticks.
- **Sky** (client only): `VoidTakeoverClient.skyStrength` = full within 24 of a takeover, gone at 72; sky + fog colour to
  near-black, fog in to 40 blocks; >= 0.5 the client's day time reads as midnight (`SentryMidnightMixin`). 0.45 around
  a Sentry past 90 Void (no midnight). World time never changes.
- **View** (owner's client): forced third person (restored after), movement/jump/sprint/sneak/attack/use keys released
  every tick, body faces the synced `takeoverTarget`, black bleed + gold rim, "THE VOID HAS YOU", countdown, rooms/grip. Rework 2:
  with no target the body turns 0.35°/tick and looks ~58° down; the camera distance eases (x0.05 a tick) to 14 blocks while it hangs
  high (no target, or 16+ above the ground) and 7 while it is down fighting (`VoidTakeoverCameraMixin` on `Camera.getMaxZoom`).
- Tests (`VoidTakeoverGameTests`, rework 2): blocks fade (decay state) before air and come back, a carved 3x3 patch comes back, the
  radius grows exactly 0.4 a second and caps, the depth is a bowl, the body rises toward its hover height (at most 0.6 a tick, smooth
  steps), comes down (<= 0.25 a tick) for a 500-HP zombie and not for a 20-HP one (which the destruction hurts instead), decays its
  way out of a stone cell, and the gamerule still records nothing. Bodies in tests carry `VoidTakeover.TEST_NO_DISC` (no disc / weak
  consumption: parallel tests share the world). Films: `scratchpad/VoidTakeoverHarness.v0175.java.txt` ->
  `docs/screenshots/v0175/void_takeover2/` (`node scratchpad/sentry/films_void2.js`).

## Getting control back

1. Nothing for 600 ticks (`mindLockedUntil`).
2. **Shame Rooms** (`ShameRoomsScreen`, client; `ShameRooms`, server). 3 rooms, 45 s each. Light bar starts 30; a light
   click +9/(1+0.15·room); a darkness shape touching the cursor drains 22·d/s. Lights: max(3, 6-room), speed (25..45)·d px/s.
   Shapes: 2 + room + floor(d), speed (40..65)·d px/s. d = (1 + 0.32·room) · (1 + min(1, minutes·0.25)) · (0.6 + 0.4·grip/100).
   Server accepts a win only for the current room, with the mind open, >= 80 ticks into the room and >= 5 clicks; a loss:
   room - 1 (min 0) and the mind shut 400 ticks more. Esc closes it; it reopens after 5 s.
3. **Outside help**: hold right click on the body with an empty hand within 6 (`UseEntityCallback`): grip -2 per
   interaction (x1.2 by day, x0.8 at night); the Void targets the helper. Grip regains +0.25/s by day, +1/s at night.
   Grip 0 -> the current room breaks open, grip back to 60.
- **Win** (room 3): golden flash (`THROWN_OUT` fx), title, kneel 60 ticks, Void 40, Shift+V locked 6000 ticks.
- **Death**: ends it (`AFTER_DEATH`), Void 60. **Logout / dimension change / server stop**: ends it (stop: on next join),
  Void 60, world scheduled back.

## Files outside the packages

`ProjectHeroMod`, `AbilityRouter` (context after the Hulk + tick), `HeroTiers` (4), `PowerGrants` (4), `HeroCommand`,
`ProjectHeroCommand`, `ServerStateReset`, `ModCreativeTab`, `HeroIdentity`, `Squads` (takeover breaks squad protection),
`SupplyTargetDefaults` (energy cell -> Sun; cooldown reset), `VaultBuilder` (vat), `VaultClearing` (opens the vat),
`HeroPackGuide` (new chapter `CH_SENTRY` = 79, power chapters now start at 80; `SENTRY_CONTROLS`, `VOID_CONTROLS`; rework: + the
`suit` and `trial` sections, X / 2x Space rows).
Client: `ProjectHeroModClient` (rework: double-tap Space -> `SentryClient.toggleFlight`), `DirectionalFlight`, `FlightFeel`,
`FlightPoseHelper`, `PowerInfoScreen`, `SquadScreen`, `PlayerModelMixin` (rework: `hideOverlay`), `SuitFirstPersonArmMixin`,
`HumanoidModelMixin`, `ClientLevelMixin`, `FogRendererMixin`, new `SentryMidnightMixin`, `ProjectHeroJeiPlugin` (rework: suit info
pages), rework: `QuirkSuitArmourMixin` (Sentry Suit + takeover armour), `GyroCoatCapeMixin` (Sentry cape hides the player cape). Takeover rework 2: `FogRendererMixin` (the black fog end 40 -> 112), new `VoidTakeoverCameraMixin` (+ `projecthero.client.mixins.json`), `SentryMod` registers `VoidDecayPayload`.
Resources (rework): `recipe/sentry_suit_top.json`, `recipe/sentry_suit_trousers.json`, item models + icons, the cape / body textures,
`.gitignore` (held-back skins). Gametests: chapter count 79 -> 80 in `HeroPackGameTests` and `MiraculousTurtleHorseGameTests`.

Moveset 2 (2026-11-18): only `HeroPackGuide` (`SENTRY_CONTROLS`, `VOID_CONTROLS`) and `en_us.json` (keys by script) outside the two
packages, plus the two test classes; the HUD key boxes, the Left-Alt move list and the power info screen read `SentryAbilityManager` /
the Guidebook chapter, so they follow on their own.

Round 3 (2026-11-18): `HeroLandingStyle` (+ SENTRY 50, SENTRY_VOID 51), `HeroLandingStyles` (registration), `HeroLandingFx` (their signature +
`SentryMoves.landing`), `HeroPackGuide` (`SENTRY_CONTROLS` / `VOID_CONTROLS`), `en_us.json` (by script); client: `ProjectHeroModClient` (H -> Let It Out,
Sentry in the Thor-armour Shift+H list), `DirectionalFlight` (the boost), `FlightPoseHelper` + `HumanoidModelMixin` (the flight shake fix, all fliers),
`VoidTakeoverClient` (no forced third person while viewing through another entity), `void_body.png` / `void_body_glow.png` regenerated (no eye texels),
`scratchpad/sentry/make_suit_textures.js`.

## Static state

Round 3: `SentryCalmTasks.SEEN` (UUID -> stat values) cleared from `Sentry.clearSessionState` / `forget`; `VoidMoves.NEXT_ID` is a plain counter. Client:
`ShadeFigures` (clone id -> positions, nightmare figures, one model, one texture) cleared on disconnect.
All session maps key by player UUID only (combo, gaze / Disassemble keys, Sunfall, the launched creature (an entity id), Tendril Lash's combo, dash hits, flight start / last landing, brains, zones store
UUID + dimension key + Vec3 + an entity id); cleared by `SentryMod.clearSessionState` from `ServerStateReset`. The eaten world is SavedData, never
static. Client caches (`SentrySuitTextures`: dissolve steps, capes) hold only textures and are released on every resource reload.
