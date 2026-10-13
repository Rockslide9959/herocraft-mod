// v0.16.1 Iron Man COMIC-ACCURATE REWORK: every lang key (idempotent -- re-run after a merge conflict).
// Run from the repo root: node scratchpad/lang_v0161_ironman.js
'use strict';

const MOVES = {
	// Mark 1
	mk1_iron_punch: 'Iron Punch', mk1_ground_pound: 'Ground Pound', mk1_flamethrower: 'Flamethrower', mk1_smoke_screen: 'Smoke Screen',
	mk1_rocket_hop: 'Rocket-Boot Hop', mk1_glide: 'Glide', mk1_shoulder_rocket: 'Shoulder Rocket', mk1_cluster_rocket: 'Cluster Rocket',
	mk1_electromagnet: 'Electromagnet', mk1_magnetic_repel: 'Magnetic Repel',
	// Mark 1 V2
	mk1v2_palm_repulsor: 'Palm Repulsor', mk1v2_dash: 'Dash', mk1v2_magnet_pull: 'Magnet Pull', mk1v2_magnet_push: 'Magnet Push',
	mk1v2_rocket_skates: 'Rocket Skates', mk1v2_spin_kick: 'Skate Spin-Kick', mk1v2_lamp_flash: 'Chest Lamp Flash',
	mk1v2_searchlight: 'Searchlight', mk1v2_oil_slick: 'Oil Slick', mk1v2_ignite_slick: 'Ignite Slick',
	// Mark 2
	mk2_repulsor: 'Repulsor (hold: Charged)', mk2_dash: 'Dash', mk2_tractor_beam: 'Tractor Beam', mk2_pressor_blast: 'Pressor Blast',
	mk2_flares: 'Flares', mk2_supersonic: 'Supersonic Boost', mk2_unibeam: 'Unibeam', mk2_unibeam_flash: 'Unibeam Flash',
	mk2_highlight: 'Mob Highlight', mk2_jarvis_scan: 'JARVIS Scan',
	// Mark 3
	mk3_rapid_repulsors: 'Rapid Repulsors', mk3_dash: 'Dash', mk3_pulse_bolts: 'Pulse Bolts', mk3_sonic_clap: 'Sonic Clap',
	mk3_micro_missiles: 'Micro-Missiles', mk3_missile_barrage: 'Missile Barrage', mk3_tri_unibeam: 'Triangular Unibeam',
	mk3_unibeam_sweep: 'Unibeam Sweep', mk3_energy_shield: 'Energy Shield', mk3_shield_bash: 'Shield Bash',
	// Mark 4 Hydro
	mk4_sonar_pulse: 'Sonar Pulse', mk4_sonar_mark: 'Sonar Mark', mk4_torpedo: 'Torpedo', mk4_depth_charge: 'Depth Charge',
	mk4_propeller_dash: 'Propeller Dash', mk4_whirlpool: 'Whirlpool', mk4_headlamp_beam: 'Headlamp Beam', mk4_floodlight: 'Floodlight',
	mk4_ballast_anchor: 'Ballast Anchor', mk4_pressure_crush: 'Pressure Crush',
	// Mark 5 Stealth
	mk5_silenced_repulsor: 'Silenced Repulsor', mk5_silent_takedown: 'Silent Takedown', mk5_chameleon_cloak: 'Chameleon Cloak',
	mk5_light_bend_blink: 'Light-Bend Blink', mk5_sensor_jammer: 'Sensor Jammer', mk5_emp_pulse: 'EMP Pulse',
	mk5_low_glow_shot: 'Low-Glow Shot', mk5_marked_shot: 'Marked Shot', mk5_holo_decoy: 'Holo-Decoy', mk5_decoy_swap: 'Decoy Swap',
	// Mark 6 Silver Centurion
	mk6_repulsor: 'Repulsor', mk6_dash: 'Dash', mk6_pulse_bolts: 'Pulse Bolts', mk6_pulse_storm: 'Pulse Storm',
	mk6_ricochet: 'Ricochet Repulsor', mk6_chameleon: 'Chameleon Effect', mk6_heavy_unibeam: 'Heavy Triangular Unibeam',
	mk6_unibeam_overcharge: 'Unibeam Overcharge', mk6_energy_shield: 'Energy Shield', mk6_overload_burst: 'Overload Burst',
	// Mark 7 Space
	mk7_repulsor: 'Repulsor', mk7_dash: 'Dash', mk7_vacuum_missiles: 'Vacuum Micro-missiles', mk7_orbital_strike: 'Orbital Strike',
	mk7_booster_rockets: 'Booster Rockets', mk7_meteor_dive: 'Meteor Dive', mk7_solar_beam: 'Solar Beam', mk7_solar_flare: 'Solar Flare',
	mk7_gravity_tether: 'Gravity Tether', mk7_zero_g_field: 'Zero-G Field',
	// Mark 8 Hulkbuster
	mk8_punch_combo: 'Giant Punch Combo', mk8_rocket_fist: 'Rocket Fist', mk8_pile_driver: 'Pile-Driver Slam',
	mk8_earthquake_stomp: 'Earthquake Stomp', mk8_grab_pound: 'Grab & Pound', mk8_grab_throw: 'Grab & Throw',
	mk8_mega_beam: 'Chest Mega-Beam', mk8_missile_swarm: 'Shoulder Missile Swarm', mk8_hydraulic_brace: 'Hydraulic Brace',
	mk8_replacement_pod: 'Replacement Pod',
};

