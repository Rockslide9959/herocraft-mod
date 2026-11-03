// v0.17.2 Doomsday: models, blockstates, loot tables, recipes and the lang file. Usage: node scratchpad/gen_doomsday_json.js
// The lang lives in its own namespace folder (assets/projecthero_doomsday/lang/en_us.json): Minecraft merges every
// namespace's en_us.json, and a separate file keeps this branch from conflicting with en_us.json edits on master.
const fs = require('fs');
const path = require('path');
const res = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(res, 'assets', 'projecthero');
const D = path.join(res, 'data', 'projecthero');
const w = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 2) + '\n'); };

const facing = model => ({ variants: Object.fromEntries([['north', 0], ['east', 90], ['south', 180], ['west', 270]].map(([f, y]) =>
	[`facing=${f}`, y ? { model, y } : { model }])) });

// ---- the Calatonian Casket block: a low coffin with two cable bands
w(path.join(A, 'blockstates', 'calatonian_casket.json'), facing('projecthero:block/calatonian_casket'));
const faces = (t, top) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map(f => [f, { texture: f === 'up' || f === 'down' ? top : t }]));
w(path.join(A, 'models', 'block', 'calatonian_casket.json'), {
	parent: 'minecraft:block/block',
	textures: { particle: 'projecthero:block/calatonian_casket_side', side: 'projecthero:block/calatonian_casket_side', top: 'projecthero:block/calatonian_casket_top' },
	elements: [
		{ from: [1, 0, 0], to: [15, 9, 16], faces: faces('#side', '#top') },
		{ from: [0.5, 0, 3], to: [15.5, 10, 5], faces: faces('#side', '#side') },
		{ from: [0.5, 0, 11], to: [15.5, 10, 13], faces: faces('#side', '#side') },
	],
});
w(path.join(A, 'models', 'item', 'calatonian_casket.json'), { parent: 'projecthero:block/calatonian_casket' });

// ---- Doomsday's Skull: his head (front from the skin) with two bone horns
w(path.join(A, 'blockstates', 'doomsday_skull.json'), facing('projecthero:block/doomsday_skull'));
w(path.join(A, 'models', 'block', 'doomsday_skull.json'), {
	parent: 'minecraft:block/block',
	textures: { particle: 'projecthero:block/doomsday_skull_side', front: 'projecthero:block/doomsday_skull_front', side: 'projecthero:block/doomsday_skull_side',
		top: 'projecthero:block/doomsday_skull_top', bone: 'projecthero:block/doomsday_bone' },
	elements: [
		{ from: [4, 0, 4], to: [12, 8, 12], faces: { north: { texture: '#front' }, south: { texture: '#side' }, east: { texture: '#side' },
			west: { texture: '#side' }, up: { texture: '#top' }, down: { texture: '#side' } } },
		{ from: [2.5, 6, 5], to: [4, 11, 6.5], rotation: { angle: -22.5, axis: 'z', origin: [4, 6, 5] }, faces: faces('#bone', '#bone') },
		{ from: [12, 6, 5], to: [13.5, 11, 6.5], rotation: { angle: 22.5, axis: 'z', origin: [12, 6, 5] }, faces: faces('#bone', '#bone') },
		{ from: [7, 8, 9], to: [9, 11, 11], rotation: { angle: -22.5, axis: 'x', origin: [8, 8, 10] }, faces: faces('#bone', '#bone') },
	],
});
w(path.join(A, 'models', 'item', 'doomsday_skull.json'), { parent: 'projecthero:block/doomsday_skull' });

// ---- items
for (const id of ['phantom_zone_shard', 'phantom_zone_projector']) {
	w(path.join(A, 'models', 'item', id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } });
}
w(path.join(A, 'models', 'item', 'doomsday_bone_gauntlet.json'), { parent: 'minecraft:item/handheld', textures: { layer0: 'projecthero:item/doomsday_bone_gauntlet' } });
w(path.join(A, 'models', 'item', 'doomsday_spawn_egg.json'), { parent: 'minecraft:item/template_spawn_egg' });

