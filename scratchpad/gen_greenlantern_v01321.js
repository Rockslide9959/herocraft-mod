// v0.13.21 Green Lantern assets: the hard-light construct blocks (panel, bright panel, stairs, lamp orb), the worn
// Power Ring + hand-construct textures, a new Power Ring item icon, and the reshaped Personal Power Battery (model +
// frame / glass / core textures). Hand-rolled PNGs via scratchpad/pngkit.js (Node zlib, no Python here).
// Run from the repo root: node scratchpad/gen_greenlantern_v01321.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit.js');

const ROOT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');
const TEX = path.join(ROOT, 'textures');

function img(w, h) {
	const px = Buffer.alloc(w * h * 4);
	return {
		w, h, px,
		set(x, y, r, g, b, a = 255) {
			if (x < 0 || y < 0 || x >= w || y >= h) return;
			const i = (y * w + x) * 4;
			px[i] = clamp(r); px[i + 1] = clamp(g); px[i + 2] = clamp(b); px[i + 3] = clamp(a);
		},
		get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
	};
}
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
function save(rel, im) {
	const p = path.join(TEX, rel);
	fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, encode(im.w, im.h, im.px));
	console.log('wrote', rel);
}
function json(rel, obj) {
	const p = path.join(ROOT, rel);
	fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, JSON.stringify(obj, null, 2) + '\n');
	console.log('wrote', rel);
}
// deterministic noise
let seed = 1321;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };

// ---------------- hard-light panel textures ----------------

function hardLight(fill, rim, inner, corner, fillA, sheen) {
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const edge = Math.min(x, y, 15 - x, 15 - y);
		const isCorner = (x < 2 || x > 13) && (y < 2 || y > 13);
		if (isCorner) im.set(x, y, ...corner, 255);
		else if (edge === 0) im.set(x, y, ...rim, 235);
		else if (edge === 1) im.set(x, y, ...inner, 120);
		else {
			const d = x + y;
			const s = (d >= 9 && d <= 10) || (d >= 13 && d <= 13) ? sheen : 0;
			const n = (rnd() - 0.5) * 10;
			im.set(x, y, fill[0] + s + n, fill[1], fill[2] + s + n, fillA + s * 1.2);
		}
	}
	return im;
}
save('block/hard_light.png', hardLight([60, 236, 112], [175, 255, 195], [110, 250, 150], [235, 255, 238], 70, 26));
save('block/hard_light_bright.png', hardLight([150, 255, 140], [232, 255, 212], [190, 255, 180], [255, 255, 245], 96, 22));

// the Lantern Light orb: a radial glow with a bright rim
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5) / 7.5;
		const t = Math.min(1, d);
		const edge = Math.min(x, y, 15 - x, 15 - y) === 0;
		if (edge) im.set(x, y, 190, 255, 205, 240);
		else im.set(x, y, 255 - t * 190, 255, 240 - t * 120, 255 - t * 120);
	}
	save('block/hard_light_lamp.png', im);
}

// hand constructs (Energy Blade / Mining Drill): translucent light with scan lines
{
	const im = img(32, 32);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const scan = y % 4 === 0;
		const n = (rnd() - 0.5) * 24;
		im.set(x, y, (scan ? 170 : 90) + n, 255, (scan ? 185 : 140) + n, scan ? 200 : 150);
	}
	save('entity/green_lantern/hard_light_construct.png', im);
}

// the worn ring: rows 0-7 band metal, rows 8-15 gem
{
	const im = img(16, 16);
	for (let y = 0; y < 8; y++) for (let x = 0; x < 16; x++) {
		const shade = [[95, 175, 120], [62, 138, 88], [45, 118, 72], [36, 96, 60], [30, 82, 52], [26, 70, 45], [22, 60, 38], [18, 50, 32]][y];
		const spec = (x + y) % 5 === 0 ? 25 : 0;
		im.set(x, y, shade[0] + spec, shade[1] + spec, shade[2] + spec);
	}
	for (let y = 8; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - 1.2, y - 9.2);
		im.set(x, y, 110 + Math.max(0, 140 - d * 60), 255, 140 + Math.max(0, 110 - d * 50));
	}
	save('entity/green_lantern/power_ring_worn.png', im);
}

