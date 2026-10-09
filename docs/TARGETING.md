# Targeting rules (v0.14.20)

Every player power picks who it hits through one class: `com.projecthero.mod.combat.HeroTargets`.
Boss / enemy AI (Titan boss, Oathbreaker, Behemoth, Darkseid raid, horde mobs) is **not** routed through it.

## The two rules

| Rule | Method | Hits | Used for |
|---|---|---|---|
| **1 - can harm** | `HeroTargets.canHarm(owner, e)` | every living thing: hostile mobs, animals, villagers, golems, neutral mobs, other players (PvP on) | deliberate, aimed attacks: melee abilities, beams, rays, thrown things, grabs, and the player's own slams / blasts / shockwaves centred on their attack |
| **2 - hostile** | `HeroTargets.isHostile(owner, e)` | `canHarm` **and** a threat: any `Enemy`, any mob targeting the owner or a squadmate, a neutral mob angry at them, whatever just attacked the owner, the thing the owner is fighting, and (PvP on) a player who hurt the owner or a squadmate in the last 10 s | anything that picks targets automatically or sweeps a huge area without aiming: turrets, sentries, summons' AI, homing / lock-on, chain jumps, auras and lingering damage fields, storms, 15+ block "hit everything" ultimates, target-marking scans |

**Never harmed by either rule:** the owner; dead entities; armour stands; spectators; creative / invulnerable players;
whatever the owner is riding or carrying; the owner's own pets and summons (tamed animals incl. Symbiote pets, Mirror
Images, Shadow Servants, the Titan Shifter's body); a squadmate's pets; other players while PvP is off (server PvP **or**
the mod's `abilityPvpDamage` config switch).

**Squadmates:** rule 1 follows the squad damage veto (`Squads.shields`): squadmates are spared unless the leader turned
friendly fire on (or one of them is a rampaging Hulk). Rule 2 never auto-targets a squadmate (`Squads.areAllies`), friendly
fire or not.

**Shared helpers:** `AbilityHelpers.enemiesAround` = rule 1, `AbilityHelpers.hostilesAround` = rule 2, and
`AbilityHelpers.hurt` / `hurtBurst` refuse any non-player target rule 1 forbids (so an ability can never hurt your own pet,
whatever list it built). Per-power helpers now delegate: `ThorTargets.canAffect` / `isHostile`, `KryptonianCombat.isTarget`
/ `isHostileTarget`, `GreenLanternConstructAttacks.canHit` (1), `GreenLanternConstructs.isHostileTarget` (2),
`SuperSpeedMoves.validFoe` (1), `SuperSoldierAbilities.canTarget` (1) / `isHostile` (2), `MoonKnightCombat.friendly` (not 1),
`MoonKnightKhonshu.isFoe` (2), `HulkCombat.targets` (1), `TitanCombat.validTarget` (1), `SymbiotePet.isFoe` (2).

## Which abilities use which rule

Anything not listed below is rule 1 (aimed / deliberate).

| Power | Rule 2 (threats only) | Notes |
|---|---|---|
| Thor (Mjolnir / Stormbreaker) | Chain-lightning jumps after the first strike, Storm Call picks + splash, Hammer Volley auto-targets | Lightning Strike + aim assist, Chain Lightning cone, Lightning Beam, God of Thunder's Wrath, Thunderclap, thrown hammer / Stormbreaker are rule 1 |
| Iron Man | Targeting Mode glow, Flare blind, micro-missile in-flight homing | Unibeam, repulsors, flamethrower, punches, missile hit + blast are rule 1 |
| Green Lantern | Turret, Missile Barrage extra picks, Buzzsaw bounces, Chains snare, Warrior hunt, Ring Scan "hostile" list | everything aimed / slammed is rule 1 |
| Kryptonian | - | all moves are aimed or centred on his own blow |
| Super Speed / Flash | Speed Sweep (50-block auto sweep), aim-assist fallback cone | Blitz, punches, vortex, carry are rule 1 |
| Spider-Man | - | web yank / Web Blossom / impact webs are rule 1 (Blossom still never cocoons players) |
| Hulk | - | everything rule 1, incl. the unwilling rampage (it is meant to be indiscriminate; squad protection is dropped as before) |
| Symbiote | Symbiote Pet AoE moves (tendril burst, spike burst) | all host moves rule 1, never squadmates |
| Wolverine | - | claws, lunge, Frenzy (4-block melee flurry) rule 1; Sniff still shows every living thing (a sense, not an attack) |
| All Might | - | every Smash is rule 1 |
| Moon Knight | Eye of Khonshu pulse + random Moonbeams, Crescent Fan picks, crescent-dart homing | the aimed Z Moonbeam and everything else is rule 1; Judgement still never targets players |
| Max Steel | Arm-cannon lock-on | beam, Turbo bolts, slams rule 1; Stealth now drops aggro of any mob hunting him |
| Punisher | - | guns, grenades, C4 rule 1 (your own explosive can still hurt you) |
| Super Soldier | Shield ricochet next-target, Battle Cry debuff, Tactical Focus marks | shield throw path, strikes rule 1 |
| Titan Shifter | Titan Roar crowd control (32 blocks) | punches, stomps, grabs rule 1 (now also respects PvP); the roar's 50-block outline still shows everything |
| HeroPack mutations | Geokinesis Earthquake (25), Crystal Eruption (20), Refract node beams, Crystal Node spire turret, Electrokinesis chain hops + Electrical Storm follow-up bolts, Pyrokinesis Flame Body ignite aura + blue burn aura (24), Cryokinesis frost aura + Absolute Zero (20), Psychic Detonation (50), Bamf Strike follow-up hops, Sonic Barrier field + sneak Supersonic Scream (20), Density Heavy Impact (20), Shadow Cloak aura + darkness Zone (20), Kinetic Detonation (20), Plant ultimate (20), Nature's Blessing aura, Spore Cloud, Thorn Sentry, Gravity Well field, gravity toggle aura, Black Hole (30), Heavy Ground field, Wind Tailwind aura, Hurricane, ridden Wind Tornado, Size giant step-on crush, Flight sonic-speed trail, Mirror Image lure, Shadow Servant AI | everything else rule 1 |

## Boss aggro: the shared threat table (v0.15.19)

Bosses pick who to chase through one class: `com.projecthero.mod.event.boss.BossThreat` (one instance per boss, an
instance field -- never a static map, so nothing to add to `ServerStateReset`; not saved, a reloaded boss starts clean).
It replaced vanilla aggro (`HurtByTargetGoal` only retargets when it *starts*, `NearestAttackableTargetGoal` keeps its first
find), which let one player kite a boss while a friend hit it for free.

- **Threat in:** every hit that lands adds its damage (min `MIN_THREAT_PER_HIT` = 1) to whoever `DamageSource.getEntity()` names
  -- projectiles and summons count for their owner. Only things the boss may fight (`BossTargets.isVictim`: players,
  their pets / summons, golems) are recorded.
- **Decay:** threat halves every `HALF_LIFE_TICKS` = 200 (10 s, about -6.7%/s), decayed lazily on read.
- **Switching:** every `RETHINK_TICKS` = 10 the top attacker becomes the target if the boss has none / its target is no
  longer valid, or if the challenger's threat is more than `SWITCH_MARGIN` = 1.2x the current target's (a target who never
  hurt the boss has 0, so anyone who does takes it). After a switch the boss holds for `SWITCH_LOCKOUT_TICKS` = 30.
  `holdWhile(...)` defers the re-think while an attack is in flight, so a swing finishes on its victim and the next one
  reads the new `getTarget()`.
- **Dropped entries:** dead / removed, spectator or creative-invulnerable, another dimension, beyond the boss's range
  (its `FOLLOW_RANGE` clamped to 24-96, or `range(...)`), failing the boss's own `filter(...)`, or faded below 0.05.
- **Empty table:** nothing changes -- the boss keeps its own acquisition (nearest player, raid participant lists).

Wiring: `threat.record(source, amount)` after a hit lands in `hurt` / `actuallyHurt`, `threat.tick()` once per server tick.
Wired into Carnage, Kingpin, the Bone Tyrant, the Brood Queen, Ultron Prime, the Ultron Sentry, the Abyssal Behemoth,
Darkseid (`DarkseidCombat.refreshTarget`: the table first, the old 10 s sticky distance scoring only while the table is
empty) and every Empowered Zombie / Supervillain (the table overrides its rotation whenever someone clearly out-threatens
the target). The Oathbreaker (`OathbreakerThreat`), the Titan (`TitanThreat`) and the Sentinels / Master Mold
(`SentinelRobot` spread aggro) already had their own threat systems and keep them. The vanilla `HurtByTargetGoal` was
removed from Carnage, Kingpin, the Bone Tyrant and the Brood Queen. Normal (non-boss) mobs are untouched.
Tests: `BossThreatV01519GameTests`.

## Judgement calls

- **Huge-radius threshold:** a deliberate "hit everything around me" move of 15+ blocks counts as unaimed (rule 2); up to
  ~10 blocks (Thunderclap 7, Heat Wave up to 10, Sonic Scream disorient 10) stays rule 1.
- **Senses are not attacks:** Wolverine Sniff, Geokinesis Seismic Sense, Echolocation, Thermal Vision and the Titan Roar
  outline still show every living thing; abilities that *mark targets* (Iron Man Targeting Mode, Tactical Focus, Ring Scan's
  hostile list) are rule 2.
- **Thorn Sentry** keeps its old "never shoots a player" rule on top of rule 2.
- **"Hostile" includes the owner's current fight:** a cow you just punched (or that hurt you) becomes a valid pick for
  rule-2 moves for a few seconds, like vanilla tamed wolves joining your fights.
