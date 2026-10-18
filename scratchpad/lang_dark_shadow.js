// v0.16.7 Dark Shadow lang block, inserted right after the anchor key (CRLF kept). node scratchpad/lang_dark_shadow.js
const fs = require('fs');
const path = require('path');
const f = path.resolve(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
let s = fs.readFileSync(f, 'utf8');
const P = 'projecthero.quirk.ability.quirk_dark_shadow_';
const e = {
	[P + 'strike']: 'Shadow Strike',
	[P + 'strike.desc']: 'Dark Shadow lunges up to 8 blocks and hits for 9 x Shadow strength.',
	[P + 'flurry']: 'Shadow Flurry',
	[P + 'flurry.desc']: 'A rapid string of 6 strikes on the target, 3.5 x strength each.',
	[P + 'guard']: 'Shadow Guard',
	[P + 'guard.desc']: 'Hold: Dark Shadow wraps you, soaking 85% of hits from the front and every frontal projectile. Up to 6 s.',
	[P + 'counter']: 'Shadow Counter',
	[P + 'counter.desc']: 'A 0.75 s parry: the next hit is blocked and answered with a 10 x strength strike.',
	[P + 'fallen_angel']: 'Black Fallen Angel',
	[P + 'fallen_angel.desc']: 'Toggle: shadow wings. Fly and glide, no fall damage.',
	[P + 'dive']: 'Shadow Dive',
	[P + 'dive.desc']: 'A fast dash the way you look, striking what you pass (6 x strength).',
	[P + 'grab']: 'Shadow Grab',
	[P + 'grab.desc']: 'Dark Shadow snatches a creature up to 12 blocks away and pulls it to you.',
	[P + 'grab_throw']: 'Grab and Throw',
	[P + 'grab_throw.desc']: 'Snatch a creature and hurl it the way you look (6 x strength).',
	[P + 'abyssal_rampage']: 'Abyssal Rampage',
	[P + 'abyssal_rampage.desc']: 'Darkness only: a 6 s frenzy hitting up to 3 creatures around you 4 times a second. Never you or your squad.',
	[P + 'abyssal_swallow']: 'Abyssal Swallow',
	[P + 'abyssal_swallow.desc']: 'Dark Shadow swallows a creature and holds it 5 s, chewing on it. Not bosses.',
	[P + 'black_ankh']: 'Black Ankh',
	[P + 'black_ankh.desc']: 'Toggle: Dark Shadow as clawed armour. +8 armour, +4 toughness, +4 x strength attack damage.',
	[P + 'calm']: 'Calm',
	[P + 'calm.desc']: 'A flash of light settles Dark Shadow: Shadow drops to 10, Berserk ends. Works while berserk.',
	'projecthero.quirk.dark_shadow.passive': 'Shadow rises in the dark, falls in the light. Strength x0.5 empty to x1.6 full. Full in the dark: Berserk for 8 s.',
	'message.projecthero.dark_shadow.no_target': 'Dark Shadow finds nothing to reach.',
	'message.projecthero.dark_shadow.too_bright': 'Too bright: Abyssal Rampage needs darkness.',
	'message.projecthero.dark_shadow.cant_swallow': 'Dark Shadow can\'t swallow that.',
	'message.projecthero.dark_shadow.calm': 'Dark Shadow settles down.',
};
const obj = JSON.parse(s);
for (const k of Object.keys(e)) if (k in obj) throw new Error('duplicate key ' + k);
const anchor = '"message.projecthero.quirk.overloaded.dark_shadow"';
const i = s.indexOf(anchor);
if (i < 0) throw new Error('anchor not found');
const eol = s.indexOf('\n', i);
if (!/,\s*$/.test(s.slice(i, eol))) throw new Error('anchor line has no trailing comma');
const block = Object.entries(e).map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',').join('\r\n');
s = s.slice(0, eol + 1) + block + '\r\n' + s.slice(eol + 1);
JSON.parse(s);
fs.writeFileSync(f, s);
console.log('inserted', Object.keys(e).length);
