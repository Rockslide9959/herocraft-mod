// v0.17.7 Iron Man suits from PlanetMinecraft skins: every texture (suit, glowmask), the thin skin-tight geos and the offline
// previews, generated from the six untouched originals in scratchpad/v0177/im_skins/. Repeatable:
//
//   node scratchpad/v0177/make_im_suits.js
//
// The user's request: "Use these skins for the iron man suits ... Mark 4 Hydro armour ... just put the glass helmet over the skin
// please". Borrowed skins: they ship publicly like the others (CLAUDE.md); the sources are noted here and in SuperheroArmorVisuals.
//
// Sources (NEVER modified; all 64x64, page model Steve = WIDE 4-pixel arms). planetminecraft.com sits behind a Cloudflare check
// (curl gets a 403), so each file was fetched from inside a real browser tab: the page's Download button link returns the PNG.
//   mark1_original.png             "Iron Man Model 1"            https://www.planetminecraft.com/skin/iron-man-model-1-6258811/
//                                    file https://www.planetminecraft.com/skin/iron-man-model-1-6258811/download/file/17738731/
//   mark3_original.png             "Iron Man Model 4"            https://www.planetminecraft.com/skin/iron-man-model-4/
//                                    file https://www.planetminecraft.com/skin/iron-man-model-4/download/file/18572462/
//   mark4_hydro_original.png       "Iron Man Model 8 - Hydro Armor"
//                                    https://www.planetminecraft.com/skin/iron-man-model-8-hydro-armor/  (file 19463758)
//   mark6_renaissance_original.png "Iron Man Model 16 - Renaissance/Heroes Return Armor"
//                                    https://www.planetminecraft.com/skin/iron-man-model-16-renaissance-heroes-return-armor/  (file 19463770)
//   mark7_centurion_original.png   "Iron Man Model 7 - Silver Centurion Armor"
//                                    https://www.planetminecraft.com/skin/iron-man-model-7-silver-centurion-armor/  (file 19463754)
//   hulkbuster_original.png        "Iron Man Model 13"           https://www.planetminecraft.com/skin/iron-man-model-13/  (file 18572466)
//
// Mapping (the user's names -> the mod's suits; item / suit ids unchanged):
//   "mark 1"                  -> mark_1    (Mk I Cave-Forged)
//   "mark 3"                  -> mark_iii  (Mk III Classic)
//   "Mark 4 Hydro armour"     -> mark_4    (Mk IV Hydro) + a glass dome over the head
//   "mark 6"                  -> mark_2    (Mk VI Suitcase: the mark_2 id has been the Mk VI since the v0.16.7 Era I renames)
//   "Mark 7 Silver Centurion" -> mark_6    (Mk VII Silver Centurion: the mark_6 id)
//   Hulkbuster                -> the Hulkbuster body (textures/armor/hulkbuster_body.png, the player-shaped GeckoLib body at
//                                Hulkbuster scale; its geo already maps every part onto the 64x64 skin layout, both layers)
//
// Writes (src/main/resources/assets/projecthero/):
//   textures/armor/<set>.png             the skin, vanilla layout, both layers. The hat layer's FRONT face is merged onto the base
//                                        face front (the faceplate is one solid plate that draws the face; H lifts it) and cleared.
//                                        Every unused skin region is cleared (the PMC files carry a few stray texels there). The
//                                        Hydro adds its glass (u 56-63, v 16-31: dome sides, then dome top), translucent light blue
//                                        with a diagonal reflection streak and a brighter rim.
//   textures/armor/<set>_glowmask.png    emissive map (RenderType.eyes, IronManSuitGlowLayer): the skin's own bright texels
//                                        (white / cream, never the gold or silver plating) on the face front, the chest front
//                                        (arc reactor) and the boot soles, plus a 2x2 palm repulsor on each glove's underside
//                                        (not the Mark 1: no repulsors). Battle damage / the build reveal read it as before.
//   geo/<set>.geo.json                   the skin-tight rig (same bones / pivots as before, so the assembly, gantry, sentry doors,
//                                        bracelet wrap and battle damage keep working): base layer 0.05 px over the body, outer layer
//                                        at vanilla's overlay inflation (hat 0.5, jacket / sleeves / trousers 0.25), legs moved 0.1
//                                        px out (GeckoLib draws leg bones at x +/-2, vanilla legs sit at +/-1.9), thigh = leg rows
//                                        0-7, boot = rows 8-11, the faceplate one solid plate in front of the face. Mark 4: + the
//                                        glass dome cube on the helmet bone (no pauldrons any more).
//   textures/armor/hulkbuster_body.png   the Hulkbuster skin (the old body's small joint-cap palette strip u 56-59, v 16-19 kept,
//   + _glowmask.png                      the forearm / shin "up" caps sample it)
// and the offline previews in docs/screenshots/v0177/ironman_suits/ (flat front / right / back / left on a plain wide and slim
// wearer; slim = each 4-wide arm face squeezed to 3 px, the way the game draws a slim wearer's arms: SuperheroArmorRenderer narrows
// the arm cubes by one pixel, so ONE wide texture serves both and every derived texture -- build reveal, battle damage, glowmask
// -- stays aligned).
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');
const PNG = require('../pnglib.js');
const preview = require('./skin_preview.js');

