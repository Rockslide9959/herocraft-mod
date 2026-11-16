# Sentry & the Void (v0.17.5) — reference

Design: `docs/SENTRY_VOID_DESIGN.md` (approved 2026-11-15, reworked 2026-11-16). Code: `com.projecthero.mod.sentry` (server/common),
`com.projecthero.mod.client.sentry` (client). Every number lives in `SentryConfig`. Tests: `SentryGameTests`,
`VoidTakeoverGameTests`. Assets: `node scratchpad/gen_sentry_v0175.js` (the original pixel art: serum, vat, blocks, the original
suit / Void Form textures); `node scratchpad/sentry/make_suit_textures.js` (the rework: suit textures from the two borrowed skins, capes,
the takeover body, suit icons, preview sheet). Lang: `node scratchpad/lang_sentry_v0175.js`, then
`node scratchpad/sentry/lang_sentry_rework_v0175.js` (key by key). Films/screens: `scratchpad/SentryHarness.v0175.java.txt` ->
`docs/screenshots/v0175/sentry/`; rework: `scratchpad/SentryHarness2.v0175.java.txt` -> `docs/screenshots/v0175/sentry2/`.

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

| | Sun (gold **Gauge**) | Void (black **Hairline** under it) | was |
|---|---|---|---|
| Direct sunlight | +6/s (suit +9) | -1/s (not merged) | |
| Daytime shade | +2/s (suit +3) | 0 | |
| Night, under the sky | +0.5/s (suit +0.75) | +0.08/s | 0.25 |
| Dark (no sky, block + sky light < 8; Nether/End) | 0 | +0.17/s | 0.5 |
| Calm (hold V) | | -6/s | |
| Merged with the Void | | +0.5/s of its own (then x1.5) | 1.0 |
| Heavy hit taken (>= 8 after the cut) | | +0.13 per point, max 2 a hit | 0.4, 6 |
| Kill (player) | | +0.7 (+2) | 2 (6) |
| Sunbeam | -5/s | +0.17/s | 0.5 |
| A Million Suns | -60 | +8 | 25 |
| Molecular Grip tear | (grip 10) | +1.7 | 5 |
| Unmake | -8 | +1 | 3 |
| Healing Light | -20 | -8, -1 per other healed | |
| Void Form moves | | Lash +0.35, Erase +2, Grasp +0.7, Rift +1.7, Nightfall +2, Step +0.35 | 1/6/2/5/6/1 |
| Let It Out trial lost | | +15 (flat) | (new) |

Sun-fed healing: 1 HP every 40 ticks while Sun > 0 (0.5 Sun each). Creative: no costs, the Void never climbs.
In practice: a night of fighting in the dark now takes well over 10 minutes to reach 100 without Calm; the takeover stays reachable
(A Million Suns spam at night, a lost trial or two, staying merged) but rare.

| Void | Effect |
|---|---|
| 50 | whispers (soul sand valley mood, every 10-25 s), black veins creep from the screen edges, the suit's emissive glow flickers dark (20-65% of frames) |
| 75 | 12% of Sentry attack presses (R, Shift+R, G, Z, Shift+Z) misfire into a Void move (Lash / Grasp / Rift); client aim drifts up to ~0.8°/tick toward the nearest living thing within 12 ahead; veins thicker + branching |
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

No hunger. No fall damage while flying, diving or in the takeover. (Kryptonian: -80%, punch 32, Solar Flare 120.)

## Sentry kit (H = power selector only; N unused)

| Key | Move | Numbers |
|---|---|---|
| R | Golden Strike | 24, 3rd hit 30 + shockwave 14 (r 4.5); reach 4.5; 8-tick cd; combo window 30 ticks; 2 Sun |
| Hold Shift+R | Sunbeam | 6 every 4 ticks + 2 s fire, 32 blocks, max 120 ticks, 60-tick cd after; 5 Sun/s; `BeamClash.report(power 1.4, gold)` every tick; renderer checks `BeamClashClient.hides` |
| G (hold) | Molecular Grip | lift within 20 (held 4 blocks ahead); release = throw (14, 2.4 b/t); hold 30 ticks = tear 55; max 100 ticks; 10 Sun; 80-tick cd. Bosses (>= 300 HP or > 4 tall) are held in place, not lifted |
| Shift+G | Unmake | 3x3 on the looked-at face (24), into light, no drops; never bedrock / unbreakable / block entities / fluids / portals; respects `abilityTerrainDamage` and `mayInteract`; 8 Sun; 40-tick cd |
| Z | Sun Dive | air only; 2.6 b/t dive (max 60 ticks), crater 32 in r 5; 12 Sun; 120-tick cd |
| Shift+Z | A Million Suns | 60-tick charge (hovers), then 140 at centre -> 70 at r 12, knockback, 4 s fire; 60 Sun; 1200-tick cd |
| C | Healing Light | r 10: self, squadmates, own pets, villagers, golems: +8 HP, harmful effects cleared; 20 Sun; 240-tick cd |
| Shift+C | Sentinel Shield | r 5 dome, 120 ticks, discards non-friendly projectiles; 15 Sun; 400-tick cd |
| X | Sonic Boom | 18 blocks along the look in 8 ticks, 14 to each thing passed once; ground or air; never starts flight; 5 Sun; 80-tick cd; blocked during Calm |
| 2x Space | Flight | toggle (`SentryActionPayload.TOGGLE_FLIGHT` from `ProjectHeroModClient.handleDoubleJump`, in the air or flying), 22 b/s (44 sprint), Space up / Shift down (shared `DirectionalFlight`), vanilla mayfly; ends on landing or another double-tap; a take-off within 6 ticks of a landing is ignored (vanilla's own double-tap can drop flight the same tick) |
| Hold V | Calm | -6 Void/s, no attacks (moves + melee blocked), floats |
| Shift+V | Let It Out | the trial (below); locked 5 min after a takeover is thrown out |

## Let It Out: the trial (`LetItOut`, `LetItOutScreen`)

- Shift+V as the Sentry: `letOutTrialStart` set + synced; the owner's client opens **Hold the Light** (`SentryClient.openTrial`,
  once per trial). A dark shaft; a gold sun drifts up and down (half-height 0.16 of the shaft, minus 0.06 x Void/100; faster with Void);
  his light (a white bead) falls (-2.2/s²) unless Space or left mouse is held (+2.6/s²). Inside the sun the Light bar fills +22/s,
  outside it drains -18/s; starts at 30. Full = won, empty / 8 s / Esc = lost. Never pauses; text wraps (`font.split`).
