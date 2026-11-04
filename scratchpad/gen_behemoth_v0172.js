// v0.17.2 Abyssal Behemoth rebuild: geometry, animations, texture + glowmask, and the Abyssal Lure / To'kustar Shard icons.
// Usage: node scratchpad/gen_behemoth_v0172.js
// Everything here is built from scratch for this mod (no community model). Reference frame: 1 unit = 1 model px, front = -Z,
// the model is MODEL_HEIGHT (4.5 blocks = 72 px) tall from the tentacle tips (y=0) to the horn tips; the renderer scales it to
// the 9-block hit-box.
const fs = require('fs');
const path = require('path');
const k = require('./pngkit.js');

const assets = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const TW = 256, TH = 512;

let seed = 90210;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const img = (w, h) => ({ w, h, px: Buffer.alloc(w * h * 4) });
const set = (im, x, y, c) => { x = Math.floor(x); y = Math.floor(y); if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = clamp(c[0]); im.px[i + 1] = clamp(c[1]); im.px[i + 2] = clamp(c[2]); im.px[i + 3] = c.length > 3 ? clamp(c[3]) : 255; };
const get = (im, x, y) => { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; };
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
const save = (im, p) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, k.encode(im.w, im.h, im.px)); };

const tex = img(TW, TH);
const glow = img(TW, TH);

// ------------------------------------------------------------------ palette
const P = {
	// an ANCIENT ghast: the pale hide gone ashen and soot-stained, split by magma, armoured in fused obsidian
	hide: [204, 196, 188], hideDark: [110, 100, 96], hideLight: [236, 230, 220], vein: [128, 36, 40],
	plate: [44, 30, 60], plateEdge: [124, 92, 168], plateGlint: [170, 110, 240],
	bone: [84, 70, 64], boneTip: [240, 230, 204],
	magma: [255, 120, 24], magmaHot: [255, 214, 90], magmaDeep: [150, 34, 10],
	eye: [255, 84, 16], eyeHot: [255, 228, 130], pupil: [30, 0, 0], lid: [60, 48, 50],
	maw: [255, 96, 20], mawDeep: [110, 12, 4], tooth: [232, 222, 196], toothDark: [150, 136, 110],
	tent: [196, 186, 180], tentRing: [120, 108, 106],
};

// ------------------------------------------------------------------ packer (per-face UV)
let shelfX = 0, shelfY = 0, shelfH = 0;
function alloc(w, h) {
	w = Math.max(1, Math.ceil(w)); h = Math.max(1, Math.ceil(h));
	if (shelfX + w > TW) { shelfX = 0; shelfY += shelfH; shelfH = 0; }
	const r = [shelfX, shelfY];
	shelfX += w; shelfH = Math.max(shelfH, h);
	if (shelfY + h > TH) throw new Error('texture full');
	return r;
}

