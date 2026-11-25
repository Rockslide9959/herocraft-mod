// v0.17.7 Thor acquisition: block/item textures + models, blockstate, loot table, recipes, tags.
//   - Star Forge (block): blackstone-and-netherite body, a gold-banded rim and a star lens on top (cold / lit)
//   - Living Branch (item): a gnarled green branch with a leaf and glowing sap veins
//   - Stormbreaker Head (item, id unforged_stormbreaker): the crescent axe head and hammer poll, no haft
// Usage (repo root): node scratchpad/v0177/gen_thor_acquire_assets.js
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const R = path.join(__dirname, '..', '..', 'src/main/resources');
const A = path.join(R, 'assets/projecthero');
const D = path.join(R, 'data');
const json = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 2) + '\n'); };

let seed = 1717;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const shade = (c, f) => c.map(v => clamp(v * f));
function img(w, h) {
	const px = Buffer.alloc(w * h * 4);
	return {
		w, h, px,
		set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = a; },
		get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
		save(p) { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, encode(w, h, px)); },
	};
}

const BLACK = [30, 28, 34], BLACKSTONE = [52, 46, 54], NETHERITE = [72, 64, 68], GOLD = [222, 172, 60], GOLD_D = [150, 104, 34],
	HOT = [255, 236, 170], ORANGE = [255, 140, 40], BLUE = [150, 210, 255];

// ---------------- Star Forge side: blackstone bricks round a netherite core band, a gold rim at the top
{
	const t = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = mix(BLACKSTONE, BLACK, rnd() * 0.5);
		// brick courses
		const row = Math.floor(y / 4);
		const off = row % 2 ? 4 : 0;
		if (y % 4 === 3 || (x + off) % 8 === 7) c = shade(c, 0.6);
		if (y >= 5 && y <= 10) {
			// netherite core band with vertical ribs and a glowing slit
			c = mix(NETHERITE, [40, 36, 40], rnd() * 0.4);
			if (x % 3 === 0) c = shade(c, 0.7);
			if (y === 5) c = mix(c, [150, 140, 140], 0.5);
			if (y === 10) c = shade(c, 0.6);
			if (y >= 7 && y <= 8 && x >= 5 && x <= 10) c = mix(ORANGE, HOT, (x - 5) % 2 ? 0.3 : 0);
		}
		if (y <= 1) c = y === 0 ? mix(GOLD, [255, 240, 190], 0.3) : GOLD_D;
		if (y === 15) c = shade(c, 0.7);
		t.set(x, y, c);
	}
	t.save(path.join(A, 'textures/block/star_forge_side.png'));
}
// ---------------- top: a gold ring round a star lens (cold: dark glass with a faint star; lit: white-hot)
for (const lit of [false, true]) {
	const t = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x - 7.5, dy = y - 7.5, d = Math.hypot(dx, dy);
		let c = mix(BLACKSTONE, BLACK, rnd() * 0.5);
		if (x === 0 || y === 0 || x === 15 || y === 15) c = GOLD_D;
		if (d > 5.2 && d < 6.6) c = mix(GOLD, [255, 236, 180], (dx + dy < 0) ? 0.3 : 0);
		if (d <= 5.2) {
			const a = Math.atan2(dy, dx);
			const star = Math.abs(Math.cos(a * 4)) ** 6 * (1 - d / 5.2);
			if (lit) {
				c = mix(mix(ORANGE, HOT, 1 - d / 5.2), [255, 255, 255], star * 1.5);
			} else {
				c = mix([22, 20, 30], [70, 80, 120], star * 1.6 + (d < 1.2 ? 0.5 : 0));
			}
		}
		t.set(x, y, c);
	}
	t.save(path.join(A, `textures/block/star_forge_top${lit ? '_lit' : ''}.png`));
}
// ---------------- bottom: plain polished blackstone
{
	const t = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = mix(BLACKSTONE, BLACK, rnd() * 0.4);
		if (x === 0 || y === 0) c = shade(c, 1.2);
		if (x === 15 || y === 15) c = shade(c, 0.7);
		t.set(x, y, c);
	}
	t.save(path.join(A, 'textures/block/star_forge_bottom.png'));
}

