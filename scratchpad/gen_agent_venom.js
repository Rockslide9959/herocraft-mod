// v0.13.11: Agent Venom suit assets from the user-supplied Blockbench model
// (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/agent venom/agentvenom.bbmodel).
//
// The model is a plain 64x64 player skin on a skin-shaped rig (Head/Body/arms/legs, each a base cube plus
// a second-layer cube) -- exactly the layout of the Normal Symbiote Host's converted rig, so the geometry
// is symbiote_host.geo.json under a new identifier (same bone names, same UVs, same z-fight inflates;
// Blockbench's baked "natural pose" limb rotations are dropped, as for every converted set). The texture
// is the model's embedded PNG, byte for byte. The four item icons are cut from the skin itself.
//
// Run from the repo root:  node scratchpad/gen_agent_venom.js
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit');

const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/agent venom/agentvenom.bbmodel';
const RES = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');

const model = JSON.parse(fs.readFileSync(SRC, 'utf8'));

// ---- geometry: the Symbiote host's skin rig, renamed ----
const geo = JSON.parse(fs.readFileSync(path.join(RES, 'geo/symbiote_host.geo.json'), 'utf8'));
geo['minecraft:geometry'][0].description.identifier = 'geometry.agent_venom';
fs.writeFileSync(path.join(RES, 'geo/agent_venom.geo.json'), JSON.stringify(geo, null, '\t'));

// ---- texture: the embedded skin ----
const texPath = path.join(RES, 'textures/armor/agent_venom.png');
fs.writeFileSync(texPath, Buffer.from(model.textures[0].source.split(',')[1], 'base64'));
const skin = decode(texPath);

// ---- icons: cut from the skin (front faces), nearest-neighbour ----
function icon(parts) {
	const px = Buffer.alloc(16 * 16 * 4);
	for (const p of parts) {
		for (let y = 0; y < p.h * p.k; y++) {
			for (let x = 0; x < p.w * p.k; x++) {
				const sx = p.u + Math.floor(x / p.k), sy = p.v + Math.floor(y / p.k);
				const s = (sy * skin.w + sx) * 4, dx = p.x + x, dy = p.y + y;
				if (dx < 0 || dy < 0 || dx >= 16 || dy >= 16 || skin.px[s + 3] === 0) continue;
				skin.px.copy(px, (dy * 16 + dx) * 4, s, s + 4);
			}
		}
	}
	return encode(16, 16, px);
}
const ITEM = path.join(RES, 'textures/item');
// helmet: the face (hat layer on top), 2x
fs.writeFileSync(path.join(ITEM, 'agent_venom_helmet.png'), icon([
	{ u: 8, v: 8, w: 8, h: 8, k: 2, x: 0, y: 0 }, { u: 40, v: 8, w: 8, h: 8, k: 2, x: 0, y: 0 }]));
// chestplate: torso front with both arms, 1x, centred
fs.writeFileSync(path.join(ITEM, 'agent_venom_chestplate.png'), icon([
	{ u: 20, v: 20, w: 8, h: 12, k: 1, x: 4, y: 2 }, { u: 20, v: 36, w: 8, h: 12, k: 1, x: 4, y: 2 },
	{ u: 44, v: 20, w: 4, h: 12, k: 1, x: 0, y: 2 }, { u: 44, v: 36, w: 4, h: 12, k: 1, x: 0, y: 2 },
	{ u: 36, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }, { u: 52, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }]));
// leggings: both legs' upper two thirds, 1x
fs.writeFileSync(path.join(ITEM, 'agent_venom_leggings.png'), icon([
	{ u: 4, v: 20, w: 4, h: 12, k: 1, x: 4, y: 2 }, { u: 4, v: 36, w: 4, h: 12, k: 1, x: 4, y: 2 },
	{ u: 20, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }, { u: 4, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }]));
// boots: the bottom 4 rows of both legs, 2x
fs.writeFileSync(path.join(ITEM, 'agent_venom_boots.png'), icon([
	{ u: 4, v: 28, w: 4, h: 4, k: 2, x: 0, y: 4 }, { u: 4, v: 44, w: 4, h: 4, k: 2, x: 0, y: 4 },
	{ u: 20, v: 60, w: 4, h: 4, k: 2, x: 8, y: 4 }, { u: 4, v: 60, w: 4, h: 4, k: 2, x: 8, y: 4 }]));

for (const piece of ['helmet', 'chestplate', 'leggings', 'boots']) {
	fs.writeFileSync(path.join(RES, `models/item/agent_venom_${piece}.json`),
		JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/agent_venom_${piece}` } }));
}
console.log('wrote agent_venom geo, texture, 4 icons, 4 item models');
