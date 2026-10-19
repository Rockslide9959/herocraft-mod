// v0.16.7 (user): the seven craftable Quirk hero suits -- 16x16 item icons (an armour-piece silhouette filled from the
// suit's own costume skin, so each icon is in that suit's colours), item models, shaped recipes (the vanilla armour
// shapes: iron + two themed items) and recipe-unlock advancements.
// Run from the worktree root: node scratchpad/quirk_suits/gen_quirk_suits.js
'use strict';
const T = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');
const fs = require('fs');
const R = 'src/main/resources/';
const SKIN = R + 'assets/projecthero/textures/entity/quirk/';

// suit id, quirk id, A (main), B (accent) -- keep in sync with QuirkSuits.java
const SUITS = [
	['endeavor', 'hellflame', 'minecraft:blaze_powder', 'minecraft:magma_cream'],
	['overhaul', 'overhaul', 'minecraft:green_wool', 'minecraft:bone'],
	['ingenium', 'engine', 'minecraft:quartz', 'minecraft:blue_dye'],
	['tsukuyomi', 'dark_shadow', 'minecraft:black_wool', 'minecraft:phantom_membrane'],
	['eraser_head', 'erasure', 'minecraft:black_wool', 'minecraft:string'],
	['ground_zero', 'explosion', 'minecraft:gunpowder', 'minecraft:orange_dye'],
	['uravity', 'zero_gravity', 'minecraft:pink_wool', 'minecraft:white_wool'],
];

// the vanilla armour shapes, I = iron ingot
const PATTERNS = {
	helmet: ['IAI', 'B B'],
	chestplate: ['I I', 'ABA', 'III'],
	leggings: ['IAI', 'B B', 'I I'],
	boots: ['B B', 'I I'],
};

const MASKS = {
	helmet: [
		'................',
		'................',
		'................',
		'....########....',
		'...##########...',
		'..############..',
		'..############..',
		'..############..',
		'..####....####..',
		'..###......###..',
		'..###......###..',
		'..###......###..',
		'................',
		'................',
		'................',
		'................',
	],
	chestplate: [
		'................',
		'..####....####..',
		'.#####....#####.',
		'.##############.',
		'.##############.',
		'.##############.',
		'.###########.##.',
		'..############..',
		'...##########...',
		'...##########...',
		'...##########...',
		'...##########...',
		'...##########...',
		'...##########...',
		'................',
		'................',
	],
	leggings: [
		'................',
		'..############..',
		'..############..',
		'..############..',
		'..#####..#####..',
		'..#####..#####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'................',
		'................',
	],
	boots: [
		'................',
		'................',
		'................',
		'................',
		'................',
		'................',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'..####....####..',
		'.#####....#####.',
		'######....######',
		'######....######',
		'................',
		'................',
		'................',
	],
};

// where each icon samples the skin: a list of [u, v, w, h] front faces laid side by side (base, overlay on top)
const FRONT = {
	helmet: { base: [[8, 8, 8, 8]], over: [[40, 8, 8, 8]] },
	chestplate: { base: [[44, 20, 4, 12], [20, 20, 8, 12], [36, 52, 4, 12]], over: [[44, 36, 4, 12], [20, 36, 8, 12], [52, 52, 4, 12]] },
	leggings: { base: [[4, 20, 4, 9], [20, 52, 4, 9]], over: [[4, 36, 4, 9], [4, 52, 4, 9]] },
	boots: { base: [[4, 27, 4, 5], [20, 59, 4, 5]], over: [[4, 43, 4, 5], [4, 59, 4, 5]] },
};

function px(img, x, y) { const i = (y * img.w + x) * 4; return [img.px[i], img.px[i + 1], img.px[i + 2], img.px[i + 3]]; }

function strip(img, rects) { // rects side by side -> {w, h, get(x, y)}
	const w = rects.reduce((s, r) => s + r[2], 0), h = Math.max(...rects.map(r => r[3]));
	return { w, h, get(x, y) { let ox = 0; for (const r of rects) { if (x < ox + r[2]) return px(img, r[0] + x - ox, r[1] + Math.min(y, r[3] - 1)); ox += r[2]; } return [0, 0, 0, 0]; } };
}

