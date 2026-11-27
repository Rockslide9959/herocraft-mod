# Black Panther reference

Hero-Tier Primary power, **Street tier** (melee 7-10, abilities 8-10, ultimate 15-20, `MAX_HIT` 20). Package
`com.projecthero.mod.blackpanther`, client `client/blackpanther`. Numbers live in `BlackPantherConfig`; state in the
`projecthero:black_panther_state` attachment (`BlackPantherState`, synced, copy-on-death). Tests: `BlackPantherGameTests`,
`BlackPantherV0177GameTests`, `BlackPantherSuitedV0177GameTests` (the v0.17.7 suited kit and the no-decay Kinetic bar).

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
  chestplate slot. Death / keepInventory / graves treat it like armour. A chestplate cannot be worn with it (the suit gives its own
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
- **Stats while suited:** `reconcile` adds `black_panther_armor` +12 armour and `black_panther_toughness` +4 toughness
  (`SUIT_ARMOR` / `SUIT_TOUGHNESS`) and Regeneration II; both modifiers are removed when the suit goes.
- **Necklace model** (`BlackPantherRender.renderNecklace`, drawn by `BlackPantherLayer` only while `BlackPanther.showsNecklace` =
  suited AND necklace worn AND not spectator): the gold necklace painted on the comics skin, in 3D (rebuilt for the comics skin, user:
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
| Herb | Speed I, Jump Boost I | same |
| Falls | half (Cat's Landing) | none |
| Night Eyes | in the dark | always |
| Hunter's Instinct | creatures hunting him within 20 outlined | same |
| Kinetic Absorption | -- | 40% of a hit soaked, 1 damage = 4 Kinetic |
| Vibranium Weave | -- | 12 armour / 4 toughness (attribute modifiers while suited); shots under 15 bounce off |
| Silent Paws | -- | no vibrations |

The melee is one attribute modifier (`black_panther_herb`): `UNSUITED_MELEE - 1` or `SUITED_MELEE - 1`. Regeneration is an
infinite `PowerToggles.effect` refreshed in `reconcile`.

## Unsuited kit (T'Challa)

| Key | Move | Damage / effect | Cooldown |
|---|---|---|---|
| R | Warrior's Combo | 5 / 7 / 10 (jab on the press, kick shoves) | 1.5 s |
| Shift+R | Leg Sweep | 7 within 2.6, trip 1.5 s | 6 s |
| G | Takedown | grab within 3 blocks, slam 10, daze 2 s | 8 s |
| Shift+G | Ground Pound (new) | hop, dive, slam: 9 within 3.5, knock-up, Slowness II 1.5 s | 8 s |
| X | Vault | front flip, no fall damage | 2.5 s |
| Shift+X | Flying Kick (new) | dash ~8 blocks, 10 to the first creature reached, rebound | 6 s |
| Z | Throwing Knife | 8, returns | 4 s |
| Shift+Z | Kimoyo Scan | mark 10 s, +25% from him | 15 s |
| V | Rising Uppercut (new) | 9 within 3, launched + Slowness III 1.5 s | 6 s |
| Shift+V | Hunter's Tracking | toggle, outlines + footprints, 24 | -- |
| C (hold) | Block (new) | every hit from the front -60%, up to 5 s | 1 s |
| Shift+C | Ancestral Resolve | Regeneration II 4 s, Strength I 6 s | 30 s |

Bosses only take the damage of the control moves. The new moves were designed for the user's requests: Ground Pound replaces
Disarm, Flying Kick replaces Backflip, Block replaces Parry (no perfect parry / riposte), Rising Uppercut replaces Shadow Step.

## Suited kit (v0.17.7, user-approved)

The user approved this moveset ("build this please") and asked that the Kinetic bar never dissipate. Rule: **plain keys are free
and fill a little Kinetic when they land; Shift keys spend Kinetic for a charged violet version.** The numbers are placeholders
from the approved table: the user dropped power-tier balancing ("i want to do all the damage numbers myself"), so every damage,
cost, gain, duration and cooldown is a named constant in `BlackPantherConfig` (section "v0.17.7 suited kit", one block per move),
and the new moves have no tier cap (`BlackPantherCombat.strike(..., tierCap = false)`, boss cap only: 8% of a boss's max health a
hit; bosses are never pinned or knocked back, and they keep their threat table under Stalk).

| Key | Move | Kinetic | Damage / effect | Cooldown |
|---|---|---|---|---|
| R | Vibranium Combo | +5 per strike landed | claw strikes 8 / 9 / 10 (6 ticks apart, the first on the press); the 3rd leaves a violet slash mark (three claw lines of violet dust) | 1.5 s |
| Shift+R | Kinetic Combo | costs 25 | three fast charged slashes, 10 each (4 ticks apart); the finisher releases a forward cone shockwave (4 blocks, 60 deg either side): 8 to everything the slash itself missed | 2 s |
| G | Pounce | +10 on a hit | aimed leap onto a target up to 8 blocks: 9 on landing, pinned (Slowness VII) 1 s | 6 s |
| Shift+G | Kinetic Pounce | costs 30 (only once a target is aimed at) | the same leap, ending in a violet ground burst: 10 to everything within 3 blocks of the target, knocked away; the target pinned 1 s | 8 s |
| X | Panther Leap | -- | ~12 blocks up, ledge catch | 3 s |
| Shift+X | Kinetic Dash | costs 20 | ~10 blocks straight along the look (pitch included) over 5 ticks, moved by the server in 0.25-block collision-checked steps (stops flush at a wall); no damage reaches him for the dash + 2 ticks; each creature passed through takes 8 once; no fall damage; violet afterimages | 3 s |
| Z | Vibranium Dagger | +5 per target hit | 8, 3 hops | 7 s |
| Shift+Z | Kinetic Overload (ULTIMATE) | the whole bar, must be FULL | leap + ground slam: 20 at the centre falling straight to 15 at 10 blocks; big violet dome + ring + sonic boom | 5 s |
| V | Stalk | -- | lose mobs past 6 while sneaking; backstab x3 (x1.75 players), max 20 | 10 s |
| Shift+V | Kinetic Shroud | costs 30 | 4 s: every hit 50% less, a living attacker that lands one takes 4 back (thorns; never for thorns); violet energy shimmer over the suit | 12 s from the cast |
| C (hold) | Absorb Stance | fills | frontal hits into Kinetic, 40 a stance | 4 s |
| Shift+C | Kinetic Return | spends up to 40 | max(8, shot x1.25) + 0.25 per Kinetic spent, max 20 | 6 s |
| Shift+N | Mask | -- | toggle | -- |
| H | Suit Up / Away | -- | the necklace worn: the suit forms / drains back (both kits) | one change at a time |

- **Refusal:** a Shift move with too little Kinetic fires nothing, spends nothing and starts no cooldown; a dull note (note-block
  bass + amethyst hit, to him only) plays, the action bar says how much is needed, and the HUD's Kinetic Meter flashes red / white
  for 12 ticks (`abilityReadyAt["kinetic_denied"]`, synced). Kinetic Overload one short of full is refused the same way.
- **Gains** only from a strike that really landed on a living target (`strike` returns whether `hurt` took); a whiff adds nothing.
  Shift moves never add Kinetic back.
- **Removed from the suited kit (v0.17.7):** Kimoyo Flash (and the Kimoyo bead entity), Panther Senses, the Kinetic Pulse, the
  Claw Combo (its bleed), Drop Strike, the suited Warrior's Combo. Hunter's Instinct (passive), Kimoyo Scan and Hunter's Tracking
  (unsuited) stay. The `kimoyo_bead` item stays registered (unused) so old stacks load.
- Shroud timer: `abilityReadyAt["kinetic_shroud_active"]` (synced, so every viewer sees the shimmer); cleared when the suit goes.

## Kinetic Energy (v0.17.7)

0-100. Fills from Kinetic Absorption (40% of each hit soaked, 1 damage = 4 Kinetic), Absorb Stance and the plain moves' on-hit
gains above. **It never decays** (user: "make it so that the kinetic energy bar doesnt dissipate, it doesnt dissipate in the movies"):
the old out-of-combat drain (2 a second after 10 s) is gone, suited or not, after the suit goes, and across a relog (the attachment
is persistent). **A death empties it** (`BlackPanther.onDeath`, from `AFTER_DEATH`, before the copy-on-death copy).

## HUD

Bottom-right `VanillaHud.Stack`: title always "Black Panther" (no T'Challa title, no passive status words, user v0.17.7), the
Kinetic bar in the **Meter** style (suited; flashes red / white on a refused Shift move), the Absorb bar (Hairline) while absorbing,
the six key boxes for the live kit (V lit while Stalk or the Shroud runs). Left Alt lists both moves per key (Overload under Z), H
Suit Up / Away (necklace worn or suited) and Shift+N Mask in the suit.

## Animations (BlackPantherPose)

Anim ids keep their numbers: 13 = Ground Pound (was Disarm), 15 = Flying Kick (was Backflip), 18 = Rising Uppercut (was the
riposte). v0.17.7 suited kit: 5 = Vibranium Combo (the claw clip, `VIBRANIUM_COMBO_STEP`), 6 = Pounce leap, 22 = Pounce landing
rake, 23 = Kinetic Combo (two cross slashes then a two-handed thrust lunge), 24 = Kinetic Pounce burst (both fists down, a knee),
25 = Kinetic Dash (low, chest forward, arms swept back, then a skid), 26 = Kinetic Shroud (fists crossed, then flung open), 4 =
Overload rewritten as a ground slam (sink, fists overhead, hammer down on a knee). Each has a first-person hand clip. 3 / 7 / 21
(Pulse, Drop Strike, Kimoyo Flash) are retired. `BlackPantherEffectsRenderer`: the ring, plus Overload's violet dome (latitude
bands, brightest at the rim) and the Dash's afterimages (translucent violet silhouettes every 0.7 blocks, fading over 10 ticks,
client-only map). `BlackPantherRender.renderShroud`: the charged creeper's swirl in violet on a slightly larger shell while shrouded. Block uses the held stance `BLOCK` (forearms crossed in front of the face) and `F_BLOCK` in first person. The Ground
Pound's slam also sets the shockwave ring (`pulseStart/pulseRadius/pulsePos`, drawn by `BlackPantherEffectsRenderer`).
