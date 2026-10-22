// v0.16.7 (user) -- Titan Shifting revamp, VISUALS: writes every Titan Shifter asset.
//   geo/<model>.geo.json, textures/entity/<model>{,_hardened,_corpse,_glow}.png, animations/<model>.animation.json,
//   titan_texels/<model>.bin (one record per cube face: atlas rect, bone, layer, rest-pose corners) and
//   titan_texels/<model>_times.png (R = build time, G = dissolve time, B = layer * 100, A = 255 where a texel is used).
// Usage: node main.js <assetsDir> [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const R = require('./rig.js');
const PT = require('./paint.js');
const AN = require('./anims.js');
const { FACES, facePoint, fbm, vlen, vsub, encodePng } = L;

const ASSETS = process.argv[2];
const PREVIEW = process.argv[3] || null;
const ONLY = process.env.ONLY ? process.env.ONLY.split(',') : null;

const BONE_ORDER = ['root', 'body', 'head', 'jaw', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_leg', 'right_shin',
	'left_leg', 'left_shin', 'nape_flap_l', 'nape_flap_r', 'right_arm_stump', 'left_arm_stump', 'right_leg_stump', 'left_leg_stump'];

function paintAll(M) {
	M.layout();
	const TW = M.TW, TH = M.TH;
	M.tex = Buffer.alloc(TW * TH * 4); M.glow = Buffer.alloc(TW * TH * 4);
	const info = new Array(TW * TH);
	const shade = PT.shade(M);
	for (const r of M.rects) {
		const { c, f, w, h, u, v } = r;
		for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
			const s = (i + 0.5) / w, t = (j + 0.5) / h;
			const p = facePoint(c, f, s, t);
			const ePx = Math.min(Math.min(s, 1 - s) * r.fw, Math.min(t, 1 - t) * r.fh);
			const [col, a, g] = shade(c, f, s, t, p, { i, j, w, h, fw: r.fw, fh: r.fh, ePx, D: M.D });
			const idx = (v + j) * TW + u + i, o = idx * 4;
			M.tex[o] = L.clamp(Math.round(col[0])); M.tex[o + 1] = L.clamp(Math.round(col[1])); M.tex[o + 2] = L.clamp(Math.round(col[2])); M.tex[o + 3] = a == null ? 255 : a;
			if (g) { M.glow[o] = g[0]; M.glow[o + 1] = g[1]; M.glow[o + 2] = g[2]; M.glow[o + 3] = 255; }
			info[idx] = { c, f, p, layer: c.layer };
		}
	}
	return info;
}

/** Chain distance from the chest along the skeleton: the build grows from the spine outward. */
function chainDist(M, c, p) {
	const P = M.P, B = M.B;
	const C0 = [0, (P.chest[1] + P.chest[4]) / 2, 0];
	const piv = n => B[n].pivot;
	const d = (a, b) => vlen(vsub(a, b));
	const bone = c.bone;
	if (bone === 'body' || bone.startsWith('nape')) return d(p, C0);
	if (bone === 'head' || bone === 'jaw') return d(piv('head'), C0) + d(p, piv('head'));
	const side = bone.startsWith('right') ? 'right' : 'left';
	if (bone.includes('arm')) {
		const sh = piv(`${side}_arm`), el = piv(`${side}_forearm`);
		if (bone.endsWith('forearm')) return d(sh, C0) + d(el, sh) + d(p, el);
		return d(sh, C0) + d(p, sh);
	}
	const hip = piv(`${side}_leg`), kn = piv(`${side}_shin`);
	if (bone.endsWith('shin')) return d(hip, C0) + d(kn, hip) + d(p, kn);
	if (bone.endsWith('foot')) { const an = piv(`${side}_foot`); return d(hip, C0) + d(kn, hip) + d(an, kn) + d(p, an); }
	return d(hip, C0) + d(p, hip);
}

function rankNorm(list) { // list of {i, key} -> Map i -> 0..1 by rank
	list.sort((a, b) => a.key - b.key);
	const n = Math.max(1, list.length - 1);
	list.forEach((e, k) => { e.v = k / n; });
}

function times(M, info, corpseTex) {
	const TW = M.TW, TH = M.TH;
	const used = [];
	for (let i = 0; i < TW * TH; i++) {
		if (!info[i]) continue;
		if (M.tex[i * 4 + 3] === 0 && corpseTex[i * 4 + 3] === 0) continue;
		used.push(i);
	}
	let dmax = 0;
	const dist = new Float32Array(TW * TH);
	for (const i of used) { dist[i] = chainDist(M, info[i].c, info[i].p); dmax = Math.max(dmax, dist[i]); }
	const build = new Float32Array(TW * TH), diss = new Float32Array(TW * TH);
	const B_WIN = [[0.02, 0.36], [0.30, 0.66], [0.60, 0.95]];
	const D_WIN = [[0.0, 0.36], [0.31, 0.67], [0.63, 0.98]];
	for (let layer = 0; layer < 3; layer++) {
		const bl = [], dl = [];
		for (const i of used) {
			if (info[i].layer !== layer) continue;
			const p = info[i].p, dn = dist[i] / dmax;
			const n = fbm(p[0] * 0.45 + layer * 9, p[1] * 0.45, p[2] * 0.45, 3);
			const n2 = fbm(p[0] * 0.3 + 31, p[1] * 0.3 + layer * 5, p[2] * 0.3, 4);
			const hair = info[i].c.outer ? 0.12 : 0; // the skin's second layer forms last
			const bkey = layer === 0 ? dn + (n - 0.5) * 0.08 : layer === 1 ? dn * 0.8 + (n - 0.5) * 0.45 : dn * 0.55 + (n - 0.5) * 0.6 + hair;
			bl.push({ i, key: bkey });
			const top = 1 - p[1] / 32;
			dl.push({ i, key: 0.72 * n2 + 0.14 * top + 0.14 * (1 - dn) });
		}
		rankNorm(bl); rankNorm(dl);
		for (const e of bl) build[e.i] = B_WIN[layer][0] + (B_WIN[layer][1] - B_WIN[layer][0]) * e.v;
		const dw = D_WIN[2 - layer];
		for (const e of dl) diss[e.i] = dw[0] + (dw[1] - dw[0]) * e.v;
	}
	const png = Buffer.alloc(TW * TH * 4);
	for (const i of used) {
		png[i * 4] = Math.min(255, Math.floor(build[i] * 256));
		png[i * 4 + 1] = Math.min(255, Math.floor(diss[i] * 256));
		png[i * 4 + 2] = info[i].layer * 100;
		png[i * 4 + 3] = 255;
	}
	return { png, build, diss, used };
}