// painters: (x0, y0, w, h, face, opts) -> paint into tex/glow
function noiseFill(x0, y0, w, h, base, amp) {
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const n = (rnd() - 0.5) * amp;
		set(tex, x0 + x, y0 + y, [base[0] + n, base[1] + n * 0.9, base[2] + n]);
	}
}
function glowPx(x, y, c) { set(tex, x, y, c); set(glow, x, y, c); }
function crack(x0, y0, w, h, n, len) {
	for (let c = 0; c < n; c++) {
		let x = x0 + rnd() * w, y = y0 + rnd() * h;
		let dx = rnd() - 0.5, dy = rnd() - 0.5;
		for (let i = 0; i < len; i++) {
			if (x < x0 || y < y0 || x >= x0 + w || y >= y0 + h) break;
			const hot = i % 5 === 0 || rnd() < 0.15;
			glowPx(x, y, hot ? P.magmaHot : P.magma);
			// a dim rim beside the crack (not glowing)
			const [rx, ry] = [Math.floor(x + (dy > 0 ? 1 : -1)), Math.floor(y + (dx > 0 ? -1 : 1))];
			if (rx >= x0 && ry >= y0 && rx < x0 + w && ry < y0 + h && get(glow, rx, ry)[3] === 0) set(tex, rx, ry, P.magmaDeep);
			dx += (rnd() - 0.5) * 0.7; dy += (rnd() - 0.5) * 0.7;
			const l = Math.hypot(dx, dy) || 1; dx /= l; dy /= l;
			x += dx; y += dy;
			if (rnd() < 0.06) { // a short fork
				let fx = x, fy = y, fdx = -dy, fdy = dx;
				for (let j = 0; j < 4 + rnd() * 5; j++) { fx += fdx; fy += fdy; if (fx >= x0 && fy >= y0 && fx < x0 + w && fy < y0 + h) glowPx(fx, fy, P.magma); }
			}
		}
	}
}
const painters = {
	hide(x0, y0, w, h, face, o) {
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const n = (rnd() - 0.5) * 18;
			// wrinkled, scaly flesh: soft horizontal folds + a scale lattice
			const fold = Math.sin((y + Math.sin(x * 0.35) * 1.5) * 0.9) * 8;
			const scale = ((x + (Math.floor(y / 3) % 2) * 2) % 4 === 0 || y % 3 === 0) ? -10 : 0;
			// soot: side faces darken toward the belly; the underside is the darkest
			const shade = face === 'up' ? 8 : face === 'down' ? -60 : -Math.pow(y / Math.max(1, h - 1), 1.6) * 55;
			set(tex, x0 + x, y0 + y, [P.hide[0] + n + fold + scale + shade, P.hide[1] + n + fold + scale + shade, P.hide[2] + n + fold + scale + shade]);
		}
		// dark crimson veins
		for (let v = 0; v < Math.max(1, (w * h) / 120); v++) {
			let x = rnd() * w, y = rnd() * h;
			for (let i = 0; i < 10; i++) { set(tex, x0 + x, y0 + y, P.vein); x += rnd() * 2 - 1; y += rnd() * 2 - 1; if (x < 0 || y < 0 || x >= w || y >= h) break; }
		}
		if (o.tears && face === 'north') { // magma tears: soot streaks running down from each eye, a glowing trickle in each
			for (const u of o.tears) for (let y = o.tearFrom; y < h; y++) {
				const wob = Math.round(Math.sin(y * 0.7 + u) * 0.6);
				for (let dx = -2; dx <= 2; dx++) { const [r, g, b] = get(tex, x0 + u + dx + wob, y0 + y); set(tex, x0 + u + dx + wob, y0 + y, [r * 0.55, g * 0.5, b * 0.5]); }
				if (y < h - 2 || rnd() < 0.5) glowPx(x0 + u + wob, y0 + y, y % 4 === 0 ? P.magmaHot : P.magma);
			}
		}
		if (o.cracks) crack(x0, y0, w, h, Math.max(1, Math.round((w * h) / 260 * o.cracks)), 8 + Math.min(w, h) * 0.8);
		if (o.scars) for (let s = 0; s < o.scars; s++) {
			const sx = rnd() * w, sy = rnd() * h, L = 4 + rnd() * 6;
			for (let i = 0; i < L; i++) set(tex, x0 + sx + i, y0 + sy + i * 0.4, P.hideLight);
		}
	},
	plate(x0, y0, w, h, face, o) {
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const n = (rnd() - 0.5) * 10;
			const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
			const facet = (x + y * 2) % 11 === 0 || (x * 2 - y + 40) % 13 === 0;
			let c = edge ? P.plateEdge : facet ? mix(P.plate, P.plateEdge, 0.45) : P.plate;
			if (!edge && rnd() < 0.015) c = P.plateGlint;
			set(tex, x0 + x, y0 + y, [c[0] + n, c[1] + n, c[2] + n * 1.4]);
		}
		// rivet-like studs on large faces, and the occasional glowing seam along one edge
		if (w >= 8 && h >= 6) for (let i = 2; i < w - 2; i += 6) set(tex, x0 + i, y0 + 1, [96, 80, 120]);
		if (o.seam && h >= 3 && w >= 3) for (let x = 1; x < w - 1; x++) if (rnd() < 0.7) glowPx(x0 + x, y0 + h - 1, rnd() < 0.3 ? P.magmaHot : P.magma);
		if (o.rim && h >= 3 && w >= 3) for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if ((x === 0 || y === 0 || x === w - 1 || y === h - 1) && rnd() < 0.75) glowPx(x0 + x, y0 + y, rnd() < 0.3 ? P.magmaHot : P.magma);
	},
	bone(x0, y0, w, h, face, o) { // horns/spikes: basalt at the root, bone at the tip (v small = top)
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const t0 = o.tipTop ? 1 - y / Math.max(1, h - 1) : y / Math.max(1, h - 1);
			const t = Math.pow(Math.max(0, Math.min(1, (o.t0 || 0) + t0 * (o.span || 1))), 1.3);
			const n = (rnd() - 0.5) * 14;
			const ridge = (y % 3 === 0) ? -14 : 0;
			const c = mix(P.bone, P.boneTip, t);
			set(tex, x0 + x, y0 + y, [c[0] + n + ridge, c[1] + n + ridge, c[2] + n + ridge]);
		}
		if (o.cracks) crack(x0, y0, w, h, 1, 6);
	},
	tooth(x0, y0, w, h) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(tex, x0 + x, y0 + y, mix(P.toothDark, P.tooth, (x + y) % 3 === 0 ? 0.6 : 1)); },
	eye(x0, y0, w, h, face, o) {
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const cx = (x + 0.5) / w - 0.5, cy = (y + 0.5) / h - 0.5;
			// a heavy lid slanting down toward the middle of the face: an angry, hooded eye
			const slant = o.side ? (o.side < 0 ? (x / w) : (1 - x / w)) : 0.5;
			if (face === 'north' && h >= 4 && y < Math.round(slant * (h * 0.55))) { set(tex, x0 + x, y0 + y, P.lid); continue; }
			const r = Math.hypot(cx * 1.0, cy * 1.3);
			let c = mix(P.eyeHot, P.eye, Math.min(1, r * 2.4));
			if (face === 'north' && w >= 3 && Math.abs(x + 0.5 - w / 2) < 0.6) c = P.pupil; // a slit pupil
			glowPx(x0 + x, y0 + y, c);
		}
	},
	maw(x0, y0, w, h) { // the throat: dim at the lips, white-hot deep inside
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const e = Math.max(Math.abs((y + 0.5) / h - 0.5) * 2, Math.abs((x + 0.5) / w - 0.5) * 2);
			const c = e > 0.7 ? mix(P.maw, P.mawDeep, (e - 0.7) / 0.3) : mix(P.magmaHot, P.maw, e / 0.7 + rnd() * 0.15);
			glowPx(x0 + x, y0 + y, c);
		}
	},
	vent(x0, y0, w, h) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) glowPx(x0 + x, y0 + y, (y + x) % 4 === 0 ? P.magmaHot : P.magma); },
	tent(x0, y0, w, h, face, o) {
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const n = (rnd() - 0.5) * 14;
			const ring = y % 4 === 0;
			const c = ring ? P.tentRing : P.tent;
			set(tex, x0 + x, y0 + y, [c[0] + n, c[1] + n, c[2] + n]);
		}
		if (o.glowTip) for (let x = 0; x < w; x++) for (let y = h - 2; y < h; y++) glowPx(x0 + x, y0 + y, y === h - 1 ? P.magmaHot : P.magma);
		if (o.vein && h > 4) { const vx = Math.floor(w / 2); for (let y = 1; y < h - 1; y++) if (rnd() < 0.8) glowPx(x0 + vx + (y % 3 === 0 ? 1 : 0), y0 + y, P.magma); }
	},
};

