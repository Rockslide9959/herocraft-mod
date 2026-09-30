// v0.14.8 Kryptonian: the 16x16 textures (kryptonite ore / block, Meteor Core, kryptonite shard, Kryptonian Crystal)
// and every data / asset JSON the meteor's blocks and items need (blockstates, models, loot tables, tags, recipes).
// Run from the repo root:  node scratchpad/gen_kryptonian.js   (re-runnable; tags are merged, not overwritten)
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit');

const ROOT = path.join(__dirname, '..', 'src/main/resources');
const A = path.join(ROOT, 'assets/projecthero');
const D = path.join(ROOT, 'data');
let seed = 1938;
const rand = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const img = () => Buffer.alloc(16 * 16 * 4);
function put(px, x, y, r, g, b, a = 255) {
	if (x < 0 || y < 0 || x > 15 || y > 15) return;
	const i = (y * 16 + x) * 4;
	px[i] = Math.max(0, Math.min(255, r | 0)); px[i + 1] = Math.max(0, Math.min(255, g | 0)); px[i + 2] = Math.max(0, Math.min(255, b | 0)); px[i + 3] = a;
}
function write(rel, px) {
	const p = path.join(A, 'textures', rel);
	fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, encode(16, 16, px));
}
function json(p, obj) {
	fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, JSON.stringify(obj, null, 2) + '\n');
}

// A green crystal pixel: t 0 (deep) .. 1 (bright edge)
function green(t) { return [30 + t * 150, 150 + t * 105, 30 + t * 120]; }

// ---- kryptonite ore: stone with clusters of glowing green crystal
{
	const px = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const n = rand() * 22;
		put(px, x, y, 112 + n, 112 + n, 112 + n);
	}
	const clusters = [[4, 4], [11, 3], [8, 9], [3, 12], [12, 12]];
	for (const [cx, cy] of clusters) {
		for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
			const d = Math.abs(dx) + Math.abs(dy);
			if (d > 2 || (d === 2 && rand() < 0.5)) continue;
			const t = d === 0 ? 1 : d === 1 ? 0.65 : 0.3;
			const [r, g, b] = green(t);
			put(px, cx + dx, cy + dy, r, g, b);
		}
	}
	write('block/kryptonite_ore.png', px);
}

// ---- kryptonite block: faceted green crystal with light seams
{
	const px = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const facet = ((x >> 2) + (y >> 2)) % 3;
		const edge = x % 4 === 0 || y % 4 === 0;
		const n = rand() * 0.12;
		const t = (edge ? 0.95 : 0.35 + facet * 0.15) + n;
		const [r, g, b] = green(Math.min(1, t));
		put(px, x, y, r, g, b);
	}
	write('block/kryptonite_block.png', px);
}

// ---- Meteor Core: charred black rock veined with molten gold, a white-hot heart
{
	const px = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const n = rand() * 16;
		put(px, x, y, 34 + n, 28 + n, 26 + n);
	}
	// veins radiating from the centre
	for (let a = 0; a < 6; a++) {
		const ang = a * Math.PI / 3 + 0.3;
		for (let r = 1; r < 9; r++) {
			const x = Math.round(7.5 + Math.cos(ang) * r + (rand() - 0.5));
			const y = Math.round(7.5 + Math.sin(ang) * r + (rand() - 0.5));
			const t = 1 - r / 9;
			put(px, x, y, 200 + t * 55, 110 + t * 120, 20 + t * 60);
		}
	}
	for (let y = 5; y <= 10; y++) for (let x = 5; x <= 10; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d > 2.9) continue;
		const t = 1 - d / 2.9;
		put(px, x, y, 240 + t * 15, 190 + t * 60, 70 + t * 170);
	}
	write('block/meteor_core.png', px);
}

// ---- kryptonite shard (item): a jagged green crystal
{
	const px = img();
	for (let y = 1; y <= 14; y++) {
		const half = Math.max(0, Math.round((y < 7 ? (y - 1) * 0.6 : (14 - y) * 0.45)));
		const cx = 8 + Math.round((y - 8) * -0.25);
		for (let x = cx - half; x <= cx + half; x++) {
			const edge = x === cx - half || x === cx + half || y === 1 || y === 14;
			const t = edge ? 0.15 : (x < cx ? 0.95 : 0.55);
			const [r, g, b] = green(t);
			put(px, x, y, r, g, b);
		}
	}
	write('item/kryptonite_shard.png', px);
}

