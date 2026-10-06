// v0.15.1: all seven Iron Man marks straight from the user's Blockbench skin models
// (`3d minecraft models/IRon Man/new models/mark{1..7}.bbmodel` -- each a plain 64x64 player skin on the standard
// base + layer rig: Head/Hat 0/0.5, Body/Arms/Legs 0/0.25 inflation, box UV). Supersedes gen_v01427_ironman_models.js.
//
// The armour must look exactly like the uploaded models, nothing added. v0.14.27 also put thin "detail" cubes on every
// bone the Java code drives (chest_armor, arc_reactor, waist, back_panel, shoulders, gauntlets, thigh plates, knees)
// UV-mapped onto skin regions, plus boot shells a little bigger than the legs -- on the Mark III / 4 those showed up as
// stray gold blocks on the back, shoulders and hips. Now:
//   * textures/armor/<id>.png  = the skin embedded in the bbmodel, pixel for pixel (Mark V: + its blade swatch in the
//     skin's unused top-left 8x8 corner, which the blade bones and the first-person blade sample)
//   * textures/armor/<id>_glowmask.png = only the very bright cyan / white skin pixels (eyes, reactor) + palm repulsors
//   * geo/<id>.geo.json = the skin rig only: base + layer cube per body part under the GeckoLib armour bones. The legs
//     are split at the boot line (thigh bone = top 8 texel rows, boot bone = bottom 4) with explicit, non-overlapping
//     coordinates, so leggings + boots together are exactly the model's legs. The helmet's front faces live on the
//     faceplate bone (H lifts them). Every other named bone the Java assembly / reveal / suitcase code looks up stays
//     in the geo, with its pivot, but carries no cubes. Mark V keeps its gauntlet blade bones (shown only when extended).
//   * item icons off the skin's front faces
// Usage: node scratchpad/gen_v0151_ironman_models.js
const fs = require('fs');
const path = require('path');
const pk = require('./pngkit.js');

const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/IRon Man/new models/';
const ASSETS = path.join(__dirname, '../src/main/resources/assets/projecthero');
const MARKS = { mark1: 'mark_1', mark2: 'mark_2', mark3: 'mark_iii', mark4: 'mark_4', mark5: 'mark_v', mark6: 'mark_6', mark7: 'mark_vii' };

// base inflation sits just off the wearer's skin; layer = base + the bbmodel's own layer inflation
const BASE = 0.25;
const LAYER = { head: BASE + 0.5, body: BASE + 0.25, arm: BASE + 0.25, leg: BASE + 0.25 };
/** Texel rows of the leg that belong to the leggings (the rest, 12 - this, are the boots). */
const THIGH_ROWS = 8;

// per-face box UV for a w x h x d box at (u, v), optionally only rows [rowFrom, rowFrom+rows) of the side faces
function boxFaces(u, v, w, h, d, rowFrom = 0, rows = h) {
	const sv = v + d + rowFrom;
	return {
		east: { uv: [u, sv], uv_size: [d, rows] }, // v0.15.3: Blockbench box UV is east | north | west | south (the 0.14.28 west/east swap put the face-side gold wraps of the Mark 3 / 4 helmets on the back edges)
		north: { uv: [u + d, sv], uv_size: [w, rows] },
		west: { uv: [u + d + w, sv], uv_size: [d, rows] },
		south: { uv: [u + 2 * d + w, sv], uv_size: [w, rows] },
		up: { uv: [u + d, v], uv_size: [w, d] },
		down: { uv: [u + d + w, v], uv_size: [w, d] },
	};
}

const r3 = n => Math.round(n * 10000) / 10000;

/**
 * One layer of a leg, rows [rowFrom, rowFrom + rows) of 12: the inflated 4x12x4 box cut at the same fraction of its
 * height, as an explicit un-inflated cube (two inflated halves would overlap by 2 x inflate and z-fight).
 */
function legPart(x, inflate, u, v, rowFrom, rows) {
	const H = 12 + 2 * inflate;
	const yTop = 12 + inflate - H * rowFrom / 12;
	const yBot = yTop - H * rows / 12;
	const uv = boxFaces(u, v, 4, 12, 4, rowFrom, rows);
	if (rowFrom > 0) delete uv.up;         // the cut: no cap where the thigh meets the boot
	if (rowFrom + rows < 12) delete uv.down;
	return { origin: [r3(x - inflate), r3(yBot), r3(-2 - inflate)], size: [r3(4 + 2 * inflate), r3(yTop - yBot), r3(4 + 2 * inflate)], uv };
}

