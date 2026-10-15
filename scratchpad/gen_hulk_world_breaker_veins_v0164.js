// v0.16.4 (user): World Breaker's glowing green veins -- an emissive overlay on hulk.png's own UV layout.
// Branching random-walk veins, kept to the Hulk's green skin (so they never run over his shorts or hair).
// Run from the repo root: node scratchpad/gen_hulk_world_breaker_veins_v0164.js
const path = require('path');
const png = require('./pnglib.js');
const TEX = path.join(__dirname, '../src/main/resources/assets/projecthero/textures/entity');
const skin = png.read(path.join(TEX, 'hulk.png'));
const W = skin.w, H = skin.h;
const out = { w: W, h: H, data: Buffer.alloc(W * H * 4) };

function isSkin(x, y) {
	if (x < 0 || y < 0 || x >= W || y >= H) return false;
	const i = (y * W + x) * 4;
	const r = skin.data[i], g = skin.data[i + 1], b = skin.data[i + 2], a = skin.data[i + 3];
	return a > 0 && g > 50 && g >= r * 1.02 && g > b * 1.3;
}
function put(x, y, r, g, b) {
	if (!isSkin(x, y)) return;
	const i = (y * W + x) * 4;
	// keep the brightest write (a core over a halo)
	if (out.data[i + 1] >= g) return;
	out.data[i] = r; out.data[i + 1] = g; out.data[i + 2] = b; out.data[i + 3] = 255;
}
let seed = 0x5eed;
function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }

function vein(x, y, dir, len, depth) {
	let fx = x + 0.5, fy = y + 0.5;
	for (let i = 0; i < len; i++) {
		const px = Math.floor(fx), py = Math.floor(fy);
		if (!isSkin(px, py)) break;
		put(px, py, 120, 255, 70); // the bright core
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) put(px + dx, py + dy, 6, 34, 4); // a faint halo
		dir += (rnd() - 0.5) * 0.9;
		fx += Math.cos(dir);
		fy += Math.sin(dir);
		if (depth < 2 && rnd() < 0.12) vein(Math.floor(fx), Math.floor(fy), dir + (rnd() < 0.5 ? 1 : -1) * (0.6 + rnd() * 0.6), Math.floor(len * 0.5), depth + 1);
	}
}

function near(x, y) { for (let dy = -3; dy <= 3; dy++) for (let dx = -3; dx <= 3; dx++) { const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < W && yy < H && out.data[(yy * W + xx) * 4 + 3]) return true; } return false; }
// seeds: scatter starting points over the skin, mostly running "down the limb" (+y in each face)
let skinPixels = [];
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (isSkin(x, y)) skinPixels.push([x, y]);
const count = Math.round(skinPixels.length / 40);
for (let k = 0; k < count; k++) {
	let x, y, tries = 0;
	do { [x, y] = skinPixels[Math.floor(rnd() * skinPixels.length)]; tries++; } while (tries < 20 && near(x, y));
	const dir = Math.PI / 2 + (rnd() - 0.5) * 1.6;
	vein(x, y, dir, 6 + Math.floor(rnd() * 8), 0);
}
png.write(path.join(TEX, 'hulk_world_breaker_veins.png'), out);
let lit = 0;
for (let i = 0; i < W * H; i++) if (out.data[i * 4 + 3]) lit++;
console.log('skin px', skinPixels.length, 'veins', count, 'lit px', lit);
// preview (x8, on dark grey) next to the skin for a quick look
const prev = png.upscale(out, 8, [20, 20, 20]);
png.write(path.join(__dirname, 'hulk_world_breaker_veins_preview.png'), prev);
