// v0.16.7: Tenya Iida's (Ingenium's) hero suit as a 64x64 costume skin (wide + slim) and a contact sheet.
// White / silver armour with blue accents, the full helmet with a dark visor, and the engine exhaust pipes on the calves
// (outer layer). Every face of every base-layer cube is filled. Usage: node gen_engine_suit.js <outDir> <previewPng>
'use strict';
const fs = require('fs');
const path = require('path');
const { encode, convertArms } = require('./skin_tools.js');

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const C = {
	W0: hex('#ffffff'), W1: hex('#eef1f5'), W2: hex('#d6dce4'), W3: hex('#b3bcc8'), W4: hex('#8c96a5'),
	B0: hex('#9fd4ff'), B1: hex('#4b9cf5'), B2: hex('#2c6bd0'), B3: hex('#1c4596'),
	V0: hex('#7d9cc4'), V1: hex('#2e3d55'), V2: hex('#1b2333'), V3: hex('#10151f'),
	G1: hex('#6b7480'), G2: hex('#4c545f'), G3: hex('#353b44'), G4: hex('#24282f'),
	GLOW: hex('#c9ecff'),
};

const W = 64, H = 64;
const px = Buffer.alloc(W * H * 4);
const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) & 255) / 255; };
function put(x, y, c, shade = 1) {
	const i = (y * W + x) * 4;
	const n = 1 + (hash(x, y) - 0.5) * 0.035;
	for (let k = 0; k < 3; k++) px[i + k] = Math.max(0, Math.min(255, Math.round(c[k] * shade * n)));
	px[i + 3] = 255;
}

// faces of a box at (ox, oy) with width w, height h, depth d (the standard skin unwrap)
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
// paint(face, u, v, fw, fh) -> colour | null; a light gradient top->bottom is applied to the side faces
function paintBox(ox, oy, w, h, d, paint, opts = {}) {
	const f = faces(ox, oy, w, h, d);
	for (const name of Object.keys(f)) {
		const [fx, fy, fw, fh] = f[name];
		for (let v = 0; v < fh; v++) for (let u = 0; u < fw; u++) {
			const c = paint(name, u, v, fw, fh);
			if (!c) continue;
			let s = 1;
			if (name !== 'top' && name !== 'bottom' && !opts.flat) s = 1.03 - 0.07 * (v / Math.max(1, fh - 1));
			if (name === 'bottom') s = 0.86;
			if (!opts.flat && name !== 'top' && name !== 'bottom' && (u === 0 || u === fw - 1)) s *= 0.94;
			if (opts.flat && name !== 'top' && name !== 'bottom' && v === 0) s *= 1.04;
			put(fx + u, fy + v, c, s);
		}
	}
}