// ------------------------------------------------------------------ geometry
const bones = [];
const boneMap = {};
function bone(name, parent, pivot, rotation) {
	const b = { name, pivot };
	if (parent) b.parent = parent;
	if (rotation) b.rotation = rotation;
	b.cubes = [];
	bones.push(b); boneMap[name] = b;
	return b;
}
/** A cube on {@code b}: origin/size in px, painted with {@code mat} (per face; faceMat overrides one face's material). */
function cube(b, origin, size, mat, o = {}) {
	const [w, h, d] = size;
	const faces = { north: [w, h], south: [w, h], east: [d, h], west: [d, h], up: [w, d], down: [w, d] };
	const uv = {};
	for (const [f, [fw, fh]] of Object.entries(faces)) {
		const cw = Math.max(1, Math.ceil(fw)), ch = Math.max(1, Math.ceil(fh));
		const [u, v] = alloc(cw, ch);
		const m = (o.faceMat && o.faceMat[f]) || mat;
		painters[m](u, v, cw, ch, f, Object.assign({}, o, (o.faceOpts && o.faceOpts[f]) || {}));
		uv[f] = f === 'up' || f === 'down' ? { uv: [u, v], uv_size: [cw, ch] } : { uv: [u, v], uv_size: [cw, ch] };
	}
	const c = { origin, size, uv };
	if (o.inflate) c.inflate = o.inflate;
	if (o.rotation) { c.rotation = o.rotation; c.pivot = o.pivot || [origin[0] + w / 2, origin[1] + h / 2, origin[2] + d / 2]; }
	b.cubes.push(c);
	return c;
}

bone('root', null, [0, 0, 0]);
bone('body', 'root', [0, 40, 0]);
const B = boneMap.body;
// the hull: a great swollen sac, tapering top and bottom
cube(B, [-20, 28, -20], [40, 26, 40], 'hide', { cracks: 0.75, scars: 3, tears: [10, 30], tearFrom: 15 });
cube(B, [-17, 24, -17], [34, 4, 34], 'hide', { cracks: 0.6 });
cube(B, [-16, 54, -16], [32, 3, 32], 'hide', { cracks: 0.5 });
// bulges on the flanks and back round off the box into a swollen sac
for (const s of [-1, 1]) cube(B, [s < 0 ? -22 : 20, 31, -15], [2, 20, 30], 'hide', { cracks: 1.0 });
cube(B, [-15, 31, 20], [30, 20, 2], 'hide', { cracks: 1.0 });
cube(B, [-15, 57, -12], [30, 1, 24], 'hide', { cracks: 0.4 });
// underside vent: the abyssal core showing through the cracked belly
cube(B, [-7, 23, -7], [14, 1, 14], 'vent');
// side vents (three slits each side) and the back vents
for (const s of [-1, 1]) for (let i = 0; i < 3; i++) cube(B, [s < 0 ? -21 : 20, 33 + i * 0, -10 + i * 8], [1, 14, 2], 'vent');
for (let i = 0; i < 3; i++) cube(B, [-9 + i * 8, 34, 20], [2, 12, 1], 'vent');

