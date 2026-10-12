// v0.16.1 polish: the sky-citadel's Citadel Light (red light strip) and Citadel Cable (a dangling cable, chain-shaped).
// Usage: node scratchpad/gen_v0161_citadel_extras.js   (idempotent)
'use strict';
const L = require('./v0161_asset_lib.js');
const { Canvas, writePng, writeJson, fbm, hash3, mix } = L;
const NS = 'projecthero';

// ---- citadel_light: a black frame round a recessed glowing red strip, horizontal
{
	const c = new Canvas(16, 16);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const n = fbm(x * 0.4, y * 0.4, 3);
			let col = mix([22, 24, 30], [52, 56, 66], n);
			if (y === 0 || x === 0) col = [70, 74, 86];
			if (y === 15 || x === 15) col = [10, 11, 14];
			c.set(x, y, col);
		}
	}
	for (let x = 1; x < 15; x++) {
		for (let y = 5; y <= 10; y++) {
			const core = y >= 7 && y <= 8;
			const hot = core && (x % 5 === 2);
			c.set(x, y, hot ? [255, 190, 170] : core ? [255, 52, 30] : y === 5 || y === 10 ? [90, 8, 6] : [200, 22, 14]);
		}
	}
	for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) {
		c.set(x, y, [120, 126, 140]);
	}
	writePng('textures/block/citadel_light.png', c);
	writeJson('models/block/citadel_light.json', { parent: 'minecraft:block/cube_all', textures: { all: `${NS}:block/citadel_light` } });
	writeJson('blockstates/citadel_light.json', { variants: { '': [{ model: `${NS}:block/citadel_light` }, { model: `${NS}:block/citadel_light`, y: 90 }] } });
	writeJson('models/item/citadel_light.json', { parent: `${NS}:block/citadel_light` });
}

// ---- citadel_cable: the vanilla chain model's layout (two crossed 3-px planes) -- a twisted black cable with red sheath marks
{
	const c = new Canvas(16, 16);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			c.set(x, y, [0, 0, 0], 0);
		}
	}
	// vanilla chain.png uses x 0..2 (front link) and 3..5 (side link) columns; we paint a continuous cable in both
	for (const ox of [0, 3]) {
		for (let y = 0; y < 16; y++) {
			const twist = (y + ox) % 4;
			for (let x = 0; x < 3; x++) {
				const edge = x === 0 ? 1.15 : x === 2 ? 0.7 : 1.0;
				let col = [28 * edge, 30 * edge, 36 * edge];
				if ((x + twist) % 4 === 0) col = [64, 68, 78];
				if (y % 6 === 2 && x === 1) col = [230, 36, 20];
				c.set(ox + x, y, col);
			}
		}
	}
	writePng('textures/block/citadel_cable.png', c);
	writeJson('models/block/citadel_cable.json', { parent: 'minecraft:block/chain', textures: { particle: `${NS}:block/citadel_cable`, all: `${NS}:block/citadel_cable` } });
	writeJson('blockstates/citadel_cable.json', { variants: {
		'axis=x': { model: `${NS}:block/citadel_cable`, x: 90, y: 90 },
		'axis=y': { model: `${NS}:block/citadel_cable` },
		'axis=z': { model: `${NS}:block/citadel_cable`, x: 90 },
	} });
	writeJson('models/item/citadel_cable.json', { parent: 'minecraft:item/generated', textures: { layer0: `${NS}:block/citadel_cable` } });
}
console.log(L.written.join('\n'));
