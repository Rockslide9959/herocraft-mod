// v0.17.2 Recalibrated Omnitrix: Echo Echo's basic body (user: "basic versions ... that we can revamp later").
//   * entity/omnitrix/echo_echo.png          64x64 WIDE player skin: a smooth white shell, the black face plate with two green eyes
//                                             and a round speaker mouth, black speaker rings on the chest / back, black hands and feet
//   * entity/omnitrix/echo_echo_extras.png   disc 0,0 8x8 (white rim, black speaker) | plate 8,0 8x8 (black) | rim 16,0 4x4 (grey)
// The clones are drawn with the same skin.
// Usage: node scratchpad/recal_v0172/echo_echo/gen_echo_echo.js [previewDir]
'use strict';
const H = require('../swampfire/skin_helpers.js');
const { img, hex, hash, rect, save, paintBox, SKIN, tone } = H;
const PREVIEW = process.argv[2];

const WHITE = ['#9aa4ac', '#c4ccd2', '#e2e8ec', '#f2f6f8', '#ffffff'].map(hex);
const BLACK = ['#08090b', '#15181c', '#252a30'].map(hex);
const GREEN = ['#1a9a2a', '#3ae04a', '#a8ffb0'].map(hex);

function speaker(u, v, cx, cy, r) {
	const d = Math.hypot(u - cx, v - cy);
	if (d > r) return null;
	if (d > r - 0.9) return BLACK[2];
	return d < r * 0.35 ? BLACK[2] : BLACK[0];
}

const im = img(64, 64);
paintBox(im, SKIN.head, (f, u, v, w, h) => {
	if (f === 'front') {
		// the face plate (the head is a white shell round it)
		if (u >= 1 && u <= 6 && v >= 2 && v <= 7) {
			if (v === 3 && (u === 2 || u === 5)) return GREEN[2];
			if (v === 3 && (u === 1 || u === 6)) return GREEN[1];
			if (v === 4 && (u === 2 || u === 5)) return GREEN[0];
			if (v === 6 && u >= 3 && u <= 4) return BLACK[2]; // the speaker mouth
			return BLACK[(u + v) % 3 === 0 ? 1 : 0];
		}
	}
	if ((f === 'left' || f === 'right') && Math.hypot(u - 3.5, v - 4) < 2.2) return Math.hypot(u - 3.5, v - 4) < 1 ? BLACK[0] : BLACK[2]; // ear discs
	return tone(WHITE, u, v, w, h, 7 + f.length, f);
});
paintBox(im, SKIN.body, (f, u, v, w, h) => {
	if (f === 'front' || f === 'back') {
		const s = speaker(u, v, 3.5, 6.5, 2.8);
		if (s) return s;
	}
	if (v === h - 1 && f !== 'top' && f !== 'bottom') return WHITE[1];
	return tone(WHITE, u, v, w, h, 17 + f.length, f);
});
for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 3 && f !== 'top') return BLACK[v === h - 1 ? 0 : 1]; // black hands
	if (v === 4 && f !== 'top' && f !== 'bottom') return WHITE[0]; // the elbow seam
	return tone(WHITE, u, v, w, h, 27 + k.length, f);
});
for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
	if (v >= h - 3 && f !== 'top') return BLACK[v === h - 1 ? 0 : 1]; // black feet
	if (v === 6 && f !== 'top' && f !== 'bottom') return WHITE[0]; // the knee seam
	return tone(WHITE, u, v, w, h, 37 + k.length, f);
});
save(im, 'entity/omnitrix/echo_echo.png', PREVIEW);

const ex = img(64, 64);
rect(ex, 0, 0, 8, 8, (u, v) => {
	const d = Math.hypot(u - 3.5, v - 3.5);
	if (d > 3.2) return WHITE[3];
	if (d > 2.4) return BLACK[2];
	return d < 1 ? BLACK[2] : BLACK[0];
});
rect(ex, 8, 0, 8, 8, (u, v) => hash(u, v, 4) > 0.8 ? BLACK[2] : BLACK[0]);
rect(ex, 16, 0, 4, 4, () => WHITE[1]);
save(ex, 'entity/omnitrix/echo_echo_extras.png', PREVIEW);
console.log('echo_echo done');
