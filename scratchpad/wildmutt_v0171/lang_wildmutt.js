// v0.17.1 Wildmutt (replaces Wildvine on the dial): every lang line he needs -- his name, the guide line, the info page's role /
// passives / weaknesses, every move's name and description, his messages and HUD row -- as ONE contiguous block at the END of
// en_us.json (_comment_omnitrix_wildmutt_start .. _end). Re-running replaces the block; nothing else in the file is touched.
//   node scratchpad/wildmutt_v0171/lang_wildmutt.js
'use strict';
const fs = require('fs');
const path = require('path');
const LANG = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');

const L = {
	'projecthero.omnitrix.alien.wildmutt': 'Wildmutt',
	'projecthero.guide.omnitrix.alien.wildmutt': 'A Vulpimancer: a huge orange beast on all fours with no eyes -- he smells and hears through the gills on his neck, so every creature around him stands out (even invisible ones). Fast, savage in close, with quills on his back he can fire, and he climbs walls like an animal.',
	'projecthero.omnitrix.info.wildmutt.role': 'Feral tracker: pounces, claws, quills and a roar.',
	'projecthero.omnitrix.info.wildmutt.passive.1': 'Scent Sense: creatures within 24 blocks are outlined for you -- invisible ones too (Sniff widens it to 48).',
	'projecthero.omnitrix.info.wildmutt.passive.2': 'No eyes: Blindness and Darkness do nothing to him.',
	'projecthero.omnitrix.info.wildmutt.passive.3': 'Fast on all fours (x1.35), and he gallops while sprinting (+20% more).',
	'projecthero.omnitrix.info.wildmutt.passive.4': 'A low, wide body (0.98 x 1.5 blocks); steps up a full block and takes no fall damage from his own leaps or climbs.',
	'projecthero.omnitrix.info.wildmutt.weakness.1': 'Underwater his senses shrink to 6 blocks and his air runs out twice as fast.',
	'projecthero.omnitrix.info.wildmutt.weakness.2': 'An explosion close by deafens his senses for 3 s.',
	'message.projecthero.omnitrix.wildmutt.climb_on': 'Wall Climb on: run into a wall to scale it (Sneak lets go).',
	'message.projecthero.omnitrix.wildmutt.climb_off': 'Wall Climb off.',
	'message.projecthero.omnitrix.wildmutt.no_scent': 'No scent to follow nearby.',
	'message.projecthero.omnitrix.wildmutt.tracking': 'On the scent of %s.',
	'message.projecthero.omnitrix.wildmutt.deafened': 'The blast deafens your senses!',
	'hud.projecthero.omnitrix.wildmutt.scent': 'Scent',
	'hud.projecthero.omnitrix.wildmutt.deaf': 'deaf',
};
const MOVES = {
	pounce: ['Pounce', 'Lunge onto the creature you look at (12 blocks), or a bound ahead: the bite does 9, pins it for a moment and heals you 1.'],
	maul: ['Maul', 'Rear up on your hind legs and rake three times (3.5 each), then bring both claws down (6, knockback) on everything in front.'],
	quill_volley: ['Quill Volley', 'Shake your back and fire 7 quills in a fan ahead (3.5 damage each).'],
	savage_roar: ['Savage Roar', 'A roar in a 9-block cone: 3 damage, staggers, slows and weakens everything in front of you for 3.5 s.'],
	leap: ['Leap', 'A huge bound where you look (about 8 blocks); the landing kicks up a shock of dust. No fall damage.'],
	coiled_leap: ['Coiled Leap', 'Hold X to coil low, release to spring -- up to 1.8 times as far after 1.5 s.'],
	wall_climb: ['Wall Climb', 'Toggle: run into a wall to climb it like a beast (no fall damage while it is on). Sneak lets go.'],
	ground_slam: ['Ground Slam', 'Rear up and slam both fists down ahead: 8 damage near you, 5 at the edge (4.5 blocks), launching them up.'],
	quill_roll: ['Quill Roll', 'Curl into a spiked ball and tumble forward for 1.5 s: 5 damage to each creature you roll through, and you take 40% less.'],
	feral_frenzy: ['Feral Frenzy', '8 s of rage: +25% speed, +4 claw damage, faster swipes, and every claw hit heals you 1.5.'],
	scent_track: ['Scent Track', 'Lock onto the creature you look at (or the nearest one, players first, 48 blocks) for 30 s: it shows red through walls and a trail of scent leads to it.'],
	claw_swipe: ['Claw Swipe', 'Your punch is a claw rake (+4) that also cleaves the creatures beside the one you hit (half damage).'],
	sniff: ['Sniff', 'Empty hand: sniff the air -- for 6 s your senses reach 48 blocks and dropped items show up too.'],
};
for (const [id, [name, desc]] of Object.entries(MOVES)) {
	L['projecthero.omnitrix.wildmutt.' + id] = name;
	L['projecthero.omnitrix.wildmutt.' + id + '.desc'] = desc;
}

let raw = fs.readFileSync(LANG, 'utf8');
const EOL = raw.includes('\r\n') ? '\r\n' : '\n';
let s = raw.replace(/\r\n/g, '\n');
const START = '  "_comment_omnitrix_wildmutt_start": "v0.17.1 Omnitrix: Wildmutt",\n';
const END_KEY = '"_comment_omnitrix_wildmutt_end": ""';
for (let i = s.indexOf(START); i >= 0; i = s.indexOf(START)) {
	const j = s.indexOf(END_KEY, i);
	const lineEnd = s.indexOf('\n', j);
	s = s.slice(0, i) + s.slice(lineEnd + 1);
	s = s.replace(/,(\s*)\}\s*$/, '$1}\n');
}
let block = START;
for (const [k, v] of Object.entries(L)) block += '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n';
block += '  ' + END_KEY + '\n';
s = s.replace(/\s*\}\s*$/, ',\n' + block + '}\n');
JSON.parse(s);
fs.writeFileSync(LANG, s.replace(/\n/g, EOL));
console.log(`wildmutt lang: ${Object.keys(L).length} lines`);
