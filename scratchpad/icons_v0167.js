// v0.16.7 icons: hand-drawn 16x16 Iron Man armour item sprites (vanilla armour-icon silhouettes,
// top-left light, 1 px dark outline) for every suit, plus the two creative-tab icon items.
// Run from anywhere:  node scratchpad/icons_v0167.js   -> writes textures/item/*.png + the preview sheet.
//
// Templates are 16 strings of 16 chars. Each char is a ROLE, the suit palette maps roles to colours:
//   .        transparent
//   K        outline              X  dark seam / slot
//   1-5      primary ramp (highlight -> deep shadow)
//   a-e      secondary ramp (highlight -> deep shadow)
//   W G g    glow: core / glow / dim glow (eyes, arc reactor)
//   r        rivet (light dot)    h  specular highlight
//   u V w    visor ramp (Space suit)
//   Y y      lightning bolt (tab emblem)
const fs = require('fs');
const path = require('path');
const k = require('./pngkit.js');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'item');

// ---------------------------------------------------------------- templates
const T = {};
T.helmet = [
	'................',
	'................',
	'.....KKKKKK.....',
	'...KK112223KK...',
	'..K1122222334K..',
	'..K1XaabbbcX4K..',
	'..K2abbbbbcd4K..',
	'..K2GWWbcWWG4K..',
	'..K2cbbbbccd4K..',
	'..K23bbbccd44K..',
	'..K23bXXXXd44K..',
	'..K34bcccdd45K..',
	'...K3bccdd55K...',
	'....KccddeeK....',
	'.....KKKKKK.....',
	'................',
];
// Mk I / Mk II: the riveted bucket helmet, eye slits + mouth grille.
T.helmet_bucket = [
	'................',
	'................',
	'....KKKKKKKK....',
	'...K11222233K...',
	'..K1r222222r4K..',
	'..K1222222334K..',
	'..K2XXX33XXX4K..',
	'..K2gGW33WGg4K..',
	'..K2333333344K..',
	'..K2r333333r4K..',
	'..K23X3X3X3X4K..',
	'..K233333344 4K..'.replace(' ', ''),
	'..K3r444444r5K..',
	'..K3444444555K..',
	'...KKKKKKKKKK...',
	'................',
];
// Hulkbuster: same face, heavy jaw + collar.
T.helmet_bulky = [
	'................',
	'................',
	'.....KKKKKK.....',
	'...KK112223KK...',
	'..K1122222334K..',
	'..K1XaabbbcX4K..',
	'..K2abbbbbcd4K..',
	'..K2GWWbcWWG4K..',
	'..K2cbbbbccd4K..',
	'..K23bbbccd44K..',
	'.KK23bXXXXd44KK.',
	'K1aaK3bccdd4KddK',
	'K12abK4ccd5Kdd5K',
	'K1223KKKKKK3445K',
	'K23344dddd44555K',
	'KKKKKKKKKKKKKKKK',
];
T.chestplate = [
	'................',
	'................',
	'.KKKKK....KKKKK.',
	'.K122K....K234K.',
	'.K1223K..K2334K.',
	'.K12233KK23344K.',
	'.Kb1222222334cK.',
	'.Kc1222GG2334dK.',
	'.KKK12GWWG34KKK.',
	'...K12GWWG34K...',
	'...K122GG234K...',
	'...KabbcccddK...',
	'...K23333445K...',
	'....KcccddeK....',
	'.....KKKKKK.....',
	'................',
];
T.chestplate_bulky = [
	'................',
	'.KKKKK....KKKKK.',
	'K112a2K..K2b334K',
	'K11222KKKK22334K',
	'K12222222223344K',
	'Kb122222222334cK',
	'Kc12222GG22334dK',
	'KcK122GWWG234KdK',
	'KdK122GWWG234KeK',
	'KKK1222GG2344KKK',
	'..KabbbccccddK..',
	'..K2333333445K..',
	'...K33334455K...',
	'...KcccdddeeK...',
	'....KKKKKKKK....',
	'................',
];
T.leggings = [
	'................',
	'................',
	'...KKKKKKKKKK...',
	'...KaabbbbcdK...',
	'...KdeeXXeedK...',
	'...K12222334K...',
	'...K122KK334K...',
	'...K12K..K34K...',
	'...K12K..K34K...',
	'...KabK..KbcK...',
	'...KbcK..KcdK...',
	'...K12K..K34K...',
	'...K23K..K45K...',
	'...KKKK..KKKK...',
	'................',
	'................',
];
T.leggings_bulky = [
	'................',
	'................',
	'..KKKKKKKKKKKK..',
	'..KaabbbbbccdK..',
	'..KdeeeXXeeedK..',
	'..K1222222334K..',
	'..K1222KK3344K..',
	'..K122K..K344K..',
	'..K122K..K344K..',
	'..KabbK..KbcdK..',
	'..KbccK..KcddK..',
	'..K122K..K344K..',
	'..K233K..K455K..',
	'..KKKKK..KKKKK..',
	'................',
	'................',
];
T.boots = [
	'................',
	'................',
	'................',
	'....KKK..KKK....',
	'...KabK..KbcK...',
	'...KbcK..KcdK...',
	'...K12K..K34K...',
	'...K12K..K34K...',
	'...K12K..K34K...',
	'..K122K..K234K..',
	'.K1223K..K2344K.',
	'.KbccdK..KccdeK.',
	'.KKKKKK..KKKKKK.',
	'................',
	'................',
	'................',
];
T.boots_bulky = [
	'................',
	'................',
	'...KKKK..KKKK...',
	'...KabK..KbcK...',
	'..KabbK..KbcdK..',
	'..K122K..K234K..',
	'..K122K..K234K..',
	'..K122K..K334K..',
	'..K122K..K334K..',
	'.K1222K..K2334K.',
	'K11223K..K23344K',
	'KabccdK..KbcddeK',
	'KKKKKKK..KKKKKKK',
	'................',
	'................',
	'................',
];
// The Iron Man tab: a bigger, more detailed classic helmet.
T.tab_iron_man = [
	'................',
	'.....KKKKKK.....',
	'...KKh11222KK...',
	'..Kh112222334K..',
	'.K1XaabbbbccX4K.',
	'.K1abbbbbbccd4K.',
	'.K2abbbbbbbcd4K.',
	'.K2bGWWbcWWGc4K.',
	'.K2GWbbbbccWG4K.',
	'.K23cbbbbccd44K.',
	'.K234bbbccd444K.',
	'.K234bXXXXd445K.',
	'..K34bcccdd45K..',
	'...K4bccdde5K...',
	'....KcddeeeK....',
	'.....KKKKKK.....',
];
// The Superheroes tab: gold-rimmed shield badge with a lightning bolt.
T.tab_superheroes = [
	'................',
	'.KKKKKKKKKKKKKK.',
	'.KaabbbbbbbbcdK.',
	'.Kb1122222334dK.',
	'.Kb1222222334dK.',
	'.Kb1222222334dK.',
	'.Kb1222222344dK.',
	'.Kb2222223344dK.',
	'.Kc2222233344eK.',
	'..Kc22233344eK..',
	'..Kc22333444eK..',
	'...Kc233444eK...',
	'....Kc3444eK....',
	'.....Kd44eK.....',
	'......KddK......',
	'.......KK.......',
];

