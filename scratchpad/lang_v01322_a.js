// v0.13.22 mutation revamp, batch A lang: Super Strength, Laser Vision, Flight, Super Speed, Super Regeneration and
// Super Durability -- new / renamed abilities, every ability description rewritten for the new behaviour, the new
// resource passives and feedback messages. Also drops the keys of the abilities the revamp removed.
// Run from the repo root: node scratchpad/lang_v01322_a.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

// ---- 1. remove the dropped abilities' keys (name + desc) ----
const dropped = [
	'power_01_super_strength.ability.air_punch', 'power_01_super_strength.ability.grab_carry',
	'power_02_laser_vision.ability.focused_beam', 'power_02_laser_vision.ability.heat_burst',
	'power_02_laser_vision.ability.precision_vision',
	'power_03_flight.ability.aerial_burst',
	'power_04_super_speed.ability.whirlwind',
	'power_12_super_regeneration.ability.recovery_burst',
].flatMap(k => ['projecthero.power.' + k, 'projecthero.power.' + k + '.desc']);
{
	let text = fs.readFileSync(FILE, 'utf8');
	const eol = text.includes('\r\n') ? '\r\n' : '\n';
	const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
	const lines = text.split(/\r?\n/).filter(l => { const m = l.match(keyRe); return !(m && dropped.includes(m[1])); });
	const out = lines.join(eol);
	JSON.parse(out);
	fs.writeFileSync(FILE, out);
}

// ---- 2. set / add the new text ----
const P = (power, rest) => 'projecthero.power.' + power + '.' + rest;
const S1 = 'power_01_super_strength', S2 = 'power_02_laser_vision', S3 = 'power_03_flight', S4 = 'power_04_super_speed';
const S12 = 'power_12_super_regeneration', S13 = 'power_13_super_durability';

