# Galactus Raid v2: "The Devourer Descends" (approved 2026-11-04, built in v0.17.2)

> Status: **approved ("Build it") with the recommended defaults** -- the Nullifier finisher at 5%, items kept on a death in
> orbit (handed back on respawn), the old overworld acts retired. Code map and gotchas: `docs/GALACTUS_REFERENCE.md`.
> Sources: the user's brief, plus their friend's notes (`docs/reference/galactus_revamp/friend_notes.png`): the beacon starts a
> minigame on floating islands; Galactus punches the islands and you hop between them; make him bigger; separate head and
> arm hitboxes; anyone who falls takes some damage and lands on the next island; anyone flying gets beamed; "a big super
> raidboss".
> Replaces the v0.16.1 flow in `docs/GALACTUS_REFERENCE.md`: the overworld Worldship act and the ground-eating Element
> Converters go away. The Herald's Beacon, the natural cosmic signal, the Silver Surfer, the Punishers, the rewards and the
> Power Cosmic and Board items all stay.

Target length: **20-25 minutes** for a party of 2-4, broken up by five short cutscenes (about 50 s in total, and each
player can skip their own).

```
 OVERWORLD                                   ORBIT (new dimension projecthero:galactus_orbit)
 ┌────────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────────┐  ┌────────────┐
 │ C1 Herald  │→ │ ACT 0    │→ │ C2 The    │→ │ STAGE 1      │→ │ STAGE 2    │→ │ STAGE 3       │→ │ C5 Ending  │
 │ falls from │  │ Surfer   │  │ Head +    │  │ Shattered    │  │ The Feast  │  │ The Devourer  │  │ + home     │
 │ orbit (8s) │  │ duel     │  │ roar +    │  │ Orbit        │  │ (overload  │  │ (real fight,  │  │ (chest)    │
 └────────────┘  └──────────┘  │ ascension │  │ (islands)    │  │ converters)│  │ multipart)    │  └────────────┘
                               └───────────┘  └──────┬───────┘  └─────┬──────┘  └──────┬────────┘
                                                   C3 islands lock    C4 he rises     finisher
                                                   to the Worldship   to full height  (Nullifier)
```

---

## 0. The cutscene engine (shared by all five scenes)

- **`GalactusCinematics`** (server) starts a scene for the fighters: `CinematicPayload(sceneId, startTick, anchor)`. The
  client (`GalactusCinematicClient` + a camera mixin built like `MiraculousSequenceCameraMixin`) flies a keyframed camera
  (Catmull-Rom path, eased), draws **letterbox bars**, **subtitles** (Galactus' and the Surfer's lines), fades
  (black / white / violet) and calls `QuirkCamera` shake, FOV punch and vignette on cue.
- While a scene plays, fighters are **frozen and invulnerable** (held in place on the server, their hotbar hidden). Bosses
  and mobs are paused, so nothing can hit you mid-scene.
- **Hold Sneak to skip** (your own view only; a small "Hold ⇧ to skip" hint fades in after 1 s). The server waits for the
  scene's length either way, so the party stays in sync.
- Presentation only: your real look direction and aim are never touched, and control comes straight back at the end.
- Scenes are tagged with the event id and end by themselves if the event ends (abort, server stop).

## 1. Cutscene 1: "The Herald" (overworld, about 8 s)

Light the Herald's Beacon at night, as today.
1. **0-2 s**: the beam turns violet and shoots into the sky, the sky drains to deep violet (the existing raid tint) and
   the stars come out. The camera starts on the party, then tilts up the beam.
2. **2-5 s**: high in the sky one star **grows brighter and moves**. It's the Surfer streaking down from orbit, leaving a
   long silver trail. As he hits the atmosphere a white ring flashes (the sonic boom): a shake and a thunder crack.
3. **5-8 s**: a low hero shot. He pulls up hard over the beacon, his board throws a ring of dust and sparks, he levels a
   glowing hand at the party. Title: **THE HERALD OF GALACTUS**, and the subtitle *"This world has been chosen."*

## 2. Act 0: the Silver Surfer duel (overworld, about 3-4 min)

The **existing duel** with its four moves (Power Cosmic blast, light-speed dash, reflective shield, cosmic lift), the
`BossThreat` targeting and the arena border, balanced as it is now. Two changes:
- He still can't be killed. At 10% he **falls to one knee on his board** (a new pose) instead of turning straight away:
  *"You fight... for a world already lost. He is here."*
