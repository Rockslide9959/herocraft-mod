# The Recalibrated Omnitrix (v0.17.2)

User: "okay make the recalibrated omnitrix please. Add the first 10 aliens aswell ... Just make them basic versions of the aliens for
now please that we can revamp later. ill give you sound effects too so use them please. Also come up with a way for the player to
recalibrate the omnitrix please."

Approved (user, 2026-11-02): **Pod + Recalibration Chip**, **Alien Force ten only** on the recalibrated dial, Alien X **debates before
each move**.

## How it works (framework -- already in master)

* `Omnitrix.recalibrated(p)` -- a flag in the persisted timers (`omnitrix.recal` = 1); no codec change, kept through death, synced.
  `Omnitrix.prototypeTransforms(p)` counts transformations made with the prototype (`omnitrix.transforms`).
* `Aliens` declares a second roster: `SWAMPFIRE, ECHO_ECHO, HUMUNGOUSAUR, JETRAY, BIG_CHILL, CHROMASTONE, BRAINSTORM, SPIDERMONKEY, GOOP,
  ALIEN_X` (`Alien.recalibrated() == true`). `Aliens.all()` / `playable()` stay the prototype's ten; `Aliens.recalibrated()`,
  `Aliens.roster(p)`, `Aliens.playable(p)`, `Aliens.dial(boolean)` and `Aliens.available(p, id)` are the watch-aware versions. The dial,
  the slap, `select`, Master Control, the auto-transform (`OmnitrixAuto`, each rule lists an Alien Force alien after the original), the
  pod's flicker and the wheel all use the wearer's roster.
* **Recalibration**: craft a **Recalibration Chip**; stand in a fuelled **Containment Pod** as a human wearing the prototype, with at least
  `OmnitrixConfig.RECAL_MIN_TRANSFORMS` (25) transformations made with it, and right-click the pod with the chip. 30 s
  (`RECAL_TICKS`), `FUEL_PER_RECAL` (4) fuel, **no damage**: the field stays green, the wearer is held in the middle, the dial turns on
  its own (the user's dial-turn sound) and the new aliens flicker on the wearer, the HUD shows a green RECALIBRATING box (the
  `EXTRACTION` attachment carries `-1 - ticks`). Stepping out, dying or transforming stops it and hands the chip back. At the end
  `Omnitrix.recalibrate(p)`: the flag, the first Alien Force alien selected, the user's transform sound, a title.
  `OmnitrixPodBlockEntity.tryRecalibrate / startRecalibration` (tests call the latter).
* The pod's extraction of a recalibrated wearer pops a **Recalibrated Omnitrix** item (`OmnitrixItems.RECALIBRATED_OMNITRIX`); putting it
  on gives the recalibrated watch (`Omnitrix.grant(p, true)`).