// ---------------------------------------------------------------- the helmet
function helmet(face, u, v) {
	if (face === 'bottom') return (u === 0 || u === 7 || v === 0 || v === 7) ? C.W3 : C.G2; // the neck seal
	if (face === 'top') {
		if (u === 3 || u === 4) return v === 7 ? C.B1 : (u === 3 ? C.B1 : C.B2); // the crest stripe, front to back
		if (u === 2 || u === 5) return C.W3;
		return (u === 0 || u === 7) ? C.W2 : C.W1;
	}
	if (face === 'front') {
		if (v === 0) return (u === 3 || u === 4) ? C.B1 : C.W2;
		if (v <= 2) return (u === 3 || u === 4) ? (v === 1 ? C.B2 : C.B3) : (u === 0 || u === 7 ? C.W2 : C.W1);
		// the dark visor: a wide band over the eyes narrowing at the nose
		if (v === 3) return u === 0 || u === 7 ? C.W3 : (u === 1 || u === 2 ? C.V0 : u === 6 ? C.V1 : C.V2);
		if (v === 4) return u === 0 || u === 7 ? C.W3 : (u === 1 ? C.V1 : C.V3);
		if (v === 5) return u === 0 || u === 7 ? C.W2 : (u >= 2 && u <= 5 ? C.V2 : C.W3);
		// the mouth guard: silver with grille slits
		if (v === 6) return u === 0 || u === 7 ? C.W2 : (u === 2 || u === 5 ? C.G1 : C.W1);
		return u === 0 || u === 7 ? C.W3 : (u === 2 || u === 5 ? C.G2 : u === 3 || u === 4 ? C.B2 : C.W2);
	}
	if (face === 'right' || face === 'left') {
		// u runs back->front on the right face, front->back on the left one
		const fwd = face === 'right' ? u : 7 - u;
		if (v === 7) return C.W3;
		// the round "ear" engine housing
		const dx = fwd - 3.5, dy = v - 4;
		const r = Math.sqrt(dx * dx + dy * dy);
		if (r < 0.8) return C.B0;
		if (r < 1.6) return C.B2;
		if (r < 2.3) return C.W4;
		if (fwd === 7 && v >= 3 && v <= 4) return C.V2; // the visor wraps round
		if (v === 0) return C.W2;
		return C.W1;
	}
	// back
	if (u === 3 || u === 4) return v <= 3 ? (u === 3 ? C.B1 : C.B2) : C.W3;
	if (v === 5 || v === 6) return (u === 1 || u === 6) ? C.G2 : C.W2; // two cooling vents
	if (v === 7) return C.W3;
	return u === 0 || u === 7 ? C.W2 : C.W1;
}
function helmetOuter(face, u, v) {
	if (face === 'front') {
		if (v === 2) return u === 3 || u === 4 ? C.B1 : C.W0; // the brow ridge over the visor
		if (v === 3 && (u === 0 || u === 7)) return C.W2; // the visor frame
		if (v === 4 && (u === 0 || u === 7)) return C.W3;
		return null;
	}
	if (face === 'top') return (u === 3 || u === 4) ? (v >= 2 ? C.B0 : C.B1) : null; // a raised crest fin
	if (face === 'right' || face === 'left') {
		const fwd = face === 'right' ? u : 7 - u;
		// the swept-back blue fins over the ear housings
		if (v === 2 && fwd >= 1 && fwd <= 5) return fwd === 5 ? C.B1 : C.B2;
		if (v === 3 && fwd >= 0 && fwd <= 2) return C.B3;
		if (fwd === 7 && v === 2) return C.W0;
		return null;
	}
	if (face === 'back') return (u === 3 || u === 4) && v <= 1 ? C.B1 : null;
	return null;
}

// ---------------------------------------------------------------- the torso
function torso(face, u, v, fw) {
	if (face === 'top') return (u === 0 || u === 7) ? C.W2 : (v === 0 ? C.W2 : C.W1);
	if (face === 'bottom') return C.G2;
	if (v === 10) return face === 'front' && (u === 3 || u === 4) ? (u === 3 ? C.B0 : C.B1) : C.G3; // the belt + buckle
	if (v === 11) return C.W3;
	if (face === 'front') {
		if (v === 0) return u === 0 || u === 7 ? C.W3 : C.W2; // the collar
		if (v <= 4) { // the chest plate, split down the middle
			if (u === 3) return v === 1 ? C.W1 : C.W2;
			if (u === 0 || u === 7) return C.W2;
			return v === 1 ? C.W0 : C.W1;
		}
		if (v === 5) return C.B2; // blue piping under the chest
		if (v === 6) return u === 0 || u === 7 ? C.B3 : C.B1;
		// the ab plates
		if (u === 0 || u === 7) return C.G2;
		if (u === 3 || u === 4) return u === 3 ? C.W3 : C.W4;
		if (v === 8) return C.W4;
		return v === 7 ? C.W2 : C.W3;
	}
	if (face === 'back') {
		if (v === 0) return C.W3;
		if (u === 3 || u === 4) return v % 3 === 2 ? C.G1 : (u === 3 ? C.W3 : C.W4); // the spine plates
		if (v >= 2 && v <= 3 && (u === 1 || u === 6)) return C.B2; // twin blue vents
		if (v >= 7) return u === 0 || u === 7 ? C.W3 : C.W2;
		return u === 0 || u === 7 ? C.W2 : C.W1;
	}
	// the sides: a blue panel down the flank
	if (u === 1 || u === 2) return v >= 1 && v <= 8 ? (u === 1 ? C.B1 : C.B2) : C.W3;
	return C.W2;
}
function torsoOuter(face, u, v) {
	if (face === 'front') {
		if (v >= 1 && v <= 4 && u >= 1 && u <= 6 && u !== 3 && u !== 4) return v === 1 ? C.W0 : (v === 4 ? C.W2 : C.W1); // raised pecs
		if (v === 5 && u >= 1 && u <= 6) return C.B1;
		return null;
	}
	if (face === 'back') {
		if (v >= 1 && v <= 6 && u >= 1 && u <= 6 && u !== 3 && u !== 4) return v === 1 ? C.W0 : C.W1; // the back plate
		return null;
	}
	if (face === 'top') return (u === 0 || u === 1 || u === 6 || u === 7) ? C.W0 : null; // shoulder ridge
	return null;
}

