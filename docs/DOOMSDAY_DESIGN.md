# Doomsday: The Day Superman Died (v0.17.2: approved 2026-11-03, built)

User decision (2026-11-03): **boss only**, as strong as Galactus. A hulk with Superman's powers minus flight.
Skin: NovaSkin post 4845083152, "Batman The Devastator" (128x128 HD, wide arms). Credit its author on NovaSkin.
Local copy: scratchpad `doomsday_skin/devastator.png`. It will ship as `textures/entity/doomsday/doomsday.png` with a
red-eye glowmask.

## 1. Lore that drives the mechanics
| Comics fact | Mechanic |
|---|---|
| Bred on prehistoric Krypton by Bertron: killed and cloned over and over; **"can't be killed the same way twice"** | **Reactive Adaptation**: he learns your damage types, and each death makes him immune to what killed him (section 4) |
| Always comes back; revives **slower in darkness**, away from the sun | **3 lives**. Revival takes 20 s in daylight and 40 s at night or under a roof |
| Sealed in a **Calatonian burial suit**, cables and shackles, in a casket that crashed on Earth | Arrives in a **casket meteor**. Life 1 starts **Bound**: one arm shackled, cables still on |
| 1992: he flattened the Justice League **with one arm tied** and used his cables as a shield | Bound phase: a one-arm kit plus a **Cable Guard** that blocks ranged hits |
| Bone protrusions grow as armour and weapons | Bone spike moves. Procedural bone spikes grow on the model with every life |
| Superman-level-plus strength, beat Darkseid, tanked the Omega Beams, no flight but **miles-long leaps** | Huge leaps, crater landings, shockwaves. Galactus-tier health and damage |
| Never permanently killed: contained in the Phantom Zone, on the Moon, at the End of Time | **The ending**: banish him with a **Phantom Zone Projector** while he lies dead (section 6) |
| Hunts Kryptonians; kryptonite hurts him | Holders of the Kryptonian power draw 1.5x threat. Kryptonite damage gets x1.5 and can't be adapted to |
| "Hulk with Superman's powers": heat vision, super breath, super speed | Gained as **adaptations** in later lives (Heat Vision in life 2, Super Breath and Blitz in life 3) |

The Devastator skin (the Dark Nights: Metal Batman fused with Doomsday) is only his look. He has Doomsday's kit and isn't
called Batman anywhere.

## 2. Starting it
- **Unlock**: the natural signal only fires in a world that has beaten **Galactus** or the **Apokolips Invasion** (he
  beat Darkseid in *Hunter/Prey*). This is the same top rung of the threat ladder as Galactus.
- **Natural**: a daily roll of 4% at night. It never fires on Peaceful, never while another event runs, and never within
  256 blocks of another event. A casket meteor lands 30-50 blocks from a random eligible player.
- **Summon**: a **Calatonian Casket** block, crafted from 4 netherite ingots, 2 crying obsidian, a nether star, a chain
  and an echo shard (`NCN / OSO / NEN`). Right-click it to start.
- `/projecthero raid start|end|advance doomsday` (op), like every other raid.
- Config: `config/projecthero_doomsday.json` (`DoomsdayConfig`, VersionedConfig v1).

## 3. The fight
**Arena**: radius 72, grey and red dust border, the shared raid sky tinted ash-red. It is an EventInstance like
Galactus: fighters, down/back, the event bar and a boss bar. Aggro uses the shared **BossThreat** table.

**Body**: the player rig with wide arms and the HD skin, **x2.4 scale (about 4.3 blocks)**, growing **+0.25 per life**.
The hit-box grows to match. Poses are code-driven (like the Silver Surfer and Hulk): hunch, roar, leap crouch, slam and
grab. Bound life: the left arm is strapped to the chest and green cable strips trail from the body. Bone spikes
(procedural bone geometry on the forearms, knuckles, shoulders, spine and knees) get longer and more numerous each life.

**Health**: **3,000 per life, +750 per extra fighter**, 12 armour, 8 toughness, full knockback resistance. Three lives
make about 9,000, plus adaptation. Galactus has 4,000 + 1,000 behind pylons and is immune to normal damage. He
regenerates 4 HP/s in sunlight and 1 HP/s otherwise.

### Moves (all telegraphed; one at a time with a 1.5 s breather; cooldowns x0.75 in life 3)
| Move | Life | Telegraph | Effect |
|---|---|---|---|
| Hammer Blows | all | fist raise | Melee 3-hit combo, 14 each, heavy knockback. Bound: 2 hits |
| Cable Guard | 1 | cables whirl | 3 s: projectiles are blocked and melee does 50% |
| Doomsday Leap | all | crouch 0.8 s + red ring at the landing spot | Ballistic leap of 20-60 blocks onto the target. Landing: 26 within 5, dirt and stone thrown up (particles only: no blocks change) |
| Earthshatter | all | both fists up | A ground ring at 0.9 blocks/tick out to 40: 16 to anyone on the ground. Jump it (Galactus wave rules) |
| Bone Eruption | 2+ | body flashes bone-white | Spikes burst from the body: 18 within 4. Then a line of ground spikes toward the target: 12 + launch |
| Bone Volley | 2+ | arm cocked | A fan of 5 bone shards, 7 each + Slowness |
| Heat Vision | 2+ | eyes flare for 1 s | A red beam swept at the target: 6 every 4 ticks + fire, 40 range, lasts 2 s |
| Grab & Slam | all | lunge | Grabs the nearest fighter and slams them 3 times (10 each), then throws them. Allies break it with 60 damage |
| Super Breath | 3 | deep inhale | A 14-block cone gust: big knockback, puts out fires, Slowness |
| Blitz | 3 | blur trail | A super-speed charge of 30 blocks: 20 to everything on the line |
| Doomsday Roar | all | head back | 16-block radius: Weakness I + Slowness II for 4 s, an ash shockwave and a ground shake (no blocks break) |

