// v0.16.6: the Green Lantern Power Ring and the Flash Ring as real 3D item models.
//   GL:    a chunky octagonal emerald band, a raised square bezel with a bright rim, a near-black face and the Lantern
//          emblem (a circle between two bars) raised on it -- the emblem's faces carry tintindex 15, which
//          client/render/RingItemModels turns full-bright (emissive) through the Fabric Renderer API.
//   Flash: a slimmer octagonal gold band, a gold collet, a stepped red dome and a gold lightning bolt raised on top.
// Textures are 32x32 swatch sheets (UVs stay in vanilla's 0-16 range -- NO texture_size, see the Punisher gun gotcha).
// Run from the repo root: node scratchpad/gen_ring_models_v0166.js
const fs = require('fs');
const path = require('path');
const { encodePng } = require('./png_tools_v0166');

const OUT_MODELS = 'src/main/resources/assets/projecthero/models/item';
const OUT_TEX = 'src/main/resources/assets/projecthero/textures/item';
const GLOW_TINT = 15;

// ---------------------------------------------------------------- swatch sheet (32 px, UV = px / 2)
const SW = {
	band: [0, 0, 16, 8],
	bandUp: [16, 0, 32, 8],
	bezel: [0, 8, 16, 16],
	rim: [16, 8, 32, 16],
	face: [0, 16, 8, 24],
	glow: [8, 16, 16, 24],
	gem: [16, 16, 32, 24],
	accent: [0, 24, 16, 32],
	dark: [16, 24, 32, 32],
};
const uv = k => SW[k].map(v => v / 2);

/** An element; {@code mat} is one swatch or {side, up, down}. {@code rot} = [axis, angle] about the element's centre. */
function el(name, from, to, mat, opts = {}) {
	const m = typeof mat === 'string' ? { side: mat, up: mat, down: mat } : mat;
	const faces = {};
	for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) {
		const k = f === 'up' ? m.up || m.side : f === 'down' ? m.down || m.side : m.side;
		faces[f] = { uv: uv(k), texture: '#t' };
		if (opts.glow) faces[f].tintindex = GLOW_TINT;
	}
	const e = { name, from: from.map(r), to: to.map(r), faces };
	if (opts.rot) {
		const c = from.map((v, i) => r((v + to[i]) / 2));
		e.rotation = { angle: opts.rot[1], axis: opts.rot[0], origin: c };
	}
	return e;
}
const r = v => Math.round(v * 1000) / 1000;

/** An octagonal band standing upright (hole along z): centre (cx, cy), outer apothem a, thickness t, z0..z1. */
function band(prefix, cx, cy, a, t, z0, z1, mat, skipTop) {
	const s = 2 * a * Math.tan(Math.PI / 8);
	const out = [
		el(prefix + '_bottom', [cx - s / 2, cy - a, z0], [cx + s / 2, cy - a + t, z1], mat),
		el(prefix + '_left', [cx - a, cy - s / 2, z0], [cx - a + t, cy + s / 2, z1], mat),
		el(prefix + '_right', [cx + a - t, cy - s / 2, z0], [cx + a, cy + s / 2, z1], mat),
	];
	if (!skipTop) {
		out.push(el(prefix + '_top', [cx - s / 2, cy + a - t, z0], [cx + s / 2, cy + a, z1], mat));
	}
	const d = (a - t / 2) / Math.SQRT2;
	const L = s * 1.02;
	for (const [n, sx, sy] of [['tr', 1, 1], ['tl', -1, 1], ['bl', -1, -1], ['br', 1, -1]]) {
		const x = cx + sx * d, y = cy + sy * d;
		// tangent to the corner: along (1,-1) for tr / bl, (1,1) for tl / br
		const ang = sx * sy > 0 ? -45 : 45;
		out.push(el(prefix + '_' + n, [x - L / 2, y - t / 2, z0], [x + L / 2, y + t / 2, z1], mat, { rot: ['z', ang] }));
	}
	return out;
}

/** A square plate with its corners cut (two crossed boxes) -- reads round at this size. */
function cross(name, cx, cz, half, cut, y0, y1, mat, opts) {
	return [
		el(name + '_a', [cx - half, y0, cz - half + cut], [cx + half, y1, cz + half - cut], mat, opts),
		el(name + '_b', [cx - half + cut, y0, cz - half], [cx + half - cut, y1, cz + half], mat, opts),
	];
}

