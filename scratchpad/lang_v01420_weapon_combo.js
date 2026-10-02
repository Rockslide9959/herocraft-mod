// v0.14.20: Mjolnir / Stormbreaker 3-hit melee combo -- Guidebook section in the Thor chapter.
// Re-runnable: only ever sets keys. Run from the repo root: node scratchpad/lang_v01420_weapon_combo.js
require('./langset.js')([
	{ anchor: 'projecthero.guide.thor.stormbreaker.weapon', entries: {
		'projecthero.guide.thor.combo': 'Melee Combo',
		'projecthero.guide.thor.combo.body': 'Mjolnir and Stormbreaker each have a 3-hit melee combo (v0.14.20). Land full-strength hits in a row: 1 is a flat swing, 2 a backhand on the other diagonal, 3 a heavy overhead slam. Each hit must land within 1.25 seconds of your swing recharging, or the combo starts over. A weak, spam-clicked hit never advances it, and a miss does not count.',
		'projecthero.guide.thor.combo.finisher': 'The slam is the finisher: +50% damage on the target. With Mjolnir it cracks like thunder, a shockwave hitting everything within 3 blocks of the target. With Stormbreaker it cleaves through everything within 4.5 blocks in front of you. Neither touches you, your pets or your squad. Then the combo starts over.',
	} },
]);
