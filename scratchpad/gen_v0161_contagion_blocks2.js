// v0.16.1 Ultron Contagion -- corruption BLOCK art, second pass (replaces the "bland grid" first pass that used to live
// in gen_v0161_contagion_assets.js; that script no longer writes any of these files).
// usage (from the repo/worktree root):  node scratchpad/gen_v0161_contagion_blocks2.js [previewDir]
//
// Writes (under src/main/resources/assets/projecthero/):
//   corrupted_circuitry        4 variants + 4 `edge` variants (16x128 animated strips, frametime 3, interpolate),
//                              8 cube_all models, blockstate with edge=false/true weighted random lists
//                              (variants 0/1/2 weight 3, variant 3 weight 1, x 4 y-rotations), item model -> _0
//   corrupted_circuitry_stone  3 variants (weights 3/3/2 x 4 rotations), models, blockstate, item model -> _0
//   corrupted_circuitry_log    bark side (16x128 animated) + ring top (static), cube_column model, axis blockstate
//   corrupted_circuitry_leaves sparse wire mesh (16x64 animated, frametime 6), leaves model, blockstate
// and deletes the superseded single-texture files of corrupted_circuitry / corrupted_circuitry_stone.
//
// Vein rule: a red vein may only touch a block edge at pixel 4 or 11 (same two positions on all four sides, and
// {4,11} maps onto itself under every 90-degree rotation / mirror), so neighbouring variants line up in any rotation.
// The animation only changes vein/node brightness; the metal underneath is identical in every frame.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./v0161_asset_lib.js');
const { Canvas, mix, mul, hex, hash3, vnoise, fbm, clamp, writeJson, writePng, written, RES, encodePng } = L;
const NS = 'projecthero';
const PREVIEW = process.argv[2] || null;

// ================================================================ palette
const MET = ['0B0D12', '11141B', '161A22', '1C2029', '232731', '2B2F39', '333843', '3A3F4A', '4A505C', '5E6573'].map(hex);
const STONE = ['0E0F11', '151619', '1C1D21', '24252A', '2D2E34', '37383E', '424349', '4F5057', '5F6068'].map(hex);
const BARK = ['120E0D', '1C1615', '28201D', '342923', '40332B', '4C3D33', '5A493D', '6A5747'].map(hex);
const DIRT = ['1E1610', '2A1F17', '33251B', '3B2A1E', '46331F'].map(hex);
const V_DEEP = hex('3A0604'), V_DIM = hex('7A0A06'), V_CORE = hex('FF2010'), V_HOT = hex('FF6A50'), V_WHITE = hex('FFC2AE');
const GLOW = hex('5C0C08'), SOOT = hex('16130F');
const rampOf = pal => t => { t = clamp(t, 0, 1) * (pal.length - 1); const i = Math.min(pal.length - 2, Math.floor(t)); return mix(pal[i], pal[i + 1], t - i); };
const metal = rampOf(MET), stone = rampOf(STONE), bark = rampOf(BARK), dirt = rampOf(DIRT);
function vc(b) {
	b = clamp(b, 0, 1);
	let c = b < 0.5 ? mix(V_DEEP, V_DIM, b * 2) : mix(V_DIM, V_CORE, (b - 0.5) * 2);
	if (b > 0.86) c = mix(c, V_HOT, (b - 0.86) / 0.14 * 0.85);
	return c;
}

// ================================================================ helpers
const K = (x, y) => (y << 4) | x;
// pixel path through control points. four=true makes it 4-connected (a solid channel), else 8-connected (thin).
function line(pts, four, seed = 1) {
	const out = []; const push = (x, y) => { const l = out[out.length - 1]; if (!l || l[0] !== x || l[1] !== y) out.push([x, y]); };
	for (let k = 0; k < pts.length - 1; k++) {
		let [x0, y0] = pts[k]; const [x1, y1] = pts[k + 1];
		const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
		let err = dx + dy; push(x0, y0);
		while (x0 !== x1 || y0 !== y1) {
			const e2 = 2 * err; let nx = x0, ny = y0;
			if (e2 >= dy) { err += dy; nx += sx; }
			if (e2 <= dx) { err += dx; ny += sy; }
			if (four && nx !== x0 && ny !== y0) { if (hash3(nx, ny, seed) < 0.5) push(nx, y0); else push(x0, ny); }
			x0 = nx; y0 = ny; push(x0, y0);
		}
	}
	return out;
}
function pulse(idx, len, phase, sigK = 6) {
	const sig = Math.max(1.6, len / sigK), pos = (((phase % 1) + 1) % 1) * len;
	let d = Math.abs(idx - pos); d = Math.min(d, len - d);
	return Math.exp(-(d * d) / (2 * sig * sig));
}
function strip(frames) { const out = new Canvas(16, 16 * frames.length); frames.forEach((f, i) => out.blit(f, 0, i * 16)); return out; }
function mcmeta(rel, frametime) {
	const p = path.join(RES, rel); fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, JSON.stringify({ animation: { frametime, interpolate: true } }, null, '\t') + '\n'); written.push(rel);
}
const FRAMES = 8;

