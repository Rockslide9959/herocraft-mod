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
