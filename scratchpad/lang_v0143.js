// v0.14.3 lang: Green Lantern revamp, Max Steel mode label, Moon Knight speed / grapple / glide / jump / dash, Hulk
// rampage hits squad-mates. Run from the repo root: node scratchpad/lang_v0143.js
require('./langset.js')([
	// ---------------------------------------------------------------- Max Steel
	{ anchor: 'hud.projecthero.max_steel.turbo_pct', entries: {
		'hud.projecthero.max_steel.mode_label': 'TURBO MODE',
	} },
	// ---------------------------------------------------------------- Hulk
	{ anchor: 'projecthero.guide.hulk.control', entries: {
		'projecthero.guide.hulk.control.body': 'Only a Hulk who came out on his own (Rage hit 100, or he refused to die) fights you for control -- one you let out with H never does. You control him as long as he keeps hitting things. Go 8 seconds without landing a hit and control starts to slip: a key prompt appears (forward, left, back or right) -- press it in time to claw control back; the wrong key or none loses more. At 0 control the Hulk RAMPAGES on his own for 15 seconds: he hunts down the nearest living thing out in the open up to 100 blocks away (never anything down in caves), leaps to it, pounds it, and smashes the ground and anything soft in his way, and your abilities are locked out. A rampaging Hulk no longer knows friend from foe: his squad-mates are fair game too, and his blows hurt them like anyone else\'s (PvP permitting). Then you wrestle back control. Anyone riding him is thrown off.',
	} },
	// ---------------------------------------------------------------- Moon Knight
	{ anchor: 'projecthero.guide.moon_knight.suit', entries: {
		'projecthero.guide.moon_knight.suit.body': 'Press H: for a second and a half white bandages spiral up your body and the suit materialises one pixel at a time (you can\'t be hurt while it does), then the suit of Khonshu is on, with its cape. While it is on you heal half a heart every quarter second, hit 7 harder with your fists, move 30% faster, jump clean over two blocks, and take 20% less damage. Press H again and it dissolves away over another second and a half. The armour you were wearing is kept safe and handed back exactly as it was. Out of the suit, Khonshu will not let you fall: a single hit of more than 5 hearts, or being left under 4 hearts, starts the suit-up on its own (not in the 3 seconds after you take it off). The suit can\'t be taken off, dropped or stored, and none of Moon Knight\'s abilities or HUD work without it. While suited, his keys win over vanilla\'s creative hotbar save / load (C / X).',
	} },
	{ anchor: 'projecthero.moon_knight.move.grapple', entries: {
		'projecthero.moon_knight.move.grapple.desc': 'Fire a line up to 100 blocks. At a block you are pulled to it; at a mob, it is reeled in to you and stunned for 1.5 s (a boss is too heavy, so you are pulled to it instead).',
		'projecthero.moon_knight.move.dive_kick.desc': 'Fire the line into the mob you are looking at (up to 100 blocks), get pulled in and crash into it feet first (8 + knockback).',
		'projecthero.moon_knight.move.glide.desc': 'Jump, then hold Sneak in the air: you tip forward flat, the cape stretches between your arms and legs, and you glide -- fast -- (no elytra needed, no fall damage). Look down to dive even faster. Glides go further at night. Glide into a mob to kick it (12). Let go of Sneak, or land, to stop.',
		'projecthero.moon_knight.move.dash.desc': 'A burst of speed about 7 blocks exactly where you are aiming -- up, down or level -- with no fall damage at the end of it.',
	} },
	// ---------------------------------------------------------------- Green Lantern: HUD + wheel
	{ anchor: 'screen.projecthero.green_lantern.construct_wheel.hint', entries: {
		'screen.projecthero.green_lantern.construct_wheel': 'SHAPE A CONSTRUCT',
		'screen.projecthero.green_lantern.construct_wheel.cancel': 'Release here to cancel',
		'screen.projecthero.green_lantern.construct_wheel.hint': 'Point at a construct and release C, or click it. Esc cancels.',
		'screen.projecthero.green_lantern.category.attack': 'ATTACK',
		'screen.projecthero.green_lantern.category.defense': 'DEFENCE',
		'screen.projecthero.green_lantern.category.mobility': 'MOBILITY',
		'screen.projecthero.green_lantern.category.utility': 'UTILITY',
	} },
	{ anchor: 'hud.projecthero.green_lantern.barrier', entries: {
		'hud.projecthero.green_lantern.ring_charge': 'RING CHARGE',
		'hud.projecthero.green_lantern.tag.oath': 'OATH %ss',
		'hud.projecthero.green_lantern.tag.reciting': 'RECITING',
		'hud.projecthero.green_lantern.tag.flying': 'FLYING',
		'hud.projecthero.green_lantern.tag.boost': 'BOOST',
		'hud.projecthero.green_lantern.tag.suited': 'SUITED',
		'hud.projecthero.green_lantern.cost': '%s charge',
		'hud.projecthero.green_lantern.cooldown_short': 'Ready in %ss',
		'hud.projecthero.green_lantern.ring_remove': 'TAKING OFF THE RING',
		'message.projecthero.green_lantern.ring_remove_hold': 'Keep holding Sneak + N to take off the Power Ring...',
		'message.projecthero.green_lantern.ring_remove_cancel': 'The ring stays on.',
		'message.projecthero.green_lantern.ring_removed': 'You slide the Power Ring off your finger. Right-click it to put it back on -- or give it to someone worthy.',
		'message.projecthero.green_lantern.pad_limit': 'Up to 3 Launch Pads at once',
		'message.projecthero.green_lantern.no_targets': 'Nothing hostile close enough to chain',
		'entity.projecthero.hard_light_construct': 'Hard-Light Construct',
	} },
	// ---------------------------------------------------------------- Green Lantern: constructs
	{ anchor: 'projecthero.green_lantern.construct.hard_light_tools', entries: {
		'projecthero.green_lantern.construct.buzzsaw': 'Buzzsaw',
		'projecthero.green_lantern.construct.anvil_drop': 'Anvil Drop',
		'projecthero.green_lantern.construct.chain_snare': 'Chain Snare',
		'projecthero.green_lantern.construct.launch_pad': 'Launch Pad',
		'projecthero.green_lantern.construct.emerald_warrior': 'Emerald Warrior',
		'projecthero.green_lantern.construct.energy_blade.desc': 'A blade of light on your fist. First press equips it, the next switches it on (+13 melee, longer reach) and off.',
		'projecthero.green_lantern.construct.containment_cage.desc': 'Traps what you aim at in a closed box of light sized to fit it. 15 seconds, or until it breaks.',
		'projecthero.green_lantern.construct.sentry_turret.desc': 'A spinning core of light that shoots hostiles it can see (7 per shot). Up to 5 at once.',
		'projecthero.green_lantern.construct.battering_ram.desc': 'A ram head of light shoots along your aim and smashes the first creature it meets for 20, or bursts a door open.',
		'projecthero.green_lantern.construct.hard_light_wall.desc': 'A 4-high wall of light that settles on the ground where you aim. Very tough.',
		'projecthero.green_lantern.construct.platform.desc': 'A raised floor where you aim -- or right under your feet if you are falling and look down.',
		'projecthero.green_lantern.construct.bridge.desc': 'A 20-block, 3-wide bridge of light runs out from your feet.',
		'projecthero.green_lantern.construct.stair_ramp.desc': 'A 3-wide flight of real steps you can walk straight up.',
		'projecthero.green_lantern.construct.mining_drill.desc': 'A spinning drill on your fist. Equip, then press again to switch it on: it bores a 2x2 hole every half-second.',
		'projecthero.green_lantern.construct.lantern_light.desc': 'A floating orb of bright light where you aim, for a minute.',
		'projecthero.green_lantern.construct.atmosphere_bubble.desc': 'A bubble of air round you -- breathe anywhere, and so does your squad inside it.',
		'projecthero.green_lantern.construct.rescue_tether.desc': 'Grab what you aim at on a tether and carry it. Press again to throw it; N sets it down gently.',
		'projecthero.green_lantern.construct.carry_platform.desc': 'A bright 3x3 platform anyone can ride. Press again to make it follow your aim or park it.',
		'projecthero.green_lantern.construct.hard_light_tools.desc': 'A glowing pickaxe, axe and shovel that never wear out. Drop one and the whole kit fades.',
		'projecthero.green_lantern.construct.buzzsaw.desc': 'A spinning saw of light that ricochets between up to 5 enemies (14 each), then flies back to you.',
		'projecthero.green_lantern.construct.anvil_drop.desc': 'A giant anvil of light falls on the spot you aim at: 34 in the middle, 18 around it, and everything hit is slowed.',
		'projecthero.green_lantern.construct.chain_snare.desc': 'Chains of light burst from the ground and pin every hostile within 10 blocks in place for 5 seconds.',
		'projecthero.green_lantern.construct.launch_pad.desc': 'A springboard of light where you aim. Anything that steps on it is flung ~25 blocks up (you and your squad land safely).',
		'projecthero.green_lantern.construct.emerald_warrior.desc': 'A knight of hard light fights at your side for 30 seconds, hunting down whatever threatens you (12 per blow).',
	} },
	// ---------------------------------------------------------------- Green Lantern: guide
	{ anchor: 'projecthero.guide.green_lantern.tier', entries: {
		'projecthero.guide.green_lantern.body': 'A bonded Power Ring channels willpower into Ring Charge — a reserve shown as a percentage that fuels flight, ranged bolts, shields and hard-light constructs shaped on the fly. A permanent, passive Resistance II comes with the bond itself, suited or not. Everything the ring makes is now a visible shape of hard light — the fist that flies, the hammer that falls, missiles, a buzzsaw, an anvil, a giant hand, a knight of light — and the Lantern moves with it: every ability has its own animation. Green Lantern is the mod\'s toolbox hero: exceptionally versatile, with the Ring Charge pool and construct upkeep as the balancing axis — strong offense, defence and utility at once drains it fast.',
	} },
	{ anchor: 'projecthero.guide.green_lantern.ability.construct.desc', entries: {
		'projecthero.guide.green_lantern.ability.ring_bolt': 'Ring Bolt / Shift: Continuous Beam',
		'projecthero.guide.green_lantern.ability.ring_bolt.desc': 'A streak of hard light fired from the ring hand (18 damage, 40-block range, 10 charge). Shift channels a solid beam of light for 5 damage every 0.3 seconds (about 17 DPS) while held, draining charge as it goes, at the cost of some movement speed — your free hand grips your wrist to steady it.',
		'projecthero.guide.green_lantern.ability.construct_fist': 'Construct Fist / Shift: War Hammer Slam',
		'projecthero.guide.green_lantern.ability.construct_fist.desc': 'A giant fist of light flies from your ring (up to 20 blocks) and smashes the first thing in its path for 24 with heavy knockback, splashing half that onto anything next to it (1.5s cooldown, 30 charge). Shift raises a huge war hammer of light over your head and brings it down on the ground ahead: 26 damage and a knock-up to everything within 10 blocks (4s cooldown, 40 charge).',
		'projecthero.guide.green_lantern.ability.oath': 'Green Lantern\'s Light! / Shift: Emerald Gatling',
		'projecthero.guide.green_lantern.ability.oath.desc': 'Hold to recite the Oath, your ring fist raised before your face (release early and it\'s cancelled, no cost). Finish it and you punch the ring to the sky, empowered for 30 seconds: double ability damage, double melee, double construct strength — at double the Ring Charge cost for everything, plus a flat 10 charge/sec just to sustain it. An 80-second cooldown follows once it ends. Shift + hold X instead spins up the Emerald Gatling: six barrels of light form on your fist and hose the crosshair with 10 bolts a second (5 damage each, 2 charge a shot) for up to 8 seconds.',
		'projecthero.guide.green_lantern.ability.suit.desc': 'Forms or retracts the hard-light suit — it sweeps onto your body from the shoulders down one row of light at a time behind a bright green scan line, and dissolves from the feet back up when you take it off. Full diamond-level protection (20 armour, 2.0 toughness) plus a flat +10 melee damage bonus while worn. Costs 10 charge to summon, plus 1 charge every 5 seconds while worn — running out of charge forces it to retract. Shift casts Ring Scan: every living creature within 50 blocks glows through walls for 6 seconds, red for hostiles and green for everything else — visible only to you, on a 5-second cooldown.',
		'projecthero.guide.green_lantern.ability.construct': 'Deploy Construct / hold: construct wheel / Shift: Missile Barrage',
		'projecthero.guide.green_lantern.ability.construct.desc': 'Tap to shape the selected construct. Hold for half a second to open the construct wheel — a ring of every construct, grouped into Attack, Defence, Mobility and Utility, with the one under your cursor described in the middle — and pick a different one. Shift fires a Missile Barrage: six homing missiles of light streak out at the hostiles in front of you and burst for 12 each (8s cooldown, 60 charge). A construct still on cooldown is listed, with a countdown, just above the HUD.',
		'projecthero.guide.green_lantern.ability.giant_hand': 'Giant Hand',
		'projecthero.guide.green_lantern.ability.giant_hand.desc': 'H: a huge hand of light closes round whatever is under your crosshair (up to 28 blocks), lifts it in front of you and crushes it (6 damage every half-second). Press H again — or wait 6 seconds — to hurl it where you look; whatever it slams into takes 22, and so does it (10s cooldown, 50 charge). Shift + H still opens the power wheel.',
		'projecthero.guide.green_lantern.ability.dismiss': 'Dismiss Constructs / Shift + hold: take off the ring',
		'projecthero.guide.green_lantern.ability.dismiss.desc': 'N dismisses every construct you have up (it used to be Shift + C) — if you are carrying something on the Rescue Tether, the first press sets it down gently. Hold Sneak + N for 5 seconds to take the Power Ring off: the power leaves you and the ring goes back into your inventory, ready to be put on again or handed to someone else.',
		'projecthero.guide.green_lantern.constructs.body': 'Temporary shapes of glowing, translucent hard light, not free building blocks — each costs charge up front plus ongoing upkeep, builds itself out in a few ticks as a beam of light traces from your ring, flickers over its last three seconds, and dissolves in green sparks when it ends. Aimed constructs form on the open space in front of whatever you point at; Walls and Sentry Turrets settle onto the ground there, and nothing ever forms inside a player — you included. There are 19 of them, in four groups on the wheel. ATTACK: Energy Blade, Sentry Turret (5 live, 12 seconds each — a spinning core of light that only fires at hostiles it can see), Battering Ram, Buzzsaw (a spinning saw that ricochets between up to 5 enemies and flies back to you), Anvil Drop (a giant anvil of light falls on the spot you aim at) and the Emerald Warrior (a knight of hard light with sword and shield that fights beside you for 30 seconds). DEFENCE: Hard-Light Wall, Containment Cage, Chain Snare (chains burst from the ground and pin every hostile within 10 blocks for 5 seconds), Atmosphere Bubble and Rescue Tether. MOBILITY: Platform (a raised floor where you aim — or under your feet if you are falling and look down), Bridge, Stair/Ramp (real steps), Carry Platform (anyone can ride; press again to make it follow or park) and the Launch Pad (a springboard that flings anything on it about 25 blocks up — you and your squad land safely; 3 at once). UTILITY: Mining Drill, Lantern Light and the Hard-Light Tool Kit. Mining Drill and Energy Blade deploy for free as an equipped stance — a second press switches them on (only then do they cost anything, and you can see them on your hand), a third turns them off. N dismisses everything.',
	} },
]);