// ---------------------------------------------------------------- Green Lantern
function glElements() {
	const E = [];
	const bandMat = { side: 'band', up: 'bandUp', down: 'band' };
	E.push(...band('band', 8, 6, 5, 1.5, 6.5, 9.5, bandMat, true));
	// shoulders up into the bezel, flaring a little over the band
	E.push(el('shoulder', [5, 9.5, 5.75], [11, 11.25, 10.25], bandMat));
	E.push(el('shoulder_lip', [5.75, 9.0, 6.25], [10.25, 9.5, 9.75], 'band'));
	// the bezel block and its bright raised rim
	E.push(el('bezel', [4.5, 11.25, 4.5], [11.5, 12.5, 11.5], { side: 'bezel', up: 'face', down: 'bezel' }));
	E.push(el('rim_n', [4.5, 12.5, 4.5], [11.5, 13.25, 5.25], 'rim'));
	E.push(el('rim_s', [4.5, 12.5, 10.75], [11.5, 13.25, 11.5], 'rim'));
	E.push(el('rim_w', [4.5, 12.5, 5.25], [5.25, 13.25, 10.75], 'rim'));
	E.push(el('rim_e', [10.75, 12.5, 5.25], [11.5, 13.25, 10.75], 'rim'));
	// the emblem: a ring between two bars, full-bright
	const y0 = 12.5, y1 = 13.1, g = { glow: true };
	const cx = 8, cz = 8, ro = 1.85, w = 0.6;
	E.push(el('emblem_n', [cx - 0.75, y0, cz - ro], [cx + 0.75, y1, cz - ro + w], 'glow', g));
	E.push(el('emblem_s', [cx - 0.75, y0, cz + ro - w], [cx + 0.75, y1, cz + ro], 'glow', g));
	E.push(el('emblem_w', [cx - ro, y0, cz - 0.75], [cx - ro + w, y1, cz + 0.75], 'glow', g));
	E.push(el('emblem_e', [cx + ro - w, y0, cz - 0.75], [cx + ro, y1, cz + 0.75], 'glow', g));
	const dd = (ro - w / 2 - 0.08) / Math.SQRT2;
	for (const [sx, sz] of [[1, 1], [-1, 1], [-1, -1], [1, -1]]) {
		const x = cx + sx * dd, z = cz + sz * dd;
		// about y: the box's x axis turns to (cos, -sin) in (x, z); tangent (1,-1) where sx*sz > 0
		const ang = sx * sz > 0 ? -45 : 45;
		E.push(el('emblem_c' + sx + sz, [x - 0.7, y0, z - w / 2], [x + 0.7, y1, z + w / 2], 'glow', { glow: true, rot: ['y', ang] }));
	}
	E.push(el('emblem_bar_n', [5.6, y0, cz - ro - 0.55], [10.4, y1, cz - ro + 0.02], 'glow', g));
	E.push(el('emblem_bar_s', [5.6, y0, cz + ro - 0.02], [10.4, y1, cz + ro + 0.55], 'glow', g));
	return E;
}

// ---------------------------------------------------------------- Flash
function flashElements() {
	const E = [];
	const bandMat = { side: 'band', up: 'bandUp', down: 'band' };
	E.push(...band('band', 8, 6, 4.75, 1.25, 6.75, 9.25, bandMat, true));
	E.push(el('shoulder', [5.5, 9.5, 6.25], [10.5, 10.5, 9.75], bandMat));
	// gold collet round the dome's foot
	E.push(...cross('collet', 8, 8, 3.6, 1.0, 10.25, 11.1, { side: 'rim', up: 'rim', down: 'band' }));
	// stepped red dome
	E.push(...cross('dome1', 8, 8, 3.2, 0.9, 11.1, 11.8, { side: 'gem', up: 'gem' }));
	E.push(...cross('dome2', 8, 8, 2.6, 0.8, 11.8, 12.4, { side: 'gem', up: 'gem' }));
	E.push(...cross('dome3', 8, 8, 1.8, 0.6, 12.4, 12.85, { side: 'gem', up: 'gem' }));
	// the bolt, raised on the dome (screen-up on the top face is -z): down-left stroke, a jog right, down-left again
	const b0 = 12.15, b1 = 13.2;
	E.push(el('bolt_upper', [8.5 - 1.55, b0, 6.75 - 0.36], [8.5 + 1.55, b1, 6.75 + 0.36], 'accent', { rot: ['y', 45] }));
	E.push(el('bolt_jog', [7.35, b0 + 0.1, 7.7], [9.15, b1, 8.3], 'accent'));
	E.push(el('bolt_lower', [7.75 - 1.7, b0, 9.25 - 0.33], [7.75 + 1.7, b1, 9.25 + 0.33], 'accent', { rot: ['y', 45] }));
	return E;
}

