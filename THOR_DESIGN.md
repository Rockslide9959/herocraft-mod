# Thor / Mjolnir Mod Design Document

## 1. Mjolnir Spawn: The Shrine Structure

**Structure Concept**: A "Lightning Crater" — a scorched impact site where Mjolnir fell to earth.

### Structure Composition
- **Center**: Small crater (5-7 blocks wide) with scorched/blackened terrain
- **Ground**: Mix of coarse dirt, blackstone, and scorched grass (custom block or reused Blackstone/Basalt)
- **Ring**: Charred trees/logs radiating outward, some still "smoking" (particle effect, no actual fire spread)
- **Pedestal**: Center of the crater — a small obsidian/basalt plinth with Mjolnir resting on top
- **Ambient Lightning**: Occasional non-damaging lightning strikes around the structure (visual + sound only, no fire) to signal "something powerful is here"

### Generation Rules
- **Rarity**: Very rare — comparable spacing to Woodland Mansions or rarer (e.g. 1 per ~6-8 chunk-grid cells, with additional random chance per candidate location)
- **Biome Restriction**: Can spawn in most overworld biomes but weight toward open/dramatic ones (plains, mountains, taiga) — avoid oceans/deep caves for the "fell from sky" fantasy
- **Singleton Option**: You could cap this at "one unclaimed shrine at a time per world" so it feels special, then have a new one generate elsewhere once the current Mjolnir is claimed. This is a config toggle — up to you whether multiplayer servers allow multiple hammers.

### Implementation Notes
- Use a `StructurePiece`/`Jigsaw` structure (NeoForge/Fabric both support structure templates via `.nbt` files you build in-game with structure blocks, then export)
- Attach a `BlockEntity` to the pedestal block that holds the actual Mjolnir item entity — this makes "is Mjolnir still here" queryable and lets you replace it with a static model when unclaimed

---

## 2. Worthiness System (Hidden Moral Score)

### Core Data
- Each player gets a persistent hidden score: `asgardianWorth` (integer, roughly -100 to +100), stored via player capability (Forge) or persistent component (Fabric/NeoForge attachments)
- **No UI indicator** — worthiness should feel discovered, not min-maxed. Players learn where they stand only by trying to lift the hammer.
- Threshold for worthy: score ≥ 50 (tune to taste — lower if you want worthiness to be common, higher if it should be rare/aspirational)

### Actions That Raise Worth (examples — pick a subset to start, expand later)
| Action | Points | Hook |
|---|---|---|
| Curing a zombie villager | +15 | `VillagerConversionEvent` |
| Feeding/breeding passive mobs | +2 | `PlayerInteractEntityEvent` |
| Defeating a hostile mob threatening a village (proximity check) | +3 | `LivingDeathEvent` |
| Giving items via trade at fair/no profit | +1 | `TradeEvent` |
| Rescuing a villager (e.g. from drowning, lava proximity) | +10 | custom proximity-save detection |
| Planting saplings/crops (net positive world impact) | +1 (capped/day) | `BlockPlaceEvent` |
| Sleeping to skip night peacefully (non-aggressive playstyle bonus, small) | +1 | `PlayerSleepEvent` |

