// node tv_contact.js <genDir> <skinsDir> <shotsDir> <outDir>
const path = require('path');
const fs = require('fs');
const [, , gen, skins, shots, out] = process.argv;
const PK = require(gen + '/pngkit.js');
const L = require(gen + '/lib_concept_models.js');
fs.mkdirSync(out, { recursive: true });
const TYPES = [['titan_form', 'Attack 15'], ['titan_armored', 'Armoured 15'], ['titan_female', 'Female 12'], ['titan_beast', 'Beast 14']];
const TH = 480, TW = 300; // tile size

function crop(file, fx0, fx1) {
	const im = PK.decode(file);
	const x0 = Math.round(im.w * fx0), x1 = Math.round(im.w * fx1);
	const w = x1 - x0, s = TH / im.h, ow = Math.round(w * s);
	const buf = Buffer.alloc(ow * TH * 4);
	for (let y = 0; y < TH; y++) for (let x = 0; x < ow; x++) {
		const sx = x0 + Math.min(w - 1, Math.floor(x / s)), sy = Math.min(im.h - 1, Math.floor(y / s));
		im.px.copy(buf, (y * ow + x) * 4, (sy * im.w + sx) * 4, (sy * im.w + sx) * 4 + 4);
		buf[(y * ow + x) * 4 + 3] = 255;
	}
	return { buf, w: ow, h: TH };
}
function skinTile(id) {
	const im = PK.decode(path.join(skins, id + '.png'));
	const S = 6, w = 64 * S, h = 64 * S, buf = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const i = (((y / S) | 0) * 64 + ((x / S) | 0)) * 4, o = (y * w + x) * 4;
		const a = im.px[i + 3] / 255, chk = (((x >> 3) + (y >> 3)) & 1) ? 70 : 95;
		for (let c = 0; c < 3; c++) buf[o + c] = im.px[i + c] * a + chk * (1 - a);
		buf[o + 3] = 255;
	}
	return { buf, w, h };
}
function sheet(file, title, rows) {
	const LAB = 26, TOP = 56, GAP = 8;
	const W = Math.max(...rows.map(r => r.tiles.reduce((s, t) => s + t.img.w + GAP, 0))) + 20;
	const H = TOP + rows.reduce((s, r) => s + LAB + Math.max(...r.tiles.map(t => t.img.h)) + GAP, 0);
	const cv = new L.Canvas(W, H, [30, 32, 38]);
	cv.text(title, 12, 14, 4, [240, 244, 250]);
	let y = TOP;
	for (const r of rows) {
		let x = 10;
		cv.text(r.label, x, y + 4, 2, [250, 220, 140]);
		for (const t of r.tiles) {
			cv.blit(t.img.buf, t.img.w, t.img.h, x, y + LAB);
			cv.text(t.label, x + 4, y + LAB + 4, 2, [235, 240, 250]);
			x += t.img.w + GAP;
		}
		y += LAB + Math.max(...r.tiles.map(t => t.img.h)) + GAP;
	}
	cv.save(file);
	console.log('wrote', file);
}
const shot = n => path.join(shots, 'tv_' + n + '.png');
// 1. each Titan next to its source skin
sheet(path.join(out, 'titans_vs_skins.png'), 'Titans vs the user skins', TYPES.map(([id, name], i) => ({
	label: name,
	tiles: [{ label: 'source skin', img: skinTile(id) }, ...['front', '3q', 'back', 'side'].map(v => ({ label: v, img: crop(shot(`type_${i}_${v}`), 0.3, 0.7) })),
		...(i === 1 ? [{ label: 'hardened', img: crop(shot('type_4_hardened'), 0.3, 0.7) }] : [])],
})));
// 2. transformation, sever, corpse
const tr = [...Array(11).keys()].map(k => ({ label: 't' + k * 6, img: crop(shot('transform_0_' + String(k).padStart(2, '0')), 0.3, 0.7) }));
sheet(path.join(out, 'transform_attack.png'), 'Attack Titan transformation (every 6 ticks of 60)', [{ label: 'skeleton - muscle - skin', tiles: tr.slice(0, 6) }, { label: '', tiles: [...tr.slice(6), { label: 'severed arm', img: crop(shot('sever_0'), 0.3, 0.7) }] }]);
const co = [...Array(10).keys()].map(k => ({ label: 'c' + k, img: crop(shot('corpse_' + String(k).padStart(2, '0')), 0.2, 0.8) }));
sheet(path.join(out, 'corpse_beast.png'), 'Beast Titan corpse: torn nape, dissolve', [{ label: 'skin - muscle - skeleton', tiles: co.slice(0, 5) }, { label: '', tiles: co.slice(5) }]);
// 3. walk + punch per type
sheet(path.join(out, 'walk_punch.png'), 'Walk + punch', TYPES.map(([id, name], i) => ({
	label: name,
	tiles: [0, 1, 2, 3].map(k => ({ label: 'walk ' + k, img: crop(shot(`move_${i}_a_walk${k}`), 0.25, 0.75) }))
		.concat([1, 2, 3].map(k => ({ label: 'punch ' + k, img: crop(shot(`move_${i}_b_punch${k}`), 0.25, 0.75) }))),
})));