function build(id, extraBones) {
	const tpl = JSON.parse(TEMPLATE);
	const geo = tpl['minecraft:geometry'][0];
	geo.description.identifier = 'geometry.' + id;
	const rig = {
		helmet: [[-4, 24, -4], [8, 8, 8], [0, 0], [32, 0], LAYER.head],
		chest: [[-4, 12, -2], [8, 12, 4], [16, 16], [16, 32], LAYER.body],
		right_upper_arm: [[-8, 12, -2], [4, 12, 4], [40, 16], [40, 32], LAYER.arm],
		left_upper_arm: [[4, 12, -2], [4, 12, 4], [32, 48], [48, 48], LAYER.arm],
	};
	// leg: x, base uv, layer uv
	const legs = {
		right_thigh: [-4, [0, 16], [0, 32], 0, THIGH_ROWS], left_thigh: [0, [16, 48], [0, 48], 0, THIGH_ROWS],
		right_boot: [-4, [0, 16], [0, 32], THIGH_ROWS, 12 - THIGH_ROWS], left_boot: [0, [16, 48], [0, 48], THIGH_ROWS, 12 - THIGH_ROWS],
	};
	for (const bone of geo.bones) {
		const r = rig[bone.name];
		if (r) {
			bone.cubes = [
				{ origin: r[0], size: r[1], inflate: BASE, uv: r[2] },
				{ origin: r[0], size: r[1], inflate: r[4], uv: r[3] },
			];
			if (bone.name === 'helmet') {
				// the helmet's front faces live on the faceplate bone (no z-fighting with it)
				for (const c of bone.cubes) { c.uv = boxFaces(c.uv[0], c.uv[1], 8, 8, 8); delete c.uv.north; }
			}
			continue;
		}
		const l = legs[bone.name];
		if (l) {
			bone.cubes = [legPart(l[0], BASE, l[1][0], l[1][1], l[3], l[4]), legPart(l[0], LAYER.leg, l[2][0], l[2][1], l[3], l[4])];
			continue;
		}
		// v0.14.28: the faceplate is exactly the front of both helmet layers (base face + hat-layer face); the helmet
		// cubes have no north faces, so these two faces are the whole visible front and H swings them up
		if (bone.name === 'faceplate') {
			const face = (u, v) => ({ north: { uv: [u, v], uv_size: [8, 8] }, south: { uv: [u, v], uv_size: [8, 8] } });
			bone.cubes = [
				{ origin: [-4, 24, -4], size: [8, 8, 0], inflate: BASE, uv: face(8, 8) },
				{ origin: [-4, 24, -4], size: [8, 8, 0], inflate: LAYER.head, uv: face(40, 8) },
			];
			continue;
		}
		// every other named bone (helmet_brow, chest_armor, arc_reactor, waist, back_panel, shoulders, gauntlets,
		// thigh plates, knees) stays for the code that looks it up, but adds nothing to the user's model
		bone.cubes = [];
	}
	// extra bones (the Mark V blades) go right after their arm's gauntlet bone
	for (const eb of extraBones) {
		const after = eb.parent === 'armorRightArm' ? 'right_gauntlet' : 'left_gauntlet';
		const i = geo.bones.findIndex(b => b.name === after);
		geo.bones.splice(i + 1, 0, eb);
	}
	return tpl;
}

function glowmask(img) {
	const px = Buffer.alloc(64 * 64 * 4);
	for (let i = 0; i < 64 * 64; i++) {
		const r = img.px[i * 4], g = img.px[i * 4 + 1], b = img.px[i * 4 + 2], a = img.px[i * 4 + 3];
		const bright = a > 0 && g >= 228 && b >= 228 && (r >= 228 || b - r >= 25);
		if (bright) { px[i * 4] = r; px[i * 4 + 1] = g; px[i * 4 + 2] = b; px[i * 4 + 3] = 255; }
	}
	return px;
}

// the pre-v0.14.27 mark_iii geometry is the bone template (names, parents, pivots)
const TEMPLATE = fs.readFileSync(path.join(__dirname, 'gen_v01427_template_mark_iii.geo.json'), 'utf8');

// Mark V blades: the bones + the 8x8 blade swatch (texture + glowmask) from the pre-v0.15.1 mark_v assets, saved once so
// re-running never builds on its own output
const BLADES = path.join(__dirname, 'gen_v0151_mark_v_blades.json');
if (!fs.existsSync(BLADES)) {
	const g = JSON.parse(fs.readFileSync(path.join(ASSETS, 'geo/mark_v.geo.json'), 'utf8'))['minecraft:geometry'][0];
	const corner = img => { const o = []; for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) { const i = (y * 64 + x) * 4; o.push([...img.px.slice(i, i + 4)]); } return o; };
	fs.writeFileSync(BLADES, JSON.stringify({
		bones: g.bones.filter(b => /_blade$/.test(b.name)),
		swatch: corner(pk.decode(path.join(ASSETS, 'textures/armor/mark_v.png'))),
		swatchGlow: corner(pk.decode(path.join(ASSETS, 'textures/armor/mark_v_glowmask.png'))),
	}));
}
const blades = JSON.parse(fs.readFileSync(BLADES, 'utf8'));
if (blades.bones.length !== 2) throw new Error('expected 2 Mark V blade bones, got ' + blades.bones.length);
const paintCorner = (px, sw) => { for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) { const i = (y * 64 + x) * 4; for (let c = 0; c < 4; c++) px[i + c] = sw[y * 8 + x][c]; } };

