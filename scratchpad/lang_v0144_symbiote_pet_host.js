// v0.14.4 lang: Symbiote pet hosts -- transform in combat, detransform out of it, Latching Bite, Guardian Shroud.
// Re-runnable. Run from the repo root: node scratchpad/lang_v0144_symbiote_pet_host.js
require('./langset.js')([
	{ anchor: 'message.projecthero.symbiote.pet_too_weak', entries: {
		'message.projecthero.symbiote.pet_shared': 'Your Symbiote spills into %s -- it is its host now, and still yours. Whenever there is a fight, it takes the black form.',
		'message.projecthero.symbiote.pet_bonded': 'A Symbiote has bonded with %s! It is still your pet -- loyal, obedient -- but when it fights, it transforms.',
		'message.projecthero.symbiote.pet_shroud': '%s wraps you in its Symbiote!',
	} },
	{ anchor: 'projecthero.guide.symbiote.creatures.wild', entries: {
		'projecthero.guide.symbiote.creatures.pet': 'Your own tamed wolf or cat can become a Symbiote\'s host and stay loyal. A free Symbiote goes looking for tamed pets before anything else, and springs onto one it reaches; you can also share yours (sneak and right-click your pet with an empty hand while your suit is on -- a Normal host pays 40 Biomass; Black Suit Spider-Man and Agent Venom pay nothing), or tame an infested wolf or stray cat. You are told when it happens. A Symbiote Pet still sits, stands, follows you and teleports to you, attacks what you attack and defends you, and never targets or hurts you, your squadmates or their pets.',
		'projecthero.guide.symbiote.creatures.form': 'Out of a fight it looks like its normal self -- a few black blotches and the odd black drip give it away. The moment it fights (it picks a target, it is hurt, or you are fighting or badly hurt nearby) it transforms: the black skin spreads over it pixel by pixel, it grows 30%, white eyes open, and it gains +20 max health, +4 attack damage, +8 armour, 25% speed, knockback resistance and regeneration (three quarters of a heart a second). About 9 seconds after the fight ends the skin recedes and it shrinks back (it still heals slowly, a quarter heart a second).',
		'projecthero.guide.symbiote.creatures.powers': 'Transformed, it has powers of its own: Latching Bite (a lunging bite whose tendrils pin the target -- slowed and weakened -- while the Symbiote feeds and heals it), Tendril Lash (a tendril yanks a target 4-14 blocks away in), Pounce (a leap onto a target up to 9 blocks away), Spike Burst (spikes erupt all round it when it is crowded or badly hurt) and Guardian Shroud (when you drop under 40% health it throws a tendril to you and wraps you in the Symbiote -- Absorption II and Resistance -- hurling attackers back; every 45 seconds). Even a cat fights now.',
	} },
	{ anchor: 'projecthero.guide.symbiote.passive.squad', entries: {
		'projecthero.guide.symbiote.passive.pet': 'Symbiote Pet -- sneak + right-click your own tamed wolf or cat with an empty hand while suited to share the Symbiote with it (40 Biomass for a Normal host): it stays your pet, and whenever it fights it transforms -- black, bigger, stronger, with Latching Bite, Tendril Lash, Pounce, Spike Burst and a Guardian Shroud for you when you are low. Do it again to take the Symbiote back.',
	} },
]);