// ---------------- Living Branch: a diagonal gnarled branch (bottom-left to top-right), a knot, two leaves, glowing sap veins
{
	const t = img(16, 16);
	const BARK = [96, 70, 40], BARK_D = [60, 42, 24], LEAF = [70, 190, 70], LEAF_D = [36, 120, 44], SAP = [150, 255, 160];
	// the branch: a thick diagonal with a slight S
	for (let i = 0; i <= 13; i++) {
		const x = 1 + i, y = 14 - i + Math.round(Math.sin(i * 0.7) * 0.8);
		for (const [ox, oy] of [[0, 0], [1, 0], [0, -1]]) {
			const edge = ox === 1 || oy === -1;
			t.set(x + ox, y + oy, edge ? BARK_D : mix(BARK, BARK_D, rnd() * 0.3));
		}
		if (i % 3 === 1) t.set(x, y, SAP); // the glowing sap line along the grain
	}
	t.set(6, 8, BARK_D); t.set(6, 9, BARK); t.set(5, 9, BARK_D); // a knot
	// a side twig with a leaf
	for (const [x, y] of [[9, 7], [9, 6], [8, 5]]) t.set(x, y, BARK);
	for (const [x, y, c] of [[7, 3, LEAF], [8, 3, LEAF], [7, 4, LEAF_D], [6, 4, LEAF], [8, 4, LEAF], [9, 4, LEAF_D], [7, 5, LEAF_D]]) t.set(x, y, c);
	// a leaf at the tip
	for (const [x, y, c] of [[14, 2, LEAF], [15, 1, LEAF], [15, 2, LEAF_D], [14, 1, LEAF], [13, 1, LEAF_D]]) t.set(x, y, c);
	t.set(14, 1, [190, 255, 190]);
	t.save(path.join(A, 'textures/item/living_branch.png'));
}

// ---------------- Stormbreaker Head: crescent blade on the left, hammer poll on the right, a socket where the haft goes
{
	const t = img(16, 16);
	const URU = [88, 92, 104], URU_D = [52, 54, 64], EDGE = [215, 222, 235];
	// socket block in the middle
	for (let y = 5; y <= 10; y++) for (let x = 7; x <= 10; x++) t.set(x, y, (x === 7 || y === 5) ? mix(URU, EDGE, 0.3) : (x === 10 || y === 10) ? URU_D : URU);
	for (let x = 8; x <= 9; x++) t.set(x, 11, [26, 24, 28]); // empty socket hole underneath
	t.set(8, 7, BLUE); t.set(9, 8, BLUE); // rune
	// crescent blade to the left: rows widening toward the edge, curved
	for (let y = 2; y <= 13; y++) {
		const k = Math.abs(y - 7.5) / 5.5;
		const left = Math.round(1 + k * k * 3);
		for (let x = left; x <= 6; x++) {
			let c = x === left ? EDGE : x === left + 1 ? mix(URU, EDGE, 0.4) : mix(URU, URU_D, (x - left) / 6);
			if (y === 2 || y === 13) c = shade(c, 0.85);
			if (x >= 4 && x <= 5 && y >= 6 && y <= 9 && (x + y) % 2 === 0) c = BLUE; // rune strip
			t.set(x, y, c);
		}
	}
	// hammer poll to the right
	for (let y = 6; y <= 9; y++) for (let x = 11; x <= 14; x++) t.set(x, y, x === 14 ? mix(URU, EDGE, 0.5) : y === 6 ? mix(URU, EDGE, 0.2) : y === 9 ? URU_D : URU);
	// gold collar bands
	for (let y = 5; y <= 10; y++) { t.set(6, y, y % 2 ? GOLD : GOLD_D); t.set(11, y, y % 2 ? GOLD_D : GOLD); }
	t.save(path.join(A, 'textures/item/unforged_stormbreaker.png'));
}

