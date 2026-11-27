// v0.17.7 Captain America suit look: the "Captain America | Classic" skin from PlanetMinecraft with the shield painted on its back
// taken off, drawn skin-tight over the wearer (QuirkCostume, the same path as the Battle Suit / Undead / Gyro suits).
//
//   node scratchpad/v0177/make_cap_suit.js
//
// History: the user asked "make this the new captain America armour please:
// https://www.planetminecraft.com/skin/captain-america-classic-6692155/ Remove the shield from the back of the suit and dont make the
// armour bulky please like how it is right now, armour should be diamond level". It replaces the v0.14.9 bulky GeckoLib suit
// (geo/captain_america.geo.json + textures/armor/captain_america.png, both deleted).
//
// Source (NEVER modified): scratchpad/v0177/cap_skin_original.png -- "Captain America | Classic" on PlanetMinecraft (page model:
// Steve = WIDE 4-pixel arms), the 64x64 file behind the page's download button (PMC file 19513766):
//   https://www.planetminecraft.com/skin/captain-america-classic-6692155/download/file/19513766/
//   static file: https://static.planetminecraft.com/files/resource_media/skin/captainamerica2-19513766.png
// (planetminecraft.com sits behind a Cloudflare check: curl gets a 403, so it was fetched from inside a real browser tab.)
// Borrowed skin: it ships publicly like the others (CLAUDE.md); the source is noted here and in SuperSoldierArmorItem.
//
// Writes (textures/entity/supersoldier/):
//   captain_america_wide.png / captain_america_slim.png   the suit, 64x64 vanilla skin layout, both layers, transparent where the
//                                                         wearer's own skin shows; the slim copy drops each 4-wide arm face's
//                                                         most-alike middle column (same rule as the Battle Suit script)
// and the offline previews in docs/screenshots/v0177/cap_suit/.
//
// What the source paints, and what this does with it:
//  * The SHIELD is only on the second layer: the jacket's back face (u 32-39, v 37-44, round red / white rings, blue centre, white
//    star). Those texels are cleared. Under it the base layer is already the suit's back (navy yoke, scale mail, the red / white
//    stripes, navy waist), except one plain navy band at v 21 (the shield's strap line, hidden by the shield): it gets the scale-mail
//    row the rest of the back uses (v 23), so the back reads as one continuous suit. The belt (v 45, second layer) stays.
//  * The FACE: the cowl covers the head (blue, white "A" with wings) with the lower face open. Every skin-tone texel of the head and
//    hat (nose, mouth, chin, the two ears, the chin's underside) is cleared, and the mask's painted eyes (white + blue, v 12) are cleared
//    too, so the wearer's own eyes, nose and mouth show through the cowl's openings (the Battle Suit cowl does the same). The cowl's
//    hat layer has pixels, so QuirkCostume hides the wearer's hat layer (hair) under the helmet.
//  * Nothing else is bare skin (gloves and boots are painted), nothing else changes.
const fs = require('fs');
const os = require('os');
const path = require('path');
const PNG = require('../pnglib.js');
const preview = require('./skin_preview.js');

const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(__dirname, 'cap_skin_original.png');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/supersoldier');
const SHOTS = path.join(ROOT, 'docs/screenshots/v0177/cap_suit');
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
const load = f => { const s = PNG.read(f); return new Img(s.W, s.H, s.px); };

const src = load(SRC);
if (src.w !== 64 || src.h !== 64) throw new Error('expected a 64x64 skin, got ' + src.w + 'x' + src.h);
const suit = src.copy();
const counts = { shield: 0, band: 0, face: 0, eyes: 0 };

// ---------------------------------------------------------------- 1. the shield on the back (second layer only)
// jacket back face: u 32-39, v 36-47; the shield fills v 37-44, the belt at v 45 stays
for (let y = 36; y <= 44; y++) for (let x = 32; x <= 39; x++) {
	if (suit.get(x, y)[3]) { suit.clear(x, y); counts.shield++; }
}
// sanity: nothing of the shield's red / white rings may be left anywhere on the jacket's back face above the belt
for (let y = 36; y <= 44; y++) for (let x = 32; x <= 39; x++) if (suit.get(x, y)[3]) throw new Error('shield texel left at ' + x + ',' + y);

// ---------------------------------------------------------------- 2. the strap band under it (base body back, v 21)
for (let x = 33; x <= 38; x++) { suit.set(x, 21, suit.get(x, 23)); counts.band++; }

