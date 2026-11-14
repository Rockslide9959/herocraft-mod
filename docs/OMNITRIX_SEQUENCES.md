# Omnitrix show transformations (v0.17.3, retuned v0.17.4)

> **v0.17.4: switched back on** (`SHOW_SEQUENCES = true`). The note below is how the v0.17.3 hold-back worked.
>
> **Held back from the v0.17.3 release** (user: "I want to exclude the new Ben 10 aliens and all the transformation scenes from this
> update"). The release switch is `OmnitrixFeature.SHOW_SEQUENCES` (`false`), read through `OmnitrixFeature.showSequences()` /
> `sequenceAllowed(id)`. While it is off, `ShowSequences.get/has/byHash` answer only for `OmnitrixFeature.SHIPPED_SEQUENCES`
> (`four_arms`: its right-click long transformation already shipped in v0.17.2, so released players keep it), and a right-click on
> any other alien is the normal quick slap with the normal transform sound, as in v0.17.2. Everything below stays in the code.
> **To ship them:** set `SHOW_SEQUENCES = true` (one line). To hold Four Arms back too, empty `SHIPPED_SEQUENCES` (and reword the
> "right-click: the full show transformation" hints in `en_us.json`). The gametest server turns the switch on by default
> (`fabric-api.gametest`), so the sequence tests keep running; `OmnitrixHoldBackGameTests` checks the off state.
> The six new prototype aliens (Snare-oh, Shock Rock, Frankenstrike, Eye Guy, Benwolf, Arctiguana) are held back the same way by
> `OmnitrixFeature.PROTOTYPE_ALIENS_0173`.

A right-click on the raised dial plays the alien's transformation from the show, shot for shot and cut to the show's own audio.
A left-click is still the quick slap. Four Arms was the first one (v0.17.2). From v0.17.3 every alien's sequence is its own pair
of classes on a shared framework, so several aliens can be built in parallel without two branches touching the same lines.

**The user's posing brief:** "make the posing and everything look exactly like how it looks in the show, try to be as expressive as
you can without bending the limbs". Limb parts stay rigid in those: no elbow or knee bends. **v0.17.3: Four Arms was remade with bent limbs** (user: "you may bend the
limbs ... dont break the body or make limbs detatch"), through `BentLimbs` (below); **Heatblast got the same v2 remake**, and Ghostfreak, XLR8, Stinkfly, Wildmutt, Diamondhead and Ripjaws bend too since their v1b touch-ups (below).
Get the expression from whole-limb angles, the head, the body's lean, part scales (`bulge`), the camera and the stage.

## Files

| File | What it is | Who edits it |
|---|---|---|
| `omnitrix/sequence/ShowSequence.java` | Common base: total ticks, change tick, skip tick, soundtrack, optional server hook | framework |
| `omnitrix/sequence/ShowSequences.java` | Common registry. Finds `<Pascal>Sequence` for each id in `OmnitrixSounds.SEQUENCE_ALIENS` by name | framework |
| `omnitrix/sequence/<Pascal>Sequence.java` | **One alien's common half** | that alien's agent |
| `omnitrix/OmnitrixLongTransform.java` | Runs whichever sequence started: root, skip on sneak, change, end, music stop | framework |
| `client/omnitrix/OmnitrixSequenceClient.java` | Client dispatcher: camera/FOV mixin entry, HUD, wrist tracking, the stage pass, flashes | framework |
| `client/omnitrix/sequence/ShowSequenceClient.java` | Client base class (the hooks below) | framework |
| `client/omnitrix/sequence/ShowSequencesClient.java` | Client registry. Finds `<Pascal>Show` for each registered sequence by name | framework |
| `client/omnitrix/sequence/<Pascal>Show.java` | **One alien's client half: the show** | that alien's agent |
| `client/omnitrix/sequence/CameraTrack.java` | Camera keys, cuts and shots, whip-pan spans, shakes | shared helper |
| `client/omnitrix/sequence/ShowShots.java` | Beats every transformation shares: slap shot, dial close-up, green flash, dial bloom and light, gold/white flash, whip streaks, green out | shared helper |
| `client/omnitrix/sequence/Stage.java` | The holder's screen plane and its flat primitives (gradient, radial, fill, rect, rays, dot) | shared helper |
| `client/omnitrix/sequence/KirbyDots.java` | The show's Kirby-dot field (streams, clumps, rim) | shared helper |
| `client/omnitrix/sequence/SwellLumps.java` | Springy swelling lumps on body bones, with an additive glow | shared helper |
| `client/omnitrix/sequence/ShowMath.java` | smooth / pop / env / hash / rot / colour maths | shared helper |
| `client/omnitrix/sequence/FourArmsShow.java` | The worked example | Four Arms |
| `client/omnitrix/sequence/LimbKeys.java` | v1b: a keyframed whole-body pose track for a humanoid show body (head, lean about the hips, yaw, drop, shrug, arms and legs with elbow / knee flex and twist through `BentLimbs`) | shared helper (Wildmutt, Diamondhead, Ripjaws) |
| `client/omnitrix/sequence/Shards.java` | v1b: cel-shaded facets lying on a part's surface (crystal, scales, fur tufts), skins over a limb that bend with its mitred joint (`sleeve`), drawing on a bent forearm (`onLimb`), a stage line | shared helper (Wildmutt, Diamondhead, Ripjaws) |
| `scratchpad/omnitrix_seq/` | `cut_clip.js`, `OmnitrixSeqHarness.java.txt`, `harness.js`, `film.js` | tools |

The soundtracks of all 12 show aliens are **already registered**: the `<id>_sequence.ogg` files are cut, and `OmnitrixSounds`
(`SEQUENCE_ALIENS`, `sequenceSound(id)`, `sequenceCue(id)`), `sounds.json`, the `en_us.json` subtitles and the sounds gametest's file
list already include them. A per-alien agent never edits those shared files.

## Adding an alien's sequence, end to end

A per-alien agent touches only these:

- `src/main/java/com/projecthero/mod/omnitrix/sequence/<Pascal>Sequence.java` (new)
- `src/client/java/com/projecthero/mod/client/omnitrix/sequence/<Pascal>Show.java` (new)
- `src/main/resources/assets/projecthero/sounds/omnitrix/<id>_sequence.ogg` (re-cut it if the start or end moves)
- `docs/reference/omnitrix_sequences/<id>/` (the cut's frames, contact sheet and clip.json)
- `docs/screenshots/v0173/omnitrix_sequences/<id>_*` (films)
- new textures under `textures/entity/omnitrix/sequence/<id>/`, if the show needs any
- only if the alien's body needs a sequence hook (the way Four Arms' lower arms take the sequence's pose), that alien's own client
  renderer (`client/omnitrix/alien/<Alien>Client.java`), calling a static method on its own `<Pascal>Show`

`<Pascal>` is the id in PascalCase: `heatblast` -> `Heatblast`, `grey_matter` -> `GreyMatter`, `xlr8` -> `Xlr8`.
Both classes are found by name, so there is no list to add a line to.

1. **Cut the clip** (see the timestamp table below; refine the start and end on the contact sheet):
   ```bash
   node scratchpad/omnitrix_seq/cut_clip.js heatblast 10.30 20.40
   ```
   This writes the ogg, `docs/reference/omnitrix_sequences/heatblast/f_000.jpg ...` (frame N = tick N) and `contact_sheet.jpg`
   (every 5th tick, labelled). The frames are gitignored. Each worktree re-runs the cut to get them.
2. **Map the beats.** On the contact sheet, list every cut and the beats between them: tick ranges, camera angle, framing, what the
   body does, what the background is (dots, rays, flash colour). Copy the Four Arms beat table style into your class javadoc.
3. **Common half.** The total is the last frame you keep. The change tick is the first tick of a flash or cut that hides the body change.
   ```java
   package com.projecthero.mod.omnitrix.sequence;

   public final class HeatblastSequence extends ShowSequence {
       public HeatblastSequence() {
           super(Aliens.HEATBLAST.id(), 202, 118);   // alien, total ticks, change tick
       }
       // optional: skipFrom(), serverTick(p, s, t) -- e.g. fire particles server-side
   }
   ```
4. **Client half.** Start from `FourArmsShow`: beat constants, a `CameraTrack`, then the hooks.
   ```java
   package com.projecthero.mod.client.omnitrix.sequence;

   public final class HeatblastShow extends ShowSequenceClient {
       static final float SLAP_END = 5f, CLOSE_AT = 14f, ... WORLD_AT = 190f;
       private static final CameraTrack TRACK = new CameraTrack(CameraTrack.keys(
               ShowShots.slapShot(0f, SLAP_END), ShowShots.dialCloseUp(5.01f, 30f),
               new float[][] { { 30.01f, 0.6f, -50f, 1.1f, 1.05f, -0.1f, 0.3f }, ... }));

       public HeatblastShow() { super(Aliens.HEATBLAST.id()); }
       @Override public float worldAt() { return WORLD_AT; }
       @Override public CameraTrack track() { return TRACK; }
       @Override public void pose(AbstractClientPlayer p, HumanoidModel<?> m, float t) { ... }
       @Override public void drawStage(Stage st, float t) {
           st.gradient(0xA9CB63, 0x86AE48, 0x4C7524);
           KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t, KirbyDots.Style.show());
       }
       @Override public void drawOverlay(Stage st, float t) {
           ShowShots.slapFlash(st, t, 3.5f);
           ShowShots.greenOut(st, t, 176f, WORLD_AT, total());
       }
   }
   ```
5. **Compile**: `./gradlew compileJava compileClientJava compileGametestJava`.
6. **Film and compare** (next section). Iterate on poses, camera keys and stage until the side-by-side matches the show.
7. **Uninstall the harness** (`node scratchpad/omnitrix_seq/harness.js uninstall`), compile again, commit.

### The hooks (`ShowSequenceClient`)

Every hook gets `t`: ticks since the slap, with the partial tick. The dispatcher calls them only while this alien's sequence plays.

| Hook | Default | What for |
|---|---|---|
| `worldAt()` | (required) | From this tick the holder sees the world through their own camera again |
| `track()` / `camera(p, t, partial)` | none | The holder's scripted camera (`CameraTrack` keys), until `worldAt` |
| `cameraScale(p, t)` | 1 before the change, height/1.8 after | How the camera keys grow with the alien |
| `fov(t)` | 62 | Field of view (punch-ins, slow pushes) |
| `pose(p, model, t)` | nothing | The body's pose, run after the vanilla and watch poses. Rigid limbs, or bent with `BentLimbs.bend` (below). Copy sleeves/pants/hat at the end |
| `bulge(p, model, t)` | nothing | Part scales (`xScale` and so on), after the model resets them |
| `renderBody(pose, buffers, p, model, t)` | nothing | Extra geometry on the human body (e.g. `SwellLumps`). Everyone sees it |
| `renderWorldFx(pose, buffers, cam, p, t, partial)` | nothing | World glows round the body (`ShowShots.dialLight`, `bodyGlow`). Everyone sees it |
| `drawStage(st, t)` | nothing | The holder's cartoon background. The body is drawn again over it, so the world never shows |
| `drawOverlay(st, t)` | nothing | The holder's full-screen flashes, over everything, for the whole sequence |
| `burstAt()` | `worldAt - 11` | When the usual green burst plays in the world for everyone |

### Bent limbs (`BentLimbs`, v0.17.3)

From `pose()`, `BentLimbs.bend(limb, flex, twist)` bends an elbow or knee for that render only (`RIGHT_ARM`, `LEFT_ARM`, `RIGHT_LEG`,
`LEFT_LEG`). Flex + is the natural way (forearm forward, shin back); twist turns the whole limb about its long axis, which picks the way
the elbow or knee points. The model's limb parts (skin, sleeves/pants, an alien's skin) are drawn through `JointedLimbs`' hook with a true
mitre: the cut edge sits on the bend's bisecting plane at full depth, so the halves share their joint vertices (no gap, no pinch, the skin
continues), the axis slides inward on a deep bend so the inner side never turns inside out, and past ~105 degrees the outer corner gets a
flat chamfer cap. Extra limbs use `BentLimbs.cube(...)` + `drawPart(...)` (Four Arms' lower pair); things riding a forearm (the watch,
lumps) use `BentLimbs.toForearm(pose, limb, slim)` inside the arm's bone space. `FourArmsShow.POSE` is a keyframed pose track to copy.

