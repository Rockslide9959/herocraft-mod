// v0.16.7 Explosion (Bakugo / Ground Zero) costume: explosion_wide.png / explosion_slim.png (64x64 player-skin layout,
// costume only -- the face and hair are left to the wearer's own skin). Painted procedurally in 3D body coordinates so
// every face of every cube is filled and patterns run across the seams.
//   black suit with the orange X straps over the chest, shoulders and back, a utility belt with grenade pouches,
//   the big grenade gauntlets on the forearms (sleeve layer: grey metal, orange bands, a silver pin, the trigger),
//   the black eye mask with orange edges and spiked corners (hat layer), knee pads and boots.
// Usage: node gen_explosion_suit.js <repo root> <preview dir>
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = process.argv[2] || path.join(__dirname, '..');
const PREV = process.argv[3] || path.join(__dirname, 'explosion_preview');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/quirk');
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(PREV, { recursive: true });

// ---------------------------------------------------------------- PNG
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encode(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const img = (w, h) => ({ w, h, px: Buffer.alloc(w * h * 4) });
function set(im, x, y, c) {
	if (!c || x < 0 || y < 0 || x >= im.w || y >= im.h) return;
	const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c.length > 3 ? c[3] : 255;
}
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }

// ---------------------------------------------------------------- colour
function hash(x, y, s = 0) { let h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h & 0xffff) / 65535; }
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, k) => v + (b[k] - v) * t);
const mul = (c, f) => c.map(v => v * f);

const BLACK = { hi: [58, 58, 66], mid: [38, 38, 44], dk: [26, 26, 30], seam: [14, 14, 17] };
const ORANGE = { hi: [255, 176, 64], mid: [240, 128, 26], dk: [198, 90, 16], deep: [140, 58, 10] };
const METAL = { hi: [186, 190, 178], mid: [134, 140, 128], dk: [92, 98, 88], deep: [56, 60, 54] };
const SILVER = { hi: [236, 238, 242], mid: [176, 180, 188], dk: [110, 114, 122] };
const BELT = { hi: [92, 88, 82], mid: [66, 63, 60], dk: [44, 42, 40] };
const SOLE = [30, 24, 22];

// light from above / front: each face direction has its own brightness
const FACE_LIGHT = { top: 1.14, front: 1.0, right: 0.9, left: 0.9, back: 0.84, bottom: 0.72 };

// ---------------------------------------------------------------- the model's cubes (64x64 layout)
// box: [w, h, d]; at: min corner in body coordinates (X = toward the wearer's LEFT, Y = up from the feet, Z = toward the front)
function parts(slim) {
	const a = slim ? 3 : 4;
	return [
		{ name: 'head', uv: [0, 0], box: [8, 8, 8], at: [-4, 24, -4], layer: 0 },
		{ name: 'head', uv: [32, 0], box: [8, 8, 8], at: [-4, 24, -4], layer: 1 },
		{ name: 'body', uv: [16, 16], box: [8, 12, 4], at: [-4, 12, -2], layer: 0 },
		{ name: 'body', uv: [16, 32], box: [8, 12, 4], at: [-4, 12, -2], layer: 1 },
		{ name: 'rarm', uv: [40, 16], box: [a, 12, 4], at: [-4 - a, 12, -2], layer: 0 },
		{ name: 'rarm', uv: [40, 32], box: [a, 12, 4], at: [-4 - a, 12, -2], layer: 1 },
		{ name: 'larm', uv: [32, 48], box: [a, 12, 4], at: [4, 12, -2], layer: 0 },
		{ name: 'larm', uv: [48, 48], box: [a, 12, 4], at: [4, 12, -2], layer: 1 },
		{ name: 'rleg', uv: [0, 16], box: [4, 12, 4], at: [-4, 0, -2], layer: 0 },
		{ name: 'rleg', uv: [0, 32], box: [4, 12, 4], at: [-4, 0, -2], layer: 1 },
		{ name: 'lleg', uv: [16, 48], box: [4, 12, 4], at: [0, 0, -2], layer: 0 },
		{ name: 'lleg', uv: [0, 48], box: [4, 12, 4], at: [0, 0, -2], layer: 1 },
	];
}

