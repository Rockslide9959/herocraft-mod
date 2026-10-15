# Hulk v0.16.4 revamp — shared contract

Three agents build this in parallel worktrees; the lead merges them. This file is the contract between them.
All new code: `// v0.16.4 (user): ...` comments, tabs, match the surrounding style.

## The user's request (verbatim excerpts, with the user's answers)

- Rampage: guaranteed every 1 minute while transformed. Warning first: a randomly generated string of arrow-key
  directions on screen; match it with the arrow keys within 10 s (a wrong arrow restarts the string). Success = no
  rampage that minute. Fail/timeout = rampage.
- Rampage: the Hulk attacks EVERY creature within 100 blocks, squad members included; he must attack creatures much more
  and smash far fewer blocks than today. Regain control with a minigame: a circle with a ring closing in; press Space
  when the ring matches the circle, 3 times in a row (a miss resets the streak).
- He can rampage during all transformations (i.e. whichever way he became the Hulk).
- Transformations: ONLY (a) automatically when the player dies in base form (every time, no limit) and (b) manually by
  HOLDING H. Nothing else transforms him (no rage-triggered change, no 50-rage tap).
- Remove calming down entirely (the calm minigame / HulkCalm / HulkCalmScreen go).
- Rage bar only in Hulk form (not in base form). Rage at 0 never detransforms. He can stay transformed indefinitely.
  He cannot detransform for 3 minutes after transforming. After a WILLING (hold-H) transform, detransforming puts the
  Hulk form on a 1-minute cooldown — but dying during that cooldown still forces the change.
- Size by rage while transformed: 0% = 3.5 blocks tall, 100% = 6 blocks tall (smooth). Damage by rage: 0% = normal,
  100% = +50%.
- Dying as the Hulk: he doesn't die at once — he drops flat on his back in Hulk form, slowly detransforms into the
  regular player, then dies 2 s after that as the regular player.
- 4 MORE transformation animations (6 total, random each change). Break the player's body parts in half at the joints
  (elbows, knees) so the Banner side looks better; movie/comic-inspired. Better Hulk animations overall, and segment
  the Hulk model's limbs at the joints.
- VFX + SFX for all the Hulk's moves.
- Kit:
  - R Super Punch: punches an 8-block cone in front, can break blocks. Shift+R Ground Smash (the ground smash ability).
  - G Thunderclap (sonic clap shockwave cone, knockback + stagger). Shift+G Fault-line Stomp (a fissure races forward
    ~16 blocks, launching mobs up).
  - Z Bulldozer Charge (barrel forward ~12 blocks carrying/knocking mobs). Shift+Z Launch Uppercut (launches a target
    high into the air).
  - X Super Leap: ground-cracking VFX at take-off and landing. Hold X to charge up to 0.5 s, shown by a leap bar above
    the HUD. Tap = 25 blocks along the look direction; half charge = 100 blocks; full = 500 blocks. He keeps his speed
    (no slow-down/drag) until he lands.
  - C Gamma Burst (spends rage: radial gamma energy blast). Shift+C Earth Wall (tears up a wall of earth in front).
  - V Grab: pick up an entity; V again throws it 300 blocks along the look direction.
    Shift+V Debris Throw: rip a chunk of the ground out (debris model looks better and matches the blocks he stands
    on); V again throws it. Shift+V while looking at a target within 10 blocks: grab it and slam it into the ground 4
    times (the Avengers "puny god" Loki slam), animated the same way.
  - N (transformed): roar; low-level mobs flee; with an animation.
- Gladiator Hulk: same rules and moves (it keeps its weapon moves where they already replace the base kit).
- Mirror (Gamma origin mind room, HulkMind/MirrorHulkEntity): the reflected Hulk must always look straight at the
  player when the player faces the mirror (today his pitch follows the player's pitch, so looking up makes him look
  up). And press H (not right-click) to make the Hulk come through the mirror.

## Ownership (do NOT edit files owned by another agent unless listed as shared)