## 4. Reactive Adaptation (the hook)
- Every hit is sorted into one of 7 buckets: **melee, projectile, fire, explosion, lightning/energy, magic, power**.
  "Power" covers this mod's hero ability damage types (repulsors, webs and so on).
- **Learning**: once a bucket has dealt 20% of the current life's health, he takes **-25% from it**. This stacks to -75%.
  An action-bar line announces it ("Doomsday adapts: FIRE"). Grey stone flecks show on the skin, and the boss bar lists
  his adaptations.
- **Death**: the bucket that landed the killing blow becomes an **immunity** for every later life ("he can't be killed
  the same way twice"). Players are forced to change tactics.
- **Fairness floors**: at most 4 buckets can be at -75% or immune at once; when a fifth would be added, the oldest
  learned resistance decays. **Kryptonite** and **void/`/kill`** damage are never adapted to. Melee can never become
  fully immune, only -75%, so a fighter with just a sword can still finish.

## 5. Lives and revival
- At 0 HP he drops as a **corpse** (untouchable). It stays put and the music stops for 20 s (day) or 40 s (night or a
  roof over him). Then he rises with the next life's kit, +0.25 scale and longer spikes.
- Each death drops a **Kryptonian Crystal Shard** (one per life, invulnerable, never despawns).

## 6. The ending: banish him
- After the **third** death, the three shards combine (the same flow as the Nullifier parts) into the **Phantom Zone
  Projector**, given to the fighter holding the most shards.
- Hold right-click for 3 s on his corpse **before he revives** (40 s, or 80 s in the dark). A spinning Phantom Zone
  pane pulls him in and the event is won.
- Miss the window and he rises in a **4th, enraged life** (1,500 HP, every adaptation kept). The window opens again when
  he dies. All fighters down = a loss, and he leaps away.

## 7. Rewards
- A chest like Galactus's: Doomsday's Skull (trophy block), diamonds, netherite scrap 3-5, emeralds, XP bottles and
  enchanted books, scaled by `Valuables.partyScale`.
- **First clear per player**: **Doomsday Bone Gauntlet**, a melee weapon (netherite-tier). Each kill with it adds +1
  damage against that mob type, up to +4 for 4 types: his adaptation in item form. **Optional, cut it if you like.**

## 8. As built (code map)
- Server `com.projecthero.mod.doomsday`:
  - `Doomsday`: the entry point, the natural impact roll and the command.
  - `DoomsdayEvent`: the EventInstance. Casket, arena, fighters, bar, Projector hand-over and rewards.
  - `DoomsdayAdaptation`: pure logic. Buckets, learning, immunity, the floor, save/load.
  - `DoomsdayConfig`: `config/projecthero_doomsday.json`.
  - `DoomsdayRewards`.
  - `DoomsdayWorldState`: the unlock (Galactus driven off, or the Apokolips Invasion won; that one takes a single line in
    `DarkseidRaid`).
  - `entity.DoomsdayEntity`: lives, corpse, revival, banish and the move brain.
  - `entity.CasketEntity`.
  - `entity.DoomsdayAttackEntity`: bone spike, shard and ground ring.
  - `item.PhantomZoneProjectorItem` and `item.BoneGauntletItem`.
  - `block.DoomsdayBlocks`: the Calatonian Casket and Doomsday's Skull.
- Client `com.projecthero.mod.client.doomsday`:
  - `DoomsdayRenderer`: the player rig with the HD skin, poses, eye glow, stone flecks, bone spikes, cables, the heat beam
    and the Phantom Zone pane.
  - `DoomsdayClient`: the casket, spikes and shards.
  - `DoomsdayMesh`.
- Lang lives in `assets/projecthero_doomsday/lang/en_us.json`, a separate namespace file. Minecraft merges every
  namespace's en_us.json, so this branch never conflicts with master's big en_us.json.
- Generated assets:
  - `scratchpad/gen_doomsday_assets.js <devastator.png>`: textures.
  - `scratchpad/gen_doomsday_json.js`: models, blockstates, loot, recipes and lang.
- `DoomsdayGameTests` (9 tests):
  - registration;
  - adaptation steps, caps, the floor and save/load;
  - damage buckets;
  - corpse and revival, and slower revival in the dark;
  - only a final corpse can be banished;
  - the event's casket and Projector hand-over;
  - the Bone Gauntlet.
- Debug: `/projecthero raid advance doomsday` steps through casket opens -> this life ends -> revival finishes -> ... ->
  banished.
