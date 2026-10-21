// v0.16.7 Iron Man Era II stage 1 (part A) -- ART install for the three existing suits that become Era II Portable suits.
// Repeatable; run from the repo root (or a worktree):  node scratchpad/install_ironman_era2_stage1.js
//
//   e2_mk6 -> mark_2   (Mk VI Suitcase)       e2_mk7 -> mark_6 (Mk VII Silver Centurion)   e2_mk8 -> mark_vii (Mk VIII Space)
//
// 1. Skins + glowmasks (user-approved concepts in the main checkout's untracked scratchpad/concepts_ironman_v2/skins/; a
//    tracked copy lives in scratchpad/ironman_era2_skins/) become textures/armor/<id>.png / <id>_glowmask.png, with the hat
//    front (40,8) baked over the face front (8,8) exactly like scratchpad/solid_faceplates_v0167.js -- the helmet cubes have
//    no north face, the closed face IS the faceplate bone, which samples the face front.
// 2. Geos: an exact copy of geo/mark_iii.geo.json (the LAYERED thin rig: base +0.08 / helmet +0.1, outer +0.4, hat +0.32,
//    split thigh/boot cubes, 0.3 px non-inflated faceplate) with its identifier renamed. Same bone names as before, so the
//    gantry per-texel build, bracelet wrap-on, retract, faceplate hinge, battle damage and glow layer all keep working.
//    (Step 0 first fixes the Era I geos' outer leg seam, so the copy inherits the fix.)
'use strict';
const fs = require('fs');
const path = require('path');
const P = require('./pngkit.js');

const here = __dirname.split(path.sep).join('/');
const wt = here.indexOf('/.claude/worktrees/');
const MAIN = wt >= 0 ? here.slice(0, wt) : path.join(__dirname, '..');
const MAIN_SKINS = path.join(MAIN, 'scratchpad/concepts_ironman_v2/skins');
const LOCAL_SKINS = path.join(__dirname, 'ironman_era2_skins');
const RES = path.join(__dirname, '../src/main/resources/assets/projecthero');

const SUITS = [
	{ skin: 'e2_mk6', id: 'mark_2' },
	{ skin: 'e2_mk7', id: 'mark_6' },
	{ skin: 'e2_mk8', id: 'mark_vii' },
];

// 0. Leg seam fix on the LAYERED rig (layer_era1_v0167.js grew the outer thigh down and the outer boot up by 0.18 px each,
//    so the two outer shells overlapped in a 0.36 px band with coplanar side faces -- z-fighting at the knee-boot line, and
//    IronManRound2GameTests.ironManGeoIsJustTheSkinModel failed). Both outer shells now meet exactly at the base split.
const r4 = v => Math.round(v * 10000) / 10000;
for (const id of ['mark_1', 'mark_iii', 'mark_4', 'mark_v']) {
	const f = path.join(RES, 'geo', id + '.geo.json');
	const g = JSON.parse(fs.readFileSync(f, 'utf8'));
	const bones = g['minecraft:geometry'][0].bones;
	let changed = false;
	for (const side of ['right', 'left']) {
		const thigh = bones.find(b => b.name === side + '_thigh').cubes;
		const boot = bones.find(b => b.name === side + '_boot').cubes;
		const split = thigh[0].origin[1];
		const top = r4(thigh[1].origin[1] + thigh[1].size[1]);
		if (Math.abs(thigh[1].origin[1] - split) < 1e-4 && Math.abs(boot[1].origin[1] + boot[1].size[1] - split) < 1e-4) continue;
		thigh[1].origin[1] = split;
		thigh[1].size[1] = r4(top - split);
		boot[1].size[1] = r4(split - boot[1].origin[1]);
		changed = true;
	}
	if (changed) {
		fs.writeFileSync(f, JSON.stringify(g, null, '\t') + '\n');
		console.log('geo/' + id + '.geo.json: outer thigh + boot meet at the base split (no overlap)');
	}
}

fs.mkdirSync(LOCAL_SKINS, { recursive: true });
for (const s of SUITS) {
	for (const suffix of ['', '_glowmask']) {
		const name = s.skin + suffix + '.png';
		const main = path.join(MAIN_SKINS, name), local = path.join(LOCAL_SKINS, name);
		if (fs.existsSync(main) && path.resolve(main) !== path.resolve(local)) fs.copyFileSync(main, local);
		if (!fs.existsSync(local)) throw new Error('missing skin ' + name);
		const im = P.decode(local);
		const W = im.w, S = W / 64;
		for (let y = 0; y < 8 * S; y++) for (let x = 0; x < 8 * S; x++) {
			const src = ((8 * S + y) * W + 40 * S + x) * 4, dst = ((8 * S + y) * W + 8 * S + x) * 4;
			const a = im.px[src + 3] / 255;
			if (a <= 0) continue;
			for (let k = 0; k < 3; k++) im.px[dst + k] = Math.round(im.px[src + k] * a + im.px[dst + k] * (1 - a));
			im.px[dst + 3] = Math.max(im.px[dst + 3], im.px[src + 3]);
		}
		fs.writeFileSync(path.join(RES, 'textures/armor', s.id + suffix + '.png'), P.encode(W, im.h, im.px));
		console.log('installed textures/armor/' + s.id + suffix + '.png <- ' + name + ' (hat front baked into the face)');
	}
	const g = JSON.parse(fs.readFileSync(path.join(RES, 'geo/mark_iii.geo.json'), 'utf8'));
	g['minecraft:geometry'][0].description.identifier = 'geometry.' + s.id;
	fs.writeFileSync(path.join(RES, 'geo', s.id + '.geo.json'), JSON.stringify(g, null, '\t') + '\n');
	console.log('wrote geo/' + s.id + '.geo.json (mark_iii LAYERED rig)');
}