- He does **not** turn ally yet. His turn becomes a story beat across the stages (see Stage 1 and C3).

## 3. Cutscene 2: "The Devourer Wakes" + the ascension (about 14 s)

1. **0-3 s**: the sky tears open. A violet seam runs across the horizon, and the arena goes quiet and dark except the
   beacon. The camera pulls back behind the party.
2. **3-7 s**: **a colossal Galactus head rises over the horizon**, about 150 blocks tall (render-only; his helmet prongs
   cut through the clouds). His eyes ignite one after the other, with low rumbling and a ground shake that grows.
3. **7-9 s**: **THE ROAR.** Layered sound (a pitched-down dragon growl, warden roar and thunder), a huge screen shake, an
   FOV punch, a shockwave ring rolling across the ground and leaves and dust whipping toward him. Subtitle:
   *"I HUNGER."*
4. **9-14 s**: **the ascension.** Violet light pours from his eyes onto the party. Every fighter **floats up**, slowly
   then fast. The camera rides with you: the ground drops away, the clouds rush past, the FOV stretches (warp), streaking
   star lines fill the screen, and the blue sky turns black. A **white-out flash**, then the teleport.
5. **Arrival shot** (in orbit, 3 s): fade in from white. The camera sweeps over the **meteor field**, past the **Earth**
   hanging huge below, up to Galactus' upper body looming behind it. Title: **STAGE 1: THE SHATTERED ORBIT**.

## 4. The orbit arena (a new dimension, `projecthero:galactus_orbit`)

- Built like the Alien X council dimension: void, fixed time, no weather, no mob spawns. Each raid gets its own instance
  20 000 blocks apart, so two raids never meet, and every instance is wiped and rebuilt from its seed.
- **The sky** (a Fabric `DimensionRenderingRegistry` sky renderer, the first in the mod): a dense, gently twinkling
  starfield, two drifting **nebula bands** (violet and teal), a distant sun with a lens glow, **the Earth** as a big
  textured sphere filling the lower view with slowly turning clouds, and the **Worldship** silhouette. In Stage 2 the
  Earth visibly **greys over** as World Consumption climbs (its texture blends to a cracked, drained version).
- **Islands are real blocks** (seeded noise, so each meteor looks different): blackstone, basalt, tuff and end stone
  with amethyst clusters, crying obsidian veins and glowing cosmic ore. Small debris rocks drift around between them
  (block displays, purely visual).
- **Building and breaking are off inside the arena**, so nobody can pillar or bridge their way out (ender pearls still
  work and are a fair trick). Orbit gravity: **Jump Boost II** inside the arena, so a sprint-jump clears the gaps.
- **The Anchor**: the Herald's Beacon floats on the central island, which is never smashed. It is the safe spawn and
  where late joiners arrive.

## 5. Stage 1: "The Shattered Orbit" (the friend's islands, about 5 min)

**Layout:** about 9 islands (11 for 3+ fighters) in two rings around the Anchor, each 9-13 blocks across with 4-6 block
gaps, plus a few **Gravity Pads** (glowing amethyst plates). Step on one and it launches you along a visible arc to a
linked island across a big gap.

**Galactus here** is far out in space past the islands: his upper body from the chest up, too far to reach. He fights with
his fists.

| Move | Telegraph | Effect |
|---|---|---|
| **Island Smash** | the target island glows red, a growing shadow and crack sounds, and his fist winds back (3 s) | the fist slams down and **the island shatters**: real blocks gone, debris tumbling into space. Anyone still on it takes 14 and is flung off. |
| **Double Smash** (after the 1st stagger) | two islands glow at once | both fists |
| **Backhand Sweep** (after the 2nd stagger) | a red line across a row of islands (2 s) | his hand sweeps along the row; jump to the next row in time or take 10 and get thrown |
| **Flyer's Beam** (the notes) | anyone **flying** (creative-style flight, Iron Man, Kryptonian flight, elytra, the Surfer's Board, levitation) more than 6 blocks over the island plane, or off the islands, for over 1.5 s gets a red eye-lock reticle on screen and a "GALACTUS SEES YOU" line | an eye beam: 12 damage, slammed down onto the nearest island, and **flight disabled for 5 s** |

- **Targeting**: an island is picked by its occupants' threat (`BossThreat`) and headcount, so camping on one island is
  never safe. Smashed islands **re-form after 20 s**: the debris drifts back together with a grinding sound and a glow.
  Smashes get faster as the stage goes on.
