# Omnitrix: Feedback (v0.17.2)

User: "build a feedback alien please, do research on his powers and abilites in the show and base his moves on that please, add him
to the mistransformation list. use this skin: https://www.planetminecraft.com/skin/feedback-ben-10-5251468/ Give him the tail and long
ear things with the plug things at the end please, look at the references and make sure the his tail and ear things look good and
that the plugs at the end look really accurate aswell please, please do this well im begging you claude".

Feedback belongs to the **prototype** Omnitrix. His dial glyph is U+E01A, after Buzzshock's U+E019. He is not on the Recalibrated
Omnitrix.

## Found by accident

- He is a hidden alien (`Aliens.HIDDEN`), like Cannonbolt, Wildvine, Ditto, Upchuck and Buzzshock. At first he is **not on the dial**.
- Every 11th mistransform (22nd, 33rd...) finds one hidden alien a mistransform can find. The pick is random and even among the ones
  still unfound (`Aliens.pickHidden` / `unfound`), so he joins that draw. Way Big is `ITEM_ONLY` and stays out of it.
- Once found, he is on the dial for good. The unlock belongs to the player, so taking the watch off and putting it back on keeps
  him (`Omnitrix.keepPlayerData`).
- Test tools: `/projecthero power tune omnitrix found on|off` and `mistransforms <count>`, as for Cannonbolt.

## In the show (Ben 10: Omniverse)

Feedback is a Conductoid, from Teslavorr. He is lanky and black, with a white chest panel (the Omnitrix in its middle), one green eye
and a wide toothy grin. Two long antenna cables grow from the back of his head and one long tail from his lower back. All three end in
gold plugs with prongs, and his fingers and toes are capped in gold plugs too. He was Ben's favourite Omniverse alien.

What he does in the show:
- He **absorbs energy** through his plugs, fingertips and antennae: electricity, energy blasts, lasers, power lines.
- He **redirects** what he absorbed as powerful blasts. The species' name is about conducting it. Ben turned Malware's own energy back
  on him this way.
