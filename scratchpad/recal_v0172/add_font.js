// v0.17.2: appends the Alien Force ten to font/omnitrix_dial.json (U+E00A..U+E013, OmnitrixInfo.DIAL_ORDER); idempotent
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/font/omnitrix_dial.json';
let s = fs.readFileSync(f, 'utf8');
const ids = ['swampfire', 'echo_echo', 'humungousaur', 'jetray', 'big_chill', 'chromastone', 'brainstorm', 'spidermonkey', 'goop', 'alien_x'];
if (!s.includes('swampfire')) {
	const BS = String.fromCharCode(92);
	const lines = ids.map((id, k) => `    { "type": "bitmap", "file": "projecthero:font/omnitrix_dial/${id}.png", "height": 9, "ascent": 8, "chars": ["${BS}u${(0xE00A + k).toString(16).toUpperCase()}"] }`);
	const nl = s.includes('\r\n') ? '\r\n' : '\n';
	const i = s.lastIndexOf('}', s.lastIndexOf(']'));
	s = s.slice(0, i + 1) + ',' + nl + lines.join(',' + nl) + s.slice(i + 1);
	JSON.parse(s);
	fs.writeFileSync(f, s);
}
console.log(JSON.parse(s).providers.length, 'providers');
