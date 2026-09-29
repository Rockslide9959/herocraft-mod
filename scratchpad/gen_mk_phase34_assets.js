// Moon Knight Phase 3-4 assets (all original, procedural): the Crescent Dart blade texture, the Truncheon and Staff
// item sprites, and their item models. Run: node scratchpad/gen_mk_phase34_assets.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pnglib.js');

const ASSETS = path.join(__dirname, '../src/main/resources/assets/projecthero');
const W = 16, H = 16;

function canvas() { return { px: Buffer.alloc(W * H * 4) }; }
function set(c, x, y, rgb, a = 255) {
	if (x < 0 || y < 0 || x >= W || y >= H) return;
	const i = (y * W + x) * 4;
	c.px[i] = rgb[0]; c.px[i + 1] = rgb[1]; c.px[i + 2] = rgb[2]; c.px[i + 3] = a;
}
function opaque(c, x, y) { return x >= 0 && y >= 0 && x < W && y < H && c.px[(y * W + x) * 4 + 3] > 0; }
function outline(c, rgb) {
	const add = [];
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		if (opaque(c, x, y)) continue;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (opaque(c, x + dx, y + dy)) { add.push([x, y]); break; }
	}
	for (const [x, y] of add) set(c, x, y, rgb);
}
function write(rel, c) {
	const f = path.join(ASSETS, rel);
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, encode(W, H, c.px));
	console.log('wrote', rel);
}
const lerp = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

// ---------------------------------------------------------------- crescent dart (entity texture)
// u = along the arc, v = across: rows 0-3 the inner rim, rows 4-15 the bevel out to the sharp edge.
{
	const c = canvas();
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		let col;
		if (y < 4) col = lerp([150, 158, 172], [178, 186, 198], y / 3);
		else col = lerp([196, 202, 214], [252, 253, 255], (y - 4) / 11);
		if (y === 9 || y === 10) col = lerp(col, [170, 200, 240], 0.45); // a faint engraved moonline
		const tip = Math.min(x, 15 - x);
		if (tip === 0) col = lerp(col, [236, 240, 250], 0.5);
		set(c, x, y, col);
	}
	write('textures/entity/crescent_dart.png', c);
}

// ---------------------------------------------------------------- shared: a diagonal pole with a crescent head
function pole(c, x0, x1, colourAt) {
	// the diagonal y = 15 - x, two pixels thick; highlight on the upper-left side
	for (let x = x0; x <= x1; x++) {
		const y = 15 - x;
		set(c, x, y, colourAt(x, true));
		set(c, x + 1, y, colourAt(x, false));
	}
}
function crescent(c, cx, cy, r, ox, oy, ir) {
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const d1 = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
		const d2 = Math.hypot(x + 0.5 - (cx + ox), y + 0.5 - (cy + oy));
		if (d1 <= r && d2 > ir) set(c, x, y, lerp([214, 222, 236], [255, 255, 255], Math.max(0, 1 - d1 / r)));
	}
}
const SILVER_HI = [246, 248, 252], SILVER_LO = [178, 184, 196];
const GRIP_A = [58, 54, 62], GRIP_B = [96, 90, 100];
const GUARD = [168, 198, 236];
const OUTLINE = [34, 36, 46];

// ---------------------------------------------------------------- truncheon (item)
{
	const c = canvas();
	pole(c, 1, 10, (x, hi) => {
		if (x === 1) return [226, 230, 238];            // pommel cap
		if (x <= 5) return (x % 2 === 0) ? GRIP_A : GRIP_B; // wrapped grip
		if (x === 6) return GUARD;                      // pale-blue ring
		return hi ? SILVER_HI : SILVER_LO;              // silver shaft
	});
	crescent(c, 12.2, 3.8, 3.4, 1.5, -1.5, 2.6);
	outline(c, OUTLINE);
	write('textures/item/moon_knight_truncheon.png', c);
}

// ---------------------------------------------------------------- staff (item): the same weapon, extended
{
	const c = canvas();
	pole(c, 0, 14, (x, hi) => {
		if (x >= 6 && x <= 8) return (x % 2 === 0) ? GRIP_A : GRIP_B; // grip in the middle
		if (x === 5 || x === 9) return GUARD;
		return hi ? SILVER_HI : SILVER_LO;
	});
	crescent(c, 13.0, 3.0, 3.0, 1.4, -1.4, 2.3);
	crescent(c, 2.4, 13.6, 2.0, -1.0, 1.0, 1.5); // a small crescent counterweight at the foot
	outline(c, OUTLINE);
	write('textures/item/moon_knight_staff.png', c);
}

// ---------------------------------------------------------------- item models
function json(rel, obj) {
	const f = path.join(ASSETS, rel);
	fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n');
	console.log('wrote', rel);
}
json('models/item/moon_knight_truncheon.json', {
	parent: 'minecraft:item/handheld',
	textures: { layer0: 'projecthero:item/moon_knight_truncheon' },
	overrides: [{ predicate: { 'projecthero:staff': 1 }, model: 'projecthero:item/moon_knight_staff' }],
});
// The staff sprite has its grip in the middle, so it is scaled up around the centre: both ends reach out past the hand.
json('models/item/moon_knight_staff.json', {
	parent: 'minecraft:item/handheld',
	textures: { layer0: 'projecthero:item/moon_knight_staff' },
	display: {
		thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 1.5, 0.5], scale: [1.7, 1.7, 0.85] },
		thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 1.5, 0.5], scale: [1.7, 1.7, 0.85] },
		firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 2.2, 1.13], scale: [1.15, 1.15, 0.68] },
		firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 2.2, 1.13], scale: [1.15, 1.15, 0.68] },
	},
});
