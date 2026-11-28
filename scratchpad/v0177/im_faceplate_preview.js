// v0.17.7 round 2: offline 3D close-ups of the Iron Man skin suits' helmets, faceplate closed and lifted, next to the original
// PlanetMinecraft skin's head (base head + hat layer at vanilla's 0.5), drawn from the REAL geo + texture the game loads. A small
// software renderer (orthographic, per-texel quads, painter's order, back faces culled like closed solids), so it needs no client.
//
//   node scratchpad/v0177/im_faceplate_preview.js
//
// Writes docs/screenshots/v0177/ironman_faceplates/<set>_faceplate.png (one row: original front | original 3/4 | closed front |
// closed 3/4 | lifted front | lifted 3/4 | lifted side) and faceplates_contact_sheet.png (every suit, one row each). Lifted = the
// thin plate swung RAISED_DEG (90) about IronManFaceplateLook.HINGE with the helmet's front faces left out, over a plain wearer's
// head (skin-coloured face, dark eyes) so the opening reads.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.resolve(__dirname, '..', '..');
const RES = path.join(ROOT, 'src/main/resources/assets/projecthero');
const OUT = path.join(ROOT, 'docs/screenshots/v0177/ironman_faceplates');
fs.mkdirSync(OUT, { recursive: true });

const SUITS = [
	{ set: 'mark_1', src: 'mark1' }, { set: 'mark_iii', src: 'mark3' }, { set: 'mark_4', src: 'mark4_hydro' },
	{ set: 'mark_2', src: 'mark6_renaissance' }, { set: 'mark_6', src: 'mark7_centurion' }, { set: 'mark_1_v2', geo: 'mark_1', src: null },
];
const HINGE = [0, 32.1, -4.92], RAISED = 90;
const S = 20; // screen pixels per model pixel
const CELL = 400;
const BG = [44, 48, 56];

const tex = f => { const s = PNG.read(f); return { w: s.W, h: s.H, get(x, y) { x = Math.max(0, Math.min(s.W - 1, x)); y = Math.max(0, Math.min(s.H - 1, y)); const i = (y * s.W + x) * 4; return [s.px[i], s.px[i + 1], s.px[i + 2], s.px[i + 3]]; } }; };

