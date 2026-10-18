// v0.16.7: the Overhaul (Kai Chisaki) costume skin -- 64x64 player layout, COSTUME ONLY (his eyes / forehead are the
// wearer's own skin). Dark green-black bomber jacket with a thick fur collar, a purple shirt and dark tie in the open
// front, dark purple leather gloves, dark trousers and shoes, swept-back dark hair, and the white plague-doctor beak
// mask on the face with the hat layer giving it depth.
// Usage: node gen_overhaul_skin.js <outDir> <previewDir>
'use strict';
const fs = require('fs');
const path = require('path');
const tools = require(process.env.SKIN_TOOLS || 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');

const W = 64, H = 64;
const px = Buffer.alloc(W * H * 4);
let seed = 1337;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const hash = (x, y, k = 0) => { let h = (x * 374761393 + y * 668265263 + k * 1442695041) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };

const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const P = {
	jacket: ['#3a4a3c', '#2c392e', '#202a22', '#151c17'].map(hex),
	fur: ['#6e7568', '#535a4f', '#3c4239', '#272c25', '#888f80'].map(hex),
	shirt: ['#6a3f7a', '#55305f', '#3f2347', '#2b1731'].map(hex),
	tie: ['#2a2a30', '#1d1d22', '#121216'].map(hex),
	glove: ['#4a2f52', '#3a2440', '#2a1a2f', '#1b1020'].map(hex),
	pants: ['#2f3236', '#24272a', '#1a1c1f', '#111214'].map(hex),
	shoe: ['#2a2420', '#1d1915', '#120f0c'].map(hex),
	hair: ['#4a3a30', '#3a2c24', '#2a1f19', '#1b1410'].map(hex),
	mask: ['#f6f3ec', '#e3ded3', '#c9c2b4', '#a39b8c', '#7b7468'].map(hex),
	strap: ['#3a2440', '#24162a'].map(hex),
	rib: ['#1c241e', '#131915'].map(hex),
	zip: ['#8b8f88', '#5d615b'].map(hex),
};

function set(x, y, c, a = 255) {
	if (x < 0 || y < 0 || x >= W || y >= H) return;
	const i = (y * W + x) * 4;
	px[i] = Math.max(0, Math.min(255, Math.round(c[0])));
	px[i + 1] = Math.max(0, Math.min(255, Math.round(c[1])));
	px[i + 2] = Math.max(0, Math.min(255, Math.round(c[2])));
	px[i + 3] = a;
}
const shade = (c, f) => [c[0] * f, c[1] * f, c[2] * f];
const pick = (pal, t) => pal[Math.max(0, Math.min(pal.length - 1, Math.round(t)))];

// face brightness: top lightest, front, sides, back darkest (keeps every cube readable in-game)
const FACE_LIGHT = { top: 1.08, front: 1.0, right: 0.9, left: 0.9, back: 0.84, bottom: 0.72 };

/** Cube faces for an origin (ox, oy) and size (w, h, d) in the standard unwrap. */
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + d + w + d, oy + d, w, h],
	};
}
function paint(f, fn) {
	for (const name of Object.keys(f)) {
		const [x0, y0, w, h] = f[name];
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const r = fn(name, x, y, w, h, x0 + x, y0 + y);
			if (r) set(x0 + x, y0 + y, shade(r[0], FACE_LIGHT[name] * (r[2] || 1)), r[1] === undefined ? 255 : r[1]);
		}
	}
}
// fabric: a soft two-shade noise with seams
const fabric = (pal, gx, gy, base = 1) => pick(pal, base + (hash(gx, gy, 3) < 0.22 ? 1 : 0) - (hash(gx, gy, 5) < 0.12 ? 1 : 0));
const furTex = (gx, gy) => { const h = hash(gx, gy, 9); return h < 0.12 ? P.fur[4] : h < 0.4 ? P.fur[0] : h < 0.75 ? P.fur[1] : h < 0.93 ? P.fur[2] : P.fur[3]; };