* **Sounds** (the user's, `sounds/omnitrix/recal_*.ogg`, mono, peaks ~-1.5 dBFS; `scratchpad/recal_v0172/` has the ffmpeg line):
  `RECAL_CORE_UP` (prime: the core pops), `RECAL_DIAL` (each dial turn), `RECAL_TRANSFORM` (the slap / a direct transform),
  `RECAL_REVERT` (N turns back at once on the recalibrated watch -- no five beeps -- and the timeout's beeps end on it). Chosen by
  `Omnitrix.cue(p, prototype, recal)`.

## Building an Alien Force alien (the parallel builds)

Read `docs/OMNITRIX_FRAMEWORK_0171.md` ("Adding an alien") and one shipped kit for style. Each alien already has STUB classes and
registration lines -- **only edit your own files**:

| Alien | Server kit (replace the stub) | Client kit (replace the stub) |
|---|---|---|
| Swampfire | `omnitrix/alien/swampfire/SwampfireKit` | `client/omnitrix/alien/SwampfireClient` |
| Echo Echo | `alien/echoecho/EchoEchoKit` | `EchoEchoClient` |
| Humungousaur | `alien/humungousaur/HumungousaurKit` | `HumungousaurClient` |
| Jetray | `alien/jetray/JetrayKit` | `JetrayClient` |
| Big Chill | `alien/bigchill/BigChillKit` | `BigChillClient` |
| Chromastone | `alien/chromastone/ChromastoneKit` | `ChromastoneClient` |
| Brainstorm | `alien/brainstorm/BrainstormKit` | `BrainstormClient` |
| Spidermonkey | `alien/spidermonkey/SpidermonkeyKit` | `SpidermonkeyClient` |
| Goop | `alien/goop/GoopKit` | `GoopClient` |
| Alien X | `alien/alienx/AlienXKit` | `AlienXClient` |

* Keep the stub's static `register()` (it must call `Aliens.register(new XKit())`), `clearSessionState()` and the `ID` constant; the
  client's static `register()` must call `AlienClients.register(Aliens.X.id(), new XClient())`. Extra classes go in your own
  package / `client/omnitrix/alien/<alien>/`. An entity of your own (Echo Echo's clones): register its `EntityType` from your kit's
  `register()` and its renderer from your client's `register()`.
* **Do not edit** shared files: `Aliens`, `Omnitrix`, `OmnitrixMod`, `OmnitrixClient`, `OmnitrixEntities`, `en_us.json`, `sounds.json`,
  either `fabric.mod.json`. If a framework hook is truly missing, add the smallest backwards-compatible default method and say so in
  your report.
* **Lang**: write `scratchpad/recal_v0172/lang/<alien id>.json` (a flat JSON object). The merge script puts it into `en_us.json`:
  every move's `projecthero.omnitrix.<id>.<move>` and `.desc`, `projecthero.guide.omnitrix.alien.<id>` (one line), and the info page's
  `projecthero.omnitrix.info.<id>.role`, `.passive.1..n`, `.weakness.1..n`, plus any message / HUD keys of yours. The alien's display
  name (`projecthero.omnitrix.alien.<id>`) is done already.
* **Sounds**: `OmnitrixSfx` cues of vanilla samples in your kit class (no new sound files) -- quiet, layered, like the other kits.
* **Body** (basic, user: "we can revamp later"): the player model with a generated 64x64 WIDE skin and a few `AlienParts.box` extras
  from an `<alien>_extras.png` sheet (Stinkfly's / Grey Matter's first versions are the pattern; `scratchpad/pnglib.js` writes PNGs).
  Generator: `scratchpad/recal_v0172/<alien>/gen_<alien>.js`, output `textures/entity/omnitrix/<alien>.png` (+ extras). Show-accurate
  colours. Simple third-person poses (`QuirkKeyframes`) and first-person clips per move.
* **Moves**: R G X Z V C with Shift variants (X = movement, V = utility / mode, R G Z C attacks; **never H or N**). Every move gets a
  third-person pose. **Rendered effects over particle spam** (the user likes show-accurate moves with rendered VFX: beams, cones,
  shells, rings drawn with `RenderType` quads / boxes; a few particles as accents only). Damage on the mod's scale: quick moves 5-8,
  normal 12-17, big 17-20, ultimates (one per alien, usually C or Shift+C) 40-70. Bosses: `AlienStatus.isBoss` / `QuirkThreat`, as the
  other kits. Static per-player maps cleared in `onRevert` and `clearSessionState`.
* The framework gives every alien hidden Resistance I, the alien's name and the HUD (key boxes, amber Shift cooldown bars) from
  `moves()` -- nothing to do for those. Armour comes from `AlienStats.armor`.
* **Tests** in your group's class (`OmnitrixRecalAGameTests` etc., already registered): a recalibrated mock (`Omnitrix.grant(p, true)`,
  then `Omnitrix.select` / `transform`), the body stats, each alien's signature passive, a move's damage + cooldown, and turning back
  leaves nothing behind. Run only yours: `./gradlew runGameTest -PtestFilter=OmnitrixRecalA` (mock players are not ticked -- pump
  `Omnitrix.tick` yourself, as `OmnitrixAliens2GameTests` does).

## The ten (design sketch -- basic versions)

| Alien | Body | Signature |
|---|---|---|
| Swampfire | 32 HP, x1.0, armour 6 | methane + fire: fire immune, regrows (regen; burns back faster), seeds / vines; weak: cold |
| Echo Echo | 16 HP, size 0.6, x1.15 | sonic screams, **duplicates** (V: up to 3 clones that scream with him); weak: water / losing his clones |
| Humungousaur | 44 HP, size 1.5, x0.85, armour 10 | brute; **size-shift** (V: grow 1.5 -> 2.2 -> 3.0, more damage / armour / reach, slower) |
| Jetray | 24 HP, x1.2 | fast **flight** (air and water), neuroshock eye / tail blasts; dive strikes |
| Big Chill | 28 HP, armour 4 | **freezing breath**, moth-wing flight, **intangible** phase (Ghostfreak's `phasing` hook), cloak; cold immune |
| Chromastone | 34 HP, armour 10 | **absorbs** energy / fire / magic / projectile damage into a Charge meter and **redirects** it (prism beams) |
| Brainstorm | 22 HP, size 0.9, armour 6 | **electrokinesis** (bolts, chain lightning, storm), force-field bubble, genius analysis |
| Spidermonkey | 22 HP, x1.25, size 0.85 | agile, **webs** (shots, nets, swing / zip), wall crawl, four-armed flurries |
| Goop | 26 HP, armour 2 | **shapeshifting acid slime**: half physical damage, squeezes through gaps, acid; the **anti-gravity projector** (a hit can knock it off: he is stuck until he gets back to it) |
| Alien X | 60 HP, armour 14 | reality-warping, every move **debated first** (1.5 s frozen while Serena and Bellicus argue on the action bar, then 75% it happens, else a short cooldown); a 5 s opening debate frozen on transform; huge moves (ultimates 50-70) |

## Parallel build (v0.17.2)

Four worktree builds, merged into master: A (Swampfire, Echo Echo, Humungousaur), B (Jetray, Big Chill, Chromastone), C (Brainstorm,
Spidermonkey, Goop), D (Alien X). The watch's recalibrated look, dial icons, recipes, lang merge, guide and the recalibration tests are
done on master.
