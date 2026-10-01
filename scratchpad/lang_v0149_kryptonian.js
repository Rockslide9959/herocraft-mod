// v0.14.9 Kryptonian: the Superman Suit -- set (or add) every en_us.json key it uses (items, tooltips, messages and the
// guide chapter's new section). Re-runnable (e.g. after a merge conflict in en_us.json): existing keys are replaced in
// place, new ones are added just before the closing brace. Keeps the file's line endings. Run from the repo root:
//   node scratchpad/lang_v0149_kryptonian.js
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';

const set = {
	'item.projecthero.superman_suit_helmet': 'Superman Suit Helmet',
	'item.projecthero.superman_suit_chestplate': 'Superman Suit Chestplate',
	'item.projecthero.superman_suit_leggings': 'Superman Suit Leggings',
	'item.projecthero.superman_suit_boots': 'Superman Suit Boots',
	'item.projecthero.superman_suit.tooltip': 'Only a Kryptonian can wear it',
	'item.projecthero.superman_suit_helmet.tooltip': 'Invisible: your own face shows',
	'item.projecthero.superman_suit_chestplate.tooltip': 'Comes with the red cape',
	'message.projecthero.superman_suit.refused': 'Only a Kryptonian can wear the Superman Suit',
	'message.projecthero.superman_suit.popped': 'The Superman Suit slips off -- only a Kryptonian can wear it',
	'projecthero.guide.kryptonian.suit': 'The Superman Suit',
	'projecthero.guide.kryptonian.suit.body': 'A four-piece armour set only a Kryptonian can wear -- anyone else cannot put it on, and if you lose the power while wearing it, it slips off into your inventory. Netherite-strong (3/6/8/3 armour, 3 toughness, a little knockback resistance) and fireproof, but it grants no powers. The chestplate brings a red cloth cape with the gold shield on the back: it sways as you walk and streams out behind you in flight, lying almost flat at super-speed. The helmet is invisible, so your own face shows. Crafting (no kryptonite!): Helmet -- blue wool, diamond, blue wool over two gold ingots. Chestplate -- two red wool, two diamonds and a block of gold, three blue wool. Leggings -- gold ingot, red wool, gold ingot over two blue wool and two diamonds. Boots -- two red wool over two diamonds.',
};

let added = 0;
let replaced = 0;
for (const [k, v] of Object.entries(set)) {
	const re = new RegExp('^(\\s*)"' + k.replace(/[.]/g, '\\.') + '": ".*?",?$', 'm');
	const m = s.match(re);
	if (m) {
		const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
		s = s.replace(re, m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma);
		replaced++;
		continue;
	}
	// new key: before the closing brace (the previous last line gains a comma)
	const end = s.lastIndexOf('}');
	let head = s.substring(0, end).replace(/\s+$/, '');
	if (!head.endsWith(',') && !head.endsWith('{')) {
		head += ',';
	}
	s = head + nl + '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + nl + '}' + nl;
	added++;
}
JSON.parse(s);
fs.writeFileSync(file, s);
console.log('lang ok: added', added, 'replaced', replaced);
