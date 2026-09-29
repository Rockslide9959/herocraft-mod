// v0.14.3 docs: CurseForge description bullets + the Green Lantern / Moon Knight / Hulk / Max Steel reference docs.
const fs = require('fs');
function edit(F, pairs) {
	let s = fs.readFileSync(F, 'utf8');
	const crlf = s.includes('\r\n');
	s = s.replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		if (!s.includes(a)) throw new Error(F + ': ' + a.slice(0, 80));
		s = s.replace(a, () => b);
	}
	if (crlf) s = s.replace(/\n/g, '\r\n');
	fs.writeFileSync(F, s);
}
function append(F, text) {
	let s = fs.readFileSync(F, 'utf8');
	const eol = s.includes('\r\n') ? '\r\n' : '\n';
	fs.writeFileSync(F, s.replace(/\s*$/, '') + eol + eol + text.replace(/\n/g, eol) + eol);
}

edit('docs/CURSEFORGE_DESCRIPTION.md', [
[`- A 10,000-point **Ring Charge** fuels beams, a Construct Fist, a War Hammer, **directional flight** (fly wherever you
  look), shields, a Protective Dome and
  **14 hard-light constructs** of glowing green light -- walls, walkable ramps, a spinning turret, a travelling
  battering ram, a blade or drill on your fist and more.
- Your suit sweeps on one row of light at a time, and the ring glows on your right hand.
- Hold **X** to recite the Oath for 22 seconds of doubled power.`,
`- A 10,000-point **Ring Charge** fuels beams, **directional flight** (fly wherever you look), shields, a Protective Dome
  and everything the ring can imagine -- and every attack is a real shape of hard light: a **giant fist** that flies,
  a **war hammer** swung down from the sky, **homing missiles**, an **Emerald Gatling** on your fist and a **Giant
  Hand** (**H**) that crushes and hurls whatever you aim at.
- **19 hard-light constructs** on a new construct wheel (Attack / Defence / Mobility / Utility) -- walls, walkable
  ramps, a spinning turret, a **Buzzsaw** that ricochets between enemies, an **Anvil Drop**, a **Chain Snare**, a
  **Launch Pad** and an **Emerald Warrior** that fights at your side. **N** dismisses them all.
- Every move has its own animation, the HUD is a framed ring-charge panel, and the ring on your hand glows and flares
  as you use it. Hold **Sneak + N** for 5 seconds to take the ring off (and give it to someone else).
- Hold **X** to recite the Oath for 30 seconds of doubled power.`],
[`- **Banner can't be killed:** a fatal hit just unleashes the Hulk. Lose control and he goes on a rampage, and you'll
  need the breathing minigame to calm him down.`,
`- **Banner can't be killed:** a fatal hit just unleashes the Hulk. Lose control and he goes on a rampage -- hitting
  friend and foe alike, squadmates included -- and you'll need the breathing minigame to calm him down.`],
[`with a flowing **hooded cape**. In it you **regenerate** fast, hit **+7** harder and take **20% less** damage.`,
 `with a flowing **hooded cape**. In it you **regenerate** fast, hit **+7** harder, run **30% faster**, jump over
  **two blocks** and take **20% less** damage.`],
[`  and **Sneak+X** a 60-block Grappling Line that reels mobs in`, `  that goes wherever you aim, and **Sneak+X** a 100-block Grappling Line that reels mobs in`],
[`- **The cape:** jump and hold Sneak to **glide** flat out on cape wings`, `- **The cape:** jump and hold Sneak to **glide** fast and flat out on cape wings`],
]);

