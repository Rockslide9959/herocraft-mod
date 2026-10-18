// v0.16.7: Endeavor's hero costume for the Hellflame Quirk -- 64x64 player-skin layout, the costume only (the head is
// left transparent: his face and hair show). Writes hellflame_wide.png / hellflame_slim.png and preview sheets.
// Usage: node scratchpad/gen_hellflame_skin.js <outDir for previews>
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');

const W = 64, H = 64;
const img = { w: W, h: H, px: Buffer.alloc(W * H * 4) };

// ---------------------------------------------------------------- palette
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const NAVY = ['#0c1020', '#141b33', '#1c2647', '#27345e', '#34447a'].map(hex);      // deep .. highlight
const BOOT = ['#0e0f14', '#191b23', '#252833', '#323645'].map(hex);
const STEEL = ['#3f4652', '#5d6673', '#828c99', '#aab3bf', '#d6dde5'].map(hex);
const FLAME = ['#7a1408', '#b5260f', '#e2521a', '#f88a24', '#ffc24a', '#fff0b0'].map(hex); // ember .. white-hot
const LEATHER = ['#2a1e17', '#3d2c21', '#54402f'].map(hex);

function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function shade(c, f) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * f)))); }
function put(x, y, c, a = 255) { const i = (y * W + x) * 4; img.px[i] = c[0]; img.px[i + 1] = c[1]; img.px[i + 2] = c[2]; img.px[i + 3] = a; }
function get(x, y) { const i = (y * W + x) * 4; return img.px[i + 3] ? [img.px[i], img.px[i + 1], img.px[i + 2]] : null; }
/** fabric: a palette index with a little per-texel grain */
function fabric(pal, i, x, y, seed = 1) { const n = hash(x, y, seed); const c = pal[Math.max(0, Math.min(pal.length - 1, i))]; return shade(c, 0.94 + n * 0.12); }

// the standard box unwrap: faces as [x, y, w, h] for top, bottom, right(+x side of the texture = the model's right), front, left, back
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
/** paint every texel of a face with fn(u, v, fw, fh) -> colour | null */
function paint(face, fn) { const [fx, fy, fw, fh] = face; for (let v = 0; v < fh; v++) for (let u = 0; u < fw; u++) { const c = fn(u, v, fw, fh); if (c) put(fx + u, fy + v, c.slice(0, 3), c[3] === undefined ? 255 : c[3]); } }

// a flame tongue profile: for column t (0..1 across the band) the flame reaches this many texels up from its base
function flameHeight(col, seed, max) { const a = hash(col, 7, seed), b = hash(col + 1, 7, seed); return Math.round(max * (0.35 + 0.65 * Math.max(a, b * 0.8))); }
function flameColour(depth, height) { // depth: texels below the tip (0 = tip)
	if (depth <= 0) return FLAME[4];
	if (depth === 1) return FLAME[3];
	if (depth < height * 0.6) return FLAME[2];
	return FLAME[1];
}

