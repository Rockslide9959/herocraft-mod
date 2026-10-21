// v0.16.7 (user): Era I energy costs lowered -- "the armour's energy drains way too fast, the player won't be able to use
// the armours for long missions". Mk I = the user's exact numbers; every other suit scaled the same way (about a third).
'use strict';
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', 'src', 'main', 'java', 'com', 'projecthero', 'mod', 'ironman', 'ability', 'IronManComicKit.java');
let s = fs.readFileSync(f, 'utf8');
// one-off costs (en(...)) and per-second drains (enPerSecond(...))
const COST = {
	// Mk I -- the user's numbers (smoke screen / scrap burst scaled to match)
	e1mk1_fire_wall: 20, e1mk1_iron_fist: 10, e1mk1_haymaker: 20, e1mk1_rocket_jump: 5, e1mk1_rocket_leap: 10,
	e1mk1_smoke_screen: 15, e1mk1_scrap_burst: 10, e1mk1_magnetic_repel: 10, e1mk1_brace: 25, e1mk1_reactor_overload: 50,
	// Mk II
	e1mk2_repulsor: 5, e1mk2_charged_repulsor: 20, e1mk2_gold_uppercut: 10, e1mk2_ground_pound: 15, e1mk2_jet_dash: 15,
	e1mk2_golden_glare: 15, e1mk2_jet_skates: 15, e1mk2_magnetic_pull: 10, e1mk2_magnetic_shield: 20, e1mk2_reactor_overload: 50,
	// Mk III
	e1mk3_repulsor: 5, e1mk3_twin_volley: 15, e1mk3_unibeam: 45, e1mk3_unibeam_sweep: 45, e1mk3_flight_boost: 15,
	e1mk3_barrel_roll: 10, e1mk3_mini_missiles: 30, e1mk3_missile_barrage: 45, e1mk3_tractor_throw: 15,
	// Mk IV
	e1mk4_torpedo: 15, e1mk4_homing_torpedo: 25, e1mk4_sonar_ping: 10, e1mk4_sonic_pulse: 20, e1mk4_hydro_surge: 10,
	e1mk4_ballast_drop: 15, e1mk4_depth_charge: 25, e1mk4_mine_field: 35, e1mk4_whirlpool: 30, e1mk4_bubble_shield: 25,
	// Mk V
	e1mk5_silenced_dart: 5, e1mk5_sleeper_dart: 15, e1mk5_silent_takedown: 10, e1mk5_shadow_strike: 25,
	e1mk5_light_bend_blink: 15, e1mk5_emp: 35, e1mk5_decoy: 25, e1mk5_radar: 10, e1mk5_thermal_vision: 15,
};
const PER_SECOND = {
	e1mk1_flamethrower: 5, e1mk2_boot_hover: 3, e1mk3_tractor_beam: 5, e1mk3_targeting_hud: 1, e1mk5_silent_glide: 3,
	e1mk5_silent_running: 2,
};
let n = 0;
for (const [id, v] of Object.entries(COST)) {
	const re = new RegExp('(en\\((?:tap|hold)\\("' + id + '",.*?Items\\.[A-Z_]+\\), )([0-9.]+)f\\)');
	if (!re.test(s)) throw new Error('no cost for ' + id);
	s = s.replace(re, (m, p) => p + v + 'f)');
	n++;
}
for (const [id, v] of Object.entries(PER_SECOND)) {
	const re = new RegExp('(enPerSecond\\((?:tap|hold)\\("' + id + '",.*?Items\\.[A-Z_]+\\), )([0-9.]+)f\\)');
	if (!re.test(s)) throw new Error('no drain for ' + id);
	s = s.replace(re, (m, p) => p + v + 'f)');
	n++;
}
s = s.replace('// docs/IRONMAN_ERA1_PLAN.md. Every key has a tap + Shift move; C\'s Shift is the emergency eject. en = energy.',
	'// docs/IRONMAN_ERA1_PLAN.md. Every key has a tap + Shift move; C\'s Shift is the emergency eject. en = energy\n\t\t// (v0.16.7, user: lowered to about a third so a reactor lasts a long mission; the Mk I\'s numbers are the user\'s).');
fs.writeFileSync(f, s);
console.log('updated ' + n + ' costs');
