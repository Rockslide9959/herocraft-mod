// v0.17.3 Saiyan sounds: sounds.json events + en_us.json subtitles + the startup hook. Safe to re-run (skips what exists).
// CRLF-preserving; refuses duplicate keys; validates both JSON files. Run from the repo root: node scratchpad/saiyan_sounds_json.js
const fs = require('fs');
const path = require('path');
const R = path.join(__dirname, '..', 'src', 'main');
const SND = path.join(R, 'resources', 'assets', 'projecthero', 'sounds.json');
const LANG = path.join(R, 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const OGG = path.join(R, 'resources', 'assets', 'projecthero', 'sounds', 'saiyan');

// event -> [file stems, subtitle]
const EVENTS = {
	ki_charge_start: [['ki_charge_start'], 'Ki surges'],
	ki_charge_loop: [['ki_charge_loop'], 'Ki rumbles'],
	aura_loop_gold: [['aura_loop_gold'], 'Golden aura roars'],
	aura_loop_blue: [['aura_loop_blue'], 'Blue aura hums'],
	aura_loop_red: [['aura_loop_red'], 'Crimson aura burns'],
	aura_loop_silver: [['aura_loop_silver'], 'Silver aura shimmers'],
	transform_burst: [['transform_burst1', 'transform_burst2'], 'Saiyan transforms'],
	ssj2_spark: [['ssj2_spark1', 'ssj2_spark2', 'ssj2_spark3'], 'Aura crackles'],
	power_down: [['power_down'], 'Aura fades'],
	ki_dash: [['ki_dash1', 'ki_dash2'], 'Ki dash'],
	instant_step: [['instant_step1', 'instant_step2'], 'Saiyan vanishes'],
	punch_hit_light: [['punch_hit_light1', 'punch_hit_light2', 'punch_hit_light3'], 'Punch lands'],
	punch_hit_heavy: [['punch_hit_heavy1', 'punch_hit_heavy2'], 'Heavy strike lands'],
	beam_charge: [['beam_charge'], 'Ki beam charges'],
	beam_fire_loop: [['beam_fire_loop'], 'Ki beam roars'],
	beam_impact: [['beam_impact1', 'beam_impact2'], 'Ki beam explodes'],
	orb_charge: [['orb_charge'], 'Spirit Orb gathers'],
	orb_explode: [['orb_explode'], 'Spirit Orb detonates'],
	dragon_rush: [['dragon_rush'], 'Dragon Rush'],
	barrage_rapid: [['barrage_rapid'], 'Barrage of blows'],
	instinct_dodge: [['instinct_dodge1', 'instinct_dodge2'], 'Instinct dodge'],
	exhausted: [['exhausted'], 'Saiyan runs out of Ki'],
};
for (const [stems] of Object.values(EVENTS)) for (const s of stems) if (!fs.existsSync(path.join(OGG, s + '.ogg'))) throw new Error('missing ogg ' + s);

function dupCheck(text, file) {
	const seen = new Set();
	const re = /^\s{2}"([^"]+)"\s*:/gm;
	let m;
	while ((m = re.exec(text))) { if (seen.has(m[1])) throw new Error(file + ': duplicate key ' + m[1]); seen.add(m[1]); }
}
function appendKeys(file, entries) {
	let t = fs.readFileSync(file, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
	const j = JSON.parse(t);
	const add = entries.filter(([k]) => !(k in j));
	if (add.length) {
		const end = t.lastIndexOf('}');
		let head = t.slice(0, end).replace(/\s*$/, '');
		head += ',\n' + add.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v, null, 2).replace(/\n/g, '\n  ')).join(',\n') + '\n';
		t = head + t.slice(end);
	}
	JSON.parse(t);
	dupCheck(t, file);
	fs.writeFileSync(file, crlf ? t.replace(/\n/g, '\r\n') : t);
	return add.length;
}
const n1 = appendKeys(SND, Object.entries(EVENTS).map(([e, [stems]]) => ['saiyan_' + e, {
	subtitle: 'subtitles.projecthero.saiyan_' + e,
	sounds: stems.map(s => ({ name: 'projecthero:saiyan/' + s })),
}]));
const n2 = appendKeys(LANG, Object.entries(EVENTS).map(([e, [, sub]]) => ['subtitles.projecthero.saiyan_' + e, sub]));

// startup hook
const MAIN = path.join(R, 'java', 'com', 'projecthero', 'mod', 'ProjectHeroMod.java');
let m = fs.readFileSync(MAIN, 'utf8');
if (!m.includes('SaiyanSounds.initialize()')) {
	const nl = m.includes('\r\n') ? '\r\n' : '\n';
	const a = '\t\tModItems.initialize();';
	if (!m.includes(a)) throw new Error('hook anchor');
	m = m.replace(a, a + nl + '\t\tcom.projecthero.mod.saiyan.SaiyanSounds.initialize(); // v0.17.3: the Saiyan power\'s synthesised sounds');
	fs.writeFileSync(MAIN, m);
}
console.log('sounds.json +' + n1 + ', en_us.json +' + n2 + ', hook ok');