// ---------------------------------------------------------------- palettes
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), h.length > 7 ? parseInt(h.slice(7, 9), 16) : 255];
const ramp = (chars, cols) => Object.fromEntries(chars.split('').map((c, i) => [c, cols[i]]));
const RED = ['#ff7a66', '#e8382e', '#bf1e24', '#8c1119', '#5a0a10'];
const CRIMSON = ['#f0605a', '#c8283a', '#9c1a2a', '#701220', '#480a14'];
const GOLD = ['#fff3a8', '#f6cf4e', '#dba532', '#a8741c', '#6e4a10'];
const PALE_GOLD = ['#fff6c0', '#f2d878', '#d8b450', '#a8842c', '#6e5418'];
const POLISHED_GOLD = ['#fffbd0', '#ffe066', '#e9b432', '#b07a1a', '#73480e'];
const IRON = ['#c8c8c4', '#9a9a96', '#767674', '#575756', '#3c3c3c'];
const GUNMETAL = ['#a4a094', '#7e7a70', '#5e5a52', '#46423c', '#2e2b27'];
const BRONZE = ['#f0cc70', '#c89a38', '#a07626', '#7a5418', '#4e340c'];
const BLACK = ['#6c7584', '#454b57', '#2c313b', '#1c2028', '#101318'];
const GRAPHITE = ['#8a93a2', '#5f6876', '#454c58', '#323843', '#20252d'];
const SILVER = ['#f4f6fa', '#c9cfd9', '#a3abb9', '#7b8494', '#525a68'];
const WHITE = ['#ffffff', '#e9edf2', '#c8d0da', '#9aa4b2', '#6a7482'];
const HB_RED = ['#e85048', '#b8222a', '#8c141e', '#640c14', '#40060c'];
const BLUE = ['#7ab4ff', '#3f7ee8', '#2a5cc4', '#1c3f92', '#122a64'];
const CYAN = { W: '#f4ffff', G: '#7fe8ff', g: '#3aa6d4' };
const DEEPBLUE = { W: '#e8f4ff', G: '#4aa8ff', g: '#2266cc' };
const DIM = { W: '#fff6dc', G: '#ffd27a', g: '#b07a30' };

