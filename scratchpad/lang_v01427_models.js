// v0.14.27 agent A lang: platform regen plate (reserve removed), Sneak+C send-back, guide text.
// Usage: node scratchpad/lang_v01427_models.js
const fs = require('fs');
const path = require('path');
require('./langset.js')([
	{
		anchor: 'screen.projecthero.suit_platform.empty_hint',
		entries: {
			'screen.projecthero.suit_platform.regen_short': 'REGEN',
			'screen.projecthero.suit_platform.regen_tip': 'Docked suits charge %s energy and repair %s integrity per second',
		},
	},
	{
		anchor: 'screen.projecthero.suit_call.hint',
		entries: {
			'screen.projecthero.suit_call.send_back': 'send home, %sm',
		},
	},
	{
		anchor: 'message.projecthero.ironman.armor_rejects',
		entries: {
			'message.projecthero.ironman.send_back_no_platform': 'No Suit Platform of yours can take the %s',
			'message.projecthero.ironman.send_back_docked': '%s sent home to the platform at %s, %s, %s',
			'message.projecthero.ironman.send_back_queued': '%s is flying home to the platform at %s, %s, %s',
		},
	},
]);

// the platform reserve is gone: drop its keys, and fix the guide line that mentioned it
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(FILE, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const drop = ['screen.projecthero.suit_platform.reserve_short', 'screen.projecthero.suit_platform.reserve_tip',
	'screen.projecthero.suit_platform.energy'];
let lines = text.split(/\r?\n/).filter(l => !drop.some(k => l.trimStart().startsWith(JSON.stringify(k) + ':')));
text = lines.join(eol).replace(
	'The Suit Platform previews the stored suit with full charge / integrity bars and the platform reserve.',
	'The Suit Platform previews the stored suit with full charge / integrity bars; every docked suit charges 10 energy and repairs 10 integrity a second. Sneak + C also lists armour carried in your pack -- pick it to send those pieces home to your platform.');
JSON.parse(text); // must stay valid
fs.writeFileSync(FILE, text);
console.log('lang v0.14.27 (agent A) written');
