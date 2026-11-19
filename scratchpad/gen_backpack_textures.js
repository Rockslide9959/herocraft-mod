// v0.17.5 backpacks: item icons (a tinted canvas layer + a per-tier trim layer) and the worn-model textures (a tinted canvas
// sheet + a per-tier trim sheet, laid out for client/backpack/BackpackModel's cubes).
// Usage: node scratchpad/gen_backpack_textures.js   (run from the worktree root)
'use strict';
const fs = require('fs');
const path = require('path');
const tools = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');

const ITEM = 'src/main/resources/assets/projecthero/textures/item';
const ENT = 'src/main/resources/assets/projecthero/textures/entity/backpack';
fs.mkdirSync(ITEM, { recursive: true });
fs.mkdirSync(ENT, { recursive: true });

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function hex(c, a = 255) { const n = parseInt(c.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a]; }
function grey(v) { v = Math.max(0, Math.min(255, Math.round(v))); return [v, v, v, 255]; }
function set(im, x, y, c) { if (x < 0 || y < 0 || x >= im.w || y >= im.h || !c) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3]; }
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function rect(im, x, y, w, h, fn) { for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) { const c = fn(u, v, w, h, x + u, y + v); if (c) set(im, x + u, y + v, typeof c === 'string' ? hex(c) : c); } }
function box(ox, oy, w, h, d) {
	return { top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d], right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h] };
}
const write = (dir, name, im) => fs.writeFileSync(path.join(dir, name), tools.encode(im.w, im.h, im.px));

// ------------------------------------------------------------------ tiers
const TIERS = {
	leather: { strap: ['#2e1d0f', '#3d2715', '#4f331c'], metal: ['#8a6a24', '#c9a24a', '#ecd28a'], plates: false, emblem: null },
	iron: { strap: ['#26282c', '#33363b', '#43474d'], metal: ['#8c8f94', '#c9cbce', '#f1f2f3'], plates: true, emblem: null },
	gold: { strap: ['#2e1d0f', '#3d2715', '#4f331c'], metal: ['#a77a12', '#f2c935', '#fff1a0'], plates: true, emblem: null },
	diamond: { strap: ['#141c2c', '#1d2940', '#283855'], metal: ['#1d9c95', '#4fe0d6', '#c8fffa'], plates: true, emblem: ['#127873', '#4fe0d6', '#d9fffb'] },
	netherite: { strap: ['#161314', '#1e1a1b', '#2a2526'], metal: ['#3a3134', '#5b4f53', '#857479'], plates: true, emblem: ['#3a3134', '#7d5e8a', '#b495c4'] },
};

