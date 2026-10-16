// v0.16.5 film / comic Spider-Man suits: textures, 16x16 icons, item models, recipes, recipe advancements.
// Skins: minecraftskins.com (fetched through the browser -- the site 403s curl), saved under skins/.
// Run from the repo root: node scratchpad/v0165_suits/gen.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const P = require('./png.js');
const D = require('./icon_designs.js');

const HERE = __dirname;
const ASSETS = 'src/main/resources/assets/projecthero';
const DATA = 'src/main/resources/data/projecthero';

// skin -> source file, emblem pass (null = untouched), icon design, recipe dyes and the extra ingredient
const SUITS = {
	comic: { src: 'classic', emblem: null, design: 'v2', a: 'minecraft:red_dye', b: 'minecraft:blue_dye', c: 'minecraft:paper' },
	bnd: { src: 'bnd', emblem: null, design: 'orig', a: 'minecraft:red_dye', b: 'minecraft:blue_dye', c: 'minecraft:sunflower' },
	tasm: { src: 'tasm', emblem: null, design: 'orig', a: 'minecraft:red_dye', b: 'minecraft:blue_dye', c: 'minecraft:gold_nugget' },
	tasm2: { src: 'tasm2', emblem: null, design: 'orig', a: 'minecraft:red_dye', b: 'minecraft:blue_dye', c: 'minecraft:white_dye' },
	raimi: { src: 'raimi', emblem: null, design: 'v2', a: 'minecraft:red_dye', b: 'minecraft:blue_dye', c: 'minecraft:cobweb' },
	future_foundation: { src: 'future_foundation', emblem: null, design: 'v2', a: 'minecraft:white_dye', b: 'minecraft:black_dye', c: 'minecraft:string' },
};
const PIECES = ['helmet', 'chestplate', 'leggings', 'boots'];

const toHex = c => '#' + c.slice(0, 3).map(v => v.toString(16).padStart(2, '0')).join('');
const lum = c => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];

/** A 6-step ramp (outline .. highlight) from the skin's pixels that pass `test`, by luminance percentile. */
function ramp(px, test) {
	const cols = [];
	for (let y = 16; y < 32; y++) for (let x = 16; x < 56; x++) { // body + arms, base layer
		const i = (y * 64 + x) * 4;
		const c = [px[i], px[i + 1], px[i + 2], px[i + 3]];
		if (c[3] > 0 && test(c)) cols.push(c);
	}
	for (let y = 48; y < 64; y++) for (let x = 16; x < 48; x++) { // legs
		const i = (y * 64 + x) * 4;
		const c = [px[i], px[i + 1], px[i + 2], px[i + 3]];
		if (c[3] > 0 && test(c)) cols.push(c);
	}
	cols.sort((p, q) => lum(p) - lum(q));
	const at = f => cols[Math.min(cols.length - 1, Math.floor(f * cols.length))];
	const dark = at(0.05).map(v => Math.round(v * 0.55));
	return [toHex(dark), toHex(at(0.12)), toHex(at(0.3)), toHex(at(0.55)), toHex(at(0.78)), toHex(at(0.96))];
}

const red = c => c[0] > c[2] + 30 && c[0] > c[1] + 30;
const blue = c => c[2] > c[0] + 8;
const white = c => lum(c) > 150;
const black = c => lum(c) < 60;

const outTex = path.join(ASSETS, 'textures/armor');
const outIcon = path.join(ASSETS, 'textures/item');
const icons = [];
for (const [skin, s] of Object.entries(SUITS)) {
	const src = path.join(HERE, 'skins', s.src + '.png');
	const dst = path.join(outTex, 'spider_man_' + skin + '.png');
	if (s.emblem) {
		execFileSync('node', [path.join(HERE, 'emblem.js'), src, dst, ...s.emblem.map(String)], { stdio: 'inherit' });
	} else {
		fs.copyFileSync(src, dst);
	}
	const { px } = P.read(dst);
	let pal;
	if (skin === 'future_foundation') {
		pal = { R: ramp(px, white), B: ramp(px, black), w: '#9aa0a8', K: '#0b0b0d', E: '#1a1a1e', e: '#050506', S: '#5a5a62' };
	} else {
		// the 2002 suit has pinkish raised webbing -- only its saturated reds make the icon ramp
		const R = ramp(px, skin === "raimi" ? c => red(c) && c[0] > c[1] * 2.6 : red), B = ramp(px, blue);
		const lens = skin === 'tasm' ? { E: '#e2a640', e: '#a86a1e', S: '#ffe9b0' } : { E: '#f0f3f6', e: '#c4c8d6', S: '#ffffff' };
		pal = { R, B, w: R[1], K: '#0b0a0e', ...lens };
	}
	const grids = [D.HELMET[s.design], D.CHEST[s.design], D.LEGS[s.design], D.BOOTS[s.design]];
	PIECES.forEach((piece, k) => {
		const id = `spider_man_suit_${skin}_${piece}`;
		const ipx = D.render(grids[k], pal);
		icons.push(ipx);
		P.write(path.join(outIcon, id + '.png'), 16, 16, ipx);
		fs.writeFileSync(path.join(ASSETS, 'models/item', id + '.json'), JSON.stringify({
			parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + id } }, null, 2) + '\n');
		// "AHB" over " C ": the original piece in the middle (kept -- spider_suit_upgrade), two dyes and the suit's own extra
		fs.writeFileSync(path.join(DATA, 'recipe', id + '.json'), JSON.stringify({
			type: 'projecthero:spider_suit_upgrade', category: 'equipment', pattern: ['AHB', ' C '],
			key: { A: { item: s.a }, H: { item: 'projecthero:spider_man_suit_' + piece }, B: { item: s.b }, C: { item: s.c } },
			result: { id: 'projecthero:' + id, count: 1 } }, null, 2) + '\n');
		fs.writeFileSync(path.join(DATA, 'advancement/recipes', id + '.json'), JSON.stringify({
			parent: 'minecraft:recipes/root',
			criteria: {
				has_old_piece: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: 'projecthero:spider_man_suit_' + piece }] } },
				has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:' + id } } },
			requirements: [['has_old_piece', 'has_the_recipe']],
			rewards: { recipes: ['projecthero:' + id] } }, null, 2) + '\n');
	});
	console.log(skin, JSON.stringify(pal));
}

// preview: one row per suit, 8x
const S = 8, CELL = 16 * S + 8, W = CELL * 4 + 8, H = CELL * icons.length / 4 + 8;
const out = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) out.set([139, 139, 139, 255], i * 4);
icons.forEach((ipx, k) => {
	const ox = 8 + (k % 4) * CELL, oy = 8 + Math.floor(k / 4) * CELL;
	for (let y = 0; y < 16 * S; y++) for (let x = 0; x < 16 * S; x++) {
		const i = ((y / S | 0) * 16 + (x / S | 0)) * 4;
		if (ipx[i + 3]) out.set([ipx[i], ipx[i + 1], ipx[i + 2], 255], ((oy + y) * W + ox + x) * 4);
	}
});
P.write(path.join(HERE, 'icons_preview.png'), W, H, out);
