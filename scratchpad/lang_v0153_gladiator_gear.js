// v0.15.3: Gladiator Hulk gear -- items, the Gladiator Gear screen, messages and the guidebook section.
// Run from the repo root: node scratchpad/lang_v0153_gladiator_gear.js
require('./langset.js')([
	{
		anchor: 'item.projecthero.gamma_serum.desc2',
		entries: {
			'item.projecthero.gladiator_helmet': 'Gladiator Helmet',
			'item.projecthero.gladiator_pauldron': 'Gladiator Pauldron',
			'item.projecthero.gladiator_harness': 'Gladiator Chest Harness',
			'item.projecthero.gladiator_bracers': 'Gladiator Bracers',
			'item.projecthero.gladiator_kilt': 'Gladiator War Kilt',
			'item.projecthero.gladiator_hammer': 'Gladiator Hammer',
			'item.projecthero.gladiator_axe': 'Gladiator Axe',
			'item.projecthero.gladiator_gear.tooltip': 'Hulk gear from the arenas of Sakaar. Tap N as Banner to put it in your Gladiator Gear.',
			'item.projecthero.gladiator_gear.tooltip2': 'All seven pieces: the Hulk comes out as Gladiator Hulk and takes 10% less damage.',
		},
	},
	{
		anchor: 'message.projecthero.hulk.calm_already',
		entries: {
			'message.projecthero.hulk.gladiator.locked': 'Your gladiator gear is locked on while you are the Hulk.',
			'message.projecthero.hulk.gladiator.no_power': 'Only a Gamma player can wear gladiator gear.',
		},
	},
	{
		anchor: 'screen.projecthero.stark_gear.title',
		entries: {
			'screen.projecthero.gladiator_gear.title': 'Gladiator Gear',
			'screen.projecthero.gladiator_gear.slot.helmet': 'Helmet',
			'screen.projecthero.gladiator_gear.slot.pauldron': 'Pauldron',
			'screen.projecthero.gladiator_gear.slot.harness': 'Harness',
			'screen.projecthero.gladiator_gear.slot.bracers': 'Bracers',
			'screen.projecthero.gladiator_gear.slot.kilt': 'War Kilt',
			'screen.projecthero.gladiator_gear.slot.hammer': 'Hammer',
			'screen.projecthero.gladiator_gear.slot.axe': 'Axe',
			'screen.projecthero.gladiator_gear.equipped': '%s/7 equipped',
			'screen.projecthero.gladiator_gear.hint_partial': 'Equip all 7 pieces to fight as Gladiator Hulk.',
			'screen.projecthero.gladiator_gear.hint_full': 'Full kit! You change into Gladiator Hulk: -10% damage.',
			'screen.projecthero.gladiator_gear.slot_tip': 'Only the %s goes here.',
		},
	},
	{
		anchor: 'projecthero.guide.hulk.riding.body',
		entries: {
			'projecthero.guide.hulk.gladiator': 'Gladiator Hulk',
			'projecthero.guide.hulk.gladiator.body': 'Dress the Hulk for the arenas of Sakaar. Craft the seven pieces of gladiator gear on a crafting table: the Gladiator Helmet, Pauldron, Chest Harness, Bracers and War Kilt (iron, gold, leather and red wool), and the end-game Gladiator Hammer and Gladiator Axe (netherite and blocks of iron). As Banner, tap N to open the Gladiator Gear screen and put each piece in its own slot (each slot takes only its piece; the screen shows how many of the 7 you have on). With all seven in, the Hulk comes out as Gladiator Hulk: a Sakaaran helmet with a red crest, war paint, a big pauldron on his left shoulder, a chest harness, bracers and a war kilt, the hammer in his right hand and the axe in his left. Gladiator Hulk takes 10% less damage, on top of everything the Hulk already shrugs off. The gear is locked on while you are the Hulk -- change back to Banner to take any of it off -- and it stays with you when you die.',
		},
	},
]);
