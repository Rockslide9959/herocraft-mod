// v0.14.16 speed + squad -- lang keys for en_us.json. Re-runnable (idempotent): existing keys are rewritten in place,
// new ones are appended before the closing brace; every other line (order, formatting, line endings) is untouched.
// Safe to re-run after a merge conflict in en_us.json: resolve it by taking the other side, then run this.
// Usage (from the repo root): node scratchpad/lang_v01416_speed_squad.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const S = 'projecthero.power.power_04_super_speed.ability.';

const SET = {
	// ---- Super Speed: Overdrive (walk 32 / sprint 100, V again ends it) and Speed Sweep (50 blocks, every enemy) ----
	[S + 'overdrive.desc']: 'V: for 30s — walk at roughly 32 blocks/second and SPRINT at roughly 100 blocks/second (a faster tier that replaces Speed Mode), +150% attack speed, a 10-block step assist, fall immunity, and your melee and every Super Speed move hit twice as hard. Your run gets even more extreme, your after-image trail turns red and leaves crackling yellow-white lightning streaking behind you, and speed explosions burst behind you while you run. Ordinary armour cannot take it: every second you run in Overdrive, each armour piece you wear loses 10 durability -- only the Flash Suit is built for this speed. Press V again to end it early (the cooldown still counts from when you switched it on). 42.5s cooldown.',
	[S + 'momentum_dash.desc']: 'X: an instant dash exactly where you are looking — look up to dash upward, down to dash downward. Further in Overdrive. In speed modes, jumps also keep their momentum for long leaps. 1.7s cooldown. Shift+X: Speed Sweep — blink from enemy to enemy through every hostile mob (and anything hunting you, and players when PvP is on — never your squad or pets) within 50 blocks, one every 2 ticks, hitting each for 15 (30 in Overdrive). It does not stop until every one of them has been hit (or has died or run out of range), then you snap back to exactly where you started, facing the same way. Nothing can hurt you while it runs. An enemy there is no room to land beside is given up on after 5s, a sweep never runs longer than 60s, and pressing X again calls it off early. 10s cooldown (shown on the X box).',
	'message.projecthero.speed.overdrive_ended': 'Overdrive ended',

	// ---- Flash Suit durability above the Super Speed HUD ----
	'hud.projecthero.flash_suit_durability': 'Suit %s%%',
	'projecthero.guide.flash_suit.body': 'A craftable four-piece suit only a speedster can wear. With all four pieces on, every speed you have is 50% faster, Speed Mode and Overdrive included. It is the only armour that can take Overdrive -- any other armour loses 10 durability every second you run in Overdrive. Your after-images wear whatever armour you have on. Diamond-strong (3/8/6/3 armour, 2 toughness), no other powers. Press H while wearing it: the suit is pulled back into a gold Flash Ring on your right hand. Press H again: the ring snaps open, the compressed suit shoots out and swells, and you whirl into it in a storm of lightning as it spreads over you from the ring. While it holds the suit, the ring is worn in your CHESTPLATE slot (the slot the Flash chestplate leaves free), so it is treated exactly like armour: kept with keepInventory, and on death it drops with the rest of your gear and never despawns -- the suit is never lost. Right-click it (or put it in the chest slot) to wear it again. The ring shows on the knuckles of your right hand (body side, and in first person) only while the suit is packed inside it, and while it is in there the suit slowly mends itself (1 durability per piece every second) -- the line just above your Super Speed HUD shows how far along it is ("Suit 87%": the durability left across every packed piece; yellow at half, red at a quarter). Crafting (R red wool, D diamond, G gold ingot, S sugar, L lightning rod): Cowl RDR / G G, Chestplate R R / DLD / RSR, Leggings RDR / S S / R R, Boots D D / G G.',

	// ---- Squads: friendly fire ----
	'commands.projecthero.squad.friendly_fire.on': '%s turned friendly fire ON: squadmates can now hurt each other.',
	'commands.projecthero.squad.friendly_fire.off': '%s turned friendly fire OFF: squadmates can no longer hurt each other.',
	'commands.projecthero.squad.friendly_fire.already_on': 'Friendly fire is already on.',
	'commands.projecthero.squad.friendly_fire.already_off': 'Friendly fire is already off.',
	'commands.projecthero.squad.friendly_fire.status': 'Friendly fire: %s',
	'screen.projecthero.squad.friendly_fire_button': 'Friendly Fire: %s',
	'screen.projecthero.squad.friendly_fire.on': 'FRIENDLY FIRE ON',
	'screen.projecthero.squad.friendly_fire.off': 'FRIENDLY FIRE OFF',
	'screen.projecthero.squad.help.friendlyfire': 'Leader: let squadmates hurt each other, or not',
	'screen.projecthero.squad.help.intro': "Squadmates can't hurt each other (unless the leader turns friendly fire on), and show up on this screen and on your Locator Bar. A squad holds up to %s players and survives a restart.",
	'projecthero.guide.squads.body': 'A squad is a group of players who cannot hurt each other. Nothing one squadmate does lands on another — not a sword, not an arrow, not a grenade, not a repulsor beam, not a 55-damage Psychic Detonation. That is the whole point: these powers are built to level a hillside, and without a squad you cannot use half of them anywhere near a friend. The one exception is a Hulk on a rampage: he stops being anyone\'s squad-mate until it ends, so he can hurt his squad and they can hurt him back (PvP permitting). The leader can also switch FRIENDLY FIRE on (/squad friendlyfire on, or the button on the squad screen): then squadmates\' hits land on each other like anyone else\'s -- swords, arrows, and any power that strikes them -- for sparring or a free-for-all. It only changes damage: area powers still aim past squadmates, and heals, shields, carries and buffs still treat them as friends. Everyone in the squad is told when it changes, the squad screen shows it, and it is saved with the squad. /squad friendlyfire off turns it back off; it is off for every new squad.',
	'projecthero.guide.squads.commands.body': '/squad create <name> starts one and makes you its leader. /squad invite <player> offers a place; the offer stands for a minute and the invited player gets a clickable ACCEPT, or can type /squad accept. /squad leave walks away, /squad kick and /squad rename and /squad disband are the leader’s, and so is /squad friendlyfire on|off (plain /squad friendlyfire says how it is set). Plain /squad lists who is in yours. A squad holds up to 12, and survives a restart.',
	'projecthero.guide.squads.menu.body': "Press P at any time (P again closes it) for the squad panel, tinted in your squad's colour: the squad name, a crown if you lead it, how many are online, whether friendly fire is on (a red FRIENDLY FIRE ON tag) and a 12-slot capacity bar. Every squadmate has a card with their face, a tag in their current hero's colour, a smooth health bar (gold for absorption, a pulsing red edge when they are low), their coordinates, and how far away they are with a compass arrow that turns as you look around -- or the dimension they are in. Offline members are greyed out at the bottom; long squads scroll with the mouse wheel or arrow keys. The Commands tab (also shown when you are not in a squad) lists every /squad command -- click one to type it into chat. A button also toggles the Locator Bar: a thin hairline across the top of your screen with every online squadmate's face on it, sliding toward the middle as you turn to face them and growing larger the closer they are. The leader gets one more button, Friendly Fire, to switch it on or off.",
};

let text = fs.readFileSync(FILE, 'utf8');
const crlf = text.includes('\r\n');
const lines = text.replace(/\r\n/g, '\n').split('\n');
const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
const render = (k, v) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`;

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
console.log('en_us.json: set ' + Object.keys(SET).length + ' keys (' + add.length + ' new)');