// ------------------------------------------------------------------ item icons (16x16)
// canvas: x 3..12, y 4..14, lid y 4..7, front pocket x 5..10 y 9..13; handle loop above
function inBag(x, y) {
	if (x < 3 || x > 12 || y < 4 || y > 14) return false;
	if ((x === 3 || x === 12) && (y === 4 || y === 14)) return false; // rounded corners
	return true;
}
function iconBase() {
	const im = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (!inBag(x, y)) continue;
		const edge = !inBag(x - 1, y) || !inBag(x + 1, y) || !inBag(x, y - 1) || !inBag(x, y + 1);
		let v = 196 + (hash(x, y, 3) - 0.5) * 22;
		if (x <= 4) v += 18; // light from the left
		if (x >= 11) v -= 26;
		if (y >= 4 && y <= 7) v -= 14; // the lid
		if (y === 8) v = 120; // lid shadow
		if (x >= 5 && x <= 10 && y >= 9 && y <= 13) { // pocket
			const pe = x === 5 || x === 10 || y === 13;
			v = pe ? 110 : 214 + (hash(x, y, 5) - 0.5) * 16;
			if (y === 10 && x > 5 && x < 10) v = 150; // its zip / flap line
		}
		if (edge) v = 58;
		set(im, x, y, grey(v));
	}
	return im;
}
function iconTrim(t) {
	const T = TIERS[t];
	const im = img(16, 16);
	// handle loop
	for (const [x, y] of [[7, 1], [8, 1], [6, 2], [9, 2], [6, 3], [9, 3]]) set(im, x, y, hex(T.strap[y === 1 ? 2 : 1]));
	set(im, 7, 2, null);
	// two straps down the lid, buckles where they meet the bag
	for (const sx of [5, 10]) {
		for (let y = 4; y <= 8; y++) set(im, sx, y, hex(T.strap[y === 4 ? 2 : (sx === 10 ? 0 : 1)]));
		set(im, sx, 8, hex(T.metal[1]));
		set(im, sx, 9, hex(T.metal[0]));
		set(im, sx, 7, hex(T.metal[2]));
	}
	if (T.plates) { // metal corner caps on the bottom of the bag
		for (const [x, y, s] of [[3, 13, 1], [4, 14, 1], [3, 12, 2], [12, 13, 0], [11, 14, 0], [12, 12, 1]]) set(im, x, y, hex(T.metal[s]));
	}
	if (t === 'gold') for (let x = 4; x <= 11; x++) if (x !== 5 && x !== 10) set(im, x, 7, hex(T.metal[x < 8 ? 2 : 1])); // gilded lid rim
	if (T.emblem) { // a gem on the pocket
		set(im, 7, 11, hex(T.emblem[2])); set(im, 8, 11, hex(T.emblem[1]));
		set(im, 7, 12, hex(T.emblem[1])); set(im, 8, 12, hex(T.emblem[0]));
	}
	return im;
}

// ------------------------------------------------------------------ worn model (64x32)
// must match BackpackModel: main 8x9x4 @ (0,0), lid 8x2x4 @ (0,13), pocket 6x4x2 @ (0,19), handle 3x1x1 @ (16,19),
// side pockets 1x4x3 @ (24,0) and (24,7), front straps 1x8x1 @ (32,0) and (36,0), shoulder straps 1x1x5 @ (40,0) and (40,6)
const MAIN = box(0, 0, 8, 9, 4), LID = box(0, 13, 8, 2, 4), POCKET = box(0, 19, 6, 4, 2), HANDLE = box(16, 19, 3, 1, 1);
const SIDE_L = box(24, 0, 1, 4, 3), SIDE_R = box(24, 7, 1, 4, 3);
const STRAP_L = box(32, 0, 1, 8, 1), STRAP_R = box(36, 0, 1, 8, 1), SHOULDER_L = box(40, 0, 1, 1, 5), SHOULDER_R = box(40, 6, 1, 1, 5);
const FACES = ['top', 'bottom', 'right', 'front', 'left', 'back'];

function fabric(x, y, seed, base) { return base + (hash(x, y, seed) - 0.5) * 20 + ((x + y) % 2 === 0 ? 3 : -3); }

function bodySheet() {
	const im = img(64, 32);
	// main bag: canvas, darker seams on every edge, a stitched line round the outer (back) face
	for (const f of FACES) rect(im, ...MAIN[f], (u, v, w, h, x, y) => {
		let b = f === 'back' ? 204 : f === 'top' ? 214 : f === 'bottom' ? 150 : f === 'front' ? 120 : 186;
		let c = fabric(x, y, 11, b);
		if (u === 0 || v === 0 || u === w - 1 || v === h - 1) c -= 34;
		if (f === 'back' && (u === 1 || u === w - 2) && v > 0 && v < h - 1 && v % 2 === 0) c -= 30; // stitching
		if ((f === 'right' || f === 'left') && v >= h - 2) c -= 18;
		return grey(c);
	});
	// lid: a shade darker, flap edge darkest
	for (const f of FACES) rect(im, ...LID[f], (u, v, w, h, x, y) => {
		let c = fabric(x, y, 13, f === 'top' ? 190 : f === 'bottom' ? 120 : 172);
		if (f !== 'top' && f !== 'bottom' && v === h - 1) c -= 40;
		if (f === 'top' && (u === 0 || u === w - 1)) c -= 24;
		return grey(c);
	});
	// front pocket: lighter, bordered, a flap line near the top
	for (const f of FACES) rect(im, ...POCKET[f], (u, v, w, h, x, y) => {
		let c = fabric(x, y, 17, f === 'back' ? 216 : f === 'top' ? 200 : 170);
		if (f === 'back' && (u === 0 || u === w - 1 || v === h - 1)) c -= 40;
		if (f === 'back' && v === 1) c -= 52;
		return grey(c);
	});
	for (const s of [SIDE_L, SIDE_R]) for (const f of FACES) rect(im, ...s[f], (u, v, w, h, x, y) => {
		let c = fabric(x, y, 19, 176);
		if (v === 0) c -= 40;
		return grey(c);
	});
	return im;
}

