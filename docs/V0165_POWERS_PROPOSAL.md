# v0.16.5 Proposal: Cyborg, Black Panther, Ghost Rider, and Teleportation Rework

**Status: design only. Nothing is built until you sign off.** Every number here is a starting point you can change.

Shared rules for all four (they match the current mod):
- Six-key kit: **R, G, X, Z, V, C**, plus a **Shift** version of each move. **H** only suits up or transforms (like Hulk, Thor and All Might). **N stays empty** unless you assign it.
- The three heroes are **Hero-Tier Primary powers**, so you can hold only one (the "ONE Primary" rule). Teleportation is a mutation.
- Each power gets a mono HUD (six boxes plus its own bar), a guidebook chapter, a Power Info screen entry, lang text and a `/hero grant` id.
- Bosses use the shared rules: damage is capped and they cannot be stunned or launched.

---

## 1. Cyborg (Victor Stone): Hero-Tier

**Fantasy:** a walking tech arsenal. He hacks machines and uses a Boom Tube to travel. He is strong at range and support, and only average in melee.

**How you get it:** the **Mother Box** already drops from the Apokolips Invasion (Darkseid raid). Right-click it while you have **4 hearts or less**. The box fuses with you and you get a 10 s per-texel conversion, the same reveal style as the Max Steel nanotech. The Mother Box is used up.

**Look:** a player model with a detailed cyborg skin (chrome plates, red eye, glowing chest core).
- **Reworked (user):** he is **always Cyborg**; the full kit and the passives always work. **H** projects a **hologram** of your normal skin from the blue light on his forehead. It builds over the body one pixel at a time, top-down, and any move (or H again) drops it.
- **Jet Flight (user):** double-tap jump in the air. A jetpack builds itself onto his back and he flies on energy jets from it and from his boots; landing folds it away.
- **Arm blaster (user):** the Sonic Cannon and the Sonic Burst build a blaster round his forearm, like Max Steel's.

**Resource: Power Core (100).** It regenerates 2 per second. Getting struck by lightning or standing next to a powered redstone block fills it faster.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Sonic Cannon:** hold to charge (up to 1.5 s), release to fire a blue beam that deals 6-16 damage with knockback. 20 core | **Sonic Burst:** a 6-block cone blast that pushes enemies away. 25 core |
| **G** | **Hydraulic Punch:** a heavy hit that breaks guards, 12 damage. 6 s | **Grapple Claw:** fire a cable 30 blocks. A block pulls you to it; a mob is pulled to you |
| **X** | **Thruster Dash:** a jet-boot dash 10 blocks in the direction you are moving. 4 s | **Jet Flight:** toggle (or double-tap jump), draining 4 core per second |
| **Z** | **Techno-Override:** hack what you aim at. Ultron drones, Sentinels, Stark Sorter Bots and iron golems fight for you for 20 s. Doors, pistons, lamps and TNT toggle. 40 core | **Scan:** highlight mobs and ores within 32 blocks for 10 s, and show the target's HP and armour on the HUD |
| **V** | **Boom Tube:** teleport to where you are looking (up to 64 blocks) or to your bed; your squad within 6 blocks comes too. 90 s | **Waypoint:** save the current spot as the Boom Tube's next destination (works across dimensions) |
| **C** | **Defense Matrix:** an energy shield that blocks 30 damage in front of you, for up to 5 s | **Self-Repair:** spend 3 iron ingots to heal 8 HP over 4 s. 30 s |

**Passives (always):** no drowning, hunger drains at half speed, no fall damage, and a +4 armour plating bonus. Lightning fills the core. **Weakness:** water stops the core from regenerating.

**New assets:** cyborg skin (two forms), Sonic Cannon beam visual, Boom Tube portal visual (the Darkseid Boom Tube effect can be reused), hologram shimmer.

---

## 2. Black Panther (T'Challa): Hero-Tier

**Fantasy:** a stealth melee predator. Hits you take charge the suit, and you release that stored energy as purple kinetic blasts.

**How you get it:** a new rare jungle structure, the **Panther Shrine** (built like the Kryptonite crater worldgen), holds a **Heart-Shaped Herb**. Eat it at night: you get 10 s of Ancestral Plane vision (a purple sky screen), then the power. The **Vibranium Necklace** suit item is crafted from vibranium ore, which spawns only near shrines.

**Look:** a player-model suit with a detailed skin. **H** (with the necklace in the chest slot, the same rule as the Flash Ring) runs a nanite suit-up that flows out from the necklace texel by texel. Purple lines glow brighter as the Kinetic bar fills.

**Resource: Kinetic Energy (100).** The suit soaks **40 % of every hit** and that 40 % fills the bar (1 damage = 4 kinetic). The bar drains slowly out of combat.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Kinetic Pulse:** a purple shockwave of radius 5 dealing 8 damage plus knockback. 30 kinetic | **Kinetic Overload:** needs a full bar. Releases everything in a radius-10 blast for up to 30 damage (bosses capped) |
| **G** | **Claw Combo:** three fast vibranium claw slashes; the third hit causes a 4 s bleed | **Pounce:** lunge up to 12 blocks onto the target, pin it for 2 s, then a free claw strike |
| **X** | **Panther Leap:** a very high jump (12 blocks) with no fall damage, and you can grab a ledge | **Drop Strike:** dive straight down from the air; the landing radius 4 deals 10 damage |
| **Z** | **Vibranium Dagger:** a thrown dagger that bounces to up to 3 targets, then returns to you. 5 s | **Kimoyo Flash:** a thrown bead that blinds and slows everything within 6 blocks for 3 s |
| **V** | **Stalk:** toggle. While sneaking, mobs more than 6 blocks away can't see you. An attack from behind does 3× damage and ends Stalk | **Panther Senses:** see enemies through walls within 40 blocks, plus their recent footprints. 10 s |
| **C** | **Absorb Stance:** hold to brace. Hits from the front deal **0 damage** and go fully into Kinetic, up to 40 per stance; you move slowly | **Kinetic Return:** reflect the last projectile that hit you back at the shooter |

**Passives (suit on):** no fall damage, Speed I, Jump Boost I, night vision, and arrows/bullets bounce off unless they're above the bulletproof threshold (the same rule as Iron Man). **Suit off:** Speed I and enhanced reflexes only.

**New assets:** suit skin, the necklace item (3D, worn in the chest slot), shrine structure, Heart-Shaped Herb plant, vibranium ore, dagger item, Ancestral Plane screen.

---

## 3. Ghost Rider (Spirit of Vengeance): Hero-Tier

**Fantasy:** a flaming-skull judge on a hell bike. He punishes the guilty. He is a tanky brawler with fire.

**How you get it:** **Mephisto's Contract** is a rare loot item in Nether fortress and bastion chests. Right-click it and you get a "sign in blood" screen that costs 5 hearts permanently until you transform for the first time. The Spirit then bonds with you.

**Look:** a player model with a leather jacket. **H** sets your head on fire: flames climb up the body texel by texel, and the head becomes a **flaming skull** (an animated flame overlay, the same pixel-flame style as the existing fire effects). A spiked chain is wrapped around your chest.