// every texel of a cube: [u, v, face, lx, ly, lz] (local: lx from the wearer's right side, ly up, lz from the back)
function texels(p) {
	const [w, h, d] = p.box, [ou, ov] = p.uv, out = [];
	const add = (u0, v0, fw, fh, face, fn) => { for (let v = 0; v < fh; v++) for (let u = 0; u < fw; u++) out.push([ou + u0 + u, ov + v0 + v, face, ...fn(u + 0.5, v + 0.5)]); };
	add(d, 0, w, d, 'top', (u, v) => [u, h, v]);
	add(d + w, 0, w, d, 'bottom', (u, v) => [u, 0, d - v]);
	add(0, d, d, h, 'right', (u, v) => [0, h - v, u]);
	add(d, d, w, h, 'front', (u, v) => [u, h - v, d]);
	add(d + w, d, d, h, 'left', (u, v) => [w, h - v, d - u]);
	add(2 * d + w, d, w, h, 'back', (u, v) => [w - u, h - v, 0]);
	return out;
}

// ---------------------------------------------------------------- the costume
function fabric(X, Y, Z, face, seed) {
	const n = hash(Math.floor(X * 2 + 50), Math.floor(Y * 2 + 50) + Math.floor(Z * 2) * 131, seed);
	const weave = ((Math.floor(X + 40) + Math.floor(Y + 40)) & 1) ? 0.04 : -0.02;
	return mix(BLACK.dk, BLACK.hi, Math.min(1, Math.max(0, 0.35 + (n - 0.5) * 0.35 + weave)));
}

/** Distance from (x, y) to the segment a-b. */
function segDist(x, y, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay; const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
	return Math.hypot(x - (ax + t * dx), y - (ay + t * dy));
}

/** The orange X straps (front and back) and their run over the shoulders: 0 off, 1 the band, 2 its dark edge. */
function strap(X, Y, Z, face) {
	if (face === 'front' || face === 'back') {
		const d = Math.min(segDist(X, Y, -3.6, 24, 3.6, 14.2), segDist(X, Y, 3.6, 24, -3.6, 14.2));
		if (d < 0.75) return 1;
		if (d < 1.15) return 2;
		return 0;
	}
	if (face === 'top') {
		// over each shoulder, front to back
		const ax = Math.abs(X);
		if (ax > 2.4 && ax < 3.9) return ax > 2.7 && ax < 3.6 ? 1 : 2;
	}
	return 0;
}

function bodyBase(X, Y, Z, face) {
	// the utility belt (Y 12..14)
	if (Y < 14) {
		if (face === 'bottom') return BELT.dk;
		let c = Y < 12.5 || Y > 13.5 ? BELT.dk : mix(BELT.mid, BELT.hi, hash(Math.floor(X + 9), Math.floor(Z + 9), 3) * 0.5);
		// the buckle: an orange-rimmed grey plate front centre
		if (face === 'front' && Math.abs(X) < 1.2) c = Math.abs(X) < 0.6 && Y > 12.5 && Y < 13.5 ? ORANGE.hi : SILVER.mid;
		return c;
	}
	const s = strap(X, Y, Z, face);
	if (s === 1) {
		const t = (Y - 14) / 10;
		return mix(ORANGE.dk, ORANGE.hi, 0.35 + 0.5 * t + (hash(Math.floor(X * 3), Math.floor(Y * 3), 5) - 0.5) * 0.2);
	}
	if (s === 2) return ORANGE.deep;
	let c = fabric(X, Y, Z, face, 1);
	// chest: a little highlight on the pecs, the sternum seam, side seams
	if (face === 'front') {
		if (Y > 19 && Y < 22.5 && Math.abs(X) > 0.8 && Math.abs(X) < 3.2) c = mix(c, BLACK.hi, 0.35);
		if (Math.abs(X) < 0.5 && Y < 19) c = BLACK.seam;
		// the collar: a dark band round the neck
		if (Y > 23 && Math.abs(X) < 2.2) c = BLACK.seam;
	}
	if ((face === 'right' || face === 'left') && Math.abs(Z) < 0.5) c = BLACK.seam;
	if (face === 'back' && Math.abs(X) < 0.5 && Y < 19) c = BLACK.seam;
	if (face === 'top' && Math.abs(X) < 2.4) c = BLACK.seam; // the neck opening (under the head)
	return c;
}