- **Falling off** (the notes): fall about 12 blocks below the island plane and **the Silver Surfer streaks in, catches
  you and drops you on the nearest safe island**. You take **5 damage** (config), the screen flashes white and he says a
  short line (*"...Not yet."*). This seeds his turn: he catches players behind Galactus' back.
- **How you win: punish the fist.** After every Island Smash his **fist stays embedded for 4 s**: its knuckles glow with
  cracks and it is a **separate hitbox** (the friend's idea). Damage to the fist fills a **Stagger bar**. Fill it 3 times
  (it is bigger for bigger parties) and he recoils. Ranged players can hit it from the next island; melee players have
  to commit and get back off before it rips free (when it lifts out it knocks back anyone on it).
- Each stagger: he rears back with a roar, a shockwave and a 6 s breather, and his Stagger bar resets bigger.

## 6. Cutscene 3: "The Worldship" (about 10 s)

After the 3rd stagger he clutches his fist and bellows. The **Worldship** slides over the field with a deep hum, and its
violet tractor beams catch every island and **pull them together onto its hull**, slotting them into place like puzzle
pieces (block displays moving smoothly, then real blocks set). The camera pulls back to show **Galactus kneeling over the
Earth with his hands cupped around it, a river of light flowing up from the planet into the ship**. The Surfer flies
alongside the party, finally turned: *"I have served him a thousand worlds. Not this one."* He is now an **ALLY**
(existing ally behaviour: orbit, bolts and shield bubbles). Title: **STAGE 2: THE FEAST**.

## 7. Stage 2: "The Feast" (overload the converters, about 6 min)

**Arena:** the joined islands now form one big hull platform (about 70 across) with **Element Converters** (the existing
model) on raised hull towers around it: 3, plus 1 per two extra fighters, at most 5. Each converter pumps a thick
beam from the Earth up to Galactus. **World Consumption** climbs 4%/min per working converter; the Earth greys over and
titles fire at 50 / 75 / 90%. At 100% you lose (the existing loss cinematic, re-staged in orbit).

The idea is to **redirect his energy back into him**. It is interactive and needs the team to work together:
1. **Break the guard.** Each converter is shielded while its **2 Punishers** live (the existing guard rule). Punishers
   drop from the Worldship in waves.
2. **Grab a Cosmic Cell.** Where a feed beam passes a hull tower, it throws off a **Cosmic Cell** every 20 s (a glowing,
   humming orb item). Pick it up and you become the **Carrier**: you glow violet, move at 80% speed, **can't fly**, and you
   **drop it if hit for 6+ in one blow**. Punishers and Galactus' eye beams go after Carriers first.
3. **Socket it.** Carry the Cell to a shield-free converter's intake and right-click. The converter **overloads**: it
   shudders and sparks for 6 s (defend it: Punishers will try to pull the cell back out), then **reverses**. Its beam
   flips and blasts **into Galactus** (he flinches with a big hit flash) and the converter blows apart, dropping an
   **Ultimate Nullifier Part**, which you'll need for the finale.
4. **Galactus** stays kneeling over the Earth (out of reach) but swats at the deck: **Eye Beam** lines across the hull
   (a 2 s glow telegraph, then the sweep), **Punisher drops**, and when a converter starts overloading, a **finger flick**
   aimed at it (a red circle; Carriers and defenders scatter).
5. Flying is allowed again here (the beam rule is Stage 1 only), but Carriers can't fly.

Every converter reversed means the stage is won.

## 8. Cutscene 4: "The Devourer Rises" (about 9 s)

The reversed beams converge on his chest and he **lets go of the Earth**, screaming (the planet's grey slowly recedes).
The camera starts at his feet on a huge planetoid below the hull and **tilts all the way up his body**: he stands to his
full **90 blocks**, bigger than ever (the notes: "make him bigger"). He steps toward the ship and plants a hand on the
hull's edge, which shakes. Title: **STAGE 3: THE DEVOURER**. Boss bar: **GALACTUS**.

## 9. Stage 3: "The Devourer" (the real fight, about 8 min)

**Arena:** the Worldship's deck (radius 40), about chest-high on him, with a ring of **cover pillars** and 4 **Gravity
Pads** that launch you onto his **shoulders** (the pads light up when he's close). Flying is fully allowed.

### Separate hitboxes (the friend's idea)
Real damageable part entities placed on his body every tick (the proven `GalactusWeakPointEntity` approach: never saved,
re-made on load), following each move's key poses so the boxes stay on the arms and head as he animates:

| Part | Damage taken | Notes |
|---|---|---|
| **Head** | **x2** (x3 during Devour) | normally out of reach; it comes down in Devour and the grab, or you can reach it from the shoulders or by flying |
| **Left / Right Arm** (upper + forearm) | x1 | lots of damage on an arm **breaks its gauntlet**: that arm goes limp and its moves stop for 25 s |
| **Left / Right Hand** | x1 | hitting a hand mid-move **interrupts** it (a stagger meter per hand); hitting the grabbing hand breaks a grip |
| **Chest Core** | x1.5 | **sealed** until both gauntlets have broken at least once, then it opens and glows |
| body elsewhere | x0.25 | |

Health 8000 + 2000 per extra fighter (config). Every part's damage comes off his one health bar, and every hit builds
threat. He re-targets from the threat table (`BossThreat`, as every boss does).

### Moves
| Move | Telegraph | Effect / counterplay |
|---|---|---|
| **Hammer Fist** | a shadow grows on the deck (1.5 s) | 16 in a 5-block radius; the hand **stays down for 3 s**, which is the time to hit hands and arms |
| **Palm Sweep** | his hand drags along the deck edge (1 s) | 12 and thrown; jump over it, fly over it or hide behind a pillar (pillars crack and fall after 2 hits) |
| **Eye Beams** | a 2 s glowing line | the existing sweep (16 + fire) |
| **The Grab** | the existing hand grab | lifted to his face; allies break the grip by hitting the hand. **New:** the victim can hit his **face** while held |
| **DEVOUR** (signature) | he leans right in with his head at deck level, his mouth glows and a vacuum roar | a **vacuum pulls everyone toward his face** for 6 s. Sprint away, hold onto a pillar (stand behind it) or **hit his head**: each hit fills a "choke" bar, and when it fills he gags and recoils (a 6 s stun with the head on the deck at x3). Anyone pulled in takes 20 and is spat across the deck. |
| **Meteor Rain** | red circles | the Stage 1 islands come back as falling meteors |
| **Punisher Drop** | | 2 at a time, 4 alive at most (fewer than now) |
| **PLANET CRACKER** (below 40%, once, then every 90 s) | he lifts both hands and **charges a Power Cosmic sphere between them** over 12 s, with a big charge bar on screen | the team has to **hit both hands** (two charge-break meters) before it fires. If it fires: 70% of max health to everyone on the deck, minus armour. It needs the team to split up (one group per hand, which is where the shoulder pads come in). |

At 50% his **helmet cracks** (a visual change), he roars and his attacks speed up 20%.

### The finale (the Ultimate Nullifier)
At **5%** he falls to one knee against the ship. The Surfer flies up with the Nullifier, assembled from the Stage 2
parts, and hands it to the **fighter who did the most damage**. That player has to **hold right-click for 4 s aimed at
his head**, while Galactus throws everything at the holder (eye beams and grabs). Teammates protect them by interrupting
hands and body-blocking. If the holder goes down, the Nullifier drops and anyone can pick it up.

## 10. Cutscene 5: the ending (about 12 s) and the trip home

The Nullifier fires a white-violet beam into his face and light spreads through the cracks in his helmet. He roars one
last time, **comes apart into a storm of stars** and the shockwave rolls over the camera. The Worldship powers down and
drifts away, and the Earth's colour floods back. The Surfer bows: *"The world is yours. Guard it well."* A white fade,
then the **fall home**: a reverse star-streak tunnel ends on the overworld beacon, which is now the **reward chest**.

## 11. Rewards
- The existing **chest** (Galactus' Helmet Crest trophy, diamonds, netherite scrap, emeralds, amethyst, XP bottles,
  books, scaled by `Valuables.partyScale`), raised by 50% because the fight is longer.
- The existing **first clear** items (the Silver Surfer's Board and the Power Cosmic), once per player.
- **New:** a **Cosmic Shard** (crafting material) for each stagger you helped with, and a **"Worldbreaker" title line**
  in chat naming the Nullifier holder.

## 12. Multiplayer, logout and server stop
- **Scaling**: +1 island per extra fighter, +1 converter per two, Stagger bars, part health and Galactus' health all
  grow per extra fighter (`healthPerExtraFighter` style keys).
- **Joining**: during Act 0, walk into the arena as today. After the ascension the beacon becomes a **Rift**: right-click
  it to join the stage in progress (a short ascension effect, then you appear on the Anchor). This works until the
  finale starts.
- **Death**: you respawn at your own spawn. **Items dropped in orbit don't fall into the void**: they go into a **grave
  bundle** handed back to you at the beacon (or put in your inventory when the event ends). You can rejoin through the
  Rift. **All fighters down at once means a loss** (an orbit consumption cinematic, then everyone goes home).
- **Logout in orbit**: when you log back in, if the event is still running you're put on the Anchor; if it's over you're
  sent back to where you were lifted from (a saved per-player return point, like the Speed Force realm).
- **Server stop**: the event saves its stage. On load, a mid-cutscene save resumes at the start of that stage, the orbit
  instance is rebuilt from its seed, and anyone stranded in orbit with no event is sent home.

## 13. Project rules
- **Threat table**: the Surfer and Galactus (every stage, including island picks) target from `BossThreat` and switch
  targets.
- **`ServerStateReset`**: every new static map (scene playback, orbit instances, return points cache, carriers, Stage 1
  island state) is registered and cleared on server stop. The current Galactus statics get checked too.
- **Config**: `projecthero_galactus.json` goes to **`VersionedConfig` v2**, with new `cinematics`, `orbit`, `stage1`,
  `stage2`, `stage3` and `finale` sections. Removed keys (`converters.*` ground-eating, `consumption.perPylon*`, `pylons`)
  are dropped by the migration.
- **Models**: GeckoLib only for the boss. We **extend our own `galactus` model** (cleaner `left_arm`/`right_arm`/`head`
  groups for the part sync, a cracked-helmet texture variant and new clips: `smash`, `smash_stuck`, `double_smash`,
  `backhand`, `kneel_feed`, `rise`, `hammer`, `hammer_stuck`, `palm_sweep`, `devour`, `devour_gag`, `planet_cracker`,
  `kneel_defeat`, `disintegrate_stars`, `roar`). The Surfer stays a player model (plus a new `kneel` pose). Islands are
  blocks; debris is block displays. The Earth, nebulae and Worldship silhouette are sky textures we make ourselves.
- **Retired**: the overworld Worldship act, ground consumption (`GalactusTerrain` stays only to revert any leftover v1
  ledgers on load), the Power Pylons and the v0.16.4 walking.
- **Gametests**: stage transitions (C1 → Act 0 → C2 → S1 → C3 → S2 → C4 → S3 → finale → C5); island smash, re-form and
  the Anchor never targeted; fall rescue (damage, then placed on a safe island); the flyer beam fires only after 1.5 s of
  flight; the stagger bar fills only from fist damage; carrier rules (drop on a 6+ hit, no flight); a socketed cell
  reverses a converter and drops a part; part damage multipliers and the sealed core; a gauntlet break disables its arm;
  Devour choke; Planet Cracker break; the Nullifier finisher; grave bundle; logout and rejoin; abort cleans up the
  instance and sends everyone home.

## 14. Build order (once approved)
1. Orbit dimension, sky renderer, island generator and instance lifecycle (screenshots)
2. The cutscene engine plus C1 and C2 (MP4s)
3. Stage 1 (MP4: smash, rescue, beam, stagger)
4. C3 plus Stage 2 (MP4)
5. C4 plus the bigger multipart Galactus and Stage 3 moves (MP4)
6. Finale, C5, rewards, multiplayer, persistence and gametests
7. Release-ready: reference doc rewritten, config v2, test modpack

## 15. Decisions made while building (user asleep: "do everything automatically and dont ask me any questions")
- **Galactus's size:** 230 blocks for the C2 head reveal, 180 leaning over the Stage 1 islands, 100 for the Stage 3 fight
  (90 was proposed; 100 gives his arms reach over the deck). He drifts around the field toward the party so no side is safe.
- **Stage 1 reach:** he only smashes islands his fist can actually reach (checked with the shared rig), so a punch never lands short.
- **Rendering:** the Devourer entity stands at his chest and the model is drawn down to his feet, because an entity whose
  feet are underground or in empty void isn't drawn by the client.
- **Fog:** turned off in orbit and during C1/C2, so the giant reads clearly against the sky.
- **Cosmic Shards:** 4 shards + a beacon craft a Herald's Beacon (the proposal didn't give them a use).
- **Retired content:** the v0.16 overworld acts. `GalactusV0164GameTests` was removed and v0.16 saves restart at the C2 reveal.
- **Converters:** they can only be destroyed by overloading them, and weapons get a hint message.
