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
