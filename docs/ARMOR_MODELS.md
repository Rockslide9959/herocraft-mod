# Superhero Armour Models (GeckoLib)

Every custom armour **set** in the mod (Thor + the Iron Man marks) renders in-world through a
**GeckoLib** model, while each set keeps its own item ids, recipes, material, stats, powers,
abilities and creative-tab entries completely unchanged. This is a **visual-only** system.

`thor` uses the shared `crimson_vanguard` geometry. **Every Iron Man mark (1-7) has its own
geometry** (v0.6.5): each ships `geo/mark_<n>.geo.json` built from a bespoke plated model, textured
with a real 64×64 player skin rather than the crimson placeholder — see
[Iron Man marks: own geometry](#iron-man-marks-own-geometry) below.

GeckoLib: `software.bernie.geckolib:geckolib-fabric-1.21.1:4.9.2` (see `gradle.properties`
`geckolib_version`, `build.gradle`, and the `geckolib` depend in `fabric.mod.json`). Minecraft
1.21.1, Fabric, Java 21 — none of which changed.

---

## Where the files live

| Asset | Path | Notes |
|---|---|---|
| Geometry (shared) | `src/main/resources/assets/projecthero/geo/crimson_vanguard.geo.json` | `thor` only; GeckoLib scans `assets/*/geo/**` |
| Geometry (per mark) | `src/main/resources/assets/projecthero/geo/mark_<n>.geo.json` (`mark_1`, `mark_2`, `mark_iii`, `mark_4`, `mark_v`, `mark_6`, `mark_vii`) | one bespoke model per Iron Man mark; same bone names as `crimson_vanguard` |
| Animation | `src/main/resources/assets/projecthero/animations/crimson_vanguard.animation.json` | shared by **every** set, marks included — see [Adding animations](#adding-animations) |
| Textures | `src/main/resources/assets/projecthero/textures/armor/<set>.png` | one per set: `thor`, `mark_1`, `mark_2`, `mark_iii`, `mark_4`, `mark_v`, `mark_6`, `mark_vii`, plus `crimson_vanguard.png` (the default) |

## The classes

| Role | Class | Sourceset |
|---|---|---|
| Per-set visual config | `com.projecthero.mod.armor.ArmorVisualDefinition` (record: geometry, texture, animation) | main |
| Set → config registry | `com.projecthero.mod.armor.SuperheroArmorVisuals` (`get(setId)`, `register(setId, def)`) | main |
| Common armour item base | `com.projecthero.mod.armor.SuperheroArmorItem` (`ArmorItem` + `GeoItem`; abstract `armorSetId()`) | main |
| Thor pieces | `com.projecthero.mod.item.ThorArmorItem` (`armorSetId() = "thor"`) | main |
| Iron Man pieces | `com.projecthero.mod.ironman.item.IronManArmorItem` (`armorSetId() = suitId`) | main |
| Shared GeoModel | `com.projecthero.mod.client.render.SuperheroArmorModel` (forwards to `SuperheroArmorVisuals`) | client |
| Shared GeoArmorRenderer | `com.projecthero.mod.client.render.SuperheroArmorRenderer` | client |
| Client bridge | `com.projecthero.mod.client.render.SuperheroArmorRenderProvider` (a `GeoRenderProvider`) | client |

### Client / server split

`src/main` cannot see `src/client` (Loom `splitEnvironmentSourceSets()`). So `SuperheroArmorItem`
exposes a `static Consumer<Consumer<GeoRenderProvider>> rendererFactory`; `Project HeroModClient`
installs one that hands out `SuperheroArmorRenderProvider`s. On a dedicated server the factory stays
`null` and GeckoLib never asks for a renderer — nothing client-only is touched.

### How a slot picks its bones

`GeoArmorRenderer` does this itself, because every geometry (shared and per-mark alike) uses
GeckoLib's **default armour bone names**:

```
armorHead                     -> HEAD  slot
  helmet, faceplate           (faceplate is a separate bone: keep it that way)
armorBody                     -> CHEST + LEGS slots
  chest, waist, chest_armor, arc_reactor, back_panel
armorRightArm / armorLeftArm  -> CHEST slot
  <side>_shoulder / _upper_arm / _forearm / _gauntlet / _hand
armorRightLeg / armorLeftLeg  -> LEGS slot
  <side>_thigh / _knee / _shin
armorRightBoot / armorLeftBoot -> FEET slot
  <side>_boot
```

Player pose (walk / crouch / jump / swim / sprint / ride) and the mod's flight pose
(`HumanoidModelMixin`) are copied from the base `HumanoidModel` onto these bones automatically.

---

## Adding a new armour SET

1. Register its items as `SuperheroArmorItem` subclasses whose `armorSetId()` returns a new id.
2. Drop a texture at `assets/projecthero/textures/armor/<newId>.png`.
3. Add one line to `SuperheroArmorVisuals`' static block:
   `register("<newId>", new ArmorVisualDefinition(SHARED_GEO, Project HeroMod.id("textures/armor/<newId>.png"), SHARED_ANIMATION));`
   (Forget step 3 and it falls back to `DEFAULT` — crimson_vanguard — rather than crashing.)

## Giving a set its OWN model (not the shared one)

Ship `geo/<set>.geo.json` (+ optionally its own `animations/<set>.animation.json`), then change that
set's `register(...)` line in `SuperheroArmorVisuals` to point at the new geometry. **No** change to
`SuperheroArmorModel` or the renderer. Keep the bone names above if you want free pose-following. This
is exactly what every Iron Man mark now does, while still pointing at `SHARED_ANIMATION` — see
[Adding animations](#adding-animations).

## Adding animations

`crimson_vanguard.animation.json` already contains: `idle` (the only one played automatically, via
the `base` controller in `SuperheroArmorItem`), plus `assemble`, `disassemble`, `helmet_open`,
`helmet_close`, `faceplate_vent`, `panels_open`, `repulsor_aim_left/right`, `weapon_deploy_right`,
`flight_pose`, `landing`. To drive one from gameplay: register it as a triggerable animation on a new
`AnimationController` in `SuperheroArmorItem.registerControllers`, call
`GeoItem.registerSyncedAnimatable(this)` in the constructor, and fire it server-side with
`triggerArmorAnim(player, GeoItem.getId(stack), "controllerName", "animName")`. The suit-up /
suit-down / helmet systems (`IronManSuitUpManager`, etc.) are the natural callers — they are
untouched by this change and already know when each event happens.

Every mark geo reuses `crimson_vanguard`'s bone names, which is what lets `SHARED_ANIMATION` drive
all of them despite each having its own `geo/mark_<n>.geo.json`: GeckoLib builds each animation's
bone queue from whichever bones the *model* actually has, silently skipping any the clip names that
don't exist on that geometry (never an error) — see the per-mark breakdown below.

## Arc reactor / emissive

The `arc_reactor` bone exists and is independently animatable (the `idle` and `assemble` animations
already scale it). True emissive glow (a GeckoLib `AutoGlowingGeoLayer` + a `*_glowmask.png`) is a
deliberate follow-up — not wired yet so the model ships without needing per-set glow art.

---

## Cubes must never be coincident with the vanilla skin (z-fighting rule)

The GeckoLib shell renders *on top of* the player's own body model — it does not replace it, and
`PlayerModelMixin` only hides the **second** skin layer, not the base one. So any suit cube modelled
at the exact dimensions of a vanilla skin box with **`inflate: 0`** ends up coplanar with the player's
skin, and which surface wins the depth test flips with the camera angle — the classic symptom being
"the chestplate / arms vanish at some angles" in both the inventory doll and third person (v0.4.4 bug).

**Rule:** every cube in a suit geo must sit at least ~0.2 px (`inflate` ≥ 0.2, i.e. ~0.0125 blocks)
clear of the vanilla skin surface it covers. `crimson_vanguard.geo.json` had `inflate += 0.2` applied
uniformly to all 28 cubes in v0.4.4 (a Node pass over the JSON — every cube moved by the same amount,
so the artist's relative plate/shell layering is preserved). Every `mark_<n>.geo.json` conversion
(below) applies the same clearance to each imported pack. If you add a new geo or new cubes, bake the
same clearance in; do **not** ship a bare vanilla-sized box.

## Textures — current state

* `thor` and the `crimson_vanguard` default still ship `crimson_vanguard.png` — no bespoke Thor art
  yet.
* Every Iron Man mark (`mark_1` … `mark_vii`) now has **real, hand-authored art**: the actual player
  skin supplied for that mark, on that mark's own UV layout. None of them are the crimson placeholder
  or a desaturated copy of it any more.
* The old `assets/projecthero/textures/models/armor/<set>_layer_1.png` / `_layer_2.png` are unused
  (kept on disk only as art reference) — GeckoLib doesn't read vanilla armor-layer textures at all.

## First-person hand

`SuperheroFirstPersonArm` (client) draws the worn chestplate's forearm over the bare first-person
hand for any `SuperheroArmorItem`, via `PlayerRendererHandMixin` (TAIL of `PlayerRenderer.renderHand`).
Its mesh is a hand rebuild of the `armorRightArm` / `armorLeftArm` bone chain from
`crimson_vanguard.geo.json` (shoulder pad + upper arm + forearm + gauntlet, box UVs transcribed to
`texOffs`) — **not** per-mark geometry — but it still samples the correct per-set
`textures/armor/<set>.png`, so it matches each mark's own texture even though the first-person mesh
shape itself is shared. This was already true for `mark_1` before this change and needs no further
work for `mark_2` … `mark_vii`.

> **`PlayerModelMixin` must not `@Shadow` `hat`.** It is declared on the SUPERclass
> `HumanoidModel`, not on `PlayerModel`, and Mixin resolves `@Shadow` fields only against the target
> class itself. Shadowing it threw `InvalidMixinException` at APPLY time, while `LayerDefinitions.createRoots`
> was loading `PlayerModel` during the first resource reload — i.e. the client never reached the title
> screen (v0.4.1). The overlay parts are all public, so the mixin casts `(PlayerModel<?>) (Object) this`
> and reads them directly; that reaches inherited fields too. Do not `@Shadow` an inherited field here.

The vanilla sleeve (second skin layer) is hidden by `PlayerModelMixin` while any superhero piece is
worn, so nothing shows through.

**Sets that still need bespoke GeckoLib-UV art:** `thor` only — it remains on the crimson placeholder.
Every Iron Man mark is done.

---

## Iron Man marks: own geometry

Each `geo/mark_<n>.geo.json` is generated from an externally-authored model pack
(`mark<n>_armor.geo.json` + `mark<n>_skin.png`, one pack per mark, each with its own
`MARK<n>_MODEL_SPEC.json` declaring a texture SHA-256). The conversion is scripted (not hand-edited)
so all seven marks go through the exact same three fixes `mark_1` proved first, plus one texture-only
fixup two packs needed:

1. **Bone names → GeckoLib's armour names.** The packs use `head` / `body` / `right_arm` / `left_arm`
   / `right_leg` / `left_leg` under a `root` bone, plus plate/shell sub-bones (`faceplate`,
   `chest_plate`, `arc_reactor_housing`, `abdomen_plate`, `back_plate`, `<side>_shoulder`,
   `<side>_gauntlet`, `<side>_thigh_plate`, `<side>_knee`, `<side>_boot`, …). `GeoArmorRenderer` only
   knows `armorHead`, `armorBody`, `armorRightArm`/`Left`, `armorRightLeg`/`Left`,
   `armorRightBoot`/`Left`, and only those are posed from the player. `root` is dropped, the eight
   slot bones are made top-level, and every sub-bone is renamed to `crimson_vanguard`/`mark_1`'s
   existing names (`helmet`, `chest_armor`, `arc_reactor`, `waist`, `back_panel`,
   `<side>_thigh_plate`, …) — see [Adding animations](#adding-animations) for why the names matter,
   not just the hierarchy.
2. **Boots must not be children of the leg bones.** The packs parent `right_boot` under `right_leg`.
   `GeoBone.setHidden` also sets `childrenHidden`, so `armorRightLeg` being hidden (i.e. no leggings
   worn) would take the boots with it. They now hang off `armorRightBoot` / `armorLeftBoot`, exactly
   like `mark_1`.
3. **Z-fighting clearance + boot fit.** The packs' base body/limb cubes ship at exact vanilla skin
   dimensions with `inflate: 0`, and their second-layer shells at `inflate` 0.26-0.48 — both would
   coincide with (or sit *inside*) the player's own hat/jacket/sleeve/pant layer, the v0.4.4 bug
   described above. The conversion applies `mark_1`'s proven clearances (`inflate` 0.2 for base
   layers, 0.46-0.65 for shells, 0.2 for all plate/panel/gauntlet/knee/shoulder overlay cubes) and
   re-cuts each boot to `mark_1`'s exact footprint (widened in X/Z to `4.82` so it encloses the leg
   shell instead of z-fighting it; each boot's own pack height is kept).
4. **Texture alpha (mark_iii, mark_v, mark_vii only).** Three of the six packs (`mark3`, `mark5`,
   `mark7`) shipped their skin as an RGB PNG with the "should be transparent" regions baked as pure
   black (`0,0,0`) instead of an alpha channel — the other three (`mark2`, `mark4`, `mark6`) shipped
   proper RGBA. Verified against those three: every opaque pixel in them is far from black (no false
   positives) and their transparent-pixel masks land on exactly the same coordinates the RGB packs
   painted black. So for `mark3`/`mark5`/`mark7` only, pure-black pixels are losslessly converted to
   alpha-0 (RGB→RGBA, no other pixel touched) — this is a format restoration, not a repaint, and the
   result differs in bytes but not in any visible pixel from what a native RGBA export would have
   produced. `mark2`/`mark4`/`mark6`/`mark_1` are untouched, byte-for-byte copies of the supplied PNGs.

Sub-bone names deliberately match `crimson_vanguard`'s (`helmet`, `faceplate`, `chest_armor`,
`arc_reactor`, `waist`, `back_panel`, `<side>_shoulder` / `_upper_arm` / `_gauntlet` / `_thigh` /
`_knee` / `_boot`) so every mark reuses `SHARED_ANIMATION` unchanged — which it **must**, because
`SuperheroArmorItem.IDLE` hardcodes the literal name `animation.crimson_vanguard.idle`. `idle` drives
4/4 of its bones on this geometry; `flight_pose`, `landing`, `disassemble`, `panels_open` and the
helmet clips are all 100% too. The only no-ops are `<side>_forearm` / `right_hand`, which
`crimson_vanguard` splits the arm into and none of the mark packs have (their arm is one upper-arm
segment plus a gauntlet, same as `mark_1`) — GeckoLib builds its animation queues from the *model's*
bones, so absent bones are silently skipped, never an error.

Every mark adds one extra bone, `helmet_brow`, which is why it is in `SuperheroArmorRenderer`'s
`HEAD_ARMOR_BONES` — that list is the union across all geometries, and `getBone` returns an empty
`Optional` for sets that lack it (only `crimson_vanguard`/`thor` lacks it now).

**The textures are authoritative art, not placeholders.** `textures/armor/mark_<n>.png` for every mark
is the supplied skin for that mark (byte-for-byte for `mark_1`/`mark_2`/`mark_4`/`mark_6`; RGB→RGBA
restored per point 4 above for `mark_iii`/`mark_v`/`mark_vii`). Do not repaint, recolour, upscale or
regenerate any of them. They use the standard 64×64 player-skin layout, so the base body cubes use
vanilla box UVs and the plates sample the overlay regions.

| Mark | Set id | Source pack | Texture SHA-256 (as shipped in the mod) |
|---|---|---|---|
| 1 | `mark_1` | (pre-existing, v0.6.4) | `75fffe3be6c2b94728f18a76954d382d224e1d030d31eb2a94c72686befe25ae` |
| 2 | `mark_2` | `mark2_3d_model_pack_v2` | `7e66c327a0f0cd0ceefeccf46cb4f6854e214216855edf32c2c6c362770a4dc4` |
| 3 | `mark_iii` | `mark3_3d_model_pack` | `ffb38908fd309d3b2b97bd6a00c95c3016ac7130fcaae1ae7e53d40622b1d814` (RGBA restore of pack's RGB SHA `b44a03ec…`) |
| 4 | `mark_4` | `mark4_3d_model_pack` | `e701fc09504e67a9e021f3f946c2ddb0aa7e7285aef3ca84a7bed47399dad9d3` |
| 5 | `mark_v` | `mark5_3d_model_pack` | `2521fdc4174d430439922d219c06069315251649da3a5994499ab44e1ceca2c3` (RGBA restore of pack's RGB SHA `b035519e…`) |
| 6 | `mark_6` | `mark6_3d_model_pack` | `555a4f61594e74b48e946fc664680090efae7cdb21a10e4f0c0721b3bcb35648` |
| 7 | `mark_vii` | `mark7_3d_model_pack` | `0b715573e4fa9eddae69cece9bf6c0078d8c00b4530ca1b075741120e4cc4f78` (RGBA restore of pack's RGB SHA `4c63300b…`) |