function trimSheet(t) {
	const T = TIERS[t];
	const im = img(64, 32);
	const strap = (x, y, u, v, w, h) => { const c = hex(T.strap[(u === 0 || u === w - 1) && w > 1 ? 0 : (hash(x, y, 23) > 0.7 ? 2 : 1)]); return c; };
	for (const s of [STRAP_L, STRAP_R, SHOULDER_L, SHOULDER_R, HANDLE]) for (const f of FACES) rect(im, ...s[f], (u, v, w, h, x, y) => strap(x, y, u, v, w, h));
	// buckles on the shoulder straps' fronts (the chest side)
	for (const s of [STRAP_L, STRAP_R]) { const [fx, fy] = s.front; set(im, fx, fy + 3, hex(T.metal[2])); set(im, fx, fy + 4, hex(T.metal[1])); }
	// the lid straps: two vertical straps down the lid's outer face and onto the bag, with buckles
	const [lx, ly, lw, lh] = LID.back;
	const [mx, my] = MAIN.back;
	for (const su of [1, 6]) {
		for (let v = 0; v < lh; v++) set(im, lx + su, ly + v, hex(T.strap[1]));
		for (let v = 0; v < 4; v++) set(im, mx + su, my + v, hex(T.strap[v === 3 ? 0 : 1]));
		set(im, mx + su, my + 1, hex(T.metal[2]));
		set(im, mx + su, my + 2, hex(T.metal[1]));
	}
	// over the lid top too
	const [tx, ty, tw, th] = LID.top;
	for (const su of [1, 6]) for (let v = 0; v < th; v++) set(im, tx + su, ty + v, hex(T.strap[2]));
	if (T.plates) { // metal corner caps at the bottom of the outer face and the bottom face corners
		const [bx, by, bw, bh] = MAIN.back;
		for (const [u, v, s] of [[0, bh - 1, 1], [1, bh - 1, 0], [0, bh - 2, 2], [bw - 1, bh - 1, 0], [bw - 2, bh - 1, 0], [bw - 1, bh - 2, 1]]) set(im, bx + u, by + v, hex(T.metal[s]));
		for (const side of [MAIN.right, MAIN.left]) { const [sx, sy, sw, sh] = side; for (let u = 0; u < sw; u++) set(im, sx + u, sy + sh - 1, hex(T.metal[u % 2])); }
	}
	if (t === 'gold') { const [fx, fy, fw, fh] = LID.back; for (let u = 0; u < fw; u++) if (u !== 1 && u !== 6) set(im, fx + u, fy + fh - 1, hex(T.metal[u < 4 ? 2 : 1])); }
	if (T.emblem) { const [px, py] = POCKET.back; set(im, px + 2, py + 2, hex(T.emblem[2])); set(im, px + 3, py + 2, hex(T.emblem[1])); set(im, px + 2, py + 3, hex(T.emblem[1])); set(im, px + 3, py + 3, hex(T.emblem[0])); }
	return im;
}

write(ITEM, 'backpack_base.png', iconBase());
write(ENT, 'backpack_body.png', bodySheet());
for (const t of Object.keys(TIERS)) {
	write(ITEM, `backpack_trim_${t}.png`, iconTrim(t));
	write(ENT, `backpack_trim_${t}.png`, trimSheet(t));
}
console.log('backpack textures written');
