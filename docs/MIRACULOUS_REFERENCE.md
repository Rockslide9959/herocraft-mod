# The Miraculous -- core reference (v0.16.6)

Four Hero-Tier powers share one core: **Ladybug** (Tikki, the Earrings), **Cat Noir** (Plagg, the Ring), **Rena Rouge**
(Trixx, the Necklace) and **Hawk Moth** (Nooroo, the Brooch). Each is its own Hero-Tier key (`ladybug`, `cat_noir`,
`rena_rouge`, `hawk_moth`) and takes the one Primary slot like every other hero; all four share one synced state
(`MiraculousState`, attachment `projecthero:miraculous_state`). The kits (the six keys) are documented per hero:
`MIRACULOUS_LADYBUG.md`, `MIRACULOUS_CAT_NOIR.md`, `MIRACULOUS_RENA_ROUGE.md`, `MIRACULOUS_HAWK_MOTH.md`.

Code: `com.projecthero.mod.miraculous` (server: `Miraculous` API, `KwamiCompanion`, `MiraculousPhrases`,
`MiraculousDamage`, `MiraculousStats`, `MiraculousCommand`, `entity/KwamiEntity`, `item/*`) and
`com.projecthero.mod.client.miraculous` (`MiraculousClient`, `MiraculousHud`, `MiraculousPose`, `MiraculousFx`,
`MiraculousJewelLayer`, `KwamiRenderer`). Every tunable is a static final in `MiraculousConfig`. Assets are generated
by `gen_kwami_game.js` (session scratchpad copy; see "Assets").

## Getting a Miraculous

| | Ladybug | Cat Noir | Rena Rouge | Hawk Moth | Carapace | Pegasus |
|---|---|---|---|---|---|---|
| Kwami | Tikki | Plagg | Trixx | Nooroo | Wayzz | Kaalki |
| Jewel (item) | `ladybug_earrings` | `cat_ring` | `fox_necklace` | `butterfly_brooch` | `turtle_bracelet` | `horseshoe_glasses` |
| Where | the Miraculous Box | the Miraculous Box | the Miraculous Box | stronghold library chests 1 in 6, ancient city chests 1 in 10 (never in the box) | the Miraculous Box | the Miraculous Box |
| Food | cookie (vanilla) | `camembert` | `macaron` | honey bottle (vanilla; the bottle comes back) | dried kelp (vanilla) | sugar (vanilla) |
| Transform | `Tikki, spots on!` | `Plagg, claws out!` | `Trixx, let's pounce!` | `Nooroo, dark wings rise!` | `Wayzz, shell on!` | `Kaalki, full gallop!` |
| Detransform | `Spots off!` | `Claws in!` | `Let's rest!` | `Dark wings fall!` | `Shell off!` | `Dismount!` |
| Colour | `0xE0242C` | `0x7EE23A` | `0xEE7C24` | `0x9A5CD6` | `0x3FAE5A` | `0xE6C276` |

- **The Miraculous Box** (`miraculous_box`, a GeckoLib item: three 60-degree slabs make the hexagon, black lacquer with
  red trim, a Greek-key band and the red emblem on top): jungle temple chests 1 in 8, woodland mansion chests 1 in 6.
  Right-click: a chime and a red/gold burst, the box is used up, ONE random jewel of the five (Earrings, Ring, Necklace,
  Turtle Bracelet, Horseshoe Glasses; never the Brooch). With
  `projectheroUniquePowers` on it prefers a Kwami nobody holds (and not the one you already hold).
- **A jewel**: right-click -> `Miraculous.grant`. Refused (jewel kept) when you already hold that Kwami, or by
  `UniquePowers.refuse` (asked BEFORE the jewel is used). Used up on success.
- **`Miraculous.grant(player, kwami)`** itself plays the welcome, so every path gets it (jewel, `/projecthero power grant`,
  random serums, the spawn calling): the Kwami pops out of the jewel in front of the player's face, waves, and says
  three lines in chat (`[Tikki] ...`, `message.projecthero.miraculous.intro.<id>.1..3`) that name the phrases.
- Revoking (commands, another Primary) gives no jewel back.
- Recipes: Camembert = milk bucket + wheat + brown mushroom (shapeless, the bucket comes back); 4 macarons = 2 sugar +
  egg + wheat (shapeless; v0.16.6 user: no pink dye).

## Transforming (spoken, not H)

