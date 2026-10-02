// v0.14.19: Stormbreaker lang (item names, tooltips, messages, entity, Guidebook section in the Thor chapter).
// Re-runnable: only ever sets keys. Run from the repo root: node scratchpad/lang_v01419_stormbreaker.js
require('./langset.js')([
	{ anchor: 'item.projecthero.mjolnir.lift_hint', entries: {
		'item.projecthero.stormbreaker': 'Stormbreaker',
		'item.projecthero.stormbreaker_thrown': 'Stormbreaker (in flight)',
		'item.projecthero.unforged_stormbreaker': 'Unforged Stormbreaker',
		'entity.projecthero.stormbreaker': 'Stormbreaker',
		'item.projecthero.stormbreaker.flavor.line1': 'A king\'s weapon, forged in',
		'item.projecthero.stormbreaker.flavor.line2': 'the fires beneath the Nether.',
		'item.projecthero.stormbreaker.ability.throw': 'Right-click: throw (it always returns)',
		'item.projecthero.stormbreaker.ability.bifrost': 'Shift + Right-click: open the Bifrost',
		'item.projecthero.stormbreaker.ability.bifrost2': '  Teleport up to 256 blocks (30 s)',
		'item.projecthero.stormbreaker.thor_weapon': 'Works as Thor\'s weapon for his powers',
		'item.projecthero.stormbreaker.worthy_only': 'Its powers answer only the worthy.',
		'item.projecthero.unforged_stormbreaker.line1': 'Throw it into lava in the Nether',
		'item.projecthero.unforged_stormbreaker.line2': 'to forge it into Stormbreaker.',
		'item.projecthero.unforged_stormbreaker.line3': 'Anywhere else, lava is too cold.',
		'message.projecthero.stormbreaker.not_hot_enough': 'Only the heart of the Nether burns hot enough to forge it',
		'message.projecthero.bifrost.no_target': 'The Bifrost needs a block to land on, within 256 blocks',
		'message.projecthero.bifrost.no_room': 'No room to land there',
		'message.projecthero.bifrost.carried': '%s carried you across the Bifrost',
	} },
	{ anchor: 'projecthero.guide.thor.armour.body', entries: {
		'projecthero.guide.thor.stormbreaker': 'Stormbreaker',
		'projecthero.guide.thor.stormbreaker.intro': 'Thor\'s second weapon (v0.14.19). Anyone can swing it -- 14 damage at 0.9 attacks a second, three more than Mjolnir -- but only the worthy can use its powers. In anyone else\'s hands a right-click just clangs.',
		'projecthero.guide.thor.stormbreaker.recipe': 'Recipe: craft an Unforged Stormbreaker. Top row: Netherite Ingot, Nether Star, Netherite Ingot. Middle row: Netherite Ingot, Blaze Rod, Netherite Ingot. Bottom row: a Blaze Rod in the middle. That is 4 netherite ingots and a Wither kill.',
		'projecthero.guide.thor.stormbreaker.forge': 'Forging: the cold axe has to be forged in the Nether. Throw it into lava there and leave it for 10 seconds -- flames and smoke rise and the forge roars louder as it heats. Pull it out (or let it drift out) and the heat is lost. When it is done the lava bursts and throws the finished Stormbreaker up toward the nearest player. Lava anywhere else is not hot enough. Neither item ever burns, and a freshly forged Stormbreaker never despawns.',
		'projecthero.guide.thor.stormbreaker.throw': 'Right-click: throw it. It flies about 40 blocks, cuts through up to 4 enemies for 20 damage each, calls lightning down on the first, then always flies back on its own -- no key needed. It lands in your empty main hand, else your inventory, else at your feet, and flies straight past your squadmates.',
		'projecthero.guide.thor.stormbreaker.bifrost': 'Shift + Right-click: open the Bifrost. Look at a block up to 256 blocks away and a rainbow bridge carries you there, along with anyone standing within 3 blocks of you (they can sneak to stay behind). Same dimension only; it finds a safe spot on or above the block. 30-second cooldown, which also holds the throw. Shift + Right-clicking a block within arm\'s reach does nothing, as with any weapon.',
		'projecthero.guide.thor.stormbreaker.weapon': 'In a worthy hand it counts as Thor\'s weapon: Lightning Strike, the Lightning Beam, God of Thunder\'s Wrath, Thunderclap, Chain Lightning and flight all work with Stormbreaker as well as Mjolnir. Calling, throwing, binding and Hammer Volley stay Mjolnir\'s.',
		'projecthero.guide.thor.flight.body': 'Double-tap Jump while holding Mjolnir (or Stormbreaker) to fly.',
	} },
]);