// ---- the face
// brow: an overhanging plate of fused obsidian
cube(B, [-19, 46, -24], [38, 5, 6], 'plate', { seam: true, rotation: [12, 0, 0], pivot: [0, 48, -20] });
// eyes: two great slit eyes, two smaller ones flanking above, a third eye in the brow's shadow
cube(B, [-14, 39, -21], [8, 5, 1], 'eye', { side: -1 });
cube(B, [6, 39, -21], [8, 5, 1], 'eye', { side: 1 });
cube(B, [-18, 44, -21], [3, 2, 1], 'eye');
cube(B, [15, 44, -21], [3, 2, 1], 'eye');
cube(B, [-1.5, 43, -21], [3, 3, 1], 'eye');
// eye sockets: dark ridged rims
cube(B, [-15, 38, -21.5], [10, 1, 1], 'plate');
cube(B, [5, 38, -21.5], [10, 1, 1], 'plate');
// the maw: a glowing throat behind upper fangs
cube(B, [-13, 30, -20.5], [26, 6, 1], 'maw');
// ragged upper fangs: long canines at the corners and centre, stubs between
[[-12, 5], [-8.5, 2], [-5.5, 3], [-1, 6], [3.5, 3], [6.5, 2], [10, 5]].forEach(([x, len]) => cube(B, [x, 36 - len, -22], [2, len, 1], 'tooth'));
// the jaw (hinged at the back of the mouth)
bone('jaw', 'body', [0, 31, -14]);
cube(boneMap.jaw, [-15, 26, -24], [30, 5, 11], 'hide', { cracks: 0.6, faceMat: { up: 'maw' } });
cube(boneMap.jaw, [-13, 24, -22], [26, 2, 8], 'plate', { seam: true });
[[-10, 3], [-6.5, 2], [-3, 4], [1, 4], [4.5, 2], [8, 3]].forEach(([x, len]) => cube(boneMap.jaw, [x, 31, -23.5], [2, len, 1], 'tooth'));
// mandible tusks, hanging off the jaw's corners
for (const s of [-1, 1]) {
	const tusk = bone('tusk_' + (s < 0 ? 'l' : 'r'), 'jaw', [s * 14, 27, -20], [-30, 0, s * 10]);
	cube(tusk, [s * 14 - 1.5, 17, -21.5], [3, 10, 3], 'bone', { tipTop: false });
	cube(tusk, [s * 14 - 1, 13, -21], [2, 4, 2], 'bone', { tipTop: false, t0: 0.7, span: 0.3 });
}

// ---- armour plates
const plates = bone('plates', 'body', [0, 50, 0]);
cube(plates, [-16, 56, -18], [32, 3, 13], 'plate', { seam: true, rotation: [-10, 0, 0], pivot: [0, 57, -12] });
cube(plates, [-18, 56, -6], [36, 3, 13], 'plate', { seam: true });
cube(plates, [-16, 55, 6], [32, 3, 13], 'plate', { seam: true, rotation: [10, 0, 0], pivot: [0, 56, 12] });
for (const s of [-1, 1]) {
	cube(plates, [s < 0 ? -24 : 21, 41, -14], [3, 11, 11], 'bone', { tipTop: true, t0: 0.25, span: 0.45, cracks: true, rotation: [0, 0, s * -8], pivot: [s * 22, 52, -9] });
	cube(plates, [s < 0 ? -24 : 21, 37, 2], [3, 12, 12], 'bone', { tipTop: true, t0: 0.25, span: 0.45, cracks: true, rotation: [0, 0, s * -6], pivot: [s * 22, 49, 8] });
	// shoulder spikes off the side plates
	cube(plates, [s < 0 ? -27 : 23, 48, -4], [4, 3, 3], 'bone', { tipTop: false, rotation: [0, 0, s * 20], pivot: [s * 24, 49, -3] });
	cube(plates, [s < 0 ? -26 : 22, 42, 10], [4, 3, 3], 'bone', { tipTop: false, rotation: [0, 0, s * 25], pivot: [s * 24, 43, 11] });
}
cube(plates, [-15, 32, 21], [30, 20, 3], 'plate', { seam: true, rotation: [8, 0, 0], pivot: [0, 52, 22] });
// spine of obsidian spikes down the back of the crown
[[-10, 7], [-2, 10], [6, 9], [13, 6]].forEach(([z, hgt], i) => {
	cube(plates, [-2, 58, z], [4, hgt, 4], 'bone', { tipTop: true, rotation: [-22, 0, 0], pivot: [0, 58, z + 2] });
});

