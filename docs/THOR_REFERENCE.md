# Thor (Hero-Tier power)

Power logic `com.projecthero.mod.power` (`ThorPowers`, `ThorPassives`, `StormEnergy`), the hammer
`entity/MjolnirEntity` + `com.projecthero.mod.hammer`, Thor's Armour `com.projecthero.mod.thorarmor`, key routing
`hero/ThorAbilityAdapter`, client `client/thor`, flight pose `client/FlightPoseHelper` + `client/mixin/HumanoidModelMixin`,
HUD `client/gui/ThorHud`. Player-facing text: the Guidebook's Thor chapter (`hero/guide/HeroPackGuide`, keys
`projecthero.guide.thor.*`, which the power info screen shows too).

Keys (before v0.17.7 -- see the Cosmic revamp below for today's four-state kit): R Call Mjolnir (right-click throws), G Lightning Strike, Shift+G Thunder Field (v0.16.1), X Lightning Beam (hold),
Z God of Thunder's Wrath (hold 5 s), V Hammer Volley, Shift+V Thunderclap, C Chain Lightning, H Thor's Armour, N weapon
selector (v0.15.3), double-tap Jump flight.
Storm Call (`ThorPowers.stormCall`) is still in the code but not on any key since v0.6.22. There is no Thor Parry.

## v0.17.7: Cosmic revamp -- four weapon states

User-approved design; tier **Cosmic** (`hero/PowerRank.COSMIC`, placed in `PowerRank.ofGuideKey`, so the Guidebook and the I menu
show the badge). Melee 21-28, abilities 22-30, ultimates 45-60 per hit. **No single Thor hit over `ThorKit.MAX_HIT` 60**: every move
goes through `ThorKit.hit` (capped there), and the storm buff / Sunder are capped in the one hurt hook (`ThorStorm.enter`, only for
a Thor attacker, and never lowering a hit that was already higher). Bosses keep their own per-hit caps. Everything stays squad-safe
(`WeaponCombo.friendly` + `ThorTargets.canAffect`).

Code: `power/ThorState` (the state from the hands), `power/ThorKit` (routing, numbers, shared rules, HUD table `moveId` /
`cooldownOf`, melee per state, God of Thunder, timed states), `ThorUnarmedKit`, `ThorMjolnirKit`, `ThorStormbreakerKit`,
`ThorTwinKit`, `ThorUltimates`, `ThorSequences` (timed moves, static map cleared by `ServerStateReset`), `ThorSunder` (static map,
cleared too). `hero/ThorAbilityAdapter` just calls `ThorKit.handle`. The binding API (`ThorPassives.hasPowerOfThor`,
`BOUND_STORMBREAKER_ID`, `ThorPowers.bindOnAscend` / `toggleBinding`) is unchanged; `ThorState.hasKit` = worthy and (holding a Thor
weapon, or bound to Mjolnir, or bound to a Stormbreaker).

### States (`ThorState.of`: both weapons = TWIN, else Mjolnir, else Stormbreaker, else UNARMED)
| State | Melee | Flight speed | Perk | Drawback |
|---|---|---|---|---|
| Unarmed: Warrior of Asgard | fist 24 at 1.8 swings/s | none (double-tap jump does nothing) | every cooldown x0.75 | short range |
| Mjolnir: Storm Caller | 24 | 0.080 (fastest; was one shared 0.069) | lightning stuns (Slowness X 0.8 s, mob path stopped; bosses immune); Storm Call x1.5 long | lowest single-target damage |
| Stormbreaker: Executioner | 26 (slow item swing) | 0.052 | ability hits use an armour-ignoring source (indirect magic) and do x1.25 under 30% health | slow swings, slow flight |
| Both: Twin Gods | 27 | 0.066 | the highest damage | every Storm Energy cost x2; `Bifrost.canUse` is false |

Melee is one transient modifier (`ThorKit.reconcileMelee`, every tick) making up the difference from base 1 + item + the Power of
Thor's +11; anything else in the main hand keeps its own damage. Flight speed follows a weapon swap mid-flight.

### Kit (damage / base cooldown / base Storm Energy; Unarmed cooldowns x0.75, Twin energy x2)
| Key | Unarmed | Mjolnir | Stormbreaker | Both |
|---|---|---|---|---|
| R | Call Weapon | Call Weapon | Call Weapon | Call Weapon |
| Shift+R | Thunder Fist, hold up to 1.5 s: 24-30 / 8 s / 15 | Whirlwind Throw: 10-tick spin, pierces 6 x 24 / 10 s / 15 | Storm Hurl: pierces 4 x 28 + Sunder / 10 s / 15 | Twin Throw: 28 within 4 / 12 s / 20 |
| G | Lightning Palm 22 (12 blocks) / 4 s / 12 | Lightning Strike 26, chain 2 x 22 / 3.5 s / 18 | Storm Cleave 30 / 6 s / 15 | Thunderstruck Cleave: wave 13 blocks, 28 / 9 s / 18 |
| Shift+G | Asgardian Grapple 24 / 12 s / 18 | Thunder Field 22 a second x4 / 15 s / 25 | Thunder Step: blink 20, cleave 26 / 10 s / 20 | Storm Anchor: field radius 10 / 20 s / 25 |
| C | Storm Rush 4 x 22 / 10 s / 20 | Chain Lightning cone 24 (32 blocks, was 122) / 8 s / 20 | Rending Arc 180 deg r6.5, 24 / 8 s / 18 | Crossfire 24 + 24 / 9 s / 20 |
| Shift+C | Static Burst 22 within 5 / 9 s / 18 | Lightning Beam 22 every 5 ticks, 8/s | Stormbreaker Beam 26 every 5 ticks, 10/s | Twin Beam 28 every 5 ticks, 12/s (24 with x2) |
| Z | Ground Pound 26 within 5 / 10 s / 20 | Thunderclap 26 within 7 / 6 s / 15 | Executioner's Drop 28 within 4.5, x1.5 under 30% max 30 / 10 s / 20 | Twin Thunder Strike 30 + ring 22 / 15 s / 20 |
| X | Storm Sprint 4 s +60% speed / 8 s / 10 | Storm Dash 9 blocks, trail 22 / 5 s / 10 | Lightning Dash blink 10 / 4 s / 8 | Storm Dash |
| Shift+X | Thunder Leap 28 within 5 / 12 s / 20 | Meteor Dive 28 within 5.5 / 12 s / 25 | Bifrost Travel (the waypoint screen; moved off Shift + right-click) | Meteor Dive |
| Shift+Z | Lightning Incarnate | Wrath of the Storm | Stormbreaker's Judgement | Ragnarok |
| V | Storm Call: 18 s storm (27 s Mjolnir), Storm-Charged +30%, a 22 bolt every 2.5 s / 60 s / 30 | same | same | same |
| Shift+V | God of Thunder: 15 s, glowing eyes, every hit chains 22 to the nearest enemy (once per 8 ticks), +0.5 knockback resistance / 45 s / 40 | same | same | same |

**Ultimates** share one `ThorAbility.ULTIMATE` cooldown: 120 s (90 s unarmed), 100 Storm Energy (200 Twin). One shared cooldown
because swapping weapons must never chain four ultimates back to back, and it is one box on the HUD.
- Lightning Incarnate: 200 ticks as lightning (+45% speed, knockback resistance, aura 22 a second within 3.5), then 60 within 10.
- Wrath of the Storm: hover 3.5 up; t12-62 a bolt every 3 ticks on a random enemy within 18 (45, each foe at most once per 10
  ticks); t72 the giant bolt on the aimed spot, 60 within ~7.3 (`ThorPowers.wrathTargets`).
- Stormbreaker's Judgement: t8 the spectral axe flies, t16 impales the aimed foe in a lightning strike (60), pinned (non-bosses)
  while the storm gathers on the axe, a storm column (`ThorStormbreakerKit.stormColumn`: a bolt plus three round it) crashes at
  t32, t38 45 within 7.
- Ragnarok: rise 6 up; t20-60 bolts rain within 14 of the aimed centre (45); t62 the spectral Stormbreaker hurled down; t70 60
  within 8, bolts round the centre, a storm column.

Gone: Hammer Volley (as its own key), the hold-Z God of Thunder's Wrath, the old X beam (now Shift+C). Retuned to Cosmic: thrown
Mjolnir 18 -> 22, thrown Stormbreaker 20 -> 24, combo finisher area hits 4 / 6 -> 22 (the finisher hit itself capped at 28),
Twin Thunder 20 / ring 8 -> 30 / 22, Thunder Field 4 every 10 ticks -> 22 every 20 (`ThorConfig` version 2 resets those keys).

### Rules worth knowing
- Storm-Charged now also comes from his own Storm Call (`ThorStorm.isCharged`).
- Sunder: +20% from every source for 8 s, applied in the hurt hook with the storm buff (never twice on a re-issued hit).
- Timed state flags live in the synced cooldown map as "ready at = when it ends": `GOD_ACTIVE`, `SPRINT_ACTIVE`,
  `INCARNATE_ACTIVE`; `GOD_CHAIN` is the chain throttle.
- The Lightning Beam's per-tick clock reuses the old `THOR_WRATH_CHARGE` attachment.
- `ThorTwinThunder.queueNow` starts the strike at once (Z); the combo path still queues it after the hop.

### Look -- client `thor/ThorKitPose`, `thor/ThorKitFxRenderer`
- **Poses** (`ThorKitPose`, called from `ThorPose.apply` after the beam pose): the `SuperSoldierPose` clip technique -- keys with an
  anticipation, a SNAP into the strike, a follow-through and recovery faded back to vanilla; the twist carries the shoulders, the
  lean bends from the hips (`HipBend`), a crouch uses the sneak offsets. Lead arm = the Mjolnir hand, else the Stormbreaker hand,
  else the main arm (Ragnarok's hurl: the axe hand); mirrored for a left lead. Held clips (leap / dive / Executioner take-off) hold
  their last pose while airborne until the landing clip arrives. The Thunder Fist charge is a held channel pose (cocked fist,
  trembling harder as it builds). Twin Beam levels both weapons.
- **First person**: weapon states through `WeaponComboPose.firstPerson` (both hands while dual); Unarmed bare hands through
  `ItemInHandRendererThorMixin` (vanilla swing held off while a move owns the hands).
- **Stormbreaker grip** (v0.17.7 user): held like a greataxe a third of the way up the haft; a move's re-grip only takes
  `ThorKitPose.AXE_GRIP` of the -90 degree turn, so the head stands up out of the fist and the haft never runs back through him.
- **Spectral weapons**: Twin Throw, Stormbreaker's Judgement and Ragnarok fly item-model copies (`ThorKitFxRenderer`, drawn last in their
  own pass); `ItemInHandLayerThorHideMixin` hides the real one in hand while its copy flies.
- **Effects per state**: Unarmed -- fist charge ball + arcs up the arm, punch shock discs facing the blow, Static Burst's shell of
  rings and bolts, Incarnate's cage of arcs + ground strikes; Mjolnir -- the whirling strap ring, the dash's burnt bolt trail, the
  meteor streak + falling-star flare, shockwaves with ground lightning and cracks, the Wrath vortex and its colossal bolt; Stormbreaker
  -- heavier golden-white lightning (see "No rainbow on Stormbreaker" below); Twin -- the crossing spectral throw (two lightning
  trails), the lightning flowing hammer to axe and the lightning crescent wave, the Stormbreaker Beam wound with a white-blue
  lightning helix, Ragnarok's wheel of storm rings. God of Thunder: two glowing eye orbs + sparks. Beams skip
  `BeamClashClient.hides` (and the arc renderer now does too); Stormbreaker's beam is its own golden-white lightning beam
  (`ThorKitFxRenderer.stormbreakerBeam`) instead of the plain bolt.
- **Sound**: vanilla layers per move (thunder, trident thunder / riptide, wind charge burst, beacon, mace
  smash, warden sonic boom only on the three biggest blasts). Screen shake through `TitanShakePayload` (`ThorTwinThunder.shake`).

### No rainbow on Stormbreaker (v0.17.7, user: "remove the rainbow vfx for stormbreakers powers please, only make the rainbow show up for the actual teleporting with the bifrost please, just use the lightning vfx")
- **The rainbow is only Bifrost Travel's** (Shift+X with Stormbreaker alone, the waypoint screen): `Bifrost.travelTo` and its
  `BifrostEntities.Beam` column (`BifrostBeamRenderer`, its own seven colours). Nothing else spawns that entity any more.
- **Renamed** (display names only; ids, lang keys, `ThorAbility` / `ThorFx` constants unchanged): Bifrost Hurl -> **Storm Hurl**,
  Bifrost Step -> **Thunder Step**, Bifrost Beam -> **Stormbreaker Beam**, Bifrost Dash -> **Lightning Dash**, Bifrost Judgement ->
  **Stormbreaker's Judgement**. Bifrost Travel keeps its name.
- **Palette**: server `ThorKit.storm` (electric sparks + a third as much white dust) and `ThorKit.stormLine` (a crackling
  spark streak) replace `prism` / `prismLine` / `RAINBOW`; `ThorKit.STORM` is white / pale blue only, because tinted dust
  (gold, mid blue) gets shaded per particle by vanilla and read as pastel confetti in the first film. Client
  `ThorKitFxRenderer`: `SB_GOLD` 0xFFE08A / `SB_WHITE` 0xFFF8E6 on top of `ThorDraw`'s blues; helpers `axeBolt` (blue halo,
  golden-white glow, white core), `stormRing`, `stormWall`, `lightningStreak`, `stormBeam`, `sparkBurst` (axe-edge sparks),
  `arcSweep` (blue-to-gold cleaving bands under a jagged bolt edge). Beam-clash colour (`ThorPowers.beamColour`): Stormbreaker
  0xFFF1C2 golden-white, Twin 0xDDEEFF white-blue (were pink / lilac).
