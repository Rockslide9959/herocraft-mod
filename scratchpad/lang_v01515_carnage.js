// v0.15.15 Carnage: 1000 HP / iron-grade armour / 2 HP per 2 s regen, and four new moves (Tendril Whip Sweep,
// Axe-Arm Cleave, Spike Eruption, Symbiote Snare). Re-runnable. Run from the repo root: node scratchpad/lang_v01515_carnage.js
require('./langset.js')([
	{
		anchor: 'entity.projecthero.carnage',
		entries: {
			'entity.projecthero.carnage_attack': 'Crimson Symbiote',
		},
	},
	{
		anchor: 'boss.projecthero.carnage.say.spikes',
		entries: {
			'boss.projecthero.carnage.say.whip': "Let's play jump rope!",
			'boss.projecthero.carnage.say.cleave': 'Heads UP!',
			'boss.projecthero.carnage.say.erupt': 'Watch your step!',
			'boss.projecthero.carnage.say.snare': 'Hold STILL!',
		},
	},
	{
		anchor: 'projecthero.guide.carnage.moves.body',
		entries: {
			'projecthero.guide.carnage.body': 'The Symbiote\'s red, insane offspring - a world boss that falls from the night sky. 1000 health for one fighter (+250 for each extra, up to 3500), armour as tough as a full iron set, and he regenerates 2 health every 2 seconds. Symbiote hosts draw him four times as often.',
			'projecthero.guide.carnage.moves.body': 'Blade-arm combos up close, a leaping pounce from range, crimson tendrils that whip and drag everyone within ten blocks, and fans of crimson spikes. He picks a ready move at random and takes a short breather after each one.',
			'projecthero.guide.carnage.new_moves': 'New Moves',
			'projecthero.guide.carnage.new_moves.body': 'Tendril Whip Sweep: two long tendrils rear up behind his right shoulder, then whip across everything in front of him out to 8 blocks - get behind him or out of reach. Axe-Arm Cleave: his arm grows into a huge crimson axe and he leaps at you; the landing spot glows red, and a ring of shards races out from the impact - jump it or be clear. Spike Eruption: he punches the ground and glowing red cracks run toward you - step off them before the spikes burst up. Symbiote Snare: a glob of goo swells in his fist and is lobbed at you; it roots whoever it splashes in a tendril cocoon for 2 seconds.',
		},
	},
	{
		anchor: 'projecthero.guide.carnage.weakness.body',
		entries: {
			'projecthero.guide.carnage.weakness.body': 'Fire does double damage, makes him flinch and stops his regeneration for 6 seconds. Sound breaks him: ring a bell or blow a goat horn near him and he writhes helplessly, taking half again as much damage. Explosions hurt him more.',
		},
	},
]);
