# Teen Titans: Starfire, Beast Boy, Raven -- approved design (2026-11-12)

Approved by the user 2026-11-12 ("I like the build ideas, please build it now") with one change: **Starfire and Raven fly by
double-tapping Space** (like the Saiyan double-tap, through the shared directional flight), **X is a dash** and **Shift+X a
mobility combat move**. Decided for the user: Raven's emotional leak cracks glass/lamps only with mob griefing on (otherwise
cosmetic); Beast Boy is gained from both the green ocelot bite and the Sakutia Serum.

Drawn from the 2003 Teen Titans show, Teen Titans Go!, the New Teen Titans / Titans comics and the Titans live-action show.
All three are Hero-Tier **Primary** powers in the Guidebook's **DC** section. Keys follow the house rules: H = power selector
only, no N ability, X = movement, V = utility/mode, the rest attack. No skin changes (glow overlays only). (The craftable
Starfire costume and Raven's Cloak were built, then **removed by the user on 2026-11-12**: see "Costumes removed" below.)

**Titans Together (shared passive):** within 32 blocks of a squadmate who has Starfire, Beast Boy, Raven or Cyborg, every
Titan gets +10% damage and +20% bar refill, and a small Titans "T" shows next to the HUD bar.

---

## Starfire -- Koriand'r of Tamaran

**Source:** a Tamaranean who flies on joy, absorbs sunlight and fires starbolts powered by emotion; eye beams; Tamaranean
strength; survives space; learns any language by lip contact.

**Getting it:** the **Tamaranean Starstone**, found in End City chests and in the crater of the Omnitrix-style meteor events.
Right-click it in daylight.

**Passives**
- 30 health, Tamaranean strength (+6 melee, punches knock back), 20% damage resistance, no fall damage.
- **Survives space:** no drowning, no suffocation in the Galactus orbit dimension, no void freeze.
- **Solar charge:** in sunlight the Starbolt bar refills 3x faster; underground or at night it is slow.
- **Joy and Fury gauge (Gauge style).** One needle between Joy (left) and Fury (right).
  - Joy builds while flying, standing in sun, eating, and near squadmates. More Joy means faster flight and cheaper flight.
  - Fury builds when she is hit or a squadmate within 16 blocks is hurt. More Fury means starbolts up to +60% damage, but
    flight gets heavier (in the show her flight comes from joy).
  - At full Fury the eyes blaze and Shift+Z unlocks.
- Glow overlay: green eyes always; glowing hands while a starbolt is ready (soft shells fitted to the hand, first person too).
  The flame hair was removed in the 2026-11-13 tester round (user: "I never wanted that").

| Key | Move | Spec |
|---|---|---|
| R | Starbolt | fast green bolt from alternating hands, 7 dmg, small blast. Tap repeatedly to keep firing. 5 Starbolt |
| Shift+R (hold) | Star Burst | charge a two-handed ball up to 2 s: 10-28 dmg, 4-block blast. 25 |
| G | Tamaranean Grab | grab a creature in reach, carry it in flight, release to hurl it (fall damage counts). 15 |
| Shift+G (hold) | Eye Beams | twin green beams while held, 6 dmg/tick-pair, sets fire, BeamClash. 8/s |
| Z | Starbolt Barrage | 12 bolts spray in an arc over 1.5 s, homing slightly. 35, 6 s cd |
| Shift+Z | Supernova | needs full Fury: 2 s charge in the air, then a sun-coloured explosion, 45 dmg within 10 blocks, sets fire, blinds. Empties Fury to Joy. 100, 2 min cd |
| (double Space) | Flight | double-tap Space to take off / land; shared directional flight, Joy sets its speed |
| **X** | **Star Dash** | green-streak burst 10 blocks along your look (works in the air and on the ground), brief i-frames. 10, 1 s cd |
| Shift+X | Comet Dive | dive/rush along your look wrapped in green fire (from the ground it launches you up first), 18 dmg and knockback on impact. 20 |
| C | Tamaranean Charge | fly-tackle forward 8 blocks, carrying the first creature into a wall (+10 if it hits one). 20 |
| Shift+C | Ground Pound | double-fist slam, 12 dmg in 5 blocks, launches creatures up. 25 |
| V | Language Kiss | touch a villager: you "learn their tongue", 20% better trades with that villager for good and Hero of the Village style gifts once a day. Touch a player: each sees the other's name tag through walls 60 s |
| Shift+V | Glorious Radiance | toggle: your body lights the area (dynamic light) and Joy builds 2x, but bolts cost 25% more |

**Possible boss later:** Blackfire (her sister), a raid with Gordanian slavers.

---

## Beast Boy -- Garfield Logan

**Source:** Sakutia gave him green skin and the power to become any animal (always green), including extinct and alien
animals; he chains morphs mid-fight; in the 2003 show he turns into "the Beast" when cornered.

**Getting it:** the **Sakutia** infection. A rare **green ocelot** spawns in jungles. Let it bite you (it attacks once if you
feed it and then crouch next to it), get 60 s of "Sakutia fever", and you wake up as Beast Boy. Also a **Sakutia Serum** in
jungle temple chests. Random serums never give it.

**Look:** human form keeps your skin with a green tint overlay. Animals are **green-tinted vanilla mob models** where one
fits (wolf, ocelot as cheetah, parrot scaled up as hawk, polar bear, dolphin, bat, goat, silverfish as mouse). Custom bodies
only where vanilla has none: T-Rex, gorilla, rhino, elephant, pterodactyl, octopus, the Beast.
Fix round (tester notes): the wolf is now custom art too (`WolfModel`: damped tail sway, sits while crouching via
`BeastPose.sit`); the shared atlas got softer countershaded greens (dark back, cream belly) plus FUR_BACK / PLATE / NOSE
swatches; the rhino got armour-plate shells, a bigger raised head and horns that show from behind.

**Vegetarian:** Beast Boy can't eat meat or fish in any form (`BeastBoyDiet`: the `c:foods/*meat*` / `*fish*` tags,
`#minecraft:meat`, `#minecraft:fishes`, plus rabbit stew, rotten flesh, spider eye), refused at use time with a message.

