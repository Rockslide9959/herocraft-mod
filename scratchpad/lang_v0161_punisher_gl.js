// v0.16.1 Punisher armour enchanting + the Green Lantern Sniper Rifle construct.
// Run from the repo root: node scratchpad/lang_v0161_punisher_gl.js (safe to re-run: existing keys are rewritten in place).
require('./langset.js')([
	{
		anchor: 'projecthero.green_lantern.construct.emerald_warrior',
		entries: {
			'projecthero.green_lantern.construct.sniper_rifle': 'Sniper Rifle',
		},
	},
	{
		anchor: 'projecthero.green_lantern.construct.emerald_warrior.desc',
		entries: {
			'projecthero.green_lantern.construct.sniper_rifle.desc': 'A long rifle of light in your ring hand. Right-click to scope in, hold attack to charge (0.75s) and let go to fire: up to 22 damage, 120 blocks, 25 charge a shot. C again puts it away.',
		},
	},
	{
		anchor: 'hud.projecthero.green_lantern.cost',
		entries: {
			'hud.projecthero.green_lantern.sniper.ready': 'READY',
			'hud.projecthero.green_lantern.sniper.charging': 'CHARGING %s%%',
			'hud.projecthero.green_lantern.sniper.charged': 'FULL CHARGE',
			'hud.projecthero.green_lantern.sniper.cooldown': '%ss',
			'hud.projecthero.green_lantern.sniper.zoom': '%sx',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.constructs.body',
		entries: {
			'projecthero.guide.green_lantern.constructs.body': 'Shapes of glowing, translucent hard light, not free building blocks. Each costs charge up front, and the lasting ones keep costing upkeep while they stand. They build themselves out in a few ticks as a beam of light traces from your ring, and dissolve in green sparks when they end. Constructs have no time limits: they last until you press N, run out of charge or get broken, except the Sentry Turret (5 at once, 12 seconds each). Cooldowns apply again to the Sentry Turret, Hard-Light Wall, Battering Ram, Rescue Tether, Buzzsaw, Anvil Drop, Chain Snare and Emerald Warrior. Aimed constructs form on the open space in front of whatever you point at. Walls and Sentry Turrets settle onto the ground there, and nothing ever forms inside a player, you included. There are 20 of them, in four groups on the wheel. ATTACK: Energy Blade, Sentry Turret (a spinning core of light that only fires at hostiles it can see), Battering Ram, Buzzsaw (a spinning saw that ricochets between up to 5 enemies and flies back to you), Anvil Drop (a giant anvil of light falls on the spot you aim at), the Emerald Warrior (a knight of hard light with sword and shield that fights beside you until dismissed) and the Sniper Rifle (see below). DEFENCE: Hard-Light Wall, Containment Cage, Chain Snare (chains burst from the ground and pin every hostile within 10 blocks until you let them go, 4 charge a second each), Atmosphere Bubble and Rescue Tether (carries what you grab inside a bubble of hard light, for as long as you like). MOBILITY: Platform (a raised floor where you aim, or under your feet if you are falling and look down), Bridge, Stair/Ramp (real steps), Carry Platform (anyone can ride; press again to make it follow or park) and the Launch Pad (a springboard that flings anything on it about 25 blocks up; you and your squad land safely; 3 at once). UTILITY: Mining Drill, Lantern Light (your ring itself shines, with light that follows you wherever you go plus Night Vision, until you press it again) and the Hard-Light Tool Kit. Mining Drill and Energy Blade deploy for free as an equipped stance: a second press switches them on (only then do they cost anything, and you can see them on your hand), a third turns them off. N dismisses everything.',
			'projecthero.guide.green_lantern.sniper': 'Sniper Rifle',
			'projecthero.guide.green_lantern.sniper.body': 'Pick the Sniper Rifle on the construct wheel and press C: a long rifle of hard light forms round your ring fist, with a scope on top and a stock along your forearm (20 charge, then 1 charge a second while it is out). Hold right-click to scope in: the view zooms about 5x behind a green reticle of light. Hold attack to charge a shot; a ring of light round the reticle fills over 0.75 seconds. Let go to fire. The bolt hits instantly, up to 120 blocks away: 22 damage at full charge, about a third of that if you only tap, doubled during the Oath. Every shot costs 25 charge, with 1 second between shots. With too little charge the rifle will not fire. It works in Ring Flight too. While the rifle is out, attack and right-click belong to it, so press C again to put it away (free). N, death or an empty ring also end it.',
		},
	},
	{
		anchor: 'projecthero.guide.punisher.crafting',
		entries: {
			'projecthero.guide.punisher.crafting': 'Weapons are built from Weapon Parts (iron + copper + redstone), Gun Barrels and a Weapon Scope. The pistol is cheapest; the sniper is the most expensive. The Punisher Tactical Vest, Leggings and Boots (there is no helmet) protect between iron and diamond, and while you hold the power the full set adds 20% projectile-damage reduction, knockback resistance and a little less recoil. They take enchantments like any armour, at the enchanting table or an anvil: Protection of every kind, Unbreaking, Mending and Thorns, plus Swift Sneak on the leggings and Feather Falling, Depth Strider, Frost Walker and Soul Speed on the boots.',
		},
	},
]);