**Resource: Hellfire (100).** It regenerates at Superman's sun rate (6 a second), always. *(Rework: was faster at night and in the Nether.)*
**Second meter: Vengeance.** Every hostile mob that damages you, a villager or a squadmate gets marked as "guilty". Hitting a guilty target fills Vengeance, and Vengeance powers the Penance Stare.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Hellfire Blast:** hold to throw balls of skull fire (a real projectile with fire sprites and a trail), 2 per second, **20 damage each**, that set targets on fire. 10 hellfire per bolt (bosses capped like every power) | **Hellfire Wave:** a ground wave of fire 10 blocks forward, **30 damage** |
| **G** | **Chain Whip:** a burning chain with 8-block reach, **18 damage**, that yanks small mobs to you | **Chain Spin:** spin the chain around you for 3 s, **13 damage a hit** (twice a second, no fall-off) to everything within 4 blocks |
| **X** | **Hell Cycle:** summon the flaming bike: **15 blocks/s, 30 sprinting**, a 4-block step, slides along walls, drives up taller walls, leaves a fire trail, rides over lava. **X while riding: Cycle Jump** (leaps 15 blocks with a burning landing) | **Send the bike back** |
| **Z** | **Judgement Mark:** mark every guilty mob within 30 blocks so they glow red; marked targets take +25 % damage from you for 15 s | **Penance Stare:** lock eyes with a target for 2 s. Damage scales with Vengeance and with how "guilty" the target is (more vs mobs that killed villagers or players). **Undead and bosses take a fixed 15**; players take damage from their own kill count |
| **V** | **Hellfire Rush:** charge 12 blocks forward wreathed in hellfire, smashing through everything in your path for **18 damage** plus heavy knockback and burning. 30 hellfire, 8 s | **Hellfire Eruption:** slam the ground and hellfire pillars burst up in a 6-block radius around you for **25 damage**, launching enemies and leaving burning ground for 4 s. 50 hellfire, 20 s |
| **C** | **Soul Shield:** a ring of hellfire for 4 s that halves damage and burns attackers | **Rider's Wrath:** 10 s of Strength II and fire immunity for squadmates within 6 blocks |

**Passives (transformed):** **+11 melee**, **Regeneration III** (costs 2 hellfire a second; stops when the hellfire runs out), immune to fire and lava, +6 max health, undead mobs are scared of you (Wither skeletons run away), you can't drown, and **food goes twice as fast**. **Diamond-level armour built in** (+20 armour, +8 toughness) but **no armour can be worn** (worn pieces and the elytra go back into the inventory, or drop if it's full). **He looks like a demon:** iron and snow golems attack him (16 blocks), villagers and wandering traders panic, run (12 blocks) and refuse to trade, animals bolt (10 blocks), other people's cats and wolves hiss or growl and keep away (his own pets stay loyal), and creatures under his Judgement Mark cower (slowed). Undead keep the older rule (they lose their nerve). **Untransformed:** fire resistance only.

**The rework's rules (user, v0.16.5):**
- **H only turns you back out of combat:** 5 s since you last dealt or took combat damage, and no hostile monster within 20 blocks.
- **You can't die while you carry the Spirit.** A killing blow leaves you on 3 hearts; if you were human, the Spirit takes over and you change on the spot. Either way H can't turn you back until the next sunrise (a saved lock, so it survives a relog). `/kill` and the void still kill.
- **The change is slower:** 4.8 s, and the face burns away over about 2.5 s; the skull's eruption is flames only (no orange dust or lava).