// ---- Kryptonian Crystal (item): a long clear crystal with a golden sunlit core
{
	const px = img();
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		// a diagonal hexagonal prism from bottom-left to top-right
		const u = (x + (15 - y)) / 2;       // along the crystal
		const v = (x - (15 - y));           // across
		if (u < 1 || u > 14.5 || Math.abs(v) > 4) continue;
		if (u > 11.5 && Math.abs(v) > (14.5 - u) * 1.4) continue; // pointed tip
		const edge = Math.abs(v) >= 4 || u < 1.5 || (u > 11.5 && Math.abs(v) > (14.5 - u) * 1.4 - 1.2);
		const core = Math.abs(v) <= 1.5;
		if (edge) put(px, x, y, 150, 190, 210);
		else if (core) put(px, x, y, 255, 205 + u * 3, 90 + u * 9);
		else put(px, x, y, 215, 240, 250, 230);
	}
	write('item/kryptonian_crystal.png', px);
}

// ---- blockstates / models
for (const b of ['kryptonite_ore', 'kryptonite_block', 'meteor_core']) {
	json(path.join(A, 'blockstates', b + '.json'), { variants: { '': { model: 'projecthero:block/' + b } } });
	json(path.join(A, 'models/block', b + '.json'), { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/' + b } });
	json(path.join(A, 'models/item', b + '.json'), { parent: 'projecthero:block/' + b });
}
for (const i of ['kryptonite_shard', 'kryptonian_crystal']) {
	json(path.join(A, 'models/item', i + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + i } });
}

// ---- loot tables
const selfDrop = (b) => ({ type: 'minecraft:item', name: 'projecthero:' + b });
json(path.join(D, 'projecthero/loot_table/blocks/kryptonite_ore.json'), {
	type: 'minecraft:block',
	pools: [{
		rolls: 1,
		entries: [{
			type: 'minecraft:alternatives',
			children: [
				{
					...selfDrop('kryptonite_ore'),
					conditions: [{ condition: 'minecraft:match_tool', predicate: { predicates: { 'minecraft:enchantments': [{ enchantments: 'minecraft:silk_touch', levels: { min: 1 } }] } } }]
				},
				{
					type: 'minecraft:item', name: 'projecthero:kryptonite_shard',
					functions: [
						{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: 1, max: 3 } },
						{ function: 'minecraft:apply_bonus', enchantment: 'minecraft:fortune', formula: 'minecraft:ore_drops' },
						{ function: 'minecraft:explosion_decay' }
					]
				}
			]
		}]
	}]
});
json(path.join(D, 'projecthero/loot_table/blocks/kryptonite_block.json'), {
	type: 'minecraft:block',
	pools: [{ rolls: 1, entries: [selfDrop('kryptonite_block')], conditions: [{ condition: 'minecraft:survives_explosion' }] }]
});
// the core never drops itself: one crystal per meteor
json(path.join(D, 'projecthero/loot_table/blocks/meteor_core.json'), {
	type: 'minecraft:block',
	pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:kryptonian_crystal' }] }]
});

// ---- recipes: nine shards <-> one block
json(path.join(D, 'projecthero/recipe/kryptonite_block.json'), {
	type: 'minecraft:crafting_shaped', category: 'building',
	pattern: ['SSS', 'SSS', 'SSS'],
	key: { S: { item: 'projecthero:kryptonite_shard' } },
	result: { id: 'projecthero:kryptonite_block', count: 1 }
});
json(path.join(D, 'projecthero/recipe/kryptonite_shard_from_block.json'), {
	type: 'minecraft:crafting_shapeless', category: 'misc',
	ingredients: [{ item: 'projecthero:kryptonite_block' }],
	result: { id: 'projecthero:kryptonite_shard', count: 9 }
});

// ---- tags: pickaxe, iron tier (merged into the existing files)
function addTag(rel, values) {
	const p = path.join(D, rel);
	const raw = fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : '{"replace": false, "values": []}';
	const t = JSON.parse(raw);
	for (const v of values) if (!t.values.includes(v)) t.values.push(v);
	json(p, t);
}
addTag('minecraft/tags/block/mineable/pickaxe.json', ['projecthero:kryptonite_ore', 'projecthero:kryptonite_block', 'projecthero:meteor_core']);
addTag('minecraft/tags/block/needs_iron_tool.json', ['projecthero:kryptonite_ore', 'projecthero:kryptonite_block', 'projecthero:meteor_core']);
console.log('kryptonian textures + data written');
