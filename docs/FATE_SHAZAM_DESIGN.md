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

---

## Build split
Two parallel worktree agents (FATE, SHAZAM), alongside the four Titans agents. Lang text in `scratchpad/<feature>/lang_*.json`.
