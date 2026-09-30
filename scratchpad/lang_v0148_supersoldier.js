// v0.14.8 Super Soldier -- lang keys for en_us.json. Re-runnable (idempotent): existing keys are rewritten in place,
// new ones are appended before the closing brace; every other line (order, formatting, line endings) is untouched.
// Safe to re-run after a merge conflict in en_us.json: resolve the conflict by taking the other side, then run this.
// Usage (from the repo root): node scratchpad/lang_v0148_supersoldier.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const A = 'projecthero.super_soldier.ability.';
const G = 'projecthero.guide.super_soldier';

const SET = {
	// items / entity / death
	'item.projecthero.unrefined_super_soldier_serum': 'Unrefined Super Soldier Serum',
	'item.projecthero.unrefined_super_soldier_serum.desc1': '10% it makes you a Super Soldier. 90% it kills you.',
	'item.projecthero.unrefined_super_soldier_serum.desc2': 'Refine it in a Blast Furnace (10 min).',
	'item.projecthero.refined_super_soldier_serum': 'Refined Super Soldier Serum',
	'item.projecthero.refined_super_soldier_serum.desc1': 'Drink to become a Super Soldier.',
	'item.projecthero.refined_super_soldier_serum.desc2': 'Replaces your current Primary power.',
	'item.projecthero.soldier_shield': "Soldier's Shield",
	'entity.projecthero.soldier_shield': "Soldier's Shield",
	'death.attack.projecthero.serum_rejection': "%1$s's body rejected the unrefined serum",
	'death.attack.projecthero.serum_rejection.player': "%1$s's body rejected the unrefined serum",

	// messages
	'message.projecthero.super_soldier.acquired': 'The serum takes. You are a Super Soldier.',
	'message.projecthero.super_soldier.acquired_hint': 'R G Z X V (+Shift) are your moves. Shift+Z is your ultimate. Press P for details.',
	'message.projecthero.super_soldier.title': 'SUPER SOLDIER',
	'message.projecthero.super_soldier.already': 'Your body is already perfected.',
	'message.projecthero.super_soldier.rejected': 'Your body rejects the serum...',
	'message.projecthero.super_soldier.cooldown': '%s: %ss',
	'message.projecthero.super_soldier.no_target': 'No enemy in reach',
	'message.projecthero.super_soldier.onslaught': 'Super Soldier Onslaught!',
	'message.projecthero.super_soldier.onslaught_end': 'Onslaught ends',
	'message.projecthero.super_soldier.focus': 'Tactical Focus: %s marked',

	// abilities
	[A + 'combo_strike']: 'Combo Strike',
	[A + 'combo_strike.desc']: 'R: three fast punches on the enemy in front of you (5, 5, then a 7-damage finisher that knocks it back). Needs a target within 4 blocks. 5s cooldown.',
	[A + 'uppercut_launcher']: 'Uppercut Launcher',
	[A + 'uppercut_launcher.desc']: 'Shift+R: a rising uppercut for 10 damage that launches the target about 5 blocks into the air. 8s cooldown.',
	[A + 'shield_throw']: 'Shield Throw',
	[A + 'shield_throw.desc']: 'G: hurl your shield. It hits for 8, ricochets into the nearest other enemy it can see (up to 3 hits, glancing off walls too), then flies back to you through anything in the way. 7s cooldown.',
	[A + 'shield_bash']: 'Shield Bash Charge',
	[A + 'shield_bash.desc']: 'Shift+G: charge about 9 blocks forward behind your shield, hitting everything in your path for 7, throwing it aside and slowing it. 9s cooldown.',
	[A + 'leaping_slam']: 'Leaping Slam',
	[A + 'leaping_slam.desc']: 'Z: leap 4 blocks up and forward, then drive down into the ground: 12 damage to everything within 5 blocks (less at the edge), knocked back and up. No fall damage. 12s cooldown.',
	[A + 'onslaught']: 'Super Soldier Onslaught',
	[A + 'onslaught.desc']: 'Shift+Z, ULTIMATE: a shockwave (8 damage, 6 blocks), then 10s of +5 melee damage, Resistance I, Speed I and +25% damage on every move. Every punch shocks enemies within 3 blocks of the target for 4. 100s cooldown.',
	[A + 'tactical_roll']: 'Tactical Roll',
	[A + 'tactical_roll.desc']: 'X: a quick combat roll the way you are moving (or facing) -- about 5 blocks, and nothing can hurt you for half a second. 4s cooldown.',
	[A + 'high_leap']: 'High Leap',
	[A + 'high_leap.desc']: 'Shift+X: a huge leap -- 5 blocks up and far forward. No fall damage. 8s cooldown.',
	[A + 'battle_cry']: 'Battle Cry',
	[A + 'battle_cry.desc']: 'V: rally the troops. You and squadmates within 16 blocks get Strength I and Speed I for 10s; hostile mobs within 12 blocks get Weakness for 8s and Slowness for 4s. 30s cooldown.',
	[A + 'tactical_focus']: 'Tactical Focus',
	[A + 'tactical_focus.desc']: 'Shift+V: read the battlefield. Every enemy within 30 blocks glows for 10s, and for 8s every hit you land on a marked enemy is critical (x1.5). 25s cooldown.',

	// HUD (short: the Alt names column)
	'hud.projecthero.super_soldier.title': 'Super Soldier',
	'hud.projecthero.super_soldier.key.r': 'Combo / Uppercut',
	'hud.projecthero.super_soldier.key.g': 'Shield / Bash',
	'hud.projecthero.super_soldier.key.z': 'Slam / Onslaught',
	'hud.projecthero.super_soldier.key.x': 'Roll / High Leap',
	'hud.projecthero.super_soldier.key.v': 'Battle Cry / Focus',

	// squads
	'projecthero.squad.identity.super_soldier': 'Super Soldier',

	// guide
	[G]: 'Super Soldier',
	[G + '.tier']: 'Hero Tier -- a Primary power. Gaining it replaces the one you had.',
	[G + '.body']: 'Peak human, not a god: faster, tougher and stronger than any soldier, with a shield that always comes back. No transformation -- the power is always on.',
	[G + '.serum']: 'The Unrefined Serum',
	[G + '.serum.body']: 'Put one Potion of Strength, one of Swiftness and one of Leaping (long or strong versions work too) in a crafting table, any slots. Special recipe: it does not show in the recipe book. Drinking it is a gamble: 1 in 10 it takes, 9 in 10 your body rejects it and you die -- even in creative.',
	[G + '.refine']: 'Refining It',
	[G + '.refine.body']: 'Smelt the unrefined serum in a Blast Furnace for 10 minutes (keep it fuelled -- a lava bucket or a stack of coal). The Refined Serum always works.',
	[G + '.passives']: 'Always On',
	[G + '.passives.body']: '+50% movement speed, +5 hearts, +7 melee damage bare-handed, 30% less damage taken, a 2-block jump and a longer safe fall, 20% knockback resistance, +15% attack speed, healing out of combat, and immunity to Poison and Nausea.',
	[G + '.controls']: 'Moves',
	[G + '.commands']: 'Commands',
	[G + '.commands.body']: '/projecthero power grant super_soldier [player]  (op level 2). Every number lives in SuperSoldierConfig.',
};

const REMOVE = [];

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