function bodyOuter(X, Y, Z, face) {
	// grenade pouches on the belt: two at each hip, one at the back
	if (Y < 14.6 && Y > 11.5) {
		const pouch = (cx) => Math.abs(X - cx) < 0.9;
		const front = face === 'front' && (pouch(-2.6) || pouch(2.6));
		const side = (face === 'left' || face === 'right') && Math.abs(Z) < 1.0;
		const back = face === 'back' && (pouch(-1.6) || pouch(1.6));
		if (front || side || back) {
			if (Y > 14) return ORANGE.mid; // the flap
			return Y > 13 ? BELT.hi : BELT.mid;
		}
		if ((face === 'top' || face === 'bottom') && (Math.abs(Math.abs(X) - 2.6) < 0.9) && Z > 0.8) return BELT.mid;
	}
	// the shoulder-strap buckles where the straps cross
	if (face === 'front' && Math.abs(X) < 0.6 && Math.abs(Y - 19.1) < 0.6) return SILVER.hi;
	if (face === 'back' && Math.abs(X) < 0.6 && Math.abs(Y - 19.1) < 0.6) return SILVER.mid;
	return null;
}

/** lx across the arm from its outer side (0) to the body side (w); local coordinates for the arms. */
function armBase(lx, ly, lz, w, face, right) {
	// the glove: dark, fingerless look (a lighter knuckle row)
	if (ly < 1.2) {
		if (face === 'bottom') return mix(BLACK.dk, BLACK.mid, 0.4);
		return ly > 0.6 && face === 'front' ? BLACK.hi : BLACK.mid;
	}
	// under the gauntlet: the grey shows at the gaps
	if (ly < 9) return METAL.deep;
	let c = fabric(lx + (right ? 0 : 20), ly + 12, lz, face, 2);
	// the strap's end on the shoulder top
	if (face === 'top') c = mix(c, ORANGE.dk, 0.0);
	// a seam down the inside of the arm
	const inner = right ? 'left' : 'right';
	if (face === inner && Math.abs(lz - 2) < 0.5) c = BLACK.seam;
	if (ly > 11 && face !== 'top' && face !== 'bottom') c = mix(c, BLACK.hi, 0.3);
	return c;
}