**Passives**
- 20 health in human form. **Every animal form has its own health** (shown as green hearts in place of his own since v0.17.4; was a Slab bar). When the form's
  health is gone you're knocked back to human and dazed 1 s, keeping your own hearts. That's what makes big forms tanky.
- **Morph Stamina (Meter).** Big forms drain it, small forms are nearly free, and human form refills it. At zero you revert.
- **Morph Chain.** Each quick-morph attack in human form flashes into a different animal for a split second. Hitting with
  a *different* animal than last time adds +1 chain (max 5, +8% damage each); repeating an animal resets it. That's the
  show's fighting style.
- Animal instincts: night vision on, mobs' footsteps heard from further away (subtitles directional).

**Human form keys (quick morphs, back to human after each)**
| Key | Move | Spec |
|---|---|---|
| R | Ram | half-second rhino charge, 10 dmg, big knockback. 10 |
| Shift+R | Pounce | tiger leap onto the target, 8 dmg + bleed 3 s. 10 |
| G | Gorilla Smash | gorilla arms slam, 12 dmg, knocks down. 12 |
| Shift+G | Snake Strike | lunge as a snake: 5 dmg + Poison II 4 s, Slowness. 10 |
| Z | T-Rex Roar | T-Rex head roar in a cone: mobs flee 4 s, players' screens shake, 6 dmg. 25, 10 s cd |
| Shift+Z | The Beast | 30 s rage form (hulking werewolf-like Beast, 60 form health): 20 dmg swipes, leaps, roar, smashes through soft blocks. Morph Chain locked at 5. Can't change form or use the wheel until it ends. 100 Stamina, 3 min cd |
| **X** | **Context Morph** | instant movement animal for where you are: on ground = cheetah (very fast); in the air = hawk (glide/fly); in water = dolphin (swim burst). Press again to return |
| Shift+X | Fly Morph | become a fly: tiny hitbox, slips through 1-block gaps and under doors, nearly invisible, can't attack |
| C | Elephant Stomp | 14 dmg in 4 blocks, slows. 20 |
| Shift+C | Octopus Grab | 4 tentacles pull nearby creatures in, hold 2 s. 20 |
| V (hold) | Animal Wheel | radial wheel of sustained forms (below); release on one to morph, on Human to come back |
| Shift+V | Animal Senses | wolf nose 10 s: footprint trails of every creature and player nearby glow green |