append('docs/GREENLANTERN_REFERENCE.md', `## v0.14.3 -- revamp: models, animations, new attacks & constructs, wheel, HUD, ring

Explicit user request: "revamp green lantern, give him animations, add a few more useful constructs, make construct wheel
look better, give him more attacks and construct attacks, make N remove constructs instead of Shift+C, make shift+hold n
able to remove ring (5 seconds), make his HUD look better, give his moves models if needed, make the green lantern power
stronger, change ring model again and make it look cooler".

### Keys (new layout)

| Key | Tap | Shift |
|---|---|---|
| R | Ring Bolt (streak of light) | Continuous Beam (solid beam, free hand grips the wrist) |
| G | Construct Fist -- a flying fist model | War Hammer Slam -- a hammer model swung down 3 blocks ahead |
| X | hold: Oath (fist before the face; ring punched skyward on completion) | **hold: Emerald Gatling** |
| Z | Directional Shield | Protective Dome |
| V | Suit Up/Down | Ring Scan |
| C | deploy construct / hold: wheel | **Missile Barrage** (was: dismiss all) |
| H | **Giant Hand** -- grab / hurl (Shift+H = power wheel) | -- |
| N | **dismiss all constructs** (first press sets a tethered creature down) | **hold 5 s: take the ring off** |

### Architecture

- \`entity/HardLightConstructEntity\` (+ \`GreenLanternEntityTypes.HARD_LIGHT_CONSTRUCT\`): ONE entity type, the shape in
  synced data (\`BOLT, BEAM, FIST, HAMMER, MISSILE, BUZZSAW, ANVIL, HAND, CHAINS, LAUNCH_PAD, WARRIOR\`) plus scale / end
  point / target id / life / action tick. Never saved. All gameplay runs server-side in
  \`GreenLanternConstructAttacks#tickEntity\`; the client renderer \`client/greenlantern/HardLightConstructRenderer\` builds
  every model from \`HardLightDraw\` boxes (translucent full-bright green core + pale rim shell, over \`TurboDraw\`).
- \`GreenLanternConstructAttacks\`: the fist / hammer / streak visuals for the old moves, Gatling, Missile Barrage, Giant
  Hand, and the five new wheel constructs. Lasting ones (pads, warrior, chains) are tracked per owner in \`LIVE\` and end
  with N, death, logout, dimension change (\`onCleanup\` from \`GreenLantern#clearTransient\`).
- \`data/GreenLanternFx\` (+ \`ModAttachments.GREEN_LANTERN_FX\`, synced to all, never persisted, written only through
  \`GreenLanternVisuals\`): last move animation + start tick, channel bits (beam / gatling / hand / ring removal), ring
  removal start. Read by \`client/greenlantern/GreenLanternPose\` (keyframe poses, hooked in \`HumanoidModelMixin\`), the
  hand renderer (Gatling barrels on the fist, ring halo flare) and the HUD.
- New constructs are appended to \`ConstructType\` (ordinals of saved selections unchanged): \`BUZZSAW, ANVIL_DROP,
  CHAIN_SNARE\` (\`Kind.ATTACK\`), \`LAUNCH_PAD, EMERALD_WARRIOR\` (\`Kind.SUMMON\`); \`ConstructType#category()\` groups the
  wheel; \`#descriptionKey()\` = \`<name>.desc\`. \`GreenLanternConstructs#deploy\` hands those kinds to
  \`GreenLanternConstructAttacks#deploy\` first.
- N / H / Shift+N go through \`GreenLanternActionPayload\` (\`GIANT_HAND, CLEAR_CONSTRUCTS, RING_REMOVE_START/STOP\`);
  client dispatch sits in \`ProjectHeroModClient\`'s H and N chains just before the mutation-utility fallbacks.
  \`GreenLantern#removeRing\` = revoke + the Power Ring item back to the inventory (right-click re-bonds).
- Launch Pad fall safety (owner + squad) is a one-shot \`FALL_SAFE\` checked at the top of \`GreenLanternDamage\`.

### Balance (stronger)

Resistance I -> II; suit melee +8 -> +10; bolt 13 -> 18; beam 4 per 10 t -> 5 per 6 t; fist 16 -> 24 (flies 20 blocks,
half-damage splash, 1.5 s); hammer 17 -> 26 (4 s); Oath 22 -> 30 s; shield HP 80 -> 140; dome 250 -> 400; wall 160 ->
260; cage 75 -> 120; turret 4 -> 7; ram 12 -> 20; energy blade 9 -> 13. New: Gatling 5 x 10/s; missiles 6 x 12 (2.5
radius); Giant Hand 6 per 0.5 s squeeze + 22 throw; Buzzsaw 14 x 5 bounces; Anvil 34 / 18; Chains 5 s pin; Warrior 12
per swing for 30 s. All numbers in \`GreenLanternConfig\`'s "v0.14.3" block.

### Look

- Worn ring (\`GreenLanternHandRenderer#ring\`): band round the ring finger + dark bezel + white-hot gem + a breathing
  halo that flares while any move / channel is running. Still finger-sized (the v0.14.1 "not a bracelet" rule).
- Power Ring item: a real 3D model (\`models/item/power_ring.json\`, octagonal band + bezel + gem, texture
  \`item/power_ring_model.png\`), generated by \`scratchpad/gen_ring_model_v0143.js\`.
- Construct wheel (\`GreenLanternConstructWheelScreen\`): a donut of wedges drawn as GUI quads, category colour band per
  wedge, hovered wedge lifts out, selected wedge has a bright edge, icon atlas \`textures/gui/green_lantern/constructs.png\`
  (cell = ordinal, cell 31 = emblem, \`scratchpad/gen_greenlantern_v0143.js\`), centre = icon / name / group / cost,
  description under the ring. Sizes itself to the window.
- HUD (\`GreenLanternHud\`): framed panel -- emblem + title + status tags (Oath / Flying / Boost / Suited, dropped from the
  end if they would hit the title), a segmented Gauge for Ring Charge with the reserve marked, 8 key boxes (R G X Z V C
  H N) with top-down cooldown shade and lit outlines, the selected construct with icon + cost. Stacked above: ring
  removal Gauge, shield/dome meter, construct cooldowns with icons + Hairline bars. Hidden while the wheel is open.

Verified in-client with \`scratchpad/GreenLanternDebugHarness.v0143.java.txt\` + \`HarnessCameraMixin.gl.java.txt\` (env
\`PROJECTHERO_GL_DEBUG\`; both removed from the source tree afterwards). Gametests: \`V0143GameTests\`. Gotcha: GameTest
mock players are placed at world spawn -- move them into the test area (\`helper.absoluteVec\`) before testing anything
that is a ticking entity, and never use a NoAI mob to test a push (NoAI mobs ignore velocity).
`);

