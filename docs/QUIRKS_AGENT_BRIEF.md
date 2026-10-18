# Shared brief for every v0.16.7 agent (read fully before starting)

Project: Project Hero, a Fabric 1.21.1 Minecraft mod (Mojang mappings, Java 21, GeckoLib), id `projecthero`, package
`com.projecthero.mod`. Main checkout: `C:\Users\ethan\OneDrive\Desktop\Coding Projects\Superhero Mod` (you work in a
git worktree of it). Version is **0.16.7** -- never change `gradle.properties` version.

## Start

1. Your worktree may start at an OLD commit. First run `git merge --ff-only 4b498d36` (or `git reset --hard 4b498d36` if
   the worktree is clean and behind) so you have the Quirk framework. Check `git log --oneline -1`.
2. Read `docs/QUIRKS_PLAN.md` (the design, the slot mapping, the API) and the files it names.
3. Study a comparable finished system before writing: the Miraculous kits are the closest model
   (`src/main/java/com/projecthero/mod/miraculous/**`, `src/client/java/com/projecthero/mod/client/miraculous/**`,
   e.g. `pegasus/PegasusKit.java`, `PegasusMoves.java`, `PegasusConfig.java`, `client/miraculous/pegasus/*`, and
   `src/gametest/java/com/projecthero/mod/gametest/PegasusGameTests.java`). Copy their idioms: a `<Kit>Config` with every
   number as a static final, a `<Kit>Moves` with one method per move, a `Session` map for running moves, particles via
   `ServerLevel.sendParticles`, squad / boss checks, temporary blocks restored, poses as keyframes from the synced
   `animId/animStart`.

## Rules

- **Only touch the files you own** (listed in your task). If you truly need a change in a shared file, keep it minimal,
  say so in your final report, and never reformat shared files.
- Lang: add keys to `src/main/resources/assets/projecthero/lang/en_us.json` with a small Node script that inserts your
  block right AFTER your anchor key line (named in your task), keeps CRLF line endings, and `JSON.parse`s the result.
  Never duplicate a key. Player-facing text must be short and wrap-friendly (tooltips wrap; no huge lines).
- New gametest classes must be listed in `src/gametest/resources/fabric.mod.json` (your stub class already is).
- Gametests: mock players are not ticked by the server -- drive ticks yourself (see `PegasusGameTests.pump`).
  `helper.makeMockServerPlayerInLevel()` is deprecated-for-removal; `@SuppressWarnings("removal")` is fine.
  Use `Difficulty.NORMAL` when spawning `Monster` subclasses. Keep each test inside an 8x8x8 area.
- Build: `./gradlew compileJava compileClientJava compileGametestJava` must be green. Then run ONLY your own test class
  once: `./gradlew runGameTest -PtestFilter=<YourTestClass>` (fix failures). Do not run the full suite and do NOT run
  `build` (it runs every test).
- Never copy anything into the CurseForge modpack, never tag, never push. Commit your work on your worktree branch
  (one or a few commits, message prefix `v0.16.7 quirks/<you>:` and ending with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`).
- Do not copy scratch files into the main checkout. Keep generator scripts you write under `scratchpad/` IN YOUR
  WORKTREE (they get committed only if useful; untracked is fine).
- Useful reference scripts (read-only, in the MAIN checkout, untracked): `scratchpad/skin_tools.js` (PNG encode/decode,
  slim<->wide arms), `scratchpad/gen_raid_player_skins.js`, `scratchpad/gen_galactus_surfer_skin.js`,
  `scratchpad/make_suit_skins.js`, `scratchpad/gen_pegasus_v0166.js`, `scratchpad/gen_kwami_models.js` (GeckoLib
  model + texture generation). There is no Python; use Node (zlib) for PNGs.

## Costume skins (kit agents)

Write `assets/projecthero/textures/entity/quirk/<id>_wide.png` and `<id>_slim.png`: 64x64 standard player-skin layout,
the COSTUME ONLY (transparent where the player's own skin should show, e.g. the face if the hero shows it), drawn over
the player's skin by the core's costume layer. Make them DETAILED like a good hand-made skin: 3-4 shades per colour,
seams, belts, pouches, highlights, outer layer (hat/jacket/sleeves/pants) used for depth (collars, armour plates,
pads). Fill every face of every cube (top, bottom, sides, back) so there are NO gaps; check arm/leg side faces and the
slim arm's 3-px width. Produce a contact sheet / zoomed preview PNG of front/back/sides for the final report.

## Screenshots

The user wants screenshots of important things. If you can, verify visuals in the real client with the screenshot
harness pattern used before (examples in the MAIN checkout: `scratchpad/MiraculousHarness.v0166.java.txt`,
`scratchpad/V0162SuitV2Harness.java.txt` + `...CameraMixin.java.txt`; they are dropped temporarily into the client
source set, run with `./gradlew runClient` under a time limit, then REMOVED before committing). Save the PNGs to
`C:\Users\ethan\OneDrive\Desktop\Coding Projects\Superhero Mod\scratchpad\quirk_shots\<you>\` (absolute path, outside
git) and list them in your report. Time-limit every client run (kill it after ~6 minutes). If the harness won't work,
say so and fall back to texture previews.

## Final report (keep it short)

Branch name + commit hashes, what was built, what is NOT done or not verified, any shared files touched, screenshot
paths, test results.
