// v0.16.7 serum / vial / flask item textures -- one glassware family.
// node scratchpad/gen_serums_v0167.js   (writes the textures + scratchpad/serums_preview_v0167.png)
// Every vessel is described by a row profile ([y, x0, x1, part]); outline, glass, liquid, cap shading, the specular
// streak and the per-item emblem are all generated from it, so the whole family shares one light model (top-left).
// Tinted items (the per-power mutation serums on minecraft:potion and the per-power reagents) are written as two
// layers: <name>_liquid (greyscale, tintindex 0) + <name>_glass (everything else, untinted).
const fs = require('fs');
const path = require('path');
const { encode, decode } = require('./pngkit.js');
const { Canvas } = require('./lib_concept_models.js');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/item');
const BEFORE = path.join(__dirname, 'serums_before');

// ------------------------------------------------------------------ colour
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const WHITE = [255, 255, 255], BLACK = [0, 0, 0];
const lighten = (c, t) => mix(c, WHITE, t), darken = (c, t) => mix(c, BLACK, t);
const ramp = c => { c = typeof c === 'string' ? hex(c) : c; return [lighten(c, 0.55), c, darken(c, 0.22), darken(c, 0.45)]; };
const P = arr => arr.map(v => typeof v === 'string' ? hex(v) : v);

// metals: [top light, body, band, dark edge]
const STEEL = P(['#eef3f7', '#b8c2cc', '#8a95a3', '#5a6370']);
const GUN = P(['#a3acb7', '#6c7683', '#4e5762', '#2f353e']);
const GOLD = P(['#fff4b0', '#f4c63e', '#c98f1e', '#8a5a10']);
const RUBBER = P(['#6b6f7a', '#45484f', '#33353b', '#202227']);
const GLASS = { body: hex('#c4d8e4'), hi: hex('#ffffff'), lo: hex('#97afc2') };
const OUTLINE = hex('#1a1c28');

// ------------------------------------------------------------------ image
const newImg = () => ({ w: 16, h: 16, px: Buffer.alloc(16 * 16 * 4) });
function put(im, x, y, c, a = 255) {
	if (x < 0 || y < 0 || x > 15 || y > 15) return;
	const i = (y * 16 + x) * 4;
	const da = im.px[i + 3] / 255, sa = a / 255, oa = sa + da * (1 - sa);
	for (let k = 0; k < 3; k++) im.px[i + k] = oa ? Math.round((c[k] * sa + im.px[i + k] * da * (1 - sa)) / oa) : 0;
	im.px[i + 3] = Math.round(oa * 255);
}
const clear = (im, x, y) => { const i = (y * 16 + x) * 4; im.px[i] = im.px[i + 1] = im.px[i + 2] = im.px[i + 3] = 0; };
const getA = (im, x, y) => im.px[(y * 16 + x) * 4 + 3];

