// v0.16.7 (user): Endeavor / Uravity / Eraser Head costumes from the user's chosen minecraftskins.com skins, cut to the
// COSTUME ONLY so the wearer's own skin shows (skin-tone texels, the characters' hair and faces go transparent):
//   * Endeavor (skin 15896107 "Sloth endeavour"): the user does not like its mask -> the whole head (base + hat) goes;
//     the Hellflame Cloak's own flame beard / brows are the head look;
//   * Uravity (24262834): hair, face, eyes go; the pink helmet pods stay;
//   * Eraser Head (23927756): hair and face go; the yellow goggles and their dark lenses stay.
// Writes textures/entity/quirk/<id>_wide.png and _slim.png (slim arms converted from the source's arms).
// Usage: node scratchpad/quirk_user_skins_v0167.js <dir with endeavor.png uravity.png eraserhead.png>
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('./suits/skin_tools.js');
const SRC = process.argv[2];
const OUT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/entity/quirk');

const inHead = (x, y) => y < 16;
const skinTone = (r, g, b) => r > 170 && g > 110 && b >= 85 && r >= g && g >= b && r - b > 18 && r - b < 130;
const brownHair = (r, g, b) => r > 60 && r < 170 && g > 25 && g < 110 && b < 80 && r > g + 15 && g >= b;
const yellow = (r, g, b) => r > 190 && g > 160 && b < 110;

function px(img, x, y) { const i = (y * 64 + x) * 4; return [img.px[i], img.px[i + 1], img.px[i + 2], img.px[i + 3]]; }
function clear(img, x, y) { img.px[(y * 64 + x) * 4 + 3] = 0; }

function cut(name, img) {
	const keep = (x, y) => {
		const [r, g, b, a] = px(img, x, y);
		if (a === 0) return false;
		if (name === 'hellflame') {
			if (inHead(x, y)) return false;
			return !skinTone(r, g, b);
		}
		if (name === 'zero_gravity') {
			if (skinTone(r, g, b)) return false;
			if (inHead(x, y)) {
				if (brownHair(r, g, b)) return false;
				// the face: eyes / brows / mouth texels on the base head front
				if (y >= 8 && x >= 8 && x < 16) {
					const pinkish = r > 200 && g < 190 && b > 150;
					const white = r > 225 && g > 225 && b > 225;
					return pinkish || white && (x === 8 || x === 15);
				}
			}
			return true;
		}
		if (name === 'erasure') {
			if (skinTone(r, g, b)) return false;
			if (inHead(x, y)) {
				if (yellow(r, g, b)) return true;
				// a dark lens / strap texel right next to the yellow frame stays
				for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
					const nx = x + dx, ny = y + dy;
					if (nx >= 0 && nx < 64 && ny >= 0 && ny < 16 && ((nx < 32) === (x < 32))) {
						const [nr, ng, nb, na] = px(img, nx, ny);
						if (na > 0 && yellow(nr, ng, nb) && Math.max(r, g, b) < 70) return true;
					}
				}
				return false;
			}
			return true;
		}
		return true;
	};
	const drop = [];
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (!keep(x, y)) drop.push([x, y]);
	drop.forEach(([x, y]) => clear(img, x, y));
	return drop.length;
}

function isSlim(img) { // a slim skin leaves x 54..55 of the right arm's front rows empty
	for (let y = 20; y < 32; y++) if (px(img, 54, y)[3] > 0 || px(img, 55, y)[3] > 0) return false;
	return true;
}

for (const [file, id] of [['endeavor.png', 'hellflame'], ['uravity.png', 'zero_gravity'], ['eraserhead.png', 'erasure']]) {
	const img = T.decode(path.join(SRC, file));
	const slim = isSlim(img);
	const n = cut(id, img);
	const wide = slim ? T.convertArms(img, false) : img;
	const slimImg = slim ? img : T.convertArms(img, true);
	fs.writeFileSync(path.join(OUT, id + '_wide.png'), T.encode(64, 64, wide.px));
	fs.writeFileSync(path.join(OUT, id + '_slim.png'), T.encode(64, 64, slimImg.px));
	fs.writeFileSync(path.join(SRC, id + '_cut_x10.png'), (() => { const p = T.preview(wide, 10); return T.encode(p.w, p.h, p.px); })());
	console.log(id, 'source', slim ? 'slim' : 'wide', 'cleared', n);
}