- Server (`LetItOut.report`): only while a trial runs; a win counts only >= 60 ticks into it (30 -> 100 at 22/s takes 64), else it is
  a loss; no report by 160 + 100 ticks: lost (`LetItOut.tick`). Death / world change / logout clear a running trial.
- **Lost:** Void +15 (flat), nothing else. **Won:** merged with the Void (`Sentry.setVoidForm`): the Void Form kit + flight, +25%
  damage, Void move cooldowns x0.75, Void gains x1.5 + 0.5/s.
- **Back:** Shift+V (or V) while merged, only below 90 Void (`VoidMoves.returnToSentry`). The old 50-Void gate is gone.

## Void Form kit (merged; no Sun cost)

| Key | Move | Numbers (before the merged +25% / x0.75 cooldowns) |
|---|---|---|
| R | Shadow Lash | 20 to all in a 100° cone out to 6; 12-tick cd |
| Shift+R | Erase | 100 to the looked-at creature (24); a kill leaves a burnt shadow; 400-tick cd |
| G | Grasp | up to 5 within 14 dragged in, 6 each; 120-tick cd |
| Z | Void Rift | black hole at the look point (24) for 80 ticks, pulls within 7, 6 every 10 ticks; 360-tick cd |
| Shift+Z | Nightfall | dome r 10 for 200 ticks: enemies Darkness + Weakness (+ short Blindness), you Regeneration II; 800-tick cd |
| X | Shadow Step | teleport to a looked-at spot (40) with raw brightness <= 7; 60-tick cd |
| 2x Space | Flight | as the Sentry's |
| V / Shift+V | Return | only below 90 Void |
| C | — | |

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
- First person: the sleeve draws while the top is worn (or merged bare / the takeover).

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
- Brain: retarget every 10 ticks — the last helper (60 ticks) first, else the nearest living within 24. Flies to 3 blocks
  (0.55 b/t). Fists 18 every 10 ticks within 3.5. Moves by range on its own cooldowns: Lash (<= 6, 16 ticks), Erase
  (<= 20 + sight, 240), Grasp (6..14, 100), Rift (<= 24, 300), Nightfall (2+ victims in 10, 600). Nothing near: toward the
  nearest player within 128, else a village meeting POI within 128, else a random 24-block heading (every 100 ticks),
  hovering 4 above the ground. Kills are absorbed: +4 HP.
- **World consumption** (`VoidMatter`): ring radius 3 + 0.25/s, max 16; up to 8 blocks a tick (24 tries) on the surface
  under the ring (within 10 of the body's height). Block -> `void_matter` (records first) -> air after 30-40 ticks
  (scheduled tick, no neighbour shape updates so torches/flowers don't pop). Never: bedrock & unbreakables, any block
  entity (all containers skipped), fluids, portals/frames, spawn protection, `!mayInteract`, unloaded chunks.
- **Restore**: per-dimension SavedData `projecthero_void_matter` (pos, block state, owner, dueAt). On end, the owner's
  entries are due in 1200 ticks; 24 a tick come back into air / void matter / replaceable only, loaded chunks only (an
  unloaded one waits). Orphans (owner offline or not in a takeover: a crash) are adopted every 100 ticks.
  `projectheroVoidGriefing` (default false): nothing recorded, nothing returns (entries of that owner are dropped at end).
- **Sky** (client only): `VoidTakeoverClient.skyStrength` = full within 24 of a takeover, gone at 72; sky + fog colour to
  near-black, fog in to 40 blocks; >= 0.5 the client's day time reads as midnight (`SentryMidnightMixin`). 0.45 around
  a Sentry past 90 Void (no midnight). World time never changes.
- **View** (owner's client): forced third person (restored after), movement/jump/sprint/sneak/attack/use keys released
  every tick, body faces the synced `takeoverTarget`, black bleed + gold rim, "THE VOID HAS YOU", countdown, rooms/grip.

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
pages), rework: `QuirkSuitArmourMixin` (Sentry Suit + takeover armour), `GyroCoatCapeMixin` (Sentry cape hides the player cape).
Resources (rework): `recipe/sentry_suit_top.json`, `recipe/sentry_suit_trousers.json`, item models + icons, the cape / body textures,
`.gitignore` (held-back skins). Gametests: chapter count 79 -> 80 in `HeroPackGameTests` and `MiraculousTurtleHorseGameTests`.

## Static state

All session maps key by player UUID only (combo, beam / grip keys, dives, dash hits, flight start / last landing, brains, zones store
UUID + dimension key + Vec3); cleared by `SentryMod.clearSessionState` from `ServerStateReset`. The eaten world is SavedData, never
static. Client caches (`SentrySuitTextures`: dissolve steps, capes) hold only textures and are released on every resource reload.