for (const [file, id] of Object.entries(MARKS)) {
	const m = JSON.parse(fs.readFileSync(SRC + file + '.bbmodel', 'utf8'));
	if (m.textures.length !== 1 || m.elements.length !== 12) throw new Error(file + ': not the plain skin rig');
	const buf = Buffer.from(m.textures[0].source.split(',')[1], 'base64');
	const texPath = path.join(ASSETS, 'textures/armor', id + '.png');
	const tmp = texPath + '.tmp';
	fs.writeFileSync(tmp, buf);
	const img = pk.decode(tmp);
	fs.unlinkSync(tmp);
	if (img.w !== 64 || img.h !== 64) throw new Error(file + ': expected a 64x64 skin, got ' + img.w + 'x' + img.h);
	const mk5 = id === 'mark_v';
	const out = Buffer.from(img.px);
	if (mk5) {
		for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) if (img.px[(y * 64 + x) * 4 + 3]) throw new Error('mark5 skin uses the blade corner');
		paintCorner(out, blades.swatch);
	}
	fs.writeFileSync(texPath, pk.encode(64, 64, out));
	const gm = glowmask(img);
	if (id !== 'mark_1') {
		// palm repulsors: the centre 2x2 of each arm's bottom face (right arm 48..52,16..20; left arm 40..44,48..52)
		// glow cyan on every repulsor-armed mark, whatever the skin paints there
		for (const [px0, py0] of [[49, 17], [41, 49]]) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
			const i = ((py0 + dy) * 64 + px0 + dx) * 4;
			gm[i] = 200; gm[i + 1] = 255; gm[i + 2] = 255; gm[i + 3] = 255;
		}
	}
	if (mk5) paintCorner(gm, blades.swatchGlow);
	fs.writeFileSync(path.join(ASSETS, 'textures/armor', id + '_glowmask.png'), pk.encode(64, 64, gm));
	const geo = build(id, mk5 ? JSON.parse(JSON.stringify(blades.bones)) : []);
	fs.writeFileSync(path.join(ASSETS, 'geo', id + '.geo.json'), JSON.stringify(geo, null, '\t') + '\n');
	let glowN = 0; for (let i = 3; i < gm.length; i += 4) if (gm[i]) glowN++;
	icons(id, img);
	console.log(id, 'texture + geo + icons written,', glowN, 'glow pixels');
}

// 16x16 item icons straight off the skin's front faces (base with the layer composited on top), scaled 2x where it fits
function icons(id, img) {
	const at = (x, y) => {
		const i = (y * 64 + x) * 4;
		return [img.px[i], img.px[i + 1], img.px[i + 2], img.px[i + 3]];
	};
	// front face pixel of a part: layer pixel if opaque, else base pixel
	const front = (bu, bv, lu, lv) => (x, y) => { const l = at(lu + x, lv + y); return l[3] > 0 ? l : at(bu + x, bv + y); };
	const head = front(8, 8, 40, 8), body = front(20, 20, 20, 36), rArm = front(44, 20, 44, 36), lArm = front(36, 52, 52, 52);
	const rLeg = front(4, 20, 4, 36), lLeg = front(20, 52, 4, 52);
	const out = (name, w, h, scale, src) => {
		const px = Buffer.alloc(16 * 16 * 4);
		const ox = Math.floor((16 - w * scale) / 2), oy = Math.floor((16 - h * scale) / 2);
		for (let y = 0; y < h * scale; y++) for (let x = 0; x < w * scale; x++) {
			const c = src(Math.floor(x / scale), Math.floor(y / scale));
			if (!c || c[3] === 0) continue;
			const i = ((oy + y) * 16 + ox + x) * 4;
			px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
		}
		fs.writeFileSync(path.join(ASSETS, 'textures/item', 'iron_man_' + id + '_' + name + '.png'), pk.encode(16, 16, px));
	};
	out('helmet', 8, 8, 2, head);
	// chestplate: right arm | torso | left arm (as seen from the front), 16 x 12
	out('chestplate', 16, 12, 1, (x, y) => x < 4 ? rArm(x, y) : x < 12 ? body(x - 4, y) : lArm(x - 12, y));
	// leggings: the top 8 rows of both legs, 2x
	out('leggings', 8, 8, 2, (x, y) => x < 4 ? rLeg(x, y) : lLeg(x - 4, y));
	// boots: the bottom 4 rows of both legs, 2x
	out('boots', 8, 4, 2, (x, y) => x < 4 ? rLeg(x, y + 8) : lLeg(x - 4, y + 8));
}
