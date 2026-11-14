// v0.17.4: the Saiyan's sound events in two sets (see SaiyanSounds' javadoc).
//  1. PUBLIC: adds the new events to assets/projecthero/sounds.json, pointed at the old v0.17.3 sounds (synth files or vanilla
//     files, with the old pitch/volume baked in), and their subtitles to en_us.json. Key by key, never rewriting the files.
//  2. TEST: writes resourcepacks/saiyan_dbz/ (pack.mcmeta + assets/projecthero/sounds.json) that REPLACES every Saiyan event with
//     the user's Dragon Ball cuts (sounds/saiyan/dbz/cut_*.ogg, git-ignored). The client only enables that pack when the cuts are
//     in the jar (client/saiyan/SaiyanDbzSounds).
//   node scratchpad/saiyan/dbz_sounds_json.js     (from the repo root; safe to re-run)
const fs = require('fs');
const path = require('path');
const RES = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const ASSETS = path.join(RES, 'assets', 'projecthero');

const s = (name, o = {}) => ({ name, ...o });
const syn = (n, o) => s('projecthero:saiyan/' + n, o);
const mc = (n, o) => s('minecraft:' + n, o);
const many = (base, n, o) => Array.from({ length: n }, (_, i) => mc(base + (i + 1), o));
const DASH = o => [syn('ki_dash1', o), syn('ki_dash2', o)];

/** New events: [key, subtitle, public fallback sounds]. */
const NEW = [
	['saiyan_take_off', 'Saiyan takes off', DASH({ pitch: 0.8, volume: 0.82 })],
	['saiyan_land', 'Saiyan lands', DASH({ pitch: 0.7, volume: 0.67 })],
	['saiyan_land_heavy', 'Saiyan crashes down', DASH({ pitch: 0.7, volume: 0.5 })],
	['saiyan_ui_dodge', 'Saiyan sidesteps', DASH({ pitch: 1.9, volume: 0.7 })],
	['saiyan_punch', 'Punch lands', many('entity/player/attack/strong', 6, { pitch: 1.1, volume: 0.9 })],
	['saiyan_punch_heavy', 'Heavy blow lands', many('entity/player/attack/knockback', 4, { pitch: 1.0 })],
	['saiyan_finisher', 'Finishing blow', many('entity/player/attack/knockback', 4, { pitch: 0.9, volume: 0.9 })],
	['saiyan_swing', 'Fist cuts the air', DASH({ pitch: 1.7, volume: 0.45 })],
	['saiyan_flurry', 'Flurry of blows', many('entity/player/attack/sweep', 7, { pitch: 1.1, volume: 0.75 })],
	['saiyan_vanish', 'Saiyan vanishes', [mc('mob/endermen/portal', { volume: 0.75 }), mc('mob/endermen/portal2', { volume: 0.75 })]],
	['saiyan_appear', 'Saiyan reappears', [mc('mob/endermen/portal', { pitch: 1.3, volume: 0.65 }), mc('mob/endermen/portal2', { pitch: 1.3, volume: 0.65 })]],
	['saiyan_ki_blast', 'Ki blast fires', DASH({ pitch: 1.9, volume: 0.63 })],
	['saiyan_ki_blast_hit', 'Ki blast bursts', [mc('fireworks/blast1', { pitch: 1.7, volume: 0.65 })]],
	['saiyan_rain_shot', 'Ki Rain falls', DASH({ pitch: 1.9, volume: 0.7 })],
	['saiyan_kame_charge', 'Kamehameha charges', [syn('ki_charge_start')]],
	['saiyan_kame_fire', 'Kamehameha fires', [mc('fireworks/blast1', { pitch: 1.35, volume: 0.3 })]],
	['saiyan_kame_loop', 'Kamehameha roars', [syn('ki_charge_loop', { pitch: 0.85 })]],
	['saiyan_boom', 'Ki explosion', many('random/explode', 4, { pitch: 1.2, volume: 0.75, attenuation_distance: 32 })],
	['saiyan_wave', 'Explosive Wave', many('random/explode', 4, { pitch: 1.2, volume: 0.57, attenuation_distance: 32 })],
	['saiyan_spirit_bomb_rise', 'Spirit Bomb: hands raised', [syn('ki_charge_start', { pitch: 0.7, volume: 0.5 })]],
	['saiyan_spirit_bomb_charge', 'Spirit Bomb gathers energy', [mc('block/beacon/ambient', { pitch: 0.6, volume: 0.8 })]],
	['saiyan_spirit_bomb_throw', 'Spirit Bomb thrown', DASH({ pitch: 0.5, volume: 0.45 })],
	['saiyan_spirit_bomb_explode', 'Spirit Bomb explodes', [syn('power_down', { pitch: 0.5, volume: 0.5 })]],
	['saiyan_spirit_bomb_boom', 'Distant rumble', many('random/explode', 4, { pitch: 0.5, volume: 0.9 })],
];