function pal(p, s, glow, outline, seam, extra) {
	const m = Object.assign({}, ramp('12345', p), ramp('abcde', s), glow, { K: outline, X: seam, r: '#f0f0ea', h: '#ffffff' }, extra || {});
	return Object.fromEntries(Object.entries(m).map(([c, v]) => [c, hex(v)]));
}

// suitId -> item texture prefix, palette, template variant, extras
const SUITS = [
	{ id: 'mark_1', tex: 'mark_1', label: 'MK I', pal: pal(IRON, GUNMETAL, DIM, '#141414', '#202020'), helmet: 'helmet_bucket', rivets: true },
	{ id: 'mark_1_v2', tex: 'mark_1_v2', label: 'MK II', pal: pal(POLISHED_GOLD, BRONZE, CYAN, '#2a1806', '#3a2408'), helmet: 'helmet_bucket', rivets: true },
	{ id: 'mark_iii', tex: 'mark_3', label: 'MK III', pal: pal(RED, GOLD, CYAN, '#2a0606', '#3a0a0a') },
	{ id: 'mark_4', tex: 'mark_4', label: 'MK IV', pal: pal(POLISHED_GOLD, RED, CYAN, '#2a1606', '#3a1a08'), dome: true, helmetPal: pal(RED, POLISHED_GOLD, CYAN, '#2a0606', '#3a0a0a') },
	{ id: 'mark_v', tex: 'mark_5', label: 'MK V', pal: pal(BLACK, GRAPHITE, DEEPBLUE, '#000000', '#05060a') },
	{ id: 'mark_2', tex: 'mark_2', label: 'MK VI', pal: pal(CRIMSON, PALE_GOLD, CYAN, '#220408', '#30060c'), triangle: true },
	{ id: 'mark_6', tex: 'mark_6', label: 'MK VII', pal: pal(SILVER, RED, CYAN, '#1a1c22', '#30343c') },
	{ id: 'mark_vii', tex: 'mark_7', label: 'MK VIII', pal: pal(WHITE, RED, CYAN, '#1c2028', '#3a404a', { u: '#fff8c8', V: '#f0c040', w: '#a87018' }), visor: true },
	{ id: 'mark_8', tex: 'mark_8', label: 'HB I', pal: pal(HB_RED, GOLD, CYAN, '#1e0204', '#2e0406'), bulky: true },
];