// ================================================================ HEAD (base 0,0 / hat 32,0) -- 8x8x8
const HEAD = faces(0, 0, 8, 8, 8), HAT = faces(32, 0, 8, 8, 8);
paint(HEAD, (face, x, y, w, h, gx, gy) => {
	const hair = c => [c, 255, 1];
	if (face === 'top') return hair(pick(P.hair, 1 + (hash(gx, gy, 1) < 0.3 ? 1 : 0) - (y < 2 && hash(gx, gy, 2) < 0.4 ? 1 : 0)));
	if (face === 'bottom') {
		return null; // the wearer's own skin
	}
	if (face === 'front') {
		if (y <= 1) return hair(pick(P.hair, y === 1 && (x === 2 || x === 5) ? 2 : 1)); // hairline, swept back
		if (y <= 3) return null; // forehead + brows: his own skin
		if (y === 4) return (x === 0 || x === 7) ? [P.mask[2]] : null; // the eyes show
		// the mask, nose down: the beak ridge lighter in the middle
		const ridge = x === 3 || x === 4;
		if (y === 5) return [ridge ? P.mask[0] : (x === 0 || x === 7) ? P.mask[3] : P.mask[1]];
		if (y === 6) return [ridge ? P.mask[1] : (x === 0 || x === 7) ? P.mask[3] : P.mask[2]];
		return [ridge ? P.mask[2] : (x <= 1 || x >= 6) ? P.mask[4] : P.mask[3]];
	}
	if (face === 'right' || face === 'left') {
		// the front of the head is on the inner edge: right face x=7 / left face x=0
		const fromFront = face === 'right' ? 7 - x : x;
		if (y <= 3) return hair(pick(P.hair, y === 3 && fromFront > 4 ? 2 : 1 + (hash(gx, gy, 4) < 0.3 ? 1 : 0)));
		if (y === 5) return [fromFront <= 2 ? P.mask[2] : P.strap[0]]; // the mask's strap round to the back
		if (y >= 6 && fromFront <= 2) return [P.mask[fromFront === 2 ? 4 : 3]];
		if (fromFront >= 6 && y <= 6) return hair(P.hair[2]); // hair behind the ear
		return null;
	}
	if (face === 'back') {
		if (y === 5) return [P.strap[(x === 3 || x === 4) ? 1 : 0]]; // the strap's buckle
		if (y <= 6) return hair(pick(P.hair, 1 + (y >= 5 ? 1 : 0) + (hash(gx, gy, 6) < 0.25 ? 1 : 0)));
		return null;
	}
	return null;
});
paint(HAT, (face, x, y, w, h, gx, gy) => {
	// the beak mask standing off the face; a little hair volume on top and behind
	if (face === 'front') {
		// the beak: an outlined plate over the nose and mouth, a bright ridge down the middle, pointed at the bottom
		const ROWS = { 4: [2, [3, 1, 1, 3]], 5: [1, [4, 2, 0, 0, 2, 4]], 6: [1, [4, 2, 1, 1, 2, 4]], 7: [2, [4, 1, 0, 4]] };
		const r = ROWS[y];
		if (r && x >= r[0] && x < r[0] + r[1].length) return [P.mask[r[1][x - r[0]]]];
		if (y === 0 && hash(gx, gy, 7) < 0.5) return [P.hair[0]]; // fringe strands
		return null;
	}
	if (face === 'bottom') return null;
	if (face === 'right' || face === 'left') {
		const fromFront = face === 'right' ? 7 - x : x;
		if (y >= 5 && y <= 6 && fromFront <= 1) return [P.mask[2 + fromFront]];
		if (y === 5 && fromFront >= 2) return [P.strap[0]];
		if (y <= 1 && hash(gx, gy, 8) < 0.35) return [P.hair[1]];
		return null;
	}
	if (face === 'top') return hash(gx, gy, 10) < 0.45 ? [pick(P.hair, hash(gx, gy, 11) < 0.5 ? 0 : 1)] : null;
	if (face === 'back') {
		if (y === 5 && x >= 2 && x <= 5) return [P.strap[1]];
		return y <= 3 && hash(gx, gy, 12) < 0.4 ? [P.hair[1]] : null;
	}
	return null;
});

// ================================================================ BODY (base 16,16 / jacket 16,32) -- 8x12x4
const BODY = faces(16, 16, 8, 12, 4), JACKET = faces(16, 32, 8, 12, 4);
paint(BODY, (face, x, y, w, h, gx, gy) => {
	if (face === 'top') return [furTex(gx, gy)];
	if (face === 'bottom') return [P.rib[0]];
	if (face === 'front') {
		// open jacket: the shirt and tie down the middle (cols 2..5), the jacket's fronts either side
		if (y >= 10) return [P.rib[(x + y) % 2]]; // ribbed hem
		if (x >= 2 && x <= 5) {
			if ((x === 3 || x === 4) && y <= 7) return [P.tie[y === 0 ? 0 : x === 3 ? 1 : 2]];
			return [fabric(P.shirt, gx, gy, x === 2 || x === 5 ? 2 : 1)];
		}
		if (x === 1 || x === 6) return [P.jacket[3]]; // the lapel edge
		return [fabric(P.jacket, gx, gy, 1)];
	}
	if (face === 'back') {
		if (y >= 10) return [P.rib[(x + y) % 2]];
		if (x === 3 && y >= 2) return [P.jacket[2]]; // centre seam
		return [fabric(P.jacket, gx, gy, y <= 1 ? 2 : 1)];
	}
	// sides
	if (y >= 10) return [P.rib[(x + y) % 2]];
	if (x === 1 && y >= 1 && y <= 9) return [P.jacket[2]]; // side seam
	return [fabric(P.jacket, gx, gy, 1)];
});
paint(JACKET, (face, x, y, w, h, gx, gy) => {
	if (face === 'top') return [furTex(gx, gy + 40)];
	if (face === 'bottom') return [P.rib[1]];
	if (face === 'front') {
		// the fur collar over the shoulders and down the lapels, the open front left clear (the shirt shows)
		if (y <= 1 && (x <= 2 || x >= 5)) return [furTex(gx, gy)];
		if (y === 2 && (x <= 1 || x >= 6)) return [furTex(gx, gy)];
		if (y >= 3 && y <= 6 && (x === 1 || x === 6)) return [furTex(gx, gy), 255, 0.85]; // fur lapels
		if (y >= 10) return [P.rib[(x + y) % 2]];
		if (x === 0 || x === 7) {
			if (y === 7) return [P.jacket[3]]; // pocket welt
			return [fabric(P.jacket, gx, gy, 1)];
		}
		if (x === 1 || x === 6) return [P.zip[x === 1 ? 0 : 1]]; // zipper tapes
		return null;
	}
	if (face === 'back') {
		if (y <= 2) return [furTex(gx, gy)]; // the collar is deepest at the back
		if (y === 3 && hash(gx, gy, 13) < 0.6) return [furTex(gx, gy)];
		if (y >= 10) return [P.rib[(x + y) % 2]];
		if (y === 6) return [P.jacket[3]]; // yoke seam
		return [fabric(P.jacket, gx, gy, 0)];
	}
	if (y <= 2) return [furTex(gx, gy)];
	if (y >= 10) return [P.rib[(x + y) % 2]];
	return [fabric(P.jacket, gx, gy, 0)];
});