// ---------------------------------------------------------------- textures
function sheet(pal) {
	const W = 32, px = Buffer.alloc(W * W * 4);
	const put = (x, y, c, a = 255) => {
		const o = (y * W + x) * 4;
		px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = a;
	};
	const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
	const hash = (x, y) => {
		let h = (x * 374761393 + y * 668265263) | 0;
		h = Math.imul(h ^ (h >>> 13), 1274126177);
		return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
	};
	const fill = (k, fn) => {
		const [x0, y0, x1, y1] = SW[k];
		for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) put(x, y, fn((x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1), x, y));
	};
	// polished metal: a bright specular streak a third of the way down, darker toward the edges, faint grain
	const metal = (lo, mid, hi, spec) => (u, v, x, y) => {
		let c = v < 0.3 ? mix(hi, mid, v / 0.3) : mix(mid, lo, (v - 0.3) / 0.7);
		if (Math.abs(v - 0.22) < 0.09) c = mix(c, spec, 0.55);
		const n = (hash(x, y) - 0.5) * 14;
		return c.map(q => Math.max(0, Math.min(255, Math.round(q + n))));
	};
	fill('band', metal(pal.bandLo, pal.band, pal.bandHi, pal.spec));
	fill('bandUp', metal(pal.band, pal.bandHi, pal.spec, pal.spec));
	fill('bezel', metal(pal.bezelLo, pal.bezel, pal.bandHi, pal.bandHi));
	fill('rim', metal(pal.band, pal.bandHi, pal.spec, [255, 255, 255]));
	fill('face', (u, v, x, y) => mix(pal.face, pal.faceHi, Math.max(0, 1 - Math.hypot(u - 0.3, v - 0.3) * 1.6) * 0.6));
	fill('glow', (u, v) => mix(pal.glow, pal.glowCore, Math.max(0, 1 - Math.hypot(u - 0.5, v - 0.5) * 2.2)));
	// the dome: deep red with a hot highlight up and to the left
	fill('gem', (u, v, x, y) => {
		const d = Math.hypot(u - 0.3, v - 0.28);
		let c = mix(pal.gemHi, pal.gem, Math.min(1, d * 1.8));
		c = mix(c, pal.gemLo, Math.max(0, (v - 0.55) / 0.45) * 0.7);
		if (d < 0.12) c = mix(c, [255, 235, 220], 0.6);
		return c;
	});
	fill('accent', metal(pal.accentLo, pal.accent, pal.accentHi, [255, 255, 235]));
	fill('dark', () => pal.face);
	return encodePng(W, W, px);
}

const GL = {
	band: [31, 168, 76], bandLo: [12, 82, 36], bandHi: [96, 222, 132], spec: [196, 255, 210],
	bezel: [20, 120, 54], bezelLo: [8, 60, 26],
	face: [6, 22, 12], faceHi: [22, 58, 32],
	glow: [92, 255, 142], glowCore: [235, 255, 240],
	gem: [92, 255, 142], gemHi: [235, 255, 240], gemLo: [30, 160, 70],
	accent: [92, 255, 142], accentLo: [30, 160, 70], accentHi: [235, 255, 240],
};
const FLASH = {
	band: [222, 168, 36], bandLo: [128, 84, 12], bandHi: [255, 222, 112], spec: [255, 246, 196],
	bezel: [196, 140, 26], bezelLo: [110, 70, 10],
	face: [60, 8, 8], faceHi: [120, 20, 20],
	glow: [255, 220, 60], glowCore: [255, 250, 210],
	gem: [200, 22, 22], gemHi: [255, 92, 72], gemLo: [104, 6, 10],
	accent: [255, 206, 40], accentLo: [196, 128, 10], accentHi: [255, 244, 160],
};

function displays(kind) {
	// shared by both rings (same footprint): GUI tilted to show the face, small in the hand, a ring-sized drop
	return {
		gui: { rotation: [38, -28, 0], translation: [0, -0.75, 0], scale: [1.1, 1.1, 1.1] },
		ground: { rotation: [0, 0, 0], translation: [0, 2.5, 0], scale: [0.45, 0.45, 0.45] },
		fixed: { rotation: [30, 180, 0], translation: [0, 0, 0], scale: [0.85, 0.85, 0.85] },
		head: { rotation: [0, 0, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
		thirdperson_righthand: { rotation: [60, 0, 0], translation: [0, 3, 0.75], scale: [0.22, 0.22, 0.22] },
		thirdperson_lefthand: { rotation: [60, 0, 0], translation: [0, 3, 0.75], scale: [0.22, 0.22, 0.22] },
		firstperson_righthand: { rotation: [25, -30, 0], translation: [1.5, 4, 0.5], scale: [0.28, 0.28, 0.28] },
		firstperson_lefthand: { rotation: [25, 30, 0], translation: [1.5, 4, 0.5], scale: [0.28, 0.28, 0.28] },
	};
}

function write(name, texName, particle, elements) {
	const model = {
		credit: 'Project Hero v0.16.6 -- generated by scratchpad/gen_ring_models_v0166.js',
		textures: { t: 'projecthero:item/' + texName, particle: 'projecthero:item/' + particle },
		elements,
		display: displays(name),
	};
	const json = JSON.stringify(model, null, 2)
		.replace(/\[\s*([-0-9.,\s]+?)\s*\]/g, (m, g) => '[' + g.split(',').map(x => x.trim()).join(', ') + ']') + '\n';
	fs.writeFileSync(path.join(OUT_MODELS, name + '.json'), json);
	console.log(name, elements.length, 'elements');
}

write('power_ring', 'power_ring_model', 'power_ring', glElements());
fs.writeFileSync(path.join(OUT_TEX, 'power_ring_model.png'), sheet(GL));
write('flash_ring', 'flash_ring_model', 'flash_ring', flashElements());
fs.writeFileSync(path.join(OUT_TEX, 'flash_ring_model.png'), sheet(FLASH));
