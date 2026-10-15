// v0.16.4 (user): the charged V throw + World Breaker -- en_us.json keys. Re-reads the file, keeps CRLF, replaces a key's
// value in place when it exists, appends new keys before the closing brace, then checks the JSON parses with no duplicates.
// Run from the repo root: node scratchpad/lang_v0164_hulk_world_breaker.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

const KEYS = {
	'hud.projecthero.hulk.throw': 'THROW %s blocks',
	'hud.projecthero.hulk.world_breaker_ready': 'H: WORLD BREAKER',
	'hud.projecthero.hulk.world_breaker_active': 'WORLD BREAKER',
	'hud.projecthero.hulk.world_breaker_left': 'World Breaker %ss',
	'hud.projecthero.hulk.world_breaker_burnout': 'World Breaker: burned out',
	'message.projecthero.hulk.world_breaker': 'WORLD BREAKER!',
	'message.projecthero.hulk.world_breaker_running': 'World Breaker is running -- smash everything!',
	'message.projecthero.hulk.world_breaker_burnout': 'Burnout -- the Hulk is spent. Slow, weak, and no rage will come for a while.',
	'projecthero.hulk.ability.world_breaker': 'World Breaker',
	'projecthero.hulk.ability.world_breaker.desc': 'H at 100% rage (the HUD flashes "H: WORLD BREAKER"): for 30 seconds he grows to 10 blocks tall, glowing green veins burst out over his body, rage stays at 100, and everything he does is doubled -- damage (on top of his rage bonus), the size of every move, and the blocks and craters it smashes. Then Burnout: for a minute he shrinks back with no rage at all, slowed, weakened and tired (Slowness II, Weakness II, Mining Fatigue), and his rage can\'t build. World Breaker can\'t be used again for 5 minutes after it ends.',
	'projecthero.guide.hulk.world_breaker_v0164': 'World Breaker',
	'projecthero.guide.hulk.world_breaker_v0164.body': 'When his rage hits 100%, the HUD flashes "H: WORLD BREAKER". Press H (it won\'t change you back while it is up) and the Hulk lets everything out: a roar that shakes the ground and throws everything near him away, and he grows to 10 blocks tall -- as tall as there is room for -- with glowing green veins pulsing over his body. For 30 seconds his rage stays at 100 and everything is doubled: every hit does twice the damage on top of his rage bonus, every move covers twice the area, and it smashes twice the blocks and leaves craters twice the size. H does nothing while it runs, and the rage bar counts it down.\n\nIt costs him. When it ends he burns out for a minute: he shrinks back down with no rage at all, Slowness II, Weakness II and Mining Fatigue, and his rage can\'t build until the Burnout passes. World Breaker can\'t be used again for 5 minutes after it ends -- the HUD just says it is burned out. It can\'t be started mid-change or during a rampage. Dying, changing back or logging out ends it at once.',
	'projecthero.hulk.ability.grab.desc': 'V picks up the mob you are looking at (6 blocks) and holds it overhead. Then HOLD V to wind up the throw -- a bar over your ability boxes fills in under 2 seconds -- and let go to throw it along your look: a tap tosses it 20 blocks, a full charge sends it 200. It flies the whole way and smashes into whatever it hits (24 to it and everything round it). Squad-mates can be picked up too: carried safely overhead, set down with Shift+V (or they sneak to get down), or thrown -- they land without fall damage. Bosses and anything too big can\'t be picked up. 8 s cooldown from the throw.',
	'projecthero.hulk.ability.debris_throw.desc': 'Shift+V (with no mob in front of you): he rips a chunk of the ground he is standing on out -- grass, sand, stone, whatever it is -- and holds it overhead; hold V to wind up the throw and let go (20 blocks for a tap, 200 at full charge). It explodes where it lands (30 damage in 4 blocks, a small crater). Shares Grab\'s cooldown.',
	'message.projecthero.hulk.acquired_hint_v0164': 'Hold H to let the Hulk out. Dying also brings him out, every time. As the Hulk: tap H to change back (not in the first 3 minutes), N to roar -- and at 100% rage, H unleashes World Breaker.',
};

let raw = fs.readFileSync(FILE, 'utf8');
const crlf = raw.includes('\r\n');
let s = raw.replace(/\r\n/g, '\n');
const esc = (v) => JSON.stringify(v);
const added = [];
for (const [k, v] of Object.entries(KEYS)) {
	const re = new RegExp('^(\\s*)' + JSON.stringify(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':\\s*"(?:[^"\\\\]|\\\\.)*"(,?)$', 'm');
	const m = s.match(re);
	if (m) {
		s = s.replace(re, (all, ind, comma) => ind + esc(k) + ': ' + esc(v) + comma);
	} else {
		added.push(k);
	}
}
if (added.length) {
	const end = s.lastIndexOf('}');
	let head = s.slice(0, end).replace(/\s*$/, '');
	head += ',\n' + added.map((k) => '  ' + esc(k) + ': ' + esc(KEYS[k])).join(',\n') + '\n';
	s = head + s.slice(end);
}
// validate: parses, and no key appears twice
JSON.parse(s);
const seen = new Map();
for (const m of s.matchAll(/^\s*("(?:[^"\\]|\\.)*")\s*:/gm)) {
	const k = JSON.parse(m[1]);
	seen.set(k, (seen.get(k) || 0) + 1);
}
const dups = [...seen].filter(([, n]) => n > 1).map(([k]) => k);
if (dups.length) throw new Error('duplicate keys: ' + dups.join(', '));
for (const k of Object.keys(KEYS)) if (JSON.parse(s)[k] !== KEYS[k]) throw new Error('not set: ' + k);
if (crlf) s = s.replace(/\n/g, '\r\n');
fs.writeFileSync(FILE, s);
console.log('updated', Object.keys(KEYS).length - added.length, 'added', added.length, 'crlf', crlf);
