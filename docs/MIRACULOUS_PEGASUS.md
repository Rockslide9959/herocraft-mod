# Pegasus (Kaalki, the Horse Miraculous) -- v0.16.6

Kaalki lives in the Horseshoe Glasses. Say the phrase to transform; the core (`com.projecthero.mod.miraculous`) handles the
jewel, the Kwami, the countdown and the shared stats (+12 armour, +6 toughness, +10 health, +7 melee, Regeneration I,
+25% speed, +35% jump, no fall damage). This file covers the kit: `miraculous/pegasus/**`, `client/miraculous/pegasus/**`.
Every number lives in `PegasusConfig`.

## The six keys

| Key | Move | Numbers | Shift version | Numbers |
|---|---|---|---|---|
| R | **Horseshoe Throw**: a spinning boomerang to what you aim at (aim assist 6 degrees), back to your hand | 12 damage out and 12 back, 18 blocks, 1.5 s cooldown after the catch | **Horseshoe Volley**: a fan of 3 small golden horseshoes (12 degrees apart) | 7 each (first creature hit), 3 s |
| G | **Hoof Kick**: a buck that hits everything in front of you AND behind you (not to the side) | 14 damage, 3 blocks, strong knockback + lift, 2.5 s | **Gallop Trample**: a 12-block ground charge in 8 ticks (a wall ends it early) | 10 to everything within 1.5 blocks of the path, thrown aside, 6 s |
| X | **Portal Hop**: a portal before you, out of another at the spot you look at | 20 blocks, safe landing only, 4 s | **Gallop** (toggle) | Speed III + step assist (1 block) + golden hoof prints, 1 s debounce |
| Z | **Portal Trap**: a portal under the target, it drops out of one above | up to 20 blocks up (lower under a roof, refused under 6), fall damage applies, never bosses, 24 blocks range, 15 s | **VOYAGE** (special): a menu, then a big two-way portal to the pick | 30 s open, radius 1.7, starts the 5-minute countdown, 2 min cooldown |
| V | **Portal Shield**: a portal 1.6 blocks before your eyes swallows projectiles; an exit portal at your shoulder spits them back at the shooter (they become yours) | 10 s, V again closes it, 15 s cooldown after | **Sky Gallop**: run on the air (jump climbs, sneak sinks), golden hoof platforms underfoot | 10 s, 20 s cooldown after |
| C | **Mark Waypoint**: save where you stand | 3 slots, oldest replaced, saved with the world, 1 s | **Linked Portals**: A at your feet, B where you look | 32 blocks, 20 s, anyone/mobs/items pass, 20 s cooldown after |

## Portals

`PegasusPortalEntity` (never saved): a golden-white hexagonal ring with a swirling surface (`PegasusPortalRenderer`: two
counter-rotating swirl layers, full-bright, both sides). Not a see-through window (that would need a second world
render); the swirl stands in for it. Kinds: HOP, TRAP_LOW / TRAP_HIGH, VOYAGE, SHIELD / SHIELD_EXIT, LINK. A
transporting portal (Voyage, Linked) sends whatever crosses its disc to a precomputed safe exit, re-checked as it goes;
a traveller can't re-enter for 30 ticks. Bosses, Kwamis and portals never pass.

## Safe teleports (`PegasusTeleport`)

A spot is used only if: the chunk is loaded and inside the world border; the body fits (no block, no lava); it is under
the dimension's ceiling (`hasCeiling`: the Nether stops under its bedrock roof) and above the world bottom; and for a
landing there is a floor within 48 blocks below (never the void / open End sky). Portal Hop and Linked Portals search
straight down from the aim, up to 2 blocks up, then 1-2 blocks to the side. Portal Trap only lifts through a clear
column of air. Voyage loads (or generates) the far chunk first and holds both ends with a `PORTAL` chunk ticket while
open.

**Voyage across worlds (decision):** allowed, players only (mobs and items pass only within one world). The menu
offers your bed / spawn point (its own dimension), the world spawn (overworld), each squadmate online (wherever they
are; re-resolved when you pick, 2 blocks in front of them) and your waypoints. When the portal carries its own opener
across worlds the core's world-change clean-up would end his moves; `PegasusMoves.CROSSING` keeps the Voyage portals
open through that.

## Waypoints

`PegasusWaypoints` is overworld `SavedData` (`projecthero_pegasus_waypoints`), keyed by player UUID, so they outlast
detransforming, death, logout and restarts. The owner's client gets them (`PegasusWaypointsPayload`, on join, respawn,
world change, first transformed tick, each save) and draws a spinning golden Horseshoe over a thin beam on each one,
only while holding Kaalki. Nobody else is sent them.

## Suit

The user's skin (minecraftskins.com 15258841, slim) cut to suit-only by `scratchpad/make_suit_skins.js`
(`kwami_skins/pegasus_suit_{slim,wide}.png`), copied in by `scratchpad/gen_pegasus_v0166.js`, which also takes the
skin's own black glasses off the hat layer (16 texels) -- the core draws the Horseshoe Glasses jewel on the face, so the
two never fight. Drawn over the wearer's own skin, picked by `PlayerSkin.Model`, with the per-texel reveal spreading from
the glasses. Not bulky: base layer +0.02, second layer exactly vanilla's offsets (+0.25, head +0.5). The wearer's own
second layer is hidden while the suit shows -- hat included, because the cream mane hood replaces the hair (texel by
texel during the change). Worn armour is hidden (`PegasusArmorMixin`).

## Poses (ids 120..139)

Throw 120, Catch 121, Volley 122, Kick 123, Trample 124, Hop 125, Gallop rear 126, Trap 127, Voyage 128, Shield 129,
Sky 130, Waypoint 131, Link 132; plus running poses for the Gallop (lean, pumping arms), Sky Gallop (knees high), the
standing shield (hand up) and the Trample charge. The Horseshoe is at the right hip, in the right hand during a move,
gone while thrown; first person: `PegasusFirstPersonArmMixin` (sleeve) and `PegasusHorseshoeHandMixin`.

## Tests

`PegasusGameTests` (16): each move's effect and number, safe landing (stone, open sky, unloaded chunk, Nether roof),
waypoints (3 slots, oldest replaced, survive detransform and a save / load, listed in the Voyage menu), and the clean-up
on detransformation.
