// v0.14.21: GUI background textures for the Stark Fabricator and the Iron Man Suit Platform screens.
// Hand-rolled PNG (scratchpad/pnglib.js) -- no canvas / python. Deterministic: re-running rewrites identical files.
//   node scratchpad/gen_v01421_ironman_gui.js
// Writes (256 x 256 sheets, the panel in the top-left):
//   src/main/resources/assets/projecthero/textures/gui/stark_fabricator.png       panel 200 x 236
//   src/main/resources/assets/projecthero/textures/gui/iron_man_suit_platform.png panel 176 x 202
// Slot wells sit exactly where StarkFabricatorMenu / IronManSuitPlatformMenu put their slots (item at x,y -> the
// 18 x 18 well at x-1,y-1). Everything dynamic (bars, tabs, text, buttons) is drawn in code on top.
const path = require('path');
const png = require('./pnglib.js');
const OUT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures/gui');

function sheet() {
	return { w: 256, h: 256, data: Buffer.alloc(256 * 256 * 4) };
}

let seed = 1;
function rnd() {
	seed = (seed * 1103515245 + 12345) & 0x7fffffff;
	return seed / 0x7fffffff;
}

function px(img, x, y, c) {
	if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
	const o = (y * img.w + x) * 4;
	const a = c[3] === undefined ? 255 : c[3];
	if (a >= 255) {
		img.data[o] = c[0]; img.data[o + 1] = c[1]; img.data[o + 2] = c[2]; img.data[o + 3] = 255;
		return;
	}
	const t = a / 255;
	img.data[o] = Math.round(img.data[o] * (1 - t) + c[0] * t);
	img.data[o + 1] = Math.round(img.data[o + 1] * (1 - t) + c[1] * t);
	img.data[o + 2] = Math.round(img.data[o + 2] * (1 - t) + c[2] * t);
	img.data[o + 3] = Math.max(img.data[o + 3], a);
}

function rect(img, x, y, w, h, c) {
	for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) px(img, i, j, c);
}

const BLACK = [8, 9, 12];
const HI = [92, 102, 118];
const LO = [18, 20, 26];
const WELL = [13, 16, 22];
const WELL_DARK = [5, 6, 9];
const WELL_LIGHT = [74, 84, 100];
const RED = [150, 32, 30];
const GOLD = [204, 154, 48];
const CYAN = [70, 170, 205];

/** The panel body: brushed gunmetal with a soft vertical gradient, bevel, black rim and a header strip. */
function body(img, w, h) {
	for (let y = 0; y < h; y++) {
		for (let x = 0; x < w; x++) {
			const t = y / h;
			const n = (rnd() - 0.5) * 5 + Math.sin((x + y * 0.15) * 0.9) * 1.2; // fine brushed grain
			const r = 44 - t * 12 + n, g = 49 - t * 12 + n, b = 58 - t * 12 + n;
			px(img, x, y, [r | 0, g | 0, b | 0]);
		}
	}
	// header strip
	for (let y = 2; y < 15; y++) for (let x = 2; x < w - 2; x++) px(img, x, y, [24, 28, 36, 200]);
	// accent line under the header: gold lead-in, Stark red the rest
	rect(img, 2, 15, w - 4, 1, RED);
	rect(img, 2, 15, 34, 1, GOLD);
	rect(img, 2, 16, w - 4, 1, [10, 11, 14, 120]);
	// rim + bevel
	rect(img, 0, 0, w, 1, BLACK); rect(img, 0, h - 1, w, 1, BLACK);
	rect(img, 0, 0, 1, h, BLACK); rect(img, w - 1, 0, 1, h, BLACK);
	rect(img, 1, 1, w - 2, 1, HI); rect(img, 1, 1, 1, h - 2, HI);
	rect(img, 1, h - 2, w - 2, 1, LO); rect(img, w - 2, 1, 1, h - 2, LO);
	// corner screws
	// no top-left screw: it read as a stray quote mark before the title text
	for (const [sx, sy] of [[w - 7, 5], [5, h - 7], [w - 7, h - 7]]) screw(img, sx, sy);
}

function screw(img, x, y) {
	rect(img, x, y, 3, 3, [26, 29, 36]);
	px(img, x + 1, y, [110, 120, 136]); px(img, x, y + 1, [110, 120, 136]);
	px(img, x + 1, y + 1, [60, 66, 78]);
	px(img, x + 2, y + 2, [12, 13, 16]);
}

/** A recessed well: dark inner, shadowed top/left, lit bottom/right. */
function well(img, x, y, w, h, inner) {
	rect(img, x, y, w, h, inner || WELL);
	rect(img, x, y, w, 1, WELL_DARK); rect(img, x, y, 1, h, WELL_DARK);
	rect(img, x, y + h - 1, w, 1, WELL_LIGHT); rect(img, x + w - 1, y, 1, h, WELL_LIGHT);
}

