// v0.17.2 (user: "when upgrade takes over a block put the upgrade techno texture on it and make it look cool"): Upgrade's
// techno coat, a seamless 64x64 tile (UpgradeFx maps 32 texels to a block, so one tile spans two blocks).
//   upgrade_techno.png       the liquid metal: near-black with oily blue-green sheen bands and soft highlights, the circuit
//                            traces sunk into it in dark green (still readable where the glow pass is faint)
//   upgrade_techno_glow.png  the same traces lit -- bright Mechamorph green with pale cores and white-green pads, a dim halo
//                            round each line; black everywhere else (drawn additively, RenderType.eyes)
// Everything wraps at 64 (the noise is periodic, the traces are drawn modulo 64), so the tile repeats with no seam.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const N = 64;
const write = (f, px) => fs.writeFileSync(path.join(OUT, f), L.encodePng(N, N, px));
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const wrap = (v) => ((v % N) + N) % N;

function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function vnoise(x, y, p, seed) {
	const xi = Math.floor(x), yi = Math.floor(y), fx = x - xi, fy = y - yi;
	const w = (a) => ((a % p) + p) % p;
	const g = (i, j) => hash(w(i), w(j), seed);
	const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
	const a = g(xi, yi) + (g(xi + 1, yi) - g(xi, yi)) * sx;
	const b = g(xi, yi + 1) + (g(xi + 1, yi + 1) - g(xi, yi + 1)) * sx;
	return a + (b - a) * sy;
}
// fractal noise, periodic over the tile
function fbm(x, y, seed) {
	let v = 0, amp = 0.5, f = 4;
	for (let o = 0; o < 4; o++) {
		v += amp * vnoise(x * f / N, y * f / N, f, seed + o * 17);
		amp *= 0.5;
		f *= 2;
	}
	return v;
}

// ---------------------------------------------------------------- the circuit traces
// lines[] = 0 none, 1 trace, 2 pad ring, 3 pad core; drawn on a 4-texel lattice with 45-degree jogs, wrapping at the edges
const lines = new Uint8Array(N * N);
let seed = 21;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const set = (x, y, v) => { const i = wrap(y) * N + wrap(x); if (lines[i] < v) lines[i] = v; };
const DIRS = [[1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1], [0, -1], [1, -1]];
function pad(x, y) {
	for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) set(x + dx, y + dy, dx || dy ? 2 : 3);
}
for (let t = 0; t < 11; t++) {
	let x = Math.floor(rnd() * 16) * 4, y = Math.floor(rnd() * 16) * 4;
	let d = Math.floor(rnd() * 4) * 2; // start straight (0, 2, 4, 6)
	pad(x, y);
	const steps = 3 + Math.floor(rnd() * 5);
	for (let s = 0; s < steps; s++) {
		const len = 4 * (1 + Math.floor(rnd() * 2));
		for (let k = 0; k < len; k++) {
			x += DIRS[d][0];
			y += DIRS[d][1];
			set(x, y, 1);
		}
		const r = rnd();
		// jog 45 degrees then come back to the straight run (the PCB look); now and then a hard 90
		if (d % 2 === 0) {
			d = r < 0.35 ? (d + 1) % 8 : r < 0.7 ? (d + 7) % 8 : r < 0.82 ? (d + 2) % 8 : d;
		} else {
			d = r < 0.5 ? (d + 1) % 8 : (d + 7) % 8;
		}
	}
	if (rnd() < 0.75) pad(x, y);
}
// a few little chips: a hollow rectangle with pins
for (let c = 0; c < 1; c++) {
	const x0 = Math.floor(rnd() * 16) * 4, y0 = Math.floor(rnd() * 16) * 4, w = 6 + Math.floor(rnd() * 3) * 2, h = 4 + Math.floor(rnd() * 2) * 2;
	for (let x = 0; x <= w; x++) { set(x0 + x, y0, 1); set(x0 + x, y0 + h, 1); }
	for (let y = 0; y <= h; y++) { set(x0, y0 + y, 1); set(x0 + w, y0 + y, 1); }
	for (let x = 2; x < w; x += 2) { set(x0 + x, y0 - 1, 2); set(x0 + x, y0 + h + 1, 2); }
	set(x0 + (w >> 1), y0 + (h >> 1), 3);
}
// distance (in texels, up to 2) from each texel to the nearest line, for the halo
const near = new Float32Array(N * N).fill(9);
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
	let best = 9;
	for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
		if (lines[wrap(y + dy) * N + wrap(x + dx)]) best = Math.min(best, Math.hypot(dx, dy));
	}
	near[y * N + x] = best;
}

// ---------------------------------------------------------------- the two sheets
const base = Buffer.alloc(N * N * 4);
const glow = Buffer.alloc(N * N * 4);
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
	const i = y * N + x, o = i * 4;
	// liquid metal: a dark body, oily sheen bands where a slow noise crosses its contours, small bright speculars
	const n = fbm(x, y, 3);
	const band = Math.pow(0.5 + 0.5 * Math.sin(n * 18.0), 6);
	const spec = Math.pow(clamp((fbm(x + 11, y + 5, 9) - 0.58) * 4.0), 2);
	let r = 8 + 10 * n + 22 * band + 70 * spec;
	let g = 11 + 13 * n + 30 * band + 78 * spec;
	let b = 12 + 14 * n + 34 * band + 82 * spec;
	const l = lines[i];
	if (l) {
		// the traces, sunk into the metal
		r = l === 3 ? 60 : l === 2 ? 30 : 12;
		g = l === 3 ? 170 : l === 2 ? 110 : 82;
		b = l === 3 ? 80 : l === 2 ? 48 : 34;
	} else if (near[i] < 1.6) {
		r *= 0.7; g *= 0.85; b *= 0.75; // a groove each side of a trace
	}
	base[o] = clamp(r, 0, 255); base[o + 1] = clamp(g, 0, 255); base[o + 2] = clamp(b, 0, 255); base[o + 3] = 255;
	// the glow: Mechamorph green (#39FF6A) lines, pale-green cores at the pads, a faint halo
	let gr = 0, gg = 0, gb = 0;
	if (l === 1) { gr = 57; gg = 255; gb = 106; }
	else if (l === 2) { gr = 110; gg = 255; gb = 140; }
	else if (l === 3) { gr = 230; gg = 255; gb = 225; }
	else if (near[i] < 2.3) {
		const k = near[i] < 1.1 ? 0.22 : near[i] < 1.6 ? 0.1 : 0.04;
		gr = 57 * k; gg = 255 * k; gb = 106 * k;
	}
	glow[o] = gr; glow[o + 1] = gg; glow[o + 2] = gb; glow[o + 3] = 255;
}
write('upgrade_techno.png', base);
write('upgrade_techno_glow.png', glow);
console.log('wrote upgrade_techno.png, upgrade_techno_glow.png');