const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(__dirname, 'im_skins');
const RES = path.join(ROOT, 'src/main/resources/assets/projecthero');
const SHOTS = path.join(ROOT, 'docs/screenshots/v0177/ironman_suits');
fs.mkdirSync(SHOTS, { recursive: true });

class Img {
	constructor(w, h, px) { this.w = w; this.h = h; this.d = px ? Buffer.from(px) : Buffer.alloc(w * h * 4); }
	get(x, y) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return [0, 0, 0, 0]; const i = (y * this.w + x) * 4; return [this.d[i], this.d[i + 1], this.d[i + 2], this.d[i + 3]]; }
	set(x, y, c) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c[3] === undefined ? 255 : c[3]; }
	clear(x, y) { this.set(x, y, [0, 0, 0, 0]); }
	copy() { return new Img(this.w, this.h, this.d); }
	save(file) { PNG.write(file, this.w, this.h, this.d); console.log('wrote', path.relative(ROOT, file)); }
	asPreview() { return { W: this.w, H: this.h, px: this.d }; }
}
const load = f => { const s = PNG.read(f); if (s.W !== 64 || s.H !== 64) throw new Error(f + ': expected 64x64'); return new Img(s.W, s.H, s.px); };
const inRect = (x, y, [x0, y0, w, h]) => x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;

const SUITS = [
	{ src: 'mark1', set: 'mark_1', palms: false },
	{ src: 'mark3', set: 'mark_iii', palms: true },
	{ src: 'mark4_hydro', set: 'mark_4', palms: true, dome: true },
	{ src: 'mark6_renaissance', set: 'mark_2', palms: true },
	{ src: 'mark7_centurion', set: 'mark_6', palms: true },
];

// ---------------------------------------------------------------- skin layout
// every region a 64x64 skin actually uses (both layers); anything else is cleared
const USED = (() => {
	const r = [];
	const box = (u, v, w, h, d) => { r.push([u + d, v, 2 * w, d]); r.push([u, v + d, 2 * (w + d), h]); };
	box(0, 0, 8, 8, 8); box(32, 0, 8, 8, 8);        // head, hat
	box(16, 16, 8, 12, 4); box(16, 32, 8, 12, 4);   // body, jacket
	box(40, 16, 4, 12, 4); box(40, 32, 4, 12, 4);   // right arm, sleeve
	box(32, 48, 4, 12, 4); box(48, 48, 4, 12, 4);   // left arm, sleeve
	box(0, 16, 4, 12, 4); box(0, 32, 4, 12, 4);     // right leg, trousers
	box(16, 48, 4, 12, 4); box(0, 48, 4, 12, 4);    // left leg, trousers
	return r;
})();
const used = (x, y) => USED.some(r => inRect(x, y, r));

