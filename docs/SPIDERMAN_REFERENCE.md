# Spider-Man — Project Hero reference

Added in **v0.6.3**. Package root `com.projecthero.mod.spider`, client half in
`com.projecthero.mod.client.spider`.

## v0.9.10 changes (at a glance)

- **Super leap buffed to ~10 blocks.** `SpiderAbilities.superJump` initial rise `1.05 → 1.35`
  (simulated peak ~10.1 under vanilla gravity/drag). Still sneak + jump on the ground, still a
  14-tick gate, still a passive (no slot).
- **The Symbiote — an *upgrade* for Spider-Man, not a standalone power.** New package
  `com.projecthero.mod.spider.symbiote` (+ `client.spider.SymbioteFxClient`). A Spider-Man who has
  **bonded** (`SymbioteState.hasSymbiote`) toggles the black suit with **H** (`SpiderActionPayload.TOGGLE_SYMBIOTE`
  → `Symbiote.toggle`, ~0.75 s anti-spam gate). H routing in `Project HeroModClient.handlePowerSelect`:
  a bonded Spider-Man gets **plain H = Symbiote**, **Shift+H = costume mask** (the old mask-only H is
  unchanged for everyone who has not bonded). Server-authoritative throughout — the client only asks.
  - **Suit** = 4 synthesised `SymbioteArmorItem` pieces (`extends SpiderManArmorItem`, so the shared
    renderer's mask handling and `SpiderMask.wearingHood` cover it for free). `armorSetId()` →
    `"spider_man_symbiote"` → `geo/spider_man_symbiote.geo.json` + `textures/armor/spider_man_symbiote.png`
    (converted from `spiderman_black_geckolib_bundle` by the now-arg-driven
    `scratchpad/convert_spiderman_geo.js`). **No durability** (unbreakable). `MaxSteelSuitArmor`-style
    equip/strip: any real armour is pushed to the inventory, never lost.
  - **Cannot be kept.** `Symbiote.enforce` (in `AbilityRouter.serverTick`, like `PunisherArmorGate`)
    strips every piece from anyone who is not an *active* bonded Spider-Man — traded away, chest-stored,
    ground pickup — and `Symbiote.tick` deletes any loose copy an active player pulls off. No duplicate
    functional suit can exist.
  - **Armour** = `ModArmorMaterials.SYMBIOTE` (4/7/9/4 = 24, toughness 3.0 — a notch above diamond).
    Purely equipment, so it appears/vanishes exactly with the suit; nothing to reconcile, nothing that
    stacks across toggles.
  - **Webbing** = `SpiderWebReserve.maxFor(player)` returns `MAX * 2` (200 → 400) while active. A pure
    function of the synced state, so activating only raises the ceiling (50/100 → 50/200, no refill) and
    `SymbioteModifiers.onDeactivate` clamps a now-over-cap reserve back to 200.
  - **Transformation FX** = `SymbioteFxClient` (client-only), reads the synced `SYMBIOTE_TRANSFORM`
    clock and sweeps squid-ink / smoke / reverse-portal particles feet → head (~1.2 s on, ~0.6 s
    retract). Suit is equipped/stripped immediately server-side; the crawl is cosmetic over the top.
  - **State** = `SymbioteState` attachment (`hasSymbiote` + `active` + toggle cooldown), persistent +
    `copyOnDeath`, synced to everyone. `active` is torn down on death / relog / dimension change / loss
    of the Spider-Man power (`SpiderMan.clearTransient` + `SpiderMan.revoke` → `Symbiote.clearTransient`);
    the bond is always kept.
  - **Testing:** `/spiderman symbiote give|remove [player]`; `/spiderman status` shows bond + active.
    `SymbioteGameTests` (9 tests).
  - **Deliberately NOT added yet** (spec §12): tendrils, rage, auto-regen, a health bar, Venom/Carnage,
    sonic/fire weakness, any new combat ability. `Symbiote` / `SymbioteModifiers` are structured so
    each of those is an added class + a `SymbioteState` field, not a rewrite.

## v0.9.12 changes (at a glance)

User-reported follow-up on the Symbiote and (for the duplication fix) Max Steel.

- **Progressive suit-up/suit-down, chest -> arms+legs -> head.** The Symbiote toggle is no longer
  instant -- it now runs a real animation clock exactly like `MaxSteelTransform`'s: `SymbioteState`
  gained `transformDir`/`transformStartTick`/`transformDurationTicks` (the old separately-synced,
  server-decremented-every-tick `SYMBIOTE_TRANSFORM` int attachment is gone -- one state write per
  transition now, not one per tick). New common `SymbioteTransform.effectiveProgress` (shared by
  server settle logic, the client bone reveal, and the client particle sweep, so all three always
  agree) and new client `SymbioteReveal` (the `MaxSteelReveal` pattern, sized to the Symbiote's 8-bone
  rig): `armorBody` reveals at 0.32, both arms + both legs + both boots at 0.60-0.70, `armorHead` at
  0.95 -- three clearly readable stages. `SymbioteFxClient` was rewritten to localise its particle
  cloud to whichever region is about to reveal (torso, then the four limbs, then the head) instead of
  a uniform vertical sweep, so the particles visibly finish covering a part just before its armour pops
  in. `TRANSFORM_TICKS` 24->**42** (~2.1s, long enough to read three stages), `RETRACT_TICKS` 12->**24**;
  suit-down runs the identical thresholds against inverted progress, so it peels off head first, then
  limbs, chest last. The suit is still equipped in full the instant the clock starts (matching Max
  Steel) -- only the client hides bones progressively. `toggle()` now refuses a press while an animation
  is already running (mirrors `MaxSteelTransform`'s `isAnimating` guard).
- **The suit now remembers what you were wearing underneath.** `SymbioteState` gained `stowedArmor`
  (an `ItemContainerContents`, the same component/codec `PunisherSatchel` uses for its persistent
  container). `SymbioteSuit.equipAndCapture` returns what each slot displaced; `Symbiote.beginSuitUp`
  stows it. On retraction (`restoreArmorAndFinish`, run at suit-down settle or immediately for any
  non-toggle teardown -- death/relog/dimension-change/power-loss) the real armour is handed straight
  back to the matching slot, falling back to the inventory only if that slot unexpectedly already holds
  something else. Replaces the old "push to inventory, re-equip manually" simplification (which Max
  Steel still uses, unchanged, since only the Symbiote was asked for this).
- **Fixed a real duplication exploit, and it also affected Max Steel.** Removing a suit piece through
  the ordinary inventory screen let the mod's own "keep the suit on" logic synthesise a *replacement*
  for the emptied slot while the original stayed behind as a real, storable, tradeable item -- repeat
  the removal and you have "infinite netherite/diamond-look armour". New
  `com.projecthero.mod.armor.PowerEquipmentLock` closes this the same way vanilla cursed armour is closed:
  every synthesised piece (Symbiote **and** Max Steel) is enchanted with the real
  `minecraft:binding_curse` on creation, which makes vanilla's own `ArmorSlot.mayPickup` (confirmed via
  `javap` on `ArmorSlot`/`EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE`) refuse every removal path
  in one place -- shift-click, drag, number-key swap, the drop key. A Creative-mode player (testing) can
  still take it off normally, same as any cursed item; both `SymbioteSuit`/`MaxSteelSuitArmor` still
  strip the suit themselves *before* death's equipment-drop code runs, so Curse of Binding never causes
  a death-drop. `PowerEquipmentLock.bind` also sets `ENCHANTMENT_GLINT_OVERRIDE=false` and
  `HIDE_ADDITIONAL_TOOLTIP` so the piece doesn't shimmer or show a "Curse of Binding" tooltip line --
  it should just look like the suit. The per-tick "delete any stray copy" sweeps
  (`SymbioteSuit.deleteLoose`, and Max Steel now has the same: `MaxSteelSuitArmor.deleteLoose` +
  `reequipMissing`, wired into `MaxSteelAbilityManager.serverTick`, plus a new `MaxSteel.enforce`
  gate in `AbilityRouter.serverTick` mirroring `Symbiote.enforce`) stay on as a backstop for anything
  that reaches a piece some other way (Curse of Binding doesn't stop an operator command, for instance).
  Also swapped the Symbiote item icons off `minecraft:item/netherite_*` (what actually looked like
  "netherite armour" sitting in the armour-slot UI) to a plain `black_dye` placeholder -- Max Steel's
  own diamond-icon models were left alone (not what was reported wrong).
- **Fixed the meteor's core (obsidian knot + sculk shrieker) and the Symbiote itself spawning floating
  above the crater instead of embedded in its floor.** Root cause: `SymbioteMeteorPiece.postProcess`
  placed that 3x3 platform by calling `level.getHeight(WORLD_SURFACE_WG, centerX, centerZ)` a *second*,
  independent time after the main carve loop had already run -- on any naturally uneven terrain, the
  centre column's pre-carve height can differ from its neighbours', so a platform derived from one
  fresh re-sampled point doesn't necessarily match the floor the carve loop actually put under the
  other 8 core columns (and the Symbiote's own spawn height, sampled later from the same point, followed
  the same bad number). Fixed by capturing the centre column's own just-carved floor Y *while the main
  loop is already processing it* (`centerFloorY`, a local captured at `dx==0 && dz==0`) and reusing that
  single live value for the whole 3x3 platform, instead of asking the heightmap again. `symbiotePos`
  (used by `SymbioteWorldgen` at ambience time, well after generation) was already the same safe
  post-generation pattern `SteelCrashSitePiece` uses and needed no change.
- Gametests: `toggleEngagesAndRetracts` / `spammingHNeverStacksAnything` / `deactivatingClampsExcessWebbing`
  updated for the animated toggle (a new `forceSettle` test helper rewinds the live
  `transformStartTick` and ticks once, the same trick `clearCooldown` already used for the toggle gate).
  New: `deactivatingRestoresTheStowedArmor`, `suitPiecesAreCurseOfBindingLocked` (Symbiote), plus on the
  Max Steel side `suitPiecesAreCurseOfBindingLocked` / `strayMaxSteelPieceIsDeletedFromANonSuitedPlayer`.
  Build green, **213 gametests**.

## v0.9.11 changes (at a glance)

- **Natural Symbiote acquisition** -- three routes, none of them requiring the
  `/spiderman symbiote give` testing command any more. New free-floating
  `spider.symbiote.entity.SymbioteEntity` (`extends Entity` like `SteelEntity`: no mesh at all, indestructible,
  a no-op client renderer, its whole visual is its own squid-ink/smoke/reverse-portal particle cloud).
  Right-click it as a Spider-Man to bond (`SymbioteBonding`, single-claim soft lock like Steel's); anyone
  else is refused and it recoils and waits for the next visitor.
  - **Meteor.** `symbiote_meteor` structure (`spider.symbiote.worldgen.SymbioteMeteorStructure/Piece`) --
    a rare above-ground impact crater (spacing 68/24), same procedural-bowl technique as the Mjolnir
    Crater / Steel Crash Site, blackstone/basalt/sculk debris with an obsidian + crying-obsidian core
    and a (non-summoning) sculk shrieker at the centre. The Symbiote hovers over the core.
  - **Lab.** `symbiote_lab` structure (`SymbioteLabStructure/Piece`) -- a rarer (spacing 84/32),
    half-buried 11x11 deepslate-brick containment room with a ladder shaft up to the surface, a 3x3
    tinted-glass containment cell at its centre (the Symbiote sits inside, on a lodestone pedestal, with
    sculk creeping out from underneath), redstone-lamp lighting, and 1-2 chests on the shared
    `chests/research_facility` loot table.
  - Both structures share one spawn pipeline: `spider.symbiote.worldgen.SymbioteWorldgen` (deferred
    one-tick chunk-load placement, exactly like `SteelCrashAmbience` -- `getHeight`/`addFreshEntity`
    from inside `CHUNK_LOAD` deadlocks the server) + `SymbioteSpawnState` (one shared `SavedData` key
    set, "spawn exactly once"). Both pieces implement `SymbioteSpawnPiece.symbiotePos(LevelReader)`.
  - **Rare mob host.** `SymbioteHost` -- a very rare (`HeroConfig.symbioteHostChance`, default 0.0015,
    ~1 in 650) roll on any naturally-spawning vanilla hostile (excludes this mod's own raid mobs, the
    Warden, Creeper/Slime/MagmaCube/Ghast/Phantom/Shulker/Enderman, and the three vanilla bosses),
    delivered by a new `Mob#finalizeSpawn` mixin (`mixin.MobSymbioteMixin`, the multi-mob analogue of
    `ZombieSpawnMixin`). A host is mutated in place (base attributes bumped: +60% max health, +35% move
    speed, +5 melee, +24 follow range, +0.4 knockback resistance -- all on the persisted *base* value, so
    a chunk reload needs no re-apply and `mark()` can only ever run once), gets an italic "Symbiote Host"
    name and `setPersistenceRequired()` (never despawns), and a constant black particle aura driven by
    the same mixin's `aiStep` TAIL hook (one cheap attachment null-check per mob per tick). On death
    (`ServerLivingEntityEvents.AFTER_DEATH` -> `SymbioteHost.onDeath`) the Symbiote leaves the corpse as
    a free `SymbioteEntity` and every player within 28 blocks is told.
  - Testing: `/spiderman symbiote spawn` (drop one 3 blocks ahead) and `/spiderman symbiote host` (mark
    the nearest hostile within 12 blocks). `/locate structure projecthero:symbiote_meteor` /
    `projecthero:symbiote_lab`. `SymbioteGameTests` gained 3 more tests (types registered, host buffs +
    persists, a free Symbiote bonds only a Spider-Man). Build green, **209 gametests**.

## v0.9.8 changes (at a glance)

- **Suit model replaced again** with the *Brand New Day* GeckoLib entity bundle
  (`3d minecraft models/Spider-Man/spiderman_bnd_geckolib_bundle`). Same pipeline; all six parts have
  a 2nd-layer overlay this time. Texture is back to the original v6 skin (sha `168086bb…`).

## v0.9.7 changes (at a glance)

- **Suit model replaced again** with the *classic* GeckoLib entity bundle
  (`3d minecraft models/Spider-Man/spiderman_classic_geckolib_bundle`). Same conversion pipeline as
  v0.9.6; head/body keep a 2nd-layer overlay (now pinned to a fixed +0.25 over the base rather than
  the source's oversized +0.5 hat inflate), arms/legs are single-cube. Texture swapped again.

## v0.9.6 changes (at a glance)

- **Passive Regeneration lowered II → I** in `SpiderPassives.tick` (the outheal-damage role now sits
  with Super Regeneration, not Spider-Man's passive).
- **Web Reserve doubled 100 → 200** (`SpiderWebReserve.MAX`). Regen rate and all ability costs
  unchanged, so effective sustain roughly doubles.
- **Suit model replaced** with a GeckoLib entity-bundle model, converted to the armour rig by
  `scratchpad/convert_spiderman_geo.js` (box-UV, `+0.30` inflate, synthesised short boot bones).
  Texture `textures/armor/spider_man.png` swapped to the bundle's skin. *(Superseded by v0.9.7.)*

## v0.9.5 changes (at a glance)

- **Passive Regeneration II** added to the hidden infinite-effect set in `SpiderPassives.tick`
  (alongside Speed II / Resistance II), removed in the reconcile `else` with the power.

## v0.9.4 changes (at a glance)

- **Melee buffed.** Unarmed `ATTACK_DAMAGE` modifier +7 → **+9** (unarmed hit = 10). Sprint-punch
  addend in `SpiderPassives.onSpiderOutgoingDamage` +2 → **+3**, so an unarmed sprinting hit lands as
  **13** — a "+12" punch.

## v0.9.3 changes (at a glance)

- **Double-tap-jump-against-a-wall grab removed.** It collided with the double jump. Wall crawling now
  engages purely from a mode being on: Spider-Man's slot-6 wall-crawl toggle, or Spider Adhesion's
  Adhesion Mode / Wall Grip toggle — surface contact in mid-air with that mode on sticks automatically
  (`SpiderClimb.updateAttachment` `autoStick`, `SpiderInputClient` grab branch deleted). `requestGrab`
  (used by shift + Web Zip) is kept. Double-tap sneak still drops you off a wall.
- **Spider-Sense threat glow is now strictly per-viewer.** The server no longer sets the vanilla
  `glowingTag` on threatening mobs (which synced the outline to everyone). Instead `SpiderSense.serverTick`
  sends the current threat entity-id set only to that player via `SpiderSenseGlowPayload` (S2C);
  `SpiderSenseGlowClient` holds it (expires ~25 t after the last packet); `EntityGlowMixin` outlines
  exactly those ids red for that viewer. Nobody else sees or benefits from another player's danger sense.
  `SpiderSense.tick`/`clearThreatGlow` + the `ServerStateReset` hook are gone.

## v0.6.20 changes (at a glance)

- **Removable costume mask** (`SpiderMask`, `ModAttachments.SPIDER_MAN_MASK_OPEN`): H while wearing
  the `SpiderManArmorItem` head piece pulls the mask off / on. Renderer drops the whole `armorHead`
  bone; `PlayerModelMixin.helmetRetracted` extended. Reconciled from `AbilityRouter.serverTick`.
- **Web line origin** resolves the fixed swing-arm pose's fist tip directly
  (`feet + up·2.03 + bodyRight·±0.41 + bodyForward·0.36`) instead of a guessed shoulder + walk-to-anchor.
- **Web Shot rework**: no cooldown, 2 webbing, 2 damage; stickiness stacks (SLOWNESS + lower jump,
  max 3, 3 s window anchored to the first hit); 3rd stack → 12 s cocoon. See §Web Shot below.

## v0.6.17 changes (at a glance)

- **Melee bonus +3 → +8.** Passive **Speed II** and **Resistance II** added (hidden, infinite,
  refreshed in `SpiderPassives.tick`). Jump strength +20% → **+30%** (~2-block plain jump).
- **Sneak + jump on the ground = a ~6-block super leap** (`SpiderAbilities.superJump`, `SUPER_JUMP`
  packet, 14-tick gate). Passive, not a slot.
- **Double-jump "makes you fly" fixed.** Root cause: Web Zip / a stale swing left `mayfly` granted,
  so vanilla's own double-tap-jump toggled creative flight. Web Zip no longer grants the anti-float
  tolerance and `SpiderSwing.serverTick` hands `mayfly` back the moment a swing isn't holding the
  player up. Step assist no longer trips the double jump (`horizontalCollision` refusal, both sides).
- **Web Swing** drains far less (`DRAIN_SWING_PER_TICK` 0.2 → 0.04, fire cost 1 → 0.4) and now has an
  **arm animation**: the firing hand alternates each swing (`SpiderManState.swingHandRight`, drives
  `SpiderWebLineRenderer`'s line origin + a raised-arm pose in `HumanoidModelMixin`).
- **Web Zip range 28 → 100.**
- **Web Cocoon (C) replaced by a Wall Crawl toggle** (`SpiderAbilities.WALL_CRAWL`,
  `SpiderManState.wallCrawlEnabled`). While on, surface contact in mid-air sticks automatically (no
  double-tap); sneaking while clung locks you to the exact spot like a ladder rung
  (`SpiderClimbMovement`). The cocoon pin is now automatic: **three Web Shots inside the window pin
  an ordinary mob for 8 s** (`SpiderWebs.cocoonFor` / `WEB_SHOT_COCOON_TICKS`).
- **Spider Sense overhaul** — see §6.

## v0.6.18

- **Craftable Spider-Man Suit** — a 4-piece cosmetic armour set (`spider_man_suit_{helmet,
  chestplate,leggings,boots}`, `SpiderManArmorItem` → `armorSetId()` `"spider_man"`,
  `ModArmorMaterials.SPIDER_MAN` ≈ chainmail-tier, no toughness). Renders through the shared GeckoLib
  armour path via `geo/spider_man.geo.json` (converted from the v6 3D suit pack by
  `scratchpad/convert_spiderman_geo.js` — rename to the armour rig, promote boots to top-level bones,
  uniform `inflate += 0.30`) over the untouched `spiderman_skin.png` (`textures/armor/spider_man.png`).
  String + red dye recipes; in the superheroes creative tab. **Grants nothing** — pure costume,
  no lifecycle entanglement with the Hero Class.

## v0.6.19

- **Web line comes from the hand, and tracks it.** `SpiderWebLineRenderer` and
  `SpiderAbilities.handPos` now derive the origin from the **body** yaw (not the view) at the
  shoulder, then walk out along the raised arm toward the anchor — no more "webs from the armpit".
- **Swing momentum up slightly** — forward-input push `0.055 → 0.075`, bottom-of-arc push
  `0.022 → 0.030`, facing-align `0.012 → 0.016`, speed clamp `2.4 → 2.7`.
- **Spider Sense:**
  - **50% auto-dodge bug fixed.** `now - lastDodge < DODGE_INTERVAL` with `lastDodge = Long.MIN_VALUE`
    for "never dodged" overflowed to a large negative → the guard always tripped, so a player who
    had not dodged yet (i.e. everyone) never could. Now `dodgeOffCooldown` is a plain ready-at check.
  - **No fall damage at all.** `SpiderPassives.onAllowDamage` now vetoes every `IS_FALL` hit; the
    fall *warning* (kind 3) was retired with it.
  - **Multiple threats at once, held until clear.** One HUD marker per `kind` (`SpiderHud`), each
    refreshed every scan and lingering ~22 ticks — so markers stay lit while the danger is present
    and only fade ~1 s after it passes. The chime/particle cue is throttled per kind so a persistent
    threat is not a persistent chime.
- **Web Zip** — pull curve raised (`0.55 + d·0.055` capped 1.75 → `0.85 + d·0.075` capped 2.6).
  **Sneak + Web Zip** onto a surface arms adhesion (`SpiderClimb.requestGrab`) and lands flush, so
  you stick and wall-crawl on arrival with no double-tap.
- **Web Yank** — range `24 → 100`; yanked items now fly **straight at the player's chest**, a hard
  pull with only a token upward bias (no arc).
- **Web Net** range `22 → 35`.
- **Double jump** — cooldown `40 → 20` ticks (1 s); the HUD no longer shows it at all.
- **Super leap** (sneak + jump) initial rise `0.95 → 1.05` — clears a full 6-block pillar.

Spider-Man is a **Hero Class**, the same tier as Thor and Tony Stark: its own attachment, its own
progression, permanent, surviving death and relog. It is *not* one of the 27 experimental mutations —
but unlike the other two, its progression starts inside the mutation system.

---

## 1. Getting it

```
Normal player
    ↓  Adhesive Mutation Serum, then survive a Cave Spider bite
Spider Climbing / Adhesion   (experimental power 16)
    ↓  craft and use an Arachnid Mutagen
SPIDER-MAN                   (Hero Class)
```

You cannot obtain Spider-Man directly. The **Arachnid Mutagen** refines an arachnid adaptation that
is already there; it does not create one.

- Used **without** Spider Adhesion: nothing happens, **the item is not consumed**, and the player is
  told *"Your body lacks the arachnid adaptation required for this mutation."*
- Used **with** Spider Adhesion: the mutagen is consumed, Spider Adhesion is **removed** (freeing the
  mutation slot it occupied), the Hero Class is granted, and the transformation plays — web and crit
  particles, a spider/beacon/level-up sound layer, and a short Speed + Strength pulse. Then
  *"Your arachnid abilities have evolved."* and *"Hero Class Acquired: Spider-Man"*.

The two are **never** held at once. Everything Adhesion could do, Spider-Man does better.

### Arachnid Mutagen recipe

```
 Phantom Membrane │ Echo Shard        │ Phantom Membrane
 Spider Eye       │ Golden Apple      │ Spider Eye
 Fermented S. Eye │ Amethyst Shard    │ Fermented S. Eye
```

Pitched at the same tier as the Power Suppressor (which also wants echo shards and amethyst): the
echo shard means an Ancient City, so this is deliberately not an early-game item, but nothing here is
needed in absurd quantities.

---

## 2. Controls

The six universal HeroPack slots, unchanged — no new keybinding category, no duplicate inputs.

| Key | Slot | Ability | Cost | Cooldown |
|-----|------|---------|------|----------|
| **R** | 1 | Web Swing | 1 + 0.2/tick attached | none (reserve-gated) |
| **G** | 2 | Web Zip | 5 | 1 s |
| **Z** | 4 | Web Shot | 2 | none (v0.6.20) |
| **X** | 3 | Web Yank | 6 | 2 s |
| **C** | 6 | Wall Crawl (toggle) | 0 | none |
| **V** | 5 | Web Net | 20 | 8 s |

Wall crawling, ceiling crawling, Spider Sense, the double jump and the physical enhancements are
**passives** and occupy none of the six.

**Grabbing a surface (v0.6.6).** Adhesion no longer engages just because a wall is in reach — that is
what used to glue the player to anything they ran or jumped past. Instead: **double-tap jump** against
a wall or ceiling to grab it, **double-tap sneak** (or simply reach the ground) to let go, and a
single **jump** while stuck leaps off. The same gesture drives Spider Adhesion once its Wall Grip /
Adhesion Mode toggle is on.

Spider-Man holds the six slots whenever the player has not deliberately selected one of their
experimental mutations from the power wheel (**H**). Selecting a mutation gives it the slots as
usual; the wheel's first row reads *Spider-Man* instead of *None* for a Spider-Man player, and picking
it hands the slots back. **The passives are never affected by that choice.**

Router priority is Thor → Iron Man → Spider-Man → experimental power. A player who is *also* Tony
Stark will find `C` opening the call-armour picker rather than firing a cocoon, exactly as `C` already
outranks every experimental power's slot 6.

---

## 3. Spider Adhesion — what the overhaul changed

The old implementation was a single `LivingEntity#onClimbable` override plus a couple of server-side
velocity nudges. That handed the player to vanilla's **ladder** physics, which is why it felt
limited:

- a ladder makes you *sink* at 0.15 blocks/tick whenever you stop pressing forward, so holding
  position on a wall was only possible while sneaking;
- climbing is driven by forward input alone, so strafing across a wall did nothing;
- vanilla has no upside-down movement at all, so ceilings were impossible;
- the attachment test was `player.horizontalCollision` — one boolean, with no notion of *which*
  surface you were on. It drops for a tick at a block boundary or an inside corner, gravity takes
  over, and you fall.

**What replaces it** (`SpiderClimb` + `SpiderClimbMovement`):

- **An explicit grab intent (v0.6.6).** `SpiderClimbLocal.grabIntent`, set by a `CLIMB_GRAB` packet
  (double-tap jump against a surface) and cleared by `CLIMB_RELEASE` (double-tap sneak), by a leap,
  or automatically on touching the ground / entering water / starting a swing / losing the power.
  `updateAttachment` returns `null` whenever it is unset, so brushing a wall while walking or jumping
  past it does nothing. Per-side like the rest of `SpiderClimbLocal`: the owning client sets its copy
  the instant the gesture fires, the server sets its copy from the packet.
- A real attachment: a surface `Direction`, chosen from every face actually touching the player's
  hull, kept sticky across ticks and released only after a grace period (8 ticks for Spider-Man,
  5 for Adhesion). That grace is what carries the player around inside corners, over block seams and
  across the lip of a slab instead of dropping them, and it is why the attachment cannot flicker.
- All movement expressed **relative to the held surface**, so one piece of code drives a wall, a
  ceiling and every transition between them.
- The player's **look** direction, projected onto the surface plane, becomes "forward". Look up a wall
  and W climbs; look sideways and W crawls sideways; look down and W descends. The camera is never
  seized or snapped — the input is reinterpreted instead.
- No input means **stop**, held by a small pull into the surface. Sneak holds harder and refuses to
  move at all.
- Gravity is not applied while adhered, so a ceiling crawl is a crawl, not a fall.
- Jump pushes off along the real surface normal, with a short lock-out so the engine does not simply
  re-grab on the next tick.

Surface detection is generous by design: *any* collision geometry can be gripped, which is what makes
slabs, stairs, fences, logs, leaves-with-collision and irregular cliff faces work without a per-block
allow-list. Passable decoration (grass, torches) has no collision shape and correctly does not hold
you up.

**Wall → ceiling** happens when the head genuinely touches an overhang (a 0.30-block probe above the
hull); the ceiling is checked before the horizontal faces, so climbing to the top of a wall and
continuing carries you onto the ceiling. **Ceiling → wall** is the harder direction and gets a second
detection pass over a box raised 0.4 blocks: coming off the end of an overhang, a wall that continues
upward starts at or above the ceiling plane and is not yet beside the player's hull, so the ordinary
pass cannot see it. The grace period covers the tick or two in between.

**Existing saves need no migration.** Spider Adhesion still lives in `ExperimentalState` under the
same key with the same two toggles (`wall_grip`, `adhesion_mode`); those toggles now resolve through
the shared engine instead of the `onClimbable` override. A player who had the power before 0.6.3
keeps it, gets the better climbing immediately, and can evolve later.

---

## 4. Web Swing

`SpiderAnchorSearch` + `SpiderSwing` + `SpiderSwingClient`.

### Hybrid anchoring

Pressing **R** fires a ray search, not a block scan: a fan of 28 clips (4 pitches × 7 yaws) through a
cone above and ahead of the player, on the tick the swing starts and never in between. Aim direction
blends the camera with current travel, so a swing chains forward out of the previous one instead of
stalling when the camera drifts.

Candidates are scored, not sorted by distance:

```
score = min(height,22)·1.4  +  min(forward,22)·1.1  +  (14 − |dist − 20|·0.7)
```

with hard rejections for anything below 6 or above 32 blocks, less than 2 blocks above eye level, or
behind the player. Real geometry always wins — rooftops, cliffs, canopy, cave ceilings — so a city, a
mountainside or a dense forest gives longer, faster, more dramatic arcs than open ground, and a player
who swings out of the plains and toward a mountain starts catching real cliff faces automatically.

When nothing real is in reach the search **fabricates** a point instead, ahead of the player and well
above them (7.5–12.5 blocks out, 13–16 up, scaled by current speed), so traversal never stops in
plains, desert, ocean or sparse forest. Nothing in the player-facing text ever calls it that; it just
looks like the web went up off-screen.

### The rope

A soft pendulum, not a rigid rope. Once the line is taut, outward radial velocity is removed and a
spring pulls back toward rope length; gravity comes from vanilla, so the drop into the bottom of a
swing is genuinely gravity doing it. On top of that: a push through the bottom of the arc (strongest
with the anchor overhead), a small bias toward the facing direction, and damping of the lateral wobble
a bare pendulum builds up, so swings do not spin. Speed is clamped at 2.7 blocks/tick (v0.6.19).

Rider input: **W** builds momentum, **S** brakes (sheds speed rather than reversing the arc), **A/D**
steer at a rate that cannot snap the direction round, **jump** reels in, **sneak** pays out, **release
R** detaches. Release deliberately does **not** touch velocity, so a well-timed release keeps every bit
of the speed the arc built.

### Altitude rule

Real anchors have none — climbing genuine terrain is the reward for finding it, and each real anchor
resets the baseline to wherever the player currently is.

Fabricated anchors record the altitude the sequence began at and stop *adding* lift once the player is
**12 blocks** above it, fading out across the last 4 rather than stopping dead. Horizontal swinging is
never touched and the player is never slammed down; the arcs simply stop climbing. The server keeps a
backstop at +16 that drops the line outright, for a client that ignored the fade.

### Where the physics run

The rope model is in common code and the **owning client** runs it on its own player every tick — the
same split Super Speed already uses. That is why the arc has no round-trip latency and nothing to
rubber-band against. The server does not push velocity packets at a swinging player; it chooses the
anchor, charges the reserve, enforces the altitude rule, drops the line when the anchor stops being
valid (block mined, dimension changed, rope absurdly stretched), and is the only thing that can end
the swing. Rope length is client-local — the one swing value nothing else needs to see.

Swinging adds **no per-tick packets** beyond ordinary player movement: the attachment syncs on
attach and release, and the web reserve syncs only when its whole-point value changes.

### Fall protection

**v0.6.19: Spider-Man takes no fall damage, full stop.** `SpiderPassives.onAllowDamage` vetoes every
`IS_FALL` hit. (`SAFE_FALL_DISTANCE +9` / `FALL_DAMAGE_MULTIPLIER −50%` are still applied but are now
belt-and-braces.) The old rule was: free only while adhered/swinging or within 3 s of a web ability.

### Rendering

`SpiderWebLineRenderer` draws the line straight into the world-render buffer for every swinging player
the client can see, with a slight parabolic sag. **No entity of any kind is created**, so there is
nothing to accumulate, nothing to leak, and nothing left behind on release — the state flag flips and
the line stops being drawn on the next frame. The server adds one web particle at the line's midpoint
every 4 ticks so onlookers see something without a hundred particles a second.

---

## 5. The other five abilities

**Web Zip (G).** Fires at the surface you are looking at (100 blocks) and pulls you to it. Speed
scales with distance (**v0.6.19: 0.85 + 0.075·d, capped at 2.6**) plus a little lift so a zip to a
ledge clears its lip. Applied as **velocity, never a teleport**, so collision resolves it and there
is no clipping. A fifth of prior momentum is carried in and all of it out, which is what lets a zip
feed straight into a wall crawl or a swing. Breaks an active swing so the two never fight over the
player. **Sneak while zipping** (v0.6.19, reworked v0.6.21): fires *straight at the exact point*
aimed at, a touch faster (`min(3.0, 1.1 + 0.08·d)`), no lift, and drops leftover sideways momentum
so you cannot skate past the lip. Adhesion is armed on **both sides** — `SpiderClimb.requestGrab` on
the server *and* a `SpiderClimbGrabPayload` marker to the owning client (whose unsynced
`SpiderClimbLocal` actually drives the movement sim) — so the instant the zip plants you on the wall
the climb engine sticks, no double-tap.

**Web Shot (Z).** Webbing at the aimed entity (26 blocks). **v0.6.20:** no cooldown, costs 2 webbing,
deals 2 damage. Each hit is a **stickiness stack** (`SpiderWebs.WEB_STICK`, max 3) — SLOWNESS
(amp `min(4, stacks)`) + negative JUMP boost (`-(stacks+1)`, i.e. a lower jump) for 6 s. The window is
**3 s anchored to the first hit and does not extend**: land the next hit inside it to build the stack,
after it and the count restarts at 1. **Third stack → 12-second cocoon** (`WEB_SHOT_COCOON_TICKS`) and
the stacks clear. Bosses take the slow but are capped at amplifier 1, get no JUMP debuff and can never
be pinned. Extinguishes a burning target, the player, and small fires within a block or two of the impact.

**Web Yank (X).** Range **100 blocks** (v0.6.19). Items first (a modest aim cone, since a strict ray
misses them): dropped items are yanked **straight at the player's chest** with no pickup delay — a
hard distance-scaled pull (`0.9 + 0.13·d`, capped 3.2) with only a token upward bias to unstick them
from the floor, no arc (v0.6.19; was a lobbed arc to grab height). The point of having this at the
edge of a ravine or a lava lake.
Otherwise the outcome depends on mass (`height × width × (1 + maxHealth/40)`): light things come to
you, heavy things move a little while pulling you a little, and a boss or anything with full knockback
resistance pulls **you** to **it** instead. No yo-yoing a boss.

**v0.7.2 — flying targets.** A hovering mob (Vex, Phantom, Allay, bats) is rarely dead-centre on the
crosshair and is often "behind" a block it just phased through, so the strict view-vector raycast
misses it. Web Yank now has a loose **aim-cone fallback** for living entities that ignores walls
(`nearestLivingInAim`, ~13°), and an airborne target gets a dedicated branch: a hard pull straight at
the chest (`1.1 + 0.12·d`, cap 3.0), gravity re-enabled and de-aggroed so it actually arrives instead
of orbiting back up. `isAirborneTarget` = `isNoGravity() || FlyingMob || Vex || AmbientCreature`.

**Web Cocoon.** 5 seconds of near-total restraint for an ordinary mob — Slowness VI, Weakness III,
Mining Fatigue IV, target cleared, horizontal motion zeroed each tick (vertical is left alone so it
still falls). A boss gets **2 seconds** of a much weaker version (Slowness III, no weakness, no
pinning). PvP is halved, like every other hard crowd-control ability in the mod.

**v0.7.2 — a cocoon reads as one now.** While wrapped, a mob throws off a **cloud of cobweb
particles** clinging to its whole body (`SpiderWebs.emitCocoonParticles`, every 2 ticks) so you can
see at a glance that it is stuck. And a cocooned mob **cannot use ranged attacks** — a webbed
skeleton, stray, illusioner, pillager, piglin or witch stops shooting / throwing potions for the
duration. Implemented by cancelling `canUse` / `canContinueToUse` on `RangedBowAttackGoal`,
`RangedCrossbowAttackGoal` and `RangedAttackGoal` while `SpiderWebs.isCocooned(mob)` (three server
mixins). Blaze/ghast/shulker fireballs and llama spit are not covered (different goals).

**Web Net (V).** Range **35 blocks** (v0.6.19). A 2-block-radius disc of cobweb placed through the mod's existing `TempBlocks`, which
already owns restore-on-expiry, replaceable-only placement, a global cap and drop-everything-on-
server-stop. Lasts **25 seconds** and puts back exactly what it covered, so it cannot litter or grief.
Non-owners inside it also take Slowness IV. Respects `abilityTerrainDamage` in the config like every
other block-placing ability; with it off, the ability reports that it could not spin a net.

**Spider-Man passes through cobweb.** `EntityWebMixin` cancels `makeStuckInBlock` for a Spider-Man
player on cobweb specifically, so his own nets are footing and cover rather than a trap — and mineshaft
webs stop slowing him too. Every other entity, including other players, is stuck normally, which is
what makes Web Net a usable trap.

---

## 6. Passives

**Spider Sense** (`SpiderSense`, overhauled v0.6.17). The reactive half is still the `ALLOW_DAMAGE`
veto; a throttled per-player scan (`serverTick`, sub-scans every 5–10 ticks) now also looks ahead:

- **Threat / directional warning** — a mob/player locked onto you within ~24 blocks, an attack
  winding up in melee range (`swinging` / `attackAnim`), a hostile projectile closing on you, a
  primed explosion (TNT / swelling creeper / end crystal), or a hazard beside you (lava within 2, a
  `FallingBlockEntity` overhead) each fire `SpiderSenseWarningPayload` (S2C: kind, absolute yaw,
  above/below). **v0.6.19:** `SpiderHud` keeps **one marker per kind**, so several show at once, and
  each is re-sent every scan and lingers ~22 ticks — the marker stays lit the whole time the danger
  is sensed and fades ~1 s after it is gone. The chime/particle cue is throttled per kind
  (~24 ticks) so a lasting threat is not a lasting chime. (Kind 3, "fall", was retired — no fall
  damage to warn about.)
- **Attack prediction** opens a ~12-tick **perfect-dodge window**. Move / jump / crouch during it
  (`isEvading`) and the next eligible hit is a guaranteed negation (still bounded by the 6-tick dodge
  floor). v0.6.21: this negates the hit only — it does not move the player.
- **Projectile deflection** — a dodged non-arrow projectile (trident, fireball, snowball) is flung
  back at the shooter ~60% of the time instead of merely missing.

*Simplifications (noted in the class):* attack prediction is a proximity + swing-animation
heuristic, not true animation timing; environmental danger covers lava and falling blocks.

On an eligible hit the original behaviour still runs:

- a subtle cue fires (a quiet chime plus, **v0.6.21**, a short arrow of particles springing from in
  front of the face and pointing *straight at* the threat — sent **only to the Spider-Man player**,
  never to bystanders);
- a roll made on the server cancels the damage — **50% for melee hits, and (v0.7.5) 60% for
  ranged / projectile hits** (`isRanged` checks for a `Projectile` direct entity;
  `RANGED_DODGE_CHANCE`). **v0.6.21: the dodge no longer moves the
  player** — the forced sideways/back velocity + per-hit motion packet fought with web-swing and
  wall-crawl (the player got yanked off their arc). The hit is simply negated; the player keeps their
  own momentum. A 6-tick floor between dodges remains. **v0.6.19 bug fix:** the interval guard did
  `now - lastDodge < 6` with `lastDodge = Long.MIN_VALUE` for "never dodged" — the subtraction
  overflowed negative, so the guard always tripped and a player who had not dodged yet (everyone)
  could never auto-dodge. It is a plain ready-at check now (`dodgeOffCooldown`);
- if the dodged thing was an arrow, it is **caught**: the projectile is discarded and exactly one
  matching item is added — `getPickupItemStackOrigin()`, so a tipped or spectral arrow comes back as
  itself. A full inventory drops it at the player's feet rather than eating it. One projectile can
  never produce more than one item.

Ineligible by design: the void, `/kill`, starvation, drowning, fire, freezing, falls, cacti, wither,
magic, and anything tagged `BYPASSES_INVULNERABILITY` / `BYPASSES_EFFECTS`. Something has to be
*attacking* — a mob's reach or a projectile in flight.

A second, cheap half runs **once a second** over the entities already inside a small box around the
player, and cues once if any of them has targeted you. Never over every hostile in a loaded chunk.

**Double jump.** Jump while airborne. **v0.6.21: ~0.8 blocks/tick of rise — a clean 4-block jump
(was ~2.5).** **v0.6.19: a 1-second cooldown (20 ticks), and no longer shown
on the HUD at all.** Started when it is used. Server-authoritative in every respect: it refuses unless the player is
genuinely airborne, off cooldown, **clear of the ground** (no solid block within 1.15 below — added
v0.6.6 so tapping jump to hop the instant you land is not eaten as an accidental second jump), and
not doing something that already owns their movement (riding, swimming, elytra, ladders, creative
flight, adhered to a surface). The client will not even send the request until the jump key has been
released once since leaving the ground and ~5 ticks of airtime have passed, and when a climbable
surface is in reach a jump press feeds the grab double-tap first — a lone tap there still becomes a
double jump when the ~0.35 s window lapses. Combines freely with Web Swing and Web Zip.

**Physical enhancements** (`SpiderPassives`), as fixed-id transient attribute modifiers:

| Attribute / effect | Amount |
|-----------|--------|
| `ATTACK_DAMAGE` | +8 (flat) — v0.6.17, was +3 |
| `MOVEMENT_SPEED` | +18% |
| **Speed II** | hidden infinite effect (v0.6.17) |
| **Resistance II** | hidden infinite effect (v0.6.17) |
| **Regeneration I** | hidden infinite effect (v0.9.5, lowered II → I in v0.9.6) |
| `JUMP_STRENGTH` | +30% (~2-block jump) |
| `KNOCKBACK_RESISTANCE` | +0.30 |
| `SAFE_FALL_DISTANCE` | +9 |
| `FALL_DAMAGE_MULTIPLIER` | −50% |
| `STEP_HEIGHT` | +0.4 |

v0.6.17 raised the numbers on the user's instruction — Spider-Man now out-hits Super Strength's flat
+6 passive.

**Organic webbing.** No shooters, no ammunition item. A 200-point **Web Reserve** (v0.9.6, was 100)
that regenerates at 4/second starting 1.5 seconds after the last major web ability. Swinging is by a wide margin the
cheapest thing on it — 1 to fire, 0.2/tick attached, which is ~25 seconds of continuous rope time from
full — because it is the traversal the whole power is built around. Combat webbing is what costs.
Server-authoritative: only a server-side spend moves it down and only the server tick moves it up.

---

## 7. Lifecycle and cleanup

- **Death**: the swing, anchor and surface attachment are dropped as the player dies (so no web line is
  drawn on a corpse) and again on respawn. The **power itself is never removed** — the mod's other
  permanent powers do not vanish on death and neither does this one.
- **Respawn / join**: transient state cleared, passives re-established on the new entity.
- **Dimension change**: `AFTER_PLAYER_CHANGE_WORLD` clears the swing and the anchor. An anchor is a raw
  coordinate; it means something completely different, or nothing, in another dimension.
- **Logout**: transient state cleared and the player's nets forgotten.
- **Server stop**: `SpiderWebs.clearSessionState()` runs from `ServerStateReset` alongside every other
  static collection in the mod. `SpiderWebs.pruneExpired()` runs from the same class's 10-second sweep,
  because cocoon markers would otherwise only be pruned while somebody happened to be ticking — the
  exact bug pattern that made long sessions choppy in v0.5.0.
- **The anti-float grant.** Vanilla kicks a player who spends four straight seconds airborne with no
  downward motion and no blocks near them — which a good swing over open ground looks exactly like.
  `mayfly` is granted for the duration of a swing to suppress that check; `flying` is forced off every
  tick so it never becomes creative flight, and the grant is handed back on release unless Thor, hero
  flight, a suit or repulsor boots are relying on it.

---

## 8. Ceiling orientation

`PlayerRendererMixin` flips the **model** 180° about its facing axis while the synced climb state says
ceiling, and lifts it back onto the surface. Because it keys off the synced state, **every other player
sees the upside-down pose too**, including for a plain Spider Adhesion player.

The **camera is deliberately left alone**. Rolling a first-person view 180° is genuinely nauseating and
makes the player's own controls unreadable, and the design is explicit that movement reliability and a
legible camera come first. Nothing about the crawl changes; only what onlookers see.

---

## 9. Files

| File | What it owns |
|------|--------------|
| `spider/SpiderMan` | the server API: state, the evolution, cooldowns, lifecycle |
| `spider/data/SpiderManState` | the persisted + synced attachment (13 fields, codec-backed) |
| `spider/data/SpiderClimbLocal` | per-tick adhesion scratch state (not persisted, not synced) |
| `spider/SpiderClimb` | surface detection and the sticky attachment |
| `spider/SpiderClimbActions` | leaping off a held surface |
| `spider/SpiderAnchorSearch` | the ray search and the fabricated fallback |
| `spider/SpiderSwing` | anchor lifecycle, the rope model, the altitude rule |
| `spider/SpiderSwingInput` | the rider's key state, packed |
| `spider/SpiderAbilities` | the five non-swing abilities, the double jump, the slot map |
| `spider/SpiderWebs` | nets and cocoons, and their cleanup |
| `spider/SpiderWebReserve` | the organic webbing budget |
| `spider/SpiderSense` | the dodge, arrow catching, the warning |
| `spider/SpiderPassives` | attribute modifiers and the traversal fall rules |
| `spider/SpiderManAbilityManager` | slot routing and the per-player server tick |
| `spider/item/*` | the Arachnid Mutagen |
| `command/SpiderManCommand` | `/spiderman power|web|status` (op 2) |
| `client/spider/SpiderClimbMovement` | adhered movement, client-simulated |
| `client/spider/SpiderSwingClient` | the rope, client-simulated |
| `client/spider/SpiderWebLineRenderer` | the web line |
| `client/spider/SpiderInputClient` | the two jump gestures |
| `client/gui/SpiderHud` | the ability row and the Web Reserve meter |
| `mixin/EntityWebMixin` | Spider-Man passes through cobweb |
| `mixin/LivingEntityClimbMixin` | the climbing *flag* (no longer the implementation) |
| `client/mixin/SpiderTravelMixin` | hands adhered movement to the engine |
| `client/mixin/PlayerRendererMixin` | the upside-down ceiling pose |

---

## 10. Admin

```
/spiderman power grant [player]     evolve into Spider-Man (grants Spider Adhesion first if needed)
/spiderman power revoke [player]    remove the Hero Class
/spiderman web <0-200> [player]     set the Web Reserve
/spiderman status                   dump the state
```

`grant` deliberately goes through the same evolution the survival path uses, so the tested path is the
shipped path. The **Power Suppressor** strips Spider-Man along with every other power; the Spider
Adhesion it grew out of is not handed back.

## v0.12.20 -- Combat Mode, web strands, thin bars

- **N toggles Traversal / Combat Mode** (`SpiderCombat.toggleMode`, `SpiderManState.combatMode`, the 16th and last field
  of its record codec). Traversal is the original kit. Combat Mode (`SpiderCombat`): **R Web Strike** (pulled to the
  target, 15 dmg + knockback, 3 s cd), **G Web Zip** (unchanged, but on an armed mob/player it yanks their held items to
  you), **X Web-Throw** (hold: target orbits your head; release: hurled along the look vector; 3 s cd), **Z Impact Web**
  (`ImpactWebEntity`, 10 dmg + knockback, pins to a wall for 6 s via `SpiderWebs.cocoonFor`; 1 s cd), **V** Web Blossom
  charges in 2 s instead of 3 s. C / V-tap unchanged.
- **Web strands** (`SpiderWebStrandPayload` -> `client.spider.SpiderStrands` / `SpiderWebLineRenderer`): Web Zip and the
  combat moves draw the same web line as the swing, hand -> target, holding briefly then phasing out over 5 s; a released
  swing also fades over 5 s instead of vanishing. The near end is tied to the *real* fist: `SpiderHandTrackerLayer`
  captures the posed arm's world position each frame (third person / other players); first person uses the view-space
  hand spot. Lines are drawn as segment pairs (`RenderType.lines`) so several strands never join up.
- **HUD**: all bars thin (3 px, no border); Web Reserve reads as a percentage in a dark maroon.
- N is shared with Max Steel (Go Turbo) / Wolverine (Sniff) / Symbiote host (Predator Vision): Max Steel wins, then
  Spider-Man, then Wolverine, then the Symbiote host.

## v0.12.21

- Shift + Web Zip is hauled every tick (`SpiderCombat.beginZipPull`) until the eye is within 1.9 blocks of the aimed block
  (stall/timeout guarded); wall adhesion is still only armed when the aim is not steeply downward.
- Fading strands no longer follow the hand: the near end freezes where the hand was the moment the web is let go
  (release of R, or the end of a strand's hold phase).
- HUD restructured and lifted: ability keys / "Spider-Man" / "Web N%" + thin bar / "Traversal|Combat Mode [N]".

## v0.12.22

- Released swing webs no longer replace each other: every released web stays put until it has fully phased out (5 s).
- HUD: the Web % text and the mode label use the web bar's grey-white.