function armOuter(lx, ly, lz, w, face, right) {
	// the grenade gauntlet: ly 1..9 (forearm), round in section
	if (ly < 1.0 || ly > 9.0) return null;
	// the outer side faces away from the body
	const outerFace = right ? 'right' : 'left';
	// the cuff at the elbow and the cap at the wrist: orange bands
	if (ly > 7.9) return ly > 8.5 ? ORANGE.mid : ORANGE.dk;
	if (ly < 2.1) return ly < 1.5 ? ORANGE.hi : ORANGE.dk;
	// the body: grey metal segmented like a grenade, rounded shading across each face
	const across = face === 'front' || face === 'back' ? lx / w : lz / 4;
	const round = 1 - Math.pow(Math.abs(across - 0.5) * 2, 2) * 0.35;
	let c = mix(METAL.dk, METAL.hi, 0.25 + 0.55 * ((ly - 2) / 6) * round + (hash(Math.floor(lx * 4 + 3), Math.floor(ly * 4), Math.floor(lz * 4) + 9) - 0.5) * 0.12);
	if (Math.abs(ly - 4.0) < 0.5 || Math.abs(ly - 6.0) < 0.5) c = mix(c, METAL.deep, 0.75); // the segment grooves
	if (Math.abs(across - 0.3) < 0.13 && ly > 2.5 && ly < 7.5) c = mix(c, METAL.hi, 0.55); // the polished highlight
	if (across < 0.12 || across > 0.88) c = mix(c, METAL.deep, 0.35); // the rounded edges darken
	if ((face === 'front' || face === 'back') && Math.abs(lx - w / 2) < 0.5 && ly < 6) c = mix(c, METAL.deep, 0.6);
	// the pin: a silver ring on the outer side, near the elbow
	if (face === outerFace) {
		const dz = lz - 2, dy = ly - 7.0;
		const r = Math.hypot(dz, dy);
		if (r < 1.3 && r > 0.55) return dy > 0 ? SILVER.hi : SILVER.mid;
		if (r <= 0.55) return METAL.deep;
		// the spoon / lever down the outer side, orange
		if (Math.abs(dz) < 0.55 && ly < 5.6) return ly > 3 ? ORANGE.mid : ORANGE.dk;
	}
	// the trigger grip on the front, by the hand
	if (face === 'front' && ly < 3.2 && Math.abs(lx - w / 2) < 0.9) return ORANGE.hi;
	return c;
}

function legBase(X, ly, lz, face, right) {
	// the boots: ly 0..4
	if (ly < 4.2) {
		if (face === 'bottom') return SOLE;
		if (ly < 0.8) return mix(SOLE, BLACK.dk, 0.4); // the sole's edge
		let c = mix(BLACK.dk, BLACK.mid, 0.4 + (hash(Math.floor(X * 2 + 30), Math.floor(ly * 2), 4) - 0.5) * 0.3);
		// the orange toe cap and a heel stripe
		if (face === 'front' && ly < 2.0) c = ly < 1.4 ? ORANGE.dk : ORANGE.mid;
		if (face === 'back' && ly < 2.0) c = ORANGE.deep;
		if (ly > 3.6) c = BLACK.seam; // the boot top
		return c;
	}
	let c = fabric(X, ly, lz, face, 3);
	// a seam down the outside of each leg
	const outer = right ? 'right' : 'left';
	if (face === outer && Math.abs(lz - 2) < 0.5) c = BLACK.seam;
	// the hip: a darker band under the belt
	if (ly > 11.2) c = BLACK.dk;
	return c;
}

function legOuter(X, ly, lz, face, right) {
	// knee pads: ly 5..8.6 on the front and sides
	if (ly > 5.0 && ly < 8.6) {
		const cx = Math.abs((X - (right ? -2 : 2)));
		if (face === 'front') {
			// a rounded plate: the corners cut, an orange rim on top, the dome lit from above
			const corner = cx > 1.0 && (ly < 5.6 || ly > 8.0);
			if (corner) return null;
			if (ly > 8.0) return ORANGE.mid;
			if (ly < 5.6) return BELT.dk;
			return mix(BELT.mid, BELT.hi, 0.25 + 0.6 * (1 - cx / 2) * ((ly - 5.0) / 3.6));
		}
		// the strap round the knee
		if ((face === 'left' || face === 'right') && ly > 6.2 && ly < 7.4) return lz > 3.0 ? BELT.mid : BLACK.dk;
		if (face === 'back' && ly > 6.2 && ly < 7.4) return BLACK.dk;
	}
	// the boot cuffs: a raised rim
	if (ly > 3.4 && ly < 4.6 && face !== 'top' && face !== 'bottom') return ly > 4 ? BLACK.hi : BLACK.dk;
	return null;
}

