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

## v0.17.5 round 3 -- Lethal Protector only, a random colour on bonding, no glint, coloured lips, the mouth clear of the eyes

User: "Remove all the classic suit styles from this power and just make it the lethal protector variants please. when player gets the
symbiote they get a random colour. Remove the enchantment glow or tint from the suit please", then "make the lip colour match the colour
of the symbiote please" and "the top of the mouth is kinda going into the eyes of the suit, please fix that".

### One suit: the Lethal Protector

- There is no style choice any more. `SymbioteColours` is back to three colours (no `style` field); its codec ignores unknown keys, so
  saves that still carry `"style": "classic"` (or `"lethal_protector"`, or junk) load as plain colours and show the Lethal Protector.
  The stream codec / `SymbioteColourPayload` carry only the three ints. `SymbioteSuitColours.setStyle` and the
  `/projecthero power tune symbiote style ...` command are gone (`colour <preset>` / `colour hex` stay).
- `SymbioteSuitStyle` survives only as **which art a build draws** (not saved, not a choice): `SymbioteSuitTint.styleOf(wearer)` /
  `artStyle()` = `LETHAL_PROTECTOR` when its textures are in the build, else `CLASSIC`. Every renderer that asked for the wearer's style
  (`SuperheroArmorModel` rig switch, `SymbioteMouth.wearsLethal`, `SymbioteEmerge` field, `SymbioteSuitFx` eye mask) asks this now.
- The screen is "Symbiote Suit Colours" again (button "Suit Colours" on Your Power (I)): the Classic / Lethal Protector row is gone,
  the panel is 22 px shorter, presets and Reset Colours set colours only. The `projecthero.symbiote.style.*` and `...style.missing`
  lang keys are deleted; the Guidebook bullet and the JEI page describe the one suit.

### The fallback rule (public builds)

The Lethal Protector textures are still made from a borrowed skin with no licence (`.gitignore`d; `node
scratchpad/symbiote/make_lethal_protector.js` re-makes them). **A build without them silently draws the mod's own original Classic art**
(`symbiote_host.png` + `symbiote_host_tint.png` on `geo/symbiote_host.geo.json`, no mouth) in the player's colours -- same
`SymbioteSuitTint.lethalAvailable()` check (once per resource reload), no message, nothing selectable. The Classic art is kept only for
that (and for the item icons' look). Ship the Lethal textures only once the skin's author has said yes.

- **Black Suit Spider-Man's Let It Take Over form** keeps using `symbiote_host.png` (the Classic art, always shipped): it is Spider-Man's
  suit, not the Normal host's, so it does not need the borrowed skin. **Agent Venom** keeps its own skin.

### A random colour on bonding

- `Symbiote.grant` (the one place a bond is made: wild / vial bonding, serums and `PowerGrants`, `/symbiote bond`, the Black Suit command)
  calls `SymbioteSuitColours.rollOnGrant` after it saves the bond. **70%** (`PRESET_CHANCE`): one of the seven presets at random;
  **30%**: `harmonious(rng)` -- one of three body kinds (deep dark body with a pale tinted or vivid near-complementary emblem; vivid
  mid-bright body with a near-black emblem; pale Anti-Venom-like body with a dark emblem), eyes that read against the body. The emblem
  always differs from the body by more than 0.25 in luminance (tested over 400 seeds).
- Drawn from the player's own RNG (`player.getRandom()`), so a test seeds it with `setSeed`. Only a fresh bond rolls: relog and death
  never pass through `grant` (the bond is saved / copied), granting while bonded is a no-op, and **re-bonding after losing the Symbiote
  rolls again**. Players already bonded when this ships keep their colours. The colour screen still changes it any time.

### No glint

- What read as an enchantment glow was `SymbioteSuitFx`'s **sheen** pass: a translucent white band sweeping up the whole suit every 110
  ticks (frames cross-faded) -- exactly a glint. Gone, with the **vein** pulse (a lifted body tint crawling up the Classic art's veins;
  the Lethal style never had it), both in third person and on the first-person sleeve. `SymbioteFxTypes.sheen` is deleted. The suit is
  now its painted, coloured texture only. Kept: the faint additive **eye glow** (lenses only, so the eyes read at night) and the suit-up's
  wet rim (only while the suit flows on / off). `symbiote_host_fx.png` stays in the assets only because the Lethal script reads it;
  `symbiote_host_lethal_fx.png` is still written but unused.
- The suit pieces never had item foil: `PowerEquipmentLock.bind` sets `ENCHANTMENT_GLINT_OVERRIDE = false` on the binding curse, and a
  plain stack has no enchantment. Now covered by a gametest (worn pieces and a plain stack: `hasFoil()` false).

### Lips in the suit colour (`make_lethal_protector.js`)

- The skin's lip texels (the lower-lip corners on the head, the lip band along the top of the 3D jaw's front) and the upper gum strip
  are no longer "keep" (tint A = 0) but body-channel tint texels with a lip shade (`lipOf`: 0.28..0.7 from the painted lip's luminance;
  the lighter ones a little gloss, the gum 0.3 / 0.4) -- so they are the body colour, slightly lifted or darkened, and read as lips on
  any colour. The rest of the jaw's outside (chin, sides, underside) now takes the suit colour too (it kept the skin's navy before, so a
  red suit had a navy chin). Throat, teeth, tongue, the floor of the mouth and the saliva keep their own colours.
- `SymbioteSuitTint` now always bakes the Lethal Protector from its mask, Classic colours included (the as-painted texture had pink lips).

### The mouth clear of the eyes

