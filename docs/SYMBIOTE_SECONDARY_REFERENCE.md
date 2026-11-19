# The Symbiote as a Secondary power (v0.17.5) -- reference

User request: "since symbiote is a secondary power for now can we make it able to be used with any other power, you only get symbiote
moveset if player transforms into symbiote armour while having a primary power, if H does something for the primary power already while
having symbiote then make it shift+H to transform into symbiote". The user's decisions: Shift+H transforms when the Primary already uses
plain H, the power wheel moves to holding H (~0.5 s) for those cases, and no Primary is blocked. Built on the Falcon / Power Armour
Secondary pattern (`FALCON_SECONDARY_REFERENCE.md`). Suit / colours / mouth: `SYMBIOTE_REFERENCE.md`.

## Before / after

| | before | v0.17.5 |
|---|---|---|
| Bonding with a Primary | purged every power except Spider-Man / the Punisher | keeps everything ("The Symbiote settles in beside your powers") |
| Gaining a Primary / a mutation later | removed the Symbiote (unless Spider-Man / Punisher) | keeps it |
| `/projecthero power grant <primary>` | wiped the Symbiote too | the Symbiote stays (like the Falcon gear); `revoke symbiote` removes it |
| `/projecthero power grant symbiote` | wiped everything first | adds it on top (`PowerGrants.SECONDARY_KEYS`) |
| Keys, suit off, with a Primary | n/a | the Primary's; the Symbiote is **dormant** |
| Keys, suit on, with a Primary | n/a | the Symbiote kit's (R/G/Z/X/C/V) ahead of every Primary; H / N swallowed |
| No Primary | the kit works suit on or off | unchanged |

Spider-Man (Black Suit) and the Punisher (Agent Venom) are still their own host types (`SymbioteHostType`), so they never also get the
Normal-host suit; nothing about them changed. The Power Suppressor still strips everything, the Symbiote included.

## State

- `Symbiote.hasPrimaryPower(ServerPlayer)` = any Hero-Tier power or a mutation (`HeroTiers.hasHeroTier` / `hasExperimental`).
- `ModAttachments.SYMBIOTE_WITH_PRIMARY` (Boolean, synced, not saved): kept by `Symbiote.refreshPrimary` -- on grant, on
  `HeroTiers.claimPrimary`, and every 10 ticks in `Symbiote.tick` (only written on change). Read by `withPrimary(Player)`.
- `stacked` = Normal host + with a Primary; `ownsKeysOverPrimary` = stacked + suit on (or flowing); `dormant` = stacked + suit off;
  `stackedNow(ServerPlayer)` computes it on the spot (key routing, tests).

## Keys

- **Server** (`AbilityRouter.handleInput`): Erased / Time Stop / Ultron checks, Power Armour, then **a Normal host with the suit on**
  (`SymbioteAbilityManager.handle` for non-utility slots, H / N swallowed), then the Falcon gear, then every Primary. Suit off with a
  Primary, `SymbioteAbilityManager.hasContext` is false, so the Primary's handlers (or nothing) get the keys. The old bottom-of-chain
  Symbiote branch now only serves a host with no Primary.
- **Client** (`ProjectHeroModClient`): with the suit on over a Primary (`inSymbiote`), H or Shift+H takes the suit off
  (`handleSymbioteSuitH`), N and the Primary's own handlers (Max Steel N, Wolverine off-hand, charged punch, firearm reload, the
  double-tap jump) are skipped. Suit off, `handlePowerSelect` asks `SymbioteKeys.route` (pure, tested):

| Situation | H | Shift+H |
|---|---|---|
| No Primary (the Symbiote alone) | suit on / off | suit on / off (as before) |
| Primary that does nothing on plain H | suit on / off | power wheel if there is one, else suit on / off |
| Primary that uses plain H | the Primary's H | suit on / off |
| ... a mutation with an H utility and a wheel (the wheel was on Shift+H) | tap = Utility 1 (sent on release), hold 10 ticks = power wheel | suit on / off |
| Suit on, with a Primary | suit off | suit off |

  "Uses plain H" (`primaryOwnsPlainH`) mirrors the H chain: Gantry floor, Omnitrix master control, Titan Shifter, All Might, Hulk, Moon
  Knight, Nova, Sentry, Doctor Fate, a Miraculous, a Stand, Ghost Rider, Cyborg, Saiyan, Black Panther, Blue Beetle, Flash Suit, Iron Man
  worn, Max Steel, Wolverine, Thor bound to Mjolnir, a Green Lantern H binding, a mutation's Utility 1. The chain's own Symbiote branch is
  skipped for a stacked host. **Note:** in the current build none of the 13 enabled mutations has an H (Utility 1) ability (the ones
  that do, e.g. Crystalkinesis, are disabled), so a stacked mutation user gets "H = suit, Shift+H = wheel"; the hold-H wheel row only
  comes into play if an H mutation is enabled again. It is covered by the `SymbioteKeys` table test, not filmed.
  **Players without the Symbiote are untouched**: the wheel stays on Shift+H for them (least surprising: only
  the people who get the new Shift+H binding have to learn hold-H). Alt+H still always fires a mutation's Utility 1.

