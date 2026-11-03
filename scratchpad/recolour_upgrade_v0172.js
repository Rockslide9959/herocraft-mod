// v0.17.2 (user: "make upgrades liquid form colours match the green thats on his skin please, most of his textures look this way so also
// try to fix that please"): remaps Upgrade's effect textures (puddle / extras, techno coat + its glow) from the old mint/teal greens and
// teal-tinted blacks onto the exact palette of his skin (upgrade.png), by luminance so highlights stay highlights.
// node scratchpad/recolour_upgrade_v0172.js   (run from the repo root; reads the ORIGINAL textures from git HEAD~ if --from-git, else in place)
const fs = require("fs"), path = require("path");
const { decodePng, encodePng } = require("./lib_png.js");
const T = "src/main/resources/assets/projecthero/textures/entity/omnitrix";

// sampled from upgrade.png -- his circuit lines (dark shade, main, light, pale) and his black / gunmetal / silver plating
const GREENS = [[0, 0, 0], [88, 161, 39], [114, 209, 46], [144, 226, 85], [188, 237, 155], [240, 251, 230]];
const DARKS = [[0, 0, 0], [14, 15, 16], [35, 39, 43], [51, 57, 63], [137, 146, 155], [175, 182, 189], [198, 202, 207], [224, 228, 232], [255, 255, 255]];
const lum = c => 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];

function ramp(stops, y) {
	const ys = stops.map(lum);
	if (y <= ys[0]) return stops[0];
	for (let i = 1; i < stops.length; i++) {
		if (y <= ys[i]) { const t = (y - ys[i - 1]) / (ys[i] - ys[i - 1]); return stops[i].map((v, k) => stops[i - 1][k] + (v - stops[i - 1][k]) * t); }
	}
	return stops[stops.length - 1];
}

function remap(r, g, b) {
	const y = lum([r, g, b]);
	// how "green" the source pixel is: the old greens are g well above r and b; the teal-tinted blacks are not
	const t = Math.max(0, Math.min(1, (g - Math.max(r, b) - 4) / 18));
	const gc = ramp(GREENS, y), dc = ramp(DARKS, y);
	return gc.map((v, k) => Math.round(Math.max(0, Math.min(255, dc[k] + (v - dc[k]) * t))));
}

for (const name of ["upgrade_extras.png", "upgrade_techno.png", "upgrade_techno_glow.png"]) {
	const f = path.join(T, name);
	const img = decodePng(fs.readFileSync(f));
	const out = Buffer.from(img.px);
	for (let i = 0; i < img.w * img.h; i++) {
		const c = remap(out[i * 4], out[i * 4 + 1], out[i * 4 + 2]);
		out[i * 4] = c[0]; out[i * 4 + 1] = c[1]; out[i * 4 + 2] = c[2];
	}
	fs.writeFileSync(f, encodePng(img.w, img.h, out));
	console.log("recoloured", name);
}