// ---- the crown: two great curved horns sweeping up and forward, two lesser swept back
function horn(name, parent, base, segs, rot) {
	let p = parent, y = base[1];
	segs.forEach(([sz, len, r], i) => {
		const n = name + (i === 0 ? '' : '_' + (i + 1));
		const pivot = i === 0 ? base : [base[0], y, base[2]];
		const b = bone(n, p, pivot, i === 0 ? rot : r);
		cube(b, [base[0] - sz / 2, y, base[2] - sz / 2], [sz, len, sz], 'bone', { tipTop: true, t0: i / segs.length, span: 1 / segs.length });
		y += len; p = n;
	});
}
horn('horn_l', 'body', [-13, 56, -12], [[7, 8], [6, 8, [10, 0, 24]], [5, 7, [14, 0, 24]], [3, 6, [16, 0, 20]], [2, 4, [18, 0, 14]]], [6, 0, -58]);
horn('horn_r', 'body', [13, 56, -12], [[7, 8], [6, 8, [10, 0, -24]], [5, 7, [14, 0, -24]], [3, 6, [16, 0, -20]], [2, 4, [18, 0, -14]]], [6, 0, 58]);
horn('horn_bl', 'body', [-10, 55, 10], [[5, 6], [4, 6, [-26, 0, 10]], [3, 5, [-26, 0, 8]], [2, 4, [-22, 0, 6]]], [-38, 0, -28]);
horn('horn_br', 'body', [10, 55, 10], [[5, 6], [4, 6, [-26, 0, -10]], [3, 5, [-26, 0, -8]], [2, 4, [-22, 0, -6]]], [-38, 0, 28]);

// ---- tentacles: eight in a ring plus a thick central one, four tapering segments each
const tentacles = [];
for (let i = 0; i < 9; i++) {
	const center = i === 8;
	const a = (Math.PI * 2 * (i + 0.5)) / 8;
	const r = center ? 0 : 12.5;
	const x = Math.round(Math.cos(a) * r), z = Math.round(Math.sin(a) * r);
	const segs = center ? [[7, 7], [6, 7], [5, 7], [4, 6]] : [[6, 7], [5, 7], [4, 7], [3, 7]];
	let y = 26, parent = 'body';
	const names = [];
	segs.forEach(([sz, len], s) => {
		const n = 'tentacle_' + (i + 1) + (s === 0 ? '' : '_' + 'bcd'[s - 1]);
		const b = bone(n, parent, [x, y, z]);
		const last = s === segs.length - 1;
		cube(b, [x - sz / 2, y - len, z - sz / 2], [sz, len, sz], 'tent', { glowTip: last, vein: s === 1 || s === 2 });
		names.push(n);
		y -= len; parent = n;
	});
	tentacles.push({ names, i, x, z });
}

const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.abyssal_behemoth', texture_width: TW, texture_height: TH, visible_bounds_width: 8, visible_bounds_height: 6, visible_bounds_offset: [0, 2.5, 0] },
		bones: bones.map(b => { const o = { name: b.name }; if (b.parent) o.parent = b.parent; o.pivot = b.pivot; if (b.rotation) o.rotation = b.rotation; if (b.cubes.length) o.cubes = b.cubes; return o; }),
	}],
};
fs.writeFileSync(path.join(assets, 'geo', 'abyssal_behemoth.geo.json'), JSON.stringify(geo, null, 1));
save(tex, path.join(assets, 'textures', 'entity', 'abyssal_behemoth.png'));
save(glow, path.join(assets, 'textures', 'entity', 'abyssal_behemoth_glowmask.png'));
console.log('bones', bones.length, 'cubes', bones.reduce((n, b) => n + b.cubes.length, 0), 'texture rows used', shelfY + shelfH);

// ------------------------------------------------------------------ animations (sampled keyframes)
const r3 = v => Math.round(v * 1000) / 1000;
function track(len, steps, fn) { const o = {}; for (let i = 0; i <= steps; i++) { const t = (len * i) / steps; o[t.toFixed(2)] = fn(t).map(r3); } return o; }
const anims = {};
const TAU = Math.PI * 2;
/** tentacle wave on every segment: amplitude grows down the tentacle, phase travels down it */
function tentacleWave(bonesOut, len, steps, amp, speed, extra = () => [0, 0, 0]) {
	for (const t of tentacles) t.names.forEach((n, s) => {
		const ph = t.i * 0.83 + s * 0.9;
		const a = amp * (0.55 + s * 0.35);
		bonesOut[n] = { rotation: track(len, steps, tt => { const e = extra(tt, t, s); return [Math.sin(TAU * speed * tt / len + ph) * a + e[0], e[1], Math.cos(TAU * speed * tt / len + ph * 1.3) * a * 0.6 + e[2]]; }) };
	});
}
function loop(name, len, fn) { const b = {}; fn(b, len); anims['animation.abyssal_behemoth.' + name] = { loop: true, animation_length: len, bones: b }; }
function once(name, len, fn, hold) { const b = {}; fn(b, len); const a = { animation_length: len, bones: b }; if (hold) a.loop = 'hold_on_last_frame'; anims['animation.abyssal_behemoth.' + name] = a; }
const sinT = (tt, len, n = 1, ph = 0) => Math.sin(TAU * n * tt / len + ph);
// an envelope 0 -> 1 -> 0 over [a,b,c] seconds
const env = (t, a, b, c) => t <= a ? 0 : t < b ? (t - a) / (b - a) : t < c ? 1 - (t - b) / (c - b) : 0;
const ramp = (t, a, b) => t <= a ? 0 : t >= b ? 1 : (t - a) / (b - a);