**Heatblast v2 (v0.17.3, user: "make sure their limbs don't go weird or detach from the body and touch up the effects and poses").**
`HeatblastShow` was rebuilt the same way: its own copy of the pose track (`HeatblastShow.POSE`, 27 channels: head, lean, drop, shrug,
then x/y/z/flex/twist per limb; bends eased straight by the end so nothing snaps when the sequence stops), re-matched shot by shot to the
reference frames (beat table in its javadoc). Notes for the next remake:

- **Rock skin** (`growRock`): plates on a jittered grid per bone face (`REGIONS`), erupting as a wave from an origin (the watch, then up
  the arm, the chest, the jaw); molten orange as they bubble out, cooling to dark red. A greyscale plate texture with a white rim
  (`heatblast/rock_plate.png`) is tinted per plate, so the rim reads as the show's pink edge lines. Arm plates below the elbow ride the
  bent forearm (`toForearm`); the upper and lower halves overlap round the elbow so the stretched outer side of a bend stays covered.
  `HeatblastShow.Plates` (Diamondhead's crystal, Wildmutt's fur) is unchanged.
- **The alien's body hook**: `HeatblastClient.renderExtras` asks `HeatblastShow.headFire(p, partial)` (-1 = not playing) so the head
  stays dark until it ignites at 127, and calls `HeatblastShow.renderAlien` for the white-hot fist flares (additive shells on the bent
  forearm, not lit blocks) and the head glow.
