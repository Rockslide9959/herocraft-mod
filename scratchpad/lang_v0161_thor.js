// v0.16.1 Thor: the thunderstorm buff, the Mjolnir + Stormbreaker dual-wield combo, the Twin Thunder Strike finisher
// and Thunder Field (Shift+G). Idempotent -- re-run after a merge conflict. From the repo root:
//   node scratchpad/lang_v0161_thor.js
require('./langset.js')([
	{
		anchor: 'projecthero.thor.ability.chain_lightning',
		entries: {
			'projecthero.thor.ability.thunder_field': 'Thunder Field',
			'projecthero.thor.ability.lightning_strike_with_field': 'Lightning Strike  /  Shift: Thunder Field',
			'projecthero.thor.ability.twin_thunder': 'Twin Thunder Strike',
			'projecthero.thor.twin_thunder.recharging': 'Twin Thunder Strike recharging (%ss)',
			'projecthero.thor.thunder_field.no_ground': 'No ground in range for a Thunder Field',
			'projecthero.thor.thunder_field.no_weapon': 'Hold Mjolnir or Stormbreaker to call a Thunder Field',
		},
	},
	{
		anchor: 'hud.projecthero.thor.storm_energy',
		entries: {
			'hud.projecthero.thor.storm_charged': 'Storm-Charged +%s%%',
		},
	},
	{
		anchor: 'projecthero.guide.thor.ability.lightning_strike',
		entries: {
			'projecthero.guide.thor.ability.thunder_field': 'Shift+G: hurl the weapon in your hand (with Mjolnir and Stormbreaker both in hand, the main-hand one) down at the ground you are aiming at, up to 24 blocks away. It slams in and stays there while a bolt hits it and the ground around it is covered in lightning for 4 seconds: a 5-block field that shocks every hostile mob standing in it for 4 damage every half second and slows it. It never touches you, your squad or anyone\'s pets, sets nothing on fire and breaks no blocks. Then the weapon flies back to your hand (press R to call it back early). 15-second cooldown -- the thin amber bar along the bottom of the G box.',
		},
	},
	{
		anchor: 'projecthero.guide.thor.perk.squad',
		entries: {
			'projecthero.guide.thor.perk.storm': 'Storm-Charged: while it is thundering where you stand, everything you hit hits 30% harder',
		},
	},
	{
		anchor: 'projecthero.guide.thor.combo.finisher',
		entries: {
			'projecthero.guide.thor.dual': 'Mjolnir and Stormbreaker Together',
			'projecthero.guide.thor.dual.body': 'With Mjolnir in one hand and Stormbreaker in the other, your left-click becomes a flowing 4-hit dual-weapon combo: 1, Mjolnir\'s overhead hammer blow; 2, Stormbreaker\'s flat axe sweep; 3, a cross-body double swing with both, cutting down in an X; 4, a leaping double slam -- you hop up with both weapons overhead and drive them down as you land. Each hit must land within 1 second of your swing recharging, or the combo starts over. Every hit deals your weapon\'s normal damage. The four pips above your ability keys show how far into it you are.',
			'projecthero.guide.thor.dual.twin_thunder': 'Twin Thunder Strike: land the 4th hit in time and, as you come down, you raise both weapons to the sky -- lightning strikes both of them and arcs between them -- then slam them together into the ground. 20 damage to every enemy within 6 blocks, thrown up into the air, the ground cracking open, then a ring of lightning bolts around you that hits the hostile mobs out at the edge. It never hurts you, your squad or anyone\'s pets. 20-second cooldown (the pips go grey with the seconds left); only while you hold both weapons.',
			'projecthero.guide.thor.storm': 'Storm-Charged',
			'projecthero.guide.thor.storm.body': 'The god of thunder draws on the storm. While it is thundering where you stand -- anywhere it rains or snows, under a roof or not -- everything you hit hits 30% harder: Mjolnir and Stormbreaker, swung or thrown, every lightning bolt and shockwave, the Twin Thunder Strike and the Thunder Field. Lightning crackles over you while it lasts, and "Storm-Charged +30%" shows above your ability keys.',
		},
	},
]);
