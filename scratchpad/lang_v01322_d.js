// v0.13.22 mutation revamp, batch D lang: Telekinesis (juggling), Teleportation (Bamf Strike / Swap), Invisibility /
// Light (refraction), Shadow (shadow travel), Gravity (Invert / Heavy Ground), Magnetism (Hover / Disarm).
// Run from the repo root: node scratchpad/lang_v01322_d.js
const P10 = 'projecthero.power.power_10_telekinesis';
const P11 = 'projecthero.power.power_11_teleportation';
const P15 = 'projecthero.power.power_15_invisibility_light_manipulation';
const P19 = 'projecthero.power.power_19_shadow_manipulation';
const P23 = 'projecthero.power.power_23_gravity_manipulation';
const P26 = 'projecthero.power.power_26_magnetic_manipulation';

require('./langset.js')([
	// ------------------------------------------------------------------ 10 Telekinesis
	{ anchor: P10 + '.name', entries: {
		[P10 + '.desc']: 'Psionic force manipulation built around juggling: pull creatures, loose items and torn-up blocks into an orbit around you -- up to three at once -- then fling them one at a time or all together. Everything is paid for out of one Psi reserve; empty it and the power shuts down for ten seconds.',
		[P10 + '.ability.force_push']: 'Force Push',
		[P10 + '.ability.force_push.desc']: '12 damage and a hard shove to the entity you are aiming at (50 blocks), plus anything within 3 blocks of it. 0.85s cooldown, 35 Psi. Sneak for Force Pull instead: reels in a target and every loose item within 50 blocks (25 Psi).',
		[P10 + '.ability.telekinetic_barrier']: 'Telekinetic Barrier',
		[P10 + '.ability.telekinetic_barrier.desc']: 'A toggled shell of pure force. Nothing gets through while it holds -- but it burns 1.6 Psi a tick, and every blow it eats costs 4 Psi per point of damage. Break your reserve on it and you burn out.',
		[P10 + '.ability.psychic_flight']: 'Psychic Flight',
		[P10 + '.ability.psychic_flight.desc']: 'Toggle telekinetic flight. A low, steady Psi drain (0.4 a tick) that stops at a safety floor instead of emptying you mid-air; what you juggle while flying is what costs.',
		[P10 + '.ability.telekinetic_explosion']: 'Psychic Detonation',
		[P10 + '.ability.telekinetic_explosion.desc']: 'Hold Z for 5s: everything within 50 blocks is torn off the ground and hauled toward you, held at arm’s length, then blown apart for 66 damage. 76s cooldown, 320 Psi, and your reserve crawls back at a fifth of its speed for 10s afterwards.',
		[P10 + '.ability.telekinetic_grab']: 'Telekinetic Grab',
		[P10 + '.ability.telekinetic_grab.desc']: 'Pull the creature or dropped item under your crosshair (50 blocks) into your orbit -- up to 3 objects circle you at chest height (55 Psi a creature, 20 an item, plus 0.45 Psi a tick each while they orbit). With the orbit full, or nothing grabbable aimed at, V flings the object nearest your aim at the crosshair instead (creature 14 damage and 8 to itself on impact, item 9, block 16). Sneak: set the whole orbit down gently -- or, with an empty orbit, Force Crush: reel a victim in and squeeze for 12 damage a second while it drains Psi and roots you.',
		[P10 + '.ability.block_manipulation']: 'Block Manipulation',
		[P10 + '.ability.block_manipulation.desc']: 'Hold to lift a block up to 50 blocks away and steer it with your crosshair; release to throw it. A quick tap instead plucks the block straight into your orbit. Sneak to tear a 3x3 chunk out of the ground -- a thrown chunk hits the first creature it reaches for 31.',
		[P10 + '.ability.launch_orbit']: 'Launch Orbit',
		[P10 + '.ability.launch_orbit.desc']: 'Hurl everything orbiting you at the crosshair at once, each object hitting 20% harder than a single throw. 30 Psi, 3s cooldown.',
		[P10 + '.ability.mind_lock']: 'Mind Lock',
		[P10 + '.ability.mind_lock.desc']: 'Seize the creature you are aiming at (50 blocks) and freeze it in mid-air, 1.5 blocks up, for 4 seconds -- it cannot move, attack or target anything, and drifts down safely afterwards. No bosses. 90 Psi, 12s cooldown.',
		[P10 + '.passive.orbit']: 'Up to three grabbed objects orbit you at once; they cost Psi to hold and fall gently if you burn out',
	} },
	// ------------------------------------------------------------------ 11 Teleportation
	{ anchor: P11 + '.name', entries: {
		[P11 + '.desc']: 'Short-range spatial distortion: combat blinks, a chaining Bamf Strike, place-swapping, marks, limited wall phasing, and linked portal gateways that can cross dimensions. Every jump leaves a puff of purple smoke and a fading afterimage where you stood.',
		[P11 + '.ability.blink.desc']: 'Hold to paint the destination (up to 75 blocks), release to teleport there, stopping safely before invalid blocks. Sneak instead for a Phase Jump straight through a thin wall. 2.5s cooldown.',
		[P11 + '.ability.target_teleport.desc']: 'Teleport to a safe spot behind the creature you are aiming at (50 blocks), turned to face it. 6s cooldown.',
		[P11 + '.ability.escape_blink.desc']: 'Instant teleport up to 9.5 blocks backward from your facing, with a moment of heavy damage resistance on arrival. 3.4s cooldown.',
		[P11 + '.ability.portal.desc']: 'Hold for 5 seconds to charge (a bar shows the charge), then pick an exact destination -- coordinates and dimension -- in a screen. A linked pair of gateways forms slowly out of particles: one where you stand, one at the destination. Step into either to travel to the other. The pair stays open until you sneak + right-click a gate to close it, or you lose the power. 51s cooldown.',
		[P11 + '.ability.teleport_mark.desc']: 'Place one return marker; activate again to return if the spot is still valid. Works across dimensions — a marker set in the Nether always brings you back to the Nether, wherever you press the key. 17s cooldown after a recall.',
		[P11 + '.ability.portal_anchor.desc']: 'Press to place anchor A on the block you are looking at, press again for anchor B. The two link into a permanent portal that works across dimensions -- anyone who steps into one end comes out the other. Sneak + right-click either end to remove the pair; they also vanish if you lose the power.',
		[P11 + '.ability.bamf_strike']: 'Bamf Strike',
		[P11 + '.ability.bamf_strike.desc']: 'Vanish and reappear behind the creature you are aiming at (30 blocks) with a strike for 11, then keep teleporting: every 0.3s you hop behind the nearest new enemy within 10 blocks and hit it too, up to 4 targets in all. Every hop is checked for a safe landing. 12s cooldown.',
		[P11 + '.ability.swap']: 'Swap',
		[P11 + '.ability.swap.desc']: 'Trade places with the creature you are aiming at (40 blocks) -- or a squadmate. Only happens if both spots are safe for both bodies; an enemy arrives briefly disoriented. No bosses. 6s cooldown.',
	} },
	// ------------------------------------------------------------------ 15 Invisibility / Light
	{ anchor: P15 + '.name', entries: {
		[P15 + '.desc']: 'Bend light: refract yourself into decoys and prisms, vanish completely, conjure a blade of solid light, and burn enemies with blasts, flashes and pillars of holy light. Stronger and longer-ranged in direct sunlight.',
		[P15 + '.ability.light_blast.desc']: 'Fired from your hand for 10 damage and 4s blindness, 0.85s cooldown. Hold to charge: +7 damage and +0.85s cooldown per second held (max 3s). Shift for a 5-blast volley, half a second apart (9.4s cooldown).',
		[P15 + '.ability.flash']: 'Flash',
		[P15 + '.ability.flash.desc']: 'A searing burst of light around you (8 blocks): 12 damage, 10s blindness and Slowness II, and every mob caught loses track of you. 8.5s cooldown. Shift for Radiant Lance: a piercing line of light (30 blocks) that hits everything along it for 24, blinds, makes them glow and bursts at its far end (10s cooldown).',
		[P15 + '.ability.mirage_dash.desc']: 'Hold to fly wherever you look; your body scatters into light while it lasts. Drains the sparkle meter (115, ~29s), which refills on its own. 2.5s cooldown once you land.',
		[P15 + '.ability.perfect_cloak.desc']: 'Hold for 5 seconds to charge, then channel a single blinding pillar of light for 3 seconds — 14 continuous beam damage plus a radiant 29-damage burst every half second where it lands. 64s cooldown.',
		[P15 + '.ability.decoy']: 'Mirror Images',
		[P15 + '.ability.decoy.desc']: 'Refract yourself: 2 Mirror Images (3 in direct sunlight) step out of you wearing your face, skin and gear, and scatter. For a second you vanish while every mob hunting you turns on an image instead -- and they keep luring nearby hostiles for 10 seconds. Any hit bursts an image into light that blinds whoever struck it. 16s cooldown.',
		[P15 + '.ability.cloaking_toggle']: 'Cloak',
		[P15 + '.ability.cloaking_toggle.desc']: 'Sustained full invisibility — your armour and whatever you hold vanish too, for every player watching. Attacking reveals you for 2 seconds.',
		[P15 + '.ability.hard_light_blade']: 'Hard-Light Blade',
		[P15 + '.ability.hard_light_blade.desc']: 'Conjure a glowing sword of solid light straight into your hand for 30 seconds: 9 damage, makes whatever it hits glow, never wears out. It is light, not an item -- switch away from it, drop it, open a container or let the 30 seconds run out and it is gone. Press again to dismiss it early. 20s cooldown once it fades.',
		[P15 + '.ability.prism_shield']: 'Prism Shield',
		[P15 + '.ability.prism_shield.desc']: 'Hold to raise a pane of refracted light in front of you: arrows, fireballs and other projectiles coming at your front are bounced back at whoever shot them, and beams or ranged hits from the front are refracted away and 60% of them sent back at the sender. Melee still gets through. You move 35% slower while holding it; it drains its own 115-point Prism bar (and more per reflection), which refills when lowered.',
		[P15 + '.passive.refraction']: 'Your cloak hides your held items and armour from everyone; Mirror Images and the Hard-Light Blade are pure light and can never be looted or kept',
	} },
	// ------------------------------------------------------------------ 19 Shadow
	{ anchor: P19 + '.name', entries: {
		[P19 + '.desc']: 'Darkness as a resource and a road: bolts, tendrils, binding chains, a shadow servant, and travel through the shadows themselves -- step through darkness or sink into a moving puddle of it. Everything is stronger in the dark and weaker in sunlight.',
		[P19 + '.ability.shadow_bolt.desc']: 'Fire a dark-energy bolt for 13 damage that blinds the target for 4 seconds (1.7s cooldown). Shift for a 5-bolt fan volley (12.75s cooldown).',
		[P19 + '.ability.shadow_tendrils.desc']: 'Lash nearby enemies with tendrils from the ground for 18 damage and blind them for 8 seconds (6.8s cooldown). Shift for a 10-block cone that also roots everything it hits in place for 8 seconds (17s cooldown).',
		[P19 + '.ability.shadow_step.desc']: 'Blink up to 75 blocks through darkness, hitting anything at the landing spot for 6 (2.5s cooldown). Out of darkness you need Shift to seek out a dark spot to land in instead (6.8s cooldown).',
		[P19 + '.ability.total_darkness.desc']: 'Hold for 5 seconds to charge, then unleash a 20-block shadow zone: everything caught sinks, slows, blinds and takes 10 damage a second for 11 seconds. 51s cooldown.',
		[P19 + '.ability.shadow_bind']: 'Shadow Bind',
		[P19 + '.ability.shadow_bind.desc']: 'Black chains erupt from the ground around the creature you are aiming at (24 blocks) and up to 2 others beside it: 9 damage, rooted in place, weakened and briefly blinded for 5 seconds (longer in the Deep Dark, shorter in light). 10s cooldown. Shift for Shadow Grab: seize a target up to 20 blocks away and hold it; press again to throw, or Shift to set it down.',
		[P19 + '.ability.shadow_form']: 'Shadow Form',
		[P19 + '.ability.shadow_form.desc']: 'Become a thick black silhouette with burning purple eyes. Toggle for Shadow Cloak: +12 ability damage, +8 melee damage and a slowing, blinding aura, draining a 115-point bar (~40 seconds) and ending on a 17s cooldown. Shift+toggle instead for the indefinite stealth Shadow Form: in darkness you turn invisible, fast, and mobs lose track of you.',
		[P19 + '.ability.shadow_walk']: 'Shadow Walk',
		[P19 + '.ability.shadow_walk.desc']: 'Sink into the ground as a moving puddle of shadow: invisible (armour and held items too), Speed III, no fall damage and half damage from everything, and every mob loses track of you -- but you cannot attack, and using an attack ability surfaces you. Drains its own 115-point bar (~10 seconds in darkness, twice as fast in bright light), which refills when you surface.',
		[P19 + '.ability.shadow_servant']: 'Shadow Servant',
		[P19 + '.ability.shadow_servant.desc']: 'Raise a silhouette of living darkness with glowing eyes that fights for you for 20 seconds: 30 health, 8 damage a hit (scaled by the light), and it goes for whatever hurt you, whatever you are fighting, or the nearest hostile. It never targets you, your squad or your pets. One at a time. 30s cooldown.',
		[P19 + '.passive.shadow_travel']: 'Shadow Walk and Shadow Step let you travel through darkness itself; the Shadow Servant never turns on you or your squad',
	} },
	// ------------------------------------------------------------------ 23 Gravity
	{ anchor: P23 + '.name', entries: {
		[P23 + '.desc']: 'Decide which way gravity pulls: push, crush and levitate, flip a target so it falls into the sky, pin a whole area to the floor, and tear open a black hole.',
		[P23 + '.ability.gravity_push.desc']: 'A focused gravity pulse, 50-block range, that throws targets away for 12 damage, 1.7s cooldown. Shift for a grab instead — press again to throw for 10.',
		[P23 + '.ability.gravity_crush.desc']: 'Hold to crush a target up to 50 blocks away for up to 8 seconds, no knockback — 5 damage a second and Slowness II. 17s cooldown on release. Shift for a separate, smaller Gravity Well 20 blocks out (2.5 damage a second, 6 seconds).',
		[P23 + '.ability.zero_g']: 'Zero-G',
		[P23 + '.ability.zero_g.desc']: 'Toggle 85% lighter personal gravity for floating and aerial repositioning (no fall damage while it is on). Shift+toggle also fires Gravity Repulsion, pushing nearby enemies away (8.5s cooldown).',
		[P23 + '.ability.gravity_well.desc']: 'Hold for 5 seconds to charge, then rip open a black hole 5 blocks ahead: pulls everything within 30 blocks in, 7 damage a second inside 10 blocks, destroys terrain, lasts 15 seconds. Never affects you or your squad. 102s cooldown.',
		[P23 + '.ability.levitate.desc']: 'Mark up to 10 targets within 20 blocks with Levitation, capped 10 blocks up, for 20 seconds -- free, no cooldown. Shift to slam every marked target down for 17 damage plus fall damage; only the slam pays the 21s cooldown.',
		[P23 + '.ability.gravity_field.desc']: 'Toggle a gravitational-pressure stance wrapped in a thin violet field: +12 ability damage, +8 melee damage, Speed II and Resistance I, while everything within 3 blocks of you takes Slowness IV and 2.5 damage a second. Drains a 115-point bar (~40 seconds); ends on a 17s cooldown.',
		[P23 + '.ability.invert']: 'Invert',
		[P23 + '.ability.invert.desc']: 'Flip gravity on the creature you are aiming at (30 blocks): it falls UP into the sky for 1.5 seconds (taking 6 if it hits a ceiling), then gravity slams back doubled -- it crashes down for 10 plus fall damage, and everything within 3 blocks of the impact takes 5. No bosses. 9s cooldown.',
		[P23 + '.ability.heavy_ground']: 'Heavy Ground',
		[P23 + '.ability.heavy_ground.desc']: 'Crush gravity onto a 7-block circle where you aim (24 blocks) for 8 seconds: nothing inside can jump, everything is slowed and takes 3 damage a second, and anything in the air above it -- flying mobs included, up to 14 blocks up -- is dragged down to the floor. Never affects you or your squad. 17s cooldown.',
	} },
	// ------------------------------------------------------------------ 26 Magnetism
	{ anchor: P26 + '.name', entries: {
		[P26 + '.desc']: 'Manipulate magnetically reactive metal for combat and movement. Your abilities require actual magnetic material in the environment or on your enemies -- ride it, rip it off them, or hurl it. Copper and gold are not magnetically reactive; netherite can be manipulated but strongly resists magnetic force.',
		[P26 + '.ability.ferrous_shot.desc']: 'Launch a nearby magnetic object — a block you look at, a dropped metal item within 30 blocks, a nearby metal block, or (failing all else) a magnetic item from your own inventory — toward your crosshair. Heavier objects fly slower but hit far harder (iron nugget ~7, iron block ~22, anvil/netherite block ~34). 2.5s cooldown; fails with no cooldown if there is no magnetic metal anywhere in reach.',
		[P26 + '.ability.magnetic_grip.desc']: 'Grab a magnetic block, dropped item, minecart or iron golem and float it on your aim. Heavier objects follow more sluggishly; netherite resists. Activate again to hurl it forward (1.25s between grips).',
		[P26 + '.ability.polarity_leap.desc']: 'Pull yourself rapidly toward a magnetic object you are looking at, up to 24 blocks. Preserves momentum and cushions the landing briefly — chain leaps between metal to travel. 1.7s cooldown; fails with no cooldown if nothing magnetic is targeted.',
		[P26 + '.ability.metal_storm.desc']: 'Gather up to 8 nearby magnetic objects into a violent orbit, then launch them at whatever you aim toward. Each object\'s damage scales with its mass (+20%); damage to any one target is capped at 36 so it can\'t instantly delete an enemy. Needs at least 2 magnetic objects nearby. 15s cooldown.',
		[P26 + '.ability.magnetic_crush.desc']: 'Use the magnetic metal an enemy wears or holds against them — crushing damage that scales with how much they carry (one item ~8, full iron armour ~23), plus Slowness and Weakness, and a hard immobilise at 3+ pieces. Netherite takes less damage but is still locked down. No metal on the target means no effect and no cooldown. 7.6s cooldown.',
		[P26 + '.ability.magneto_hover']: 'Magneto Hover',
		[P26 + '.ability.magneto_hover.desc']: 'Toggle: push off the metal around you and levitate -- fly freely while there is magnetic metal within a few blocks below or around you, or while you hold an iron item. Leave the metal behind and you drift down gently until you find more. Drains its own 115-point bar (~15 seconds), which refills when you land; field lines crackle over your body while it lifts you.',
		[P26 + '.ability.disarm']: 'Disarm',
		[P26 + '.ability.disarm.desc']: 'Rip the iron or netherite weapon out of a creature\'s hand -- or, failing that, a piece of its armour -- and send it clattering to the ground toward you as a real item (4 damage as iron tears loose). Works on players only when PvP ability damage is allowed. Never touches Mjolnir, copper or gold; no metal means no cooldown. 10s cooldown.',
	} },
	// ------------------------------------------------------------------ messages
	{ anchor: 'message.projecthero.telekinesis.chunk', entries: {
		'message.projecthero.telekinesis.orbit_add': 'Into the orbit — %s / %s',
		'message.projecthero.telekinesis.orbit_empty': 'Nothing is orbiting you.',
	} },
	{ anchor: 'message.projecthero.teleport.no_room', entries: {
		'message.projecthero.teleport.no_target': 'No target to teleport to',
	} },
	{ anchor: 'message.projecthero.light.sparkle_out', entries: {
		'message.projecthero.light.blade_hands_full': 'Your hands and inventory are full — no room for the blade',
		'message.projecthero.light.prism_low': 'Your prism is spent',
	} },
	{ anchor: 'message.projecthero.shadow.cloak_out', entries: {
		'message.projecthero.shadow.no_target': 'No target in your shadow\'s reach',
		'message.projecthero.shadow.walk_low': 'Not enough shadow left to sink into',
		'message.projecthero.shadow.walk_out': 'The shadows give you back',
	} },
	{ anchor: 'message.projecthero.gravity.lift_full', entries: {
		'message.projecthero.gravity.no_target': 'Nothing there you can flip',
	} },
	{ anchor: 'message.projecthero.magnetic.storm_short', entries: {
		'message.projecthero.magnetic.hover_low': 'Your magnetic field is too weak to lift you',
		'message.projecthero.magnetic.hover_no_metal': 'No magnetic metal nearby to push against',
		'message.projecthero.magnetic.hover_out': 'Your magnetic field gives out',
	} },
	// ------------------------------------------------------------------ entities / item
	{ anchor: null, entries: {
		'entity.projecthero.mirror_image': 'Mirror Image',
		'entity.projecthero.shadow_servant': 'Shadow Servant',
		'item.projecthero.hard_light_blade': 'Hard-Light Blade',
	} },
]);

// Shadow Grab's old slot id (shadow_clone) no longer exists -- drop its now-dead name/desc lines.
{
	const fs = require('fs');
	const path = require('path');
	const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
	let text = fs.readFileSync(FILE, 'utf8');
	const eol = text.includes('\r\n') ? '\r\n' : '\n';
	const dead = [P19 + '.ability.shadow_clone', P19 + '.ability.shadow_clone.desc'];
	const lines = text.split(/\r?\n/).filter(l => !dead.some(k => l.trim().startsWith(JSON.stringify(k) + ':')));
	const out = lines.join(eol);
	JSON.parse(out);
	fs.writeFileSync(FILE, out);
	console.log('dead keys removed');
}
