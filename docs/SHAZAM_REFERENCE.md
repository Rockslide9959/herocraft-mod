# Shazam -- reference

Billy Batson's power (`com.projecthero.mod.shazam`, client `client/shazam`). First built in v0.17.3 (design notes in
`docs/FATE_SHAZAM_DESIGN.md`, the Shazam section). This file covers the **v0.17.7 Planet-tier kit**; how the power is first obtained
(the Wizard, the Rock of Eternity) is documented by its own work and is not changed here.

User (v0.17.7): "can you rebalance shazam please hes A tier, and give him better moves aswell please, he needs more moves aswell give
him hold moves". "A" is the user's old name for **Planet** (`PowerRank.PLANET`).

## The word and the flight (unchanged)

Type just "Shazam" in chat: magic lightning strikes, he transforms (or turns back), the bolt deals 12 within 3 blocks and refills the
Lightning bar. 4 s between words. Double-tap Space in the air to fly. H is the power selector only; N does nothing for him.

## Planet tier

| Range | Planet | Shazam |
|---|---|---|
| Melee | 16-19 | fist 17 (+16 melee), combo 17 / 17 / 19 |
| Abilities | 16-20 | 16-20 (see the kit) |
| Ultimate | 32-40 | Thunderstrike 40 |

- **`ShazamConfig.MAX_HIT` = 40** (`PowerRank.PLANET.ultimateMax`). Every hit whose source is a transformed Shazam goes through
  `ShazamCombat.enter` at the top of `LivingEntity#hurt` (`mixin.LivingEntityShazamHitMixin`): Wisdom's crit, then the 40 cap, then
  the boss cap. That covers his moves, his bare fists and anything else that names him as the attacker.
- **Bosses** (`ShazamCombat.isBoss`: max health 300+, or a Titan / Wither / Dragon) take at most 6% of their max health per hit
  (300 HP -> 18) and are never lifted, launched or knocked about.
- **Toughness**: netherite-level -- armour +20, toughness +12, knockback resistance 0.4 (attribute modifiers in `Shazam.reconcile`;
  the old flat 35% cut in `ShazamDamage` is gone). Magic still hurts him 50% more. 40 health (+20), +40% speed, no hunger drain, fear
  immunity, unchanged.
- **Flight**: 18 b/s cruising, 34 sprinting (was 22 / 44; Thor, Cosmic, flies 18 / 36).
- **Family** members get half of every hit and half the health / melee bonus, and no ultimate.

## The kit (S-H-A-Z-A-M)

Keys: `ShazamAbilityManager`. Tap vs hold: on a key with a hold move the plain press waits; released before 8 ticks it is the tap
(fired on the release, the HoldTap / Spider-Man convention), still down at 8 ticks the hold move starts and the release ends it. Shift
at key-down fires the Shift move at once. X has no hold move, so it fires on the press. Every plain, Shift and hold move has its own
cooldown id.

