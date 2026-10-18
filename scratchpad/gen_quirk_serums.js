// v0.16.7 core: Quirk Serum assets -- 16x16 vial textures, item models, recipes; the 18x18 "Erased" effect icon.
// run from the worktree root: node scratchpad/gen_quirk_serums.js
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const RES = "src/main/resources";
const TEX = path.join(RES, "assets/projecthero/textures/item");
const MODELS = path.join(RES, "assets/projecthero/models/item");
const RECIPES = path.join(RES, "data/projecthero/recipe");
const EFFECT = path.join(RES, "assets/projecthero/textures/mob_effect");

function crc32(buf) {
	let c, crc = 0xffffffff;
	for (let n = 0; n < buf.length; n++) {
		c = (crc ^ buf[n]) & 0xff;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		crc = (crc >>> 8) ^ c;
	}
	return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
	const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type), data]);
	const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}
function png(w, h, px) { // px: array of [r,g,b,a]
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0;
		for (let x = 0; x < w; x++) {
			const p = px[y * w + x] || [0, 0, 0, 0];
			raw.set(p, y * (w * 4 + 1) + 1 + x * 4);
		}
	}
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const hex = (c) => [(c >> 16) & 255, (c >> 8) & 255, c & 255, 255];
const shade = (c, f) => { const [r, g, b] = hex(c); const m = (v) => Math.max(0, Math.min(255, Math.round(f >= 1 ? v + (255 - v) * (f - 1) : v * f))); return [m(r), m(g), m(b), 255]; };

// quirk id -> [liquid colour, accent (symbol / bubbles), band colour]
const QUIRKS = {
	hellflame: [0xff6a1a, 0xffe14a, 0x8a2a10],
	overhaul: [0x8fd3c7, 0xe8fff8, 0x2f5a52],
	engine: [0x5aa9ff, 0xdff0ff, 0x24427a],
	dark_shadow: [0x7a3cff, 0xd0b0ff, 0x2a1060],
	erasure: [0xe04040, 0xfff0d0, 0x3a3a3a],
	explosion: [0xffb020, 0xfff2b0, 0x5a3a10],
	zero_gravity: [0xff8fc8, 0xfff0f8, 0x6a3a5a],
	random: [0xd02030, 0x202020, 0xc8a040],
};

// the vial: 16x16, cork rows 1-2, neck rows 3-4, bulb 5-14 (x 3..12)
function vial(liquid, accent, band) {
	const px = new Array(256).fill(null);
	const set = (x, y, c) => { if (x >= 0 && x < 16 && y >= 0 && y < 16) px[y * 16 + x] = c; };
	const glassDark = [52, 60, 72, 255], glass = [150, 170, 190, 255], glassHi = [235, 245, 255, 255];
	// cork
	for (let x = 6; x <= 9; x++) { set(x, 1, [150, 104, 60, 255]); set(x, 2, [112, 76, 42, 255]); }
	set(6, 1, [176, 128, 80, 255]);
	// neck (glass)
	for (let y = 3; y <= 4; y++) { set(6, y, glassDark); set(9, y, glassDark); set(7, y, glass); set(8, y, [120, 140, 160, 255]); }
	set(7, 3, glassHi);
	// band around the neck
	for (let x = 5; x <= 10; x++) set(x, 5, x === 5 || x === 10 ? shade(band, 0.7) : hex(band));
	set(6, 5, shade(band, 1.35));
	// bulb outline (rounded rectangle x 3..12, y 6..14)
	const inside = (x, y) => {
		if (y < 6 || y > 14 || x < 3 || x > 12) return false;
		if ((y === 6 || y === 14) && (x === 3 || x === 12)) return false;
		return true;
	};
	for (let y = 6; y <= 14; y++) for (let x = 3; x <= 12; x++) {
		if (!inside(x, y)) continue;
		const edge = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1);
		if (edge) { set(x, y, glassDark); continue; }
		if (y <= 7) { set(x, y, [170, 190, 210, 255]); continue; } // air above the liquid
		// liquid, darker toward the bottom and right
		const f = 1.15 - (y - 8) * 0.07 - (x - 4) * 0.025;
		set(x, y, shade(liquid, f));
	}
	// the meniscus
	for (let x = 4; x <= 11; x++) set(x, 8, shade(liquid, 1.3));
	// glass highlight streak on the left
	set(4, 7, glassHi); set(4, 9, [255, 255, 255, 200]); set(4, 10, [255, 255, 255, 170]); set(4, 11, [255, 255, 255, 120]);
	// bubbles / accent
	set(9, 10, hex(accent)); set(10, 12, shade(accent, 0.85)); set(8, 12, shade(accent, 0.9)); set(7, 11, shade(liquid, 1.45));
	return px;
}

