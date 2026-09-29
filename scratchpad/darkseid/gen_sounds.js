// v0.13.18: adds the Darkseid Raid's sound events to sounds.json (vanilla files, re-pitched) and verifies every
// referenced vanilla file against the 1.21.1 asset index. Run from the repo root: node scratchpad/darkseid/gen_sounds.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const FILE = path.join(__dirname, '../../src/main/resources/assets/projecthero/sounds.json');
const IDX = path.join(os.homedir(), '.gradle/caches/fabric-loom/assets/indexes/1.21.1-17.json');
const index = new Set(Object.keys(JSON.parse(fs.readFileSync(IDX, 'utf8')).objects)
	.filter(k => k.startsWith('minecraft/sounds/')).map(k => 'minecraft:' + k.slice(17).replace(/\.ogg$/, '')));
const s = (name, volume, pitch, extra) => Object.assign({ name: 'minecraft:' + name, volume, pitch }, extra || {});
const range = (base, from, to, volume, pitch) => { const o = []; for (let i = from; i <= to; i++) o.push(s(base + i, volume, pitch)); return o; };
const events = {
	darkseid_entrance: [s('mob/warden/emerge', 1.0, 0.6), s('mob/wither/spawn', 0.8, 0.45)],
	darkseid_step: [...range('mob/warden/step_', 1, 4, 1.2, 0.55)],
	darkseid_punch: [...range('mob/irongolem/hit', 1, 4, 1.0, 0.55), s('mob/warden/attack_impact_1', 1.0, 0.7), s('mob/warden/attack_impact_2', 1.0, 0.7)],
	darkseid_slam: [s('item/mace/smash_ground_heavy', 1.0, 0.6), ...range('random/explode', 1, 4, 0.8, 0.55)],
	omega_beam_charge: [...range('mob/warden/sonic_charge', 1, 4, 1.0, 0.75)],
	omega_beam_fire: [...range('mob/warden/sonic_boom', 1, 4, 0.9, 1.25)],
	omega_impact: [...range('random/explode', 1, 4, 0.7, 1.35)],
	darkseid_grip: [s('mob/evocation_illager/cast1', 1.0, 0.45), s('mob/evocation_illager/cast2', 1.0, 0.45)],
	darkseid_teleport: [s('mob/endermen/portal', 1.0, 0.45), s('mob/endermen/portal2', 1.0, 0.45)],
	boom_tube: [...range('ambient/weather/thunder', 1, 3, 0.8, 1.5), s('item/trident/thunder1', 0.9, 1.3), s('item/trident/thunder2', 0.9, 1.3)],
	mother_box_hum: [s('block/beacon/ambient', 0.6, 1.6), s('block/conduit/ambient', 0.6, 1.4)],
	mother_box_activate: [s('block/beacon/activate', 1.0, 1.2), s('block/conduit/activate', 1.0, 1.1)],
	mother_box_disable: [s('block/beacon/deactivate', 1.0, 0.8), s('block/conduit/deactivate', 1.0, 0.8)],
	mother_box_overload: [s('block/respawn_anchor/deplete1', 1.0, 0.6), s('block/respawn_anchor/deplete2', 1.0, 0.6)],
	darkseid_phase: [...range('mob/warden/roar_', 1, 5, 1.2, 0.55)],
	omega_annihilation: [s('block/trial_spawner/ominous_activate', 1.0, 0.5), s('mob/warden/sonic_charge1', 1.0, 0.5)],
	darkseid_hurt: [...range('mob/guardian/elder_hit', 1, 4, 0.9, 0.5)],
	darkseid_death: [s('mob/wither/death', 1.0, 0.55), s('mob/enderdragon/end', 0.8, 0.8)],
	apokolips_victory: [s('ui/toast/challenge_complete', 1.0, 0.8)],
	apokolips_defeat: [s('mob/wither/death', 1.0, 0.4)],
	parademon_screech: [s('mob/vex/idle1', 1.0, 0.55), s('mob/vex/idle2', 1.0, 0.55), s('mob/phantom/idle1', 0.8, 0.9), s('mob/silverfish/say1', 1.0, 0.6)],
	parademon_hurt: [s('mob/vex/hurt1', 1.0, 0.6), s('mob/vex/hurt2', 1.0, 0.6)],
	parademon_death: [s('mob/vex/death1', 1.0, 0.55), s('mob/vex/death2', 1.0, 0.55)],
	parademon_wings: [s('mob/phantom/flap1', 0.5, 1.3), s('mob/phantom/flap2', 0.5, 1.3), s('mob/phantom/flap3', 0.5, 1.3)],
	apokolips_music: [s('music/game/end/boss', 1.0, 0.9, { stream: true })],
};
const json = JSON.parse(fs.readFileSync(FILE, 'utf8'));
let missing = 0;
for (const [ev, sounds] of Object.entries(events)) {
	for (const x of sounds) if (!index.has(x.name)) { console.error('MISSING', ev, x.name); missing++; }
	json[ev] = { subtitle: 'subtitles.projecthero.' + ev, sounds };
}
if (missing) { console.error(missing + ' missing files -- not writing'); process.exit(1); }
fs.writeFileSync(FILE, JSON.stringify(json, null, 2) + '\n');
console.log('wrote', Object.keys(events).length, 'darkseid events; total events', Object.keys(json).length);