// ================================================================ ground / stone base layers (scalar "tone" maps, 0..1)
// Irregular plate seams = Voronoi borders between a handful of seeds (some borders left out so plates merge),
// relief from a height-noise slope (light from the top-left), scratches, rivets and speckle.
function metalTone(v) {
	const T = new Float32Array(256), seam = new Uint8Array(256);
	const h = (x, y) => fbm(x * 0.42, y * 0.42, v.seed + 5, 2);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let d1 = 99, d2 = 99, i1 = 0, i2 = 0;
		v.plates.forEach(([sx, sy], i) => {
			const d = Math.hypot((x + 0.5 - sx) * 1.0, (y + 0.5 - sy) * 1.15);
			if (d < d1) { d2 = d1; i2 = i1; d1 = d; i1 = i; } else if (d < d2) { d2 = d; i2 = i; }
		});
		const pair = Math.min(i1, i2) * 16 + Math.max(i1, i2);
		const n = fbm(x * 0.3, y * 0.3, v.seed, 3);
		const rel = (h(x - 1, y - 1) - h(x + 1, y + 1)) * 1.7;
		let t = 0.42 + (hash3(i1, 3, v.seed) - 0.5) * 0.3 + (n - 0.5) * 0.22 + rel * 0.45;
		t += Math.min(d1, 6) * -0.018; // plates read slightly domed: darker toward their seams
		if (d2 - d1 < 0.95 && hash3(pair, 9, v.seed) > 0.25) { seam[K(x, y)] = 1; t = 0.06 + 0.04 * hash3(x, y, v.seed + 2); }
		const s = hash3(x, y, v.seed + 7);
		if (s < 0.05) t -= 0.08; else if (s > 0.965) t += 0.09;
		T[K(x, y)] = t;
	}
	// bevel the seams: lit lip below/right of a seam, shadow above/left
	const T2 = T.slice();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (seam[K(x, y)]) continue;
		const up = y > 0 && seam[K(x, y - 1)], lf = x > 0 && seam[K(x - 1, y)];
		const dn = y < 15 && seam[K(x, y + 1)], rt = x < 15 && seam[K(x + 1, y)];
		if (up || lf) T2[K(x, y)] += 0.22;
		else if (dn || rt) T2[K(x, y)] -= 0.1;
	}
	for (const [x, y, dx, dy, len] of v.scratches || []) for (let i = 0; i < len; i++) {
		const px = x + dx * i, py = y + dy * i; if (px < 0 || py < 0 || px > 15 || py > 15) continue;
		T2[K(px, py)] += i === 0 || i === len - 1 ? 0.07 : 0.14;
	}
	for (const [x, y] of v.rivets || []) { T2[K(x, y)] = 0.92; if (x < 15 && y < 15) T2[K(x + 1, y + 1)] = 0.05; if (x < 15) T2[K(x + 1, y)] -= 0.05; }
	return T2;
}
function rockTone(v) {
	const T = new Float32Array(256);
	const h = (x, y) => fbm(x * 0.36, y * 0.36, v.seed + 11, 3);
	const crack = new Uint8Array(256);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const n = fbm(x * 0.24, y * 0.24, v.seed, 4);
		const rel = (h(x - 1, y - 1) - h(x + 1, y + 1)) * 2.2;
		let t = 0.42 + (n - 0.5) * 0.55 + rel * 0.6;
		const c1 = fbm(x * 0.2, y * 0.2, v.seed + 50, 3), c2 = fbm(x * 0.17 + 9, y * 0.23, v.seed + 80, 3);
		if (Math.abs(c1 - 0.5) < 0.028 || Math.abs(c2 - 0.47) < 0.022) { crack[K(x, y)] = 1; t = 0.04; }
		const s = hash3(x, y, v.seed + 3);
		if (s > 0.93) t += 0.16; else if (s < 0.08) t -= 0.12;
		T[K(x, y)] = t;
	}
	const T2 = T.slice();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (!crack[K(x, y)]) {
		if ((y > 0 && crack[K(x, y - 1)]) || (x > 0 && crack[K(x - 1, y)])) T2[K(x, y)] += 0.14;
	}
	return T2;
}