// the Erased effect icon (18x18): a red eye with a slash
function erasedIcon() {
	const W = 18, px = new Array(W * W).fill(null);
	const set = (x, y, c) => { if (x >= 0 && x < W && y >= 0 && y < W) px[y * W + x] = c; };
	const red = [224, 48, 48, 255], dark = [90, 10, 10, 255], white = [240, 230, 220, 255];
	for (let x = 2; x <= 15; x++) {
		const t = (x - 8.5) / 6.5;
		const hgt = Math.round(4.2 * Math.sqrt(Math.max(0, 1 - t * t)));
		for (let dy = -hgt; dy <= hgt; dy++) set(x, 9 + dy, Math.abs(dy) === hgt ? dark : white);
	}
	for (let y = 6; y <= 12; y++) for (let x = 6; x <= 11; x++) {
		const d = (x - 8.5) ** 2 + (y - 9) ** 2;
		if (d <= 8) set(x, y, d <= 2 ? [20, 0, 0, 255] : red);
	}
	set(7, 8, [255, 200, 200, 255]);
	for (let i = 1; i <= 16; i++) { set(i, 17 - i, red); set(i + 1, 17 - i, dark); }
	return px;
}

// recipes: glass bottle + diamond + amethyst shard + two themed ingredients
const THEMED = {
	hellflame: ["minecraft:blaze_powder", "minecraft:magma_cream"],
	overhaul: ["minecraft:glistering_melon_slice", "minecraft:flint"],
	engine: ["minecraft:sugar", "minecraft:piston"],
	dark_shadow: ["minecraft:ink_sac", "minecraft:phantom_membrane"],
	erasure: ["minecraft:spider_eye", "minecraft:string"],
	explosion: ["minecraft:gunpowder", "minecraft:fire_charge"],
	zero_gravity: ["minecraft:feather", "minecraft:pink_dye"],
	random: ["minecraft:redstone", "minecraft:fermented_spider_eye"],
};

for (const [id, [liquid, accent, band]] of Object.entries(QUIRKS)) {
	const name = "quirk_serum_" + id;
	fs.writeFileSync(path.join(TEX, name + ".png"), png(16, 16, vial(liquid, accent, band)));
	fs.writeFileSync(path.join(MODELS, name + ".json"), JSON.stringify({ parent: "minecraft:item/generated", textures: { layer0: "projecthero:item/" + name } }, null, 2) + "\n");
	const ingredients = ["minecraft:glass_bottle", "minecraft:diamond", "minecraft:amethyst_shard", ...THEMED[id]].map((item) => ({ item }));
	fs.writeFileSync(path.join(RECIPES, name + ".json"), JSON.stringify({
		type: "minecraft:crafting_shapeless", category: "misc", ingredients, result: { id: "projecthero:" + name, count: 1 },
	}, null, 2) + "\n");
}
fs.mkdirSync(EFFECT, { recursive: true });
fs.writeFileSync(path.join(EFFECT, "erased.png"), png(18, 18, erasedIcon()));

// a zoomed preview sheet (x8) of the eight vials + the icon, for the report
const S = 8, cols = 9, PW = cols * 18 * S, PH = 18 * S;
const sheet = new Array(PW * PH).fill([40, 40, 46, 255]);
const icons = [...Object.values(QUIRKS).map(([l, a, b]) => ({ w: 16, px: vial(l, a, b) })), { w: 18, px: erasedIcon() }];
icons.forEach((ic, i) => {
	for (let y = 0; y < ic.w; y++) for (let x = 0; x < ic.w; x++) {
		const p = ic.px[y * ic.w + x];
		if (!p || p[3] === 0) continue;
		for (let dy = 0; dy < S; dy++) for (let dx = 0; dx < S; dx++) sheet[(y * S + dy) * PW + (i * 18 + x) * S + dx] = p;
	}
});
const out = process.argv[2];
if (out) {
	fs.mkdirSync(path.dirname(out), { recursive: true });
	fs.writeFileSync(out, png(PW, PH, sheet));
}
console.log("done");