// faces in a frame where X = the wearer's LEFT (viewer's right seen from the front), Y up, Z toward the back (front at zlo).
// Each: [corner, alongU, alongV, normal] in units of the box extent; texture regions follow the vanilla skin net.
function faceFrames(lo, hi) {
	const [x0, y0, z0] = lo, [x1, y1, z1] = hi;
	return {
		north: { c: [x0, y1, z0], u: [x1 - x0, 0, 0], v: [0, y0 - y1, 0], n: [0, 0, -1] },
		east: { c: [x0, y1, z1], u: [0, 0, z0 - z1], v: [0, y0 - y1, 0], n: [-1, 0, 0] }, // wearer's right (net u 0-7)
		west: { c: [x1, y1, z0], u: [0, 0, z1 - z0], v: [0, y0 - y1, 0], n: [1, 0, 0] },
		south: { c: [x1, y1, z1], u: [x0 - x1, 0, 0], v: [0, y0 - y1, 0], n: [0, 0, 1] },
		up: { c: [x0, y1, z1], u: [x1 - x0, 0, 0], v: [0, 0, z0 - z1], n: [0, 1, 0] },
		down: { c: [x0, y0, z0], u: [x1 - x0, 0, 0], v: [0, 0, z1 - z0], n: [0, -1, 0] },
	};
}
/** Per-texel quads of one cube (geo JSON form, per-face uv). Bedrock x is mirrored into the wearer-left frame. */
function cubeQuads(cube, t, opts = {}) {
	const inf = cube.inflate || 0;
	const [ox, oy, oz] = cube.origin, [sx, sy, sz] = cube.size;
	const lo = [-(ox + sx) - inf, oy - inf, oz - inf], hi = [-ox + inf, oy + sy + inf, oz + sz + inf];
	const F = faceFrames(lo, hi);
	const out = [];
	for (const [name, uv] of Object.entries(cube.uv)) {
		if (opts.skipNorth && name === 'north') continue;
		const f = F[name];
		const [u0, v0] = uv.uv, [du, dv] = uv.uv_size;
		const nu = Math.max(1, Math.round(Math.abs(du))), nv = Math.max(1, Math.round(Math.abs(dv)));
		for (let j = 0; j < nv; j++) for (let i = 0; i < nu; i++) {
			const col = t.get(Math.floor(u0 + (i + 0.5) * du / nu), Math.floor(v0 + (j + 0.5) * dv / nv));
			if (!col[3]) continue;
			const p = (a, b) => [0, 1, 2].map(k => f.c[k] + f.u[k] * a + f.v[k] * b);
			out.push({ pts: [p(i / nu, j / nv), p((i + 1) / nu, j / nv), p((i + 1) / nu, (j + 1) / nv), p(i / nu, (j + 1) / nv)], n: f.n, col });
		}
	}
	return out;
}
/** Rotate quads about the hinge line (x along the wearer's left-right) by deg, "out and up" (bottom forward then up). */
function hingeQuads(qs, deg) {
	const a = deg * Math.PI / 180, c = Math.cos(a), s = Math.sin(a);
	const r = ([x, y, z]) => { const dy = y - HINGE[1], dz = z - HINGE[2]; return [x, HINGE[1] + dy * c - dz * s, HINGE[2] + dy * s + dz * c]; };
	const rn = ([x, y, z]) => [x, y * c - z * s, y * s + z * c];
	return qs.map(q => ({ ...q, pts: q.pts.map(r), n: rn(q.n) }));
}
function render(quads, yawDeg, pitchDeg) {
	const ya = yawDeg * Math.PI / 180, pa = pitchDeg * Math.PI / 180;
	const view = ([x, y, z]) => {
		const x1 = x * Math.cos(ya) - z * Math.sin(ya), z1 = x * Math.sin(ya) + z * Math.cos(ya);
		return [x1, y * Math.cos(pa) + z1 * Math.sin(pa), z1 * Math.cos(pa) - y * Math.sin(pa)];
	};
	const img = Buffer.alloc(CELL * CELL * 3);
	for (let i = 0; i < CELL * CELL; i++) BG.forEach((v, k) => { img[i * 3 + k] = v; });
	const cx = CELL / 2, cy = CELL / 2, midY = 28.5;
	const vq = [];
	for (const q of quads) {
		const n = view(q.n);
		if (n[2] > 1e-6) continue; // back face
		const pts = q.pts.map(p => view([p[0], p[1] - midY, p[2]]));
		const depth = pts.reduce((s, p) => s + p[2], 0) / 4;
		const shade = 0.62 + 0.38 * Math.max(0, -n[2] * 0.7 + n[1] * 0.3 + 0.2);
		vq.push({ pts: pts.map(p => [cx + p[0] * S, cy - p[1] * S]), depth, col: q.col, shade: Math.min(1, shade) });
	}
	vq.sort((a, b) => b.depth - a.depth);
	for (const q of vq) fill(img, q);
	return img;
}
function fill(img, q) {
	const xs = q.pts.map(p => p[0]), ys = q.pts.map(p => p[1]);
	const x0 = Math.max(0, Math.floor(Math.min(...xs))), x1 = Math.min(CELL - 1, Math.ceil(Math.max(...xs)));
	const y0 = Math.max(0, Math.floor(Math.min(...ys))), y1 = Math.min(CELL - 1, Math.ceil(Math.max(...ys)));
	const P = q.pts;
	let area = 0;
	for (let i = 0; i < 4; i++) { const a = P[i], b = P[(i + 1) % 4]; area += a[0] * b[1] - b[0] * a[1]; }
	if (Math.abs(area) < 1e-6) return;
	const sgn = Math.sign(area);
	const a = q.col[3] / 255;
	for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
		let inside = true;
		for (let i = 0; i < 4 && inside; i++) {
			const p = P[i], r = P[(i + 1) % 4];
			if (((r[0] - p[0]) * (y + 0.5 - p[1]) - (r[1] - p[1]) * (x + 0.5 - p[0])) * sgn < -1e-9) inside = false;
		}
		if (!inside) continue;
		const k = (y * CELL + x) * 3;
		for (let c = 0; c < 3; c++) img[k + c] = Math.round(img[k + c] * (1 - a) + q.col[c] * q.shade * a);
	}
}
/** The plain wearer's head under the lifted faceplate: skin-coloured face, dark eyes, brown hair on top / back. */
function wearerHead() {
	const SKIN = [214, 166, 130, 255], HAIR = [90, 60, 34, 255], EYE = [40, 50, 90, 255], WHITE = [235, 235, 235, 255];
	const t = { get(x, y) {
		if (y < 8) return HAIR;
		const fx = x % 8, fy = y - 8, face = Math.floor(x / 8);
		if (face === 1) { if (fy < 2) return HAIR; if (fy === 4 && (fx === 1 || fx === 5)) return WHITE; if (fy === 4 && (fx === 2 || fx === 6)) return EYE; return SKIN; }
		if (face === 3) return HAIR;
		return fy < 3 ? HAIR : SKIN;
	} };
	return cubeQuads({ origin: [-4, 24, -4], size: [8, 8, 8], uv: headUv(0) }, t);
}
const headUv = u => ({
	north: { uv: [u + 8, 8], uv_size: [8, 8] }, east: { uv: [u, 8], uv_size: [8, 8] }, west: { uv: [u + 16, 8], uv_size: [8, 8] },
	south: { uv: [u + 24, 8], uv_size: [8, 8] }, up: { uv: [u + 8, 0], uv_size: [8, 8] }, down: { uv: [u + 16, 0], uv_size: [8, 8] },
});
/** The original skin's head as vanilla draws it: head at 0, hat at 0.5. */
function originalHead(t) {
	return cubeQuads({ origin: [-4, 24, -4], size: [8, 8, 8], uv: headUv(0) }, t)
		.concat(cubeQuads({ origin: [-4, 24, -4], size: [8, 8, 8], inflate: 0.5, uv: headUv(32) }, t));
}
function suitHead(geo, t, lifted) {
	const bones = geo['minecraft:geometry'][0].bones;
	const helmet = bones.find(b => b.name === 'helmet'), plate = bones.find(b => b.name === 'faceplate');
	let q = [];
	for (const c of helmet.cubes) q = q.concat(cubeQuads(c, t, { skipNorth: lifted }));
	if (lifted) {
		q = wearerHead().concat(q);
		let p = [];
		for (const c of plate.cubes) p = p.concat(cubeQuads(c, t));
		q = q.concat(hingeQuads(p, RAISED));
	}
	return q;
}
function sheet(cells, cols, file) {
	const rows = Math.ceil(cells.length / cols), W = cols * CELL, H = rows * CELL;
	const px = Buffer.alloc(W * H * 4);
	cells.forEach((img, i) => {
		const ox = (i % cols) * CELL, oy = Math.floor(i / cols) * CELL;
		for (let y = 0; y < CELL; y++) for (let x = 0; x < CELL; x++) {
			const s = (y * CELL + x) * 3, d = ((oy + y) * W + ox + x) * 4;
			const edge = x === 0 || y === 0;
			px[d] = edge ? 20 : img[s]; px[d + 1] = edge ? 20 : img[s + 1]; px[d + 2] = edge ? 20 : img[s + 2]; px[d + 3] = 255;
		}
	});
	PNG.write(file, W, H, px);
	console.log('wrote', path.relative(ROOT, file));
}

