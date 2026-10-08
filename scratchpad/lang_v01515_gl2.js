// v0.15.15 Green Lantern batch 2: tap/hold R + Shift+R Blast Wave, Z bubble, off-hand Power Battery charging, constructs
// without time limits or cooldowns, flight aura + sprint trail, HUD (no H/N boxes, Shift-move bars).
// Re-runnable. Run from the repo root: node scratchpad/lang_v01515_gl2.js
require('./langset.js')([
	{
		anchor: 'projecthero.guide.green_lantern.ability.ring_bolt.desc',
		entries: {
			'projecthero.guide.green_lantern.ability.ring_bolt': 'Ring Bolt (tap) / Beam (hold) / Shift: Blast Wave',
			'projecthero.guide.green_lantern.ability.ring_bolt.desc': 'Tap R to fire a streak of hard light from the ring hand (18 damage, 40-block range, 10 charge). Hold R to channel a solid beam instead: 5 damage every 0.3 seconds (about 17 DPS) for as long as you hold it, draining charge as it goes and slowing you a little; your free hand grips your wrist to steady it. Sneak + R sends a Blast Wave: a wall of green light rolls out over the 120 degrees in front of you, 8 blocks deep, throwing back everything it hits for 18 damage with Slowness III for 4 seconds (7s cooldown, 150 charge). Your squad and pets are never hit.',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.ability.shield.desc',
		entries: {
			'projecthero.guide.green_lantern.ability.shield': 'Bubble (hold) / Shift: Protective Dome',
			'projecthero.guide.green_lantern.ability.shield.desc': 'Hold Z to wrap yourself in a bubble of hard light. While it is up, every arrow, bullet and blow struck in person is stopped dead, from any side, and projectiles that fly into it are destroyed. Explosions, fire, magic and falls still get through. It costs 40 charge a second for as long as you hold Z, and drops by itself when the ring runs dry. Sneak + Z expands a 10-block dome out from you over a second and a half instead, pushing out anyone nearby who is not in your squad and keeping them out while it is up. Sneak + Z again dismisses it early (8-second cooldown). The dome has an uptime meter above your ability keys: up to 22 seconds of continuous use, refilling once you stop.',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.constructs.body',
		entries: {
			'projecthero.guide.green_lantern.constructs.body': 'Shapes of glowing, translucent hard light, not free building blocks. Each costs charge up front, and the lasting ones keep costing upkeep while they stand. They build themselves out in a few ticks as a beam of light traces from your ring, and dissolve in green sparks when they end. Constructs have no time limits and no cooldowns: they last until you press N, run out of charge or get broken. Only the Sentry Turret keeps its limits (5 at once, 12 seconds each, a cooldown). Aimed constructs form on the open space in front of whatever you point at. Walls and Sentry Turrets settle onto the ground there, and nothing ever forms inside a player, you included. There are 19 of them, in four groups on the wheel. ATTACK: Energy Blade, Sentry Turret (a spinning core of light that only fires at hostiles it can see), Battering Ram, Buzzsaw (a spinning saw that ricochets between up to 5 enemies and flies back to you), Anvil Drop (a giant anvil of light falls on the spot you aim at) and the Emerald Warrior (a knight of hard light with sword and shield that fights beside you until dismissed). DEFENCE: Hard-Light Wall, Containment Cage, Chain Snare (chains burst from the ground and pin every hostile within 10 blocks until you let them go, 4 charge a second each), Atmosphere Bubble and Rescue Tether (carries what you grab inside a bubble of hard light, for as long as you like). MOBILITY: Platform (a raised floor where you aim, or under your feet if you are falling and look down), Bridge, Stair/Ramp (real steps), Carry Platform (anyone can ride; press again to make it follow or park) and the Launch Pad (a springboard that flings anything on it about 25 blocks up; you and your squad land safely; 3 at once). UTILITY: Mining Drill, Lantern Light (your ring itself shines, with light that follows you wherever you go plus Night Vision, until you press it again) and the Hard-Light Tool Kit. Mining Drill and Energy Blade deploy for free as an equipped stance: a second press switches them on (only then do they cost anything, and you can see them on your hand), a third turns them off. N dismisses everything.',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.battery.body',
		entries: {
			'projecthero.guide.green_lantern.battery.body': 'Craft one (Lantern Core + 4 Emerald Blocks + 2 Amethyst Blocks + an Ender Eye + an Echo Shard): a green lantern with a glowing emblem behind glass. Carried in your hand, it hangs from its handle and swings as you move. To recharge, hold it in your OFF hand and Sneak + right-click. You hold the battery out, press your ring against it and recite the Oath, one line at a time on screen, while the battery glows brighter and brighter. You can look around but cannot move. Finish all four lines (6 seconds) and your ring is full, with a flash of light. Taking any damage, using an ability or letting go of the battery stops it, with nothing gained. This is the only way to recharge: the ring never regenerates charge on its own. A placed battery is just a lantern.',
			'message.projecthero.green_lantern.battery_how': 'Hold the Power Battery in your off hand and Sneak + right-click to charge your ring',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.flight.body',
		entries: {
			'projecthero.guide.green_lantern.flight.body': 'Double-tap Space while airborne to take off, or land to end it automatically, the same gesture Thor and Iron Man use. Ring Flight is directional: hold forward and you fly exactly where you are looking, climbing or diving with your aim, while strafing, Space (up) and Sneak (down) still work like creative flight, and letting go of everything eases you into a steady hover. The whole time you are in the air, a faint skin of green light hugs your body, following every move. Costs a flat 1 charge/sec while flying. Sprint to fly much faster at 40 charge/sec: a streamer of hard light trails out behind you from your feet, with two strands of light twisting round it, and the glow gets a little brighter. Ends safely on depletion with a brief controlled descent instead of a plummet.',
		},
	},
	{
		anchor: 'projecthero.green_lantern.construct.containment_cage.desc',
		entries: {
			'projecthero.green_lantern.construct.containment_cage.desc': 'Traps what you aim at in a closed box of light sized to fit it, until you let it go (N) or it breaks.',
			'projecthero.green_lantern.construct.chain_snare.desc': 'Chains of light burst from the ground and pin every hostile within 10 blocks in place until you press N (4 charge a second each).',
			'projecthero.green_lantern.construct.emerald_warrior.desc': 'A knight of hard light fights at your side until you dismiss it, hunting down whatever threatens you (12 per blow).',
		},
	},
	{
		// v0.15.15: Nova got Green Lantern's trail and body glow in his colours
		anchor: 'projecthero.guide.nova.flight.body',
		entries: {
			'projecthero.guide.nova.flight.body': 'Double-tap jump in the air while suited. 20 blocks a second, 45 sprinting: look where you want to go, Space and Sneak to rise and sink, let go to hover. While you fly, a faint skin of golden light hugs your uniform, and a streamer of gold light with two cyan strands twisting round it trails from your feet. Both grow brighter when you sprint. Touching the ground lands you.',
		},
	},
	{
		anchor: 'hud.projecthero.green_lantern.tag.reciting',
		entries: {
			'hud.projecthero.green_lantern.tag.charging': 'CHARGING',
			'hud.projecthero.green_lantern.shield': 'BUBBLE',
		},
	},
]);
