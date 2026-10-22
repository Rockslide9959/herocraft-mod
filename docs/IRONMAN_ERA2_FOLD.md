# Iron Man Era II -- portable forms (fold / deploy)

v0.16.7 (user), Era II stage 2. Every Era II suit packs away into a **portable form**: the Mk VI into a briefcase,
the Mk VII / Mk VIII into a backpack / thruster pack, the Mk IX into a heat case, the Hulkbuster into a drop crate.
One shared framework runs all of them. The Mk VI briefcase is built and proven on it. The other four forms are
built by registering a form and writing its prop renderer.

| Control | What it does |
|---|---|
| **C** (no suit on) | Deploys the first packed form you carry: the main hand first, then the rest of the inventory. Right-clicking the packed item does the same. |
| **Hold Shift+C** (wearing it) | Folds the suit back into its packed form after holding for `PortableDeploy.FOLD_HOLD_TICKS` (0.5 s). |
| **C** (wearing it) | v0.16.7 (user): a kit move -- the utility moved to C (see `IRONMAN_ERA2_KITS.md`, "Energy + C utility round"). Was: the fold hint. |
| H | Not used (it stays the power selector). |

## Files

| Side | Class | Role |
|---|---|---|
| common | `ironman/suit/PortableForm.java` | The record that describes a form: data and pure maths only. |
| common | `ironman/suit/PortableForms.java` | The registry (static, append-only, same order on both sides). `MK6_BRIEFCASE` lives here. |
| server | `ironman/suit/PortableDeploy.java` | Deploy, the fold hold, the fold, the tick, packing, and the death / logout / dimension hooks. |
| client | `client/ironman/IronManPortableClient.java` | Reads the clock, applies the texel build, does the pose, and draws the prop in two passes. Also holds the `PortableProp` interface. |
| client | `client/ironman/Mk6BriefcaseProp.java` | The briefcase prop: carried, dropped, flipped open, the flying plates, then shut and vanishing. |
| client | `client/ironman/IronManGantryBuild.java` | The `PortableTiming` scheme (`S_PORTABLE_CLIMB` / `S_PORTABLE_DROP`). |
| tests | `gametest/IronManPortableFormGameTests.java` | Six server tests. |
| client | `client/ironman/Mk9HeatCaseProp.java` | The Mk IX heat case prop (see below). |
| tests | `gametest/IronManMk9HeatCaseGameTests.java` | Six Mk IX tests, lava included. |

## A form = `PortableForm`

```java
new PortableForm(
    "mark_2",                              // suit id
    () -> IronManItems.MARK_V_SUITCASE,    // packed item (holds the 4 real stacks via SuitcaseContents)
    Place.HAND,                            // rest: where it sits packed  (HAND / BACK / BLOCK)
    Place.FLOOR,                           // during: where the prop is while it deploys (FLOOR / BACK / BLOCK)
    new Beats(dropFrom, dropTo, openTo, sealAt, closeFrom, total),
    stages(noStages(), STAGE, from, to, ...),   // one {from,to} frame window per GantryTimeline stage
    Direction.CLIMB,                       // texel stages build up the body (CLIMB) or down it (DROP)
    "mk6_briefcase",                       // client prop id
    new float[][] { poseKey(frame, 12 angles...), ... })   // body pose keyframes
```

* **One clock.** Everything runs on frames `0..total`. A deploy plays them forwards and a fold plays the same frames
  backwards (`form.frame(up, age)`). You never write a separate fold animation. If the deploy looks right, the fold
  is its exact mirror.
* **Beats** (frames on the deploy clock). The prop leaves the hand or back at `dropFrom` and has landed at `dropTo`.
  It is fully open at `openTo`. The faceplate seals at `sealAt` (on a fold it lifts at that frame). The emptied prop
  starts closing at `closeFrom`. The deploy ends at `total`.
* **Stage windows: the texel / part build order.** The 16 Stark Gantry stages are `GantryTimeline.TORSO_TOP ..
  FACEPLATE`: boots, thigh top / bottom, pants layer, gauntlets, arms, sleeves, torso top / bottom, jacket, helmet,
  faceplate. The client sorts every texel of the worn piece into these stages exactly as the Gantry does:
  * The base plates of `TORSO_BOTTOM`, `R_ARM` / `L_ARM` and `THIGH_BOTTOM` **slide** out from under the part before
    them.
  * Every other stage builds on **texel by texel**, with **no glow**. Its order follows `direction`, with a little
    scatter.

  `{-1, -1}` means a stage is not used. Mk VI order: boots, legs, arms + chest, helmet, then the faceplate.
