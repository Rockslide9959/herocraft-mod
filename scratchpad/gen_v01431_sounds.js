// v0.14.31: generates the Iron Man move sound events (vanilla-file layers only) into sounds.json + en_us.json,
// and writes scratchpad/lang_v01431_sounds.json. Every vanilla path is checked against the 1.21.1 asset index.
// Run from the repo root:  node scratchpad/gen_v01431_sounds.js
const fs = require('fs');
const path = require('path');
const os = require('os');

const INDEX = path.join(os.homedir(), '.gradle/caches/fabric-loom/assets/indexes/1.21.1-17.json');
const SOUNDS = 'src/main/resources/assets/projecthero/sounds.json';
const LANG = 'src/main/resources/assets/projecthero/lang/en_us.json';
const OUT_LANG = 'scratchpad/lang_v01431_sounds.json';

// s(file, volume, pitch, weight?) -- file is relative to minecraft/sounds, no .ogg
const s = (name, volume, pitch, weight) => {
	const o = { name: 'minecraft:' + name, volume, pitch };
	if (weight && weight !== 1) o.weight = weight;
	return o;
};
const many = (base, nums, volume, pitch) => nums.map(n => s(base + n, volume, pitch));

// [event, subtitle key suffix, subtitle text, sounds[]]
// Layer events share their move's subtitle key, so a layered move shows ONE subtitle line.
const EVENTS = [
	// ---- repulsors
	['ironman_repulsor_charge', 'ironman_repulsor_charge', 'Repulsor charges', [
		...many('block/respawn_anchor/charge', [1, 2, 3], 0.5, 1.4),
		...many('block/conduit/short', [1, 2, 3, 4], 0.55, 1.6)]],
	['ironman_repulsor_ready', 'ironman_repulsor_ready', 'Repulsor fully charged', [
		s('block/amethyst/shimmer', 0.8, 1.6),
		s('block/beacon/power1', 0.6, 1.9)]],
	['ironman_repulsor_blast', 'ironman_repulsor_blast', 'Repulsor fires', [
		...many('entity/wind_charge/wind_burst', [1, 2, 3], 0.9, 1.35),
		s('mob/breeze/shoot', 0.8, 1.5)]],
	['ironman_repulsor_zap', 'ironman_repulsor_blast', null, [
		...many('block/conduit/attack', [1, 2, 3], 0.7, 1.6)]],
	['ironman_repulsor_charged_blast', 'ironman_repulsor_charged_blast', 'Charged repulsor blasts', [
		...many('entity/wind_charge/wind_burst', [1, 2, 3], 1.0, 0.75),
		s('fireworks/largeblast1', 1.0, 0.65)]],
	['ironman_energy_impact', 'ironman_energy_impact', 'Energy blast hits', [
		...many('random/explode', [1, 2, 3, 4], 0.5, 1.7),
		...many('entity/wind_charge/wind_burst', [1, 2], 0.6, 1.6)]],
	// ---- Unibeam
	['ironman_unibeam_charge', 'ironman_unibeam_charge', 'Unibeam charges', [
		s('block/beacon/activate', 1.0, 1.2)]],
	['ironman_unibeam_loop', 'ironman_unibeam_loop', 'Unibeam hums', [
		...many('block/conduit/short', [1, 2, 3, 4, 5, 6, 7, 8, 9], 0.6, 0.6),
		...many('block/respawn_anchor/ambient', [1, 2, 3], 0.6, 0.7)]],
	['ironman_beam_crackle', 'ironman_unibeam_loop', null, [
		...many('block/campfire/crackle', [1, 2, 3, 4, 5, 6], 0.8, 0.8),
		...many('block/conduit/attack', [1, 2, 3], 0.45, 0.7)]],
	['ironman_unibeam_end', 'ironman_unibeam_end', 'Unibeam powers down', [
		s('block/beacon/deactivate', 0.9, 0.7),
		s('block/conduit/deactivate', 0.9, 0.8)]],
	// ---- missiles / rockets
	['ironman_missile_pod', 'ironman_missile_pod', 'Missile pods open', [
		s('tile/piston/out', 0.7, 1.6),
		s('block/vault/open_shutter', 0.8, 1.4)]],
	['ironman_missile_launch', 'ironman_missile_launch', 'Micro-missile launches', [
		s('fireworks/launch1', 0.8, 1.5, 2),
		s('fireworks/launch1', 0.8, 1.7),
		s('mob/ghast/fireball4', 0.5, 1.8)]],
	['ironman_rocket_launch', 'ironman_rocket_launch', 'Rocket launches', [
		s('fireworks/launch1', 1.0, 0.7, 2),
		s('fireworks/launch1', 1.0, 0.8),
		s('mob/ghast/fireball4', 0.8, 0.6)]],
	['ironman_missile_impact', 'ironman_missile_impact', 'Missile detonates', [
		...many('random/explode', [1, 2, 3, 4], 0.8, 1.3),
		s('fireworks/blast1', 0.8, 0.8)]],
	// ---- Mark III miniguns
	['ironman_minigun_spin', 'ironman_minigun_spin', 'Miniguns spin up', [
		s('block/crafter/craft', 0.7, 1.8),
		s('tile/piston/out', 0.6, 1.8)]],
	['ironman_minigun_fire', 'ironman_minigun_fire', 'Miniguns rattle', [
		...many('item/crossbow/shoot', [1, 2, 3], 0.6, 1.8),
		s('note/snare', 0.7, 1.4, 2),
		...many('mob/zombie/metal', [1, 2, 3], 0.35, 2.0)]],
	['ironman_minigun_stop', 'ironman_minigun_stop', 'Miniguns spin down', [
		s('tile/piston/in', 0.7, 1.2),
		s('item/crossbow/loading_end', 0.7, 1.4)]],
	// ---- flares
	['ironman_flare_launch', 'ironman_flare_launch', 'Flares pop', [
		s('fireworks/blast1', 0.9, 1.6),
		s('fireworks/blast1', 0.9, 1.4),
		s('fireworks/blast_far1', 0.9, 1.5)]],
	['ironman_flare_crackle', 'ironman_flare_launch', null, [
		s('fireworks/twinkle1', 0.9, 1.2),
		s('fireworks/twinkle_far1', 0.9, 1.4)]],
	['ironman_flare_hit', 'ironman_flare_hit', 'Flare strikes', [
		s('fireworks/blast1', 0.8, 1.2),
		s('fire/ignite', 0.9, 1.0)]],
	// ---- sonic clap
	['ironman_sonic_clap', 'ironman_sonic_clap', 'Sonic clap booms', [
		...many('mob/warden/sonic_boom', [1, 2, 3, 4], 1.0, 0.75)]],
	['ironman_clap_clang', 'ironman_sonic_clap', null, [
		s('random/anvil_land', 0.5, 1.9),
		s('item/mace/smash_ground_heavy', 0.8, 1.4)]],
	// ---- wrist laser
	['ironman_laser_start', 'ironman_laser_start', 'Wrist laser ignites', [
		s('mob/guardian/attack_loop', 0.6, 1.6)]],
	['ironman_laser', 'ironman_laser', 'Laser sizzles', [
		s('random/fizz', 0.45, 1.8, 2),
		s('random/fizz', 0.45, 2.0),
		...many('block/campfire/crackle', [1, 2, 3], 0.5, 1.7)]],
	['ironman_laser_end', 'ironman_laser_end', 'Wrist laser cuts out', [
		s('block/beacon/deactivate', 0.8, 1.6),
		s('random/fizz', 0.6, 1.0)]],
	['ironman_power_fail', 'ironman_power_fail', 'Suit systems fail', [
		...many('block/respawn_anchor/deplete', [1, 2], 1.0, 0.6),
		s('block/beacon/deactivate', 1.0, 0.5)]],
	// ---- shields
	['ironman_shield_up', 'ironman_shield_up', 'Repulsor shield hums up', [
		s('block/conduit/activate', 0.9, 1.5, 2),
		s('block/beacon/power2', 0.8, 1.6)]],
	['ironman_shield_hum', 'ironman_shield_hum', 'Repulsor shield hums', [
		...many('block/respawn_anchor/ambient', [1, 2, 3], 0.35, 1.5),
		...many('block/conduit/short', [5, 6, 7], 0.35, 1.8)]],
	['ironman_shield_down', 'ironman_shield_down', 'Repulsor shield drops', [
		s('block/conduit/deactivate', 0.8, 1.4),
		s('block/beacon/deactivate', 0.8, 1.5)]],
	['ironman_shield_deflect', 'ironman_shield_deflect', 'Repulsor shield deflects', [
		...many('item/shield/block', [1, 2, 3, 4, 5], 0.7, 1.4),
		...many('block/amethyst/place', [1, 2], 0.7, 1.2)]],
	// ---- dash / flight bursts
	['ironman_dash', 'ironman_dash', 'Repulsor dash', [
		...many('entity/wind_charge/wind_burst', [1, 2, 3], 1.0, 0.9),
		...many('mob/breeze/wind_burst', [1, 2, 3], 1.0, 0.8)]],
	['ironman_flight_burst', 'ironman_flight_burst', 'Boot jets ignite', [
		s('mob/ghast/fireball4', 1.0, 0.6),
		s('fireworks/launch1', 1.0, 0.5),
		...many('mob/blaze/breathe', [1, 2], 0.8, 0.5)]],
	['ironman_supersonic', 'ironman_supersonic', 'Supersonic boom', [
		s('fireworks/largeblast_far1', 1.0, 0.7),
		s('item/trident/riptide3', 1.0, 0.8)]],
	['ironman_supersonic_end', 'ironman_supersonic_end', 'Thrusters throttle back', [
		s('item/trident/riptide1', 0.6, 0.6),
		s('block/beacon/deactivate', 0.5, 1.4)]],
	// ---- Mark 6 surge
	['ironman_surge', 'ironman_surge', 'Arc reactor surges', [
		s('block/beacon/activate', 1.0, 1.5)]],
	['ironman_surge_crackle', 'ironman_surge', null, [
		...many('block/conduit/attack', [1, 2, 3], 0.8, 1.2)]],
	['ironman_surge_pulse', 'ironman_surge_pulse', 'Arc reactor thrums', [
		...many('block/conduit/short', [1, 2, 3, 4, 5, 6, 7, 8, 9], 0.4, 1.9)]],
	['ironman_surge_end', 'ironman_surge_end', 'Arc reactor settles', [
		s('block/beacon/deactivate', 0.9, 1.2),
		...many('block/respawn_anchor/deplete', [1, 2], 0.8, 1.3)]],
	// ---- JARVIS / HUD
	['ironman_scan', 'ironman_scan', 'JARVIS scans', [
		...many('block/beacon/power', [1, 2, 3], 0.6, 1.9),
		...many('block/sculk_sensor/sculk_clicking', [1, 2, 3, 4, 5, 6], 0.5, 1.6)]],
	['ironman_scan_chirp', 'ironman_scan', null, [
		s('note/bit', 0.6, 1.7),
		s('note/pling', 0.5, 1.8),
		s('note/bit', 0.6, 2.0)]],
	['ironman_target_lock', 'ironman_target_lock', 'Target locked', [
		s('note/bit', 0.5, 1.9),
		s('note/icechime', 0.4, 1.8)]],
	['ironman_hud_on', 'ironman_hud_on', 'HUD overlay on', [
		s('ui/toast/in', 0.6, 1.5),
		s('note/bit', 0.4, 1.5)]],
	['ironman_hud_off', 'ironman_hud_off', 'HUD overlay off', [
		s('ui/toast/out', 0.6, 1.5)]],
	['ironman_weapon_select', 'ironman_weapon_select', 'Weapon selected', [
		s('block/copper_bulb/toggle', 0.6, 1.6),
		s('random/click', 0.4, 1.7)]],
	// ---- Mark 5 blades
	['ironman_blade_extend', 'ironman_blade_extend', 'Gauntlet blades extend', [
		...many('item/trident/throw', [1, 2], 0.8, 1.7),
		...many('block/chain/break', [1, 2, 3, 4], 0.7, 1.8)]],
	['ironman_blade_ring', 'ironman_blade_extend', null, [
		...many('block/smithing_table/smithing_table', [1, 2, 3], 0.5, 1.8),
		s('random/anvil_use', 0.3, 1.9)]],
	['ironman_blade_retract', 'ironman_blade_retract', 'Gauntlet blades retract', [
		s('tile/piston/in', 0.7, 1.6),
		...many('block/chain/step', [1, 2, 3], 0.7, 1.4),
		...many('item/armor/equip_chain', [1, 2], 0.7, 1.2)]],
	// ---- Mark 1: flamethrower / punch / clunk
	['ironman_flamethrower_ignite', 'ironman_flamethrower_ignite', 'Flamethrower ignites', [
		s('fire/ignite', 1.0, 0.8),
		s('mob/ghast/fireball4', 0.7, 0.7)]],
	['ironman_flamethrower', 'ironman_flamethrower', 'Flamethrower roars', [
		...many('mob/blaze/breathe', [1, 2, 3, 4], 0.6, 0.7),
		...many('block/campfire/crackle', [1, 2, 3, 4, 5, 6], 0.8, 0.6)]],
	['ironman_punch', 'ironman_punch', 'Armoured punch lands', [
		s('mob/irongolem/throw', 1.0, 0.8),
		...many('item/mace/smash_ground', [1, 2, 3], 0.8, 1.2),
		s('random/anvil_land', 0.4, 0.7)]],
	['ironman_mk1_clunk', 'ironman_mk1_clunk', 'Crude armour clanks', [
		s('tile/piston/out', 0.6, 0.7),
		...many('block/iron_trapdoor/close', [1, 2, 3, 4], 0.6, 0.8),
		...many('mob/irongolem/walk', [1, 2], 0.6, 0.9)]],
	// ---- combo / suit stored
	['ironman_combo_finisher', 'ironman_combo_finisher', 'Staggered foe is smashed', [
		s('mob/irongolem/throw', 1.0, 1.0),
		...many('item/mace/smash_ground', [1, 2, 3, 4], 0.9, 1.3)]],
	['ironman_suit_stored', 'ironman_suit_stored', 'Suit powers down', [
		s('block/beacon/deactivate', 0.8, 1.0),
		s('block/conduit/deactivate', 0.8, 1.1)]],
];

