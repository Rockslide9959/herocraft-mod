// v0.15.15 Mark 8 + Sentry Mode lang (builder area `mark8`): run with `node scratchpad/lang_v01515_mark8.js` from the
// repo root. Re-runnable: an existing key is rewritten in place (every occurrence), new keys go after their anchor.
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

const SET = [
	{ anchor: 'message.projecthero.ironman.sentry.cant_enter', entries: {
		'message.projecthero.ironman.sentry.drained': 'Sentry suit out of energy -- back to Regular, powered down',
		'message.projecthero.ironman.sentry.auto_defend': 'Sentry: you are under attack -- Defensive mode',
		'message.projecthero.ironman.sentry.combat_over': 'Sentry: fight over -- back to %s',
		'message.projecthero.ironman.mark8.call_low_energy': '%s needs at least %s%% energy to be called',
		'message.projecthero.ironman.mark8.inbound': '%s inbound (-%s%% energy)',
		'message.projecthero.ironman.mark8.landed': 'Mark 8 standing by -- Sentry Mode: Regular',
		'message.projecthero.ironman.mark8.sent_home': '%s stepping off -- flying home to %s, %s, %s',
		'hud.projecthero.ironman.sentry_auto': 'Sentry: %s (auto)',
		'hud.projecthero.ironman.sentry_drain': '%s  -%s%%/s',
	} },
	{ anchor: null, entries: {
		'projecthero.guide.iron_man.sentry.body': 'On the Mark 8, C is Sentry Mode (Sneak + C opens the call picker). Press it in the full suit and only the back of the armour opens, its back panels swinging out like doors; you walk backwards out of it, it closes and stands there with its own energy and integrity. Right-click it to open the back, right-click again to close it. While it is open, Sneak + right-click it to step in: you walk round behind it, turn to face its way and walk into the open back with your arms a little out, like the suit\'s, and it closes around you (you can\'t be hurt meanwhile). Sneak + right-click the closed suit to cycle its mode: Regular (stands still, looking around), Defensive and Follow. Follow walks after you and lights up the area around it; it takes off and flies to catch up when you are far ahead, well above or below it or across a gap, lands near you and walks again (more than 48 blocks away it teleports to you). Defensive does the same and fights for you with repulsor blasts (a punch if the enemy is right next to it): first whoever hurt you most recently, then the enemy closest to you -- never you or your squad. Follow costs 1% of the suit\'s energy a second and Defensive 2% (shown on your HUD); every shot costs energy too, and at zero it drops to Regular and stands powered down. Whatever mode it is in, if a mob or player hurts you within 64 blocks of it, it switches to Defensive by itself, and once 5 seconds pass with no hits on you and no enemy within 16 blocks it goes back to its old mode. If you drop to 4 hearts or less in Defensive mode it flies to you, lands just in front of you facing your way, open, and you walk into it. Hits on the standing suit take its integrity 1:1 and it never repairs itself; at zero it powers down where it stands but can still be stepped into. Calling the Mark 8 with the Stark Glasses costs 10% of its energy (it can\'t be called below 10%): the whole suit flies in from its platform and lands 2 blocks in front of you, facing you, in Regular mode -- step in when you are ready. Sneak + C on the worn suit, Send Home: it opens, you step out, it closes and flies itself back to its platform. It remembers its mode for next time.',
	} },
];

const PATCH = [
	['projecthero.guide.iron_man.mark_8.body',
		'It is called with the Stark Glasses: the pieces fly in from its Suit Platform one by one, like any ordinary call (no pod).',
		'It is called with the Stark Glasses, for 10% of its energy: the whole suit flies in from its Suit Platform and lands 2 blocks in front of you as a Sentry (see Sentry Mode).'],
];

const REMOVE = [];

let text = fs.readFileSync(FILE, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let lines = text.split(/\r?\n/);
const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
const keyOf = (l) => { const m = l.match(keyRe); return m ? JSON.parse('"' + m[1] + '"') : null; };
const render = (k, v) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`;

for (const k of REMOVE) {
	lines = lines.filter(l => keyOf(l) !== k);
}
for (const [k, from, to] of PATCH) {
	for (let i = 0; i < lines.length; i++) {
		if (keyOf(lines[i]) !== k) continue;
		const m = lines[i].match(/^(\s*"(?:[^"\\]|\\.)*"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$/);
		const v = JSON.parse(m[2]);
		if (v.includes(from)) lines[i] = m[1] + JSON.stringify(v.replace(from, to)) + m[3];
		else if (!v.includes(to)) throw new Error('patch source missing in ' + k);
	}
}
for (const g of SET) {
	let at = g.anchor ? lines.findIndex(l => keyOf(l) === g.anchor) : -1;
	for (const [k, v] of Object.entries(g.entries)) {
		let found = false;
		for (let i = 0; i < lines.length; i++) {
			if (keyOf(lines[i]) === k) {
				const keepComma = lines[i].trimEnd().endsWith(',');
				lines[i] = render(k, v).replace(/,$/, keepComma ? ',' : '');
				found = true;
			}
		}
		if (found) continue;
		if (at < 0) {
			let close = lines.length - 1;
			while (close > 0 && lines[close].trim() !== '}') close--;
			let prev = close - 1;
			while (prev > 0 && lines[prev].trim() === '') prev--;
			if (!lines[prev].trimEnd().endsWith(',') && lines[prev].trim() !== '{') lines[prev] = lines[prev].trimEnd() + ',';
			lines.splice(close, 0, render(k, v).replace(/,$/, ''));
			at = close;
			continue;
		}
		if (!lines[at].trimEnd().endsWith(',')) lines[at] = lines[at].trimEnd() + ',';
		const isLast = !lines[at + 1] || lines[at + 1].trim() === '}';
		lines.splice(at + 1, 0, isLast ? render(k, v).replace(/,$/, '') : render(k, v));
		at++;
	}
}
// the last entry before the closing brace must not carry a trailing comma
let close = lines.length - 1;
while (close > 0 && lines[close].trim() !== '}') close--;
let prev = close - 1;
while (prev > 0 && lines[prev].trim() === '') prev--;
lines[prev] = lines[prev].trimEnd().replace(/,$/, '');
const out = lines.join(eol);
const parsed = JSON.parse(out); // must stay valid JSON
for (const k of REMOVE) if (k in parsed) throw new Error('still present: ' + k);
for (const g of SET) for (const [k, v] of Object.entries(g.entries)) if (parsed[k] !== v) throw new Error('not set: ' + k);
fs.writeFileSync(FILE, out);
console.log('lang updated');