* **Pieces.** The server puts each real armour piece on at `form.pieceOnAt(bit)`, the first frame any of its stages
  starts. A piece is therefore always in its slot before its first texel shows.
* **Pose.** `form.pose(frame)` returns `{weight, 12 angles}` in the `IronManSuitPoses` layout. It eases in over 5
  frames and out over 6. The angle convention is that right +z and left -z both swing the arm or leg outwards.

## Sync (multiplayer)

* There is **no new packet.** The server writes `IronManSuitFx` with `style = STYLE_PORTABLE` and
  `poseKind = PortableForms.poseKind(form, up)`, which is `100 + 2*index` for a deploy and `+1` for a fold. That
  attachment already syncs to every tracking client, so the pose kind itself tells the client which form to draw.
  * **Never reorder the registry. Only append to it.**
* No piece clocks are used. The body bones stay put (`IronManAssemblyClient` returns early), and all build visuals
  come from the pose clock.
* A **fold's** pose clock runs `PortableDeploy.END_HOLD` (10) ticks past the end. During that time viewers keep
  drawing frame 0: suit gone, case in the hand. This holds until the emptied armour slots have synced. Without it the
  whole suit flashes back for a frame.
* Floor props are anchored client-side (`IronManPortableClient.Anchor`). The anchor is the wearer's feet and body yaw
  the first time a viewer sees that clock. The wearer is held still (the Suit Platform movement lock) for the whole
  sequence, so every viewer gets the same spot.

## Server lifecycle (`PortableDeploy`)

**Deploy**
1. Check the power, that no suit is worn, that no transition is running, and that there is a packed item (selected
   slot first).
2. A legacy or crafted item that has never held a suit stands for a fresh full suit. An emptied one never conjures a
   suit.
3. Load the integrity and energy from the item, call `setActiveSuit` **before** taking the state object (it copies
   the state), and freeze the player.
4. Each piece leaves the item for its slot at `pieceOnAt`. The helmet comes on with its faceplate open and seals at
   `sealAt`. At `total` the empty item is used up.

**Fold**
1. All pieces stay worn, with their texels vanishing, until the last tick.
2. On that tick `pack()` puts every worn piece into a fresh packed item, with integrity and energy stamped on both the
   pieces and the item.
3. `IronManSuitUpManager.placeSuitcase` places it: the main hand if it is free, else the hotbar, else the inventory,
   else the feet.

**Edge cases**
* Damage does not interrupt either sequence.
* Death (`abort`) just stops it. Worn pieces drop as armour and a half-empty item drops as an item.
* Logout or a dimension change (`completeNow`) finishes the sequence at once.
* The only static per-player state is `FOLD_HOLD`. It is cleared in `ServerStateReset` and dropped on disconnect.

`IronManSuitUpManager.beginSuitUpFromCase` and `beginSuitDownToCase` hand a suit with a form over to
`PortableDeploy`, so the item's right-click and the old callers use the new flow.

## Writing the next form (Mk VII backpack, Mk VIII pack, Mk IX heat case, Hulkbuster crate)

1. **Packed item.** Reuse `SuitcaseContents` (4 slots, index = bit: 0 HEAD .. 3 FEET) and `IronManEnergy.stampStack`
   on a new item. For Place.HAND, look at `MarkVSuitcaseItem` (GeckoLib item, `openness` posing).
2. **Register** in `PortableForms`, **appended after** `MK6_BRIEFCASE`. Pick stage windows. For a backpack, chest
   first: `TORSO_TOP` early, then arms, then legs with `Direction.DROP`.
3. **Prop.** Implement `IronManPortableClient.PortableProp`:
   * `renderBody` is called from a player layer in model space. Use it for the hand or the back.
   * `renderWorld` is called after entities. The pose stack is already at the anchor and turned to its yaw: local
     +z is forward, -x is the wearer's right, +y is up, and 1 unit is 1 block. Use it for the floor or a block.
   * Register it in `IronManPortableClient.initialize()` with `registerProp(form.propId(), prop)`.
4. If the form's rest place is the hand, `ownsRightHand` already hides the real held item while the sequence runs.
   * For BACK, draw the worn pack in your own layer when **not** in a sequence.
   * For BLOCK, the crate needs its own block and block entity. The framework's clock and pieces still apply. The
     server currently finds the packed item in the inventory, so a crate form needs a small extension in
     `PortableDeploy.findPacked` / `takeFromPacked` (a block source).
