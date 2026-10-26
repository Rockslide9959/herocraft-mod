// v0.17.1 Construct Menu v2: adds the new menu strings to en_us.json without re-serialising it (inserts before the
// final "}"; keeps LF). Re-runnable: keys that already exist are skipped.
'use strict';
const fs = require('fs');
const path = require('path');
const FILE = path.resolve(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
const KEYS = {
	'screen.projecthero.gl_menu.tab.attack': 'Attack',
	'screen.projecthero.gl_menu.tab.defence': 'Defence',
	'screen.projecthero.gl_menu.tab.mobility': 'Mobility',
	'screen.projecthero.gl_menu.tab.utility': 'Utility',
	'screen.projecthero.gl_menu.tab.survival': 'Survival',
	'screen.projecthero.gl_menu.hint_v2': 'Click a construct, then a key slot - or drag it onto one. Hover a construct and press its key to bind it.',
	'screen.projecthero.gl_menu.keys_title': 'Ring keys',
	'screen.projecthero.gl_menu.stat.use': 'Use',
	'screen.projecthero.gl_menu.stat.cost': 'Cost',
	'screen.projecthero.gl_menu.stat.upkeep': 'Upkeep',
	'screen.projecthero.gl_menu.stat.cooldown': 'Cooldown',
	'screen.projecthero.gl_menu.stat.keys': 'Keys',
	'screen.projecthero.gl_menu.value.charge': '%s Ring Charge',
	'screen.projecthero.gl_menu.value.per_sec': '%s a second',
	'screen.projecthero.gl_menu.value.seconds': '%s s',
	'screen.projecthero.gl_menu.value.none': 'None',
	'screen.projecthero.gl_menu.value.unbound': 'Not bound yet',
	'screen.projecthero.gl_menu.value.tap': 'Tap the key',
	'screen.projecthero.gl_menu.value.hold': 'Hold the key',
	'screen.projecthero.gl_menu.spawn': 'Spawn once',
	'screen.projecthero.gl_menu.clear': 'Clear keys',
	'screen.projecthero.gl_menu.empty_detail': 'Pick a construct to see what it does. Then click a key slot below to put it there, or drag it onto a key.',
	'screen.projecthero.gl_menu.empty_tab': 'Nothing in this group yet.',
	'screen.projecthero.gl_menu.key_empty': '%s: empty',
	'screen.projecthero.gl_menu.key_bound': '%s: %s',
	'screen.projecthero.gl_menu.key_click_bind': 'Click to put %s here',
	'screen.projecthero.gl_menu.key_click_select': 'Click to select it',
	'screen.projecthero.gl_menu.shift_group': 'Shift +',
	'screen.projecthero.gl_menu.c_tap_note': 'Plain C only takes tap constructs (holding C opens this menu).',
};
let s = fs.readFileSync(FILE, 'utf8');
if (s.includes('\r\n')) throw new Error('expected LF line endings');
const add = Object.entries(KEYS).filter(([k]) => !s.includes('"' + k + '"'));
if (add.length === 0) { console.log('nothing to add'); process.exit(0); }
const end = s.lastIndexOf('}');
let head = s.slice(0, end).replace(/\s*$/, '');
const lines = add.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
s = head + ',\n' + lines.join(',\n') + '\n' + s.slice(end);
fs.writeFileSync(FILE, s);
JSON.parse(s);
console.log('added ' + add.length + ' keys');