append('docs/MOONKNIGHT_REFERENCE.md', `## v0.14.3

- Suit passives (\`MoonKnightAlters#reconcile\`): +30% movement speed (\`SUIT_SPEED_BONUS\`, ADD_MULTIPLIED_BASE), jump
  strength +0.16 (0.58 total, ~2.2 blocks -- clears two), safe fall +1 block.
- Grappling Line / Grapple Kick range 60 -> 100 (\`GRAPPLE_RANGE\`); pull 1.3 -> 1.8 blocks/tick and max pull 70 -> 100
  ticks so a full-length line still arrives.
- Cape Glide speed 0.55 -> 0.85 (\`GLIDE_SPEED\`).
- X Dash follows the full 3D aim (\`MoonKnightDash#dash\` / \`#push\`) -- it used to be flattened to the horizontal.
`);

append('docs/HULK_REFERENCE.md', `## v0.14.3

A rampaging Hulk hits his squadmates. \`Squads#shields(dealer, victim)\` is the one squad-protection check (same squad AND
the dealer is not \`HulkControl.rampaging\`); used by the friendly-fire veto, \`AbilityHelpers#hurtLands\` and
\`HulkCombat#ally\` (so the rampage AI also targets them). PvP / \`abilityPvpDamage\` still apply. The squadmate still
can't hurt the Hulk back.
`);

append('docs/MAXSTEEL_REFERENCE.md', `## v0.14.3

HUD: the mode row shows only the Turbo Mode he is in ("TURBO MODE  Strength", blue, underlined) instead of all five with
the active one lit (\`MaxSteelHud\`, lang \`hud.projecthero.max_steel.mode_label\`).
`);
console.log('docs ok');