- Agent RULES: Hulk.java transform/detransform/rage/death flow, HulkControl (rampage, warning QTE, ring minigame),
  HulkCalm removal, rampage AI, rage-based size (Attributes.SCALE) + damage multiplier (HulkDamage), Hulk-form death
  sequence (server side + sets ANIM_DEATH_FALL), N roar logic (fear) (sets ANIM_ROAR), random transformStyle choice,
  HUD for rage (Hulk form only) + the two minigame screens, HulkMind mirror fixes, the Hulk renderer's scale (it must
  follow the synced scale/rage instead of the fixed 1.8x).
- Agent MOVES: HulkAbilities, HulkAbilityManager, HulkGrab, HulkCombat, HulkBoulderEntity (+ its renderer/model),
  GladiatorAbilities where the base kit changes, all move VFX/SFX, the leap bar HUD, ground-crack decals/particles,
  sound events. Sets the ANIM_* ids below when a move plays (HulkState.animId/animStart, as today).
- Agent ANIMS: hulk.geo.json (segment limbs at elbows/knees — keep bone names other code uses working, update every
  existing animation to the new hierarchy), hulk.animation.json (all new/updated clips), HulkAnimatable (map ANIM_* and
  transformStyle to clips), HulkPose + Banner-side jointed limbs for the transformations, MirrorHulkModel if bone names
  change, GladiatorFirstPerson/HulkGladiatorLayer if bone names change.
- Shared, edit carefully (merge by hand): en_us.json (add keys only, Node script, keep JSON valid), HulkConfig.java
  (add fields only; VersionedConfig rules in docs/CONFIGS.md — RULES bumps the version), HulkState.java (add fields
  only, in a NEW nested group if the Combat codec would exceed 16 fields).

## Animation ids (HulkState.ANIM_*, already on master) -> clip names (animation.hulk.<name>)

| id | ANIM_ | clip | notes |
|---|---|---|---|
| 60 | SUPER_PUNCH | super_punch | R |
| 61 | GROUND_SMASH | ground_smash | Shift+R |
| 62 | THUNDERCLAP | thunderclap | G |
| 63 | FAULT_STOMP | fault_stomp | Shift+G |
| 64 | BULLDOZE | bulldoze | Z, loops while charging |
| 65 | UPPERCUT | uppercut | Shift+Z |
| 66 | GAMMA_BURST | gamma_burst | C |
| 67 | EARTH_WALL | earth_wall | Shift+C |
| 68 | GRAB | grab | V pick up (then the existing `hold` loop) |
| 69 | THROW_FAR | throw_far | V again |
| 70 | DEBRIS_RIP | debris_rip | Shift+V rip ground (then `hold`) |
| 71 | DEBRIS_THROW | debris_throw | V again with debris |
| 72 | LOKI_SLAM | loki_slam | 4 slams, ~3 s; the victim is moved by the server in sync (left-right-left-right overhead arcs) |
| 73 | ROAR | roar | N |
| 74 | LEAP_LAUNCH | leap_launch | X release |
| 75 | LEAP_LAND | leap_land | landing |
| 76 | DEATH_FALL | death_fall | held: flat on his back, then the slow change back |
| 77 | RAMPAGE | rampage_roar | rampage starts |

Transformations: `HulkState.Combat.transformStyle` (TRANSFORM_QUICK 0 = `transform`, TRANSFORM_FORCED 1 =
`transform_forced`, TRANSFORM_RIP 2 = `transform_rip` (chest swells, shirt-tearing heave), TRANSFORM_POUND 3 =
`transform_pound` (pounds the ground with both fists as he grows), TRANSFORM_ARCH 4 = `transform_arch` (back arches,
screams at the sky), TRANSFORM_STAGGER 5 = `transform_stagger` (stumbles forward growing, catches himself standing)).
RULES picks one at random for every change and sets it; ANIMS plays it and the matching Banner-side pose. User rule:
NO pose may kneel or fold the legs back — all transformations are standing.
All changes last the same server time as today's slow change (Hulk.changeTicks); the quick one stays shorter.

## Follow-up: the charged V throw and World Breaker (v0.16.4, user)

