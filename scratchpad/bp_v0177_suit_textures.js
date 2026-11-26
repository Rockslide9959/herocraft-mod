// v0.17.7 Black Panther wearable suit: armour layers (the suit skin's top half, alpha kept) and the four item icons.
// node scratchpad/bp_v0177_suit_textures.js
'use strict';
const path = require('path');
const PNG = require('./pnglib.js');

const root = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const skin = PNG.read(path.join(root, 'entity', 'black_panther', 'black_panther.png'));

// The armour layers use the same UVs as the top half of a 64x64 skin (head + hat, body, right arm, right leg), so the suit
// skin's top 64x32 IS the armour texture for both layers.
const layer = Buffer.alloc(64 * 32 * 4);
skin.px.copy(layer, 0, 0, 64 * 32 * 4);
PNG.write(path.join(root, 'models', 'armor', 'black_panther_suit_layer_1.png'), 64, 32, layer);
PNG.write(path.join(root, 'models', 'armor', 'black_panther_suit_layer_2.png'), 64, 32, layer);

const PAL = {
	o: [13, 12, 18, 255],
	d: [24, 23, 32, 255],
	m: [40, 38, 52, 255],
	h: [78, 74, 96, 255],
	p: [150, 84, 255, 255],
	s: [198, 198, 214, 255],
	e: [226, 222, 240, 255],
};

const ICONS = {
	black_panther_helmet: [
		'................',
		'...o........o...',
		'..omo......omo..',
		'..ommoooooommo..',
		'..omhmmmmmmhmo..',
		'..ommmmppmmmmo..',
		'..omeeemmeeemo..',
		'..omdeemmeedmo..',
		'..ommdmppmdmmo..',
		'...ommdppdmmo...',
		'....ommppmmo....',
		'.....ommmmo.....',
		'......oooo......',
		'................',
		'................',
		'................',
	],
	black_panther_chestplate: [
		'................',
		'..oooo....oooo..',
		'.ommmmo..ommmmo.',
		'.omhmmmoommmhmo.',
		'.ommmmmssmmmmmo.',
		'.omdmmsppsmmdmo.',
		'.odmmmmssmmmmdo.',
		'..ommpmmmmpmmo..',
		'..ommmpmmpmmmo..',
		'..ommmmppmmmmo..',
		'..ommmmmmmmmmo..',
		'..omdmmmmmmdmo..',
		'..ommmmppmmmmo..',
		'..oooooooooooo..',
		'................',
		'................',
	],
	black_panther_leggings: [
		'................',
		'...oooooooooo...',
		'...ommmppmmmo...',
		'...ommmmmmmmo...',
		'...ommmoommmo...',
		'...ommo..ommo...',
		'...ohmo..omho...',
		'...ompo..opmo...',
		'...ommo..ommo...',
		'...ompo..opmo...',
		'...ommo..ommo...',
		'...odmo..omdo...',
		'...ommo..ommo...',
		'...oooo..oooo...',
		'................',
		'................',
	],
	black_panther_boots: [
		'................',
		'................',
		'................',
		'................',
		'................',
		'................',
		'...oooo..oooo...',
		'...ommo..ommo...',
		'...ompo..opmo...',
		'...ommo..ommo...',
		'..ommmo..ommmo..',
		'.ommmmo..ommmmo.',
		'.ohhhho..ohhhho.',
		'.oooooo..oooooo.',
		'................',
		'................',
	],
};

for (const [name, rows] of Object.entries(ICONS)) {
	const px = Buffer.alloc(16 * 16 * 4);
	rows.forEach((row, y) => {
		if (row.length !== 16) {
			throw new Error(name + ' row ' + y + ' is ' + row.length + ' wide');
		}
		for (let x = 0; x < 16; x++) {
			const c = PAL[row[x]];
			if (c) {
				px.set(c, (y * 16 + x) * 4);
			}
		}
	});
	PNG.write(path.join(root, 'item', name + '.png'), 16, 16, px);
}
console.log('done');