- **Per move**: Storm Hurl -- sparks on the raised axe, a crackling wake behind the flying axe, Sunder leaves a crackle on the
  foe; Storm Cleave -- the sky bolt onto the blade, axe sparks, a storm shock ring; Thunder Step / Lightning Dash -- a heavy
  lightning streak from where he left to where he lands, a bolt-flash (sky bolt + flare + radial arcs + FLASH particle) at both
  ends, storm rings on the ground (Step adds the cleaving arc trail); Rending Arc -- the 180 degree cleaving arc trail with a
  jagged lightning edge (the 25 busy rainbow dust lines are now crackle lines); Stormbreaker Beam -- a golden-white core with two
  jagged bolts along it; Executioner's Drop -- a golden-white bolt trail in the air, storm ring + axe sparks + radial bolts on the
  slam; Stormbreaker's Judgement -- a lightning streak behind the spectral axe, then storm rings climbing it and 1-5 bolts
  reaching down onto it as the storm gathers, the storm column, a storm ring / wall and radial bolts on the burst. Twin Throw --
  the axe's golden-white bolt trail beside the hammer's; Thunderstruck Cleave -- a crescent of blue-to-golden lightning bands under
  a crackling edge; Storm Anchor -- sparks + arcs from the hands; Crossfire -- Mjolnir's bolt and the axe's lightning beam; Twin
  Beam -- the Stormbreaker Beam (wider) wound with the lightning helix; Ragnarok -- a storm halo, a wheel of storm rings, a
  lightning streak behind the hurled axe, the storm column, storm ring / wall.
- **Sound**: the amethyst chimes and the beam's beacon hum (the Bifrost's sounds) became trident thunder / lightning impacts.
- Films: `docs/screenshots/v0177/thor_revamp/*_lightning.mp4` (+ frame sheets) beside the old rainbow ones.

