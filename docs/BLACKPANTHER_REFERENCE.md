# Black Panther reference

Hero-Tier Primary power, **Street tier** (melee 7-10, abilities 8-10, ultimate 15-20, `MAX_HIT` 20). Package
`com.projecthero.mod.blackpanther`, client `client/blackpanther`. Numbers live in `BlackPantherConfig`; state in the
`projecthero:black_panther_state` attachment (`BlackPantherState`, synced, copy-on-death). Tests: `BlackPantherGameTests`,
`BlackPantherV0177GameTests`.

## Getting it

Heart-Shaped Herbs grow in jungles. 3 herbs + a bowl = Heart-Shaped Herb Soup. Drinking it runs the 10 s Ancestral Plane, then
grants the power. It replaces the Primary power. The suit is crafted separately (the necklace below).

## The suit: the Black Panther Necklace (v0.17.7)

The user, first: "make the suit craftable instead please like the flash suit and you need to equip it, when the suit is worn you
get the new moveset" (built as four armour pieces), then the same day: "Make the suit just a necklace, player crafts the necklace,
equips it and presses H to make it transform into the full suit" and "Make the necklace model look like the necklace on the suit
please, don't make the player have the necklace model on them all the time, only when theyre wearing the necklace and suit".

- **Item:** `BlackPantherItems.NECKLACE` (`projecthero:black_panther_necklace`, `BlackPantherNecklaceItem`): stack of 1, EPIC,
  fire resistant, no durability. `Equipable` with slot **CHEST**, exactly like the Flash Ring holding the Flash Suit
  (`FlashRingItem`): right-click it (only the Black Panther may, others get a refusal), shift-click it or drag it into the
  chestplate slot. Death / keepInventory / graves treat it like armour. **Black Panthers only** (v0.17.7, user: "black panther necklace can only be worn by players with black panther ability"): `LivingEntitySupermanSuitMixin` returns MAINHAND as its equipment slot for anyone without the power (armour slot, shift-click, hotbar swap, creative screen refuse it, both sides), `use` refuses right-click, `BlackPantherNecklaceItem.DISPENSE` fits it only onto a Panther with the chest slot free (else shoots it out), and `BlackPanther.ejectNecklace` (every tick, every player, and from `revoke`) pops a worn one off into the inventory (dropped if full) with the refusal message. A chestplate cannot be worn with it (the suit gives its own
  armour).
