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

// Step assist only in the modes; Mach Punch a flat 20.
{
	const j2 = JSON.parse(fs.readFileSync(f, 'utf8'));
	const fix2 = (key, a, b) => { if (!j2[key].includes(a) && !j2[key].includes(b)) throw new Error(key + ': ' + a); j2[key] = j2[key].split(a).join(b); };
	fix2('projecthero.power.power_04_super_speed.passive.speed', 'walking, sprinting and swimming, and a 3-block step assist', 'walking, sprinting and swimming');
	if (!j2['projecthero.power.power_04_super_speed.ability.speed_mode.desc'].includes('3-block step assist')) fix2('projecthero.power.power_04_super_speed.ability.speed_mode.desc', '+50% attack speed,', '+50% attack speed, a 3-block step assist,');
	fix2('projecthero.power.power_04_super_speed.ability.rapid_assault.desc', '20 damage standing still, rising with your running speed to 30 at full Overdrive speed,', '20 damage,');
	fs.writeFileSync(f, JSON.stringify(j2, null, 2) + '\n');
}
