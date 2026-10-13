// v0.16.1 raid undead: shared model helpers (the unit models now live in raid_undead_units3.js -- the Powered Zombie Boss).
'use strict';
const path = require('path');
const L = require('./lib_concept_models.js');
const G = require('./gen_raid_undead_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, vnorm, vadd, vsub, vsc, vlen } = L;
const { rig, BASE, CLAW, FIST, OPEN, SIDES, sxr, bevel, vgrad } = G;
const ap = require('./lib_geckogen_anim.js').addPose;

// under-wrap a list of cubes in a cloth shell (inflated by `k` px)
function shell(M, bone, c, k, mat, opts = {}) {
	return M.box(bone, c.from[0] - k, c.from[1] - (opts.kb == null ? k : opts.kb), c.from[2] - k, c.to[0] + k, c.to[1] + (opts.kt == null ? k : opts.kt), c.to[2] + k, mat, Object.assign({ rot: c.rot, pivot: c.pivot }, opts));
}
const cubesOf = (M, bone, paint) => M.B[bone].cubes.filter(c => !paint || c.paint === paint);

// emissive helper: a glowing body with darker mottling; `edge` brightens the rims
function glowBody(p, f, info, deep, bright, seed = 0, edge = true) {
	const n = fbm(p[0] * 0.9 + seed, p[1] * 0.9, p[2] * 0.9, 3);
	let col = mix(deep, bright, 0.25 + 0.75 * n);
	if (edge && info.ePx < 0.25) col = mix(col, [255, 255, 255], 0.35);
	if (f === 'down') col = mul(col, 0.8);
	return col;
}

module.exports = { shell, cubesOf, glowBody };

// --concept: the Powered Zombie Boss sheets (the raid's other mobs are player-rig skins now: gen_raid_player_skins.js)
function main() {
	const sheet = (id, M, poses, title, sub, actions, opts) => {
		M.layout(); M.paint(G.shadeFor(M));
		G.conceptSheet(M, id, title, sub, poses.stand, actions, opts);
		console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}`);
	};
	require('./raid_undead_units3.js').more(sheet);
}
module.exports.main = main;
