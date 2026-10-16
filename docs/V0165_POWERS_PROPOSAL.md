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