function bin(M) {
	const parts = [];
	const u8 = v => { const b = Buffer.alloc(1); b.writeUInt8(v); parts.push(b); };
	const u16 = v => { const b = Buffer.alloc(2); b.writeUInt16LE(v); parts.push(b); };
	const i32 = v => { const b = Buffer.alloc(4); b.writeInt32LE(v); parts.push(b); };
	const f32 = v => { const b = Buffer.alloc(4); b.writeFloatLE(v); parts.push(b); };
	const str = s => { u8(s.length); parts.push(Buffer.from(s, 'ascii')); };
	parts.push(Buffer.from('TTX1', 'ascii'));
	i32(1); i32(M.TW); i32(M.TH);
	const names = M.bones.map(b => b.name);
	i32(names.length); names.forEach(str);
	const P = M.P;
	const anchors = [['nape', 'body', M.nape]];
	for (const side of ['right', 'left']) {
		const ua = side === 'right' ? P.uarm : R.mirrorBox(P.uarm), th = side === 'right' ? P.thigh : R.mirrorBox(P.thigh);
		anchors.push([`${side}_arm_stump`, `${side}_arm_stump`, [R.cx(ua), ua[4] - 2.6, R.cz(ua)]]);
		anchors.push([`${side}_leg_stump`, `${side}_leg_stump`, [R.cx(th), th[4] - 2.8, R.cz(th)]]);
	}
	i32(anchors.length);
	for (const [n, b, p] of anchors) { str(n); u8(names.indexOf(b)); f32(p[0]); f32(p[1]); f32(p[2]); }
	i32(M.rects.length);
	for (const r of M.rects) {
		u16(r.u); u16(r.v); u16(r.w); u16(r.h);
		u8(names.indexOf(r.c.bone)); u8(r.c.layer); u8(r.c.corpseOnly ? 1 : 0);
		for (const [s, t] of [[0, 0], [1, 0], [0, 1]]) { const q = facePoint(r.c, r.f, s, t); f32(q[0]); f32(q[1]); f32(q[2]); }
	}
	return Buffer.concat(parts);
}

function main() {
	const out = {};
	for (const id of Object.keys(R.TYPES)) {
		if (ONLY && !ONLY.includes(id)) continue;
		const M = R.build(id);
		M.k = M.P.heightBlocks / 32; M.heightBlocks = M.P.heightBlocks;
		const info = paintAll(M);
		const layerOf = new Uint8Array(M.TW * M.TH).fill(255);
		info.forEach((e, i) => { if (e) layerOf[i] = e.layer; });
		const hard = PT.hardened(M, layerOf);
		const corpseTex = PT.corpse(M, info);
		const T = times(M, info, corpseTex);
		const anims = AN.animations(M);
		M.info = info; M.T = T; M.corpseTex = corpseTex; M.hardTex = hard; M.anims = anims;
		out[id] = M;
		if (ASSETS && ASSETS !== '-') {
			const W = (rel, buf) => { const f = path.join(ASSETS, rel); fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, buf); };
			W(`geo/${id}.geo.json`, JSON.stringify(M.geoJson(`geometry.${id}`), null, '\t') + '\n');
			W(`textures/entity/${id}.png`, encodePng(M.TW, M.TH, M.tex));
			W(`textures/entity/${id}_hardened.png`, encodePng(M.TW, M.TH, hard));
			W(`textures/entity/${id}_corpse.png`, encodePng(M.TW, M.TH, corpseTex));
			if (M.glow.some((v, i) => i % 4 === 3 && v > 0)) W(`textures/entity/${id}_glow.png`, encodePng(M.TW, M.TH, M.glow));
			W(`animations/${id}.animation.json`, JSON.stringify({ format_version: '1.8.0', animations: anims }, null, '\t') + '\n');
			W(`titan_texels/${id}.bin`, bin(M));
			W(`titan_texels/${id}_times.png`, encodePng(M.TW, M.TH, T.png));
		}
		console.log(`${id}: ${M.cubes().length} cubes, atlas ${M.TW}x${M.TH}, ${T.used.length} texels, ${Object.keys(anims).length} clips`);
	}
	if (PREVIEW) require('./preview.js').run(out, PREVIEW);
}

module.exports = { BONE_ORDER };
if (require.main === module) main();