**New assets:** flaming-skull head overlay plus body flames, the chain (a 3D whip like the Symbiote tendril), the **Hell Cycle entity** (a GeckoLib vehicle, since it's a mount and not a mob), the contract item and screen, and the Penance Stare screen effect.

---

## 4. Teleportation Rework: Experimental (mutation)

**What's wrong now:** it is switched off (`Powers.ENABLED`), it uses H and N (Bamf Strike and Swap), it has two overlapping portal moves (Portal and Portal Anchor), and the long cooldowns make it feel slow.

**New design:** Nightcrawler-style combat teleporting with a **Strain** meter instead of long cooldowns, so you can chain jumps.
- **Strain (0-100):** each jump adds strain and it drains at 10 per second. At 100 you **overload**: Nausea, and 4 s with no teleports.
- Every jump leaves a puff of purple smoke and an afterimage (the existing effects).
- You get it the same way as before: the Spatial Distortion Serum plus an Ender Pearl, or the Resonance Array. It also comes back to the power wheel and to random serums.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Blink:** an instant blink to the crosshair (up to 25 blocks) with a safe landing. 15 strain | **Long Blink:** hold to paint a spot up to 75 blocks away, release to jump. 35 strain |
| **G** | **Bamf Strike** (moved from H): blink behind your target and hit it for 11, then chain to up to 4 targets. 40 strain | **Swap** (moved from N): trade places with your target or a squadmate. 20 strain |
| **X** | **Escape Blink:** 10 blocks backward plus 1 s of Resistance III. 15 strain | **Sky Blink:** 15 blocks straight up with slow-fall |
| **Z** | **Phase Step:** go through a wall up to 3 blocks thick. 20 strain | **Displace:** teleport the target 15 blocks into the air (no bosses). 30 strain |
| **V** | **Rift:** charge 5 s, then use the existing coordinate screen to open a linked portal pair. **This merges Portal and Portal Anchor**, with one pair at a time. 60 s cooldown | **Mark / Recall:** set a return mark, and press again to go back (works across dimensions) |
| **C** | **Spatial Fold:** for 3 s, any projectile that hits you is teleported back at whoever fired it. 25 strain | **Group Jump:** your next Blink also takes squadmates within 5 blocks |

Passives: reduced Ender Pearl damage, and no fall damage on the first second after any jump.

**Built (2026-10-15).** Where the spec above was silent, I decided the following:
- **The jump that fills the meter still happens.** Then you overload. Nausea lasts 5 s. Strain keeps draining while you are overloaded.
- **Sky Blink costs 15 strain**, the same as Escape Blink. It gives 6 s of Slow Falling, and under a ceiling it stops below it.
- **Mark / Recall:** setting the mark is free and the recall costs 25 strain. The old 17 s cooldown is gone. A blocked recall loses the mark, as before.
- **Rift:** no strain and a 60 s cooldown. Tapping **V while a pair is open closes it**, and sneak + right-clicking a gate still works too. Walking through a gate costs no strain. Old Portal and Portal Anchor markers are cleared from saves.
- **Displace:** 30-block aim range. It never moves squadmates, bosses (the shared no-bosses rule) or anything above 200 HP.
- **Phase Step:** it uses your horizontal facing only (no phasing through floors or ceilings). The wall must start within 1.5 blocks of you.
- **Spatial Fold:** a projectile with no living shooter (for example from a dispenser) just vanishes. A folded projectile becomes yours, so it can't be folded back at you.
- **Group Jump:** it stays armed for 10 s and costs no strain itself. It works with Blink or Long Blink. Squadmates keep their offset from you where that spot is safe, and otherwise land beside you.
- **HUD:** mono keys, with an always-on violet Strain Hairline under them. The Overload, Rift charge, Spatial Fold and Group Jump bars show only while active.
- **Unchanged:** the old "pearl resist" rule (falls under 8 damage are ignored, which covers the Ender Pearl's 5).

---

## 4b. Blue Beetle (Jaime Reyes): Hero-Tier

**Fantasy:** a teenager bonded to an alien scarab, Khaji-Da, that builds weapons out of his armour on the fly. He's a flying all-rounder: a ranged blaster with a blade, a shield and a few tricks. Khaji-Da talks to him in short lines in chat, the same way the Symbiote does.

**How you get it:** the **Scarab** turns up in **suspicious sand in desert pyramids**, found by brushing (about as rare as Mjolnir). Right-click it and it latches onto your spine. You get a 5 s bonding: the armour builds itself pixel by pixel outward from the scarab on your back (no blue glow).

**Look:** a player model with a detailed Blue Beetle skin (you'd send the link, like the other two). **H** suits up: the armour builds itself texel by texel outward from the scarab on your back (and comes apart the same way, reversed), and the wings spread from the back plate when you fly.

**Resource: Scarab Energy (100).** It refills 3 per second, and twice as fast while you stand still. Every move costs some of it.

**Adaptation (Khaji-Da's trick):** take the same kind of damage 3 times within 10 s (fire, explosions, arrows, melee, magic...) and the armour adapts. That damage type does **40% less to you for 60 s**, and a small icon shows which type you've adapted to. One adaptation at a time.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Plasma Blaster:** hold: a blaster builds itself round your forearm and fires glowing plasma-orb projectiles, 3 a second, **15 damage** each. 4 energy per bolt | **Mega Cannon:** both arms out together, a big twin cannon forms over both forearms during a 2 s charge, then a huge beam pierces everything in a 30-block line for 30 damage (bosses capped). 50 energy, 20 s |
| **G** | **Scarab Hammer:** the arm forms an energy hammer and smashes it down: 20 damage in a short arc in front, knocked into the air. 25 energy, 6 s | **Scarab Grab:** two segmented tentacles shoot out of your upper back at the mob you aim at (14 blocks), strike it once each for 10 and knock it far back. 25 energy, 10 s |
| **X** | **Scarab Swarm:** 3 small scarab drones attack your target or the nearest enemy for 10 s (3 damage per hit). 35 energy, 25 s | **Bubble Prison:** traps the mob you aim at in an energy bubble for 5 s. It can't move or attack, and nothing can hurt it inside (no bosses). 30 energy, 18 s |
| **Z** | **Plasma Spikes:** slam a fist down and a line of energy spikes erupts along the ground for 14 blocks, 18 damage each, launching what they hit. 30 energy, 8 s | **Shield Pulse:** the shield blasts out in all directions, pushing everything within 6 blocks away and reflecting arrows. 30 energy, 12 s |
| **V** | **Energy Shield:** hold to raise a round shield on your arm; like Cyborg's Defense Matrix it soaks hits and arrows from the front out of a 100-point pool and breaks when that runs dry. No energy cost; 8 s cooldown after it drops or breaks. Khaji-Da's takeover uses it too, raising it against incoming shots and mobs about to hit, and lowering it to attack | **Camouflage:** invisible with no timer; it breaks the moment you touch, hit or use anything (blocks, items, mobs), fly or use a move. 30 energy, 15 s cooldown after it breaks |
| **C** | **Energy Blade:** toggle: the blade forms on your arm, **+7 melee** while it's out; C again folds it away. 10 energy | **Khaji-Da Unleashed:** ultimate, needs a full bar. For 15 s Khaji-Da **takes over the body**: it hunts the nearest mob hostile to you within 32 blocks (never squadmates or pets), walks to it on an A* path (jumps, drops, round walls), flies when the target is up high, there's no way on foot or it's stuck, and fights with the kit (hammer and blade up close, Shield Pulse when surrounded, spikes, grab, cannon and blaster at range, the swarm) at +50% damage, free moves, half cooldowns and a red glow. Your keys and clicks do nothing and the camera watches from behind. It ends early, with a line from Khaji-Da, once nothing hostile is left nearby. Afterwards you're drained and the bar refills at half speed for 30 s. 120 s cooldown |

**Flight:** double-tap jump in the air while suited (like Cyborg's Jet Flight); landing or another double-tap ends it. 2 energy a second. The four wings spread out still and glow blue; no wind sounds. He leans forward flying forward and lies flat sprint-flying, arms and legs straight down.

**Passives (suited):** diamond-level armour (20 armour, 8 toughness), **+9 melee damage**, no fall damage, night vision, Adaptation, and Khaji-Da's chat lines (warnings when you're low, comments on the enemies you fight). **Unsuited:** the scarab still warns you about hostile mobs within 16 blocks, with a short chat line.

**New assets:** the armour skin, the Scarab item and a voxel Scarab on your back (split oval carapace, gold rim, glowing core, legs gripping the back; armour on or off), the glowing wings, the arm blaster and twin cannon, the energy hammer, the tentacles, the spikes, the plasma bolt, the energy blade, the shield, the bubble, the scarab drones and the Mega Cannon beam.

**v0.16.5 rework (user's list):** better Scarab model; pixel-by-pixel build from the scarab with no glow (5 s first bonding); double-tap-jump flight with still, glowing wings and no wind sound; plasma bolts are real projectiles (15 damage); voxel blaster / twin cannon build on the arms; keys re-slotted (V shield, Shift+V endless camouflage, C toggle blade +7, Shift+G tentacles); new G Scarab Hammer and Z Plasma Spikes; Swarm / Bubble Prison moved to X; Wings and Afterburner keys removed; +9 melee suited.

---

## 6. Elasticity Rework: Experimental (mutation)

**What's wrong now:** it is switched off (`Powers.ENABLED`). It uses H (Rubber Shield) and N (Parachute Glide). C is a three-way form wheel (Elastic / Inflated / Compression) that is hard to read. The "25% less melee damage" passive makes a rubber brawler feel weak, and the arms only *look* stretched through particles.

**New design:** a Mr. Fantastic / Plastic Man / Luffy rubber brawler. Your **real arms and legs stretch**: the limb model itself lengthens out to the target in your own skin, then snaps back. A **Stretch** meter replaces most cooldowns, so you can chain moves. It has six keys, each with a Shift version, and no H or N.

- **Stretch (0-100):** each move spends some and it refills 15 per second (twice as fast standing still). Run it dry and you **go limp**: Slowness II and no moves for a flat 8 s.
- **Wind-up:** R and Z can be **held to coil** the limb. It visibly twists for up to 1.5 s, for more damage, reach and knockback. Release to snap.
- **Getting it:** unchanged. The Elastic Polymer Serum plus a 10-block fall onto a Slime Block, or the Gravity Distortion Rig. It returns to the power wheel and random serums.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Stretch Punch:** the arm shoots out up to 18 blocks for 12. Hold to coil up to 1.5 s, for up to 24 damage, 22 blocks and a big knockback. 10 Stretch | **Gatling Barrage:** hold to flurry stretched punches into the cone in front, 6 hits a second at 12.8 each, many blurred arms. 12 Stretch a second |
| **G** | **Elastic Grab:** the arm wraps a mob or player up to 20 blocks away and reels it in. Press again to hurl it where you look: 10 damage, plus 6 if it hits a wall or another mob. 15 Stretch (no bosses) | **Wrap & Squeeze:** your body coils round the grabbed or aimed target (6 blocks). It can't attack or move for 4 s and takes 4 a second. Press again to let go. 25 Stretch |
| **X** | **Slingshot:** both arms grip the block you aim at (80 blocks, user 2026-10-16) and hold it wherever you look. Hold to coil, release to launch: speed from the coil and the anchor distance, and no air drag until you land. 15 Stretch | **Rubber Ball:** roll up into a bouncing ball for 6 s. It's fast, bounces off walls, and bowls through mobs for 6 each. Projectiles bounce off. 30 Stretch |
| **Z** | **Giant Hammer Fist:** the fist swells to a boulder and slams the aim point (12 blocks). Hold to coil for a bigger fist: 20-34 damage in a 4.5-block area, plus Slowness. 30 Stretch, 8 s cooldown | **Spring Stomp:** your legs stretch you 8 blocks up on stilts, then you slam down: 20 damage in a 5-block ring, launching mobs. 25 Stretch, 6 s cooldown |
| **V** | **Rubber Shield** (hold, moved from H): inflate into a balloon. Projectiles bounce back at their shooter, melee attackers are flung away, and you take 60% less damage, but you move slowly. 8 Stretch a second | **Parachute** (toggle, moved from N): the torso stretches into a canopy between the arms and legs (user, 2026-10-16) and it glides like an elytra. Free |
| **C** | **Long Reach** (toggle): your arms reach 12 blocks for everything. Break and place blocks, open doors and chests, and pick up items from 12 blocks away. Free | **Shape Shift:** squash into a harmless-looking shape (a barrel, a chest or a block of your choice). Mobs lose you. You can creep about as the block; sprinting, swinging or attacking, using a block or taking a hit ends it (user, 2026-10-16). 20 Stretch, 10 s cooldown |

**Passives:**
- No fall damage, plus a springy bounce from a real fall. Hold Shift to land dead.
- 50% knockback resistance.
- Rubber body: 25% less from blunt hits (melee and explosions), but **+25% from fire and lava** (weakness).
- Squeeze through 1-block gaps.
- +3 melee reach.
- The old **"25% less melee damage" passive is removed.**

**HUD:** mono keys, with an always-on **orange Stretch Hairline** under them (like Teleport's Strain). A coil bar shows while you hold R or Z.

**Visuals:**
- Real stretching limbs: the arm or leg model is lengthened along itself to the target, with a slight wobble on the way back.
- The coil twist while charging.
- The balloon and the canopy as body shapes.
- The ball as a rubber sphere in your skin's main colour.
- Shape Shift as the chosen block, wobbling slightly when you move the camera.

**Boss:** the Gravebound Elasticity boss controller is updated to the new move ids.

**Built (2026-10-16).** Where the spec above was silent, I decided the following:
- **The move that empties the meter still happens.** Then you go limp. While limp you can still switch off the Parachute or Long Reach and let go of a wrap.
- **Stretch refills only while no held move runs** (a coil, the Gatling, the balloon, a slingshot being pulled back). Otherwise the Gatling (12 a second) and the balloon (8 a second) would cost nothing. "Standing still" means on the ground and not moving.
- **Coils scale evenly** from tap to the full 1.5 s: Stretch Punch 12 to 24 damage, 18 to 22 blocks. The Hammer Fist goes 20 to 34, and its fist grows with the coil. A move's Stretch is spent when it fires.
- **Gatling:** each punch hits everything in a ~40° cone out to 10 blocks. It keeps its own 6-a-second count, so it is not blocked by hit invulnerability.
- **Elastic Grab:** it holds the target up to 6 s and reels it in over about half a second. It refuses bosses and anything over 200 HP, and players only where hard crowd control on players is allowed (the existing grab rule). The hurl damage lands on impact: 10 when it lands, 16 when it hits a wall or another mob (that mob takes 6 too).
- **Wrap & Squeeze:** the first 4 damage lands after 1 s, then once a second. The creature is pinned to its block and its hits do nothing. It never wraps squadmates, and it lets go beyond 12 blocks.
- **Slingshot:** "pull back" builds launch power (shown on the coil bar); you don't move back. The arms follow your crosshair while you hold. No impact damage, since the spec gives none.
- **Rubber Ball:** your hitbox shrinks to 60% and you get +90% speed. Each mob is hit once per hit-invulnerability window. Pressing Shift+X again rolls you out early.
- **Spring Stomp:** an ~8-block launch, the slam starts at the top, and the ring lands when you touch down. Mobs are thrown about a block a tick upward.
- **Rubber Shield:** the 60% stacks on the blunt 25%, and you can't be knocked back.
- **Parachute:** no time limit (it's free). It ends on landing or on Shift+V. Used on the ground it hops you up first.
- **Long Reach:** 12 blocks for hitting and for blocks. Items within 12 blocks are dragged in to you (the arm stretches to the farthest one). The arm visibly stretches on any use past 4 blocks.
- **Shape Shift:** you become the block you are looking at within 12 blocks (so a chest by looking at a chest), otherwise a barrel. It needs solid ground and snaps you to the middle of your block. Mobs can't target you. It ends when you move (a third of a block), attack, use any Elasticity move, or press Shift+C again.
- **Landing bounce:** you spring back up at about half your landing speed (three quarters as the ball), so the bounces die away. Shift lands dead.
- **Blunt** means melee (mob and player attacks) and explosions. **Fire** means everything in the fire damage tag, lava included. The shared damage-rule code can now *raise* damage, which it could not before.
- **HUD:** mono keys, the always-on orange Stretch Hairline, a coil bar (also used by the slingshot pull), timers for the Hammer Fist, Spring Stomp and Shape Shift cooldowns, the Ball and the Wrap, and a LIMP bar.
- **Removed:** Elastic Form with its three-shape wheel (the screen and its network payloads), Double Fist Slam, the Rubber reserve and the -25% melee passive.
- **Boss:** Double Fist Slam became **Elastic Grab**: it reels a close target in and hurls it (10).
- **Not done:** in first person you don't see your own arm stretch (other players do, and the hit and particles still show).

---

## 5. Cutting test time

**What I found:** CI is already about **2 minutes** per push (8 parallel slices, and only the tests for the changed area run). Almost all the waiting is **on your PC**:

1. **The project lives inside OneDrive.** Every build writes thousands of files (`build/`, `.gradle/`, agent worktrees) that OneDrive then tries to sync. Just measuring the folder sizes hung for over 2 minutes. **Fix:** move the project to something like `C:\dev\ProjectHero` (git and GitHub keep everything backed up). I expect this to be the biggest single speed-up for builds, client boots and agents.
2. **Windows Defender scans every build file.** You can add a Defender exclusion for the project folder and `%USERPROFILE%\.gradle` (that's a security setting, so you would do it yourself).
3. **Client screenshot runs.** Each one boots Minecraft (2-3 min). **Fix:** do one batched screenshot run per feature at the end, not one per change.
4. **Agent worktrees have piled up again:** 1.3 GB in `.claude/worktrees`, all inside OneDrive. **Fix:** delete them now, and from now on put agent worktrees outside OneDrive. The build cache is already on; the Gradle configuration cache is off (Loom support for it is shaky, so it would need testing).
5. **The gametest suite (1712 tests):** each new power gets **one smoke test per ability**, not deep tests per edge case. Old tests for removed or disabled features (e.g. old Teleportation H/N paths) get deleted.

---

## Questions for you

Answered 2026-10-15:
- **Skins:** you'll send skins for Cyborg, Black Panther and Ghost Rider (Skindex/NovaSkin/PMC links).
- **Ghost Rider:** H transforms any time; Hellfire regenerates faster at night and in the Nether. Kit changes: R bolts deal 20 each (slowed to 2 per second); Penance Stare moved to Shift+Z (Z is now Judgement Mark); V replaced by Hellfire Rush / Shift+V Hellfire Eruption (Brimstone Breath and Hellfire Pillar removed).
- **Scope:** all four ship together in 0.16.5.
- **Test speed:** no preference picked. Section 5 lists the options.

Still waiting on: your go-ahead (or changes) for each power, the three skin links, and which test-speed fixes to apply.

---

## 7. Flight Rework: Experimental (mutation)

**What was wrong:** it was switched off (`Powers.ENABLED`). Flight itself took the X key, Slipstream and Barrel Roll sat on H and N, the speed tiers were hard to read, and every move was particles only.

**New design (user-approved):** a Superman / Homelander flier. Flying is free and starts with a **double-tap of jump** (like Cyborg's Jet Flight). A **Lift** meter (0-100) pays for sprint-flying, the dashes and the big moves. Six keys, each with a Shift version, no H or N.

- **Lift (0-100):** sprint-flying, dashes and big moves spend it. It refills 12 a second on the ground or while cruising.
- **Passives:** double-tap jump to fly; cruise **20 blocks/s**, sprint-flying **50 blocks/s** (user numbers); landing ends flight; no fall damage; flight poses (lean forward cruising, laid out flat sprint-flying, arms at the sides).
- **Getting it:** unchanged (Gravitational Instability Serum + a gravity device, or a 14+ block fall). It returns to the power wheel and random serums.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Flying Punch:** dash up to 8 blocks into the target: 14 and a big knockback. 15 Lift | **Dive Bomb:** in the air, rocket down at the aim point: 12-30 in a 4-block ring, scaling with height. 25 Lift |
| **G** | **Grab & Carry:** grab the mob or player in front and fly with it; press again to throw (no bosses). 15 Lift | **Orbital Drop:** grab, shoot 40 blocks straight up and let go (full fall damage). 40 Lift, 15 s cooldown |
| **X** | **Air Dash:** a 12-block burst the way you move. 10 Lift | **Barrel Roll:** a sideways roll with 0.5 s of projectile immunity. 10 Lift |
| **Z** | **Sonic Boom:** break the sound barrier: a 10-block cone shockwave, 16 and a launch. 30 Lift | **Takeoff Blast:** launch 20 blocks up from the ground, knockback within 5. 20 Lift |
| **V** | **Hover** (toggle): hang still for steady aim, no knockback. Free | **Slipstream** (toggle): you and squadmates flying behind you +50% speed. 4 Lift a second |
| **C** | **Afterburner** (hold): +60% speed with a contrail. 8 Lift a second | **Updraft:** every enemy within 6 blocks is lifted 6 blocks up. 25 Lift |

**Models and animations:** the Sonic Boom's vapour cone and shock-ring cone, the Afterburner's contrail ribbons, the Dive Bomb's crater (shock ring, scorch and cracks, a dust rim, thrown-up chunks of the ground block), the Takeoff Blast's ground rings and wind column, the Updraft's spiralling wind column, the Slipstream's wind funnel, the Hover's air rings, punch and dash rings; poses for the punch, dive, three-point landing, carry, throw, overhead lift and release, dash, barrel roll (a full body spin), sonic boom, takeoff, hover and updraft.

**Built (2026-10-16).** Where the spec above was silent, I decided the following:
- **Sprint-flying costs 6 Lift a second.** With the meter empty you can still fly, but only at cruise speed. Afterburner (8) and Slipstream (4) add to it, and **nothing refills while any of them runs** (otherwise cruising with Slipstream would be free). Creative players spend nothing.
- **Not enough Lift refuses the move** with a message ("Not enough Lift (15 needed)"); nothing is spent. There is no limp state.
- **Moves are driven by the server:** each dash re-sends its velocity every tick, so it flies the same distance flying or on foot. Flying Punch picks the target on your crosshair up to 8.5 blocks and lands on contact (or at the end of the dash if it is still within 3 blocks); with no target it dashes 8 blocks and hits the first thing it meets. Air Dash and Barrel Roll go the way you were last moving (Barrel Roll: the side you strafe, else alternating).
- **Dive Bomb** needs you off the ground. It flies at 3.5 blocks a tick toward the aim point (96 blocks); an aim above the horizon still comes down steeply. Height is measured from where the dive started; the full 30 lands from 30 blocks. The Super Strength + Flight combo bonus still applies.
- **Grab & Carry** reaches 5 blocks and holds for up to 30 s; the throw is 2.4 blocks a tick along your look, and the fall does the damage. **Orbital Drop** grabs the same way, carries the creature overhead at 3 blocks a tick and lets go at the top (or at a ceiling). You stay up flying.
- **Sonic Boom** hits a ~40° cone; flying, it also surges you forward for 3 ticks. **Takeoff Blast** only works from the ground and starts flight at once. **Updraft** throws enemies up at ~1 block a tick (about 6 blocks).
- **Hover** takes off if you are on the ground, holds you dead still (no drift, no climbing) and gives full knockback resistance; any dash, dive or rise ends it. **Slipstream drafting** is worked out by each squadmate's client: flying any flight within 16 blocks behind a Slipstreaming squadmate (judged by the leader's facing) gives +50%.
- **Afterburner** takes off if you are not flying; it ends on release, at 0 Lift or on landing.
- **Barrel Roll** now only stops projectiles (the old roll stopped all damage).
- **HUD:** mono keys, the always-on blue Lift Hairline, timers for the Orbital Drop cooldown and the Carry.
- **Removed:** the X Flight toggle (now the double-tap), the speed tiers, Sonic Flight, the old Orbital Drop slam, H Slipstream (now Shift+V), N Barrel Roll (now Shift+X) and the falling air-control passive.
- **Boss:** the Gravebound Flight boss keeps its moves; its ids are the new ones (take-off = Afterburner, Dive Bomb = Flying Punch, Orbital Drop = Grab & Carry, Barrel Roll = Air Dash).
## 7. Pyrokinesis Rework: Experimental (mutation)

**What was wrong:** it was switched off (`Powers.ENABLED`). It used H (Heat Wave) and N (Fire Whip), the fireball was a vanilla ghast fireball, and most moves were particles only.

**New design (user-approved):** one **Heat** meter (0-100) that every move **raises**. Hotter = stronger fire, up to +40% at 80+ Heat. At 100 you **overheat**: a forced fire nova around you (12), then no moves for 5 s. Heat cools 10 a second, faster in water or rain. Six keys, each with a Shift version, and no H or N.

- **Passives:** immune to fire and lava, punches set things alight, lava doesn't slow you.
- **Getting it:** unchanged (the Thermal Mutation Serum + standing in fire, or the Blast Chamber). It returns to the power wheel and random serums.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Fireball:** 14 + burning. +8 Heat | **Flamethrower** (hold): 8-block cone, 4 per burst + burning. +10 Heat a second |
| **G** | **Fire Whip:** 12-block lash, 12, pulls the target in. +10 Heat | **Fire Lasso:** a burning ring binds the target 3 s (it can't move, 3 a second). +20 Heat |
| **X** | **Flame Dash:** a 10-block burst leaving a fire trail. +10 Heat | **Jet Flight** (toggle): fly on fire jets. +5 Heat a second |
| **Z** | **Meteor** (hold to charge): a huge fireball, 16-30 in a 5-block blast, sets fires. +25 Heat | **Inferno:** dump your Heat in a nova, up to 30 (at full Heat) in 8 blocks. Heat to 0 |
| **V** | **Flame Wall:** an 8-wide wall of fire, burns whatever crosses, burns up arrows. +15 Heat | **Flame Body** (toggle): living fire. Melee attackers burn, -30% melee damage, walk on lava. +4 Heat a second |
| **C** | **Heat Wave:** cone knockback, melts snow and ice, smelts dropped items. +10 Heat | **Cauterize:** burn 40 Heat to heal 8 hearts. 20 s cooldown |

**Models and animations:** the Fireball and Meteor are real projectile entities drawn as layered glowing balls (a solid molten core, two churning additive shells, a soft glow and flame tongues streaming back, with a fading trail). The whip is a tapering ribbon of fire along an S-curve that cracks and recoils, the lasso two spinning rings of fire round the target with a burning rope back to your hands, the Flame Wall two staggered rows of vanilla's animated fire over a glowing ember bed. Jet flames come out of both palms and both soles, Flame Body is a burning figure: you are drawn in the user's NameMC flame skin (5e7bb58498e66c55, user 2026-10-16) with a live fire layer climbing over the whole skin (a 64x64 texture regenerated every tick, full-bright), in third person and on the first-person arm, plus real fire licking up off the limbs, the Meteor swells over your head while you charge it, and the player glows hotter as Heat climbs. Every move has a cast pose. Particles are accents only.

**HUD:** mono keys, an always-on orange **Heat Hairline**, an OVERHEATED timer, the Meteor charge bar, the Lasso timer and the Meteor / Flame Wall / Lasso / Cauterize cooldowns.

**Boss:** the Gravebound Pyrokinesis boss controller uses the new move ids (its Flamethrower is Shift+R now, and its Inferno counts as the Meteor).

**Built (2026-10-16).** Where the spec above was silent, I decided the following:
- **Heat cools only while nothing hot runs.** The Flamethrower, the Jet Flight, Flame Body and a Meteor being charged hold it up; otherwise the held / toggled moves' Heat a second would be cancelled out by the cooling. "Faster in water or rain" is three times as fast (30 a second).
- **The Heat bonus** grows evenly from x1.0 at 0 Heat to x1.4 at 80 and stays there. It uses the Heat you had when you cast (a Fireball's damage is fixed when it is thrown). It applies to every fire hit except Inferno (which has its own Heat scaling) and the overheat nova (a flat 12).
- **Overheat:** the move that reaches 100 still happens. The nova hits everything within 5 blocks for 12 and sets it burning, Heat goes to 0, every held / toggled move stops (Jet Flight lands you with no fall damage) and nothing works for a flat 5 s.
- **Small cooldowns** so a key can't be spammed every tick: Fireball 0.4 s, Whip 1 s, Flame Dash 1.5 s, Heat Wave 1.5 s, Meteor 3 s, Flame Wall 3 s, Lasso 6 s (plus Cauterize's 20 s from the spec). Only the longer ones show on the HUD.
- **Flamethrower:** a burst every 5 ticks (4 a second) at 4 each, ~35 degree cone, burning; it lights the block it hits. It won't light underwater.
- **Fire Whip:** hits the first creature on your aim within 12 blocks and throws it toward you (bosses and anything over 200 HP take the damage but aren't pulled). **Fire Lasso:** same aim and range, no bosses, players only where hard crowd control on players is allowed (the existing grab rule). The burn is 3 (+Heat) once a second; Shift+G again lets go early.
- **Flame Dash:** 2 blocks a tick for 5 ticks along your aim (the climb / dive is limited so you don't rocket into the sky), fire on the ground behind you, everything you pass catches fire, no fall damage for 2 s after. **Jet Flight** is the mod's shared directional flight at the hero-flight speed; it ends when you land, go underwater or press Shift+X again.
- **Meteor:** a full charge is 2 s; damage scales evenly 16-30 with the charge (then the Heat bonus). It flies straight, a bit slower than the Fireball, and the 5-block blast throws, burns and leaves fires. It breaks no blocks.
- **Inferno:** damage = 30 x Heat / 100 to everything within 8 blocks; it needs 10 Heat.
- **Flame Wall:** 3 blocks ahead on the ground, across your aim, 8 s; 3 (+Heat) every half second and burning to whatever stands in it; projectiles that aren't yours burn up in it.
- **Flame Body:** the -30% is for melee (a creature or player hitting you in person). Lava walking is the strider rule; hold Shift to sink in.
- **Heat Wave:** an 8-block, ~60 degree cone; no damage. Snow / snow blocks / powder snow melt away, ice melts to water (vanishes in the Nether), packed ice softens to ice. Dropped items in the cone smelt, the whole stack at once.
- **Fire is placed only where you may build** (and only with the server's terrain-damage switch on); all of it is temporary and goes out on its own.
- **Removed:** the blue-flame tier (Flame Laser, blue Inferno, blue Flame Body), the old overheat (1 damage a second until 60%), Flame Spark (light / absorb / cook held items), the old Heat Wave vent on H and the Fire Whip on N.
- **Not verified:** real multiplayer play, and lava walking (client movement) only in the screenshot harness.
## 9. Cryokinesis Rework: Experimental (mutation)

**Approved design, built.** An Iceman / Frozone ice caster on a **Cold** meter, built around **Frost**. Six keys, each with a Shift version, no H or N.

- **Cold (0-100):** moves spend it; refills 15/s (25/s in a snowy biome or standing on snow / ice) while the Freeze Beam and Frozen Armor are not draining it. A move you cannot afford is refused (nothing spent). Shown as an always-on Hairline, plus Ice Slide / Ice Prison / Absolute Zero timers.
- **Frost:** ice hits add Frost (ice-crystal pips over the target). At 5 it is **frozen solid for 2.5 s** in a block of ice: pinned, can't jump, can't attack (its hits and projectiles do nothing). Frost fades one at a time after 3 s; a thawed target can't be re-frozen for 2 s.
- **Passives:** water freezes under your feet, immune to freezing / powder snow, no slipping on ice.

| Key | Move | Shift | Move |
|---|---|---|---|
| R | **Ice Bolt**: 10 + 1 Frost, 8 Cold | Shift+R | **Ice Shards**: 7-shard fan, 4 + 1 Frost each, 15 Cold |
| G | **Freeze Beam** (hold): 1 Frost / 0.5 s, freezes water, 10 Cold/s | Shift+G | **Ice Prison**: encase a target 4 s (no bosses), 30 Cold, 12 s cd |
| X | **Ice Slide**: ride a ramp of ice you lay as you go, 5 s, 20 Cold | Shift+X | **Ice Pillar**: a column launches you 10 blocks, 15 Cold |
| Z | **Glacier Spikes**: 12-block line, 16 + 2 Frost, 25 Cold | Shift+Z | **Absolute Zero**: everything within 8 gets 5 Frost, 50 Cold, 20 s cd |
| V | **Glacier Wall**: 5 wide, 4 tall, melts after 15 s, 15 Cold | Shift+V | **Frozen Armor** (toggle): 8 absorption hearts, melee attackers get Frost, 3 Cold/s |
| C | **Ice Blade** (toggle): +8 melee, 1 Frost a hit, free | Shift+C | **Shatter**: frosted enemies within 10 take 20, frozen ones 40 and break out, 20 Cold |

**Models and animations:** an Ice Bolt lance (faceted, glowing core, tail fins), tumbling Ice Shards, a beam of a glowing core wrapped in a travelling spiral of ice crystals with frost rings and a frost bloom where it lands, the frozen-solid ice block (snaps shut, icicles, shivers, cracks; Ice Prison's is packed ice with blue-ice posts), the Frost pips, erupting spike clusters (Glacier Spikes as a travelling wave, Absolute Zero as a three-ring crown), an ice sled under the feet on the slide, Frozen Armor plates (chest, back, pauldrons with icicles, bracers, shins, a circlet), the Ice Blade on the forearm (third and first person), Shatter debris, and a cast pose for every move.

**Notes:** the old ice-tool wheel, Flash Freeze and the conjured Ice Blade item are gone (old blades still melt out of inventories). The ramp, wall, pillar and frozen water are tracked by `CryoIce`: each block melts on its own clock, a block mined or built over is never overwritten, and everything still standing is melted back when the server stops. Frozen Armor shatters when its hearts are gone.
## 9. Geokinesis Rework: Experimental (mutation)

**What was wrong:** it was switched off. It used H (Sinkhole) and N (Tectonic Pillar), a hold-to-charge Earthquake, a strain bar for Earth Armor, and a "ground type" damage table that was hard to read. Most moves were particles and real blocks rather than models.

**New design (user-approved, 2026-10-16):** an earthbender on an **Earth** meter (0-100). It refills **15 a second while you stand on natural ground** (stone, dirt, sand, gravel) and 5 a second anywhere else. Six keys, each with a Shift version, no H or N.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Rock Shot:** rip a stone out of the ground and fire it, 12. 8 Earth | **Boulder Toss** (hold to lift): a huge boulder, 26, and it shatters into shards. 30 Earth |
| **G** | **Earth Grip:** stone hands grab the target's legs, rooted 3 s. 15 Earth | **Sinkhole:** the ground under the target drops it 3 blocks and closes over it (it suffocates until it digs out; no bosses). 35 Earth, 15 s cooldown |
| **X** | **Rock Surf:** ride a slab of rock across the ground, fast, 8 s. 20 Earth | **Tectonic Pillar:** a pillar launches you or the aimed target 12 blocks up. 15 Earth |
| **Z** | **Earthquake:** a 10-block ring of shaking ground, 14 + stagger + Slowness. 30 Earth | **Earth Spikes:** a line of stone spikes 12 blocks long, 18. 25 Earth |
| **V** | **Stone Wall:** 5 wide, 4 tall, crumbles after 20 s. 15 Earth | **Earth Armor** (toggle): rock skin, -50% damage, -20% speed. 4 Earth a second |
| **C** | **Tunnel** (hold): bore through stone fast, 3x3, keep the blocks. 3 Earth a second | **Seismic Sense:** every mob and player within 32 glows through walls for 10 s (on your screen only). 20 Earth |

**Passives:** 50% knockback resistance on the ground; stone and dirt mine 2x faster; no fall damage onto natural ground.

**Getting it:** unchanged (the Geological Resonance Serum and Chamber). It returns to the power wheel and random serums.

**Built (2026-10-16).** Where the spec was silent, I decided the following:
- **A move needs the Earth to pay for it** (no "limp" state). Earth Armor and the Tunnel switch off at 0. The meter does not refill while one of those two is draining it.
- **"Natural ground"** is stone, deepslate, tuff, granite / diorite / andesite, calcite, dripstone, terracotta, dirt, grass, podzol, mud, moss, sand, sandstone, gravel, clay and the ores set in them. "Standing on" means on the ground with that block under your feet; in the air it is the 5-a-second rate.
- **Models, not blocks:** the rock, the boulder, the shards, the stone hands, the pillar, the spikes, the earthquake ripple and the sinkhole cave-in are entities drawn as scaled copies of the real block they came from (its own textures). They never change the world, so nothing is left behind. Only the Stone Wall and the Sinkhole change blocks (temporarily), and the Tunnel mines.
- **Rock Shot:** the stone rises out of the ground a step in front of you to your hand (4 ticks), then flies where you are looking at that moment.
- **Boulder Toss:** it heaves up out of the ground in front of you over 0.6 s and rides over your head while you hold Shift+R. Let go and it is thrown as soon as it is all the way up; after 8 s it is thrown by itself. 26 to everything within 3 blocks of the impact; the 10 shards are for show (no extra damage).
- **Earth Grip:** 20 blocks. The target must be on the ground. It is pinned to its spot with no jumping; players only where hard crowd control on players is allowed. Bosses (and anything over 200 HP) are only slowed (Slowness IV), not pinned.
- **Sinkhole:** 20 blocks. The ground must be natural and breakable 3 deep under the target's whole footprint, and you must be allowed to build there. Otherwise nothing happens and no Earth is spent. The target drops 3 blocks and the ground closes over it 5 ticks later, put back exactly as it was. The air pocket at its feet is filled back in once it is dead or has dug out (or after 5 minutes at most). Never under the caster. Mined sinkhole blocks are the original ground, so they drop normally.
- **Rock Surf:** about 15.6 blocks a second the way you face. It hops single steps and ends after 8 s, on a second press of X, in water, or after 1.5 s in the air. It does no damage.
- **Tectonic Pillar:** aim at a mob (20 blocks) and it is launched; aim at nothing and you are. Either way the launch is ~12 blocks straight up. It does no damage itself; the target's fall does. You get 8 s of no fall damage after launching yourself.
- **Earthquake:** the ripple runs from you out to 10 blocks over ~0.8 s and hits each creature once as the front passes it: 14 damage, a small jolt up, a 0.75 s heavy stagger, then Slowness II for 4 s. Things more than 4 blocks above or below you are missed.
- **Earth Spikes:** the line runs out 2 blocks a tick, one spike a block. Each creature is hit once, for 18, and thrown up.
- **Stone Wall:** 2.6 blocks ahead, snapped square to the nearest direction, rising a row a tick. It is made of the stone you stand on (sand makes sandstone; dirt, gravel and ores make plain stone, so it can never hand out ores). It is only placed into air or plants, never inside anyone, and only where you may build. The top row crumbles first. Mining a wall block gives nothing.
- **Earth Armor:** -50% to every hit except the void and /kill, with the plates made of the ground you stood on when you raised it.
- **Tunnel:** it bores the nearest 3x3 slice with stone or earth in it, up to 4 blocks ahead, every 2 ticks. Looking mostly level it is a walking-height tunnel (it follows a tilt up or down); looking straight up or down it digs a 3x3 shaft. The drops are an iron pickaxe's and go straight into your inventory. It only breaks natural ground, and only where you may build.
- **Seismic Sense:** the outlines are drawn by your client alone (`Minecraft#shouldEntityAppearGlowing`), never the server's glowing flag, so nobody else sees them.
- **Fall damage:** none when the block you land on is natural ground. Elsewhere it is normal, apart from the windows after Rock Surf (2 s) and your own Pillar (8 s).
- **Removed:** Earth Swim, the Ground Type table on the HUD, the iron-tool hands, vein mining and Haste, the Colossal Rock, the ravine-carving charged Earthquake and the old H / N moves.
- **Boss:** the Gravebound Geokinesis boss keeps its moves, on the new ids (Earth Spikes under `earthquake`, the Pillar under `rock_surf`, Earth Armor under `stone_wall`).
- **HUD:** mono keys, an always-on tan Earth Hairline, and bars for the boulder lift, Earth Armor, Rock Surf, Seismic Sense and the Sinkhole cooldown.
## 7. Wind Manipulation Rework: Experimental (mutation)

**What was wrong:** it was switched off (`Powers.ENABLED`). It had eight keys (H Rideable Tornado, N Vacuum), a 575-point Wind reserve plus long cooldowns, a Tailwind stance toggle and only particle effects.

**New design (user-approved):** an air-bender on a **Gust** meter. Six keys, each with a Shift version, no H or N. Every ability has a real model.

- **Gust (0-100):** moves spend it; it refills 15 a second (25 above y 100).
- **Getting it:** unchanged. The Atmospheric Pressure Serum inside an active Pressure Chamber / Industrial Fan Array. It returns to the power wheel and random serums.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Wind Blade:** a crescent that passes through enemies, 12 each. 8 Gust | **Blade Flurry** (hold): 4 blades a second at 6 each. 12 Gust a second |
| **G** | **Vacuum:** pulls every enemy within 10 to the aim point and holds them 1 s. 20 Gust | **Wind Push:** a 10-block cone, huge knockback, 6 + wall-slam damage. 15 Gust |
| **X** | **Tailwind:** a 14-block dash. 10 Gust | **Updraft:** launch 12 blocks up and open the glide. 15 Gust |
| **Z** | **Tornado:** a travelling tornado that pulls in mobs, spins them 6 s and flings them, 4 a second. 35 Gust | **Hurricane:** a wind wall round you for 5 s; projectiles flung away, mobs pushed out. 30 Gust |
| **V** | **Wind Barrier** (hold): projectiles in front are blown back. 6 Gust a second | **Glide** (toggle): elytra-like glide on the wind. Free |
| **C** | **Rideable Tornado** (toggle): ride a tornado that carries you and bowls through mobs for 6. 8 Gust a second | **Air Bubble:** 20 s; breathe underwater, fire out, harmful gas / effects cleared round you. 20 Gust |

**Passives:** no fall damage; arrows aimed at you veer off 30% of the time; no cobweb slow.

**HUD:** mono keys, an always-on pale-blue **Gust** Hairline, and Hurricane / Air Bubble timers.

**Models and animations:** a translucent swept crescent for the Wind Blade (with after-images); tornadoes as stacked, counter-rotating translucent rings inside a funnel skin over a dust swirl (the ridden one is a squat column with a flat top the hero stands on); a Vacuum vortex (spiral arms winding into a bright eye, rings collapsing inward); a Hurricane ring wall (two counter-turning streaked walls, racing rings, a dust skirt); a Wind Barrier swirl shield in front of the hero; an Air Bubble sphere; gust streak lines for Wind Push (a cone of streaks with pressure rings), Tailwind (trailing streaks) and the Glide; an Updraft column of rising rings. Cast poses for every move (slashes alternating sides, the pull, the two-palm push, the dash lean, arms up for the updraft, the overhead wind-up for the tornado, the cross-and-fling for the hurricane, held palms for the barrier, the spread glide, a surfer stance on the tornado, the cupped hands of the bubble).

**Built (2026-10-16).** Where the spec above was silent, I decided the following:
- **A move needs its full cost.** With too little Gust it is refused ("Not enough Gust"); nothing goes limp. A held move (Flurry, Barrier, the ridden tornado) ends when the meter runs dry.
- **Gust refills only while no held move runs** (like Elasticity's Stretch), otherwise the Flurry, Barrier and ride would cost nothing.
- **Wind Blade:** 1.6 blocks a tick, 30 blocks; it cuts each creature once and breaks up on the first block. The Flurry's blades are smaller, fly 24 blocks, fan out slightly and alternate hands; the first blade leaves at once.
- **Vacuum:** opens at the aimed creature or block (up to 18 blocks, lifted off the floor). Everything within 10 of that point is drawn in over half a second, then held turning round the eye for 1 s, then dropped. No damage. It never moves bosses or creatures over 200 HP.
- **Wind Push:** a ~40° cone; the shove is up to 3 blocks a tick, weaker at the far end. **The wall slam is 8** if the creature hits a wall within 1.5 s. Projectiles in the cone are blown away. Anything within 1.2 blocks is hit whatever way you face.
- **Tailwind:** 2 blocks a tick for 7 ticks (re-applied every tick so ground friction can't eat it), along your look (kept level when you are on the ground looking ahead). No damage.
- **Updraft:** a 1.5 block-a-tick launch (12 blocks under vanilla gravity); the Glide opens about 0.8 s in, at the top.
- **Tornado:** touches down 2 blocks ahead and travels along your aim at 0.2 blocks a tick for 8 s (so a creature caught late still gets its 6 s). It catches what comes within ~2.4 blocks of its core and drags in everything within 7. Each one is whirled up the funnel for 6 s (4 damage at each full second, 24 in all), then flung out high. Nothing is caught twice; bosses are never carried.
- **Hurricane:** a 4.5-block ring that follows you. Projectiles within ~7 blocks heading anywhere but out are flung outward and become yours; creatures inside are pushed out and round. No damage.
- **Wind Barrier:** about a 120° cone in front, 5 blocks. Incoming projectiles go back at their shooter (or straight back) and become yours.
- **Glide:** the same elytra hook as Elasticity's Parachute. No time limit; it ends on landing, in water, when you mount something or on Shift+V. Used on the ground it hops you up first.
- **Rideable Tornado:** you stand on top (3.6 blocks up); it follows your look at 0.55 blocks a tick and hugs the ground. Each creature it runs into takes 6 and is thrown aside, at most once a second. C again, Sneak, or an empty meter ends it. Sneaking dismounts (vanilla), so the Shift moves are not usable while riding; Tailwind and Updraft are refused while riding.
- **Air Bubble:** air is kept full; burning is put out every tick (lava still hurts); every 0.5 s it clears harmful effects from you and squadmates within 4 blocks and removes lingering gas clouds (area-effect clouds that are not yours) within ~5.5 blocks.
- **Arrow veer:** every projectile on course to pass within ~1.6 blocks of your middle gets one 30% roll when it comes within 6 blocks; a hit turns it well aside at the same speed. Bosses' projectiles count too.
- **No fall damage** now always (it was 80% less before, or none out of a glide).
- **Removed:** the Wind reserve, the Tailwind stance (+Speed / jump / step / +10 ability damage), the old updraft columns, sprint-jump glides, the rocket-free elytra boosts, the 50% slower drowning, the air-shell overlay and its texture.
- **Boss:** the Gravebound Wind boss now uses the new ids: Wind Blade, Wind Barrier (its cone blast that turns projectiles), Tailwind (a gust-dash into a close target and a shove), Vacuum, and Tornado (its storm round itself).
- **Not done:** first person shows the barrier swirl (fainter) and the Hurricane wall but not your own Air Bubble or glide streaks (they would fill the screen). The ridden tornado and the Glide steering are untested in real multiplayer.