| Key | Move | Damage | Cost | Cooldown |
|---|---|---|---|---|
| R | **Hercules Combo** -- jab, cross, hook finisher (knocks back); next punch within 24 ticks | 17 / 17 / 19 | free | 5 ticks between, 16 after the finisher |
| Shift+R | **Herculean Uppercut** -- launches (vy 1.25) | 18 | 10 | 4 s |
| Hold R | **Haymaker of Hercules** -- charges 1.5 s after the hold starts, auto-throws at 3 s; shockwave round the impact | 16 -> 20, shockwave 16 within 3.5 | 15 | 6 s |
| G | **Lightning of Zeus** -- bolt from the sky on the aimed target (40 blocks), chains to 2 more within 8 (aimed at nothing: 18 within 2 of the look point) | 18, chain 16 | 20 | 2.5 s |
| Shift+G | **Thunderhead Slam** -- 7-tick windup on the ground; in the air he dives first; a bolt onto him and a ring | 18 within 5 | 25 | 7 s |
| Hold G | **Zeus's Wrath** -- a beam from both hands (22 blocks), up to 5 s; `BeamClash.report` every live tick, no drain / damage while locked | 16 every 5 ticks | 4 a pulse | 6 s after |
| C | **Mercury Blitz** -- 14-block dash through up to 3 enemies in the path | 16 each | 10 | 3.5 s |
| Shift+C | **Thunderclap** -- shockwave cone (8 blocks, 45 degrees), heavy knockback, glass with mob griefing | 16 | 15 | 5 s |
| Hold C | **Mercury Rush** -- a hop beside the nearest enemy within 10 every 5 ticks (prefers a new one), 5 hits at most | 16 a hit | 6 a hit | 10 s (only if it hit) |
| Z | **Atlas Quake** -- 5-tick windup, a stomp that cracks the ground; Slowness II 2 s, knock up | 19 within 5 | 20 | 6 s |
| Hold Z | **Titan's Grip** -- grab (5 blocks), held overhead while the key is down (4 s max), hurled on the release; 20 on landing / a wall (or when the 2 s flight ends); a boss takes 20 (capped) but isn't lifted | 20 | 15 | 6 s |
| Shift+Z | **ULTIMATE "SHAZAM!" Thunderstrike** -- needs a full bar, spends it all; shout ("SHAZAM!" subtitle to players within 32), rise 16 ticks, colossal bolt onto him, release at 24 ticks; then **8 s thunder-charged**: every hit he lands arcs to the nearest other enemy within 8 (once every 4 ticks; a chain never chains) | 40 within 8, chain 16 | 100 | 90 s |
| X | **Mercury Dash** (12 blocks); while flying **Mercury Burst** (flight x1.6 for 2 s + a kick along the look) | -- | free | 25 ticks / 3 s |
| Shift+X | **Sonic Leap** -- vy 2.2 + forward 1.0, into flight after 12 ticks, no fall damage | -- | free | 3 s |
| V | **Wisdom of Solomon** -- enemies within 30 glow 10 s and take his hits as crits x1.25 (never past 40); the old health / weakness labels and tool hint stay | x1.25 | free | 20 s |
| Shift+V | **Courage of Achilles** -- Resistance II 8 s and knockback resistance 1.0 | -- | 20 | 30 s |
| Hold V | **Share the Power** (champion only; was Shift+V) | -- | -- | 10 min |

