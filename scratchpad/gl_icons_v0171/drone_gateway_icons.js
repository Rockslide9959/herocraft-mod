// v0.17.1: the Hard-Light Drone and Hard-Light Gateway icons -- the 32 px Construct Menu icons (icons/c_hard_light_drone.png,
// icons/c_hard_light_gateway.png, in the make_icons.js style: its primitives and style pass are loaded from the main
// checkout's scratchpad/gl_icons_v0171/make_icons.js without running its writer) and the 16 px HUD key-box strip
// ring_tech.png (32 x 16: cell 0 drone, cell 1 gateway).
//   node drone_gateway_icons.js <worktree root> [contact sheet png]
'use strict';
const fs = require('fs');
const path = require('path');
const MAIN = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad';
const st = require(MAIN + '/suits/skin_tools.js');
const ROOT = process.argv[2];
const GUI = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/gui/green_lantern');

const src = fs.readFileSync(MAIN + '/gl_icons_v0171/make_icons.js', 'utf8');
const prims = src.slice(src.indexOf('// ------------------------------------------------------------------ shape primitives'),
	src.indexOf('// ------------------------------------------------------------------ the icons'));
const styleSrc = src.slice(src.indexOf('// ------------------------------------------------------------------ raster + style'),
	src.indexOf('// the Lantern Corps emblem'));
const N = 32;
const mod = new Function('N', prims + styleSrc +
	'\nreturn { rect, rrect, circle, ellipse, ring, poly, seg, polyline, arc, union, diff, inter, rot, tr, style, raster };')(N);
const { rect, rrect, circle, ellipse, ring, poly, seg, arc, union, diff, inter, style } = mod;

// ---------------------------------------------------------------- the drone: three-quarter front view
// a teardrop body with the big lens in its nose, two rotor rings out on pylons, swept fins on top, the belly emitter
const drone = {
	shape: union(
		ellipse(16, 17.5, 8, 6.4), // the hull
		poly([[12, 12], [13.6, 7.5], [15.2, 12]]), poly([[16.8, 12], [18.4, 7.5], [20, 12]]), // swept fins
		rect(7, 15.6, 9.5, 17.6), rect(22.5, 15.6, 25, 17.6), // pylons
		diff(ellipse(5.2, 16.4, 4.6, 2.3), ellipse(5.2, 16.4, 2.6, 0.9)), // rotor rings
		diff(ellipse(26.8, 16.4, 4.6, 2.3), ellipse(26.8, 16.4, 2.6, 0.9)),
		rrect(14.6, 23, 17.4, 26.4, 1)), // the belly emitter
	cut: union(
		ring(16, 17.5, 4.3, 5.2), // the lens bezel gap
		seg(10, 21.5, 12.5, 23.2, 0.8), seg(22, 21.5, 19.5, 23.2, 0.8)), // flank seams
	hi: union(
		ring(16, 17.5, 1.6, 2.5), rect(13.2, 14.6, 18.8, 15.4), rect(13.2, 19.6, 18.8, 20.4), // the emblem in the lens
		circle(5.2, 16.4, 0.7), circle(26.8, 16.4, 0.7), circle(16, 25.4, 0.7)),
};

// ---------------------------------------------------------------- the gateway: a door with an arched crown, swirling inside
// the opening is left open (the frame reads as a door) with a spiral of light turning inside it
const opening = rect(11.6, 11.6, 20.4, 27.4);
const gate = {
	shape: union(
		rect(6, 10, 10.6, 28.2), rect(21.4, 10, 26, 28.2), // pillars
		rect(4, 8, 28, 10.8), // lintel (wider than the pillars)
		inter(ellipse(16, 8.2, 7, 6), rect(9, 1, 23, 8.2)), // the arched crown
		rect(3, 28, 29, 30.8), // threshold
		rect(5, 26, 11.6, 28.2), rect(20.4, 26, 27, 28.2), // pillar bases
		inter(opening, union(arc(16, 19.5, 2.2, 1.5, 150, 390), arc(16, 19.5, 4.6, 1.5, 300, 520),
			arc(16, 19.8, 7, 1.4, 110, 250), circle(16, 19.5, 1.3)))), // the swirl
	cut: union(
		rect(7.7, 13, 8.9, 24.5), rect(23.1, 13, 24.3, 24.5), // fluted pillar grooves
		rect(4, 10.2, 28, 10.8)), // the lintel's lip
	hi: union(
		ring(16, 5.2, 1.1, 2.0), rect(13.8, 2.4, 18.2, 3.0), rect(13.8, 7.4, 18.2, 8.0), // the emblem on the crown
		circle(16, 19.5, 0.9), // the hot centre of the swirl
		rect(8.1, 15, 8.6, 15.8), rect(8.1, 19, 8.6, 19.8), rect(8.1, 23, 8.6, 23.8), // runes
		rect(23.4, 17, 23.9, 17.8), rect(23.4, 21, 23.9, 21.8)),
};

