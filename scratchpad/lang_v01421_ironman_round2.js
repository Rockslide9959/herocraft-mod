// v0.14.21 Iron Man "round two": Homing Missiles weapon-wheel option (name, wheel description, lock-on messages),
// the Micro-Missiles wheel description (they are dumb-fire), and the guidebook "Suit screens" sentence.
// Idempotent (langset rewrites keys in place).
//   node scratchpad/lang_v01421_ironman_round2.js
require('./langset.js')([
	{ anchor: 'hud.projecthero.ironman.ability.micro_missiles', entries: {
		'hud.projecthero.ironman.ability.homing_missiles': 'Homing Missiles',
	} },
	{ anchor: 'screen.projecthero.weapon_wheel.desc.micro_missiles', entries: {
		'screen.projecthero.weapon_wheel.desc.micro_missiles': 'Dumb-fire volley where you aim',
		'screen.projecthero.weapon_wheel.desc.homing_missiles': '4 missiles lock onto a target',
	} },
	{ anchor: 'message.projecthero.ironman.no_missiles', entries: {
		'message.projecthero.ironman.homing_locked': 'Homing Missiles locked on: %s',
		'message.projecthero.ironman.homing_no_lock': 'No lock -- missiles will seek the nearest hostile',
	} },
	{ anchor: 'projecthero.guide.iron_man.screens.body', entries: {
		'projecthero.guide.iron_man.screens.body': 'With the faceplate closed the helmet HUD frames your view: suit name, ENERGY and INTEGRITY with exact values top-left, extra meters only when they matter (air, heat, flight burst, overload, missile reload) and an ability strip above the hotbar with each key, its icon and a cooldown sweep -- hold Left Alt to read the slot names. Call Armour shows every suit as a card with a turning 3D preview, charge, integrity and where it is (arrow keys + Enter work too). The Suit Platform previews the stored suit with full charge / integrity bars and the platform reserve. The Stark Fabricator has a tab per suit piece and a checklist of what you have against what the piece needs. A Blank Blueprint opens the Mark I -> Mark VII track: lit marks can be stamped, locked ones say which suit to finish first. The Mark VII weapon wheel is a ring of wedges -- point at one to read what it does. Its Homing Missiles option fires four missiles that lock on to whatever is nearest your crosshair (within 30 blocks); Micro-Missiles fly straight where you aim. Every suit\'s eyes, arc reactor and palm repulsors glow in the dark, and the Mark V\'s X blades slide out of its gauntlets.',
	} },
]);
