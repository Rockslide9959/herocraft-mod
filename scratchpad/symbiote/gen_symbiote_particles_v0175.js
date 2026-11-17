// v0.17.5 round 2: sprites for the Symbiote's own particles (SymbioteParticles / SymbioteParticleClient). Original pixel art.
//   node scratchpad/symbiote/gen_symbiote_particles_v0175.js
// Writes textures/particle/symbiote_saliva_0..3.png (a clear, glistening drop: beading, stretching, falling, splat) and
// textures/particle/symbiote_goo_0..2.png (a wet goo droplet, greyscale so the game tints it the suit's body colour: round, stretched,
// splat), plus particles/symbiote_saliva.json and particles/symbiote_goo.json.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const ASSETS = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero');
const N = 16;
const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, Math.round(v)));

/**
 * A drop shape: an ellipse (cx, cy, rx, ry) with an optional tail up to `tail` rows above it, lit from the upper left.
 * `fill(n, l, edge)` -> [r, g, b, a] given the surface normal's z (n), the light term (l) and how close to the rim (edge).
 */
function drop(cx, cy, rx, ry, tail, fill) {
	const px = Buffer.alloc(N * N * 4);
	for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
		const fx = x + 0.5, fy = y + 0.5;
		let dx = (fx - cx) / rx, dy = (fy - cy) / ry;
		// the tail: a narrowing neck above the drop
		if (tail > 0 && fy < cy && fy > cy - ry - tail) {
			const t = (cy - fy) / (ry + tail);
			const w = rx * Math.max(0.12, 1 - t * 1.25);
			dx = (fx - cx) / w;
			dy = fy > cy - ry * 0.4 ? dy : 0;
		}
		const d2 = dx * dx + dy * dy;
		if (d2 > 1) continue;
		const nz = Math.sqrt(1 - Math.min(1, d2));
		const l = Math.max(0, -0.55 * dx - 0.6 * dy + 0.58 * nz);
		const c = fill(nz, l, Math.sqrt(d2));
		const i = (y * N + x) * 4;
		px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = clamp(c[3]);
	}
	return px;
}
function splat() {
	const px = Buffer.alloc(N * N * 4);
	const blobs = [[8, 10, 4.2, 1.6], [4.5, 10.5, 1.4, 0.8], [11.8, 9.8, 1.6, 0.9], [8.4, 8.2, 1.2, 0.8], [2.6, 9.4, 0.7, 0.6], [13.6, 11.2, 0.8, 0.5]];
	for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
		let best = 2;
		for (const [cx, cy, rx, ry] of blobs) {
			const dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
			best = Math.min(best, dx * dx + dy * dy);
		}
		if (best > 1) continue;
		const i = (y * N + x) * 4;
		px[i] = px[i + 1] = px[i + 2] = 0; px[i + 3] = 255; // filled in by the caller's colour rule
		px[i] = Math.round(255 * (1 - best));
	}
	return px;
}

// saliva: almost clear -- a cool pale body, a bright rim on the shadow side, a hard white glint; translucent in the middle
const saliva = (nz, l, edge) => {
	const rim = Math.pow(edge, 3);
	const v = 150 + 70 * l + 50 * rim;
	const a = 120 + 110 * rim + 25 * l;
	const glint = l > 0.74 ? 1 : 0;
	return glint ? [255, 255, 255, 250] : [v * 0.9, v * 0.95, v, a];
};
// goo: greyscale (tinted in game): dark body, soft fill light, a sharp wet highlight
const goo = (nz, l, edge) => {
	const base = 90 + 120 * Math.pow(l, 1.6);
	const glint = l > 0.7 ? 255 : base;
	return [glint, glint, glint, 255 - Math.pow(edge, 6) * 40];
};

const out = {
	symbiote_saliva_0: drop(8, 6.5, 3.0, 3.2, 0, saliva),    // beading at the lip
	symbiote_saliva_1: drop(8, 10.0, 2.6, 3.2, 5.5, saliva),  // stretching on its strand
	symbiote_saliva_2: drop(8, 9.0, 2.5, 3.4, 1.6, saliva),  // falling
	symbiote_goo_0: drop(8, 8, 4.4, 4.4, 0, goo),             // round droplet
	symbiote_goo_1: drop(8, 9.5, 3.4, 4.4, 3.0, goo),           // stretched (flung)
};
// splats: the saliva one pale and translucent, the goo one grey (tinted)
const sp = splat();
const salSplat = Buffer.alloc(N * N * 4), gooSplat = Buffer.alloc(N * N * 4);
for (let i = 0; i < N * N * 4; i += 4) {
	if (!sp[i + 3]) continue;
	const k = sp[i] / 255;
	salSplat[i] = 190 + 60 * k; salSplat[i + 1] = 198 + 55 * k; salSplat[i + 2] = 212 + 43 * k; salSplat[i + 3] = 110 + 120 * k;
	gooSplat[i] = gooSplat[i + 1] = gooSplat[i + 2] = 80 + 150 * Math.pow(k, 2); gooSplat[i + 3] = 255;
}
out.symbiote_saliva_3 = salSplat;
out.symbiote_goo_2 = gooSplat;

const PART = path.join(ASSETS, 'textures', 'particle');
fs.mkdirSync(PART, { recursive: true });
for (const [name, px] of Object.entries(out)) {
	PNG.write(path.join(PART, name + '.png'), N, N, px);
	console.log('wrote textures/particle/' + name + '.png');
}
const json = (names) => JSON.stringify({ textures: names.map((n) => 'projecthero:' + n) }, null, 2) + '\n';
fs.writeFileSync(path.join(ASSETS, 'particles', 'symbiote_saliva.json'), json(['symbiote_saliva_0', 'symbiote_saliva_1', 'symbiote_saliva_2', 'symbiote_saliva_3']));
fs.writeFileSync(path.join(ASSETS, 'particles', 'symbiote_goo.json'), json(['symbiote_goo_0', 'symbiote_goo_1', 'symbiote_goo_2']));
console.log('wrote particles/symbiote_saliva.json, particles/symbiote_goo.json');
