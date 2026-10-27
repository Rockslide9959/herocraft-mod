// v0.17.1: copies the chosen Explosion / Hellflame harness shots into docs/screenshots/v0171 (HUD shots as 2x crops).
//   node scratchpad/pick_shots_exhf_v0171.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const root = path.join(__dirname, '..');
const R = path.join(root, 'run', 'screenshots');
const D = path.join(root, 'docs', 'screenshots', 'v0171');
const pick = {
	ex_ap_shot_004: 'quirk_explosion_ap_shot', ex_pierce_fire_002: 'quirk_explosion_pierce', ex_punch_004: 'quirk_explosion_punch',
	ex_grip_006: 'quirk_explosion_crush_grip', ex_uppercut_002: 'quirk_explosion_uppercut', ex_cluster_006: 'quirk_explosion_cluster',
	ex_gauntlet_fire_006: 'quirk_explosion_gauntlet', ex_howitzer_021: 'quirk_explosion_howitzer', ex_howitzer_crater: 'quirk_explosion_crater',
	ex_ultimate_030: 'quirk_explosion_ultimate_chain', ex_ultimate_045: 'quirk_explosion_ultimate_blast',
	ex_fp_ap_shot_002: 'quirk_explosion_fp_ap_shot', ex_fp_pierce_charge: 'quirk_explosion_fp_pierce_charge',
	ex_mining_004: 'quirk_explosion_blast_mining',
	hf_spider_002: 'quirk_hellflame_spider', hf_mesh_006: 'quirk_hellflame_mesh', hf_hellblast_beam_006: 'quirk_hellflame_hellblast',
	hf_wave_004: 'quirk_hellflame_flame_wave', hf_pillar_010: 'quirk_hellflame_pillar', hf_wall_cloak_006: 'quirk_hellflame_wall_cloak',
	hf_vent_004: 'quirk_hellflame_heat_vent', hf_fist_3_002: 'quirk_hellflame_flashfire_fist', hf_beard_005: 'quirk_hellflame_beard_tell',
	hf_ultimate_015: 'quirk_hellflame_ultimate_sun', hf_ultimate_033: 'quirk_hellflame_ultimate_comet',
	hf_fp_fist_3_002: 'quirk_hellflame_fp_fist', hf_fp_hellblast_beam_006: 'quirk_hellflame_fp_hellblast',
	hf_fp_wave_002: 'quirk_hellflame_fp_flame_wave', hf_forge_004: 'quirk_hellflame_forge_hand',
};
for (const [k, v] of Object.entries(pick)) {
	fs.copyFileSync(path.join(R, 'exhf_' + k + '.png'), path.join(D, v + '.png'));
}
const crop = path.join(__dirname, 'crop_zoom_v0171.js');
for (const b of ['dry', 'primed', 'soaked']) {
	execFileSync(process.execPath, [crop, path.join(R, 'exhf_ex_hud_' + b + '.png'), path.join(D, 'quirk_explosion_hud_' + b + '.png'), '1000', '560', '280', '160', '2']);
}
for (const b of ['kindle', 'blaze', 'inferno', 'overheat']) {
	execFileSync(process.execPath, [crop, path.join(R, 'exhf_hf_hud_' + b + '.png'), path.join(D, 'quirk_hellflame_hud_' + b + '.png'), '1000', '560', '280', '160', '2']);
}
console.log('ok', Object.keys(pick).length + 7);
