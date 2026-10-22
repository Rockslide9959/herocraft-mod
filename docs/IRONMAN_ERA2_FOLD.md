# Iron Man Era II -- portable forms (fold / deploy)

v0.16.7 (user), Era II stage 2. Every Era II suit packs away into a **portable form**: the Mk VI into a briefcase,
the Mk VII / Mk VIII into a backpack / thruster pack, the Mk IX into a heat case, the Hulkbuster into a drop crate.
One shared framework runs all of them. The Mk VI briefcase is built and proven on it. The other four forms are
built by registering a form and writing its prop renderer.

| Control | What it does |
|---|---|
| **C** (no suit on) | Deploys the first packed form you carry: the main hand first, then the rest of the inventory. Right-clicking the packed item does the same. |
| **Hold Shift+C** (wearing it) | Folds the suit back into its packed form after holding for `PortableDeploy.FOLD_HOLD_TICKS` (0.5 s). |
| **C** (wearing it) | Shows an action-bar hint ("Hold Shift+C to fold...") and nothing else. |
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

## Not done yet / known limits

* In **first person**, the wearer's own view still shows the vanilla held case while it deploys. The floor prop and
  the plates do show in first person. Only the third-person held item is hidden.
* There is no HUD fill for the 0.5 s Shift+C hold (the Era I eject has one in the C box).
* The forms for the Mk IX and Hulkbuster are not built yet (see the backpacks below for the Mk VII / Mk VIII).

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