const d = (n, o) => s('projecthero:saiyan/dbz/' + n, o);
/** Test-build replacements: every Saiyan event -> the cuts (dbz_cut.js). */
const DBZ = {
	saiyan_ki_charge_start: [d('cut_charge_start')],
	saiyan_ki_charge_loop: [d('cut_charge_loop')],
	// v0.17.4 (user): "make it the same sound as the charging sfx" -- the aura loops the V charge's own loop
	saiyan_aura_loop_gold: [d('cut_charge_loop')],
	saiyan_aura_loop_red: [d('cut_charge_loop', { pitch: 0.88 })],
	saiyan_transform_burst: [d('cut_transform_1', { attenuation_distance: 32 }), d('cut_transform_2', { attenuation_distance: 32 })],
	saiyan_power_down: [d('cut_power_down')],
	saiyan_ki_dash: [d('cut_dash_1'), d('cut_dash_2')],
	saiyan_take_off: [d('cut_take_off')],
	saiyan_land: [d('cut_land_1'), d('cut_land_2')],
	saiyan_land_heavy: [d('cut_land_heavy_1', { attenuation_distance: 24 }), d('cut_land_heavy_2', { attenuation_distance: 24 })],
	saiyan_ui_dodge: [d('cut_dodge_1'), d('cut_dodge_2'), d('cut_dodge_3')],
	saiyan_punch: [d('cut_punch_1'), d('cut_punch_2')],
	saiyan_punch_heavy: [d('cut_punch_heavy')],
	saiyan_finisher: [d('cut_finisher')],
	saiyan_swing: [d('cut_swing')],
	saiyan_flurry: [d('cut_flurry_1'), d('cut_flurry_2')],
	saiyan_vanish: [d('cut_vanish_away')],
	saiyan_appear: [d('cut_vanish')],
	saiyan_ki_blast: [d('cut_blast_1'), d('cut_blast_2'), d('cut_blast_3')],
	saiyan_ki_blast_hit: [d('cut_blast_hit')],
	saiyan_rain_shot: [d('cut_rain_shot')],
	saiyan_kame_charge: [d('cut_kame_charge')],
	saiyan_kame_fire: [d('cut_kame_fire', { attenuation_distance: 32 })],
	saiyan_kame_loop: [d('cut_beam_loop')],
	saiyan_boom: [d('cut_boom_1', { attenuation_distance: 32 }), d('cut_boom_2', { attenuation_distance: 32 })],
	saiyan_wave: [d('cut_wave', { attenuation_distance: 32 })],
	saiyan_spirit_bomb_rise: [d('cut_sb_rise')],
	saiyan_spirit_bomb_charge: [d('cut_sb_charge')],
	saiyan_spirit_bomb_throw: [d('cut_sb_throw')],
	saiyan_spirit_bomb_explode: [d('cut_sb_explode')],
	saiyan_spirit_bomb_boom: [d('cut_sb_boom')],
};

// ---- 1. public sounds.json + subtitles, inserted after saiyan_exhausted (text insert, the files keep their formatting)
const indent = (json, pad) => json.split('\n').map((l, i) => (i ? pad + l : l)).join('\n');
const sjPath = path.join(ASSETS, 'sounds.json');
let sj = fs.readFileSync(sjPath, 'utf8');
const nl = sj.includes('\r\n') ? '\r\n' : '\n';
const parsed = JSON.parse(sj);
let add = '';
for (const [key, , sounds] of NEW) {
	if (parsed[key]) {
		continue;
	}
	const body = JSON.stringify({ subtitle: 'subtitles.projecthero.' + key, sounds }, null, 2);
	add += `,${nl}  "${key}": ${indent(body, '  ').replace(/\n/g, nl)}`;
}
if (add) {
	const anchor = sj.indexOf('"saiyan_exhausted"');
	const end = sj.indexOf(nl + '  }', anchor) + (nl + '  }').length;
	sj = sj.slice(0, end) + add + sj.slice(end);
	JSON.parse(sj);
	fs.writeFileSync(sjPath, sj);
}

const langPath = path.join(ASSETS, 'lang', 'en_us.json');
let lang = fs.readFileSync(langPath, 'utf8');
const lnl = lang.includes('\r\n') ? '\r\n' : '\n';
const langObj = JSON.parse(lang);
let ladd = '';
for (const [key, sub] of NEW) {
	const k = 'subtitles.projecthero.' + key;
	if (!(k in langObj)) {
		ladd += `${lnl}  "${k}": ${JSON.stringify(sub)},`;
	}
}
if (ladd) {
	const anchorLine = lang.indexOf('"subtitles.projecthero.saiyan_exhausted"');
	const eol = lang.indexOf(lnl, anchorLine);
	lang = lang.slice(0, eol) + ladd + lang.slice(eol);
	if (!lang.slice(anchorLine, eol).trim().endsWith(',')) {
		throw new Error('anchor line has no trailing comma');
	}
	JSON.parse(lang);
	fs.writeFileSync(langPath, lang);
}

// ---- 2. the test-build pack
const PACK = path.join(RES, 'resourcepacks', 'saiyan_dbz');
fs.mkdirSync(path.join(PACK, 'assets', 'projecthero'), { recursive: true });
fs.writeFileSync(path.join(PACK, 'pack.mcmeta'), JSON.stringify({
	pack: { pack_format: 34, description: 'Project Hero: Saiyan Dragon Ball clips (local test builds only)' },
}, null, 2) + '\n');
const all = JSON.parse(fs.readFileSync(sjPath, 'utf8'));
const out = {};
for (const [key, sounds] of Object.entries(DBZ)) {
	if (!all[key]) {
		throw new Error('no public event ' + key);
	}
	out[key] = { replace: true, subtitle: all[key].subtitle, sounds };
}
fs.writeFileSync(path.join(PACK, 'assets', 'projecthero', 'sounds.json'), JSON.stringify(out, null, 2) + '\n');
console.log('public events added:', add ? NEW.length : 0, '| pack events:', Object.keys(out).length);
