// v0.15.3 Thor: the N weapon selector (Mjolnir / Stormbreaker Active or Inactive for R) and the dropped Stormbreaker
// entity. Run from the repo root: node scratchpad/lang_v0153_thor.js
require('./langset.js')([
	{
		anchor: 'message.projecthero.recall.none.stormbreaker',
		entries: {
			'message.projecthero.recall.none_active': 'Both weapons are Inactive - press N to choose which one answers R.',
			'message.projecthero.thor.weapon_select.active': '%s is Active - R can call it.',
			'message.projecthero.thor.weapon_select.inactive': '%s is Inactive - R will not call it.',
			'screen.projecthero.thor_weapons.title': "Thor's Weapons",
			'screen.projecthero.thor_weapons.intro': 'Choose which weapons answer your call (R). An Inactive weapon is never called, even if it is the closer one.',
			'screen.projecthero.thor_weapons.active': 'Active',
			'screen.projecthero.thor_weapons.inactive': 'Inactive',
			'screen.projecthero.thor_weapons.tip_off': 'Click to make %s Inactive: R will no longer call it.',
			'screen.projecthero.thor_weapons.tip_on': 'Click to make %s Active: R can call it again.',
			'screen.projecthero.thor_weapons.bound': 'Bound to you',
			'screen.projecthero.thor_weapons.unbound': 'None bound to you',
			'screen.projecthero.thor_weapons.none_active': 'Both are Inactive: R will not call either weapon.',
			'screen.projecthero.thor_weapons.footer': 'N to close',
			'projecthero.thor.ability.weapon_select': 'Weapon Select',
		},
	},
	{
		anchor: 'projecthero.guide.thor.ability.chain_lightning',
		entries: {
			'projecthero.guide.thor.ability.weapon_select': 'Opens a small screen with Mjolnir and Stormbreaker, each with an Active / Inactive switch. Only Active weapons answer R: switch one off and R never calls it, even when it is the closer or the only one bound to you -- the other is then called on its own. Switch both off and R calls nothing (it tells you so). Both start Active, and your choice is kept through relogging and death.',
		},
	},
	{
		anchor: 'projecthero.guide.thor.stormbreaker.recall',
		entries: {
			'projecthero.guide.thor.stormbreaker.dropped': 'On the ground (v0.15.3): like Mjolnir, a dropped Stormbreaker -- Q, dragged out of your inventory, lost when you die, or left behind by a throw with nobody to return to -- lies in the world as the axe itself. It never despawns or burns (in lava it floats up to the surface), only the worthy can lift it, by right-clicking it -- anyone else just hears it clang -- and R still calls it home from where it lies.',
		},
	},
]);
