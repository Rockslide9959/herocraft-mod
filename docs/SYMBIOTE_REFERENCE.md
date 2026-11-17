# Symbiote reference

Code: `src/main/java/com/projecthero/mod/symbiote/` (server) and `src/client/java/com/projecthero/mod/client/symbiote/`
(client). The history of the player power (Biomass, suit, abilities, host variants) up to v0.13.21 lives in
[HEROPACK_CONTENT_REFERENCE.md](HEROPACK_CONTENT_REFERENCE.md) ("Symbiote" sections). This file covers the Symbiote as
a creature that takes over other creatures.

## v0.14.4 -- symbiote pets

### Who a free Symbiote can take over

`SymbioteEntity.isValidHost` (the free goo's host scan):

| Mob | Result |
| --- | --- |
| Anything with an attack-damage attribute (monsters first, as before) | hostile **Symbiote Host** (`SymbioteHost.mark`) |
| Grown cow / mooshroom, pig, sheep, chicken, rabbit, fox, ocelot, wild wolf, stray cat (`SymbioteHost.isPassiveHostSpecies`) | hostile **infested animal** (a Symbiote Host with rewritten AI) |
| A **tamed** wolf or cat with an owner (`SymbiotePet.canBond`) | loyal **Symbiote Pet** (`SymbiotePet.bond(pet, WILD)`) |
| Any other owned mob, babies of the passive species, bosses, event/raid mobs, no-AI mobs, existing hosts/pets | never |

Brain-driven animals (goat, axolotl, frog, camel, sniffer, armadillo) are deliberately excluded: the Symbiote's goals
would run alongside their brain. `SymbioteHost.takeOver(mob)` is the single dispatcher (used by the free goo and by
`/spiderman symbiote host`, which now also accepts passive animals and tamed pets).

### Infested animals (`SymbioteHost.mark` + `SymbioteMobGoals.installHostile`)

- Same buffs as any host (+60% health, +35% speed, +24 follow range, knockback resistance, never despawns) plus +4
  armour; `resetLove`; a sheep's wool is dyed black.
- AI: panic / tempt / breed / avoid / follow-parent goals are removed; a `MaulGoal` (melee, priority 1), a
  `RetaliateGoal` (HurtBy) and a `HuntPlayersGoal` (nearest player) are added. Animals with no attack-damage
  attribute bite for a flat `PASSIVE_BITE_DAMAGE` (5) -- vanilla `Mob#doHurtTarget` would throw on the missing attribute.
- Goals are never saved, so `SymbiotePet.initialize` re-installs them on `ServerEntityEvents.ENTITY_LOAD`
  (`SymbioteMobGoals.onLoad`); every added goal implements the `SymbioteGoal` marker so re-installing is idempotent and
  a release removes exactly them. Goal selectors are reached through the `MobGoalsAccessor` mixin.
- Taming an infested wolf / stray cat (possible when it is not angry) converts it on its next tick
  (`SymbioteHost.convertTamed`): host flag, hostile goals and the "Symbiote Host" name go, and it becomes a wild-origin
  Symbiote Pet.
- Death: the Symbiote crawls back out exactly as from any host.

### Symbiote Pets (`SymbiotePet`)

- **State:** `ModAttachments.SYMBIOTE_PET` (int, persistent, synced to all): `1` = shared by its owner, `2` = wild.
  Combat state (cooldowns, pounce, toggle guard) is the non-persistent `SYMBIOTE_PET_BRAIN` attachment -- no static
  per-entity map, so nothing for `ServerStateReset` to clear.
- **Getting one:** owner, bonded and suited (`Symbiote.isActive`), sneak + empty main hand + right-click on their own
  tamed wolf/cat (`UseEntityCallback`, returns SUCCESS on both sides so vanilla's sit toggle does not fire). A Normal
  host pays `SHARE_BIOMASS_COST` (40) Biomass and is refused when broken or short; Black Suit Spider-Man and Agent
  Venom pay nothing. A free Symbiote reaching a tamed pet, or taming an infested one, also makes one (wild origin).
- **Buffs:** permanent attribute modifiers (saved by vanilla with the entity): max health +20, attack +4, speed +25%,
  armour +8, knockback resistance +0.5, scale +30%, step height +0.4. Full heal on bond.
- **Regeneration:** 1 HP/s, 3 HP/s once 5 s have passed since it was last hurt by a mob.
- **Powers** (server tick from the `Mob#aiStep` mixin, on its current target, never while sitting):
  - *Spike Burst* (12 s): 8 short fixed `SymbioteTendrilEntity` spikes erupt all round; 0.9x attack damage + knockback
    to every foe within 3.5 blocks. Fires when 2+ foes are in reach, or when under 40% health with any foe in reach.
  - *Tendril Lash* (8 s): target 4-14 blocks away in line of sight -- a tendril snaps onto it, 0.5x damage, and yanks it
    toward the pet.
  - *Pounce* (5 s): target 2.5-9 blocks away, on the ground, in line of sight -- a ballistic leap
    (`AbilityHelpers.ballisticLaunch`, capped at 1.6 b/t); the first touch within 25 ticks hits for 1.25x + knockback.
  - Cats get owner-defence / owner-assist / retaliate targeting and a melee goal (`SymbioteMobGoals.installPet`);
    wolves already have all of that.
- **Loyalty:** `SymbiotePet.refuses(pet, target)` -- owner, owner's squadmates (`Squads.areAllies`), and pets owned by
  either. Enforced by a `Mob#setTarget` `@ModifyVariable` (the targeting choke point, same pattern as the Mind Lock),
  a per-tick target clear, and an ALLOW_DAMAGE veto on any hit from the pet. Spike Burst only hits its target and
  hostile (`Enemy`) non-player mobs, never creepers unless they are the target.
- **Weaknesses / removal:** the owner repeats the interaction (the Symbiote flows back, no free goo); sonic disruption
  (`SonicVulnerability` -- bell, goat horn, sonic boom) shakes it loose; fire damage x1.5; the pet losing its tame
  state releases it. Only a *wild* pet Symbiote spawns a free Symbiote when it leaves (death or sound; the new goo
  ignores that pet for 60 s) -- a shared one dissolves, so sharing can't farm free Symbiotes.

### Rendering (client)

- `LivingEntitySymbioteSkinMixin`: while drawing an entity whose synced `SYMBIOTE_HOST` or `SYMBIOTE_PET` is set, the
  body's `renderToBuffer` colour is multiplied by `SymbioteSkin.TINT` (0x2A2433, glossy purple-black). One pass, so
  collars, wolf armour, wool and layers all still draw on top. Applies to every host, hostile monsters included
  (`SYMBIOTE_HOST` is now synced).
- `SymbioteSkin.EyesLayer`: white slanted Venom eye patches, `RenderType.eyes` (emissive), on wolf, cat, cow and
  mooshroom renderers. Textures `textures/entity/symbiote/symbiote_eyes_{wolf,cat,cow}.png` are generated by
  `scratchpad/gen_symbiote_pet_eyes_v0144.js` against the vanilla 64x32 UV layouts. Other infested animals are
  tinted only.
- A pet's bigger size is the `SCALE` attribute (vanilla renders it). Tendril Lash / Spike Burst reuse the existing
  `SymbioteTendrilEntity` renderer.

### Tests

`SymbiotePetGameTests`: a free Symbiote turns a cow into a hostile infested host that targets a player; a tamed wolf
bonded becomes a buffed, bigger pet that refuses its owner as a target, regenerates, survives a reload of its goals,
and is released by its owner.

## v0.14.4 -- pet hosts

Reworks the Symbiote Pet above (user request: the free Symbiote bonds with your pet, which becomes its host but stays
friendly and obedient, with its own powers; transforms in combat, detransforms out of it, with the pixel
transformation). Where this section and "symbiote pets" disagree, this one wins.

### Bonding

- A free `SymbioteEntity` now **prefers** a tamed wolf/cat (`SymbiotePet.canBond`) over every other host: its host
  score gets a `PET_PREFERENCE` (400, ~20 blocks squared) bonus, beating the monster bonus (96). Within
  `PET_LEAP_RANGE` (3.5) of a pet it springs at it once a second (`leapAt`, ~10 ticks aloft, momentum kept while
  `leapTicks` runs) so a trotting pet can't outpace the goo. Contact -> `SymbioteHost.takeOver` -> `SymbiotePet.bond`.
- `bond` (wild goo, owner share, taming an infested wolf/cat -- all the same path) marks the pet, installs cat goals,
  sends the owner a chat line (`pet_bonded` / `pet_shared`), and announces itself with one transform that recedes after
  the combat timeout if there is no fight.
- Obedience: the Symbiote adds no goals to wolves; a cat's `MaulGoal` refuses while sitting and its owner-defence
  goals are vanilla (they already check `isOrderedToSit`). Every power returns early while the pet sits (Guardian
  Shroud excepted -- it fires from the seat and never moves a sitting pet). Loyalty rules are unchanged.

### Form: transformed only in combat

- State: `ModAttachments.SYMBIOTE_PET_FORM` = `SymbiotePet.Form(on, since)` -- **not persisted**, synced to all.
  Absent = normal form. `formProgress(entity, now, partial)` (0 normal .. 1 transformed) is continuous: a change that
  interrupts the previous one back-dates `since` so the reveal never jumps.
- `inCombat`: a live target (not while sitting); hurt by a non-friendly mob within 60 ticks; bit something within
  60 ticks (not while sitting); owner within 20 blocks is under 40% health and just hurt (`ownerInDanger`); owner within
  20 blocks hit / was hit by a live non-friendly mob within 60 ticks (not while sitting). Any of these refreshes
  `Brain.lastCombatAt` and transforms if needed; `COMBAT_TIMEOUT` (180 ticks, 9 s) after the last one it detransforms.
- `TRANSFORM_TICKS` 30, `DETRANSFORM_TICKS` 40. Combat modifiers (health +20, attack +4, speed +25%, armour +8,
  knockback +0.5, step +0.4) flip at the switch; the **scale** modifier eases 0 -> +0.3 with a smoothstep of the
  progress (`easeScale`, quantised to 0.005 to limit attribute packets). All are `addOrUpdateTransientModifier` with the
  same fixed ids as before -- idempotent, never saved. Transforming heals 6.
- Regeneration: 1.5 HP/s transformed, 0.5 HP/s in normal form (the old 1 / 3 HP/s split is gone).
- **Migration**: pets saved by the first v0.14.4 slice carry *permanent* modifiers under those ids. The first tick of
  any pet instance (`Brain.reconciled`) strips all of them when it is not transformed and clamps health. Origin `1`/`2`
  keep their meaning (it only decides whether a free Symbiote tears loose on death / sound).

### Powers (transformed only)

Order each tick: Guardian Shroud (owner in danger), then on the target (never while sitting): Spike Burst, Latching
Bite, Tendril Lash, Pounce (Spike Burst, Tendril Lash and Pounce are unchanged).
- **Latching Bite** (`BITE_COOLDOWN` 140): target within 2.8 blocks in line of sight -- lunge, 1.1x attack, Slowness IV
  2.5 s + Weakness 3 s, heals the pet 30% of the damage; one tendril from its jaws onto the target plus four wrapping
  ones, ichor / crit / damage-indicator particles, growl or hiss + fangs sound.
- **Guardian Shroud** (`SHROUD_COOLDOWN` 900): owner within 16 blocks and `ownerInDanger` -- tendril pet -> owner, six
  tendrils rising around the owner, Absorption II 10 s + Resistance I 5 s, every foe within 4 blocks of the owner hurt
  (0.5x) and thrown back, the pet targets the owner's attacker (and leaps toward the owner) unless sitting; action-bar
  line `pet_shroud`.

### Rendering: the pixel transformation (`client/symbiote/SymbiotePetSkin`)

- Same technique as the player suit-up (`SymbioteDissolve`): per base texture, `STEPS`+1 (33) pre-built dynamic
  textures with progressively more opaque pixels turned glossy purple-black (shading kept from the fur's luma, 1-in-10
  wet highlights), plus a violet "wet front" band just ahead of the covered pixels. Order: distance to 7 seed pixels +
  jitter, so black blotches grow outward; seeds depend only on texture size, so a wolf switching tame / angry textures
  keeps its pattern.
- `LivingEntitySymbioteSkinMixin` swaps the `ResourceLocation` local of `LivingEntityRenderer#getRenderType`
  (`@ModifyVariable` at the first STORE) for Symbiote Pets -- body only, so collar, wolf armour and eyes draw on top.
  Pets are no longer colour-tinted (`SymbioteSkin.tinted` = hostile hosts only).
- At rest a pet keeps `DORMANT` (5%) of the skin: a few black blotches, plus a black drip every 4.5 s. The white eyes
  (`SymbioteSkin.EyesLayer`) open past `EYES_AT` (70%) coverage. The transform itself only spatters a little ichor
  (no ink cloud -- the skin is the visual, as for the player suit-up).
- Visually checked with a temporary client screenshot harness (dormant, 20-80% spread, full, recede, night); the
  harness is not committed.

### Tests

`SymbiotePetHostGameTests`: a free Symbiote picks a sitting tamed wolf over a closer husk and bonds with it (still
owned, still sitting, refuses its owner); bond -> transformed + buffs, forced calm -> detransform + buffs off + scale
eases back, a target -> transform + buffs + full size, fight over -> timeout detransform; a sitting wolf and cat with a
target stay put and don't attack; a pet with pre-rework permanent modifiers loads in its normal form without them.

## v0.15.15

- **Suit stats** -- the Normal host's `ModArmorMaterials.SYMBIOTE_HOST` is diamond grade (3/8/6/3 = 20, toughness 2.0).
  `SymbioteSuitResistance` keeps an ambient, particle-free Resistance I on a Normal host / Agent Venom while the suit
  is on (Black Suit Spider-Man already gets Resistance I from `SpiderPassives`), never replacing a stronger Resistance
  and only removing its own. The old flat 10% `SymbioteDamageRules.SUIT_DAMAGE_FACTOR` cut is now 1.0.
- **Suit-up** -- `client/symbiote/SymbioteSpread`: per-texel reveal ranked by distance from the middle of the chest along
  the body (torso direct, arms via the shoulder, legs via the hip), head texels ranked separately after the body;
  48 frames, played backwards on retract. Used by `SuperheroArmorRenderer` and the first-person sleeve
  (`SuperheroFirstPersonArm`). `SymbioteDissolve` is unchanged (Moon Knight still uses it).
- **Body spikes** -- `client/symbiote/SymbioteBodySpikes`: thorns on the back, shoulders and arms while a spike move's
  window is open (`SPIKE_SHOT` 30 t, `SPIKE_FAN` / `SPIKES_FLEX` 36 t) and for as long as Thorns mode is on. Third
  person rides `SymbioteBladeRenderer.Layer`, first person `PlayerRendererSymbioteBladeMixin`.
- **Call Carnage** -- `SymbioteCarnageCall`: a bonded player holding right-click on a `SymbioteEntity` (anything but
  flint and steel / a vial) for 100 ticks. Each repeated interaction pings the channel; the entity's tick cancels it
  after 10 ticks without one, beyond 4 blocks, or without the bond. Completion: `CarnageSpawner.dropNear(12..20)`,
  the Symbiote turns crimson (`DATA_CRIMSON`: 0..1 redness, 1..2 dissolve) and is discarded after 30 ticks; 10-minute
  per-player cooldown (static, cleared by `Symbiote.clearSessionState`). Refused on Peaceful or with a Carnage /
  crimson meteor within 256 blocks. Tests: `SymbioteV01515GameTests`.
- **Guide** -- the three Symbiote pages share section builders in `HeroPackGuide` (`symbioteHowToGet`, `symbioteSuit`,
  `symbioteBiomass`, `symbioteMoves`, ...), keys under `projecthero.guide.symbiote.s.*`
  (`scratchpad/lang_v01515_symbiote.js`). Agent Venom's I key now shows the Punisher page.

## v0.16.2 -- Black Suit Spider-Man: the Hunger rework

The black suit no longer hands out flat, always-on buffs. Its strength comes from **Hunger**
(`SymbioteHunger`, attachments `symbiote_hunger` float 0..100 synced to the owner and `symbiote_takeover`
long end-tick synced to all; neither persistent).

- **Feeding:** +4 per landed hit (any `source.getEntity() == player`, AFTER_DAMAGE), +min(8, 1.5 x damage) per
  hit taken. Decays 3/s after 5 s out of combat. Nothing feeds it during the form or the crash.
- **Tiers** (`SymbioteHunger.reconcile`, reusing the old `symbiote_black_suit_*` modifier ids):

| Tier | Hunger | Melee | Speed | Jump | KB res | Extra |
|---|---|---|---|---|---|---|
| Restrained | <30 | +10% | +5% | - | - | - |
| Bonded | 30-80 | +25% | +12% | +15% | 0.10 | swing accel +15% |
| Overdrive | 80+ | +40% | +18% | +20% | 0.20 | +10% Spider-Sense dodge; food drain 0.06 exhaustion/t; fire+sonic x2; H refuses to retract |
| Take Over | hold V at 100 | +80% | +20% | +25% | 0.60 | scale +35%, damage taken x0.6, melee cleave 50% within 2.5; 10 s then 8 s crash (Weakness I + Slowness II, meter 0, no gain) |

- Fire / sonic always x1.5 on the black suit (x2 in Overdrive / Take Over) -- `SymbioteHunger.onAllowDamage`.
- **Resurrection** (`Symbiote.tryResurrect`): a Spider-Man host must be suited with >= 50 Hunger; the revive empties the meter.
- **Nerfs:** suit armour 2/5/6/2 = 15, toughness 1.0 (`ModArmorMaterials.SYMBIOTE`); removed the black suit's extra
  Regeneration, web-regen multiplier and +20% Web Yank range.

### Kit (`SymbioteBlackSuitAbilities`, routed from `SpiderMoves` while suited)

| Key | Replaces | Move | Numbers | Cooldown |
|---|---|---|---|---|
| X tap | Web Strike | Tendril Lash | 8 blocks, 7 dmg + short pull | 1.5 s |
| Shift+X | Uppercut | Spike Burst | 8 dmg within 3, `SymbioteAnim.SPIKES_FLEX` | 6 s |
| Shift+hold Z | Grab & Hurl | Consume | 6 blocks, 5 dmg/s for 2 s, heals 30% of dealt | 14 s |
| Shift+V | Web Cocoon | Ink Shroud | Blindness + Slowness II 4 s within 5, mobs drop you as target | 12 s |
| Hold V | Web Blossom | Let It Take Over | full meter only | - |
| Shift+C | Web Wall | Symbiote Slam | 10 dmg dive / 6 grounded, radius 4 | 14 s |

`SpiderMoves.idFor(player, slot, kind)` / `maxCooldown(id)` give the HUD and menus the move actually on a key;
swapped boxes get a purple border. The old Black Suit 3-box panel is now the Hunger panel (tier, %, revive,
Take Over / crash timers, Hairline bar with ticks at 30 and 80).

Tests: `SymbioteGameTests.blackSuitHungerDrivesItsBonuses`, `blackSuitReviveNeedsHunger`, `blackSuitSpiderManIsTrimmed` (15 / 1.0).

## v0.17.5 -- the new suit model and suit colours

User: "create a new model for it ... from scratch ... make it so that youre able to change the colour". Only the Normal
host's suit (`symbiote_host`) changed. Every gameplay rule, the suit-up clock, the moves and the HUD are untouched.

### The model (`geo/symbiote_host.geo.json`, made by `scratchpad/symbiote/gen_symbiote_suit_v0175.js`)

- Base boxes (inflate 0.3) + a raised shell (0.55, head 0.62) in the player-skin box-UV layout, declared 64 wide so
  the **128 x 128** texture is sampled at 2x (GeckoLib divides UVs by the declared width).
- 3D extras, all `sym_*` child bones: ~~shoulder caps (`sym_cap_r/l`, with three goo drips each)~~ (removed in round 2, user), a raised chest plate
  (`sym_chest`, per-face UVs onto the body-front texels behind it, so the pecs and emblem stand proud), raised quads
  (`sym_thigh_r/l`, same trick) and three spine nodes (`sym_spine`). Caps, drips and nodes are wrapped in a goo patch
  painted into unused skin texels (skin (24,0)-(40,8) and (56,16)-(64,48)).
- ~~`SuperheroArmorRenderer#renderRecursively` scales the `sym_*` bones with `SymbioteSuitFx.extraScale(progress)`~~ -- round 2:
  nothing is scaled any more; the extras' texels arrive with the goo (see "round 2" below).

### The art

Original, painted in body space (every texel placed on the 3D body, so lines run across face edges): an original
spider emblem (small head, thorax, long abdomen spike, four legs a side -- the top pair runs up over the shoulders to
the back emblem, the next two wrap round the ribs to the back, the bottom pair end in free points) with a ragged living
edge and a dark outline; big angular lenses that sweep up and round the sides of the head with the suit's darkest rim;
muscles as a height field (pecs, abs, obliques, traps, shoulder blades, lats, deltoids, biceps, triceps, forearms,
quads, kneecaps, calves) lit through bump-mapped rounded normals with a key, fill and rim light and a wet specular;
a warped vein network (raised in the shell where it is thick). The emblem, lenses, their outlines and thick veins are
all on the raised shell layer.

### Colours

- `SymbioteColours(primary, secondary, eyes)` (0xRRGGBB, every constructor masks to 24 bits; `rgb(r,g,b)` clamps;
  `parseHex`), `SymbioteColourPreset` (CLASSIC, ANTI_VENOM, CARNAGE, SCREAM, AGONY, RIOT, TOXIN).
- `ModAttachments.SYMBIOTE_COLOURS`: persistent, `copyOnDeath`, synced to all, Classic by default; kept when the bond
  is lost (a cosmetic preference). `SymbioteSuitColours.get/set/setPreset/apply`; `apply` (the screen's
  `SymbioteColourPayload`) needs a bond. No static state.
- `/projecthero power tune symbiote colour <preset> [player]` and `... colour hex <body> <emblem> <eyes> [player]` (op 2, like the rest of
  `/symbiote`).
- **The screen** (`client/gui/SymbioteColourScreen`): Your Power (I) -> **Suit Colours** (the button only shows for a
  Normal host; no new key). Body / Emblem / Eyes tabs, Hue / Saturation / Brightness bars, a hex field, the seven preset
  swatches (wrapped tooltips), Apply / Classic / Cancel. Live preview: the real 3D suit following the mouse while it is
  worn (`previewOverride` makes `SymbioteSuitTint.colours` return the pending colours for the local player inside the
  screen only), else front and back paper dolls. The preview re-bakes at most every 90 ms while a bar is dragged.

### Rendering

- `textures/armor/symbiote_host_tint.png` is the TINT MASK: R shade, G gloss, B channel (0 body / 128 emblem / 255
  eyes), A coverage. `SymbioteSuitTint.bake`: shade <= 0.5 scales the colour toward black, > 0.5 lifts it toward
  `(0.5 + 0.5 hue) * 0.86`, gloss lays `0.72 + 0.28 hue` on top (hue = colour / its max channel; pure black uses a cool
  white) -- so a black suit keeps wet highlights and a white suit keeps its muscle shading. The script's `bakePixel` is
  the exact twin; `symbiote_host.png` is its Classic bake (fallback, armour stands, the Black Suit's Let It Take Over).
- One baked 128 px `DynamicTexture` per colour triple, cached (LRU, 12 triples; the evicted texture and its
  `SymbioteSpread` frames are released). `SymbioteSpread.build` reads a baked texture's pixels from
  `SymbioteSuitTint.image` (dynamic textures are not in the resource manager); `SymbioteSpread.forget` drops frames.
  Caches clear on resource reload and on disconnect (`SymbioteSuitClient`).
- `SymbioteSuitFx.Layer` (a GeckoLib layer on `SuperheroArmorRenderer`, no-op for other sets) adds three passes of the
  same model: a **sheen** band sweeping up the body (110-tick cycle, the band crosses in the first 60%), **veins**
  pulsing in slow upward waves (72 ticks, tinted by `veinColour`: a lifted body colour, darker on a pale suit) and a faint
  additive **eye glow** in the eye colour (head slot only). Sheen and vein frames are built once (32 each) from
  `textures/armor/symbiote_host_fx.png` (R sweep coordinate, G gloss, B veins) and cross-faded between neighbours, so
  they are colour-independent and shared by every wearer. Skipped while the suit spreads and for invisible wearers.
- First person: `SymbioteSuitSleeve` (the suit's own two arm boxes, coloured, spreading, sheen + veins), called from
  `SuperheroFirstPersonArm` for `symbiote_host`.
- Tendrils, spikes (body thorns and the thrown spike) and the blade: `SymbioteSuitTint.goo(owner, texture)` recolours
  the shipped goo texture (its luminance stretched into shade / gloss) to the owner's body colour -- only for a Normal
  host in non-Classic colours. Carnage's crimson, the Black Suit, Agent Venom, pets and free Symbiotes are unchanged.
- Icons: the four `symbiote_host_*` items are three-layer (body tinted by the primary colour, `_gloss` untinted,
  `_emblem` tinted by the secondary), coloured from the local player's choice (`ColorProviderRegistry`).

### Variants

- **Agent Venom** keeps its own user-supplied look and is not recoloured (it is a different character's suit).
- **Black Suit Spider-Man** keeps its suit; its Let It Take Over form shows the new suit art in Classic colours (it
  always used `symbiote_host.png`).

### Tests

`SymbioteSuitColourGameTests`: Classic default + presets store exactly their RGB; clamping (alpha bytes, -1, out of
range channels, bad saves, junk packets, hex parsing); the colours survive a save, a death (`copyOnDeath`, dying keeps them) and losing
the bond; the attachment is synced and the payload round-trips; only a bonded player can recolour through the payload.
Visual checks: `docs/screenshots/v0175/symbiote_suit/` (harness `scratchpad/SymbioteSuitHarness.v0175.java.txt`).

## v0.17.5 -- second suit style: Lethal Protector (animated mouth and tongue)

User: "add another suit model and make it this skin [PlanetMinecraft 'Venom: Lethal Protector'], keep the spider logo and the eyes but
make the mouth animated, the tongue moves and stuff". Only the Normal host's suit changes; gameplay is untouched.

### Style choice

- `SymbioteSuitStyle` (CLASSIC, LETHAL_PROTECTOR) rides in `SymbioteColours` as a fourth component (`style`), so it lives in the
  same `SYMBIOTE_COLOURS` attachment: persistent (codec field `"style"`, optional, lenient -- missing or unknown reads Classic, so old
  saves are Classic), `copyOnDeath`, synced to all (stream codec + VAR_INT ordinal, junk ordinals -> Classic), kept when the bond goes.
- `isClassic()` = Classic colours on the Classic suit; `hasClassicColours()` / `sameColours()` ignore the style; `key()` appends
  `_lethal_protector` for that style only (Classic keys unchanged). Presets match on colours only.
- `SymbioteSuitColours.set` stores colours + style exactly (the screen); `setColours` / `setPreset` keep the style (the colour
  commands); `setStyle` keeps the colours.
- The screen (`SymbioteColourScreen`, now titled "Symbiote Suit Style", button "Suit Style" on Your Power (I)) has a Classic /
  Lethal Protector row above the Body / Emblem / Eyes tabs (labels fall back to "Lethal" when the button is narrow; wrapped tooltips
  describe each). Presets and "Reset Colours" keep the chosen style. The live 3D preview switches model too.
- Commands: `/projecthero power tune symbiote style <classic|lethal_protector> [player]`. **Fixed on the way:** `SymbioteCommand` (bond,
  activate, status and the v0.17.5 `colour`) had not been registered since the v0.12.16 single-command change; it now sits under
  `/projecthero power tune symbiote ...` (so the colour command is `/projecthero power tune symbiote colour ...`).

### The skin and its textures (`scratchpad/symbiote/make_lethal_protector.js`)

- Source: `docs/reference/symbiote/venom_lethal_protector.png` (+ `SOURCE.txt`): an HD skin, 128 x 128 in the player-skin layout at
  2x -- exactly the Classic texture's layout, so it fits the same rig and the same UVs.
- **Licence: author unknown, none stated. The three processed textures are .gitignored** (local builds only, like the Gyro and Sentry
  comics skins). `SymbioteSuitTint.lethalAvailable()` checks the resource manager once per reload; without the files the style draws as
  Classic (geo too) and the screen's Lethal Protector button is disabled with a tooltip saying why. After a merge or a fresh checkout,
  re-run the script to get them back. Ask the author before a public release ships them.
- `symbiote_host_lethal.png`: the skin with the spider logo and eyes kept; the painted grin's open mouth and teeth repainted as a dark red
  throat on the face (base layer) with the hat layer cleared there, and the strip behind the 3D jaw (front + the front of each cheek)
  the same; the lower-lip corners stay as painted (round 2: the green drool is repainted as saliva). The Classic goo patches (caps / drips / spine nodes sample them)
  copied in; a mouth atlas in unused skin texels: block J = skin (0,0)-(8,8) (the jaw shell: its front / cheeks wear the skin's own
  chin and lower lip, projected from the face with the painted teeth turned into lip, plus floor / underside), block M = (56,0)-(64,8)
  (tooth ramps, gum, tongue top with a groove, sides, underside, tip).
- `symbiote_host_lethal_tint.png`: the tint mask, same format as Classic's. Body / emblem (white on the body) / eyes (white on the head)
  by luminance and region; shade solved per texel so the Classic colours bake back close to the skin. **A = 0 = keep the skin's own
  colour**: the mouth (throat, lips, teeth, gum, tongue), the saliva (round 2; was green drool) and the odd saturated texel (the blue soles). `SymbioteSuitTint`
  bakes non-Classic colours from it and copies the A = 0 texels from the base, so the style recolours and the mouth stays flesh.
  Classic colours on this style use the base texture as painted.
- `symbiote_host_lethal_fx.png`: the sheen map (sweep from the body position as before, strength from the skin's highlights), no veins.
  `SymbioteSuitFx` keeps one `FxSet` per style (sheen / vein frames + eye mask); the Lethal one skips the vein pass.

### The mouth (`geo/symbiote_host_lethal.geo.json`, committed; `client/symbiote/SymbioteMouth`)

- The geo is the Classic rig (read from `symbiote_host.geo.json`) plus, under `armorHead`: `lp_upper` (gum strip + 11 upper teeth, two
  1.6 px fangs, 3 more round each corner of the grin; each tooth a base box + a narrower tip), `lp_jaw` (pivot `(0, 26.6, 0.2)`, back
  inside the head: a 9.48 x 1.78 x 3.96 shell over the bottom of the face, 10 lower teeth offset half a step to interlock, 3 per corner)
  and `lp_tongue_0..5` (child of the jaw, each segment hinged on the last; widths 2.0 -> 0.9, 7.4 px long in all).
  `SuperheroArmorModel#getModelResource` picks it for a Lethal Protector wearer.
- `SuperheroArmorRenderer#renderRecursively` hands every `lp_*` bone to `SymbioteMouth.applyBone` (set every frame: the bones are shared):
  jaw `rotX` (negative = open), upper teeth `posY` lift, tongue root `posZ = 4 - ext` (the chain slides out of the mouth) and each
  joint's pitch / yaw. The tongue is skipped while fully in; the whole mouth is skipped until the suit-up has covered the head (spread
  progress 0.93) and then grows in over the last 6%. (Round 2: no growing -- hidden until 0.88, then the jaw splits open, see below.)
- Animation (pure function of synced state + game time; per-player phase from the entity id):
  - idle: the jaw breathes 0.6..3.4 degrees on a 68-tick cycle; every 260 ticks (offset per player) a 90-tick **loll** -- jaw 13 degrees,
    the tongue slides out 4.4 px, droops more at every joint toward the tip, sways in a slow S and curls its tip now and then;
  - **snarl** (26 ticks: up in 4, held to 11, eased shut): a melee swing (x0.75), being hurt (x0.6) or any Symbiote move from the
    synced `SymbioteVitals.animId / animStart` (x1, Resurrect x1.15; Onslaught's charge holds it open while it charges): jaw to 31
    degrees with a tremble, upper lip lifts 0.28 px, tongue raised behind the teeth;
  - **lash** (20 ticks): landing a melee hit, or a tendril strike / grapple / grab / lunge: tongue out to 7 px in 4 ticks with a little
    overshoot, a travelling S-wave down the chain, the tip flicks up as it peaks, reeled back in by tick 20; jaw 24 degrees.
- **Keeping it in the mouth** (`SymbioteMouth.keepInMouth`, user: the tongue went "through the head and not out the mouth"): the tongue
  hangs off `lp_jaw`, whose hinge (y 26.6, z 0.2) is far back in the head, so opening the jaw tipped the whole chain out under the
  chin. Now a joint only bends once it is past the face plane (z -4.75, eased over 0.8 px), so it drapes over the lower teeth, and
  the root is re-aimed every frame at the middle of the mouth gap (lower teeth tips, which move with the jaw, to upper tips at y 26.5)
  from where the jaw has put it, clamped to -12..+25 degrees. Checked in a client from front, three-quarter and side in every pose
  (`docs/screenshots/v0175/symbiote_lethal/tongue_side_view.png`, `face_sheet.png`).
- Swings, hurts and hits are noticed in a client tick (`SymbioteMouth.tick`: a swing starting, the wearer's hurt flash, and a living
  thing in front of them flashing hurt within 6 ticks of the swing) and kept per **entity id** (no entity references; pruned after 200
  ticks unseen, cleared on disconnect). Drool: ~~`ITEM_SLIME` particles~~ round 2: `SymbioteParticles.SALIVA` (clear saliva drips) from the mouth -- 1 in 16 ticks while
  the tongue lolls, 1 in 6 during a lash, 1 in 320 otherwise; not in your own first person view.
- First person: unchanged (the head is never drawn in first person; the sleeve uses the style's texture and sheen). Item icons are the
  same for both styles. The Classic style has no mouth (the rig switch is what adds it).

### Tests

`SymbioteSuitStyleGameTests`: Classic by default (new hosts, the 3-colour constructor, null, old saves without the field, unknown
names, out-of-range ordinals); the style survives a save, a death and losing the bond; payload and attachment sync round-trip both
styles; the command sets the style and keeps the colours, and the colour commands / presets keep the style; both styles take colours,
presets match on either, each style has its own bake key, editing a colour keeps the style, the screen payload needs a bond.
`SymbioteSuitColourGameTests`' junk-packet test now writes the style ordinal too. Visual checks: `docs/screenshots/v0175/symbiote_lethal/`
(harness `scratchpad/SymbioteLethalHarness.v0175.java.txt`, films via `scratchpad/symbiote/films_lethal_v0175.js`).

## v0.17.5 round 2 -- no shoulder pads, no green, and a new suit-up (the suit emerges from the body)

User: "remove the shoulder pad models that you added. Remove the green from the mouth please. Change the suitup animation please, right
now as it is the player grows as the suit covers them, make it so that the suit comes out of the player and grows pixel by pixel ...
based on the comics and movies and shows ... i want a really cool looking suitup animation".

### Shoulder pads

- `sym_cap_r` / `sym_cap_l` (the caps and their six drips) are gone from `gen_symbiote_suit_v0175.js`, so from both geos
  (`symbiote_host.geo.json` and the Lethal one, which the Lethal script builds from it). The raised chest plate, raised quads and spine
  nodes stay (muscle / spine, not pads). The goo patch (24,0)-(40,8) the caps used now wraps the suit-up tendrils.
- The `sym_*` scaling (`SymbioteSuitFx.extraScale`) is deleted.

### No green on the mouth

- `make_lethal_protector.js` step 0: the skin's 22 slime-green drool texels (hat layer, round the grin and down the cheeks, half
  transparent) are repainted as glistening saliva (the dark skin beneath lifted toward a cool pale highlight, brightest at the top of each
  run, opaque) before anything else reads the skin, so the 3D jaw's front is clean too. They keep their own colour on any suit colour
  (tint mask A = 0). Checked: no texel with green over red/blue anywhere in the processed texture.
- The drool particle is now `SymbioteParticles.SALIVA` (main, client factory `SymbioteParticleClient`, sprites from
  `scratchpad/symbiote/gen_symbiote_particles_v0175.js`): a clear drop that beads at the lip, stretches on its strand, lets go and
  falls, and leaves a small clear splat.

### The suit-up (`client/symbiote/SymbioteEmerge`)

- **No growing while it spreads.** The host's +50% size (`Symbiote.tickGrowth`) now follows `Symbiote.surge(progress)`: 0 until
  `SURGE_FROM` = 0.86, then smooth-stepped to 1 -- the goo covers a normal-sized body, and only once the head is covered and the eyes
  are open does the host bulk up (in ~8 ticks). Taking it off runs the same curve backwards: deflate first, then recede. The clock is
  longer: `TRANSFORM_TICKS` 60 (3 s, was 2), `RETRACT_TICKS` 36 (1.8 s, was 1.2) -- for all hosts.
- **Timeline** (transform progress; take-off is the same backwards):
  - 0 .. 0.21 *burst*: seven tapered, wriggling 3D tendrils whip out of the sternum (env up by 0.035, held to 0.13, then they curl
    back down onto the body and shrink by 0.21); goo droplets fly (`SymbioteParticles.GOO`, the body colour). Off: the tendrils
    reach back out and are sucked into the chest, the droplets pulled in with them.
  - 0.035 .. 0.70 *spread*: the body and limbs, texel by texel.
  - 0.50 .. 0.83 *head*: up the neck, over the back / top first, the face last.
  - 0.855 .. 0.895 *eyes*: the lenses are dark goo ("lid" colour) until they open from the middle outward, white flash fading to the
    lens colour over 0.075 (on the way on only).
  - 0.86 .. 1 *surge* (server). 0.88: the Lethal Protector's mouth turns from shut goo to the real mouth; its jaw splits open
    (`SymbioteMouth` debut: shut until 4.5 ticks before the end, then easeOutBack to 38 deg with a tremble and the lip lifted, the
    tongue lolls out from 1 tick later, held, everything eased back to idle by 40 ticks after the end). Off: the jaw shuts between 0.98
    and 0.88, then the mouth is goo again.
- **The arrival field** (`SymbioteEmerge.Field`, built once per style from its tint mask, ~6.5k base-layer texels): every texel of
  the player-skin layout is placed on the body; base texels are graph nodes (neighbours within 0.75 px, across face edges, per part);
  Dijkstra from the middle of the chest (0, 19.2, -2) over a vein cost field (two octaves of ridged value noise in body space:
  cost 0.35 on the veins, 1.3 between), so the front races along branching veins and fills in between -- organic fingers. Limbs are
  seeded from the shoulders / hips (body distance + the hop), the head from the neck (its face texels cost 1.65x, so the face closes
  last). Body + limbs normalise into 0.035 .. 0.70, the head into 0.50 .. 0.83; the shell layer arrives 0.012 after its base texel;
  the spine nodes' goo patch with the middle of the back; the mouth atlas and the tendril patch are always drawn.
- **Per wearer per frame** (`Canvas`, keyed by entity id): two 128 px `DynamicTexture`s recomposed only when the progress changes
  (once a frame, ~16k texels of array maths, no allocation) from the wearer's baked colours:
  - covered texels = the suit; the newest (within 3.2 ticks) lerp toward the colour's wet gloss with a shimmer -- the glossy leading edge;
  - base texels within 2.8 ticks ahead of the front get dithered dark droplets (spatter ahead; left-behind residue on the way off);
  - the head shell is filled solid (base colour) where the goo has reached until 0.90, because the wearer's hat layer (0.5) sits above
    the suit's base (0.3); from 0.90 `PlayerModelMixin` hides the hat instead (`SymbioteEmerge.hidesHat`);
  - the RIM canvas: only base texels within 2 ticks of the front, lifted toward the gloss, drawn by `SymbioteSuitFx.Layer` as a second
    pass of the model with every face pushed out 0.16 px (`SymbioteEmerge.inflate`, read in `SuperheroArmorRenderer#createVerticesOfQuad`)
    -- a raised wet ridge riding the front.
- **Tendrils** (`SymbioteEmerge.renderStrands`, from `SuperheroArmorRenderer#renderCubesOfBone` in the bone's own pose): chains of
  seven tapered four-sided segments wrapped in the goo patch texels, wriggling (two sine waves per joint), drooping back toward the
  surface. The chest burst on the body bone; three reaching tendrils per limb (front, outer side, back) at the limb's current front
  height (precomputed `frontY` / `cov` tables), pointing down the limb while 6..94% of it is covered; four up over the head. Model px are
  mapped onto GeckoLib's baked bone space from where the base cube's faces actually are (found by their UVs), so axis flips are followed.
- **Sounds** (client, vanilla, once per beat per wearer): sculk charge at the burst, sculk vein at 0.30, honey slide as the head starts,
  warden tendril clicks as the eyes open, a warden heartbeat for the surge, the warden's agitated growl for the Lethal grin; off: sculk
  charge, honey slide, sculk vein. **Droplets**: the burst (one or two a tick), about one every other tick dripping off the front, a spray as the eyes open.
- A suit already settled off server side whose stripped pieces have not reached the client yet is not drawn (`SymbioteEmerge.strippedButWorn`) -- it flashed the whole suit for a frame at the end of the take-off.
- **First person**: `SymbioteSuitSleeve` uses the same canvas (the goo crawls down from the shoulder over the hand) plus the rim on a
  0.46-inflated arm.
- Only the Normal host's `symbiote_host` suit; the Black Suit and Agent Venom keep `SymbioteSpread`. Canvases are released after 3 s
  unused, on a resource reload (`SymbioteSuitTint.clear`) and on leaving a world; a released baked colour texture drops its pixels
  (`SymbioteEmerge.forget`). Client-only statics, no world / entity references (ids only).

### Tests

`SymbioteSuitUpGameTests`: `surge` is 0 up to `SURGE_FROM`, 1 at the end, monotonic in bounded 1% steps; the clock is 60 / 36 ticks;
with the transform clock set directly, the host stays at scale 1.0 through 85% of the suit-up, is bulking at 95%, is 1.5 once settled,
deflates as the retract starts and is back to 1.0 from 20% of it on. Visual checks: `docs/screenshots/v0175/symbiote_suitup/` (harness
`scratchpad/SymbioteSuitUpHarness.v0175.java.txt`, films via `scratchpad/symbiote/films_suitup_v0175.js`).
