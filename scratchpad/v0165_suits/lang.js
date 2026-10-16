// Inserts the v0.16.5 suit / mannequin / wardrobe lang lines after a given key (keeps the file's CRLF line endings).
// node scratchpad/v0165_suits/lang.js
const fs = require('fs');
const F = 'src/main/resources/assets/projecthero/lang/en_us.json';
const crlf = fs.readFileSync(F, 'utf8').includes('\r\n');
let lines = fs.readFileSync(F, 'utf8').replace(/\r\n/g, '\n').split('\n');
function after(key, entries) {
	const at = lines.findIndex(l => l.trimStart().startsWith('"' + key + '"'));
	if (at < 0) throw new Error('no ' + key);
	const add = Object.entries(entries).filter(([k]) => !lines.some(l => l.trimStart().startsWith('"' + k + '"')))
		.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
	lines.splice(at + 1, 0, ...add);
}
const SUITS = {
	comic: ['Comic Classic', 'The comic-book classic: bright red and blue, black webbing, big white eyes and the black spider.'],
	bnd: ['Brand New Day', 'The Brand New Day suit: red and blue with raised webbing, its spider darkened to black.'],
	tasm: ['Amazing (2012)', 'The Amazing Spider-Man suit: red and deep blue with amber lenses and a long-legged black spider.'],
	tasm2: ['Amazing 2', 'The Amazing Spider-Man 2 suit: bright red and navy with big white eyes and a black spider.'],
	raimi: ['2002', 'The 2002 film suit: deep red and navy with raised silver webbing and a black spider.'],
	future_foundation: ['Future Foundation', 'The Future Foundation suit: white and black, with a black spider across the chest and back.'],
};
const how = (extra) => ' Crafted from your original piece (which you keep) with ' + extra + '. Same powers as the original.';
const EXTRA = {
	comic: 'red and blue dye and paper', bnd: 'red and blue dye and a sunflower', tasm: 'red and blue dye and a gold nugget',
	tasm2: 'red and blue dye and white dye', raimi: 'red and blue dye and a cobweb', future_foundation: 'white and black dye and string',
};
const suitLang = {};
for (const [skin, [name, look]] of Object.entries(SUITS)) {
	const id = 'item.projecthero.spider_man_suit_' + skin;
	suitLang[id + '_helmet'] = name + ' Spider-Man Mask';
	suitLang[id + '_helmet.desc'] = 'Works just like the original mask: H peels it off and pulls it back on.';
	suitLang[id + '_chestplate'] = name + ' Spider-Man Suit';
	suitLang[id + '_chestplate.desc'] = look + how(EXTRA[skin]);
	suitLang[id + '_leggings'] = name + ' Spider-Man Leggings';
	suitLang[id + '_boots'] = name + ' Spider-Man Boots';
}
after('item.projecthero.spider_man_suit_classic_boots', suitLang);
after('projecthero.guide.spider_man.suit_v2.trip_mine', {
	'projecthero.guide.spider_man.suits': 'More suits',
	'projecthero.guide.spider_man.suits.body': 'Six more suits, each drawn skin-tight like the original with its own mask (H): Comic Classic, Brand New Day, Amazing (2012), Amazing 2, 2002 and Future Foundation. They are costumes with the original suit\'s powers -- no Web Wings or Trip Mine. Every piece is crafted from the matching original piece, which stays in the grid (you keep it): the two dyes either side of it and the suit\'s own extra below.',
	'projecthero.guide.spider_man.suits.recipe': 'Comic Classic: red dye, blue dye, paper. Brand New Day: red dye, blue dye, a sunflower. Amazing (2012): red dye, blue dye, a gold nugget. Amazing 2: red dye, blue dye, white dye. 2002: red dye, blue dye, a cobweb. Future Foundation: white dye, black dye, string.',
	'projecthero.guide.spider_man.suits.display': 'Show them off on a Mannequin, or keep up to ten on a Wardrobe (see Laboratory Devices -> Mannequin & Wardrobe).',
});
fs.writeFileSync(F, lines.join(crlf ? '\r\n' : '\n'));
console.log('ok');

// ---- v0.16.5 Mannequin + Wardrobe (second pass; the after() helper skips keys already present)
lines = fs.readFileSync(F, 'utf8').replace(/\r\n/g, '\n').split('\n');
after('entity.projecthero.stormbreaker', {
	'entity.projecthero.mannequin': 'Mannequin',
	'item.projecthero.mannequin': 'Mannequin',
	'item.projecthero.mannequin.desc': 'A wooden, player-shaped armour stand. Right-click armour or an item onto it or off it, punch it twice to pick it up.',
	'block.projecthero.wardrobe': 'Wardrobe',
	'block.projecthero.wardrobe.desc': 'Holds ten suits, 3 wide x 2 tall. Right-click the sides to browse, Shift+right-click the middle with empty hands to swap suits, right-click the middle to hang or take one piece.',
	'block.projecthero.wardrobe.selected': 'Suit %s / %s: %s',
	'block.projecthero.wardrobe.empty': '(empty)',
	'block.projecthero.wardrobe.nothing': 'Nothing to swap -- this slot is empty and you wear no armour.',
});
after('projecthero.guide.devices.body', {
	'projecthero.guide.devices.wardrobe': 'Mannequin & Wardrobe',
	'projecthero.guide.devices.wardrobe.mannequin': 'Mannequin: an armour stand shaped like a blank wooden player, so every suit fits it the way it fits you (the Spider-Man suits included). Place it, right-click armour or a held item onto it and right-click to take one off (where you click picks the piece), punch it twice to pick it up with everything it wore. Recipe: an armour stand ringed by three white wool, a stick below.',
	'projecthero.guide.devices.wardrobe.wardrobe': 'Wardrobe: three blocks wide and two tall, facing you when placed (it needs the room). It keeps ten full suits. The selected one stands on the mannequin in the middle and the brass plate on top shows its number; the next four each way hang on the left and right rails. Right-click the left or right side to move the selection that way. Shift+right-click the middle with empty hands to swap your whole suit with the selected one (wear nothing to put it on, or store yours in an empty slot). A plain right-click on the middle works like an armour stand for the selected suit: hold a piece to hang it, empty-handed to take the piece you click. Break any part and the whole Wardrobe drops, with every suit in it. Recipe: planks round a mannequin, a gold ingot on top and a chest below.',
});
fs.writeFileSync(F, lines.join(crlf ? '\r\n' : '\n'));
console.log('ok 2');