loop('idle', 4, (b, L) => {
	b.root = { position: track(L, 16, t => [0, sinT(t, L) * 1.4, 0]) };
	b.body = { rotation: track(L, 16, t => [sinT(t, L, 1, 1) * 2, sinT(t, L, 1, 2) * 1.5, sinT(t, L, 1) * 1.2]) };
	b.jaw = { rotation: track(L, 16, t => [Math.max(0, sinT(t, L, 1, 0.6)) * 6, 0, 0]) };
	tentacleWave(b, L, 16, 7, 1);
});
loop('fly', 1.6, (b, L) => {
	b.root = { position: track(L, 8, t => [0, sinT(t, L) * 1.0, 0]) };
	b.body = { rotation: track(L, 8, t => [8 + sinT(t, L, 1, 1) * 2, 0, sinT(t, L) * 2]) };
	b.jaw = { rotation: track(L, 8, t => [3 + Math.max(0, sinT(t, L)) * 4, 0, 0]) };
	tentacleWave(b, L, 12, 9, 1, (t, te, s) => [-12 - s * 4, 0, 0]); // streaming behind
});
loop('enraged_idle', 1.6, (b, L) => {
	b.root = { position: track(L, 8, t => [sinT(t, L, 3) * 0.4, sinT(t, L) * 1.2, 0]) };
	b.body = { rotation: track(L, 8, t => [6 + sinT(t, L, 2) * 3, sinT(t, L, 1) * 3, sinT(t, L, 3) * 1.5]) };
	b.jaw = { rotation: track(L, 8, t => [10 + Math.max(0, sinT(t, L, 2)) * 10, 0, 0]) };
	b.plates = { position: track(L, 8, t => [0, Math.max(0, sinT(t, L, 2)) * 0.6, 0]) };
	tentacleWave(b, L, 12, 14, 2);
});
loop('ground_idle', 3, (b, L) => {
	b.root = { position: track(L, 12, t => [0, sinT(t, L) * 0.5, 0]) };
	b.body = { rotation: track(L, 12, t => [4 + sinT(t, L) * 1.5, 0, 0]) };
	b.jaw = { rotation: track(L, 12, t => [6 + Math.max(0, sinT(t, L)) * 5, 0, 0]) };
	tentacleWave(b, L, 12, 5, 1, (t, te, s) => [s === 0 ? -8 : s * 6, 0, 0]); // splayed out on the floor
});