// ------------------------------------------------------------------ profiles
// part: cap (metal), neck / body (glass, filled with liquid from liquidTop), base (metal, below the glass)
const rows = (y0, y1, x0, x1, part) => { const r = []; for (let y = y0; y <= y1; y++) r.push([y, x0, x1, part]); return r; };
const PROFILES = {
	vial: [[1, 6, 9, 'cap'], [2, 5, 10, 'cap'], [3, 5, 10, 'cap'], [4, 6, 9, 'neck'], [5, 5, 10, 'body'], ...rows(6, 12, 4, 11, 'body'), [13, 5, 10, 'body']],
	syringe: [[1, 5, 10, 'cap'], [2, 7, 8, 'cap'], [3, 7, 8, 'cap'], [4, 4, 11, 'cap'], ...rows(5, 11, 5, 10, 'body'), [12, 6, 9, 'base']],
	shield: [[1, 6, 9, 'cap'], [2, 5, 10, 'cap'], [3, 6, 9, 'neck'], ...rows(4, 9, 3, 12, 'body'), [10, 4, 11, 'body'], [11, 5, 10, 'body'], [12, 6, 9, 'body'], [13, 7, 8, 'body']],
	round: [[1, 6, 9, 'cap'], [2, 6, 9, 'cap'], [3, 6, 9, 'neck'], [4, 5, 10, 'body'], [5, 4, 11, 'body'], ...rows(6, 11, 3, 12, 'body'), [12, 4, 11, 'body'], [13, 5, 10, 'body']],
	diamond: [[1, 6, 9, 'cap'], [2, 6, 9, 'cap'], [3, 7, 8, 'neck'], [4, 6, 9, 'body'], [5, 5, 10, 'body'], [6, 4, 11, 'body'], [7, 3, 12, 'body'], [8, 3, 12, 'body'], [9, 4, 11, 'body'], [10, 5, 10, 'body'], [11, 6, 9, 'body'], [12, 7, 8, 'body']],
	cartridge: [[1, 7, 8, 'cap'], [2, 5, 10, 'cap'], [3, 4, 11, 'cap'], ...rows(4, 11, 4, 11, 'body'), [12, 4, 11, 'base'], [13, 5, 10, 'base']],
	jar: [[1, 5, 10, 'cap'], [2, 4, 11, 'cap'], [3, 4, 11, 'cap'], [4, 5, 10, 'neck'], ...rows(5, 12, 3, 12, 'body'), [13, 4, 11, 'body']],
	canister: [[1, 5, 10, 'cap'], [2, 4, 11, 'cap'], ...rows(3, 11, 5, 10, 'body'), [12, 4, 11, 'base'], [13, 5, 10, 'base']],
	erlenmeyer: [[1, 6, 9, 'cap'], [2, 6, 9, 'cap'], [3, 6, 9, 'neck'], [4, 6, 9, 'neck'], [5, 5, 10, 'body'], [6, 5, 10, 'body'], [7, 4, 11, 'body'], [8, 4, 11, 'body'], [9, 3, 12, 'body'], [10, 3, 12, 'body'], ...rows(11, 13, 2, 13, 'body')],
};

// ------------------------------------------------------------------ vessel painter
// returns { full, liquid, glass } images; liquid/glass are the two tint layers (liquid greyscale) when opt.tint.
function vessel(o) {
	const prof = PROFILES[o.profile];
	const role = Array.from({ length: 16 }, () => Array(16).fill(null));
	const span = {};
	for (const [y, x0, x1, part] of prof) {
		span[y] = [x0, x1, part];
		for (let x = x0; x <= x1; x++) role[y][x] = part === 'cap' || part === 'base' ? part : (y >= o.liquidTop ? 'liquid' : 'glass');
	}
	const liqRows = prof.filter(([y, , , p]) => (p === 'body' || p === 'neck') && y >= o.liquidTop).map(r => r[0]);
	const lTop = Math.min(...liqRows), lBot = Math.max(...liqRows), nL = lBot - lTop + 1;
	const capRows = prof.filter(r => r[3] === 'cap').map(r => r[0]);
	const baseRows = prof.filter(r => r[3] === 'base').map(r => r[0]);

	const full = newImg(), liq = newImg(), gl = newImg();
	const metal = o.cap || STEEL, baseMetal = o.base || metal;
	const L = o.tint ? [[255, 255, 255], [228, 228, 228], [186, 186, 186], [140, 140, 140]].map(x => x) : o.liquid;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const r = role[y][x];
		if (!r) continue;
		const [x0, x1] = span[y];
		let c;
		if (r === 'cap' || r === 'base') {
			const m = r === 'cap' ? metal : baseMetal;
			const list = r === 'cap' ? capRows : baseRows;
			const i = list.indexOf(y), n = list.length;
			c = i === 0 && r === 'cap' ? m[0] : (i === n - 1 && n > 1 ? m[2] : m[1]);
			if (r === 'base' && i === n - 1) c = m[2];
			if (x === x0) c = mix(c, m[0], 0.6);
			if (x === x1) c = m[3];
			put(full, x, y, c); put(gl, x, y, c);
		} else if (r === 'glass') {
			c = x === x0 ? GLASS.hi : x === x1 ? GLASS.lo : (o.glass || GLASS.body);
			if (o.glassFn) c = o.glassFn(x, y, c) || c;
			put(full, x, y, c); put(gl, x, y, c);
		} else {
			const i = y - lTop;
			let k = i === 0 ? 0 : i < Math.max(2, Math.round(nL * 0.55)) ? 1 : y === lBot ? 3 : 2;
			if (x === x1 && k < 3) k++;
			let base = o.liqRow && !o.tint ? o.liqRow(i, x, k) : L[k];
			if (x === x0 && k > 0) base = mix(base, L[0], 0.35);
			c = base;
			put(full, x, y, c); put(liq, x, y, c);
		}
	}
	// outline (4-neighbour, so stepped rows stay rounded)
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (role[y][x]) continue;
		const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => role[y + dy] && role[y + dy][x + dx]);
		if (n) { put(full, x, y, o.outline || OUTLINE); put(gl, x, y, o.outline || OUTLINE); }
	}
	// overlays drawn over glass + liquid: [x, y, colour, alpha]
	const over = [];
	const streak = o.streak || { x: null };
	if (streak.x != null) for (let y = streak.y0; y <= streak.y1; y++) over.push([streak.x, y, WHITE, y === streak.y0 ? 230 : 150]);
	if (o.glint) for (const [x, y] of o.glint) over.push([x, y, WHITE, 120]);
	if (o.emblem) {
		const { at: [ex, ey], map, pal } = o.emblem;
		map.forEach((row, dy) => [...row].forEach((ch, dx) => { if (pal[ch]) over.push([ex + dx, ey + dy, typeof pal[ch] === 'string' ? hex(pal[ch]) : pal[ch][0], typeof pal[ch] === 'string' ? 255 : pal[ch][1]]); }));
	}
	if (o.extra) for (const e of o.extra) over.push([e[0], e[1], typeof e[2] === 'string' ? hex(e[2]) : e[2], e[3] == null ? 255 : e[3]]);
	for (const [x, y, c, a] of over) {
		put(full, x, y, c, a);
		if (role[y] && role[y][x] === 'liquid') put(gl, x, y, c, a); else put(gl, x, y, c, a);
	}
	// outside-the-vessel decoration (glow, steam, sparkles): never inside the shape
	if (o.outside) for (const e of o.outside) {
		const [x, y] = e; if (role[y] && role[y][x]) continue; if (getA(full, x, y) && !e[4]) continue;
		const c = typeof e[2] === 'string' ? hex(e[2]) : e[2];
		put(full, x, y, c, e[3] == null ? 255 : e[3]); put(gl, x, y, c, e[3] == null ? 255 : e[3]);
	}
	return { full, liquid: liq, glass: gl };
}