// ---------------------------------------------------------------- BODY (base layer)
const body = faces(16, 16, 8, 12, 4);
const BELT_Y = 9; // rows 9-10 the belt, 11 the hips
function torso(u, v, fw, side, seed) {
	// the belt
	if (v === BELT_Y || v === BELT_Y + 1) {
		const edge = v === BELT_Y ? 3 : 1;
		return fabric(STEEL, edge, u, v, seed);
	}
	if (v === 11) return fabric(NAVY, 1, u, v, seed);
	// flame tongues rising from the belt on the sides
	if (side) {
		const h = flameHeight(u, seed, 6);
		const depth = (BELT_Y - 1) - v; // 0 right above the belt
		if (depth < h) return flameColour(h - 1 - depth, h);
	}
	return null;
}
paint(body.front, (u, v) => {
	const t = torso(u, v, 8, false, 3); if (t) return t;
	// the collar: a dark band with the orange flame trim in a V
	if (v === 0) return (u === 3 || u === 4) ? FLAME[2] : fabric(NAVY, 0, u, v, 3);
	if (v === 1 && (u === 2 || u === 5)) return FLAME[2];
	if (v === 1 && (u === 3 || u === 4)) return fabric(NAVY, 1, u, v, 3);
	// flame tongues rising from the belt up both flanks of the abdomen
	{
		const FH = [5, 3, 1, 0, 0, 1, 3, 5][u];
		const depth = (BELT_Y - 1) - v;
		if (FH > 0 && depth < FH) return flameColour(FH - 1 - depth, FH);
	}
	// pecs (highlight at the top, shadow line under), the centre seam, the abs
	if (u === 3 || u === 4) { if (v >= 2 && v <= 8) return fabric(NAVY, v <= 4 ? 1 : 1, u, v, 3); }
	if (v === 2) return fabric(NAVY, 3, u, v, 3);
	if (v === 3) return fabric(NAVY, 2, u, v, 3);
	if (v === 4) return fabric(NAVY, (u === 1 || u === 6) ? 1 : 2, u, v, 3);
	if (v === 5) return fabric(NAVY, 1, u, v, 3);
	if (v >= 6 && v <= 8) return fabric(NAVY, (v === 7 ? 1 : 2) + ((u === 2 || u === 5) ? 1 : 0), u, v, 3);
	return fabric(NAVY, 2, u, v, 3);
});
paint(body.back, (u, v) => {
	const t = torso(u, v, 8, false, 5); if (t) return t;
	if (v === 0) return fabric(NAVY, 0, u, v, 5);
	if (u === 3 || u === 4) return fabric(NAVY, 1, u, v, 5); // spine channel
	if ((v === 2 || v === 3) && (u === 1 || u === 2 || u === 5 || u === 6)) return fabric(NAVY, 3, u, v, 5); // shoulder blades
	{ const FH = [4, 2, 0, 0, 0, 0, 2, 4][u]; const depth = (BELT_Y - 1) - v; if (FH > 0 && depth < FH) return flameColour(FH - 1 - depth, FH); }
	return fabric(NAVY, 2, u, v, 5);
});
for (const [k, seed] of [['right', 7], ['left', 9]]) {
	paint(body[k], (u, v) => {
		const t = torso(u, v, 4, true, seed); if (t) return t;
		if (v === 0) return fabric(NAVY, 0, u, v, seed);
		return fabric(NAVY, u === 0 || u === 3 ? 1 : 2, u, v, seed);
	});
}
paint(body.top, (u, v) => {
	// shoulders and the collar ring round the neck (orange trim at the front edge)
	if (v === 3 && u >= 2 && u <= 5) return FLAME[2];
	if (u >= 2 && u <= 5) return fabric(NAVY, 0, u, v, 11);
	return fabric(NAVY, 3, u, v, 11);
});
paint(body.bottom, (u, v) => fabric(NAVY, 1, u, v, 13));