function dominant(img, rects) {
	const counts = new Map();
	for (const r of rects) for (let y = r[1]; y < r[1] + r[3]; y++) for (let x = r[0]; x < r[0] + r[2]; x++) {
		const p = px(img, x, y); if (p[3] < 128) continue; const k = ((p[0] >> 4) << 8) | ((p[1] >> 4) << 4) | (p[2] >> 4);
		const e = counts.get(k) || { n: 0, c: p }; e.n++; counts.set(k, e);
	}
	let best = null; for (const e of counts.values()) if (!best || e.n > best.n) best = e;
	return best ? best.c : null;
}

function icon(img, piece) {
	const mask = MASKS[piece], f = FRONT[piece];
	const base = strip(img, f.base), over = strip(img, f.over);
	let dom = (piece === 'helmet' ? dominant(img, [[0, 0, 64, 16]]) : dominant(img, f.base.concat(f.over))) || dominant(img, [[20, 20, 8, 12]]) || [120, 120, 120, 255];
	let x0 = 16, x1 = -1, y0 = 16, y1 = -1;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (mask[y][x] === '#') { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
	const out = Buffer.alloc(16 * 16 * 4);
	const at = (x, y) => x >= 0 && y >= 0 && x < 16 && y < 16 && mask[y][x] === '#';
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (!at(x, y)) continue;
		const sx = Math.min(base.w - 1, Math.floor((x - x0) * base.w / (x1 - x0 + 1)));
		const sy = Math.min(base.h - 1, Math.floor((y - y0) * base.h / (y1 - y0 + 1)));
		let c = base.get(sx, sy);
		if (c[3] < 128) c = dom;
		const o = over.get(sx, sy);
		if (o[3] >= 128) c = o;
		let k = 1;
		const edge = !at(x - 1, y) || !at(x + 1, y) || !at(x, y - 1) || !at(x, y + 1);
		if (edge) k = 0.55; else if (!at(x, y - 2) || !at(x - 2, y)) k = 1.12;
		const i = (y * 16 + x) * 4;
		for (let ch = 0; ch < 3; ch++) out[i + ch] = Math.max(0, Math.min(255, Math.round(c[ch] * k + (edge ? 6 : 0))));
		out[i + 3] = 255;
	}
	return out;
}

const preview = Buffer.alloc(16 * 4 * 8 * 16 * SUITS.length * 8 * 4);
const PW = 16 * 4 * 8, PH = 16 * SUITS.length * 8;
SUITS.forEach(([suit, quirk, A, B], row) => {
	const img = T.decode(SKIN + quirk + '_wide.png');
	Object.keys(MASKS).forEach((piece, col) => {
		const id = suit + '_suit_' + piece;
		const data = icon(img, piece);
		fs.writeFileSync(R + 'assets/projecthero/textures/item/' + id + '.png', T.encode(16, 16, data));
		fs.writeFileSync(R + 'assets/projecthero/models/item/' + id + '.json',
			JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } }, null, 2) + '\n');
		const all = { I: { item: 'minecraft:iron_ingot' }, A: { item: A }, B: { item: B } };
		const key = Object.fromEntries(Object.entries(all).filter(([k]) => PATTERNS[piece].join('').includes(k)));
		fs.writeFileSync(R + 'data/projecthero/recipe/' + id + '.json', JSON.stringify({
			type: 'minecraft:crafting_shaped', category: 'equipment', pattern: PATTERNS[piece], key,
			result: { id: 'projecthero:' + id, count: 1 },
		}, null, 2) + '\n');
		fs.writeFileSync(R + 'data/projecthero/advancement/recipes/' + id + '.json', JSON.stringify({
			parent: 'minecraft:recipes/root',
			criteria: {
				has_accent: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: [A, B] }] } },
				has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:' + id } },
			},
			requirements: [['has_accent', 'has_the_recipe']],
			rewards: { recipes: ['projecthero:' + id] },
		}, null, 2) + '\n');
		for (let y = 0; y < 16 * 8; y++) for (let x = 0; x < 16 * 8; x++) {
			const s = ((y >> 3) * 16 + (x >> 3)) * 4, X = col * 128 + x, Y = row * 128 + y, d = (Y * PW + X) * 4;
			const chk = ((X >> 4) + (Y >> 4)) & 1 ? 200 : 170, a = data[s + 3] / 255;
			for (let ch = 0; ch < 3; ch++) preview[d + ch] = Math.round(data[s + ch] * a + chk * (1 - a));
			preview[d + 3] = 255;
		}
	});
});
if (process.argv[2]) fs.writeFileSync(process.argv[2], T.encode(PW, PH, preview));
console.log('ok');
