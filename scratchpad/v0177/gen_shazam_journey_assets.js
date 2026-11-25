// v0.17.7 Shazam acquisition: the subway train's texture atlas (our own art, made here) + the Wizard spawn egg's item model.
//   node scratchpad/v0177/gen_shazam_journey_assets.js   (from the worktree root)
// Atlas: 256 x 256, a 4 x 4 grid of 64 px tiles (SubwayTrainRenderer.tile(col, row) must match):
//   row 0: side steel (red Shazam stripe under the windows) | window (rubber frame, tinted glass) | door leaf (tall window) | roof
//   row 1: floor | ceiling (light strip) | seats (orange moulded) | interior wall (cream, with an advert)
//   row 2: cab front | bogie (wheels) | pole steel | headlight glow
//   row 3: black rubber | inner end (door window) | gangway bellows | headlight beam
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const ROOT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero');
const T = 64, W = 256;
const px = Buffer.alloc(W * W * 4);
let seed = 7177;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
function set(tx, ty, x, y, c, a = 255) {
	if (x < 0 || y < 0 || x >= T || y >= T) return;
	const i = ((ty * T + y) * W + tx * T + x) * 4;
	px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = clamp(a);
}
function fill(tx, ty, f) { for (let y = 0; y < T; y++) for (let x = 0; x < T; x++) { const r = f(x, y); set(tx, ty, x, y, r[0], r[1] === undefined ? 255 : r[1]); } }
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const shade = (c, f) => c.map(v => v * f);

const STEEL = [176, 182, 188], STEEL_D = [128, 134, 142], STEEL_L = [214, 220, 226], RED = [196, 26, 34], GOLD = [236, 184, 60];
const RUBBER = [26, 26, 30], GLASS = [40, 62, 86], CREAM = [222, 210, 180], ORANGE = [228, 120, 40];