**Sustained forms (from the V wheel; each has its own R / G / C)**
| Form | Health | Role | Keys |
|---|---|---|---|
| T-Rex | 80 | tank | R bite 18, G tail sweep 12, C roar |
| Gorilla | 50 | brawler | R punch 12, G throw creature, C chest-beat (buff squad +damage) |
| Rhino | 50 | charger | R charge 16 through soft blocks |
| Elephant | 70 | crowd control | R trunk throw, G stomp, C water spray (puts out fire) |
| Wolf | 30 | hunter | R bite 8, G howl (squadmates speed) |
| Bear | 50 | bruiser | R maul 14, G stand-up roar |
| Pterodactyl | 40 | flying | carry one player, R dive claw 12 |
| Hawk | 15 | scout | fast flight, R talon 5, G screech marks target |
| Bat | 10 | scout | echolocation pulse reveals mobs through walls |
| Shark / Octopus | 40 | water | shark bite 14 / octopus ink blinds |
| Whale | 120 | water transport | carries up to 4 players, R tail slam |
| Mouse | 5 | stealth | tiny, through 1-block gaps, mobs ignore |

**Possible boss later:** the Brotherhood of Evil, or Adonis.

---

## Raven -- daughter of Trigon

> **Superseded (2026-11-13): Raven was revamped.** The current kit -- 30 health, Composure tiers (Unsettled under 40, Rage
> under 10), Demonic Heritage / Dark Empathy / Mystical Awareness, the new main keys (Soul Shield, steerable Soul-Self,
> Telekinetic Crush, Dark Energy Wave, Demonic Ascension) and the ten-spell **Spell Wheel** on V -- is specified in
> [`RAVEN_REVAMP_DESIGN.md`](RAVEN_REVAMP_DESIGN.md) and built in `raven/RavenMoves`, `raven/RavenSpells`. The section below is
> the original 2026-11-12 kit, kept for history (Dark Shield, Shadow Grasp, Telekinetic Wave and Empath Heal no longer exist as
> keys; three of them became wheel spells).

**Source:** an Azarathian empath whose dark energy lifts and throws anything ("Azarath Metrion Zinthos"). She
phases through shadow, sends out her raven-shaped soul-self, shields, heals by taking others' pain, and must stay calm,
because her emotions leak out as power and Trigon's red-eyed side breaks through when she loses control.

**Getting it:** the **Book of Azarath**, rare in ancient city and stronghold library chests. Read it at night: you hear
"Azarath Metrion Zinthos" and the power settles in.