The holder types the phrase in chat (`ServerMessageEvents.CHAT_MESSAGE`; the line still goes out: the hero shouts it).
`MiraculousPhrases.match` is lenient: case, punctuation, extra spaces and curly / straight apostrophes are ignored, and
the detransformation phrase may start with the Kwami's name. Only the holder's own Kwami's phrases count. **H does not
transform** (`MiraculousClient.ownsH` is false); `Miraculous.toggleForm` / the `MiraculousActionPayload` stay for
commands and tests. Debounce: `TOGGLE_COOLDOWN` (20 ticks) after a change, and a transformation can't be undone before
its `TRANSFORM_TICKS` (40) are over.

The sequence (all clients play it from the synced `formChangeAt`):
1. tick 0: the Kwami spins and shrinks into the jewel (`into_jewel`, 12 ticks, it flies to the jewel's spot), then it
   is removed; sounds: enchanting table + amethyst chime + illusioner mirror.
2. ticks 0-30: three ribbons of the hero's colour spiral up round the player (dust + end-rod sparkles + sparks),
   tightening as they rise; tick 14 a beacon chime. Pose (`MiraculousPose`, before the kit's pose): arms sweep out and
   up, head back.
3. tick 30: the flash -- `FLASH` particle, a burst, level-up + firework twinkle + amethyst, and a short full-screen
   tinted flash for the holder (HUD). The pose drops into a hero landing (fist forward, arm back, stride, head down),
   then eases back over 10 ticks after tick 40.
4. The kit reveals its suit by `formChangeAt` progress over `TRANSFORM_TICKS`; moves wait for `Miraculous.canAct`.

Detransforming: a flash and a burst in the hero's colour, the Kwami pops out in front of the face, **tired**.

## The countdown and the hungry Kwami

- The kit calls `Miraculous.startCountdown` when its Shift+Z special really fires: 3 minutes (`COUNTDOWN_TICKS`). A
  soft note-block chime for the holder at each minute mark, a pling every second of the last 10; at 0 the forced
  detransformation (`message.projecthero.miraculous.time_up`).
- **After EVERY detransformation** (the phrase, the countdown, death while transformed) the Kwami is tired and
  **hungry**; transforming is refused (`message.projecthero.miraculous.hungry`, naming the food) until it is fed.
- Feeding: right-click the Kwami with its food, or use (eat / drink) the food while it is hungry and you are
  detransformed (`UseItemCallback`) -- the Kwami eats instead (eat clip, crumbs, nibbling sounds, a burp, a happy line).
  Feeding a Kwami that isn't hungry: it still eats one and you get Regeneration I for 5 s. Not while it is still eating.

## The Kwami entity

`KwamiEntity` (GeckoLib, one type `projecthero:kwami`, variant + owner synced, never saved). `KwamiCompanion` keeps one
per detransformed holder and re-creates it the tick it goes missing (login, respawn, dimension change, chunk unload,
anything that removed it).
- Follows everywhere: both sides run the same follow from the owner's position, so it glides smoothly (the server's
  position packets are ignored while the client knows the owner). The owner's own movement is added first, then it eases
  to its anchor over the right shoulder (eye height - 0.3), so sprinting, elytra and riding keep it beside you; no
  physics (caves, water); more than 16 blocks away and it snaps back. Standing still 5 s, it drifts round in front of
  you. It looks where you look (at you while waving / eating).
- Can't die: no damage of any kind, fire immune, ignores explosions, no portals; `/kill` and the void send it back to
  the owner. No loot, no XP.
- Clips (`animations/miraculous/kwami.animation.json`, `animation.kwami.<id>.<clip>`): `idle` (float, bob, sway; 4.8 s),
  `wave` (arm out to the side -- the big head would hide a raised arm), `eat` (paws to the mouth, nibbling), `tired`
  (droops and sinks), `into_jewel` (spin + shrink, held), `pop_out` (scale up with a spin). Nooroo's two pairs of wings
  flutter in every clip.
- Rigs: the user-approved concept models (`gen_kwami_models.js`, `gen_nooroo_concept.js`), drawn at 0.56 scale (~0.6
  blocks tall).

## The worn jewel

`MiraculousJewelLayer` (registered after the kits' suit layers), every viewer, whenever the power is held:
earrings on the ear sides of the head (black; red with black spots transformed), the Cat Ring on the right hand's
finger through the shared `HandRing` (also first person, `GreenLanternRingHandMixin`; silver; black with a glowing
green paw print transformed; `RingPlacement` id `cat_ring`), the fox-tail necklace on the chest, the butterfly brooch at
the collar (darker violet transformed), the Turtle Bracelet round the LEFT wrist (a dark green cord, three jade beads a
face and a turtle-shell bead on the outer face; bright green transformed; fits wide and slim arms), the Horseshoe
Glasses on the face (two horseshoe frames with nail holes, an arched bridge, temples along the head; silver-grey -- hidden
while transformed, v0.16.7: the Pegasus suit's own goggles, painted on its skin, take their place). Lifted further off the body while transformed so they sit on the suits.

## Shared passives (while transformed)

`MiraculousStats`: +12 armour, +6 toughness, +10 max health, +7 melee, Regeneration I (topped up when low, removed on detransform if it is ours), +25% speed, +35% jump. `MiraculousDamage`
(`ALLOW_DAMAGE`): no fall damage; every other hit goes through the kit's `KwamiKit.modifyIncomingDamage` (0 cancels,
a changed amount is cancelled and re-issued once). `/kill` and the void are never touched.

## HUD

`MiraculousHud` (bottom-right, the mono style): hero name, the countdown's mm:ss, five jewel spots in the hero's colour
(one goes out per minute, the last blinks), six key boxes R G Z X C V from the kit's `idsOf` (cooldown fill, lit while
`kit.active`), dimmed while detransformed; Left-Alt shows the move names (`projecthero.miraculous.ability.<id>`).
Detransformed and fed: a gray hint with the phrase; hungry: "Tikki is hungry: feed her cookies" (wrapped to the box
row's width).

## Commands

- `/projecthero power grant <player> ladybug | cat_noir | rena_rouge | hawk_moth | carapace | pegasus` (and `revoke`).
- `/projecthero power tune miraculous kwami` (info) `| feed | toggle | countdown <seconds>`.

## Guide

Six chapters (`CH_LADYBUG` 42 .. `CH_HAWK_MOTH` 45, `CH_CARAPACE` 46, `CH_PEGASUS` 47, `CHAPTER_POWER_BASE` 48): tier line, body, the box, the jewel,
the Kwami, transforming, the countdown, feeding, the passives, and a controls table built from the kit's `idsOf`
(R / Shift+R / G / Shift+G / X / Shift+X / Z / Shift+Z / V / Shift+V / C / Shift+C, names + `.desc`).

## Ladybug

Kit: see `MIRACULOUS_LADYBUG.md`.

| Key | Move | Shift version |
|---|---|---|
| R / G / X / Z / V / C | see the kit doc | Shift+Z = Lucky Charm (starts the countdown) |

## Cat Noir

Kit: see `MIRACULOUS_CAT_NOIR.md`.

| Key | Move | Shift version |
|---|---|---|
| R / G / X / Z / V / C | see the kit doc | Shift+Z = Cataclysm (starts the countdown) |

## Rena Rouge

Kit: see `MIRACULOUS_RENA_ROUGE.md` (the kit build fills it in).

| Key | Move | Shift version |
|---|---|---|
| R / G / X / Z / V / C | see the kit doc | Shift+Z = Mirage (starts the countdown) |

## Hawk Moth

Kit: see `MIRACULOUS_HAWK_MOTH.md` (the kit build fills it in).

| Key | Move | Shift version |
|---|---|---|
| R / G / X / Z / V / C | see the kit doc | Shift+Z = the special (starts the countdown) |

## Assets

`gen_kwami_game.js --out src/main/resources --nooroo <nooroo assets dir>` writes the four rigs + textures, all 24
clips, the box's geo / texture, and the JSON element models + hand-painted 16 x 16 textures of the jewels and foods.
v0.16.6: Wayzz and Kaalki come from `gen_wayzz_kaalki_concept.js` (concept sheets + rigs in
`scratchpad/concepts_wayzz_kaalki/`) and `gen_wayzz_kaalki_game.js --out src/main/resources` (their rigs, their 12 clips
added to `kwami.animation.json` with the same clip code, and the Turtle Bracelet / Horseshoe Glasses item models).

## Tests

`MiraculousCoreGameTests`: grant / revoke per Kwami, one Primary, the phrases, stats on / off, the countdown's forced
detransformation, hungry refusal + feeding, the snack, death leaves it hungry, half fall damage (none after a movement move), box + jewel, the Brooch
never in the box, unique powers refuse before the jewel is used, every grant path plays the welcome, Nooroo returns the
bottle, the Kwami follows / snaps back / can't die / is re-created / goes into the jewel. `MiraculousTurtleHorseGameTests`:
Carapace / Pegasus grant + revoke, their phrases, dried kelp / sugar, the box giving both new jewels (and the jewels
granting), their own guide chapters.