5. **Gametests.** Copy `IronManPortableFormGameTests` and swap the form.

## Hulkbuster I drop crate (`PortableForms.HULKBUSTER_CRATE`, Place.BLOCK)

The Hulkbuster is a 3-block GeckoLib body, so it does not use the clock / texel build. `PortableDeploy.deploy` and
`fold` hand a BLOCK form to `ironman/crate/HulkbusterCrate`; the crate is an entity (`HulkbusterCrateEntity`) with its
own synced clock (phase + start tick), drawn by `client/ironman/HulkbusterCrateRenderer` (pure pose functions on the
entity: `wallOpen`, `lidOpen`, `height`, `floor`, `squash`).

* **Where the suit lives:** in orbit = inside the **Hulkbuster Uplink** item (`SuitcaseContents`, integrity stamped). While
  the suit is down the uplink stays in the inventory, emptied; the next fold refills it. A never-used uplink is a fresh suit.
* **C:** the crate drops where you look (5-40 blocks out; open sky over the centre and corners, room for it, `mayBuild` /
  `mayInteract` / world border), lands (dust, shake, scorched ring), walls fall flat, lid flips; the Hulkbuster stands in
  it as a real Sentry (`IronManSentryEntity.standInCrate`, `crated`) with its back open toward the crate's open front.
  C again (suit still in it): it shuts and lifts back off.
* **Step in:** walk into its open back or right-click it: the Sentry step-in. Once you are clear of the walls, the empty
  crate closes and lifts off.
* **Hold Shift+C:** the crate comes down round you open (walls splayed like petals, no floor), settles, you step out
  backwards (the Sentry eject) and aside to a corner, it closes and lifts; at the top the suit goes into your uplink.
* Static state: `HulkbusterCrate.FOLDING` (cleared in `ServerStateReset`). Tests: `IronManHulkbusterCrateGameTests`.

## Mk IX heat case (`MK9_HEAT_CASE`, built)

| | |
|---|---|
| Item | `mark_9_heat_case` "Mk IX Heat Case" (`MarkIXHeatCaseItem`, fire resistant). A plain JSON cube model (`models/item/mark_9_heat_case.json`, texture `textures/item/mark_9_heat_case.png`, swatch cells) -- the same cubes the prop draws, offset by `Mk9HeatCaseProp.JSON_OFFSET`. |
| Getting it | Like the Mk VI briefcase: right-click any loose Mk IX piece with all four in your inventory (`IronManArmorItem.packLoose`, the case takes the most worn piece's integrity), or fold a worn Mk IX. Also in the creative tab (a never-used case stands for a fresh suit, as the Mk VI's does). |
| Clock | 84 ticks (4.2 s). Set down 6..18, leaves open 18..28, boots 26, legs, arms + chest, helmet, faceplate 60..67, seal 70, shut 72, gone 84. |
| Prop | `Mk9HeatCaseProp`. The body pass draws the held item and records its live matrix every frame. The world pass takes the case out of the hand from that exact matrix (no jump), lowers it in front, tips it onto its back with the vents up, and swings both leaves over their outer top edges (246 degrees) to rest as braced legs. The interior glows (full-bright tray + red-hot plate slots), heat shimmer sheets and embers rise, and charcoal plates fly from the glow to the build height. On a fold it is all the same frames backwards, ending with the case flying back into the hand. In first person (no body pass) it starts from a fixed spot beside the right hand. Set down into lava, it floats on the surface. |

**The lava rule (fire-immune suits, `IronManSuit.fireImmune()`).**
* **Deploy:** the wearer is damage-immune for the whole deploy (`IronManDamage.suitUpImmune`), and wears the full suit
  (fire-proof) when it ends.
* **Fold:** every piece stays in its slot until the fold's last tick, so the full-set immunity
  (`IronManDamage.fireProof`) holds the whole way.
* **After:** if a sequence ends with the wearer in fire / lava / on a magma block / burning and **not** in the full suit
  (a fold, or a deploy from a case that was missing a piece), `PortableDeploy.heatGrace` puts the flames out and gives
  `HEAT_GRACE_TICKS` (200 = 10 s) of Fire Resistance, with an action-bar line, which is time enough to climb out.
* The case itself never burns (fire resistant item, like the Mk IX pieces).

## Fold polish (v0.16.7 user, stage 2)