// ---------------------------------------------------------------- the suit texture
const DOME_SIDE = [56, 16, 8, 8], DOME_TOP = [56, 24, 8, 8];

function suitTexture(src, dome) {
	const t = src.copy();
	const counts = { stray: 0, face: 0 };
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (!used(x, y) && t.get(x, y)[3]) { t.clear(x, y); counts.stray++; }
	// the hat front (u 40-47, v 8-15) composited onto the face front (u 8-15, v 8-15): the faceplate plate draws it
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		const o = t.get(40 + x, 8 + y), b = t.get(8 + x, 8 + y);
		if (o[3]) {
			const a = o[3] / 255;
			t.set(8 + x, 8 + y, [0, 1, 2].map(c => Math.round(o[c] * a + b[c] * (1 - a))).concat([Math.max(b[3], o[3])]));
			counts.face++;
		}
		t.clear(40 + x, 8 + y);
	}
	if (dome) paintDome(t);
	return { t, counts };
}

/** The Hydro's glass: light-blue tint, a diagonal reflection streak, a brighter rim; the top a soft highlight. */
function paintDome(t) {
	const [sx, sy] = DOME_SIDE, [tx, ty] = DOME_TOP;
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		let c = [150, 205, 245, 64];
		const d = x - y;
		if (d === 2 || d === 3) c = [225, 245, 255, 132];       // reflection streak
		else if (d === 4 && y > 0) c = [200, 232, 255, 96];
		if (y === 0) c = [205, 236, 255, 120];                 // rim
		if (y === 7) c = [120, 175, 215, 104];                 // collar seam
		t.set(sx + x, sy + y, c);
		// top: highlight off-centre toward the front-left
		const r = Math.hypot(x - 2.5, y - 2.5);
		const top = r < 1.6 ? [235, 250, 255, 140] : r < 3 ? [190, 228, 255, 92] : [150, 205, 245, 64];
		t.set(tx + x, ty + y, (x === 0 || y === 0 || x === 7 || y === 7) ? [205, 236, 255, 116] : top);
	}
}

// ---------------------------------------------------------------- glowmask
// where lights may be: face front (base; the hat front is merged into it), chest front (reactor) on both layers, boot soles,
// glove undersides (palms) on both layers
// (face: the eye rows v 10-13 only -- the Renaissance skin paints its brow trim and mouth slit white too, which read as teeth lit up)
const GLOW_AREAS = [[8, 10, 8, 4], [20, 20, 8, 12], [20, 36, 8, 12], [8, 16, 4, 4], [24, 48, 4, 4], [8, 32, 4, 4], [8, 48, 4, 4],
	[48, 16, 4, 4], [48, 32, 4, 4], [40, 48, 4, 4], [56, 48, 4, 4]];
const PALMS = [[48, 16], [40, 48]]; // right / left glove underside (base layer), 4x4
const bright = ([r, g, b, a]) => a > 0 && r > 230 && g > 220 && b > 170; // white / cream lenses + reactor, never gold / silver plate

function glowmask(t, palms) {
	const g = new Img(64, 64);
	let n = 0;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = t.get(x, y);
		if (GLOW_AREAS.some(r => inRect(x, y, r)) && bright(c)) { g.set(x, y, [c[0], c[1], c[2], 255]); n++; }
	}
	if (palms) for (const [u, v] of PALMS) for (let y = 1; y < 3; y++) for (let x = 1; x < 3; x++) { g.set(u + x, v + y, [205, 240, 255, 255]); n++; }
	return { g, n };
}

// ---------------------------------------------------------------- the skin-tight geo
const BASE = 0.05, OUTER = 0.25, HAT = 0.5, LEG_OUT = 0.1;
const faces = (u, v, w, h, d, which) => {
	const all = { east: [u, v + d, d, h], north: [u + d, v + d, w, h], west: [u + d + w, v + d, d, h], south: [u + d + w + d, v + d, w, h], up: [u + d, v, w, d], down: [u + d + w, v, w, d] };
	const o = {};
	for (const f of which) o[f] = { uv: [all[f][0], all[f][1]], uv_size: [all[f][2], all[f][3]] };
	return o;
};
const r4 = n => Math.round(n * 10000) / 10000;

