// v0.17.2: Galactus raid v2 item icons -- the Cosmic Cell (a glowing violet orb in a silver cage) and the Cosmic Shard
// (a jagged violet crystal splinter). node scratchpad/gen_galactus_raid_v2_items.js, from the repo root.
const fs = require("fs");
const { encodePng } = require("./lib_png.js");
const out = "src/main/resources/assets/projecthero/textures/item/";
function img(draw) {
	const px = Buffer.alloc(16 * 16 * 4);
	const set = (x, y, r, g, b, a = 255) => { if (x < 0 || y < 0 || x > 15 || y > 15) return; const i = (y * 16 + x) * 4; px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a; };
	draw(set);
	return px;
}
const cell = img(set => {
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x - 7.5, dy = y - 7.5, d = Math.sqrt(dx * dx + dy * dy);
		if (d < 5.2) {
			const k = 1 - d / 5.2;
			set(x, y, Math.round(150 + 105 * k), Math.round(70 + 150 * k * k), 255);
		}
	}
	// the silver cage: two rings and top/bottom caps
	for (let x = 4; x <= 11; x++) { set(x, 2, 200, 210, 225); set(x, 13, 200, 210, 225); }
	for (let y = 3; y <= 12; y++) { if (y % 3 !== 0) continue; set(2, y, 170, 180, 200); set(13, y, 170, 180, 200); }
	for (let y = 3; y <= 12; y++) { set(3, y, 150, 160, 185); set(12, y, 150, 160, 185); }
	set(7, 1, 230, 235, 245); set(8, 1, 230, 235, 245); set(7, 14, 230, 235, 245); set(8, 14, 230, 235, 245);
	set(6, 5, 255, 255, 255); set(7, 5, 255, 255, 255); set(6, 6, 255, 255, 255);
});
const shard = img(set => {
	const pts = [[8, 1], [9, 2], [9, 3], [10, 4], [10, 5], [10, 6], [11, 7], [10, 8], [10, 9], [9, 10], [9, 11], [8, 12], [7, 13], [6, 14]];
	for (const [x, y] of pts) {
		set(x, y, 120, 50, 200);
		set(x - 1, y, 180, 110, 255);
		set(x - 2, y, y > 4 && y < 12 ? 210 : 140, y > 4 && y < 12 ? 160 : 70, 255);
		if (y > 5 && y < 10) set(x - 3, y, 90, 30, 160);
	}
	set(7, 3, 255, 240, 255); set(7, 4, 240, 220, 255); set(8, 6, 255, 255, 255);
});
fs.writeFileSync(out + "cosmic_cell.png", encodePng(16, 16, cell));
fs.writeFileSync(out + "cosmic_shard.png", encodePng(16, 16, shard));
console.log("ok");