// ---------------- the Power Ring item icon ----------------
{
	const im = img(16, 16);
	const on = [];
	for (let y = 0; y < 16; y++) { on.push(new Array(16).fill(false)); }
	// the band, seen at three-quarters: an elliptical loop, lit from the upper left
	const cx = 7.5, cy = 10.2;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const px = x + 0.5, py = y + 0.5;
		const outer = ((px - cx - 0.5) / 6.6) ** 2 + ((py - cy) / 4.3) ** 2;
		const inner = ((px - cx - 0.5) / 4.4) ** 2 + ((py - cy) / 2.2) ** 2;
		if (outer <= 1 && inner > 1) {
			const front = py > cy;
			const lit = front ? (px < cx ? 1 : 0.7) : 0.4;
			const hi = front && py > cy + 2.4 && py < cy + 3.4 && px > 3 && px < 8;
			if (hi) im.set(x, y, 190, 252, 205);
			else im.set(x, y, 30 + 70 * lit, 95 + 105 * lit, 55 + 70 * lit);
			on[y][x] = true;
		}
	}
	// the bezel on top of the band: a 6x6 setting with its corners cut
	for (let y = 1; y <= 6; y++) for (let x = 5; x <= 10; x++) {
		if ((x === 5 || x === 10) && (y === 1 || y === 6)) continue;
		const rim = y === 1 || y === 6 || x === 5 || x === 10;
		const light = y === 1 || x === 5;
		if (rim) im.set(x, y, light ? 130 : 26, light ? 205 : 74, light ? 150 : 46);
		else im.set(x, y, 60, 255, 120);
		on[y][x] = true;
	}
	// the lantern emblem on the gem: a pale ring round a dark centre
	for (const [x, y] of [[7, 2], [8, 2], [6, 3], [9, 3], [6, 4], [9, 4], [7, 5], [8, 5]]) im.set(x, y, 215, 255, 222);
	for (const [x, y] of [[7, 3], [8, 3], [7, 4], [8, 4]]) im.set(x, y, 18, 130, 55);
// dark outline around the whole shape
	const out = [];
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (on[y][x]) continue;
		const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => on[y + dy] && on[y + dy][x + dx]);
		if (n) out.push([x, y]);
	}
	for (const [x, y] of out) im.set(x, y, 10, 38, 20);
	save('item/power_ring.png', im);
}

// ---------------- Power Battery ----------------

