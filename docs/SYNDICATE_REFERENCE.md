# Syndicate Bust (v0.14.25)

A street-level raid on the Kingpin's crew. Package `com.projecthero.mod.syndicate`.

## Flow

1. **Police Scanner** (`police_scanner`, shaped: ` R ` / `ICI` / `IRI`, with R = redstone, I = iron ingot, C = compass).
   - Use it to pick a site 60-110 blocks away (`SyndicateHideout.findSite`). The site must be:
     - in loaded chunks
     - dry
     - within 6 blocks of flat
     - free of block entities
     - not inside another event
   - It then builds the warehouse and places the stash.
   - The scanner stores the stash position in its `custom_data`. While held, its action bar shows the bearing and distance.
   - Using it again repeats the location until that stash is busted.
   - It refuses on Peaceful.
2. **The warehouse** (`SyndicateHideout`) uses local coordinates round a floor origin, rotated by `forward`:
   - 25x25 footprint, 7-high walls, a roof with a skylight strip
   - roller door at `lz = -12`, side doors at `lx = +-12`
   - crate cover on the floor
   - a mezzanine (floor `ly = 3`, `lz 6..11`) with ladders at `lx = +-9` and a balcony gap
   - the Kingpin's office with the **Syndicate Stash** inside
3. **The stash** (`syndicate_stash`, `SyndicateStashBlock` with `FACING` = warehouse forward and `ACTIVE`):
   - Its block entity checks once a second for a survival player inside the walls or within 10 blocks of the roller door, then starts the bust.
   - Right-clicking it also starts the bust.
   - It can't be mined while active.
   - It drops nothing (it only makes sense in its warehouse).
4. **SyndicateBust** (event type `syndicate_bust`):
   - SPOTTED (4 s), then 5 waves with a 7 s breather between them, then BOSS.
   - The waves come from `WAVE_MIX`. Counts are thugs / pistols / shotguns / snipers / enforcers, scaled by `1 + 0.5 * (players - 1)`, with snipers and enforcers capped at 4.

     | Wave | Thugs | Pistols | Shotguns | Snipers | Enforcers |
     |---|---|---|---|---|---|
     | 1 | 6 | 0 | 0 | 0 | 0 |
     | 2 | 5 | 3 | 0 | 0 | 0 |
     | 3 | 4 | 3 | 2 | 0 | 0 |
     | 4 | 2 | 3 | 1 | 2 | 1 |
     | 5 | 4 | 3 | 2 | 2 | 1 |

   - Spawn points:
     - doors (outside, on the ground)
     - the back room under the mezzanine (all of wave 1, and some of waves 3+)
     - the catwalk (snipers)
   - Stragglers glow after 60 s and are dropped after 120 s.
   - In the boss phase the Kingpin walks out of the office, with 1 Enforcer bodyguard (2 for 3+ players).
   - **Win:** the stash becomes the chest (`SyndicateRewards`).
   - **Fail or abort** (framework abandon: everyone gone for 5 min): the crooks are cleaned up and the stash relocks. The warehouse stays.

## The crew (`SyndicateCriminal`, player model, Skindex skins)

| Mob | HP | Notes |
|---|---|---|
| Thug | 26 | Melee with a wooden sword ("bat"), iron shovel ("crowbar") or stone sword ("knife"). |
| Gunman: Pistol | 22 | 5-14 blocks, strafes, 6-round magazine then a 2.5 s reload, 4 damage. |
| Gunman: Shotgun | 22 | Closes in, 6 pellets x 2.5, pumps for 1.5 s. |
| Gunman: Sniper | 22 | Holds the catwalk, 1.5 s red laser, then 12 damage at up to 48 blocks. |
| Enforcer | 70, 8 armour, x1.25 scale | Shoulder Charge (wall hit = dazed 1.5 s, x1.5 damage taken) and Ground Slam when 2+ players are near. |
| **Kingpin** | 600 + 200 per extra player (cap 2400), 10 armour | See below. |

- Gunmen fire through `SyndicateGunfire`:
  - hit-scan with spread
  - the Punisher's tracer, bullet-hole and impact look
  - no friendly fire inside the crew
  - shields block shots
- Gunmen hold the matching Punisher gun item, which is never dropped. They sometimes drop its ammo, rarely Weapon Parts.

**Kingpin moves** (`KingpinEntity.Brain`):
- **Cane:** 15 per swing (7 base + the cane's +8).
- **Bull Rush:** 6-20 blocks, a 1 s wind-up, then a charge for 16 damage plus a launch. Hitting a wall stuns him for 1.5 s, and he takes x1.5 damage while stunned.
- **Grab:** at 3.6 blocks or less he holds the target for 1.2 s, then throws for 8 damage. 30 damage dealt to him during the hold breaks it.
- **Cane Gun:** at 7+ blocks, 3 shots of 6 damage.
- **Ground Pound:** phase 2+, a shockwave of up to 14 damage over 7.5 blocks.
- **"Boys!":** at 66% and 33% health, `SyndicateBust.reinforce` sends 2 + players crooks through the doors.
- **Enrage:** at 30% health, +25% speed, +20% damage, and cooldowns x0.65.
- **Projectile reduction:** projectiles do x0.75.
- He has his own boss bar. The event's bar hides during the boss phase.

## Rewards

- A Villain Dossier for each fighter, up to 4. Reading one calls `SupervillainMark.mark`. It refuses on Peaceful or if the player is already marked.
- The Kingpin's Cane:
  - +8 attack, -2.9 speed, 1200 durability
  - Use it to fire a hit-scan shot for 7 damage every 50 ticks.
- Emeralds, gold, iron, diamonds, gunpowder, golden apples, XP bottles, Punisher ammo and parts, a 25% enchanted golden apple, and an enchanted book. Scaled by `Valuables.partyScale`.

## Commands

`/projecthero raid start|end|advancetimer syndicate`. `start` builds a warehouse `HALF + 6` blocks in front of you and starts the bust.

## Skin credits (The Skindex, minecraftskins.com)

| Texture | Skin | Uploader |
|---|---|---|
| `kingpin.png` | [Wilson Fisk - MCU](https://www.minecraftskins.com/skin/24041389/wilson-fisk---mcu/) | wolflywood01 |
| `thug_a.png` | [Malak gang thug 1](https://www.minecraftskins.com/skin/23801174/malak-gang-thug-1/) | Bakugo417 |
| `thug_c.png` | [Malak gang thug 5](https://www.minecraftskins.com/skin/23801205/malak-gang-thug-5/) | Bakugo417 |
| `gunman.png` | [thug](https://www.minecraftskins.com/skin/23945225/thug/) | KINGJS189 |
| `masked.png` | [thug](https://www.minecraftskins.com/skin/23637779/thug/) | hell1234568789 |
| `hood.png` | [thug](https://www.minecraftskins.com/skin/23211164/thug/) | Danroc |

The Enforcer reuses the thug skins (two planned extra skins failed to transfer intact and were dropped).
