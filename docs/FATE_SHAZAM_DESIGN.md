# Doctor Fate and Shazam -- approved design (2026-11-12)

Approved by the user 2026-11-12 ("Yes, build both"). Open choices decided as proposed: the Shazam word must be the whole
message; during Possession Nabu fights for you while you watch.

Drawn from the comics (Kent Nelson, Khalid Nassour, the JSA; Billy Batson and the Shazam Family), Young Justice, Justice
League Unlimited, the Shazam! and Black Adam films, and Batman: The Brave and the Bold. Both are Hero-Tier **Primary** powers in
the Guidebook's **DC** section. House key rules: H = selector only, no N, X = dash, Shift+X = mobility combat move, V =
utility/mode. Flight is **double-tap Space**, like Starfire and Raven.

---

## Doctor Fate -- the Helmet of Fate and Nabu

**The idea:** the helmet makes you one of the strongest magic users in the mod, but it isn't *your* power. Nabu, the Lord
of Order inside it, wants your body. The longer and harder you use it, the more he takes over. You're always choosing
between more power and more control.

**Getting it:** the **Helmet of Fate** in a new small structure, the **Tomb of Nabu**, a sandstone burial chamber under rare
desert pyramids, guarded by mummy-skinned husks. Picking it up opens Nabu's offer: "Will you serve Order?" with **Accept** or
**Refuse**. Refusing leaves the helmet on its pedestal (another player can take it). Random serums never give it.
v0.17.3 (user): the helmet is **not an item** ("make it similar to the Nova ability"): using the pedestal opens the offer,
Accept grants the power and empties the pedestal, and from then on **H** puts the helmet on / takes it off. This overrides the
house "H = selector only" rule for Doctor Fate, as Nova does; Shift+H still opens the power wheel.