// ================================================================ vein network painter (ground + stone)
// spec: { veins:[{pts, main, phase}], nodes:[[x,y,phase]], chips:[[x,y,w,h,litPin]] }
// opts: { tone(T)->rgb, edge:boolean, dirtMask, inlay:boolean }
function buildNetwork(spec, opts) {
	const paths = spec.veins.map((v, i) => {
		const main = v.main && !opts.edge;
		return { px: line(v.pts, main, 17 + i), main, phase: v.phase || 0, fine: !main };
	});
	const owner = new Map(); // key -> [{pathIdx, idx}]
	paths.forEach((p, pi) => p.px.forEach(([x, y], idx) => { const k = K(x, y); if (!owner.has(k)) owner.set(k, []); owner.get(k).push({ pi, idx }); }));
	// rule check: only exit at 4 / 11
	for (const k of owner.keys()) {
		const x = k & 15, y = k >> 4;
		if ((x === 0 || x === 15) && y !== 4 && y !== 11) throw new Error(`vein touches side edge at (${x},${y}) in ${spec.name}`);
		if ((y === 0 || y === 15) && x !== 4 && x !== 11) throw new Error(`vein touches top/bottom edge at (${x},${y}) in ${spec.name}`);
	}
	return { paths, owner };
}
function paintNetwork(T, spec, opts) {
	const { paths, owner } = buildNetwork(spec, opts);
	const base = new Array(256);
	for (let k = 0; k < 256; k++) base[k] = opts.tone(T[k], k & 15, k >> 4);
	const isV = k => owner.has(k);
	// chips (static): near-black body, lit top edge, legs along the long sides
	const chipPins = [];
	for (const [cx, cy, w, h, lit] of spec.chips || []) {
		for (let y = cy; y < cy + h; y++) for (let x = cx; x < cx + w; x++) {
			const top = y === cy, lft = x === cx;
			base[K(x, y)] = opts.tone(top || lft ? 0.24 : 0.0, x, y);
			if (x === cx + w - 1 && y === cy + h - 1 && w * h > 4) base[K(x, y)] = opts.tone(0.12, x, y);
		}
		const horiz = w >= h;
		for (let i = 0; i < (horiz ? w : h); i++) {
			if (i % 2 === 1 && (horiz ? w : h) > 2) continue;
			const pins = horiz ? [[cx + i, cy - 1], [cx + i, cy + h]] : [[cx - 1, cy + i], [cx + w, cy + i]];
			pins.forEach(([x, y], j) => {
				if (x < 0 || y < 0 || x > 15 || y > 15 || isV(K(x, y))) return;
				if (lit && i === 0 && j === 1) chipPins.push([x, y]); else base[K(x, y)] = opts.tone(0.9, x, y);
			});
		}
		if (!horiz || w > 2) { const mx = cx + (w >> 1), my = cy + (h >> 1); if (w * h >= 6) base[K(mx, my)] = opts.tone(0.28, mx, my); }
	}
	// carved channel: shadow on the up/left wall, wet red glow on the down/right wall
	const glow = new Float32Array(256);
	for (const [k, own] of owner) {
		const x = k & 15, y = k >> 4, main = own.some(o => paths[o.pi].main);
		for (const [dx, dy, w] of [[-1, 0, -1], [0, -1, -1], [1, 0, 1], [0, 1, 1], [1, 1, 0.4], [-1, -1, -0.4]]) {
			const nx = x + dx, ny = y + dy; if (nx < 0 || ny < 0 || nx > 15 || ny > 15) continue;
			const nk = K(nx, ny); if (isV(nk)) continue;
			if (w < 0 && main) base[nk] = mul(base[nk], 0.62);
			else if (w > 0) glow[nk] = Math.max(glow[nk], (main ? 0.42 : 0.12) * w);
		}
	}
	if (opts.inlay) for (const k of owner.keys()) { // stone: polished metal rim around the inlay
		const x = k & 15, y = k >> 4;
		for (const [dx, dy, lit] of [[-1, 0, 1], [0, -1, 1], [1, 0, 0], [0, 1, 0]]) {
			const nx = x + dx, ny = y + dy; if (nx < 0 || ny < 0 || nx > 15 || ny > 15 || isV(K(nx, ny))) continue;
			base[K(nx, ny)] = lit ? mix(MET[7], MET[8], hash3(nx, ny, 4)) : MET[1];
			glow[K(nx, ny)] *= 0.5;
		}
	}
	// node sockets (static dark ring)
	for (const [nx, ny] of spec.nodes || []) for (let y = ny - 1; y <= ny + 2; y++) for (let x = nx - 1; x <= nx + 2; x++) {
		if (x < 0 || y < 0 || x > 15 || y > 15) continue;
		const corner = (x === nx - 1 || x === nx + 2) && (y === ny - 1 || y === ny + 2);
		if (x >= nx && x <= nx + 1 && y >= ny && y <= ny + 1) continue;
		if (!corner && !isV(K(x, y))) base[K(x, y)] = (x === nx + 2 || y === ny + 2) ? MET[7] : MET[0];
	}
	const frames = [];
	for (let f = 0; f < FRAMES; f++) {
		const cv = new Canvas(16, 16), frac = f / FRAMES;
		const B = new Float32Array(256).fill(-1);
		for (const [k, own] of owner) {
			let b = 0;
			for (const { pi, idx } of own) {
				const p = paths[pi], pl = pulse(idx, p.px.length, frac + p.phase);
				let bb = p.main ? 0.6 + 0.4 * pl : 0.3 + 0.4 * pl;
				if (opts.edge) bb = 0.3 + 0.3 * pl;
				b = Math.max(b, bb);
			}
			if (own.length > 1 || own.some(o => o.idx === 0 || o.idx === paths[o.pi].px.length - 1)) b = Math.min(1, b + 0.06);
			B[k] = b;
		}
		for (let k = 0; k < 256; k++) {
			const x = k & 15, y = k >> 4;
			let c = base[k];
			if (B[k] >= 0) {
				c = vc(B[k]);
				if (opts.edge && opts.dirtMask && opts.dirtMask[k]) {
					const atEdge = x === 0 || y === 0 || x === 15 || y === 15 || x === 1 || y === 1 || x === 14 || y === 14;
					if (!atEdge && hash3(x, y, 99) < 0.3) c = mix(base[k], V_DEEP, 0.5);
					else c = mix(base[k], c, 0.55);
				}
			} else if (glow[k] > 0) {
				const gb = glow[k] * (0.85 + 0.15 * Math.sin(2 * Math.PI * frac));
				c = mix(c, GLOW, opts.edge ? gb * 0.6 : gb);
			}
			cv.set(x, y, c);
		}
		for (const [x, y] of chipPins) cv.set(x, y, vc(0.55 + 0.35 * (0.5 + 0.5 * Math.cos(2 * Math.PI * (frac - 0.3)))));
		for (const [nx, ny, ph = 0] of spec.nodes || []) {
			const p = 0.5 + 0.5 * Math.cos(2 * Math.PI * (frac - ph));
			const b = (opts.edge ? 0.45 : 0.72) + 0.28 * p;
			cv.set(nx, ny, mix(vc(b), V_WHITE, opts.edge ? 0 : 0.25 * p));
			cv.set(nx + 1, ny, vc(b * 0.92)); cv.set(nx, ny + 1, vc(b * 0.92)); cv.set(nx + 1, ny + 1, vc(b * 0.8));
		}
		frames.push(cv);
	}
	return frames;
}

