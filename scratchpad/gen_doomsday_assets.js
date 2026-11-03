// v0.17.2 Doomsday: textures. Usage: node scratchpad/gen_doomsday_assets.js <devastator.png>
// The skin is NovaSkin post 4845083152 ("Batman The Devastator", 128x128 HD), fetched through the browser.
const fs = require('fs');
const path = require('path');
const k = require('./pngkit.js');

const root = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const ent = path.join(root, 'entity', 'doomsday');
const item = path.join(root, 'item');
const block = path.join(root, 'block');
for (const d of [ent, item, block]) fs.mkdirSync(d, { recursive: true });

let seed = 1337;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const img = (w, h) => ({ w, h, px: Buffer.alloc(w * h * 4) });
const set = (im, x, y, r, g, b, a = 255) => { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = r; im.px[i + 1] = g; im.px[i + 2] = b; im.px[i + 3] = a; };
const get = (im, x, y) => { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; };
const save = (im, p) => fs.writeFileSync(p, k.encode(im.w, im.h, im.px));
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));

// ---- the skin, its glowmask (the red eyes on the head / hat front) and the stone overlay
const skin = k.decode(process.argv[2]);
if (skin.w !== 128 || skin.h !== 128) throw new Error('expected a 128x128 skin');
save(skin, path.join(ent, 'doomsday.png'));
const glow = img(128, 128);
for (const [x0, y0] of [[16, 16], [80, 16]]) {
	for (let y = y0; y < y0 + 16; y++) for (let x = x0; x < x0 + 16; x++) {
		const [r, g, b, a] = get(skin, x, y);
		if (a > 0 && r > 120 && g < 90 && b < 90) set(glow, x, y, 255, Math.min(255, g + 40), Math.min(255, b + 30), 255);
	}
}
save(glow, path.join(ent, 'doomsday_glowmask.png'));
// stone: blotchy grey patches only where the base skin is opaque (never on the hat / jacket layers' empty space)
const stone = img(128, 128);
const blobs = [];
for (let i = 0; i < 70; i++) blobs.push([rnd() * 128, rnd() * 128, 1.5 + rnd() * 3.5]);
for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) {
	const [, , , a] = get(skin, x, y);
	if (a < 200) continue;
	let v = 0;
	for (const [bx, by, br] of blobs) { const d = Math.hypot(x - bx, y - by); if (d < br) v = Math.max(v, 1 - d / br); }
	if (v <= 0.15) continue;
	const n = 0.75 + rnd() * 0.35;
	const g = clamp(118 * n + v * 30);
	set(stone, x, y, g, clamp(g * 0.98), clamp(g * 0.94), clamp(150 + v * 105));
}
save(stone, path.join(ent, 'doomsday_stone.png'));

// ---- bone (16x16, v 0 at the tip): ivory, darker toward the base, with hairline cracks
const bone = img(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
	const t = y / 15;
	const n = rnd() * 14;
	set(bone, x, y, clamp(238 - t * 52 + n), clamp(230 - t * 58 + n), clamp(206 - t * 66 + n));
}
for (let c = 0; c < 4; c++) { let x = Math.floor(rnd() * 16); for (let y = 4 + c * 3; y < 16; y++) { set(bone, x, y, 120, 110, 92); if (rnd() < 0.4) x += rnd() < 0.5 ? -1 : 1; } }
save(bone, path.join(ent, 'bone.png'));

// ---- cable (16x16): dark green burial cloth with grey metal binding
const cable = img(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
	const n = rnd() * 16;
	if (y < 3 || y > 12) set(cable, x, y, clamp(92 + n), clamp(96 + n), clamp(100 + n));
	else set(cable, x, y, clamp(44 + n * 0.6), clamp(72 + n), clamp(48 + n * 0.6));
	if ((y === 1 || y === 14) && x % 4 === 1) set(cable, x, y, 168, 170, 172);
}
save(cable, path.join(ent, 'cable.png'));

// ---- the casket (32x32, used per face): gunmetal-green panels, rivets, a Calatonian glyph
const casket = img(32, 32);
for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
	const n = rnd() * 12;
	const edge = x < 2 || y < 2 || x > 29 || y > 29;
	const panel = x === 10 || x === 21 || y === 15;
	if (edge) set(casket, x, y, clamp(70 + n), clamp(76 + n), clamp(78 + n));
	else if (panel) set(casket, x, y, clamp(26 + n), clamp(36 + n), clamp(30 + n));
	else set(casket, x, y, clamp(46 + n), clamp(64 + n), clamp(52 + n));
	if (edge && (x + y) % 6 === 0) set(casket, x, y, 150, 154, 150);
}
const glyph = ['..XXXX..', '.X....X.', 'X..XX..X', 'X.X..X.X', 'X..XX..X', '.X....X.', '..X..X..', '...XX...'];
glyph.forEach((row, gy) => [...row].forEach((ch, gx) => { if (ch === 'X') set(casket, 12 + gx, 4 + gy, 150, 40, 32); }));
save(casket, path.join(ent, 'casket.png'));

