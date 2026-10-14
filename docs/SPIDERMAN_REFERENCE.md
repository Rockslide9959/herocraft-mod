# Spider-Man — Project Hero reference

Added in **v0.6.3**. Package root `com.projecthero.mod.spider`, client half in
`com.projecthero.mod.client.spider`.

## v0.16.1 -- the revamp: 18 moves, right-click swinging, webs as structures

**Everything below supersedes the older control tables further down** (the R swing key, the N Traversal / Combat
toggle, the C wall-crawl toggle and Web Net are gone).

### Controls

Swinging and the rest of the traversal use **no ability key**:

| Input | What |
|---|---|
| **Hold right-click, empty main hand** | Web Swing (the existing hybrid anchoring / rope / altitude physics). Release = let go with your momentum; a fast release plays a forward somersault. Not when the crosshair is on an entity or an interactable block in reach (door, trapdoor, gate, button, lever, bed, bell, note block, repeater, comparator, daylight sensor, cake, composter, respawn anchor, anything with a menu or a block entity), so right-click still uses those. His webs (tightropes / woven webs / walls) are anchors too. |
| **Sprint + Jump in the air** | Zip Boost: a quick web-zip forward (1.6 b/t along the look, slight lift), strand to the aimed block or a point ahead. 3 webbing, 16-tick cooldown (`zip_boost`). Plain Jump in the air = the double jump, as before. |
| **Run / fly into a wall** | Wall crawl engages by itself: airborne with `horizontalCollision`, or on the ground sprinting forward into it (a 0.32 hop hands it to the climb engine), or swinging into it (the line is dropped and the wall taken). The client arms its own `SpiderClimbLocal.grabIntent` and sends `CLIMB_GRAB`. `SpiderClimb.wallCrawlEnabled` now always returns false. |
| **Sneak while clung** | Let go (one press now; the old double-tap is still two presses). A 12-tick lock stops the wall re-grabbing at once. |
| **Sneak on your tightrope** | Drop under it and hang (a 1.7-block swing line, `FLAG_HANGING`, the client keeps the length fixed). Jump = flip back on top, Sneak = let go. |
| **Z on a ceiling / woven web / tightrope** | The upside-down hang (`SpiderMoves.hangKey`, `FLAG_HANG_UPSIDE`). v0.16.2: Jump climbs / Sneak lowers at up to `HANG_REEL_SPEED` 0.4 blocks/tick (eased in by 0.06/tick; client-simulated in `SpiderSwingClient`); no short cap (`HANG_MAX_LENGTH` 128, `SpiderSwing.serverTick` allows it + 8) -- `SpiderMoves.hangLowerTo` stops him with his head 0.12 above the ground / any block under him. The hang keeps him STANDING (`PlayerMixin`, so Sneak does not crouch him). Pose: `client/spider/SpiderHang` (jointed limbs, two-bone IK like the perch: feet together up the line, knees out wide, hands together on the line below the feet; the body cartwheels over in a quarter second and sways while he moves); the line runs down to the hands' grip (`SpiderHang.lineEnd`; in his own first person it ends 0.6 above the camera so it never crosses the crosshair). Weave Mode works while hanging: C is never Shift+C while he hangs (Sneak is the lower key). Z again zips back, Shift+Z lets go. |
| **Attack your own web** | Cut it (client ray test against your webs, nearer than the block you aim at; the server re-raycasts 6 blocks, owner only). |
| Passives | Double jump, super leap (sneak + jump), Spider-Sense, no fall damage, wall leap -- unchanged. |

The six keys each hold **three moves** -- tap, Shift+key (Shift read when the key goes down; fires at once), or hold
(`webs.holdTicks`, 8 ticks; a shorter press is the tap, fired on release). Every move has its own cooldown id.

| Key | Tap | Shift + key | Hold |
|---|---|---|---|
| **R** web shooters | **Web Shot** `web_shot` -- 2 dmg, stick stacks + Slowness (3 = 12 s cocoon). 2 web, 6 t | **Web Bomb** `web_bomb` -- burst at the aim (30 b): r 4, 3 dmg, rooted 3 s. 15 web, 6 s | **Impact Web** `impact_web` -- charge (full +20 t), release: 10 dmg (x1.5 full), knockback, 12 s cocoon, wall pin; auto-fires at 3 s. 12 web, 1.5 s |
| **G** zip | **Point Zip** `point_zip` -- hauled to the aimed block (100 b); a wall face arms adhesion. 5 web, 10 t | **Web Yank** `web_yank` -- armed target: held items ripped to you; else the old yank (items / light / heavy). 6 web, 1.5 s | **Zip Kick** `zip_kick` -- zip at the aimed enemy (30 b), flying kick 14 dmg + big knockback, bounce off. 10 web, 5 s |
| **X** melee | **Web Strike** `web_strike` -- zip in (30 b) + 3 punches 5 / 5 / 9 (finisher knockback). 8 web, 2.5 s | **Uppercut Launch** `uppercut` -- 5 b: 8 dmg, target launched (1.05 up), you leap after it; a 3 s air-combo window where your hits keep it (and you) aloft. 6 web, 6 s | **Spider-Sense Counter** `spider_counter` -- 1 s stance: the next attack (attacker or projectile) is dodged with a back-flip and countered: 12 dmg + knockback to an attacker within 10 b, projectiles reversed. 4 web, 8 s (3 s on a whiff) |
| **Z** throws | **Web Whip** `web_whip` -- everything within 4 b: 7 dmg, knockback, stick stack; whip ring + body spin. 10 web, 4 s | **Grab & Hurl** `grab_hurl` -- aimed mob (16 b) or, with none, a loose block: hauled to the hand (9 t), thrown along the look; 10 dmg on impact (thrown blocks shatter into drops, half damage to bystanders). 10 web, 5 s | **Web Swing-Throw** `swing_throw` -- the old Web-Throw: whirl a mob (30 b) round your head while held, release hurls it; 6 dmg on impact. 10 web, 3 s |
| **V** big moves | **Spider-Sense Pulse** `spider_pulse` -- client-only highlight of every `Enemy` within 32 b for 6 s, Speed IV 6 s, a 2 s dodge window (every hit with an attacker is negated). 10 web, 20 s | **Web Cocoon** `web_cocoon` -- an enemy (20 b) at <= 40 % health, webbed, cocooned or Slowness III+: cocooned 12 s and takes x1.6 damage from players. Not bosses. 25 web, 10 s | **Web Blossom** `web_blossom` -- charge 2 s (no Sneak needed now), 40 dmg + 20 s trap within 20 b. 75 web, 20 s |
| **C** web building | **Tightrope** `tightrope` -- 1st press: anchor A (aimed surface); 2nd: a strand A -> B if <= 40 b apart. Aim at nothing to clear A; A is forgotten after 30 s. 10 web (at B), 10 t | **Web Wall** `web_wall` -- a 3 x 3 panel 2.5 b ahead, facing you (axis-snapped). Blocks every projectile not fired by you / your squad; traps mobs. 25 web, 10 s | **Weave Mode** `weave` -- each left-click places an anchor on the aimed surface; release C = the web centred on the centroid of the anchors (3+ anchors, else cancelled and refunded); each left-click = a radial strand to the aimed surface (<= 10 strands, <= 30 b each, 8 web each); release C = spiral rings fill in (30 web). 15 s cooldown; closes itself after 30 s |

