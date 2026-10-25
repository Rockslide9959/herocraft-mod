// v0.17.1 Falcon (Sam Wilson) assets, from the user's chosen Planet Minecraft skin (scratchpad/falcon_refs/falcon_pmc_19482157.png,
// 64x64, slim arms, the classic red / white Falcon):
//   * the suit overlay, wide-arm and slim-arm cuts (textures/entity/falcon/suit_wide.png / suit_slim.png): the whole head is
//     cut out (face, skin, hair -- the wearer's own head shows, the v0.16.6 suit rule) except the Falcon Goggles, which are
//     re-laid on the eye row (orange frame, dark lenses, the skin's own colours) with a dark strap round the head, on the
//     base layer and again raised on the hat layer so the wearer's hair never covers them;
//   * the flat armour layers for a piece worn without the rest of the set (textures/models/armor/falcon_layer_1/2.png);
//   * the four item icons and a plain white texture for the box-built wings / Redwing.
// Usage: node scratchpad/suits/make_falcon_assets.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('./skin_tools.js');
const ROOT = path.join(__dirname, '../..');
const SRC = path.join(ROOT, 'scratchpad/falcon_refs/falcon_pmc_19482157.png');
const A = path.join(ROOT, 'src/main/resources/assets/projecthero/textures');
const at = (img, x, y) => (y * img.w + x) * 4;
const put = (img, x, y, c) => { const i = at(img, x, y); img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = c.length > 3 ? c[3] : 255; };
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const shade = (c, k) => [0, 1, 2].map(i => Math.max(0, Math.min(255, Math.round(c[i] * k)))).concat(255);

const FRAME = hex(0xE58614), FRAME_DK = hex(0x884025), LENS = hex(0x10121C), LENS_HI = hex(0x3A4060), BRIDGE = hex(0xFFB433);
const STRAP = hex(0x22263C), STRAP_DK = hex(0x191C2C);

function goggles(img, ox, k) {
	// ox = 8 (base face) or 40 (hat face); frame on row 11, lenses on the eye row 12, a dark lip on row 13
	const P = (x, y, c) => put(img, ox + x, y, k === 1 ? c : shade(c, k));
	for (let x = 1; x <= 6; x++) P(x, 11, FRAME);
	P(0, 11, FRAME_DK); P(7, 11, FRAME_DK); P(0, 12, FRAME_DK); P(7, 12, FRAME_DK);
	P(1, 12, LENS_HI); P(2, 12, LENS); P(3, 12, BRIDGE); P(4, 12, BRIDGE); P(5, 12, LENS_HI); P(6, 12, LENS);
	P(1, 13, FRAME_DK); P(2, 13, FRAME_DK); P(5, 13, FRAME_DK); P(6, 13, FRAME_DK);
}
function strap(img, base) {
	// rows 11-12 round the right side, the left side and the back of the head
	const runs = base ? [[0, 8], [16, 24], [24, 32]] : [[32, 40], [48, 56], [56, 64]];
	for (const [a, b] of runs) for (let x = a; x < b; x++) { put(img, x, 11, STRAP); put(img, x, 12, STRAP_DK); }
	if (!base) { put(img, 59, 11, FRAME_DK); put(img, 60, 11, FRAME_DK); put(img, 59, 12, FRAME_DK); put(img, 60, 12, FRAME_DK); } // the buckle
}

// the skin's brown skin tones (743420, 60291a, 884025, 9f4c2a and their shades): warm, r > g > b, never the suit's reds
// (their blue is above their green) or its greys
const isSkin = (r, g, b) => r > g && g > b && r - b > 40 && r < 210 && g > 25 && g < 150;
function cutSkin(img) {
	let n = 0;
	for (let y = 16; y < 64; y++) for (let x = 0; x < 64; x++) {
		const i = at(img, x, y);
		if (img.px[i + 3] && isSkin(img.px[i], img.px[i + 1], img.px[i + 2])) { img.px[i + 3] = 0; n++; }
	}
	return n;
}

function suit() {
	const img = T.decode(SRC);
	// the head (base and hat layer) goes, every texel: the wearer's own face, skin and hair show
	for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) img.px[at(img, x, y) + 3] = 0;
	goggles(img, 8, 0.8);
	strap(img, true);
	goggles(img, 40, 1);
	strap(img, false);
	const slimSource = !img.px[at(img, 55, 24) + 3];
	const filled = T.fillSuitGaps(img, slimSource);
	const slim = slimSource ? img : T.convertArms(img, true);
	const wide = slimSource ? T.convertArms(img, false) : img;
	T.fillSuitGaps(wide, false);
	T.fillSuitGaps(slim, true);
	// v0.17.1 (user): every bare-skin texel goes too, not just the face -- Sam Wilson's brown skin tones on the torso, arms,
	// legs and their overlay layers (cut AFTER the gap fill, which would otherwise paint them back)
	console.log('skin texels cut: wide', cutSkin(wide), '| slim', cutSkin(slim));
	fs.mkdirSync(path.join(A, 'entity/falcon'), { recursive: true });
	fs.writeFileSync(path.join(A, 'entity/falcon/suit_slim.png'), T.encode(slim.w, slim.h, slim.px));
	fs.writeFileSync(path.join(A, 'entity/falcon/suit_wide.png'), T.encode(wide.w, wide.h, wide.px));
	console.log('suit:', slimSource ? 'slim source' : 'wide source', '| gap texels filled', filled);
	return wide;
}