// ---------------------------------------------------------------- the arms
function arm(face, u, v, fw) {
	if (face === 'top') return C.W1;
	if (face === 'bottom') return C.W4;
	if (v <= 3) return v === 0 ? C.W0 : C.W1;          // the shoulder
	if (v === 4) return C.B2;                           // blue band
	if (v === 5) return C.W2;
	if (v === 6) return C.G2;                           // the elbow joint
	if (v <= 8) return face === 'front' && v === 7 ? C.W0 : C.W1; // the forearm guard
	if (v === 9) return C.B1;                           // blue wrist band
	if (v === 10) return C.W3;                          // the glove
	return face === 'front' ? (u % 2 === 0 ? C.W4 : C.W3) : C.W4;
}
function armOuter(face, u, v, fw) {
	if (face === 'top') return C.W0;
	if (face === 'bottom') return null;
	if (v <= 2) return v === 0 ? C.W0 : (v === 2 ? C.W2 : C.W1); // the shoulder pad
	if (v === 7 || v === 8) { // the forearm gauntlet, a blue stripe down the outside
		const outside = face === 'right' || face === 'left';
		if (outside && (u === 1 || u === 2)) return C.B1;
		return v === 7 ? C.W0 : C.W1;
	}
	return null;
}

// ---------------------------------------------------------------- the legs
// side: which way the right / left face points ('outer' | 'inner') is not needed -- both carry the pipe housing
function leg(face, u, v) {
	if (face === 'top') return C.W2;
	if (face === 'bottom') return C.G4;                 // the sole
	if (v === 0) return C.G2;                           // under-suit at the hip
	if (v <= 2) return face === 'front' ? C.W1 : C.W2;  // the thigh
	if (v === 3) return C.W3;
	if (v === 4) return face === 'front' ? C.W0 : C.W2; // the knee
	if (v === 5) return face === 'front' ? C.B2 : C.W3;
	if (v === 11) return u === 0 || u === 3 ? C.G3 : C.B3; // the boot sole, blue trim
	if (v === 10) return C.W3;
	// the calf (the engine block): the back carries the pipe housing
	if (face === 'back') return v === 7 ? C.G2 : C.G3;
	return v === 6 ? C.W0 : C.W1;
}
function legOuter(face, u, v) {
	if (face === 'top' || face === 'bottom') return null;
	if (face === 'front') {
		if (v === 3) return C.W1;
		if (v === 4) return C.W0; // the knee guard
		if (v === 5) return C.B1;
		if (v >= 6 && v <= 9) return v === 6 ? C.W0 : C.W1; // the shin guard
		return null;
	}
	if (face === 'back') {
		// the exhaust pipes: two rows of two, metal rims over a blue-white glow
		if (v === 5 || v === 8) return u % 2 === 0 ? C.G1 : C.G2;
		if (v === 6 || v === 9) return u % 2 === 0 ? C.GLOW : C.B1;
		if (v === 7) return C.W3;
		if (v === 4 || v === 10) return C.W2;
		return null;
	}
	// right / left: the engine housing round the calf, the pipe ends on the back edge
	const back = face === 'right' ? u === 0 : u === 3;
	if (v >= 4 && v <= 10) {
		if (back && (v === 5 || v === 6 || v === 8 || v === 9)) return v === 6 || v === 9 ? C.B1 : C.G1;
		if (v === 4 || v === 10) return C.W2;
		if (v === 7) return C.B2; // a blue seam round the housing
		return v === 5 ? C.W0 : C.W1;
	}
	return null;
}

// ---------------------------------------------------------------- build
paintBox(0, 0, 8, 8, 8, helmet);
paintBox(32, 0, 8, 8, 8, helmetOuter, { flat: true });
paintBox(16, 16, 8, 12, 4, torso);
paintBox(16, 32, 8, 12, 4, torsoOuter, { flat: true });
paintBox(40, 16, 4, 12, 4, arm);              // right arm
paintBox(40, 32, 4, 12, 4, armOuter, { flat: true });
paintBox(32, 48, 4, 12, 4, arm);              // left arm
paintBox(48, 48, 4, 12, 4, armOuter, { flat: true });
paintBox(0, 16, 4, 12, 4, leg);               // right leg
paintBox(0, 32, 4, 12, 4, legOuter, { flat: true });
paintBox(16, 48, 4, 12, 4, leg);              // left leg
paintBox(0, 48, 4, 12, 4, legOuter, { flat: true });