// ================================================================ 1. corrupted_circuitry layouts
const GROUND = [
	{ name: 'g0', seed: 101,
		plates: [[3, 3], [12, 2], [14, 13], [5, 13], [9, 8], [1, 9]],
		scratches: [[10, 13, 1, -1, 4], [2, 1, 1, 0, 3], [13, 8, 0, 1, 3]], rivets: [[14, 2], [2, 14]],
		veins: [
			{ pts: [[0, 11], [1, 11], [3, 10], [4, 9], [6, 8], [7, 6], [8, 4], [10, 3], [11, 1], [11, 0]], main: true, phase: 0 },
			{ pts: [[6, 8], [8, 9], [10, 10], [12, 11], [14, 11], [15, 11]], main: true, phase: 0.35 },
			{ pts: [[4, 9], [5, 11], [5, 13], [4, 14], [4, 15]], phase: 0.6 },
			{ pts: [[8, 4], [10, 5], [12, 5], [13, 6]], phase: 0.2 },
			{ pts: [[12, 11], [12, 13], [13, 14]], phase: 0.8 },
			{ pts: [[3, 10], [2, 8], [2, 7]], phase: 0.45 },
		],
		nodes: [[13, 6, 0.1], [6, 8, 0.55]], chips: [[1, 5, 3, 2, true], [8, 13, 2, 2, false]] },
	{ name: 'g1', seed: 202,
		plates: [[2, 2], [9, 1], [14, 7], [10, 13], [3, 12], [7, 7]],
		scratches: [[1, 9, 1, 1, 4], [12, 1, -1, 1, 3], [6, 13, 1, 0, 3]], rivets: [[14, 14], [1, 1]],
		veins: [
			{ pts: [[4, 0], [4, 1], [5, 3], [6, 4], [6, 6], [8, 8], [9, 8], [10, 10], [10, 12], [11, 13], [11, 15]], main: true, phase: 0 },
			{ pts: [[6, 4], [8, 4], [9, 3], [11, 3], [13, 4], [15, 4]], main: true, phase: 0.5 },
			{ pts: [[0, 4], [1, 4], [2, 6], [3, 7], [5, 8], [8, 8]], phase: 0.25 },
			{ pts: [[10, 10], [12, 9], [13, 9]], phase: 0.7 },
			{ pts: [[3, 7], [2, 10], [2, 11]], phase: 0.9 },
			{ pts: [[11, 3], [12, 1]], phase: 0.4 },
		],
		nodes: [[13, 8, 0.3], [5, 3, 0.8]], chips: [[1, 12, 3, 2, true], [13, 12, 2, 3, false]] },
	{ name: 'g2', seed: 303,
		plates: [[4, 2], [13, 3], [12, 12], [3, 10], [8, 14], [7, 7]],
		scratches: [[6, 1, 1, 1, 3], [1, 13, 1, -1, 3], [13, 9, 1, 0, 2]], rivets: [[14, 1], [6, 14]],
		veins: [
			{ pts: [[0, 4], [1, 4], [3, 5], [4, 6], [7, 6], [9, 8], [11, 9], [13, 11], [14, 11], [15, 11]], main: true, phase: 0 },
			{ pts: [[11, 0], [11, 1], [10, 3], [9, 5], [9, 8]], main: true, phase: 0.4 },
			{ pts: [[4, 6], [4, 9], [5, 11], [4, 13], [4, 15]], phase: 0.15 },
			{ pts: [[11, 9], [10, 11], [11, 13], [11, 15]], phase: 0.65 },
			{ pts: [[3, 5], [2, 3]], phase: 0.3 },
			{ pts: [[10, 3], [12, 3]], phase: 0.85 },
		],
		nodes: [[1, 1, 0.2], [8, 7, 0.6]], chips: [[12, 5, 3, 2, true], [6, 12, 2, 2, false]] },
	{ name: 'g3', seed: 404,
		plates: [[3, 4], [11, 2], [13, 10], [6, 12], [8, 6], [1, 14]],
		scratches: [[2, 2, 1, 1, 5], [9, 9, 1, -1, 4], [12, 13, 1, 0, 3], [5, 7, 0, 1, 3]], rivets: [[14, 6], [4, 1], [9, 14]],
		veins: [
			{ pts: [[0, 11], [1, 11], [3, 10], [5, 11], [7, 13], [9, 13], [11, 14], [11, 15]], phase: 0 },
		],
		nodes: [], chips: [[10, 4, 3, 2, false]] },
];
// edge variants: same vein layout (so they still join their neighbours), soot/dirt-broken metal, faded veins
function dirtMask(seed) {
	const m = new Uint8Array(256);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const n = fbm(x * 0.21, y * 0.21, seed + 900, 4) + (hash3(x, y, seed + 901) - 0.5) * 0.08;
		m[K(x, y)] = n > 0.5 ? 2 : n > 0.46 ? 1 : 0; // 2 = dirt, 1 = soot fringe
	}
	return m;
}
function groundTexture(v, edge) {
	const T = metalTone(v);
	const dm = edge ? dirtMask(v.seed) : null;
	const tone = (t, x, y) => {
		if (!edge) return metal(t);
		const k = K(x, y);
		if (dm[k] === 2) { const n = fbm(x * 0.5, y * 0.5, v.seed + 77, 2); let c = dirt(0.15 + n * 0.75 + (hash3(x, y, 5) - 0.5) * 0.35); if (hash3(x, y, 6) > 0.92) c = DIRT[4]; return c; }
		if (dm[k] === 1) return mix(SOOT, metal(t * 0.7), 0.35);
		return mul(metal(t * 0.92), 0.88);
	};
	return paintNetwork(T, v, { tone, edge, dirtMask: dm ? dm.map(d => d === 2 ? 1 : 0) : null });
}
const groundFrames = [], edgeFrames = [];
GROUND.forEach((v, i) => {
	const fr = groundTexture(v, false), fe = groundTexture(v, true);
	groundFrames.push(fr); edgeFrames.push(fe);
	writePng(`textures/block/corrupted_circuitry_${i}.png`, strip(fr));
	mcmeta(`textures/block/corrupted_circuitry_${i}.png.mcmeta`, 3);
	writePng(`textures/block/corrupted_circuitry_edge_${i}.png`, strip(fe));
	mcmeta(`textures/block/corrupted_circuitry_edge_${i}.png.mcmeta`, 3);
	writeJson(`models/block/corrupted_circuitry_${i}.json`, { parent: 'minecraft:block/cube_all', textures: { all: `${NS}:block/corrupted_circuitry_${i}` } });
	writeJson(`models/block/corrupted_circuitry_edge_${i}.json`, { parent: 'minecraft:block/cube_all', textures: { all: `${NS}:block/corrupted_circuitry_edge_${i}` } });
});
function weighted(prefix, weights) {
	const out = [];
	weights.forEach((w, i) => [0, 90, 180, 270].forEach(r => { const e = { model: `${NS}:block/${prefix}_${i}` }; if (r) e.y = r; e.weight = w; out.push(e); }));
	return out;
}
writeJson('blockstates/corrupted_circuitry.json', { variants: {
	'edge=false': weighted('corrupted_circuitry', [3, 3, 3, 1]),
	'edge=true': weighted('corrupted_circuitry_edge', [3, 3, 3, 1]),
} });
writeJson('models/item/corrupted_circuitry.json', { parent: `${NS}:block/corrupted_circuitry_0` });

