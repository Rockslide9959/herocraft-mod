# Omnitrix show transformations (v0.17.3)

A right-click on the raised dial plays the alien's transformation from the show, shot for shot and cut to the show's own audio.
A left-click is still the quick slap. Four Arms was the first one (v0.17.2). From v0.17.3 every alien's sequence is its own pair
of classes on a shared framework, so several aliens can be built in parallel without two branches touching the same lines.

**The user's posing brief:** "make the posing and everything look exactly like how it looks in the show, try to be as expressive as
you can without bending the limbs". Limb parts stay rigid in those: no elbow or knee bends. **v0.17.3: Four Arms was remade with bent limbs** (user: "you may bend the
limbs ... dont break the body or make limbs detatch"), through `BentLimbs` (below); the other aliens are still rigid.
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
