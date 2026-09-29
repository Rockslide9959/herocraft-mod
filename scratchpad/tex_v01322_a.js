// v0.13.22 revamp batch A overlay textures (64x64 player-skin layout shells). Run from the repo root:
//   node scratchpad/tex_v01322_a.js
const path = require('path');
const png = require('./pnglib.js');
const OUT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures/entity/mutation');

// base-layer skin regions: [u0, v0, u1, v1]
const HEAD = [0, 0, 32, 16];
const BODY = [16, 16, 40, 32];
const RARM = [40, 16, 56, 32];
const RLEG = [0, 16, 16, 32];
const LARM = [32, 48, 48, 64];
const LLEG = [16, 48, 32, 64];
const LIMBS = [BODY, RARM, RLEG, LARM, LLEG];
const ALL = [HEAD, ...LIMBS];

function rng(seed) {
	let s = seed >>> 0;
	return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

function canvas() {
	return { w: 64, h: 64, data: Buffer.alloc(64 * 64 * 4) };
}

function put(c, x, y, r, g, b, a) {
	if (x < 0 || y < 0 || x >= 64 || y >= 64) return;
	const i = (y * 64 + x) * 4;
	c.data[i] = r; c.data[i + 1] = g; c.data[i + 2] = b; c.data[i + 3] = a;
}

function inRegion(x, y, regions) {
	return regions.some(([u0, v0, u1, v1]) => x >= u0 && x < u1 && y >= v0 && y < v1);
}

/** Random-walk veins that branch, clipped to {@code regions}. */
function veins(seed, regions, count, len, color) {
	const c = canvas();
	const r = rng(seed);
	for (const [u0, v0, u1, v1] of regions) {
		for (let n = 0; n < count; n++) {
			let x = u0 + Math.floor(r() * (u1 - u0));
			let y = v0 + Math.floor(r() * (v1 - v0));
			let dx = r() < 0.5 ? 0 : (r() < 0.5 ? -1 : 1);
			let dy = dx === 0 ? 1 : (r() < 0.5 ? 0 : 1);
			for (let s = 0; s < len; s++) {
				if (!inRegion(x, y, [[u0, v0, u1, v1]])) break;
				const fade = 1 - s / (len * 1.4);
				put(c, x, y, color[0], color[1], color[2], Math.round(255 * fade));
				if (r() < 0.35) { dx = r() < 0.5 ? -1 : 1; } else if (r() < 0.35) { dx = 0; }
				if (r() < 0.2) dy = r() < 0.7 ? 1 : 0;
				if (dx === 0 && dy === 0) dy = 1;
				x += dx; y += dy;
			}
		}
	}
	return c;
}

/** Jagged lightning: zigzag lines running down each part. */
function crackle(seed, regions, count) {
	const c = canvas();
	const r = rng(seed);
	for (const [u0, v0, u1, v1] of regions) {
		for (let n = 0; n < count; n++) {
			let x = u0 + Math.floor(r() * (u1 - u0));
			let y = v0;
			while (y < v1) {
				const hot = r() < 0.4;
				put(c, x, y, 255, hot ? 255 : 240, hot ? 220 : 90, 255);
				// a faint halo pixel either side
				put(c, x - 1, y, 255, 200, 40, 90);
				put(c, x + 1, y, 255, 200, 40, 90);
				y++;
				if (r() < 0.55) x += r() < 0.5 ? -1 : 1;
				if (x < u0) x = u0; if (x >= u1) x = u1 - 1;
				if (r() < 0.12) y += 2; // gaps in the arc
			}
		}
	}
	return c;
}

/** Wind streaks: short horizontal dashes with fading tails. */
function streaks(seed, regions) {
	const c = canvas();
	const r = rng(seed);
	for (const [u0, v0, u1, v1] of regions) {
		for (let y = v0; y < v1; y++) {
			if (r() < 0.45) continue;
			const len = 3 + Math.floor(r() * 6);
			const x0 = u0 + Math.floor(r() * (u1 - u0));
			for (let i = 0; i < len; i++) {
				const x = x0 + i;
				if (x >= u1) break;
				put(c, x, y, 235, 245, 255, Math.round(200 * (1 - i / len)));
			}
		}
	}
	return c;
}

/** Brushed metal plating with panel seams and rivets. Leaves the face mostly clear. */
function plating(seed) {
	const c = canvas();
	const r = rng(seed);
	for (const reg of ALL) {
		const [u0, v0, u1, v1] = reg;
		for (let y = v0; y < v1; y++) {
			for (let x = u0; x < u1; x++) {
				// the face (front of the head): only a brow band and the jaw
				if (reg === HEAD && x >= 8 && x < 16 && y >= 8 && y < 16 && !(y === 8 || y === 9 || y >= 14)) continue;
				if (reg === HEAD && y < 8 && (x < 8 || x >= 24)) continue; // unused head atlas corners
				const noise = Math.floor(r() * 14) - 7;
				const band = (y % 2 === 0) ? 6 : 0; // brushed grain
				let base = 150 + noise + band;
				const seam = ((x - u0) % 4 === 3) || ((y - v0) % 6 === 5);
				if (seam) base -= 45;
				const rivet = ((x - u0) % 4 === 1) && ((y - v0) % 6 === 1);
				if (rivet) base += 60;
				put(c, x, y, Math.max(0, Math.min(255, base - 4)), Math.max(0, Math.min(255, base)), Math.max(0, Math.min(255, base + 10)), 255);
			}
		}
	}
	return c;
}

/** Sparse glints for the Unbreakable gold sheen. */
function glint(seed) {
	const c = canvas();
	const r = rng(seed);
	for (const [u0, v0, u1, v1] of ALL) {
		const n = Math.floor((u1 - u0) * (v1 - v0) / 22);
		for (let i = 0; i < n; i++) {
			const x = u0 + Math.floor(r() * (u1 - u0));
			const y = v0 + Math.floor(r() * (v1 - v0));
			put(c, x, y, 255, 255, 255, 255);
			if (r() < 0.3) {
				put(c, x - 1, y, 255, 250, 210, 150); put(c, x + 1, y, 255, 250, 210, 150);
				put(c, x, y - 1, 255, 250, 210, 150); put(c, x, y + 1, 255, 250, 210, 150);
			}
		}
	}
	return c;
}

const write = (name, c) => { png.write(path.join(OUT, name), c); console.log('wrote', name); };
write('p01_veins.png', veins(101, [...LIMBS, [0, 8, 32, 16]], 5, 14, [255, 255, 255]));
write('p03_streaks.png', streaks(303, ALL));
write('p04_crackle_a.png', crackle(404, ALL, 2));
write('p04_crackle_b.png', crackle(4040, ALL, 2));
write('p12_veins.png', veins(1212, LIMBS, 6, 12, [255, 255, 255]));
write('p13_plating.png', plating(1313));
write('p13_glint.png', glint(1331));