Black Suit Spider-Man: Shift+X (Tendril Strike), Shift+Z (Crush, held) and airborne Shift+C (Slam) are the Symbiote's
while the suit is on (the Agent Venom precedent) -- they shadow Uppercut, Grab & Hurl and an airborne Web Wall.

### Web structures (`spider.web`)

- `WebStructureEntity` (entity type `projecthero:web_structure`): one tightrope / web wall / woven web. Its shape is a
  `WebGeometry` (strand segments + an optional thin wall panel) synced as a compound tag; owner UUID, birth and expiry
  game times are synced too. **No block is ever placed or removed.**
- **Collision**: `EntityWebCollisionMixin` adds the web's boxes to `Entity.collide`'s entity-collision list (MixinExtras
  `@ModifyExpressionValue` on `Level.getEntityCollisions`) for players allowed to use it -- the owner and his squad
  (`WebStructures.mayUse`: server `Squads.areAllies` / `SquadManager.sameSquad`, client the `SquadClient` roster). It runs
  on both sides (the client simulates, the server's `handleMovePlayer` re-runs the move with the same boxes), so walking a
  rope never rubber-bands. Strand boxes (0.44 wide, every 0.4 along the strand, top flush with it) are **one-way
  platforms** (only when the feet are within 0.5 below the top) unless you are wall-crawling (then solid from every
  side) or on a line (swinging / hanging under a tightrope: strands never snag, only a wall panel is solid), so you can jump up through a
  web and land on it, and climb a vertical one. The wall panel is solid. `SpiderClimb.hasGrip` also grips webs.
- **Strangers** (anyone else, mobs included) pass into a web and are stuck fast while touching it:
  `makeStuckInBlock(STRUCTURE_VOID, 0.08 / 0.04 / 0.08)` each tick (the server for mobs, `SpiderTraversalClient` for a
  stranger's own client). STRUCTURE_VOID, not COBWEB, so `EntityWebMixin`'s cobweb pass for Spider-Men / the Hulk does
  not apply. Carnage (`ignoresWebs`) is not held.
- **Web alarm**: a stranger touching (or a projectile hitting a wall) sends `SpiderWebAlarmPayload` to the owner (same
  level, within `alarmRange`, once per `alarmCooldownTicks` per web): the Spider-Sense tingle + a HUD arrow and distance
  for 4 s.
- **Life**: fade after `fadeSeconds` (300; the renderer fades the last 10 s), cut by the owner, capped at
  `maxStructuresPerPlayer` (12; a new web dissolves the oldest). **Never saved** (`shouldBeSaved() == false`): a chunk
  unload or restart simply drops them -- no orphans possible. Registry = two sets (server / client) in `WebStructures`,
  pruned on query; the server set is cleared by `ServerStateReset`.
- **Rendering**: `WebStructureRenderer` -- camera-facing silk ribbons (`textures/entity/spider_web_strand.png`) with a
  sag on long spans, and a double-sided orb-web panel (`spider_web_wall.png`) for walls; both generated by
  `scratchpad/gen_spiderman_web_textures.js`.

### Feel

- `SpiderPose` (client): keyframed animations for every move (thwip L/R/both, impact recoil, zip, yank, zip kick,
  3-punch combo, uppercut, counter flip, whip, hurl, swing-throw release, tightrope shot, wall cast, weave shot / finish,
  pulse, cocoon, blossom, release flip) and held stances (weaving, counter guard, impact charge, swing-throw whirl,
  blossom crouch, hanging, the swing body: free arm out, knees tucked). The **superhero landing** plays after any drop
  faster than 0.75 b/t (server-detected, `SpiderMoves.tickMotion`). Whole-body flips / spins are
  `SpiderPose.bodyRotation` (via `PlayerRendererMixin`). Synced through the new non-persistent `SpiderMoveState`
  attachment (`projecthero:spider_move_state`: anim id + start, stance flags, anchor A, weave centre / strand count,
  pulse end).
- Every web line is a `SpiderWebLineRenderer` strand from the tracked hand (new strand slots: shot, tightrope A
  preview, weave centre, grab, whip, counter), plus the Web Whip ring.
- **Sounds** (`SpiderSounds`, synthesised by `scratchpad/synth_spiderman_sounds.js`, ffmpeg -> OGG): `spider_thwip`
  (3 takes), `spider_zip`, `spider_splat`, `spider_sense`.

### HUD

Six boxes R G X Z V C: the tap move's cooldown fills the box; two thin bars along the bottom are the Shift (blue) and
hold (red) cooldowns; a key whose stance is running is outlined. Left Alt lists all 18 names (wrapped to 190 px) plus
the traversal hint. Under the row: Spider-Man, Web %, the reserve bar, and a status line (Weave strands, anchor A,
Impact charge, counter stance, air combo, hanging). The web-alarm arrow and the Spider-Sense markers draw whatever holds
the slots.

### Config -- `config/projecthero_spider_man.json` (`SpiderManConfig`)

`webs` (server preference): `fadeSeconds` 300, `maxStructuresPerPlayer` 12, `blockGrabbing` true, `alarmRange` 160,
`alarmCooldownTicks` 60, `tightropeMaxLength` 40, `weaveMaxStrands` 10, `weaveStrandLength` 30, `strangerSlowFactor`
0.08, `holdTicks` 8.

`moves` (balance; each `Move` is `{cooldown ticks, cost, damage}`): `webShot` 6/2/2, `webBomb` 120/15/3 (+`webBombRadius`
4, `webBombRootTicks` 60), `impactWeb` 30/12/10 (+`impactChargeTicks` 20), `pointZip` 10/5, `webYank` 30/6, `zipKick`
100/10/14 (+`zipKickRange` 30), `webStrike` 50/8/5 (+`webStrikeFinisher` 9, `webStrikeRange` 30), `uppercut` 120/6/8
(+`airComboTicks` 60), `spiderCounter` 160/4/12 (+`counterStanceTicks` 20), `webWhip` 80/10/7 (+`webWhipRadius` 4),
`grabHurl` 100/10/10 (+`grabRange` 16), `swingThrow` 60/10/6, `tightrope` 10/10, `webWall` 200/25, `weave` 300/8 per
strand (+`weaveFinishCost` 30), `spiderPulse` 400/10 (+`pulseHighlightTicks` 120, `pulseRadius` 32, `pulseDodgeTicks`
40), `webCocoon` 200/25 (+`webCocoonTicks` 240, `cocoonDamageMultiplier` 1.6), `webBlossom` 400/75/40
(+`blossomChargeTicks` 40), `zipBoost` 16/3.

### Decisions

- **Pulse slow-motion**: the mod's Time Slow is game-wide (tick rate), not per player, so the Pulse gives Speed IV for
  6 s plus a 2 s window in which every attack with an attacker behind it is dodged.
- **Webs vanish on reload** rather than persist (the simplest way to guarantee no orphans).
- **Strangers include other Spider-Men and the Hulk** (they wade through cobweb, not through his web).
- **Grab & Hurl blocks**: no block entities, no fluids, hardness 0..25 (obsidian / bedrock excluded), and it needs
  `blockGrabbing`, the mod's `abilityTerrainDamage`, the `mobGriefing` rule, `mayBuild` / `mayInteract`, and
  `PlayerBlockBreakEvents.BEFORE` (claim mods) to agree. The block is removed and thrown as a `ThrownBlockEntity`
  (`projecthero:thrown_block`) that never places a block: on impact it breaks into its normal drops.
- Web Net (the old V tap) is retired (it placed cobweb blocks); `SpiderWebs.weaveNet` remains, unused.

### Files

`spider/SpiderMoves` (input resolver + every new move + sessions), `spider/SpiderAnim`, `spider/SpiderManConfig`,
`spider/SpiderSounds`, `spider/data/SpiderMoveState`, `spider/web/{WebGeometry, WebStructureEntity, WebStructures}`,
`spider/entity/ThrownBlockEntity`, `mixin/EntityWebCollisionMixin`, `network/SpiderWebAlarmPayload` (+ seven new
`SpiderActionPayload` actions); client `spider/{SpiderTraversalClient, SpiderPose, WebStructureRenderer,
ThrownBlockRenderer}`, the rewritten `gui/SpiderHud`, updated `SpiderInputClient` / `SpiderSwingClient` /
`SpiderWebLineRenderer`. Tests: `SpiderManRevampGameTests`.

### Wall-crawl switch, rope networks, zip-to-rope

- **N toggles the automatic wall crawl** (`SpiderMoves.toggleWallCrawl`, `SpiderActionPayload.TOGGLE_WALL_CRAWL`). Saved per player in the new persistent `SPIDER_WALL_CRAWL_OFF` attachment (default false = ON, kept through death, synced to the owner). OFF means nothing sticks him: `SpiderClimb.updateAttachment` returns null for Spider-Man, `requestGrab` refuses (so a Point Zip at a wall does not arm adhesion either) and the client never sends the auto-grab. HUD: "Wall Crawl: ON / OFF" right-aligned on the Web line, plus an action-bar message on each press. N priority: Titan-in-Titan and a base-form Titan Shifter's Shift+N stay first; then plain N (no Shift, no mutation selected) is Spider-Man's, before All Might / Wolverine / Symbiote host / Green Lantern; Shift+N still falls through to Nova's helmet and the Stark Gear screen. One-primary-power means those heroes never share a player with Spider-Man anyway.
- **Tightropes connect.** Tightrope anchors raycast web strands as well as blocks (`WebStructures.raycastStrand`: tightropes and woven-web strands, never wall panels; whichever is nearer wins). An anchor on a strand is the exact closest point on it, so a new rope meets the old one there; the new rope records its parent web(s) (`WebStructureEntity.parents`). **Decision:** when a parent is cut or fades, every rope anchored on it dissolves too (cascading) -- simplest and never leaves a rope hanging from nothing. At a junction the balance assist follows the rope running most nearly the way he is going (`WebStructures.ropesUnderfoot`), so he walks straight on across.
- **Point Zip onto a rope.** A G tap aimed at one of his strands (nearer than any block) zips to that point and sets him down STANDING on top of it (feet 0.03 above the strand), facing along it (the direction nearer his look): `SpiderCombat.beginZipLanding`. The landing happens on arrival, or once the haul stalls / times out with a clear line to the point (so it works zipping up from below -- strands are one-way platforms -- but never through a wall).
- Gametests: `nToggleStopsAutoAttach`, `aTightropeCanAnchorOnAnotherRope`, `pointZipOntoARopeLandsStandingOnIt`. Sheet: `spidey_rope_network_sheet.png`.

### Real cobwebs, Shift+N mute, the skin-tight suit, the web-shot glob, 18-second debuffs

- **Real cobwebs** (`SpiderCobwebs`): Web Bomb fills the air round its splash (radius 3, nearest first), Impact Web puts one
  where it pins its target (feet + head), Web Blossom leaves a ring of them (radius 4). Only AIR is filled (never a
  player's block, a block entity or a fluid), only with `placeCobwebs`, the mod's `abilityTerrainDamage` and the
  `mobGriefing` rule on, and `mayInteract` (spawn protection / claims). At most `cobwebsPerCast` per cast and `cobwebsMax`
  at once (oldest first). Every cobweb is in a server-wide **SavedData ledger** (dimension, position, owner, expiry) and is
  taken back to air after `cobwebTicks` (18 s) -- or as soon as its chunk is loaded again, so a chunk unload or a restart
  can never orphan one. Breaking one drops nothing. Spider-Man and his squad are never slowed by his own cobwebs
  (`WebBlockSpiderMixin` on both sides; the owner and his online squadmates get the positions via `SpiderCobwebSyncPayload`).
  Sheet: `spidey_cobwebs_webbomb.png`.
- **Shift+N mutes his notification sounds** (`SPIDER_SOUNDS_OFF`, persistent, default on): the Spider-Sense danger ping, the
  Pulse, the counter tingle, the web alarm and the web-structure sounds (tightrope / wall / weave splats, cutting). HUD:
  "Sounds: OFF" right-aligned on the name line; an action-bar message on each press. Shift+N reaches Spider-Man only after
  a base-form Titan Shifter's Shift+N; Nova's helmet and the Stark Gear screen belong to other primary powers.
- **Suit fit**: the Spider-Man suit is no longer the GeckoLib shell (inflate 0.30 / 0.55). Its texture is a standard skin, so
  `client.spider.SpiderSuitLayer` / `SpiderSuitRender` draw it as a second skin on a wide player model +0.04 (outer layer
  +0.29) -- the Nova-uniform technique -- per piece (the mask only while closed, so H still pulls it off; the suit = body and
  arms; leggings or boots = legs). `SuperheroArmorRenderer` skips the shell for the `spider_man` set; first person draws the
  suit sleeve (`SuperheroFirstPersonArm`). The black Symbiote suit is unchanged. Sheet: `spidey_suit_before_after.png`.
- **Web Shot is a projectile** (`SpiderWebShotEntity`, `projecthero:web_shot`): a spinning glob of webbing (core + crossed
  sheath) with four trailing strands (`WebShotRenderer`, `textures/entity/web_shot.png` from `gen_spiderman_web_textures.js`),
  2.6 b/t, splatting on impact; its hit is `SpiderAbilities.webShotHit` / `webShotBlock`. Sheet: `spidey_webshot_sheet.png`.
- **18-second web debuffs** (`moves.webDebuffTicks` 360): Web Shot slow / weakness / jump, the 3-stack cocoon, the Web Bomb
  root (`webBombRootTicks` 360), the Impact Web pin (`impactPinTicks` 360), the Web Blossom trap (`blossomTrapTicks` 360),
  and anyone stuck in a web wall / strand keeps Slowness III for 18 s after getting loose. Web Cocoon (Shift+V) lasts 30 s
  (`webCocoonTicks` 600). **Bosses** get `bossWebDebuffTicks` (90 = 4.5 s) of a weaker version (Slowness I, never fully
  pinned). The config moved to version 2 (`resetBalance`), so existing files take the new numbers.

### Spider-Sense rework (ring, tingle lines, smarter warnings, deflection)

Unchanged on purpose: the dodge chances (50% melee / 60% ranged), no perfect-dodge mechanic, the V Pulse, no fall damage.
Every Spider-Sense sound respects the Shift+N mute.

- **Threat ring** (`SpiderHud.renderWarning`, client only): 16 arc segments round the crosshair (radius 30 px, never over
  it). Each warning lights the arc on the side it comes from, relative to where he looks, and pulses, then fades over its
  last half second. Several kinds show at once. The colour says how urgent it is:
  - **red**: a melee wind-up or a projectile about to land (kinds 0 / 1);
  - **orange**: a hostile closing in, including from behind, or a weapon aimed at him (kinds 5 / 6);
  - **yellow**: environment and explosives.

  A 7x7 kind icon (fist / arrow / blast / hazard) sits outside the arc, with an up / down chevron when the threat is above or
  below. A 22 px edge ripple pulses on the screen side of the most urgent threat.
- **Tingle lines** (`client.spider.SpiderSenseFx`): six jagged comic strokes fan round his head, camera-facing, flickering.
  `SpiderMoveState.tingleUntil` is synced, so other players see them in third person; it is refreshed by `SpiderMoves.tingle`
  only near its end, so it is not re-synced every scan. They are not drawn for the local player in first person.
- **Smarter warnings** (`SpiderSense`, server scans, sent only to him):
  - a player winding up a blow while facing him (squadmates excepted);
  - a bow / crossbow / trident / gun drawn and aimed at him (`aimingAt`; mobs must target him, players must look within
    about 14 degrees) -- a new kind, `KIND_AIMED` = 6;
  - a hostile closing from behind before it has even targeted him;
  - lava up to 3 blocks ahead along his movement;
  - a pressure plate or tripwire with TNT wired within 4 blocks.

  Anyone flagged is outlined for him alone, now including other players. This goes through the existing
  `SpiderSenseGlowPayload` + `EntityGlowMixin` path, so **no server glowing tag, effect or flag is ever set**.
- **Path lines and shimmer** (`SpiderSenseFx`, client only): a projectile not his own whose simulated flight passes within
  2.6 blocks of him gets a faint dotted red line (arrow / throwable gravity and drag simulated). Firearms are hitscan, so
  their cue is the aim outline plus the orange ring. Invisible hostiles within 16 blocks, and invisible players his sense
  lists, give off a faint `WAX_OFF` glint.
- **Deflection** (`SpiderSense.deflect`, `moves.deflectReturnChance` 0.4): a dodged projectile never passes through:
  - with a shooter, `deflectReturnChance` of the time it is sent straight back at them, and its owner becomes him (RETURNED);
  - otherwise an arrow is caught, as before (CAUGHT);
  - anything else is swatted aside: turned sideways, slowed and lifted (SWATTED).
- Gametests: `spiderSenseAlwaysDeflectsADodgedProjectile` (400 seeded deflections: never NONE, never still heading at him,
  returned ones go back at the shooter, a ratio of about 40%, chance 0 / 1 / no owner) and `spiderSenseWarningsAreClientOnly`
  (a wind-up is in his private threat set, but there is no glowing tag, no Glowing effect and `isCurrentlyGlowing` stays
  false).
- Shots: `spidey_sense_ring.png` / `spidey_sense_ring_zoom.png` (several threats at once), `spidey_sense_tingle_lines.png`,
  `spidey_sense_path_and_aim_outline.png` / `spidey_sense_path_2.png` (the archer outlined while drawing, the arrow's path).

### The living web swing + faster swinging

- **Animation** (`client.spider.SpiderSwingPose`). It is driven every frame by the swing itself, with no keyframes.
  - **Inputs:** the synced anchor, the player's own motion (position delta) and time on the line, so every viewer sees the same swing.
  - **Body:** hangs along the rope. A pitch / roll about the shoulders (`PlayerRendererMixin`) follows the rope angle, plus a roll into turns.
  - **Web arm:** aimed at the anchor every frame, in the tilted body's own frame, so it stays on the line.
  - **Phase:** stretched, with the legs trailing, through the bottom of a fast arc. Knees up and pitched back on the upswing. Tucked at the slow top.
  - **Secondary motion:** the legs scissor with arc speed, the free arm counter-swings out to the side, and the head takes the tilt back out so it looks where he is going.
  - **Styles per swing** (chosen from the anchor, so viewers agree):
    - one-arm classic;
    - two-hand grip on fast lines;
    - cannonball tuck through the bottom of fast arcs (always when very fast);
    - corkscrew spin with arms spread when a long, fast swing is let go.
  - **Blending:** everything is eased — the swing weight in (9/s) and out (4.5/s), each angle, and the aim from one anchor to the next on a chained swing. The server's release somersault (`RELEASE_FLIP`) plays on top.
  - **Torso:** bends go through `HipBend`.
  - **Hanging under a tightrope:** keeps its two-hand hang.
- **First person** (`SpiderFirstPersonHandsMixin#swingHands`):
  - The web hand is raised toward the anchor, leaning to its side, bobbing with the arc. The web line leaves from the raised hand (`SpiderStrands.handPosition`).
  - The free hand comes into view at the screen edge on the upswing, and onto the line on a two-hand swing.
  - When the web hand is the off hand it is drawn here.
- **Faster swinging** (config `moves`, version 3, so old files take the new numbers):

  | Key | Old | New |
  | --- | --- | --- |
  | `swingForwardAccel` | 0.100 | 0.13 |
  | `swingBottomPush` | 0.042 | 0.068 |
  | `swingGravityPump` (extra pull down while falling on a taut line) | 0 | 0.03 |
  | `swingFacingAssist` | 0.022 | 0.03 |
  | `swingTension` (a fresh line goes taut quicker) | 0.14 | 0.20 |
  | `swingMaxSpeed` | 3.2 | 4.6 (+44%) |
  | `swingReleaseBoost` (velocity x on an in-air release) | 1.0 | 1.15 |
  | `swingReleaseLift` | 0 | 0.12 |
  | `swingChainWindowTicks` / `swingChainBoost` (a line fired within 1 s of letting go adds a kick along the travel) | none | 20 / 0.35 |

  - The rope model is shared (`SpiderSwing.applyRope`) and runs on the owning client. The release / chain kicks are client-side in `SpiderSwingClient`, where the movement is owned.
  - The server has no swing speed check beyond vanilla's moved-too-quickly limit (4.6 b/t is far inside it), so there is nothing to rubber-band.
  - The animation's "fast" scale reaches full at about 2.8 b/t, so it keeps growing with the new speeds.
  - Sheet: `spidey_swing_sheet_v2.png`. Rows:
    1. side view through an arc;
    2. 3/4 view through an arc;
    3. the release;
    4. a chained swing;
    5. first person through an arc.

### First-person web lines leave the drawn wrist; Weave centre 3 blocks ahead

- **First-person web lines** (`client.spider.FirstPersonWebAnchor`). In first person every web line starts at the wrist of the hand drawn on screen. This covers the swing, Web Zip / Yank, tightrope shots, combat strands and the swing remnant.
  - **Capture:** `PlayerRendererHandMixin` reports each drawn first-person arm. The wrist point (arm-local y 0.62) is pushed through the hand pass's projection x model-view x pose to screen coordinates.
  - **Line end:** `SpiderStrands.handPosition` turns that screen point back into a world point 0.6 blocks in front of the camera, using the world pass's own projection and view rotation (captured at `WorldRenderEvents.START`).
  - **Robustness:** this holds at any FOV, with or without view bobbing, and through the swing raise and any arm animation.
  - **Lag:** the arm pass runs after the world pass, so the screen point is one frame old. The hand is fixed to the view, so that is about a pixel.
  - **Off hand:** an off-hand strand now draws the off arm, raised toward the strand, so its line has a wrist.
  - **Fallback:** a hand not drawn for 150 ms falls back to the old estimate.
  - Sheet: `spidey_fp_web_wrist_sheet.png` (FOV 70 top row, FOV 100 bottom row: tightrope shot, Web Zip, swing frames).
- **Weave Mode centre** (superseded: see "Weave Mode: the centre is the centroid of the anchors") (`SpiderMoves.weaveCentre`, `webs.weaveCenterDistance` 3.0). Holding C puts the web's centre 3 blocks in front of his eyes along his look, or 0.3 blocks short of a closer block. The radial strands still run from the centre to the aimed surfaces. Gametest: `weaveCentreSitsThreeBlocksAheadOrShortOfAWall`.

### Perch removed, the Web Bomb glob, the Zip Boost sound

- **The ledge perch is removed** (user request). This covers the pose (perch v4), its server detection (`FLAG_PERCH`, `perchSpot`), the first-person perch camera and hands, the in-game pose editor (`/spiderposeeditor`, `PerchPose`, `PerchPoseEditorScreen`, `SpiderPerchCameraMixin`), its lang keys, its guide line and its gametest.
  - Sneaking at a ledge is ordinary sneaking again.
  - The first-person swing hands and off-hand strand arms moved to `SpiderFirstPersonHandsMixin`.
  - On start-up the client deletes `config/projecthero_perch_pose.json` / `config/projecthero_perch_pose_export.txt` if the editor left them.

- **Web Bomb glob** (`SpiderWebBombEntity`, `projecthero:web_bomb`, `WebBombRenderer`):
  - A fat ball of webbing (core, three crossed sheaths, loose strands) that tumbles and pulses.
  - It is lobbed in an exact arc to the aimed point (`launchVelocity`, gravity 0.05 b/t^2, ~1.5 b/t across).
  - It bursts on a body, a block or its landing point into the splash (`SpiderMoves.webBombBurst`: the root, damage, rings and real cobwebs). It no longer draws a web line.
  - Sheet: `spidey_webbomb_glob_sheet.png`. Gametest: `webBombIsALobbedGlobThatBurstsWhereItLands`.
- **Zip Boost** now plays the swing line's sounds: the thwip at the usual pitch plus the line going out (`SpiderSwing.playAttachSound`).

### Fix: "hold right-click and it shoots a web and immediately releases"

The harness held right-click for 30 ticks through 14 situations, twice each, with every start, release and detach logged on both sides. Two real causes showed up:

1. **The server's altitude backstop.** A fabricated (open-air) line belongs to a "sequence" whose baseline height is kept while he keeps swinging, and `serverTick` detaches a fabricated line once he is more than `AIR_SWING_CEILING` + 4 above that baseline. A line fired while he was *already* above it (he dropped in from higher up, or a fast release / chain flung him up) was therefore cut on the very next tick (logged: start t=254, detach t=255 by `serverTick`).
   - **Fix:** `SpiderSwing.fire` re-bases the sequence so he is at the ceiling. The rope still gives no lift above it, but the line holds.
2. **The "swung into a wall: take the wall" release.** Any horizontal collision in the air while swinging sent `SWING_STOP` the same tick, so a line fired while brushing a wall or pillar was dropped at once (logged: `client wall-grab release` one tick after the start).
   - **Fix:** that release now needs the line to have been held 4+ ticks, and never fires for a line started against a wall until he is clear of it (`SpiderInputClient.wallAtLineStart`, `SpiderSwingClient.ticksOnLine`).

Also: a line fired from the ground now hops him up into the swing (0.48 up) instead of dragging him along the ground. With both fixes, all 28 holds (and the harness's own chained swing) stayed attached until the key was let go.

Gametests:
- `aSwingFiredFromTheGroundIsHeld`
- `aSwingFiredHighAboveAFabricatedRunIsNotCutAtOnce`
- `aRefusedSwingShowsNoLine` (no webbing: the server refuses, nothing attaches)

### Weave Mode: the centre is the centroid of the anchors

The weave now comes out flat and centred instead of a tent pulled toward one point.

- **Opening:** holding C opens Weave Mode with no centre (`SpiderMoves.openWeave`).
- **Anchors:** each left-click places an anchor on the aimed surface within `weaveStrandLength` (30) blocks (`weaveAnchor`; 8 webbing each, at most `weaveMaxStrands`).
- **Live preview:**
  - The synced centre (`SpiderMoveState.cx/cy/cz`) is the centroid of the anchors so far, drawn as a pulsing star (`SpiderWebLineRenderer`).
  - From 2 anchors on, preview radials run from it to each anchor.
  - Each click flashes a short line to the new anchor.
- **Release with 3+ anchors:**
  - Centre = centroid (`weaveCentroid`; it lies on the anchors' best-fit plane).
  - Plane normal from Newell's method over the anchors in order round the centroid (`weaveNormal`).
  - Radials sorted by angle round the centre in that plane (`orderAroundCentre`), so the spirals join neighbours whatever order they were clicked in.
  - Anchors farther than 30 from the centroid are dropped.
  - The spirals are laid in (30 webbing), then the cooldown starts.
- **Release with fewer than 3:** the weave is cancelled with a message, the preview is taken down, all the anchor webbing is refunded, and there is no cooldown.
- **Unchanged:** walkability and collision for him and his squad, stuck-fast for others, limits, costs and cooldowns.
- **Removed:** `webs.weaveCenterDistance` (an old config file's key is simply ignored).
- **Gametests:** `aWovenWebIsCentredOnItsAnchorsAndFlat`, `aWeaveWithFewerThanThreeAnchorsIsCancelledAndRefunded`, and `wovenWebNeverChangesABlock` (updated).
- **Sheet:** `spidey_weave_centroid_sheet.png` shows:
  - the preview with 3, then 6, anchors;
  - the finished web across a gap, from the front, the side and 3/4.

### v0.16.2: the ledge perch returns, with jointed limbs

- **Trigger** (as in v0.16.1): Sneak, standing still on the ground, at a drop of 3+ blocks just ahead of his feet where he faces, or on something thin (a fence / wall / post top, collision box under 0.7 wide). Server-detected in `SpiderMoves.tickMotion` (`perchSpot`, `perchAllowed`), synced as `SpiderMoveState.FLAG_PERCH` (bit 14). Sticky once settled (a shuffle under 0.2 b/t keeps it). Not while swinging, wall-crawling, riding or in water. Vanilla sneak edge-safety is untouched.
- **The pose** (`client.spider.SpiderPerch`, called first in `SpiderPose.apply`): the Insomniac crouch on a post. A deep frog squat, hips 3 px above the perch top; feet together on the top with the toes turned ~38 degrees out; knees spread wide out at about hip height (thighs out and a little forward, shins back in to the feet, ~140 degrees of knee bend); torso leaning ~38 degrees forward with the shoulders hunched; both arms down between the knees, elbows a little bent and out, hands planted flat on the perch in front of the toes; head up, looking ahead (follows the look, limited). Each limb is solved as a two-bone chain (hip / knee / sole, shoulder / elbow / hand) in model space; a tilted sole or palm is lifted so its lowest corner sits on the surface (the 2 px sneak render offset is accounted for). Eased in / out over ~5 ticks (`SpiderPerch.amount`).
- **Jointed limbs** (`client.render.JointedLimbs`): while a pose sets a joint (only inside that player's `PlayerRenderer.render`), every humanoid limb box drawn for him is split at the elbow (4 px below the arm pivot) / knee (6 px below the hip): upper half as posed, lower half turned by the joint rotation. Faces crossing the cut are clipped there with interpolated UVs and the cut edge is turned by HALF the rotation (a mitre), so the two halves share one continuous skin with no gap at any angle.
  - Vanilla parts: `ModelPartJointMixin` (on `ModelPart.compile`) for parts registered by `HumanoidModelLimbRegistryMixin` / `PlayerModelLimbRegistryMixin` -- covers the skin and its sleeve / pants overlay (wide and slim), the red suit's skin rig (`SpiderSuitRender`), and vanilla armour.
  - GeckoLib armour (the Black Suit etc.): `SuperheroArmorRenderer.renderJointedBone` splits `armorRightArm` / `armorLeftArm` / `armorRightLeg` / `armorLeftLeg` / boots the same way (rotation mirrored through GeckoLib's x/y flip).
  - A held item follows the bent forearm (`PlayerModel.translateToHand`).
- **First person:** the eye sinks 0.36 and tips 6 degrees down (`SpiderPerchCameraMixin`); both hands come down to the bottom of the view (`SpiderPerchHandMixin`, which also draws the empty off hand).
- Gametests: `perchNeedsADropOrAThinPost`, `perchNeedsSneakingStillOnTheGround`. Screenshot harness: `scratchpad/V0162PerchHarness.java.txt` (+ camera mixin).

### v0.16.2: Inverted Takedown and the perfect dodge

**Inverted Takedown** (`spider.SpiderTakedown`, user-approved controls):
- **Trigger:** hanging upside down (the Z hang) over a mob that is *unaware* (not targeting him, he has not hurt it), not a boss (`SpiderWebs.isBoss`), not too heavy (`tooHeavyToThrow`), not web-immune (`ignoresWebs`), not already cocooned, not an ally / un-harmable. Its top no higher than his head + 0.6, its feet within `RANGE_DOWN` 7 below his head, at most `RANGE_OUT` 5 out and `RANGE` 7.5 straight-line, in front (facing dot > 0.25 unless within 1.4 horizontally) and in plain sight. **Left-click.**
- **Targeting / HUD:** every 4 ticks while he hangs (and the move is off cooldown) the server picks the best target (`findTarget`) and sends its id to his client only when it changes (`SpiderReflexFxPayload.TAKEDOWN_TARGET`). `client.spider.SpiderReflexClient` draws "[LMB] Takedown" under the crosshair and, at the START of the client tick, drains the attack key's clicks (and `setDown(false)`) so the click never also punches or breaks a block; it sends `SpiderReflexPayload.TAKEDOWN`. Weave Mode keeps its own left-click.
- **The move:** `start` -> `SpiderAnim.TAKEDOWN` (31, 16 ticks) + a line from his hands to it (`SpiderCombat.sendStrand`, `SLOT_GRAB`). The mob waits while his hands drop (`GRAB_AT` 4 ticks), then is hauled up on an eased curve to hang beside him by `UP_AT` 12 and cocooned: from one of his webs (`SpiderMoves.hangFromWeb`) it is put on the strand 1.3 to his side with the strand through its chest and `SpiderWrap.wrap` (permanent while the web holds it); from a ceiling, its top just under the anchor a body-width to the side and `SpiderWrap.wrapFor` `TIMED_WRAP_TICKS` 500 (25 s). If neither spot fits, it is cocooned where it stands.
- **Stealth:** no damage is dealt and no loud sound played, so `SpiderStealth` never exposes him. No webbing cost; **8 s** cooldown (`inverted_takedown`, `COOLDOWN` 160).
- **Animation:** `client.spider.SpiderHang.takedownReach` (0..1: snaps down over 4 ticks, holds 2, eases back up over 8). The body drops `TAKEDOWN_DROP` 1.5 blocks down the line (`applyBodyRotation`; `lineEnd` pays the line out with it) and both jointed arms leave the line and reach down past his head (`pose(..., reach)`).
- New hooks: `SpiderMoves.hangAnchor` / `hangFromWeb` (read the private hang record).

**Perfect dodge** (`spider.SpiderPerfectDodge`; generous on purpose):
- **Prediction** (`predict`, every 2 ticks while he is free and off cooldown): a hunting melee mob within 6 -- its swing timer (`MeleeAttackGoalAccessor.ticksUntilNextAttack`) and its time to close the reach gap (vanilla's 0.83 attack-box inflation, its closing speed, at least 0.12 b/t while it paths) give an ETA; brain / modded mobs count once in reach. A bow at full draw on him (`20 - ticksUsingItem`, + flight time for archers past 6 blocks). A non-ally player within 4 facing him with a charged sword / axe (or mid-swing). Every projectile within 32 whose simulated flight (arrow gravity 0.05 / throwable 0.03, drag 0.99) enters his box + 0.45 within the lead. Anything with ETA <= `WARN_LEAD` 12 ticks (0.6 s) opens the warning until ETA + `LATE_GRACE` 4.
- **Warning** (`warn`): the synced comic tingle lines (`SpiderMoves.tingle`) and `SpiderReflexFxPayload.DODGE_WARNING` to his client (onset + every 4 ticks): red screen-edge flash (strong for 10 ticks, then a throb), a distinct tingle (Spider-Sense at pitch 1.45 + amethyst resonance; muted by Shift+N) and "[Space] Dodge!" under the crosshair.
- **Jump** during the warning (client edge on the jump key, nothing consumed -- it is still a jump) sends `SpiderReflexPayload.DODGE`; `arm` checks the warning, free (not swinging, no `FLAG_HANGING`) and cooldown, and arms it for `ARMED_TICKS` 16. Outside a warning a Jump is just a jump.
- **The dodge** (`tryPerfectDodge`, first thing in `SpiderSense.onAllowDamage` for an eligible hit): the hit is cancelled; a sidestep perpendicular to the attack (toward his drift, else random; a flip -- `COUNTER_FLIP` -- if airborne); a projectile is deflected (`SpiderSense.deflect`); the attacker and every enemy / hunting mob / non-ally player within `SLOW_RADIUS` 4 get Slowness VI + Mining Fatigue III for 1.5 s (`applyControl`, so halved vs players) and a melee goal's next swing pushed back 30 ticks; `DODGE_PERFECT` to his client: a desaturating grey wash + dark vignette pulse (24 ticks) and a whoosh. **5 s** cooldown (`perfect_dodge`). No world time slow (multiplayer).
- **Counter:** for 3 s (`COUNTER_TICKS` 60) his next damage on anything (melee or ability; `AFTER_DAMAGE`) deals the same again (x2 total) with a 1.4 knockback + lift, crit / sweep particles and sounds; "Counter x2" shows by the crosshair until it lands.
- The automatic dodge roll (`SpiderSense.rollFor`) and the old move-during-the-warning dodge still run for every other hit.

Network: `SpiderReflexPayload` (C2S: 0 takedown, 1 dodge) and `SpiderReflexFxPayload` (S2C: kind / value / yaw), registered in `SpiderTakedown.initialize`. Gametests: `SpiderReflexGameTests` (takedown timed + permanent cocoon, no expose, cooldown; window, evasion, slow radius, x2 counter, cooldown; melee + arrow prediction). Screenshot harness: `scratchpad/V0162TakedownHarness.java.txt` (+ camera mixin).

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
  (`ImpactWebEntity`, 10 dmg + knockback, **cocoons whatever it hits for 12 s** via `SpiderWebs.cocoonFor` as of v0.12.32 — and still pins a target knocked into a wall; 1 s cd), **V** Web Blossom
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
