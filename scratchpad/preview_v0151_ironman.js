// v0.15.1: offline software preview of an Iron Man armour geo (GeckoLib bedrock json) + its 64x64 texture.
// Draws front | back | right side | left side, orthographic, z-buffered, point-splatted texels, light per-face shading.
// Usage: node scratchpad/preview_v0151_ironman.js <geo.json> <texture.png> <out.png> [scale=10] [glowmask.png]
// Conventions (skin semantics): front = north (z min), the wearer's right = x min; a box's first UV strip ("west") is the
// x-min side read back -> front, "east" the x-max side read front -> back, "south" the back read x-max -> x-min.
const fs = require('fs');
const path = require('path');
const pk = require(path.join(__dirname, 'pngkit.js'));

function render(geoPath, texPath, outPath, S = 10, glowPath = null, skipBone = null) {
	const geo = JSON.parse(fs.readFileSync(geoPath, 'utf8'))['minecraft:geometry'][0];
	const tex = pk.decode(texPath);
	const glow = glowPath ? pk.decode(glowPath) : null;
	const TW = geo.description.texture_width || 64, TH = geo.description.texture_height || 64;
	const quads = []; // {p0, du, dv (3d vectors), u0, v0, uw, vh, shade}
	for (const bone of geo.bones) {
		if (skipBone && skipBone(bone.name)) continue;
		for (const c of bone.cubes || []) {
			const inf = c.inflate || 0;
			const [x0, y0, z0] = c.origin.map(v => v - inf);
			const [x1, y1, z1] = c.origin.map((v, i) => v + c.size[i] + inf);
			const [sx, sy, sz] = c.size;
			let faces;
			if (Array.isArray(c.uv)) {
				const [u, v] = c.uv;
				faces = {
					west: { uv: [u, v + sz], uv_size: [sz, sy] }, north: { uv: [u + sz, v + sz], uv_size: [sx, sy] },
					east: { uv: [u + sz + sx, v + sz], uv_size: [sz, sy] }, south: { uv: [u + 2 * sz + sx, v + sz], uv_size: [sx, sy] },
					up: { uv: [u + sz, v], uv_size: [sx, sz] }, down: { uv: [u + sz + sx, v], uv_size: [sx, sz] },
				};
			} else faces = c.uv || {};
			const W = { // origin corner (texel u0,v0), direction along u, direction along v
				north: [[x0, y1, z0], [x1 - x0, 0, 0], [0, y0 - y1, 0], 1.0],
				south: [[x1, y1, z1], [x0 - x1, 0, 0], [0, y0 - y1, 0], 0.8],
				west: [[x0, y1, z1], [0, 0, z0 - z1], [0, y0 - y1, 0], 0.85],
				east: [[x1, y1, z0], [0, 0, z1 - z0], [0, y0 - y1, 0], 0.85],
				up: [[x0, y1, z1], [x1 - x0, 0, 0], [0, 0, z0 - z1], 1.1],
				down: [[x0, y0, z0], [x1 - x0, 0, 0], [0, 0, z1 - z0], 0.6],
			};
			for (const [name, f] of Object.entries(faces)) {
				if (!W[name] || !f || !f.uv) continue;
				quads.push({ p0: W[name][0], du: W[name][1], dv: W[name][2], shade: W[name][3], u0: f.uv[0], v0: f.uv[1], uw: f.uv_size[0], vh: f.uv_size[1], bone: bone.name });
			}
		}
	}
	// views: screen x/y and depth from a 3d point
	const views = [
		{ name: 'front', sx: p => p[0], d: p => p[2] },
		{ name: 'back', sx: p => -p[0], d: p => -p[2] },
		{ name: 'right', sx: p => -p[2], d: p => p[0] },
		{ name: 'left', sx: p => p[2], d: p => -p[0] },
	];
	const VW = 24 * S, VH = 36 * S, PAD = S;
	const OW = views.length * (VW + PAD) + PAD, OH = VH + 2 * PAD;
	const px = Buffer.alloc(OW * OH * 4);
	for (let y = 0; y < OH; y++) for (let x = 0; x < OW; x++) {
		const i = (y * OW + x) * 4; const ch = ((x >> 4) + (y >> 4)) & 1 ? 58 : 46;
		px[i] = ch; px[i + 1] = ch; px[i + 2] = ch + 6; px[i + 3] = 255;
	}
	views.forEach((view, vi) => {
		const ox = PAD + vi * (VW + PAD) + VW / 2, oy = PAD + VH - 2 * S; // y = -2 at the bottom margin
		const zb = new Float32Array(VW * VH).fill(1e9);
		for (const q of quads) {
			const lenU = Math.hypot(...q.du), lenV = Math.hypot(...q.dv);
			const nu = Math.max(1, Math.ceil(lenU * S * 2)), nv = Math.max(1, Math.ceil(lenV * S * 2));
			for (let j = 0; j < nv; j++) for (let k = 0; k < nu; k++) {
				const fu = (k + 0.5) / nu, fv = (j + 0.5) / nv;
				const tu = Math.floor(q.u0 + q.uw * fu - (q.uw < 0 ? 1e-6 : 0)), tv = Math.floor(q.v0 + q.vh * fv - (q.vh < 0 ? 1e-6 : 0));
				const tx = Math.floor(tu * tex.w / TW), ty = Math.floor(tv * tex.h / TH);
				if (tx < 0 || ty < 0 || tx >= tex.w || ty >= tex.h) continue;
				const ti = (ty * tex.w + tx) * 4;
				if (tex.px[ti + 3] < 8) continue;
				const p = [q.p0[0] + q.du[0] * fu + q.dv[0] * fv, q.p0[1] + q.du[1] * fu + q.dv[1] * fv, q.p0[2] + q.du[2] * fu + q.dv[2] * fv];
				const X = Math.floor(view.sx(p) * S + VW / 2), Y = Math.floor(VH - 2 * S - p[1] * S);
				if (X < 0 || Y < 0 || X >= VW || Y >= VH) continue;
				const d = view.d(p);
				if (d >= zb[Y * VW + X] - 1e-4) continue;
				zb[Y * VW + X] = d;
				const o = ((PAD + Y) * OW + (ox - VW / 2 + X)) * 4;
				const g = glow && glow.px[ti + 3] > 0;
				const sh = g ? 1.0 : q.shade;
				for (let c = 0; c < 3; c++) px[o + c] = Math.min(255, Math.round(tex.px[ti + c] * sh));
			}
		}
	});
	fs.mkdirSync(path.dirname(outPath), { recursive: true });
	fs.writeFileSync(outPath, pk.encode(OW, OH, px));
	return quads.length;
}

module.exports = { render };
if (require.main === module) {
	const [, , g, t, o, s, gl] = process.argv;
	console.log(o, render(g, t, o, Number(s || 10), gl || null), 'quads');
}