// ---------------------------------------------------------------- drawing
function blank() { return { w: 16, h: 16, px: Buffer.alloc(16 * 16 * 4) }; }
function set(im, x, y, c) { const i = (y * 16 + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3]; }
function getc(im, x, y) { const i = (y * 16 + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }

function check(name, rows) {
	if (rows.length !== 16) throw new Error(name + ': ' + rows.length + ' rows');
	rows.forEach((r, y) => { if (r.length !== 16) throw new Error(name + ' row ' + y + ' is ' + r.length + ': ' + r); });
	// silhouette must be left-right symmetric
	rows.forEach((r, y) => { for (let x = 0; x < 16; x++) if ((r[x] === '.') !== (r[15 - x] === '.')) throw new Error(name + ' asymmetric at ' + x + ',' + y); });
}

function render(name, rows, palette) {
	check(name, rows);
	const im = blank();
	rows.forEach((r, y) => {
		for (let x = 0; x < 16; x++) {
			const ch = r[x]; if (ch === '.') continue;
			const c = palette[ch]; if (!c) throw new Error(name + ': no colour for ' + ch);
			set(im, x, y, c);
		}
	});
	return im;
}

function edit(rows, x, y, ch) { rows[y] = rows[y].slice(0, x) + ch + rows[y].slice(x + 1); }
function mix(a, b, t) { return [0, 1, 2].map(i => Math.round(a[i] * (1 - t) + b[i] * t)).concat([255]); }

function suitTemplate(s, piece) {
	let base = piece;
	if (piece === 'helmet') base = s.helmet || (s.bulky ? 'helmet_bulky' : 'helmet');
	else if (s.bulky) base = piece + '_bulky';
	const rows = T[base].slice();
	if (s.rivets) {
		if (piece === 'chestplate') { edit(rows, 3, 4, 'r'); edit(rows, 12, 4, 'r'); edit(rows, 4, 12, 'r'); edit(rows, 11, 12, 'r'); }
		if (piece === 'leggings') { edit(rows, 4, 5, 'r'); edit(rows, 11, 5, 'r'); }
		if (piece === 'boots') { edit(rows, 4, 7, 'r'); edit(rows, 11, 7, 'r'); }
	}
	if (s.triangle && piece === 'chestplate') {
		// Mk VI: the triangular reactor
		edit(rows, 7, 7, '2'); edit(rows, 8, 7, '2');
		edit(rows, 6, 8, 'W'); edit(rows, 7, 8, 'W'); edit(rows, 8, 8, 'W'); edit(rows, 9, 8, 'G');
		edit(rows, 6, 9, '2'); edit(rows, 7, 9, 'W'); edit(rows, 8, 9, 'G'); edit(rows, 9, 9, '3');
		edit(rows, 7, 10, 'g'); edit(rows, 8, 10, '3');
	}
	if (s.visor && piece === 'helmet') {
		// Mk VIII Space: a gold visor band across the eyes
		rows[6] = '..K2uVVVVVVw4K..';
		rows[7] = '..K2VuVVVVVw4K..';
		rows[8] = '..K2VVVVVVww4K..';
	}
	return rows;
}

function domeOverlay(im) {
	// Mk IV Hydro: a clear glass dome around the helmet -- pale rim outside the helmet, glints over it.
	const cx = 7.5, cy = 7.6, R = 7.4;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - cx, y - cy);
		const a = getc(im, x, y)[3];
		if (a === 0 && d <= R + 0.5) {
			if (d > R - 0.75) set(im, x, y, [168, 228, 248, 235]);      // rim
			else set(im, x, y, [200, 240, 255, 70]);                     // faint glass fill
		}
	}
	// the dark outline under the rim reads too heavy -- keep it, but add the glints
	const glint = [[3, 4], [4, 3], [2, 6], [5, 2]];
	for (const [x, y] of glint) { const c = getc(im, x, y); set(im, x, y, c[3] ? mix(c, [255, 255, 255], 0.6) : [255, 255, 255, 230]); }
	// gold collar ring under the dome
	for (const x of [5, 6, 7, 8, 9, 10]) set(im, x, 15, x < 8 ? [233, 180, 50, 255] : [176, 122, 26, 255]);
	set(im, 4, 15, [74, 46, 8, 255]); set(im, 11, 15, [74, 46, 8, 255]);
}

