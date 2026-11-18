// v0.17.5 Captain America shields: original pixel art for the stepped-dish shield model (CapShieldModel).
//   node scratchpad/gen_cap_shields_v0175.js      (from the checkout root)
// Writes textures/entity/{soldier,vibranium,adamantium}_shield.png (64x64) and textures/item/<same>.png (16x16 particle / flat icon).
//
// 64x64 layout (texels; the model maps 2 texels per model pixel):
//   (0,0)   32x32  front disc, centre (16,16): blue r<6, red 6-9, white 9-12, red 12-14, rim 14-16 (= model px 3 / 4.5 / 6 / 7 / 8)
//   (32,0)  32x32  back disc (concave inside), same radii
//   (0,32)  64x12  step walls, one 2-texel row each: 0 rim outer edge, 1 outer ring, 2 white ring, 3 inner ring, 4 centre, 5 back steps
//   (0,48)  16x8   leather strap (along u), (16,48) 8x8 buckle / rivet metal, (24,48) 8x8 leather pad
const p = require('./pnglib.js');
const path = require('path');
const root = process.cwd();
const A = 'src/main/resources/assets/projecthero/textures';

const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => c.map(v => clamp(v * f));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
// deterministic noise
let seed = 1;
const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
const hash = (x, y, k) => {
	let h = (x * 374761393 + y * 668265263 + k * 2147483647) | 0;
	h = (h ^ (h >>> 13)) * 1274126177;
	return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
};

function star(x, y, R, r) {
	// point-in-5-point-star, one point straight up (texel y grows down)
	const ang = Math.atan2(x, -y); // 0 = up
	const d = Math.hypot(x, y);
	const seg = Math.PI / 5;
	let a = ((ang % (2 * seg)) + 2 * seg) % (2 * seg); // 0..2seg, 0 = a point
	const t = Math.abs(a - seg) / seg; // 1 at a point, 0 at a valley
	// edge radius along the straight edge between point (R at 0) and valley (r at seg)
	const th = seg * (1 - t);
	const P = [0, R], V = [Math.sin(seg) * r, Math.cos(seg) * r];
	// intersect ray at angle th with segment P-V
	const dx = Math.sin(th), dy = Math.cos(th);
	const ex = V[0] - P[0], ey = V[1] - P[1];
	const den = dx * ey - dy * ex;
	const s = (P[0] * ey - P[1] * ex) / den;
	return d <= s;
}

const STYLES = {
	// Soldier's Shield: the classic painted shield, matte, a few chips showing the steel under the paint
	soldier: {
		red: hex('#b4232c'), white: hex('#e9e5da'), blue: hex('#203d8f'), star: hex('#f1eee6'), rim: hex('#9d1e26'), rimEdge: hex('#c9ccd0'),
		back: hex('#8f959c'), backRim: hex('#b5bac0'), chip: hex('#9aa0a6'), strap: hex('#6b4426'), sheen: 0.10, spec: 0.0, brush: 0.03, chips: 7,
	},
	// Vibranium Shield: the same design in polished metal -- deeper saturated paint, a bright specular arc and concentric spin marks
	vibranium: {
		red: hex('#c4122f'), white: hex('#d9dde6'), blue: hex('#173a9e'), star: hex('#f4f6fb'), rim: hex('#a90f28'), rimEdge: hex('#eef1f6'),
		back: hex('#8a8899'), backRim: hex('#c2c0d0'), chip: null, strap: hex('#5a3a22'), sheen: 0.16, spec: 0.55, brush: 0.06, chips: 0,
	},
	// Adamantium Shield: unpainted steel -- gunmetal and brushed-silver rings, a dark centre and a polished star
	adamantium: {
		red: hex('#454b54'), white: hex('#a9b0b9'), blue: hex('#2a2f37'), star: hex('#dde2e8'), rim: hex('#3a3f47'), rimEdge: hex('#c8ced6'),
		back: hex('#5d636b'), backRim: hex('#8b9199'), chip: null, strap: hex('#4b3020'), sheen: 0.14, spec: 0.35, brush: 0.08, chips: 0,
	},
};

function ringColor(S, r) {
	if (r < 6) return 'blue';
	if (r < 9) return 'red';
	if (r < 12) return 'white';
	if (r < 14) return 'red';
	return 'rim';
}

function quant(f, steps) {
	return Math.round(f * steps) / steps;
}

