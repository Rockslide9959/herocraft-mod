// v0.16.7 quirk suits: lang edits for en_us.json (CRLF kept, JSON.parse checked). Run from the worktree root.
'use strict';
const fs = require('fs');
const F = 'src/main/resources/assets/projecthero/lang/en_us.json';
let text = fs.readFileSync(F, 'utf8');
const NL = text.includes('\r\n') ? '\r\n' : '\n';
const q = s => JSON.stringify(s);

function setValue(key, value) {
	const re = new RegExp('^(  ' + q(key).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': )(".*?")(,?)\\r?$', 'm');
	if (!re.test(text)) throw new Error('missing ' + key);
	text = text.replace(re, (m, a, b, c) => a + q(value) + c);
}
function removeKey(key) {
	const re = new RegExp('^  ' + q(key).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': ".*?",\\r?\\n', 'm');
	if (!re.test(text)) throw new Error('missing ' + key);
	text = text.replace(re, '');
}
function insertAfter(anchor, entries) {
	const lines = text.split(NL);
	const i = lines.findIndex(l => l.startsWith('  ' + q(anchor) + ':'));
	if (i < 0) throw new Error('no anchor ' + anchor);
	for (const [k] of entries) if (text.includes('  ' + q(k) + ':')) throw new Error('duplicate ' + k);
	lines.splice(i + 1, 0, ...entries.map(([k, v]) => '  ' + q(k) + ': ' + q(v) + ','));
	text = lines.join(NL);
}

// overview: the costume section becomes the suit
removeKey('projecthero.guide.quirks.costume');
removeKey('projecthero.guide.quirks.costume.body');
insertAfter('projecthero.guide.quirks.serums.body', [
	['projecthero.guide.quirks.suit', 'The hero suit'],
	['projecthero.guide.quirks.suit.body', 'Getting a Quirk never changes your look. Each hero has a suit you craft and wear like armour (iron-level): helmet, chestplate, leggings and boots, in the vanilla armour shapes from iron and two themed items. Anyone can wear one.'],
	['projecthero.guide.quirks.suit.short', 'Craft it and wear it like armour. Anyone can wear it; a Quirk alone never changes your look.'],
	['projecthero.guide.quirks.suit.recipe', 'Recipe: %s + %s + %s, in the armour shapes'],
]);
insertAfter('projecthero.guide.quirk.dark_shadow.passive.body', [
	['projecthero.guide.quirk.dark_shadow.bird', 'Your head becomes a bird\'s while you hold Dark Shadow, suit or not.'],
]);
setValue('screen.projecthero.power_info.quirk.costume', 'Your hero suit is craftable: wear it like armour.');

const SUITS = [['endeavor', 'Endeavor'], ['overhaul', 'Overhaul'], ['ingenium', 'Ingenium'], ['tsukuyomi', 'Tsukuyomi'],
	['eraser_head', 'Eraser Head'], ['ground_zero', 'Ground Zero'], ['uravity', 'Uravity']];
const PIECES = [['helmet', 'Helmet'], ['chestplate', 'Chestplate'], ['leggings', 'Leggings'], ['boots', 'Boots']];
const items = [];
for (const [id, name] of SUITS) {
	for (const [p, pn] of PIECES) {
		items.push(['item.projecthero.' + id + '_suit_' + p, name + (id === 'ingenium' ? ' Armour ' : ' Costume ') + pn]);
	}
}
items.push(['item.projecthero.quirk_suit.tooltip', 'Hero suit. Anyone can wear it.']);
items.push(['message.projecthero.quirk.costume_is_suit', 'The hero costume is a craftable suit now: wear its pieces like armour.']);
insertAfter('item.projecthero.quirk_serum_random.hint', items);

JSON.parse(text);
fs.writeFileSync(F, text);
console.log('ok');
