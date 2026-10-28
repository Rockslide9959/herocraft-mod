// v0.17.1 Omnitrix: writes the data + model JSON (damage types and tags, recipes, loot table, block tags, blockstate, block
// and item models). Idempotent: tags are merged, everything else is overwritten.
//   node scratchpad/gen_omnitrix_data.js
'use strict';
const fs = require('fs');
const path = require('path');
const RES = path.join(__dirname, '..', 'src', 'main', 'resources');
const DATA = path.join(RES, 'data');
const ASSETS = path.join(RES, 'assets', 'projecthero');

function write(file, obj) {
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
}
function tag(file, ...values) {
	let t = { replace: false, values: [] };
	if (fs.existsSync(file)) t = JSON.parse(fs.readFileSync(file, 'utf8'));
	for (const v of values) if (!t.values.includes(v)) t.values.push(v);
	write(file, t);
}

// ---------------------------------------------------------------- damage
write(path.join(DATA, 'projecthero', 'damage_type', 'omnitrix_extraction.json'), { message_id: 'projecthero.omnitrix_extraction', scaling: 'never', exhaustion: 0 });
write(path.join(DATA, 'projecthero', 'damage_type', 'alien_strike.json'), { message_id: 'projecthero.alien_strike', scaling: 'when_caused_by_living_non_player', exhaustion: 0.1 });
write(path.join(DATA, 'projecthero', 'damage_type', 'alien_fire.json'), { message_id: 'projecthero.alien_fire', scaling: 'when_caused_by_living_non_player', exhaustion: 0.1, effects: 'burning' });
const DT = path.join(DATA, 'minecraft', 'tags', 'damage_type');
for (const t of ['bypasses_armor', 'bypasses_effects', 'bypasses_enchantments', 'bypasses_invulnerability', 'bypasses_resistance', 'no_knockback']) {
	tag(path.join(DT, t + '.json'), 'projecthero:omnitrix_extraction');
}
tag(path.join(DT, 'is_fire.json'), 'projecthero:alien_fire');

// ---------------------------------------------------------------- recipes
const R = path.join(DATA, 'projecthero', 'recipe');
function shaped(name, pattern, key, result, count = 1) {
	const k = {};
	for (const [c, item] of Object.entries(key)) k[c] = { item };
	write(path.join(R, name + '.json'), { type: 'minecraft:crafting_shaped', category: 'misc', pattern, key: k, result: { id: result, count } });
}
// 1. Plumber Alloy: 2 netherite ingots + a nether star + 4 echo shards + 2 crying obsidian
shaped('plumber_alloy', ['ECE', 'NSN', 'ECE'],
	{ E: 'minecraft:echo_shard', C: 'minecraft:crying_obsidian', N: 'minecraft:netherite_ingot', S: 'minecraft:nether_star' }, 'projecthero:plumber_alloy');
// 2. Containment Core: 2 Plumber Alloy + a heart of the sea + 2 eyes of ender + 4 redstone blocks
shaped('containment_core', ['RER', 'AHA', 'RER'],
	{ R: 'minecraft:redstone_block', E: 'minecraft:ender_eye', A: 'projecthero:plumber_alloy', H: 'minecraft:heart_of_the_sea' }, 'projecthero:containment_core');
// 3. The Omnitrix Containment Pod: the core + 3 tinted glass + 2 iron blocks + 2 crying obsidian + a lodestone
shaped('omnitrix_containment_pod', ['TTT', 'ICI', 'OLO'],
	{ T: 'minecraft:tinted_glass', I: 'minecraft:iron_block', C: 'projecthero:containment_core', O: 'minecraft:crying_obsidian', L: 'minecraft:lodestone' },
	'projecthero:omnitrix_containment_pod');
// the Meteor Scanner (mid-game): a compass, a diamond, an amethyst shard, 2 redstone, 4 iron
shaped('meteor_scanner', ['IDI', 'RCR', 'IAI'],
	{ I: 'minecraft:iron_ingot', D: 'minecraft:diamond', R: 'minecraft:redstone', C: 'minecraft:compass', A: 'minecraft:amethyst_shard' }, 'projecthero:meteor_scanner');

