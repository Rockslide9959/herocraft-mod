// v0.16.6 Ladybug: the Lucky Charm item models. node gen_ladybug_models.js <assets/projecthero dir>
'use strict';
const fs = require('fs');
const path = require('path');
const dir = path.join(process.argv[2], 'models/item');
const w = (n, o) => fs.writeFileSync(path.join(dir, n + '.json'), JSON.stringify(o, null, 2) + '\n');
for (const n of ['apple', 'tnt', 'parachute']) w('ladybug_lucky_' + n, { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/ladybug_lucky_' + n } });
w('ladybug_lucky_sword', { parent: 'minecraft:item/handheld', textures: { layer0: 'projecthero:item/ladybug_lucky_sword' } });
for (const i of [0, 1, 2]) w('ladybug_lucky_bow_pulling_' + i, { parent: 'projecthero:item/ladybug_lucky_bow', textures: { layer0: 'projecthero:item/ladybug_lucky_bow_pulling_' + i } });
w('ladybug_lucky_bow', {
	parent: 'minecraft:item/bow', textures: { layer0: 'projecthero:item/ladybug_lucky_bow' },
	overrides: [
		{ predicate: { pulling: 1 }, model: 'projecthero:item/ladybug_lucky_bow_pulling_0' },
		{ predicate: { pulling: 1, pull: 0.65 }, model: 'projecthero:item/ladybug_lucky_bow_pulling_1' },
		{ predicate: { pulling: 1, pull: 0.9 }, model: 'projecthero:item/ladybug_lucky_bow_pulling_2' }]
});
console.log('models written');
