// v0.17.1 Hero Landing: the Green Lantern emblem decal (QuirkFx.DECAL_EMBLEM) -- the Lantern symbol (a ring between two
// bars) glowing in a web of fractures, white and premultiplied (additive; the renderer tints it green and pulses it).
// Usage, from the repo root: node scratchpad/gen_hero_landing_textures.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encode } = require('./suits/skin_tools.js');

const ROOT = path.resolve(process.argv[2] || path.join(__dirname, '..'));
const FX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/quirk/fx');
fs.mkdirSync(FX, { recursive: true });

const clamp = (v, a = 0, b = 255) => Math.max(a, Math.min(b, Math.round(v)));
const hash = (x, y, s = 0) => { let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
const smooth = t => t * t * (3 - 2 * t);
function vnoise(x, y, s) {
	const xi = Math.floor(x), yi = Math.floor(y), fx = smooth(x - xi), fy = smooth(y - yi);
	const a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
	return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}

const N = 128, px = Buffer.alloc(N * N * 4), c = (N - 1) / 2;
/** Soft-edged band: 1 inside |d| < w, falling to 0 over f. */
const band = (d, w, f) => Math.max(0, Math.min(1, 1 - (Math.abs(d) - w) / f));
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
	const dx = (x - c) / c, dy = (y - c) / c, d = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
	// the symbol: a ring (r 0.34) between two bars (y = +-0.47, half width 0.6), scaled to sit inside the crack web
	const ring = band(d - 0.34, 0.075, 0.03);
	const barW = band(Math.abs(dy) - 0.47, 0.06, 0.03) * band(dx, 0.58, 0.04);
	// the posts joining the ring to the bars at either side (the lantern's frame)
	const post = band(Math.abs(dx) - 0.52, 0.05, 0.03) * (Math.abs(dy) < 0.47 && Math.abs(dy) > 0.18 ? 1 : 0) * 0;
	let sym = Math.max(ring, barW, post);
	// a soft inner glow and a faint halo round the symbol
	const halo = Math.max(0, 1 - Math.abs(d - 0.34) / 0.25) * 0.28 + Math.max(0, 1 - d / 0.3) * 0.18;
	// fractures radiating out of the symbol, glowing where they leave it
	let crack = 0;
	for (let i = 0; i < 11; i++) {
		const ca = i / 11 * Math.PI * 2 + hash(i, 0, 5) * 0.45;
		let da = Math.abs(((ang - ca + Math.PI * 3) % (Math.PI * 2)) - Math.PI);
		const wob = (vnoise(d * 8, i, 13) - 0.5) * 0.22;
		da = Math.abs(da + wob) * d * c;
		const len = 0.98 - hash(i, 1, 5) * 0.32;
		if (d > 0.3 && d < len && da < 1.4) crack = Math.max(crack, (1 - da / 1.4) * (1 - (d - 0.3) / (len - 0.3)) * 0.85);
	}
	// grain so it reads as light in broken ground, not a flat sticker
	const grain = 0.82 + 0.18 * vnoise(x / 3, y / 3, 21);
	let a = Math.min(1, Math.max(sym * grain, halo, crack));
	a *= 1 - Math.max(0, d - 0.92) * 8;
	a = Math.max(0, Math.min(1, a));
	const i = (y * N + x) * 4;
	// premultiplied white (additive blend ignores alpha)
	px[i] = clamp(255 * a); px[i + 1] = clamp(255 * a); px[i + 2] = clamp(255 * a); px[i + 3] = clamp(255 * a);
}
fs.writeFileSync(path.join(FX, 'decal_emblem.png'), encode(N, N, px));
console.log('decal_emblem', N + 'x' + N);