// ================================================================ 2. corrupted_circuitry_stone
const STONES = [
	{ name: 's0', seed: 511,
		veins: [
			{ pts: [[0, 4], [1, 4], [3, 5], [5, 4], [8, 6], [10, 5], [13, 6], [14, 4], [15, 4]], main: true, phase: 0 },
			{ pts: [[8, 6], [9, 9], [11, 12], [11, 15]], main: true, phase: 0.45 },
			{ pts: [[3, 5], [2, 8], [2, 9]], phase: 0.7 },
		],
		nodes: [[2, 10, 0.4]], chips: [[5, 11, 3, 2, false]] },
	{ name: 's1', seed: 622,
		veins: [
			{ pts: [[4, 0], [4, 1], [5, 3], [4, 6], [6, 9], [5, 12], [4, 14], [4, 15]], main: true, phase: 0 },
			{ pts: [[6, 9], [9, 10], [12, 9], [14, 11], [15, 11]], main: true, phase: 0.5 },
			{ pts: [[5, 3], [8, 2], [10, 3]], phase: 0.2 },
		],
		nodes: [[10, 3, 0.6]], chips: [[11, 13, 2, 2, true]] },
	{ name: 's2', seed: 733,
		veins: [
			{ pts: [[11, 0], [11, 1], [10, 3], [12, 6], [11, 8]], main: true, phase: 0 },
			{ pts: [[0, 11], [1, 11], [3, 12], [6, 10], [7, 11]], phase: 0.5 },
		],
		nodes: [[11, 8, 0.3], [7, 11, 0.8]], chips: [] },
];
const stoneFrames = [];
STONES.forEach((v, i) => {
	const T = rockTone(v);
	const fr = paintNetwork(T, v, { tone: t => stone(t), inlay: true });
	stoneFrames.push(fr);
	writePng(`textures/block/corrupted_circuitry_stone_${i}.png`, strip(fr));
	mcmeta(`textures/block/corrupted_circuitry_stone_${i}.png.mcmeta`, 3);
	writeJson(`models/block/corrupted_circuitry_stone_${i}.json`, { parent: 'minecraft:block/cube_all', textures: { all: `${NS}:block/corrupted_circuitry_stone_${i}` } });
});
writeJson('blockstates/corrupted_circuitry_stone.json', { variants: { '': weighted('corrupted_circuitry_stone', [3, 3, 2]) } });
writeJson('models/item/corrupted_circuitry_stone.json', { parent: `${NS}:block/corrupted_circuitry_stone_0` });