const SUITS = {
	mark_1: ['Mark 1', 'Grey Iron'], mark_1_v2: ['Mark 1 V2', 'Gold Armour'], mark_2: ['Mark 2', 'Classic Red and Gold'],
	mark_iii: ['Mark 3', 'Upgraded Classic'], mark_4: ['Mark 4 Hydro', 'Hydro Armour'], mark_v: ['Mark 5 Stealth', 'Stealth Armour'],
	mark_6: ['Mark 6 Silver Centurion', 'Silver Centurion'], mark_vii: ['Mark 7 Space', 'Space Armour'], mark_8: ['Mark 8 Hulkbuster', 'Hulkbuster'],
};
const ITEM_NAME = { mark_1: 'mark_1', mark_1_v2: 'mark_1_v2', mark_2: 'mark_2', mark_iii: 'mark_3', mark_4: 'mark_4', mark_v: 'mark_5',
	mark_6: 'mark_6', mark_vii: 'mark_7', mark_8: 'mark_8' };
const PIECES = { helmet: 'Helmet', chestplate: 'Chestplate', leggings: 'Leggings', boots: 'Boots' };

const SPECIAL = {
	mark_1: 'Sinks and wades slowly in water; walks 10% slower; no sustained flight (rocket-boot hops + glide); 1.25x size',
	mark_1_v2: 'Upgraded from a full Mark 1; flies; ices up above Y 320',
	mark_2: 'Lock-on targeting, breathes underwater; deploys from the Briefcase; ices up above Y 320',
	mark_iii: 'Lock-on, water breathing, auto-feed',
	mark_4: 'Swims 3x fast, clear underwater vision, water breathing; every hit x1.5 while you are in water',
	mark_v: 'Silent: no footsteps, flight or swim sounds, sculk never hears you',
	mark_6: 'Resistance I; repairs 8 integrity/s after 10 s out of combat',
	mark_vii: 'Immune to fire, lava and freezing; no altitude ceiling; repairs 15/s out of combat; Colantotte Bracelets + orbital pod',
	mark_8: 'About 3 blocks tall (1.45x), knockback immune, x1.5 damage on bosses and the Hulk; Sentry Mode; orbital drop',
};

