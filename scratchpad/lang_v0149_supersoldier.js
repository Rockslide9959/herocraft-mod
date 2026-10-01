// v0.14.9 Super Soldier -- lang keys for en_us.json. Re-runnable (idempotent): existing keys are rewritten in place,
// new ones are appended before the closing brace, REMOVE keys are deleted; every other line (order, formatting, line
// endings) is untouched. Safe to re-run after a merge conflict in en_us.json: resolve it by taking the other side, then
// run this. Usage (from the repo root): node scratchpad/lang_v0149_supersoldier.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const A = 'projecthero.super_soldier.ability.';
const G = 'projecthero.guide.super_soldier';

const SET = {
	// items
	'item.projecthero.adamantium_shield': 'Adamantium Shield',
	'item.projecthero.adamantium_shield.desc1': 'Blocks like a shield. Never breaks.',
	'item.projecthero.adamantium_shield.desc2': 'Super Soldier: C throws it.',
	'item.projecthero.captain_america_helmet': 'Captain America Helmet',
	'item.projecthero.captain_america_chestplate': 'Captain America Chestplate',
	'item.projecthero.captain_america_leggings': 'Captain America Leggings',
	'item.projecthero.captain_america_boots': 'Captain America Boots',
	'item.projecthero.captain_america_suit.desc': 'Only a Super Soldier can wear it.',

	// messages
	'message.projecthero.super_soldier.acquired_hint': 'R G Z X V (+Shift) are your moves; C throws a shield you hold. Shift+Z is your ultimate. Press P for details.',
	'message.projecthero.super_soldier.no_shield': 'Hold a shield to throw it',
	'message.projecthero.super_soldier.armor_locked': 'Only a Super Soldier can wear this suit',

	// abilities
	[A + 'flying_kick']: 'Flying Kick',
	[A + 'flying_kick.desc']: 'G: launch yourself feet-first at the enemy in front of you (up to 8 blocks away) for 12 damage and a big knockback, then spring back off it. No fall damage. 8s cooldown.',
	[A + 'judo_takedown']: 'Judo Takedown',
	[A + 'judo_takedown.desc']: 'Shift+G: grab the enemy in front of you (3.5 blocks), heave it over your shoulder and slam it into the ground behind you: 14 damage and stunned for 1.5s. Bosses are too big to throw but still take the hit. 10s cooldown.',
	[A + 'shield_throw']: 'Shield Throw',
	[A + 'shield_throw.desc']: 'C: throw the shield in your hand. It bounces from enemy to enemy, then flies back to you. Adamantium Shield: 9 per hit, up to 4 enemies, 24 blocks, 5s cooldown. Any other shield: 6 per hit, up to 3 enemies, 16 blocks, 6s cooldown.',

	// HUD (short: the Alt names column)
	'hud.projecthero.super_soldier.key.g': 'Kick / Takedown',
	'hud.projecthero.super_soldier.key.c': 'Shield Throw',

	// guide
	[G + '.body']: 'Peak human, not a god: faster, tougher and stronger than any soldier, with a shield that always comes back. No transformation -- the power is always on.',
	[G + '.shield']: 'The Shield',
	[G + '.shield.body']: 'Craft the Adamantium Shield: an iron block in each corner, a netherite ingot in the centre, red dye above it, blue dye below it and white dye either side. It blocks like any shield and never breaks. Hold it in either hand and press C to throw it; it comes back to the hand it left (or your inventory). Any ordinary shield can be thrown too, just weaker. If you die while it is in the air, it drops where it is.',
	[G + '.suit']: 'The Suit',
	[G + '.suit.body']: 'A craftable four-piece Captain America suit (blue, red and white wool, iron and leather -- see the recipe book): 18 armour in all, between iron and diamond. Only a Super Soldier can wear it; anyone else finds it pops straight off.',
};

const REMOVE = [
	A + 'shield_bash',
	A + 'shield_bash.desc',
];

let text = fs.readFileSync(FILE, 'utf8');
const crlf = text.includes('\r\n');
const lines = text.replace(/\r\n/g, '\n').split('\n');
const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
const render = (k, v) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`;

for (let i = lines.length - 1; i >= 0; i--) {
	const m = lines[i].match(keyRe);
	if (m && REMOVE.includes(JSON.parse('"' + m[1] + '"'))) lines.splice(i, 1);
}
const done = new Set();
for (let i = 0; i < lines.length; i++) {
	const m = lines[i].match(keyRe);
	if (!m) continue;
	const k = JSON.parse('"' + m[1] + '"');
	if (Object.prototype.hasOwnProperty.call(SET, k)) {
		lines[i] = render(k, SET[k]);
		done.add(k);
	}
}
const close = lines.map((l) => l.trim()).lastIndexOf('}');
const add = Object.keys(SET).filter((k) => !done.has(k)).map((k) => render(k, SET[k]));
lines.splice(close, 0, ...add);
// every entry but the last ends in a comma
const closeNow = lines.map((l) => l.trim()).lastIndexOf('}');
let lastEntry = -1;
for (let i = closeNow - 1; i >= 0; i--) {
	if (lines[i].trim() === '') continue;
	lastEntry = i;
	break;
}
for (let i = 1; i < lastEntry; i++) {
	if (keyRe.test(lines[i]) && !lines[i].trimEnd().endsWith(',')) lines[i] = lines[i].trimEnd() + ',';
}
lines[lastEntry] = lines[lastEntry].replace(/,\s*$/, '');

let out = lines.join('\n');
JSON.parse(out); // validate
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(FILE, out);
console.log('en_us.json: set ' + Object.keys(SET).length + ' keys (' + add.length + ' new), removed up to ' + REMOVE.length);