### Bifrost Travel cinematic (v0.17.7) -- `stormbreaker/BifrostTravel`, client `thor/BifrostTravelClient`, `thor/BifrostColumnDraw`
User: "When player selects a destination on the bifrost travel can you make it so that player lifts stormbreaker and then the
bifrost appears beaming down on the player and surrounding players, then they all slowly rise up 10 blocks for 3 seconds and
get transported back down where they wanted to teleport". `Bifrost.travelTo` validates exactly as before (and spends the 60 s
cooldown), then `BifrostTravel.begin` runs the trip instead of teleporting at once. Ticks from the start:
- **0..14 raise** -- the caster lifts Stormbreaker to the sky: `BifrostRaisePoseMixin` (axe arm straight up, free arm out,
  head back), `BifrostRaiseGripMixin` (the axe turned a quarter turn about the hand's X so the head points up along the arm),
  `BifrostRaiseFirstPersonMixin` (first person: lifted 0.42 and tilted back 28 degrees). Weight from the synced `CAST` clock
  (`BifrostTravelClient.raise`): up over 10 ticks, held through the whole trip, lowered over the last `POSE_LOWER` 10 ticks
  of the descent. Lightning arcs crawl over the raised head (more as it builds), sparks + a beacon charge sound, and at the
  strike a bolt from the axe into the sky. Everyone in the beam is held where they stand.
- **14 strike** -- the Bifrost beams down: a `BifrostEntities.Beam` in mode `LIFT` (radius = the group's spread + 1, 1.6..5.5)
  crashes down in 4 ticks, the rune circle (`Mark`, scaled to the beam via its new saved `size`) burns in under it, a ring of
  rainbow dust, thunder + lightning impact + beacon power-select + amethyst resonance, a 0.6 camera shake for every rider and a
  soft white flash on their screens.
- **18..78 rise** (60 ticks = 3 s) -- everyone rises `RISE_HEIGHT` 10 blocks, smoothstep-eased (biggest step 0.25 blocks/tick),
  gravity off, fall distance held at 0. Players: the server sets the position (so the server copy is right) and sends the next
  step as velocity (smooth on the client); more than 1.25 blocks off (lag, knockback) snaps them back. Pets: positioned every
  tick, navigation stopped. Fly kick: `mixin/BifrostFlightKickMixin` lifts `getMaximumFlyingTicks` while `inBeam`. Sound:
  an elytra whoosh + wind charge burst at the start, then an amethyst chime and the beacon hum every 10 ticks, rising in pitch.
  END_ROD motes stream up the column.
- **78 transport** -- the screen whites out over the last 4 ticks; the landing is checked again (it may have been built over);
  everyone goes to the top of the destination column (as high above their landing as the space allows, up to 10 -- a
  `DESCEND` Beam crashing down onto them), a flash + END_ROD burst up there, thunder, beacon, amethyst cluster break.
- **78..108 descent** (30 ticks) -- lowered with an ease-out (quick at the top, settling softly), then at **108 land**: exactly
  on their landing, gravity back, momentum + fall distance cleared, the rune circle burned in, a rainbow shockwave (the DESCEND
  beam's), trident thunder + amethyst + a 0.35 shake. The action-bar message ("The Bifrost carried you and N companion(s)").

**Who comes** (`BifrostTravel.ridersFor`): the caster; every player within `PLAYER_RADIUS` 4 blocks (anyone standing in it, squad
or not); every squadmate within `Bifrost.CARRY_RADIUS` 8 (as before); any `OwnableEntity` (wolves, cats, parrots, tamed horses)
owned by one of those players within 4 blocks of the caster or of its owner -- not one told to sit. A player can sneak to stay
behind. Hostile mobs and creatures nobody owns never come. Riders are dismounted; each keeps their X/Z offset on their own
safe spot (`Bifrost.landingFor`, the old ally rule: within 6 blocks up/down of the caster's landing, else the caster's spot).

**The column** (`BifrostColumnDraw`, the one place the rainbow stays): a white core in a soft glow; a luminous sheath of smooth
rainbow bands spiralling up the wall (vertex-coloured quads, both windings, hue by angle + height, scrolling up on the way up
and down on the way down); a counter-turning inner swirl of brighter streaks; four rings of Asgardian rune glyphs (Tiwaz, Algiz,
Fehu, Thurisaz, Raido, Kenaz, Gebo, Sowilo as gold-white strokes) turning round it and climbing it (sinking for DESCEND) over a
thin band of light; 46 motes; a glowing pool on the ground; a rainbow shockwave on each impact; a white-gold flare at the top at
the moment of transport. LIFT lives 80 ticks (pulls up into the sky after the transport), DESCEND 60. Rider HUD
(`BifrostTravelClient.hud`, `RIDE` clock, owner-synced): rainbow shimmer at the screen edges while in the beam + the flashes.

**Edge cases.** Cross-dimension: still refused (same dimension only, as before). Unloaded destination: loaded (generated if
need be) before validation, then held by a `projecthero_bifrost` ticket (renewed every second) on every rider's landing chunk.
A ceiling: each rider rises only as far as their box stays clear (`clearance`, 1/8-block steps) -- never into blocks; the
same at the destination's top. Destination inside blocks: `findSafeLanding` climbs out (as before); if the landing is no longer
safe at the transport another is searched in that column, and if there is none the beam lowers everyone back where they started
and refunds the cooldown. Logout mid-trip (`DISCONNECT` -> `onLeave`): put back on the ground (where they stood, or their
landing once across), gravity on, fall distance 0, off the run -- so nobody is saved floating with NoGravity; if it was the
caster the bridge stays open for the others. Death / leaving the level / spectator: dropped from the run with gravity restored.
Server stopping: everyone is set down safely before the players are saved (`SERVER_STOPPING`). A jump in game time cannot skip
the strike or the transport (`>=` + flags). A second trip while one runs is refused with `Result.BUSY`
(`message.projecthero.bifrost.fail.busy`). Statics (`RUNS`, `RIDING`: UUIDs, dimension keys, positions) cleared in
`ServerStateReset`.

Tests: `BifrostTravelGameTests` (batch `bifrost_travel_v0177`) -- the beam takes the caster, a non-squad player 2 blocks away
and his wolf but not a far player, a zombie or a cow; the rise is upward every tick with the biggest step 0.1..0.3 and tops out
10 blocks up for all three; the descent never drops more than 0.75 a tick; everyone lands on their spot with gravity back, no
fall distance and no damage; a second trip is BUSY; a rider / the caster logging out mid-rise is put back where they stood with
gravity on (and the others still arrive); a ceiling stops the rise under it without ever being inside a block, and a destination
inside a pillar lands on top. `StormbreakerGameTests`' Bifrost tests play the trip out with `BifrostTravel.finishForTests`.
Lang: `scratchpad/v0177/lang_bifrost_travel.js`. Films: `docs/screenshots/v0177/bifrost/` (harness
`scratchpad/BifrostHarness.v0177.java.txt`, script `scratchpad/v0177/bifrost_films.js`).

### HUD -- `client/gui/ThorHud`
State badge first in the tags (Unarmed / Mjolnir / Stormbreaker / Twin Gods), then Storm-Charged, God of Thunder, Lightning
Incarnate. The boxes show the state's cooldowns (Shift swaps to the Shift moves, the other on the durability bar); a narrow
wrapped line above them names the six moves the boxes hold right now. A Charge meter while Thunder Fist charges.

### Tests -- `ThorRevampV0177GameTests`
State from the hands; 23 key routes across the states (each fires its move's cooldown and matches the HUD table); Lightning
Strike 26, cap 60 (also with Storm Call's +30%), melee 24 / 24 / 26 / 27; Sunder +20%; Executioner's Drop 28 / 30; Twin pays x2;
no unarmed flight + speed order; Unarmed cooldowns x0.75; each ultimate fires and lands. `StormbreakerGameTests` sneak-click test
now opens the Bifrost through Shift+X.

Films: `docs/screenshots/v0177/thor_revamp/`.
## v0.17.7: how players get Thor and Stormbreaker -- `com.projecthero.mod.thoracquire`

Registration in `ThorAcquire`, per-player data in `ThorAcquireAttachments` (kept out of `ModAttachments`), client in
`client/thoracquire`. Guidebook: the Thor chapter's "Becoming Thor" steps, Valor (with the reader's own Valor, a live line
fed by `HeroPackGuide.thorValorLive` / `refreshThor`), Mjolnir's Crater, the Trial of Asgard, and the Stormbreaker parts
`recipe / forge / channel / branch / finish`. Lang: `scratchpad/v0177/lang_thor_acquire.js` (langset, key by key).

The path: **earn Valor without powers -> find the crater -> wait for a thunderstorm -> win the Trial of Asgard without
powers -> the lift cutscene binds Mjolnir** (Thor becomes the one Primary power through the same `Worthiness.ascend` ->
`ThorPowers.bindOnAscend` as before). Hero of the Village no longer opens Mjolnir: it gives Valor.

### Valor -- `Valor`, `ThorAcquireAttachments.VALOR` (persistent, copyOnDeath, synced to the owner)
- 0..1000; Mjolnir only answers full Valor. **Earned only while unpowered** (`HeroTiers.hasAnyPower`, user rule): a powered
  player keeps what they have and gets a quiet, 10-minute rate-limited hint instead.
- Sources: Hero of the Village gained 150 (`ThorAcquireEffectMixin` on `LivingEntity.onEffectAdded` -- a vanilla raid win,
  a crime stopped, the Supervillain Raid's Champion effect; a refresh is not a new deed); Supervillain Raid won +150
  (`SupervillainRaidRewards.grantVictory`); Zombie Raid won 150 (`ZombieRaidRewards.grantCompletion`); a world/raid boss
  (c:bosses or 150+ max health) dying with a player's kill credit 200 to every player within 48; a hideout/dungeon
  mini-boss (`HideoutBoss`, Ankhatep, the Tomb Sentinel, Warden Kross, the Riot Captain) 75 likewise; a first clear of a
  hideout / the Ancient Temple / a Vault Dungeon 100 (`*Clearing`); a saved villager 15 (killing a hostile that targets a
  villager or hit one in the last 10 s, the villager alive within 16; once per 30 s, `VALOR_SAVE_READY`); a cured zombie
  villager 40 (`ThorAcquireCureMixin` on the vanilla trigger). Roughly a raid + a boss + a hideout + a dungeon + villagers.
- Shown: the Guidebook line + bar, and `ValorHud` under the crosshair when looking at a resting Mjolnir as an outsider
  ("Valor 43%" over a thin gold Meter; full: what the hammer still wants).
- v0.17.7: full Valor with no powers is also what makes a player **worthy for Shazam** (the Wizard chooses the pure of heart):
  illusions of a subway stairway start to appear (`com.projecthero.mod.shazam.journey`, see FATE_SHAZAM_DESIGN.md). Neither path spends Valor;
  whichever power they take first makes them powered, which closes the other.

### Mjolnir's Crater -- `worldgen/MjolnirCraterPiece`, `CraterAmbience`, `CraterGuard`, client `CraterCloudRenderer`
- The existing `projecthero:mjolnir_crater` structure (same id, same structure set: random_spread spacing 100 chunks,
  separation 40, frequency 0.5, the old biome tag) -- **rare, not one per world**: about one per 1,600 x 1,600 blocks before
  the biome filter. Multiplayer servers get several; the unique-powers gamerule still limits Thor to one holder.
- New look (new chunks only): a 17-block scorched bowl -- blackstone / basalt / coal core with a few magma cracks, six
  zig-zag lightning scars of cracked deepslate tiles and basalt running out over the rim, a broken lip and thrown debris --
  and a cracked stone plinth (3x3 cracked stone bricks + outward stairs on polished blackstone bricks, a chiseled pillar
  block) with the hammer standing on top. Shapes come from position hashes, so the four chunk quarters always meet.
  `buildForTest` / `buildOnTerrain` build one by hand.
- The hammer is still placed once per crater by `CraterAmbience` (deferred out of the chunk-load callback); the visual
  ambient strikes still play. **Old worlds:** craters generated before keep their old bowl and their hammers work exactly as
  before (adopted on load); free hammers anywhere follow the same lift rules.
- Protection while the hammer is in it (`CraterGuard`, from `CraterAmbience.activeCraters` -- loaded, unclaimed craters):
  no breaking or placing (blocks/buckets) within 11 blocks horizontally, 9 below / 16 above the hammer spot (creative may),
  explosions skip those blocks (`ThorAcquireExplosionMixin` on `Explosion.finalizeExplosion`).
- Storm cloud: `CraterCloudPayload` (craters + trial centres within 384 blocks, every 2 s, every 0.5 s during a trial).
  The client draws a slowly turning knot of ~36 billboarded vanilla smoke sprites tinted slate grey, 24 blocks above the
  hammer, lit from inside every 3-7 s by a silent flicker with a short bolt licking down; during a trial it grows, darkens,
  churns, flickers every second with bolts to the ground and a pulsing ring on the plinth.

### The lift gate -- `MjolnirLift` (from `MjolnirEntity.interact` for anyone who can't already lift it)
1. bound to someone else: never budges (the Hero-of-the-Village "carry another's hammer" exception is gone);
2. unique-powers refusal as before;
3. Valor not full: "You are not worthy. Valor: 43%", heavy clunk (anvil + netherite), smoke, a small screen shake (1 s
   throttle, `LIFT_FEEDBACK_READY`);
4. not thundering where it stands (`Storms.isStorming`: `isThundering` + a sky; deserts count, the Nether / End never storm):
   "The storm has not answered. Come back in a thunderstorm.";
5. trial won (`TRIAL_PASSED`): the lift; otherwise the Trial of Asgard.
- Walk-over pickup never ascends any more (`ItemEntityMixin`), and `WorthinessEnforcer` only exempts a player mid-lift.

### The lift cutscene -- `MjolnirLift.begin`, synced clock `MJOLNIR_LIFT`, client `MjolnirLiftClient` + `ThorAcquireLiftPoseMixin`
- 76 ticks, the player held in place: the hammer goes into the main hand and the arm rises straight up (0-18), arcs gather
  on the head with rumbling thunder; tick 26 a lightning column (visual bolts every 5 ticks, a thick sky-to-hammer bolt and
  a cylinder of light client-side) strikes, white screen flash, shake; tick 46 a burst (flash, end rods, fireworks, totem +
  beacon sounds, an expanding ring), the bind (`Worthiness.ascend`), and Thor's Armour forms (`ThorArmor.toggle`).

### The Trial of Asgard -- `TrialOfAsgard`, boss `entity/DestroyerBoss` (was the Stormwarden)
- Starts when a full-Valor player tries to lift in a storm: refused (attempt kept) while they have any power; refused if they
  already lost in this storm (`TRIAL_FAILED_STORM` vs `Storms.current`, a saved storm counter that ticks up each time the
  overworld starts thundering); one trial per hammer. Intro 30 ticks: title, a pillar strikes the plinth (0), two more strike
  the landing spot 4 blocks from the hammer toward the challenger (14/28, `landingSpot`), then the Destroyer lands there in a
  bolt (the 50-tick "form" clip, untouchable): down on one knee, it stands, the faceplate cracks open on the fire at tick 38.
- Lost on: death, more than 48 blocks from the crater, logging out, the storm ending, or gaining a power -> the guardian
  dissipates, the hammer stays, the storm is recorded. Won (the guardian dies): `TRIAL_PASSED` saved; 50 ticks later, if it is
  still storming and the hammer is within 48, the lift plays; otherwise the next storm lets them lift it without fighting.
- Running trials: a static map of player UUID -> centre/dimension/boss UUID (no worlds or entities), cleared by
  `ServerStateReset`; a trial guardian whose trial is gone (server restart) dissipates. A spawn-egg Destroyer is free.
- **The Destroyer** (user: "can you make the mjolnir guardian the destroyer please, i think it would be more fitting"; it
  replaced the original Stormwarden before release): the enchanted Asgardian armour from Thor (2011). Entity id `destroyer`
  (was `stormwarden`; `item/LegacyItemIds` redirects `stormwarden` and `stormwarden_spawn_egg` in every registry, so one
  saved in a test world loads as the Destroyer). The Storm Spirit summon is gone. Spawn egg silver / orange.
- Look (user: "use this skin for the model of the destroyer please"): built from the PlanetMinecraft skin "Destroyer"
  (https://www.planetminecraft.com/skin/destroyer-6483954/, original at `scratchpad/v0177/destroyer_skin_original.png`;
  ships publicly like the other borrowed skins, credited in the generator and `DestroyerRenderer`). `scratchpad/v0177/
  gen_destroyer.js` (edit it, not the outputs) makes a player-shaped GeckoLib body at one unit per skin texel (32 units;
  the renderer draws it `withScale(1.8)`, about 3.6 blocks) on a 128x128 atlas: the skin copied 1:1 at its own box-UV
  offsets (head + hat, body + jacket), arms and legs split at elbow / knee into halves with their own box-UV regions painted
  from the matching skin rows (sleeves / trousers as 0.25-inflated cubes), the skin's near-black overlay texels keyed out.
  The face is cut out of the head and hat and moved onto two faceplate bones (`faceplate_left` / `faceplate_right`, the
  face's halves, hinged at the outer edges, swung open by the clips) over a white-hot molten cube (glowmask). The skin's
  opaque black base texels (the gaps between its plates and the eye slits) glow orange; `destroyer_unleashed.png` (+
  glowmask) makes them blaze and warms the darker plates; the renderer swaps to it in phase 2, flickers the glow, fades it
  out over the death, and drops vanilla's red death tint. No weapon. 13 clips (translations scaled 32/57 from the earlier
  57-unit body): idle, walk, form, beam, beam_wide, blast, blast_double, slam, slam_double, charge, backhand, surge, death.
- 420 HP, 12 armour / 4 toughness, knockback-immune, fire-immune, lightning-proof (`thunderHit` does nothing, lightning
  damage refused), speed 0.25. Tuned for a well-geared unpowered player (diamond/netherite, potions): a few minutes.
  Shared `BossThreat` table (switches targets), boss bar (white; "The Destroyer -- Unleashed" red in phase 2), leashed 28
  blocks to the crater. **Only unpowered players' hits count** (`countsAgainst`, checked in `hurt` and `actuallyHurt`).
  The body lingers 50 ticks after death (`tickDeath`) so the collapse plays.
  - Disintegration Beam (5-20 blocks, 240-tick cooldown): stops, plants its feet, the faceplate grinds open in four steps
    (anvil / iron door / smithing table) while the fire builds at its face and a line of embers marks the start of the arc
    (30 ticks; it tracks you until tick 24, then turns to the start). Then a thick molten beam from the face (range 22, stops
    at blocks) sweeps 50 degrees each side of where you stood over 60 ticks: 10 every 5 ticks to anyone in it (`invulnerableTime`
    reset), alight 4 s; where it crosses the ground it leaves scorch points (flames for 3 s that set standers alight, no
    blocks changed). Beacon / blaze / fire roar. `BeamClash.report` every live tick (power 1.6, orange); a locked clash holds
    the sweep and skips the damage. The renderer skips its beam while `BeamClashClient.hides` it.
  - Fire Blast (5-16 blocks, 110): the faceplate flashes open, aim tracks until tick 8 then a line of embers marks the locked
    path (14 ticks), then a 10-tick short blast down that line (range 16): 8 once per shot, alight 3 s.
  - Armoured Slam (within 7, 160): both fists overhead 24 ticks while a ring marks the reach, then a ground-pound shockwave
    rolls out to 8 blocks -- 10 to anyone on the ground as it passes (jump it).
  - Charge (6-18 blocks, 110): a 16-tick crouch with an ember line along the path, a 16-tick charge at 0.9 b/t (12 +
    thrown), an 18-tick stagger where it takes +25%.
  - Backhand (its melee: `doHurtTarget` starts it, 30-tick cooldown): the arm drawn across 10 ticks, then a swat: 9 to
    everyone in front within 4.2, knocked 1.6 away.
  - Phase 2 at half health: a 40-tick surge (untouchable, lightning pours in, the faceplate flies open), then Unleashed:
    speed 0.30, cooldowns -30%, damage +15%, the beam sweeps 65 degrees each side and back again (100 ticks), a double Fire
    Blast, a double slam.
- Mock-player gotcha (tests): a GameTest mock player's `isCreative()` is always true, so the boss uses
  `getAbilities().instabuild` for "creative".

### Stormbreaker, forged in a dying star -- `forge/StarForge`, `StarForgeBlock(Entity)`, `LivingBranch`, client `StarForgeRenderer`
- The **Stormbreaker Head** is the old `unforged_stormbreaker` item (same id, so old ones in inventories are heads already),
  renamed and re-drawn without a haft; recipe `NSN / N N` (4 netherite + a nether star, no blaze rods). A forged head carries
  custom data `StormbreakerForged` (glints, "Forged Stormbreaker Head").
- **Star Forge** block (`GLG / BNB / BFB`: gilded blackstone, lightning rod, polished blackstone bricks, netherite ingot,
  blast furnace; pickaxe, diamond tier). Works only inside a ring of 8 blackstone (any blackstone) on its level; anything
  done to an incomplete one answers "x placed, y missing" and marks the gaps with red dust.
- Channel: a worthy Thor holds right-click on the forge with Mjolnir (the client repeats the use every 4 ticks; each is a
  ping; > 8 ticks without one, walking > 4.5 away, putting the hammer away or breaking the ring breaks it and resets).
  200 ticks. Burns him 3 (in-fire) every 10 ticks -- survivable at full Thor health. Sparks, flame, lava, the core swelling
  white-hot, a bolt from his hammer into the head (renderer), a roar that builds (fire, blaze, anvil, beacon), a growing
  screen shake. Done: a strike, the head is forged. (The Lightning Beam key is not wired to it: the combat keys are being
  rewritten.)
- **Living Branch**: lightning (natural, or any visual-only Thor bolt -- `ThorAcquireLightningMixin`, a bolt's first tick)
  near jungle logs/leaves: 10% the tree drops one (glint, green motes, chime). Also 1 in 12 in jungle temple chests and 15%
  from Ankhatep's first defeat.
- Finish: the branch into the forged head's forge -> 70 ticks: green haft-light knits (0-22), lightning strikes the forge,
  Stormbreaker rises out of it in a pillar of light spinning to a stop, a burst, and the normal Stormbreaker item drops (it
  becomes the usual resting axe). The Nether-lava forging (`StormbreakerForge`) is removed.

### Tests -- `ThorAcquireV0177GameTests` (batches `thor_acquire_storm_on/off/crater_v0177`)
Valor from every source + capped + save/load + copyOnDeath; powered gets none; refused unworthy / no storm; a trial winner
lifts and binds; full Valor in a storm starts the trial (no bind); winning lifts the hammer; a loss blocks the rest of the
storm, the next storm allows a retry; powered refused and keeps the attempt; gaining a power mid-trial fails it; the
threat table (and powered hits do nothing); the Disintegration Beam (nothing during the wind-up, a hit every 5 ticks while
in it, about 10 each, the ground scorched, no fire blocks, powered hits still refused); an old `stormwarden` id / egg loads
as the Destroyer; the crater (plinth, hammer, protection on/off); the forge ring; the 10 s
channel; letting go breaks it; the branch finishes Stormbreaker; old Unforged Stormbreakers are heads; a struck jungle tree
(forced chance, and a real bolt) drops a branch. Updated: `PrimaryPowerGameTests` (full Valor, not Hero of the Village),
`HulkGameTests`, `StormbreakerGameTests` (head recipe; lava tests removed).

## v0.16.1: storm buff, dual-wield combo, Twin Thunder Strike, Thunder Field

Config `config/projecthero_thor.json` (`power/ThorConfig`, versioned, sections `storm` / `dualCombo` / `twinThunder` /
`thunderField` all balance-owned).

### Storm-Charged -- `power/ThorStorm`, `mixin/LivingEntityThorStormMixin`
- Condition: `level.isThundering()` and the biome at his feet has precipitation (rain or snow). Sky NOT required by
  default (`storm.requireSky = false`): the god of thunder feels the storm under a roof. "Thor" = worthy + (holding
  Mjolnir / Stormbreaker or bound) -- the same test as his ability keys.
- Effect: +`storm.damageBonus` (0.30) on every hit whose `DamageSource.getEntity()` is that player. ONE hook: a
  `@ModifyVariable` at `LivingEntity#hurt` HEAD (like the Punisher mark), paired with a RETURN inject. Double-count
  guards: (1) a per-thread stack of victims being hurt by a scaled hit -- a hit re-issued from inside the same `hurt`
  (Thor's 80% reduction, Nova, Moon Knight...) on that victim is not scaled again; (2) the single-weapon combo
  finisher's re-hurt (whose amount comes from Fabric's `AFTER_DAMAGE` base, which already carries the bonus) runs in
  `ThorStorm.unscaled`. The stack is cleared every player tick (nothing is inside a hurt then).
- Tell: `ThorFx.stormPct` (synced) -- the client draws little arcs crackling over him (`ThorFxRenderer.stormCrackle`),
  the server sends sparks, and the HUD shows "Storm-Charged +30%" above the keys.

### Dual-wield combo -- `power/WeaponCombo` (`WEAPON_DUAL`), client `thor/WeaponComboPose`
- Mjolnir in one hand and Stormbreaker in the other (either way round): `WeaponCombo.comboWeapon` = `WEAPON_DUAL`, 4
  steps (`DUAL_HITS`). 1 Mjolnir overhead blow, 2 Stormbreaker flat sweep, 3 cross-body double swing (X), 4 leaping
  double slam (the server hops him: `dualCombo.slamHop` 0.42, ground only). Window = swing recharge +
  `dualCombo.windowTicks` (20 = 1 s). Damage = the vanilla hit with the main-hand weapon, untouched.
- Poses: third-person tables `DUAL_1..4` written for Mjolnir on the right (mirrored when it is on the left), with
  anticipation (ticks 0-2), the snap (4), follow-through (7), recovery (13 / 16); hips twist (bodyY) and HipBend.
  First person: per-hand tables (`FP_D*`), `ItemInHandRendererComboMixin` now also calls the off hand while dual.
- 4th hit in the window -> `ThorTwinThunder.queue`.

### Twin Thunder Strike -- `power/ThorTwinThunder`, client `thor/ThorTwinFrames` + `ThorFxRenderer.twinThunder`
- Only while dual wielding, cooldown `twinThunder.cooldownTicks` 400 (20 s, `ThorAbility.TWIN_THUNDER`, started when it
  queues; the action bar says when it is still recharging). Starts `DELAY` 12 ticks after the 4th hit (the hop lands),
  synced as `ThorFx.ANIM_TWIN_THUNDER`: tick 0 raise, 8 a visual bolt onto each weapon head + arcs between them, 16 the
  slam (`twinThunder.damage` 20 within `radius` 6, knock-up 0.7 / back 0.9, bosses not moved, debris rings, screen shake
  via `TitanShakePayload` within 24 blocks, glowing cracks + shockwave client-side), 19 a ring of `ringBolts` 8 bolts
  at `ringRadius` 6.5, each hitting hostiles within `ringSplash` 2 for `ringDamage` 8. Never the wielder, pets, squad
  allies or their pets (`WeaponCombo.friendly`); the storm buff applies via the hook. Static `PENDING` map, ticked on
  END_SERVER_TICK, cleared by `ServerStateReset`.

### Thunder Field (Shift+G) -- `power/ThunderField`
- Binding: Shift+G (plain G stays Lightning Strike; the Shift variant was free). Throws the main-hand Thor weapon (else
  the off-hand one) at the aimed ground within `thunderField.range` 24 (a block-clip along the look; a wall / ceiling hit
  drops to the ground at its foot; nothing in range = action-bar message, no cooldown). Re-uses the real
  `MjolnirEntity` / `StormbreakerEntity` throw, re-aimed at the spot.
- Landing (Mjolnir leaves THROWN / Stormbreaker turns home, or 40 ticks): the weapon is pinned there for the field's
  life (`pinAt`, a synced `DATA_PINNED` on both entities; Stormbreaker renders blade-down in the ground), a visual bolt
  hits it, and the field forms: radius 5, 80 ticks; every 10 ticks hostiles (rule 2) within it (and within 2 blocks
  vertically) take `pulseDamage` 4 (+ storm) and Slowness II. No fire, no block damage. Then the weapon flies home as a
  thrown one always does; the call key pulls it out at once. Cooldown 300 (15 s, `ThorAbility.THUNDER_FIELD`), shown as
  an amber bar along the bottom of the G box.
- Look: `ThorFx.field*` (separate from the one-shot move slot) -> `ThorFxRenderer.thunderField`: glowing disc + rim,
  arcs crawling over the ground, arcs from the weapon to the rim, short bolts out of the air.

### Stormbreaker model rebuild -- `scratchpad/gen_v01419_stormbreaker_assets.js` (edit the generator, not the outputs)
- 76 boxes instead of 12: a crescent blade built row by row (half-pixel rows, each a dark Uru body + lighter ground
  bevel + thin bright cutting edge, so the edge arcs, the horn sweeps up, the beard droops and the back curves away
  from the haft), a rune strip on the blade and a rune band on the head, a stepped hammer poll with a polished face,
  a wandering, knotted Groot-branch haft, alternating leather wraps, a collar and a capped pommel. Block-projected UVs
  so grain / wraps / brushing run continuously across the stepped boxes. Textures now 32x32 (new
  `stormbreaker_bevel`). Same bounding convention and display transforms as before; `stormbreaker_thrown.json` is the
  same geometry unrotated. A pinned (Thunder Field) axe renders blade-first in the ground (`PINNED_LIFT` 0.66).
  **Superseded in v0.17.7, see "v0.17.7: Mjolnir and Stormbreaker models" below.**

### v0.17.7: Mjolnir and Stormbreaker models
Screens and films: `docs/screenshots/v0177/thor_weapons/`. Offline previewer (textured rasteriser, gui / fixed views):
`node scratchpad/preview_thor_weapons_v0177.js <out.png> mjolnir stormbreaker ...`. Client harness:
`scratchpad/ThorWeaponsHarness.v0177.java.txt`.
- **Mjolnir** -- new generator `scratchpad/gen_v0177_mjolnir_assets.js` (the old model was a hand-fitted Blockbench
  export with no generator). 17 boxes: a 9 x 4.5 x 4.5 head with every edge chamfered in two steps (four boxes: 0.75
  cut + 0.5 rounding step, the steps on the bright `mjolnir_bevel`), Celtic-knot side panels (a closed two-strand
  figure-eight interlace with over-under, in a recessed frame), framed striking faces, a plain brushed top; stepped
  collar, leather spiral grip between two Uru bands, chamfered pommel, eyelet and a leather wrist-strap loop hanging to
  y -2.25. Textures 64x64 at 4 texels per model pixel: `mjolnir_head` (face atlas), `mjolnir_metal`, `mjolnir_leather`
  (regenerated, same paths) and new `mjolnir_bevel` / `mjolnir_strap` (16x16). Bounding convention kept (x/z centred on
  8, head top y 14.375), so `MjolnirEntityRenderer.UPRIGHT_LIFT` and the hand transforms are unchanged (same grip);
  gui / fixed are now fitted to the slot / frame by the generator (it used to sit small in both).
- **Stormbreaker** -- same generator, rebuilt: 63 boxes, **24 px long = 1.5 blocks** (y 0..24; elements run past the
  16 px box, Minecraft allows -16..32). A faceted crescent blade whose every outline segment is a straight element at a
  multiple of 22.5 degrees (honed edge 0.4 wide / ground bevel 0.8 / dark body, each thinner than the next so the edge
  is a crisp bright step), horn swept up, beard hooked down along the haft, lit rims along the back edges, a rune strip;
  a chamfered head with a rune band and branch-end crown, a stepped poll (root, waisted neck, block, polished face); a
  long Groot-branch haft (knots, a vine), a leather grip y 6..12 between two ties, bare branch below it, ferrule + cap.
  Held model: each facet's own rotation is composed with the -45 degree icon bake into one rotation about a new origin
  (`place()`); totals outside -45..45 are brought in by turning the box a quarter turn exactly. UVs block-projected with
  v wrapped every 16 px (tiling textures). Textures 64x64; new `stormbreaker_blade` (the darker flat of the blade).
- **Size / grip.** Third person scale 1.0 = true size. Per the user, it is held like a greataxe: the fist sits at
  **y 9 of 24 px from the pommel** (it was y 4.2, the very end), the end of the haft sticking out below the fist --
  third person translation [0,-0.69,1.83] (the vanilla-style [0,4,1] slid 4.77 px down the baked handle axis), first
  person [0,-1.0,-4.3] at 0.72. Rotations are still vanilla `item/handheld`, so `ItemInHandLayerMixin`'s move-pose
  re-grip (-90 about the hand's X) still turns it about the fist. Mjolnir's grip is unchanged (fist at y ~4.2 of its
  0.5..14.4 handle, on the leather).
- **Entity renderer** (`StormbreakerEntityRenderer`): `stormbreaker_thrown.json` FIXED scale 1.0 (was 1.15) so the
  flying / resting / pinned axe is 1.5 blocks too; `FIXED_SCALE` constant, `FLAT_LIFT` from the 1.5 px half-thickness
  of the rune band, `FLAT_CENTRE` from y 12; new `SPIN_PIVOT` -- the tumble turns about model y 15 (the balance point
  under the head) instead of y 8. Pinned: measured from the ground found under the axe on the client (`groundY`),
  `PINNED_LIFT` 0.75 -- the client's copy of a just-pinned axe was seen 0.33..0.94 blocks inside the ground (it flies
  on until the next position sync; the entity type syncs at `trackedUpdateRate(20)`), which buried the new head.
- Not changed: the entity hitbox (0.6 x 0.6), `ThorFxRenderer`'s approximate raised-weapon points.
- **Clipping fix (v0.17.7, user: "stormbreaker model has some clipping on it")** -- audit with
  `node scratchpad/v0177/stormbreaker_overlap_check.js [stormbreaker|stormbreaker_thrown]` (lists every pair of same-facing
  faces on one plane, or under 0.019 px apart, that overlap and are not buried inside a third element; 0 / 0 now, was 20
  coplanar fights + 32 too-close pairs). Close-ups: `VIEW=head [FIGHT=1] [HC=x,y HS=span] node
  scratchpad/preview_thor_weapons_v0177.js out.png stormbreaker_thrown` (FIGHT paints z-fighting pixels magenta). What it was:
  neighbouring edge / bevel facets ran 0.12 px past every joint on the same plane (flicker at each joint), and at the horn and
  beard tips that overrun stuck the bright edge out past the horn / beard back (the light nub in the screenshot); the lit rims
  sat on the horn / beard back planes; the beard's start face sat on the last edge facet; the blade body pieces were only
  0.003 px apart in thickness (flicker at range); knot1 / vine3 and vine1 / tie_lo shared faces. Fix (generator): every second
  edge / bevel facet 0.03 thinner, joints overlap only w * tan(11.25 deg) (just enough to close a 22.5 degree turn), no overrun
  at the tips (the horn / beard strip already covers that corner), body pieces 0.02 px steps with the plates abutting, rims
  wrapped 0.03 proud and stopped short of the tips, the handle bits nudged apart. `strip()` takes `[past p, past q]`. Size,
  look, grip and display transforms unchanged (gui / fixed fit moved by 0.001). Stills: `docs/screenshots/v0177/bifrost/clip_*`.

### HUD -- `client/gui/ThorHud.renderStatusRow`
- Above the keys: 4 combo pips while dual wielding (gold up to the landed step; grey + seconds while Twin Thunder
  recharges) and "Storm-Charged +30%" on the right (a line higher if it would overlap the pips).

### Tests -- `ThorV0161GameTests`
- storm buff only while thundering and only once (ability hit, melee hit, finisher ratio, non-Thor unaffected); dual
  combo 4 steps / window / reset / back to single; Twin Thunder only on the 4th hit in the window and never with one
  weapon; it hits an enemy for 20 but never a squad ally or pet; Thunder Field shocks + slows an enemy, not a pet or a
  squad ally, then expires; the Shift+G throw pins Mjolnir, forms a field and comes home.

## v0.15.3: N weapon selector, Stormbreaker on the ground

### N weapon selector -- `hammer/ThorWeaponSelection`, client `gui/ThorWeaponScreen`
- N opens a small screen (gold on night blue, like the Bifrost menu): one row per weapon -- icon, name, "Bound to you" /
  "None bound to you", and an Active / Inactive toggle (tooltip says what a click does). Intro and the both-off warning
  are word-wrapped to the panel (`font.split`). N again (or Done / Esc) closes it.
- Client routing: an `else if` in `ProjectHeroModClient.handleMaxSteelTransform`, just before the mutation Utility 2
  fallback, gated on `ThorWeaponSelection.ownsSelector` = worthy and (holding Mjolnir/Stormbreaker, or bound to
  Mjolnir, or bound to a Stormbreaker) -- the same rule on both sides. Nothing else owned N for Thor: the server's
  `AbilityRouter` gives Thor's context slots 1-6 only and dropped slot 8, and `mutationHasUtility` is already off while
  a Thor weapon is held.
- State: `ModAttachments.THOR_WEAPONS_INACTIVE` -- an int, bit `1 << ThorWeapon.ordinal()` set = that weapon INACTIVE;
  0 (default) = both active = the v0.15.1 rule. Persistent, `copyOnDeath`, synced to the owner (the screen reads it,
  so it always shows the server's state). No static state.
- Writes: the toggle sends `ThorWeaponTogglePayload(weapon ordinal, active)`; `ThorWeaponSelection.handleToggle`
  re-validates (index in range, `ownsSelector`) and answers on the action bar.
- Recall (`MjolnirRecall.call` / `chooseWeapon`): both inactive -> nothing is called, action bar
  `message.projecthero.recall.none_active`, `call` returns false. One inactive -> it is never called (not even when it
  is the closer or the only bound one); the other is treated as the only weapon (v0.15.1 rule 3: already in hand /
  swap from the pack / called home). R is the only weapon-calling path -- Thor's Armour (H) calls no weapon.

### Stormbreaker on the ground -- `StormbreakerEntity` resting state
- Like Mjolnir: a loose Stormbreaker `ItemEntity` is promoted on its first tick (`ItemEntityMixin
  #promoteToStormbreakerEntity`, replacing v0.15.1's "stay an item but track it") into a RESTING
  `StormbreakerEntity` carrying the real stack and the item's toss velocity -- Q, dragging it out of the inventory, death
  drops, dispensers, and a freshly forged axe (the forge runs earlier in the same tick). The /give decoration
  (`NEVER_PICK_UP`) is left alone; a stale ghost is just discarded.
- Also resting, as the SAME entity: a throw whose thrower is gone (offline / dead / another dimension; it used to become
  an item) and a non-recall catch with a full pack (waits at the owner's feet).
- Resting (`DATA_RESTING`, synced, saved as `Resting` + `PickupDelay`): gravity 0.055 (Mjolnir's), stops dead on the
  floor (`landOn` sets it exactly on the hit surface; walls only kill sideways speed), floats up out of lava (negative
  gravity, rise capped at 0.04/tick) so a forged axe can be fished out; heading held steady; no flight effects; hits
  nothing. Never despawns (a projectile) or burns (fire-immune type).
- Pickup: right-click only (`interact`, `isPickable` while resting), after a 40-tick grace, and only for
  `StormbreakerEntity.canLift` = creative, or worthy and not the Hulk (Mjolnir's rule minus Hero of the Village
  ascension); anyone else gets Mjolnir's throttled clang. Main hand if free, else the pack, else it stays. An unworthy
  player still can't grab the item form during the one tick before promotion (`ItemEntityMixin#blockUnworthyPickup`).
  Holding/swinging it is unchanged: anyone may still carry Stormbreaker that reaches them some other way (a chest).
- Tracking/anti-dupe: registry status `RESTING`, entity id recorded on its first tick; stale copies discard themselves
  before registering (as before); R (`MjolnirRecall.answerLoaded`) calls `recall` on the resting entity itself, which
  clears resting and flies the same entity home.
- Render (`StormbreakerEntityRenderer`): resting = lying flat on its side along the landing yaw (model +Y along the yaw,
  blade face up, lifted by half the model's 2.8 px thickness at the 1.15 FIXED scale; v0.17.7: 3 px at 1.0).
- Tests: `ThorWeaponSelectV0153GameTests` (toggle default/persist/validation; inactive never called even when closer;
  inactive only-bound weapon stays put; both inactive -> nothing; Q-drop becomes the entity, registry RESTING, unworthy
  can't lift, save/load, worthy can; dropped axe recalled home as the same entity; ghost resting axe deletes itself).
  `StormbreakerGameTests#anOrphanedAxeLiesDownAsItselfAndStaysPut` and `ThorRecallV0151GameTests` updated for the
  entity. Lang: `scratchpad/lang_v0153_thor.js`.

## v0.14.20: Mjolnir / Stormbreaker 3-hit melee combo, Stormbreaker hold fix

### Combo -- `power/WeaponCombo` (+ `WeaponComboState`, `mixin/PlayerAttackComboMixin`)
- Any wielder (no power gate), main hand only. A melee hit counts when it LANDS (confirmed through `AFTER_DAMAGE`,
  not blocked) at attack strength >= 0.9 (`MIN_STRENGTH`, read at `Player.attack` HEAD before vanilla resets it).
  Steps 1 -> 2 -> 3 -> 1. Weak hits neither advance nor reset; misses are ignored; switching weapon starts over.
- Window: the next step must land within `windowTicks` = the weapon's swing recharge (ceil of
  `getCurrentItemAttackStrengthDelay`, 19 ticks Mjolnir / 23 Stormbreaker) + 25 ticks (1.25 s), else it starts at 1.
- Step 3 (finisher): +50% (`FINISHER_BONUS`) of the hit's own damage on the target -- dealt by re-hurting it at 1.5x
  inside its hurt cooldown, so vanilla applies exactly the difference (works on players too). Mjolnir: shockwave of
  4 damage + knockback 0.8 / lift 0.25 within 3 blocks of the target, sparks + thunder crack. Stormbreaker: cleave of
  6 damage + knockback 0.6 within 4.5 blocks in a 150-degree arc in front of the wielder. The area hit skips the
  target, the wielder, their pets (`OwnableEntity`) and squad allies (`Squads.areAllies`); bosses are never knocked
  back. Base weapon damage is unchanged.
- State = the synced, non-persistent `ModAttachments.WEAPON_COMBO` (`step, weapon, start`): both the combo's own state
  (no static map) and what every viewer animates from. `WeaponCombo.setForTests` for tests.

### Swing animations -- client `thor/WeaponComboPose`, `mixin/ItemInHandRendererComboMixin`
- Third person: keyframe tables (ThorPose technique) per weapon per step -- Mjolnir one-handed (flat swing, high-left
  to low-right backhand, overhead slam with the free arm thrown wide), Stormbreaker two-handed (flat cleave, rising
  backhand, two-handed overhead chop). Applied in `HumanoidModelMixin` just before `ThorPose` (a Thor move wins).
  `weaponGrip` re-grips the held weapon along the arm in `ItemInHandLayerMixin` (the same -90 degree turn as ThorPose).
- First person: the weapon is moved about the hand before it is drawn; vanilla's swing and post-hit lowering of the
  main-hand item are scaled out by the swing's weight (`@ModifyArg` on the main-hand `renderArmWithItem` call).
- The local player's step is predicted on click (client `AttackEntityCallback`), so it starts with the click; the
  server's stamp of the same step takes over without a jump. Other players see the synced state.

### Stormbreaker hold
- v0.14.19 copied Mjolnir's handheld display but had the axe blade on +X, which the -45 degree icon-diagonal bake puts
  on the side a vanilla axe sprite does NOT have its blade: held blade-up ("upside down"). The generator
  (`scratchpad/gen_v01419_stormbreaker_assets.js`) now mirrors the geometry in X (blade -X, hammer +X) and uses the
  vanilla `item/handheld` rotations ([0,-90,55] third person, [0,-90,25] first person) with its own translation /
  scale (first person bigger and moved so the whole head shows), gui = the plain tool diagonal. Verified side by side
  with Mjolnir and an iron axe in the client screenshot harness.
- Thrown (`StormbreakerEntityRenderer`): the model is turned a quarter about Y before the tumble so it spins in its own
  blade plane (it used to spin about an axis through the blade, the blade sticking out sideways).
- Tests: `WeaponComboGameTests`.

## v0.14.4: squad safety, piece-by-piece armour, move animations, new effects

### Squad safety -- `power/ThorTargets`
- `ThorTargets.canAffect(caster, target)` is the single rule every Thor power goes through: never the caster, a
  squadmate (`Squads.areAllies`), a pet owned by either (`OwnableEntity`), another player while PvP is off, or an armour
  stand. It gates damage **and** knockback, Slowness and the animal singe-fire, not just the hit.
- Wired into: Lightning Strike (primary + auto-chain), Chain Lightning (`chainLightningTargets`), Lightning Beam,
  God of Thunder's Wrath (`wrathTargets`), Thunderclap, Storm Call, the thrown hammer (`MjolnirEntity#canHitEntity` -- it
  flies straight past allies instead of stopping on them) and Hammer Volley (target pick + strike).
- Aim rays (`ThorPowers.aimable`) treat spared entities as see-through, so a bolt/beam goes past a squadmate standing in
  the way onto the enemy behind.
- Thor's lightning damage is now `ThorTargets.lightning(level, caster)`: still the `lightning_bolt` damage type (Thor's
  lightning immunity and every other lightning rule unchanged) but with the caster as attacker, so the squad
  friendly-fire veto, PvP rules, kill credit/XP and mob retaliation all see who threw it.
- Storm Call no longer spawns a real vanilla `LightningBolt` (which hit everything in 3 blocks, lit fires and converted
  villagers/pigs/creepers): a visual-only bolt + 8 damage to what `canAffect` allows within 3 blocks.
- Every other Thor bolt was already visual-only (`setVisualOnly(true)`), so it never damages or ignites anything itself.

### Synced move state -- `power/ThorFx` + `ThorVisuals`
`ModAttachments.THOR_FX` (synced to all, never persisted): last one-shot anim (id, start tick, world point), channel bits
(`CH_BEAM`, `CH_WRATH` + `chargeStart`) and the armour suit clock (`suitDir`, `suitStart`). Only `ThorVisuals` writes it,
and only on change (the per-tick channel calls are free on the wire).

### Thor's Armour suit-up -- `thorarmor/ThorArmor`, client `thor/ThorSuitReveal`
- H equips all three pieces on the first tick (the protection is instant and nothing can be interrupted), and starts
  the `SUIT_UP` clock *before* equipping so no viewer sees a frame of the full set.
- v0.17.7 look (user: "no i still want the suit and armour i was just saying remove thors actual skin form the suit and keep the
  helmet"): the **full suit** from the PlanetMinecraft "Thor | Avengers" skin (https://www.planetminecraft.com/skin/thor-avengers-6321094/)
  with **Thor himself taken out**, plus the **red cape**. `textures/armor/thor.png` is built by `scratchpad/v0177/make_thor_skin.js`
  from the original download beside it (never edit the PNG by hand):
  - head: only the helmet (silver crown + brow band, steel cheek guards, grey wing roots; hat layer: white wings, silver crest). Face,
    eyes, brows, blond hair/beard and the neck opening are transparent, so the wearer's face shows under the helmet;
  - body/arms/legs: Thor's skin tones (neck, bare upper arms, hands) are transparent; his long hair (over the shoulders, down the
    tunic's back) is repainted as tunic; the tunic back (base u32-39 v20-25) is rebuilt mirroring the front (collar, four blue
    discs, seam) and the jacket back gets the front's disc highlights and belt studs, mirrored;
  - the painted cape (second layer: jacket back/sides/bottom, leg backs, arm backs) is cleared; over the shoulders its red becomes
    dark-steel shoulder plates; the red band on the bracer row is the bracer's trim and stays.
  `SuperheroArmorRenderer.applyThorPieces` draws boots / greaves / chestplate (body + arms) in their own slots and the `armorHead`
  bone (the helmet) on the chestplate's pass while the HEAD slot is empty. Slim (3 px arm) wearers get the arm boxes narrowed by the
  same `renderCube` squeeze as the Symbiote host's suit. First person: `SuperheroFirstPersonArm` draws the armoured sleeve (shoulder
  plate, strap, bracer) once the chestplate part has arrived; the player's own hand shows through the cleared hand texels. Stats
  unchanged (chest+legs+boots). Cape: `ThorCapeLayer` (shared `FlowingCapeLayer`), `textures/entity/thor_cape.png` in the skin's
  cape reds.
- Timeline (`ThorArmor.partProgress`, parts `PART_BOOTS` / `PART_GREAVES` / `PART_CHEST` / `PART_HELMET` / `PART_CAPE`):
  `SUIT_UP_TICKS` 60 -- boots 6-12, greaves 14-20, chestplate 22-30, helmet 32-38, cape 38-52 (unrolls from the collar via
  `unfurl`). **v0.17.7 polish: pixel by pixel again** -- each piece builds on the body texel by texel over its window (see
  "v0.17.7 polish" below; the earlier whole-piece pop-and-settle `ThorSuitReveal.pieceScale` is gone). Visual-only vanilla bolts round the wearer at ticks 6/10/14/18/22/26/32/36/42/48 (plus the
  opening bolt at 0), a rising crackle every 3 ticks, a clink (`pieceArrives`) as each part locks on (window end), and the final
  bolt + flash + layered thunder at `FINAL_BOLT_TICK` 54. The client also draws a sky bolt as the greaves and the chestplate start
  (`ThorFxRenderer.suitBolts` via `arrivalTick`). No spark particles in the suit-up or removal.
- H again starts `SUIT_DOWN` (26 ticks, the build backwards: cape 0-8, helmet 8-12, chestplate 12-17, greaves 17-21, boots 21-25;
  each piece's pixels vanish in the reverse order, with a crackle and an unclasping clink, `pieceLeaves`); `ThorArmor.tick` strips the
  pieces when it ends and clears the clock 20 ticks later (clearing at once could flash the suit back on for a frame).
- Toggle debounce: `SUIT_UP_TICKS + 4` after summoning, 20 after dismissing. Death / the once-a-second audit still
  strip instantly.
- Hooked in `SuperheroArmorRenderer#applyBoneVisibilityBySlot` (`applyThorPieces`); no texture swap any more.

### v0.17.7 polish -- pixel suit-up, blue XP bar, flight trail, vanilla landing sounds
Films and stills: `docs/screenshots/v0177/thor_polish/`. Harness: `scratchpad/ThorPolishHarness.v0177.java.txt` (built from the skin
harness + `scratchpad/v0177/thor_polish_plan.java.txt` by `thor_polish_build.js`; install / remove with `thor_polish_harness.js`,
films with `thor_polish_film.js`). No gameplay number changed.
- **Suit-up pixel by pixel** (user: "change the suitup animation, the suit should appear pixel by pixel like i said before please"):
  `client thor/ThorSuitReveal#texture`, swapped in by `SuperheroArmorRenderer#getRenderType` per slot pass (and by
  `SuperheroFirstPersonArm` for the first-person sleeve). Like the old `ArmorSweepReveal`: pre-built copies of `textures/armor/thor.png`
  with the not-yet-formed texels cleared; each texel's order comes from where it sits on the rest-pose `geo/thor.geo.json`
  (`ArmorSweepReveal.forEachTexel`): boots and greaves fill from the ground up, the chestplate spreads out from the sternum (shoulders
  and bracers last), the helmet climbs from the neck to the wing tips; 76% body order + 24% fixed per-texel scatter, 24 steps per piece,
  so the edge is ragged single pixels. The newest texels flash storm white (`#EAF6FF`), the row behind a pale blue blend. The boots
  share the greaves' texels in the UV, so each slot pass has its own frames (FEET = boots, LEGS = greaves, CHEST = chestplate then
  helmet, which never overlap in time, so their summed progress picks the frame). Bones are only hidden until their part starts; no
  more scaling. **Cape**: `ThorCapeLayer` shows it full length once its part starts and its texture (`ThorSuitReveal#capeTexture`)
  fills from the collar to the hem (v runs down the cloth); on H it empties hem first. Complete = the shipped textures untouched, so
  the finished look is unchanged. Bolts, crackle, clinks, final bolt + flash and the part windows are unchanged. Frames are built
  lazily on the render thread and cached (texture ids only).
- **Storm Energy as a blue XP bar** (user: "Can you make thors bar look like a blue xp bar please"): `ThorHud.stormBar` replaces the
  Hairline meter row: the vanilla experience bar's own background + progress sprites via `SentryHud.xpBar` (now public; the Sentry's
  Sun / Void bars use it), progress tinted `#3DA5FF` (`#2A4E9A` under 20%, top row breathing softly when full), across the column
  (at most 182 px, right-aligned), the percentage centred above it in the XP level's black-outlined digits (`#8FC8FF`, `#6F8FD8` low)
  and the "Storm" label at the left, cut to fit. 17 px row in the shared column; Left-Alt names / move list unchanged.
- **Flight trail** (user: "Change the flight vfx please, remove the particles, make it a very slightly trail behind him please"): the
  server's `ThorPowers.tickFlightTrail` (electric sparks + clouds every other tick) is removed. Client `thor/ThorFlightTrail` samples
  each flying Thor's body centre every client tick and draws a faint camera-facing streak through the last 6 ticks: a soft
  `#9CCBFF` band (alpha 0.16, half width 0.2) with a thin `#EAF5FF` core (0.22, 0.045), width and alpha falling off with the square
  of age, starting 0.45 blocks behind him, and only once the streak is over 0.6 blocks long (hovering leaves nothing). Not drawn for
  your own body in first person. Samples are plain coordinates per entity id, cleared on a world change / disconnect.
- **Landing sound** (user: "Also change the landing sfx please, make it existing minecraft sounds so that it doesnt sound ai
  generated please"): the Thor hero landing (`HeroLandingFx.thor`) no longer plays the synthesised `hero_landing_impact` thud or
  `hero_landing_thunder` crack. `HeroLandingFx.thorSounds`, by strength s: `MACE_SMASH_GROUND` (`_HEAVY` from s 0.55), vol
  0.55-1.0, pitch 0.92 -> 0.80; the landed-on block's fall sound pitched down (x0.72 -> 0.64); `LIGHTNING_BOLT_IMPACT` quietly with the
  hammer bolt (s 0.3+); a soft low `LIGHTNING_BOLT_THUNDER` rumble on a hard landing (s 0.6+, vol 0.12-0.5, pitch ~0.65). The
  `hero_landing_thunder` event, its `sounds.json` entry, subtitle and `sounds/hero_landing/thunder.ogg` are deleted (nothing else used
  them); the shared impact thud stays for the other styles. The flight on/off breeze sounds were already vanilla and are unchanged.

### Move animations -- client `thor/ThorPose`
Hooked as its own `HumanoidModelMixin` TAIL injection declared **after** the flight pose, so a move made mid-flight
overrides the flight arm pose. Keyframes (smoothstep, fade-out over the last 4 ticks), written for a right-hand hammer
and mirrored when Mjolnir is in the left hand. Strike (hammer to the sky, then down), Thunderclap (two-handed overhead
slam, slam lands ~tick 3), Chain Lightning (aimed thrust), throw (aimed follow-through), Hammer Volley (fling + direct),
Wrath release (aimed chop), Storm Call and suit-up (hammer to the sky); held: Beam (braced along the aim, free hand on
the wrist) and the Wrath charge (hammer straight up, other arm wide, trembling as it builds).

Each frame also carries a `grip` weight: `ItemInHandLayerMixin` turns the held hammer `-90 deg * grip` about the hand's X
axis, which lays the hammer's long axis along the arm (head to the sky when raised, at the target when thrust out)
whatever the arm angle. The old HOVER flight correction is scaled by `1 - ThorPose.armWeight`.

### Effects -- client `thor/ThorDraw`, `ThorLightningArcRenderer`, `ThorFxRenderer`
- `ThorDraw.bolt`: a jagged node path drawn as three camera-facing ribbons (halo `0x2F5BFF`, glow `0x7FC4FF`, core
  `0xF4FAFF`) with `entityTranslucentEmissive` over the shared white texture. Replaces the 1-px `RenderType.lines()`
  bundles.
- Lightning Beam / Chain Lightning arcs: main channel + a second twisting channel + 2-3 forks + flares at both ends,
  re-shaped ~7-9x a second with a brightness flicker; the Beam is drawn 1.35x heavier. While the Beam / Chain pose is up
  the arc leaves the hammer head at arm's length (`ThorLightningArcClient.hammerHandPosition`) -- except in your own
  first-person view, where it keeps the old hip start and skips the start flare so nothing sits across the crosshair.
- `ThorDraw.flare`: a small orb core with six jagged sparks whose directions jump every couple of ticks.
- `ThorFxRenderer` (per player, within 128 blocks): Thunderclap shockwave ring + light wall + ground lightning; strike
  impact ring; Wrath double ring + ground lightning + central blaze; Wrath charge orb, lashing arcs, foot ring and sky
  bolts past half charge; suit-up sky bolts.

### Tests
`ThorSquadGameTests` (target rule incl. pets, Thunderclap no shove/slow on the mate, Chain Lightning and Wrath target
lists, Lightning Strike through a mate, piece timeline); `ThorArmorGameTests#hTogglesTheArmourOnAndOff` now waits for
the dissolve. Lang: `scratchpad/lang_v0144_thor.js`.

## v0.14.19-0.14.20: Stormbreaker and the Bifrost -- `stormbreaker/*`

Stormbreaker (v0.14.19): `StormbreakerItem` (14 melee, worthy-only powers), `StormbreakerEntity` (piercing returning
throw), `StormbreakerForge` (10 s in Nether lava), `ThorPowers.isHoldingThorWeapon` (counts as Thor's weapon).

### Bifrost (reworked v0.14.20) -- `stormbreaker/Bifrost`, `BifrostWaypoints`, client `gui/BifrostScreen`
- **Sneak + right-click** (worthy, main hand) sends `BifrostScreenPayload(open=true)`: the three waypoints, the
  cooldown ticks left and the player's dimension. The only exception: sneak-clicking a block within reach with
  something in the off hand returns PASS, so off-hand block placement still works.
- **Screen:** X / Y / Z fields (prefilled with the player's block position) + *Open Bifrost*; three waypoint rows
  (name field, coordinates + dimension, *Save here*, *Go*, clear); a rainbow cooldown bar. *Go* is greyed out with a
  tooltip for a waypoint saved in another dimension. Buttons send `BifrostActionPayload` (TRAVEL_COORDS /
  TRAVEL_WAYPOINT / SAVE / CLEAR); save/clear/failures answer with a refresh (`open=false`).
- **Waypoints:** `ModAttachments.BIFROST_WAYPOINTS` -- persistent + `copyOnDeath`, exactly 3 slots, names cleaned and
  capped at 20 chars (blank = "Waypoint N"). Saved server-side from the player's own position; travel to a waypoint
  uses the server's copy, never client coordinates.
- **Validation (`Bifrost.travelTo`)**, in order: worthy + Stormbreaker in either hand + alive + not spectator
  (`NOT_HOLDING`); cooldown (`COOLDOWN`); same dimension for waypoints (`WRONG_DIMENSION`); Y within build height and
  X/Z inside the world border (`OUT_OF_BOUNDS`); a safe landing (`NO_LANDING`). Any failure spends no cooldown and
  shows `message.projecthero.bifrost.fail.<result>`.
- **No distance limit** beyond the world border. **Same dimension only** (cross-dimension was left out on purpose:
  portals, the End fight and Nether-roof landings make it more than a teleport call).
- **Safe landing (`findSafeLanding`)**, same column only: from Y, if open air, drop to the first floor; otherwise (or
  if the drop hits something unsafe) climb to the first gap. A spot needs two free blocks (no collision, fluid, fire,
  powder snow, berry bush or cobweb) over a solid floor that is not lava, magma, a campfire, cactus or powder snow --
  or a water surface. A column with nothing safe (the void) is refused.
- **Riders (v0.17.7: see "Bifrost Travel cinematic" -- now also every player within 4 blocks and pets, and the trip is a
  cinematic, not an instant teleport):** the user + every `Squads.areAllies` squadmate within **8 blocks** (3D), same level, alive, not a
  spectator and not sneaking (sneak = stay behind). Each keeps their X/Z offset and gets their own safe landing in
  their column if it is within 6 blocks up/down of the user's; otherwise they share the user's spot. Non-squad
  players are never carried. Everyone is dismounted (`stopRiding` / `ejectPassengers`), teleported with
  `teleportTo(level, ...)`, zeroed momentum and fall distance, with the rainbow column at both ends.
- **Chunks:** the destination chunk (and each ally's column chunk) gets a `POST_TELEPORT` ticket and is loaded
  (generated if need be) before the landing search.
- **Cooldown:** 60 s in `ModAttachments.BIFROST_READY_AT` (overworld game time, persistent + `copyOnDeath`). No
  vanilla item cooldown any more, so the throw is never held. Shown in the screen; the action bar names the seconds
  left on a refused attempt and after each trip.

Tests: `StormbreakerGameTests` (Bifrost batch) -- waypoints save / persist through an NBT save-load / clean names;
travel carries a nearby squadmate but not a distant one or a non-squad player; second trip blocked for 60 s; out of
bounds / other-dimension waypoint / empty slot / unworthy refused; landing climbs out of stone and drops through air.
Lang: `scratchpad/lang_v01420_bifrost.js`.
