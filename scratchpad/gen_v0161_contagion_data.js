// v0.16.1 Ultron Contagion: recipes, tags and loot tables.
// Usage (from the repo root): node scratchpad/gen_v0161_contagion_data.js   (idempotent)
'use strict';
const fs = require('fs');
const path = require('path');
const DATA = path.join(__dirname, '..', 'src/main/resources/data');
const NS = 'projecthero';

function write(rel, obj) {
	const f = path.join(DATA, rel);
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n');
	console.log('wrote', rel);
}

function shaped(name, pattern, key, result, count = 1) {
	const k = {};
	for (const [c, id] of Object.entries(key)) k[c] = id.startsWith('#') ? { tag: id.slice(1) } : { item: id };
	write(`${NS}/recipe/${name}.json`, { type: 'minecraft:crafting_shaped', category: 'misc', pattern, key: k, result: { id: result, count } });
}

function shapeless(name, ingredients, result, count = 1) {
	write(`${NS}/recipe/${name}.json`, {
		type: 'minecraft:crafting_shapeless', category: 'misc',
		ingredients: ingredients.map(id => id.startsWith('#') ? { tag: id.slice(1) } : { item: id }),
		result: { id: result, count },
	});
}

const SCRAP = `${NS}:ultron_scrap`, NANITE = `${NS}:nanite_sample`, INGOT = `${NS}:vibranium_ingot`;

// ---- recipes
shaped('purge_charge', ['SCS', 'RER', 'SCS'], { S: SCRAP, C: 'minecraft:copper_ingot', R: 'minecraft:redstone', E: 'minecraft:sculk_sensor' },
	`${NS}:purge_charge`, 4);
shaped('firewall_beacon', ['NAN', 'IVI', 'III'], { N: NANITE, A: `${NS}:arc_reactor`, V: `${NS}:vibranium_plating`, I: 'minecraft:iron_block' },
	`${NS}:firewall_beacon`);
shaped('override_spike', ['SLS', 'SNS', ' R '], { S: SCRAP, L: 'minecraft:lightning_rod', N: NANITE, R: `${NS}:reactor_core` },
	`${NS}:override_spike`);
shaped('override_spike_redstone', ['SLS', 'SNS', ' R '], { S: SCRAP, L: 'minecraft:lightning_rod', N: NANITE, R: 'minecraft:redstone_block' },
	`${NS}:override_spike`);
shapeless('firewall_chip', [NANITE, `${NS}:stark_circuit`, SCRAP, SCRAP, 'minecraft:quartz'], `${NS}:firewall_chip`);
shaped('vibranium_plating_2', [' V ', 'VPV', ' V '], { V: INGOT, P: `${NS}:vibranium_plating` }, `${NS}:vibranium_plating_2`);
shaped('vibranium_shield', ['VVV', 'VSV', ' V '], { V: INGOT, S: `${NS}:adamantium_shield` }, `${NS}:vibranium_shield`);
// smithing (special recipes, no template): Firewall Chip into an Iron Man helmet, Vibranium Plating II onto an Iron Man piece
write(`${NS}/recipe/firewall_chip_install.json`, { type: `${NS}:firewall_chip` });
write(`${NS}/recipe/vibranium_plating_2_apply.json`, { type: `${NS}:vibranium_plating_2` });

// ---- the natural blocks a Nexus may corrupt (never anything player-made: no planks, bricks, glass, wood, stripped logs)
write(`${NS}/tags/block/contagion_corruptible.json`, {
	replace: false,
	values: [
		'minecraft:grass_block', 'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:podzol', 'minecraft:rooted_dirt', 'minecraft:mycelium',
		'minecraft:moss_block', 'minecraft:mud', 'minecraft:stone', 'minecraft:granite', 'minecraft:diorite', 'minecraft:andesite',
		'minecraft:deepslate', 'minecraft:tuff', 'minecraft:calcite', 'minecraft:sand', 'minecraft:red_sand', 'minecraft:gravel',
		'minecraft:sandstone', 'minecraft:red_sandstone', 'minecraft:terracotta', 'minecraft:snow_block', 'minecraft:oak_log',
		'minecraft:spruce_log', 'minecraft:birch_log', 'minecraft:jungle_log', 'minecraft:acacia_log', 'minecraft:dark_oak_log',
		'minecraft:mangrove_log', 'minecraft:cherry_log', '#minecraft:leaves',
	],
});

// ---- loot: the two placeable Contagion blocks drop themselves
for (const id of ['firewall_beacon', 'ultron_omega_core']) {
	write(`${NS}/loot_table/blocks/${id}.json`, {
		type: 'minecraft:block',
		pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: `${NS}:${id}` }], conditions: [{ condition: 'minecraft:survives_explosion' }] }],
	});
}

// ---- pickaxe-mineable (merged into the existing tag, not replaced)
const pick = path.join(DATA, 'minecraft/tags/block/mineable/pickaxe.json');
const j = JSON.parse(fs.readFileSync(pick, 'utf8'));
for (const id of ['firewall_beacon', 'ultron_omega_core', 'corrupted_circuitry', 'corrupted_circuitry_stone', 'corrupted_circuitry_log']) {
	if (!j.values.includes(`${NS}:${id}`)) j.values.push(`${NS}:${id}`);
}
fs.writeFileSync(pick, JSON.stringify(j, null, 2) + '\n');
console.log('updated minecraft/tags/block/mineable/pickaxe.json');