function front(S, px, W, ox, oy) {
	seed = 7;
	const chipsAt = [];
	for (let i = 0; i < S.chips; i++) {
		const a = rnd() * Math.PI * 2, rr = 6.5 + rnd() * 8.5;
		chipsAt.push([16 + Math.cos(a) * rr, 16 + Math.sin(a) * rr]);
	}
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const cx = x + 0.5 - 16, cy = y + 0.5 - 16, r = Math.hypot(cx, cy);
		const i = ((oy + y) * W + ox + x) * 4;
		if (r > 16.3) { px[i + 3] = 0; continue; }
		const ring = ringColor(S, r);
		let c = ring === 'rim' ? S.rim : S[ring];
		if (ring === 'blue' && star(cx, cy, 5.6, 2.25)) c = S.star;
		// light from the top left: a convex dish is brighter on that side, darker bottom right
		const nx = cx / 16, ny = cy / 16;
		const lambert = -(nx * -0.6 + ny * -0.8); // >0 top-left
		let f = 1 + quant(S.sheen * lambert * Math.min(1, r / 8), 4);
		// each ring is a slight bevel: lighter at its outer-top edge
		const edges = [6, 9, 12, 14, 16];
		for (const e of edges) {
			if (r > e - 1 && r <= e) f += (lambert > 0 ? 0.08 : -0.06);
			if (r > e && r <= e + 0.8 && e < 16) f -= 0.07;
		}
		// brushed / spun concentric marks
		f += (Math.floor(r * 1.5) % 2 === 0 ? 1 : -1) * S.brush * 0.5;
		f += (hash(x, y, 3) - 0.5) * S.brush;
		// specular arc (metal shields): a crescent high on the upper left
		if (S.spec > 0) {
			const ang = Math.atan2(cy, cx);
			const target = Math.atan2(-0.8, -0.6);
			let da = Math.abs(ang - target);
			if (da > Math.PI) da = 2 * Math.PI - da;
			if (da < 0.5 && r > 4 && r < 15) {
				const k = (1 - da / 0.5) * S.spec;
				c = mix(c, [255, 255, 255], quant(k * 0.5, 4));
			}
			// a second, softer glint lower right
			const t2 = Math.atan2(0.8, 0.6);
			let db = Math.abs(ang - t2);
			if (db > Math.PI) db = 2 * Math.PI - db;
			if (db < 0.3 && r > 8 && r < 13) c = mix(c, [255, 255, 255], quant((1 - db / 0.3) * S.spec * 0.25, 4));
		}
		// the rim: a polished steel edge on its outer texel
		if (ring === 'rim' && r > 15.1) c = mix(c, S.rimEdge, 0.65);
		// paint chips (soldier)
		for (const [kx, ky] of chipsAt) {
			if (Math.abs(x + 0.5 - kx) < 0.75 && Math.abs(y + 0.5 - ky) < 0.6 && ring !== 'rim') c = S.chip;
		}
		c = shade(c, f);
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
}

function back(S, px, W, ox, oy) {
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const cx = x + 0.5 - 16, cy = y + 0.5 - 16, r = Math.hypot(cx, cy);
		const i = ((oy + y) * W + ox + x) * 4;
		if (r > 16.3) { px[i + 3] = 0; continue; }
		let c = r >= 14 ? S.backRim : S.back;
		// concave: lit on the bottom right (the opposite side to the front)
		const lambert = -((cx / 16) * 0.6 + (cy / 16) * 0.8);
		let f = 1 - 0.10 * lambert * Math.min(1, r / 8);
		const steps = [6, 9, 12, 14];
		for (const e of steps) if (r > e - 0.9 && r <= e) f -= 0.12; // the step shadows
		f += (Math.floor(r * 2) % 2 === 0 ? 1 : -1) * 0.035; // spin marks
		f += (hash(x, y, 9) - 0.5) * 0.05;
		if (r > 15.1) f += 0.1;
		c = shade(c, quant(f, 16));
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
}

function walls(S, px, W) {
	const rows = [mix(S.rim, S.rimEdge, 0.55), S.red, S.white, S.red, S.blue, S.back];
	rows.forEach((c, k) => {
		for (let y = 0; y < 2; y++) for (let x = 0; x < 64; x++) {
			const i = ((32 + k * 2 + y) * W + x) * 4;
			const f = (y === 0 ? 0.86 : 0.72) + (hash(x, k, 5) - 0.5) * 0.06;
			const cc = shade(c, f);
			px[i] = cc[0]; px[i + 1] = cc[1]; px[i + 2] = cc[2]; px[i + 3] = 255;
		}
	});
	// leather strap 16x8 (along u): two stitch lines, darker edges
	for (let y = 0; y < 8; y++) for (let x = 0; x < 16; x++) {
		const i = ((48 + y) * W + x) * 4;
		let f = 1 + (hash(x, y, 11) - 0.5) * 0.12;
		if (y === 0 || y === 7) f -= 0.25;
		if ((y === 1 || y === 6) && x % 2 === 0) f += 0.35; // stitching
		const c = shade(S.strap, f);
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
	// buckle / rivet metal 8x8
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		const i = ((48 + y) * W + 16 + x) * 4;
		const edge = x === 0 || y === 0 || x === 7 || y === 7;
		const c = shade(hex('#c7ccd2'), edge ? 0.7 : 1 - (x + y) * 0.02);
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
	// leather pad 8x8
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		const i = ((48 + y) * W + 24 + x) * 4;
		const c = shade(S.strap, 0.85 + (hash(x, y, 13) - 0.5) * 0.15);
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
}

function icon(S) {
	// 16x16 flat icon (particles / anything drawing the item texture): the front at half resolution, box-filtered
	const big = Buffer.alloc(32 * 32 * 4);
	front(S, big, 32, 0, 0);
	const o = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let r = 0, g = 0, b = 0, a = 0;
		for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
			const i = ((y * 2 + dy) * 32 + x * 2 + dx) * 4;
			if (big[i + 3]) { r += big[i]; g += big[i + 1]; b += big[i + 2]; a++; }
		}
		const i = (y * 16 + x) * 4;
		const rr = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
		if (a === 0 || rr > 8.1) continue;
		o[i] = r / a; o[i + 1] = g / a; o[i + 2] = b / a; o[i + 3] = 255;
		if (rr > 7.2) { o[i] *= 0.7; o[i + 1] *= 0.7; o[i + 2] *= 0.7; } // dark outline
	}
	return o;
}

for (const [name, S] of Object.entries(STYLES)) {
	const W = 64, H = 64;
	const px = Buffer.alloc(W * H * 4);
	front(S, px, W, 0, 0);
	back(S, px, W, 32, 0);
	walls(S, px, W);
	p.write(path.join(root, A, 'entity', name + '_shield.png'), W, H, px);
	p.write(path.join(root, A, 'item', name + '_shield.png'), 16, 16, icon(S));
	console.log('wrote', name);
}
