// v0.16.4 Steel model (concept A, show-accurate): geo + texture + glowmask + idle animation.
const fs = require('fs'), path = require('path');
const png = require('../pnglib.js');
const A = path.resolve(__dirname, '../../src/main/resources/assets/projecthero');
const W = 64, H = 64;
const tex = Buffer.alloc(W * H * 4), glow = Buffer.alloc(W * H * 4);
function px(buf, x, y, c) { const i = (y * W + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = c[3] === undefined ? 255 : c[3]; }
function rect(buf, x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) px(buf, x + i, y + j, c); }
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const WHITE = hex('#e6ebf0'), WHITE2 = hex('#cdd5dd'), GREY = hex('#9aa4ae'), DARK = hex('#3a414b'), DARKER = hex('#262b32'),
	CYAN = hex('#35e0ff'), CYAN2 = hex('#1aa9d6'), DEEP = hex('#0c2730');
// swatches (8x8 each, row 0)
const SW = { white: [0, 0], white2: [8, 0], grey: [16, 0], dark: [24, 0], darker: [32, 0], cyan: [40, 0], deep: [48, 0] };
rect(tex, 0, 0, 8, 8, WHITE); rect(tex, 8, 0, 8, 8, WHITE2); rect(tex, 16, 0, 8, 8, GREY); rect(tex, 24, 0, 8, 8, DARK);
rect(tex, 32, 0, 8, 8, DARKER); rect(tex, 40, 0, 8, 8, CYAN); rect(tex, 48, 0, 8, 8, DEEP);
rect(glow, 40, 0, 8, 8, [...CYAN, 255]);
// white panel with a cyan seam stripe (8x8 at 0,8)
rect(tex, 0, 8, 8, 8, WHITE); rect(tex, 5, 8, 1, 8, CYAN); rect(glow, 5, 8, 1, 8, [...CYAN, 255]);
// dark arm with a cyan strip (8x8 at 8,8)
rect(tex, 8, 8, 8, 8, DARK); rect(tex, 11, 8, 2, 8, CYAN); rect(glow, 11, 8, 2, 8, [...CYAN, 255]);
// the ring eye (16x16 at 0,16): transparent outside the outer ring
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
	const d = Math.hypot(x - 7.5, y - 7.5);
	let c = null, g = false;
	if (d > 7.6) c = null;
	else if (d > 6.2) { c = CYAN; g = true; }
	else if (d > 5.2) c = DEEP;
	else if (d > 4.4) { c = CYAN2; g = true; }
	else if (d > 3.0) c = DEEP;
	else if (d > 1.9) { c = CYAN; g = true; }
	else if (d > 0.9) c = DEEP;
	else { c = [200, 250, 255]; g = true; }
	if (c) { px(tex, x, y + 16, c); if (g) px(glow, x, y + 16, [...c, 255]); }
}
// the gap in the inner ring (the show's broken ring): a notch at the upper right
for (const [x, y] of [[10, 4], [11, 5], [10, 5], [11, 4]]) { px(tex, x, y + 16, DEEP); px(glow, x, y + 16, [0, 0, 0, 0]); }
// chin vent grille (8x8 at 16,16): cyan dots on deep
rect(tex, 16, 16, 8, 8, DEEP);
for (let y = 1; y < 8; y += 2) for (let x = 1; x < 8; x += 2) { px(tex, 16 + x, 16 + y, CYAN); px(glow, 16 + x, 16 + y, [...CYAN, 255]); }

png.write(path.join(A, 'textures/entity/steel.png'), W, H, tex);
png.write(path.join(A, 'textures/entity/steel_glowmask.png'), W, H, glow);

