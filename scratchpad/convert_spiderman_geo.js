/*
 * Convert a Spider-Man GeckoLib *entity* bundle model (currently
 * C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Spider-Man/spiderman_bnd_geckolib_bundle)
 * into the mod's shared superhero-armour rig. The user keeps supplying new variant bundles; just
 * repoint SRC and re-run.
 *
 * The bundle model is a plain vanilla-layout player rig: a `root` plus `body / head / right_arm /
 * left_arm / right_leg / left_leg`, each with a base cube and (usually) a second-layer "suit detail"
 * overlay cube. Its cubes carry per-face UVs, but they follow the exact standard 64x64 skin
 * net, so every cube converts losslessly to a single box-UV origin: for a box of depth d the `up`
 * face sits at [boxU + d, boxV], hence boxUV = [up.uv[0] - d, up.uv[1]].
 *
 * Conversion:
 *   head -> armorHead   body -> armorBody
 *   right_arm -> armorRightArm   left_arm -> armorLeftArm
 *   right_leg -> armorRightLeg   left_leg -> armorLeftLeg
 *   root and all `parent` links are dropped (GeoArmorRenderer binds each armour bone to its own
 *   vanilla model part, so the rig is flat).
 *
 * The bundle has no boot bones, but the armour set is 4-piece (FEET slot -> armorRightBoot /
 * armorLeftBoot in SuperheroArmorRenderer). We synthesise a short boot bone per leg from the bottom
 * 4px of that leg's texture region so the boots piece still shows something on its own.
 *
 * Uniform `inflate += INFLATE` on every body cube: the suit renders as a shell OVER the vanilla
 * player box (only the skin's 2nd overlay layer is hidden), so every surface must sit a little proud
 * of it or it z-fights. Same technique the previous convert script used; 0.30 is the proven value.
 *
 * Run from the repo root:  node scratchpad/convert_spiderman_geo.js
 *   optional args:          node scratchpad/convert_spiderman_geo.js <srcGeoJson> <outGeoJson>
 *
 * v0.9.10: the Symbiote suit is converted the same way -- a plain vanilla-layout black-suit bundle
 * (spiderman_black_geckolib_bundle) -> geo/spider_man_symbiote.geo.json. Same rig, same INFLATE, so it
 * drops straight onto the shared armour renderer just like every other Spider-Man look.
 */
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2]
	|| 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Spider-Man/spiderman_bnd_geckolib_bundle/bundle/spider_man_bnd.geo.json';
const OUT = process.argv[3]
	|| path.join(__dirname, '..', 'src/main/resources/assets/herocraft/geo/spider_man.geo.json');
const INFLATE = 0.30;

const MAP = {
	head: 'armorHead',
	body: 'armorBody',
	right_arm: 'armorRightArm',
	left_arm: 'armorLeftArm',
	right_leg: 'armorRightLeg',
	left_leg: 'armorLeftLeg',
};

/** Collapse a per-face UV cube (standard skin net) to a single box-UV origin. */
function boxUv(cube) {
	const d = cube.size[2];
	const up = cube.uv && cube.uv.up && cube.uv.up.uv;
	if (!up) throw new Error('cube ' + JSON.stringify(cube.origin) + ' has no up-face UV');
	return [up[0] - d, up[1]];
}

const src = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const geo = src['minecraft:geometry'][0];
const outBones = [];

for (const bone of geo.bones) {
	if (bone.name === 'root') continue;
	const mapped = MAP[bone.name];
	if (!mapped) {
		console.warn('skipping unmapped bone', bone.name);
		continue;
	}

	// Base cube: +INFLATE so the shell sits proud of the vanilla player box. Overlay/"shell" cubes:
	// pinned to a fixed 0.25 above the base (the vanilla 2nd-layer gap) rather than carrying the
	// source's own delta -- the bundle authors the hat layer at +0.5, which blows up to an oversized
	// bubble once INFLATE is added on top.
	const cubes = (bone.cubes || []).map((cube, i) => ({
		name: bone.name + (i === 0 ? '_base' : '_shell'),
		origin: cube.origin.slice(),
		size: cube.size.slice(),
		uv: boxUv(cube),
		inflate: i === 0
			? Math.round(((cube.inflate || 0) + INFLATE) * 1000) / 1000
			: Math.round((INFLATE + 0.25) * 1000) / 1000,
	}));

	outBones.push({ name: mapped, pivot: bone.pivot.slice(), cubes });
}

/* Synthesise boot bones from the bottom 4px of each leg. */
function boot(name, legBone) {
	const src = legBone.cubes;
	const base = src[0];
	const shell = src[1];
	const mk = (cube, extra) => {
		const bh = 4;
		const dropped = cube.size[1] - bh; // leg body rows above the boot
		return {
			origin: [cube.origin[0], cube.origin[1], cube.origin[2]],
			size: [cube.size[0], bh, cube.size[2]],
			// same box-UV column, V shifted down past the skipped upper-leg rows so the boot
			// samples the bottom of the leg's own texture region
			uv: [cube.uv[0], cube.uv[1] + dropped],
			inflate: Math.round((cube.inflate + extra) * 1000) / 1000,
		};
	};
	const cubes = [mk(base, 0.05)];
	if (shell) cubes.push(mk(shell, 0.05));
	return { name, pivot: legBone.pivot.slice(), cubes };
}

const rightLeg = outBones.find((b) => b.name === 'armorRightLeg');
const leftLeg = outBones.find((b) => b.name === 'armorLeftLeg');
outBones.push(boot('armorRightBoot', rightLeg));
outBones.push(boot('armorLeftBoot', leftLeg));

const identifier = 'geometry.' + path.basename(OUT).replace(/\.geo\.json$/, '');
const out = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier,
			texture_width: geo.description.texture_width || 64,
			texture_height: geo.description.texture_height || 64,
			visible_bounds_width: 3,
			visible_bounds_height: 4,
			visible_bounds_offset: [0, 16, 0],
		},
		bones: outBones,
	}],
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(out, null, '\t') + '\n');
console.log('wrote', OUT, '-', outBones.length, 'bones');
for (const b of outBones) {
	console.log(' ', b.name, 'cubes:', b.cubes.map((c) => 'uv' + JSON.stringify(c.uv) + '/i' + c.inflate).join(' '));
}