- He **plugs his cables into machines** and power sources and drains them.
- He fires energy he has stored as bolts and beams.
- He is agile and quick, with sharp reflexes (he knocked one of Ultimate Humungousaur's missiles back at him), and whips with his
  cables and tail.

His weaknesses in the show:
- He needs energy to work with. Without a source he is a lanky brawler.
- Too much energy at once can overload a Conductoid.

Sources: the show itself; the Ben 10 wiki's Conductoid page (https://ben10.fandom.com/wiki/Conductoid) for the redirection and the
missile reflexes; a fan FAQ summary of the plug-like fingers, tail and antennae. Fan-fiction wikis were not used.

## Body

The skin is the user's planetminecraft skin. It was downloaded through the built-in browser, since planetminecraft blocks curl
(`scratchpad/ben10_v0171/feedback_dl/feedback_skin.png`).

| | |
|---|---|
| Health | 32 |
| Armour | 8 |
| Speed | 1.2 |
| Size | x1.05 (lanky, a little taller than Ben) |
| Jump | +0.35 |
| Fists | +3 (the attack stat), plus Plug Fingers' shock |
| Safe fall | 6 blocks |

- **Badge:** the skin paints the Omnitrix as a little hourglass in the middle of the white chest panel (jacket texels 23..24 x
  37..39, over a dark pair on the body layer). `gen_feedback.js` paints those in the panel's own whites, and the mod's badge sits there
  (body bone, y 2.5).
- **Eye:** `feedback_glow.png` draws his one green eye full-bright.

### The cables and plugs (FeedbackCables)

The reference pictures are in `docs/reference/feedback/`.

- **Three cords:** his left ear rises off the back of his head in a high arc and falls to his hip. His right ear loops out lower and
  hangs to his hip. The tail leaves the small of his back and curves down to the ground beside him. Those resting shapes are
  Catmull-Rom curves through hand-placed points, traced from the references. The ears ride the head bone (they turn and nod with it);
  the tail rides the body.
- **Physics:** each cord is a rope of 14 points simulated in the world every client tick (verlet, like Ghostfreak's tail). They are
  pushed out of blocks, so they lie on the floor and bend round walls.
  - **Round 2** (user: "the tail and ears react way too much to movement, they jiggle a LOT and shake around a lot and go inside his
    body sometimes, i like this detail but tone it down a bit please"). The cords are now calm:
    - The first points are rigid with the bone: 2 for each ear, 3 for the tail. The bases never swing.
    - Round 3 (user: "please give it more movement now"): the rest swing freely again on soft springs (0.42 at the root to 0.12 at the tip), damped at 0.76 so a swing settles instead of ringing on.
    - They carry 30% of his own movement, so they trail about as far as round 1 did, with far less shake.
    - The idle sway is three quarters of what it was.
  - **Body collision** covers the head, torso and legs, padded by the cord's thickness. Out of the torso or legs a cord always leaves
    by his back or a side, never through his front or out under his torso.
  - **The tail roots on the surface of his back.** It used to root inside the body and could poke out at the bottom of the torso while
    he moved (user: "sometimes you can see the tail poking out of at the bottom of his torso").
- **Drawn smooth:** the points go through a spline, 4 rings a link and 8 sides a ring. A parallel-transport frame keeps them from
  twisting, with smooth normals. They are blue-black (0x1F242B), between the skin's darks. The ears are about 1.5 px thick. The tail
  swells to 2.7 px where it leaves his back.
- **The plugs** follow the reference:
  - a round gold barrel, 12-sided, 2.5 px across and 6.5 px long (the reference's 2.6 : 1), a little over twice the cord's width
  - the cord enters a black socket that fills almost all of its back face, with a thin gold lip
  - three thin dark-brown ring grooves (each a small step in), toward the front, split it into four bands
  - a bevelled front
  - two thin, flat, paler gold prongs side by side out of the front (about 0.3 of the barrel's length), set well apart with their flat faces toward each other

  They light up blue as his Charge fills, flicker when he is nearly full, and spark at 60+.
- **No finger or toe caps** (round 2; user: "remove the plugs on his fingers please, they dont look good in first person either.
  also remove the plugs from his toes please"). The hands and feet are the skin's own.
- **The skin's painted plugs are gone** (round 2; user: "since hes got the ear models remove the ear plugs from his back form his skin
  model please. Also remove the tail plug from his leg"). `gen_feedback.js` paints the gold and silver plug texels on his back and on his
  right leg's upper back and side in the black round them, and clears the same spots on the overlay layer. The 3D cords replace them.
- **Checked against the references** over four in-game passes (see docs/screenshots/v0172/feedback/side_by_side_*.png). The passes made the ear arcs bigger and wider and the plugs longer, with finer grooves.
- **Round 2 footage:** feedback_walk_sprint.mp4 (walking from the side, sprinting from the front three-quarter, a quick turn, then
  sprinting away), plus feedback_round2_turnaround.png.
- **Moves bend them:** Absorb flares all three cords forward round him, plugs facing out. Plug Whip sweeps the tail round in front
  of him at waist height. Plug In stretches all three out to what they plug into, and beads of light run up them into him.

## Round 3

User: "make his lightning blue, his lightning and powers are blue in the show", "swap c and shift C abilities with z and shift z abilities
please", "swap G and shift G abilities with v and shift V abilities please", "remove time limit of absorb, player can hold v as long as
they want to absorb energy", "replace the shift+X move with an ability that uses charge and flies, as long as player holds shift+X they
fly in the direction they are aiming by shooting out energy behind them", "edit and update animations on all moves please".

- **Blue:** his energy is 0x2F9BFF with a 0xD8F0FF hot edge, on every bolt, ring, beam, plug glow and the Charge meter. Vanilla's electric
  spark particle is white, so his sparks are a blue mote (`FeedbackKit.SPARK`). The gold of the plugs themselves stays gold.
- **Keys:** G / Shift+G took V's moves (Power Feed, Overclock). V / Shift+V took G's (Absorb, Redirect). Z / Shift+Z took C's (Full Discharge,
  Feedback Cannon). C / Shift+C took Z's (Plug Whip, Plug In).
- **Absorb has no time limit.** It is `AlienMove.hold` on V: braced until V comes up, then a 4 s cooldown. (A holdable key starts its hold
  after the framework's 8 ticks.) Every key keeps a tap, so a tap of V is **Quick Absorb**, the same brace for 1 s, sharing the cooldown.
  Overcharge is what stops an endless brace being free. While he braces, thin arcs crawl in from the air to his plugs.
- **Surge Flight replaces Cable Swing** on Shift+X. Hold it: energy shot out of his palms behind him drives him along his aim, 22 blocks a
  second, 36 sprinting, through `DirectionalFlight`. It spends 5 Charge a second, needs 5 to lift off, and stops when either key comes up
  or the Charge runs out. No fall damage until he lands. The 1 s cooldown starts when it stops. The jets are tapering cones on the arm
  bones, with bolts torn off behind them (`FeedbackFx`); his body lies along the aim (`ThrustFlightPose`).
- **The cords' physics were not touched.** Removing the swing took out the one line that stretched the left ear to its anchor.
- **Animations:** every clip was rebuilt with hips and legs, and every move has a first-person clip. Absorb, Plug In and flight are held
  stances that a clip can play over, and each has an exit clip instead of snapping back.

## Charge

His meter (HUD: "Charge", 0-100, flashing FULL).

| Fills it | |
|---|---|
| Absorb (hold V), braced | shots, energy, fire and explosions: no damage, 4 Charge for each point |
| Energy Sponge (always) | energy hits (fireballs, wither skulls, the Warden's boom, magic bolts, alien blasts) do half; the other half is Charge (x4) |
| Lightning | never hurts: +40 (once a second) |
| Plug In (Shift+C) | a power source: 1-3 Charge a tick for 2 s; a creature: +6 every half second (machines +9) |
| Plug Fingers (punch) | +3 |
| Storm Feeder | a thunderstorm under open sky: +1 a second |

**Power sources:**
- 1 a tick: powered redstone wire, anything powered or lit (levers, buttons, repeaters, rails, lamps, furnaces), and any block
  giving a signal
- 2 a tick: a redstone block, a beacon, or a charged respawn anchor (drained: one charge a second)
- 3 a tick: a powered lightning rod

What you absorb in the last 15 s is also banked for Redirect.

## Keys

| Key | Move | What it does | Cooldown |
|---|---|---|---|
| R | **Feedback Blast** | a fingertip bolt at the crosshair (28): 14 for 10 Charge, knocked back; with less, a Static Spark (6) | 0.6 s |
| Shift+R | **Rapid Fire** | five bolts from alternating hands over a second (25 Charge): 6 each | 7 s |
| G | **Power Feed** | 25 Charge into 6 health; clears slowness and weakness | 5 s |
| Shift+G | **Overclock** | 30 Charge: 10 s of Speed II, Jump Boost II and shocks x1.25 | 20 s |
| X | **Conductoid Leap** | a long springy leap where you look; no fall damage for 2 s | 2 s |
| Shift+X (hold) | **Surge Flight** | energy shot out behind you drives you where you aim: 22 blocks a second, 36 sprinting, 5 Charge a second (5 to lift off); stops when you let go or run out; no fall damage until you land | 1 s, from letting go |
| Z | **Full Discharge** (ultimate) | 40+ Charge, spends all of it: 14-30 to everything within 3-8 blocks (by Charge), knocked back, stunned 1 s (machines 3 s) | 45 s |
| Shift+Z | **Feedback Cannon** | 50 Charge: a piercing beam (32): 24 to everything on it, knocked back, stunned 0.5 s | 15 s |
| V | **Quick Absorb** | a tap: the Absorb brace for 1 s | 4 s, shared |
| Hold V | **Absorb** | braced for as long as V is held, arms and plugs out: shots, energy, fire and blasts become Charge instead of damage | 4 s, from letting go |
| Shift+V | **Redirect** | sends back what you absorbed in the last 15 s (15 Charge): 12 + 1.5x it, 30 at most, at the crosshair or back at whoever sent it | 5 s |
| C | **Plug Whip** | the tail lashes across in front of you (4 blocks, 140 degrees): 12, knocked back; with 5 Charge the plug discharges for 4 more | 2.5 s |
| Shift+C | **Plug In** | cables plug into the creature you look at (8) or the nearest power within 6. Power: Charge for 2 s. A creature: drained 3 every half second (+6 Charge; machines x1.5 and stunned), then reeled in | 10 s |

H is the power selector and N is the Omnitrix's, as for every alien. A move that needs Charge (or a target or a power
source) refuses with a message and spends no cooldown.

Damage follows the prototype's scale: quick moves about 6-14, normal 12-24, the ultimate up to 30.

## Passives

- **Plug Fingers** (attack): a punch shocks for 3 more (scaled by the swing's charge) and sips 3 Charge.
- **Conductoid:** lightning never hurts him; a strike is 40 Charge.
- **Energy Sponge:** half of any energy hit is drunk, even unbraced.
- **Storm Feeder:** +1 Charge a second in a thunderstorm under open sky.
- **Machine Drainer:** his shocks do x1.5 to machines (Upgrade's `UpgradeKit.machine`: golems and mechanical mobs), and draining
  one gives half again.

## Weaknesses

- **Overcharge:** absorbing when his Charge is full overloads him: 2 damage and slowed for 2 s (once a second).
- **Grounded:** in water his Charge leaks away, 2 a second.
- **Needs energy:** without Charge his blast is a weak spark, and Rapid Fire, Redirect, Feed, Overclock, Surge Flight, Discharge and the Cannon are locked.
- Not on the dial until a mistransform finds him.

## Build notes

- Server: `omnitrix/alien/feedback/FeedbackKit`. Charge is a synced kit timer (`feedback.charge`), as Upchuck's Gut is. The plug
  target (a block as `BlockPos.asLong`, or an entity id + 1) is a synced timer too, so the client can stretch the cords to it.
- Client: `client/omnitrix/alien/FeedbackClient` (skin, badge, eye, poses, first-person clips, the Charge HUD), `FeedbackCables` (the
  cords, plugs and their glow and pulses), and `alien/feedback/FeedbackFx` (bolts).
- **Bolts:** short-lived held `AlienProjectileEntity` carriers, as Buzzshock's are:
  - `feedback_arc`: a bolt from his hand (or from the entity the colour field names)
  - `feedback_big`: the Redirect, Discharge and Cannon bolt
  - `feedback_suck`: energy pulled from what he absorbs or drains into his nearest plug

  All are drawn with `BrainstormFx.bolt` in his electric blue (0x2F9BFF).
- **Registration:**
  - `Aliens.FEEDBACK` and `Aliens.HIDDEN`
  - `OmnitrixMod` (register, and `clearSessionState`, which `ServerStateReset` calls)
  - `OmnitrixClient`
  - the `OmnitrixInfo.DIAL_ORDER` glyph list (after `buzzshock`)
  - `font/omnitrix_dial.json` (U+E01A)
- **Textures:** `scratchpad/ben10_v0171/gen_feedback.js`. The dial icon is the user's (`scratchpad/feedback_icon_src/feedback.png`), through `node scratchpad/gen_omnitrix_icon_transplant.js feedback=scratchpad/feedback_icon_src/feedback.png`, so the frame matches
  the others exactly. (The placeholder used before it came from `gen_feedback_dial_placeholder.js`.)
- **Tests:** `OmnitrixFeedbackGameTests`:
  - the dial and glyph, and every key having a move
  - blasts spending Charge, and hitting machines harder
  - moves that need Charge refusing when empty
  - Absorb, Redirect and the Energy Sponge
  - lightning and Overcharge
  - Plug In on a redstone block and on a golem, and Plug Fingers
  - the whip, Rapid Fire, Discharge and the Cannon, Feed and Overclock, the leap, Surge Flight, held Absorb, and water

  Note that Hard difficulty scales a mob's shot at a player by 1.5, so what gets absorbed is the scaled amount. The discovery tests in
  `OmnitrixCannonboltGameTests` now name Feedback in the pool. The prototype dial count is 17.
- **In game:** `docs/screenshots/v0172/feedback/`, from the harness `scratchpad/FeedbackHarness.v0172.java.txt`. It needs a
  `feedback.flag` file in its run dir, and a temporary camera mixin (`scratchpad/FeedbackHarnessCameraMixin.java.txt`) for the true
  front, side and back views.

## Decisions made while building (no questions asked)

User (via the release session): "do everything automatically and dont ask me any questions please and add this all to my test modpack once its done please".

- **The core is a Charge meter (0-100)**, filled by absorbing and draining and spent on his blasts. The show's Feedback has nothing to fire without something to absorb.
- **Energy colour:** pale electric yellow at first; round 3 made it the show's blue (0x2F9BFF, user).
- **Energy Sponge is passive at half.** Full absorption needs the V brace, so he isn't immune to anything energy-based.
- **Overcharge** (absorbing while full hurts him) is the show's Conductoid overload risk, turned into a weakness. **Grounded** (water drains Charge) is a light second one.
- **Plug In drains rather than destroys.** Only a respawn anchor actually loses charges; redstone blocks, lit lamps and the like are left as they are, so builds aren't griefed.
- **Cable lengths and arcs** were traced from the two reference pictures, then made a little wider so the ear plugs hang clear of his hands at Minecraft scale.
- **Plug proportions** come from the clearer reference (the second picture): barrel about 2.6 : 1, prongs about 0.3 of the barrel, three thin brown groove lines toward the front.
- **The dial icon** is the user's own ("Use this for feedbacks alien icon please"), from `scratchpad/feedback_icon_src/feedback.png` through the transplant script, which keeps the shared frame. It replaced the placeholder.
