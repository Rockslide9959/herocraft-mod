// v0.17.1 (user: "add all these flash suits please"): the four extra Flash Suit styles, cut from the user's skins in
// ./src (downloaded 2026-10-26):
//   zoom.png          novaskin post 4927066664861696 "Zoom (CW TV show The Flash)"  -> Zoom, eyes recoloured blue -> grey
//   reverse_flash.png namemc 411d7cd8a63750cf                                        -> Reverse-Flash (Eobard Thawne)
//   classic.png       namemc a109e78d781f33d0                                        -> the classic comic Flash (Barry Allen)
//   scarlet.png       namemc 2647a5236e6ae4df (slim arms)                            -> a crimson "Scarlet Speedster" suit
//
// Each is drawn on the existing Flash Suit rig (geo/flash.geo.json, a full 64x64 player-skin rig with wide arms), the
// same way textures/armor/flash.png is: the skin as it is, except
//   - slim arms are widened to 4 px (skin_tools.convertArms),
//   - the wearer's face is cut out of the cowl: skin-tone texels anywhere on the head (and the drawn grin under
//     Reverse-Flash's cowl) go transparent, so the wearer's own mouth and chin show through, as on the original suit,
//   - base-layer gaps on the body / arms / legs are filled (skin_tools.fillSuitGaps), and the unused 56..63 x 16..31
//     corner is cleared.
// Output: src/main/resources/assets/projecthero/textures/armor/flash_<id>.png. Run from the repo root:
//   node scratchpad/flash_suits_v0171/gen_flash_styles.js        (re-runnable; PREVIEW=<dir> also writes flat views)
'use strict';
const fs = require('fs');
const path = require('path');
const tools = require('../suits/skin_tools.js');

const ROOT = path.join(__dirname, '..', '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/armor');
const SRC = path.join(__dirname, 'src');

const isSkinTone = (r, g, b) => r >= 200 && g / r >= 0.55 && g / r <= 0.88 && b / r >= 0.45 && b / r <= 0.78;
const inHead = (x, y) => y < 16 && x < 64; // both head boxes (base 0..31, hat 32..63)

function build(id, opts) {
	let img = tools.decode(path.join(SRC, id + '.png'));
	if (img.w !== 64 || img.h !== 64) throw new Error(id + ': expected 64x64, got ' + img.w + 'x' + img.h);
	if (opts.slim) img = tools.convertArms(img, false);
	const px = img.px;
	const at = (x, y) => (y * 64 + x) * 4;
	let cut = 0;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (!px[i + 3]) continue;
		const [r, g, b] = [px[i], px[i + 1], px[i + 2]];
		// the face (head front base 8..15 / hat 40..47, rows 8..15): skin, and a drawn grin under the cowl
		const front = y >= 8 && ((x >= 8 && x < 16) || (x >= 40 && x < 48));
		const grin = opts.grin && front && y >= 13 && r > 235 && g > 235 && b > 235;
		if (inHead(x, y) && (isSkinTone(r, g, b) || grin)) {
			px.fill(0, i, i + 4);
			cut++;
		}
	}
	// the unused corner (some skin sites leave a marker pixel there)
	for (let y = 16; y < 32; y++) for (let x = 56; x < 64; x++) px.fill(0, at(x, y), at(x, y) + 4);
	const filled = tools.fillSuitGaps(img, false);
	if (opts.eyes) opts.eyes(px, at);
	// v0.17.1 (user: "for the wally west suit, remove the hair on top of his head"): the hat layer's hair -- its whole top
	// face and the top three rows of every side (the cowl's lower flares stay)
	if (opts.noHair) {
		for (let y = 0; y < 8; y++) for (let x = 40; x < 48; x++) px.fill(0, at(x, y), at(x, y) + 4);
		for (let y = 8; y < 11; y++) for (let x = 32; x < 64; x++) px.fill(0, at(x, y), at(x, y) + 4);
	}
	fs.writeFileSync(path.join(OUT, 'flash_' + id + '.png'), tools.encode(64, 64, px));
	console.log(id, 'face texels cut', cut, 'gaps filled', filled);
	return img;
}

// Zoom: the two-texel blue eye slits (row 12) become grey lenses -- light inner texel as the highlight, mid-grey outer.
const ZOOM_BLUE = (r, g, b) => b > r + 40 && b > g + 20;
function zoomEyes(px, at) {
	const LIGHT = [208, 212, 216], MID = [150, 155, 161], DARK = [112, 117, 123];
	let n = 0;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (!px[i + 3] || !ZOOM_BLUE(px[i], px[i + 1], px[i + 2])) continue;
		// on the face: inner texel of each slit (next to the nose) is the highlight
		let c = MID;
		if (y === 12 && (x === 10 || x === 13)) c = LIGHT;
		else if (y !== 12) c = DARK; // any other blue trim, if one turns up
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2];
		n++;
	}
	console.log('zoom: blue texels recoloured grey', n);
}

const out = {
	zoom: build('zoom', { eyes: zoomEyes }),
	reverse_flash: build('reverse_flash', { grin: true }),
	classic: build('classic', {}),
	scarlet: build('scarlet', { slim: true, noHair: true }), // Wally West
};

if (process.env.PREVIEW) {
	const cp = require('child_process');
	for (const id of Object.keys(out)) {
		cp.execFileSync('node', [path.join(__dirname, '..', 'skin_views.js'), path.join(OUT, 'flash_' + id + '.png'),
			path.join(process.env.PREVIEW, 'style_' + id + '.png')], { env: { ...process.env, SKIN_TOOLS: path.join(__dirname, '..', 'suits', 'skin_tools.js') } });
	}
}