// ---------------------------------------------------------------- the pod block
write(path.join(DATA, 'projecthero', 'loot_table', 'blocks', 'omnitrix_containment_pod.json'), {
	type: 'minecraft:block',
	pools: [{
		rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:omnitrix_containment_pod' }],
		conditions: [{ condition: 'minecraft:survives_explosion' },
			{ condition: 'minecraft:block_state_property', block: 'projecthero:omnitrix_containment_pod', properties: { half: 'lower' } }],
	}],
});
tag(path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), 'projecthero:omnitrix_containment_pod');
tag(path.join(DATA, 'minecraft', 'tags', 'block', 'needs_diamond_tool.json'), 'projecthero:omnitrix_containment_pod');

const variants = {};
const rot = { north: 0, east: 90, south: 180, west: 270 };
for (const half of ['lower', 'upper']) {
	for (const [facing, y] of Object.entries(rot)) {
		const v = { model: 'projecthero:block/omnitrix_containment_pod_' + half };
		if (y) v.y = y;
		variants[`facing=${facing},half=${half}`] = v;
	}
}
write(path.join(ASSETS, 'blockstates', 'omnitrix_containment_pod.json'), { variants });

const BASE = 'projecthero:block/omnitrix_pod_base';
const FRAME = 'projecthero:block/omnitrix_pod_frame';
const PANEL = 'projecthero:block/omnitrix_pod_panel';
function box(from, to, tex, faces = ['north', 'south', 'east', 'west', 'up', 'down'], uvFor) {
	const f = {};
	for (const d of faces) {
		f[d] = { texture: tex };
		if (uvFor) f[d].uv = uvFor(d);
	}
	return { from, to, faces: f };
}
const pillarUv = () => [0, 0, 2, 16];
write(path.join(ASSETS, 'models', 'block', 'omnitrix_containment_pod_lower.json'), {
	parent: 'block/block', ambientocclusion: false,
	textures: { particle: BASE, base: BASE, frame: FRAME, panel: PANEL },
	elements: [
		box([0, 0, 0], [16, 2, 16], '#base', undefined, d => (d === 'up' || d === 'down') ? [0, 0, 16, 16] : [0, 14, 16, 16]),
		box([0, 2, 0], [2, 16, 2], '#frame', ['north', 'south', 'east', 'west'], () => [0, 0, 2, 14]),
		box([14, 2, 0], [16, 16, 2], '#frame', ['north', 'south', 'east', 'west'], () => [0, 0, 2, 14]),
		box([0, 2, 14], [2, 16, 16], '#frame', ['north', 'south', 'east', 'west'], () => [0, 0, 2, 14]),
		box([14, 2, 14], [16, 16, 16], '#frame', ['north', 'south', 'east', 'west'], () => [0, 0, 2, 14]),
		// the control panel on the front-left pillar
		box([2, 7, 0], [5, 11, 1], '#panel', ['north', 'south', 'east', 'west', 'up', 'down'], () => [0, 0, 3, 4]),
	],
});
write(path.join(ASSETS, 'models', 'block', 'omnitrix_containment_pod_upper.json'), {
	parent: 'block/block', ambientocclusion: false,
	textures: { particle: BASE, base: BASE, frame: FRAME },
	elements: [
		box([0, 0, 0], [2, 14, 2], '#frame', ['north', 'south', 'east', 'west'], () => [0, 2, 2, 16]),
		box([14, 0, 0], [16, 14, 2], '#frame', ['north', 'south', 'east', 'west'], () => [0, 2, 2, 16]),
		box([0, 0, 14], [2, 14, 16], '#frame', ['north', 'south', 'east', 'west'], () => [0, 2, 2, 16]),
		box([14, 0, 14], [16, 14, 16], '#frame', ['north', 'south', 'east', 'west'], () => [0, 2, 2, 16]),
		box([0, 14, 0], [16, 16, 16], '#base', undefined, d => (d === 'up' || d === 'down') ? [0, 0, 16, 16] : [0, 14, 16, 16]),
	],
});

// ---------------------------------------------------------------- items
for (const id of ['omnitrix', 'meteor_scanner', 'plumber_alloy', 'containment_core', 'omnitrix_containment_pod']) {
	write(path.join(ASSETS, 'models', 'item', id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } });
}
console.log('omnitrix data written');
