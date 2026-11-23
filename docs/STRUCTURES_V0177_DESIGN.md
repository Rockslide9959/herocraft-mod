# Structures & Dungeons, round 2 (v0.17.7) — design

User's picks (2026-11-23): current places are **too small / plain**, **too easy / boring**, and there should be **more
places**. Add all of: Sky Fortress dungeon, Ancient Temple dungeon, new villain hideouts, small world places.
**Alarm + stealth: yes. Elite variants: no. Runs: 10-15 minute crawls** (several wings / floors, a mid-boss, then the boss).

Builds on v0.17.5 (`docs/STRUCTURES_PROGRESSION_DESIGN.md`, `docs/LOOT_PROGRESSION_REFERENCE.md`,
`docs/VILLAIN_HIDEOUTS_REFERENCE.md`, `docs/VAULT_DUNGEON_REFERENCE.md`): four place tiers, Locator chain, Hero Vaults,
rarity, gear tiers. Nothing there is removed. Implementation details not fixed here are the builder's call (note them in the
piece's reference doc).

## 1. Alarm & stealth (shared framework, package `com.projecthero.mod.alarm`)

Every guarded place (Tier 2 sites, Tier 3 hideouts, Tier 4 dungeons) is an **alarm zone** (its structure bounding box, kept
in SavedData by structure start). States: **Calm -> Suspicious -> Alert -> (cooldown) Calm**.

- **Spotting.** Guards see in a ~70 degree cone, 16 blocks (8 while you sneak, 4 invisible, halved in darkness). A
  per-guard suspicion meter fills while they see you (faster close up); full = that guard is alerted. A "?" over a
  suspicious guard, a "!" when alerted (client particles / nameplate icon, small).
- **Raising the alarm.** An alerted guard shouts (nearby guards join the fight), and the zone's **Alarm Runner** guard
  sprints to the nearest **Alarm Panel** block. If he reaches it, or a **sensor** fires (laser tripwire, sweeping security
  camera block, a ward gong in the temple), the zone goes **Alert**.
- **Stopping it.** Kill the runner before he reaches a panel; or sabotage a panel while the zone is Calm (sneak up,
  hold right-click 3 s: it sparks and goes dead, and sensors wired to it shut off).
- **Alert.** Siren sound + red Alarm Lamps light up, **Blast Doors** shut (opened by the place's keycard from the
  Captain, or by killing everything in the current wave), **reinforcement waves** (2 waves from spawn points, size by
  tier), and every guard knows where you are. After all reinforcements die and 60 s pass with no guard seeing you, the
  zone calms down again (doors re-open, but sabotaged panels stay dead).
- **Reward for stealth.** Reaching and opening the place's vault without the zone ever going Alert = a **Ghost bonus**:
  one extra roll on the vault's table and the `projecthero:ghost` advancement.
- **HUD.** While inside a zone: a small eye icon near the top showing Calm / Suspicious / Alert. Wraps and stays small.
- **Guard roles** (added to the existing guard mobs as a role tag + behaviour, keeping player models with skins):
  - **Captain** — carries the place's **Keycard**, buffs guards within 8 blocks (+20% damage). One per place.
  - **Alarm Runner** — light armour, fast, never fights while a panel is reachable.
  - **Medic** — heals hurt guards in range, priority target.
  - **Shield-bearer** — blocks 70% of damage from the front; hit from the side / behind or knock the shield away.
  - **Sniper** — on perches, long range, slow shots, highlighted with a laser sight line.
- All bosses and mini-bosses keep the **shared threat table**. Static collections holding worlds / entities / block
  states are cleared in `diagnostics/ServerStateReset`.

## 2. Existing places, bigger and harder

- **Villain hideouts (5):** rebuilt to a 10-15 min crawl: outer defences (guard posts, a sniper perch, the first panel),
  an inner level (keycard door, a puzzle or trap gauntlet, the Captain), the mini-boss arena (a new phase or two for each
  mini-boss) and the sealed vault. Roughly double the current footprint, multi-level, more decoration per faction.
- **The Vault (Tier 4):** two more floors — a **Maximum Security wing** (laser grids, camera sweeps, a mid-boss: the
  Riot Captain) and the **Warden's Control Room** (shut down the cell-block power to open the way). Warden Kross gets a
  second phase.
- **Tier 2 sites (20):** one to two extra rooms each, a guard Captain with the keycard to the vault room, an Alarm Panel
  and a camera or two. Stays shorter than a hideout (5-8 min).
