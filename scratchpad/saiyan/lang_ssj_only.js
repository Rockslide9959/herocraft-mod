// v0.17.3: Saiyan trimmed to Base + Super Saiyan -- removes the other forms' text and rewrites what mentions them.
// Edits en_us.json key by key (CRLF kept), refuses duplicate keys, validates the JSON. Run from the worktree root.
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(f, 'utf8');
const eol = raw.includes('\r\n') ? '\r\n' : '\n';
let lines = raw.replace(/\r\n/g, '\n').split('\n');
// duplicate-key guard on the input
const seen = new Set();
for (const l of lines) {
	const m = l.match(/^\s*"([^"]+)"\s*:/);
	if (m) {
		if (seen.has(m[1])) throw new Error('duplicate key already in file: ' + m[1]);
		seen.add(m[1]);
	}
}
const remove = [
	'gamerule.projectheroSaiyanAllForms',
	'projecthero.saiyan.form.ssj2', 'projecthero.saiyan.form.ssj2.short',
	'projecthero.saiyan.form.ssj3', 'projecthero.saiyan.form.ssj3.short',
	'projecthero.saiyan.form.god', 'projecthero.saiyan.form.god.short',
	'projecthero.saiyan.form.blue', 'projecthero.saiyan.form.blue.short',
	'projecthero.saiyan.form.blue_overdrive', 'projecthero.saiyan.form.blue_overdrive.short',
	'projecthero.saiyan.form.instinct', 'projecthero.saiyan.form.instinct.short',
	'hud.projecthero.saiyan.mastery',
	'message.projecthero.saiyan.overdrive_from_blue', 'message.projecthero.saiyan.instinct_health',
	'message.projecthero.saiyan.instinct_ki', 'message.projecthero.saiyan.unlocked', 'message.projecthero.saiyan.overdrive_drop',
	'screen.projecthero.saiyan.from_blue', 'screen.projecthero.saiyan.low_hp',
	'projecthero.guide.saiyan.mastery', 'projecthero.guide.saiyan.mastery.body',
	'projecthero.guide.saiyan.instinct', 'projecthero.guide.saiyan.instinct.body',
	'projecthero.guide.saiyan.overdrive', 'projecthero.guide.saiyan.overdrive.body',
];
const set = {
	'projecthero.saiyan.ability.dragon_rush.desc': 'A wind-up, then a rushing punch. Super Saiyan only.',
	'projecthero.saiyan.move.dragon_rush.desc': 'Super Saiyan only. Draw back, then rush forward with a punch that hits like a truck (26). 30 Ki, 15s cooldown.',
	'message.projecthero.saiyan.rush_ssj3': 'Dragon Rush needs Super Saiyan',
	'message.projecthero.saiyan.acquired_hint': 'Tap or hold V to charge Ki, hold H to go Super Saiyan, double-tap jump to fly, and Shift+V to power down.',
	'message.projecthero.saiyan.locked': "%s isn't available",
	'screen.projecthero.saiyan.locked': 'Not available',
	'projecthero.guide.saiyan.body': 'A warrior race with hidden power. You charge Ki and transform into a Super Saiyan.',
	'projecthero.guide.saiyan.ki.body': 'Your Ki bar holds 100. Tap V to charge it until full (tap again to stop), or hold V to charge while held (12 a second). You slow to a crawl while charging, and using any move stops the charge. Going Super Saiyan costs Ki, and Ki slowly comes back by itself in either form. At 0 Ki you drop to Base, exhausted: slowed and unable to transform for 5 seconds.',
	'projecthero.guide.saiyan.forms.body': 'Hold H and click a form. Base: +4 attack, +4 health, 10% less damage taken. Super Saiyan (15 Ki): +8 attack, +8 health, 25% less damage, faster, and your moves hit harder; your hair turns into a golden flame. Shift+V powers down.',
	'projecthero.guide.saiyan.commands.body': 'Operators: /projecthero power tune saiyan ki <0-100>, /projecthero power tune saiyan form <base|ssj>.',
};
const removed = new Set(), done = new Set();
lines = lines.filter((l) => {
	const m = l.match(/^\s*"([^"]+)"\s*:/);
	if (m && remove.includes(m[1])) {
		removed.add(m[1]);
		return false;
	}
	return true;
});
lines = lines.map((l) => {
	const m = l.match(/^(\s*)"([^"]+)"\s*:\s*"(?:[^"\\]|\\.)*"(,?)\s*$/);
	if (m && set[m[2]] !== undefined) {
		done.add(m[2]);
		return `${m[1]}"${m[2]}": ${JSON.stringify(set[m[2]])}${m[3]}`;
	}
	return l;
});
for (const k of remove) if (!removed.has(k)) throw new Error('not found to remove: ' + k);
for (const k of Object.keys(set)) if (!done.has(k)) throw new Error('not found to set: ' + k);
// the last entry must not keep a trailing comma
let out = lines.join('\n');
out = out.replace(/,(\s*\n\})/, '$1');
JSON.parse(out);
fs.writeFileSync(f, out.replace(/\n/g, eol));
console.log('removed', removed.size, 'set', done.size);