const GUIDE = {
	mark_1: 'Grey iron, built at a crafting table. 600 integrity, absorbs 20% of every hit; diamond plates (3/8/6/3, toughness 2); +6 melee; walks 10% slower and sinks in water. No sustained flight. R Iron Punch (15) / Shift Ground Pound (12 around you). G Flamethrower (hold, 8/s + burning) / Shift Smoke Screen (a blinding cloud, 8 s, enemies inside lose you). X Rocket-Boot Hop / Shift Glide (10 s, again to stop). Z Shoulder Rocket (30) / Shift Cluster Rocket (splits into 3 bomblets of 12). V Electromagnet (pulls metal-armoured mobs and items to you) / Shift Magnetic Repel (shoves everything within 7 away, 4).',
	mark_1_v2: 'The Mark 1 rebuilt in gold at the Fabricator (each piece: the Mark 1 piece + gold + Gilded Plating). 750 integrity, absorbs 25%; 3/8/6/3, toughness 2; flies 12 b/s (18 sprinting); +6 melee. R Palm Repulsor (12) / Shift Dash (14). G Magnet Pull (yanks the target to you, 4) / Shift Magnet Push (cone shove, 6). X Rocket Skates (Speed III, 5 s) / Shift Skate Spin-Kick (14 all round). Z Chest Lamp Flash (blinds a wide cone) / Shift Searchlight (outlines enemies in a narrow cone for you only, 12 s). V Oil Slick (10 s: enemies slip and slow) / Shift Ignite Slick (sets your slicks ablaze, 6 + burning).',
	mark_2: 'The classic red and gold, carried in the Briefcase (right-click the case to suit up, C folds it back). 1000 integrity, absorbs 30%; 3/8/6/3, toughness 2.5; flies 18 (28); +7 melee; lock-on; breathes underwater. R Repulsor -- tap 11, hold 1 s and release for a charged 20 / Shift Dash (16). G Tractor Beam (hold: lift a target on a beam, release to throw it) / Shift Pressor Blast (cone shove, 10). X Flares / Shift Supersonic Boost (20 s). Z round Unibeam (26, pierces) / Shift Unibeam Flash (short-range stun burst, 8). V Mob Highlight / Shift JARVIS Scan (scanned enemies take +25% for 15 s).',
	mark_iii: 'The upgraded classic. 1400 integrity, absorbs 35%; 3/8/6/3, toughness 3; flies 20 (32); +8 melee; auto-feed. R Rapid Repulsors (3 x 9) / Shift Dash (19). G Pulse Bolts (8; the third stack on a target explodes for 20) / Shift Sonic Clap (20). X Micro-missiles (4 x 18, homing) / Shift Missile Barrage (8 x 16, multi-lock). Z Triangular Unibeam (32) / Shift Unibeam Sweep (22 across a wide arc). V Energy Shield (6 s, stops 75% of every hit) / Shift Shield Bash (18 + a big shove).',
	mark_4: 'The HYDRO armour. 1800 integrity, absorbs 40%; 4/9/7/3, toughness 3; flies 14 (22) but swims three times as fast, sees clearly and breathes underwater; +8 melee; every hit x1.5 while you are in water. R Sonar Pulse (6 + stun all round, outlines what it finds for you) / Shift Sonar Mark (outlines enemies in a cone through walls for 20 s, for you only). G Torpedo (30, homing, keeps its speed underwater) / Shift Depth Charge (28 around where it lands). X Propeller Dash (16) / Shift Whirlpool (5 s, drags enemies in). Z Headlamp Beam (14 + blind) / Shift Floodlight (blinds a wide cone). V Ballast Anchor (10, drags the target down and pins it 4 s) / Shift Pressure Crush (24 around you).',
	mark_v: 'The STEALTH armour -- no more suitcase. 1100 integrity, absorbs 30%; 3/8/6/3, toughness 3; flies 20 (34); +7 melee; silent (no footsteps, flight or swim sounds; sculk never hears you). R Silenced Repulsor (14, double on an unaware enemy) / Shift Silent Takedown (45 on an unaware enemy within reach, 15 otherwise). G Chameleon Cloak (10 s, mobs lose you; attacking breaks it) / Shift Light-Bend Blink (an invisible 10-block teleport). X Sensor Jammer (enemies drop their target) / Shift EMP Pulse (stuns robots, turrets and Ultron units 5 s). Z Low-Glow Shot (26 sniper beam) / Shift Marked Shot (your next 3 hits on the target crit, x1.5). V Holo-Decoy (mobs chase it) / Shift Decoy Swap (swap places with it).',
	mark_6: 'The SILVER CENTURION. 2200 integrity, absorbs 45%; 4/9/7/4, toughness 3.5; flies 22 (38); +9 melee; Resistance I; repairs 8 integrity/s after 10 s out of combat. R Repulsor (17) / Shift Dash (22). G Pulse Bolts (10) / Shift Pulse Storm (8 rapid bolts). X Ricochet Repulsor (20, bounces through 4) / Shift Chameleon Effect (6 s invisible). Z Heavy Triangular Unibeam (40) / Shift Unibeam Overcharge (charges 1.5 s, then 75). V Energy Shield (6 s, stops 80%) / Shift Overload Burst (30 all round).',
	mark_vii: 'The SPACE armour. 2600 integrity, absorbs 50%; 4/9/7/4, toughness 4; the fastest flier, 24 (44), no altitude ceiling; immune to fire, lava and freezing; +9 melee; repairs 15/s after 10 s out of combat; still called with the Colantotte Bracelets / orbital pod. R Repulsor (20) / Shift Dash (25). G Vacuum Micro-missiles (6 x 20, each blast sucks enemies in) / Shift Orbital Strike (mark a spot: 12 missiles rain down, 18 each). X Booster Rockets (launch straight up into flight) / Shift Meteor Dive (dive down: 15 + 1.5 per block fallen, up to 80). Z Solar Beam (30, x1.5 in daylight under open sky) / Shift Solar Flare (22 + fire + blind all round). V Gravity Tether (lift and hold a target 4 s, bosses 1.5 s) / Shift Zero-G Field (6 s: enemies float helplessly).',
	mark_8: 'The HULKBUSTER -- about 3 blocks tall. 4500 integrity, absorbs 60%; 5/10/8/5, toughness 5; a slow jet-hover at 10 b/s; +16 melee; knockback immune; x1.5 damage on bosses and the Hulk; never repairs itself except by its Replacement Pod. C Sentry Mode; called with the Stark Glasses it drops in from orbit. R Giant Punch Combo (26, 26, then a 38 finisher) / Shift Rocket Fist (45). G Pile-Driver Slam (40 around you) / Shift Earthquake Stomp (a shockwave 18 blocks ahead, 32 + launch). X Grab & Pound (3 x 20) / Shift Grab & Throw (18 + 18 on impact). Z Chest Mega-Beam (3 s, 18 every half second) / Shift Shoulder Missile Swarm (10 x 18). V Hydraulic Brace (5 s, 70% less damage) / Shift Replacement Pod (an orbital pod restores 30% integrity, 3 min cooldown; works even on a disabled suit).',
};