function boltOverlay(im) {
	const Y = hex('#fff27a'), y = hex('#ffd02a'), o = hex('#7a4a00'), w = hex('#ffffff');
	const bolt = [
		[9, 3, w], [10, 3, Y],
		[8, 4, Y], [9, 4, y],
		[7, 5, Y], [8, 5, y],
		[5, 6, w], [6, 6, Y], [7, 6, Y], [8, 6, Y], [9, 6, y], [10, 6, y],
		[8, 7, Y], [9, 7, y],
		[7, 8, Y], [8, 8, y],
		[6, 9, Y], [7, 9, y],
		[6, 10, y],
	];
	const on = new Set(bolt.map(([x, yy]) => x + ',' + yy));
	// dark rim around the bolt where it touches the blue field (down/right side = shadow)
	for (const [x, yy] of bolt) for (const [dx, dy] of [[1, 0], [0, 1], [1, 1]]) {
		const nx = x + dx, ny = yy + dy; if (on.has(nx + ',' + ny)) continue;
		const c = getc(im, nx, ny); if (c[3] && !(c[0] < 40 && c[1] < 40 && c[2] < 40)) set(im, nx, ny, mix(c, [0, 0, 20], 0.5));
	}
	for (const [x, yy, c] of bolt) set(im, x, yy, c);
	void o;
}

// ---------------------------------------------------------------- build
const PIECES = ['helmet', 'chestplate', 'leggings', 'boots'];
const icons = []; // {name, label, im}
for (const s of SUITS) {
	for (const p of PIECES) {
		const rows = suitTemplate(s, p);
		const palette = p === 'helmet' && s.helmetPal ? s.helmetPal : s.pal;
		const im = render(s.id + ' ' + p, rows, palette);
		if (p === 'helmet' && s.dome) domeOverlay(im);
		const name = 'iron_man_' + s.tex + '_' + p;
		icons.push({ name, label: s.label, piece: p, im });
	}
}
const tabIron = render('tab_iron_man', T.tab_iron_man, pal(RED, GOLD, CYAN, '#2a0606', '#3a0a0a'));
const tabHero = render('tab_superheroes', T.tab_superheroes, pal(BLUE, GOLD, CYAN, '#1a1206', '#000000'));
boltOverlay(tabHero);
icons.push({ name: 'tab_icon_iron_man', label: 'TAB', piece: 'iron man', im: tabIron });
icons.push({ name: 'tab_icon_superheroes', label: 'TAB', piece: 'heroes', im: tabHero });

if (!process.argv.includes('--preview-only')) {
	for (const ic of icons) fs.writeFileSync(path.join(OUT, ic.name + '.png'), k.encode(16, 16, ic.im.px));
	console.log('wrote', icons.length, 'icons to', OUT);
}