// 0,0 side steel: fine vertical corrugation, a red stripe near the top (just under the windows), a little grime low down
fill(0, 0, (x, y) => {
	let c = (x % 4 === 0) ? STEEL_D : (x % 4 === 1 ? STEEL_L : STEEL);
	c = mix(c, STEEL, 0.3);
	if (y >= 6 && y < 12) c = (y === 6 || y === 11) ? shade(RED, 0.75) : RED;
	if (y >= 13 && y < 15) c = GOLD;
	if (y > 50) c = shade(c, 1 - (y - 50) * 0.012);
	return [shade(c, 0.94 + rnd() * 0.08)];
});
// 1,0 window: rubber frame round tinted glass (translucent), a highlight streak
fill(1, 0, (x, y) => {
	const edge = Math.min(x, y, T - 1 - x, T - 1 - y);
	if (edge < 3) return [STEEL_D];
	if (edge < 6) return [RUBBER];
	let c = GLASS;
	const streak = x - y * 0.6;
	if (streak > 14 && streak < 20) c = mix(c, [190, 220, 240], 0.35);
	return [c, 120];
});
// 2,0 door leaf: steel with a tall window in the upper part and a rubber edge
fill(2, 0, (x, y) => {
	if (x < 2 || x > T - 3 || y < 2 || y > T - 3) return [RUBBER];
	if (x > 10 && x < T - 11 && y > 6 && y < 34) {
		const edge = Math.min(x - 11, y - 7, T - 12 - x, 33 - y);
		if (edge < 2) return [RUBBER];
		return [mix(GLASS, [170, 200, 220], (x - y) % 23 < 3 ? 0.3 : 0), 120];
	}
	let c = (x % 6 === 0) ? STEEL_D : STEEL;
	if (y > 44 && y < 47) c = shade(STEEL, 0.8);
	return [shade(c, 0.95 + rnd() * 0.06)];
});
// 3,0 roof: grey, ribbed, dusty
fill(3, 0, (x, y) => [shade((y % 8 < 2) ? [92, 94, 98] : [118, 120, 124], 0.9 + rnd() * 0.15)]);
// 0,1 floor: dark speckled rubber with a yellow door-strip at one edge
fill(0, 1, (x, y) => {
	let c = shade([58, 58, 62], 0.85 + rnd() * 0.3);
	if (rnd() < 0.04) c = [92, 92, 96];
	return [c];
});
// 1,1 ceiling: off-white panels with a bright light strip down the middle (drawn full bright)
fill(1, 1, (x, y) => {
	if (x >= 24 && x < 40) return [(x === 24 || x === 39) ? [200, 200, 190] : [255, 252, 232]];
	return [shade([226, 224, 214], y % 16 === 0 ? 0.85 : 1)];
});
// 2,1 seats: orange moulded bucket seats, 4 across, with dark gaps
fill(2, 1, (x, y) => {
	const s = x % 16;
	if (s === 0 || s === 15) return [shade(ORANGE, 0.55)];
	const curve = Math.abs(s - 7.5) / 7.5;
	return [shade(ORANGE, 1.0 - curve * 0.25 - (y > 52 ? 0.2 : 0))];
});
// 3,1 interior wall: cream panels, an advert card (red / gold Shazam bolt), a grab-rail shadow
fill(3, 1, (x, y) => {
	let c = shade(CREAM, 0.94 + rnd() * 0.06);
	if (x >= 10 && x < 54 && y >= 8 && y < 30) {
		c = (x === 10 || x === 53 || y === 8 || y === 29) ? [60, 50, 40] : [178, 36, 40];
		// a gold bolt on the card
		const bx = x - 32, by = y - 19;
		if ((Math.abs(bx + by * 0.5) < 2.2 && by < 2) || (Math.abs(bx - 2 + (by - 2) * 0.5) < 2.2 && by >= 0)) c = GOLD;
	}
	if (y > 56) c = shade(c, 0.8);
	return [c];
});
// 0,2 cab front: destination window, windshield, a door with a window, two headlights, two red marker lights, the red stripe
fill(0, 2, (x, y) => {
	let c = (x % 4 === 0) ? STEEL_D : STEEL;
	if (y >= 2 && y < 9 && x > 8 && x < 56) c = [18, 14, 10]; // destination sign (text drawn over it)
	if (y >= 11 && y < 30) { // windshield either side of the door
		if ((x > 3 && x < 22) || (x > 42 && x < 61)) c = mix(GLASS, [150, 180, 200], (x + y) % 19 < 3 ? 0.3 : 0.05);
	}
	if (x >= 24 && x <= 40 && y >= 10) { // the front door
		c = (x === 24 || x === 40) ? RUBBER : STEEL;
		if (y >= 13 && y < 30 && x > 27 && x < 37) c = GLASS;
	}
	if (y >= 36 && y < 42 && !(x >= 24 && x <= 40)) c = RED;
	const hl = (cx, cy) => Math.hypot(x - cx, y - cy);
	if (hl(12, 48) < 4 || hl(52, 48) < 4) c = [255, 248, 210];
	else if (hl(12, 48) < 5 || hl(52, 48) < 5) c = RUBBER;
	if (hl(6, 56) < 2 || hl(58, 56) < 2) c = [255, 60, 50];
	return [c];
});
// 1,2 bogie: dark frame with two wheels and a spring
fill(1, 2, (x, y) => {
	let c = shade([46, 44, 44], 0.9 + rnd() * 0.15);
	for (const cx of [16, 48]) {
		const d = Math.hypot(x - cx, y - 40);
		if (d < 14) c = d < 4 ? [90, 90, 92] : d > 12 ? [70, 66, 62] : [36, 34, 34];
	}
	if (y < 10) c = [56, 52, 52];
	return [c];
});
// 2,2 pole steel: polished, a bright vertical highlight
fill(2, 2, (x, y) => [mix(STEEL, [250, 250, 252], Math.max(0, 1 - Math.abs(x - 20) / 10))]);
// 3,2 headlight glow: radial white-gold, alpha falling off
fill(3, 2, (x, y) => {
	const d = Math.hypot(x - 31.5, y - 31.5) / 32;
	return [[255, 246, 214], clamp(255 * Math.max(0, 1 - d) ** 1.6)];
});
// 0,3 black rubber
fill(0, 3, (x, y) => [shade(RUBBER, 0.9 + rnd() * 0.2)]);
// 1,3 inner end: cream wall, the end door with its window
fill(1, 3, (x, y) => {
	let c = shade(CREAM, 0.92);
	if (x >= 20 && x < 44 && y >= 4) {
		c = (x === 20 || x === 43) ? RUBBER : STEEL;
		if (y >= 8 && y < 30 && x > 24 && x < 40) c = GLASS;
	}
	return [c];
});
// 2,3 gangway bellows: dark folds
fill(2, 3, (x, y) => [shade([40, 40, 44], x % 6 < 3 ? 0.8 : 1.2)]);
// 3,3 headlight beam: soft, fading along its length (u) and to the edges (v)
fill(3, 3, (x, y) => {
	const along = 1 - x / 64, across = 1 - Math.abs(y - 31.5) / 32;
	return [[255, 240, 200], clamp(90 * along * along * across)];
});

const out = path.join(ROOT, 'textures/entity/shazam/subway_train.png');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, encode(W, W, px));
console.log('wrote', out);

const egg = path.join(ROOT, 'models/item/wizard_shazam_spawn_egg.json');
fs.writeFileSync(egg, JSON.stringify({ parent: 'minecraft:item/template_spawn_egg' }, null, 1) + '\n');
console.log('wrote', egg);