// ================================================================ 3. corrupted_circuitry_log
// Side: fissured dark bark (periodic in y so the trunk tiles), a black metal rib growing up it, a clamp band wrapping
// around, and two red vein channels whose pulse climbs the trunk seamlessly from block to block.
const TAU = Math.PI * 2;
const cylNoise = (x, y, seed, sx = 0.5) => vnoise(Math.cos(TAU * y / 16) * 1.6 + seed, Math.sin(TAU * y / 16) * 1.6, x * sx); // periodic in y
const fissures = [[1, 1, 0.3, 1.0], [5, 2, 2.1, 0.8], [10, 1, 4.0, 1.1], [14, 2, 5.2, 0.7]];
const fissX = (f, y) => f[0] + Math.round(f[3] * Math.sin(TAU * f[1] * y / 16 + f[2]));
const ribX = y => 7 + Math.round(0.9 * Math.sin(TAU * y / 16 + 1.3));
const bandY = x => 5 + Math.round(0.7 * Math.sin(TAU * x / 16 + 0.6));
const veinX = [y => 3 + Math.round(1.0 * Math.sin(TAU * y / 16 + 0.5)), y => 12 + Math.round(1.1 * Math.sin(TAU * y / 16 + 2.4))];
const logFrames = [];
{
	const base = new Array(256);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let dmin = 9;
		for (const f of fissures) { let d = x - fissX(f, y); d = ((d % 16) + 16) % 16; d = Math.min(d, 16 - d); dmin = Math.min(dmin, d); }
		const n = cylNoise(x, y, 3, 0.7);
		let t = dmin === 0 ? 0.08 : 0.42 + Math.min(dmin, 3) * 0.12 + (n - 0.5) * 0.3;
		// left side of each ridge catches light, right side falls into the next fissure
		for (const f of fissures) { const fx = fissX(f, y); if (x === (fx + 1) % 16) t += 0.1; if (x === (fx + 15) % 16) t -= 0.08; }
		if (hash3(x, (y * 7) >> 2, 11) < 0.05 && dmin > 0) t = 0.12; // short horizontal bark splits
		t += (hash3(x, y, 12) - 0.5) * 0.12;
		base[K(x, y)] = bark(t);
	}
	// vertical black-metal rib (2 px), shadow on the bark to its right
	for (let y = 0; y < 16; y++) {
		const rx = ribX(y);
		base[K(rx, y)] = mix(MET[8], MET[9], 0.6 * hash3(rx, y, 2));
		base[K(rx + 1, y)] = mix(MET[1], MET[3], hash3(rx, y, 3));
		base[K(rx + 2, y)] = mul(base[K(rx + 2, y)], 0.55);
		base[K(rx - 1, y)] = mul(base[K(rx - 1, y)], 0.8);
		if (y === 2 || y === 10) { base[K(rx, y)] = MET[9]; base[K(rx + 1, y)] = MET[6]; }
	}
	// clamp band wrapping the trunk (2 rows, lit top, dark lower lip), shadow below
	for (let x = 0; x < 16; x++) {
		const by = bandY(x);
		base[K(x, by)] = mix(MET[8], MET[9], 0.3 + 0.7 * hash3(x, by, 5));
		base[K(x, by + 1)] = mix(MET[0], MET[2], hash3(x, by, 6));
		base[K(x, by + 2)] = mul(base[K(x, by + 2)], 0.6);
		if (by > 0) base[K(x, by - 1)] = mul(base[K(x, by - 1)], 0.85);
	}
	// a smaller metal tendril curling off the rib
	const tendril = line([[9, 10], [10, 11], [11, 11], [12, 13], [13, 14]], false);
	tendril.forEach(([x, y], i) => { base[K(x, y)] = i < 3 ? MET[5] : MET[3]; if (y < 15) base[K(x, y + 1)] = mul(base[K(x, y + 1)], 0.65); });
	// vein channels (4-connected, periodic in y). Index runs bottom->top so the pulse climbs.
	const vein = new Map();
	veinX.forEach((fx, vi) => {
		for (let y = 15; y >= 0; y--) {
			const x = fx(y), idx = 15 - y;
			vein.set(K(x, y), { vi, idx });
			const xn = fx((y + 15) % 16); // row above
			if (y > 0 && xn !== x) vein.set(K(xn, y), { vi, idx: idx + 0.5 });
		}
	});
	const branch = line([[12, 9], [11, 8], [9, 7]], false); // vein feeding the rib
	branch.forEach(([x, y], i) => { if (!vein.has(K(x, y))) vein.set(K(x, y), { vi: 2, idx: i }); });
	for (const k of vein.keys()) {
		const x = k & 15, y = k >> 4;
		const l = K((x + 15) % 16, y), r = K((x + 1) % 16, y);
		if (!vein.has(l)) base[l] = mul(base[l], 0.55);
		if (!vein.has(r)) base[r] = mix(base[r], GLOW, 0.35);
	}
	for (let f = 0; f < FRAMES; f++) {
		const cv = new Canvas(16, 16), frac = f / FRAMES;
		for (let k = 0; k < 256; k++) {
			const x = k & 15, y = k >> 4, v = vein.get(k);
			if (!v) { cv.set(x, y, base[k]); continue; }
			const len = v.vi === 2 ? 6 : 16, ph = v.vi === 1 ? 0.5 : v.vi === 2 ? 0.25 : 0;
			let b = 0.55 + 0.45 * pulse(v.idx, len, frac + ph, 4);
			if (y === bandY(x) || y === bandY(x) + 1) b = Math.min(1, b + 0.12); // vein cutting through the band glows hotter
			cv.set(x, y, vc(b));
		}
		// junction where the branch meets the rib
		const jp = 0.5 + 0.5 * Math.cos(TAU * (frac - 0.25));
		cv.set(ribX(7) - 1, 7, mix(vc(0.8 + 0.2 * jp), V_WHITE, 0.2 * jp));
		logFrames.push(cv);
	}
	writePng('textures/block/corrupted_circuitry_log.png', strip(logFrames));
	mcmeta('textures/block/corrupted_circuitry_log.png.mcmeta', 3);
}
// Top: tree rings as concentric circuitry around a glowing red core, bark ring outside.
const logTop = new Canvas(16, 16);
{
	const spokes = [0.35, 2.0, 3.4, 5.0];
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x - 7.5, dy = y - 7.5, d = Math.hypot(dx, dy), a = (Math.atan2(dy, dx) + TAU) % TAU;
		const r = d + 0.32 * Math.sin(3 * a + 1.1) + 0.18 * Math.sin(5 * a + 0.3);
		let c;
		if (Math.max(Math.abs(dx), Math.abs(dy)) >= 7 || r > 7.3) {
			c = bark(0.35 + (vnoise(x * 0.8, y * 0.8, 21) - 0.5) * 0.35 + (hash3(x, y, 22) - 0.5) * 0.15);
			if (r > 6.8 && r < 7.6 && Math.max(Math.abs(dx), Math.abs(dy)) < 7.5) c = bark(0.08);
		} else if (d < 1.0) {
			c = (x === 7 && y === 7) ? V_WHITE : (x === 8 && y === 8) ? V_CORE : V_HOT;
		} else if (d < 1.75) {
			c = mix(V_CORE, V_DIM, 0.35);
		} else if (r < 2.4) {
			c = mix(V_DEEP, MET[1], 0.3);
		} else {
			// ring bands: trace rings at ~3.2, ~4.8, ~6.2; growth-ring metal between
			const ringT = [3.2, 4.8, 6.2].map(q => Math.abs(r - q)).reduce((m, v) => Math.min(m, v), 9);
			let t = 0.3 + 0.12 * Math.sin(r * 4.2) + (hash3(x, y, 31) - 0.5) * 0.12;
			c = metal(t);
			if (ringT < 0.38) {
				const lit = Math.abs(r - 4.8) < 0.38 && (a > 3.6 && a < 5.6); // one red-lit arc on the middle ring
				c = lit ? vc(0.7) : mix(MET[6], MET[8], hash3(x, y, 32) * 0.6);
			}
			for (const s of spokes) {
				let da = Math.abs(a - s); da = Math.min(da, TAU - da);
				if (da * d < 0.55 && r > 2.3 && r < 6.6) {
					c = r < 3.6 ? vc(0.62) : mix(MET[7], MET[8], 0.4);
					if (ringT < 0.38) c = r < 4 ? vc(0.85) : MET[9];
				}
			}
		}
		logTop.set(x, y, c);
	}
	writePng('textures/block/corrupted_circuitry_log_top.png', logTop);
}
writeJson('blockstates/corrupted_circuitry_log.json', { variants: {
	'axis=x': { model: `${NS}:block/corrupted_circuitry_log`, x: 90, y: 90 },
	'axis=y': { model: `${NS}:block/corrupted_circuitry_log` },
	'axis=z': { model: `${NS}:block/corrupted_circuitry_log`, x: 90 },
} });
writeJson('models/block/corrupted_circuitry_log.json', { parent: 'minecraft:block/cube_column', textures: { end: `${NS}:block/corrupted_circuitry_log_top`, side: `${NS}:block/corrupted_circuitry_log` } });
writeJson('models/item/corrupted_circuitry_log.json', { parent: `${NS}:block/corrupted_circuitry_log` });