* **Faceplate.** In a portable deploy / fold the faceplate never swings: it builds on (and comes off) texel by texel
  in its CLOSED place, like the rest of the helmet. The server still raises `IRON_MAN_FACEPLATE_OPEN` for the
  sequence (the seal beat), but `IronManAssemblyClient.tick` drops the client lift while
  `IronManPortableClient.form(p)` is set, and `PlayerModelMixin` ignores the flag then (no front-only hat layer / hair
  poking through). The eyes flash once when a deploy passes `sealAt`. The seal sparks
  (`IronManSuitUpManager.faceplateClose`) go to everyone but the wearer (they burst over the first-person view).
* **Mk IX lights are orange.** The glow pass (`RenderType.eyes`) is ADDITIVE over the lit base texel, so the old bright
  orange base + orange mask summed to yellow-white. `mark_9.png` now has deep orange glow texels (185,58,6) and
  `mark_9_glowmask.png` a red-heavy orange (225,72,0): orange by day and by night. `IronManSuitGlowLayer` tints the
  extra flash passes red / orange for `ORANGE_GLOW` marks so a flash does not saturate to yellow.
* **Packing loose pieces.** Right-click any loose piece of a suit with a form, with all four in the inventory
  (`IronManArmorItem.packLoose(player, suitId)`): Mk VI briefcase, Mk VII / Mk VIII packs (placed with `placePack`,
  never the hand), Mk IX heat case, Hulkbuster Uplink. An emptied packed item already carried is filled first.
* **Recipes** (crafting table, all give an EMPTY item -- `minecraft:container: []` -- so a crafted one never conjures a
  suit): `mark_v_suitcase` (gold ingot, 4 titanium-gold plate, servo, leather), `centurion_backpack` (2 leather, 5
  plate, flight stabiliser, servo), `space_thruster_pack` (2 leather, thermal shielding, 2 micro-thrusters, 3 plate,
  servo), `mark_9_heat_case` (blaze rod, 4 plate, magma cream, netherite scrap), `hulkbuster_uplink` (targeting module,
  2 Stark circuits, eye of ender, 3 plate). Tooltip: "Empty: right-click a loose piece...".
* **First person.** `ItemInHandRendererPortableMixin` swaps a carried pack's sprite for a bare hand, and hides the held
  case once a hand-carried form lets go of it (`firstPersonHidesHeld`: after `dropFrom` on a deploy, the whole fold).
  Flying plates / chips shrink away near the wearer's own eyes (`nearEyeScale`).
* **HUD.** `client/gui/IronManFoldHoldBar` (one call from `IronManHud.render`): beside the C box of the left key
  column, the packed item's icon, "FOLD INTO <item>" wrapped, and a Gauge filling over `FOLD_HOLD_TICKS`; gold when
  full; hidden once the fold starts. It stops short of the hotbar on short screens. Tests:
  `gametest/IronManFoldPolishGameTests.java` (3).

## Suit Platform pack / unpack (v0.16.7 user)

* **Pack:** with a Mk VI / VII / VIII / IX racked, the platform screen shows a tab off the panel's right edge (left
  edge on a narrow screen), "Pack into Briefcase / Backpack / Thruster Pack / Heat Case", wrapped onto two lines, with
  a wrapped tooltip. Pressing it (menu button 3, `IronManSuitPlatformBlockEntity.packSuitcase`) closes the screen and
  locks the rack. The stand plays the form's own build backwards over `PACK_FOLD_TICKS` (54): frames `lastAppear+1`
  down to the first stage's start, through `IronManGantryBuild.standForm` / `standFormFrame` and `portableTexture`, so
  it is the exact reverse of a worn deploy (backpacks lose legs first, the cases lose the helmet first), with a few
  sparks. Then the packed item grows at the chest, drops and hops off (`PACK_TICKS` = 70). `finishPack` writes the four
  pieces into the form's packed item. Pieces and item are stamped with the rack's integrity and energy
  (`stampPacked`), and the item goes to the player: backpacks via `PortableDeploy.placePack`, cases into the
  inventory, else dropped on the pad.
* **Unpack (docking):** the same ways the Mk VI briefcase always docked: right-click the platform holding the packed
  item, sneak + right-click empty-handed while carrying one, or shift-click it in the platform screen.
  `storeSuitcase` is all-or-nothing. It is refused if the rack holds another suit, and an emptied item racks nothing.
  A never-used item racks a fresh suit. The packed item's integrity and energy stamps go onto the racked pieces.
  The unfold is the fold played backwards (`UNPACK_TICKS`), with the rack locked until it stands complete.
