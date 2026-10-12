// v0.16.1: the shaded brushed-metal strip for the smooth Wolverine claw mesh (client/wolverine/WolverineClawsModel).
//   node scratchpad/gen_wolverine_claws_v0161.js
// Output: src/main/resources/assets/projecthero/textures/entity/wolverine_claws.png (32 x 128)
// Layout: four 8-px columns, one per blade face, v runs root (top) -> tip (bottom):
//   0 front bevel (edge -> ridge), 1 front flat (ridge -> spine), 2 back flat (spine -> ridge), 3 back bevel (ridge -> edge)
const path = require('path');
const png = require('./pnglib.js');

const W = 32, H = 128;
const data = Buffer.alloc(W * H * 4);
let seed = 7;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
// brushed streaks: one value per texel column, slowly wandering down the blade
const streak = [];
for (let x = 0; x < W; x++) streak.push(rnd());
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

for (let y = 0; y < H; y++) {
	const v = (y + 0.5) / H; // 0 root .. 1 tip
	for (let x = 0; x < W; x++) {
		const col = Math.floor(x / 8);
		const s = ((x % 8) + 0.5) / 8; // 0 .. 1 across the face (first point -> second point)
		// distance to the cutting edge across this face (0 at the edge)
		let edgeDist;
		if (col === 0) edgeDist = s; else if (col === 3) edgeDist = 1 - s; else edgeDist = 2;
		// base adamantium: cool silver, the bevels brighter than the flats, the spine side a little darker
		let base = col === 0 || col === 3 ? 196 : 170;
		if (col === 1) base -= 22 * s; // toward the spine
		if (col === 2) base -= 22 * (1 - s);
		// the honed edge: a bright line
		if (edgeDist < 0.3) base += 60 * (1 - edgeDist / 0.3);
		// the bevel ridge: a soft highlight
		const ridge = col === 0 ? 1 - s : col === 3 ? s : col === 1 ? s : 1 - s;
		if (col === 0 || col === 3) base += 10 * Math.max(0, 1 - Math.abs(ridge - 0.0) / 0.25);
		// darker at the root (just out of the fist), polished toward the tip
		base *= 0.82 + 0.22 * Math.min(1, v * 1.4);
		// brushed streaks along the length, with a slow wobble
		const st = streak[(x + Math.floor(v * 6)) % W];
		base += (st - 0.5) * 18 + Math.sin(v * 40 + x * 1.7) * 2.5;
		const r = clamp(base * 0.96), g = clamp(base * 0.99), b = clamp(base * 1.06 + 6);
		const i = (y * W + x) * 4;
		data[i] = r; data[i + 1] = g; data[i + 2] = b; data[i + 3] = 255;
	}
}

const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'wolverine_claws.png');
png.write(out, { w: W, h: H, data });
console.log('wrote', out);