// ================================================================ 4. corrupted_circuitry_leaves
// Sparse wire mesh: wavy diagonal wires that are periodic over 16 px (so leaf blocks tile), red nodes where wires
// cross, a few metal flakes. Transparent texels keep a near-black RGB for Fast graphics.
const leafFrames = [];
let leafOpaque = 0;
{
	const wires = [
		t => [t, t + 1 + 2.2 * Math.sin(TAU * t / 16)],
		t => [t, -t + 9 + 1.6 * Math.sin(TAU * t / 8 + 1)],
		t => [t, -t + 2 + 2.0 * Math.sin(TAU * t / 16 + 4)],
		t => [5 + 2.4 * Math.sin(TAU * t / 16 + 0.5), t], // hanging vine
	];
	const cover = new Map(); // key -> set of wire ids
	wires.forEach((w, wi) => {
		for (let t = 0; t < 16; t += 0.04) {
			const [x, y] = w(t); const px = ((Math.floor(x) % 16) + 16) % 16, py = ((Math.floor(y) % 16) + 16) % 16;
			if (!cover.has(K(px, py))) cover.set(K(px, py), new Set());
			cover.get(K(px, py)).add(wi);
		}
	});
	// thin out: drop a few wire texels (breaks) so it reads as a tangle, not a lattice
	for (const k of [...cover.keys()]) if (cover.get(k).size === 1 && hash3(k & 15, k >> 4, 41) < 0.04) cover.delete(k);
	const nodes = [...cover.entries()].filter(([, s]) => s.size > 1).map(([k]) => k);
	const flakes = [[[1, 6], [2, 6], [2, 7]], [[13, 2], [14, 2], [14, 3]], [[10, 12], [11, 12], [10, 13]], [[6, 14], [7, 13]], [[13, 8], [12, 9]]];
	const flake = new Map();
	flakes.forEach((fl, i) => fl.forEach(([x, y], j) => { if (!cover.has(K(x, y))) flake.set(K(x, y), j === 0 ? 0.62 : j === 1 ? 0.4 : 0.2); }));
	for (let f = 0; f < 4; f++) {
		const cv = new Canvas(16, 16), frac = f / 4;
		for (let k = 0; k < 256; k++) cv.set(k & 15, k >> 4, hex('0E1014'), 0);
		for (const [k, s] of cover) {
			const x = k & 15, y = k >> 4, wi = [...s][0];
			let t = 0.4 + (wi % 2) * 0.14 + (hash3(x, y, 51) - 0.5) * 0.2;
			if (hash3(x, y, 52) > 0.9) t = 0.8; // glint
			cv.set(x, y, metal(t));
		}
		for (const [k, t] of flake) cv.set(k & 15, k >> 4, metal(t));
		nodes.forEach((k, i) => {
			const x = k & 15, y = k >> 4, ph = hash3(x, y, 61), pulsing = hash3(x, y, 62) > 0.35;
			const p = pulsing ? 0.5 + 0.5 * Math.cos(TAU * (frac - ph)) : 0.4;
			cv.set(x, y, mix(vc(0.55 + 0.45 * p), V_WHITE, 0.2 * p * p));
			if (i % 2 === 0) { // some nodes are 2 px
				const nx = (x + 1) % 16; if (!cover.has(K(nx, y))) cv.set(nx, y, vc(0.35 + 0.35 * p));
			}
		});
		leafFrames.push(cv);
	}
	for (let k = 0; k < 256; k++) if (leafFrames[0].get(k & 15, k >> 4)[3] > 0) leafOpaque++;
	writePng('textures/block/corrupted_circuitry_leaves.png', strip(leafFrames));
	mcmeta('textures/block/corrupted_circuitry_leaves.png.mcmeta', 6);
}
writeJson('blockstates/corrupted_circuitry_leaves.json', { variants: { '': { model: `${NS}:block/corrupted_circuitry_leaves` } } });
writeJson('models/block/corrupted_circuitry_leaves.json', { parent: 'minecraft:block/leaves', textures: { all: `${NS}:block/corrupted_circuitry_leaves` } });
writeJson('models/item/corrupted_circuitry_leaves.json', { parent: `${NS}:block/corrupted_circuitry_leaves` });