const STEPS = {
	mark_1: 'Craft the Mark 1 at a crafting table (grey iron)',
	mark_1_v2: 'Upgrade the whole Mark 1 into the gold Mark 1 V2 (Fabricator: Mark 1 piece + gold + Gilded Plating)',
	mark_2: 'Develop the Mark 2 (classic red and gold, carried in the Briefcase)',
	mark_iii: 'Develop the Mark 3 (upgraded classic)',
	mark_4: 'Develop the Mark 4 HYDRO armour (Pressure Hull)',
	mark_v: 'Develop the Mark 5 STEALTH armour (Stealth Coating)',
	mark_6: 'Develop the Mark 6 SILVER CENTURION',
	mark_vii: 'Develop the Mark 7 SPACE armour (Thermal Shielding)',
	mark_8: 'Develop the Mark 8 HULKBUSTER (Hydraulic Frame)',
};

const moveNames = {};
for (const [id, name] of Object.entries(MOVES)) moveNames['hud.projecthero.ironman.comic.' + id] = name;

const suitKeys = {};
for (const [id, [name, comic]] of Object.entries(SUITS)) {
	suitKeys['projecthero.ironman.suit.' + id + '.name'] = name;
	suitKeys['projecthero.ironman.suit.' + id + '.comic'] = comic;
	for (const [p, pn] of Object.entries(PIECES)) suitKeys['item.projecthero.iron_man_' + ITEM_NAME[id] + '_' + p] = name + ' ' + pn;
	suitKeys['screen.projecthero.ironman_comic.special.' + id] = SPECIAL[id];
}

