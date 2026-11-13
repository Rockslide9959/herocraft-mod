# Saiyan design (v0.17.3)

The core numbers live in `saiyan/SaiyanConfig.java` (its header table is the reference for Ki, Rage, Super Saiyan, Kaioken,
Zenkai, the stronger-foe bonus and flight); the moves in `saiyan/kit/SaiyanKitConfig.java`. This doc covers what was added
after the revamp: the hero landing, the tail, the hair being stashed, and the secret Ultra Instinct.

## Keys (unchanged rules)

H = the transformation key the power already had (selector-only rule: no new H/N abilities were added). X = movement, V = utility/mode.
Ultra Instinct uses the **same H press** as Super Saiyan: Base -> (H at full Rage) Super Saiyan -> (H again, once unlocked) Ultra Instinct.

## Hero landing (tester note)

`HeroLandingStyle.SAIYAN` (id 40, FIST pose, CRATER element, 18-tick hold), registered on `Saiyan::isFlying`. The shared system
gives the shockwave, crack, dust, thud and shake; `saiyan/SaiyanLanding` adds a Ki flash + fast shockwave + vertical ring,
scorch/crack crater, block chunks, a dust wall and gold Ki motes. Base: white core, gold edge. Super Saiyan: full gold, x1.6
size, a second slow golden wave, a rising aura column, sparks, the transform-burst sound, a gold vignette and a 25% heavier shake.

## Tail

User (round 2): "make it smooth and round like Spider-Monkey's tail and give it the same reaction rules".
`client/saiyan/SaiyanTailRenderer` (drawn from `SaiyanLayer`, so never in first person; ticked for every Saiyan in view from
`SaiyanVisualsClient`). Physics = Spidermonkey's spring chain, now shared as `client/render/SpringTail`: 7 world-space links
(Spidermonkey's lengths x0.9, about 17 px) from the lower back (body bone (0, 10.6, 2.0) px), springing toward the shared
`MONKEY_IDLE` / `MONKEY_WALK` rest shapes (low out behind, curling up into a hook; trailing and swaying with the walk cycle when
moving, slow idle sway standing), lagging and swinging on turns, loose and streaming when falling fast, gliding or flying
(Spidermonkey's web-swing rule), kept 3.1 px behind the back and legs and out of blocks. Crouching leans the root with the body
(Spidermonkey's rule; the old waist wrap is gone). Mesh = `client/render/TubeMesh`: a Catmull-Rom curve through the links,
4 samples per link, 10-sided rings with real normals, rounded tip; radius 1.1 px at the root -> 0.6 px -> a fuller 0.82 px tip,
plus a soft fixed fur mottle in the vertex colours and a lighter tip tuft (no translucent shell: it read as a grey outline). Colour eases over ~1 s:
Base brown `#6B4423`, Super Saiyan gold `#FFCF3A`, Ultra Instinct silver `#CDD6E2`, Kaioken red `#C0352C`; transformed tails glow.
Footage: `docs/screenshots/v0173/saiyan_tail_v2/`.

## Hair (stashed)

User: "remove the Super Saiyan hair from the mod for now, keep it stashed". Files and restore steps: `scratchpad/saiyan_hair_stash/README.md`.

## Ultra Instinct (secret)

Code: `saiyan/SaiyanUltraInstinct.java`. Saved in the synced `SaiyanState.abilityReadyAt` map (`ui_unlocked`, `ui_since`,
`ui_until`, `ui_lock`) because the state's record codec is at its 16-field limit.

**Unlock (once, permanent).** In a fight with a *stronger foe* (a boss, or 1.5x his max health; the existing 10 s window), be at
or below 25% max health (10 of 40) for 10 s in total and take at least 5 hits while there, without dying. Healing above 40%,
the stronger-foe window lapsing, or dying resets the count. On unlock: a silver flash and two chat lines (the second tells him
to press H as a Super Saiyan). The Guidebook only carries the hint "Beyond anger": a Saiyan pushed to the edge by a far stronger
enemy who keeps standing can find a calm beyond anger.

**Entering.** H while a Super Saiyan, unlocked, not exhausted, with 50+ Ki. Plays the transformation burst again in silver.

**The form (30 s max).**
- Silver aura (palette `#8C9CB8 / #E8F0FF / #FFFFFF`), silver tail, silver Ki colour, HUD title "Ultra Instinct" with its clock.
- +40% movement speed (and +40% flight speed) on top of Super Saiyan's; +25% damage on top of Super Saiyan's +35%.
- **Auto-dodge**: 60% of melee hits and 75% of projectiles are sidestepped, at most one every 6 ticks. He steps 1.6 blocks
  (fallback 0.96) perpendicular to the attack line onto the free side; a silver afterimage (the kit's GHOST effect) stays where
  he stood; a dodged projectile is removed. Explosions, fire, falls and invulnerability-bypassing damage can't be dodged.
- **Counter**: any hit he lands within 2 s of a dodge deals +50% more (on top of everything else).
- **Cost**: 3 Ki a second and no Ki regen. Ends at 0 Ki or after 30 s; Super Saiyan's own 3-minute timer is held while it runs.

**After.** Super Saiyan ends with it (its normal 60 s lockout and Rage reset), Slowness II + Weakness I for 10 s, and no Ultra
Instinct for 3 minutes. Death ends it quietly and still starts the 3-minute lockout.

**Op command (testing; not in the Guidebook):** `/projecthero power tune saiyan ultra` unlocks it, transforms and enters it.

Tests: `SaiyanUltraInstinctGameTests` (landing style, unlock count steps / reset, entry rules + Ki drain step, the dodge
chances via a forced roll, the counter, undodgeable damage, the end).