// frame: dark lantern-green metal with a bevel and rivets
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let r = 34, g = 84, b = 52;
		if (y === 0 || x === 0) { r = 92; g = 168; b = 116; }
		else if (y === 15 || x === 15) { r = 14; g = 38; b = 24; }
		else if (y === 7) { r = 58; g = 128; b = 82; }
		else if (y === 8) { r = 20; g = 54; b = 34; }
		const n = (rnd() - 0.5) * 8;
		im.set(x, y, r + n, g + n, b + n);
	}
	for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) { im.set(x, y, 160, 230, 175); im.set(x + 1, y + 1, 12, 34, 20); }
	save('block/power_battery_frame.png', im);
}
// glass barrel: translucent green, faint vertical streaks, brighter top/bottom rim
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const rim = y === 0 || y === 15;
		const streak = x === 3 || x === 4 || x === 11;
		if (rim) im.set(x, y, 150, 255, 175, 210);
		else im.set(x, y, streak ? 150 : 70, 250, streak ? 170 : 125, streak ? 120 : 70);
	}
	save('block/power_battery.png', im);
}
// core: blazing green with the lantern emblem (a ring between two bars)
{
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5);
		const bar = (y === 1 || y === 2 || y === 13 || y === 14) && x >= 3 && x <= 12;
		const ring = d >= 3.6 && d <= 5.4 && y >= 3 && y <= 12;
		if (bar || ring) im.set(x, y, 235, 255, 238);
		else im.set(x, y, 60 + Math.max(0, 60 - d * 10), 245, 110);
	}
	save('block/power_battery_core.png', im);
}
// block model: base plates, a glass barrel with a glowing core, corner posts, a domed cap and a carrying handle
{
	const face = (tex, uv) => ({ texture: tex, uv });
	function box(name, from, to, tex, opts = {}) {
		const [x0, y0, z0] = from, [x1, y1, z1] = to;
		const w = x1 - x0, h = y1 - y0, d = z1 - z0;
		const full = opts.full;
		// thin parts sample the middle of the frame texture, away from its bright bevel edge
		const uvWH = (a, b) => full ? [0, 0, 16, 16] : [2, 2, 2 + Math.min(12, a), 2 + Math.min(12, b)];
		return {
			name, from, to,
			faces: {
				down: face(tex, uvWH(w, d)), up: face(tex, uvWH(w, d)),
				north: face(tex, uvWH(w, h)), south: face(tex, uvWH(w, h)),
				west: face(tex, uvWH(d, h)), east: face(tex, uvWH(d, h)),
			},
		};
	}
	const F = '#frame', G = '#glass', C = '#core';
	json('models/block/power_battery.json', {
		__comment: 'v0.13.21: reshaped again into a proper Green Lantern power battery -- base plates, a glass barrel with a glowing emblem core, four corner posts, a domed cap and a carrying handle (scratchpad/gen_greenlantern_v01321.js).',
		parent: 'minecraft:block/block',
		ambientocclusion: false,
		textures: {
			frame: 'projecthero:block/power_battery_frame',
			glass: 'projecthero:block/power_battery',
			core: 'projecthero:block/power_battery_core',
			particle: 'projecthero:block/power_battery_frame',
		},
		elements: [
			box('base', [2, 0, 2], [14, 1, 14], F),
			box('base_step', [3, 1, 3], [13, 2, 13], F),
			box('core', [5.5, 3.5, 5.5], [10.5, 9.5, 10.5], C, { full: true }),
			box('glass', [3.5, 2, 3.5], [12.5, 11, 12.5], G, { full: true }),
			box('post_nw', [3, 2, 3], [4.5, 11, 4.5], F),
			box('post_ne', [11.5, 2, 3], [13, 11, 4.5], F),
			box('post_sw', [3, 2, 11.5], [4.5, 11, 13], F),
			box('post_se', [11.5, 2, 11.5], [13, 11, 13], F),
			box('top_ring', [3, 11, 3], [13, 12, 13], F),
			box('cap', [4, 12, 4], [12, 13.5, 12], F),
			box('dome', [5.5, 13.5, 5.5], [10.5, 14.5, 10.5], F),
			box('handle_left', [4.5, 13.5, 7.5], [5.5, 17, 8.5], F),
			box('handle_right', [10.5, 13.5, 7.5], [11.5, 17, 8.5], F),
			box('handle_top', [4.5, 17, 7.5], [11.5, 18, 8.5], F),
		],
	});
}

// ---------------- hard-light block models + blockstates ----------------