function legCubes(right, base) {
	// leg box (vanilla) x -4..0 (right) / 0..4 (left), moved LEG_OUT outward; thigh = rows 0-7 (y 4..12), boot = rows 8-11 (y 0..4)
	const g = base ? BASE : OUTER;
	const x0 = right ? -4 - LEG_OUT : LEG_OUT;
	const [u, v] = right ? (base ? [0, 16] : [0, 32]) : (base ? [16, 48] : [0, 48]);
	const side = (row0, rows) => {
		const d = 4, w = 4;
		return {
			east: { uv: [u, v + d + row0], uv_size: [d, rows] }, north: { uv: [u + d, v + d + row0], uv_size: [w, rows] },
			west: { uv: [u + d + w, v + d + row0], uv_size: [d, rows] }, south: { uv: [u + d + w + d, v + d + row0], uv_size: [w, rows] },
		};
	};
	const thigh = { origin: [r4(x0 - g), 4, r4(-2 - g)], size: [r4(4 + 2 * g), r4(8 + g), r4(4 + 2 * g)], uv: { ...side(0, 8), up: { uv: [u + 4, v], uv_size: [4, 4] } } };
	const boot = { origin: [r4(x0 - g), r4(-g), r4(-2 - g)], size: [r4(4 + 2 * g), r4(4 + g), r4(4 + 2 * g)], uv: { ...side(8, 4), down: { uv: [u + 8, v], uv_size: [4, 4] } } };
	return { thigh, boot };
}

function skinRig(oldGeo, set, dome) {
	const geo = JSON.parse(JSON.stringify(oldGeo));
	const m = geo['minecraft:geometry'][0];
	m.description.identifier = 'geometry.' + set;
	const cubes = {
		helmet: [
			{ origin: [-4, 24, -4], size: [8, 8, 8], inflate: BASE, uv: faces(0, 0, 8, 8, 8, ['east', 'west', 'south', 'up', 'down']) },
			{ origin: [-4, 24, -4], size: [8, 8, 8], inflate: HAT, uv: faces(32, 0, 8, 8, 8, ['east', 'west', 'south', 'up', 'down']) },
		],
		// one solid plate in front of the face, flush with the hat layer's front edge: the face (hat front merged in)
		faceplate: [{ origin: [-4.5, 23.5, -4.5], size: [9, 9, 0.45], uv: {
			north: { uv: [8, 8], uv_size: [8, 8] }, south: { uv: [16, 0], uv_size: [8, 8] }, east: { uv: [15, 8], uv_size: [1, 8] },
			west: { uv: [8, 8], uv_size: [1, 8] }, up: { uv: [8, 8], uv_size: [8, 1] }, down: { uv: [8, 15], uv_size: [8, 1] } } }],
		chest: [{ origin: [-4, 12, -2], size: [8, 12, 4], inflate: BASE, uv: [16, 16] }, { origin: [-4, 12, -2], size: [8, 12, 4], inflate: OUTER, uv: [16, 32] }],
		right_upper_arm: [{ origin: [-8, 12, -2], size: [4, 12, 4], inflate: BASE, uv: [40, 16] }, { origin: [-8, 12, -2], size: [4, 12, 4], inflate: OUTER, uv: [40, 32] }],
		left_upper_arm: [{ origin: [4, 12, -2], size: [4, 12, 4], inflate: BASE, uv: [32, 48] }, { origin: [4, 12, -2], size: [4, 12, 4], inflate: OUTER, uv: [48, 48] }],
	};
	for (const right of [true, false]) {
		const b = legCubes(right, true), o = legCubes(right, false);
		cubes[(right ? 'right' : 'left') + '_thigh'] = [b.thigh, o.thigh];
		cubes[(right ? 'right' : 'left') + '_boot'] = [b.boot, o.boot];
	}
	if (dome) {
		// the Hydro's glass helmet: a translucent box a half pixel clear of the hat layer, open at the neck (no down face). On the
		// helmet bone, so it builds on with the helmet; its front goes with the faceplate's front when H lifts it.
		const side = { uv: [DOME_SIDE[0], DOME_SIDE[1]], uv_size: [8, 8] };
		cubes.helmet.push({ origin: [-5, 23.4, -5], size: [10, 9.6, 10], uv: { north: side, east: side, west: side, south: side,
			up: { uv: [DOME_TOP[0], DOME_TOP[1]], uv_size: [8, 8] } } });
	}
	for (const b of m.bones) {
		if (cubes[b.name]) b.cubes = cubes[b.name];
		else delete b.cubes; // nothing but the skin model (the old Hydro pauldrons go)
		if (b.name === 'faceplate') b.pivot = [0, 32.5, -4.5];
	}
	return geo;
}

