// SUPERSEDED in v0.15.1 by gen_v0151_ironman_models.js -- do not re-run (its detail cubes are the stray gold blocks the
// user asked to remove).
// v0.14.27: rebuild the Mark 1 / Mark 2 / Mark III / Mark 4 Iron Man armour from the user's new Blockbench skin models
// (`3d minecraft models/IRon Man/new models/mark{1,2,3,4}.bbmodel` -- each a plain 64x64 player skin on the standard
// base + layer rig). For each mark:
//   * textures/armor/<id>.png  = the skin embedded in the bbmodel (textures[0].source)
//   * textures/armor/<id>_glowmask.png = only the very bright cyan / white pixels of that skin (eyes, reactor, soles)
//   * geo/<id>.geo.json = the skin rig (base cube + inflated layer cube per body part) under the GeckoLib armour bones,
//     real boot cubes (bottom 4 px of each leg, base + shell, per-face UV), and every named detail bone the Java
//     assembly / faceplate code drives (helmet_brow, faceplate, chest_armor, arc_reactor, waist, back_panel, shoulders,
//     gauntlets, thigh plates, knees) as thin cubes UV-mapped onto the matching skin region. A detail face samples the
//     layer region when that region is mostly opaque in the new skin, otherwise the base region under it.
// Usage: node scratchpad/gen_v01427_ironman_models.js
const fs = require('fs');
const path = require('path');
const pk = require('./pngkit.js');

const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/IRon Man/new models/';
const ASSETS = path.join(__dirname, '../src/main/resources/assets/projecthero');
const MARKS = { mark1: 'mark_1', mark2: 'mark_2', mark3: 'mark_iii', mark4: 'mark_4' };

// base inflation sits just off the wearer's skin; layer = base + the bbmodel's own layer inflation
const BASE = 0.25;
const LAYER = { head: BASE + 0.5, body: BASE + 0.25, arm: BASE + 0.25, leg: BASE + 0.25 };
const BOOT = { base: 0.35, shell: 0.62 };

// layer region -> base region offset, per skin part (standard 64x64 layout)
function layerToBase(u, v) {
	if (v < 16 && u >= 32) return [u - 32, v];          // hat -> head
	if (v >= 32 && v < 48 && u < 16) return [u, v - 16];  // right leg layer -> right leg
	if (v >= 32 && v < 48 && u < 40) return [u, v - 16];  // body layer -> body
	if (v >= 32 && v < 48) return [u, v - 16];            // right arm layer -> right arm
	if (v >= 48 && u < 16) return [u + 16, v];            // left leg layer -> left leg
	if (v >= 48 && u >= 48) return [u - 16, v];           // left arm layer -> left arm
	return null;                                          // already a base region
}

function opaqueShare(img, u, v, w, h) {
	let n = 0, o = 0;
	for (let y = Math.floor(v); y < Math.ceil(v + h); y++) for (let x = Math.floor(u); x < Math.ceil(u + w); x++) {
		if (x < 0 || y < 0 || x >= 64 || y >= 64) continue;
		n++; if (img.px[(y * 64 + x) * 4 + 3] > 0) o++;
	}
	return n ? o / n : 0;
}

// per-face box UV for a w x h x d box at (u, v), optionally only rows [rowFrom, rowFrom+rows) of the side faces
function boxFaces(u, v, w, h, d, rowFrom = 0, rows = h) {
	const sv = v + d + rowFrom;
	return {
		west: { uv: [u, sv], uv_size: [d, rows] }, // v0.14.28: Bedrock box layout is west | north | east | south
		north: { uv: [u + d, sv], uv_size: [w, rows] },
		east: { uv: [u + d + w, sv], uv_size: [d, rows] },
		south: { uv: [u + 2 * d + w, sv], uv_size: [w, rows] },
		up: { uv: [u + d, v], uv_size: [w, d] },
		down: { uv: [u + d + w, v], uv_size: [w, d] },
	};
}

const r2 = n => Math.round(n * 1000) / 1000;