* **Hulkbuster (`mark_8`, a BLOCK form) is excluded** (`platformForm` returns null for it). Its Uplink has its own path.
* **No Bracelets tab.** The platform no longer hands out Colantotte Bracelets. Menu button 2 is reserved and refused.
  The item, `ColantotteBracelets.claim` and a pair already owned keep working, so old saves are fine.
* Tests: `gametest/IronManPlatformPackGameTests.java` (5).

## Not done yet / known limits

* The carried Mk VII / Mk VIII pack is not drawn on your back in first person (only the held sprite is hidden).
* With the first-person body view, a Mk VI / Mk IX deploy's first `dropFrom` frames (0.2-0.3 s) show no case at all in
  your own view (the body view hides the held item; the floor prop starts at `dropFrom`).
* The fold-hold bar mirrors the server's timing on the client; it does not read the server's hold.

## The backpacks: Mk VII Silver Centurion + Mk VIII Space

| | Mk VII (`mark_6`) | Mk VIII (`mark_vii`) |
|---|---|---|
| Form | `PortableForms.MK7_BACKPACK` | `PortableForms.MK8_THRUSTER_PACK` |
| Item | `centurion_backpack` (`IronManPackItem`) | `space_thruster_pack` |
| Look | silver core, red top / silver bottom shell plates, blue strip | white core and shell over twin O2 tanks with gold caps |

* **Where the packed pack lives:** in the inventory, any slot. While it is carried it is worn on the back. The server
  mirrors the carried pack's suit id into the synced, non-persistent `ModAttachments.IRON_MAN_BACK_PACK` every tick
  (`PortableDeploy.backTick`), and every viewer's `IronManPortableClient.BodyLayer` draws the closed pack from it.
  The pack is pushed out over a worn vanilla chestplate and never drawn over an Iron Man suit. The held item is never
  drawn in the hand (`ItemInHandLayerSuitcaseMixin`). Why not the chest slot: the chestplate is the *first* piece a
  backpack puts on, so a pack in that slot would have to be moved out mid-deploy into an inventory that may be full.
  It would also collide with elytras and chestplates. Other players cannot see inventories, hence the attachment.
* **Fold placement:** `PortableDeploy.placePack` puts the pack in the first free main-inventory slot, then a free
  hotbar slot other than the selected one, then the selected slot, then drops it at the feet. It never goes into
  the hand.
* **Controls:** C (no suit on) or right-click deploys it. Hold Shift+C while wearing the suit folds it back. **Plain
  C while wearing the suit keeps the suit's old store** (Suit Platform / pod / bracelets). The pack is an extra way to
  carry the suit, so the hint-only C applies to non-BACK forms only (`IronManAbilities` SUIT_TOGGLE).
* **Timetable (76 ticks, `packStages()` / `packPose()`):** the shell blooms over frames 2-12. Then the chest
  (`TORSO_TOP` 12), the arms (sleeves, sliding upper arms, gauntlets) and the legs (`THIGH_BOTTOM` slides down,
  `PANTS`). Then the boots, the helmet over the top and the faceplate drops (seal at 64). The emptied pack folds flat
  into the back from 64. Direction is `DROP`. Pose: brace, arms wide while the chest and arms wrap, arms down and
  eyes down for the legs, chin up for the helmet, a nod, then settle.
* **Prop:** `client/ironman/BackpackProp` (one class, per-suit `Style`). Everything is drawn on the body; there is no
  world pass. The straps slide back over the shoulders into the pack, and the four shell plates swing out on their
  outer hinges and splay like petals. Plates stream out of the open core, round the side the target is on, and land
  where the build is at that moment. Their targets come from the form's stage windows, placed through the posed
  `ModelPart`s, so they follow the arms out. The model is a 64x16 swatch atlas, `textures/entity/iron_man_pack.png`.
* **Mk VIII O2 tanks:** `IRON_MAN_PACK_TANKS` (persistent and synced) is set when a thruster-pack deploy starts. It is
  cleared by `backTick` once the Mk VIII chestplate is off outside a sequence. The prop slides the tanks in flush
  during the final fold-flat, and `BackpackProp.renderWornTanks` keeps drawing them at that exact spot afterwards.
  A Mk VIII put on any other way (pod / bracelets / platform) has no tanks.
* **Tests:** `gametest/IronManBackpackFormGameTests.java` (6).

* The forms for the Mk VII, Mk VIII and Mk IX are not built yet.

* The Mk IX heat case is built (above). The forms for the Mk VII, Mk VIII and Hulkbuster are being built separately.
* The Mk IX's heat shimmer is drawn as faint glowing sheets (no screen-space distortion).
