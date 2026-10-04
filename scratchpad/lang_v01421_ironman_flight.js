// v0.14.21: Iron Man flight revamp -- subtitles for the client-side thruster / take-off / landing / sonic-boom sounds,
// and their sounds.json events (re-pitched vanilla sound files, no new audio assets).
// Re-runnable: lang keys are only ever set; a sounds.json event is only added if it is not there yet.
// Run from the repo root: node scratchpad/lang_v01421_ironman_flight.js
const fs = require('fs');
const path = require('path');

require('./langset.js')([
	{ anchor: 'subtitles.projecthero.apokolips_music', entries: {
		'subtitles.projecthero.ironman_thrusters': 'Repulsors roar',
		'subtitles.projecthero.ironman_takeoff': 'Repulsors ignite',
		'subtitles.projecthero.ironman_land': 'Iron Man lands hard',
		'subtitles.projecthero.ironman_sonic_boom': 'Sonic boom',
		'subtitles.projecthero.ironman_power_down': 'Repulsors power down',
	} },
]);

const SOUNDS = path.join(__dirname, '../src/main/resources/assets/projecthero/sounds.json');
const events = {
	// the jet burn -- the volume / pitch curve is applied per tick in code (IronManFlightLook)
	ironman_thruster_roar: { subtitle: 'subtitles.projecthero.ironman_thrusters', sounds: [
		{ name: 'minecraft:fire/fire', volume: 1.0, pitch: 0.75 } ] },
	// the repulsor whirr, strongest at a hover
	ironman_thruster_whine: { sounds: [ { name: 'minecraft:mob/breeze/whirl', volume: 0.8, pitch: 1.0 } ] },
	// the air rush at speed
	ironman_thruster_wind: { sounds: [ { name: 'minecraft:item/elytra/elytra_loop', volume: 1.0, pitch: 1.0 } ] },
	ironman_takeoff: { subtitle: 'subtitles.projecthero.ironman_takeoff', sounds: [
		{ name: 'minecraft:mob/breeze/wind_burst1', volume: 0.9, pitch: 0.75 },
		{ name: 'minecraft:mob/breeze/wind_burst2', volume: 0.9, pitch: 0.75 },
		{ name: 'minecraft:mob/breeze/wind_burst3', volume: 0.9, pitch: 0.75 } ] },
	ironman_takeoff_kick: { sounds: [ { name: 'minecraft:fireworks/launch1', volume: 0.8, pitch: 0.6 } ] },
	ironman_land_impact: { subtitle: 'subtitles.projecthero.ironman_land', sounds: [
		{ name: 'minecraft:item/mace/smash_ground_heavy', volume: 1.0, pitch: 0.9 } ] },
	ironman_land_boom: { sounds: [
		{ name: 'minecraft:random/explode1', volume: 0.45, pitch: 1.5 },
		{ name: 'minecraft:random/explode2', volume: 0.45, pitch: 1.5 },
		{ name: 'minecraft:random/explode3', volume: 0.45, pitch: 1.5 },
		{ name: 'minecraft:random/explode4', volume: 0.45, pitch: 1.5 } ] },
	ironman_sonic_boom: { subtitle: 'subtitles.projecthero.ironman_sonic_boom', sounds: [
		{ name: 'minecraft:mob/warden/sonic_boom1', volume: 1.0, pitch: 0.75 },
		{ name: 'minecraft:mob/warden/sonic_boom2', volume: 1.0, pitch: 0.75 },
		{ name: 'minecraft:mob/warden/sonic_boom3', volume: 1.0, pitch: 0.75 },
		{ name: 'minecraft:mob/warden/sonic_boom4', volume: 1.0, pitch: 0.75 } ] },
	ironman_power_down: { subtitle: 'subtitles.projecthero.ironman_power_down', sounds: [
		{ name: 'minecraft:mob/breeze/land1', volume: 0.8, pitch: 1.2 },
		{ name: 'minecraft:mob/breeze/land2', volume: 0.8, pitch: 1.2 } ] },
};

let text = fs.readFileSync(SOUNDS, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(text);
const add = Object.entries(events).filter(([k]) => !(k in existing));
if (add.length > 0) {
	const close = text.lastIndexOf('}');
	let body = text.slice(0, close).replace(/\s*$/, '');
	const chunks = add.map(([k, v]) => {
		const json = JSON.stringify(v, null, 2).split('\n').map((l, i) => (i === 0 ? l : '  ' + l)).join(eol);
		return '  ' + JSON.stringify(k) + ': ' + json;
	});
	text = body + ',' + eol + chunks.join(',' + eol) + eol + '}' + eol;
	JSON.parse(text); // still valid
	fs.writeFileSync(SOUNDS, text);
}
console.log('sounds.json: added ' + add.length + ' event(s)');