Removed from the v0.17.3 kit: Lightning Hand, Call the Lightning, Hercules Punch, Atlas Lift, Zeus Storm, Eternity Strike, Thunder
Tackle, Achilles Stand (their ideas live on in Lightning of Zeus, Titan's Grip, the Thunderstrike, Mercury Blitz and Courage).
`ShazamState.standUntil` now times Courage and `eternityStart` the Thunderstrike (old saves load fine; `stormUntil` is unused).

## Animations and effects

- **Synced state** (`ShazamState`): `anim` + `animStart` (a body clip, `ShazamAnim.*`), `hold` + `holdStart` (the hold move running),
  `thunderUntil`, `boostUntil`.
- **Third person** (`client/shazam/ShazamPose`, hooked by `client/mixin/ShazamPoseMixin` at priority 1500): the clip technique of
  `SuperSoldierPose` / `ThorKitPose` -- wind-up, a snap through the strike, follow-through, recovery faded back into vanilla; twist,
  hip lean, crouch. Clips: COMBO_1 / 2 / 3, UPPERCUT, HAYMAKER, ZEUS_CALL (aimed), SLAM (held raised during the dive), BLITZ, CLAP,
  RUSH_A / B, QUAKE (knee up, stomp), HURL, SHOVE (Titan's Grip on a boss), ULT (crossed arms, the shout with arms to the sky, spread
  and crackling at the bolt, flung out at the release), LEAP (fist up, held in the air), DASH, WISDOM (fingers to the temple, a sweep),
  COURAGE (fists to the chest, planted). Held poses: the Haymaker's cocked fist (trembling more as it charges), Zeus's Wrath's both
  arms along the aim, Titan's Grip's arms overhead. Left-handed players play them mirrored.
- **First person** (`client/mixin/ItemInHandRendererShazamMixin`): both empty hands play the move (jab / cross / hook, uppercut, cocked
  Haymaker, the hand raised to call the lightning, Wrath held out, hands overhead, the ultimate's raise and fling).
- **Sprint-flight pose** (v0.17.7, user: "when shazam sprint flies make it so that he puts both his hands in front of him and make it
  so that you see the hands going in front of you in first person"). Sprint-flying = `Shazam.isFlying`, airborne, and the shared
  flight's FAST tier (`FlightPoseHelper.tier`: sprinting, moving forward). `ShazamPose.clientTick` (from `ShazamClient`, every player
  in view) moves a per-player 0..1 blend by 1/5 a tick, smoothstepped when read (`ShazamPose.sprintFlight`): about 5 ticks in and 5
  out, never a snap. The Shazam Family share the state, so they get it too. Visual only.
  - Third person (`ShazamPose.flightPose`, applied first in `apply`): the body is already flat from the shared flight lean, so both
    arms go to xRot -PI + 0.1 (straight ahead, just under the head line), angled in 0.2 rad so the fists close up in front of the face,
    with a faint wind tremble; legs together, trailing straight back (30% of the flight scissor kept). A move clip or held pose is
    blended on top of it afterwards, so a move always wins while it plays and fades back into the flight pose. Symmetric, so it is its
    own mirror for left-handed players. Normal (non-sprint) flight is unchanged.
  - First person (`ShazamPose.firstPersonFlight`): both fists up in front of the camera toward the middle of the screen
    (`FP_FLIGHT`: in 0.22, up 0.42, forward 0.5, turned in 14 degrees), the off hand rising from below, the bare main arm moving over
    from its rest. **A held item is put away** for it: over the first half of the blend vanilla's equip dip lowers the item out of
    view (`@ModifyVariable` on `renderArmWithItem`'s equip progress), then both bare fists rise in its place; the reverse on the way
    out. A move's first-person hands still win over the flight hands.
- **Lightning** (`ShazamBoltFx`, gold-and-white): new IMPACT kind (a white-hot flash disc with gold spokes and crackle, on every punch);
  the Mercury blur is now gold and white (was red); held effects drawn every frame from the synced state -- Zeus's Wrath's two
  braiding bolts from the palms with a hot core, palm knots and a flare at the end (skipped while `BeamClashClient.hides` the owner),
  the Haymaker's gathering glow and in-drawn sparks, arcs up the arms under Titan's Grip. AURA on the Thunderhead windup, Courage and
  the 8 s thunder charge.
- **QuirkFx** layered on top: standing shock rings on punches, ground rings / crack decals / block shards on slams, gold light
  ribbons (uppercut, hurled creature, Mercury Burst), a soft 60-block wave for Wisdom.
- **Screen shake**: `TitanShakePayload` round the impact (finisher 0.3, Haymaker 0.45-0.95, slams 0.8-1.25, ultimate 1.0).
- **Sounds** (vanilla, layered): thunder, trident thunder, lightning impact, wind charge burst / throw, mace smash air / ground / heavy,
  breeze inhale / jump, beacon on / off for the Wrath, netherite equip for Courage, the SHAZAM word sound on the ultimate.

## HUD and Guidebook

`ShazamHud`: the key strip shows plain / Shift cooldowns, a running hold move lights its key; tags for Courage, Thunder-charged,
Burst, Haymaker charge %, Zeus's Wrath, Titan's Grip. The Left-Alt move list adds "Hold: ..." per key. The Guidebook / I menu chapter
(`HeroPackGuide.SHAZAM_CONTROLS`) lists every plain, Shift and hold move with its description, and the Planet badge shows under the
tier line (`PowerRank.ofGuideKey`).

## Tests

`ShazamKitGameTests` (new): keys and the Planet badge, every number inside Planet, the 40 cap, the combo, the uppercut and Shift's own
cooldown, the Haymaker charging and releasing through the real key path (and a quick tap being the combo), Lightning of Zeus' chain,
the Thunderhead windup, Zeus's Wrath pulsing while held and stopping on release, Mercury Blitz' three-in-a-line, Mercury Rush's five
hops, Atlas Quake, Titan's Grip holding and hurling, the Thunderstrike and its chain buff, no ultimate for the family, Wisdom's crit and
its cap, Courage, Sonic Leap into flight and Mercury Burst, the boss cap (no launch, no lift). `ShazamGameTests` lost its old move
tests and checks the new passives (17 fist, netherite toughness, magic x1.5) and the family's half damage.