- **Stage buffers**: `st.flat()` and `st.dots()` share one immediate buffer source. Fetching one ends the other, so re-fetch
  `st.flat()` after drawing any dots (a stale flat consumer throws "Not building!" and crashes the client).
- Bystanders still see `HeatblastKit`'s ambient flame particle over the head before it ignites (server side, untouched).

### v1b touch-ups: Ghostfreak, XLR8, Stinkfly (v0.17.3)

User: "do touch ups on the v1s for the others, make sure their limbs don't go weird or detach from the body and touch up the effects
and poses". Same shot lists and timing as v1. Films: `<alien>_v1b_vs_show.mp4` + sheet, `<alien>_v1b_sequence.mp4` (ours, 2x).

- **One pose path for all three.** After the change each body runs on a `FourArmsShow.Track` (reused as is: Catmull-Rom keys, holds,
  snaps on cuts) applied by `Xlr8Show.apply(m, row, breathe, twitch, bend)`: every part from its rest place (nothing left over from the
  kit's own idle/flight poses), the drop, the lean through `HipBend` (the torso never leaves the legs), then the head, limbs and
  `BentLimbs` elbows/knees. Ghostfreak's v1 float pose had pushed the torso, head and arms up by hand on top of the kit's pose.
- **Tails in the holder's view.** The holder's scripted camera counts as first person, and both world-simulated tails skip first person,
  so neither tail was ever in the sequence. Now each sequence scripts its tail's shape for the run of the show:
  `Xlr8Show.tailShape` -> `TubeTail.renderScripted` (hanging, curling up over the claws, the whip across the frame, curled over the
  crouch, streaming flat in the sprint); `GhostfreakShow.tailShape` -> `GhostfreakTail.render` (hanging, swept under him as he rears,
  an S-rippling wisp behind the float). Both lay the tail out on the body bone's axes from its usual root, so it stays on him.
- **Stinkfly's wings** were folded for the whole sequence (they open on the flying flag). `StinkflyShow.wingOpen` drives
  `StinkflyClient.openAmount` while it plays: folded until they burst open in the torso shot, spread from then on.
- **Side-on shots.** A body leaning toward or away from a side-on camera shows its far arm as a block hanging off the torso (the
  shoulder is hidden behind it). XLR8's sprint and Ghostfreak's float are framed from three-quarters front now.
- **Effects.** XLR8: the face shot's light is a fan of feathered warm beams (the flat bars turned into grey slabs over the black dots),
  the sprint's shadow is a feathered smear from behind him to the corner (it was a plank floating off to the side) with speed lines
  along it. Ghostfreak: pale wisps curling across the burst, a faint lilac haze on the float. Stinkfly: yellow-white flares bursting up
  behind him as the wings open.
- **Gotcha (not fixed here, gameplay):** in daylight the sun burns Ghostfreak every second, sequence or not, so his show gets red hurt
  flashes outdoors by day. The harness now films Ghostfreak at night (one line in `OmnitrixSeqHarness.java.txt`).

### v1b touch-ups: Wildmutt, Diamondhead, Ripjaws (v0.17.3)

User: "do touch ups on the v1s for the others, make sure their limbs don't go weird or detach from the body and touch up the effects and
poses please." Shot lists and timing kept. Films: `<alien>_v1b_vs_show.mp4` (+ `_sheet.jpg`) and `<alien>_v1b_sequence.mp4` (ours alone, 2x).

- **One pose track per human body** (`LimbKeys`, like `FourArmsShow.POSE`). The v1 poses were per-beat `rot(part, w, ...)` blends from
  whatever the model held, so inside one shot the body snapped back to the vanilla pose at every beat change. A track can't: every frame
  is sampled from the same keys (hold keys ease, keys under 0.05 apart cut). Start a show's track at its first cut; before it the vanilla
  and raised-watch pose run as usual.
- **Lean about the hips.** Setting `body.xRot` alone swings the torso's bottom off the legs (vanilla pivots it at the neck). v1 Diamondhead's
  stance, Ripjaws' stance and Wildmutt's hunch all did that. `LimbKeys.apply` leans through `HipBend` and moves the head and shoulders with
  it. Keep the track's roll (`body.zRot`) tiny for the same reason.
- **Bent elbows and knees** (`BentLimbs`): Diamondhead's crossed forearms, flex and stance; Ripjaws' clawed arms and stance; Wildmutt's claws
  at the face and the hunch's hanging arms. Twist: + turns a left arm's front face inward and a right arm's outward. Crossed forearms in
  front of the chest: upper arms forward (xRot about -1.1), flex about 1.85, twist inward about 1.45.
- **Anything on a limb bends with it.** Skins over a limb are one box drawn through `BentLimbs.draw` with the limb's joint (`Shards.sleeve`:
  Diamondhead's crystal sheath, Ripjaws' grey skin), so they mitre with it. Lumps, facets, veins, fingers and fur below the elbow go through
  `Shards.onLimb` (`BentLimbs.toForearm` when y is below the joint: y 4 for arms, 6 for legs, bone px).
