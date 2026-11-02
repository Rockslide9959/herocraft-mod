// v0.17.2: the Recalibrated Omnitrix's ONE contiguous lang block at the end of en_us.json (between _comment_recal_0172_start / _end):
// the framework's own keys below, then every alien's fragment from scratchpad/recal_v0172/lang/<id>.json (written by each kit's
// build), in dial order. Re-run after any fragment changes; it replaces the block in place and keeps the file's line endings.
// Usage: node scratchpad/recal_v0172/gen_recal_lang.js
'use strict';
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const FRAG = path.join(__dirname, 'lang');
const ORDER = ['swampfire', 'echo_echo', 'humungousaur', 'jetray', 'big_chill', 'chromastone', 'brainstorm', 'spidermonkey', 'goop', 'alien_x'];

const OWN = {
	'projecthero.omnitrix.alien.swampfire': 'Swampfire',
	'projecthero.omnitrix.alien.echo_echo': 'Echo Echo',
	'projecthero.omnitrix.alien.humungousaur': 'Humungousaur',
	'projecthero.omnitrix.alien.jetray': 'Jetray',
	'projecthero.omnitrix.alien.big_chill': 'Big Chill',
	'projecthero.omnitrix.alien.chromastone': 'Chromastone',
	'projecthero.omnitrix.alien.brainstorm': 'Brainstorm',
	'projecthero.omnitrix.alien.spidermonkey': 'Spidermonkey',
	'projecthero.omnitrix.alien.goop': 'Goop',
	'projecthero.omnitrix.alien.alien_x': 'Alien X',
	'item.projecthero.recalibrated_omnitrix': 'Recalibrated Omnitrix',
	'item.projecthero.recalibrated_omnitrix.hint': 'Recalibrated: its dial holds a whole new set of aliens.',
	'item.projecthero.recalibration_chip': 'Recalibration Chip',
	'item.projecthero.recalibration_chip.hint': 'Plumber tech. Stand in a fuelled Containment Pod wearing the Omnitrix (as a human) and use this on the pod to recalibrate it.',
	'message.projecthero.omnitrix_pod.recal_no_watch': 'Wear the Omnitrix to recalibrate it.',
	'message.projecthero.omnitrix_pod.recal_already': 'This Omnitrix is already recalibrated.',
	'message.projecthero.omnitrix_pod.recal_untrained': 'The Omnitrix has not learnt your DNA yet: %s / %s transformations.',
	'message.projecthero.omnitrix_pod.recal_start': 'Recalibration started. Stay inside the field.',
	'message.projecthero.omnitrix_pod.recal_failed_step_out': 'You stepped out -- the recalibration stopped. The chip is returned.',
	'message.projecthero.omnitrix_pod.recal_failed': 'The recalibration stopped. The chip is returned.',
	'message.projecthero.omnitrix_pod.recal_success': 'The Omnitrix has been recalibrated!',
	'message.projecthero.omnitrix.recal_title': 'Recalibrated!',
	'message.projecthero.omnitrix.recal_subtitle': 'The Omnitrix has a new set of aliens',
	'hud.projecthero.omnitrix.recalibrating': 'RECALIBRATING THE OMNITRIX',
	'hud.projecthero.omnitrix.recal_left': 'Stay inside the field: %s s',
	'projecthero.omnitrix.info.recalibrated': 'Recalibrated Omnitrix: the Alien Force dial.',
	'projecthero.omnitrix.info.recal_progress': 'Prototype: %s / %s transformations before it can be recalibrated.',
	'projecthero.omnitrix.info.recal_ready': 'Prototype: %s / %s transformations -- ready for a Recalibration Chip.',
	'projecthero.guide.omnitrix.recal': 'The Recalibrated Omnitrix',
	'projecthero.guide.omnitrix.recal.body': 'Recalibrated, the Omnitrix unlocks a whole new set of aliens: Swampfire, Echo Echo, Humungousaur, Jetray, Big Chill, Chromastone, Brainstorm, Spidermonkey, Goop and Alien X. The original ten are not on its dial. The watch changes too: a chunky grey bezel, a green-and-black face, a button on each side and a green band with black stripes.',
	'projecthero.guide.omnitrix.recal.how': 'How: transform 25 times with the prototype, so it learns your DNA. Then stand inside a Containment Pod with at least 4 fuel, as a human, and use a Recalibration Chip on the pod. It takes 30 seconds and does not hurt. Stay inside the field. Stepping out, transforming or dying stops it and hands the chip back.',
	'projecthero.guide.omnitrix.recal.recipe': 'Recalibration Chip: a Plumber Alloy in the middle, 2 redstone either side, 2 diamonds above and below, 4 emeralds in the corners. A recalibrated watch pulled off by the pod drops as a Recalibrated Omnitrix.',
	'subtitles.projecthero.omnitrix_recal_prime': 'Omnitrix primes',
	'subtitles.projecthero.omnitrix_recal_transform': 'Omnitrix transforms',
	'subtitles.projecthero.omnitrix_recal_detransform': 'Omnitrix powers down',
	'subtitles.projecthero.omnitrix_recal_dial_turn': 'Omnitrix dial turns',
	'subtitles.projecthero.omnitrix_recal_master_control': 'Omnitrix: Master Control unlocked',
};

const raw = fs.readFileSync(FILE, 'utf8');
const nl = raw.includes('\r\n') ? '\r\n' : '\n';
let text = raw.replace(/\r\n/g, '\n');
const START = '  "_comment_recal_0172_start"';
const END = '  "_comment_recal_0172_end": ""';
// drop the old block
const s0 = text.indexOf(START);
if (s0 >= 0) {
	const e0 = text.indexOf(END, s0);
	if (e0 < 0) throw new Error('block start without end');
	let cut = e0 + END.length;
	if (text[cut] === ',') cut++;
	if (text[cut] === '\n') cut++;
	text = text.slice(0, s0) + text.slice(cut);
	text = text.replace(/,\s*\n}\s*$/, '\n}\n');
}
const existing = JSON.parse(text);
const entries = Object.entries(OWN);
const used = new Set(Object.keys(OWN));
for (const id of ORDER) {
	const f = path.join(FRAG, id + '.json');
	if (!fs.existsSync(f)) continue;
	for (const [k, v] of Object.entries(JSON.parse(fs.readFileSync(f, 'utf8')))) {
		if (used.has(k)) continue;
		used.add(k);
		entries.push([k, v]);
	}
}
const clash = entries.filter(([k]) => k in existing).map(([k]) => k);
if (clash.length) throw new Error('keys already outside the block: ' + clash.join(', '));
const block = [`${START}: "v0.17.2 the Recalibrated Omnitrix + the Alien Force ten (scratchpad/recal_v0172/gen_recal_lang.js)"`,
	...entries.map(([k, v]) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)}`), END];
const close = text.lastIndexOf('}');
let head = text.slice(0, close).replace(/\s*$/, '');
text = head + ',\n' + block.join(',\n') + '\n}\n';
JSON.parse(text);
fs.writeFileSync(FILE, text.replace(/\n/g, nl));
console.log('recal lang block:', entries.length, 'keys');
