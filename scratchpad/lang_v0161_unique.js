// v0.16.1: the projectheroUniquePowers gamerule (one holder per hero power). Re-runnable: existing keys are rewritten.
require('./langset.js')([
	{ anchor: 'projecthero.guide.powerclass.body', entries: {
		'projecthero.guide.unique_powers': 'Unique Hero Powers',
		'projecthero.guide.unique_powers.body': 'Server owners can make every hero power one-of-a-kind with /gamerule projectheroUniquePowers true. While it is on, only one player at a time can hold each hero power (the Symbiote too; mutations are not limited). Trying to gain a taken power fails and tells you who holds it, and your serum or item is kept. A power frees up when its holder loses it or swaps it for another. Operators: /projecthero uniquepowers list shows who holds what, and /projecthero uniquepowers release <power> frees one.',
	} },
	{ anchor: 'projecthero.hero_tier.kryptonian', entries: {
		'projecthero.hero_tier.super_soldier': 'Super Soldier',
		'projecthero.hero_tier.nova': 'Nova',
	} },
	{ anchor: null, entries: {
		'message.projecthero.unique_powers.taken': '%s is already held by %s',
		'message.projecthero.unique_powers.released': 'An operator released your %s.',
		'gamerule.projectheroUniquePowers': 'Unique hero powers',
		'gamerule.projectheroUniquePowers.description': 'Only one player at a time can hold each Project Hero hero power.',
	} },
]);