function build(id, img) {
	const tpl = JSON.parse(TEMPLATE);
	const geo = tpl['minecraft:geometry'][0];
	geo.description.identifier = 'geometry.' + id;
	const rig = {
		helmet: [[-4, 24, -4], [8, 8, 8], [0, 0], [32, 0], LAYER.head],
		chest: [[-4, 12, -2], [8, 12, 4], [16, 16], [16, 32], LAYER.body],
		right_upper_arm: [[-8, 12, -2], [4, 12, 4], [40, 16], [40, 32], LAYER.arm],
		left_upper_arm: [[4, 12, -2], [4, 12, 4], [32, 48], [48, 48], LAYER.arm],
		right_thigh: [[-4, 0, -2], [4, 12, 4], [0, 16], [0, 32], LAYER.leg],
		left_thigh: [[0, 0, -2], [4, 12, 4], [16, 48], [0, 48], LAYER.leg],
	};
	for (const bone of geo.bones) {
		const r = rig[bone.name];
		if (r) {
			bone.cubes = [
				{ origin: r[0], size: r[1], inflate: BASE, uv: r[2] },
				{ origin: r[0], size: r[1], inflate: r[4], uv: r[3] },
			];
			if (bone.name === "helmet") {
				// v0.14.28: the helmet's front faces live on the faceplate bone (no z-fighting with it)
				for (const c of bone.cubes) { c.uv = boxFaces(c.uv[0], c.uv[1], 8, 8, 8); delete c.uv.north; }
			}
			continue;
		}
		if (bone.name === 'right_boot' || bone.name === 'left_boot') {
			const right = bone.name === 'right_boot';
			const x = right ? -4 : 0;
			const [bu, bv] = right ? [0, 16] : [16, 48];
			const [lu, lv] = right ? [0, 32] : [0, 48];
			bone.cubes = [
				{ origin: [x, 0, -2], size: [4, 4, 4], inflate: BOOT.base, uv: boxFaces(bu, bv, 4, 12, 4, 8, 4) },
				{ origin: [x, 0, -2], size: [4, 4, 4], inflate: BOOT.shell, uv: boxFaces(lu, lv, 4, 12, 4, 8, 4) },
			];
			// boot faces: up shows the leg's top face -- hidden inside the leg anyway; drop it from the shell
			delete bone.cubes[1].uv.up;
			continue;
		}
		// v0.14.28: the faceplate is exactly the front of both helmet layers (base face + hat-layer face) -- the old
		// thin plate covered the hat layer and squashed the brow, so the helmet's second layer never showed on the face.
		// The helmet renderer skips the helmet cubes' north faces, so these two faces are the whole visible front.
		if (bone.name === "faceplate") {
			const face = (u, v) => ({ north: { uv: [u, v], uv_size: [8, 8] }, south: { uv: [u, v], uv_size: [8, 8] } });
			bone.cubes = [
				{ origin: [-4, 24, -4], size: [8, 8, 0], inflate: BASE, uv: face(8, 8) },
				{ origin: [-4, 24, -4], size: [8, 8, 0], inflate: LAYER.head, uv: face(40, 8) },
			];
			continue;
		}
		if (bone.name === "helmet_brow") { bone.cubes = []; continue; } // v0.14.28: part of the faceplate now
		// detail plates: keep the template geometry, re-pick each face's region for this skin
		for (const c of bone.cubes || []) {
			if (Array.isArray(c.uv)) continue;
			for (const face of Object.keys(c.uv)) {
				const f = c.uv[face];
				const [u, v] = f.uv, [w, h] = f.uv_size;
				const base = layerToBase(u, v);
				if (base && opaqueShare(img, u, v, w, h) < 0.5) f.uv = base;
			}
		}
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

// the pre-v0.14.27 mark_iii geometry is the bone template; keep a copy so re-running never builds on its own output
const TPL_COPY = path.join(__dirname, 'gen_v01427_template_mark_iii.geo.json');
if (!fs.existsSync(TPL_COPY)) fs.copyFileSync(path.join(ASSETS, 'geo/mark_iii.geo.json'), TPL_COPY);
const TEMPLATE = fs.readFileSync(TPL_COPY, 'utf8');

for (const [file, id] of Object.entries(MARKS)) {
	const m = JSON.parse(fs.readFileSync(SRC + file + '.bbmodel', 'utf8'));
	const buf = Buffer.from(m.textures[0].source.split(',')[1], 'base64');
	const texPath = path.join(ASSETS, 'textures/armor', id + '.png');
	const tmp = texPath + '.tmp';
	fs.writeFileSync(tmp, buf);
	const img = pk.decode(tmp);
	fs.unlinkSync(tmp);
	if (img.w !== 64 || img.h !== 64) throw new Error(file + ': expected a 64x64 skin, got ' + img.w + 'x' + img.h);
	fs.writeFileSync(texPath, pk.encode(64, 64, img.px));
	const gm = glowmask(img);
	if (id !== 'mark_1') {
		// palm repulsors: the centre 2x2 of each arm's bottom face (right arm 48..52,16..20; left arm 40..44,48..52)
		// glow cyan on every repulsor-armed mark, whatever the skin paints there
		for (const [px0, py0] of [[49, 17], [41, 49]]) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
			const i = ((py0 + dy) * 64 + px0 + dx) * 4;
			gm[i] = 200; gm[i + 1] = 255; gm[i + 2] = 255; gm[i + 3] = 255;
		}
	}
	fs.writeFileSync(path.join(ASSETS, 'textures/armor', id + '_glowmask.png'), pk.encode(64, 64, gm));
	const geo = build(id, img);
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