// ---------------------------------------------------------------- BODY overlay (jacket): the raised belt, buckle, pouches, high collar
const jacket = faces(16, 32, 8, 12, 4);
function beltOver(u, v, fw, face) {
	if (v === BELT_Y) return fabric(STEEL, 3, u, v, 21);
	if (v === BELT_Y + 1) return fabric(STEEL, 1, u, v, 21);
	return null;
}
paint(jacket.front, (u, v) => {
	// the buckle: a polished plate with a flame-orange centre
	if ((v === BELT_Y || v === BELT_Y + 1) && (u === 3 || u === 4)) return v === BELT_Y ? STEEL[4] : FLAME[3];
	if ((v === BELT_Y || v === BELT_Y + 1) && (u === 2 || u === 5)) return STEEL[2];
	const b = beltOver(u, v, 8, 'front'); if (b) return b;
	// the high collar: a dark rim with an orange edge
	if (v === 0 && (u <= 1 || u >= 6)) return fabric(NAVY, 1, u, v, 23);
	return null;
});
paint(jacket.back, (u, v) => {
	const b = beltOver(u, v, 8, 'back'); if (b) return (u === 3 || u === 4) && v === BELT_Y ? STEEL[2] : b;
	if (v === 0) return fabric(NAVY, 1, u, v, 25);
	return null;
});
for (const [k, seed] of [['right', 27], ['left', 29]]) {
	paint(jacket[k], (u, v) => {
		// a leather pouch on each hip
		if (v === BELT_Y - 1 && u >= 1 && u <= 2) return LEATHER[2];
		if ((v === BELT_Y || v === BELT_Y + 1) && u >= 1 && u <= 2) return v === BELT_Y ? LEATHER[1] : LEATHER[0];
		const b = beltOver(u, v, 4, k); if (b) return b;
		if (v === 0) return fabric(NAVY, 1, u, v, seed);
		return null;
	});
}
paint(jacket.top, (u, v) => (u <= 1 || u >= 6) && v >= 1 && v <= 2 ? null : (v === 0 || v === 3) && (u >= 1 && u <= 6) ? fabric(NAVY, 1, u, v, 31) : null);
paint(jacket.bottom, (u, v) => null);

// ---------------------------------------------------------------- ARMS (wide, 4 px): base + sleeve
function armBase(ox, oy, outerKey, seed) {
	const f = faces(ox, oy, 4, 12, 4);
	const fn = key => (u, v) => {
		// the glove: rows 10-11, dark, knuckle highlight on the front
		if (v >= 10) return fabric(NAVY, key === 'front' && v === 10 ? 2 : 0, u, v, seed);
		if (v === 9) return fabric(NAVY, 1, u, v, seed); // glove cuff
		// a flame flick up the outer side of the forearm
		if (key === outerKey && v >= 6 && v <= 8) { const h = flameHeight(u, seed, 3); if (8 - v < h) return flameColour(h - 1 - (8 - v), h); }
		if (v === 5) return fabric(NAVY, 1, u, v, seed); // the elbow crease
		if (key === outerKey && u === 1 && v >= 3 && v <= 4) return FLAME[1]; // red piping down the outer seam
		if (v <= 1) return fabric(NAVY, 3, u, v, seed); // shoulder highlight
		return fabric(NAVY, key === 'back' ? 1 : 2, u, v, seed);
	};
	for (const k of ['front', 'back', 'right', 'left']) paint(f[k], fn(k));
	paint(f.top, (u, v) => fabric(NAVY, 3, u, v, seed));
	paint(f.bottom, (u, v) => fabric(NAVY, 0, u, v, seed));
}
function armSleeve(ox, oy, outerKey, seed) {
	const f = faces(ox, oy, 4, 12, 4);
	const fn = key => (u, v, fw) => {
		// the shoulder guard: rows 0-2, steel, an orange edge underneath
		if (v === 0) return fabric(STEEL, 3, u, v, seed);
		if (v === 1) return fabric(STEEL, key === outerKey ? 2 : 2, u, v, seed);
		if (v === 2) return key === 'right' || key === 'left' || key === 'front' || key === 'back' ? FLAME[2] : null;
		// the forearm guard (vambrace): rows 6-8, steel with orange vents on the outer side
		if (v >= 6 && v <= 8) {
			if (key === outerKey && v === 7 && (u === 1 || u === 2)) return FLAME[3];
			return fabric(STEEL, v === 6 ? 3 : v === 7 ? 2 : 1, u, v, seed);
		}
		return null;
	};
	for (const k of ['front', 'back', 'right', 'left']) paint(f[k], fn(k));
	paint(f.top, (u, v) => fabric(STEEL, (u + v) % 3 === 0 ? 4 : 3, u, v, seed));
	paint(f.bottom, () => null);
}
armBase(40, 16, 'right', 41);
armSleeve(40, 32, 'right', 43);
armBase(32, 48, 'left', 45);
armSleeve(48, 48, 'left', 47);