- **Facets, not cubes** (`Shards.draw`): a low four-sided pyramid on a diamond base lying on the face it grows from, in three flat shades. v1
  used Heatblast's rock plates (boxes tilted off the skin at random), which read as loose cubes floating round the arm and face.
- **One vertex buffer at a time.** `buffers.getBuffer(type)` ends the buffer fetched before it, so writing to an older one throws
  `IllegalStateException: Not building!` (it crashed the first Wildmutt film). Fetch, draw everything of that type, then fetch the next.
- **Wildmutt (GeckoLib)** pitches about his hip joint (geo y 17.5, z 10: `root` rotX plus posY / posZ = P - R*P), not the root's origin under
  his belly, which sank the hind feet and floated the front paws when he reared and roared. `poseBones` takes each limb's world angle and
  subtracts its parent's, so the hind legs stand straight while he rears, fold into a sit on the slope (hips down 5.5 px, thighs +30, shins
  -34) and the paws stay flat. The idle clip keys every bone's rotation and the root's position, so nothing sticks after the sequence.
- **Per alien.** Diamondhead: the crystal is a sheath growing over the arm, edged and studded with facets; face facets lie flat on the cheek
  and hair; the profile camera pulled back to three-quarters (v1 was a flat green wall); the eye's facets are cel-shaded; the shoulder spikes
  are tall facets. Ripjaws: the arm runs across the frame; webbed, splayed, clawed fingers; scale chevrons and a jagged skin edge; gill slits
  on the side of the neck (v1's stood off the shoulders like bars); triangular teeth; a smooth tapering lure; a lighter face wash; the eye
  close-up drawn (screwed shut on pale mint). Wildmutt: smaller lumps sunk into the skin with red veins ahead of them; spiky fur tufts.
- None of the three uses `HeatblastShow`'s helpers any more (`Plates`, `copyLayers`, `line`), so they don't depend on that file.

### Keeping the body joined (`ShowBody`, v0.17.3 touch-up)

Setting `body.xRot / yRot / zRot` directly swings the torso about the **neck**, so the shoulders and hips stay put: the arms float off
the shoulders and the torso slides off the legs. That was the main "limbs detach" bug in the v1 Cannonbolt, Wildvine, Upgrade and Grey
Matter shows. `ShowBody` (package-private, used by those four) turns the torso without breaking it:

- `reset(m, drop)`: rest pivots, the body `drop` px lower (a crouch), every rotation zeroed.
- `tilt(m, roll)` (sideways, about the hips), `twist(m, yaw)` (at the waist, the shoulders swing round), `lean(m, pitch)` (`HipBend`).
- `apply(m, k, bend)`: poses everything from one sample of a `FourArmsShow.Track` (the Four Arms keyframe track, reused). The track's
  spare channels are the waist twist (`split(...)` = `ShowBody.TWIST`) and the tilt (`set(ShowBody.TILT, v)`).
- `layers(m)`: sleeves, pants, jacket, hat.

**Knee and elbow rule of thumb** (BentLimbs' flex/twist with a limb swung out sideways by `zRot = a`): twist `1.57` (mirrored for the
left) turns the bend toward the body's midline. For a leg, knee flex `≈ a` then brings the shin back to **vertical**: a squat with the
feet apart (Cannonbolt `a = 0.7`, Grey Matter's frog crouch `a = 1.0`). With `a ≈ 1.5` (the thigh out level) the shin hangs straight
down: Wildvine's spider legs. A plain big knee flex with twist `1.3` and a small `zRot` gives a diamond (knees out, feet together), which
reads wrong. For an arm out sideways, twist `+1.57` (right) bends the forearm **up** (a flex), twist `-1.57` bends it down toward the hip
(Upgrade's bowed arms). Drop the body about `6 * (1 - cos a)` px to keep the feet on the ground.

**Things riding a bent forearm** go through `BentLimbs.toForearm` inside the arm's bone space: Cannonbolt's swelling hand lumps and Grey
Matter's grey hands do. Upgrade's black shell is one box per bone, so his arms stay straight until the change.

**Hidden aliens in the harness:** `Omnitrix.select` refuses a hidden alien (Cannonbolt, Wildvine) that isn't discovered, and the default
alien (Heatblast) plays instead; the harness now calls `Aliens.discover(p, alien, true)` first. Pass the `to` tick explicitly
(`harness.js flag cannonbolt self 0 272`): the default end is read before the sequence starts.

#### v1b touch-ups (Cannonbolt, Wildvine, Upgrade, Grey Matter)

Films: `docs/screenshots/v0173/omnitrix_sequences/<alien>_v1b_vs_show.mp4` (+ `_sheet.jpg`) and `<alien>_v1b_sequence.mp4` (ours alone,
2x nearest-neighbour).

- **Cannonbolt**: the slap hand goes up sideways by his head (it pointed at the camera); the swelling hand is lifted out to the side on a
  bent elbow so his face shows past it, its lumps riding the forearm; in the belly shots the arms are forced out round the belly with the
  forearms hugging its sides (they stuck straight out of its middle); under the shell the arms are tucked in, forearms up (they poked
  at the lens as rigid slabs in the profile and chest shots); the stance is the show's squat, legs apart with vertical shins, arms flung
  up and out (was a straight-limbed T). New: dark and gold **spin streaks** over the ball while it spins hard, thinning as it slows; the
  stance camera is closer, like the show's.
- **Wildvine**: one pose track. The fright pulls his shoulders up; the hunch over the pods bends at the hips (the torso no longer slides
  off the legs); his hands come up round his sides to clutch his head (they swept through the lens); the roar's arms bend; the whip arm
  stretches long as it cracks (`bulge`, sleeve too); the crouches use spider legs (thighs out level, shins straight down), the claws out
  to the sides. New: the show's black shadow streak behind the last stance.
- **Upgrade**: the slap hand goes up open beside his head like the show's first frame; the seizing arch and shudder turn about the hips;
  the eye opens as a thin ring from a small one (it was a solid blob at first); the stance is low with bowed elbows and soft knees.
- **Grey Matter**: one pose track. The slap hand up; the new hands held up palms out on bent elbows (they were on straight arms), riding
  the forearm, with curling fingers, round fingertip pads and a thumb; the arm thrown at the camera; the frog crouch with the knees out
  and the feet apart; the leap with bent legs. The body's twist and tilt keep the shoulders on.

### Final-pose rework: Heatblast, Cannonbolt, Wildmutt, Ghostfreak, XLR8, Upgrade (v0.17.3)

User: "rework all the final poses to match the show." Compared in `docs/screenshots/v0173/omnitrix_final_poses/` (ours vs show, `NOTES.md`).
Only the last shot of each changed (its keys, camera and stage); earlier shots and the timing are untouched. The show's final poses are
wide, uneven power stances from a low or tilted camera; ours were upright, square and symmetric.

- **Sideways tilt in the human-body tracks.** `Xlr8Show.apply` (XLR8, Ghostfreak, Stinkfly) now reads the track's spare channels as
  `ShowBody.TILT` (a sideways tilt about the hips) and `ShowBody.TWIST`, like `ShowBody.apply`; 0 in a track that doesn't set them, so
  those poses are unchanged. Heatblast's own track has no spare channel: `HeatblastShow.stanceRoll(t)` tilts the stance the same way.
  Tilt + leans the top of the body toward the alien's left (the right of frame from in front); the arms and head roll with the chest.
- **Heatblast**: leans back and over to his left; the raised left fist is out to the side at head height (upper arm near level, elbow
  flex 1.0; it was folded in beside the face at 1.85); the right fist low, out and toward the lens; the camera pulled back and nearly
  square on (knees to crown). From the guard on the fist flares shrink to a white shell just over the hand with a thin haze.
- **Cannonbolt**: a squat (thighs forward, shins bent back near vertical, the right knee a little higher; they were splayed straight
  to the corners), the arms higher and wider in the V, the camera lower. New: `CannonboltShow.renderAlien` (from
  `CannonboltClient.renderExtras`) puts the show's yellow shell domes on his arms from the moment he opens out of the ball: a big one by
  each shoulder, a smaller one on each bent forearm, facing the front and straddling the arm's upper edge so they fan over the V.
- **Wildmutt**: the camera three-quarters front from his right (he faces the right of frame); the snout up at about 60 degrees (neck
  22, head 12-18; it was straight up), the jaw wide, the front paws reaching forward onto the slope. The roar's stage is the show's pale
  khaki sky with black blobs (it was the green swirl), with an ink edge on the slope.
- **Ghostfreak**: front on and close, slanted (`TILT` 0.42) with the head up at the right and the tail sweeping off low to the left
  (`tailShape`'s optional sixth value, a steady sideways swing read by `GhostfreakTail`); the right claw raised out to the side, the left
  hanging. New: `GhostfreakShow.renderAlien` (from `GhostfreakClient.renderExtras`) draws the open black front widening from under the
  badge to the hem and three long pale fingers with dark tips on each hand, through the float.
- **XLR8**: side-on from his right (facing right, visor down); both arms swung back, elbows high, forearms folded forward; a wide planted
  stride (front foot under the hips, back leg far behind); the tail rising out behind (18 -> 33 degrees above level), its links
  stretched 1.12x through `TubeTail.renderScripted`'s new `stretch` overload (the old signature still calls it with 1).
- **Upgrade**: the body slants over to his left (`ShowBody.tilt` 0.3) from the reveal; from tick 155 the fists swing forward at the
  low camera (right one low and out left, left one out right, elbows soft) so they fill the foreground; both ease out after the world cut.

### Camera and pose retune against the show: all 12 (v0.17.4)

User: "make the camera angles and poses match up better with what shows in the transformation scenes please, right now they only look
kinda decent". Every sequence was filmed as it was (`<alien>_old`), compared beat by beat with the show's frames, retuned, and filmed
again (`<alien>_new`). Only camera keys, FOV, pose keys and a few framing comments changed: the shot lists, beat ticks, soundtracks,
stages and effects are as before, and no kit or shared helper was touched. Each change carries a `v0.17.4` comment in its Show class.

**Tools.** `harness.js flag <alien> self 0 -1 <tag> <t1,t2,...>`: the optional sixth argument films only those ticks (the harness steps
straight to each), so a key-beat check takes about 90 s instead of 4-7 minutes. `scratchpad/omnitrix_seq/compare.js <alien> <ticks>
<tag1>,<tag2> [--labels OLD,NEW]` makes a sheet with one row per tick: show | capture 1 | capture 2. Sheets and side-by-sides with show
frames go to `docs/screenshots/v0174/omnitrix_sequences/vs_show/`, which is git-ignored (copyrighted stills); our own films go next to it.

**Films** (`docs/screenshots/v0174/omnitrix_sequences/`): `<alien>_before_after.mp4` + `_sheet.jpg` (old | new, ours only, committed).
Git-ignored: `vs_show/<alien>_compare.jpg` (show | old | new at the key beats) and `vs_show/<alien>_new_vs_show.mp4`.

**What the old versions got wrong, over and over:** the slap was the shared full-length shot where the show is waist or chest up; the
close-ups stood too far back or off to the side (the show fills the frame with the hand, the eye, the face); the body shots after the
change were small figures where the show is close, and the final stances were cropped where the show has the whole body (or the other way
round). Gotcha: sed -i in Git Bash strips every CR from a CRLF file, not just the edited line; edit with a CRLF-aware script.

- **Heatblast.** Slap: chest-up from a little above, the watch forearm low in front of the belly, the right hand up by the face, then
  slapping down onto the dial in the middle (it was a high forearm across the chest). Rock arm (38-62): tight, a little above, a 6-degree
  longer lens, so the rock fills the frame and the face stays out (the show never shows the face there). Pull-back off the symbol: starts
  tighter, ends much wider (2.6 vs 2.05), the whole torso and the low fists with room round him. Stance: closer and lower (2.15 -> 1.9,
  he's cropped at the thighs like the show), the raised fist up level with the head (elbow flex 1.0 -> 1.45), the near fist low at the
  hip, `STANCE_ROLL` 0.15 -> 0.2.
- **Four Arms.** Slap: its own close chest-up shot, the right hand already high from the first frame. Hand swelling: starts nearly square
  on and above both fists round the dial, then slides round his left. Back shot (101-128): close and from above (1.5 at 1.95 high; it was
  2.2 from low behind), and all four arms flung wide across the frame, forearms clawing (they were folded up beside the head, hidden
  behind the back); the orbit ends on his left front with all four reaching forward. Wind-up and lunge: closer (1.4-1.6). Stance:
  pulled back so the feet stay in.
- **Wildmutt.** Slap: its own waist-up shot from his left front, with a new slap pose (the watch forearm forward and low, the right arm
  flung wide, then slapping down); the human pose track now starts at tick 0. Up the arm (66-100): lower and further up the arm, toward
  the sleeve. Face, hunch from behind, side-on head and the face-and-claws shot: all closer; the claws stay low at the chest so the face
  fills the lens (they rose up over the head, then swung into the lens). After the change: the head profile close (1.2 -> 1.08, the head
  filling the right of the frame), the paw shot from his right and down at the paw (it was the whole front of him from his left), the
  rearing shot closer.
- **Diamondhead.** Opening: Ben waist up, level and square on (it was from low with the head cut off). Arm and hand: closer, the arm
  raised a little above level. Face: square on and a little further back so the whole face is in. Head three-quarter: closer (0.95 ->
  0.62). Crossed forearms: closer and lower. Pull-back: ends much wider, a small whole figure like the show's flex. Stance: nearly square
  on (30 -> 14 degrees), the whole body in frame, chest up and back, both fists out to the sides on bent elbows, legs wider.
- **XLR8.** Slap: close and low with a slap hand (new shared helpers `Xlr8Show.slapClose(end)` and `slapArm(m, t, end)`, also used by
  Grey Matter's camera, Stinkfly and Ripjaws: the right hand flung up beside the head then slapping down, easing back into the raised-watch
  pose over 4 ticks). Face (36-62): further round his left, head bowed deeper. Ben's and XLR8's eyes: right in (0.15-0.17). Legs, claws
  and the low badge shot (now from his left): about 40% closer. Visor face: lower look point and closer. Crouch: low from his left front,
  slanted (`TILT` -0.2), knees wider. Dash: three-quarter front from his right, lower, lean 0.62 -> 0.5.
- **Grey Matter.** Slap closer. New hands (28-62): from his left front with Ben to the right of frame, the hands held toward the lens.
  Arm throw: from his right front with the right arm, so it crosses to the right instead of covering his face. Back shot: arms down,
  slightly out; the swing ends close behind him. Face, nape, front face, crouch (from a little above, hands reaching at the camera) and
  leap (`TILT` -0.2): closer.
- **Stinkfly.** Slap closer, with the slap hand. Straining face: fills the frame from a little below, easing back so the four eye-stalks
  stay in. Glowing torso: much closer and lower, arms flung up in a V. Forehead badge: fills the frame. Hover: from below, nearly front
  on, slanted (`TILT` 0.38), legs splayed.
- **Ripjaws.** Slap closer, with the slap hand. Outstretched arm: from above and behind his left shoulder so it runs across the frame.
  Front face, profiles and back of the neck: about 30% closer. Lure: three-quarters from his right. Cheek and teeth: three-quarters (60
  and 50 degrees). Rise: low in front. Stance: the whole body, nearly front on, arms wide with the forearms curled up, right claw raised.
- **Upgrade.** Slap: chest up, a little above, the watch forearm raised so the dial sits under the face. Dial close-up: closer, square on.
  Reveal: close and low, looking up. Stance: arms out and down at his sides; from 155 both fists swing forward into the bottom corners
  together. `STANCE_TILT` 0.3 -> -0.08 (nearly upright, like the show).
- **Ghostfreak.** Slap: much closer and steeper from above. 24-32 waist up, head to the right; 32-38 tight on the face; seam shot square
  on; purple eye centred and closer; rearing profile close and from below. Float: `ShowBody.TILT` 0.42 -> 0.72 (the show's diagonal).
- **Cannonbolt.** Slap a little wider. Under the forearm: look point at the wrist. Hand swelling: in close, the hand big in front of his
  head. Belly: fills the frame. Eye: tighter, on one eye. Face on the chest: closer. Opening out: new keys so the body stays as big as the
  ball. Stance: arms out near level, so the domes run in a row like the show's (was a V).
- **Wildvine.** Fright face and frown: closer. The tilt ends on the collar and shoulder from his left front. Eye close-up: level with the
  eye. Head profile: close from his left front (was a small figure from behind). 166-186 aimed at the head and the hands clutching it.
  Wide shot: closer. Last crouch: closer and lower, claws out, slanted (`TILT` -0.22, eased to 0 under the green out).

Still short of the show (Minecraft limits or not done): `CameraTrack` has no roll, so the show's Dutch angles can't be matched; Wildmutt's
and Four Arms' hand shots still show the watch body where the show has only flesh; Diamondhead's cheek crystal covers more of the face than
the show's; only the holder's view was filmed (the bystander view of the new poses is unchecked).

### Camera keys (`CameraTrack`)

Each key is `{ tick, distance, angle, camera height, look height, look right, look forward }`, in a 1.8-block human's blocks
(scaled with the body after the change). Angle 0 is in front, + is toward the player's right, 180 is behind. A key less than
0.05 ticks after the previous one is a **cut** (`{ 74.01f, ... }` after `{ 74f, ... }`), and each cut starts a new shot:
`track.shot(t)` counts them, `track.shotStart(t)` gives the tick a shot began. `CameraTrack.W` as the camera height and look
point frames the watch dial from above. Keys ease into each other with smoothstep; `.linear(from, to)` runs the segments inside
that span straight through, for a whip pan. `.shake(from, to, amp, decay)` adds a hand-held shake, for a roar.

### Shared beats (`ShowShots`)

- `slapShot(from, to)`, `dialCloseUp(from, to)`: camera keys for the slap and the dial close-up.
- `slapFlash(st, t, at)`: the green flash off the slap (~18 ticks). `dialBloom(st, t, from, to)`: the green bloom round the dial.
- `goldFlash(st, t, from, to)`: a gold flash to change the body under. `flash(st, t, rgb, a, in, b, out, alpha)`: any flat flash.
- `whipStreaks(st, t, from, to)`: blur and black and white streaks for a whip pan.
- `greenOut(st, t, greenAt, worldAt, total)`: the green flash out of the stage, then the green fading off the world.
- `dialLight(...)`, `bodyGlow(...)`: glows in the world (call them from `renderWorldFx`).
- `Stage`: `gradient`, `radial`, `fill`, `rect`, `rays`, `dot`. Screen units run -1..1 across each axis. Multiply an x measured
  in screen heights by `st.k()` to keep it round.
- `SwellLumps.render(pose, buffers, bones, lumps, t, popLen, skin, glow)`: lumps of `{ bone, x, y, z, half-size, erupt tick }`.

### Kirby dots (`KirbyDots`)

In the show, almost every staged shot has the olive or green field crossed by a few broad **streams** of black dots. The dots are
solid where they crowd together and break up toward the edges into clumps, then single dots of every size. The streams sweep across
the frame in long, gently curved bands, all roughly the same way within a shot. The frame's edges and corners are crowded with bigger
clumps, often heavier on one side. The middle, where the body is, stays mostly clear apart from small loose dots.

```java
// a new layout per shot (like the show's cuts), flowing along its streams while the shot lasts
KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t, KirbyDots.Style.show());
// fewer, steeper streams in the burst's dark green, a wider clear middle
KirbyDots.draw(st, st.shotSeed(shot) + 5, t, KirbyDots.Style.show().bands(2).angle(70).rgb(0x0D3F0B).clear(0.5f));
```

`Style` knobs: `bands` (streams, 0 = only the rim), `density`, `size`, `width`, `rim` (edge and corner clumps), `clear` (thinned middle
radius in screen heights), `flow` (drift speed, negative reverses), `angle` / `spread` (stream direction, unset = per shot), `bend`,
`popIn` (pop in over N ticks from t = 0), `rgb`, `alpha`. It is drawn in screen space, so it fills the frame from any camera angle.
Every dot boils (a tiny re-jitter every 2 ticks).

## Tick mapping

Tick 0 is the slap (the right-click). Video second `v` of a clip cut from `start` is tick `(v - start) * 20`. The cut's frame
`f_NNN.jpg` is tick NNN. Four Arms was cut from the user's own episode clip: tick `(v - 2.55) * 20` there, which is
`(v - 132.585) * 20` in the compilation (found by audio cross-correlation). The compilation's Four Arms ending runs a little slower than
the user's clip: its final green flash and world cut are about 0.5-1.5 s later than our tick 236-261.

## Filming: the harness and the side-by-side

Before **any** `runClient`, set the low-graphics options (lowest settings, master volume 5%). Use one client at a time and keep runs short.

```bash
node scratchpad/omnitrix_seq/harness.js install                         # copies the harness into src/client + one hook line
node scratchpad/omnitrix_seq/harness.js flag heatblast self             # run/omnitrix_seq.flag: alien, mode, [from] [to] [tag]
node "C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/low_graphics_options.js" run/options.txt
./gradlew runClient --args="--username SeqFilm"                         # films every tick, then quits on its own
node scratchpad/omnitrix_seq/film.js heatblast_self --sbs --sheet       # heatblast_vs_show.mp4 + its frame sheet
node scratchpad/omnitrix_seq/film.js heatblast_self --sheet             # heatblast_self.mp4 (ours alone, with the sound)
node scratchpad/omnitrix_seq/harness.js uninstall                       # before committing
```

- The harness makes a flat world, grants the watch, raises it, selects the alien and right-clicks. It then **freezes the server's
  ticks and steps them one at a time**, pinning the client sequence to the same tick (`OmnitrixSequenceClient.freezeAt`) and the random
  layouts to a fixed seed (`seedOverride`). So screenshot N is exactly tick N, and two runs of the same code give the same frames.
  Pass a fixed `--username`, or the dev account gets a random default skin each launch, and the human frames differ only by skin.
  The v0.17.3 refactor was checked this way: before and after frames matched exactly (after the change, every frame was identical;
  before it, the only difference was the random skin).
- Modes: `self` is the holder's scripted view (what the player sees). `watch` is a bystander's third-person front view, with the
  scripted camera and stage off (`OmnitrixSequenceClient.bystander`). It shows the poses, lumps and world glows other players see.
- A short range films faster: `harness.js flag heatblast self 100 140`. Frames go to `run/screenshots/seq_<tag>_NNN.png`.
- `film.js` options: `--sbs` gives ours and the show side by side, tick-locked, with the soundtrack. `--vs <tag2> [--labels A,B]` puts
  two captures side by side (before and after). `--diff` prints the per-frame PSNR of the two (identical renders show as `inf`).
  `--sheet` makes a PNG of every 5th tick. `--from/--to` limit the range, and `--out` names the file.
- Send the user an **MP4 plus a frame sheet, never a GIF**. Films go in `docs/screenshots/v0173/omnitrix_sequences/`.

## The compilation video's timestamps

`C:/Users/ethan/Downloads/EVERY-ALIEN-TRANSFORMATIONS-SnapYT.App.mp4` (8:24, 640x360, 29.8 fps). Each alien's segment follows a black
title card with its name. The name stays over the first ~0.5 s of the clip as the card fades. Times are video seconds, refined from
frames (black-card detection, full-green-frame detection, 10 fps strips; Four Arms by audio cross-correlation). **Slap** is tick 0 and
the suggested cut start. **World** is the cut back to the scene. **Cut end** is World + 0.6 s (Four Arms' fade length): use it as the
cut's end and the sequence's `total` (the ticks column). All 11 new oggs and reference folders were made with exactly these cuts.

| Alien | Card ends | Slap (tick 0) | Back in world | Cut end | Ticks | Segment ends (next card) | Notes |
|---|---|---|---|---|---|---|---|
| Heatblast | 10.13 | 10.30 | 19.80 | 20.40 | 202 | 23.22 | The title overlays ticks 0-10. World is the cut to the RV on the road; Heatblast shows inside it ~21-22 s, so extend the cut if you want him |
| Wildmutt | 27.49 | 31.10 | 48.00 | 48.60 | 350 | 51.14 | Green fades into the alley by ~48.3 |
| Diamondhead | 55.37 | 61.95 | 77.30 | 77.90 | 319 | 78.93 | |
| XLR8 | 83.19 | 87.15 | 99.50 | 100.10 | 259 | 104.03 | A second full green flash mid-sequence at 91.65-92.45 |
| Grey Matter | 108.33 | 111.10 | 123.20 | 123.80 | 254 | 124.84 | Green flashes at 120.7 and 122.55; the world fades in by ~123.25 |
| Four Arms | 129.24 | 132.585 | 145.03 (our tick 249) | 145.635 | 261 | 151.42 | Existing build, cut from the user's own clip; the compilation's end runs ~1 s slower |
| Stinkfly | 155.88 | 160.70 | 176.20 | 176.80 | 322 | 180.05 | Yellow-white flash ~175.6 before the green out |
| Ripjaws | 184.41 | 186.30 | 200.50 | 201.10 | 296 | 203.64 | A second full green flash at 188.85-189.50 |
| Upgrade | 208.07 | 211.10 | 219.50 | 220.10 | 180 | 222.30 | White flash ~217.7; world is the ceiling shot |
| Ghostfreak | 226.56 | 231.00 | 242.50 | 243.10 | 242 | 245.45 | |
| Cannonbolt | 249.71 | 252.15 | 265.20 | 265.80 | 273 | 269.98 | |
| Wildvine | 274.31 | 277.45 | 293.80 | 294.40 | 339 | 297.00 | The first green is a fade over Ben's face (~277.6); a second full flash at 281.75-282.25 |

## A new sound event (an alien outside the 12)

For an alien outside `OmnitrixSounds.SEQUENCE_ALIENS`:

1. Add the id to `SEQUENCE_ALIENS`. That registers `projecthero:omnitrix_<id>_sequence` and its Cue (volume 0.75, no jitter).
2. Add a `sounds.json` entry (`"name": "projecthero:omnitrix/<id>_sequence", "stream": true`, subtitle
   `subtitles.projecthero.omnitrix_<id>_sequence`) and the subtitle in `en_us.json`. Merge both key by key.
3. Add `"<id>_sequence"` to `SYNTH_FILES` in `OmnitrixSoundsGameTests`.

Or keep it outside the framework: override `cue()` and `sound()` in the `ShowSequence`, and call `ShowSequences.register(...)`
and `ShowSequencesClient.register(...)` at init.

## Gotchas

- Many files are CRLF. Edit with the Edit tool or CRLF-aware scripts (`sed -i` in Git Bash strips the CRs).
- `pose()` runs after `OmnitrixPose`'s raised-watch pose. Before the change, the arm holding the dial is already up.
- The stage covers the world only while `camera()` is non-null (before `worldAt`). After that, only `drawOverlay` draws.
- The body changes on the server at `transformAt`. Hide it under a flash or a cut, because the model swaps on that tick.
- The holder's body is drawn over the stage with full-bright light and no shadow. Anything drawn in `renderWorldFx` is drawn after
  the body (depth cleared), so it sits on top.
- Gametest `everyShowSequenceIsWellFormed` checks every registered sequence: a declared alien, skip < change < total, 1-30 s,
  a soundtrack.