// ------------------------------------------------------------------ emblems
const STAR5 = ['..#..', '#####', '.###.', '.#.#.'];
const ITEMS = [];
const add = (name, o, before) => ITEMS.push({ name, o, before: before === undefined ? name : before });

// Super Soldier: refined = glowing blue + white star, unrefined = murky grey-blue with sediment
add('refined_super_soldier_serum', {
	profile: 'vial', liquidTop: 6, liquid: P(['#c8f4ff', '#45b4ff', '#2a7ce6', '#1a4ea6']), cap: STEEL,
	streak: { x: 5, y0: 6, y1: 8 }, glint: [[10, 12]],
	emblem: { at: [6, 8], map: STAR5, pal: { '#': '#ffffff' } },
	outside: [[3, 4, '#9fe2ff', 110], [12, 6, '#9fe2ff', 110], [2, 9, '#9fe2ff', 70], [13, 11, '#9fe2ff', 70]],
});
add('unrefined_super_soldier_serum', {
	profile: 'vial', liquidTop: 7, liquid: P(['#b9c6cf', '#7d8e9c', '#617384', '#45535f']), cap: GUN,
	streak: { x: 5, y0: 6, y1: 8 },
	extra: [[7, 9, '#4a5966'], [9, 10, '#4a5966'], [6, 11, '#53636f'], [8, 12, '#3c4752'], [10, 11, '#3c4752'], [7, 12, '#4a5966']],
});
// Adamantium: liquid metal with three claw marks
add('adamantium_serum', {
	profile: 'vial', liquidTop: 6, liquid: P(['#ffffff', '#c3ccd6', '#939fac', '#626d7a']), cap: GUN,
	streak: { x: 5, y0: 6, y1: 9 }, glint: [[10, 12]],
	// liquid chrome: two bright diagonal sheen bands and a dark reflection band between them
	liqRow: (i, x, k) => {
		const L = P(['#ffffff', '#c3ccd6', '#939fac', '#626d7a']); const d = x + i;
		if (k === 0 || k === 3) return L[k];
		return d === 9 || d === 13 ? mix(L[k], WHITE, 0.7) : d === 11 ? darken(L[k], 0.25) : L[k];
	},
	emblem: { at: [7, 9], map: ['#.#.#'.slice(0, 0)], pal: {} },
});
// Gamma: toxic green, hazard tag, radiation glow
add('gamma_serum', {
	profile: 'vial', liquidTop: 6, liquid: P(['#efffb8', '#7dff3a', '#3fc21a', '#1f7a12']), cap: STEEL,
	streak: { x: 5, y0: 6, y1: 7 }, glint: [[10, 12]],
	emblem: { at: [5, 9], map: ['YYYYYY', 'yyKKyy'], pal: { Y: '#ffd83a', y: '#d9a91f', K: '#1d1d1d' } },
	extra: [[7, 12, '#c8ff8a'], [9, 7, '#c8ff8a'], [6, 11, '#a6ff5e']],
	outside: [[3, 6, '#7dff3a', 120], [12, 7, '#7dff3a', 120], [3, 11, '#7dff3a', 90], [12, 12, '#7dff3a', 90], [2, 8, '#7dff3a', 60], [13, 9, '#7dff3a', 60], [5, 0, '#7dff3a', 70], [11, 2, '#7dff3a', 70]],
});
// Titan: Attack-on-Titan spinal-fluid syringe, steaming
add('titan_serum', {
	profile: 'syringe', liquidTop: 6, liquid: P(['#ffd2a6', '#ff6b2b', '#d23c14', '#8e200b']), cap: STEEL, base: STEEL,
	streak: { x: 6, y0: 6, y1: 9 },
	extra: [[10, 6, '#7a1a08'], [10, 8, '#7a1a08'], [10, 10, '#7a1a08'], [7, 13, '#e6ecf1'], [8, 13, '#7d8896'], [7, 14, '#c3ccd5']],
	outside: [[12, 3, '#e8e8e8', 150], [13, 2, '#f4f4f4', 120], [12, 1, '#ffffff', 90], [3, 7, '#e8e8e8', 120], [2, 6, '#ffffff', 80], [13, 9, '#e8e8e8', 100]],
});
// Heroic: gold shield-shaped bottle with a white star
add('heroic_serum', {
	profile: 'shield', liquidTop: 5, liquid: P(['#fff7c8', '#ffd23a', '#eaa21b', '#a96b0c']), cap: P(['#ff9a9a', '#d8343c', '#a01d27', '#6a1018']),
	streak: { x: 4, y0: 5, y1: 8 }, glint: [[11, 9]],
	emblem: { at: [6, 6], map: ['..#..', '#####', '.###.', '.#.#.'], pal: { '#': '#ffffff' } },
	outside: [[13, 2, '#fff2a0', 200], [14, 1, '#fff2a0', 110], [12, 1, '#fff2a0', 110], [13, 0, '#fff2a0', 110], [14, 3, '#fff2a0', 110]],
});
// Mutagenic: round flask of unstable purple with a green DNA strand + bubbles
add('mutagenic_serum', {
	profile: 'round', liquidTop: 6, liquid: P(['#f0c8ff', '#b04dff', '#7a25c9', '#4a1385']), cap: STEEL,
	streak: { x: 4, y0: 6, y1: 8 }, glint: [[11, 11]],
	emblem: { at: [7, 7], map: ['G.g', '.W.', 'g.G', '.W.', 'G.g'], pal: { G: '#9dff5a', g: '#5fd13a', W: '#ffffff' } },
	extra: [[5, 11, '#d79cff'], [10, 8, '#d79cff'], [11, 6, '#e8c4ff']],
	outside: [[12, 3, '#c98cff', 150], [13, 1, '#c98cff', 100]],
});
// Prismatic: crystal bottle of rainbow bands, gold cap, sparkle
const RAINBOW = P(['#ff4d5e', '#ff9a3c', '#ffe14d', '#5fe06a', '#3fd6e8', '#4f7cff', '#9a5cff', '#e05cff']);
add('prismatic_serum', {
	profile: 'diamond', liquidTop: 5, liquid: P(['#ffffff', '#cccccc', '#999999', '#666666']), cap: GOLD,
	liqRow: (i, x, k) => { const c = RAINBOW[Math.min(7, i)]; return k === 0 ? lighten(c, 0.35) : (k >= 2 && x > 7 ? darken(c, 0.2) : c); },
	streak: { x: 5, y0: 5, y1: 7 }, glint: [[4, 7], [9, 10]],
	outside: [[13, 3, '#ffffff', 230], [12, 3, '#ffffff', 120], [14, 3, '#ffffff', 120], [13, 2, '#ffffff', 120], [13, 4, '#ffffff', 120], [2, 10, '#ffffff', 150]],
});
// Spider-Man arachnid mutagen: red with a black spider
add('arachnid_mutagen', {
	profile: 'vial', liquidTop: 6, liquid: P(['#ffb6b6', '#e6303b', '#a8141f', '#6a0a12']), cap: STEEL,
	streak: { x: 5, y0: 6, y1: 7 }, glint: [[10, 12]],
	emblem: { at: [6, 8], map: ['#...#', '.###.', '#####', '.###.', '#...#'], pal: { '#': '#141016' } },
});
// Grave essence: dark jar with a ghostly teal wisp
add('grave_essence', {
	profile: 'jar', liquidTop: 5, liquid: P(['#3c7774', '#21454b', '#18343b', '#10242a']), cap: GUN, glass: hex('#9fb8bd'),
	streak: { x: 4, y0: 5, y1: 7 }, glint: [[11, 12]],
	emblem: {
		at: [5, 6], map: ['..cww.', '.c..Wc', '.c...c', '..cc..', '....c.', '..cWc.', '...c..'],
		pal: { c: '#58f2d6', W: '#ffffff', w: '#c8fff4' },
	},
	outside: [[2, 6, '#58f2d6', 70], [13, 9, '#58f2d6', 70], [7, 0, '#58f2d6', 60]],
});
// Symbiote canister: empty (glass + residue) and filled (black ooze, white tendril)
add('symbiote_vial', {
	profile: 'canister', liquidTop: 11, liquid: P(['#5a5470', '#26212f', '#17131e', '#0b0910']), cap: GUN, base: GUN,
	streak: { x: 6, y0: 3, y1: 7 },
	extra: [[7, 10, '#26212f'], [9, 9, '#26212f'], [9, 10, '#17131e']],
});
add('symbiote_vial_filled', {
	profile: 'canister', liquidTop: 3, liquid: P(['#4a4460', '#1b1726', '#110d19', '#07050c']), cap: GUN, base: GUN,
	streak: { x: 6, y0: 3, y1: 4 },
	extra: [[8, 4, '#e8e8ff'], [8, 5, '#ffffff'], [7, 6, '#ffffff'], [7, 7, '#e8e8ff'], [8, 8, '#ffffff'], [9, 9, '#e8e8ff'], [8, 10, '#bdbdd8'], [6, 9, '#bdbdd8'], [6, 10, '#8f8aa8']],
});

