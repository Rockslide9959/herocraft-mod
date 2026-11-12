// v0.17.3 Spin: appends the 13 Spin sound events to sounds.json (text insert before the final brace, keeps the file's line
// endings and every other entry untouched) and writes their subtitle lang keys to scratchpad/spin/lang_art.json.
//   node scratchpad/spin/add_sounds_json.js
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '..', '..');
const FILE = path.join(ROOT, 'src/main/resources/assets/projecthero/sounds.json');

const SUBS = {
	spin_ball_throw: 'Steel Ball thrown',
	spin_ball_spin_loop: 'Steel Ball whirs',
	spin_ball_hit: 'Steel Ball strikes',
	spin_ball_catch: 'Steel Ball caught',
	spin_body_spin: 'Body twists with the Spin',
	spin_golden_charge_loop: 'Golden Spin charges',
	spin_golden_release: 'Golden Spin released',
	spin_ball_breaker: 'Ball Breaker strikes',
	spin_scan: 'Steel Ball hums',
	spin_quake: 'Spin Quake slams',
	spin_heal: 'Healing Spin',
	spin_leap: 'Spin Leap',
	spin_whistle: 'Spin whistles',
};
const LOOPS = new Set(['spin_ball_spin_loop', 'spin_golden_charge_loop']);

let text = fs.readFileSync(FILE, 'utf8');
const NL = text.includes('\r\n') ? '\r\n' : '\n';
const json = JSON.parse(text);
const add = Object.keys(SUBS).filter((id) => !(id in json));
if (add.length) {
	const blocks = add.map((id) => {
		const lines = [
			`  "${id}": {`,
			`    "subtitle": "subtitles.projecthero.${id}",`,
			`    "sounds": [`,
			`      {`,
			`        "name": "projecthero:spin/${id}"` + (LOOPS.has(id) ? ',' : ''),
			...(LOOPS.has(id) ? [`        "attenuation_distance": 24`] : []),
			`      }`,
			`    ]`,
			`  }`,
		];
		return lines.join(NL);
	});
	const end = text.lastIndexOf('}');
	let head = text.slice(0, end).replace(/\s+$/, '');
	text = head + ',' + NL + blocks.join(',' + NL) + NL + '}' + text.slice(end + 1);
	JSON.parse(text);
	fs.writeFileSync(FILE, text);
}
const lang = { 'item.projecthero.zeppeli_steel_balls': 'Zeppeli Steel Balls' };
for (const [id, s] of Object.entries(SUBS)) lang[`subtitles.projecthero.${id}`] = s;
fs.writeFileSync(path.join(__dirname, 'lang_art.json'), JSON.stringify(lang, null, 2) + '\n');
console.log('added', add.length, 'events; lang keys', Object.keys(lang).length);