- **Recipe** (shaped, `recipe/black_panther_necklace.json`): `VAV` / `VWV` / `VHV`. V = Vibranium Ingot (6, the old set's cost),
  A = Amethyst Shard, W = Black Wool, H = Heart-Shaped Herb. JEI shows it plus an info page (`projecthero.jei.black_panther_necklace`
  / `2`).
- **H** (`BlackPantherSuitPayload` -> `BlackPanther.toggleSuit`, server re-validates, `PowerSuppression` respected): suited -> the
  suit drains back (`suitDown`, `ANIM_SUIT_DOWN`); not suited + necklace worn -> `suitUp` (the texel build out of the necklace, head
  last, `SUIT_UP_TICKS`); a necklace only in the inventory is put on first (swapped with whatever was in the chest slot, the Flash
  Ring's `equipFromInventory`); no necklace -> "Wear the Black Panther Necklace to suit up". One change at a time
  (`transitioning`). Client: `BlackPantherClient.ownsH` = has the power AND (suited OR necklace worn OR necklace in inventory),
  checked in `ProjectHeroModClient.handlePowerSelect` right after the Flash Ring and listed in `primaryOwnsPlainH`. Plain H only;
  Shift+H always opens the wheel; with no necklace in play H falls through to the power selector as before.
- **Unequip:** `BlackPanther.syncSuit` (every tick, join, respawn) runs `suitDown` when suited without the necklace worn (or as a
  spectator). Nothing ever suits him up on its own.
- **Stats while suited:** `reconcile` adds `black_panther_armor` +20 armour and `black_panther_toughness` +8 toughness
  (`SUIT_ARMOR` / `SUIT_TOUGHNESS`, full diamond; user: "make the black panther armour diamond level armour", was 12 / 4) and
  Regeneration II; both modifiers are removed when the suit goes.
- **Necklace model** (`BlackPantherRender.renderNecklace`, drawn by `BlackPantherLayer` while `BlackPanther.showsNecklace` =
  necklace worn in the chest slot AND not spectator -- suited or not, all through the build / retract, drawn before the layer's
  has-power check so any wearer shows it; user: "make the black panther necklace model visible when not transformed, should be
  visible when the necklace is equipped"). On the bare skin it clears the vanilla jacket (2.25 front / back, 4.25 sides) with its
  inside faces at 2.36 / 4.36; its side runs sit inside the arm cubes (inner faces at x 4, wide and slim; a slim sleeve's top is at
  y 0.25, so a sliver of the top tier shows above a slim shoulder), and the chest slot never holds a chestplate with it. The gold necklace painted on the comics skin, in 3D (rebuilt for the comics skin, user:
  "Make the necklace model look like the necklace on the suit please"). On `black_panther.png` body row 20 is a gold band all the way
  round (chest texels 20 / 22-25 / 27 gold, near-black gaps at 21 and 26; sides solid; back mirrored), with claw-teeth at 21, 23, 24, 26
  on row 21 and a centre point at 23-24 on row 22; the jacket layer raises gold over rows 0-2 of the chest; the arms carry no gold. The
  model, all in the body's space: a band at y 0.08-1.18 of gold links (bright top bevel, mid face, darker lower lip, each tier a step
  further in), links at x -4.68..-3.04 / 3.04..4.68 (wrapping round the corners to a solid side run) and a bolder centre plate at
  -1.96..1.96, dark cord set back in the gaps at x = +-2.5, chest and back alike; on the chest four teeth (x +-2.5 hanging from the gap
  cords, x +-1.2 under the plate) and the long centre fang at 0 down to y 3.4, each root / middle / bright tip curling forward. Inside
  faces at 2.36 (chest / back) and 4.36 (sides), outside up to 2.88: never coplanar with the skin, the jacket (2.29) or the sleeves;
  clear of the high collar (its yoke is at y -0.73, its wall 6.35 px round the neck and open at the front, its back skirt 3.7 px+ behind).
  Texture `necklace.png` (64x64 flat gold swatches: #E7C76E, #D19C4C, #C18039, #11101C gap, #EACC71 tips, #DAAD59 plate) and the gold
  item icon (band, plate, four teeth and the fang, no gem) come from `scratchpad/bp_v0177_necklace_textures.js`. With Kinetic stored the
  gold takes the trims' violet glow (`necklace_glow.png`, same swatches in violet, alpha = the Kinetic bar). The skin is unchanged (its painted band stays under the model).
- **Migration** (`BlackPanther.migrate`, flag `v177`): a Black Panther who had the suit before v0.17.7 (habit bound in
  0.17.4-0.17.6, or any 0.17.3 power) gets ONE necklace: worn (and still suited) if he was suited and the chest slot is free,
  otherwise into the inventory (dropped if full). Old `habit`, `necklaceOff`, `clawsOut` flags are cleared.
  `BlackPanther.convertLegacyItems` (run by the legacy items' own `inventoryTick`, on join, and when a habit is used) turns the
  four armour pieces of the first v0.17.7 build into necklaces, one per full set of four (at least one for any pieces) -- a worn
  legacy chestplate becomes a worn necklace, so a player suited in the pieces stays suited -- and each Panther Habit into one
  necklace. The legacy items (`black_panther_helmet/_chestplate/_leggings/_boots`, `panther_habit`) stay registered so old stacks
  load (`PantherLegacySuitItem`, plain items, necklace icon), have no recipe, are not in the creative tab and are hidden from JEI.
- Removed: the four armour pieces as armour (material, armour-layer textures, item icons, recipes, JEI page), the always-on
  necklace model. The hold-H necklace toggle and the N claw toggle stay removed.

## Suit look: comics skin, cape and high collar (v0.17.7)

The user: "make this the new black panther suit: https://www.planetminecraft.com/skin/black-panther-marvel-comics-6828688/ Remove the
cape and and high collar from the skin and make it a model that reacts with the players movement, make it look cool".

- **Skin**: "Black Panther [Marvel Comics]" (PlanetMinecraft file 19479117, Steve / wide arms; borrowed, ships publicly). Original kept
  untouched at `scratchpad/v0177/bp_skin_original.png`; `node scratchpad/v0177/make_bp_suit_skin.js` writes `black_panther.png`,
  `black_panther_glow.png`, `black_panther_cape.png`, `black_panther_collar.png` (all in `textures/entity/black_panther/`) and the
  previews in `docs/screenshots/v0177/bp_suit/`. One wide texture for every wearer (the suit model is always wide).
- Black suit with gold trims: gold eye lenses, a gold neck band with four hanging claw-teeth and a centre point on the upper chest
  (rows 20-22 of the body front, plus the front half of the shoulders' top), a gold V belt at the waist, gold clawed bracers on both
  forearms. Cat-ear tips on the mask's top corners.
- **Removed from the paint**: the purple-grey cape (body back, back of the shoulders, body sides' back columns, the legs' backs, the
  cape fold highlights on the second layer) and the high collar (the light texels round the back / sides of the head's lower five rows,
  the underside ring, the hat layer's copies).
- **Filled**: the back is the front seen from behind without the hanging teeth, so the gold neck band and the V belt run all the way
  round; sides and leg backs mirror their own fronts (boots included); a vibranium spine + shoulder-blade seam (a Y) on the back; the
  bracers' gap on the inner back of each forearm closed; the mask's sides / back repainted as a dark hood. Every base texel is opaque.
- **Glow map** (Kinetic bar): the gold trims and the back seams, violet.
- **Cape** (`BlackPantherCapeLayer`, on the shared cloth `FlowingCapeLayer`): full length (1.25 blocks, to the shins, as painted),
  purple-grey with folds, a dark-gold hem, violet-black lining. Swings with walking / sprinting / crouching like the Thor and Shazam
  capes; streams up behind him while falling faster than 0.45 blocks/tick (or elytra gliding). Unrolls from the collar over the last
  45% of the texel build, rolls up first on the way back.
- **High collar** (`BlackPantherCollarLayer`): a curved standing wall round the back and sides of the neck (110 deg either side of
  straight behind, open at the front), 5 px tall behind the head down to 2 px at the front ends, flaring out 14 deg, on a yoke over the
  shoulders, with a short skirt behind that runs down over the cape's top. Radius 6.35 px so its inside clears the suit head's corners
  however the head turns. Body frame (torso turn, crouch); turns 30% with the head (max 20 deg), leans back with 60% of an upward look
  (max 35 deg), flares with the cape's lagging swing (+ side sway twist) and opens 10 deg more in a fast drop. Rises over build share
  0.45-0.75, sinks first on the way back. Purple-grey ribs, lining inside, gold top edge.
- Both layers draw only while `BlackPantherRender.anim(player).anything()` (the suit is out or building), never in first person.

## Passives

| Passive | Unsuited | Suited |
|---|---|---|
| Melee (bare fist) | 5 | 10 (claws always out) |
| Regeneration | I | II |
| Resistance (v0.17.7) | I | I |
| Herb | Speed I; jump 2 blocks (jump strength +0.19, peak 2.44) | Speed I; jump 3 blocks (+0.32, peak 3.46) |
| Falls | half (Cat's Landing) | none |
| Night Eyes | in the dark | always |
| Hunter's Instinct | creatures hunting him within 20 outlined | same |
| Kinetic Absorption | -- | 40% of a hit soaked, 1 damage = 4 Kinetic |
| Vibranium Weave | -- | 20 armour / 8 toughness, full diamond (attribute modifiers while suited); shots under 15 bounce off |
| Silent Paws | -- | no vibrations |

The melee is one attribute modifier (`black_panther_herb`): `UNSUITED_MELEE - 1` or `SUITED_MELEE - 1`. The jump is a `JUMP_STRENGTH` modifier (`black_panther_jump`, `JUMP_UNSUITED` / `JUMP_SUITED`; the old endless Jump Boost is cleared). Regeneration and
Resistance I (`RESISTANCE_LEVEL`, user: "give player passive resistance one") are infinite `PowerToggles.effect`s refreshed in
`reconcile` (every 20 ticks), cleared by `revoke`.

## Unsuited kit (T'Challa)

| Key | Move | Damage / effect | Cooldown |
|---|---|---|---|
| R | Warrior's Combo | 5 / 7 / 10 (jab on the press, kick shoves) | 1.5 s |
| Shift+R | Leg Sweep | 7 within 2.6, trip 1.5 s | 6 s |
| G | Takedown | grab within 3 blocks, slam 12 (v0.17.7 user), daze 2 s | 8 s |
| Shift+G | Ground Pound (new) | hop, dive, slam: 15 (v0.17.7 user) within 3.5, knock-up, Slowness II 1.5 s | 8 s |
| X | Vault | front flip, no fall damage | 2.5 s |
| Shift+X | Flying Kick (new) | dash ~8 blocks along the full look (aim up = kick upward, aim down = dive; a downward aim from the ground kicks level), 10 to the first creature reached, rebound; a wall / ceiling / the ground one step ahead stops it dead, soft landing (`FLIPS` 60 ticks, refreshed when it ends); the body tilts with the look pitch (`BlackPantherPose.flipDegrees`) | 6 s |
| Z | Throwing Knife | 8, returns (renderer: sprite plane along the flight, tumbling forward end over end, shared with the suited dagger) | 4 s |
| Shift+Z | Kimoyo Scan | mark 10 s, +25% from him | 15 s |
| V | Rising Uppercut (new) | 9 within 3, launched + Slowness III 1.5 s | 6 s |
| Shift+V | Hunter's Tracking | toggle, outlines + footprints, 24 | -- |
| C (hold) | Block (new) | every hit from the front -60%, no time limit (as long as C is held) | 6 s, from release |
| Shift+C | Wakanda Forever (was Ancestral Resolve; id still `ancestral_resolve`) | Speed II, Strength II, Regeneration II, Resistance II for 50 s (the endless passives wait behind them as vanilla hidden effects and return after); purple aura every 3 ticks for every viewer (`WAKANDA` map) | 100 s, from activation |

Bosses only take the damage of the control moves. The new moves were designed for the user's requests: Ground Pound replaces
Disarm, Flying Kick replaces Backflip, Block replaces Parry (no perfect parry / riposte), Rising Uppercut replaces Shadow Step.

## Suited kit

| Key | Move | Damage / effect | Cooldown |
|---|---|---|---|
| R | Warrior's Combo | 8 / 9 / 10 | 1.5 s |
| Shift+R | Kinetic Pulse | 10 within 5, 30 Kinetic | 2.5 s |
| Shift+R, full bar | Kinetic Overload | 20 -> 15 at the edge, 10 blocks, whole bar | 5 s |
| G | Claw Combo | 7 / 7 / 9, bleed 4 s | 2 s |
| Shift+G | Pounce | 8 landing, pin 2 s, 10 strike | 12 s |
| X | Panther Leap | ~12 blocks up, ledge catch | 3 s |
| Shift+X | Drop Strike | 10 within 4 | 8 s |
| Z | Vibranium Dagger | 8, 3 hops | 7 s |
| Shift+Z | Kimoyo Flash | blind + slow within 6 | 10 s |
| V | Stalk | lose mobs past 6 while sneaking; backstab x3 (x1.75 players), max 20 | 10 s |
| Shift+V | Panther Senses | toggle, 40 | -- |
| C (hold) | Absorb Stance | frontal hits into Kinetic, 40 a stance | 4 s |
| Shift+C | Kinetic Return | max(8, shot x1.25) + 0.25 per Kinetic spent (40 max), max 20 | 6 s |
| Shift+N | Mask | toggle | -- |
| H | Suit Up / Away | the necklace worn: the suit forms / drains back (both kits) | one change at a time |

Kinetic Overload lost its own key when the user moved the Kinetic Pulse to Shift+R; it fires from Shift+R when the bar is full.

## HUD

Bottom-right `VanillaHud.Stack`: title always "Black Panther" (no T'Challa title, no passive status words, user v0.17.7), the
Kinetic bar in the **Meter** style (suited), the Absorb bar (Hairline) while absorbing, the six key boxes for the live kit. Left Alt
lists both moves per key, H Suit Up / Away (necklace worn or suited), Shift+N Mask and "Shift+R (full)" Kinetic Overload in the suit.

## Animations (BlackPantherPose)

Anim ids keep their numbers: 13 = Ground Pound (was Disarm), 15 = Flying Kick (was Backflip), 18 = Rising Uppercut (was the
riposte). Block uses the held stance `BLOCK` (forearms crossed in front of the face) and `F_BLOCK` in first person. The Ground
Pound's slam also sets the shockwave ring (`pulseStart/pulseRadius/pulsePos`, drawn by `BlackPantherEffectsRenderer`).