// Quirk serums: one cartridge family, liquid = quirk colour, emblem = the quirk
const QUIRKS = {
	hellflame: ['#ff6a1a', { at: [5, 5], map: ['...Y..', '..YY..', '.YWY..', '.YWWY.', 'YYWWYY', '.YYYY.'], pal: { Y: '#ffe14d', W: '#ffffff' } }],
	overhaul: ['#8fd3c7', { at: [5, 5], map: ['..##..', '..##..', '######', '######', '..##..', '..##..'], pal: { '#': '#ffffff' } }],
	engine: ['#5aa9ff', { at: [5, 5], map: ['.#..#.', '######', '##..##', '##..##', '######', '.#..#.'], pal: { '#': '#eef3f7' } }],
	dark_shadow: ['#7a3cff', { at: [5, 5], map: ['.KKKK.', 'KKKKKK', 'KYKKYK', 'KKKKKK', '.KKKK.', '.K..K.'], pal: { K: '#120a22', Y: '#ffe14d' } }],
	erasure: ['#e04040', { at: [4, 6], map: ['.YYYYYY.', 'YRRYYRRY', '.YYYYYY.'], pal: { Y: '#ffe14d', R: '#ff9a9a' } }],
	explosion: ['#ffb020', { at: [5, 5], map: ['..W...', 'W.W.W.', '.WWW..', 'WWYWW.', '.WWW..', 'W.W.W.'], pal: { W: '#ffffff', Y: '#fff2a0' } }],
	zero_gravity: ['#ff8fc8', { at: [5, 5], map: ['.WWWW.', 'WW..WW', 'W....W', 'W....W', 'WW..WW', '.WWWW.'], pal: { W: '#ffffff' } }],
	random: ['#c03a4a', { at: [5, 5], map: ['.WWWW.', 'WW..WW', '...WW.', '..WW..', '......', '..WW..'], pal: { W: '#ffffff' } }],
};
for (const [q, [col, emblem]] of Object.entries(QUIRKS)) {
	add('quirk_serum_' + q, {
		profile: 'cartridge', liquidTop: 5, liquid: ramp(col), cap: STEEL, base: STEEL,
		streak: null, glint: [[4, 5], [4, 6], [4, 7]], emblem,
	});
}