// ---------------------------------------------------------------- 3. the face opening
const isSkinTone = ([r, g, b, a]) => a > 0 && r > 180 && r > b + 20 && g > 120 && r - g < 90 && !(r > 240 && g > 240 && b > 240);
for (const [x0, y0] of [[0, 0], [32, 0]]) { // head, hat
	for (let y = y0; y < y0 + 16; y++) for (let x = x0; x < x0 + 32; x++) {
		if (isSkinTone(suit.get(x, y))) { suit.clear(x, y); counts.face++; }
	}
}
// the mask's painted eyes (front face u 8-15, v 12): white / light grey and the blue irises, between the navy mask edges
for (let x = 9; x <= 14; x++) {
	const c = suit.get(x, 12);
	const white = c[0] > 200 && c[1] > 200 && c[2] > 200;
	const iris = c[2] > 200 && c[0] < 40;
	if (c[3] && (white || iris)) { suit.clear(x, 12); counts.eyes++; }
}

// ---------------------------------------------------------------- 4. wide -> slim
const rgbDiff = (a, b) => { if (!a[3] && !b[3]) return 0; if (!a[3] || !b[3]) return 400; return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]); };
const ARMS = [[40, 16], [40, 32], [32, 48], [48, 48]];
function armFaces(u, v, w) { const d = 4, h = 12; return { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] }; }
function readCols(img, [fx, fy, fw, fh]) { const cols = []; for (let x = 0; x < fw; x++) { const col = []; for (let y = 0; y < fh; y++) col.push(img.get(fx + x, fy + y)); cols.push(col); } return cols; }
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
const slim = wideToSlim(suit);

suit.save(path.join(OUT, 'captain_america_wide.png'));
slim.save(path.join(OUT, 'captain_america_slim.png'));

// ---------------------------------------------------------------- previews
// a plain test wearer (skin-tone face with eyes / mouth, brown hair, grey shirt, sleeves to the elbow, dark trousers)
function testSkin() {
	const img = new Img(64, 64);
	const faces = (u, v, w, h, d) => ({ top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] });
	const P = { head: faces(0, 0, 8, 8, 8), body: faces(16, 16, 8, 12, 4), rightArm: faces(40, 16, 4, 12, 4), leftArm: faces(32, 48, 4, 12, 4), rightLeg: faces(0, 16, 4, 12, 4), leftLeg: faces(16, 48, 4, 12, 4) };
	const SKIN = [0xd6, 0xa6, 0x82], SHIRT = [0x7a, 0x7a, 0x7a], HAIR = [0x5a, 0x3c, 0x22], PANTS = [0x3a, 0x4a, 0x6a];
	for (const [name, part] of Object.entries(P)) for (const [f, [u, v, w, h]] of Object.entries(part)) for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = name === 'head' ? (y < 2 || f === 'back' || f === 'top' ? HAIR : SKIN) : name.includes('Leg') ? PANTS : name.includes('Arm') && y >= 8 ? SKIN : SHIRT;
		img.set(u + x, v + y, c);
	}
	const [fu, fv] = P.head.front;
	img.set(fu + 1, fv + 4, [255, 255, 255]); img.set(fu + 2, fv + 4, [0x30, 0x60, 0xc0]); img.set(fu + 5, fv + 4, [0x30, 0x60, 0xc0]); img.set(fu + 6, fv + 4, [255, 255, 255]);
	img.set(fu + 3, fv + 6, [0x90, 0x50, 0x40]); img.set(fu + 4, fv + 6, [0x90, 0x50, 0x40]);
	return img;
}
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'cap_suit_'));
const wearerWide = testSkin(), wearerSlim = wideToSlim(testSkin());
wearerWide.save(path.join(tmp, 'wearer_wide.png'));
wearerSlim.save(path.join(tmp, 'wearer_slim.png'));
// overlay a suit on a wearer: the suit's texels win where they're opaque (both layers, like the game draws them)
preview.render([src.asPreview(), suit.asPreview()], path.join(SHOTS, 'original_vs_processed.png'));
preview.render([suit.asPreview()], path.join(SHOTS, 'processed_on_wide_wearer.png'), { underlay: path.join(tmp, 'wearer_wide.png') });
preview.render([slim.asPreview()], path.join(SHOTS, 'processed_on_slim_wearer.png'), { slim: true, underlay: path.join(tmp, 'wearer_slim.png') });
// a partial set as the game splits it (QuirkCostume): chestplate (body + arms, both layers) + boots (legs from leg row 7 down)
const partial = new Img(64, 64);
const keepRect = (x0, y0, w, h) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) partial.set(x, y, suit.get(x, y)); };
keepRect(16, 16, 40, 16); keepRect(16, 32, 40, 16); keepRect(32, 48, 32, 16); // body / arms, jacket / sleeves, left arm + sleeve
for (const [u, v] of [[0, 16], [16, 48], [0, 32], [0, 48]]) { keepRect(u + 8, v, 4, 4); keepRect(u, v + 4 + 7, 16, 5); } // leg soles + boot rows
preview.render([partial.asPreview()], path.join(SHOTS, 'partial_chest_boots_on_wide_wearer.png'), { underlay: path.join(tmp, 'wearer_wide.png') });
fs.rmSync(tmp, { recursive: true, force: true });
console.log(JSON.stringify(counts));
