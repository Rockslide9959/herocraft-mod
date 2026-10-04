// v0.14.21 lang: Gravebound power bosses (Empowered Zombies) now fight with the revamped mutation kits -- every one of
// the mutations, including the ones players cannot currently obtain. Idempotent (keys are rewritten in place).
// Run from the repo root:
//   node scratchpad/lang_v01421_gravebound_bosses.js
require('./langset.js')([
	{ anchor: 'projecthero.guide.zombie_raid.waves', entries: {
		'projecthero.guide.zombie_raid.waves.body': 'Risen and baby zombies, Armoured Zombies that shrug off arrows, Acid Zombies that deny ground, Sword Skeletons that hunt archers, and Juggernauts that charge. Powered Zombie Bosses arrive on waves 4, 8 and 12, each carrying one of the mutations -- any of them, even ones you cannot get yourself yet.',
	} },
	{ anchor: 'projecthero.guide.zombie_raid.bosses', entries: {
		'projecthero.guide.zombie_raid.bosses.body': 'An Empowered Zombie fights with a real mutation, not a bigger health bar: three to five of that power\'s current abilities -- a Laser Vision boss sweeps its beam and blasts you at close range, a Geokinesis boss races earth spikes at you and quakes the ground, a Super Speed boss blitzes and whips up a vortex. Its boss bar and nameplate name the power. Every big move is telegraphed (a wind-up, a ring on the ground, a charging sound) and its strongest one is saved for when it is badly hurt. It picks its targets, follows fliers, closes on archers and goes for your pets too, but never harms its own horde. Kill one for a heap of Grave Essence and a chance at its head. The wave-12 boss always drops the Grave Champion Head.',
	} },
]);