// ---------------- models / blockstate
json(path.join(A, 'blockstates/star_forge.json'), { variants: {
	'heat=0': { model: 'projecthero:block/star_forge' },
	'heat=1': { model: 'projecthero:block/star_forge_lit' },
	'heat=2': { model: 'projecthero:block/star_forge_lit' } } });
json(path.join(A, 'models/block/star_forge.json'), { parent: 'minecraft:block/cube_bottom_top', textures: {
	top: 'projecthero:block/star_forge_top', side: 'projecthero:block/star_forge_side', bottom: 'projecthero:block/star_forge_bottom' } });
json(path.join(A, 'models/block/star_forge_lit.json'), { parent: 'minecraft:block/cube_bottom_top', textures: {
	top: 'projecthero:block/star_forge_top_lit', side: 'projecthero:block/star_forge_side', bottom: 'projecthero:block/star_forge_bottom' } });
json(path.join(A, 'models/item/star_forge.json'), { parent: 'projecthero:block/star_forge' });
json(path.join(A, 'models/item/living_branch.json'), { parent: 'minecraft:item/handheld', textures: { layer0: 'projecthero:item/living_branch' } });
json(path.join(A, 'models/item/unforged_stormbreaker.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/unforged_stormbreaker' } });
json(path.join(A, 'models/item/stormwarden_spawn_egg.json'), { parent: 'minecraft:item/template_spawn_egg' });

// ---------------- loot table, recipes
json(path.join(D, 'projecthero/loot_table/blocks/star_forge.json'), { type: 'minecraft:block', pools: [{ rolls: 1,
	entries: [{ type: 'minecraft:item', name: 'projecthero:star_forge' }], conditions: [{ condition: 'minecraft:survives_explosion' }] }] });
json(path.join(D, 'projecthero/recipe/star_forge.json'), { type: 'minecraft:crafting_shaped', category: 'misc',
	pattern: ['GLG', 'BNB', 'BFB'],
	key: { G: { item: 'minecraft:gilded_blackstone' }, L: { item: 'minecraft:lightning_rod' }, B: { item: 'minecraft:polished_blackstone_bricks' },
		N: { item: 'minecraft:netherite_ingot' }, F: { item: 'minecraft:blast_furnace' } },
	result: { id: 'projecthero:star_forge', count: 1 } });
// v0.17.7: the head is crafted WITHOUT a handle (the blaze rods are gone): netherite round a nether star
json(path.join(D, 'projecthero/recipe/unforged_stormbreaker.json'), { type: 'minecraft:crafting_shaped', category: 'equipment',
	pattern: ['NSN', 'N N'],
	key: { N: { item: 'minecraft:netherite_ingot' }, S: { item: 'minecraft:nether_star' } },
	result: { id: 'projecthero:unforged_stormbreaker', count: 1 } });

// ---------------- tags (append, never rewrite other entries)
function tagAdd(file, id) {
	const p = path.join(D, file);
	const raw = fs.readFileSync(p, 'utf8');
	const j = JSON.parse(raw);
	if (j.values.includes(id)) return;
	const eol = raw.includes('\r\n') ? '\r\n' : '\n';
	const lines = raw.split(/\r?\n/);
	const close = lines.findIndex(l => l.trim() === ']');
	let prev = close - 1;
	lines[prev] = lines[prev].replace(/\s*$/, '') + ',';
	const indent = lines[prev].match(/^\s*/)[0];
	lines.splice(close, 0, `${indent}${JSON.stringify(id)}`);
	const out = lines.join(eol);
	JSON.parse(out);
	fs.writeFileSync(p, out);
}
tagAdd('minecraft/tags/block/mineable/pickaxe.json', 'projecthero:star_forge');
tagAdd('minecraft/tags/block/needs_diamond_tool.json', 'projecthero:star_forge');
console.log('thor acquisition assets written');