// Tinted families
const TINTED = [
	{ name: 'mutation_serum', o: {
		profile: 'vial', liquidTop: 6, tint: true, cap: RUBBER, streak: { x: 5, y0: 6, y1: 8 }, glint: [[10, 12]],
		emblem: { at: [5, 9], map: ['LLLLLL', 'llKlKl'], pal: { L: '#efe9dc', l: '#cdc5b3', K: '#3a3a46' } },
	}, before: null },
	{ name: 'experimental_reagent', o: {
		profile: 'erlenmeyer', liquidTop: 8, tint: true, cap: RUBBER, glass: hex('#aec6d6'), streak: null, glint: [[12, 12], [6, 6], [5, 7]],
		extra: [[5, 10, WHITE, 120], [4, 11, WHITE, 110]],
	}, before: 'experimental_reagent' },
];
// power colours (keep in sync with MutationSerumColors.java)
const POWER_COLORS = {
	super_strength: '#e0482b', laser_vision: '#ff2f5a', flight: '#5fb0ff', super_speed: '#ffd52a', geokinesis: '#a8743c',
	pyrokinesis: '#ff8a1f', cryokinesis: '#c8faff', teleportation: '#b05cff', super_regeneration: '#4ce06a',
	elasticity: '#ff6fd0', wind_manipulation: '#b8f0d8', water_manipulation: '#2f5fff',
};
const DEFAULT_TINT = '#9b4dff';