const outDir = process.argv[2];
const wide = { w: W, h: H, px };
const slim = convertArms(wide, true);
fs.writeFileSync(path.join(outDir, 'engine_wide.png'), encode(W, H, wide.px));
fs.writeFileSync(path.join(outDir, 'engine_slim.png'), encode(W, H, slim.px));

// ---------------------------------------------------------------- contact sheet: front / back / right / left + the raw skin
function sheet(img, slimArms) {
	const S = 8, a = slimArms ? 3 : 4;
	const get = (x, y) => { const i = (y * W + x) * 4; return [img.px[i], img.px[i + 1], img.px[i + 2], img.px[i + 3]]; };
	const views = 4, vw = 18, vh = 34, pad = 2;
	const SW = (views * (vw + pad) + pad) * S + 64 * 6 + 24, SH = Math.max((vh + 2 * pad) * S, 64 * 6 + 16);
	const out = Buffer.alloc(SW * SH * 4);
	for (let i = 0; i < SW * SH; i++) { out[i * 4] = 44; out[i * 4 + 1] = 48; out[i * 4 + 2] = 56; out[i * 4 + 3] = 255; }
	const plot = (X, Y, c, s) => {
		if (c[3] === 0) return;
		for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
			const d = ((Y + y) * SW + X + x) * 4; out[d] = c[0]; out[d + 1] = c[1]; out[d + 2] = c[2]; out[d + 3] = 255;
		}
	};
	// draw a face rect from the texture at (gx, gy) in view units, overlay on top
	const blit = (vx, vy, rect, over, mirror) => {
		for (const r of [rect, over]) {
			if (!r) continue;
			const [fx, fy, fw, fh] = r;
			for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
				const sx = mirror ? fx + fw - 1 - x : fx + x;
				plot((vx + x) * S, (vy + y) * S, get(sx, fy + y), S);
			}
		}
	};
	const F = (ox, oy, w, h, d) => faces(ox, oy, w, h, d);
	const head = F(0, 0, 8, 8, 8), hat = F(32, 0, 8, 8, 8), body = F(16, 16, 8, 12, 4), jacket = F(16, 32, 8, 12, 4);
	const ra = F(40, 16, a, 12, 4), rs = F(40, 32, a, 12, 4), la = F(32, 48, a, 12, 4), ls = F(48, 48, a, 12, 4);
	const rl = F(0, 16, 4, 12, 4), rp = F(0, 32, 4, 12, 4), ll = F(16, 48, 4, 12, 4), lp = F(0, 48, 4, 12, 4);
	for (let view = 0; view < 4; view++) {
		const bx = pad + view * (vw + pad), by = pad;
		if (view === 0 || view === 1) {
			const k = view === 0 ? 'front' : 'back';
			const cx = bx + 1;
			// front: the player's right is on the viewer's left; back: mirrored
			const left = view === 0 ? [ra, rs] : [la, ls], right = view === 0 ? [la, ls] : [ra, rs];
			const legL = view === 0 ? [rl, rp] : [ll, lp], legR = view === 0 ? [ll, lp] : [rl, rp];
			blit(cx + a, by, head[k], hat[k]);
			blit(cx + a, by + 8, body[k], jacket[k]);
			blit(cx, by + 8, left[0][k], left[1][k]);
			blit(cx + a + 8, by + 8, right[0][k], right[1][k]);
			blit(cx + a, by + 20, legL[0][k], legL[1][k]);
			blit(cx + a + 4, by + 20, legR[0][k], legR[1][k]);
		} else {
			const k = view === 2 ? 'right' : 'left';
			const cx = bx + 5;
			blit(cx, by, head[k], hat[k]);
			blit(cx + 2, by + 8, (view === 2 ? ra : la)[k], (view === 2 ? rs : ls)[k]);
			blit(cx + 2, by + 20, (view === 2 ? rl : ll)[k], (view === 2 ? rp : lp)[k]);
		}
	}
	// the raw skin at 6x on the right
	const rx = (views * (vw + pad) + pad) * S + 12;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = get(x, y);
		const chk = ((x + y) & 1) ? [70, 74, 82, 255] : [58, 62, 70, 255];
		plot(rx + x * 6, 8 + y * 6, c[3] ? c : chk, 6);
	}
	return { w: SW, h: SH, px: out };
}
if (process.argv[3]) {
	const a = sheet(wide, false), b = sheet(slim, true);
	const both = { w: a.w, h: a.h + b.h, px: Buffer.concat([a.px, b.px]) };
	fs.writeFileSync(process.argv[3], encode(both.w, both.h, both.px));
}
console.log('wrote engine_wide.png / engine_slim.png');
