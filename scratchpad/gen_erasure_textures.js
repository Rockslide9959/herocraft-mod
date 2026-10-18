// v0.16.7 Erasure (Eraser Head) textures: the costume skin (wide + slim), the glowing-eyes overlay, the hair-lock /
// goggles parts sheet, the capture-scarf ribbon, plus a contact sheet over Steve for review.
// Usage: node scratchpad/gen_erasure_textures.js   (run from the worktree root)
'use strict';
const fs = require('fs');
const path = require('path');
const tools = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');

const OUT = 'src/main/resources/assets/projecthero/textures/entity/quirk';
const SHOTS = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/quirk_shots/erasure';
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(SHOTS, { recursive: true });

// ---------------------------------------------------------------- canvas helpers
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function hex(c, a = 255) { const n = parseInt(c.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a]; }
function set(im, x, y, c) { if (x < 0 || y < 0 || x >= im.w || y >= im.h || !c) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3]; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function pick(pal, t) { return pal[Math.max(0, Math.min(pal.length - 1, Math.round(t * (pal.length - 1))))]; }
function paint(im, rect, fn) { const [fx, fy, fw, fh] = rect; for (let v = 0; v < fh; v++) for (let u = 0; u < fw; u++) { const c = fn(u, v, fw, fh, fx + u, fy + v); if (c) set(im, fx + u, fy + v, typeof c === 'string' ? hex(c) : c); } }
// the standard skin box unwrap: width w, height h, depth d at (ox, oy)
function box(ox, oy, w, h, d) {
	return { top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d], right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h] };
}

// ---------------------------------------------------------------- palettes
const SUIT = ['#121216', '#18181d', '#1f1f25', '#27272e', '#313139', '#3c3c45'];
const SCARF = ['#55575e', '#6b6e75', '#82858c', '#999ca3', '#b1b4ba', '#c8cacf'];
const BELT = ['#3f4147', '#575a61', '#72757c', '#90939a'];
const BOOT = ['#0b0b0d', '#131316', '#1b1b1f', '#25252a'];
const HAIR = ['#09090b', '#111114', '#18181c', '#212126', '#2c2c33'];

// ---------------------------------------------------------------- the jumpsuit (base layer)
// loose, so lots of soft folds: a light gradient across each face, darker creases that run down it, noise
function suit(u, v, fw, fh, x, y, seed, edgeDark = true) {
	let t = 0.55 + (hash(x, y, seed) - 0.5) * 0.35;
	if (edgeDark && (u === 0 || u === fw - 1)) t -= 0.18;
	// vertical creases
	const crease = (hash(Math.floor(u / 2), Math.floor(v / 4), seed + 7) > 0.72);
	if (crease && u % 2 === 0) t -= 0.25;
	// soft highlight on the upper part
	if (v < 3) t += 0.08;
	return pick(SUIT, Math.max(0, Math.min(1, t)));
}

