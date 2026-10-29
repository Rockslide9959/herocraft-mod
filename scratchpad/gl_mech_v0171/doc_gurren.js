// v0.17.1 Gurren rework: appends the mech rework section to docs/GREENLANTERN_REFERENCE.md (CRLF kept). Run once.
'use strict';
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', '..', 'docs', 'GREENLANTERN_REFERENCE.md');
const text = `
## v0.17.1: the Hard-Light Mech, Gurren-style rework

User: "make the green lantern mech look cooler please, make it slightly taller and a bit thinner please, make it look like gurren
lagann". An original hard-light take on the drill-mech style, built in \`scratchpad/gl_mech_v0171/gen_gl_mech.js\` (the generator
stays the source of truth; \`anims.js\` holds the clips, \`preview.js\` the offline sheets):

- **Proportions**: ~7.5 blocks tall (was ~6.7): longer thighs and shins (hip pivot 53 px, knee 37), a narrow waist (+/-7 px),
  slimmer arms (arm pivot 88 px at x 26.5, fist 41..51.5 px). Hitbox 3.0 x 6.95 (was 3.2 x 6.5; to the helmet crown -- the horn and spike tips stand above it), seat 4.22 (was 3.66),
  third-person camera pulled back 7.4. A prouder \`STANCE\` (torso 1, head -3, feet planted wider) feeds every clip and combo pose.
- **The chest is a face**: a wide dark mouth with rising grin corners, an upper lip with seven fangs and two big canines, a hinged
  lower jaw (new bone \`jaw\`, child of \`torso\`) with five fangs -- it drops open in the \`beam\` clip and the Chest Lantern Beam
  now comes out of the mouth (\`HardLightMechEntity#chest\` and the renderer's beam origin, y 4.78); angry slanted eyes with slit
  pupils in recessed sockets under heavy frowning brows; the Lantern emblem is the forehead gem round the cockpit glass (and the
  belt buckle, and on each pauldron).
- **The shades**: new bone \`shades\` (child of \`torso\`), a bright glassy V-crest from the brow out and up over the shoulders, with
  a bright green rim and a diagonal sheen (material \`shades\`).
- **Spikes and drills**: pauldrons with a big up-and-out spike, an outward spike and a small back spike (the missile pods sit
  behind them, ports at 6.36 / 6.50 blocks); two blades on each forearm guard; spikes on the shin fins; ear fins on the small
  helmeted head; spiral-grooved drill cones (material \`drill\`: a two-start helix running continuously along the stepped cone) on
  the knees, the elbows and as the head's horn; pointed toes.
- **Build-up**: \`HardLightMechRenderer.WINDOWS\` adds \`jaw\` (0.40..0.56, with the torso) and \`shades\` (0.58..0.72: the face forms
  first, then the shades light up across it); both are hidden from the pilot in first person (\`COCKPIT_BONES\`). The dissolve
  order includes the shades.
- **Offsets** moved with the body: \`MechComboRender\` / \`MechMoveRender\` \`FIST_X\`, \`FIST_Y\`, \`WRIST_Y\`; the punch / rocket-fist /
  cannon / drill / dash / thruster / missile-pod / shield points in \`GreenLanternMech\`, \`GreenLanternMechCombos\`,
  \`GreenLanternMechClient\` and \`HardLightMechRenderer\`.
- **No white outlines** (user: "maybe remove all the white outlines for the green lantern mech"): the texture has no near-white
  left -- edge lines, lights, emblems, fangs, eyes, shades and drill grooves are all saturated greens (and their additive glow
  texels dimmer, so they do not burn to white). The mech's code-drawn hard light (katana, Giga Drill, mace, ball, cannon, beam,
  shield, shockwave, the white-hot forming cells of the build) goes green the same way: \`HardLightMechRenderer#render\` sets
  \`HardLightDraw.mechGreen\`, which makes \`tint(EDGE)\` a bright green and \`tint(HOT)\` / the glow cores a brief green-white;
  \`MechWeaponDraw.WHITE\` is a green-white. Mech only -- every other construct keeps its near-white edge lines.

Screenshots: \`docs/screenshots/v0171/gl_gurren_*.png\` (\`gl_gurren_before_after.png\`: the old mech on top, the new one below).
`;
fs.appendFileSync(f, text.replace(/\r?\n/g, '\r\n'));
console.log('appended to', f);
