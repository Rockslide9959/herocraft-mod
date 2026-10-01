// v0.14.16 Skeleton Horde: the six horde skeletons, the Blast Arrow, their spawn eggs, the rebuilt Bone Tyrant's boss bar
// and phase messages, and the guidebook's Skeleton Horde entry. Run from the repo root: node scratchpad/lang_v01416_skeleton_horde.js
require('./langset.js')([
	{ anchor: 'entity.projecthero.bone_tyrant', entries: {
		'entity.projecthero.bone_runner': 'Bone Runner',
		'entity.projecthero.bone_knight': 'Bone Knight',
		'entity.projecthero.blight_archer': 'Blight Archer',
		'entity.projecthero.bone_bomber': 'Bone Bomber',
		'entity.projecthero.bone_brute': 'Bone Brute',
		'entity.projecthero.necromancer': 'Necromancer',
		'entity.projecthero.blast_arrow': 'Blast Arrow',
	} },
	{ anchor: 'item.projecthero.bone_tyrant_spawn_egg', entries: {
		'item.projecthero.bone_runner_spawn_egg': 'Bone Runner Spawn Egg',
		'item.projecthero.bone_knight_spawn_egg': 'Bone Knight Spawn Egg',
		'item.projecthero.blight_archer_spawn_egg': 'Blight Archer Spawn Egg',
		'item.projecthero.bone_bomber_spawn_egg': 'Bone Bomber Spawn Egg',
		'item.projecthero.bone_brute_spawn_egg': 'Bone Brute Spawn Egg',
		'item.projecthero.necromancer_spawn_egg': 'Necromancer Spawn Egg',
	} },
	{ anchor: 'event.projecthero.spider_horde', entries: {
		'boss.projecthero.bone_tyrant.bar.1': 'The Bone Tyrant',
		'boss.projecthero.bone_tyrant.bar.2': 'The Bone Tyrant -- Risen',
		'boss.projecthero.bone_tyrant.bar.3': 'The Bone Tyrant -- Enraged',
		'boss.projecthero.bone_tyrant.phase2': 'The Bone Tyrant roars -- the dead answer!',
		'boss.projecthero.bone_tyrant.phase3': 'The Bone Tyrant is enraged!',
	} },
	{ entries: {
		'projecthero.guide.hordes.skeleton.body': 'Crafting: fill the crafting grid with bone blocks. Skeletons and strays, bogged, sword and wither skeletons -- and six of the horde\'s own: small, pouncing Bone Runners; Blight Archers (Wither and Hunger arrows); Bone Knights in iron behind a shield no arrow gets past from the front; Bone Bombers with TNT heads and exploding arrows; Necromancers that raise the dead and heal the horde (kill them first); and hulking Bone Brutes whose Ground Slam you can see coming. They get tougher every wave. Boss: the Bone Tyrant, a seven-block lich-king stronger than the Titan -- greatsword sweeps, spike lines and spike rings (jump them), arrow volleys and storms, bone cages, a charge, Grave Step, and when enraged a sweeping soul-fire beam and a wither aura. Every move is telegraphed: watch his wind-up and the marks on the ground.',
	} },
]);
