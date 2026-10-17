// v0.16.6 Ladybug lang block: inserted as ONE contiguous block right after the Ghost Rider title line (idempotent:
// an existing block between the markers is replaced). node lang_v0166_ladybug.js <en_us.json>
'use strict';
const fs = require('fs');
const file = process.argv[2];
const ANCHOR = '  "message.projecthero.ghost_rider.title": "GHOST RIDER",\n';
const L = {
	'projecthero.miraculous.ability.ladybug_yoyo_strike': 'Yo-yo Strike',
	'projecthero.miraculous.ability.ladybug_yoyo_strike.desc': 'Whip the Yo-yo out 12 blocks along your look: the first creature in the way takes 10 damage and it snaps back. 0.8 s cooldown.',
	'projecthero.miraculous.ability.ladybug_ricochet': 'Ricochet',
	'projecthero.miraculous.ability.ladybug_ricochet.desc': 'The Yo-yo bounces between up to 4 threats, each within 8 blocks of the last, for 7 damage each, then returns. 3 s cooldown.',
	'projecthero.miraculous.ability.ladybug_yoyo_bind': 'Yo-yo Bind',
	'projecthero.miraculous.ability.ladybug_yoyo_bind.desc': 'The string wraps a creature or player up to 16 blocks away and roots it for 3 s (bosses 1 s). Press G again within the 3 s to yank it to you. 6 s cooldown.',
	'projecthero.miraculous.ability.ladybug_lasso_fling': 'Lasso Fling',
	'projecthero.miraculous.ability.ladybug_lasso_fling.desc': 'Throw the bound target (or bind and throw one at once) the way you look; it takes 8 damage when it lands or hits a wall, plus any fall damage. Not bosses. 7 s cooldown.',
	'projecthero.miraculous.ability.ladybug_yoyo_swing': 'Yo-yo Swing',
	'projecthero.miraculous.ability.ladybug_yoyo_swing.desc': 'Hold to hook a block up to 30 blocks away and swing on the string like Spider-Man; Jump reels in, Sneak lets out. Let go to fling yourself off with the arc\'s speed.',
	'projecthero.miraculous.ability.ladybug_zip_line': 'Zip Line',
	'projecthero.miraculous.ability.ladybug_zip_line.desc': 'Hook a block up to 30 blocks away and reel straight to it fast, no swing. 2 s cooldown.',
	'projecthero.miraculous.ability.ladybug_spinning_shield': 'Spinning Shield',
	'projecthero.miraculous.ability.ladybug_spinning_shield.desc': 'Hold: the Yo-yo spins in front of you as a red disc. Arrows shatter on it, other shots bounce back, and melee from the front does a quarter damage and throws the attacker back. Up to 6 s; 2 s cooldown.',
	'projecthero.miraculous.ability.ladybug_lucky_charm': 'LUCKY CHARM',
	'projecthero.miraculous.ability.ladybug_lucky_charm.desc': 'A spotted object drops from the sky that suits the moment: an apple when hurt, TNT when surrounded, a parachute when falling, otherwise a sword or bow (+50%). 10 s of Ladybug Vision; starts the 5-minute countdown. Again while holding it: Miraculous Ladybug -- broken blocks within 64 come back and you and your squad heal to full.',
	'projecthero.miraculous.ability.ladybug_pole_vault': 'Pole Vault',
	'projecthero.miraculous.ability.ladybug_pole_vault.desc': 'Plant the string and vault about 10 blocks up and 10 forward. 3 s cooldown.',
	'projecthero.miraculous.ability.ladybug_trip_wire': 'Trip Wire',
	'projecthero.miraculous.ability.ladybug_trip_wire.desc': 'String a wire from where you stand to the block you look at (up to 10 blocks) for 20 s; anything hostile that crosses it is rooted for 2 s. 10 s cooldown.',
	'projecthero.miraculous.ability.ladybug_yoyo_scan': 'Yo-yo Scan',
	'projecthero.miraculous.ability.ladybug_yoyo_scan.desc': 'Open the Yo-yo like a compact: every threat within 40 blocks glows red through walls for 10 s, for you alone. 15 s cooldown.',
	'projecthero.miraculous.ability.ladybug_yoyo_phone': 'Yo-yo Phone',
	'projecthero.miraculous.ability.ladybug_yoyo_phone.desc': 'Ping your position to your squadmates (a chat message and a compass marker for 30 s); they glow for you for 10 s. 10 s cooldown.',
	'item.projecthero.ladybug_lucky_apple': 'Lucky Charm: Spotted Apple',
	'item.projecthero.ladybug_lucky_tnt': 'Lucky Charm: Spotted TNT',
	'item.projecthero.ladybug_lucky_parachute': 'Lucky Charm: Spotted Parachute',
	'item.projecthero.ladybug_lucky_sword': 'Lucky Charm: Spotted Sword',
	'item.projecthero.ladybug_lucky_bow': 'Lucky Charm: Spotted Bow',
	'entity.projecthero.ladybug_lucky_tnt': 'Spotted TNT',
	'message.projecthero.ladybug.no_target': 'Nothing for the Yo-yo to reach',
	'message.projecthero.ladybug.no_hook': 'Nothing to hook the Yo-yo on',
	'message.projecthero.ladybug.too_heavy': 'Too heavy to move',
	'message.projecthero.ladybug.scan': 'Yo-yo Scan: %s threats marked',
	'message.projecthero.ladybug.no_squad': 'No squadmates to call',
	'message.projecthero.ladybug.phoned': 'Yo-yo Phone: pinged %s squadmates',
	'message.projecthero.ladybug.ping': '%s is calling on the Yo-yo from %s, %s, %s',
	'message.projecthero.ladybug.lucky_charm': 'LUCKY CHARM! %s',
	'message.projecthero.ladybug.hold_charm': 'Hold the Lucky Charm to cast Miraculous Ladybug',
	'message.projecthero.ladybug.charm_spent': 'The Lucky Charm is spent for this transformation',
	'message.projecthero.ladybug.miraculous': 'MIRACULOUS LADYBUG! %s blocks restored, %s healed',
	'hud.projecthero.ladybug.ping': '%s  %sm',
};
const raw = fs.readFileSync(file, 'utf8');
const crlf = raw.includes('\r\n');
let s = raw.replace(/\r\n/g, '\n');
const START = '  "projecthero.miraculous.ability.ladybug_yoyo_strike":';
const i0 = s.indexOf(START);
if (i0 >= 0) {
	const endKey = '  "hud.projecthero.ladybug.ping":';
	const i1 = s.indexOf('\n', s.indexOf(endKey)) + 1;
	s = s.slice(0, i0) + s.slice(i1);
}
const at = s.indexOf(ANCHOR);
if (at < 0) throw new Error('anchor not found');
for (const k of Object.keys(L)) if (s.includes('"' + k + '":')) throw new Error('duplicate key ' + k);
const block = Object.entries(L).map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n').join('');
s = s.slice(0, at + ANCHOR.length) + block + s.slice(at + ANCHOR.length);
JSON.parse(s);
fs.writeFileSync(file, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('inserted', Object.keys(L).length, 'keys');
