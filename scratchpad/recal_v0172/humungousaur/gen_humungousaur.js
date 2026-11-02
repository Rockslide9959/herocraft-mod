// v0.17.2 Recalibrated Omnitrix: Humungousaur's basic body (user: "basic versions ... that we can revamp later").
//   * entity/omnitrix/humungousaur.png          64x64 WIDE player skin: tan-brown hide, the cream belly plates, a darker scaly back,
//                                                small green eyes under a heavy brow, the black shorts with a green belt (his
//                                                Alien Force outfit), three-toed feet
//   * entity/omnitrix/humungousaur_extras.png   tail 0,0 8x8 (hide, banded) | plate 8,0 4x4 (dark back plate) | belly 12,0 4x4 |
//                                                spike 16,0 4x4 (bone)
// Usage: node scratchpad/recal_v0172/humungousaur/gen_humungousaur.js [previewDir]
'use strict';
const H = require('../swampfire/skin_helpers.js');
const { img, hex, hash, shade, rect, save, paintBox, SKIN, tone } = H;
const PREVIEW = process.argv[2];

const HIDE = ['#5a3e24', '#7a5634', '#9c7246', '#b58a5a', '#cba47a'].map(hex);
const BACK = ['#3e2a18', '#563a22', '#6e4a2c'].map(hex);
const CREAM = ['#c8ae82', '#e0c89a', '#f0dcb4'].map(hex);
const BLACK = ['#0c0d0e', '#1a1c1e', '#2a2d30'].map(hex);
const GREEN = ['#1a9a2a', '#3ae04a', '#a8ffb0'].map(hex);
const BONE = ['#b8ac8c', '#d8ccaa', '#efe6c8'].map(hex);

function scales(u, v, seed) { return ((u + (v % 2)) % 2 === 0) && hash(u, v, seed) > 0.45; }

const im = img(64, 64);
paintBox(im, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		if (v === 2) return HIDE[0]; // the heavy brow ridge
		if (v === 3 && (u === 1 || u === 6)) return GREEN[1];
		if (v === 3 && (u === 2 || u === 5)) return GREEN[2];
		if (v >= 5) {
			// a wide jaw: the cream underside, a dark mouth line
			if (v === 5) return BLACK[1];
			return CREAM[v === 7 ? 0 : 1];
		}
	}
	if (f === 'top' || f === 'back') return scales(u, v, 5) ? BACK[1] : BACK[2];
	if (f === 'bottom') return CREAM[0];
	return tone(HIDE, u, v, w, h, 3 + f.length, f);
});
paintBox(im, SKIN.body, (f, u, v, w, h) => {
	if (v >= 9 && f !== 'top' && f !== 'bottom') return v === 9 ? GREEN[0] : BLACK[(u + v) % 2]; // the shorts' waistband
	if (f === 'front' && u >= 1 && u <= 6) {
		// the cream belly plates
		if (v % 3 === 2) return CREAM[0];
		return CREAM[u === 1 || u === 6 ? 1 : 2];
	}
	if (f === 'back') return scales(u, v, 9) ? BACK[0] : BACK[1];
	return tone(HIDE, u, v, w, h, 13 + f.length, f);
});
for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v === h - 1 && f !== 'top') return BONE[1]; // the claws
	if (v >= h - 3 && f !== 'top') return HIDE[1];
	if (f === 'front' && v >= 3 && v <= 8 && u >= 1 && u <= 2) return CREAM[1]; // the inner forearm
	if (scales(u, v, 23 + k.length) && f !== 'front') return shade(HIDE[2], 0.85);
	return tone(HIDE, u, v, w, h, 23 + k.length, f);
});
for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v <= 5 && f !== 'bottom') return f === 'top' ? BLACK[1] : BLACK[(u + v) % 2]; // the black shorts
	if (v >= h - 2 && f !== 'top') return v === h - 1 && f === 'front' && u % 2 === 0 ? BONE[0] : HIDE[0]; // three-toed feet
	return tone(HIDE, u, v, w, h, 33 + k.length, f);
});
save(im, 'entity/omnitrix/humungousaur.png', PREVIEW);

const ex = img(64, 64);
rect(ex, 0, 0, 8, 8, (u, v) => {
	if (v >= 6) return CREAM[1]; // the tail's underside band
	return scales(u, v, 2) ? BACK[1] : HIDE[2];
});
rect(ex, 8, 0, 4, 4, (u, v) => (u + v) % 2 ? BACK[0] : BACK[1]);
rect(ex, 12, 0, 4, 4, (u, v) => v === 3 ? CREAM[0] : CREAM[2]);
rect(ex, 16, 0, 4, 4, (u, v) => BONE[v < 2 ? 2 : 1]);
save(ex, 'entity/omnitrix/humungousaur_extras.png', PREVIEW);
console.log('humungousaur done');
