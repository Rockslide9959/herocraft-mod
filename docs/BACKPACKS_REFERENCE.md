# Backpacks (v0.17.5)

User request: "can you add backpacks to the mod please". Package `com.projecthero.mod.backpack`, client `client/backpack`.

## Tiers

| Tier | Item id | Slots | Undyed colour | Recipe |
|---|---|---|---|---|
| Leather | `leather_backpack` | 27 (3 rows) | brown `#9C6236` | `LSL / LCL / LLL`: leather, string, chest |
| Iron | `iron_backpack` | 36 | slate `#5E6B78` | Leather Backpack inside 8 iron ingots |
| Golden | `gold_backpack` | 45 | crimson `#8E3B2E` | Iron Backpack inside 8 gold ingots |
| Diamond | `diamond_backpack` | 54 | teal `#2E6E8E` | Golden Backpack inside 8 diamonds |
| Netherite | `netherite_backpack` | 54, fireproof | charcoal `#564C50` | smithing: Netherite Upgrade + Diamond Backpack + netherite ingot |

- Upgrades use the recipe type `projecthero:backpack_upgrade` (`BackpackUpgradeRecipe`): a shaped recipe whose result takes the whole
  component patch of the backpack in the grid (contents, dye, anvil name). Smithing is vanilla `smithing_transform`, which already copies components.
- Netherite: `fireResistant()` (floats in lava, never burns). No void protection (vanilla netherite has none either).
- Any other tier destroyed as an item entity (lava, fire, cactus) spills its contents like a shulker box (`onDestroyed`).
- All tiers are in `#minecraft:dyeable` (dye in a crafting grid like leather armour) and a water cauldron washes the dye off.

## Storage and the dupe guard

- Contents live on the stack in vanilla `minecraft:container` (`ItemContainerContents`), so a backpack keeps them when dropped,
  stored, picked up or upgraded, and the data is saved/synced by vanilla codecs.
- `BackpackMenu` is a view onto one `ItemStack` instance, the one in inventory slot `invSlot` when opened. Every change writes straight
  back (`BackpackContainer.setChanged`, plus a catch-all in `broadcastChanges`). Nothing is held in the menu or returned on close.
- `live()` = that exact instance is still in that slot. `stillValid` uses it, so vanilla closes the screen the next tick after the
  backpack moves by any route (drop, `/clear`, death, another mod). Until then every backpack slot refuses place/pickup, `clicked` and
  `quickMoveStack` do nothing and the container refuses to write.
- The backpack's own slot in the inventory rows is a `LockedSlot` (shaded in the screen): no pick-up, Q, shift-click; number-key / F
  swaps involving it (`SWAP` with `button == invSlot`) are refused.

## Nesting

`BackpackItem.canFitInsideContainerItems()` is false, so backpacks can't go in shulker boxes or bundles (vanilla checks it). Backpack
slots refuse anything whose item says `canFitInsideContainerItems() == false`, which covers other backpacks and shulker boxes. Bundles
are allowed (they cap themselves). This rules out infinite storage and NBT bloat.

## Opening

- Right-click with it in either hand.
- **Open Backpack** key, default **B** (vanilla Inventory category in Controls; B is unbound in vanilla 1.21.1, the narrator is
  Ctrl+B, and nothing else in the mod uses it). Sends `OpenBackpackPayload`; the server opens the backpack on your back (best tier
  not in a hand), else one in your hands. Pressing it in the backpack screen closes it.

## Wearing

- No armour slot. Every 5 ticks the server picks the best backpack carried (highest tier, earliest slot; hotbar, main rows, offhand
  excluded with the main hand) and syncs a contents-free copy to all trackers (`BackpackMod.WORN`, not saved).
- `BackpackLayer` draws `BackpackModel` (bag, lid, front + side pockets, handle, shoulder straps) on the body part: canvas
  `textures/entity/backpack/backpack_body.png` tinted with the dye/tier colour, then `backpack_trim_<tier>.png` untinted
  (straps, buckles, corner plates, gem). Over a chestplate the bag moves 1 px out and the straps widen.
- The vanilla cape is hidden while one is worn (`BackpackCapeMixin`). The Captain America back shield moves outside the bag
  (`BackpackLayer.shieldZ`, used by `CapShieldBackLayer`).

## Capes, wings and other back gear over the bag

User request: "make the backpacks not clip through capes and wings". Everything on a player's back is drawn outside the bag through one
helper in `BackpackLayer` (no per-layer magic numbers):

