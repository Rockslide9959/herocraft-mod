// v0.17.5 backpacks: adds the backpack lang keys to en_us.json key by key (never rewrites existing keys).
// Usage: node scratchpad/add_backpack_lang.js   (run from the worktree root)
'use strict';
const fs = require('fs');
const FILE = 'src/main/resources/assets/projecthero/lang/en_us.json';
const KEYS = {
	'item.projecthero.leather_backpack': 'Leather Backpack',
	'item.projecthero.iron_backpack': 'Iron Backpack',
	'item.projecthero.gold_backpack': 'Golden Backpack',
	'item.projecthero.diamond_backpack': 'Diamond Backpack',
	'item.projecthero.netherite_backpack': 'Netherite Backpack',
	'item.projecthero.backpack.slots': 'Storage: %s / %s slots used',
	'item.projecthero.backpack.entry': '%s x%s',
	'item.projecthero.backpack.more': '...and %s more',
	'item.projecthero.backpack.fireproof': 'Fireproof: floats in lava and never burns.',
	'item.projecthero.backpack.hint': 'Right-click to open, or press %s with it anywhere in your inventory. Carried, it is worn on your back.',
	'item.projecthero.leather_backpack.hint': 'Upgrade: craft it inside eight iron ingots. It keeps everything inside.',
	'item.projecthero.iron_backpack.hint': 'Upgrade: craft it inside eight gold ingots. It keeps everything inside.',
	'item.projecthero.gold_backpack.hint': 'Upgrade: craft it inside eight diamonds. It keeps everything inside.',
	'item.projecthero.diamond_backpack.hint': 'Upgrade: a Netherite Upgrade and a netherite ingot at a smithing table. It keeps everything inside.',
	'item.projecthero.netherite_backpack.hint': 'The top tier: six rows, like a double chest.',
	'container.projecthero.backpack.fill': '%s/%s',
	'key.projecthero.open_backpack': 'Open Backpack',
	'message.projecthero.backpack.none': "You aren't carrying a backpack.",
	'projecthero.jei.backpack': 'A backpack is a chest you carry: right-click it, or press Open Backpack (B) with it anywhere in your inventory. It keeps its contents when dropped, stored or upgraded. Backpacks and shulker boxes never go inside one. Dye it like leather armour; a cauldron washes the dye off.',
	'projecthero.guide.devices.backpacks': 'Backpacks',
	'projecthero.guide.devices.backpacks.body': 'A backpack is a chest you carry. Right-click with it to open it, or press Open Backpack (B, rebindable under Controls > Inventory) to open the one on your back without holding it; press it again to close. Shift-click moves stacks in and out. Everything stays inside the item, so it keeps its contents when you drop it, put it in a chest or upgrade it. A backpack never goes inside another backpack, and shulker boxes never go inside a backpack. While the screen is open the backpack itself is locked in its slot.',
	'projecthero.guide.devices.backpacks.tiers': 'Tiers: Leather (3 rows, 27 slots) from leather, string and a chest. Iron (36), Golden (45) and Diamond (54): craft the backpack inside a ring of eight iron ingots, gold ingots or diamonds. Netherite (54, fireproof: it floats in lava and never burns) at a smithing table from a Diamond Backpack, a Netherite Upgrade and a netherite ingot. Every upgrade keeps the contents and the dye. Any other backpack burnt in lava spills what it holds.',
	'projecthero.guide.devices.backpacks.wearing': 'Wearing it: no armour slot needed. Carry a backpack anywhere in your inventory (not in your hand) and it is worn on your back, the best tier if you carry several. A cape is hidden while you wear one, and a Captain America shield slung on your back hangs over the bag. Dye any backpack like leather armour; a water cauldron washes the dye off.',
};
const raw = fs.readFileSync(FILE, 'utf8');
const json = JSON.parse(raw);
const eol = raw.includes('\r\n') ? '\r\n' : '\n';
const add = Object.entries(KEYS).filter(([k]) => !(k in json));
if (add.length === 0) { console.log('nothing to add'); process.exit(0); }
// insert before the final closing brace, keeping the file's own formatting for everything already there
const end = raw.lastIndexOf('}');
let head = raw.slice(0, end).replace(/\s*$/, '');
const lines = add.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
const out = head + ',' + eol + lines.join(',' + eol) + eol + '}' + eol;
JSON.parse(out); // still valid
fs.writeFileSync(FILE, out);
console.log('added ' + add.length + ' keys');
