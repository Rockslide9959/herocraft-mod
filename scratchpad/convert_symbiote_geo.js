/*
 * Convert the user-supplied Symbiote (Normal Host) Blockbench Bedrock-geometry export
 * (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Symbiote/model.geo.json) into the mod's
 * shared superhero-armour rig, the same way convert_spiderman_geo.js did for the Spider-Man bundle.
 *
 * Source shape: a plain vanilla-layout skin rig -- a "armor" root plus
 * Head / Body / "Right Arm" / "Left Arm" / "Right Leg" / "Left Leg", each with a base cube and a
 * second-layer "shell" overlay cube, already using single-origin box UVs (not per-face), on the
 * standard 64x64 skin net. Unlike the Spider-Man bundle, each limb bone also carries a baked
 * "rest pose" rotation (arms/legs held slightly out) from Blockbench's "natural" skin pose -- these
 * are zeroed, exactly like every other set's conversion in this mod, so GeckoLib's own per-frame
 * player-pose copy (walk/crouch/swim/...) is the only rotation ever applied to the bone.
 *
 * Conversion:
 *   Head -> armorHead   Body -> armorBody
 *   Right Arm -> armorRightArm   Left Arm -> armorLeftArm
 *   Right Leg -> armorRightLeg   Left Leg -> armorLeftLeg
 *   "armor" root and all `parent` links are dropped -- flat rig, same as spider_man.geo.json.
 *
 * The source has no boot bones and the leg cubes already reach y=0 (the floor), so two EMPTY
 * armorRightBoot / armorLeftBoot placeholder bones are added (same reasoning as Green Lantern's
 * boot bones -- see docs/GREENLANTERN_REFERENCE.md) rather than synthesising boot geometry.
 *
 * Uniform inflate += 0.30 on every cube (the proven z-fighting clearance value used by every other
 * converted set) so the shell sits proud of the vanilla player box instead of coinciding with it.
 *
 * Run from the repo root:  node scratchpad/convert_symbiote_geo.js
 */
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2]
	|| 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Symbiote/model.geo.json';
const OUT = process.argv[3]
	|| path.join(__dirname, '..', 'src/main/resources/assets/projecthero/geo/symbiote_host.geo.json');
const INFLATE = 0.30;

const MAP = {
	'Head': 'armorHead',
	'Body': 'armorBody',
	'Right Arm': 'armorRightArm',
	'Left Arm': 'armorLeftArm',
	'Right Leg': 'armorRightLeg',
	'Left Leg': 'armorLeftLeg',
};

const src = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const geo = src['minecraft:geometry'][0];
const outBones = [];

for (const bone of geo.bones) {
	if (bone.name === 'armor') continue; // root wrapper, dropped -- flat rig like spider_man.geo.json
	const mapped = MAP[bone.name];
	if (!mapped) {
		console.warn('skipping unmapped bone', bone.name);
		continue;
	}

	// Baked "natural" skin-pose rotation is intentionally dropped (zeroed) -- see file header.
	const cubes = (bone.cubes || []).map((cube, i) => ({
		name: bone.name.toLowerCase().replace(/\s+/g, '_') + (i === 0 ? '_base' : '_shell'),
		origin: cube.origin.slice(),
		size: cube.size.slice(),
		uv: cube.uv.slice(),
		inflate: Math.round(((cube.inflate || 0) + INFLATE) * 1000) / 1000,
	}));

	outBones.push({ name: mapped, pivot: bone.pivot.slice(), cubes });
}

/* Empty placeholder boot bones -- source has none and the leg cubes already reach the floor. */
const rightLeg = outBones.find((b) => b.name === 'armorRightLeg');
const leftLeg = outBones.find((b) => b.name === 'armorLeftLeg');
outBones.push({ name: 'armorRightBoot', pivot: rightLeg.pivot.slice() });
outBones.push({ name: 'armorLeftBoot', pivot: leftLeg.pivot.slice() });

const out = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.symbiote_host',
			texture_width: geo.description.texture_width || 64,
			texture_height: geo.description.texture_height || 64,
			visible_bounds_width: 3,
			visible_bounds_height: 3.5,
			visible_bounds_offset: [0, 1.25, 0],
		},
		bones: outBones,
	}],
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, '\t') + '\n');
console.log('wrote', OUT, '-', outBones.length, 'bones');
for (const b of outBones) {
	console.log(' ', b.name, 'cubes:', (b.cubes || []).map((c) => 'uv' + JSON.stringify(c.uv) + '/i' + c.inflate).join(' ') || '(empty)');
}
