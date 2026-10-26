// Loads a GeckoLib geo.json + its PNG back into a lib_concept_models Model, so the sheet renderer can show the
// in-game model as it is (used to compare the old Kwamis / Sass against the new ones).
'use strict';
const fs = require('fs');
const zlib = require('zlib');
const L = require('../viperion/lib_concept_models.js');

function decodePng(buf) {
	let o = 8, w, h, bd, ct; const idat = [];
	while (o < buf.length) {
		const len = buf.readUInt32BE(o), ty = buf.toString('ascii', o + 4, o + 8), d = buf.subarray(o + 8, o + 8 + len);
		if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); bd = d[8]; ct = d[9]; }
		else if (ty === 'IDAT') idat.push(d);
		o += 12 + len;
	}
	if (bd !== 8 || (ct !== 6 && ct !== 2)) throw new Error('unsupported png ' + bd + '/' + ct);
	const bpp = ct === 6 ? 4 : 3, st = w * bpp;
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const out = Buffer.alloc(w * h * bpp);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)];
		for (let x = 0; x < st; x++) {
			const v = raw[y * (st + 1) + 1 + x];
			const a = x >= bpp ? out[y * st + x - bpp] : 0, b = y ? out[(y - 1) * st + x] : 0, c = (x >= bpp && y) ? out[(y - 1) * st + x - bpp] : 0;
			let p;
			if (f === 0) p = 0; else if (f === 1) p = a; else if (f === 2) p = b; else if (f === 3) p = (a + b) >> 1;
			else { const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c); p = pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
			out[y * st + x] = (v + p) & 255;
		}
	}
	if (bpp === 4) return { w, h, px: out };
	const px = Buffer.alloc(w * h * 4);
	for (let i = 0; i < w * h; i++) { px[i * 4] = out[i * 3]; px[i * 4 + 1] = out[i * 3 + 1]; px[i * 4 + 2] = out[i * 3 + 2]; px[i * 4 + 3] = 255; }
	return { w, h, px };
}

function loadGeo(geoFile, pngFile) {
	const g = JSON.parse(fs.readFileSync(geoFile, 'utf8'))['minecraft:geometry'][0];
	const tex = decodePng(fs.readFileSync(pngFile));
	const M = new L.Model(g.description.identifier, { TW: tex.w });
	M.TW = tex.w; M.TH = tex.h; M.tex = tex.px; M.glow = Buffer.alloc(tex.px.length);
	for (const b of g.bones) {
		const B = M.bone(b.name, b.parent || null, b.pivot || [0, 0, 0], b.rotation || [0, 0, 0]);
		for (const c of (b.cubes || [])) {
			const to = c.origin.map((v, i) => v + c.size[i]);
			const cc = { from: c.origin.slice(), to, mat: 'x', rot: c.rotation || null, pivot: c.pivot || null, bone: b.name, uv: {} };
			for (const f of L.FACES) { const u = c.uv[f]; cc.uv[f] = { u: u.uv[0], v: u.uv[1], w: Math.abs(u.uv_size[0]) || 1, h: Math.abs(u.uv_size[1]) || 1 }; }
			B.cubes.push(cc);
		}
	}
	const hpx = M.bounds(M.world({})).hi[1];
	M.k = 0.56 / 16; M.heightBlocks = hpx * M.k;
	return M;
}
module.exports = { decodePng, loadGeo };