// ---------------------------------------------------------------- LEGS: base + pants
function legBase(ox, oy, outerKey, seed) {
	const f = faces(ox, oy, 4, 12, 4);
	const fn = key => (u, v) => {
		if (v === 11) return fabric(BOOT, 0, u, v, seed); // the sole
		if (v >= 7) {
			// the boot, flames licking up from it on the outer side
			if (v === 9) return fabric(STEEL, 1, u, v, seed); // the ankle strap
			return fabric(BOOT, v === 7 ? 3 : v === 10 ? 1 : 2, u, v, seed);
		}
		if (key === outerKey || key === 'front') {
			const h = key === 'front' ? [3, 1, 2, 4][u] : flameHeight(u, seed, 4);
			const depth = 6 - v;
			if (depth < h) return flameColour(h - 1 - depth, h);
		}
		if (v === 0) return fabric(NAVY, 1, u, v, seed);
		if (key === 'front' && (v === 4)) return fabric(NAVY, 3, u, v, seed); // knee highlight
		return fabric(NAVY, key === 'back' ? 1 : 2, u, v, seed);
	};
	for (const k of ['front', 'back', 'right', 'left']) paint(f[k], fn(k));
	paint(f.top, (u, v) => fabric(NAVY, 1, u, v, seed));
	paint(f.bottom, (u, v) => fabric(BOOT, 0, u, v, seed));
}
function legPants(ox, oy, outerKey, seed) {
	const f = faces(ox, oy, 4, 12, 4);
	const fn = key => (u, v) => {
		if (v === 7) return fabric(BOOT, 3, u, v, seed);           // the boot cuff rim
		if (key === 'front' && v === 10) return fabric(STEEL, 2, u, v, seed); // toe cap
		return null;
	};
	for (const k of ['front', 'back', 'right', 'left']) paint(f[k], fn(k));
	paint(f.top, () => null);
	paint(f.bottom, () => null);
}
legBase(0, 16, 'right', 51);
legPants(0, 32, 'right', 53);
legBase(16, 48, 'left', 55);
legPants(0, 48, 'left', 57);

// ---------------------------------------------------------------- write
const outTex = 'src/main/resources/assets/projecthero/textures/entity/quirk';
fs.mkdirSync(outTex, { recursive: true });
fs.writeFileSync(path.join(outTex, 'hellflame_wide.png'), T.encode(W, H, img.px));
const slim = T.convertArms(img, true);
fs.writeFileSync(path.join(outTex, 'hellflame_slim.png'), T.encode(W, H, slim.px));

