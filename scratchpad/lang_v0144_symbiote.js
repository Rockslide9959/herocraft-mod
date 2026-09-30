// v0.14.4 lang: the Symbiote takes over passive animals, wolves and cats; Symbiote Pets.
// Run from the repo root: node scratchpad/lang_v0144_symbiote.js
require('./langset.js')([
	{ anchor: 'message.projecthero.symbiote.took_host', entries: {
		'message.projecthero.symbiote.pet_shared': 'Your Symbiote spills into %s -- it rises bigger, blacker and hungry for a fight, and still yours.',
		'message.projecthero.symbiote.pet_bonded': 'A Symbiote bonds with %s -- it stays loyal to you, and now it has powers.',
		'message.projecthero.symbiote.pet_recalled': 'The Symbiote flows out of %s and back into you.',
		'message.projecthero.symbiote.pet_shaken': 'The noise shakes the Symbiote out of %s!',
		'message.projecthero.symbiote.pet_too_weak': 'Your Symbiote is too weak to share itself (it needs %s Biomass).',
	} },
	{ anchor: 'projecthero.guide.symbiote.step.find', entries: {
		'projecthero.guide.symbiote.step.find': 'Find a loose Symbiote -- break open the Symbiote Meteorite at the heart of a meteor crater and it crawls out of the rock, it waits sealed in the containment cell of a hidden research lab, or it tears itself free of a rare Symbiote Host mob when that mob dies. A free Symbiote is alive: it crawls off looking for a creature to take over -- monsters first, but cows, pigs, sheep, chickens, rabbits, foxes, ocelots, wolves and cats too (and when that host dies it comes back out) -- and it shrinks away from fire, lava and loud sound. It holds still while you reach for it.',
	} },
	{ anchor: 'projecthero.guide.symbiote.host.agent_venom', entries: {
		'projecthero.guide.symbiote.creatures': 'Infested creatures & Symbiote Pets',
		'projecthero.guide.symbiote.creatures.wild': 'A Symbiote that takes over an animal -- a cow, pig, sheep, chicken, rabbit, fox, ocelot, wild wolf or stray cat -- turns it black with white eyes, makes it tougher, faster and harder-hitting, and sends it hunting players (even a cow bites, for 2.5 hearts). A sheep\'s wool turns black. Kill it and the Symbiote crawls back out.',
		'projecthero.guide.symbiote.creatures.pet': 'Your own tamed wolf or cat stays loyal. Bonded, sneak and right-click it with an empty hand while your suit is on to share your Symbiote (a Normal host pays 40 Biomass; Black Suit Spider-Man and Agent Venom pay nothing) -- or a free Symbiote may crawl onto it by itself, or you can tame an infested wolf or stray cat. It becomes a Symbiote Pet: 30% bigger and pitch black with white eyes, +20 max health, +4 attack damage, +8 armour, 25% faster, hard to knock back, and it regenerates (half a heart a second in a fight, a heart and a half out of one). It fights beside you with three powers -- Tendril Lash (a tendril yanks a target 4-14 blocks away in), Pounce (a leap onto a target up to 9 blocks away) and Spike Burst (spikes erupt all round it when it is crowded or badly hurt) -- and even a cat fights now. It never targets or hurts you, your squadmates or their pets.',
		'projecthero.guide.symbiote.creatures.release': 'To take it back, repeat the sneak + empty-hand right-click while suited. A bell, a goat horn or a sonic boom shakes the Symbiote out of a pet, and fire burns a Symbiote Pet 50% harder. A pet\'s Symbiote that came from the wild crawls free when it leaves (or the pet dies); one you shared simply dissolves.',
	} },
	{ anchor: 'projecthero.guide.symbiote.passive.squad', entries: {
		'projecthero.guide.symbiote.passive.pet': 'Symbiote Pet -- sneak + right-click your own tamed wolf or cat with an empty hand while suited to share the Symbiote with it (40 Biomass for a Normal host): it grows, turns black, regenerates and fights beside you with Tendril Lash, Pounce and Spike Burst. Do it again to take the Symbiote back.',
	} },
]);
