// v0.17.4 Gyro's suit items (GyroSuitItem): the four item icons (16x16 pixel art, our own), item models, shaped recipes and their
// recipe-book advancements. Run from the repo root: node scratchpad/spin/gen_gyro_suit_items.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng } = require('../lib_png.js');

const RES = 'src/main/resources';
const C = {
	'.': null,
	k: [24, 30, 26], // outline
	g: [63, 122, 90], // green cloth
	G: [96, 162, 122], // green highlight
	d: [40, 84, 62], // green shadow
	y: [232, 196, 64], // gold
	Y: [255, 230, 140],
	b: [104, 70, 42], // leather
	B: [140, 98, 60],
	n: [70, 46, 28],
	s: [150, 170, 160], // steel ball
	S: [210, 230, 220],
};
const ICONS = {
	gyro_suit_hat: [
		'................',
		'................',
		'................',
		'.....kkkkkk.....',
		'....kGGGgggk....',
		'...kGgggggddk...',
		'...kgggggggdk...',
		'...kgggggggdk...',
		'...kyyYyyYyyk...',
		'..kkyykyykyykk..',
		'.kdgggggggggddk.',
		'kdgggggggggggddk',
		'.kkkkkkkkkkkkkk.',
		'................',
		'................',
		'................',
	],
	gyro_suit_coat: [
		'................',
		'...kkk....kkk...',
		'..kGgykkkkygdk..',
		'.kGggyyGgyygddk.',
		'.kgggkyggykgddk.',
		'.kggk.kygk.kgdk.',
		'.kggk.kgyk.kgdk.',
		'.kggk.kygk.kgdk.',
		'.kkkk.kgyk.kkkk.',
		'.kyyk.kygk.kyyk.',
		'.kkkk.kgyk.kkkk.',
		'......kygk......',
		'......kgyk......',
		'.....kkkkkk.....',
		'................',
		'................',
	],
	gyro_suit_trousers: [
		'................',
		'..kkkkkkkkkkkk..',
		'..knnnnyynnnnk..',
		'kkkbbbbyybbbbkkk',
		'kSskggggggggkSsk',
		'kssbggggggggbssk',
		'.kkkgggkkgggkkk.',
		'...kgggk.kgddk..',
		'...kgggk.kgddk..',
		'...kgggk.kgddk..',
		'...kgggk.kgddk..',
		'...kgggk.kgddk..',
		'...kdddk.kdddk..',
		'...kkkkk.kkkkk..',
		'................',
		'................',
	],
	gyro_suit_boots: [
		'................',
		'................',
		'................',
		'................',
		'..kkkkk..kkkkk..',
		'..kBbbk..kBbbk..',
		'..kBbbk..kBbbk..',
		'..kyyyk..kyyyk..',
		'..kBbbk..kBbbk..',
		'..kBbbk..kBbbk..',
		'.kBbbbk.kBbbbk..',
		'kBbbbbk.kBbbbbk.',
		'knnnnnk.knnnnnk.',
		'kkkkkkk.kkkkkkk.',
		'................',
		'................',
	],
};
for (const [name, rows] of Object.entries(ICONS)) {
	const px = Buffer.alloc(16 * 16 * 4);
	rows.forEach((row, y) => [...row].forEach((ch, x) => {
		const c = C[ch];
		if (c === undefined) throw new Error(name + ' bad char ' + ch);
		if (c) px.set([c[0], c[1], c[2], 255], (y * 16 + x) * 4);
	}));
	fs.writeFileSync(path.join(RES, 'assets/projecthero/textures/item', name + '.png'), encodePng(16, 16, px));
	fs.writeFileSync(path.join(RES, 'assets/projecthero/models/item', name + '.json'),
		JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + name } }, null, 2) + '\n');
}
// shaped recipes: leather (L) with gold nuggets (G, his gold teeth and buckles); none clash with vanilla leather armour
const RECIPES = {
	gyro_suit_hat: ['LGL', 'L L'],
	gyro_suit_coat: ['LGL', 'LLL', 'LLL'],
	gyro_suit_trousers: ['LLL', 'LGL', 'L L'],
	gyro_suit_boots: ['G G', 'L L'],
};
for (const [name, pattern] of Object.entries(RECIPES)) {
	const recipe = {
		type: 'minecraft:crafting_shaped', category: 'equipment', pattern,
		key: { L: { item: 'minecraft:leather' }, G: { item: 'minecraft:gold_nugget' } },
		result: { id: 'projecthero:' + name, count: 1 },
	};
	fs.writeFileSync(path.join(RES, 'data/projecthero/recipe', name + '.json'), JSON.stringify(recipe, null, 2) + '\n');
	const adv = {
		parent: 'minecraft:recipes/root',
		criteria: {
			has_leather: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: ['minecraft:leather'] }] } },
			has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:' + name } },
		},
		requirements: [['has_leather', 'has_the_recipe']],
		rewards: { recipes: ['projecthero:' + name] },
	};
	fs.writeFileSync(path.join(RES, 'data/projecthero/advancement/recipes', name + '.json'), JSON.stringify(adv, null, 2) + '\n');
}
console.log('done');