const jawOpen = (L, a, b2, c, deg) => ({ rotation: track(L, Math.max(6, Math.round(L * 12)), t => [env(t, a, b2, c) * deg, 0, 0]) });
once('charge_fireball', 0.7, (b, L) => {
	b.jaw = jawOpen(L, 0, 0.5, 0.7, 34);
	b.body = { rotation: track(L, 8, t => [-ramp(t, 0, 0.5) * 10 + ramp(t, 0.5, 0.7) * 14, 0, 0]) };
	b.root = { position: track(L, 8, t => [0, 0, ramp(t, 0, 0.5) * 2 - ramp(t, 0.5, 0.7) * 3]) };
	tentacleWave(b, L, 8, 4, 1, (t, te, s) => [ramp(t, 0, 0.5) * -10, 0, 0]);
});
once('fireball_attack', 0.5, (b, L) => {
	b.jaw = jawOpen(L, 0, 0.1, 0.5, 36);
	b.body = { rotation: track(L, 6, t => [env(t, 0, 0.1, 0.5) * 14, 0, 0]) };
});
once('hellfire_barrage', 1.2, (b, L) => {
	b.jaw = { rotation: track(L, 14, t => [ramp(t, 0, 0.2) * (22 + Math.abs(sinT(t, L, 6)) * 14) * (1 - ramp(t, 1.0, 1.2)), 0, 0]) };
	b.body = { rotation: track(L, 14, t => [sinT(t, L, 6) * 4, 0, 0]) };
	b.root = { position: track(L, 14, t => [0, 0, Math.abs(sinT(t, L, 6)) * -1.5]) };
});
once('charge_beam', 2, (b, L) => {
	b.jaw = jawOpen(L, 0, 1.6, 2.0, 40);
	b.body = { rotation: track(L, 16, t => [-ramp(t, 0, 1.6) * 12, sinT(t, L, 8) * ramp(t, 0.8, 1.8) * 1.5, 0]) };
	b.plates = { position: track(L, 16, t => [0, ramp(t, 0, 1.6) * 1.5, 0]) };
	for (const h of ['horn_l', 'horn_r']) b[h] = { rotation: track(L, 8, t => [ramp(t, 0, 1.6) * -8, 0, 0]) };
	tentacleWave(b, L, 16, 3, 4, (t, te, s) => [ramp(t, 0, 1.6) * 18, 0, 0]); // tentacles flare forward, bracing
});
loop('beam_fire', 1.5, (b, L) => {
	b.jaw = { rotation: track(L, 12, t => [42 + sinT(t, L, 8) * 2, 0, 0]) };
	b.body = { rotation: track(L, 12, t => [10 + sinT(t, L, 10) * 1, 0, 0]) };
	b.root = { position: track(L, 12, t => [sinT(t, L, 12) * 0.3, 0, 1.5]) };
	tentacleWave(b, L, 12, 3, 6, () => [20, 0, 0]);
});
loop('magma_rain', 1, (b, L) => {
	b.body = { rotation: track(L, 8, t => [-14, 0, sinT(t, L) * 3]) };
	b.jaw = { rotation: track(L, 8, t => [18 + sinT(t, L, 2) * 6, 0, 0]) };
	b.plates = { position: track(L, 8, t => [0, Math.abs(sinT(t, L, 2)) * 1.5, 0]) };
	tentacleWave(b, L, 8, 16, 2);
});
once('cinder_tether', 0.8, (b, L) => {
	b.jaw = jawOpen(L, 0, 0.4, 0.8, 24);
	b.body = { rotation: track(L, 8, t => [env(t, 0, 0.4, 0.8) * 16, 0, 0]) };
	tentacleWave(b, L, 8, 4, 1, (t, te, s) => [env(t, 0, 0.35, 0.8) * (-50 + s * 10), 0, 0]); // tentacles lash out forward
});
once('sovereign_descent', 0.6, (b, L) => {
	b.body = { rotation: track(L, 6, t => [ramp(t, 0, 0.3) * 20, 0, 0]) };
	b.jaw = jawOpen(L, 0, 0.3, 0.6, 30);
	tentacleWave(b, L, 6, 2, 1, (t, te, s) => [0, 0, 0]);
	for (const t of tentacles) t.names.forEach((n, s) => { b[n] = { rotation: track(L, 6, tt => [ramp(tt, 0, 0.4) * (t.z < 0 ? 40 : -40) / (s + 1), 0, ramp(tt, 0, 0.4) * (t.x < 0 ? -40 : 40) / (s + 1)]) }; });
});
once('ground_land', 0.5, (b, L) => {
	b.root = { position: track(L, 6, t => [0, -env(t, 0, 0.12, 0.5) * 3, 0]) };
	b.body = { scale: track(L, 6, t => [1 + env(t, 0, 0.12, 0.5) * 0.06, 1 - env(t, 0, 0.12, 0.5) * 0.08, 1 + env(t, 0, 0.12, 0.5) * 0.06]) };
	for (const t of tentacles) t.names.forEach((n, s) => { b[n] = { rotation: track(L, 6, tt => [env(tt, 0, 0.12, 0.5) * (t.z < 0 ? 55 : -55) / (s + 1), 0, env(tt, 0, 0.12, 0.5) * (t.x < 0 ? -55 : 55) / (s + 1)]) }; });
});
once('ground_attack', 0.6, (b, L) => {
	b.jaw = jawOpen(L, 0, 0.25, 0.6, 38);
	b.body = { rotation: track(L, 8, t => [-env(t, 0, 0.2, 0.6) * 10 + env(t, 0.2, 0.3, 0.6) * 24, 0, 0]) };
});
once('hellwind', 1, (b, L) => {
	b.root = { position: track(L, 10, t => [0, env(t, 0, 0.7, 1) * 2, 0]) };
	b.body = { scale: track(L, 10, t => { const s = 1 + env(t, 0, 0.7, 0.85) * 0.08; return [s, s, s]; }) };
	b.jaw = jawOpen(L, 0, 0.7, 1, 40);
	b.plates = { position: track(L, 10, t => [0, env(t, 0, 0.7, 1) * 2.5, 0]) };
	for (const t of tentacles) t.names.forEach((n, s) => { b[n] = { rotation: track(L, 10, tt => [env(tt, 0.3, 0.75, 1) * (t.z < 0 ? 70 : -70) / (s * 0.6 + 1), 0, env(tt, 0.3, 0.75, 1) * (t.x < 0 ? -70 : 70) / (s * 0.6 + 1)]) }; });
});
loop('netherstorm', 1.5, (b, L) => {
	b.root = { position: track(L, 12, t => [sinT(t, L, 5) * 0.3, sinT(t, L) * 1.5, 0]) };
	b.body = { rotation: track(L, 12, t => [-10, sinT(t, L) * 6, 0]) };
	b.jaw = { rotation: track(L, 12, t => [26 + sinT(t, L, 3) * 6, 0, 0]) };
	tentacleWave(b, L, 12, 22, 2);
});
once('hit', 0.3, (b, L) => {
	b.body = { rotation: track(L, 6, t => [-env(t, 0, 0.07, 0.3) * 9, 0, env(t, 0, 0.07, 0.3) * 4]) };
	b.jaw = jawOpen(L, 0, 0.07, 0.3, 20);
	b.root = { position: track(L, 6, t => [0, env(t, 0, 0.07, 0.3) * 0.8, env(t, 0, 0.07, 0.3) * 1.5]) };
});
once('phase_transition', 1.8, (b, L) => {
	b.root = { position: track(L, 18, t => [sinT(t, L, 14) * 0.5 * env(t, 0.2, 0.9, 1.8), env(t, 0, 0.9, 1.8) * 3, 0]) };
	b.body = { rotation: track(L, 18, t => [-env(t, 0, 0.9, 1.8) * 22, 0, 0]), scale: track(L, 18, t => { const s = 1 + env(t, 0.6, 0.9, 1.4) * 0.06; return [s, s, s]; }) };
	b.jaw = jawOpen(L, 0, 0.8, 1.8, 48);
	b.plates = { position: track(L, 18, t => [0, env(t, 0.2, 0.9, 1.8) * 3, 0]) };
	tentacleWave(b, L, 18, 20, 3, (t, te, s) => [env(t, 0, 0.9, 1.8) * -20, 0, 0]);
});
once('death', 3, (b, L) => {
	b.root = { position: track(L, 15, t => [sinT(t, L, 10) * 0.6 * (1 - ramp(t, 1.5, 3)), -ramp(t, 0.8, 3) * 14, 0]), rotation: track(L, 15, t => [ramp(t, 0.6, 3) * 32, 0, ramp(t, 0.6, 3) * 18]) };
	b.jaw = { rotation: track(L, 10, t => [ramp(t, 0, 0.6) * 50 - ramp(t, 1.8, 3) * 20, 0, 0]) };
	b.plates = { position: track(L, 10, t => [0, ramp(t, 0.2, 0.8) * 2, 0]) };
	for (const t of tentacles) t.names.forEach((n, s) => { b[n] = { rotation: track(L, 15, tt => [Math.sin(tt * 9 + t.i) * 14 * (1 - ramp(tt, 0, 2)) + ramp(tt, 1, 3) * 10 * (s + 1), 0, Math.cos(tt * 7 + t.i) * 10 * (1 - ramp(tt, 0, 2))]) }; });
}, true);

