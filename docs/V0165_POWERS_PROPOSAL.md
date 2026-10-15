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

**Look:** a player model with a detailed cyborg skin (chrome plates, red eye, glowing chest core). **H** switches between:
- **Civilian:** a hologram hides the plates and your normal skin shows. Only the passives work.
- **Combat:** the plates show and the full kit works.

**Resource: Power Core (100).** It regenerates 2 per second. Getting struck by lightning or standing next to a powered redstone block fills it faster.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Sonic Cannon:** hold to charge (up to 1.5 s), release to fire a blue beam that deals 6-16 damage with knockback. 20 core | **Sonic Burst:** a 6-block cone blast that pushes enemies away. 25 core |
| **G** | **Hydraulic Punch:** a heavy hit that breaks guards, 12 damage. 6 s | **Grapple Claw:** fire a cable 30 blocks. A block pulls you to it; a mob is pulled to you |
| **X** | **Thruster Dash:** a jet-boot dash 10 blocks in the direction you are moving. 4 s | **Hover:** hold to float, draining 5 core per second |
| **Z** | **Techno-Override:** hack what you aim at. Ultron drones, Sentinels, Stark Sorter Bots and iron golems fight for you for 20 s. Doors, pistons, lamps and TNT toggle. 40 core | **Scan:** highlight mobs and ores within 32 blocks for 10 s, and show the target's HP and armour on the HUD |
| **V** | **Boom Tube:** teleport to where you are looking (up to 64 blocks) or to your bed; your squad within 6 blocks comes too. 90 s | **Waypoint:** save the current spot as the Boom Tube's next destination (works across dimensions) |
| **C** | **Defense Matrix:** an energy shield that blocks 30 damage in front of you, for up to 5 s | **Self-Repair:** spend 3 iron ingots to heal 8 HP over 4 s. 30 s |

**Passives (combat form):** no drowning, hunger drains at half speed, 50 % less fall damage, and a +4 armour plating bonus. **Weakness:** taking lightning or electric damage in civilian form shocks you, and water stops the core from regenerating.

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

**Resource: Hellfire (100).** It regenerates faster at night and in the Nether, slower in daylight.
**Second meter: Vengeance.** Every hostile mob that damages you, a villager or a squadmate gets marked as "guilty". Hitting a guilty target fills Vengeance, and Vengeance powers the Penance Stare.

| Key | Tap | Shift |
|---|---|---|
| **R** | **Hellfire Blast:** hold to fire hellfire bolts, 2 per second, **20 damage each**, that set targets on fire. 10 hellfire per bolt (bosses capped like every power) | **Hellfire Wave:** a ground wave of fire 10 blocks forward |
| **G** | **Chain Whip:** a burning chain with 8-block reach, 9 damage, that yanks small mobs to you | **Chain Spin:** spin the chain around you for 3 s, hitting everything within 4 blocks |
| **X** | **Hell Cycle:** summon the flaming bike. It's fast, can drive up walls, leaves a fire trail, and can ride over lava. Tap again to dismiss | **Cycle Jump:** while riding, the bike leaps 15 blocks with a burning landing |
| **Z** | **Judgement Mark:** mark every guilty mob within 30 blocks so they glow red; marked targets take +25 % damage from you for 15 s | **Penance Stare:** lock eyes with a target for 2 s. Damage scales with Vengeance and with how "guilty" the target is (more vs mobs that killed villagers or players). **Undead and bosses take a fixed 15**; players take damage from their own kill count |
| **V** | **Hellfire Rush:** charge 12 blocks forward wreathed in hellfire, smashing through everything in your path for **18 damage** plus heavy knockback and burning. 30 hellfire, 8 s | **Hellfire Eruption:** slam the ground and hellfire pillars burst up in a 6-block radius around you for **25 damage**, launching enemies and leaving burning ground for 4 s. 50 hellfire, 20 s |
| **C** | **Soul Shield:** a ring of hellfire for 4 s that halves damage and burns attackers | **Rider's Wrath:** 10 s of Strength II and fire immunity for squadmates within 6 blocks |

**Passives (transformed):** immune to fire and lava, +6 max health, undead mobs are scared of you (Wither skeletons run away), and you can't drown. **Untransformed:** fire resistance only.

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