// ------------------------------------------------------------------ write
const write = (im, name) => fs.writeFileSync(path.join(OUT, name + '.png'), encode(16, 16, im.px));
const tintImg = (liq, glass, col) => {
	const out = newImg(), c = hex(col);
	for (let i = 0; i < 256; i++) if (liq.px[i * 4 + 3]) { out.px[i * 4] = liq.px[i * 4] * c[0] / 255; out.px[i * 4 + 1] = liq.px[i * 4 + 1] * c[1] / 255; out.px[i * 4 + 2] = liq.px[i * 4 + 2] * c[2] / 255; out.px[i * 4 + 3] = 255; }
	for (let i = 0; i < 256; i++) if (glass.px[i * 4 + 3]) put(out, i % 16, i >> 4, [glass.px[i * 4], glass.px[i * 4 + 1], glass.px[i * 4 + 2]], glass.px[i * 4 + 3]);
	return out;
};
const results = [];
for (const it of ITEMS) {
	const r = vessel(it.o);
	write(r.full, it.name);
	results.push({ name: it.name, img: r.full, before: it.before });
}
const tinted = {};
for (const t of TINTED) {
	const r = vessel(t.o);
	write(r.liquid, t.name + '_liquid');
	write(r.glass, t.name + '_glass');
	tinted[t.name] = r;
	const composite = tintImg(r.liquid, r.glass, DEFAULT_TINT);
	if (t.name === 'experimental_reagent') write(composite, 'experimental_reagent'); // still the plain fallback texture
	results.push({ name: t.name + ' (tinted)', img: composite, before: t.before });
}