// ---- loot tables (both blocks drop themselves)
for (const id of ['calatonian_casket', 'doomsday_skull']) {
	w(path.join(D, 'loot_table', 'blocks', id + '.json'), { type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:' + id }],
		conditions: [{ condition: 'minecraft:survives_explosion' }] }] });
}

// ---- recipes
w(path.join(D, 'recipe', 'calatonian_casket.json'), {
	type: 'minecraft:crafting_shaped', category: 'misc',
	pattern: ['NCN', 'OSO', 'NEN'],
	key: { N: { item: 'minecraft:netherite_ingot' }, C: { item: 'minecraft:chain' }, O: { item: 'minecraft:crying_obsidian' },
		S: { item: 'minecraft:nether_star' }, E: { item: 'minecraft:echo_shard' } },
	result: { id: 'projecthero:calatonian_casket', count: 1 },
});
w(path.join(D, 'recipe', 'phantom_zone_projector.json'), {
	type: 'minecraft:crafting_shapeless', category: 'misc',
	ingredients: [{ item: 'projecthero:phantom_zone_shard' }, { item: 'projecthero:phantom_zone_shard' }, { item: 'projecthero:phantom_zone_shard' }],
	result: { id: 'projecthero:phantom_zone_projector', count: 1 },
});

// ---- the lang
const L = {
	'entity.projecthero.doomsday': 'Doomsday',
	'entity.projecthero.calatonian_casket': 'Calatonian Casket',
	'entity.projecthero.doomsday_attack': 'Bone Spike',
	'item.projecthero.doomsday_spawn_egg': 'Doomsday Spawn Egg',
	'item.projecthero.phantom_zone_shard': 'Phantom Zone Shard',
	'item.projecthero.phantom_zone_shard.hint': 'Falls from Doomsday each time he dies. Three make a Phantom Zone Projector.',
	'item.projecthero.phantom_zone_projector': 'Phantom Zone Projector',
	'item.projecthero.phantom_zone_projector.hint': 'Hold right-click on Doomsday\'s corpse after his third death to banish him.',
	'item.projecthero.phantom_zone_projector.hint2': 'He can\'t be killed. He can only be sent away.',
	'item.projecthero.doomsday_bone_gauntlet': 'Doomsday\'s Bone Gauntlet',
	'item.projecthero.doomsday_bone_gauntlet.hint': 'Adapts: hits harder against every kind of mob it has killed.',
	'item.projecthero.doomsday_bone_gauntlet.adapted': 'Adapted to %s: +%s damage',
	'block.projecthero.calatonian_casket': 'Calatonian Casket',
	'block.projecthero.calatonian_casket.hint': 'Right-click to open it. Doomsday is inside.',
	'block.projecthero.calatonian_casket.hint2': 'The burial vault Calaton sealed him in. Bring an army.',
	'block.projecthero.doomsday_skull': 'Doomsday\'s Skull',
	'block.projecthero.doomsday_skull.hint': 'A trophy from the monster that killed Superman.',
	'event.projecthero.doomsday': 'Doomsday',
	'event.projecthero.doomsday.bar.impact': 'Something is trying to get out...',
	'event.projecthero.doomsday.bar.fight': 'Doomsday: killed %s of 3 times (%s fighters)',
	'event.projecthero.doomsday.bar.corpse': 'He is getting back up: %s%%',
	'event.projecthero.doomsday.bar.banish': 'BANISH HIM: Phantom Zone Projector (he rises at 100%%: %s%%)',
	'boss.projecthero.doomsday.life': ' (life %s of 3)',
	'boss.projecthero.doomsday.life_enraged': ' (ENRAGED)',
	'boss.projecthero.doomsday.adapted': ' | adapted: %s',
	'doomsday.projecthero.bucket.melee': 'Melee',
	'doomsday.projecthero.bucket.projectile': 'Projectiles',
	'doomsday.projecthero.bucket.fire': 'Fire',
	'doomsday.projecthero.bucket.explosion': 'Explosions',
	'doomsday.projecthero.bucket.energy': 'Energy',
	'doomsday.projecthero.bucket.magic': 'Magic',
	'doomsday.projecthero.bucket.power': 'Hero Powers',
	'doomsday.projecthero.bucket.none': 'Nothing',
	'message.projecthero.doomsday.immune': 'Doomsday has adapted: %s does nothing to him now',
	'message.projecthero.doomsday.adapts': 'DOOMSDAY ADAPTS: %s (-%s%%)',
	'message.projecthero.doomsday.killed_by': 'He will never die to %s again.',
	'message.projecthero.doomsday.down': 'Doomsday falls (life %s). He is not dead.',
	'message.projecthero.doomsday.down_final': 'Doomsday falls (life %s). Banish him before he rises!',
	'message.projecthero.doomsday.banished': 'Doomsday is sealed in the Phantom Zone.',
	'message.projecthero.doomsday.grip_broken': 'His grip breaks!',
	'message.projecthero.doomsday.projector.no_target': 'Aim the Projector at Doomsday\'s corpse.',
	'message.projecthero.doomsday.projector.lives_left': 'He still has lives in him. Kill him three times first.',
	'message.projecthero.doomsday.projector.alive': 'He has to be down first.',
	'message.projecthero.doomsday.projector.charging': 'Opening the Phantom Zone... %s',
	'message.projecthero.doomsday.projector.fired': 'THE PHANTOM ZONE OPENS',
	'message.projecthero.doomsday.disabled': 'Doomsday is switched off on this server.',
	'message.projecthero.doomsday.survival_only': 'Only a fighter in Survival can open the casket.',
	'message.projecthero.doomsday.peaceful': 'Nothing stirs inside on Peaceful.',
	'message.projecthero.doomsday.refused': 'The casket will not open here (is Doomsday already loose nearby?).',
	'message.projecthero.doomsday.impact': 'Something huge just fell out of the sky...',
	'message.projecthero.doomsday.joined': 'You are in Doomsday\'s path.',
	'message.projecthero.doomsday.no_escape': 'There is no outrunning Doomsday.',
	'message.projecthero.doomsday.vanished': 'Doomsday is gone.',
	'message.projecthero.doomsday.projector_given': 'The Phantom Zone shards come together: %s holds the Projector!',
	'message.projecthero.doomsday.fighter_down': '%s was torn apart by Doomsday.',
	'message.projecthero.doomsday.all_down': 'Doomsday has killed everyone and leaps away, looking for more.',
	'message.projecthero.doomsday.first_clear': 'From his bones you take a gauntlet, and it remembers what it kills.',
	'title.projecthero.doomsday.start': 'DOOMSDAY',
	'title.projecthero.doomsday.start.sub': 'Something is pounding its way out',
	'title.projecthero.doomsday.out': 'DOOMSDAY IS LOOSE',
	'title.projecthero.doomsday.out.sub': 'Still bound. Not for long.',
	'title.projecthero.doomsday.unbound': 'THE CABLES TEAR',
	'title.projecthero.doomsday.unbound.sub': 'Both fists free',
	'title.projecthero.doomsday.down': 'HE FALLS (%s/3)',
	'title.projecthero.doomsday.down.sub': 'He will not die to %s again',
	'title.projecthero.doomsday.down.sub_none': 'He is not dead',
	'title.projecthero.doomsday.final': 'BANISH HIM',
	'title.projecthero.doomsday.final.sub': 'Phantom Zone Projector on his corpse, before he rises',
	'title.projecthero.doomsday.rises': 'HE RISES',
	'title.projecthero.doomsday.rises.sub': 'Bigger. Harder. Adapted.',
	'title.projecthero.doomsday.enraged': 'HE RISES AGAIN',
	'title.projecthero.doomsday.enraged.sub': 'Enraged: kill him and use the Projector',
	'title.projecthero.doomsday.banished': 'DOOMSDAY BANISHED',
	'title.projecthero.doomsday.banished.sub': 'Sealed in the Phantom Zone',
};
w(path.join(res, 'assets', 'projecthero_doomsday', 'lang', 'en_us.json'), L);
console.log('ok', Object.keys(L).length, 'lang keys');