// ---------------------------------------------------------------- previews
const rgbDiff = (a, b) => { if (!a[3] && !b[3]) return 0; if (!a[3] || !b[3]) return 400; return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]); };
const ARMS = [[40, 16], [40, 32], [32, 48], [48, 48]];
function armFaces(u, v, w) { const d = 4, h = 12; return { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] }; }
function readCols(img, [fx, fy, fw, fh]) { const cols = []; for (let x = 0; x < fw; x++) { const col = []; for (let y = 0; y < fh; y++) col.push(img.get(fx + x, fy + y)); cols.push(col); } return cols; }
/** Preview only: a wide skin as a slim one (each 4-wide arm face loses its most-alike middle column). */
function wideToSlim(wide) {
	const slim = wide.copy();
	for (const [u, v] of ARMS) {
		const W = armFaces(u, v, 4), S = armFaces(u, v, 3);
		const cols = {};
		for (const f of Object.keys(W)) cols[f] = readCols(wide, W[f]);
		let drop = 1, best = Infinity;
		for (const c of [1, 2]) {
			let cost = 0;
			for (let y = 0; y < 12; y++) cost += Math.min(rgbDiff(cols.front[c][y], cols.front[c - 1][y]), rgbDiff(cols.front[c][y], cols.front[c + 1][y]));
			if (cost < best) { best = cost; drop = c; }
		}
		for (let y = v; y < v + 16; y++) for (let x = u; x < u + 16; x++) slim.clear(x, y);
		for (const f of Object.keys(S)) {
			const [sx, sy] = S[f];
			const keep = (f === 'right' || f === 'left') ? cols[f] : cols[f].filter((_, i) => i !== drop);
			keep.forEach((col, x) => col.forEach((c, y) => slim.set(sx + x, sy + y, c)));
		}
	}
	return slim;
}
function testSkin() {
	const img = new Img(64, 64);
	const fc = (u, v, w, h, d) => ({ top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] });
	const P = { head: fc(0, 0, 8, 8, 8), body: fc(16, 16, 8, 12, 4), rightArm: fc(40, 16, 4, 12, 4), leftArm: fc(32, 48, 4, 12, 4), rightLeg: fc(0, 16, 4, 12, 4), leftLeg: fc(16, 48, 4, 12, 4) };
	const SKIN = [0xd6, 0xa6, 0x82], SHIRT = [0x7a, 0x7a, 0x7a], HAIR = [0x5a, 0x3c, 0x22], PANTS = [0x3a, 0x4a, 0x6a];
	for (const [name, part] of Object.entries(P)) for (const [f, [u, v, w, h]] of Object.entries(part)) for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = name === 'head' ? (y < 2 || f === 'back' || f === 'top' ? HAIR : SKIN) : name.includes('Leg') ? PANTS : name.includes('Arm') && y >= 8 ? SKIN : SHIRT;
		img.set(u + x, v + y, c);
	}
	return img;
}
/** The glowmask lit on a darkened copy of the suit (what the eyes / reactor / palms look like at night). */
function nightView(t, g) {
	const o = t.copy();
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = o.get(x, y), l = g.get(x, y);
		if (!c[3]) continue;
		o.set(x, y, l[3] ? [l[0], l[1], l[2], 255] : [c[0] * 0.25, c[1] * 0.25, c[2] * 0.3, c[3]].map(Math.round));
	}
	return o;
}