function headOuter(lx, ly, lz, face) {
	// the eye mask: rows 2..5 of the face (ly 6..2 from the bottom), spiked corners, orange edges
	const row = 8 - ly; // 0 = top of the head
	if (face === 'front') {
		const col = lx; // 0 = the wearer's right
		const c = Math.floor(col), r = Math.floor(row);
		const bridge = c === 3 || c === 4;
		// the spiked corners rise and drop at the outer columns
		if (c === 0 || c === 7) {
			if (r >= 1 && r <= 5) return r === 1 ? ORANGE.hi : r === 2 ? ORANGE.mid : r === 5 ? ORANGE.dk : BLACK.mid; // the spikes point up and out
			return null;
		}
		if (r === 2) return bridge ? null : (c === 1 || c === 6) ? ORANGE.mid : ORANGE.dk;
		if (r === 3) return bridge ? BLACK.hi : BLACK.mid;
		if (r === 4) return (c === 1 || c === 2 || c === 5 || c === 6) ? null : BLACK.dk; // the eye holes
		if (r === 5) return bridge ? null : (c === 1 || c === 6) ? ORANGE.mid : ORANGE.dk;
		return null;
	}
	if (face === 'right' || face === 'left') {
		// the mask wraps two texels round the temples, its spikes sweeping back
		const back = face === 'right' ? 8 - lz : lz; // distance from the front edge
		const r = Math.floor(row);
		if (back < 1) return r >= 2 && r <= 5 ? (r === 2 || r === 5 ? ORANGE.dk : BLACK.mid) : null;
		if (back < 2) return r === 3 ? ORANGE.mid : r === 4 ? BLACK.dk : null;
		if (back < 3) return r === 3 ? ORANGE.hi : null;
		return null;
	}
	return null;
}

function paint(p, slim, face, lx, ly, lz) {
	const X = p.at[0] + lx, Y = p.at[1] + ly, Z = p.at[2] + lz;
	const w = p.box[0];
	switch (p.name) {
		case 'head': return p.layer === 0 ? null : headOuter(lx, ly, lz, face);
		case 'body': return p.layer === 0 ? bodyBase(X, Y, Z, face) : bodyOuter(X, Y, Z, face);
		case 'rarm': case 'larm': {
			const right = p.name === 'rarm';
			// arm-local lx from the OUTER side: the right arm's outer side is its 'right' face (lx 0)
			const ox = right ? lx : w - lx;
			return p.layer === 0 ? armBase(ox, ly, lz, w, face, right) : armOuter(ox, ly, lz, w, face, right);
		}
		case 'rleg': case 'lleg': {
			const right = p.name === 'rleg';
			return p.layer === 0 ? legBase(X, ly, lz, face, right) : legOuter(X, ly, lz, face, right);
		}
	}
	return null;
}

function build(slim) {
	const im = img(64, 64);
	for (const p of parts(slim)) {
		for (const [u, v, face, lx, ly, lz] of texels(p)) {
			const c = paint(p, slim, face, lx, ly, lz);
			if (!c) continue;
			const light = FACE_LIGHT[face];
			const n = (hash(u, v, 11) - 0.5) * 6;
			set(im, u, v, c.map(k => clamp(k * light + n)));
		}
	}
	return im;
}

// ---------------------------------------------------------------- previews
// a plain stand-in skin under the costume (so the preview shows what the wearer's face does)
function underColour(p, face, lx, ly, lz) {
	if (p.name === 'head') {
		const row = 8 - ly;
		if (row < 2 || face === 'top' || face === 'back' || (face !== 'front' && row < 4)) return [214, 196, 120]; // ash-blond hair
		if (face === 'front' && Math.floor(row) === 4 && [1, 2, 5, 6].includes(Math.floor(lx))) return [Math.floor(lx) === 2 || Math.floor(lx) === 5 ? 170 : 240, 40, 30]; // red eyes
		return [236, 198, 168];
	}
	return [236, 198, 168];
}