// ================================================================ remove superseded single-texture files
for (const rel of ['textures/block/corrupted_circuitry.png', 'textures/block/corrupted_circuitry.png.mcmeta', 'models/block/corrupted_circuitry.json',
	'textures/block/corrupted_circuitry_stone.png', 'textures/block/corrupted_circuitry_stone.png.mcmeta', 'models/block/corrupted_circuitry_stone.json']) {
	const p = path.join(RES, rel); if (fs.existsSync(p)) { fs.unlinkSync(p); console.log('deleted ' + rel); }
}

console.log(written.join('\n'));
console.log(`leaves: ${leafOpaque}/256 opaque (${(100 - leafOpaque / 2.56).toFixed(1)}% transparent)`);

// ================================================================ optional preview sheet (not part of the mod)
if (PREVIEW) {
	const S = 4, rot = (cv, r) => { const o = new Canvas(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { let sx = x, sy = y; for (let i = 0; i < r; i++) { const t = sx; sx = sy; sy = 15 - t; } o.set(x, y, cv.get(sx, sy), 255); } return o; };
	const W = 16 * 26, H = 16 * 12, sheet = new Canvas(W * S, H * S);
	const put = (cv, bx, by) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const c = cv.get(x, y); const bg = ((x >> 2) + (y >> 2)) & 1 ? [70, 90, 60] : [60, 80, 50]; const col = c[3] ? c.slice(0, 3) : bg; for (let j = 0; j < S; j++) for (let i = 0; i < S; i++) sheet.set((bx + x) * S + i, (by + y) * S + j, col); } };
	const pick = (ws, h) => { let s = ws.reduce((a, b) => a + b), r = h * s; for (let i = 0; i < ws.length; i++) { if ((r -= ws[i]) < 0) return i; } return ws.length - 1; };
	const frame = +(process.env.PF || 0);
	for (let by = 0; by < 10; by++) for (let bx = 0; bx < 10; bx++) {
		const edge = bx >= 8;
		const v = pick([3, 3, 3, 1], hash3(bx, by, 1)), r = Math.floor(hash3(bx, by, 2) * 4);
		put(rot((edge ? edgeFrames : groundFrames)[v][frame], r), bx * 16, by * 16);
	}
	for (let by = 0; by < 6; by++) for (let bx = 0; bx < 6; bx++) {
		const v = pick([3, 3, 2], hash3(bx, by, 3)), r = Math.floor(hash3(bx, by, 4) * 4);
		put(rot(stoneFrames[v][frame], r), 176 + bx * 16, by * 16);
	}
	for (let by = 0; by < 4; by++) for (let bx = 0; bx < 2; bx++) put(logFrames[frame], 176 + bx * 16, 112 + by * 16 - 16);
	put(logTop, 176 + 40, 112);
	for (let by = 0; by < 4; by++) for (let bx = 0; bx < 4; bx++) put(leafFrames[frame % 4], 288 + bx * 16, 112 + by * 16 - 16);
	GROUND.forEach((g, i) => { put(groundFrames[i][frame], 288 + i * 18 - 16 * 0, 0 + 0); put(edgeFrames[i][frame], 288 + i * 18, 18); });
	STONES.forEach((g, i) => put(stoneFrames[i][frame], 288 + i * 18, 40));
	fs.mkdirSync(PREVIEW, { recursive: true });
	fs.writeFileSync(path.join(PREVIEW, `blocks2_preview_f${frame}.png`), encodePng(sheet.w, sheet.h, sheet.px));
	console.log('preview -> ' + path.join(PREVIEW, `blocks2_preview_f${frame}.png`));
}