### Actions That Lower Worth
| Action | Points | Hook |
|---|---|---|
| Killing a villager | -20 | `LivingDeathEvent` |
| Killing passive/baby mobs without cause | -5 | `LivingDeathEvent` |
| Unprovoked PvP (attacking a player who hasn't flagged hostile) | -10 | `AttackEntityEvent` |
| Griefing (breaking player-placed blocks in certain contexts, or destroying beds not your own) | -5 | `BlockBreakEvent` |
| Burning down forests/farmland with fire | -3 | `BlockIgniteEvent` |

### Score Behavior
- Slow passive decay toward 0 over long periods of inactivity (optional) — prevents "grind once, worthy forever" if you want worthiness to require *sustained* good behavior
- Store last-known worthy state so you can trigger one-time "you have become worthy" / "you are no longer worthy" events (title card + sound) rather than silent state changes

### The Lift Attempt
- Right-click (or attempt to pick up) Mjolnir on its pedestal
- **If worthy**: Hammer lifts with a burst of lightning + thunder sound, screen flashes briefly, hammer flies to player's hand. This is the "hero moment" — make it dramatic.
- **If unworthy**: Hammer doesn't budge. Small "clunk"/resist sound, maybe the player visibly strains (a shake animation on the item) but it stays put. No feedback about *why* — that's for the player to wonder about, Marvel-style.

---

## 3. Powers

### Flight
- **Activation**: Hold Mjolnir + keybind (or double-jump while holding it) to toggle flight
- **Control**: Full creative-style flight while active, but tied to a "Storm Energy" resource bar (see Energy System below) so it's not infinite
- **Visual**: Player has a lightning/wind particle trail; hammer arm extended forward like the classic pose
- **Landing**: Slamming into the ground from flight (fast descent + right-click) triggers a small shockwave — knockback + minor damage to nearby entities (a fun "hero landing" easter egg)

### Lightning Strike (Ranged Attack)
- **Activation**: Right-click while aiming at a target/location (crosshair-based, similar to how a bow trajectory works but instant)
- **Aim assist (v0.7.5)**: if the raw crosshair ray misses every entity, the strike snaps onto the living entity closest to the look vector within a 14° half-angle (and within 30 blocks, line of sight required), so looking near an enemy lands the bolt on it instead of behind it (`ThorPowers.findAimAssistTarget`)
- **Effect**: Calls down a lightning bolt at the target point — damage + visual lightning strike (using the vanilla lightning bolt entity, but a "clean" variant that doesn't start fires unless you want it to)
- **Range**: ~30 blocks
- **Cooldown**: 8-10 seconds (tune for balance)

### Throw & Return (Loyalty-style)
- **Activation**: Right-click while NOT flying to throw Mjolnir at a target/direction
- **Behavior**: Custom projectile entity (not a normal thrown item) — flies in a straight/slightly arced line, damages and knocks back anything it passes through, then automatically returns to the player's hand after either:
  - Reaching max range and having nothing to hit, or
  - A short delay after impact (like Loyalty enchant timing)
- **Visual**: This is the same underlying system as your "summon from anywhere" — see below, since throwing is really just "summon after a short flight."

### Drop & Summon (Core Signature Mechanic)
This is the heart of the mod, so let's design it carefully.

**When dropped/thrown/lost**: Mjolnir becomes a tracked entity in the world (not a normal item — a custom `MjolnirEntity`) so it always exists at a real position, even across dimensions.

**Global Tracking**: A `SavedData`/persistent world-state object stores, per hammer instance (each has a UUID):
- Current dimension
- Current position
- Current holder (if any)
- Current state (idle, flying-to-owner, flying-cross-dimension)

**Summon Activation**: Player presses a keybind ("Call Mjolnir") or does a specific gesture (e.g. hold empty main hand + extend arm / sneak + jump)

**Same-Dimension Summon**:
- Hammer entity immediately begins flying toward the player
- Pathing: straight-line vector toward player's hand position, updated each tick (homing, like a slow-turning missile) so it curves naturally around the player if they move
- Speed: fast but visible — you want players to see it crossing the world, not teleport. Something like 2-3 blocks/tick (40-60 blocks/sec) feels dramatic but trackable. Tune based on testing.
- Collision: Mjolnir should break through weak blocks in its path (leaves, glass, flowers, cobwebs) for visual "unstoppable force" feel, but stop/redirect around solid stone-tier blocks — OR, if you want it truer to the comics, let it phase through everything and only worry about not clipping through the map (respect world border/dimension bounds).
- Arrival: Snaps into the player's hand with a small flash/sound when it reaches them

**Cross-Dimension Summon** (your signature request):
- If the hammer's stored dimension ≠ player's current dimension:
  1. **Phase 1 — "Charging"**: A delay (suggest 15-45 seconds, tune to taste) during which nothing visibly happens yet except maybe distant thunder sound effects and a status message ("Mjolnir stirs in the void...")
  2. **Phase 2 — Dimension Transfer**: After the delay, the hammer entity is removed from its old dimension and re-spawned at a "gateway" point in the player's current dimension (e.g., directly above the player, or at the world spawn point, or at the nearest point matching its old coordinates)
  3. **Phase 3 — Visible Flight**: Now that it's in the same dimension, it does the normal visible homing-flight sequence described above, so the player still gets to *see* it arrive
- **Death Handling**: If a player dies while holding Mjolnir in the Nether/End, the hammer stays there as a dropped `MjolnirEntity` (never a normal item — don't let it despawn or burn in lava). When the player respawns in the Overworld and calls it, this triggers the full cross-dimension sequence above automatically.

---

## 4. Energy System: "Storm Energy"

To prevent flight/lightning spam, introduce a resource bar (separate from hunger, since Thor shouldn't need to eat constantly like a base-game survivalist):

- **Display**: Custom HUD bar (like the food bar but different icon — lightning bolt) visible only while holding/attuned to Mjolnir
- **Regeneration**: Passive slow regen, faster during thunderstorms (thematic bonus — Thor draws power from storms), faster still if standing directly in rain
- **Costs**:
  - Flight: drains ~1 point per second while active
  - Lightning Strike: 15-20 points per cast
  - Hammer throw: negligible (it's a signature move, shouldn't be gated hard)

---

## 5. Custom Textures Needed

| Asset | Notes |
|---|---|
| Mjolnir item model | 3D model (not flat 2D) — head + handle, ideally with a subtle glow/emissive texture for the runes |
| MjolnirEntity in-flight model | Same model, but you'll want a rotation animation while flying |
| Structure blocks | Scorched grass/dirt variant, obsidian pedestal detail texture |
| Lightning particle | Custom particle texture if vanilla lightning doesn't feel dramatic enough on strike |
| Storm Energy HUD icon | Lightning bolt icon set (full/empty states like hunger drumsticks) |
| Player worthy-glow effect | Optional: subtle particle aura on a worthy player holding Mjolnir |
| Thor armor/cape (future) | If you want a suit later, matching your Spider-Man suit pattern |

---

## 6. Implementation Plan (Suggested Build Order)

1. **Item + Model**: Get Mjolnir existing as a basic item with your custom texture/model first — confirm it renders correctly
2. **Worthiness Capability**: Build the hidden score system and hook 2-3 simple events (villager kill = bad, cure zombie villager = good) to prove the concept before adding the full table
3. **Pickup Gate**: Implement the "can't lift if unworthy" logic on pickup/interact
4. **MjolnirEntity + Throw/Return**: Build the custom flying entity, get basic same-dimension throw-and-return working (this is your most complex piece — budget the most time here)
5. **Summon Keybind**: Wire up the "call from anywhere" logic using the same entity, add the cross-dimension delay/transfer logic
6. **Flight**: Add the flight toggle and Storm Energy bar
7. **Lightning Strike**: Add the ranged attack
8. **Structure Generation**: Build the shrine structure last, once the hammer itself is fully functional — easier to test "is this hammer worth finding" once it actually does everything

This order lets you test the coolest mechanic (cross-dimension summon) early with a placeholder texture, rather than building the structure/world-gen first and having nothing interesting to put in it yet.

---

## 7. Events/Hooks Reference (Forge/NeoForge naming — Fabric has equivalents via callbacks)

- `PlayerInteractEvent.RightClickItem` — throw, lightning strike, flight toggle
- `LivingDeathEvent` — worthiness scoring (villager/mob kills)
- `VillagerConversionEvent` — worthiness bonus (curing zombie villagers)
- `PlayerRespawnEvent` — trigger cross-dimension hammer check on respawn
- `TickEvent.PlayerTickEvent` — Storm Energy regen, homing flight updates for MjolnirEntity
- `EntityJoinLevelEvent` — handle MjolnirEntity persistence when changing dimensions
- Custom `SavedData` class — global hammer state tracking (position, dimension, owner)