**Look:** your skin plus a dark aura overlay (black energy wisps from the hands, white glowing eyes while casting). No
costume item (Raven's Cloak was removed on 2026-11-12); only Trigon's Wrath draws a black hood and cape.

**Passives**
- 20 health, 10% damage resistance, slow fall always on (not while flying).
- **Dark Energy (Slab)**, 100, refills 5/s. Moves cost it.
- **Composure (Hairline under the Slab).** Starts calm.
  - Taking damage, casting big moves and being near hostile mobs for a long time push it toward Rage.
  - **Emotional leak:** below 30 Composure, now and then something nearby cracks, like glass or a lamp, or a mob gets pushed.
    It's cosmetic and won't break blocks unless mob griefing is on. Bolts do +25% damage.
  - Below 10 the eyes turn into four red eyes and Shift+Z unlocks.
  - Meditating brings her back.
- Raven's soul-self is not the Dark Shadow quirk: it's one big raven made of energy, and it engulfs targets rather than fighting.

| Key | Move | Spec |
|---|---|---|
| R | Dark Bolt | black energy bolt, 8 dmg, passes through one creature. 8 |
| Shift+R (hold) | Telekinesis | wrap the block, item or creature you look at in black energy (not bosses), steer it with your look, release to throw (creatures take 10 + impact, blocks fly as falling blocks and land back). 10 + 4/s |
| G | Shadow Grasp | black claws rise from the ground under the target: hold 2 s, 6 dmg. 15 |
| Shift+G | Soul-Self | your raven soul flies out along your look 25 blocks, engulfing every creature it passes: 16 dmg, Blindness, Slowness. 35, 8 s cd |
| Z (hold) | Dark Shield | black energy dome (3-block radius) around you, absorbing projectiles and 40 damage, moves with you. 5/s |
| Shift+Z | Trigon's Wrath | needs Rage (Composure under 10): you grow into a giant cloaked shadow (scaled 3x) with four red eyes for 15 s. Tentacles of dark energy grab and slam everything within 12 blocks, 30 dmg, and fear on players. Afterward you're drained (Composure resets to calm, Dark Energy to 0). 3 min cd |
| (double Space) | Flight | double-tap Space to take off / land: calm levitation flight, cloak drifting (shared directional flight) |
| **X** | **Shadow Dash** | become a streak of dark energy and shoot 10 blocks along your look, passing through creatures (no damage taken during it). 12, 1 s cd |
| Shift+X | Shadow Step Strike | sink into your shadow and erupt out of the ground under the creature you look at (up to 20 blocks): dark claws launch it up, 12 dmg. 20, 4 s cd |
| C | Telekinetic Wave | lift every creature within 8 blocks up 4 blocks, hold 1 s, slam down for 12. 30 |
| Shift+C | Debris Storm | tear up 8 nearby blocks into an orbit and fire them one per click (restored after 10 s; no block changes if mob griefing is off, then the debris is energy copies). 30 |
| V | Empath Heal | aim at a squadmate: take up to 10 of their missing health as your damage and heal them double; with no one aimed at, heal yourself 4 and cure effects. 20, 10 s cd |
| Shift+V (hold) | Meditate | float cross-legged chanting softly: Composure and Dark Energy refill fast; you can't move or attack |

**Possible boss later:** Trigon, as a world event that rips open a portal, with Raven players getting a special finisher. Slade as the herald.

---

## Build split (2026-11-12)
Four parallel worktree agents: STARFIRE, RAVEN, BEASTBOY-POWER, BEASTBOY-ART. Contract between the Beast Boy pair (already in
master): `beastboy/BeastForm` (form ids, sizes, health, art flag) and `client/beastboy/BeastFormArt.render(...)` (ART fills it,
POWER only calls it and falls back when it returns false). Titans Together is added after the merges. Lang text goes into
`scratchpad/<feature>/lang_*.json` fragments merged key by key.

## Costumes removed (2026-11-12)
The user asked for the Starfire and Raven suits to be removed. Gone: the Starfire Top / Skirt / Boots (`starfire_suit_*`,
`StarfireSuitItem`, its skin-costume hook in `QuirkCostume` / `QuirkSuitArmourMixin`) and Raven's Cloak (`raven_cloak`, its
recipe, armour layer, the blue / white / red Composure-coloured hood and cape), with their recipes, textures, creative tab
entries, Guidebook sections and lang keys. Kept: Starfire's glowing fists (the flame hair went on 2026-11-13, the glowing eyes on 2026-11-13 too); Raven's wisps, white casting eyes,
four red Rage eyes, Soul-Self and the Trigon's Wrath shadow (its black hood and cape now come from the Wrath alone,
`RavenCloakLayer` / `RavenAuraLayer`). Saved copies of the removed items load as air (vanilla warns once); no mod state referenced
them. The old generator scripts stay in `scratchpad/starfire/` and `scratchpad/raven/` for reference.

