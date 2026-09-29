// v0.13.21 Moon Knight: new key layout (R Darts, G Grapple Kick, X Dash, Z Khonshu, C Truncheon, V Alters), the Cape
// off the keys (jump + hold Sneak = glide, hold right click = block), the suit's regen / +7 melee / -20% damage,
// auto suit-up, per-alter suits, new numbers. Re-run after merging:  node scratchpad/lang_v01321_moonknight.js
require('./langset.js')([
	{ anchor: 'projecthero.moon_knight.ability.khonshu', entries: {
		'projecthero.moon_knight.ability.kick': 'Grapple Kick',
		'projecthero.moon_knight.ability.dash': 'Dash',
	} },
	{ anchor: 'hud.projecthero.moon_knight.glide', entries: {
		'hud.projecthero.moon_knight.cape_block': 'BLOCK',
	} },
	{ anchor: 'message.projecthero.moon_knight.suited', entries: {
		'message.projecthero.moon_knight.auto_suit': 'Khonshu will not let his fist fall -- the suit answers',
	} },
	{ anchor: 'projecthero.moon_knight.move.shadow_step.desc', entries: {
		'projecthero.moon_knight.move.dash': 'Dash',
		'projecthero.moon_knight.move.dash.desc': 'A burst of speed about 7 blocks the way you are looking, with no fall damage at the end of it.',
	} },
	{ anchor: null, entries: {
		'message.projecthero.moon_knight.acquired_hint': 'Press H to wear the suit of Khonshu. R, G, X, Z, C and V hold his abilities (tap, hold, or with Sneak). Jump and hold Sneak to glide; hold right click to block with the cape.',
		'message.projecthero.moon_knight.yank_heavy': 'Too heavy to reel in -- the line pulls you to it instead',
		'projecthero.guide.moon_knight.suit.body': 'Press H: for a second and a half white bandages spiral up your body and the suit materialises one pixel at a time (you can\'t be hurt while it does), then the suit of Khonshu is on, with its cape. While it is on you heal half a heart every quarter second, hit 7 harder with your fists, and take 20% less damage. Press H again and it dissolves away over another second and a half. The armour you were wearing is kept safe and handed back exactly as it was. Out of the suit, Khonshu will not let you fall: a single hit of more than 5 hearts, or being left under 4 hearts, starts the suit-up on its own (not in the 3 seconds after you take it off). The suit can\'t be taken off, dropped or stored, and none of Moon Knight\'s abilities or HUD work without it. While suited, his keys win over vanilla\'s creative hotbar save / load (C / X).',
		'projecthero.guide.moon_knight.alters.body': 'Marc Spector, the fighter: +4 armour, +20% melee damage, harder to knock back. Steven Grant, the thinker: 15% less melee damage taken, extra loot from mobs, better prices from villagers. Jake Lockley, the shadow: faster while sneaking, mobs notice you from half as far away, +50% damage attacking from behind. Each wears his own suit -- Marc\'s white and gold armour, Steven\'s white Mr. Knight suit, Jake\'s dark suit -- and changing alter in the suit rematerialises the new one over the old, pixel by pixel.',
		'projecthero.guide.moon_knight.controls.body': 'Most keys have up to three moves: tap it, hold it, or press it while sneaking. The cape needs no key: jump and hold Sneak to glide, hold right click to block. They only work in the suit.',
		'projecthero.moon_knight.move.darts.desc': 'Throw a spinning crescent dart (15). At night it gently homes on a nearby hostile mob, and if it misses it curves back to you like a boomerang. Ready again after a second.',
		'projecthero.moon_knight.move.grapple': 'Grappling Line',
		'projecthero.moon_knight.move.grapple.desc': 'Fire a line up to 60 blocks. At a block you are pulled to it; at a mob, it is reeled in to you and stunned for 1.5 s (a boss is too heavy, so you are pulled to it instead).',
		'projecthero.moon_knight.move.dive_kick': 'Grapple Kick',
		'projecthero.moon_knight.move.dive_kick.desc': 'Fire the line into the mob you are looking at (up to 60 blocks), get pulled in and crash into it feet first (8 + knockback).',
		'projecthero.moon_knight.move.shadow_step.desc': 'Blink 5 blocks backwards into a puff of shadow and vanish for 3 s.',
		'projecthero.moon_knight.move.glide': 'Cape Glide',
		'projecthero.moon_knight.move.glide.desc': 'Jump, then hold Sneak in the air: you tip forward flat, the cape stretches between your arms and legs, and you glide (no elytra needed, no fall damage). Glides go further at night. Glide into a mob to kick it (12). Let go of Sneak, or land, to stop.',
		'projecthero.moon_knight.move.shroud': 'Cape Block',
		'projecthero.moon_knight.move.shroud.desc': 'Hold right click with an empty hand (or the Truncheon): the cape wraps around you and you take 30% less damage for as long as you hold it, but you move slowly.',
		'projecthero.moon_knight.move.alter.desc': 'Marc -> Steven -> Jake -> Marc. The new alter\'s suit rematerialises over the old one.',
	} },
]);
