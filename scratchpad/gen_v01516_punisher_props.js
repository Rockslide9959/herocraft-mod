// v0.15.16: render-only Punisher props -- the adrenaline syringe (needle -Y, plunger +Y) and a rifle magazine, built
// from vanilla block textures (no new PNGs). Run from the repo root: node scratchpad/gen_v01516_punisher_props.js
const fs = require('fs');
const out = 'src/main/resources/assets/projecthero/models/item/';

function el(name, from, to, tex) {
	const faces = {};
	const [x0, y0, z0] = from, [x1, y1, z1] = to;
	const uv = (a, b, c, d) => [a, b, c, d].map(v => Math.max(0, Math.min(16, v)));
	faces.north = { uv: uv(16 - x1, 16 - y1, 16 - x0, 16 - y0), texture: '#' + tex };
	faces.south = { uv: uv(x0, 16 - y1, x1, 16 - y0), texture: '#' + tex };
	faces.east = { uv: uv(16 - z1, 16 - y1, 16 - z0, 16 - y0), texture: '#' + tex };
	faces.west = { uv: uv(z0, 16 - y1, z1, 16 - y0), texture: '#' + tex };
	faces.up = { uv: uv(x0, z0, x1, z1), texture: '#' + tex };
	faces.down = { uv: uv(x0, 16 - z1, x1, 16 - z0), texture: '#' + tex };
	return { name, from, to, faces };
}

const display = {
	thirdperson_righthand: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
	firstperson_righthand: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
	gui: { rotation: [30, 45, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
	ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
	fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.7, 0.7, 0.7] },
};

const syringe = {
	credit: 'Project Hero v0.15.16 -- render-only Punisher adrenaline syringe (needle -Y). Vanilla block textures.',
	textures: {
		w: 'minecraft:block/white_concrete',
		o: 'minecraft:block/orange_concrete',
		g: 'minecraft:block/gray_concrete',
		l: 'minecraft:block/light_gray_concrete',
		n: 'minecraft:block/iron_block',
		particle: 'minecraft:block/white_concrete',
	},
	elements: [
		el('barrel', [7, 5, 7], [9, 11, 9], 'w'),
		el('dose', [6.9, 5.6, 7.4], [9.1, 9.8, 8.6], 'o'),
		el('flange', [5.8, 11, 7.4], [10.2, 11.5, 8.6], 'g'),
		el('rod', [7.6, 11.5, 7.6], [8.4, 13.6, 8.4], 'l'),
		el('cap', [6.8, 13.6, 6.8], [9.2, 14.1, 9.2], 'g'),
		el('hub', [7.5, 4.2, 7.5], [8.5, 5, 8.5], 'g'),
		el('needle', [7.85, 1, 7.85], [8.15, 4.2, 8.15], 'n'),
	],
	display,
};

const magazine = {
	credit: 'Project Hero v0.15.16 -- render-only Punisher magazine (feed lips +Y). Vanilla block textures.',
	textures: {
		b: 'minecraft:block/black_concrete',
		g: 'minecraft:block/gray_concrete',
		a: 'minecraft:block/gold_block',
		particle: 'minecraft:block/black_concrete',
	},
	elements: [
		el('body', [6.6, 0.6, 6.8], [9.4, 7, 9.2], 'b'),
		el('base', [6.3, 0, 6.5], [9.7, 0.6, 9.5], 'g'),
		el('round', [7.2, 7, 7.3], [8.8, 7.6, 8.7], 'a'),
	],
	display,
};

fs.writeFileSync(out + 'adrenaline_syringe.json', JSON.stringify(syringe, null, 2) + '\n');
fs.writeFileSync(out + 'gun_magazine.json', JSON.stringify(magazine, null, 2) + '\n');
console.log('wrote adrenaline_syringe.json + gun_magazine.json');