function armour(wide) {
	// 64x32 armour layers in the classic layout: the top half of the suit (head, body, right arm, right leg)
	const l1 = { w: 64, h: 32, px: Buffer.alloc(64 * 32 * 4) };
	const l2 = { w: 64, h: 32, px: Buffer.alloc(64 * 32 * 4) };
	for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
		const s = at(wide, x, y), d = (y * 64 + x) * 4;
		wide.px.copy(l1.px, d, s, s + 4);
		// leggings: the legs, and the bottom four rows (the belt and hips) of the body
		const legs = y >= 16 && x < 16;
		const hips = y >= 16 && x >= 16 && x < 40 && (y < 20 ? false : y >= 28);
		if (legs || hips) wide.px.copy(l2.px, d, s, s + 4);
	}
	fs.mkdirSync(path.join(A, 'models/armor'), { recursive: true });
	fs.writeFileSync(path.join(A, 'models/armor/falcon_layer_1.png'), T.encode(64, 32, l1.px));
	fs.writeFileSync(path.join(A, 'models/armor/falcon_layer_2.png'), T.encode(64, 32, l2.px));
}

// ---- item icons (16 x 16): '.' clear, the rest from the palette
const PAL = {
	'.': null, 'k': hex(0x15161C), 'g': hex(0x5A6070), 's': hex(0x9AA2B0), 'S': hex(0xC9D0DA), 'w': hex(0xEEF1F5),
	'r': hex(0xB8102E), 'R': hex(0xE8143C), 'd': hex(0x6E0A1C), 'o': hex(0xE58614), 'O': hex(0xFFB433), 'b': hex(0x5A2A1A), 'c': hex(0x7FE3FF),
};
const ICONS = {
	falcon_wing_pack: [
		'................',
		'ww............ww',
		'Sww..........wwS',
		'sSww........wwSs',
		'gsSww......wwSsg',
		'.gsSwwkrrkwwSsg.',
		'..gsSwrRRrwSsg..',
		'...gsSrRRrSsg...',
		'....gsrRRrsg....',
		'.....grddrg.....',
		'......kcck......',
		'......kcck......',
		'.......kk.......',
		'................',
		'................',
		'................'],
	falcon_goggles: [
		'................',
		'................',
		'................',
		'................',
		'..oooooooooooo..',
		'.oOkkkoOOokkkOo.',
		'ko.kckkoOkkckk.k',
		'kbokkkkobokkkkbk',
		'k.bbbbb..bbbbb.k',
		'k..............k',
		'.k............k.',
		'..kk........kk..',
		'................',
		'................',
		'................',
		'................'],
	falcon_flight_suit_leggings: [
		'................',
		'...bbbbbbbbbb...',
		'...rRRRRRRRRr...',
		'...rRRRddRRRr...',
		'...rRRRddRRRr...',
		'...rRRr..rRRr...',
		'...rRRr..rRRr...',
		'...wSSr..rSSw...',
		'...rRRr..rRRr...',
		'...rRRr..rRRr...',
		'...rRRr..rRRr...',
		'...rRRr..rRRr...',
		'...rRRr..rRRr...',
		'...dddd..dddd...',
		'................',
		'................'],
	falcon_flight_suit_boots: [
		'................',
		'................',
		'................',
		'................',
		'...rRr....rRr...',
		'...rRr....rRr...',
		'...rRr....rRr...',
		'...wSw....wSw...',
		'...rRr....rRr...',
		'...rRr....rRr...',
		'...rRRr...rRRr..',
		'...rRRRr..rRRRr.',
		'...ddddd..ddddd.',
		'................',
		'................',
		'................'],
};
function icons() {
	fs.mkdirSync(path.join(A, 'item'), { recursive: true });
	for (const [name, rows] of Object.entries(ICONS)) {
		const img = { w: 16, h: 16, px: Buffer.alloc(16 * 16 * 4) };
		rows.forEach((row, y) => [...row].forEach((ch, x) => { const c = PAL[ch]; if (c) put(img, x, y, c); }));
		fs.writeFileSync(path.join(A, 'item', name + '.png'), T.encode(16, 16, img.px));
	}
	const white = { w: 16, h: 16, px: Buffer.alloc(16 * 16 * 4, 255) };
	fs.writeFileSync(path.join(A, 'entity/falcon/white.png'), T.encode(16, 16, white.px));
}

const wide = suit();
armour(wide);
icons();
console.log('done');
