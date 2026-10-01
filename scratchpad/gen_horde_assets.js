// v0.14.12 Horde blocks: every asset of the three Horde blocks and the two new bosses.
//   - textures/block/{zombie,skeleton,spider}_horde{,_active}.png  (drawn: a face set in a mottled block; the eyes glow when active)
//   - textures/entity/bone_tyrant.png  (vanilla skeleton, aged yellow bone, burning red eye sockets)
//   - textures/entity/brood_queen.png  (vanilla spider, blood-red and black)
//   - blockstates, block + item models, spawn-egg item models, block loot tables, the three recipes
// Usage (from the repo root): node scratchpad/gen_horde_assets.js <dir holding assets/minecraft/textures/entity/...>
//   The vanilla textures are read from the Minecraft client jar extracted into that dir (they are not committed).
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit');

const VANILLA = process.argv[2];
if (!VANILLA) throw new Error('usage: node scratchpad/gen_horde_assets.js <extracted client jar dir>');
const RES = path.join(__dirname, '..', 'src/main/resources');
const A = path.join(RES, 'assets/projecthero');
const D = path.join(RES, 'data/projecthero');
const json = (p, obj) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, 2) + '\n'); };

// deterministic noise
let seed = 1234567;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));

function block(name, base, dark, accent, eye, eyeGlow, face) {
	for (const active of [false, true]) {
		seed = name.length * 7919;
		const px = Buffer.alloc(16 * 16 * 4);
		const put = (x, y, c) => { const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; };
		for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
			let c = mix(base, dark, rnd() * 0.45);
			if (rnd() < 0.08) c = mix(c, accent, 0.6);
			if (x === 0 || y === 0 || x === 15 || y === 15) c = mix(c, dark, 0.55);
			put(x, y, c);
		}
		face.forEach((row, y) => [...row].forEach((ch, x) => {
			if (ch === 'K') put(x, y, dark);
			else if (ch === 'E') put(x, y, active ? eyeGlow : eye);
			else if (ch === 'A') put(x, y, accent);
		}));
		fs.writeFileSync(path.join(A, 'textures/block', `${name}${active ? '_active' : ''}.png`), encode(16, 16, px));
	}
	json(path.join(A, `blockstates/${name}.json`), { variants: {
		'active=false': { model: `projecthero:block/${name}` },
		'active=true': { model: `projecthero:block/${name}_active` },
	} });
	json(path.join(A, `models/block/${name}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${name}` } });
	json(path.join(A, `models/block/${name}_active.json`), { parent: 'minecraft:block/cube_all', textures: { all: `projecthero:block/${name}_active` } });
	json(path.join(A, `models/item/${name}.json`), { parent: `projecthero:block/${name}` });
	json(path.join(D, `loot_table/blocks/${name}.json`), {
		type: 'minecraft:block',
		pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: `projecthero:${name}` }], conditions: [{ condition: 'minecraft:survives_explosion' }] }],
	});
}
fs.mkdirSync(path.join(A, 'textures/block'), { recursive: true });

// a rotting face: hollow eyes, a gaping mouth
block('zombie_horde', [92, 128, 70], [36, 52, 30], [120, 60, 50], [20, 28, 16], [150, 255, 90], [
	'', '', '', '',
	'................', '...EEE....EEE...', '...EEE....EEE...', '....K......K....',
	'................', '.......KK.......', '.....KKKKKK.....', '....KAAAAAAK....', '....KKKKKKKK....',
]);
// a skull set in bone
block('skeleton_horde', [222, 216, 196], [120, 112, 96], [250, 248, 238], [30, 28, 26], [140, 220, 255], [
	'', '', '',
	'....KKKKKKKK....', '...K........K...', '...K.EEE.EEE.K..', '...K.EEE.EEE.K..', '...K....K....K..',
	'....K..KKK..K...', '.....KAKAKAK....', '.....KKKKKKK....',
]);
// eight eyes in a black, webbed mass
block('spider_horde', [40, 32, 36], [12, 8, 10], [170, 170, 176], [90, 10, 14], [255, 40, 40], [
	'', '', '', '', '',
	'...E........E...', '....EE....EE....', '.....EE..EE.....', '......E..E......', '',
	'..A.........A...', '...A.......A....', '....AAAAAAA.....',
]);

// ---- the bosses' textures, recoloured from vanilla
fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
{
	const s = decode(path.join(VANILLA, 'assets/minecraft/textures/entity/skeleton/skeleton.png'));
	for (let i = 0; i < s.w * s.h; i++) {
		const o = i * 4;
		if (!s.px[o + 3]) continue;
		const x = i % s.w, y = Math.floor(i / s.w);
		const lum = (s.px[o] + s.px[o + 1] + s.px[o + 2]) / 3;
		// the face (8..16 x 8..16): the dark sockets burn red
		if (x >= 8 && x < 16 && y >= 8 && y < 16 && lum < 110) {
			s.px[o] = 230; s.px[o + 1] = 30; s.px[o + 2] = 20;
			continue;
		}
		s.px[o] = Math.min(255, Math.round(s.px[o] * 1.0));
		s.px[o + 1] = Math.round(s.px[o + 1] * 0.9);
		s.px[o + 2] = Math.round(s.px[o + 2] * 0.68);
	}
	fs.writeFileSync(path.join(A, 'textures/entity/bone_tyrant.png'), encode(s.w, s.h, s.px));
}
{
	const s = decode(path.join(VANILLA, 'assets/minecraft/textures/entity/spider/spider.png'));
	for (let i = 0; i < s.w * s.h; i++) {
		const o = i * 4;
		if (!s.px[o + 3]) continue;
		const r = s.px[o], g = s.px[o + 1], b = s.px[o + 2];
		const lum = (r + g + b) / 3;
		if (r > 150 && g < 80) continue; // keep the eyes
		// black body, blood-red highlights
		s.px[o] = Math.min(255, Math.round(lum * lum / 120 + 12));
		s.px[o + 1] = Math.round(lum * 0.35);
		s.px[o + 2] = Math.round(lum * 0.42);
	}
	fs.writeFileSync(path.join(A, 'textures/entity/brood_queen.png'), encode(s.w, s.h, s.px));
}

// ---- spawn eggs
for (const egg of ['horde_spider_spawn_egg', 'bone_tyrant_spawn_egg', 'brood_queen_spawn_egg']) {
	json(path.join(A, `models/item/${egg}.json`), { parent: 'minecraft:item/template_spawn_egg' });
}

// ---- recipes: a full crafting grid of the horde's drop
const fill = (name, item) => json(path.join(D, `recipe/${name}.json`), {
	type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['XXX', 'XXX', 'XXX'],
	key: { X: { item } }, result: { id: `projecthero:${name}`, count: 1 },
});
fill('zombie_horde', 'minecraft:rotten_flesh');
fill('skeleton_horde', 'minecraft:bone_block');
fill('spider_horde', 'minecraft:spider_eye');
console.log('horde assets: 6 block textures, 3 blockstates, 6 block models, 6 item models, 3 loot tables, 3 recipes, 2 boss textures');