const all = [];
const blank = Buffer.alloc(CELL * CELL * 3, 0);
for (let i = 0; i < CELL * CELL; i++) BG.forEach((v, k) => { blank[i * 3 + k] = v; });
for (const s of SUITS) {
	const geo = JSON.parse(fs.readFileSync(path.join(RES, 'geo', (s.geo || s.set) + '.geo.json'), 'utf8'));
	const t = tex(path.join(RES, 'textures/armor', s.set + '.png'));
	const row = [];
	if (s.src) {
		const o = tex(path.join(__dirname, 'im_skins', s.src + '_original.png'));
		row.push(render(originalHead(o), 0, 8), render(originalHead(o), 35, 12));
	} else {
		row.push(render(originalHead(t), 0, 8), render(originalHead(t), 35, 12)); // Mk II: its own texture as a plain skin
	}
	row.push(render(suitHead(geo, t, false), 0, 8), render(suitHead(geo, t, false), 35, 12));
	row.push(render(suitHead(geo, t, true), 0, 8), render(suitHead(geo, t, true), 35, 12), render(suitHead(geo, t, true), 80, 6));
	sheet(row, row.length, path.join(OUT, s.set + '_faceplate.png'));
	all.push(...row);
}
sheet(all, 7, path.join(OUT, 'faceplates_contact_sheet.png'));