// ---------------------------------------------------------------- previews: the raw sheets + an assembled front / back / side view
const prevDir = process.argv[2];
if (prevDir) {
	fs.mkdirSync(prevDir, { recursive: true });
	for (const [name, im] of [['wide', img], ['slim', slim]]) {
		const p = T.preview(im, 10);
		fs.writeFileSync(path.join(prevDir, `hellflame_${name}_sheet.png`), T.encode(p.w, p.h, p.px));
	}
	// a flat "standing figure" view: base face with the overlay face on top
	function compose(im, slimArms) {
		const a = slimArms ? 3 : 4;
		const S = 12, FW = 4 * 16 + 3 * 4, FH = 32;
		const views = [
			// [label, list of [srcFace(base), srcFace(overlay), dx, dy]]
			['front', [[faces(16, 16, 8, 12, 4).front, faces(16, 32, 8, 12, 4).front, 4, 8],
				[faces(40, 16, a, 12, 4).front, faces(40, 32, a, 12, 4).front, 4 - a, 8],
				[faces(32, 48, a, 12, 4).front, faces(48, 48, a, 12, 4).front, 12, 8],
				[faces(0, 16, 4, 12, 4).front, faces(0, 32, 4, 12, 4).front, 4, 20],
				[faces(16, 48, 4, 12, 4).front, faces(0, 48, 4, 12, 4).front, 8, 20]]],
			['back', [[faces(16, 16, 8, 12, 4).back, faces(16, 32, 8, 12, 4).back, 4, 8],
				[faces(32, 48, a, 12, 4).back, faces(48, 48, a, 12, 4).back, 4 - a, 8],
				[faces(40, 16, a, 12, 4).back, faces(40, 32, a, 12, 4).back, 12, 8],
				[faces(16, 48, 4, 12, 4).back, faces(0, 48, 4, 12, 4).back, 4, 20],
				[faces(0, 16, 4, 12, 4).back, faces(0, 32, 4, 12, 4).back, 8, 20]]],
			['right side', [[faces(16, 16, 8, 12, 4).right, faces(16, 32, 8, 12, 4).right, 4, 8],
				[faces(40, 16, a, 12, 4).right, faces(40, 32, a, 12, 4).right, 4, 8],
				[faces(0, 16, 4, 12, 4).right, faces(0, 32, 4, 12, 4).right, 4, 20]]],
			['left side', [[faces(16, 16, 8, 12, 4).left, faces(16, 32, 8, 12, 4).left, 4, 8],
				[faces(32, 48, a, 12, 4).left, faces(48, 48, a, 12, 4).left, 4, 8],
				[faces(16, 48, 4, 12, 4).left, faces(0, 48, 4, 12, 4).left, 4, 20]]],
		];
		const cw = 16, ch = 32, gap = 2;
		const OW = (cw * views.length + gap * (views.length + 1)) * S, OH = (ch + 2 * gap) * S;
		const out = Buffer.alloc(OW * OH * 4);
		for (let i = 0; i < OW * OH; i++) { out[i * 4] = 70; out[i * 4 + 1] = 74; out[i * 4 + 2] = 80; out[i * 4 + 3] = 255; }
		views.forEach(([, parts], vi) => {
			const ox = (gap + vi * (cw + gap)) * S, oy = gap * S;
			// the skin-coloured head stand-in so the costume reads in context
			for (let y = 0; y < 8; y++) for (let x = 4; x < 12; x++) for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) {
				const d = ((oy + y * S + yy) * OW + ox + x * S + xx) * 4; out[d] = 196; out[d + 1] = 150; out[d + 2] = 118;
			}
			for (const [bf, of, dx, dy] of parts) {
				for (let v = 0; v < bf[3]; v++) for (let u = 0; u < bf[2]; u++) {
					let s = ((bf[1] + v) * W + bf[0] + u) * 4;
					const so = ((of[1] + v) * W + of[0] + u) * 4;
					const src = im.px[so + 3] ? so : s;
					if (!im.px[src + 3]) continue;
					for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) {
						const d = ((oy + (dy + v) * S + yy) * OW + ox + (dx + u) * S + xx) * 4;
						out[d] = im.px[src]; out[d + 1] = im.px[src + 1]; out[d + 2] = im.px[src + 2];
					}
				}
			}
		});
		return { w: OW, h: OH, px: out };
	}
	for (const [name, im, s] of [['wide', img, false], ['slim', slim, true]]) {
		const c = compose(im, s);
		fs.writeFileSync(path.join(prevDir, `hellflame_${name}_views.png`), T.encode(c.w, c.h, c.px));
	}
}
// gap check: every base-layer body / arm / leg face fully opaque
function gaps(im, slimArms) {
	const a = slimArms ? 3 : 4; let n = 0;
	const boxes = [[16, 16, 8], [40, 16, a], [32, 48, a], [0, 16, 4], [16, 48, 4]];
	for (const [ox, oy, w] of boxes) for (const [fx, fy, fw, fh] of Object.values(faces(ox, oy, w, 12, 4)))
		for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) if (!im.px[(y * W + x) * 4 + 3]) n++;
	return n;
}
console.log('gaps wide', gaps(img, false), 'slim', gaps(slim, true));