function view(slim, which, scale) {
	// which: 'front' | 'back' | 'right'
	const W = 18 * scale, H = 34 * scale, out = img(W, H);
	for (let i = 0; i < W * H; i++) { out.px[i * 4] = 40; out.px[i * 4 + 1] = 44; out.px[i * 4 + 2] = 52; out.px[i * 4 + 3] = 255; }
	const tex = build(slim);
	const order = parts(slim);
	for (const layer of [-1, 0, 1]) {
		for (const p of order) {
			if (layer >= 0 && p.layer !== layer) continue;
			if (layer < 0 && p.layer !== 0) continue;
			if (which === 'right' && (p.name === 'larm' || p.name === 'lleg')) continue;
			for (const [u, v, face, lx, ly, lz] of texels(p)) {
				if (face !== which) continue;
				const X = p.at[0] + lx, Y = p.at[1] + ly, Z = p.at[2] + lz;
				let c;
				if (layer < 0) c = underColour(p, face, lx, ly, lz);
				else { const t = get(tex, u, v); if (t[3] === 0) continue; c = t; }
				// looking at the wearer's right side, the front is to the viewer's right
				const sx = which === 'front' ? X + 9 : which === 'back' ? 9 - X : Z + 9;
				const sy = 33 - Y;
				const px0 = Math.floor((sx - 0.5) * scale), py0 = Math.floor((sy - 0.5) * scale);
				for (let yy = 0; yy < scale; yy++) for (let xx = 0; xx < scale; xx++) set(out, px0 + xx, py0 + yy, c);
			}
		}
	}
	return out;
}

function sheet(slim, path_) {
	const s = 12;
	const views = ['front', 'back', 'right'].map(w => view(slim, w, s));
	const tex = build(slim);
	const ts = 8, texW = 64 * ts;
	const W = views.reduce((a, v) => a + v.w + 16, 16) + texW + 16, H = Math.max(views[0].h, texW) + 32;
	const out = img(W, H);
	for (let i = 0; i < W * H; i++) { out.px[i * 4] = 24; out.px[i * 4 + 1] = 26; out.px[i * 4 + 2] = 30; out.px[i * 4 + 3] = 255; }
	let x = 16;
	for (const v of views) {
		for (let yy = 0; yy < v.h; yy++) for (let xx = 0; xx < v.w; xx++) set(out, x + xx, 16 + yy, get(v, xx, yy));
		x += v.w + 16;
	}
	for (let yy = 0; yy < texW; yy++) for (let xx = 0; xx < texW; xx++) {
		const t = get(tex, Math.floor(xx / ts), Math.floor(yy / ts));
		const chk = ((xx >> 3) + (yy >> 3)) & 1 ? 200 : 160, a = t[3] / 255;
		let c = [0, 1, 2].map(k => Math.round(t[k] * a + chk * (1 - a)));
		if (xx % (ts * 8) === 0 || yy % (ts * 8) === 0) c = [255, 0, 255];
		set(out, x + xx, 16 + yy, c);
	}
	fs.writeFileSync(path_, encode(out.w, out.h, out.px));
}

for (const slim of [false, true]) {
	const im = build(slim);
	const name = `explosion_${slim ? 'slim' : 'wide'}.png`;
	fs.writeFileSync(path.join(OUT, name), encode(64, 64, im.px));
	sheet(slim, path.join(PREV, `explosion_${slim ? 'slim' : 'wide'}_sheet.png`));
	// a gap check: every face texel of the base body / legs / arms is filled
	let gaps = 0;
	for (const p of parts(slim)) {
		if (p.layer !== 0 || p.name === 'head') continue;
		for (const [u, v] of texels(p)) if (get(im, u, v)[3] === 0) gaps++;
	}
	console.log(name, 'base gaps:', gaps);
}
console.log('wrote', OUT, 'previews in', PREV);