## Tester round (2026-11-13)
- Starfire: flame hair removed; hand glow refitted to the arm's hand cube (wide and slim, inner + outer additive shell with
  faces fading at the edges, `textures/entity/starfire/hand_glow.png`) and drawn on the first-person arm too.
- Raven: the Dark Shield dome draws without depth writes (`RavenDomeRenderType`), so the player and mobs inside always show
  (with ImmediatelyFast batching, the old depth-writing dome could draw before the player's translucent skin and hide it).
  Casting eyes lost their big additive halos, Rage eyes got tighter, dimmer ones; the hand wisps are smaller, no smoke, rarer at rest.
- Landings: `HeroLandingStyle.STARFIRE` (id 30, fist, green starbolt burst: `StarfireLanding`) and `RAVEN` (id 31, wings kneel,
  dark soul ring: `RavenLanding`), registered on the shared `HeroLanding` detector. Raven's calm 9 b/s cruise is under the
  detector's fast-descent bar, so her landing needs a sprinting dive; Starfire lands from any dive.

## Starfire animation round (2026-11-13)

User: "make her ability animations better". Every move got eased body keyframes (`client.starfire.StarfirePose`, hooked in
`HumanoidModelMixin` after the flight pose, plus `FlightPoseHelper.lean` for whole-body leans) and first-person arms
(`mixin.StarfireFirstPersonPoseMixin`, QuirkFirstPersonPose pattern). Driven by synced state only: each move starts at its
cooldown stamp minus its cooldown; the new `StarfireState.anim` bits say which hand threw the last starbolt and whether Grab
is carrying (no gameplay). Starbolt: overhand sweep from alternating hands, released ~1 tick after the press (the bolt spawns
on the press, so the wind-up is short; its comet tail no longer reaches back past the hand and a flash blooms at the hand).
Barrage: left-right jabs with the torso turning along the arc. Star Burst: hands cup the ball, palms drive out, recoil.
Eye Beams: head and shoulders forward, fists back. Supernova: curled ball, then a star. Dash / Comet Dive laid along the
path; Charge shoulder-first; Ground Pound fists overhead then a crouching slam (on the ground the slam pose lands ~2 ticks
after the hit); Grab reach / carry / overhead hurl; Kiss lean-in with a heel kick; Radiance arms open. Flight: graceful
hover, trailing cruise, swept-back speed pose, and a lean-back brake when she pulls up out of speed.
Films: `docs/screenshots/v0173/starfire_anim/` (before/ = previous build).

## Beast Boy morph round (2026-11-13)
- **Every form change morphs** (human <-> animal, animal -> animal chains): 10 ticks (0.5 s), driven by the synced
  `BeastBoyState.morph` (the form he left + game time), so every watching client plays it; stats and hitbox still switch at once.
  Code in `client/beastboy/morph`: `MorphAnchors` (7 shared anchors per body: head, torso, front limbs L/R, hind limbs L/R, tail;
  the player model's are exact, each animal's come from a family template fitted to its measured idle mesh), `MorphField` (every
  vertex skinned to its own body's anchors and pushed through the anchors blended toward the other body, size blended
  geometrically so whale <-> fly reads), `MorphBuffers` (bends any renderer's output: the vanilla player with armour, the box and
  mesh animals), `BeastMorph` (timeline, glow, sparks, landing puff). Timeline: the old body reshapes (0-0.36), its parts shrink
  into their roots (0.36-0.72) while the new body breaks out at 40% and swells (0.34-0.69); a squash at the start, a spring pop at
  the end, skin green, green energy along the seams between parts.
- **Quick morphs**: the tiger pounce and snake strike show the whole animal (full morph in over the first 3 ticks, out over the
  last 3). The partials use the new blocky parts (`model/QuickMorphs`): gorilla arms (GorillaGeo), rhino head (RhinoGeo), T-Rex
  head (TRexModel.quickHead), elephant head + legs (ElephantGeo), octopus arms (SeaShapes.octopus). His matching part swells,
  hides while the animal part is out (attached to his model at the joint, following his pose; the gorilla arm also in first
  person), and comes back. LegacyBigParts and the old smooth partials are gone.
- Sound: a layered morph sound (honey-block slide + illusioner mirror move, pitched by body size) under every morph and quick morph.

## Beast Boy hearts and swimming round (v0.17.4)
- **Green hearts** (user: "make the animal hp bars that show up green hearts instead please, make it show up instead of the players
  hearts when transformed"): the form-health Slab is gone. While he is an animal, vanilla's own heart row shows the form's health:
  `BeastBoyHeartsMixin` wraps `Gui.renderPlayerHealth`'s `getHealth()` and `getAttributeValue(MAX_HEALTH)` (MixinExtras WrapOperation)
  with the form's pool and maximum, and swaps the plain heart / container sprites in `Gui.renderHeart` for `hud/beast_heart/*` (9x9, own
  palette, `scratchpad/beastboy/gen_green_hearts.js`). So the rows (the whale's 120 is six rows), half hearts, the low-health wobble, the
  regeneration wave, the armour bar's lift and the hurt / heal blink all behave like vanilla (the pool's hits broadcast a damage event,
  which sets the blink's invulnerability window). Poisoned / withered / frozen / absorption hearts keep their vanilla look.
  Code: `client/beastboy/BeastHearts`.
- **Sea-form shake and tilt, root causes:** (1) `SwimRig` took the speed and travel pitch from frame-to-frame changes of the interpolated
  position over wall-clock time (noisy: frame pacing never lines up with the ticks) and made the pitch `atan2(-vertical, horizontal)`, so
  the player's small up-and-down bob at the surface or while hovering, over almost no horizontal speed, swung the nose up to +-70 degrees
  and back; (2) the models added the look pitch to the body pitch, so a hovering animal tilted whenever he looked up or down; (3) vanilla
  turns the body toward the travel direction and only drags it after the head past 50-75 degrees, and the sea bodies are drawn along the
  body yaw, so they jerked round; (4) vanilla player swimming bobs a player in and out at the surface, flipping the in-water state the
  animation keys off.
  Fixes: `SwimRig.track` runs on the game clock (the ticks' own steps `x - xo`, eased on `tickCount + partialTick`); the pitch follows the
  swim only once he's really moving, stays shallow for mostly-vertical motion and at the surface; a leap keeps the swim pose; the body
  pitch no longer adds the look pitch; `BeastBoySwimTurnMixin` (common, `tickHeadTurn`) eases the body toward the look in the water on
  every side (the whale's rider seats hang off the body yaw, so they stay on its back). The octopus used to lean a fixed 40-65 degrees
  whenever it moved; it now lies out mantle-first along the swim (about level at a cruise, following the climb / dive), upright at rest.
- **Sea-form controls** (`client/beastboy/BeastSwim`, from `Player#travel` via `BeastBoySwimTravelMixin`, local player only): swim where
  you look (W along the look, S backs off at 45%, A / D slide), Space / Sneak rise / sink, Sprint a burst (blocks a tick, cruise / sprint:
  dolphin 0.26 / 0.48, shark 0.24 / 0.44, octopus 0.19 / 0.36, whale 0.18 / 0.30), the velocity eases (0.11 a tick speeding up, 0.075
  gliding), no gravity or buoyancy in the water. At the surface a spring holds him with his eyes just out of the water (first person sees
  over it, the fin / back shows; he can't look his way out of it, no bobbing), and a near-level look (under ~12 degrees down) keeps
  cruising there: look further down or Sneak to dive; Space there leaps (12-tick cooldown); swimming into a ledge near the surface climbs out; server shoves (knockback, a
  move's lunge) are adopted and eased out of.
