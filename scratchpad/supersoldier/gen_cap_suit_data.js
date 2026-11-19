// v0.17.5 Captain America Battle Suit: item models, recipes, recipe advancements and the en_us keys (merged key by key).
//   node scratchpad/supersoldier/gen_cap_suit_data.js
'use strict';
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '..', '..');
const RES = path.join(ROOT, 'src', 'main', 'resources');
const w = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 2) + '\n'); console.log('wrote', path.relative(ROOT, p)); };

const B = { item: 'minecraft:blue_wool' }, L = { item: 'minecraft:leather' }, W = { item: 'minecraft:white_dye' }, R = { item: 'minecraft:red_dye' };
const I = { item: 'minecraft:iron_ingot' }, H = { item: 'projecthero:hero_alloy_ingot' };
const PIECES = {
	cap_battle_cowl: { pattern: ['BWB', 'L L'], key: { B, W, L }, unlock: 'minecraft:blue_wool' },
	cap_battle_suit_top: { pattern: ['B B', 'WHW', 'RLR'], key: { B, W, H, R, L }, unlock: 'projecthero:hero_alloy_ingot' },
	cap_battle_suit_trousers: { pattern: ['BLB', 'B B', 'B B'], key: { B, L }, unlock: 'minecraft:blue_wool' },
	cap_battle_suit_boots: { pattern: ['L L', 'I I'], key: { L, I }, unlock: 'minecraft:leather' },
};
for (const [id, p] of Object.entries(PIECES)) {
	w(path.join(RES, 'assets', 'projecthero', 'models', 'item', id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } });
	w(path.join(RES, 'data', 'projecthero', 'recipe', id + '.json'), {
		type: 'minecraft:crafting_shaped', category: 'equipment', pattern: p.pattern, key: p.key, result: { id: 'projecthero:' + id, count: 1 },
	});
	w(path.join(RES, 'data', 'projecthero', 'advancement', 'recipes', id + '.json'), {
		parent: 'minecraft:recipes/root',
		criteria: {
			has_item: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: [p.unlock] }] } },
			has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:' + id } },
		},
		requirements: [['has_item', 'has_the_recipe']],
		rewards: { recipes: ['projecthero:' + id] },
	});
}

// en_us: add / replace only these keys (never rewrite the rest of the file's text)
const LANG = path.join(RES, 'assets', 'projecthero', 'lang', 'en_us.json');
const KEYS = {
	'item.projecthero.cap_battle_cowl': 'Captain America Battle Cowl',
	'item.projecthero.cap_battle_suit_top': 'Captain America Battle Suit Top',
	'item.projecthero.cap_battle_suit_trousers': 'Captain America Battle Suit Trousers',
	'item.projecthero.cap_battle_suit_boots': 'Captain America Battle Suit Boots',
	'item.projecthero.cap_battle_suit.desc': 'Anyone can wear it.',
	'item.projecthero.cap_battle_suit.slot_hint': 'Super Soldier: N puts your shield in the slot on the back.',
	'item.projecthero.cap_battle_suit.slotted': 'In the slot: %s',
	'item.projecthero.cap_battle_suit.hint': 'The Captain America Battle Suit: cowl, top, trousers and boots. 19 armour in all, between iron and diamond. Anyone can wear it. A Super Soldier wearing the top gets a shield slot on his back.',
	'projecthero.guide.super_soldier.battle_suit': 'The Battle Suit and the Shield Slot',
	'projecthero.guide.super_soldier.battle_suit.body': 'The Captain America Battle Suit (cowl, top, trousers, boots; blue wool, leather, dyes, iron and a Hero Alloy Ingot for the top -- see the recipe book) gives 19 armour and anyone can wear it. A Super Soldier wearing the top gets a shield slot on his back. Press N with a Captain America shield in your hand to clip it into the slot; press N again with an empty hand to take it back (holding another shield swaps them). A slotted shield counts as held: C throws it straight from your back and it flies home into the slot, and holding right-click with an empty main hand raises it to block, exactly like a raised shield (no attacking while it is up). It hangs on your back and moves to your arm while you block. It stays in the slot through death and logging out, and comes out into your inventory when you take the top off.',
	'projecthero.super_soldier.ability.shield_slot': 'Shield Slot',
	'projecthero.super_soldier.ability.shield_slot.desc': 'N, wearing the Battle Suit top: put the shield in your hand into the slot on your back, or take it back out. A slotted shield counts as held: C throws it, right-click with an empty hand blocks with it.',
	'hud.projecthero.super_soldier.slot_full': 'Shield on back',
	'hud.projecthero.super_soldier.slot_empty': 'Slot empty',
	'hud.projecthero.super_soldier.slot_raised': 'Shield raised',
	'message.projecthero.super_soldier.slot_stored': '%s clipped to your back',
	'message.projecthero.super_soldier.slot_swapped': '%s clipped to your back (swapped)',
	'message.projecthero.super_soldier.slot_nothing': 'Hold a Captain America shield to put it in the slot',
	'message.projecthero.super_soldier.slot_in_flight': 'Your shield is on its way back',
	'message.projecthero.super_soldier.slot_hands_full': 'Free a hand to take the shield',
	'message.projecthero.super_soldier.no_shield': 'Hold a shield (or slot one in the Battle Suit) to throw it',
};
let text = fs.readFileSync(LANG, 'utf8');
const crlf = text.includes('\r\n');
const nl = crlf ? '\r\n' : '\n';
let added = 0, replaced = 0;
const esc = (s) => JSON.stringify(s);
for (const [k, v] of Object.entries(KEYS)) {
	const re = new RegExp('^(\\s*)' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*:\\s*"(?:[^"\\\\]|\\\\.)*"', 'm');
	if (re.test(text)) {
		text = text.replace(re, (m, ind) => ind + esc(k) + ': ' + esc(v));
		replaced++;
	} else {
		// before the closing brace: add a comma to the last entry
		const end = text.lastIndexOf('}');
		let body = text.slice(0, end).replace(/\s*$/, '');
		body += ',' + nl + '  ' + esc(k) + ': ' + esc(v) + nl;
		text = body + text.slice(end);
		added++;
	}
}
JSON.parse(text); // still valid
fs.writeFileSync(LANG, text);
console.log(`en_us: ${added} added, ${replaced} replaced`);
