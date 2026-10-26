// v0.17.1 Flash Suit styles: new lang keys (inserted before the final "}") and the guide line (edited in place).
// Never re-serialises the file; keeps LF. Run from the repo root: node scratchpad/flash_suits_v0171/lang_flash_styles.js
'use strict';
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let s = fs.readFileSync(FILE, 'utf8');
if (s.includes('\r\n')) throw new Error('expected LF line endings');

const add = {
	'flash_suit_style.projecthero.original': 'The Flash',
	'flash_suit_style.projecthero.zoom': 'Zoom',
	'flash_suit_style.projecthero.reverse_flash': 'Reverse-Flash',
	'flash_suit_style.projecthero.classic': 'Classic Flash',
	'flash_suit_style.projecthero.scarlet': 'Scarlet Speedster',
	'item.projecthero.flash_suit.style': 'Style: %s',
	'item.projecthero.flash_ring.style_tip': 'Shift + right-click: change the suit\'s style',
	'message.projecthero.flash_suit.style': 'Flash Suit style: %s',
	'message.projecthero.flash_suit.style.no_suit': 'No Flash Suit to restyle -- wear it, or pack it into your ring',
	'screen.projecthero.speed_force_menu.suit': 'Suit: %s',
	'screen.projecthero.speed_force_menu.suit.none': 'Suit: no Flash Suit',
	'screen.projecthero.speed_force_menu.suit.tip': '%s -- restyles the Flash Suit you wear (or the one in your ring)',
};
const lines = [];
for (const [k, v] of Object.entries(add)) {
	if (s.includes('"' + k + '"')) continue;
	lines.push('  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
}
if (lines.length) {
	const end = s.lastIndexOf('}');
	let head = s.slice(0, end).replace(/\s*$/, '');
	s = head + ',\n' + lines.join(',\n') + '\n' + s.slice(end);
}

// the guide's Flash Suit chapter: one line about the styles, appended to its body in place
const GUIDE = '"projecthero.guide.flash_suit.body": "';
const STYLE_LINE = ' Styles: the suit comes in five looks -- The Flash, Zoom, Reverse-Flash, Classic Flash and Scarlet Speedster. Shift + right-click your Flash Ring to cycle them (the suit you wear, the one packed in the ring you wear, or the one in the ring in your hand), or, as the Speed Force Speedster, pick one on the Suit row of the Shift+N menu. The style stays on the suit pieces, through the ring and back.';
const gi = s.indexOf(GUIDE);
if (gi < 0) throw new Error('guide key not found');
if (!s.includes(STYLE_LINE.trim())) {
	// the end of that value: the first unescaped quote after its start
	let i = gi + GUIDE.length;
	for (; i < s.length; i++) {
		if (s[i] === '\\') { i++; continue; }
		if (s[i] === '"') break;
	}
	s = s.slice(0, i) + STYLE_LINE.replace(/"/g, '\\"') + s.slice(i);
}
JSON.parse(s); // still valid
fs.writeFileSync(FILE, s);
console.log('added', lines.length, 'keys');