const guide = {};
for (const [id, body] of Object.entries(GUIDE)) guide['projecthero.guide.iron_man.' + id + '.body'] = body;
guide['projecthero.guide.iron_man.mark_1_v2'] = 'Mark 1 V2 (gold)';
guide['projecthero.guide.iron_man.mark_4'] = 'Mark 4 -- Hydro';
guide['projecthero.guide.iron_man.mark_v'] = 'Mark 5 -- Stealth';
guide['projecthero.guide.iron_man.mark_6'] = 'Mark 6 -- Silver Centurion';
guide['projecthero.guide.iron_man.mark_vii'] = 'Mark 7 -- Space';
guide['projecthero.guide.iron_man.mark_8'] = 'Mark 8 -- Hulkbuster';
guide['projecthero.guide.iron_man.comic'] = 'How the suits work (v0.16.1)';
guide['projecthero.guide.iron_man.comic.body'] = 'There is no suit energy. Every key (R, G, X, Z, V) has a tap move and a Shift move, each on its own cooldown -- nothing else limits them, and flight is unlimited (except the Mark 1, which hops and glides). Hold Left Alt to list a key strip\'s moves; the I key shows the full spec sheet.';
guide['projecthero.guide.iron_man.comic.integrity'] = 'Integrity is the suit\'s resource. While it has any, the suit absorbs its Mark\'s share of every hit (Mark 1 20% up to the Hulkbuster 60%) -- that share comes off the integrity instead of you. At 0 integrity the suit is DISABLED: no flight, no abilities, heavy Slowness and a dark visor until a Suit Platform or Stark Gantry repairs it. The Mark 6 and Mark 7 also repair themselves after 10 seconds out of combat; the Hulkbuster has its Replacement Pod.';
for (const [id, s] of Object.entries(STEPS)) guide['projecthero.guide.iron_man.step.' + id] = s;