const writes = [];
for (const [name, icon] of [['c_hard_light_drone', drone], ['c_hard_light_gateway', gate]]) {
	const big = style(icon, 32);
	fs.writeFileSync(path.join(GUI, 'icons/' + name + '.png'), st.encode(big.w, big.h, big.px));
	writes.push('icons/' + name + '.png');
}

// ---------------------------------------------------------------- the 16 px strip (the survival strip's flat look)
const small = [
	{ // drone
		shape: union(ellipse(16, 18, 9, 7.5), rect(5, 15, 27, 19), ellipse(4, 16, 4, 3), ellipse(28, 16, 4, 3),
			poly([[11, 12], [13, 5], [16, 12]]), poly([[16, 12], [19, 5], [21, 12]])),
		cut: union(ring(16, 18, 4.5, 6.4), ellipse(4, 16, 1.6, 0.8), ellipse(28, 16, 1.6, 0.8)),
		hi: union(circle(16, 18, 2.6)),
	},
	{ // gateway
		shape: union(rect(3, 9, 10, 30), rect(22, 9, 29, 30), rect(1, 5, 31, 10), inter(ellipse(16, 5.5, 8, 5.5), rect(8, 0, 24, 6)),
			rect(1, 28, 31, 32), diff(ellipse(16, 19.5, 4.6, 6.6), ellipse(16, 19.5, 2.4, 4.2)), circle(16, 19.5, 1.4)),
		cut: union(rect(1, 9, 31, 10)),
		hi: union(rect(14, 2, 18, 4), circle(16, 19, 1.4)),
	},
];
function flat16(icon) {
	const S = 16, M = mod.raster(icon.shape, S), C = mod.raster(icon.cut, S, 0.4), H = mod.raster(icon.hi, S, 0.4);
	const at = (m, x, y) => (x < 0 || y < 0 || x >= S || y >= S ? 0 : m[y * S + x]);
	const px = Buffer.alloc(S * S * 4);
	const put = (x, y, r, g, b, a) => { const i = (y * S + x) * 4; px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a; };
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		if (at(M, x, y)) {
			const edge = !at(M, x, y - 1) || !at(M, x - 1, y) || !at(M, x, y + 1) || !at(M, x + 1, y);
			if (at(H, x, y)) put(x, y, 238, 255, 242, 255);
			else if (edge) put(x, y, 150, 255, 182, 255);
			else if (at(C, x, y)) put(x, y, 14, 96, 44, 255);
			else put(x, y, y < 8 ? 66 : 44, y < 8 ? 232 : 205, y < 8 ? 116 : 98, 255);
		} else {
			let near = false;
			for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) if (at(M, x + dx, y + dy)) near = true;
			if (near) put(x, y, 6, 46, 22, 230);
		}
	}
	return { w: S, h: S, px };
}
const strip = Buffer.alloc(32 * 16 * 4);
small.forEach((icon, c) => {
	const s16 = flat16(icon);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const s = (y * 16 + x) * 4, d = (y * 32 + c * 16 + x) * 4;
		for (let k = 0; k < 4; k++) strip[d + k] = s16.px[s + k];
	}
});
fs.writeFileSync(path.join(GUI, 'ring_tech.png'), st.encode(32, 16, strip));
writes.push('ring_tech.png');

// optional contact sheet (4x the 32 px icons, 8x the strip)
if (process.argv[3]) {
	const sheetW = 4 + 128 + 8 + 128 + 4, sheetH = 4 + 128 + 8 + 128 + 4;
	const sheet = Buffer.alloc(sheetW * sheetH * 4);
	for (let i = 0; i < sheet.length; i += 4) { sheet[i] = 20; sheet[i + 1] = 24; sheet[i + 2] = 22; sheet[i + 3] = 255; }
	const blit = (img, w, h, ox, oy, k) => {
		for (let y = 0; y < h * k; y++) for (let x = 0; x < w * k; x++) {
			const s = (Math.floor(y / k) * w + Math.floor(x / k)) * 4, d = ((oy + y) * sheetW + ox + x) * 4, a = img[s + 3] / 255;
			for (let c = 0; c < 3; c++) sheet[d + c] = Math.round(img[s + c] * a + sheet[d + c] * (1 - a));
		}
	};
	const d32 = st.decode(path.join(GUI, 'icons/c_hard_light_drone.png'));
	const g32 = st.decode(path.join(GUI, 'icons/c_hard_light_gateway.png'));
	blit(d32.px, 32, 32, 4, 4, 4);
	blit(g32.px, 32, 32, 4 + 128 + 8, 4, 4);
	blit(strip, 32, 16, 4, 4 + 128 + 8, 8);
	fs.writeFileSync(process.argv[3], st.encode(sheetW, sheetH, sheet));
	writes.push(process.argv[3]);
}
console.log('wrote ' + writes.join(', '));