function makeCostume() {
	const im = img(64, 64);
	// ---- body (16,16) 8x12x4
	const body = box(16, 16, 8, 12, 4);
	for (const f of ['front', 'back', 'right', 'left']) {
		paint(im, body[f], (u, v, fw, fh, x, y) => {
			if (v === 8) return pick(BELT, f === 'front' && (u === 3 || u === 4) ? 1 : 0.35 + hash(x, y, 3) * 0.3); // the belt
			if (v === 9) return pick(BELT, 0.1);
			if (f === 'front' && v === 2 && (u === 3 || u === 4)) return SUIT[0]; // the collar shadow under the scarf
			if (f === 'front' && v >= 10 && (u === 3 || u === 4)) return pick(SUIT, 0.15); // the crotch seam
			if (f === 'back' && v >= 1 && v <= 7 && u === 4) return pick(SUIT, 0.2); // back seam
			return suit(u, v, fw, fh, x, y, 11);
		});
	}
	paint(im, body.top, (u, v, fw, fh, x, y) => pick(SUIT, 0.35 + hash(x, y, 4) * 0.2));
	paint(im, body.bottom, (u, v, fw, fh, x, y) => pick(SUIT, 0.2 + hash(x, y, 5) * 0.2));

	// ---- the capture weapon (jacket layer, 16,32): thick grey wraps round the neck and shoulders, the two ends
	// hanging down the front a little, tied at the back
	const jk = box(16, 32, 8, 12, 4);
	const wrap = (u, v, x, y, phase) => {
		// diagonal wrap bands: a darker line every 3 texels along (u + v)
		const band = (u + v * 2 + phase) % 4;
		let t = band === 0 ? 0.22 : band === 1 ? 0.55 : band === 2 ? 0.78 : 0.62;
		t += (hash(x, y, 21) - 0.5) * 0.18;
		if (v === 0) t += 0.12; // the top roll catches the light
		return pick(SCARF, Math.max(0, Math.min(1, t)));
	};
	paint(im, jk.top, (u, v, fw, fh, x, y) => wrap(u, v, x, y, 1));
	paint(im, jk.front, (u, v, fw, fh, x, y) => {
		if (v <= 2) return wrap(u, v, x, y, 0);
		if (v === 3) return (u >= 1 && u <= 6) ? wrap(u, v, x, y, 2) : null;
		// the two loose ends hanging down over the chest
		if (v >= 4 && v <= 6 && (u === 1 || u === 2)) return wrap(u, v, x, y, 3);
		if (v >= 4 && v <= 5 && (u === 5 || u === 6)) return wrap(u, v, x, y, 1);
		if (v === 7 && u === 2) return pick(SCARF, 0.3);
		// the belt pouches (outer layer for depth)
		if (v >= 8 && v <= 9 && (u === 0 || u === 7)) return v === 8 ? BELT[3] : BELT[1];
		return null;
	});
	paint(im, jk.back, (u, v, fw, fh, x, y) => {
		if (v <= 2) return wrap(u, v, x, y, 2);
		if (v === 3) return (u >= 2 && u <= 5) ? wrap(u, v, x, y, 0) : null;
		if (v === 4 && (u === 3 || u === 4)) return pick(SCARF, 0.45); // the knot
		if (v >= 8 && v <= 9 && (u === 1 || u === 6)) return v === 8 ? BELT[3] : BELT[1];
		return null;
	});
	for (const f of ['right', 'left']) {
		paint(im, jk[f], (u, v, fw, fh, x, y) => {
			if (v <= 2) return wrap(u, v, x, y, f === 'right' ? 3 : 1);
			if (v >= 8 && v <= 9 && u >= 1 && u <= 2) return v === 8 ? BELT[3] : BELT[1]; // a pouch on each hip
			return null;
		});
	}
	// the bottom of the jacket box stays empty (it sits at the waist, inside the legs)

	// ---- arms: black sleeves, hands bare (the wearer's own skin), a rolled cuff, the scarf over the shoulder
	const arm = (ox, oy, sox, soy, mirror) => {
		const a = box(ox, oy, 4, 12, 4);
		for (const f of ['front', 'back', 'right', 'left']) {
			paint(im, a[f], (u, v, fw, fh, x, y) => {
				if (v >= 10) return null; // the hand
				if (v === 9) return pick(SUIT, 0.75 + hash(x, y, 31) * 0.2); // the cuff
				if (v === 8) return pick(SUIT, 0.2);
				return suit(u, v, fw, fh, x, y, 33, false);
			});
		}
		paint(im, a.top, (u, v, fw, fh, x, y) => pick(SUIT, 0.4 + hash(x, y, 34) * 0.2));
		// a.bottom: the hand's underside -- bare skin
		const s = box(sox, soy, 4, 12, 4);
		paint(im, s.top, (u, v, fw, fh, x, y) => wrap(u, v, x, y, 2));
		const outer = mirror ? 'left' : 'right';
		for (const f of ['front', 'back', outer]) {
			paint(im, s[f], (u, v, fw, fh, x, y) => {
				if (v <= 1) return wrap(u, v, x, y, 0);
				if (v === 2 && f === outer) return wrap(u, v, x, y, 1);
				if (v === 9) return pick(SUIT, 0.9); // the rolled cuff stands out
				return null;
			});
		}
		paint(im, s[mirror ? 'right' : 'left'], (u, v, fw, fh, x, y) => (v === 0 ? wrap(u, v, x, y, 3) : v === 9 ? pick(SUIT, 0.85) : null));
	};
	arm(40, 16, 40, 32, false);
	arm(32, 48, 48, 48, true);

	// ---- legs: loose black trousers into black boots with grey soles
	const leg = (ox, oy, pox, poy, seed) => {
		const l = box(ox, oy, 4, 12, 4);
		for (const f of ['front', 'back', 'right', 'left']) {
			paint(im, l[f], (u, v, fw, fh, x, y) => {
				if (v === 11) return pick(BELT, 0.25); // the sole
				if (v >= 8) {
					if (v === 8) return pick(BOOT, 0.9);
					if (f === 'front' && v === 9 && (u === 1 || u === 2)) return pick(BOOT, 1); // laces
					return pick(BOOT, 0.3 + hash(x, y, seed) * 0.4);
				}
				if (v === 4 && f === 'front') return pick(SUIT, 0.15); // the knee crease
				return suit(u, v, fw, fh, x, y, seed);
			});
		}
		paint(im, l.top, (u, v, fw, fh, x, y) => pick(SUIT, 0.3 + hash(x, y, seed + 1) * 0.2));
		paint(im, l.bottom, (u, v, fw, fh, x, y) => pick(BELT, 0.15 + hash(x, y, seed + 2) * 0.15));
		// trousers layer: the bagginess bunching over the boot tops, and a utility strap on the outer thigh
		const p = box(pox, poy, 4, 12, 4);
		for (const f of ['front', 'back', 'right', 'left']) {
			paint(im, p[f], (u, v, fw, fh, x, y) => {
				if (v === 7) return pick(SUIT, 0.5 + hash(x, y, seed + 3) * 0.3);
				if (v === 8 && (u + seed) % 2 === 0) return pick(SUIT, 0.35);
				if (v === 2 && ((seed === 51 && f === 'right') || (seed === 61 && f === 'left'))) return BELT[2];
				return null;
			});
		}
	};
	leg(0, 16, 0, 32, 51);
	leg(16, 48, 0, 48, 61);

	// ---- the head: the face is the wearer's; the hat layer is Aizawa's shaggy black hair (+ a little stubble)
	const hat = box(32, 0, 8, 8, 8);
	const hairC = (x, y, t) => pick(HAIR, Math.max(0, Math.min(1, t + (hash(x, y, 71) - 0.5) * 0.45)));
	paint(im, hat.top, (u, v, fw, fh, x, y) => hairC(x, y, 0.45 + ((u + v) % 3 === 0 ? -0.2 : 0)));
	paint(im, hat.front, (u, v, fw, fh, x, y) => {
		if (v <= 1) return hairC(x, y, 0.5);
		if (v === 2) return [0, 1, 3, 4, 6, 7].includes(u) ? hairC(x, y, 0.35) : null; // ragged fringe
		if (v === 3) return [0, 7].includes(u) || u === 3 ? hairC(x, y, 0.25) : null;
		if (v >= 4 && v <= 5) return u === 0 || u === 7 ? hairC(x, y, 0.2) : null; // hair framing the face
		if (v >= 6) return (u >= 1 && u <= 6 && hash(x, y, 72) > 0.45) || u === 0 || u === 7 ? (u === 0 || u === 7 ? hairC(x, y, 0.2) : hex('#2a2622', 120)) : null; // stubble
		return null;
	});
	for (const f of ['right', 'left']) {
		paint(im, hat[f], (u, v, fw, fh, x, y) => {
			const back = f === 'right' ? fw - 1 - u : u; // 0 at the front edge, 7 at the back
			const toFront = 7 - back;
			if (v <= 3) return hairC(x, y, 0.4);
			if (v <= 5) return back >= 2 || toFront === 7 ? hairC(x, y, 0.3) : (back === 1 && v === 4 ? hairC(x, y, 0.2) : null);
			if (v === 6) return back >= 3 ? hairC(x, y, 0.25) : null;
			if (v === 7) return back >= 4 && hash(x, y, 73) > 0.3 ? hairC(x, y, 0.2) : null;
			return null;
		});
	}
	paint(im, hat.back, (u, v, fw, fh, x, y) => (v < 7 || hash(x, y, 74) > 0.35 ? hairC(x, y, 0.35 + (u % 3 === 0 ? -0.15 : 0)) : null));
	return im;
}