// ---- block textures
const casketSide = img(16, 16), casketTop = img(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
	const n = rnd() * 12;
	const band = x === 3 || x === 4 || x === 11 || x === 12;
	set(casketSide, x, y, band ? clamp(56 + n) : clamp(44 + n), band ? clamp(84 + n) : clamp(60 + n), band ? clamp(58 + n) : clamp(50 + n));
	const edge = x === 0 || y === 0 || x === 15 || y === 15;
	set(casketTop, x, y, edge ? clamp(80 + n) : band ? clamp(58 + n) : clamp(46 + n), edge ? clamp(84 + n) : band ? clamp(86 + n) : clamp(64 + n),
		edge ? clamp(86 + n) : band ? clamp(60 + n) : clamp(52 + n));
}
glyph.forEach((row, gy) => [...row].forEach((ch, gx) => { if (ch === 'X') set(casketTop, 4 + gx, 4 + gy, 160, 42, 34); }));
save(casketSide, path.join(block, 'calatonian_casket_side.png'));
save(casketTop, path.join(block, 'calatonian_casket_top.png'));
// the skull block: the skin's head front (downsampled 16->8, scaled back up) framed in bone
const skull = img(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
	const [r, g, b] = get(skin, 16 + x, 16 + y);
	set(skull, x, y, r, g, b);
}
save(skull, path.join(block, 'doomsday_skull_front.png'));
const skullSide = img(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const [r, g, b] = get(skin, 0 + x, 16 + y); set(skullSide, x, y, r, g, b); }
save(skullSide, path.join(block, 'doomsday_skull_side.png'));
const skullTop = img(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const [r, g, b] = get(skin, 16 + x, 0 + y); set(skullTop, x, y, r, g, b); }
save(skullTop, path.join(block, 'doomsday_skull_top.png'));
save(bone, path.join(block, 'doomsday_bone.png'));

// ---- item icons (16x16 pixel art)
function sprite(rows, pal) {
	const im = img(16, 16);
	rows.forEach((row, y) => [...row].forEach((ch, x) => { if (pal[ch]) set(im, x, y, ...pal[ch]); }));
	return im;
}
save(sprite([
	'................',
	'.......W........',
	'......WCW.......',
	'......WCCW......',
	'.....WCCCW......',
	'.....WCcCCW.....',
	'....WCCcCCW.....',
	'....WCCccCCW....',
	'....WCCCcCCW....',
	'.....WCCcCW.....',
	'.....WCCCCW.....',
	'......WCCW......',
	'......WCW.......',
	'.......W........',
	'................',
	'................'], { W: [230, 250, 255], C: [150, 220, 245], c: [80, 160, 220] }), path.join(item, 'phantom_zone_shard.png'));
save(sprite([
	'................',
	'.....SSSSSS.....',
	'....SLLLLLLS....',
	'...SLCCCCCCLS...',
	'...SLCWWWWCLS...',
	'...SLCWccWCLS...',
	'...SLCWccWCLS...',
	'...SLCWWWWCLS...',
	'...SLCCCCCCLS...',
	'....SLLLLLLS....',
	'.....SSDDSS.....',
	'.......DD.......',
	'......DGGD......',
	'......DGGD......',
	'......DDDD......',
	'................'], { S: [120, 126, 136], L: [200, 206, 214], C: [120, 210, 240], W: [225, 250, 255], c: [255, 255, 255], D: [70, 74, 82], G: [150, 40, 30] }),
	path.join(item, 'phantom_zone_projector.png'));
save(sprite([
	'................',
	'..B...B...B.....',
	'..BB..BB..BB....',
	'.GGGGGGGGGGG....',
	'.GgGgGgGgGgG.B..',
	'.GGGGGGGGGGGBB..',
	'.GGGGGGGGGGGG...',
	'.GGGGGGGGGGGG...',
	'..GGGGGGGGGG....',
	'..GgGGGGGGgG.B..',
	'...GGGGGGGGGBB..',
	'...GCCCCCCCG....',
	'...GCcCcCcCG....',
	'...GCCCCCCCG....',
	'....GGGGGGG.....',
	'................'], { B: [236, 228, 204], G: [104, 106, 112], g: [140, 142, 148], C: [52, 78, 56], c: [150, 152, 150] }),
	path.join(item, 'doomsday_bone_gauntlet.png'));
console.log('ok');
