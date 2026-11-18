# Falcon as a Secondary power (v0.17.5) — reference

User request: "can you make falcons powers a secondary power, while wearing the armour you get his powers and moveset but
when you take it off you go back to what you had before please". Original design: `FALCON_SILVER_SURFER_DESIGN.md`.

## Before / after

| | v0.17.1 – v0.17.4 | v0.17.5 |
|---|---|---|
| How you get it | Wear all four craftable gear pieces | Same four pieces, same recipes |
| Power class | Primary (Hero-Tier): `HeroTiers.claimPrimary("falcon")` on suit-up, so it **replaced** your Primary | **Secondary** (`Falcon.POWER_CLASS`): claims nothing, replaces nothing |
| A new Primary while suited | Revoked the Falcon; the kit stayed off (`blocked`) until a piece came off and on | The Primary swaps as usual; the Falcon gear is untouched |
| Keys while suited | Only if no mutation selected, and only after ~30 Primaries in `AbilityRouter` | Ahead of every Primary, right after Power Armour; H / N swallowed |
| HUD | Its own column, drawn alongside other powers | Owns the corner (`Stack.bottomRightOwned`); every other power's column steps aside (`VanillaHud.muteOthers`) |
| Taking a piece off | Lost the power (and the Primary was already gone) | The kit goes; your Primary's keys and HUD come straight back |

The suit items didn't change. Goggles (head), EXO-7 Wing Pack (chest), Flight Suit leggings and boots, all with recipes in
`data/projecthero/recipe/falcon_*.json` and the existing layer model and wings. No new items, models or recipes were needed.

## How it's wired (copied from Power Armour)

- `Falcon.ownsKeys(Player)` is true when the gear is live (`FalconState.hasPower`) and Power Armour isn't running. Client-safe.
  `FalconAbilityManager.hasContext` is now just that, and no longer cares whether a mutation is selected.
- `AbilityRouter.handleInput`: Erased check, then Power Armour, then **Falcon** (non-utility keys go to
  `FalconAbilityManager.handle`, H / N are swallowed), then everything else. The old Falcon branch lower down is gone.
- Client (`ProjectHeroModClient`): `FalconClient.swallowHeroKeys` eats H and N clicks and skips the Primary's own handlers
  (power wheel, Max Steel N, Wolverine off-hand, charged punch, firearm R reload). Unlike Power Armour, the **double-tap jump
  stays**. `handleDoubleJump` checks Falcon first (`Falcon.ownsKeys`), ahead of Thor, Sentry, the Kryptonian and the others.
- HUD: `PowerArmourClient` sets `VanillaHud.muteOthers` to `PowerArmour.ownsKeys || Falcon.ownsKeys`, and `FalconHud` draws with
  `Stack.bottomRightOwned`. The Left-Alt move list is the Falcon's. HUDs that don't use `Stack.bottomRight` (Omnitrix, Stand,
  Saiyan and the others listed in the Power Armour notes) aren't muted, same as for Power Armour.
- Fix found while filming (also affected Power Armour): a muted column's Left-Alt move list clamped itself back on screen, so
  Spider-Man's list drew over the Falcon's. `VanillaHud.Stack.moveList` now draws nothing for a muted (off-screen) column.
- The I screen (`PowerInfoScreen.heroTierChapter`) shows the Falcon page first while the gear owns the keys.
- Primary passives keep running, since nothing about the Primary is touched.

## Rules (the choices)

- **Not a Primary:** `"falcon"` is out of `HeroTiers.HERO_KEYS`, so the one-Primary order, `heroCount`, `trimHeroes` and
  `wipeAll` never see it. It's also out of `hasHeroTier` and `hasIncompatibleWith` (so Symbiote bonding never tries to purge
  it). `holdsHero("falcon")` is kept for `PowerGrants` and `UniquePowers`.
- **Grant key:** it stays in `PowerGrants.HERO_TIER_KEYS` and is listed in the new `PowerGrants.SECONDARY_KEYS`.
  `/projecthero power grant falcon` puts the gear on **without** wiping the player's powers (`HeroCommand.grant` skips the
  wipe for Secondary keys). `revoke falcon` switches the kit off until a piece comes off and goes back on (`FalconState.blocked`,
  now saved as `"revoked"`).
- **Serums:** still never roll it (`NOT_FROM_SERUMS`). It's a suit you craft.
- **Unique-powers gamerule / Omnitrix lock:** still apply on suit-up (`UniquePowers.refuse`). The holder is recorded with
  `UniquePowers.onClaimed` (the Primary path used to do this through `claimPrimary`). So an Omnitrix wearer still can't use the
  Falcon kit.
- **Power Armour comes first.** Stepping into a frame already stores the worn armour (the Falcon gear included) and hands it
  back on the way out. While Power Armour owns the keys (any phase), the Falcon kit stands down, and it comes back by itself,
  quietly and with Thrust and ammo as they were, when you step out. You can step into a frame with the gear on.
- **Other suits and transformations:** `Falcon.dormant` = Power Armour running, or `PowerArmour.otherSuitActive`: Iron Man
  pieces, Hulk out, inside a Titan, Omnitrix alien, Moon Knight suited, Black Panther suit, Max Steel transformed, Shazam,
  Miraculous transformed, Symbiote active, or Thor's Armour. While dormant the kit stands down (`Falcon.standDown`: flight,
  held moves and Redwing end, no block) and resumes when the form ends. Iron Man and the gear can't both be worn anyway,
  since both use the armour slots.
- **Erased (Eraser Head):** unchanged from before. The Falcon kit is still blocked like a power, not treated as tech.

## Migration (old saves)

- The power always followed the worn gear, so nobody loses items or the kit.
- `Falcon.migrateFromPrimary` (on join) strips `"falcon"` from the Primary-slot order and tells the player once that the gear
  is now a Secondary power. (`HeroTiers.enforceLimit` would also drop the entry silently, since it's no longer a HERO_KEY.
  Whichever join hook runs first wins, so the message can be skipped. Nothing else depends on it.)
- Players whose Falcon had been **replaced** by another Primary while the set stayed on had `blocked = true`. That flag was saved
  under `"blocked"`; the codec now reads and writes `"revoked"`, so the old flags are dropped on load and those players get the
  kit back on top of their Primary the moment they're wearing the full set. The Primary that once replaced the Falcon stays.
- A player whose Primary was replaced by the Falcon in the old days can't get it back, because that Primary was revoked at the
  time and nothing recorded it. Their Primary slot is simply free now.

## Statics

`Falcon.DORMANT` (a `Set<UUID>`: whose kit stood down, so the comeback is quiet and keeps the tanks) is cleared in
`Falcon.clearSessionState`, which `ServerStateReset` already calls through `FalconMod.clearSessionState`. It holds no worlds,
entities or block states.

## Tests

`FalconSecondaryGameTests`: keys route to the kit while suited and back to the Primary when a piece comes off, the Falcon
never claims or replaces a Primary (and a new Primary leaves it alone), no Primary is needed, the grant adds on top, Power
Armour comes first and the kit resumes with its Thrust, and old saves migrate (order entry and the `blocked` to `revoked` key).
`FalconGameTests`: the Hero-Tier key assertions now expect a Secondary, and `replacedFalconStaysOffUntilRegeared` became
`aNewPrimaryNeverReplacesTheFalconGear`.