- The lenses reach down to y 27.0 at the sides (face rows 8-9); the mouth's top (`MOUTH_TOP`) was 27.55, so the upper gum and teeth sat
  on the bottom of the eyes. `MOUTH_TOP` is now **26.0** (the top of face row 12, `MOUTH_ROW`): the black of the painted open mouth
  between the lenses and the skin's painted top row of teeth (rows 8-11) are repainted as suit (`SUIT_REF`, a plain suit texel, lightly
  varied), both rows of teeth are **0.6x** as long (`UPPER_SCALE` / `LOWER_SCALE`: upper tips ~25.45, lower ~25.7, still interlocking
  in the smaller closed gap over the jaw's top at 25.1), the cheek teeth hang from 25.85, the gum strip is 0.3 tall with its top on 26.0.
  Even with the snarl's 0.28 px lip lift the gum stays under 26.3 -- a full pixel of suit (rows 10-11) always separates eyes and mouth.
- `SymbioteMouth.UPPER_TIP_Y` 26.5 -> **25.45** and `LOWER_TIP_Y` 26.15 -> **25.7**, so `keepInMouth` aims the tongue at the new
  middle of the gap.

### Tests

`SymbioteSuitStyleGameTests` (rewritten): old `style` saves (classic / lethal_protector / junk) load as plain colours and no style is
written; the payload is 12 bytes and both codecs round-trip; `/symbiote style` no longer parses while `colour` still works; random schemes
are deterministic per seed, ~70% presets, varied, customs contrast; a seeded fresh bond gets exactly the seeded scheme, relog / death /
a second grant keep it, re-bonding rolls again; no foil on worn pieces or a plain stack. `SymbioteSuitColourGameTests`: the attachment
default (never bonded) is Classic; the junk packet has no style ordinal. Visual checks: `docs/screenshots/v0175/symbiote_lethal_only/`
(harness `scratchpad/SymbioteLethalOnlyHarness.v0175.java.txt`: the same shots on the old and new code).

## v0.17.5 -- a Secondary power on top of any Primary

The Symbiote now stacks on any Primary power (no purge on bonding, a new Primary keeps it); with a Primary its moveset is only yours while
the suit is on, and Shift+H is the suit when the Primary uses plain H. Everything about it: `docs/SYMBIOTE_SECONDARY_REFERENCE.md`.

## v0.17.5 -- Moveset v2 (more attacks, nothing lost)

User: "Can we come up with a better moveset for symbiote please, the power needs more attacking moves while not losing what i can
already do". Design approved as `docs/SYMBIOTE_MOVESET_V2_DESIGN.md`; built exactly as listed there. Normal host only (the Lethal
Protector suit); Black Suit Spider-Man and Agent Venom keep their own kits. As a Secondary power the kit (old and new moves) is only the
player's while the suit is on when they also hold a Primary (`SymbioteAbilityManager.hasContext`, unchanged).

### Keys and numbers