// ---------------------------------------------------------------- the glowing eyes (64x64 over the head)
function makeEyes() {
	const im = img(64, 64);
	const R = '#ff2626';
	for (const x of [9, 10, 13, 14]) set(im, x, 12, hex(R, 255));
	for (const x of [9, 10, 13, 14]) { set(im, x, 11, hex('#ff3a3a', 90)); set(im, x, 13, hex('#ff3a3a', 70)); }
	for (const x of [8, 11, 12, 15]) set(im, x, 12, hex('#ff3030', 70));
	return im;
}

// ---------------------------------------------------------------- the parts sheet (64x32): hair locks + goggles
function makeParts() {
	const im = img(64, 32);
	// hair locks: four 8x10 blocks (box 2x8x2), lighter at the tips (the top rows of the side faces)
	for (let b = 0; b < 4; b++) {
		paint(im, [b * 8, 0, 8, 10], (u, v, fw, fh, x, y) => {
			const tip = v <= 3 ? 0.25 : 0;
			return pick(HAIR, Math.min(1, 0.25 + tip + hash(x, y, 80 + b) * 0.45 + (u % 2 === 0 ? 0.1 : 0)));
		});
	}
	const FRAME = ['#1c1c1c', '#2b2b2b', '#3a3a3a'];
	const LENS = ['#c99a0e', '#e8b81a', '#f6cf32', '#ffe680'];
	// the plate (box 9x3x1 at 0,16): top / bottom / sides frame, front and back two lenses and a bridge
	paint(im, [0, 16, 20, 4], (u, v, fw, fh, x, y) => pick(FRAME, hash(x, y, 90)));
	const lens = (u, v, x, y, front) => {
		if (u === 0 || u === 8 || u === 4) return FRAME[1];
		const hi = front && v === 0 && (u === 1 || u === 5);
		const t = hi ? 3 : v === 0 ? 2 : v === 1 ? 1.6 : 1;
		const c = hex(LENS[Math.round(t)], front ? 215 : 255);
		return c;
	};
	paint(im, [1, 17, 9, 3], (u, v, fw, fh, x, y) => lens(u, v, x, y, true));
	paint(im, [11, 17, 9, 3], (u, v, fw, fh, x, y) => lens(u, v, x, y, false));
	// straps (side: 0,21 box 1x1x9; back: 20,16 box 9x1x1): black elastic, a light stitch
	paint(im, [0, 21, 20, 10], (u, v, fw, fh, x, y) => ((x + y) % 4 === 0 ? FRAME[2] : FRAME[0]));
	paint(im, [20, 16, 20, 2], (u, v, fw, fh, x, y) => ((x + y) % 4 === 0 ? FRAME[2] : FRAME[0]));
	return im;
}