- **V throw:** V picks up as before; V held with something overhead charges (`HulkState.Moves.throwChargeStart`,
  `abilities.throwChargeTicks` = 35, 1.75 s), V released throws it (`HulkGrab.releaseV`, routed from
  `HulkAbilityManager` on the key-up): `throwTapBlocks` 20 for a tap up to `throwFarBlocks` 300 at full charge (200 at first; the user raised it), same
  drag-free arc + chunk tickets; the debris uses the same arc with `HulkBoulderEntity.GRAVITY`. `ANIM_THROW_FAR` /
  `ANIM_DEBRIS_THROW` play on release. The rampage AI throws at once at full charge. The charge bar is
  `HulkMovesClient.renderThrowBar` (the leap bar's slot).
- **World Breaker** (`hulk/WorldBreaker.java`, config section `worldBreaker`): H as the Hulk at 100 rage, in control. 30 s:
  10 blocks tall, rage pinned at 100, damage x2 on top of the rage bonus (`Hulk.damageMultiplier`), area x2
  (`WorldBreaker.aoe`) and blocks / craters x2 (`WorldBreaker.destruction`, applied centrally in `HulkCombat.crater`).
  Then Burnout 60 s (rage 0 and frozen, Slowness II, Weakness II, Mining Fatigue I) and a 5-minute cooldown from the
  end. State lives in `HulkState.Moves` (`wbStartedAt`, `wbUntil`, `wbBurnoutUntil`, `wbReadyAt`) -- no statics.
  Visuals: `HulkWorldBreakerLayer` (emissive veins texture `hulk_world_breaker_veins.png` + a faint green glow pass).

## Follow-up: tuning pass -- health, damage, air clap, long-throw craters, the World Breaker shout (v0.16.4, user)

- **Health by rage:** `Hulk.applyRageHealth` (from `reconcile`, every Hulk tick and `setRage`) sets the MAX_HEALTH bonus to
  `HulkConfig.healthBonusForRage` -- 20 at 0 rage (40 HP, 2 bars) up to 40 at 100 (60 HP, 3 bars), in whole HP. Current
  health keeps its share when the max moves, never below 1. World Breaker (rage pinned at 100) = 60. Banner unchanged.
- **Damage nerf:** punch 20 -> 15 at 0 rage (`ATTACK_BONUS` 19 -> 14), 25 at 100 (`RAGE_DAMAGE_BONUS_MAX` 0.5 -> 2/3, so
  x1.0 -> x1.667). Every move's base damage x0.75 (`HulkConfig.DAMAGE_NERF`), Gladiator moves too; HulkConfig v9 resets
  those keys. World Breaker still x2 on top.
- **Air clap:** G off the ground (`HulkAbilities.airborne`) fires at once: `airRedirect` replaces his velocity with
  -look x `airClapPush` (2.2 b/t, the recoil: opposite his aim) via `AbilityHelpers.launchSelf` (motion packet); mid leap
  the synced leap velocity becomes the recoil first, so `HulkMovesClient` holds that horizontal speed until he lands. The leap stays open, so its
  landing still lands and no fall is felt. The cone is x`airClapScale` (0.75) range and damage.
- **Long throws:** a charged V throw (mob or debris) planned 100+ blocks calls `HulkGrab.longThrowImpact` where it lands:
  t = (planned - 100) / 200 (full at the 300-block max); crater radius 2.5 -> 7, cap 20 -> 120 blocks (`HulkCombat.crater`: the Hulk's block rules,
  loaded chunks only, World Breaker x2), cracks, a shockwave ring, explosion / dust / debris, a heavy boom; impact damage
  up to +50%. Config `abilities.longThrow*`.
- **World Breaker shout:** `WorldBreaker.shout` -- a vanilla title "HULK IS STRONGEST" / subtitle "THERE IS!!!" (lang
  `title.projecthero.hulk.strongest[_sub]`) for every player within 64 blocks, a layered low roar (ravager, warden, polar
  bear, dragon), plus the sound event `projecthero:hulk.strongest` (`ProjectHeroSounds.HULK_STRONGEST`).
  **No audio ships** (the movie line is copyrighted) and sounds.json deliberately has no entry, so the event plays
  nothing. To add a voice line: put `assets/projecthero/sounds/hulk/strongest.ogg` (mono, so it is positional) and add to
  sounds.json `"hulk.strongest": {"sounds": ["projecthero:hulk/strongest"], "subtitle": "subtitles.projecthero.hulk.strongest"}`
  (the subtitle lang key already exists). Don't add the entry without the file: that logs a missing-file warning every launch.
