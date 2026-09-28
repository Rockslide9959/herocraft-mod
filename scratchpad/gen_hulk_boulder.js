// v0.13.14: the chunk of earth the Hulk tears out of the ground (Shift+V) -- a lumpy GeckoLib model of packed dirt,
// stone and roots with a grassy top, plus its tumble animation.
//   geo/hulk_boulder.geo.json, animations/hulk_boulder.animation.json, textures/entity/hulk_boulder.png (64x64)
// Run from the repo root:  node scratchpad/gen_hulk_boulder.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit');

const ROOT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/');

// ---- texture: every cube uses box UV; the whole sheet is dirt/stone/root noise, and the top-face rows are grass ----
let seed = 90210;
const rand = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const W = 64, H = 64;
const px = Buffer.alloc(W * H * 4);
function put(x, y, r, g, b) { const i = (y * W + x) * 4; px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = 255; }
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const n = rand();
	if (n < 0.18) { const v = 105 + rand() * 30; put(x, y, v, v, v + 4); }          // stone
	else if (n < 0.24) { put(x, y, 70 + rand() * 20, 50, 30); }                        // roots
	else { const v = rand() * 22; put(x, y, 112 + v, 80 + v * 0.8, 52 + v * 0.5); }  // dirt
}
// cubes and their UV origins (box UV: a cube of size w,h,d at (u,v) uses (2d+2w) x (d+h))
// The model is ~1.2 blocks across; HulkBoulderRenderer draws it at 1.7x so it suits a 3.2-block Hulk (box UV needs the
// cube sizes unscaled here).
const cubes = [
	{ origin: [-7, 0, -7], size: [14, 12, 14], uv: [0, 0] },
	{ origin: [-5, 10, -5], size: [10, 5, 10], uv: [0, 26] },
	{ origin: [5, 3, -4], size: [4, 7, 8], uv: [40, 26] },
	{ origin: [-9, 2, -3], size: [4, 6, 7], uv: [40, 41] },
	{ origin: [-4, -2, 5], size: [8, 6, 4], uv: [0, 41] },
	{ origin: [-3, -3, -8], size: [7, 5, 3], uv: [24, 41] },
];
// grass on the top faces (box UV top face: (u+d, v) .. (u+d+w, v+d))
for (const c of cubes.slice(0, 2)) {
	const [w, , d] = c.size, [u, v] = c.uv;
	for (let y = v; y < v + d; y++) for (let x = u + d; x < u + d + w; x++) {
		const g = rand() * 25; put(x, y, 70 + g * 0.5, 130 + g, 50 + g * 0.3);
	}
}
fs.mkdirSync(ROOT + 'textures/entity', { recursive: true });
fs.writeFileSync(ROOT + 'textures/entity/hulk_boulder.png', encode(W, H, px));

// ---- geometry: centred on the entity origin ----
fs.writeFileSync(ROOT + 'geo/hulk_boulder.geo.json', JSON.stringify({
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.hulk_boulder', texture_width: W, texture_height: H, visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 0.6, 0] },
		bones: [{ name: 'boulder', pivot: [0, 6, 0], cubes }],
	}],
}, null, 1));

// ---- tumble: spins end over end while it flies ----
fs.writeFileSync(ROOT + 'animations/hulk_boulder.animation.json', JSON.stringify({
	format_version: '1.8.0',
	animations: {
		'animation.hulk_boulder.tumble': {
			loop: true, animation_length: 1.0,
			bones: { boulder: { rotation: { '0.0': [0, 0, 0], '0.5': [180, 40, 0], '1.0': [360, 80, 0] } } },
		},
	},
}, null, 1));
console.log('wrote hulk_boulder geo, animation, texture');
