# Project Hero

A Minecraft superhero mod: 70+ feature packages covering powers, suits, bosses, raids and world events.
Solo hobby project. The user plays and tests in a CurseForge modpack, not in this checkout.

- **Fabric 1.21.1** (loader 0.19.3, Loom 1.17), Java 21, GeckoLib 4.9.2, JEI
- Mod id `projecthero`, package `com.projecthero.mod` (renamed from HeroCraft in v0.9.22, so old notes may say `com.herocraft`)
- Version lives in `gradle.properties` -> `version=`
- CurseForge project 1684169, a GitHub repo, and a Discord `#changelog` webhook

## Layout

| Path | What's there |
|---|---|
| `src/main/java/com/projecthero/mod/<feature>/` | One package per power, boss or system (`hulk`, `omnitrix`, `miraculous`, `galactus`, ...) |
| `src/client/java/...` | Client code (renderers, HUD, screens, input), mirrored as `client/<feature>` |
| `src/gametest/java/...` | Fabric gametests. A new test class must also be listed in `src/gametest/resources/fabric.mod.json` |
| `src/main/resources/assets/projecthero/lang/en_us.json` | All player-facing text |
| `hero/guide/` (`HeroPackGuide`, `GuidebookHint`) | The in-game Guidebook. Update it whenever a power or item is added or changed |
| `event/` | Shared raid/world-event framework (`EventInstance`, `EventManager`, `EventBossBar`, `EventTypes`) |
| `diagnostics/ServerStateReset` | Clears static state when the server stops (see rules below) |
| `docs/*_REFERENCE.md`, `docs/*_DESIGN.md` | Design notes per feature. Read the matching one before changing a feature |
| `docs/IDEAS_BACKLOG.md` | Ideas the user liked but hasn't scheduled |
| `docs/screenshots/v<version>/` | Screenshots and clips from testing |
| `scratchpad/` | Harnesses (`*Harness*.java.txt`), asset scripts (`*.js`), test-client tools. Not shipped |

## Commands

```bash
./gradlew compileJava compileClientJava compileGametestJava   # fast compile check
./gradlew build                                               # jar; also runs copyToModpack + copyToDrive
./gradlew copyToModpack                                       # jar -> "herocraft test" CurseForge instance, then Google Drive
./gradlew copyToDrive                                         # jar -> G:\My Drive\mods only
./gradlew runClient                                           # test client
./gradlew runGameTest -PtestFilter=<ClassName>                # only when debugging a failing CI test
```

- `copyToModpack` refuses if the test modpack is running (swapping the jar mid-game crashed the user's game).
  Never pass `-PforceModpackCopy` on your own. Say "close Minecraft and I'll copy it" and copy when it's closed.
- `copyToDrive` runs after `copyToModpack` and replaces older `projecthero` jars in the Drive folder.
  If Google Drive for Desktop isn't running, it skips with a warning. Tell the user.
- Gradle runs with a 6 GB heap. Don't run two Gradle builds in the same checkout at once (that once produced a broken jar).

## Testing

- **Don't run the full gametest suite locally.** Compile, push to master, and let `build.yml` run it on CI
  (it picks tests per push, sharded). Check in-game at the same time. Run a local filtered gametest only to debug a CI failure.
- **Test clients:** before every `runClient`, run `node scratchpad/low_graphics_options.js run/options.txt`
  (lowest graphics, master volume 5%). One client at a time where possible, small windows. Put the same line
  in any subagent brief that launches a client.
- **Show the user what you tested:** screenshots for anything visual (send them with SendUserFile, save under
  `docs/screenshots/v<version>/`). For animations send an **MP4 plus a frame sheet, never a GIF** (GIFs don't show in the user's chat).
- Time-based gametests: assert on step sizes, not elapsed totals (CI ticks mock players at a different rate).
- Gametest gotchas: `EMPTY_STRUCTURE` is 8x8 and walled by barriers. Other tests can leave the world on Hard,
  so assert on ratios for mob hits.

## Workflow rules

- **Bump `version=` on every change meant for the user's game.** The next version is the one after the newest
  `v*` tag (`git tag --list "v*" --sort=-v:refname | head`). If a prompt names a version that's already released,
  it's stale boilerplate: use the next unreleased one and say so in one line.
- **Copy to the test modpack as each feature finishes**, and tell the user, instead of batching to the end.
- **Merge finished branches automatically** (fix conflicts, compile, push master, copy to the modpack). Don't ask
  "merge?" or "copy?". **Do ask before tagging a release.**
- **Keep going** on multi-part builds. Don't stop to ask "continue?". Decide implementation details yourself and
  list the choices in the summary. Ask only about gameplay rules that are hard to undo once players have them.
- Delete agent worktrees after merging (they pile up in OneDrive and slow everything down).
- Many files are **CRLF**. Edits and JS regexes must handle `\r\n`.
- `en_us.json` is touched by almost every branch: merge it key by key, never overwrite the whole file.

## Design rules (the user's)

- **Keys:** H = power selector only. N only when the user assigns it. Never invent H/N abilities or add H/N HUD boxes.
  **X = movement, V = utility/mode**, the other keys are attacks.
- **Tooltips and UI text must wrap.** Keep the `TooltipWrap` guard and check the width of any new text.
- **Regular mobs** use player models with detailed skins. Custom GeckoLib bodies are for bosses only.
- **Every boss** uses the shared threat table and switches targets.
- **Max Steel stays blue**, including the Blast and Cannon effects.
- The **Repulsor** item never gets durability (it would break the armour recipes).
- HUD bar styles are called **Slab, Meter, Gauge and Hairline**. Use those names.
- New beams call `BeamClash.report(...)` every live tick, and their renderer checks `BeamClashClient.hides(entity)`.
- Any new **static collection holding worlds, entities or block states** must be cleared in
  `diagnostics/ServerStateReset`, or it leaks whole worlds across world switches.
- Borrowed skins and models (PlanetMinecraft and the like): note the source and check the licence before a public release.

## Releasing

Only when the user says to release.

1. Make sure master CI (`build.yml`) is green for the commit. The release workflow needs it.
2. `git tag -a v<version> -m "<changelog>"` then `git push origin master --tags`.
   **The tag message is the changelog** for CurseForge, GitHub Releases and Discord. There's no CHANGELOG file.
3. Changelog style: detailed and player-facing, grouped by feature, covering everything in the update. No internal
   test notes. A hotfix right after a release gets the **combined** changelog. Discord splitting is automatic
   (`release.yml` breaks long changelogs into several messages), so write it as long as it needs to be.
4. **Check that all three got it:** `gh run list --workflow release`, confirm the build, publish and notify-discord
   steps passed, and `gh release list` shows the version. If anything failed (flaky test, CurseForge 500, Discord),
   fix it and re-release with a higher version and the combined changelog. Don't just report the failure.
5. The mod page text is `docs/CURSEFORGE_DESCRIPTION.md` / `docs/curseforge_description.html` (the Discord invite lives there).