/** An 18 x 18 item-slot well for a slot whose item draws at (sx, sy). */
function slot(img, sx, sy) {
	well(img, sx - 1, sy - 1, 18, 18);
	px(img, sx, sy, [24, 28, 36]); // faint inner top-left catch-light
}

/** A faint holo grid inside a well (preview boxes). */
function holoGrid(img, x, y, w, h) {
	for (let j = y + 1; j < y + h - 1; j++) {
		for (let i = x + 1; i < x + w - 1; i++) {
			if ((i - x) % 6 === 0 || (j - y) % 6 === 0) px(img, i, j, [CYAN[0], CYAN[1], CYAN[2], 22]);
		}
	}
	// a glowing floor ellipse near the bottom
	const cx = x + w / 2, cy = y + h - 7;
	for (let j = -3; j <= 3; j++) for (let i = -Math.floor(w / 2) + 3; i <= Math.floor(w / 2) - 3; i++) {
		const d = (i * i) / ((w / 2 - 3) * (w / 2 - 3)) + (j * j) / 9;
		if (d <= 1) px(img, Math.round(cx + i), cy + j, [CYAN[0], CYAN[1], CYAN[2], Math.round(70 * (1 - d))]);
	}
}

/** A thin engraved groove line. */
function groove(img, x, y, w) {
	rect(img, x, y, w, 1, [20, 23, 29]);
	rect(img, x, y + 1, w, 1, [70, 78, 92]);
}

// ---------------------------------------------------------------- Stark Fabricator 200 x 236
{
	seed = 7;
	const img = sheet();
	const W = 200, H = 236;
	body(img, W, H);
	// 3 x 3 input grid, items at (44 + c*18, 17 + r*18), inside a framing plate
	well(img, 40, 13, 60, 60, [20, 23, 30]);
	for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) slot(img, 44 + c * 18, 17 + r * 18);
	// blueprint slot (12, 35) with a blue "paper" corner tab
	slot(img, 12, 35);
	rect(img, 11, 53, 18, 2, [40, 90, 150]);
	// output slot (120, 35): a larger lit well around it
	well(img, 115, 30, 26, 26, [18, 22, 30]);
	slot(img, 120, 35);
	rect(img, 115, 29, 26, 1, [GOLD[0], GOLD[1], GOLD[2], 140]);
	// progress arrow well between grid and output
	well(img, 102, 39, 12, 6);
	for (let i = 0; i < 5; i++) { rect(img, 114 + i, 37 + i, 1, 10 - 2 * i, i === 0 ? WELL_DARK : WELL); }
	// energy gauge well (code fills x 177..186, y 17..71)
	well(img, 176, 16, 12, 56);
	for (let t = 0; t <= 4; t++) rect(img, 172, 16 + Math.round(t * 55 / 4), 3, 1, [100, 112, 130]);
	// progress bar well (code fills x 12..188, y 75..79)
	well(img, 11, 74, 178, 6);
	// tab rail
	groove(img, 6, 110, W - 12);
	// checklist box
	well(img, 7, 112, 186, 40, [16, 19, 25]);
	// inventory + hotbar
	groove(img, 6, 153, W - 12);
	for (let r = 0; r < 3; r++) for (let c = 0; c < 9; c++) slot(img, 8 + c * 18, 156 + r * 18);
	for (let c = 0; c < 9; c++) slot(img, 8 + c * 18, 214);
	png.write(path.join(OUT, 'stark_fabricator.png'), img);
}

// ---------------------------------------------------------------- Suit Platform 176 x 202
{
	seed = 11;
	const img = sheet();
	const W = 176, H = 202;
	body(img, W, H);
	// 3D preview well
	well(img, 7, 18, 41, 95, [8, 12, 18]);
	holoGrid(img, 7, 18, 41, 95);
	// armour slots at (53 + i*20, 32) on a plate
	well(img, 50, 29, 82, 22, [20, 23, 30]);
	for (let i = 0; i < 4; i++) slot(img, 53 + i * 20, 32);
	// reserve / status plate right of the slots
	well(img, 136, 29, 34, 22, [16, 19, 25]);
	// meters plate
	groove(img, 52, 54, 118);
	// button rail
	groove(img, 6, 117, W - 12);
	for (let r = 0; r < 3; r++) for (let c = 0; c < 9; c++) slot(img, 8 + c * 18, 124 + r * 18);
	for (let c = 0; c < 9; c++) slot(img, 8 + c * 18, 182);
	png.write(path.join(OUT, 'iron_man_suit_platform.png'), img);
}
console.log('wrote stark_fabricator.png + iron_man_suit_platform.png');
