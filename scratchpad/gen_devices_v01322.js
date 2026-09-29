// v0.13.22: the six lab devices the guide always advertised but that never existed. Run from repo root.
const fs = require('fs');
const R = 'src/main/resources/';
const devices = [
	{ id: 'charged_copper_plates', tex: 'minecraft:block/cut_copper', recipe: ['CRC', 'RLR', 'CRC'], key: { C: 'minecraft:copper_block', R: 'minecraft:redstone', L: 'minecraft:lightning_rod' } },
	{ id: 'crystal_chamber', tex: 'minecraft:block/amethyst_block', recipe: ['AGA', 'GSG', 'AIA'], key: { A: 'minecraft:amethyst_block', G: 'minecraft:glass', S: 'minecraft:snow_block', I: 'minecraft:iron_ingot' } },
	{ id: 'enchanting_resonance', tex: 'minecraft:block/purpur_block', recipe: ['BEB', 'APA', 'BEB'], key: { B: 'minecraft:bookshelf', E: 'minecraft:ender_pearl', A: 'minecraft:amethyst_shard', P: 'minecraft:lapis_block' } },
	{ id: 'blast_chamber', tex: 'minecraft:block/polished_blackstone_bricks', recipe: ['OTO', 'FBF', 'OIO'], key: { O: 'minecraft:obsidian', T: 'minecraft:tnt', F: 'minecraft:fire_charge', B: 'minecraft:blast_furnace', I: 'minecraft:iron_ingot' } },
	{ id: 'resonant_chamber', tex: 'minecraft:block/note_block', recipe: ['WNW', 'NAN', 'WIW'], key: { W: 'minecraft:white_wool', N: 'minecraft:note_block', A: 'minecraft:amethyst_shard', I: 'minecraft:iron_ingot' } },
	{ id: 'gravity_distortion_rig', tex: 'minecraft:block/crying_obsidian', recipe: ['ICI', 'SOS', 'IRI'], key: { I: 'minecraft:iron_ingot', C: 'minecraft:compass', S: 'minecraft:slime_block', O: 'minecraft:crying_obsidian', R: 'minecraft:redstone' } },
];
const w = (p, o) => { fs.mkdirSync(require('path').dirname(R + p), { recursive: true }); fs.writeFileSync(R + p, JSON.stringify(o, null, 2) + '\n'); };
for (const d of devices) {
	w(`assets/projecthero/blockstates/${d.id}.json`, { variants: { 'powered=false': { model: `projecthero:block/${d.id}` }, 'powered=true': { model: `projecthero:block/${d.id}` } } });
	w(`assets/projecthero/models/block/${d.id}.json`, { parent: 'minecraft:block/cube_all', textures: { all: d.tex } });
	w(`assets/projecthero/models/item/${d.id}.json`, { parent: `projecthero:block/${d.id}` });
	const key = {}; for (const [k, v] of Object.entries(d.key)) key[k] = { item: v };
	w(`data/projecthero/recipe/${d.id}.json`, { type: 'minecraft:crafting_shaped', category: 'misc', pattern: d.recipe, key, result: { id: `projecthero:${d.id}`, count: 1 } });
	w(`data/projecthero/loot_table/blocks/${d.id}.json`, { type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: `projecthero:${d.id}` }], conditions: [{ condition: 'minecraft:survives_explosion' }] }] });
}
for (const tag of ['data/minecraft/tags/block/mineable/pickaxe.json', 'data/minecraft/tags/block/needs_iron_tool.json']) {
	const t = JSON.parse(fs.readFileSync(R + tag, 'utf8'));
	for (const d of devices) if (!t.values.includes(`projecthero:${d.id}`)) t.values.push(`projecthero:${d.id}`);
	fs.writeFileSync(R + tag, JSON.stringify(t, null, 2) + '\n');
}
console.log('devices written');