- **Tier 1 finds:** two to three layout variants for each kind and a decoration pass (no alarms — finds are unguarded).

## 3. New Tier 4 dungeons

### Sky Fortress
- A flying fortress at y 180-220 over plains / ocean / savanna, rare (wider spacing than The Vault). The Dungeon Locator can
  point at it. A **Launch Pad** (small guarded ground post) sits under it: step on with a Fortress Pass (from the post's
  Captain) to be flung up to the hangar. Fliers can just fly up.
- Wings: **Hangar deck** (entry, parked jets as decoration, turret guns), **Engine room** (four turbines: sabotage all four
  to cut the shields on the bridge door; the fortress shakes and tilts slightly), **Cell block** (freed prisoners can be
  left or freed for a reward), **Command bridge** (mid-boss: the Deck Officer), **Flight deck** (boss).
- **Boss: Admiral Kestrel** — an original villain in a heavy flight-suit (GeckoLib, bosses only): jet dashes, missile
  barrages, a deck-wide strafing run, calls fighter drones; phase 2 at 50% takes to the air around the deck.
- Fall risk is part of it: open edges, wind gusts on the flight deck. Falling off with no flight = a long drop (a slow-fall
  parachute crate near the hangar).

### Ancient Temple
- A huge temple in jungle and desert (sandstone or mossy stone by biome), mostly underground, rare.
- Its alarm is the **Temple Wards**: gong tripwires and pressure plates "wake the guardians" (same framework, themed).
- Wings: **Antechamber** (dart halls, collapsing floor), **Hall of Trials** (three seals to break: a glyph pressure-plate
  order shown on a mural, a mirror light-beam puzzle, a weight altar), **Crushing Corridor** (walls close in, timed run),
  **Flooded Crypt** (mid-boss: the Tomb Sentinel), **Throne of the Bound King** (boss).
- **Boss: Ankhatep, the Bound King** — an original undead sorcerer-king (GeckoLib): sand-blast waves, summons
  temple guardians, a curse that drains health until you break the canopic jars in the arena; phase 2 he breaks his chains.
- Guards: temple guardians, wrapped revenants, cult acolytes (player models with detailed skins).

## 4. New villain hideouts (Tier 3, same crawl structure as §2)

- **Gravebound Crypt** (dark forest / swamp, underground): Gravebound undead, the Gravebound Curse traps, mini-boss the
  Crypt Lord.
- **Carnage Nest** (around an old meteor crater, any temperate biome): red symbiote-infested mobs, biomass vents, pulsing
  egg walls, mini-boss the Brood Spawn of Carnage.
- **Oathbreaker Keep** (mountain / windswept hills): a ruined fortress, oath-bound knights, ballistae, mini-boss the
  Oathbound Champion.
Each joins `#projecthero:locator/tier3`, calms its faction's raids nearby when cleared, and has its own artifact.

## 5. Small world places (Tier 1-ish, unguarded or lightly guarded, no alarms)

Ruined Hero Base (a collapsed hideout with a costume case and notes), Crashed Parademon Pod (scorched crater, an Apokolips
tech cache, a couple of Parademons), Abandoned Workshop (a dusty inventor's lab, a broken suit frame, Salvaged Tech),
Smugglers' Den (a cave stash behind a waterfall or in a cliff, a few thugs), Rooftop Vigilante Stash (only in the City),
Tech Meteor (a small crater with glowing scrap). Each has 2-3 layout variants, Tier 1 loot, and some drop a Site Locator.

## 6. Everywhere

Guidebook (Structures and Loot & Progression chapters, the new places, the alarm system), JEI info pages for new items and
blocks (Keycard, Fortress Pass, Alarm Panel, Alarm Lamp, Blast Door, Security Camera, Laser Tripwire), en_us key by key,
tooltips that wrap, gametests for each piece (listed in `src/gametest/resources/fabric.mod.json`), screenshots / MP4s in
`docs/screenshots/v0177/<piece>/`.

## 7. Build order

- **Wave 1:** (a) the alarm framework + guard roles, wired into the 5 hideouts, and the 5 hideouts rebuilt as crawls;
  (b) small world places (independent).
- **Wave 2 (on the framework):** (c) Sky Fortress, (d) Ancient Temple, (e) the 3 new hideouts, (f) The Vault's new floors +
  Tier 2 site rooms + Tier 1 find variants.
Each piece is merged, compiled and copied to the test modpack + Drive as it lands.