require('./langset.js')([
	// ------------------------------------------------------------------ 01 Super Strength
	{ anchor: P(S1, 'ability.maximum_effort.desc'), entries: {
		[P(S1, 'desc')]: 'Enhanced physiology built on raw force: throw anything you can get your hands on — creatures, players, even the ground itself — and land every leap like a hero.',
		[P(S1, 'ability.haymaker')]: 'Haymaker',
		[P(S1, 'ability.haymaker.desc')]: 'R: a three-punch combo — jab (10), cross (12), then a launching haymaker (22) that sends the target flying and knocks their shield down. Each follow-up must land within 1.5s; the full combo goes on a 5.1s cooldown, dropping it half way costs 2.5s. Snaps onto the nearest enemy in reach if you are not aiming at one.',
		[P(S1, 'ability.ground_slam.desc')]: 'G: 17 damage in a 5-block radius, slows for 2s and launches enemies up. Used airborne it becomes a dive that hits harder the farther you fell (up to 34) and ends in a hero landing that shrugs off the fall. Leaves the ground intact. 6.8s cooldown.',
		[P(S1, 'ability.power_leap.desc')]: 'X: hold to charge, release to launch yourself the way you are looking — 11 blocks at a tap, up to 38 fully charged — then touch down in a crater-cracking hero landing: 7 damage in a 2.5-block radius at a tap, up to 22 in 5.5 blocks fully charged. No fall damage. 2.5s cooldown.',
		[P(S1, 'ability.maximum_effort')]: 'Maximum Effort',
		[P(S1, 'ability.maximum_effort.desc')]: 'Z: 15 seconds of everything turned up — every Super Strength move and your fists hit twice as hard (Bull Rush / Impact Smash +25%), slams grow 50% wider, you are immune to knockback, take 30% less damage, move and jump faster, and ability cooldowns are halved. Red veins light up under your skin. 51s cooldown.',
		[P(S1, 'ability.grab_throw')]: 'Grab & Throw',
		[P(S1, 'ability.grab_throw.desc')]: 'V: grab the creature or player in front of you and hoist it over your head — or, if there is none, rip the block you are looking at straight out of the world. Press again to throw: a thrown creature takes 12 when it slams into a wall, the floor or another mob (and so do they); a thrown block hits for 19 and shatters for 8 around it. Sneak + V sets a creature down gently. Anything held is dropped after 15s. Containers and obsidian-hard blocks cannot be ripped; with terrain damage off the block is copied, not removed. 3.4s cooldown.',
		[P(S1, 'ability.bull_rush.desc')]: 'C: hold for 5s to charge (damage resistance and full knockback resistance while charging), then plough forward for 8s at double sprint speed with a 4-block step assist, hitting everything in your path for 24 and a 5-block knockback. Sneak + hold C for 5s = Impact Smash: up to 72 damage in a 20-block radius that shatters soft blocks. Releasing early cancels. One shared cooldown: 34s after a rush, 76s after a smash.',
		[P(S1, 'ability.thunderclap')]: 'Thunderclap',
		[P(S1, 'ability.thunderclap.desc')]: 'H: clap your hands and send a 120° cone of air rolling 9 blocks out: 9 damage, a hard shove and a 2.5s stun (Slowness V + Weakness), arrows turned around, and every fire in the cone snuffed out — on creatures, on you and on the ground. 8s cooldown.',
		[P(S1, 'ability.rip_hurl')]: 'Rip & Hurl',
		[P(S1, 'ability.rip_hurl.desc')]: 'N: tear a boulder out of the ground in front of you — it rises over your head in half a second — and hurl it where you aim: 26 damage to whatever it strikes and 14 to everything within 3 blocks as it shatters. With terrain damage on, the block it came from is gone. 9.4s cooldown.',
		[P(S1, 'passive.melee')]: 'Unarmed hits deal 14 damage with +150% knockback; a held tool adds its own damage on top',
		[P(S1, 'passive.charged')]: 'Hold the attack key for 2s (not while aimed at a block you could mine), then release for a Charged Punch: 24 damage to whatever is in front of you, massive knockback, disables shields. 2.5s cooldown — you can keep winding it up while it recharges.',
	} },
	// ------------------------------------------------------------------ 02 Laser Vision
	{ anchor: P(S2, 'ability.thermal_vision.desc'), entries: {
		[P(S2, 'desc')]: 'A photonic mutation that runs on heat: every beam heats your eyes, and the hotter they run the sharper — and deadlier — the beam. Push it too far and you overheat.',
		[P(S2, 'ability.heat_vision.desc')]: 'R: hold to fire two continuous beams from your eyes — 4.8 damage every half-second, ramping up to 2.5x the longer you hold, igniting what it touches. Shift + R switches to utility mode, mining blocks as fast as a diamond tool but running hotter.',
		[P(S2, 'ability.piercing_lance')]: 'Piercing Lance',
		[P(S2, 'ability.piercing_lance.desc')]: 'G: hold to charge (2s for full), release to fire a 40-block needle of light that runs through up to 4 creatures for 18–29 damage and cuts straight through glass, leaves and panes (with terrain damage on). +90 heat, 7.5s cooldown.',
		[P(S2, 'ability.recoil_blast')]: 'Recoil Blast',
		[P(S2, 'ability.recoil_blast.desc')]: 'X: fire a point-blank blast where you are looking — at the ground, a wall, behind you — and the recoil throws you the other way. 9.6 damage and fire in a 2.5-block splash where it lands; no fall damage for 4s after. +50 heat, 4.25s cooldown.',
		[P(S2, 'ability.maximum_output.desc')]: 'Z: hold for 4s, gathering heat at your eyes, then unleash a thick 48-block beam for 3s: 9.6 damage a tick to what it touches, fire, and a 41-damage burst every half-second where it lands. +250 heat, 55s cooldown.',
		[P(S2, 'ability.ricochet_shot')]: 'Ricochet Shot',
		[P(S2, 'ability.ricochet_shot.desc')]: 'V: a bolt that caroms off up to 3 surfaces over 48 blocks, burning everything it passes through — 11 damage, +25% after every bounce. Lights any TNT it hits. +40 heat, 3.5s cooldown.',
		[P(S2, 'ability.thermal_vision.desc')]: 'C: toggle thermal highlighting of nearby living entities, through walls and cover (only you can see it). Keeps your eyes warm: a slow trickle of heat, and heat does not vent while it is on.',
		[P(S2, 'ability.sweeping_arc')]: 'Sweeping Arc',
		[P(S2, 'ability.sweeping_arc.desc')]: 'H: swing the beam through a 150° arc in half a second, 14 blocks out — 14.4 damage and fire to everything it crosses. +80 heat, 8.5s cooldown.',
		[P(S2, 'ability.cauterize')]: 'Cauterize',
		[P(S2, 'ability.cauterize.desc')]: 'N: vent all of your heat into your own wounds: heals 3 health, up to 16 at a full gauge, puts you out and clears Wither and Poison. Needs at least 60 heat. 14s cooldown.',
		[P(S2, 'passive.glow')]: 'Your eyes always glow red — brighter the hotter they run, blazing while you fire — you see clearly in darkness, and a subtle reticle marks any creature you look at',
		[P(S2, 'passive.heat')]: 'Heat (0–575): every beam adds heat, and every beam hits up to 60% harder the hotter your eyes are (past 60% a white-hot core runs down the beam). A full gauge overheats you — 3s locked out while it vents. Heat vents on its own a second after you stop firing.',
	} },
	// ------------------------------------------------------------------ 03 Flight
	{ anchor: P(S3, 'ability.carry.desc'), entries: {
		[P(S3, 'desc')]: 'True superhero flight built on speed: sprint in the air to climb through speed tiers and break the sound barrier, then bring the whole team along in your slipstream.',
		[P(S3, 'ability.air_dash.desc')]: 'R: a burst forward (much stronger in flight) that shoulder-checks anything you dash into for 10 damage and a shove. 2.5s cooldown.',
		[P(S3, 'ability.dive_bomb.desc')]: 'G: plunge dead straight down, all the way to the ground, then detonate in a hero landing. The impact scales with how far you fell — 18 damage plus 1.9 per block, up to 80 in a 12-block radius. 6.8s cooldown.',
		[P(S3, 'ability.flight_toggle')]: 'Flight',
		[P(S3, 'ability.flight_toggle.desc')]: 'X (or double-tap jump): take off or land. Cruises at ~15 blocks/second; sprinting climbs three speed tiers, one every 2 seconds — 25, 32, 39 and finally 46 blocks/second, where you break the sound barrier with a sonic boom ring (6 damage and a shove to anything close). Let go of sprint and the tiers bleed off.',
		[P(S3, 'ability.orbital_drop')]: 'Orbital Drop',
		[P(S3, 'ability.orbital_drop.desc')]: 'Z: rocket straight up (about 30 blocks, stopping at a ceiling), hang at the top for a heartbeat, then come down like a meteor: 24 damage plus 1.2 per block fallen (up to 72) in a 7-block radius, with a sonic boom. No fall damage. 45s cooldown.',
		[P(S3, 'ability.sonic_flight.desc')]: 'C: up to 25s of ~50 blocks/second supersonic flight, opening with a sonic boom and trailing a roaring vapour wake that throws 11-damage shockwaves. Press again to end it early; the 25.5s cooldown only starts once it ends.',
		[P(S3, 'ability.slipstream')]: 'Slipstream',
		[P(S3, 'ability.slipstream.desc')]: 'H: for 12s your squad-mates, your tamed pets and nearby villagers within 16 blocks are pulled along in your wake and kept on Slow Falling — bring the team with you. 16s cooldown.',
		[P(S3, 'ability.barrel_roll')]: 'Barrel Roll',
		[P(S3, 'ability.barrel_roll.desc')]: 'N: snap-roll sideways (alternating left and right) with half a second of complete invulnerability. 3.5s cooldown.',
	} },
	// ------------------------------------------------------------------ 04 Super Speed
	{ anchor: P(S4, 'ability.speed_mode.desc'), entries: {
		[P(S4, 'desc')]: 'A speedster mutation built on momentum: the longer you run, the harder everything hits.',
		[P(S4, 'ability.rapid_assault.desc')]: 'R: a blur of blows on everything in front of you — 4 hits of 2.4, plus one more hit for every 25 Momentum (up to 8). 1.5s cooldown.',
		[P(S4, 'ability.speed_carry.desc')]: 'G: snatch up the creature or player you are looking at and run with them held in front of you. It is a carry, not an attack: press again to set them down unharmed. 2.5s cooldown.',
		[P(S4, 'ability.momentum_dash.desc')]: 'X: an instant dash along the way you are moving; Momentum carries it up to 80% further. In speed modes, jumps also keep their momentum for long leaps. 1.7s cooldown.',
		[P(S4, 'ability.overdrive.desc')]: 'Z: for 30s — sprint at roughly 64 blocks/second (a faster tier that replaces Speed Mode), +150% attack speed, a 10-block step assist, fall immunity, Momentum three times as fast, your melee and every Super Speed move hit twice as hard, and time slows around you: everything hostile within 12 blocks crawls. 42.5s cooldown.',
		[P(S4, 'ability.vortex')]: 'Vortex',
		[P(S4, 'ability.vortex.desc')]: 'V: hold to run tight circles for up to 8s — the whirlwind drags every enemy within 10 blocks into its eye, lifting and battering those inside (3.6 damage every half-second), flinging back projectiles and putting out fire. 10.2s cooldown on release.',
		[P(S4, 'ability.speed_mode.desc')]: 'C: toggle: sprint at roughly 32 blocks/second, +50% attack speed, run on water, 2-block step, +100% swim speed, -80% fall damage, Momentum twice as fast. Yellow lightning crackles over you and a speed trail follows your feet.',
		[P(S4, 'ability.phase_vibrate')]: 'Phase Vibrate',
		[P(S4, 'ability.phase_vibrate.desc')]: 'H: vibrate your molecules and step straight through up to two blocks of wall in front of you (never bedrock or other unbreakable blocks). 5.1s cooldown.',
		[P(S4, 'ability.lightning_throw')]: 'Lightning Throw',
		[P(S4, 'ability.lightning_throw.desc')]: 'N: spend all your Momentum (at least 30) on a hurled bolt of static: 8 damage plus 0.14 per point of Momentum (up to 24), a brief slow, and it arcs on to one more enemy nearby for half. 4s cooldown.',
		[P(S4, 'passive.sprint')]: 'Momentum (0–115): sprinting builds it (twice as fast in Speed Mode, three times in Overdrive); it bleeds away a second after you stop. Your base speed is normal — the real speed comes from Speed Mode and Overdrive',
	} },
	// ------------------------------------------------------------------ 12 Super Regeneration
	{ anchor: P(S12, 'ability.regeneration_mode.desc'), entries: {
		[P(S12, 'desc')]: 'Aggressive cellular regeneration fuelled by Adrenaline: fast passive healing that never slows down, and every hit you take pumps you up for the next burst. Ascends into Wolverine with an Adamantium Serum.',
		[P(S12, 'ability.rapid_heal.desc')]: 'R: instantly restore 7.2 health — 13.2 if you spend 20 Adrenaline. 5.1s cooldown.',
		[P(S12, 'ability.purge.desc')]: 'G: burn every harmful effect out of your blood (the unstable mutation is not a poison — it stays). 10.2s cooldown.',
		[P(S12, 'ability.adrenal_rush')]: 'Adrenal Rush',
		[P(S12, 'ability.adrenal_rush.desc')]: 'X: a lunge forward and 6s of Speed II and Jump Boost II; spend 25 Adrenaline and it is Speed III plus Resistance I. 8.5s cooldown.',
		[P(S12, 'ability.resurrection.desc')]: 'Z (automatic): dying triggers full totem-of-undying protection — the unstable mutation survives it — and tops your Adrenaline off, then a 51s cooldown. Nothing to press.',
		[P(S12, 'ability.cellular_surge.desc')]: 'V: an extra 2 HP every 0.25s (8 HP/s) for 30 seconds on top of your regeneration, plus a movement and mining boost; green veins glow bright under your skin. 38s cooldown.',
		[P(S12, 'ability.regeneration_mode.desc')]: 'C: toggle an extra 1 HP every 0.25s; the only Super Regeneration ability that burns hunger (saturation every 5s). Green veins pulse under your skin while it runs.',
		[P(S12, 'ability.blood_rage')]: 'Blood Rage',
		[P(S12, 'ability.blood_rage.desc')]: 'H: pour all your Adrenaline (at least 40) into 10s of fury: melee +25%, climbing to +100% the closer to death you are. 20s cooldown.',
		[P(S12, 'ability.mend')]: 'Mend',
		[P(S12, 'ability.mend.desc')]: 'N: close the wounds of everyone you protect within 8 blocks — squad-mates, your tamed pets, villagers and iron golems: 8 health each, or 12 plus Regeneration I for 5s if you spend 20 Adrenaline. 12s cooldown.',
		[P(S12, 'passive.adrenaline')]: 'Adrenaline (0–115): taking damage fills it (4 per point of damage taken); it drains slowly once you are out of combat',
	} },
	// ------------------------------------------------------------------ 13 Super Durability
	{ anchor: P(S13, 'ability.tank_mode.desc'), entries: {
		[P(S13, 'desc')]: 'A tank mutation that hardens the body against damage, knockback, projectiles and explosions — and banks everything it stops as Impact to hit back with.',
		[P(S13, 'ability.heavy_strike.desc')]: 'R: a reinforced blow for 12 damage that staggers (Slowness IV for 2s). 2.5s cooldown.',
		[P(S13, 'ability.shoulder_charge.desc')]: 'G: barrel forward through enemies — 10 damage and a hard shove to each. 6.8s cooldown.',
		[P(S13, 'ability.block.desc')]: 'X: hold to cut damage from the frontal 180° arc by a further 60% and shrug off projectiles entirely; drains the guard bar.',
		[P(S13, 'ability.unbreakable.desc')]: 'Z: ignore 100% of all damage for 15 seconds — and all of it is banked as Impact. Your plating turns to gleaming gold. 42.5s cooldown.',
		[P(S13, 'ability.projectile_deflection')]: 'Deflection',
		[P(S13, 'ability.projectile_deflection.desc')]: 'V: hold to send every projectile that comes within 3.5 blocks straight back at whoever fired it, faster than it came (and it counts as yours); immune to projectiles while held. Drains the guard bar.',
		[P(S13, 'ability.tank_mode.desc')]: 'C: toggle a braced stance: a further 30% damage reduction and knockback resistance, 30% slower. A thin metal shell covers your skin.',
		[P(S13, 'ability.impact_release')]: 'Impact Release',
		[P(S13, 'ability.impact_release.desc')]: 'H: slam everything you have absorbed back out through the ground: 8 damage plus 0.3 per point of Impact (up to 42.5) in a 4–8 block radius, with knockback. Needs at least 20 Impact. 8s cooldown.',
		[P(S13, 'ability.taunt')]: 'Taunt',
		[P(S13, 'ability.taunt.desc')]: 'N: every mob within 16 blocks turns on you, and you brace with Resistance I for 6s. 15s cooldown.',
		[P(S13, 'passive.impact')]: 'Impact (0–115): every point of damage your durability stops is banked, 3 per point — Unbreakable, Block and Deflection included. The guard bar is 575.',
	} },
	// ------------------------------------------------------------------ feedback messages
	{ anchor: 'message.projecthero.durability.guard_spent', entries: {
		'message.projecthero.strength.nothing_to_grab': 'Nothing to grab',
		'message.projecthero.strength.no_ground': 'No ground to tear up',
		'message.projecthero.laser.not_hot_enough': 'Not hot enough — you need at least 60 heat',
		'message.projecthero.speed.no_momentum': 'Build up Momentum first (30 needed)',
		'message.projecthero.speed.cannot_phase': 'No thin wall to phase through',
		'message.projecthero.regen.no_adrenaline': 'Not enough Adrenaline (%s needed)',
		'message.projecthero.regen.no_allies': 'No allies close enough to mend',
		'message.projecthero.durability.no_impact': 'Not enough Impact (%s needed)',
	} },
]);