| Helper | What it gives |
|---|---|
| `outerZ(p)` | the bag's outer face, px out of the back in body space: `2 + BackpackModel.DEPTH` (+1 over a chestplate) + `GAP` (0.35). 0 without a bag |
| `backClearance(p, innerZ)` | **the shared helper**: px to push something whose innermost point is at body z = `innerZ` so it lands outside the bag (0 without one) |
| `hangCapeFromBag(p, model, pose)` | for root-space cape layers: moves the pose into the body's frame at the bag's top outer edge (y -1.7, z `outerZ` + 0.15) and returns the body's lean (deg) to take off the swing; NaN = no bag, draw as before |
| `CAPE_MIN_SWING` (4 deg) | least swing a cape hung from the bag may have, so its length and flutter never cut back into the bag |
| `hiddenBy(p)` | cases where the backpack is not drawn at all (below) |

Moved outside the bag:

- **Capes / cloaks** hang from the bag's top outer edge, in the body's frame (so crouching or any body pose carries them), swing clamped
  at `CAPE_MIN_SWING`, side sway x0.6: `FlowingCapeLayer` (Superman, Sentry, Shazam, Thor, Vision, Raven, Doctor Fate; the collar
  runs from the shoulders up over the lid and handle, `drawBagCollar`), `MoonKnightCapeLayer` (hood too; Cape Block scales wider and
  deeper so its folds wrap round the bag; the glide webbing sits `capePivotZ` behind the limbs), `GyroCapeLayer`.
- **Raven's hood** (`RavenAuraLayer`): its back stops above the lid and handle (y -1.9) instead of reaching the neck.
- **Wings** mount on the bag's outer face: vanilla elytra (`BackpackElytraMixin`, ModifyArg on the 0.125 translate), Falcon's Wing Pack
  (`FalconLayer`), Blue Beetle (`BlueBeetleRender.renderWings`), Dark Shadow's ridge + Abyssal wings, Black Body included
  (`DarkShadowAnkhLayer`), Cryokinesis ice wings + harness, Pyrokinesis Phoenix wings (the tail is below the bag and stays),
  Chloro glide canopy, Max Steel's flight pack with its wings and the strength power pack (`SuperheroArmorRenderer.renderRecursively`,
  bones `flt_pack` / `str_power_pack`).
- **Jetpacks / extra limbs**: Cyborg's jetpack (`CyborgTech.renderJetpack`, jets move with it), Overhaul's two extra stone arms.
- **Back-slung items**: Carapace's shield on the back and the Shell Roll shell (`CarapaceRender.renderShield(..., back)`), Viperion's
  lyre, Rena Rouge's flute, Cat Noir's collapsed baton, Ladybug's yo-yo.
- **Tails** leave from under the bag: Rena Rouge's fox tail (root y 12.2, 0.2 rad lower), Multimouse's rope tail (root y 10.6), the
  Saiyan tail (root y 11.6, and every spring link level with the bag is held outside `outerZ` -- tested in the body's frame, so crouching
  works).

Hidden instead:

- **The backpack** (`BackpackLayer.hiddenBy`) while an **Omnitrix alien** is shown (a different body whose tails, wings and crystals grow
  out of the back -- and armour/cape are already hidden for aliens), while in **Aizawa's sleeping bag** (zipped round the whole player),
  inside the **Power Armour** (a mech drawn over the hidden wearer, with its own back modules), and while an **Iron Man suit-pack** (Mk VII /
  VIII) is carried on the back or deploying (it is a pack itself; its plates fly from the back onto the body).
- **Symbiote back spikes**: the six out of the back face stay in under a bag (the two shoulder spikes still grow).
- **Mk VIII worn O2 tanks** (flush on the suit after a pack deploy): not drawn under a bag (their bottoms would poke out under it).

Left alone (no clash): Spider Suit v2 web wings (armpit to wrist to hip, at z 0, beside the bag), the Spin holster (front of the hips),
Cat Noir's belt tail (hangs from y 10.6, under the bag), back plates that sit under the bag (Cryo/Geo armour, Overhaul hump, Crimson
Vanguard), the Saiyan aura (a translucent shell round everything).

## Not done / choices

- No placeable block form (optional in the brief; skipped to keep it robust).
- Textures are generated by `scratchpad/gen_backpack_textures.js` (original art). Lang keys added by `scratchpad/add_backpack_lang.js`.
- No static state, so nothing in `ServerStateReset`.
- Gametests: `BackpackGameTests`.