json('models/block/hard_light.json', { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/hard_light' } });
json('models/block/hard_light_bright.json', { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/hard_light_bright' } });
for (const [suffix, parent] of [['', 'stairs'], ['_inner', 'inner_stairs'], ['_outer', 'outer_stairs']]) {
	json(`models/block/hard_light_stairs${suffix}.json`, {
		parent: `minecraft:block/${parent}`,
		textures: { bottom: 'projecthero:block/hard_light', top: 'projecthero:block/hard_light', side: 'projecthero:block/hard_light' },
	});
}
json('models/block/hard_light_lamp.json', {
	textures: { orb: 'projecthero:block/hard_light_lamp', core: 'projecthero:block/hard_light_bright', particle: 'projecthero:block/hard_light_lamp' },
	elements: [
		{ from: [6.5, 6.5, 6.5], to: [9.5, 9.5, 9.5], faces: Object.fromEntries(['down', 'up', 'north', 'south', 'west', 'east'].map(f => [f, { texture: '#core', uv: [6, 6, 10, 10] }])) },
		{ from: [5, 5, 5], to: [11, 11, 11], faces: Object.fromEntries(['down', 'up', 'north', 'south', 'west', 'east'].map(f => [f, { texture: '#orb', uv: [0, 0, 16, 16] }])) },
	],
});
json('blockstates/hard_light.json', { variants: {
	'bright=false': { model: 'projecthero:block/hard_light' },
	'bright=true': { model: 'projecthero:block/hard_light_bright' },
} });
json('blockstates/hard_light_lamp.json', { variants: { '': { model: 'projecthero:block/hard_light_lamp' } } });
{
	// vanilla's stair blockstate table (BlockModelGenerators#createStairs)
	const S = 'projecthero:block/hard_light_stairs', I = S + '_inner', O = S + '_outer';
	const rows = [
		['east', 'bottom', 'straight', S], ['west', 'bottom', 'straight', S, 0, 180], ['south', 'bottom', 'straight', S, 0, 90], ['north', 'bottom', 'straight', S, 0, 270],
		['east', 'bottom', 'outer_right', O], ['west', 'bottom', 'outer_right', O, 0, 180], ['south', 'bottom', 'outer_right', O, 0, 90], ['north', 'bottom', 'outer_right', O, 0, 270],
		['east', 'bottom', 'outer_left', O, 0, 270], ['west', 'bottom', 'outer_left', O, 0, 90], ['south', 'bottom', 'outer_left', O], ['north', 'bottom', 'outer_left', O, 0, 180],
		['east', 'bottom', 'inner_right', I], ['west', 'bottom', 'inner_right', I, 0, 180], ['south', 'bottom', 'inner_right', I, 0, 90], ['north', 'bottom', 'inner_right', I, 0, 270],
		['east', 'bottom', 'inner_left', I, 0, 270], ['west', 'bottom', 'inner_left', I, 0, 90], ['south', 'bottom', 'inner_left', I], ['north', 'bottom', 'inner_left', I, 0, 180],
		['east', 'top', 'straight', S, 180], ['west', 'top', 'straight', S, 180, 180], ['south', 'top', 'straight', S, 180, 90], ['north', 'top', 'straight', S, 180, 270],
		['east', 'top', 'outer_right', O, 180, 90], ['west', 'top', 'outer_right', O, 180, 270], ['south', 'top', 'outer_right', O, 180, 180], ['north', 'top', 'outer_right', O, 180],
		['east', 'top', 'outer_left', O, 180, 180], ['west', 'top', 'outer_left', O, 180], ['south', 'top', 'outer_left', O, 180, 270], ['north', 'top', 'outer_left', O, 180, 90],
		['east', 'top', 'inner_right', I, 180, 90], ['west', 'top', 'inner_right', I, 180, 270], ['south', 'top', 'inner_right', I, 180, 180], ['north', 'top', 'inner_right', I, 180],
		['east', 'top', 'inner_left', I, 180, 180], ['west', 'top', 'inner_left', I, 180], ['south', 'top', 'inner_left', I, 180, 270], ['north', 'top', 'inner_left', I, 180, 90],
	];
	const variants = {};
	for (const [facing, half, shape, model, x = 0, y = 0] of rows) {
		const v = { model };
		if (x) v.x = x;
		if (y) v.y = y;
		if (x || y) v.uvlock = true;
		variants[`facing=${facing},half=${half},shape=${shape}`] = v;
	}
	json('blockstates/hard_light_stairs.json', { variants });
}