## HUD

- `VanillaHud.muteOthers` (set in `PowerArmourClient`) also holds while `Symbiote.ownsKeysOverPrimary`: every other power's column steps
  aside and the Symbiote column draws with `Stack.bottomRightOwned` (its Left-Alt move list with it).
- Dormant (stacked, suit off): the Symbiote column is not drawn at all; the Primary's HUD is the one on screen.

## Dormant: what the Symbiote does with the suit off over a Primary

Nothing, so the Primary is the player's power until they suit up: no bonded passives (Regeneration, +7% speed, knockback resistance),
no damage rules (fall immunity, Biomass share, the protective auto-wrap on a heavy hit), no wooden-tool hands, no resurrection, never
an automatic suit-up (`autoEquip`), no HUD. The Biomass bar itself keeps regenerating. With the suit on everything works as before,
and the Primary's passives keep running underneath.

## Other forms (no blocking, no breakage)

| Primary state | What happens |
|---|---|
| Iron Man pieces worn, Moon Knight / Black Panther suited, Max Steel transformed, a Miraculous transformed, Thor's Armour, any other power's bound suit piece in an armour slot (Nova, Blue Beetle, Green Lantern, ...) | the suit-up **waits**: "The Symbiote will not cover another form. Change back first." |
| Hulk out, inside a Titan, an Omnitrix alien, Shazam, a Power Armour suit | the suit-up waits (same message); if one of these starts while the suit is on (a non-H path -- H is the suit's own while it is on), the suit lets go at once (`hardDeactivate`, "Another form takes over: the Symbiote draws back.") |
| Falcon gear worn | allowed: the suit stows the gear like any armour and the Falcon kit stands down (`Falcon.dormant` already lists an active Symbiote); both come back when the suit comes off |
| Sentry, Nova, Doctor Fate, Ghost Rider, Cyborg, Saiyan, ... (no armour-slot suit) | allowed; their keys and HUD step aside while the suit is on, passives keep running |

While the suit is on the client never sends the Primary's H, so a Primary transformation cannot be started under it by key.

## Old saves

Nobody had both before (bonding purged, a Primary removed the Symbiote), except Spider-Man / Punisher hosts, who are unchanged. Anyone
who has both from now on keeps both. The flag is recomputed on the first tick after joining.

## Tests

`SymbioteSecondaryGameTests`: untransformed the Primary (Kryptonian) keeps its keys and G never reaches the Symbiote; suit on, G goes to
the Symbiote kit while the Primary is still held, and suit off the Primary has the keys again; no Primary is replaced either way
(Primary then bond, bond then Primary, bond then a mutation) and a lone host keeps the old suit-off kit; the H / Shift+H table
(`SymbioteKeys.route`); another power's bound suit piece blocks the suit-up until it is off. Updated: `SymbioteGameTests` (bonding keeps
a Hero-Tier power / a mutation), `PrimaryPowerGameTests.symbioteStacksOnAnyPrimary`.

Visual checks (`docs/screenshots/v0175/symbiote_secondary/`, harness `scratchpad/SymbioteSecondaryHarness.v0175.java.txt`, film via
`scratchpad/symbiote/films_secondary_v0175.js`): a Sentry with the Symbiote -- the Sentry HUD with the Symbiote dormant, the suit flowing
on (sent as the same `TOGGLE_SYMBIOTE` packet Shift+H sends; the harness cannot hold a real Shift key), the Symbiote column taking the
corner, a Spike Shot through the real router, the suit coming off and the Sentry HUD back (`sentry_shift_h_symbiote_suit_up_and_back.mp4`
+ `_sheet.png`); then a Super Strength user (no H ability) whose plain H (the real key mapping held) suits up.