// ---------------------------------------------------------------- preview sheet
const FONT = {
	'A': '010101111101101', 'B': '110101110101110', 'C': '011100100100011', 'D': '110101101101110', 'E': '111100110100111',
	'F': '111100110100100', 'G': '011100101101011', 'H': '101101111101101', 'I': '111010010010111', 'K': '101101110101101',
	'L': '100100100100111', 'M': '101111111101101', 'N': '110101101101101', 'O': '010101101101010', 'P': '110101110100100',
	'R': '110101110101101', 'S': '011100010001110', 'T': '111010010010010', 'U': '101101101101111', 'V': '101101101101010',
	'X': '101101010101101', 'Y': '101101010010010', '1': '010110010010111', '2': '110001010100111', '8': '010101010101010',
	'x': '000101010101000', ' ': '000000000000000', '-': '000000111000000', 'J': '001001001101010', 'W': '101101111111101',
};
function sheet() {
	const S = 8, cell = 16 * S, pad = 10, labelH = 14;
	const cols = 4 + 1, rowsN = SUITS.length + 1;
	const W = pad + cols * (cell + pad) + 60, H = pad + rowsN * (cell + pad + labelH) + 140;
	const im = { w: W, h: H, px: Buffer.alloc(W * H * 4) };
	const fill = (x0, y0, w, h, c) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) if (x >= 0 && y >= 0 && x < W && y < H) { const i = (y * W + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = 255; } };
	const text = (str, x0, y0, sc, c) => { let x = x0; for (const ch of str.toUpperCase()) { const g = FONT[ch] || FONT[' ']; for (let i = 0; i < 15; i++) if (g[i] === '1') fill(x + (i % 3) * sc, y0 + Math.floor(i / 3) * sc, sc, sc, c); x += 4 * sc; } };
	const blit = (ic, x0, y0, sc) => {
		for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
			const c = getc(ic, x, y); if (!c[3]) continue;
			for (let yy = 0; yy < sc; yy++) for (let xx = 0; xx < sc; xx++) {
				const i = ((y0 + y * sc + yy) * W + x0 + x * sc + xx) * 4; const a = c[3] / 255;
				for (let ch = 0; ch < 3; ch++) im.px[i + ch] = Math.round(c[ch] * a + im.px[i + ch] * (1 - a));
			}
		}
	};
	fill(0, 0, W, H, [198, 198, 198]);                 // inventory background
	const slot = (x, y, sz) => { fill(x - 2, y - 2, sz + 4, sz + 4, [55, 55, 55]); fill(x, y, sz + 2, sz + 2, [255, 255, 255]); fill(x, y, sz, sz, [139, 139, 139]); };
	let y = pad;
	for (let r = 0; r < SUITS.length; r++) {
		text(SUITS[r].label, pad, y, 2, [60, 60, 60]);
		for (let c = 0; c < 4; c++) { const x = pad + 60 + c * (cell + pad); slot(x, y + labelH, cell); blit(icons[r * 4 + c].im, x, y + labelH, S); }
		y += cell + pad + labelH;
	}
	text('TABS', pad, y, 2, [60, 60, 60]);
	for (let c = 0; c < 2; c++) { const x = pad + 60 + c * (cell + pad); slot(x, y + labelH, cell); blit(icons[SUITS.length * 4 + c].im, x, y + labelH, S); }
	// 1x / 2x rows
	y += cell + pad + labelH + 4;
	text('1X', pad, y, 2, [60, 60, 60]);
	icons.forEach((ic, i) => { const x = pad + 30 + (i % 19) * 22; const yy = y + Math.floor(i / 19) * 22; slot(x, yy, 16); blit(ic.im, x, yy, 1); });
	y += 50;
	text('2X', pad, y, 2, [60, 60, 60]);
	icons.forEach((ic, i) => { const x = pad + 30 + (i % 19) * 36; const yy = y + Math.floor(i / 19) * 38; slot(x, yy, 32); blit(ic.im, x, yy, 2); });
	return im;
}
const sh = sheet();
const full = { w: sh.w, h: sh.h, px: Buffer.alloc(sh.w * sh.h * 4) };
sh.px.copy(full.px); for (let i = sh.px.length; i < full.px.length; i += 4) { full.px[i] = full.px[i + 1] = full.px[i + 2] = 198; full.px[i + 3] = 255; }
fs.writeFileSync(path.join(__dirname, 'icons_preview_v0167.png'), k.encode(full.w, full.h, full.px));
console.log('wrote preview', full.w + 'x' + full.h);
