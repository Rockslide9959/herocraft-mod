// v0.16.7 (user): an opened Iron Man faceplate looked like "a bunch of flat PNGs stacked on top of one another" -- every
// suit's faceplate bone was two zero-depth planes (the base face + the outer detail layer). This turns each into ONE
// solid plate (0.6 px deep; its edges use the face's own edge pixels, its inside the helmet's underside) and bakes the
// outer front detail into the face texture so the closed look is unchanged. The Mk IV Hydro is the exception: its outer
// "face" is the glass dome's front, so that pane moves to the helmet (the dome stays shut, the solid faceplate inside it
// lifts). Repeatable: run after scratchpad/install_ironman_era1_art.js (which rewrites the Era I geos).
// Usage: node scratchpad/solid_faceplates_v0167.js
'use strict';
const fs = require('fs');
const path = require('path');
const pk = require('./pngkit.js');
const L = require('./lib_concept_models.js');
const RES = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');

const DEPTH = 0.6;
const GEO_TEXTURES = {
	mark_1: ['mark_1', 'mark_1_v2'], mark_2: ['mark_2'], mark_iii: ['mark_iii'], mark_4: [], mark_v: ['mark_v'],
	mark_6: ['mark_6'], mark_vii: ['mark_vii'], mark_8: ['mark_8'],
};

function solidPlate(inflate) {
	return {
		origin: [-4, 24, -4], size: [8, 8, DEPTH], inflate,
		uv: {
			north: { uv: [8, 8], uv_size: [8, 8] },
			south: { uv: [16, 0], uv_size: [8, 8] }, // the inside: the helmet's underside
			east: { uv: [15, 8], uv_size: [1, 8] },
			west: { uv: [8, 8], uv_size: [1, 8] },
			up: { uv: [8, 8], uv_size: [8, 1] },
			down: { uv: [8, 15], uv_size: [8, 1] },
		},
	};
}

for (const [geoId, textures] of Object.entries(GEO_TEXTURES)) {
	const gf = path.join(RES, 'geo', geoId + '.geo.json');
	const g = JSON.parse(fs.readFileSync(gf, 'utf8'));
	const bones = g['minecraft:geometry'][0].bones;
	const fp = bones.find(b => b.name === 'faceplate');
	const cubes = fp.cubes || [];
	if (cubes.length === 1 && cubes[0].size[2] > 0) {
		console.log(geoId + ': already solid');
		continue;
	}
	const flat = cubes.filter(c => c.size[2] === 0).sort((a, b) => (a.inflate || 0) - (b.inflate || 0));
	if (flat.length !== 2) throw new Error(geoId + ': expected two flat faceplate planes');
	const [base, outer] = flat;
	if (geoId === 'mark_4') {
		// the Hydro: the glass dome's front stays on the helmet; the solid plate is the inner face
		const helmet = bones.find(b => b.name === 'helmet');
		helmet.cubes.push(outer);
		fp.cubes = [solidPlate(base.inflate || 0.1)];
	} else {
		fp.cubes = [solidPlate(outer.inflate || 0.32)];
	}
	fs.writeFileSync(gf, JSON.stringify(g, null, '\t') + '\n');
	// bake the outer front (hat front, 40,8) over the base front (head front, 8,8) -- colour and glowmask
	for (const t of textures) {
		for (const suffix of ['', '_glowmask']) {
			const tf = path.join(RES, 'textures', 'armor', t + suffix + '.png');
			if (!fs.existsSync(tf)) continue;
			const im = pk.decode(tf);
			const W = im.w, S = W / 64;
			for (let y = 0; y < 8 * S; y++) for (let x = 0; x < 8 * S; x++) {
				const src = ((8 * S + y) * W + 40 * S + x) * 4, dst = ((8 * S + y) * W + 8 * S + x) * 4;
				const a = im.px[src + 3] / 255;
				if (a <= 0) continue;
				for (let k = 0; k < 3; k++) im.px[dst + k] = Math.round(im.px[src + k] * a + im.px[dst + k] * (1 - a));
				im.px[dst + 3] = Math.max(im.px[dst + 3], im.px[src + 3]);
			}
			fs.writeFileSync(tf, L.encodePng(W, im.h, im.px));
		}
	}
	console.log(geoId + ': solid faceplate (' + (textures.join(', ') || 'Hydro dome kept on the helmet') + ')');
}