fs.writeFileSync(path.join(assets, 'animations', 'abyssal_behemoth.animation.json'), JSON.stringify({ format_version: '1.8.0', animations: anims }, null, 1));
console.log('animations', Object.keys(anims).length);

// ------------------------------------------------------------------ item icons
function sprite(rows, pal, glowKeys) {
	const im = img(16, 16);
	rows.forEach((row, y) => [...row].forEach((ch, x) => { if (pal[ch]) set(im, x, y, pal[ch]); }));
	return im;
}
const lure = sprite([
	'................',
	'......PPPP......',
	'.....PpLLpP.....',
	'....PpLTTLpP....',
	'...PpLTWWTLpP...',
	'...PLTWOOWTLP...',
	'..PpTWOYYOWTpP..',
	'..PLTOYHHYOTLP..',
	'..PLTOYHHYOTLP..',
	'..PpTWOYYOWTpP..',
	'...PLTWOOWTLP...',
	'...PpLTWWTLpP...',
	'....PpLTTLpP....',
	'.....PpLLpP.....',
	'......PPPP......',
	'................',
], {
	P: [30, 14, 48], p: [92, 38, 150], L: [140, 70, 210], T: [190, 230, 236], W: [236, 250, 250],
	O: [255, 120, 24], Y: [255, 200, 60], H: [255, 248, 210],
});
save(lure, path.join(assets, 'textures', 'item', 'abyssal_lure.png'));
const shard = sprite([
	'................',
	'..........D.....',
	'.........DGD....',
	'........DGLGD...',
	'.......DGLWLD...',
	'......DGLWLGD...',
	'.....DGLWLGD....',
	'....DGLLLGGD....',
	'...DGGLLGGD.....',
	'...DGGGGGD......',
	'..DkGGGGD.......',
	'..DkkGGD........',
	'..DkkkD.........',
	'...DDD..........',
	'................',
	'................',
], {
	D: [14, 40, 22], k: [28, 70, 40], G: [70, 170, 70], L: [150, 230, 120], W: [236, 255, 220],
});
// a few star glints inside the crystal (it was born in a cosmic storm)
set(shard, 9, 6, [255, 255, 255]); set(shard, 6, 9, [220, 255, 255]); set(shard, 11, 3, [255, 255, 255]);
save(shard, path.join(assets, 'textures', 'item', 'tokustar_shard.png'));
console.log('icons written');