// ------------------------------------------------------------------ preview
const beforeImg = name => {
	if (name === null) { // vanilla potion tinted the old Unstable Mutation purple
		const pot = decode(path.join(BEFORE, 'potion.png')), ov = decode(path.join(BEFORE, 'potion_overlay.png'));
		const liq = newImg(); ov.px.copy(liq.px);
		const gl = newImg(); pot.px.copy(gl.px);
		return tintImg(liq, gl, '#9b4dff');
	}
	const f = path.join(BEFORE, name + '.png');
	return fs.existsSync(f) ? decode(f) : null;
};
const draw = (cv, im, x0, y0, k) => {
	for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) {
		const s = (y * im.w + x) * 4; const a = im.px[s + 3];
		if (a) cv.rect(x0 + x * k * 16 / im.w, y0 + y * k * 16 / im.h, k * 16 / im.w, k * 16 / im.h, [im.px[s], im.px[s + 1], im.px[s + 2]], a / 255);
	}
};
const slot = (cv, x, y, s) => { // inventory slot: dark top-left, light bottom-right, grey fill
	cv.rect(x, y, s, s, [139, 139, 139]); cv.rect(x, y, s, 1, [55, 55, 55]); cv.rect(x, y, 1, s, [55, 55, 55]);
	cv.rect(x, y + s - 1, s, 1, [255, 255, 255]); cv.rect(x + s - 1, y, 1, s, [255, 255, 255]);
};
const K = 8, CELL = 16 * K, PAD = 14, COLS = 3;
const pairW = CELL * 2 + 3 * PAD, pairH = CELL + 2 * PAD + 22;
const gridRows = Math.ceil(results.length / COLS);
const powers = Object.keys(POWER_COLORS);
const W = COLS * pairW + PAD, HEAD = 60;
const smallY = HEAD + gridRows * pairH + 10;
const tintY = smallY + 130;
const H = tintY + 2 * (4 * 16 + 34) + 30;
const cv = new Canvas(W, H, [198, 198, 198]);
cv.text('PROJECT HERO V0.16.7 - SERUMS / VIALS / FLASKS', PAD, 14, 3, [40, 40, 40], { shadow: false });
cv.text('LEFT = BEFORE   RIGHT = AFTER   (8X)', PAD, 40, 2, [70, 70, 70], { shadow: false });
results.forEach((r, i) => {
	const x = PAD + (i % COLS) * pairW, y = HEAD + Math.floor(i / COLS) * pairH;
	cv.rect(x, y, pairW - PAD, pairH - 8, [139, 139, 139]);
	const b = beforeImg(r.before);
	if (b) draw(cv, b, x + PAD, y + PAD, K); else cv.text('NEW', x + PAD + 40, y + PAD + 56, 3, [80, 80, 80], { shadow: false });
	draw(cv, r.img, x + 2 * PAD + CELL, y + PAD, K);
	cv.text(r.name.replace(/_/g, ' '), x + PAD, y + PAD + CELL + 6, 1, [255, 255, 255]);
});
// 1x / 2x inventory strip
cv.text('IN-SLOT SIZE: 1X (TOP) AND 2X (GUI SCALE 2)', PAD, smallY, 2, [40, 40, 40], { shadow: false });
results.forEach((r, i) => {
	const x = PAD + i * 38;
	slot(cv, x, smallY + 22, 18); draw(cv, r.img, x + 1, smallY + 23, 1);
	slot(cv, x, smallY + 50, 36); draw(cv, r.img, x + 2, smallY + 52, 2);
});
// tint rows
['mutation_serum', 'experimental_reagent'].forEach((n, row) => {
	const y = tintY + row * (4 * 16 + 34);
	cv.text((n === 'mutation_serum' ? 'MUTATION SERUM (MINECRAFT:POTION)' : 'REAGENTS') + ' PER POWER, 4X', PAD, y, 2, [40, 40, 40], { shadow: false });
	powers.forEach((p, i) => {
		const x = PAD + i * 76;
		cv.rect(x, y + 20, 68, 68, [139, 139, 139]);
		draw(cv, tintImg(tinted[n].liquid, tinted[n].glass, POWER_COLORS[p]), x + 2, y + 22, 4);
		cv.text(p.replace('_manipulation', '').replace('super_', 's.').slice(0, 11), x, y + 90, 1, [30, 30, 30], { shadow: false });
	});
});
cv.save(path.join(__dirname, 'serums_preview_v0167.png'));
console.log('wrote', results.length, 'items + preview');