// ---------------------------------------------------------------- the scarf ribbon (8x16, u across, v along)
function makeScarf() {
	const im = img(8, 16);
	paint(im, [0, 0, 8, 16], (u, v, fw, fh, x, y) => {
		let t = 0.6 + (hash(x, y, 95) - 0.5) * 0.2;
		if (u === 0 || u === 7) t = 0.18; // hemmed edges
		else if (u === 1 || u === 6) t -= 0.12;
		if ((u + v) % 5 === 0 && u > 0 && u < 7) t -= 0.18; // the weave
		if (v % 8 === 0 && u > 0 && u < 7) t += 0.15;
		return pick(SCARF, Math.max(0, Math.min(1, t)));
	});
	return im;
}

// ---------------------------------------------------------------- contact sheet over Steve
function over(dst, src) { // src drawn over dst (alpha), both same size
	const out = { w: dst.w, h: dst.h, px: Buffer.from(dst.px) };
	for (let i = 0; i < dst.w * dst.h; i++) {
		const a = src.px[i * 4 + 3] / 255; if (a <= 0) continue;
		for (let k = 0; k < 3; k++) out.px[i * 4 + k] = Math.round(src.px[i * 4 + k] * a + out.px[i * 4 + k] * (1 - a));
		out.px[i * 4 + 3] = Math.max(out.px[i * 4 + 3], src.px[i * 4 + 3]);
	}
	return out;
}
function sheet(skin, slim, file) {
	const base = tools.decode(`scratchpad/ref/steve_${slim ? 'slim' : 'wide'}.png`);
	// Steve under the costume (his face / hands), but drop Steve's own overlay layer first
	const clean = { w: 64, h: 64, px: Buffer.from(base.px) };
	const clear = (x0, y0, w, h) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) clean.px.fill(0, (y * 64 + x) * 4, (y * 64 + x) * 4 + 4); };
	clear(32, 0, 32, 16); clear(16, 32, 24, 16); clear(40, 32, 16, 16); clear(0, 32, 16, 16); clear(48, 48, 16, 16); clear(0, 48, 16, 16);
	const s = over(clean, skin);
	const a = slim ? 3 : 4;
	const S = 10, W = 4 * 26, H = 44;
	const out = img(W * S, H * S);
	for (let i = 0; i < out.w * out.h; i++) { out.px[i * 4] = 70; out.px[i * 4 + 1] = 74; out.px[i * 4 + 2] = 82; out.px[i * 4 + 3] = 255; }
	// draw a skin rect (base then its overlay, the overlay inflated is ignored -- same texels) at (gx, gy) in grid units
	const blit = (rect, ovl, gx, gy, flip = false) => {
		const [rx, ry, rw, rh] = rect;
		for (let v = 0; v < rh; v++) for (let u = 0; u < rw; u++) {
			const su = flip ? rw - 1 - u : u;
			let c = get(s, rx + su, ry + v);
			if (ovl) { const o = get(s, ovl[0] + su, ovl[1] + v); if (o[3] > 0) { const al = o[3] / 255; c = [0, 1, 2].map(k => Math.round(o[k] * al + c[k] * (1 - al))).concat([255]); } }
			if (c[3] === 0) continue;
			for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) set(out, (gx + u) * S + xx, (gy + v) * S + yy, c);
		}
	};
	const B = (ox, oy, w, h, d) => box(ox, oy, w, h, d);
	const head = B(0, 0, 8, 8, 8), hat = B(32, 0, 8, 8, 8), body = B(16, 16, 8, 12, 4), jk = B(16, 32, 8, 12, 4);
	const ra = B(40, 16, a, 12, 4), rs = B(40, 32, a, 12, 4), la = B(32, 48, a, 12, 4), ls = B(48, 48, a, 12, 4);
	const rl = B(0, 16, 4, 12, 4), rp = B(0, 32, 4, 12, 4), ll = B(16, 48, 4, 12, 4), lp = B(0, 48, 4, 12, 4);
	const pos = (r) => [r[0], r[1]];
	// FRONT
	let x0 = 1;
	blit(head.front, pos(hat.front), x0 + 4, 1); blit(body.front, pos(jk.front), x0 + 4, 9);
	blit(ra.front, pos(rs.front), x0 + 4 - a, 9); blit(la.front, pos(ls.front), x0 + 12, 9);
	blit(rl.front, pos(rp.front), x0 + 4, 21); blit(ll.front, pos(lp.front), x0 + 8, 21);
	// BACK
	x0 = 27;
	blit(head.back, pos(hat.back), x0 + 4, 1); blit(body.back, pos(jk.back), x0 + 4, 9);
	blit(la.back, pos(ls.back), x0 + 4 - a, 9); blit(ra.back, pos(rs.back), x0 + 12, 9);
	blit(ll.back, pos(lp.back), x0 + 4, 21); blit(rl.back, pos(rp.back), x0 + 8, 21);
	// RIGHT side
	x0 = 53;
	blit(head.right, pos(hat.right), x0 + 2, 1); blit(ra.right, pos(rs.right), x0 + 4, 9); blit(rl.right, pos(rp.right), x0 + 4, 21);
	blit(body.right, pos(jk.right), x0 + 9, 9);
	// LEFT side
	x0 = 79;
	blit(head.left, pos(hat.left), x0 + 2, 1); blit(la.left, pos(ls.left), x0 + 4, 9); blit(ll.left, pos(lp.left), x0 + 4, 21);
	blit(body.left, pos(jk.left), x0 + 9, 9);
	// tops: head / shoulders, under the side views
	blit(hat.top, null, 5, 35); blit(jk.top, null, 31, 37); blit(rs.top, null, 57, 37); blit(ls.top, null, 83, 37);
	fs.writeFileSync(file, tools.encode(out.w, out.h, out.px));
}

const wide = makeCostume();
const slim = tools.convertArms(wide, true);
const write = (name, im) => fs.writeFileSync(path.join(OUT, name), tools.encode(im.w, im.h, im.px));
write('erasure_wide.png', wide);
write('erasure_slim.png', slim);
write('erasure_eyes.png', makeEyes());
write('erasure_parts.png', makeParts());
write('erasure_scarf.png', makeScarf());
sheet(wide, false, path.join(SHOTS, 'erasure_costume_wide_sheet.png'));
sheet(slim, true, path.join(SHOTS, 'erasure_costume_slim_sheet.png'));
const pw = tools.preview(wide, 10);
fs.writeFileSync(path.join(SHOTS, 'erasure_wide_texture.png'), tools.encode(pw.w, pw.h, pw.px));
const pp = tools.preview(makeParts(), 12);
fs.writeFileSync(path.join(SHOTS, 'erasure_parts_texture.png'), tools.encode(pp.w, pp.h, pp.px));
console.log('ok');