| Key | Move | Numbers | Cooldown (list index) |
|---|---|---|---|
| R tap | Tendril Strike (kept) | 30 blocks, 15 dmg, knockback 0.9 | 1.5 s (0) |
| R tap, 3rd in a row | **Tendril Slam** | the 3rd strike that finds something, each within 4 s of the last (a miss resets): yanks the target to 2.2 blocks in front (only if the grab rules allow: not a boss, not a dummy, players per config), 22 dmg, Slowness II 2 s, 6 dmg + knockback to others within 2.5 blocks | 1.5 s (0) |
| Hold R | **Impaling Spear** | charges from the hold start, release to throw (auto-throws after 3 s); 10-block line, blocks stop it, pierces everything: 14 dmg at once, up to 26 after another 1 s; first victim pinned 1 s (held in place + Slowness X; not bosses) | 8 s (6), on the throw |
| Shift+R | Tendril Sweep (kept) | | 10 s (0) |
| G tap | Spike Shot (kept) | 14 + Wither V 5 s | 4 s (1) |
| Hold G | **Spike Rain** | aim point up to 24 blocks (ground under it), 4-block circle, 3 s; 2 spikes every 3 ticks from up to 11 blocks up (lower under a roof), one per volley falls on an enemy in the circle; 3 dmg + Wither I 2 s a spike, no knockback; spikes respect hit invulnerability (about one hit per target per 0.5 s) | 14 s (7) |
| Shift+G | Spike Fan (kept) | | 10 s (own) |
| X | Symbiote Lunge (kept) | | 2 s (2) |
| X in the air | **Dive Bomb** | needs 2.5+ blocks clear below (a hop still lunges), not in water / flying / riding; driven down at 2.4 b/t; landing: 4.5-block slam, 12 dmg + 0.8 per block fallen (max 24), thrown up and out (not bosses), ring of 10 ground tendrils; no fall damage | 6 s (8) |
| Shift+X | Grapple (kept) | | 3 s (own) |
| Z tap | Tendril Barrage / Blade Slash in Blade form (kept) | | 15 s (3) |
| Hold Z | **Ground Eruption** | 8 bursts, one a tick, every 1.5 blocks to 12 blocks ahead (flat aim); each hits within 1.7 blocks (+ half the target's width), 2.5 up/down: 12 dmg, Slowness II 3 s, launched up 1.1 (not bosses); each target once | 12 s (9) |
| Shift + hold Z | Onslaught (kept) | | 90 s (3) |
| C tap | **Maw Bite** (replaces the Thorns toggle) | nearest enemy in the aim or a ~41-degree cone within 5 blocks; short lunge toward it (also on a miss), 18 dmg, +12 Biomass on a hit | 5 s (10) |
| Hold C | **Devour** | enemy within 4.5 blocks, at or under 25% health, not a boss / player / dummy: the tongue (also drawn as a thin red tendril from the mouth) wraps it for 5 ticks, then reels it to the mouth by tick 14 (no gravity), then it dies (a killing blow from the host, `kill()` if that did not finish it); +40 Biomass, host heals 6 HP. A boss (200+ max health, or `TitanCombat.isBoss`) takes a **heavy bite**: 30 dmg, +20 Biomass. Anything else (above 25%, a player, nothing in reach, Devour cooling down) gets a Maw Bite | 20 s (11), devour and heavy bite |
| Shift+C | Tendril Grab, C again throws (kept) | | 5 s (5) |
| V | **Form** cycle Normal -> Blade -> Thorns -> Normal (replaces the separate Blade and Thorns toggles) | Blade and Thorns keep their old rules (+5 melee / 50% faster / 0.3 Biomass a second; Thorns IV reflection). A full main hand skips the Blade (Normal -> Thorns); a spent bar refuses; an old save with both on goes to Normal | none |
| Shift+V | Symbiote Shield (kept) | | toggle |
| passive | **Suit fists** | suit on (and the kit live): +1 `entity_interaction_range`; every 3rd punch in a row (each within 1.5 s) knocks back 1.4 + 0.3 lift (not bosses). Counted from Fabric's `AttackEntityCallback` (real swings only, not ability hits) | -- |

No new move costs Biomass (none of the existing attacks did either); Maw Bite, the heavy bite and Devour feed it. Balance against peers:
the heaviest new hits (Spear 26, Dive Bomb 24, heavy bite 30, the Devour execute on 20 s) stay under Onslaught's 20 + DoT + crowd control
on 90 s, and the frequent ones (Slam 22 every 3rd strike, Maw Bite 18 on 5 s) sit with Tendril Strike's 15 on 1.5 s.

### Tap vs hold

R, G, Z and C without Sneak: the press only records the game time (`KEY_DOWN`); a release before `HOLD_TICKS` (8 ticks, 0.4 s) runs the
tap move; still down at 8 ticks, `serverTick -> tickHolds` runs the hold move once and clears the press, so the release does nothing --
except for the Spear, whose release throws it. Sneak presses fire on the press as before (Shift+R/G/X/C/V, Shift + hold Z Onslaught; a
plain Z press let go while sneaking is still a tap). C while holding a grab throws on the press and clears the pending C so its release
cannot bite. Taps now fire on the release (at most 0.4 s later than before). X and V have no hold move and still fire on the press.

### State and cleanup

- Cooldowns: `SymbioteState.abilityCooldowns` grew from 6 to `COOLDOWN_SLOTS` = 12 entries (old saves read the new ones as ready; the
  list codec is unchanged).
- Static maps in `SymbioteAbilityManager` (`KEY_DOWN`, `STRIKE_CHAIN`, `SPEAR`, `PINNED`, `RAIN`, `DIVE`, `ERUPTION`, `DEVOUR`,
  `PUNCH_CHAIN`) hold only entity ids and numbers, are cleared per player by `clearFor` / `disrupt` (sonic) / losing the kit, and all at
  once in `clearSessionState` (from `ServerStateReset`). A half-eaten target gets its gravity back.
- New anim ids 16-24 (`SymbioteAnim`): TENDRIL_SLAM, SPEAR_CHARGE (held), SPEAR_THROW, SPIKE_RAIN, DIVE_BOMB (held), DIVE_IMPACT,
  ERUPTION, MAW_BITE, DEVOUR, each with a pose in `SymbiotePose`.

### Visuals

- Tendrils and spikes use the existing entities, so they wear the host's suit colours: `SymbioteTendrilEntity.fromPoint(Player, ...)`
  (new) is a fixed, ground-rooted tendril that carries the owner id for the tint only; `SymbioteSpikeEntity.rain(...)` (new) is a
  non-crimson spike owned by the host (its renderer tints from the owner) with its own damage / Wither / no knockback.
- `SymbioteMouth` (Lethal Protector): every new move snarls (the default); SPEAR_CHARGE and DIVE_BOMB hold the roar while they run;
  **Maw Bite** gapes to 40 degrees, snaps shut at ticks 3-5, then the tongue licks out (a lash 8 ticks in); **Devour** has its own
  30-tick animation (`devourJaw` / `devourTongue`): the jaw thrown to 42 degrees with a tremble, the tongue shot all the way out
  (7.3 px), its tip curling down round the prey, reeled in by tick 14, the jaws crash shut at 13-16, then it chews. The tongue is only 7 px long, so the server also lashes a thin crimson tendril from the mouth to the prey for the pull.
- HUD (`SymbioteHud`): V's box is lit while a Form (Blade / Thorns) or the shield is up, plus a "Form: Blade / Thorns" tag; C's box shows
  Maw Bite's cooldown (Shift: the grab's); each key's thin strip shows its hold move's cooldown (G: the larger of Spike Fan and Spike
  Rain; X: Dive Bomb); a held R / G / Z / C fills its box up to the hold threshold (R keeps filling with the Spear's charge), read from
  the local key. The Left-Alt list adds "Hold: ..." on R / G / Z / C and "In the air: Dive Bomb" on X.
- Guidebook / Your Power (I): the Moves table gains Tendril Slam, Dive Bomb, Maw Bite and Form (Blade / Thorns under it), a new "Hold
  moves" section with the four hold moves, and the "Suit fists" passive. JEI: the suit pieces' info page gets a moves summary.
- Lang: `scratchpad/symbiote/moveset_v2_lang_v0175.js` (key by key, re-runnable).

### Tests

`SymbioteMovesetV2GameTests`: tap fires on release and a hold fires only the hold move (no tap on the release); strikes 1-2 are
Tendril Strikes, the 3rd the Slam (harder, yanks the golem in), the 4th plain again; the Spear charges while R is held, pierces two
golems and pins the first, with no Tendril Strike on the release; Spike Rain hits a husk in the circle and stops after 3 s; grounded X
lunges, airborne X dives and the landing slams a husk; hold Z erupts a line through two golems, each hit once, with no Barrage; Maw Bite
bites, feeds 12 Biomass, uses its own cooldown and no longer toggles Thorns; Devour eats a husk at 20% (+40 Biomass); a healthy golem
gets a Maw Bite (Devour's cooldown untouched); a 400-HP husk at 10% is not devoured and takes the heavy bite; Shift+C grabs and C throws
without biting; V cycles Normal / Blade / Thorns, a full hand skips the Blade, Shift+V is the shield; the old moves (G / Z taps,
Shift+R / G / X, Shift + hold Z charge and early cancel) still fire; +1 reach and the 3rd-punch knockback with the suit on, neither with
it off.

Visual checks: `docs/screenshots/v0175/symbiote_moveset2/` -- a still per move (01-23: HUD, Left-Alt list, Tendril Strike / Slam,
Spear charge / throw, Spike Rain, Dive Bomb, Ground Eruption, Maw Bite gape / snap / lick, Devour tongue / reel / jaws / chew, the three
Forms), `symbiote_moveset_v2_all_moves.mp4` + `_sheet.png` (every move in sequence on husks and iron golems, the real keys held and
let go so the HUD fills) and `symbiote_maw_bite_devour_closeup.mp4` + `_sheet.png`. Harness
`scratchpad/SymbioteMovesetHarness.v0175.java.txt` (+ `SymbioteMovesetHarnessCameraMixin.v0175.java.txt` for the fixed camera offsets),
films via `scratchpad/symbiote/films_moveset_v0175.js`.

## v0.17.5 -- tendrils instead of particles, exact colours, the soles, separate Shift cooldowns

User: "make the bottom of the symbiote suit feet not blue, make it the same colour as the suit itself", "instead of spawning in particles
for the symbiote powers can you make it the tentacle models and vfx ... when player uses symbiote lunge ... symbiote tendril come off the
players legs and push off the ground launching them", "for symbiote customization you cant get the exact colours your want with the eyes
and emblem ... make sure the symbiote suit is fully customizable", and "if i use shift+R on symbiote i cant use regular R while Shift+R is on
cooldown". Normal host (Lethal Protector suit) only. No damage, cooldown length or targeting changed.

### The soles

The borrowed skin paints the bottom faces of both legs flat blue (40, 94, 175); `make_lethal_protector.js` left every saturated texel as
painted (tint mask A = 0), so they stayed blue on every colour. The script now marks the leg bottom faces (base and trouser layers:
texels (16..24, 32..40), (16..24, 64..72), (48..56, 96..104), (16..24, 96..104)) as body channel at shade 112 (a touch under the exact
colour, an underside) and repaints the wearable texture's soles to the Classic bake. Re-run the script after a merge (the textures stay
gitignored). Shots: `feet_below_*` (six colours, from under the hovering host).

### Exact colours (`SymbioteTintMath`, common; `SymbioteSuitTint.bake` delegates)

- **Why black was impossible:** the emblem and eye texels sit at mask shade 255 (the emblem with gloss 94). Above shade 0.5 the bake lifted
  toward a pale tint of the hue (a cool white for black: ~0.78 grey) and the gloss laid a near-white on top -- so a black emblem or black
  eyes came out light grey.
- **Now:** shade 128 is the exact colour (was 0.5, which 8 bits cannot hit -- 128 baked black to #010101). Body and mouth keep the lift and
  wet gloss (muscle shading, highlights on a black suit). **Emblem and eyes are never lifted** above the picked colour, and their gloss only
  brightens by `c + (1 - c) * 0.3 * max(c)` -- zero for black. Black is #000000, white #FFFFFF, any RGB in between as picked.
- **Eye glow:** `SymbioteSuitFx.eyeGlow` is scaled by `SymbioteTintMath.eyeGlowStrength` (40% for bright eyes, smoothstepped to none below
  ~35% brightness) and the pass is skipped when it adds nothing, so dark eyes are not brightened.
- **A fourth colour, the mouth:** `SymbioteColours(primary, secondary, eyes, mouth)`; the 3-int constructor (presets, the random scheme, the
  colour command) uses `NATURAL_MOUTH` (0xB03A52, the painted tongue red), so nobody's look changes. Codec field `"mouth"` (optional, old saves
  read natural), stream codec / payload 16 bytes. `key()` only appends the mouth when it is not natural (existing bake keys unchanged).
  `sameColours` / preset matching / `hasClassicColours` compare body, emblem and eyes; `setColours` / `setPreset` keep the player's mouth.
  The mask's mouth flesh (throat, tongue, the floor of the mouth) is channel B = 32 (16..63 = mouth), shade solved against NATURAL_MOUTH;
  teeth and saliva stay A = 0. The Classic fallback art has no mouth, so the mouth colour only shows on the Lethal Protector.
- `gen_symbiote_suit_v0175.js`'s `bakePixel` twin follows (shipped Classic textures not re-baked: white emblem / eyes bake the same).

### The colour screen

Four parts (Body, Emblem, Eyes, Mouth, each with a wrapped tooltip). The bars switch between Hue / Saturation / Brightness and Red / Green /
Blue (0-255) with the RGB / HSB button beside the hex field (remembered while the game runs); each bar shows its exact value; the hovered bar
nudges one step with the mouse wheel or the left / right arrows; two chips set pure black / pure white for the current part. Presets set body
/ emblem / eyes and keep the mouth. Lang: `scratchpad/symbiote/lang_symbiote_tendril_vfx_v0175.js` (key by key). Shots: `screen_*`.

### Tendrils instead of particles (`SymbioteVfx` server, `SymbioteVfxPayload` S2C, `client/symbiote/SymbioteVfxClient`)

The server sends one small payload per effect to the host and everyone tracking them (96 blocks); every client draws it as living tendrils
(`GooMesh` tubes, the tendril texture tinted to the host's body colour by `SymbioteSuitTint.goo`) in world space after the entities,
re-anchored every frame on the host's legs / hands / body and on a held target. Held states need no packets -- they are drawn from the
synced `SymbioteState` / `SymbioteVitals` of every player in view. The only particles left on the kit are 2-3 goo droplets
(`SymbioteParticles.GOO`, body colour) as accents on impacts / bursts, and a little block dust where Ground Eruption breaks the ground.

| Move | Was | Now |
|---|---|---|
| R Tendril Strike | ink + crits on a hit, ink on a miss | IMPACT: tendrils splay out of the wound (clinging to the target), a small splat where a miss lands |
| R x3 Tendril Slam | ichor ring, ink, explosion particle | WRAP on the target while it is yanked + GROUND_BURST where it lands |
| Shift+R Sweep | ink per target | IMPACT per target |
| Hold R Impaling Spear | ichor spiral + sheen round the fist | held: tendrils spiral down the arm into a spear growing from the fist (barbs at full charge); IMPACT per pierced target |
| G Spike Shot / Shift+G Fan | squid-ink trail, ink + crits on hit, ink on blocks | a writhing goo strand trails each spike (renderer); IMPACT on hit, small IMPACT on a block (Carnage's crimson spikes unchanged) |
| Hold G Spike Rain | ichor ring every 5 ticks | GROUND_MARK: tendrils writhe up round the circle for the 3 s |
| X Lunge | ink + poof, an ink trail, ink on the ram | LEG_PUSH: two tendrils per leg (calf, thigh) stab down, plant on the ground where the host stood with three splayed toes, stay planted and stretch as the host flies off, then let go and whip back; IMPACT on the ram |
| X air Dive Bomb | ink at the start and every tick | held: five tendrils stream off the shoulders like a wake, two claws reach down from the feet; landing GROUND_BURST (the ring of ground tendrils was already there) |
| Shift+X Grapple | (tendril only) | + a small IMPACT where the tip latches |
| Z Barrage | ink per hit | IMPACT (small) per hit |
| Z Blade Slash | vanilla sweep particles, ink per target | SLASH: a living blade-crescent sweeps right to left; IMPACT per target |
| Hold Z Ground Eruption | ink + ichor per burst | a small GROUND_BURST round each burst (the eruption tendrils were already entities) |
| Shift + hold Z Onslaught | ichor ring while charging; ichor / sheen rings, sonic boom, sculk souls, 80 ink; ink on victims for 8 s | held charge: tendrils from the legs anchor in the ground and a ring of claws rises and closes in; release: two GROUND_BURSTs (9 and 4.5 blocks), a BODY_BURST, IMPACT + WRAP (3 s) on every victim |
| C Maw Bite / heavy bite | crits, damage indicators, ink | JAWS: two jaw-tendrils from the shoulders gape round the prey and snap shut, fangs inward; IMPACT on a hit |
| Hold C Devour | ink while reeling; ink, sheen, damage indicators on the kill | WRAP for the pull (with the tongue tendril), JAWS on the kill |
| Shift+C Grab / C throw | ink at the seize and every 3 ticks; ink on the throw | WRAP for the whole hold; IMPACT on the throw |
| V Form | ink on Thorns | BODY_BURST on Blade and Thorns |
| Shift+V Shield | ink burst; an ink ring every other tick | BODY_BURST; held: a woven disc of tendrils (rim, inner ring, spokes) in front of the host, grown from both forearms |
| suit fists (3rd punch), blade bite, thorns reflect, arrow catch, Biomass hit | ink | IMPACT |
| bonding, bond complete, cloak, wall climb, suit collapse, hazard / sonic recoil, resurrection | ink / portal / flash / smoke / sonic boom | WRAP on the host, BODY_BURST, WALL_GRIP (tendrils from the hands to the wall), GROUND_BURST |

The suit-up already was tendrils + goo droplets (`SymbioteEmerge`) and is unchanged. The normal host has no Let It Take Over / Frenzy (those
belong to Black Suit Spider-Man, whose kit, like Agent Venom's and the pets', is untouched).

### Every Shift / hold variant on its own cooldown

`SymbioteState.COOLDOWN_SLOTS` 12 -> 15 (old saves read the new entries as ready): 12 Tendril Sweep (Shift+R; was R's entry 0), 13 Onslaught
(Shift + hold Z; was Z's entry 3), 14 Grapple (Shift+X; was an unsynced static map, now synced). Already separate: Shift+G Spike Fan
(`spikeConeReadyAt`), Shift+C Grab (5, while C's tap Maw Bite is 10), every hold move (6-9, 11). Still shared on purpose: Barrage and Blade
Slash (both Z's tap, the Form decides which -- a Form swap must not hand out a fresh Z) and Devour's Maw Bite fallback (the same bite). HUD:
R / X / Z follow the existing G / C rule -- the box shows the plain move's cooldown, the Shift move's while Shift is held, and the thin strip
carries the other.

### Tests

`SymbioteExactColourGameTests` (black / white emblem, eyes, body and mouth bake exactly; any RGB at the top shade comes out as picked; the eye
glow never brightens dark eyes; pure black / white survive the payload, the save and the sync; the mouth channel, old saves, keys, presets keep
the mouth), `SymbioteTendrilVfxGameTests` (the Lunge sends LEG_PUSH planted at the host's feet with the launch direction; strike / Maw Bite /
grab / shield send IMPACT / JAWS / WRAP / BODY_BURST; the payload round-trips and clamps), `SymbioteMovesetV2GameTests.shiftVariantsNeverBlockThePlainMove`
(R fires while Shift+R cools down and the other way round, Barrage while Onslaught cools down, Grapple vs Lunge, 15 entries); the payload
size and junk-packet tests updated for the mouth. Visual checks: `docs/screenshots/v0175/symbiote_tendril_vfx/` -- stills per move (`move_*`),
soles (`feet_below_*`), faces in extreme colours with the mouth open (`face_*`; the baked textures were checked but are not committed -- they derive from the held-back skin), the screen (`screen_*`),
the HUD with / without Shift (`hud_*`), `symbiote_tendril_vfx_all_moves.mp4` + `_sheet.png`, the Lunge close-ups
`symbiote_lunge_leg_tendrils_closeup.mp4` / `_from_behind.mp4` (quarter speed) + sheets. Harness `scratchpad/SymbioteVfxHarness.v0175.java.txt`
(+ `SymbioteVfxHarnessCameraMixin.v0175.java.txt`), films via `scratchpad/symbiote/films_tendril_vfx_v0175.js`.


## v0.17.5 -- weapons (Blade, Axe, Mace, Whip), Symbiote Wings, slim arms

User: "improve the symbiote blade model ... create an actual blade model and create other weapons like axes and stuff ... an axe model
and another one, maybe a mace ... also i want the symbiote to be able to fly for a short amount of time by creating giant symbiote wings",
and "if the players model is a thin arm player model make their symbiote form also have thin arms". Normal host (Lethal Protector suit)
only. Reference images: `docs/reference/symbiote_weapons/` (12-21: Carnage clawed arms, the Venom-film crescent scythe arm-blades with
lattice holes, a comic blade, bat-like symbiote wings).

### The Form cycle (V)

Normal -> **Blade -> Axe -> Mace -> Whip** -> Thorns -> Normal (`SymbioteVitalsManager.FORM_CYCLE`; was Normal -> Blade -> Thorns). The four
weapons are one state: `SymbioteVitals.bladeActive` (a weapon is out -- the empty-hand rule, the 0.3 Biomass/s drain, `keepBladeHandEmpty`
and the sound-attack sheathe all cover every weapon, unchanged) plus `SymbioteVitals.weapon` (`SymbioteWeapon` ordinal). A full hand skips
every weapon (Normal -> Thorns, "Your hand is full: no weapon -- Thorns form"); a spent bar refuses from Normal. `formSince` / `prevForm`
(synced) stamp each change so every viewer plays the growth and the melt. HUD: the "Form: ..." tag names the weapon; the action bar says
it on every switch. Shift+V is still the Shield. Old saves read `weapon` 0 (Blade).

### Numbers (`SymbioteWeapon`, `SymbioteWeaponMoves`)

| Weapon | Punch damage | Attack speed | Extra reach | On a punch | Z move (lands at) | Z numbers | Z cooldown |
|---|---|---|---|---|---|---|---|
| Blade | +5 | +50% | +0.5 | 2-damage armour-bypassing bite (as before) | Blade Slash (at once) | 8 + 2, 5-block cone, knockback 0.6 (unchanged) | 15 s |
| Axe | +8 | -25% | 0 | knocks a raised player shield away (`disableShield`, attack callback) | Cleave (tick 4) | 16 + 4 armour-bypassing on a 4.5-block line ahead, 1.3 either side; raised shields knocked away first; small downward knock | 14 s |
| Mace | +6 | -15% | 0 | knockback 0.8 (not bosses) | Ground Smash (tick 6) | 14 to everything within 4 blocks of a point 2.2 ahead, thrown out 1.2 + up 0.5 (not bosses), Slowness II 2 s | 16 s |
| Whip | +3 | +10% | +2.5 | -- | Lash (tick 3) | 12 to the first living thing within 7 blocks of the aim (blocks stop it), Slowness I 1 s, yanked toward a point 2 blocks in front (not bosses / dummies / grab-rule refusals) | 10 s |

Reach stacks with the suit's own +1. Every Z shares **Z's tap entry (3)** with Barrage, each setting its own length -- swapping weapon never
hands out a fresh Z (the existing intentional rule). Swings pose at once (`SymbioteAnim` 25 AXE_CLEAVE / 26 MACE_SMASH / 27 WHIP_LASH) and land
from `SymbioteWeaponMoves.tick` (static `PENDING`: player id -> {weapon, tick}; cleared by `clearFor` / `disrupt` / `clearSessionState`); a
weapon melted back mid-swing cancels it. Balance: the frequent hits sit with Tendril Strike / Maw Bite (12-18), the slow ones under the Spear
(26) and Onslaught.

### The models (`client/symbiote/SymbioteWeaponRenderer`)

Procedural living meshes (the `GooMesh` technique of the tendrils), drawn in the arm bone's space on the **main arm** (the right by default --
the arm that swings; the other stays the clawed suit hand), third person for every viewer (`SymbioteBladeRenderer.Layer`) and first person
(`PlayerRendererSymbioteBladeMixin`, drawn at 85%). Textures: `textures/entity/symbiote_weapon.png` (wet goo with a vein network, tinted to the
body colour by `SymbioteSuitTint.goo`) and `symbiote_weapon_edge.png` (pale; vertex-coloured with the emblem colour mixed 60% into the body
colour) -- both painted by `scratchpad/symbiote/gen_symbiote_weapons_v0175.js`.

- **Blade**: the film's crescent scythe -- a lens-section plate ~1.6 blocks round its arc (centre 0.5 down / 0.45 ahead of the shoulder,
  radius 0.56, 102 -> -74 degrees), rooted on the outer forearm, sweeping down past the fist and hooking forward to a point. The half by the
  arm is an organic lattice (ten elliptical holes with walled rims, three tendril strands running through it); the outer edge band is the
  pale cutting edge; four spines rake back up the arm.
- **Axe**: a 0.66-long tendril haft over the fist (two helix strands, a knot), a heavy bearded bit (0.56 deep, flaring to 0.8 tall, convex
  edge, edge band pale), four lattice holes by the haft, veins across both faces, a spine spike behind and a smaller one under it.
- **Mace**: a thick tendril cord (three wrapping strands) to a lumpy ball (radius 0.23) with sixteen conical spikes with pale tips.
- **Whip**: a barbed tendril coiled twice round the forearm, a 1.55-long swaying tail with alternating barbs and a three-barbed pale head;
  Lash curls it back over the shoulder, cracks it out to 7 blocks down the aim (the pose aims the arm) and recoils.
- Every weapon: three binding tendrils spiral round the forearm first; then the shape fills in from the root while three thin tendrils race
  ahead to the growth front (6 ticks); the old weapon melts back the same way at the same time.

### Symbiote Wings (`SymbioteWings`, `SymbioteWingsPayload`, `client/symbiote/SymbioteWingsClient` / `SymbioteWingsRenderer`)

| | |
|---|---|
| Start | **double-tap Space in the air** (the mod's usual 7-tick window, `ProjectHeroModClient.handleDoubleJump`; while the suit owns the keys over a Primary, `handleSymbioteDoubleJump`). Suit on, Normal host with the kit, not on the ground / in water or lava / riding / on an elytra / in creative flight. No new key; H and N untouched. |
| Cost | 10 Biomass; cooldown entry **15** (`COOLDOWN_SLOTS` 15 -> 16), 20 s from take-off; a new pair can't grow until the last has dissolved |
| Grow | 8 ticks: bones reach out from between the shoulder blades, then the membrane fills in from the wrist outward |
| Fly | up to 6 s (120 ticks): toward the look at 0.72 b/t, a strong lift on the first beat, a small one each beat (every 10 ticks, with a flap sound), Space +0.38 rise, Shift -0.6 dive (client-driven, `START_CLIENT_TICK`, like every mod flight) |
| Glide | when the time is up, until landing: forward speed kept (0.45-0.8 b/t along the look), sinks ~0.14 b/t, Shift dives |
| End | touching the ground (after 3 ticks), water / lava, a mount, the suit coming off or losing the kit: the wings dissolve back in over 12 ticks (membrane first, then bones). No fall damage (a bonded host never takes any; the server also resets the fall distance while the wings are out). The server's "flying" kick limit is lifted while airborne on the wings (`UndeadFlightKickMixin`). |
| HUD | a tag: "Wings 4.2s" while flying, "Gliding", "Wings 12s" while cooling; the X box's thin strip carries the larger of Dive Bomb's, Grapple's and the wings' cooldowns. Left-Alt list: X's line adds "Space x2: Wings". |

State: `SymbioteVitals.wingsStart` / `wingsLanded` (synced, persistent; cleared by `clearTransient` on death / relog / dimension change). No
static collections. The look: two bat wings in the body bone's frame, mounted outside a worn backpack (`BackpackLayer.backClearance(p, 2)`):
upper arm and forearm (thick tendrils) to a wrist, four finger bones fanning out (up-out, out, down-out, down) plus the hip line; membrane
panels between them (`symbiote_wing_membrane.png`: pale fill vertex-coloured `0.78 x mix(body, emblem, 0.3)`, dark web veins, a ragged
scalloped trailing edge and tears), billowing back and rippling; leading-edge spikes and a top claw in the edge colour. Span ~4.6 blocks at
scale 1. Flap: the whole wing rotates about its root (+-0.55 rad, 1.5x on the first beats), fingers spread on the down-stroke and fold on the
up-stroke; glide: a slight dihedral with a flutter. Pose (`SymbiotePose`): arms swept back and out, legs trailing.

### Slim (3 px) arms

A wearer with the slim skin model gets slim suit arms: `SuperheroArmorRenderer#renderCube` narrows each `armorRightArm` / `armorLeftArm` box of
the Normal host's suit by one pixel on its outer side, keeping the inner edge (where vanilla's slim arm keeps it) and every texel -- the 4 px
faces are squeezed into 3 px rather than cropped, so the colour bake, the suit-up spread and rim, the FX passes and the Lethal Protector art all
line up with no new textures (the held-back textures need no slim bake). First person: `SymbioteSuitSleeve` squeezes the same boxes about the
base box's inflated inner edge (4.6 -> 3.6 px, keeping the 0.3 px clearance over the slim skin's sleeve layer). The weapons sit on the slim
arm's centre line (half a pixel in); the hand anchor of the tendril VFX (`handOf`, 5 px out, the arm pivot) already lies inside both arm widths.

### Tests

`SymbioteWeaponWingsGameTests`: V cycles Normal -> Blade -> Axe -> Mace -> Whip -> Thorns -> Normal with each weapon's damage and reach on the
attributes and the change stamped; a full hand skips the weapons; Blade Slash cuts and cools entry 3 for 15 s; Cleave poses, lands 4 ticks later
on the golem in line and not on one off to the side; Ground Smash hits and slows both husks in the ring; Lash hits a golem 5 blocks off and yanks
it in; a weapon swap (and Normal's Barrage) is refused while another weapon's Z cools; the wings refuse on the ground, grow in the air (10
Biomass, entry 15 for 20 s, nothing else touched, no second pair), fly 6 s then glide, dissolve on landing and are gone 12 ticks later, still
cooling; under 10 Biomass / cooling down refused; the suit coming off dissolves them. `SymbioteMovesetV2GameTests`: the V cycle and the cooldown
list size (16) updated.

### Filming pass (fixes found on screen)

- **Crash fix** (`454ee663f`): the weapon and wing renderers held two `VertexConsumer`s from one `MultiBufferSource`; writing to the first after
  fetching the second ended its batch ("Not building!", the client crashed the moment a weapon was drawn). Each render type's buffer is now
  fetched right before writing; the plates draw their body cells and edge band in two passes.
- **Weapon size**: at 1x the weapons read as a small claw on the 2.7-block host. Third person now draws them 1.5x about the fist (the Mace
  1.25x so its ball clears the ground); first person keeps 0.85x. To fit: the Blade's circle is 0.5 (centre 0.43 down / 0.42 ahead), the Axe
  sits 0.17 higher, the Mace's cord is shorter (ball 1.06 down), and the Whip's rest tail hangs as a loop from the fist (a straight 1.55 tail
  went through the ground); the Lash still reaches its 7 blocks (divided by the draw scale).
- **Lash yank**: 0.32 lift threw an iron golem about 3 blocks up; now 0.22 lift, 0.19 x the distance horizontally.

Shots: `docs/screenshots/v0175/symbiote_weapons_wings/` -- `weapon_<blade|axe|mace|whip>_<classic|carnage|blue_white>_<1_front|2_right|3_back|4_left|5_first_person>`,
`arms_<wide|slim>_<classic|carnage>_*` + `wide_vs_slim_*` (side by side, wide left), `arms_slim_weapon_blade_*`, `cycle_*`, `z_*`, `wings_<behind|side>_*`,
`wings_backpack_*`; films `symbiote_weapon_cycle_growth_melt`, `symbiote_weapon_z_<blade_slash|cleave|ground_smash|lash>` (half speed),
`symbiote_wings_flight_<behind|side>` (`.mp4` + `_sheet.png`). Harness `scratchpad/SymWWHarness.v0175.java.txt` (+ `SymWWHarnessMixin` camera,
`SymWWHarnessSkinMixin` forced slim / wide skin), films via `scratchpad/symbiote/films_weapons_wings_v0175.js <harness out dir>`.

## v0.17.5 -- Moveset v3 (Form wheel, Thorns aura + Spike Surge, Spike Nova, the grab without a limit, faster cooldowns)

User: "I want to redo the moveset now that we have so many moves -- V - holding V for 0.5 seconds open a weapon wheel where you can choose
what form you want to be in, make the thorns form hurt things that get close to you aswell and make the z move for this to make the spikes
temporarily longer so that the range increases please. Shift+V - keep this the shield move. R - Keep this as tendril strike, lower cooldown to
1 second. Shift+R - lower cooldown of tendril sweep to 4 seconds. make shift+G an aoe ability please. lower cooldown of g to 1 second. lower
cooldown of z move. Remove time limit of Shift+C grab move, player press C to throw enemy, left clicks to crush them and presses shift+C to
let them down gently, works on players and squad mates too". Normal host (Lethal Protector suit) only; Black Suit Spider-Man and Agent
Venom keep their own kits. Shift + hold Z (the ultimate) is being replaced on a parallel branch and was not touched here.

### Keys and numbers (only what changed)

| Key | Move | Numbers | Cooldown (list index) |
|---|---|---|---|
| R tap | Tendril Strike | unchanged | **1 s** (0; was 1.5 s) |
| Shift+R | Tendril Sweep | unchanged | **4 s** (12; was 10 s) |
| G tap | Spike Shot | unchanged | **1 s** (1; was 4 s) |
| Shift+G | **Spike Nova** (replaces the Spike Fan) | 16 spikes fly out flat in a ring from the chest (the look only: they pass through creatures and splat on blocks, ~7 blocks) + 12 ground tendrils lashing out + a 6-block GROUND_BURST; everything within 6 blocks (flat, + its half width; 3 up / down) takes **10**, **Wither II 3 s**, knockback 1.0 + 0.35 lift (not bosses) | **8 s** (`spikeConeReadyAt`, the Fan's own entry) |
| Z tap | Tendril Barrage | unchanged | **8 s** (3; was 15 s) |
| Z tap, a weapon out | Blade Slash / Cleave / Ground Smash / Lash | unchanged | **8 s** each (3; were 15 / 14 / 16 / 10 s) |
| Z tap, Thorns | **Spike Surge** | the body spikes grow 2.5x longer for 6 s (grow out over 4 ticks, draw back over the last 8); the aura reaches **3.5** blocks and hits for **5** | **10 s** (**16**, new) |
| Hold Z | Ground Eruption | unchanged | **8 s** (9; was 12 s) |
| V tap | Form cycle | as before (Normal -> Blade -> Axe -> Mace -> Whip -> Thorns), now on the release under 10 ticks | none |
| Hold V (10 ticks) | **Form wheel** | a radial wheel of the six Forms (client screen); point + let go of V or click; the centre keeps the current Form; Escape cancels; does not pause | none |
| Shift+C | Tendril Grab | **no time limit**; players and squadmates can be grabbed (PvP or not); still no bosses / Target Dummy / armour stands / spectators / creative players | 5 s (5), started by a throw or a set-down |
| C while holding | throw | as before (7 damage, not on a squadmate / PvP-protected player) | -- |
| Left click while holding | **crush** | **6** damage, at most one per **10** ticks; the coils squeeze (WRAP 12 ticks), the holding arm clenches (`GRAB_CRUSH` pose), the jaws snarl. A squadmate or PvP-protected player: no damage, action-bar hint | -- |
| Shift+C while holding | **set down** | put on the ground just in front (at the host's feet if there is no room), no damage, no fall damage | -- |
| passive, Thorns form | **Thorns aura** | every 15 ticks: everything within **1.5** blocks (flat gap from the host's side to the target's side, with the bodies overlapping in height) takes **3**, Wither I 2 s, knockback 0.4 (0.55 surging; not bosses). Not squadmates, own / squadmates' pets, players with PvP off (`enemiesAround`); bosses take it. The Thorns IV reflection is unchanged | -- |

Every Shift / hold variant still has its own cooldown: Spike Nova keeps the Fan's `spikeConeReadyAt`, Spike Surge is entry 16 (so it never
blocks Barrage or a weapon's Z, which share entry 3), the crush has only its own 10-tick rate limit.

### How it works

- **Cooldown list**: `SymbioteState.COOLDOWN_SLOTS` 16 -> **18** (16 = Spike Surge; 17 is reserved for the parallel branch's Shift+Z
  ultimate). Old saves read the new entries as ready.
- **Spike Surge's clock** is its cooldown: `SymbioteAbilityManager.surgeAge(state, now)` = now - (entry 16 - 200) while under 120 ticks.
  Every client reads it off the synced state (`SymbioteBodySpikes.surgeScale` lengthens each spike, roots 35% of the extra thicker), so
  there is no new field or packet. Leaving Thorns ends the surge's effect (aura and look).
- **V tap vs hold**: V without Sneak joins R / G / Z / C in `KEY_DOWN` (`waitsForRelease`, `holdTicks` = `FORM_WHEEL_TICKS` 10 for V,
  `HOLD_TICKS` 8 for the rest). Released under 10 ticks: `cycleForm`. The client (`client/symbiote/SymbioteKitClient.tickWheelHold`,
  called from `ProjectHeroModClient` next to the Green Lantern wheel hold) opens `SymbioteFormWheelScreen` at 10 ticks, sends
  `SymbioteKitPayload.WHEEL_OPENED` (the server drops the pending V) and never sends V's release (the "screen opened" cleanup skips V,
  like C for the construct wheel); V is then ignored until it is physically let go. Shift+V is still the Shield on the press.
- **The wheel** (`SymbioteFormWheelScreen`): Beast Boy's Animal Wheel geometry (`PowerWheelScreen.band / sector / side`) in the Symbiote's
  purple, the Forms in cycle order clockwise from the top, each slice a 9 x 9 drawn glyph (head / crescent / axe / spiked ball / curl /
  spike burst) over the name (scaled to fit the slice); the centre names the hovered Form; under the wheel the hovered Form's one-line
  description, wrapped with `TooltipWrap.wrap`, then the controls hint. A weapon slice is red with an item in the main hand. The pick goes
  as `SymbioteKitPayload.PICK_FORM` -> `SymbioteAbilityManager.pickForm` -> `SymbioteVitalsManager.selectForm`, which applies the cycle's
  rules but refuses a weapon with a full hand ("Empty your hand to grow a weapon") instead of skipping to Thorns. `cycleForm` and
  `selectForm` share `applyForm` (a weapon from Thorns drops the spikes first).
- **The grab** (`SymbioteAbilityManager` "Tendril Grab"): static `GRAB` (player id -> the held `LivingEntity`, whether the suit was on, last
  crush / wrap tick) and `SAFE_LANDING` (entity id -> tick); both cleared per player and in `clearSessionState` (ServerStateReset). The hold
  re-tops the tendril (`SymbioteTendrilEntity.keepAlive`) and the WRAP coils every 60 ticks; the pose holds the grab frame for as long as
  `tendrilGrabHeld` (`SymbiotePose.apply`). Released quietly (gravity back, no fall damage) when the host dies / relogs / changes dimension
  (`clearFor`, which used to leave the target floating with no gravity), takes the suit off (if it was on for the grab), is sonic-disrupted,
  loses the kit, or the target dies, changes level or is a boss. The crush comes from the client (`SymbioteKitClient.startTick`: holding a
  grab, attack-key clicks are swallowed -- no punch, no block breaking -- and sent as `SymbioteKitPayload.CRUSH`, the arm swings); as a
  backstop the server's `AttackEntityCallback` turns any melee swing while holding into the crush and cancels the punch. Fall damage on a
  `SAFE_LANDING` entity is negated by an `ALLOW_DAMAGE` hook (a set-down target; a thrown or released player; anything released quietly).
- **Spike Nova** spikes: `SymbioteSpikeEntity.nova` (visual-only, 1.9 b/t, life = ceil(6 / 1.9) = 4 ticks); `SymbioteBodySpikes.window`
  raises the body spikes for 30 ticks on `SPIKE_NOVA` and 36 on `SPIKE_SURGE`.
- New anim ids 32-35 (`SPIKE_SURGE`, `SPIKE_NOVA`, `GRAB_CRUSH`, `GRAB_SET_DOWN`; 28-31 left free for the parallel branch), each with a pose.
  The Lethal Protector mouth snarls on all of them (the default for any move).
- **HUD**: G's Shift box / thin strip = Spike Nova's 8 s; in Thorns form Z's box shows Spike Surge's cooldown and lights while it runs
  ("Surge 4.2s" tag); hold V fills the V box up to the wheel; holding a grab adds three tags ("C: Throw", "Click: Crush", "Shift+C: Set down")
  and lights C. Left-Alt list: Shift+G "Spike Nova", V "Hold: Form Wheel", Z "Hold: Ground Eruption; a weapon: its Z; Thorns: Spike Surge".
- **Guidebook** (Your Power, I): cooldowns updated, "Z (Thorns) Spike Surge", "Hold V Form Wheel", "Sneak+G Spike Nova"; Thorns and the grab
  rewritten. JEI suit page summary updated. Lang: `scratchpad/symbiote/moveset_v3_lang_v0175.js` (key by key, re-runnable).

### Tests

`SymbioteMovesetV3GameTests`: the cooldown numbers (R / G 1 s, Shift+R 4 s, Z 8 s, every weapon Z 8 s, Ground Eruption 8 s, Spike Nova 8 s on
its own entry, Spike Surge 10 s on entry 16 without touching entry 3, 18 entries, Shift variants never block the plain key); the Thorns aura
stabs and withers a husk 0.6 blocks off and not a squadmate, nothing again inside 15 ticks, nothing in Normal form; a husk 2.6 blocks off is
out of the aura until Spike Surge, then hit for the surge's 5; Spike Nova hits a ring of six husks with Wither II; the grab is still held
after 5 s (the old limit was 3.5), a click crushes, a second click inside 10 ticks does not, the next one does, C throws (grab cooldown, no
bite); Shift+C puts it down on the ground in front, unhurt, with gravity and a negated fall; a squadmate can be grabbed, squeezed without
damage, thrown with no fall damage after; V taps still cycle, a wheel pick goes straight to Thorns / Whip, a weapon is refused with a full
hand, a held V (or the wheel's message) does not cycle, Shift+V is the Shield. `SymbioteMovesetV2GameTests`: Sweep cooldown and the
18-entry list updated.