// ================================================================ ARMS (wide, 4x12x4) -- right 40,16 / sleeve 40,32; left 32,48 / sleeve 48,48
function arm(baseX, baseY, sleeveX, sleeveY, mirror) {
	paint(faces(baseX, baseY, 4, 12, 4), (face, x, y, w, h, gx, gy) => {
		if (face === 'top') return [fabric(P.jacket, gx, gy, 0)];
		if (face === 'bottom') return [P.glove[2]];
		if (y >= 10) return [pick(P.glove, (y === 11 ? 2 : 1) - (face === 'front' && (x === 1 || x === 2) ? 1 : 0))]; // the gloved hand
		if (y === 9) return [P.rib[(x + y) % 2]]; // the cuff
		if (face === 'front' && y === 4) return [P.jacket[2]]; // elbow crease
		return [fabric(P.jacket, gx, gy, 1)];
	});
	paint(faces(sleeveX, sleeveY, 4, 12, 4), (face, x, y, w, h, gx, gy) => {
		if (face === 'top') return [fabric(P.jacket, gx, gy, 0)];
		if (face === 'bottom') return null;
		if (y >= 10) return (y === 10) ? [P.glove[0]] : null; // the glove's flared cuff
		if (y >= 8) return [P.rib[(x + y) % 2], 255, 1.1]; // ribbed sleeve cuff
		if (y === 0) return [P.jacket[0]]; // shoulder seam catching the light
		const outer = mirror ? face === 'left' : face === 'right';
		if (outer && y >= 2 && y <= 3) return [P.jacket[3]]; // the sleeve's patch seam
		if (y <= 1) return [fabric(P.jacket, gx, gy, 0)];
		return hash(gx, gy, 14) < 0.85 ? [fabric(P.jacket, gx, gy, 0)] : [P.jacket[1]];
	});
}
arm(40, 16, 40, 32, false);
arm(32, 48, 48, 48, true);

// ================================================================ LEGS (4x12x4) -- right 0,16 / pants 0,32; left 16,48 / pants 0,48
function leg(baseX, baseY, padX, padY) {
	paint(faces(baseX, baseY, 4, 12, 4), (face, x, y, w, h, gx, gy) => {
		if (face === 'top') return [P.pants[1]];
		if (face === 'bottom') return [P.shoe[2]];
		if (y >= 10) return [pick(P.shoe, y === 10 ? 0 : 1)];
		if (face === 'front' && (x === 1 || x === 2) && y <= 8) return [P.pants[x === 1 ? 0 : 1]]; // the crease
		return [fabric(P.pants, gx, gy, 1)];
	});
	paint(faces(padX, padY, 4, 12, 4), (face, x, y, w, h, gx, gy) => {
		if (face === 'top' || face === 'bottom') return null;
		if (y === 0) return [P.rib[0]]; // the jacket's hem hangs just over the waist
		if (y === 9) return [P.pants[2]]; // the trouser hem over the shoe
		if (y === 10 && face === 'front') return [P.shoe[0], 255, 1.15]; // the toe cap
		return null;
	});
}
leg(0, 16, 0, 32);
leg(16, 48, 0, 48);

// ================================================================ write
const outDir = process.argv[2], previewDir = process.argv[3];
const wide = { w: W, h: H, px };
const slim = tools.convertArms(wide, true);
fs.writeFileSync(path.join(outDir, 'overhaul_wide.png'), tools.encode(W, H, px));
fs.writeFileSync(path.join(outDir, 'overhaul_slim.png'), tools.encode(W, H, slim.px));
if (previewDir) {
	const p = tools.preview(wide, 10);
	fs.writeFileSync(path.join(previewDir, 'overhaul_wide_uv_x10.png'), tools.encode(p.w, p.h, p.px));
	const q = tools.preview(slim, 10);
	fs.writeFileSync(path.join(previewDir, 'overhaul_slim_uv_x10.png'), tools.encode(q.w, q.h, q.px));
}
console.log('ok');