**Two states (H puts the helmet on or takes it off):**
- **Without the helmet (Kent Nelson's own magic):** weak versions of R, G and X only, normal health. Nabu's Dominion drains.
  The Amulet of Anubis lets you see Nabu's bar.
- **Wearing the Helmet:**
  - 40 health, 30% damage resistance, flight, immunity to Wither and Poison.
  - Every spell is at full strength.
  - Look: the golden helmet, Cloak of Destiny and amulet appear. They're a suit layer over your skin, and your face is
    hidden by the helmet.
  - v0.17.3 (user): the suit is the DCEU Doctor Fate skin by **JMCT** on PlanetMinecraft
    (https://www.planetminecraft.com/skin/doctor-fate-dceu/, original in `docs/reference/doctorfate/`, see `SOURCE.txt`).
    **No licence is stated on the page: check it with the author / credit them before a public release.**
    `scratchpad/doctorfate/build_fate_skin.js` makes `suit_slim.png` (the skin is slim) and a wide `suit.png` from it, cuts out
    the helmet's side openings so your own skin shows there, takes the painted cape off the back and paints the cloth
    Cloak of Destiny (`cloak.png`) in the skin's colours. The skin's head is the helmet; the old separate helmet model is now
    only worn by the Avatar of Order. Your own outer skin layer is hidden while the suit is on.

**Dominion (Nabu's control, a Gauge with Order gold creeping over your colour)** rises while you wear the helmet (slowly),
with every spell (bigger spells raise it more) and every time you accept one of Nabu's bargains. It falls with the helmet
off. As it climbs, Nabu fights you:

| Dominion | What Nabu does |
|---|---|
| 0-24 | Whispers. Faint golden subtitles ("Order must be kept."). Nothing else. |
| 25-49 | **Override:** 1 in 6 spells comes out as the spell Nabu prefers, a stronger one he picks for the situation (e.g. you cast Ankh Bolt, he fires Golden Chains at the most chaotic target). He won't let you hurt villagers, golems or tamed animals (the spell fizzles). |
| 50-74 | **Pull:** your aim drifts toward the nearest creature of Chaos (Nether mobs, the undead, End mobs, Raven players at Rage). The drift is gentle and you can fight it. Spells cost 10% less, because he's helping. Your voice in chat gets a gold "[Nabu]" tag. |
| 75-99 | **Taking hold:** gold tint at the edges of the screen, your movement keys sometimes lag a moment, and spells cost health on top of Dominion. You heal 2x, because he's protecting his vessel. |
| 100 | **Possession:** Nabu takes your body for 15 s. He flies and casts on his own at the strongest hostile nearby (huge damage), and you can only watch. Mash Space to **struggle**: each press shortens it. Afterward the helmet falls off by itself and can't be put back on for 60 s. |

**Bargains (choices):** Nabu offers deals on a small two-button screen that pauses nothing. You have 10 s to choose, and
ignoring one counts as refusing.
- **Death's door:** at 3 hearts or fewer, "Let me save you." Accept: full heal and a golden shield, **+35 Dominion**.
  Refuse: nothing.
- **Order's command:** sometimes at night, "Banish 5 creatures of Chaos before dawn." Accept: if you succeed, Dominion
  -30 and an Ankh Charm; if you fail, Dominion +40.
- **Forbidden spell:** Shift+Z asks Nabu first, every time. He always says yes, but it costs Dominion to 90+.
- Each bargain you **refuse** makes the next offer meaner (bigger reward, bigger cost) for the rest of that day.

| Key | Move | Spec (helmet on) |
|---|---|---|
| (double Space) | Flight | golden-ankh hover flight, cloak billowing |
| R | Ankh Bolt | golden ankh-shaped bolt, 10 dmg, passes through one creature. +1 Dominion |
| Shift+R | Golden Chains | glowing chains from the ground bind the target 3 s (bosses 1 s), 6 dmg. +3 |
| G | Ankh Shield | hold: giant golden ankh in front of you blocks everything from the front, reflects projectiles. +1/s |
| Shift+G | Banishment | open an ankh gate under the target. Normal mobs are banished (gone, no drops); strong mobs and players are thrown 40 blocks away. Bosses immune. +12, 20 s cd |
| Z | Glyph Storm | 3 s rain of golden hieroglyph blades in a 7-block circle where you look, 4 dmg per hit. +8 |
| Shift+Z | Avatar of Order | asks Nabu, then a 40-block-tall golden projection of Doctor Fate rises behind you for 12 s: its giant hand slams (40 dmg, 8 blocks) on each attack key press and stops all projectiles near you. Dominion to 90+ afterward. 3 min cd |
| **X** | **Ankh Step** | blink through a small golden ankh portal 12 blocks along your look |
| Shift+X | Gate Drop | open a portal under the target and its exit 15 blocks above it: it falls (fall damage) and you blink beside where it lands. +5 |
| C | Order Blast | cone of golden force, 14 dmg, big knockback. +4 |
| Shift+C | Ward of Order | 6-block golden glyph circle for 10 s: hostile projectiles and potions inside fizzle, squadmates in it take 30% less damage. +10 |
| H | Helmet | put the helmet on / take it off (1 s animation; can't take it off during Possession, won't go on for 60 s after one). Not an item: part of the power. Shift+H still opens the power wheel |
| V | Sight of Nabu | 10 s: every creature within 64 blocks glows (Chaos red, Order gold, neutral white) and you see their health. +6 |
| Shift+V | Amulet of Anubis | (v0.17.3, replaced the V helmet toggle) Kent grips the amulet and steadies his mind: Dominion -10, helmet on or off. 2 min cd. **Hold 1 s** = the Tower of Fate mindscape (below) |

### v0.17.3 tester round (effects, moves, mindscape, landing)

Tester: "needs more work on effects and moves. Maybe add a mindscape where you can speak directly to Nabu. Add a landing like
Green Lantern and Kryptonian."

**Effects pass.** Every spell now draws glowing golden geometry on every nearby client (`FateSpellFxPayload` ->
`client.doctorfate.FateSpellFxClient`, built from `FateGlyphs`: additive light-lines, rings, discs, the ankh and nine
hieroglyphs -- the Eye of Horus, djed, water, Ma'at's feather, was sceptre, sun disc, bird, scarab, house) instead of dust lines:
- Ankh Bolt: a beam with a spinning ankh racing down it, rune rings left on its path, an ankh flaring at the end.
- Golden Chains: linked chains rise from four anchors of a binding rune circle and follow the target for the whole bind.
- Ankh Shield: the ankh inside a turning ring of hieroglyphs over a faint disc (was a plain bar outline).
- Banishment / Gate Drop: a real ankh gate on the ground -- rune band, counter-turning rings, a lying ankh, a pillar of light --
  opening and closing (at both ends for Gate Drop and the throw).
- Glyph Storm: a rune circle opens 7 blocks up and rains hieroglyph blades (four a tick, each falling with a streak and flashing
  where it lands) onto a circle on the ground.
- Avatar of Order: rings of light climb as it rises; each slam leaves a huge rune circle, a shock ring and a camera shake.
- Ankh Step: an upright ankh-ring portal irises open and shut at both ends.
- Order Blast: three rings of force race down the cone (glyphs on the first), the edges flash, an ankh at its mouth; a FOV punch.
- Ward of Order: a turning rune circle with an inscribed square, the ankh at its heart and a low golden rim wall, for all 10 s.
- Sight / Amulet: an ankh flare (blue for the Amulet). Flight lift-off: a small flat ankh under him.
- Hit feedback: every landed spell flashes a small ankh on the target with a bright amethyst chime; reflected / fizzled
  projectiles flash too. Sounds layered on most spells (amethyst resonance, respawn-anchor charge, beacon, sonic boom).

**Moves pass** (before -> after):
- Golden Chains damage 6 -> 8.
- Glyph Storm damage per hit 4 -> 5, a hit every 10 -> 8 ticks (6 -> 7 hits), and the blades slow (Slowness I, 1 s).
- Ankh Step cooldown 3 s -> 2 s; looking a little down while standing used to fail outright (every spot along the look was in the
  floor), now it steps along the ground.
- Gate Drop: the creature is hurled down at 1.1 blocks/tick (was a 0.2 drift), so the fall lands in about a second.
- Aim assist for the targeted spells (Chains, Banishment, Gate Drop): a near miss takes the creature nearest the look ray within
  7 degrees, in sight, instead of "no target".

**The Tower of Fate mindscape (new).** Trigger: **hold Shift+V for 1 s** (a tap is still the Amulet of Anubis, now on release;
blue motes gather at the amulet while you hold). Kent's mind goes into the Tower of Fate: a gold-on-black screen -- starfield,
the Tower in golden outline under a great faint ankh, hieroglyph columns drifting, and Nabu's helmet whose eyes burn while he
speaks (`client.doctorfate.FateMindscapeScreen`, server `DoctorFateMindscape`). Nothing pauses (multiplayer-safe, and the body
can still be hit); Possession breaks the vision. Nabu's greeting and his answer about your Dominion change with the tier.
Choices, all checked by the server:

| Choice | Effect |
|---|---|
| How strong is your hold on me? | Nabu tells you your Dominion and what the next tier does. Free. |
| Lend me your strength (Blessing of Order) | Helmet on only: spells deal +30% for 30 s, for +15 Dominion. 3 min. |
| Leave my mind be (Silence) | 60 s with no Override, no Pull, no lagging keys, no whispers, no chat tag -- but every spell's Dominion counts x1.5 ("I will keep count"). Refused from Taking hold (75) up. 5 min. |
| Take my future, not my present (Debt) | Dominion -25 now; 10 minutes later he collects 35 (even with the helmet off, even mid-fight). One debt at a time; nothing to trade below 10. |
| I will settle my debt now | Pay 30 now instead of 35 later. |
| Lore (Lords of Order, the Helmet, Kent Nelson) | Text only. |

Everything agreed is saved in `FateState.Mind` (Blessing / Silence end times, the Debt and when it's due) and shows as chips
in the Tower. Picked because each choice trades control against power in a new direction: the Blessing is power for Dominion,
the Silence is control for a higher spell price, the Debt is control now for more Dominion later.

**Landing.** The shared hero landing (`flight.landing`): `HeroLandingStyle.DOCTOR_FATE` (id 24, fist pose) registered on his
flight; `DoctorFateFx.landing` draws a golden ankh flaring flat on the ground, a rune ring racing out with a low rim wall, gold
shock rings, a deep chime and a gold vignette.

### v0.17.4 (user round): the helmet as an item, powers need it, Dominion retune, flight

User: "melee bonus +8; make the dominion bar fill up a lot slower ... about 2 minutes straight for Nabu to start taking over; fix
the flight animation and make it look better; add an animation for putting on the helmet; regen 1 while wearing it; no armour
while wearing it; H gives the helmet item when removing it, Shift+right-click to put it on, drop it or place it down as an entity,
H summons it from anywhere (another dimension, a chest) without ever making two." Clarified: **powers need the helmet** (no Fate
powers, regen or flight with it off; Dominion drains). This replaces v0.17.3's "not an item" and Kent's weak helmet-off magic.

- **Helmet on:** 40 health, 30% resistance, **+8 attack damage** (an ATTACK_DAMAGE modifier; there was no melee bonus before),
  **Regeneration I** (ambient, refreshed before it runs out), flight, Wither/Poison immunity, every spell. **No armour:** anything in
  the four armour slots goes to the inventory (the ground if full) with an action-bar line, every tick it's worn.
- **Helmet off:** nothing but H. Every move (R/G/X, the Amulet, the Tower) needs the helmet.
- **H wearing it:** the 1.2 s lift-off (hands up, lifted overhead, down to the chest, turns to gold) then the **Helmet of Fate item**
  (`projecthero:helmet_of_fate`, fireproof, never despawns when dropped, not in the creative tab).
- **Shift+right-click the item:** the 1.5 s put-on (formed in both hands at the chest, raised overhead, held high while a golden
  ankh flares behind it, lowered onto the head, settles in a ring of gold; the suit fades in as it comes down). First person: it
  rises into view, is lifted to the top of the screen, comes down over your eyes until the view closes to the eye slit, then opens
  with a gold flash. Only its owner can wear it; an unowned (`/give`n or v0.17.2-world) helmet is claimed by the first Doctor Fate.
- **Right-click a block with it:** set down as a `FateHelmetEntity`: hovers and bobs, turns slowly, lit gold, a flat ankh and rune
  ring glowing under it. Right-click picks it up. Immune to fire, lava, explosions. A Possession's end drops it this way.
- **H not wearing it = summon.** From your own inventory it goes straight on. Otherwise it flies to your raised hand along a rising
  golden arc out of a small ankh portal where it lay (loaded, same dimension), or out of a big ankh portal beside you (another
  dimension, unloaded, unknown); 1 s, then the put-on. During the lockout after a Possession it lands in the inventory instead.
- **No duplicates (Mjolnir's scheme):** every copy carries owner + generation (`fate_helmet` component / entity NBT). The
  `FateHelmetRegistry` SavedData keeps each owner's generation and last-seen place (worn / inventory of player X / item entity /
  placed entity / container at pos). A summon removes the copy where it's loaded (also every online player's inventory, ender chest,
  cursor, and containers/mobs within 2 blocks of the last place), then **bumps the generation**, so any copy it couldn't reach is a
  ghost. `FateHelmetGuard` deletes ghosts wherever they surface: inventory tick, any open menu, entity load (item, placed helmet,
  item frame, mob equipment, minecarts, villagers), container block load, player join. A double H press is refused while a
  sequence runs. Gametests: `DoctorFateHelmetGameTests`.
- **Dominion retune:** every spell's Dominion is a tenth of v0.17.3's (Bolt 0.1, Chains 0.3, Shield 0.1/s, Banishment 1.2, Glyph
  Storm 0.8, Gate Drop 0.5, Order Blast 0.4, Ward 1, Sight 0.6); wearing +0.06/s (was 0.15); off -0.5/s (was 0.75). Casting every
  spell the moment its cooldown allows (no Avatar, no Amulet): about 0.59 Dominion/s from spells + 0.06 worn = 0.65/s, 0.59/s after
  the Pull discount at 50 -> Taking hold (75) in about 1 min 55 s, Possession about 40 s later. Bargains, the Blessing, the Debt and
  the Forbidden spell keep their numbers (they're choices). Taking hold's double health cost is for spells worth 0.8+.
- **Flight look:** Doctor Fate's own `FlightFeel` (slow deep float, gentle banks) and `client.doctorfate.FatePose`: hanging still,
  upright with arms held out low to the sides, palms down, breathing, legs together with one knee bent; flying along the arms ease
  back; at full speed (body laid along the flight) arms swept back along the sides, legs straight together. Every limb is set
  outright, so no walk cycle shows through.

### v0.17.4 round 2 (user): dropped helmet, icon, Nabu's library, Death's door, falls

User: "make the helmet an entity when dropped please, also make the item texture in the inventory look better please, right now it
looks like an egg / when nabu takes control of you there is no time limit you have to convince him to let you go similar to alien X,
player must go to the same mindscape place like with hulk and alien x, the mindscape should be a hallway that ends in a large circular
library with a ton of books and a screen for the player to watch whats happening in the outside world, in this mode the player is
twice as strong, convincing Nabu to let the player go should be difficult and it should feel like the player is trapped in there, also
nabu attack nearby players even if they have commited bad actions in the past, player cant die in this state / when the player dies
and that menu pops up to save them, they can still die before choosing an option, please fix this / doctor fate shouldn't take fall
damage / remove slow fall"

- **Dropped = the hovering helmet.** Any dropped Helmet of Fate item (Q, death, a full inventory, a broken item frame) is turned by
  `FateHelmetGuard` (entity-load hook, one tick later) into a `FateHelmetEntity` with the same owner and generation, carrying the
  item's motion (`FateHelmetGuard.toPlaced`): it flies on with the throw, falls, lands and hovers there. A ghost (old generation) is
  purged instead, never converted. Dropped into the void it rises to the highest ground above (or the spawn).
- **Icon:** `scratchpad/doctorfate/gen_helmet_icon.js` (hand-placed 16x16: gold dome and crest, T-shaped eye slit, flared cheek
  guards, dark outline, top-left light). Previews in `docs/screenshots/v0174/doctor_fate_r2/helmet_icon_*.png`.
- **Possession has no time limit** (`possessedUntil = DoctorFateConfig.POSSESSED_FOREVER`) and Space no longer shortens it. The body
  hits twice as hard (spells x2 as before, plus x2 attack damage), attacks **every player** near it (no grudge needed; still never a
  squadmate, villager, golem or tamed pet) and hostile mobs, players first. **He can't die:** `DoctorFateDamage.allowDeath` holds him
  at 1 health (only /kill gets through) and `DoctorFatePossession.keepOutOfTheVoid` lifts the body back to the last ground. A logout
  or world change doesn't free him (the trial restarts when he's back); death (/kill) and losing the power do.
- **Nabu's library** (client `client.doctorfate.FateLibrary` + `FateLibraryMesh` + `FateTrialScreen`, the Hulk / Alien X mind-hall
  illusion: the world frame from the body's own eyes goes onto a screen and the library is drawn over the window): a 64-block
  sandstone hallway (chiseled pillars, gold braziers; the door behind slams after 1.6 s, the view stretches as you walk it, a dolly
  zoom, and the hall drags at your speed), a gold door into an 18-block-tall round library (bookshelf walls on four tiers, gold ledges,
  lapis ceiling, chandelier light, 12 lecterns in a ring, a gold-framed screen on a plinth on the far wall) that slams shut behind you.
  The Eye of Nabu at the heart: a floating golden ankh and Eye of Horus that burn redder as he angers. Ambience: the deep dark's music,
  page turns, chimes. Nabu talks in gold chat lines (`[Nabu] ...`), with titles at the start, at his wrath and at the release.
- **The trial** (`DoctorFateNabuTrial`, server-authoritative):
  1. *Pages*: 3 of the 12 lecterns hold a page of Kent Nelson's journal (3 of 5 arguments, random lecterns each time); the other 9
     hold Nabu's tomes. Reading a tome the first time: anger +7. Unread books glow gold, found pages blue, tomes ember.
  2. *Challenges*: at the Eye, one challenge per page, 3 shuffled answers, only the page's argument is right. Right: anger -10.
     Wrong: anger +25, that page is torn away to another (unread) lectern and you're back in stage 1 (won challenges stay won).
  3. *Will*: rebuilt as hold-to-rise (see "Will test rebuild" below): progress 200 frees you; 5 s outside the band in a row or 2 s
     on the floor fails: anger +20, one won challenge re-opens (back to stage 2).
  - *Wrath*: anger 100 = a new layout, every page and challenge lost, back to stage 1, anger 30, and the body lashes out at once.
  - Freed: the camera flies into the screen, a gold flash, and the old end of a Possession (helmet falls off as the entity, 60 s
    lockout, Dominion 70). **No Slow Falling** any more.
- **Death's door root cause:** the offer was only a screen with a 10 s timer: every later hit still landed, and a killing blow never
  offered it at all. Now (`DoctorFateDamage`): while it's open no damage lands (only /kill) and he can't die; a blow that would kill
  him opens it at 1 health (if Nabu would offer it then: helmet on, not offered in the last minute). The screen says so.
- **No fall damage** with the helmet on (or going on), while possessed, and in the 60 s lockout after a Possession (the body may be
  high up when Nabu lets go). Without the helmet otherwise, falls hurt as usual (powers need the helmet).
- Tests: `DoctorFateGameTests` (`nabusTrialPagesChallengesAndWill`, `failingTheWillReopensAChallengeAndWrathScattersEverything`,
  `possessedHeCantDieAndHitsTwiceAsHard`, `nabuAttacksAnyPlayerWhateverTheyveDone`, `deathsDoorHoldsDeathOffUntilHeChooses`,
  `noFallDamageWithTheHelmet`, the Possession test updated), `DoctorFateHelmetGameTests` (`summonFromADroppedItem` updated,
  `aDroppedHelmetLandsAsTheEntity`). Lang: `scratchpad/doctorfate/lang_fate_r2.json` via `merge_lang_r2.js`.

#### Will test rebuild (v0.17.4, user: "the doctor fate space bar minigame to leave doesn't work very well, please improve it")

The old test was tap-mashing (each press +0.075, presses within 2 ticks dropped, raw 2-tick sync, 3 s out = fail). Now it's a
fishing-minigame **hold to rise**:
- **Physics** (`DoctorFateWill`, shared by server and client): per tick `v = (v + (held ? 0.0052 : 0) - sink) * 0.9; will += v`,
  `sink = 0.0024 + 0.00001 x anger`. Top speed held ~0.025/tick (whole Gauge in 2 s); falling ~0.022/tick calm, ~0.03 at 90 anger.
  Floor and ceiling bounce at 30% of the speed.
- **Input**: the client reads Space from the raw GLFW key state (`FateLibrary.held`, since mapped keys are released every tick in
  the library) and sends `STRUGGLE` with `arg` 1/0 only when it changes. The server integrates every tick (authoritative).
- **Sync and prediction**: a small `FateTrialPayload.WILL` packet every tick (will, velocity, band, half-width, progress, out/floor
  ticks, lunge telegraph). The client steps the same physics from its own Space every tick and eases 30% toward the server's sample
  (jumps if more than 0.2 off); band/half-width/progress are smoothed; everything is drawn interpolated by the partial tick.
- **Band**: eased (smoothstep) drifts between random heights in 0.22-0.80, 50-90 ticks each (35% faster at full anger); half-width
  0.15 calm to 0.10 at full anger. **Lunge**: first after 5-11 s, then every 8-14 s (40% sooner at full anger): the band stops and
  flashes and the Eye flares for 0.5 s (bell), then it jumps at least 0.25 to the roomier side in 5 ticks.
- **Progress**: +1/tick in the band, -0.5/tick outside (never reset), 200 needed. Fail: 100 ticks out in a row, or 40 ticks with the
  will at or under 0.02.
- **Feedback**: gold band, marker white with a glow inside / red outside, a progress frame filling both rails of the Gauge (closing
  the top at 100%) and a percentage, a looping beacon hum rising in pitch with progress (loud inside, faint outside), a heartbeat
  that speeds up plus a red screen edge and a shaking Gauge when slipping, Nabu's lines at 50% and 90%. Hint: "Hold Space to lift
  your will, let go to sink. Stay in the gold band."
- Tuning check (`scratchpad/doctorfate/will_sim.js`): a player reacting 200 ms late who anticipates a little frees himself in
  ~11-13 s at any anger; one who reacts 400 ms late without anticipating mostly fails at high anger.
- Tests: `holdingSpaceRaisesTheWillLettingGoSinksIt`, `willProgressDrainsAtHalfRateOutsideTheBand`,
  `willFailsOnlyAfterFiveSecondsOutside`, `aSteadyHandOnSpaceWinsTheWill`, plus the two trial tests updated. Real-client harness
  `scratchpad/doctorfate/DoctorFateWillHarness.v0174r2b.java.txt` presses Space via Win32 `PostMessage` (through GLFW's key state);
  films in `docs/screenshots/v0174/doctor_fate_r2/will_*`.

**Fair-play rules:** Possession never attacks squadmates. On a server you can't be possessed while inside spawn protection.
Dominion is saved per player.

**Possible boss later:** Klarion the Witch Boy, a Lord of Chaos raid. Helmet wearers get a bonus against him and Nabu stays
quiet during the fight.

---

## Shazam -- Billy Batson

**The idea:** type **Shazam** in chat and magic lightning strikes you. You become the champion: S.H.A.Z.A.M. (Wisdom of
Solomon, Strength of Hercules, Stamina of Atlas, Power of Zeus, Courage of Achilles, Speed of Mercury). Type it again to
turn back. Like in the comics and films, the transform lightning is itself a weapon.

**Saying the word**
- A chat message that is just the word ("shazam", "SHAZAM!", "Shazam.", any case) triggers it. That message is not posted
  to chat. Everyone nearby sees and hears the lightning instead, and the subtitle says "SHAZAM!".
- **The lightning hurts:** each transform bolt deals 12 to creatures within 3 blocks of you (never you or squadmates). In the
  films Billy "turns back and forth" to call lightning, so that's a real trick, but there's a 4 s gap between words.
- **You need your voice:** it doesn't work underwater with no air bubble, while muffled by Black Adam (later boss), or inside
  a powder snow block. You get the message "You can't say it!".
- Saying it while transformed turns you back to Billy (no damage to you) and the lightning still hits nearby enemies.

**Getting it:** during a thunderstorm, a very rare **Wizard Shazam** NPC appears near villages (player model, detailed skin,
long beard and robes). Right-click him: he judges if you're **pure of heart** using the existing worthiness check (no recent
villager or iron golem kills, good village reputation). If worthy he says your name and grants the power; otherwise he fades
in a lightning flash and won't return for 5 days. Random serums never give it.

**Look:** human form is your normal skin. Transformed you get the Shazam suit as a layer over your skin: red, a gold
lightning-bolt chest, gold sash and cuffs, and a short white cape with gold trim. You also get 0.1 blocks taller.

**Suit skin (2026-11-12, the user's pick):** the suit is the PlanetMinecraft skin "Shazam (Justice League)" by outofspace2
(https://www.planetminecraft.com/skin/shazam-justice-league/, wide model), kept untouched in
`docs/reference/shazam/` with `SOURCE.txt`. **No licence is stated: check with / credit the author before a public release.**
`scratchpad/shazam/make_shazam_suit.js` builds the suit from it: head, hair, neck and hands made transparent (your own head and
skin show), the painted cape taken off (it's a cape model on `FlowingCapeLayer` now, restyled to match, hanging to the belt),
a slim-arm version, and a Family version with gold piping.

**Passives (transformed only)**
- **Stamina of Atlas:** 50 health, 35% damage resistance, no hunger drain.
- **Strength of Hercules:** +10 melee.
- **Speed of Mercury:** +40% speed.
- **Courage of Achilles:** immune to fear effects (Warden darkness, T-Rex roar, Raven's fear).
- **Weakness:** magic damage (potions, evokers, witches, Doctor Fate, Raven) hurts you 50% more. In the comics magic is the
  champion's weakness.
- **Lightning bar (a Minecraft boss bar on the VanillaHud.Stack column since v0.17.4; was a Slab):** fills 4 a second while
  transformed (v0.17.4, `ShazamConfig.LIGHTNING_REGEN_PER_TICK`), +3 a second during thunderstorms, refilled to full by every
  transform bolt, and topped up when lightning strikes near you.

| Key | Move | Spec |
|---|---|---|
| (chat) | **SHAZAM!** | transform or turn back, lightning strike, 12 dmg within 3 blocks, refills the Lightning bar. 4 s gap |
| (double Space) | Flight | fast flight, cape streaming |
| R | Lightning Hand | lightning bolt from the hand, 9 dmg, chains to 2 nearby creatures. 8 |
| Shift+R | Call the Lightning | real lightning strike where you look (64 blocks), 22 dmg, sets fire only if mob griefing is on. 30 |
| G | Hercules Punch | 16 dmg, launches the target 15 blocks; it takes extra damage if it hits a wall. 10 |
| Shift+G | Atlas Lift | grab a creature (or lift a big mob you couldn't normally move) and hurl it where you look. 15 |
| Z | Zeus Storm | 5 s: lightning hits a random hostile within 16 blocks every half second, 10 dmg each. 50, 15 s cd |
| Shift+Z | Eternity Strike | rise up, the sky darkens, a huge bolt hits you, then you slam down: 50 dmg within 10 blocks, Lightning bar emptied. 100, 3 min cd |
| **X** | **Mercury Dash** | red blur 12 blocks along your look, light crackle trail |
| Shift+X | Thunder Tackle | fly-tackle up to 20 blocks, carrying the first creature into the ground for 18 dmg with a lightning burst. 20 |
| C | Thunderclap | clap shockwave cone, 12 dmg, knockback, breaks glass only if mob griefing is on. 15 |
| Shift+C | Achilles Stand | 4 s: you take no damage, every hostile within 16 blocks targets you (threat table), and squadmates take 30% less. 30, 30 s cd |
| V | Wisdom of Solomon | 10 s: every creature's health, armour and weakness shows above it, and the best tool or weapon against the block or mob you look at is hinted |
| Shift+V | **Share the Power** | aim at a squadmate (transformed): they join the **Shazam Family** for 10 minutes, so typing "Shazam" transforms *them* too (half your health and damage, same keys except Shift+Z, gold suit trim). Up to 5 family members. 10 min cd |

### v0.17.3 tester round (effects, landing)

Tester: "looks great, but needs a little work effect-wise. Add a landing like Green Lantern and Kryptonian."

**Effects pass.** His lightning is now real branching bolt geometry on every nearby client (`ShazamFxPayload` ->
`client.shazam.ShazamBoltFx`: midpoint-displaced bolts with forks, a white-hot core in a gold halo, re-shaped every two ticks so
they flicker) instead of vanilla `LightningBolt` entities and spark lines. `Shazam.bolt` no longer spawns an entity at all (so
it can't burn or refill his own bar); it draws the bolt, a ground flash, a scorch, sparks, a sky flash for anyone near, and
layered thunder.
- Lightning Hand: a forked arc to the target and each chain hop, with a crack.
- Call the Lightning / Zeus Storm: a thick forked bolt out of the sky, a ground flash with ground-hugging forks, a shock ring.
- The transform strike and Eternity Strike: a huge double-forked bolt with rolling thunder; the transform also sends bolts racing
  out along the ground; Eternity rises wrapped in a crackling gold aura and the slam cracks the ground with light, with a huge
  shock ring and a heavy shake.
- ~~Charged: arcs round both hands and glowing eyes~~ -- removed in v0.17.4 (user: "remove the eye glow and the lightning
  coming from the hands").
- Mercury Dash: a red blur -- three streaks at shin, waist and shoulder height fading from the tail, red after-images along the
  path, crackle, and a whoosh.
- Thunder Tackle: bolts burst out along the ground where he slams the target down, with a shake.
- Thunderclap: three jagged rings of force race down the cone, sparking at the rim, a FOV punch and a thunder crack.
- Achilles Stand: a golden aura for the whole 4 s -- rings climbing his body, a soft shell, arcs crawling over him -- and a shock ring.
- Fix found while filming: Mercury Dash (and the free Thunder Tackle dash) looking a little down while standing ran into the floor
  and did nothing; now it dashes along the ground.

**Landing.** `HeroLandingStyle.SHAZAM` (id 25, fist pose) registered on his flight; `ShazamFx.landing` is a lightning-crack ground
slam: glowing cracks race out across the ground (white-hot, cooling to gold), sparks jump off them, a bolt answers from the sky on
a hard landing, a scorch, a shock ring and a thunder crack.

**Possible boss later:** **Black Adam**. He says "Shazam" too: when he's below half health, typing "Shazam" near him strikes
*him* with lightning and turns him back into a mortal for 5 s. That's the classic trick.

### v0.17.7: how players get Shazam -- the journey (`com.projecthero.mod.shazam.journey`)

User: "change the way he gets his powers to make it so that once a player becomes worthy they can randomly see illusions of
the train stairway, approaching it transports them to the subway and then they can hop onto the train, the train then takes
them to the cave with the wizard shazam and then approaching him he will tell you to say his name and then the player
transforms for the first time, i want there to be cutscenes for this stuff too". The storm-visit Wizard is gone
(`ShazamWizards.tickLevel` is no longer registered); his judgement stays for operators (`/projecthero power tune shazam
wizard`, the creative `wizard_shazam_spawn_egg`). Client in `client/shazam/journey` (+ `ThroneWizardModel/Renderer`,
`ShazamJourneyCameraMixin`, `ShazamJourneyGuiMixin`). Lang: `scratchpad/v0177/shazam_journey_lang.json` (`lang_add.js`, key by
key). Film: `docs/screenshots/v0177/shazam_journey/shazam_journey_full.mp4` (harness `scratchpad/ShazamJourneyHarness.v0177.java.txt`,
encoder `scratchpad/v0177/shazam_journey_film.js`). Illusion v2: `docs/screenshots/v0177/shazam_journey/illusion_v2/`
(before/after stills by day and night, `illusion_v2_film.mp4` + sheets, `hands_*` stills; harness flag line 2 `both`, line 3
the still prefix; encoder `... <frames> illusion_v2 <before stills dir>`).

**Worthy** (`ShazamIllusions.worthy`): full Valor (`thoracquire.Valor`) and no power at all (`HeroTiers.hasAnyPower`, not
Shazam, not Shazam Family). Being chosen spends no Valor; once Shazam they are powered, so Thor is out (and vice versa).

**The illusions** (`ShazamIllusions`): every 60 s (`ROLL_EVERY`) a worthy player on foot in the Overworld rolls 4%, 8% at
night, +4% in a City chunk (`chanceOverride` for tests). A hit places a stairway 7-12 blocks ahead within 34 degrees of their
look, on open ground with a clear 3 x 5 footprint and a clear line of sight, descending away from them; then a 5-minute
cooldown (`COOLDOWN`). It's only sent to that player (`JourneyPayloads.Illusion`) and drawn client-side (`IllusionRenderer`).

*Illusion v2* (user: "it look like a green lantern construct, also the abrupt change into the grey colour from the green and
then the darkness, make it look better"): a real, solid, old city subway entrance, only subtly otherworldly. Everything is
textured geometry at full opacity, lit by the world (the spot's sky light plus the lamps' own warm block light), so it sits in
daylight and glows at night:
- a stone-brick kerb with smooth-stone coping round three sides and a flush stone threshold at the entrance (grass meets stone);
- black-green wrought-iron railings on the kerb: balusters with spear finials, three posts a side with ball finials;
- two cast-iron lamp posts at the entrance (foot, plinth, shaft, collar) with warm full-bright globes and a soft halo (stronger at night);
- a crossbar between them with a dark-green enamel sign, cream border and cream "SUBWAY", and a small cream "Eternity Line"
  plate hung under it on two chains;
- below ground, through the depth "hole" (`JourneyRenderTypes.HOLE`): eight worn steps (smooth-stone treads with a darker worn
  path, slab-side risers), white subway tiles with a green tile band, a landing, a polished-deepslate door frame and a 3.6-block
  corridor. Colour and lightmap both fall off per vertex with depth (daylight, shadowed tiles, dark) and into the corridor to
  pure black, so there's no hard black rectangle; a faint warm glimmer breathes at the far end (the station far below).
- otherworldly touches (`JourneyRenderTypes.GLOW`, additive): a faint gold shimmer rippling up off the kerb's outer edges, and
  fourteen slow golden motes drifting up out of the stairwell. No arch, no vanilla particles.
- **Materialises** over ~1.6 s (`IllusionRenderer.materialise`): the gold shimmer first, then the stone and iron ease in with
  a few flickers; once there it holds steady (no more wavering). **Fades out** over 1.5 s: the stone thins away as the shimmer
  swells briefly and goes. It still whispers (soul-escape sounds, one faint horn).

It fades after 30 s or 32 blocks away. Walking within 2 blocks of its top step starts the journey. The player's position when
it appeared is the return point (`ShazamJourney.RETURN`, saved).

**The realm** (`EternityRealm`): `projecthero:rock_of_eternity`, a void datapack dimension (no sky light, ceiling, fixed
night, ash, cave mood), one build per journey slot 1000 blocks apart along Z, built once in 3-column slices over a few
seconds (marker: bedrock 8 under the stop point) and reused. Layout along +X from the stop point O (`JourneyGeometry`):
- **Fawcett Central** (x -24..24): brown wainscot, grimy white quartz tiles, a cyan mosaic band, oxidised copper columns, a
  yellow platform edge, ceiling redstone lamps (two flicker while you're on the platform, `flicker`), benches, the timetable
  ("Next train arriving now / Last train never"), FAWCETT CENTRAL across the track, MIND THE GAP, and the stairs you "came
  down" at the east end (you land at their foot).
- the west tunnel (to -105) the train comes from; the east tunnel: tiled subway tunnel with a froglight every 5 blocks on
  both walls (the streaks) to x 125, then glyph-carved blackstone and crying obsidian with glowstone runes, turning to raw
  rock by 175, a widening rough rock bore to the cave at 256.
- **the Rock of Eternity** (an 80 x 44 roughened dome, 28 high, centred on x 296): paved floor, the end of the line with a
  buffer stop, a red carpet to a three-step gold-edged dais and a blackstone throne crowned in gold, the Seven Deadly Sins
  (Pride, Envy, Greed, Wrath, Lust, Gluttony, Sloth) on plinths with glowing eyes, braziers and name signs, a ring of
  glowing runes round the walls, a great lightning-bolt rune behind the throne, and lightning arcing under the dome
  (`ShazamFx.arc` every 1.5-4.5 s with quiet thunder).
- Fallback: with no dimension (the GameTest server) the same build goes into the overworld sky at x 1,200,000, y 200.
- Nothing in it can be broken, placed or used; no damage to anyone in a journey or in the realm; falling below the build puts
  you back on the floor. The route is held loaded and entity-ticking (`hold`, a ticket every two chunks).

**The stages** (`ShazamJourney`, server-authoritative; scenes and beats in `JourneyScene`):
1. DESCENT (74 ticks): held at the stairs. v2 timing (one continuous, eased move, no hard cuts): 0-26 the camera rises
   from your eyes to behind the lamps, looking down the stairs; 26-62 it glides forward and down over the opening with a
   faint step-bob, looking ever deeper into the doorway. The light dims gradually from 20 (a smoothstep curve) to full black
   at 58 (`DESCENT_BLACK`); the illusion's shimmer settles as it does. Sounds: soul whisper at 0, a gust at 8, a faint
   distant horn at 22, then in the beat of darkness the second horn and the rumble at 55 (`DESCENT_HORN_DARK`). At 62
   (once the station stands) you're taken to the platform; the "Loading terrain" screen is drawn black while a journey
   scene plays (`ShazamJourneyLoadingMixin`, white for the trip home), so there's no grey flash.
2. TRAIN (152): the dark holds 8 more ticks, then eases up into the platform over 30 (smoothstep); the camera watches the tunnel as the headlights come, the horn at the
   tunnel mouth, the wind as it bursts out, screeching brakes, the air brakes sigh, the doors open. The train only spawns
   once its tunnel chunk is entity-ticking (the scene waits for it).
3. WAITING: "The doors are open. Step aboard the train." Walk into any of the six doorways, or right-click the train.
4. RIDE (262): seated in the front car (re-seated if you try to get off); the door chime and doors closing, away at 20,
   full speed (30 blocks/s) by 60, braking from 200, stopped in the cave at 250. Shots: inside the car looking back at you,
   first person out of the far windows as the lamps streak by, glyphs out of the window (gold glyphs drift over the screen,
   enchanting sounds), a white lightning flash at 150 (thunder, shake, tiles crumbling off the walls), a camera in the rock
   tunnel as the train roars past it, then the cave as it brakes in ("The Rock of Eternity" caption).
5. CAVE: you step off beside the front car facing the throne; walking within 7 blocks of the Wizard (or right-clicking him).
6. MEETING (206): you're placed before the dais; he rises from the throne (40 ticks, stepping forward) and speaks:
   "I have waited a long time for a champion..." / "<name>. You are worthy. Your heart is pure." / "Say my name, so that my
   power may flow through you!" Wide shot, close on his face, over his shoulder.
7. PROMPT: `SayShazamScreen` -- a big gold SHAZAM! button (Enter / Space too), a wrapped hint that typing Shazam in chat works
   too. Esc leaves a HUD hint; right-clicking the Wizard reopens it. The word in chat is swallowed like the real word.
8. TRANSFORM (150): "SHAZAM!" fills the screen; at 14 the power (`Shazam.grant`: the one Primary) and the first
   transformation (`Shazam.speak`: the colossal bolt, the suit, "SHAZAM!"), two more bolts and a burst, a white flash and big
   shake; an orbit round the new champion; his last words ("It is done. My power is yours, champion." / "Speak my name and the
   lightning will answer. Speak it again to return. Now go."); white at 118, home at 134.
9. HOME: back at the return point, turned back (`Shazam.forceUntransform`), a flash and sparks there, the white fades over 2 s,
   and a line telling them to type Shazam.

**Safety:** a journey is a static map of player UUIDs (`ServerStateReset` -> `ShazamJourney.clearSessionState`, which also
clears the illusions and pending builds). Disconnect: the journey ends (train and Wizard go), the saved return point stays;
`check` (every 10 ticks, any player with no journey) sends anyone with a return point home and anyone in the realm without one
to world spawn. Death ends it and clears the return point on respawn. Taken out of the realm some other way (a command): the
journey ends quietly. Client: every scene times out by itself (the descent's black at most 10 s past its end, the prompt
without a server refresh every 5 s), movement keys are held during scenes, everything clears on disconnect. No first-person
hands or held items during any journey scene (user: "during the cutscenes I can see my hands"): the shared
`client/cutscene/CutsceneHands` registry + `CutsceneHandsMixin` (HEAD cancel of `GameRenderer#renderItemInHand`); also
registered by the Mjolnir lift, the Galactus cinematics and the Omnitrix show sequences.

**Wizard on the throne** (`ThroneWizardEntity`, id `wizard_shazam_throne`): the same player-model Wizard in the project's own
detailed skin (`textures/entity/shazam/wizard_shazam.png` + parts, from `scratchpad/shazam/gen_wizard_textures.js`), seated
and rising (`ThroneWizardModel`); never fades (`WizardShazamEntity.fades()`), no physics, not saved.

**The train** (`SubwayTrainEntity`, id `subway_train`, renderer `SubwayTrainRenderer`, atlas from
`scratchpad/v0177/gen_shazam_journey_assets.js`): two 16-block stainless cars with a red Shazam stripe, tinted windows, three
pairs of sliding doors a side, roof boxes, skirt and bogies, a bellows gangway, headlights with beams, red tail markers, an
amber "ROCK OF ETERNITY" destination sign, and a lit interior (benches, poles, grab rails, adverts, light strips). Its
position is scripted from synced mode / start / origin on both sides (`lerpTo` ignored), so a passenger rides smoothly.

**Tests:** `ShazamJourneyV0177GameTests` (worthiness, the forced illusion, walking to it -> the platform, boarding -> the
cave, the chat answer -> power + transform -> home turned back, the button answer, logout safety).

---

## Build split
Two parallel worktree agents (FATE, SHAZAM), alongside the four Titans agents. Lang text in `scratchpad/<feature>/lang_*.json`.