const OY = 6;
function faces(sw, over) {
	const f = {};
	for (const n of ['north', 'south', 'east', 'west', 'up', 'down']) {
		const s = (over && over[n]) || sw;
		f[n] = Array.isArray(s) ? { uv: s.slice(0, 2), uv_size: s.slice(2) } : { uv: SW[s], uv_size: [8, 8] };
	}
	return f;
}
const cube = (o, s, sw, over) => ({ origin: [o[0], o[1] + OY, o[2]], size: s, uv: faces(sw, over) });
const STRIPE = [0, 8, 8, 8], ARM = [8, 8, 8, 8], EYE = [0, 16, 16, 16], GRILLE = [16, 16, 8, 8];
const bones = [
	{ name: 'root', pivot: [0, 0, 0] },
	{ name: 'body', parent: 'root', pivot: [0, 26 + OY, 0], cubes: [
		cube([-4.5, 18, -4], [9, 15, 8], 'dark'),                                       // the dark core (the face shows through the hood)
		cube([-5, 30, -4.8], [10, 4.5, 9.4], 'white'),                                  // hood crown
		cube([-4.2, 34.5, -4.2], [8.4, 1.4, 8.4], 'white'),                             // crown taper
		cube([-3, 35.9, -3.2], [6, 0.9, 6.4], 'white2'),                                // dome
		cube([-0.5, 36.1, -3.6], [1, 0.8, 7], 'cyan'),                                  // crest seam (glows)
		cube([-4.2, 28.6, -5.4], [8.4, 1.6, 1], 'white'),                               // brow over the eye
		cube([4.1, 19, -5.2], [1.3, 11, 9.2], 'white', { east: STRIPE }),               // left hood side
		cube([-5.4, 19, -5.2], [1.3, 11, 9.2], 'white', { west: STRIPE }),              // right hood side
		cube([3.2, 17.5, -5.3], [1.4, 2.5, 6], 'white'),                                // hood sweeping in to the chin
		cube([-4.6, 17.5, -5.3], [1.4, 2.5, 6], 'white'),
		cube([-3.4, 16, -5], [6.8, 2, 6.4], 'dark', { north: GRILLE }),                 // jaw with the vent grille
		cube([2.2, 15.8, -5.2], [1.3, 2.4, 5], 'white'),                                // the V's two white edges
		cube([-3.5, 15.8, -5.2], [1.3, 2.4, 5], 'white'),
		cube([-2.4, 14, -4.8], [4.8, 2, 5.6], 'white'),                                 // chin
		cube([-1.4, 12.3, -4.4], [2.8, 1.8, 4.6], 'white2'),                            // chin point
		cube([-0.5, 11.7, -4.1], [1, 0.7, 1.4], 'cyan'),                                // tip light
		cube([-4, 20, 3.8], [8, 10, 1], 'darker'),                                      // back plate
	] },
	{ name: 'eye', parent: 'body', pivot: [0, 24.6 + OY, -4.3], cubes: [
		cube([-3.4, 21.2, -4.35], [6.8, 6.8, 0.3], 'deep', { north: EYE, south: 'dark', east: 'dark', west: 'dark', up: 'dark', down: 'dark' }),
	] },
	{ name: 'left_fin', parent: 'body', pivot: [5.4, 31 + OY, 0], rotation: [0, 0, -22], cubes: [
		cube([5.4, 28, -2.6], [0.9, 7, 5.2], 'white2', { east: STRIPE }),
	] },
	{ name: 'right_fin', parent: 'body', pivot: [-5.4, 31 + OY, 0], rotation: [0, 0, 22], cubes: [
		cube([-6.3, 28, -2.6], [0.9, 7, 5.2], 'white2', { west: STRIPE }),
	] },
	{ name: 'left_arm', parent: 'body', pivot: [4.6, 22 + OY, 0], rotation: [-6, 0, -8], cubes: [
		cube([4.6, 21, -1.4], [2.6, 3, 2.8], 'white'),                                  // shoulder cap
		cube([4.9, 11, -0.9], [1.8, 10.5, 1.8], 'dark', { east: ARM }),                // upper arm
	] },
	{ name: 'left_forearm', parent: 'left_arm', pivot: [5.8, 11 + OY, 0], rotation: [10, 0, 4], cubes: [
		cube([5.05, -1, -0.75], [1.5, 12.2, 1.5], 'dark', { east: ARM, north: ARM }),
		cube([4.8, -2.4, -1], [2, 1.6, 2], 'darker'),                                   // claw tip
	] },
	{ name: 'right_arm', parent: 'body', pivot: [-4.6, 22 + OY, 0], rotation: [-6, 0, 8], cubes: [
		cube([-7.2, 21, -1.4], [2.6, 3, 2.8], 'white'),
		cube([-6.7, 11, -0.9], [1.8, 10.5, 1.8], 'dark', { west: ARM }),
	] },
	{ name: 'right_forearm', parent: 'right_arm', pivot: [-5.8, 11 + OY, 0], rotation: [10, 0, -4], cubes: [
		cube([-6.55, -1, -0.75], [1.5, 12.2, 1.5], 'dark', { west: ARM, north: ARM }),
		cube([-6.8, -2.4, -1], [2, 1.6, 2], 'darker'),
	] },
];
const geo = { format_version: '1.12.0', 'minecraft:geometry': [{ description: { identifier: 'geometry.steel_entity', texture_width: W, texture_height: H,
	visible_bounds_width: 3, visible_bounds_height: 3.5, visible_bounds_offset: [0, 1.5, 0] }, bones }] };
fs.writeFileSync(path.join(A, 'geo/steel_entity.geo.json'), JSON.stringify(geo, null, '\t'));

const k = (a, b) => ({ '0.0': a, '2.0': b, '4.0': a });
const anim = { format_version: '1.8.0', animations: { 'animation.steel_entity.idle': { loop: true, animation_length: 4, bones: {
	root: { position: k([0, 0, 0], [0, 1.2, 0]) },
	body: { rotation: { '0.0': [2, 0, 0], '1.0': [0, 3, 0], '2.0': [-2, 0, 0], '3.0': [0, -3, 0], '4.0': [2, 0, 0] } },
	eye: { scale: { '0.0': [1, 1, 1], '0.6': [1.06, 1.06, 1], '1.2': [1, 1, 1], '4.0': [1, 1, 1] } },
	left_fin: { rotation: k([0, 0, 0], [0, 0, -10]) },
	right_fin: { rotation: k([0, 0, 0], [0, 0, 10]) },
	left_arm: { rotation: { '0.0': [0, 0, 0], '1.3': [7, 0, -3], '2.6': [-5, 0, 2], '4.0': [0, 0, 0] } },
	right_arm: { rotation: { '0.0': [0, 0, 0], '1.3': [-5, 0, 2], '2.6': [7, 0, -3], '4.0': [0, 0, 0] } },
	left_forearm: { rotation: { '0.0': [0, 0, 0], '1.6': [8, 0, 0], '3.2': [-4, 0, 0], '4.0': [0, 0, 0] } },
	right_forearm: { rotation: { '0.0': [0, 0, 0], '1.6': [-4, 0, 0], '3.2': [8, 0, 0], '4.0': [0, 0, 0] } },
} } } };
fs.writeFileSync(path.join(A, 'animations/steel_entity.animation.json'), JSON.stringify(anim, null, '\t'));
console.log('ok');