// ---------------------------------------------------------------- run
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'im_suits_'));
const wearerWide = testSkin(), wearerSlim = wideToSlim(testSkin());
wearerWide.save(path.join(tmp, 'wearer_wide.png'));
wearerSlim.save(path.join(tmp, 'wearer_slim.png'));
const report = {};
const sheetWide = [], sheetSlim = [], sheetNight = [];
for (const s of SUITS) {
	const src = load(path.join(SRC, s.src + '_original.png'));
	const { t, counts } = suitTexture(src, !!s.dome);
	const { g, n } = glowmask(t, s.palms);
	t.save(path.join(RES, 'textures/armor', s.set + '.png'));
	g.save(path.join(RES, 'textures/armor', s.set + '_glowmask.png'));
	const geoFile = path.join(RES, 'geo', s.set + '.geo.json');
	const geo = skinRig(JSON.parse(fs.readFileSync(geoFile, 'utf8')), s.set, !!s.dome);
	fs.writeFileSync(geoFile, (JSON.stringify(geo, null, '\t') + '\n').replace(/\r?\n/g, '\r\n')); // the geos are CRLF
	console.log('wrote', path.relative(ROOT, geoFile));
	report[s.set] = { ...counts, glow: n };
	sheetWide.push(t.asPreview());
	sheetSlim.push(wideToSlim(t).asPreview());
	sheetNight.push(nightView(t, g).asPreview());
	preview.render([src.asPreview(), t.asPreview()], path.join(SHOTS, s.set + '_original_vs_suit.png'));
}
preview.render(sheetWide, path.join(SHOTS, 'suits_on_wide_wearer.png'), { underlay: path.join(tmp, 'wearer_wide.png') });
preview.render(sheetSlim, path.join(SHOTS, 'suits_on_slim_wearer.png'), { slim: true, underlay: path.join(tmp, 'wearer_slim.png') });
preview.render(sheetNight, path.join(SHOTS, 'suits_glow_at_night.png'));

// ---------------------------------------------------------------- the Hulkbuster body
{
	const src = load(path.join(SRC, 'hulkbuster_original.png'));
	const bodyFile = path.join(RES, 'textures/armor/hulkbuster_body.png');
	const old = load(bodyFile);
	const t = src.copy();
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (!used(x, y)) t.clear(x, y);
	// the joint-cap palette strip the body's forearm / shin "up" faces sample (u 56-59, v 16-19)
	for (let y = 16; y < 20; y++) for (let x = 56; x < 60; x++) t.set(x, y, old.get(x, y));
	// the body draws BOTH layers (base cubes + an outer shell per segment), so the hat front stays where it is; its face front
	// lights come from either layer
	const g = new Img(64, 64);
	let n = 0;
	const areas = GLOW_AREAS.concat([[40, 10, 8, 4]]);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = t.get(x, y);
		if (areas.some(r => inRect(x, y, r)) && bright(c)) { g.set(x, y, [c[0], c[1], c[2], 255]); n++; }
	}
	for (const [u, v] of PALMS) for (let y = 1; y < 3; y++) for (let x = 1; x < 3; x++) { g.set(u + x, v + y, [205, 240, 255, 255]); n++; }
	t.save(bodyFile);
	g.save(path.join(RES, 'textures/armor/hulkbuster_body_glowmask.png'));
	report.hulkbuster_body = { glow: n };
	preview.render([src.asPreview(), t.asPreview(), nightView(t, g).asPreview()], path.join(SHOTS, 'hulkbuster_original_suit_night.png'));
}
fs.rmSync(tmp, { recursive: true, force: true });
console.log(JSON.stringify(report));
