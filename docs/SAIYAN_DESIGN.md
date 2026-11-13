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

`client/saiyan/SaiyanTailRenderer` (drawn from `SaiyanLayer`, so never in first person). 7 tapering segments from the lower back
(body space (0, 10.6, 2.05) px), each 2.15 px, half-width 1.05 -> 0.62 px with a fuller tip; cel-shaded core + translucent fur
shell (x1.3, tip x1.5), alternating bands. Hangs back ~38 deg below horizontal and curls up toward the tip; sways (two slow
sines, phase-shifted per segment); streams back when running (less droop and curl), leans against strafing, lifts when falling.
Crouching eases it into a wrap round the waist (an ellipse 5 x 3 px outside the body). Colour eases over ~1 s:
Base brown `#6B4423`, Super Saiyan gold `#FFCF3A`, Ultra Instinct silver `#CDD6E2`, Kaioken red `#C0352C`; transformed tails glow.

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
