// v0.14.9: Super Speed numbers -- Rapid Assault 4x5, Mach Punch 20 standing / 8 s, Speed Sweep 15. Re-runnable.
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const j = JSON.parse(fs.readFileSync(f, 'utf8'));
const fix = (key, pairs) => {
	let v = j[key];
	if (v === undefined) throw new Error('missing ' + key);
	for (const [a, b] of pairs) v = v.split(a).join(b);
	j[key] = v;
};
fix('projecthero.power.power_04_super_speed.ability.rapid_assault.desc', [
	['4 punches of 8 damage each (32 in all;', '4 punches of 5 damage each (20 in all;'],
	['12 damage standing still, rising', '20 damage standing still, rising'],
	['and throws it back. 10s cooldown', 'and throws it back. 8s cooldown'],
]);
fix('projecthero.power.power_04_super_speed.ability.momentum_dash.desc', [
	['hitting each for 12 (24 in Overdrive)', 'hitting each for 15 (30 in Overdrive)'],
]);
fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
console.log('ok');