require('./langset.js')([
	{ anchor: 'projecthero.ironman.suit.mark_8.name', entries: suitKeys },
	{ anchor: 'hud.projecthero.ironman.ability.suit_toggle', entries: Object.assign({
		'hud.projecthero.ironman.comic.pair': '[%s] %s / Shift: %s',
		'hud.projecthero.ironman.integrity_abs': 'INTEGRITY  abs %s%%',
		'hud.projecthero.ironman.disabled_title': 'SUIT DISABLED',
		'hud.projecthero.ironman.disabled_hint': 'Integrity is gone: no flight, no abilities. Dock the suit on a Suit Platform or Stark Gantry to repair it.',
		'hud.projecthero.ironman.ability.comic_r': 'R (tap / Shift)',
		'hud.projecthero.ironman.ability.comic_g': 'G (tap / Shift)',
		'hud.projecthero.ironman.ability.comic_x': 'X (tap / Shift)',
		'hud.projecthero.ironman.ability.comic_z': 'Z (tap / Shift)',
		'hud.projecthero.ironman.ability.comic_v': 'V (tap / Shift)',
	}, moveNames) },
	{ anchor: 'message.projecthero.ironman.integrity_failed', entries: {
		'message.projecthero.ironman.suit_disabled': 'Suit disabled -- repair it on a Suit Platform or Stark Gantry',
		'message.projecthero.ironman.comic.glide_on': 'Gliding -- Shift+X again to stop',
		'message.projecthero.ironman.comic.marked': 'Marked %s (only you see them)',
		'message.projecthero.ironman.comic.no_target': 'No target',
		'message.projecthero.ironman.comic.scanned': 'JARVIS: %s hostiles scanned -- they take 25%% more damage',
		'message.projecthero.ironman.comic.takedown': 'Silent takedown',
		'message.projecthero.ironman.comic.cloaked': 'Cloaked for %s s -- attacking breaks it',
		'message.projecthero.ironman.comic.marked_shot': 'Target marked: your next 3 hits crit',
		'message.projecthero.ironman.comic.decoy': 'Holo-decoy deployed',
		'message.projecthero.ironman.comic.orbital': 'Orbital strike inbound',
		'message.projecthero.ironman.comic.dive_too_low': 'Too low to dive -- get at least 4 blocks up',
		'message.projecthero.ironman.comic.brace': 'Hydraulic brace locked',
		'message.projecthero.ironman.comic.pod_not_needed': 'The suit is at full integrity',
		'message.projecthero.ironman.comic.pod_inbound': 'Replacement pod inbound',
		'message.projecthero.ironman.comic.pod_repaired': 'Replacement parts fitted: +%s%% integrity',
		'message.projecthero.ironman.briefcase_unpacked': 'The briefcase now carries the Mark 2 -- its old Mark 5 pieces are back in your inventory',
		'message.projecthero.ironman.mark8.inbound': '%s inbound from orbit',
		'message.projecthero.ironman.jarvis.suit': 'Suit: integrity %s',
	} },
	{ anchor: 'screen.projecthero.suit_call.charge', entries: {
		'screen.projecthero.suit_call.absorb': 'ABSORB',
		'screen.projecthero.suit_call.cmp.absorb': 'Absorb',
		'screen.projecthero.suit_call.cmp.self_repair': 'Self-repair',
		'screen.projecthero.suit_platform.regen_tip': 'Docked suits repair %s integrity per second (suits have no energy any more)',
		'screen.projecthero.ironman_comic.stats': 'Comic stats',
		'screen.projecthero.ironman_comic.moves': 'Keys (tap / Shift)',
		'screen.projecthero.ironman_comic.move_line': '[%s] %s / Shift: %s',
		'screen.projecthero.ironman_comic.armour': 'Armour (H/C/L/B)',
		'screen.projecthero.ironman_comic.integrity': 'Integrity',
		'screen.projecthero.ironman_comic.absorb': 'Absorbs of each hit',
		'screen.projecthero.ironman_comic.flight': 'Flight',
		'screen.projecthero.ironman_comic.flight_hops': 'none -- rocket-boot hops and a glide',
		'screen.projecthero.ironman_comic.flight_value': '%s b/s (%s sprinting), unlimited',
		'screen.projecthero.ironman_comic.flight_hover': '%s b/s jet-hover, unlimited',
		'screen.projecthero.ironman_comic.melee': 'Melee',
		'screen.projecthero.ironman_comic.repair': 'Repair',
		'screen.projecthero.ironman_comic.repair_self': 'Suit Platform; self-repair %s/s out of combat',
		'screen.projecthero.ironman_comic.repair_pod': 'Suit Platform; Replacement Pod (+30%)',
		'screen.projecthero.ironman_comic.repair_platform': 'Suit Platform / Stark Gantry',
		'screen.projecthero.ironman_comic.special': 'Special',
	} },
	{ anchor: 'item.projecthero.mark_v_suitcase', entries: {
		'item.projecthero.mark_v_suitcase': 'Mark 2 Briefcase',
		'item.projecthero.mark_v_suitcase.hint': 'Right-click: the Mark 2 builds itself around you out of the case. C while wearing it folds it back.',
		'item.projecthero.mark_v_suitcase.contents': 'Holds %s of 4 Mark 2 pieces',
		'item.projecthero.mark_1_v2_blueprint': 'Mark 1 V2 Blueprint',
		'item.projecthero.gilded_plating': 'Gilded Plating',
		'item.projecthero.pressure_hull': 'Pressure Hull',
		'item.projecthero.stealth_coating': 'Stealth Coating',
		'item.projecthero.thermal_shielding': 'Thermal Shielding',
		'item.projecthero.hydraulic_frame': 'Hydraulic Frame',
	} },
	{ anchor: 'projecthero.guide.iron_man.controls', entries: guide },
	// the older guide pages, rewritten for the comic rework (no energy; the briefcase is the Mark 2's)
	{ anchor: 'projecthero.guide.iron_man.controls', entries: {
		'projecthero.guide.iron_man.controls': 'Wearing a suit the keys are R / G / X / Z / V -- each with a tap move and a Shift move on their own cooldowns (see the Mark pages below, or press I for the spec sheet) -- and C. Double-tap jump in the air to fly, as long as you like (the Mark 1 cannot: it hops and glides); touching the ground ends a flight. Speeds: Mark 1 V2 12 (18 sprinting), Mark 2 18 (28), Mark 3 20 (32), Hydro 14 (22), Stealth 20 (34), Centurion 22 (38), Space 24 (44), Hulkbuster a 10 b/s hover. Unarmoured, C puts on any armour you are carrying; the Mark 2 lives in its Briefcase (right-click it). With the Stark Glasses on, Sneak + C opens the Call Armour picker for your Mark 8 Hulkbuster (it drops in from orbit); the Colantotte Bracelets call the Mark 7. Iron Man armour makes you immune to fall damage, and flying hard into the ground slams down for 20 damage.',
		'projecthero.guide.iron_man.platform.body': 'Any suit docked on a Suit Platform repairs 5 integrity every second, while two welding arms rise out of the pad and spark over the plates until it is whole. Suits have no energy any more, so docking is purely repair (the Mark 6 and Mark 7 also repair themselves out of combat). Build a Stark Gantry Floor within 20 blocks and its arms put the suit on and take it off (see Stark Gantry). Sneak + right-click the platform with the Mark 2 Briefcase in your pack to unfold the Mark 2 onto the rack; with a Mark 2 racked, the Briefcase tab beside the platform screen folds it back into its case.',
		'projecthero.guide.iron_man.remote_pilot.body': 'Unsuited with the Stark Glasses on, open the Call Armour picker and press PILOT on a full suit. It deploys as a drone and your view jumps into it: WASD flies, Jump / Sneak go up and down, Sprint boosts, click or R fires its repulsor. Your real body stays behind and can be hurt. The link ends if you press C, take damage or go past 96 m; the drone then flies back and docks in your inventory.',
		'projecthero.guide.iron_man.armour_rules.body': 'Iron Man armour only goes on with C, a suit call, the Briefcase or the Stark Gantry, and only comes off with C, the Stark Gantry or a send-home (it drops like any armour when you die). Every suit is at least diamond-grade, bulletproof against guns and gives 70% knockback resistance (the Hulkbuster 100%). There is no suit energy: integrity is the resource. While the suit has integrity it absorbs its Mark\'s share of every hit (20% Mark 1 ... 60% Hulkbuster) out of its integrity, 1:1; at 0 it is disabled -- no flight, no abilities, heavy Slowness, a dark visor -- until repaired. From the Mark 3 up the mob highlight switches on by itself with the helmet. A full suit gives Haste I in the Mark 1, 1 V2, 2 and 3, Haste II from the Mark 4 up. An open faceplate snaps shut the moment you take off, use an ability or get hit.',
		'projecthero.guide.iron_man.glasses.body': 'Craft the Stark Glasses (two gold ingots over red stained glass pane, Basic Circuit, red stained glass pane), press Shift + N to open the Stark Gear screen and put them in the Eyes slot -- or just right-click them. They stay on under a suit. Wearing them you get automatic night vision, you can call your Mark 8 Hulkbuster (the Sneak + C picker, the double-tap quick call, /ironman suit, Remote Pilot) -- it drops in from orbit -- and Protocol Phoenix is armed. The Mark 7 Space armour is called with the Colantotte Bracelets, and the Mark 2 travels in its Briefcase.',
		'projecthero.guide.iron_man.bracelets.body': 'Store a Mark 7 Space armour on a Suit Platform and press the red Bracelets tab beside its menu to get the Colantotte Bracelets -- one pair, bound to you. Wear them in the Stark Gear slot instead of the glasses: they call ONLY the Mark 7 -- press C and its orbital pod is on its way (twice as fast as a normal call), lands beside you and fires the pieces onto you one by one. The call is free (suits have no energy) but uses the bracelets up. A Mark 7 you carry in your pack still wraps on in about 4 seconds with them on.',
		'projecthero.guide.iron_man.phoenix.body': 'If you would die while not in a full suit, Protocol Phoenix stops the death, holds you still and calls in your best Mark 7 or later suit (from your pack or a Suit Platform, even far away). Once it is on you are back on 5 hearts and the suit loses 10% integrity. It only works with your Stark Glasses on, needs that suit at 10%+ integrity, and recharges for 20 minutes.',
		'projecthero.guide.iron_man.sentry.body': 'On the Mark 8 Hulkbuster, C is Sentry Mode. Press it in the full suit and the back of the armour opens; you walk out of it and it stands there with its own integrity. Right-click it to open or close the back; Sneak + right-click the open suit to step back in (you can\'t be hurt meanwhile). Sneak + right-click the closed suit to cycle its mode: Regular, Defensive (fights for you with repulsor blasts and punches) and Follow. Modes and shots cost nothing -- a sentry runs until its integrity is gone (hits take it 1:1; it never repairs itself), then powers down where it stands. If a mob or player hurts you within 64 blocks it switches to Defensive by itself. Called with the Stark Glasses, the whole Hulkbuster drops in from orbit and lands 2 blocks in front of you in Regular mode. Sneak + C on the worn suit sends it home.',
		'projecthero.guide.iron_man.step.tony_stark': 'Right-click it: gain Tony Stark',
	} },
]);
