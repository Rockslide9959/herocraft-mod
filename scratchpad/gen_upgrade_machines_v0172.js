// v0.17.2 (user: "give all the things that upgrade can upgrade upgraded models with the black and white colour scheme with the green
// wiring pattern on the black parts please"): the upgraded machines' texture atlas (16x16 tiles the box models pick from), its
// emissive copy, and the tileable coat a merged golem wears. Palette sampled from upgrade.png (his skin).
// node scratchpad/gen_upgrade_machines_v0172.js   (from the repo root)
const fs = require("fs"), path = require("path");
const { encodePng } = require("./lib_png.js");
const T = "src/main/resources/assets/projecthero/textures/entity/omnitrix";

const W0 = [240, 242, 245], W1 = [224, 228, 232], W2 = [198, 202, 207], W3 = [175, 182, 189], W4 = [137, 146, 155];
const K0 = [14, 15, 16], K1 = [24, 27, 30], K2 = [35, 39, 43], K3 = [51, 57, 63];
const G0 = [88, 161, 39], G1 = [114, 209, 46], G2 = [144, 226, 85], G3 = [188, 237, 155], G4 = [240, 251, 230];
const GREENS = [G0, G1, G2, G3, G4].map(String);
const isGreen = c => GREENS.includes(String(c));
const hash = (x, y, s) => { let n = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967296; };

// ---- tiles: (x, y in 0..15, seed) -> colour
function plate(x, y, s) {
	let c = W1;
	if (x === 0 || y === 0) c = W0;
	if (x === 15 || y === 15) c = W2;
	if ((y === 7 && x > 1 && x < 14) || (s & 1 && x === 7 && y > 1 && y < 14)) c = W3; // panel seam
	if ((x === 2 || x === 13) && (y === 2 || y === 13)) c = W4; // rivets
	if (hash(x, y, s) < 0.05 && c === W1) c = W0;
	return c;
}
function circuit(x, y, s) {
	let c = (x + y) % 9 === 0 ? K1 : K0; // a faint gloss diagonal
	if (x === 0 || y === 0 || x === 15 || y === 15) c = K2;
	const v = s % 3;
	// L-bracket trace with a node
	if (v !== 2) {
		if (y === 3 && x >= 2 && x <= 9) c = G1;
		if (x === 9 && y >= 3 && y <= 7) c = G1;
		if ((x === 9 || x === 10) && (y === 7 || y === 8)) c = G2;
	}
	// dashed run
	if (y === 12 && x >= 2 && x <= 13 && (x >> 1) % 2 === 0) c = G1;
	// ring with a centre node
	const cx = v === 2 ? 7.5 : 12, cy = v === 2 ? 6.5 : 6;
	const r = Math.hypot(x - cx, y - cy);
	if (r > 1.6 && r < 2.6) c = G1;
	if (r < 0.8) c = G3;
	// corner brackets
	if ((x === 2 && y >= 8 && y <= 10) || (y === 10 && x >= 2 && x <= 4)) c = G0;
	if (v === 2 && ((y === 2 && x >= 2 && x <= 5) || (x === 2 && y >= 2 && y <= 4))) c = G1;
	return c;
}
function lens(x, y) {
	const r = Math.hypot(x - 7.5, y - 7.5);
	if (r < 2.2) return G4;
	if (r < 3.6) return G3;
	if (r < 5.0) return G2;
	if (r < 6.2) return G1;
	if (r < 7.2) return G0;
	return K0;
}
function joint(x, y) {
	if (y % 4 === 0) return K2;
	return x === 0 || x === 15 ? K2 : K3;
}
function screen(x, y) {
	if (x === 0 || y === 0 || x === 15 || y === 15) return K2;
	const len = [0, 0, 0, 11, 0, 0, 7, 0, 0, 13, 0, 0, 5, 0, 0, 0][y];
	if (len && x >= 2 && x < 2 + len) return x === 1 + len ? G3 : G1;
	return K0;
}
function grille(x, y) {
	if (x === 0 || y === 0 || x === 15 || y === 15) return K3;
	if (y >= 2 && y <= 13 && x >= 2 && x <= 13 && x % 3 === 2) return y > 9 ? G3 : y > 5 ? G2 : G1;
	return K0;
}
function band(x, y, s) {
	if (y >= 6 && y <= 9) return y === 7 && x % 4 < 2 ? G1 : K0;
	return plate(x, y, s);
}
const TILES = [[plate, circuit, lens, joint], [(x, y) => circuit(x, y, 2), screen, grille, band]];

function atlas(glow) {
	const px = Buffer.alloc(64 * 64 * 4);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const tx = x >> 4, ty = y >> 4, o = (y * 64 + x) * 4;
		const f = ty < 2 ? TILES[ty][tx] : null;
		let c = f ? f(x & 15, y & 15, tx + ty * 4 + 1) : (ty === 2 ? W1 : K0); // rows 2-3: flat white / flat black
		if (glow) c = isGreen(c) ? c : [0, 0, 0];
		px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255;
	}
	return px;
}
// the coat: white plating and black circuit panels in a staggered grid (tiles across the model's own UVs)
function coat(glow) {
	const px = Buffer.alloc(64 * 64 * 4);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const cx = x >> 4, cy = y >> 4, o = (y * 64 + x) * 4;
		const black = (cx + cy) % 2 === 0 ? hash(cx, cy, 7) < 0.8 : hash(cx, cy, 9) < 0.25;
		let c = black ? circuit(x & 15, y & 15, cx * 5 + cy * 3) : plate(x & 15, y & 15, cx + cy);
		if (glow) c = isGreen(c) ? c : [0, 0, 0];
		px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255;
	}
	return px;
}

fs.writeFileSync(path.join(T, "upgrade_machine.png"), encodePng(64, 64, atlas(false)));
fs.writeFileSync(path.join(T, "upgrade_machine_glow.png"), encodePng(64, 64, atlas(true)));
fs.writeFileSync(path.join(T, "upgrade_machine_coat.png"), encodePng(64, 64, coat(false)));
fs.writeFileSync(path.join(T, "upgrade_machine_coat_glow.png"), encodePng(64, 64, coat(true)));
console.log("wrote upgrade_machine(.png/_glow) + upgrade_machine_coat(.png/_glow)");