// ------------------------------------------------------------------ verify
const index = JSON.parse(fs.readFileSync(INDEX, 'utf8')).objects;
let bad = 0;
for (const [ev, , , sounds] of EVENTS) {
	for (const snd of sounds) {
		const file = 'minecraft/sounds/' + snd.name.slice('minecraft:'.length) + '.ogg';
		if (!index[file]) {
			console.error('MISSING', ev, snd.name);
			bad++;
		}
		if (snd.volume > 1.2) { console.error('LOUD', ev, snd.name); bad++; }
		if (snd.pitch < 0.5 || snd.pitch > 2.0) { console.error('PITCH', ev, snd.name); bad++; }
	}
}
if (bad) {
	process.exit(1);
}

// ------------------------------------------------------------------ write sounds.json (sounds.json has no duplicate keys)
const sounds = JSON.parse(fs.readFileSync(SOUNDS, 'utf8'));
for (const [ev, subKey, , snds] of EVENTS) {
	sounds[ev] = { subtitle: 'subtitles.projecthero.' + subKey, sounds: snds };
}
fs.writeFileSync(SOUNDS, JSON.stringify(sounds, null, 2).replace(/\n/g, '\r\n') + '\r\n');

// ------------------------------------------------------------------ lang (appended textually: en_us.json has duplicate
// keys that a parse/re-stringify would silently collapse)
const added = {};
for (const [, subKey, text] of EVENTS) {
	if (text) {
		added['subtitles.projecthero.' + subKey] = text;
	}
}
let langText = fs.readFileSync(LANG, 'utf8');
const existing = JSON.parse(langText);
const fresh = Object.entries(added).filter(([k]) => !(k in existing));
if (fresh.length) {
	const close = langText.lastIndexOf('}');
	const body = langText.slice(0, close).replace(/\s+$/, '');
	const lines = fresh.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
	langText = body + ',\r\n' + lines.join(',\r\n') + '\r\n}\r\n';
	JSON.parse(langText); // still valid
	fs.writeFileSync(LANG, langText);
}
fs.writeFileSync(OUT_LANG, JSON.stringify(added, null, 2).replace(/\n/g, '\r\n') + '\r\n');

console.log('events:', EVENTS.length, 'lang keys:', Object.keys(added).length);
console.log(EVENTS.map(e => e[0]).join(' '));
