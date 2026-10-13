// Iron Man comic-armour CONCEPT renders (planning art for the Iron Man rework; nothing here is wired into the game and
// nothing is written under src/).
//
// Nine comic-accurate armours, each modelled as cubes ON THE PLAYER RIG (head / body / arms / legs of a 2-block player,
// pivots as in the vanilla player model; armour plates sit 0.25-1.5 px off the body like Minecraft armour, extra parts
// where the design needs them), except the Hulkbuster, a ~3-block exosuit built around the wearer:
//   01 Mark 1            Tales of Suspense #39  - grey riveted "box of scraps" iron, bucket helm, mouth grille
//   02 Mark 1 V2         Tales of Suspense #40  - the same suit slimmed and painted gold
//   03 Mark 2            Tales of Suspense #48  - Ditko's first red-and-gold, round unibeam, palm repulsors
//   04 Mark 3            1970s-80s classic      - segmented red/gold, triangular unibeam, layered shoulders, wrist repulsors
//   05 Mark 4            Iron Man #218          - Hydro armour: navy/gunmetal/yellow, porthole dome, tanks, propellers
//   06 Mark 5            Iron Man #152          - Stealth: matte black, panel lines, dim blue-white emitters
//   07 Mark 6            Iron Man #200          - Silver Centurion: silver + red, flared shoulders, triangular beam
//   08 Mark 7            Iron Man #142          - Space armour: white/red/gold, gold visor, thruster pack, joint rings
//   09 Mark 8            Iron Man #304 style    - Hulkbuster exosuit, ~3 blocks
// Every texture is painted procedurally (brushed / polished / rough metal, panel lines, rivets, welds, rust, glowing eyes,
// repulsors, reactors and beams composited bright with a bloom pass). Renderer + Model come from lib_concept_models.js.
//
// Usage (from the repo root or a worktree):  node scratchpad/gen_ironman_comic_concepts.js [--out <dir>] [--only 1,4,9,lineup]
// Default output: <main repo>/scratchpad/concepts_ironman/  (01_mark1.png ... 09_mark8_hulkbuster.png, 00_lineup.png)
//
// Conventions (Bedrock / GeckoLib, as in the lib): +Y up, the model faces -Z, the wearer's RIGHT side is -X, cube
// coordinates are absolute model pixels. Pose rotation X < 0 swings a hanging limb forward; Z > 0 swings it toward -X.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth } = L;

const args = process.argv.slice(2);
const oa = args.indexOf('--out');
function defaultOut() {
	const d = __dirname.split(path.sep).join('/');
	const i = d.indexOf('/.claude/worktrees/');
	const root = i >= 0 ? d.slice(0, i) : path.join(__dirname, '..');
	return path.join(root, 'scratchpad', 'concepts_ironman');
}
const OUT = oa >= 0 ? args[oa + 1] : defaultOut();
const oo = args.indexOf('--only');
const ONLY = oo >= 0 ? args[oo + 1].split(',') : null;
const WANT = k => !ONLY || ONLY.includes(String(k));

// ================================================================ materials
// kind: polish (glossy painted / plated metal), brushed, matte, rough (raw hammered iron), under (flex undersuit),
// rubber (ribbed hose / bellows), visor (gold mirror), glass, fx (glow-only effect geometry)
const MAT = {
	under: { base: [44, 45, 50], kind: 'under' },
	dark: { base: [26, 26, 28], kind: 'matte' },
	rubber: { base: [36, 37, 40], kind: 'rubber' },
	// Mark 1
	iron: { base: [128, 130, 128], kind: 'rough', rust: 0.32 },
	ironD: { base: [98, 100, 101], kind: 'rough', rust: 0.24 },
	ironL: { base: [150, 151, 148], kind: 'rough', rust: 0.18 },
	// gold / red classics
	gold: { base: [232, 172, 46], kind: 'polish', spec: [255, 244, 196] },
	goldD: { base: [186, 128, 30], kind: 'polish', spec: [255, 222, 150] },
	goldB: { base: [228, 176, 60], kind: 'brushed', spec: [255, 236, 180] },
	red: { base: [182, 28, 32], kind: 'polish', spec: [255, 150, 140] },
	redD: { base: [128, 18, 24], kind: 'polish', spec: [236, 110, 104] },
	// silver
	silver: { base: [184, 190, 198], kind: 'polish', spec: [255, 255, 255] },
	silverD: { base: [128, 134, 144], kind: 'polish', spec: [226, 232, 240] },
	chrome: { base: [160, 168, 180], kind: 'chrome', spec: [250, 252, 255] },
	steel: { base: [96, 100, 108], kind: 'brushed', spec: [200, 206, 214] },
	// hydro
	navy: { base: [34, 48, 84], kind: 'polish', spec: [130, 160, 210] },
	navyD: { base: [24, 32, 58], kind: 'polish', spec: [100, 126, 176] },
	gunmetal: { base: [78, 84, 92], kind: 'brushed', spec: [170, 180, 192] },
	yellow: { base: [236, 188, 34], kind: 'paint' },
	// stealth
	black: { base: [34, 36, 40], kind: 'matte' },
	blackD: { base: [24, 25, 28], kind: 'matte' },
	stealthAcc: { base: [78, 94, 116], kind: 'matte' },
	// space
	white: { base: [226, 229, 233], kind: 'polish', spec: [255, 255, 255] },
	whiteD: { base: [184, 188, 196], kind: 'polish', spec: [250, 252, 255] },
	visorGold: { base: [214, 152, 38], kind: 'visor' },
	glass: { base: [24, 58, 70], kind: 'glass' },
	// effects
	fx_rep: { kind: 'fx', glow: [168, 226, 255], core: [245, 252, 255] },
	fx_uni: { kind: 'fx', glow: [190, 236, 255], core: [255, 255, 255] },
	fx_flame: { kind: 'fx', glow: [255, 140, 40], core: [255, 236, 170] },
	fx_jet: { kind: 'fx', glow: [150, 210, 255], core: [235, 248, 255] },
	fx_dim: { kind: 'fx', glow: [90, 130, 170], core: [170, 200, 230] },
	fx_wash: { kind: 'fx', glow: [110, 210, 225], core: [210, 250, 255] },
};

// glow colours
const G = {
	eye: [200, 236, 255], rep: [150, 222, 255], uni: [200, 240, 255], amber: [255, 168, 70], dimBlue: [110, 150, 200],
	lamp: [255, 244, 200], teal: [70, 190, 210], white: [235, 245, 255],
};

// ================================================================ painting helpers
function edgeDist(s, t, fw, fh) { return { top: t * fh, bot: (1 - t) * fh, left: s * fw, right: (1 - s) * fw }; }

// nearest rivet centre on a border lattice (spacing sp, inset m) in face pixels -> [dx, dy] or null
function rivetOff(u, v, fw, fh, sp, m) {
	if (fw < 2 * m + 0.4 || fh < 2 * m + 0.4) return null;
	let best = null, bd = 1e9;
	const along = (len, x) => { const n = Math.max(1, Math.round((len - 2 * m) / sp)); const st = (len - 2 * m) / n; const k = clamp(Math.round((x - m) / st), 0, n); return m + k * st; };
	for (const v0 of [m, fh - m]) { const cx = along(fw, u); const d = Math.hypot(u - cx, v - v0); if (d < bd) { bd = d; best = [u - cx, v - v0]; } }
	for (const u0 of [m, fw - m]) { const cy = along(fh, v); const d = Math.hypot(u - u0, v - cy); if (d < bd) { bd = d; best = [u - u0, v - cy]; } }
	return best;
}

function surface(m, c, f, s, t, p, info) {
	const sd = (c.seed || 0) * 7.3;
	const side = f !== 'up' && f !== 'down';
	let col = m.base.slice();
	switch (m.kind) {
		case 'polish': case 'chrome': {
			const n = fbm(p[0] * 2.5 + sd, p[1] * 0.35, p[2] * 2.5, 2);
			col = mul(col, 0.95 + 0.09 * n);
			if (side) {
				const q = clamp(t * 0.85 + (f === 'north' || f === 'east' ? s : 1 - s) * 0.15, 0, 1);
				const ch = m.kind === 'chrome' ? 1.6 : 1;
				col = mix(col, m.spec, ch * 0.34 * Math.exp(-(((q - 0.2) / 0.13) ** 2)));
				col = mul(col, 1.05 - ch * 0.2 * smooth(0.45, 1, q));
				if (m.kind === 'chrome') col = mul(col, 1 - 0.25 * Math.exp(-(((q - 0.62) / 0.08) ** 2)));
			} else if (f === 'up') col = mix(col, m.spec, 0.2);
			break;
		}
		case 'brushed': {
			const n = fbm(p[0] * 7 + sd, p[1] * 0.4, p[2] * 7, 3);
			col = mul(col, 0.88 + 0.22 * n);
			if (side) col = mul(col, 1.08 - 0.2 * t); else if (f === 'up') col = mul(col, 1.12);
			if (m.spec && side) col = mix(col, m.spec, 0.18 * Math.exp(-(((t - 0.22) / 0.12) ** 2)));
			break;
		}
		case 'matte': case 'paint': {
			const n = fbm(p[0] * 3 + sd, p[1] * 3, p[2] * 3, 2);
			col = mul(col, 0.96 + 0.08 * n);
			if (side) col = mul(col, 1.05 - 0.12 * t);
			if (m.kind === 'paint' && vnoise(p[0] * 5 + sd, p[1] * 5, p[2] * 5) > 0.9) col = mix(col, [80, 80, 82], 0.45); // chips
			break;
		}
		case 'rough': {
			const n = fbm(p[0] * 1.3 + sd, p[1] * 1.3, p[2] * 1.3, 4);
			col = mul(col, 0.8 + 0.36 * n);
			const pit = vnoise(p[0] * 4.5 + 3, p[1] * 4.5, p[2] * 4.5 + sd);
			if (pit > 0.78) col = mul(col, 0.82);
			// dents: a dark crescent with a lit rim
			const dn = vnoise(p[0] * 0.9 + sd, p[1] * 0.9 + 5, p[2] * 0.9);
			if (dn > 0.74) col = mul(col, 0.86 + (dn - 0.74) * 0.9);
			// rust: patches + drips running down
			const r = fbm(p[0] * 0.75 + 11 + sd, p[1] * 0.75, p[2] * 0.75, 3);
			const drip = fbm(p[0] * 4 + sd, p[1] * 0.25, p[2] * 4, 2);
			const rk = (m.rust || 0.5) * (smooth(0.62, 0.76, r) + 0.6 * smooth(0.66, 0.8, drip) * smooth(0.45, 0.6, r));
			col = mix(col, mix([118, 62, 30], [160, 92, 44], n), clamp(rk, 0, 0.85));
			// scratches
			if (hash3(Math.floor(p[0] * 9 + p[1] * 3), Math.floor(p[1] * 1.5 - p[2] * 9), (c.seed || 0) + 3) > 0.985) col = mul(col, 1.35);
			if (side) col = mul(col, 1.06 - 0.16 * t);
			break;
		}
		case 'under': {
			const n = fbm(p[0] * 4 + sd, p[1] * 4, p[2] * 4, 2);
			col = mul(col, 0.9 + 0.15 * n);
			const rib = (p[1] * 2.2) % 1;
			if (rib < 0.22) col = mul(col, 0.7); else if (rib < 0.4) col = mul(col, 1.18);
			break;
		}
		case 'rubber': {
			const along = side ? t * info.fh : s * info.fw;
			const rib = (along * 2.6) % 1;
			col = mul(col, rib < 0.3 ? 0.62 : rib < 0.55 ? 1.28 : 1.0);
			break;
		}
		case 'visor': {
			if (side) {
				col = mix([255, 236, 160], [196, 132, 30], smooth(0.05, 0.45, t));
				col = mix(col, [92, 56, 14], smooth(0.45, 0.62, t) * (1 - smooth(0.7, 0.95, t)));
				col = mix(col, [214, 150, 44], smooth(0.7, 1, t));
				const dg = s * 0.8 + t * 0.6;
				if (Math.abs(dg - 0.42) < 0.05) col = mix(col, [255, 252, 230], 0.75);
				if (Math.abs(dg - 0.56) < 0.018) col = mix(col, [255, 250, 220], 0.5);
			}
			break;
		}
		case 'glass': {
			col = mix([40, 110, 128], [10, 26, 34], smooth(0, 0.8, t));
			break;
		}
	}
	// directional bevel: lit top/left rims, shadowed bottom/right rims
	const bv = m.kind === 'rough' ? 1.15 : m.kind === 'under' || m.kind === 'rubber' ? 0.4 : 1;
	const e = edgeDist(s, t, info.fw, info.fh);
	const w1 = 0.12, w2 = 0.26;
	if (side) {
		if (e.top < w1) col = mul(col, 1 + 0.38 * bv);
		else if (e.bot < w1) col = mul(col, 1 - 0.36 * bv);
		else if (e.left < w1) col = mul(col, 1 + 0.18 * bv);
		else if (e.right < w1) col = mul(col, 1 - 0.24 * bv);
		else if (Math.min(e.top, e.left) < w2) col = mul(col, 1 - 0.1 * bv);
	} else if (info.ePx < w1) col = mul(col, f === 'up' ? 1 + 0.3 * bv : 1 - 0.3 * bv);
	if (f === 'down') col = mul(col, 0.72);
	return col;
}

// cut-out shapes on a face: returns null when the texel is outside, else { e } = normalised distance to the shape edge
function cutShape(kind, s, t) {
	if (kind === 'disc') { const d = Math.hypot(s - 0.5, t - 0.5) * 2; if (d > 1) return null; return { e: 1 - d, d }; }
	if (kind === 'tri' || kind === 'triup') {
		const tt = kind === 'tri' ? t : 1 - t; // 'tri' points down
		const half = 0.5 * (1 - tt), dx = Math.abs(s - 0.5);
		if (dx > half) return null;
		const e = Math.min(tt, (half - dx) * 0.9);
		return { e: e * 2.2, d: 1 - Math.min(1, e * 3.2) };
	}
	if (kind === 'ring') { const d = Math.hypot(s - 0.5, t - 0.5) * 2; if (d > 1 || d < 0.8) return null; const e = Math.min(d - 0.8, 1 - d) * 10; return { e, d: 1 - e }; }
	if (kind === 'mask') { // faceplate: chin corners clipped
		const dx = Math.abs(s - 0.5); if (t > 0.55 && dx > 0.5 - (t - 0.55) * 0.75) return null; return { e: 1, d: 0, mask: true };
	}
	if (kind === 'oct') { const dx = Math.abs(s - 0.5), dy = Math.abs(t - 0.5); if (dx + dy > 0.68) return null; const e = Math.min(0.5 - dx, 0.5 - dy, 0.68 - dx - dy); return { e: e * 2, d: 1 - e * 2 }; }
	return { e: 1, d: 0 };
}

// ---------------------------------------------------------------- feature painters (tags)
function paintEmitter(st, c, o, sh, info) {
	// rim (cube material) + glowing core with a white-hot centre; 'reactor' adds the classic ring + spokes
	const rim = o.rim == null ? 0.2 : o.rim;
	if (sh.e < rim) {
		const k = sh.e / rim;
		st.col = mul(st.col, k < 0.3 ? 1.3 : k > 0.75 ? 0.62 : 1.0);
		if (o.bolts && sh.e > rim * 0.3 && sh.e < rim * 0.8) {
			const ang = Math.atan2(info.t - 0.5, info.s - 0.5);
			const a = ((ang / (Math.PI * 2) * o.bolts) % 1 + 1) % 1;
			if (Math.abs(a - 0.5) < 0.09) st.col = mul(st.col, 1.5);
		}
		return;
	}
	const g = o.glow || G.uni;
	const inner = clamp((sh.e - rim) / (1 - rim), 0, 1);
	const dim = o.dim || 1;
	let gc = mix(g, G.white, smooth(0.15, 0.85, inner) * 0.85);
	if (o.reactor) {
		const d = sh.d / (1 - rim);
		const ang = Math.atan2(info.t - 0.5, info.s - 0.5);
		if (Math.abs(d - 0.62) < 0.07) gc = mul(gc, 0.55);
		if (d > 0.62 && (((ang / (Math.PI * 2)) * 10 % 1) + 1) % 1 < 0.18) gc = mul(gc, 0.7);
		if (d < 0.3) gc = mix(gc, G.white, 0.5);
	}
	if (inner < 0.12) gc = mul(gc, 0.75);
	gc = mul(gc, dim);
	st.col = gc; st.glow = gc;
}

function paintFace(st, c, f, p, info) {
	if (f !== 'north') return;
	const F = c.o.face;
	const X = p[0], Y = p[1], ax = Math.abs(X);
	const [x0, x1] = F.eyeX, [y0, y1] = F.eyeY;
	let inEye = false, nearEye = false;
	if (ax >= x0 - 0.13 && ax <= x1 + 0.13) {
		const k = clamp((ax - x0) / (x1 - x0), 0, 1);
		const yb = y0 + (F.slant || 0) * k, yt = y1 - (F.topSlant || 0) * k;
		const inner = (F.innerDrop || 0) * (1 - k);
		if (ax >= x0 && ax <= x1 && Y >= yb - inner && Y <= yt) inEye = true;
		else if (Y >= yb - inner - 0.13 && Y <= yt + 0.13) nearEye = true;
	}
	if (inEye) {
		if (F.dark) { st.col = [14, 14, 16]; if (F.glow) { const g = mul(F.glow, F.dim || 0.5); st.col = g; st.glow = g; } return; }
		const g0 = mul(F.glow || G.eye, F.dim || 1);
		const k = clamp((ax - x0) / (x1 - x0), 0, 1);
		const gc = mix(g0, mul(G.white, F.dim || 1), 0.6 * (1 - Math.abs(k - 0.45) * 1.6));
		st.col = gc; st.glow = gc; return;
	}
	if (nearEye) { st.col = mul(st.col, 0.42); return; }
	// mouth
	if (F.mouth === 'slit' || F.mouth === 'frown') {
		const mw = F.mouthW || 1.4, my = F.mouthY || 25.7;
		let yy = my;
		if (F.mouth === 'frown' && ax > mw * 0.7) yy = my - (ax - mw * 0.7) * 0.6;
		if (ax <= mw + (F.mouth === 'frown' ? 0.35 : 0) && Math.abs(Y - yy) < 0.15) { st.col = [16, 12, 12]; return; }
		if (ax <= mw && Y - yy < -0.15 && Y - yy > -0.27) st.col = mul(st.col, 1.25);
	}
	if (F.mouth === 'grille') {
		const gw = F.grilleW || 2.3, gy0 = F.grilleY ? F.grilleY[0] : 24.6, gy1 = F.grilleY ? F.grilleY[1] : 26.4;
		if (ax <= gw && Y >= gy0 && Y <= gy1) {
			const q = ((X + gw) * 1.75) % 1;
			st.col = q < 0.45 ? [14, 14, 14] : q < 0.6 ? mul(st.col, 1.35) : mul(st.col, 0.9);
			return;
		}
		if (ax <= gw + 0.15 && Y >= gy0 - 0.15 && Y <= gy1 + 0.15) { st.col = mul(st.col, 0.5); return; }
	}
	// cheek / jaw grooves: from the outer eye corner down to beside the mouth
	if (F.cheek) {
		const [ax0, ay0, ax1, ay1] = F.cheek; // |x| at top, y at top, |x| at bottom, y at bottom
		if (Y <= ay0 && Y >= ay1) {
			const xl = ax0 + (ax1 - ax0) * (ay0 - Y) / (ay0 - ay1);
			const d = ax - xl;
			if (Math.abs(d) < 0.07) { st.col = mul(st.col, 0.5); return; }
			if (d < -0.07 && d > -0.17) st.col = mul(st.col, 1.22);
		}
	}
	if (F.brow != null && Math.abs(Y - F.brow) < 0.07 && ax < x1 + 0.4) st.col = mul(st.col, 0.55);
	if (F.ridge && ax < 0.06 && Y > y1 + 0.2) st.col = mul(st.col, 1.3);
}

function paintPropeller(st, s, t) {
	const dx = s - 0.5, dy = t - 0.5, d = Math.hypot(dx, dy) * 2;
	if (d > 0.88) return;                                  // housing face
	if (d > 0.78) { st.col = mul(st.col, 0.5); return; }   // shroud lip
	const ang = Math.atan2(dy, dx);
	const blade = ((ang / (Math.PI * 2) * 3 + d * 0.35) % 1 + 1) % 1;
	if (d < 0.18) { st.col = [236, 188, 34]; return; }      // yellow hub
	st.col = blade < 0.32 ? mul([150, 156, 166], 1.05 - 0.4 * blade) : [12, 14, 18];
}

// ================================================================ shading entry
function shader() {
	return (c, f, s, t, p, info) => {
		const m = MAT[c.mat];
		const o = c.o || {};
		if (m.kind === 'fx') {
			if (o.cut) { if (f !== (o.cutFace || 'north')) return [[0, 0, 0], 0]; const sh = cutShape(o.cut, s, t); if (!sh) return [[0, 0, 0], 0]; const gc = mix(m.glow, m.core, smooth(0.1, 0.8, sh.e)); return [gc, 0, gc, 255]; }
			if (o.hollow != null) { // fake volumetric beam: the middle strip of each long face is open onto the white core inside
				const ax = o.hollow; const nAx = f === 'east' || f === 'west' ? 0 : f === 'up' || f === 'down' ? 1 : 2;
				if (nAx === ax) return [[0, 0, 0], 0];
				const sAx = nAx === 0 ? 2 : 0; // the axis s runs along (north/south/up/down: x, east/west: z)
				const across = sAx === ax ? t : s;
				const a = Math.abs(across - 0.5) * 2;
				if (a < (o.open || 0.45)) return [[0, 0, 0], 0];
				const q = (p[ax] - c.from[ax]) / (c.to[ax] - c.from[ax]); // 0 at the min end
				const fk = o.bright === 'min' ? 1 - q : o.bright === 'max' ? q : 1;
				const gc = mul(mix(m.core, m.glow, smooth(0.4, 1, a)), 0.55 + 0.45 * fk);
				return [gc, 0, gc, 255];
			}
			const q = o.ax != null ? (p[o.ax] - c.from[o.ax]) / (c.to[o.ax] - c.from[o.ax]) : 0;
			const k = o.bright === 'min' ? 1 - q : o.bright === 'max' ? q : 1;
			const gc = mix(m.glow, m.core, o.coreK == null ? 0.55 : o.coreK);
			const g2 = mul(gc, 0.4 + 0.6 * k);
			return [g2, 0, g2, 255];
		}
		const fi = Object.assign({}, info, { s, t });
		let sh = null;
		if (o.cut) {
			if (f !== (o.cutFace || 'north')) { if (!o.cutKeepSides) return [[0, 0, 0], 0]; }
			else { sh = cutShape(o.cut, s, t); if (!sh) return [[0, 0, 0], 0]; if (sh.mask) sh = null; }
		}
		let col = surface(m, c, f, s, t, p, info);
		const st = { col, glow: null, a: 255 };
		const side = f !== 'up' && f !== 'down';
		const u = s * info.fw, v = t * info.fh;
		// painted bands in another material: [axis, a, b, matName]
		if (o.bands) for (const [ax, a, b, mn] of o.bands) if (p[ax] >= a && p[ax] <= b) {
			st.col = surface(MAT[mn], c, f, s, t, p, info);
			if (Math.abs(p[ax] - a) < 0.08 || Math.abs(p[ax] - b) < 0.08) st.col = mul(st.col, 0.6);
		}
		if (o.paintFn) o.paintFn(st, c, f, s, t, p, info);
		// grooves
		if (o.lines) for (const ax of [0, 1, 2]) {
			const vals = o.lines[ax]; if (!vals) continue;
			const nAx = f === 'east' || f === 'west' ? 0 : f === 'up' || f === 'down' ? 1 : 2;
			if (nAx === ax) continue;
			for (const val of vals) {
				const d = p[ax] - val;
				if (Math.abs(d) < 0.065) st.col = MAT[c.mat].base[0] < 50 ? mix(st.col, [92, 100, 112], 0.55) : mul(st.col, 0.45);
				else if (ax === 1 && d < -0.065 && d > -0.16) st.col = mul(st.col, 1.22);
				else if (ax !== 1 && d > 0.065 && d < 0.15) st.col = mul(st.col, 1.15);
			}
		}
		// weld beads
		if (o.weld) for (const val of o.weld) {
			const d = Math.abs(p[1] - val);
			if (side && d < 0.2) { const ph = Math.sin((p[0] + p[2]) * 15 + p[1] * 3); st.col = mul(mix(st.col, [150, 140, 128], 0.4), 0.78 + 0.36 * (0.5 + 0.5 * ph) - d); }
			else if (side && d < 0.55) st.col = mix(st.col, [92, 74, 70], 0.22 * (1 - d / 0.55));
		}
		// rivets
		if (o.rivet && f !== 'down' && !(o.rivetFaces && !o.rivetFaces.includes(f))) {
			const r = rivetOff(u, v, info.fw, info.fh, o.rivet, o.rivetM || 0.42);
			const R = o.rivetR || 0.2;
			if (r) {
				const d = Math.hypot(r[0], r[1]);
				if (d < R) { const l = (-r[0] - r[1]) / R; st.col = mul(MAT[c.mat].base, 0.95 + 0.42 * l * 0.7 + (d < R * 0.4 ? 0.12 : 0)); if (MAT[c.mat].kind === 'rough') st.col = mix(st.col, [110, 70, 44], 0.18); }
				else if (d < R + 0.09 && r[0] + r[1] > 0) st.col = mul(st.col, 0.55);
			}
		}
		if (sh) paintEmitter(st, c, o, sh, fi);
		if (o.face) paintFace(st, c, f, p, info);
		if (o.palm && f === o.palm) {
			const d = Math.hypot(s - 0.5, t - 0.5) * 2;
			if (d < 0.62) { const gc = mix(o.glow || G.rep, G.white, 0.8 * (1 - d / 0.62)); st.col = gc; st.glow = mul(gc, o.dim || 1); }
			else if (d < 0.78) st.col = mul(st.col, 0.5);
		}
		if (o.lens && f === o.lens) {
			const d = Math.hypot(s - 0.5, t - 0.5) * 2;
			if (d < 0.7) { const gc = mul(mix(o.glow || G.lamp, G.white, 0.7 * (1 - d / 0.7)), o.dim || 1); st.col = gc; st.glow = gc; }
			else if (d < 0.86) st.col = mul(st.col, 0.45);
		}
		if (o.nozzle && f === o.nozzle) {
			const d = Math.hypot(s - 0.5, t - 0.5) * 2;
			if (d < 0.5) { if (o.glow) { const gc = mul(mix(o.glow, G.white, 0.5 * (1 - d / 0.5)), o.dim || 1); st.col = gc; st.glow = gc; } else st.col = [18, 18, 20]; }
			else if (d < 0.66) st.col = mul(st.col, 0.4);
			else if (d < 0.78) st.col = mul(st.col, 1.3);
		}
		if (o.prop && f === o.prop) paintPropeller(st, s, t);
		if (o.vent && o.vent.includes(f)) {
			const along = f === 'up' || f === 'down' ? s * info.fw : t * info.fh;
			if (info.ePx > 0.35) { const q = (along * 2.2) % 1; st.col = q < 0.45 ? [16, 16, 18] : q < 0.6 ? mul(st.col, 1.25) : st.col; }
		}
		return [st.col, st.a, st.glow];
	};
}

// ================================================================ rig + build helpers
// Player rig: bones root(pivot 0,12,0) > head(0,24,0), arm_r(-5,22,0), arm_l(5,22,0), leg_r(-1.9,12,0), leg_l(1.9,12,0),
// plus effect bones (fx_*) that are hidden in the turnaround panels.
function rig(name, dims = {}) {
	const M = new Model(name, { D: dims.D || 8, TW: dims.TW || 2048 });
	const P = Object.assign({ head: [0, 24, 0], armR: [-5, 22, 0], legR: [-1.9, 12, 0], root: [0, 12, 0] }, dims.pivots || {});
	M.bone('root', null, P.root);
	M.bone('head', 'root', P.head);
	M.bone('arm_r', 'root', P.armR);
	M.bone('arm_l', 'root', [-P.armR[0], P.armR[1], P.armR[2]]);
	M.bone('leg_r', 'root', P.legR);
	M.bone('leg_l', 'root', [-P.legR[0], P.legR[1], P.legR[2]]);
	M.bone('fx_chest', 'root', [0, 20, 0]);
	M.bone('fx_arm_r', 'arm_r', P.armR); M.bone('fx_arm_l', 'arm_l', [-P.armR[0], P.armR[1], P.armR[2]]);
	M.bone('fx_leg_r', 'leg_r', P.legR); M.bone('fx_leg_l', 'leg_l', [-P.legR[0], P.legR[1], P.legR[2]]);
	M.bone('fx_back', 'root', [0, 18, 4]);
	M.heightBlocks = 2;
	let seed = 1;
	M.add = (bone, a, mat, o = {}) => { const c = M.box(bone, a[0], a[1], a[2], a[3], a[4], a[5], mat, { rot: o.rot, pivot: o.pivot, seed: seed++ }); c.o = o; return c; };
	// mirrored pair: the given coords are the RIGHT side (-X); bone names with _r get their _l twin
	M.pair = (bone, a, mat, o = {}) => {
		const r = M.add(bone, a, mat, o);
		const lb = bone.endsWith('_r') ? bone.slice(0, -2) + '_l' : bone;
		const o2 = Object.assign({}, o);
		if (o.rot) { o2.rot = [o.rot[0], -o.rot[1], -o.rot[2]]; o2.pivot = [-o.pivot[0], o.pivot[1], o.pivot[2]]; }
		if (o.cutFace === 'west') o2.cutFace = 'east'; else if (o.cutFace === 'east') o2.cutFace = 'west';
		if (o.palm === 'west') o2.palm = 'east'; else if (o.palm === 'east') o2.palm = 'west';
		if (o.lens === 'west') o2.lens = 'east'; else if (o.lens === 'east') o2.lens = 'west';
		if (o.nozzle === 'west') o2.nozzle = 'east'; else if (o.nozzle === 'east') o2.nozzle = 'west';
		if (o.vent) o2.vent = o.vent.map(f => f === 'west' ? 'east' : f === 'east' ? 'west' : f);
		const l = M.add(lb, [-a[3], a[1], a[2], -a[0], a[4], a[5]], mat, o2);
		return [r, l];
	};
	// glowing beam along axis ax from lo to hi, centred on (c1, c2) = the other two axes in x,y,z order, width w
	// (wd: depth on the second cross axis): a white core inside a hollow coloured shell (reads as a volumetric beam)
	M.beam = (bone, ax, lo, hi, c1, c2, w, mat, bright, opts = {}) => {
		const wd = opts.wd || w;
		const mk = (ww, dd) => { const a = [0, 0, 0, 0, 0, 0]; const cr = [0, 1, 2].filter(i => i !== ax); a[ax] = lo; a[ax + 3] = hi; a[cr[0]] = c1 - ww / 2; a[cr[0] + 3] = c1 + ww / 2; a[cr[1]] = c2 - dd / 2; a[cr[1] + 3] = c2 + dd / 2; return a; };
		const add = opts.pair ? M.pair : M.add;
		add(bone, mk(w * 0.42, wd * 0.42), mat, { ax, bright, coreK: 0.92 });
		add(bone, mk(w, wd), mat, { hollow: ax, bright, open: opts.open || 0.5 });
		if (opts.halo) add(bone, mk(w * opts.halo, wd * opts.halo), mat, { hollow: ax, bright, open: 0.78 });
	};
	// the wearer's vanilla body parts in undersuit (only visible through the joint gaps)
	M.add('root', [-4, 12, -2, 4, 24, 2], 'under');
	M.pair('arm_r', [-8, 12, -2, -4, 24, 2], 'under');
	M.pair('leg_r', [-3.9, 0, -2, 0.1, 12, 2], 'under');
	M.add('head', [-4, 24, -4, 4, 32, 4], 'under');
	return M;
}
const isFx = c => /^fx_/.test(c.bone);

function finish(M) { M.layout(); M.paint(shader()); return M; }
function planted(M, pose, lift = 0) {
	const W = M.world(pose);
	return [0, -M.bounds(W, c => !isFx(c)).lo[1] + lift, 0];
}

// ================================================================ the nine armours
// ---------------------------------------------------------------- 01 Mark 1 (grey iron)
function buildMark1() {
	const M = rig('mark1');
	const RV = { rivet: 1.5 }, RV2 = { rivet: 1.25, rivetR: 0.22 };
	// bucket helmet
	M.add('head', [-5, 23.3, -5, 5, 32.3, 5], 'iron', Object.assign({ weld: [27.2], lines: { 0: [0] } }, RV));
	M.add('head', [-4.4, 32.3, -4.4, 4.4, 33.0, 4.4], 'ironD', { rivet: 1.4, rivetFaces: ['up'] });
	M.add('head', [-1, 33.0, -3.6, 1, 33.4, 3.6], 'ironD');
	M.add('head', [-4.1, 23.9, -5.55, 4.1, 30.2, -5.0], 'ironL', {
		rivet: 1.3, rivetR: 0.18, face: { eyeX: [0.75, 3.1], eyeY: [28.15, 28.6], slant: 0.06, dark: true, glow: G.amber, dim: 0.42, mouth: 'grille', grilleW: 2.0, grilleY: [24.8, 26.6] },
	});
	M.add('head', [-4.7, 29.7, -5.95, 4.7, 31.0, -5.0], 'ironD', { rivet: 1.15, rivetR: 0.17 });
	M.pair('head', [-5.55, 26.0, -1.3, -5.0, 28.6, 1.3], 'ironD', { rivet: 0.9, rivetR: 0.22, rivetM: 0.5 });
	// barrel chest
	M.add('root', [-5.3, 12.4, -3.6, 5.3, 24.2, 3.3], 'iron', Object.assign({ weld: [18.4], lines: { 0: [0] } }, RV));
	M.add('root', [-4.6, 15.0, -4.35, 4.6, 23.6, -3.6], 'iron', Object.assign({ weld: [] }, RV2));
	M.add('root', [-2.5, 17.2, -4.75, 2.5, 22.2, -4.35], 'ironD', { cut: 'disc', rim: 0.34, bolts: 8, glow: G.amber, dim: 0.55, reactor: true });
	M.add('root', [-5.6, 11.4, -3.95, 5.6, 13.3, 3.65], 'ironD', { rivet: 1.2, rivetR: 0.2 });
	M.add('root', [-4.3, 14.2, 3.3, 4.3, 22.8, 4.2], 'ironD', Object.assign({ vent: ['south'] }, RV2));
	M.add('root', [-3.6, 23.6, -3.6, 3.6, 24.6, 3.6], 'ironD');
	// arms: rounded pauldrons, plain upper arms, big box gauntlets
	M.pair('arm_r', [-9.5, 20.4, -3.5, -3.7, 24.9, 3.5], 'iron', Object.assign({ weld: [22.4] }, RV));
	M.pair('arm_r', [-9.0, 24.9, -3.0, -4.2, 25.6, 3.0], 'ironD');
	M.pair('arm_r', [-8.65, 15.5, -2.65, -3.4, 20.5, 2.65], 'iron', { lines: { 1: [18] } });
	M.pair('arm_r', [-9.7, 10.4, -3.4, -2.75, 16.2, 3.4], 'ironD', Object.assign({ weld: [13.4] }, RV2));
	M.pair('arm_r', [-9.2, 9.4, -2.9, -3.2, 10.4, 2.9], 'iron', { lines: { 0: [-7.6, -6.2, -4.8] } });
	// legs: thick thighs, knee plates, heavy boots with heel jets
	M.pair('leg_r', [-4.75, 6.0, -2.85, -0.12, 12.6, 2.85], 'iron', Object.assign({ weld: [9.2] }, RV));
	M.pair('leg_r', [-4.4, 4.8, -3.5, -0.45, 7.6, -2.85], 'ironD', { rivet: 1.0, rivetR: 0.2 });
	M.pair('leg_r', [-5.0, 0, -3.15, -0.12, 5.4, 3.0], 'ironD', Object.assign({ weld: [3.0] }, RV2));
	M.pair('leg_r', [-4.75, 0, -4.7, -0.35, 2.1, -3.15], 'iron', { rivet: 1.1, rivetR: 0.18 });
	M.pair('leg_r', [-3.7, 0.5, 3.0, -1.4, 2.8, 4.3], 'steel', { nozzle: 'south' });
	M.beam('fx_leg_r', 2, 4.3, 8.5, -2.55, 1.65, 1.6, 'fx_flame', 'min', { pair: true });
	M.beam('fx_leg_r', 1, -5, 0, -2.4, -0.3, 2.2, 'fx_flame', 'max', { pair: true });
	return finish(M);
}

// ---------------------------------------------------------------- 02 Mark 1 V2 (gold)
function buildMark1v2() {
	const M = rig('mark1_v2');
	M.add('head', [-4.7, 23.6, -4.7, 4.7, 32.3, 4.7], 'gold', { lines: { 1: [27.4] } });
	M.add('head', [-4.15, 32.3, -4.15, 4.15, 32.85, 4.15], 'goldD');
	M.add('head', [-3.9, 24.1, -5.15, 3.9, 30.0, -4.7], 'gold', {
		face: { eyeX: [0.75, 3.0], eyeY: [28.05, 28.65], slant: 0.08, glow: [255, 236, 190], dim: 0.95, mouth: 'grille', grilleW: 1.7, grilleY: [24.9, 26.2] },
	});
	M.add('head', [-4.4, 29.6, -5.5, 4.4, 30.6, -4.7], 'goldD', { rivet: 2.2, rivetR: 0.16 });
	M.pair('head', [-5.1, 26.2, -1.1, -4.7, 28.4, 1.1], 'goldD');
	M.add('root', [-4.85, 12.7, -3.05, 4.85, 24.2, 2.85], 'gold', { lines: { 1: [17.6], 0: [0] } });
	M.add('root', [-4.3, 17.6, -3.5, 4.3, 23.6, -3.05], 'gold', { lines: { 0: [0] } });
	M.add('root', [-1.25, 19.3, -3.85, 1.25, 21.8, -3.5], 'goldD', { cut: 'disc', rim: 0.3, glow: [255, 240, 200], reactor: false });
	M.add('root', [-5.1, 11.6, -3.3, 5.1, 13.1, 3.1], 'goldD', { rivet: 3.0, rivetR: 0.17 });
	M.add('root', [-3.9, 14.6, 2.85, 3.9, 22.4, 3.5], 'goldD', { lines: { 1: [17, 19.8] } });
	M.pair('arm_r', [-9.0, 21.0, -3.1, -3.75, 24.7, 3.1], 'gold', { lines: { 1: [22.6] } });
	M.pair('arm_r', [-8.5, 15.8, -2.5, -3.5, 21.0, 2.5], 'goldB');
	M.pair('arm_r', [-9.05, 10.7, -3.0, -3.0, 16.0, 3.0], 'gold', { lines: { 1: [14.6] } });
	M.pair('arm_r', [-8.8, 9.9, -2.6, -3.3, 10.7, 2.6], 'goldD');
	M.pair('leg_r', [-4.55, 5.8, -2.6, -0.12, 12.5, 2.6], 'gold', { lines: { 1: [9.6] } });
	M.pair('leg_r', [-4.2, 4.9, -3.1, -0.5, 7.3, -2.6], 'goldD');
	M.pair('leg_r', [-4.75, 0, -2.85, -0.12, 5.3, 2.75], 'gold', { lines: { 1: [3.2] } });
	M.pair('leg_r', [-4.55, 0, -4.0, -0.35, 1.8, -2.85], 'goldD');
	M.pair('leg_r', [-3.4, 0.45, 2.75, -1.6, 2.3, 3.6], 'steel', { nozzle: 'south' });
	M.beam('fx_leg_r', 1, -8, 0, -2.25, -0.15, 2.4, 'fx_flame', 'max', { pair: true });
	return finish(M);
}

// ---------------------------------------------------------------- shared classic pieces
function classicHelmet(M, shell, plate, faceO, extra = {}) {
	M.add('head', [-4.6, 23.6, -4.6, 4.6, 32.6, 4.6], shell, { lines: { 0: [0] } });
	M.add('head', [-4.0, 32.6, -4.0, 4.0, 33.0, 4.0], shell);
	M.add('head', extra.plate || [-3.55, 24.0, -5.12, 3.55, 30.8, -4.6], plate, { face: faceO, cut: 'mask', cutKeepSides: true });
	if (extra.ears !== false) M.pair('head', [-5.05, 26.4, -1.05, -4.6, 28.6, 1.05], extra.earMat || plate);
}

// ---------------------------------------------------------------- 03 Mark 2 (Ditko red and gold)
function buildMark2() {
	const M = rig('mark2');
	classicHelmet(M, 'red', 'gold', { eyeX: [0.6, 2.8], eyeY: [28.15, 28.95], slant: 0.18, topSlant: 0.12, mouth: 'slit', mouthW: 1.25, mouthY: 25.8, cheek: [3.05, 28.0, 1.95, 24.4] });
	M.add('root', [-4.6, 12.6, -2.6, 4.6, 24.35, 2.6], 'red', { lines: { 0: [0] } });
	M.add('root', [-4.3, 17.9, -3.1, 4.3, 23.9, -2.6], 'red', { lines: { 0: [0] } });
	M.add('root', [-1.65, 18.75, -3.45, 1.65, 22.05, -3.1], 'gold', { cut: 'disc', rim: 0.24, glow: G.uni, reactor: true });
	M.add('root', [-4.75, 11.4, -2.85, 4.75, 13.25, 2.85], 'red');
	M.add('root', [-4.9, 12.95, -3.0, 4.9, 13.85, 3.0], 'gold', { rivet: 1.2, rivetR: 0.16, rivetFaces: ['north'] });
	M.add('root', [-3.6, 15, 2.6, 3.6, 22.6, 3.1], 'red', { lines: { 1: [18.8] } });
	M.pair('arm_r', [-8.55, 15.6, -2.55, -3.45, 24.45, 2.55], 'gold', { lines: { 1: [20.4] } });
	M.pair('arm_r', [-8.85, 22.0, -2.85, -3.65, 24.85, 2.85], 'gold');
	M.pair('arm_r', [-8.8, 10.8, -2.8, -3.2, 15.9, 2.8], 'red', { palm: 'down', glow: G.rep });
	M.pair('arm_r', [-9.05, 15.3, -3.05, -2.95, 16.55, 3.05], 'red');
	M.pair('leg_r', [-4.55, 5.4, -2.55, -0.12, 12.4, 2.55], 'gold', { lines: { 1: [8.8] } });
	M.pair('leg_r', [-4.2, 7.0, -2.95, -0.5, 9.0, -2.55], 'gold');
	M.pair('leg_r', [-4.72, 0, -2.75, -0.12, 5.7, 2.75], 'red');
	M.pair('leg_r', [-4.9, 5.0, -2.95, -0.1, 6.1, 2.95], 'red');
	M.pair('leg_r', [-4.55, 0, -3.5, -0.35, 1.4, -2.75], 'red');
	M.beam('fx_arm_r', 1, -24, 10.6, -6, 0, 2.4, 'fx_rep', 'max', { halo: 1.55 });
	M.add('fx_arm_r', [-8.6, 10.2, -2.6, -3.4, 10.7, 2.6], 'fx_rep', { cut: 'disc', cutFace: 'down' });
	return finish(M);
}

// ---------------------------------------------------------------- 04 Mark 3 (segmented classic, triangle unibeam)
function buildMark3() {
	const M = rig('mark3');
	const face = { eyeX: [0.55, 2.85], eyeY: [28.2, 28.9], slant: 0.22, topSlant: 0.2, mouth: 'slit', mouthW: 1.3, mouthY: 25.75, cheek: [3.1, 28.0, 2.05, 24.8], brow: 29.55, ridge: true };
	classicHelmet(M, 'red', 'gold', face, { plate: [-3.6, 25.2, -5.12, 3.6, 30.9, -4.6], earMat: 'gold' });
	M.add('head', [-2.75, 23.75, -5.3, 2.75, 25.45, -4.6], 'gold', { lines: { 0: [0] } });
	M.add('head', [-4.9, 30.6, -4.3, 4.9, 31.6, 3.4], 'red');
	// torso: red chest, gold segmented abs, triangular unibeam
	M.add('root', [-4.6, 12.6, -2.6, 4.6, 24.35, 2.6], 'red');
	M.add('root', [-4.3, 18.2, -3.1, 4.3, 23.9, -2.6], 'red', { lines: { 0: [0] } });
	M.add('root', [-2.25, 19.0, -3.5, 2.25, 22.9, -3.1], 'gold', { cut: 'tri', rim: 0.2, glow: G.uni });
	M.add('root', [-3.3, 13.4, -3.0, 3.3, 18.2, -2.6], 'gold', { lines: { 1: [14.6, 15.8, 17.0], 0: [0] } });
	M.pair('root', [-4.85, 13.6, -2.4, -4.6, 21.6, 2.4], 'gold', { lines: { 1: [16.2, 18.8] } });
	M.add('root', [-4.8, 11.4, -2.85, 4.8, 13.4, 2.85], 'red');
	M.add('root', [-4.95, 12.6, -3.0, 4.95, 13.25, 3.0], 'gold');
	M.add('root', [-3.7, 14.5, 2.6, 3.7, 23.2, 3.2], 'red', { lines: { 1: [17.2, 19.9], 0: [0] }, vent: [] });
	// layered shoulders
	M.pair('arm_r', [-9.0, 22.7, -3.0, -3.75, 25.05, 3.0], 'red', { lines: { 0: [-6.4] } });
	M.pair('arm_r', [-9.35, 21.25, -3.2, -4.4, 23.0, 3.2], 'red');
	M.pair('arm_r', [-9.65, 19.85, -3.35, -5.2, 21.55, 3.35], 'gold');
	M.pair('arm_r', [-8.55, 16.4, -2.55, -3.45, 21.0, 2.55], 'gold', { lines: { 1: [18.4] } });
	M.pair('arm_r', [-8.8, 10.8, -2.8, -3.2, 15.7, 2.8], 'red', { palm: 'down', glow: G.rep, lines: { 1: [12.4] } });
	M.pair('arm_r', [-9.05, 13.35, -3.05, -2.95, 14.45, 3.05], 'gold');
	M.pair('arm_r', [-9.5, 13.0, -1.0, -9.05, 14.8, 1.0], 'gold', { lens: 'west', glow: G.rep, dim: 0.75 });
	// legs
	M.pair('leg_r', [-4.55, 6.6, -2.55, -0.12, 12.4, 2.55], 'gold', { lines: { 1: [9.4] } });
	M.pair('leg_r', [-4.25, 5.0, -3.1, -0.45, 7.8, -2.55], 'red', { lines: { 1: [6.4] } });
	M.pair('leg_r', [-4.72, 0, -2.75, -0.12, 6.0, 2.75], 'red', { lines: { 1: [2.4, 4.2] } });
	M.pair('leg_r', [-4.9, 5.6, -2.95, -0.1, 6.5, 2.95], 'gold');
	M.pair('leg_r', [-4.55, 0, -3.6, -0.35, 1.5, -2.75], 'red');
	// unibeam: a thick triangular-ish beam
	M.beam('fx_chest', 2, -26, -3.5, 0, 20.6, 3.0, 'fx_uni', 'max', { wd: 2.8, halo: 1.6 });
	M.add('fx_chest', [-2.6, 18.4, -3.9, 2.6, 23.2, -3.6], 'fx_uni', { cut: 'tri' });
	return finish(M);
}

// ---------------------------------------------------------------- 05 Mark 4 (Hydro armour)
function buildMark4() {
	const M = rig('mark4');
	// domed helmet
	M.add('head', [-5.4, 23.2, -5.4, 5.4, 32.0, 5.4], 'navy', { rivet: 1.8, rivetR: 0.18, rivetFaces: ['east', 'west', 'south'] });
	M.add('head', [-4.8, 32.0, -4.8, 4.8, 33.0, 4.8], 'navy');
	M.add('head', [-3.7, 33.0, -3.7, 3.7, 33.7, 3.7], 'navyD');
	M.add('head', [-1.6, 33.7, -1.6, 1.6, 34.1, 1.6], 'yellow');
	M.add('head', [-5.8, 22.5, -5.8, 5.8, 23.75, 5.8], 'gunmetal', { lines: { 1: [23.1] } });
	M.add('head', [-3.2, 25.0, -6.05, 3.2, 31.4, -5.4], 'yellow', { cut: 'disc', rim: 0.2, bolts: 8, glow: G.teal, dim: 0.7, reactor: false });
	M.add('head', [-2.4, 25.8, -6.08, 2.4, 30.6, -6.0], 'glass', { cut: 'disc', rim: 0.0, glow: [70, 170, 196], dim: 0.55 });
	M.pair('head', [-6.4, 28.3, -4.6, -5.2, 30.5, -2.4], 'gunmetal', { lens: 'north', glow: G.lamp });
	// torso
	M.add('root', [-5.2, 12.4, -3.2, 5.2, 24.2, 3.0], 'navy', { rivet: 1.6, rivetR: 0.16, lines: { 0: [0] } });
	M.add('root', [-4.6, 15.6, -3.7, 4.6, 22.6, -3.2], 'gunmetal', { lines: { 1: [17.8] }, bands: [[1, 20.4, 21.2, 'yellow']] });
	M.add('root', [-1.15, 18.4, -3.95, 1.15, 20.3, -3.7], 'yellow', { cut: 'disc', rim: 0.35, glow: G.amber, dim: 0.75 });
	M.add('root', [-5.5, 11.3, -3.5, 5.5, 13.1, 3.3], 'yellow', { rivet: 1.4, rivetR: 0.18 });
	// back air tanks + hoses into the helmet
	M.pair('root', [-4.0, 13.4, 3.0, -0.4, 23.4, 6.4], 'yellow', { bands: [[1, 15.0, 15.6, 'gunmetal'], [1, 21.2, 21.8, 'gunmetal']] });
	M.pair('root', [-3.6, 23.4, 3.4, -0.8, 24.4, 6.0], 'gunmetal');
	M.pair('root', [-2.6, 24.4, 4.2, -1.8, 25.2, 5.2], 'steel');
	M.tube('root', [[-2.2, 24.8, 4.7], [-3.8, 26.6, 6.6], [-4.6, 28.4, 5.8]], 0.95, 'rubber', {}).forEach(c => { c.o = {}; });
	M.tube('root', [[2.2, 24.8, 4.7], [3.8, 26.6, 6.6], [4.6, 28.4, 5.8]], 0.95, 'rubber', {}).forEach(c => { c.o = {}; });
	// shoulder ballast tanks
	M.pair('arm_r', [-10.0, 21.6, -3.6, -5.0, 25.0, 3.6], 'gunmetal', { bands: [[2, -2.3, -1.4, 'yellow'], [2, 1.4, 2.3, 'yellow']] });
	M.pair('arm_r', [-9.4, 22.2, -4.25, -5.6, 24.4, -3.6], 'yellow');
	M.pair('arm_r', [-9.4, 22.2, 3.6, -5.6, 24.4, 4.25], 'yellow');
	M.pair('arm_r', [-8.95, 16.7, -2.95, -3.05, 21.6, 2.95], 'navy', { rivet: 1.4, rivetR: 0.15 });
	M.pair('arm_r', [-8.55, 15.0, -2.55, -3.45, 16.8, 2.55], 'rubber');
	M.pair('arm_r', [-9.4, 10.2, -3.4, -2.6, 15.1, 3.4], 'gunmetal', { bands: [[1, 13.6, 14.3, 'yellow']], rivet: 1.5, rivetR: 0.15 });
	// legs: bellows knees, weighted boots with propeller pods and fins
	M.pair('leg_r', [-4.9, 7.0, -2.95, -0.12, 12.3, 2.95], 'navy', { rivet: 1.5, rivetR: 0.15 });
	M.pair('leg_r', [-4.5, 5.2, -2.55, -0.45, 7.1, 2.55], 'rubber');
	M.pair('leg_r', [-5.3, 0.9, -3.5, -0.12, 5.4, 3.3], 'gunmetal', { bands: [[1, 4.2, 4.9, 'yellow']] });
	M.pair('leg_r', [-5.55, 0, -4.7, -0.12, 1.15, 3.6], 'navyD', { rivet: 1.4, rivetR: 0.16 });
	M.pair('leg_r', [-4.6, 1.3, 3.3, -0.8, 5.0, 4.4], 'gunmetal', { prop: 'south' });
	M.pair('leg_r', [-6.0, 0.9, 0.6, -5.3, 4.4, 4.2], 'yellow');
	M.beam('fx_leg_r', 2, 4.4, 11, -2.7, 3.15, 2.2, 'fx_wash', 'min', { pair: true });
	return finish(M);
}

// ---------------------------------------------------------------- 06 Mark 5 (Stealth)
function buildMark5() {
	const M = rig('mark5');
	const acc = { lines: { 0: [0] } };
	M.add('head', [-4.5, 23.7, -4.5, 4.5, 32.25, 4.5], 'black', { lines: { 1: [30.4], 2: [1.6] } });
	M.add('head', [-3.9, 32.25, -3.9, 3.9, 32.65, 3.9], 'black');
	M.add('head', [-3.4, 24.2, -4.92, 3.4, 30.3, -4.5], 'blackD', { face: { eyeX: [0.7, 2.7], eyeY: [28.25, 28.75], slant: 0.16, topSlant: 0.1, glow: [170, 205, 240], dim: 0.62, mouth: 'slit', mouthW: 1.1, mouthY: 25.7, cheek: [2.9, 28.0, 1.8, 24.6] } });
	M.pair('head', [-4.85, 26.6, -0.9, -4.5, 28.4, 0.9], 'stealthAcc');
	M.add('root', [-4.5, 12.6, -2.5, 4.5, 24.3, 2.5], 'black', acc);
	M.add('root', [-4.2, 18.3, -2.95, 4.2, 23.85, -2.5], 'blackD', { lines: { 0: [0], 1: [20.9] } });
	M.add('root', [-1.3, 19.6, -3.25, 1.3, 22.2, -2.95], 'stealthAcc', { cut: 'disc', rim: 0.3, glow: [110, 160, 220], dim: 0.55, reactor: true });
	M.add('root', [-3.2, 13.6, -2.85, 3.2, 18.3, -2.5], 'black', { lines: { 1: [15.1, 16.7], 0: [0] } });
	M.add('root', [-4.7, 11.6, -2.7, 4.7, 13.2, 2.7], 'blackD', { bands: [[1, 12.25, 12.55, 'stealthAcc']] });
	M.add('root', [-3.4, 14.8, 2.5, 3.4, 22.8, 3.0], 'blackD', { lines: { 1: [18.8], 0: [0] } });
	M.pair('arm_r', [-8.8, 21.8, -2.8, -3.7, 24.75, 2.8], 'blackD', { bands: [[1, 21.8, 22.15, 'stealthAcc']] });
	M.pair('arm_r', [-8.4, 24.75, -2.4, -4.2, 25.15, 2.4], 'blackD');
	M.pair('arm_r', [-8.5, 15.8, -2.5, -3.5, 22.0, 2.5], 'black', { lines: { 1: [18.6] } });
	M.pair('arm_r', [-8.7, 10.9, -2.7, -3.3, 16.1, 2.7], 'blackD', { palm: 'down', glow: [120, 170, 220], dim: 0.6, bands: [[1, 14.9, 15.2, 'stealthAcc']] });
	M.pair('leg_r', [-4.5, 5.6, -2.5, -0.12, 12.4, 2.5], 'black', { lines: { 1: [9.2] } });
	M.pair('leg_r', [-4.2, 4.9, -2.9, -0.45, 7.2, -2.5], 'blackD');
	M.pair('leg_r', [-4.7, 0, -2.7, -0.12, 5.8, 2.7], 'blackD', { bands: [[1, 5.1, 5.4, 'stealthAcc']] });
	M.pair('leg_r', [-4.5, 0, -3.35, -0.35, 1.35, -2.7], 'black');
	M.beam('fx_leg_r', 1, -5.5, 0, -2, 0, 2.0, 'fx_dim', 'max', { pair: true });
	M.beam('fx_arm_r', 1, 5, 10.9, -6, 0, 1.8, 'fx_dim', 'max', { pair: true });
	return finish(M);
}

// ---------------------------------------------------------------- 07 Mark 6 (Silver Centurion)
function buildMark6() {
	const M = rig('mark6');
	const face = { eyeX: [0.55, 2.95], eyeY: [28.0, 28.85], slant: 0.42, topSlant: 0.05, innerDrop: 0.0, mouth: 'slit', mouthW: 1.2, mouthY: 25.7, cheek: [3.15, 27.6, 2.0, 24.5], brow: 29.5, ridge: true };
	classicHelmet(M, 'red', 'silver', face, { plate: [-3.6, 24.0, -5.12, 3.6, 30.85, -4.6], earMat: 'silver' });
	M.add('head', [-0.55, 30.6, -4.95, 0.55, 33.1, 4.2], 'silver');
	// torso: silver chest, red V-cut midsection, triangular beam
	const redV = (st, c, f, s, t, p) => { if (f === 'north' && p[1] < 16.6 + 0.7 * Math.abs(p[0])) { st.col = surface(MAT.red, c, f, s, t, p, { fw: 9, fh: 12, ePx: 1 }); if (Math.abs(p[1] - (16.6 + 0.7 * Math.abs(p[0]))) < 0.12) st.col = mul(st.col, 0.5); } };
	M.add('root', [-4.6, 12.6, -2.6, 4.6, 24.35, 2.6], 'silver', { paintFn: redV });
	M.add('root', [-4.3, 18.1, -3.1, 4.3, 23.9, -2.6], 'silver', { lines: { 0: [0] } });
	M.add('root', [-2.6, 18.5, -3.5, 2.6, 23.1, -3.1], 'silverD', { cut: 'tri', rim: 0.2, glow: G.uni });
	M.add('root', [-4.8, 11.4, -2.85, 4.8, 13.4, 2.85], 'red');
	M.add('root', [-3.7, 14.6, 2.6, 3.7, 23.2, 3.2], 'silver', { lines: { 1: [17.4, 20.2], 0: [0] } });
	// wide, flared, angular shoulders
	M.pair('arm_r', [-11.6, 23.0, -3.5, -3.8, 24.6, 3.5], 'red', { rot: [0, 0, -16], pivot: [-4, 24, 0], lines: { 0: [-7.4] } });
	M.pair('arm_r', [-10.8, 21.4, -3.2, -4.2, 22.9, 3.2], 'silver', { rot: [0, 0, -12], pivot: [-4.2, 22.6, 0] });
	M.pair('arm_r', [-9.0, 21.0, -2.9, -3.7, 24.9, 2.9], 'red');
	M.pair('arm_r', [-8.55, 15.8, -2.55, -3.45, 21.5, 2.55], 'silver', { lines: { 1: [18.6] } });
	M.pair('arm_r', [-8.8, 10.8, -2.8, -3.2, 15.9, 2.8], 'red', { palm: 'down', glow: G.rep });
	M.pair('arm_r', [-9.25, 15.2, -3.25, -2.85, 16.7, 3.25], 'red', { lines: { 1: [15.9] } });
	M.pair('leg_r', [-4.55, 5.6, -2.55, -0.12, 12.4, 2.55], 'silver', { lines: { 1: [9.0] } });
	M.pair('leg_r', [-4.25, 6.0, -3.0, -0.45, 8.6, -2.55], 'silverD');
	M.pair('leg_r', [-4.72, 0, -2.75, -0.12, 5.9, 2.75], 'red');
	M.pair('leg_r', [-5.0, 5.2, -3.05, -0.1, 6.5, 3.05], 'red', { rot: [8, 0, 0], pivot: [-2.5, 5.2, 0] });
	M.pair('leg_r', [-4.55, 0, -3.55, -0.35, 1.45, -2.75], 'red');
	M.beam('fx_chest', 2, -27, -3.5, 0, 20.6, 3.6, 'fx_uni', 'max', { wd: 3.4, halo: 1.6 });
	M.add('fx_chest', [-3.0, 18.2, -3.9, 3.0, 23.6, -3.6], 'fx_uni', { cut: 'tri' });
	return finish(M);
}

// ---------------------------------------------------------------- 08 Mark 7 (Space armour)
function buildMark7() {
	const M = rig('mark7');
	// reinforced helmet + gold visor
	M.add('head', [-5.0, 23.3, -5.0, 5.0, 32.6, 5.0], 'white', { bands: [[0, -1.0, 1.0, 'red']] });
	M.add('head', [-4.3, 32.6, -4.3, 4.3, 33.15, 4.3], 'white', { bands: [[0, -1.0, 1.0, 'red']] });
	M.add('head', [-4.4, 24.3, -5.35, 4.4, 31.3, -5.0], 'gold');
	M.add('head', [-3.75, 24.9, -5.62, 3.75, 30.7, -5.35], 'visorGold');
	M.pair('head', [-5.45, 25.8, -1.4, -5.0, 29.2, 1.4], 'red', { lens: 'west', glow: [255, 90, 70], dim: 0.6 });
	M.add('head', [-5.5, 22.5, -5.5, 5.5, 23.7, 5.5], 'gold', { lines: { 1: [23.1] } });
	// torso
	M.add('root', [-5.0, 12.4, -3.0, 5.0, 24.3, 3.0], 'white', { lines: { 0: [0] } });
	M.add('root', [-4.6, 17.4, -3.5, 4.6, 23.8, -3.0], 'red', { lines: { 0: [0] } });
	M.add('root', [-1.5, 19.0, -3.85, 1.5, 22.0, -3.5], 'gold', { cut: 'disc', rim: 0.25, glow: G.uni, reactor: true });
	M.add('root', [-4.2, 13.6, -3.35, 4.2, 17.4, -3.0], 'whiteD', { lines: { 1: [14.9, 16.2] } });
	M.add('root', [-5.25, 11.6, -3.25, 5.25, 13.4, 3.25], 'gold', { rivet: 1.4, rivetR: 0.16 });
	// thruster / oxygen pack
	M.add('root', [-4.6, 13.0, 3.0, 4.6, 25.0, 6.6], 'white', { bands: [[1, 20.6, 23.2, 'red']], lines: { 0: [0] }, vent: ['up'] });
	M.add('root', [-3.6, 14.2, 6.6, 3.6, 19.8, 7.1], 'whiteD', { vent: ['south'] });
	M.pair('root', [-6.2, 14.0, 3.4, -4.6, 23.6, 6.2], 'red', { bands: [[1, 14.0, 14.7, 'gold'], [1, 22.9, 23.6, 'gold']] });
	M.pair('root', [-3.8, 11.0, 3.6, -1.0, 13.0, 6.2], 'steel', { nozzle: 'down', glow: G.rep, dim: 0.7 });
	// arms: joint rings, heavy gauntlets
	M.pair('arm_r', [-9.5, 21.8, -3.5, -3.6, 25.2, 3.5], 'red', { lines: { 1: [23.4] } });
	M.pair('arm_r', [-9.0, 20.9, -3.05, -3.3, 21.85, 3.05], 'gold', { lines: { 1: [21.35] } });
	M.pair('arm_r', [-8.7, 16.6, -2.7, -3.3, 21.0, 2.7], 'white');
	M.pair('arm_r', [-8.95, 15.6, -2.95, -3.05, 16.65, 2.95], 'gold', { lines: { 1: [16.1] } });
	M.pair('arm_r', [-9.5, 10.3, -3.35, -2.6, 15.65, 3.35], 'red', { palm: 'down', glow: G.rep, lines: { 1: [12.0] }, bands: [[1, 14.6, 15.0, 'gold']] });
	// legs: knee rings, big boots with extra thrusters
	M.pair('leg_r', [-4.8, 6.8, -2.8, -0.12, 12.3, 2.8], 'white', { lines: { 1: [9.6] } });
	M.pair('leg_r', [-4.95, 5.8, -2.95, -0.12, 6.85, 2.95], 'gold', { lines: { 1: [6.3] } });
	M.pair('leg_r', [-5.05, 0, -3.25, -0.12, 5.85, 3.25], 'red', { bands: [[1, 4.9, 5.3, 'gold']] });
	M.pair('leg_r', [-4.85, 0, -4.2, -0.35, 1.6, -3.25], 'red');
	M.pair('leg_r', [-4.3, 0.6, 3.25, -0.9, 3.6, 4.6], 'steel', { nozzle: 'south', glow: G.rep, dim: 0.6 });
	M.beam('fx_leg_r', 1, -8, 0, -2.25, 0, 2.6, 'fx_jet', 'max', { pair: true });
	M.beam('fx_leg_r', 2, 4.6, 10, -2.6, 2.1, 1.6, 'fx_jet', 'min', { pair: true });
	M.beam('fx_back', 1, 2, 11, -2.4, 4.9, 2.2, 'fx_jet', 'max', { pair: true });
	return finish(M);
}

// ---------------------------------------------------------------- 09 Mark 8 (Hulkbuster, ~3 blocks)
// Same bone names, but its own proportions: shoulders at +-15, hips at +-5, a small head sunk into a huge torso.
function buildHulkbuster() {
	const M = rig('mark8_hulkbuster', { D: 6, TW: 2048, pivots: { head: [0, 38, -2], armR: [-15, 36, 0], legR: [-5, 20, 0], root: [0, 20, 0] } });
	// drop the default player under-cubes (the wearer sits inside the torso)
	for (const b of M.bones) b.cubes = [];
	// ---- head: small, low, forward
	M.add('head', [-3.6, 37.6, -6.4, 3.6, 45.0, 0.6], 'red', { lines: { 0: [0] } });
	M.add('head', [-3.0, 45.0, -5.8, 3.0, 45.5, 0.0], 'red');
	M.add('head', [-2.8, 37.9, -6.9, 2.8, 43.6, -6.4], 'gold', { face: { eyeX: [0.4, 2.2], eyeY: [41.75, 42.4], slant: 0.25, topSlant: 0.15, mouth: 'slit', mouthW: 0.9, mouthY: 39.3, cheek: [2.4, 41.5, 1.5, 38.2] } });
	// ---- torso
	M.add('root', [-11, 22, -6.2, 11, 38.5, 6.0], 'red', { lines: { 0: [0], 1: [30.0] } });
	M.add('root', [-10.2, 30.5, -7.2, 10.2, 38.2, -6.2], 'gold', { lines: { 0: [0], 1: [34.6] } });
	M.add('root', [-3.4, 31.2, -7.6, 3.4, 37.4, -7.2], 'silverD', { cut: 'tri', rim: 0.18, glow: G.uni });
	M.add('root', [-7.4, 23.4, -6.9, 7.4, 30.5, -6.2], 'silver', { lines: { 1: [25.4, 27.4, 29.2], 0: [0] } });
	M.add('root', [-9.6, 38.0, -5.0, 9.6, 41.4, 5.2], 'red', { vent: ['up'] });
	M.add('root', [-9.0, 26.0, 6.0, 9.0, 40.2, 9.4], 'silverD', { lines: { 0: [0], 1: [33] }, vent: ['south'] });
	M.pair('root', [-8.0, 30.0, 9.4, -3.0, 38.0, 10.2], 'steel', { nozzle: 'south', glow: G.rep, dim: 0.5 });
	M.add('root', [-8.8, 18.6, -5.2, 8.8, 23.0, 5.2], 'steel', { lines: { 0: [0] } });
	M.add('root', [-7.0, 17.6, -5.6, 7.0, 21.2, 4.6], 'gold');
	// ---- arms: huge shoulders, piston upper arm, red forearm, oversized fists
	M.pair('arm_r', [-21.0, 32.5, -7.0, -10.0, 41.5, 7.0], 'red', { lines: { 1: [36.8] } });
	M.pair('arm_r', [-21.5, 35.0, -5.0, -21.0, 40.5, 5.0], 'gold');
	M.pair('arm_r', [-20.2, 30.6, -6.2, -10.8, 32.6, 6.2], 'gold');
	M.pair('arm_r', [-18.4, 24.8, -4.4, -11.8, 30.8, 4.4], 'steel', { lines: { 1: [27.6] } });
	M.pair('arm_r', [-17.4, 23.0, -5.4, -16.0, 31.0, -4.4], 'chrome');
	M.pair('arm_r', [-14.2, 23.0, -5.4, -12.8, 31.0, -4.4], 'chrome');
	M.pair('arm_r', [-17.8, 27.0, -5.9, -12.4, 29.0, -5.3], 'silverD');
	M.pair('arm_r', [-21.0, 14.6, -6.4, -9.6, 25.6, 6.4], 'red', { lines: { 1: [18.6], 0: [-15.3] } });
	M.pair('arm_r', [-21.3, 21.4, -6.7, -9.3, 23.4, 6.7], 'gold');
	M.pair('arm_r', [-21.5, 6.6, -7.0, -9.1, 15.0, 7.0], 'gold', { lines: { 1: [11.6] }, palm: 'down', glow: G.rep, dim: 0.85 });
	for (let i = 0; i < 4; i++) M.pair('arm_r', [-20.9 + i * 3.0, 7.2, -7.8, -18.6 + i * 3.0, 12.0, -7.0], 'redD');
	M.pair('arm_r', [-22.3, 8.0, -4.4, -21.5, 12.8, 3.0], 'redD');
	// ---- legs: thick, piston-braced
	M.pair('leg_r', [-10.2, 10.6, -4.6, -0.6, 20.6, 4.6], 'silverD', { lines: { 1: [15.4] } });
	M.pair('leg_r', [-10.7, 12.4, -5.2, -0.3, 18.4, 5.0], 'red', { lines: { 0: [-5.4] } });
	M.pair('leg_r', [-6.9, 6.5, -6.0, -3.5, 16.0, -4.8], 'chrome');
	M.pair('leg_r', [-7.6, 9.8, -6.4, -2.8, 12.6, -5.0], 'gold');
	M.pair('leg_r', [-10.9, 2.6, -5.2, -0.25, 10.8, 5.2], 'red', { lines: { 1: [6.4] } });
	M.pair('leg_r', [-11.2, 0, -8.0, -0.2, 3.0, 5.6], 'silverD', { lines: { 0: [-7.6, -3.8] } });
	M.pair('leg_r', [-10.6, 0, -9.2, -0.6, 2.2, -8.0], 'gold');
	M.pair('leg_r', [-8.8, 3.6, 5.2, -2.4, 9.6, 6.6], 'steel', { vent: ['south'] });
	M.pair('leg_r', [-11.6, 4.0, -2.2, -10.9, 17.0, -0.8], 'chrome');
	// fx: the punch shockwave rings in front of the fist (arm local -Y = forward when punching)
	M.add('fx_arm_r', [-27.5, 3.6, -13.2, -3.1, 4.0, 13.2], 'fx_rep', { cut: 'ring', cutFace: 'down' });
	M.add('fx_arm_r', [-31.5, -2.4, -17.2, 0.9, -2.0, 17.2], 'fx_rep', { cut: 'ring', cutFace: 'down' });
	M.add('fx_arm_r', [-20.0, 6.0, -5.6, -10.6, 6.5, 5.6], 'fx_rep', { cut: 'disc', cutFace: 'down' });
	return finish(M);
}

// ================================================================ poses
const P = {
	stand: { arm_r: [0, 0, 5], arm_l: [0, 0, -5], leg_r: [0, 0, 1.5], leg_l: [0, 0, -1.5] },
	standWide: { arm_r: [0, 0, 9], arm_l: [0, 0, -9], leg_r: [0, 0, 3], leg_l: [0, 0, -3] },
};

// ================================================================ sheets
function noShadowFx(faces) { for (const f of faces) if (isFx(f.c)) f.noShadow = true; return faces; }

// my own sheet: 5 panels, shared scale for the turnaround, own scale for the hero pose, a tighter bloom.
function bloomPost(rad1, rad2, k1, k2) {
	return ctx => {
		const { W, H, col, emi, ss } = ctx;
		const e1 = Float32Array.from(emi), e2 = Float32Array.from(emi);
		for (let k = 0; k < 3; k++) L.boxBlur(e1, W, H, Math.round(rad1 * ss), 3);
		for (let k = 0; k < 3; k++) L.boxBlur(e2, W, H, Math.round(rad2 * ss), 3);
		for (let i = 0; i < W * H * 3; i++) col[i] += k1 * e1[i] + k2 * e2[i];
	};
}

function sheet(file, title, sub, panels, opts = {}) {
	const PH = opts.panelH || 1100, TITLE = 84, PAD = 0.075;
	const fits = panels.map(pn => { pn.faces = noShadowFx(L.gather(pn.items)); return L.projBounds(pn.faces.filter(f => !pn.fitIgnoreFx || !isFx(f.c)), pn.cam, pn.items.map(it => it.pos || [0, 0, 0])); });
	let ppu = Infinity;
	fits.forEach((b, i) => { if (!panels[i].ownScale) ppu = Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])); });
	const widths = fits.map((b, i) => {
		let k = ppu;
		if (panels[i].ownScale) k = Math.min(ppu * 1.05, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1]), (panels[i].maxW || 900) * 0.9 / (b.hi[0] - b.lo[0]));
		panels[i].ppu = k;
		return Math.max(opts.minPanelW || 380, Math.round((b.hi[0] - b.lo[0]) * k + PH * 2 * PAD * 0.8));
	});
	let total = widths.reduce((a, b) => a + b, 0);
	const minW = opts.minW || 1900;
	if (total < minW) { const ex = (minW - total) / widths.length; for (let i = 0; i < widths.length; i++) widths[i] = Math.round(widths[i] + ex); total = widths.reduce((a, b) => a + b, 0); }
	const cv = new L.Canvas(total, PH + TITLE, [34, 36, 42]);
	let x = 0;
	panels.forEach((pn, i) => {
		const b = fits[i], k = pn.ppu, w = widths[i];
		const cxw = (b.lo[0] + b.hi[0]) / 2, cyw = (b.lo[1] + b.hi[1]) / 2;
		const origin = [w / 2 - cxw * k, PH / 2 + cyw * k + 22];
		const floors = pn.items.map(it => ({ x: (it.pos || [0, 0, 0])[0], z: (it.pos || [0, 0, 0])[2], r: it.model.heightBlocks * 0.42 + 0.8 }));
		const bl = opts.bloom || [0.035, 0.11, 1.35, 1.0];
		const img = L.renderPanel({
			items: pn.items, faces: pn.faces, W: w, H: PH, cam: pn.cam, ppu: k, origin, ss: 2, floor: floors,
			bg: opts.bg || { top: [132, 136, 146], bottom: [82, 86, 96] }, bloom: 0,
			post: bloomPost(PH * bl[0] * (pn.ownScale ? 1 : 1), PH * bl[1], bl[2], bl[3]),
			aoR: 0.16, edgeThr: 0.06, shadowSoft: 0.14, contactH: 0.3,
		});
		cv.blit(img, w, PH, x, TITLE);
		if (i > 0) cv.rect(x, TITLE, 2, PH, [30, 32, 38]);
		cv.text(pn.label, x + w / 2, TITLE + 18, 3, [236, 240, 246], { align: 'center' });
		x += w;
	});
	cv.rect(0, 0, total, TITLE, [26, 28, 34]);
	cv.text(title, 28, 18, 5, [240, 244, 250]);
	if (sub) cv.text(sub, 28 + title.length * 30 + 28, 30, 3, [160, 190, 220]);
	if (opts.footer) cv.text(opts.footer, total - 24, TITLE + PH - 34, 2, [214, 220, 230], { align: 'right' });
	cv.save(file);
	console.log(`wrote ${file} (${total}x${PH + TITLE})`);
}

function render(M, file, title, sub, action, opts = {}) {
	const stand = opts.stand || P.stand;
	const pitch = opts.pitch == null ? 6 : opts.pitch;
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: pose.$root || planted(M, pose), hide: isFx }, extra);
	const panels = [
		{ label: 'FRONT', items: [it(stand)], cam: { yaw: 0, pitch } },
		{ label: 'THREE-QUARTER', items: [it(stand)], cam: { yaw: 35, pitch } },
		{ label: 'SIDE', items: [it(stand)], cam: { yaw: 90, pitch } },
		{ label: 'BACK', items: [it(stand)], cam: { yaw: 180, pitch } },
		{ label: action.label, items: [it(action.pose, { root: action.pose.$root || planted(M, action.pose, action.lift || 0), hide: action.hide || (c => isFx(c) && !(action.fx || []).includes(c.bone)) })], cam: { yaw: action.yaw == null ? 30 : action.yaw, pitch: action.pitch == null ? pitch : action.pitch }, ownScale: true, maxW: action.maxW || 1000, fitIgnoreFx: action.fitIgnoreFx !== false },
	];
	sheet(path.join(OUT, file), title, sub, panels, Object.assign({ footer: opts.footer }, opts.sheet || {}));
}

// ================================================================ main
const ARMOURS = [
	{
		n: 1, file: '01_mark1.png', build: buildMark1, title: 'MARK 1', sub: "TALES OF SUSPENSE 39 (1963) - GREY IRON 'BOX OF SCRAPS'",
		footer: 'RIVETED PLATE - WELD SEAMS - BUCKET HELM + GRILLE - BOX GAUNTLETS - HEEL JETS',
		action: { label: 'ACTION: IRON FIST', yaw: -58, pose: { root: [0, 12, 0], head: [0, -8, 0], arm_r: [-92, 0, 0], arm_l: [28, 0, -6], leg_r: [-26, 0, 2], leg_l: [22, 0, -2] } },
	},
	{
		n: 2, file: '02_mark1_v2.png', build: buildMark1v2, title: 'MARK 1 V2', sub: 'TALES OF SUSPENSE 40 - THE SAME SUIT, SLIMMED AND PAINTED GOLD',
		footer: 'POLISHED GOLD - FEWER RIVETS - SLEEKER BUCKET HELM - SMALL CHEST LIGHT',
		action: { label: 'ACTION: JET-BOOT LIFT-OFF', yaw: 28, lift: 7, fx: ['fx_leg_r', 'fx_leg_l'], pose: { head: [-12, 0, 0], arm_r: [0, 0, 30], arm_l: [0, 0, -30], leg_r: [6, 0, 4], leg_l: [-4, 0, -4] } },
	},
	{
		n: 3, file: '03_mark2.png', build: buildMark2, title: 'MARK 2', sub: 'TALES OF SUSPENSE 48 (DITKO, 1963) - THE FIRST RED AND GOLD',
		footer: 'RED SHELL - GOLD FACEPLATE + LIMBS - ROUND UNIBEAM - PALM REPULSORS',
		action: { label: 'ACTION: REPULSOR BLAST', yaw: -62, fx: ['fx_arm_r'], maxW: 1800, fitIgnoreFx: false, pose: { root: [0, 10, 0], head: [0, -10, 0], arm_r: [-90, 0, 0], arm_l: [-30, 0, -22], leg_r: [-14, 0, 2], leg_l: [16, 0, -2] } },
	},
	{
		n: 4, file: '04_mark3.png', build: buildMark3, title: 'MARK 3', sub: '1970S-80S CLASSIC - SEGMENTED RED AND GOLD',
		footer: 'TRIANGULAR UNIBEAM - LAYERED SHOULDERS - WRIST REPULSORS - SEGMENTED ABS - JAWED FACEPLATE',
		action: { label: 'ACTION: UNIBEAM', yaw: -38, fx: ['fx_chest'], maxW: 1600, fitIgnoreFx: false, pose: { root: [-6, 0, 0], head: [8, 0, 0], arm_r: [46, 0, 12], arm_l: [46, 0, -12], leg_r: [-10, 0, 6], leg_l: [12, 0, -6] } },
	},
	{
		n: 5, file: '05_mark4_hydro.png', build: buildMark4, title: 'MARK 4 - HYDRO ARMOUR', sub: 'IRON MAN 218 (1987) - DEEP-SEA',
		footer: 'PORTHOLE DOME + TWIN HEADLAMPS - BALLAST + AIR TANKS - RIBBED HOSES - PROPELLER BOOTS',
		action: { label: 'ACTION: SEA-FLOOR CHARGE', yaw: 68, pitch: 8, fx: ['fx_leg_r', 'fx_leg_l'], lift: 1, maxW: 1150, pose: { root: [16, 0, 0], head: [-14, 0, 0], arm_r: [-70, 0, 8], arm_l: [-40, 0, -10], leg_r: [-30, 0, 2], leg_l: [26, 0, -2] } },
	},
	{
		n: 6, file: '06_mark5_stealth.png', build: buildMark5, title: 'MARK 5 - STEALTH ARMOUR', sub: 'IRON MAN 152 (1981)',
		footer: 'MATTE BLACK - DARK PANEL LINES - NO RIVETS - DIM BLUE-WHITE EYES + LOW-GLOW EMITTER',
		action: { label: 'ACTION: SILENT HOVER', yaw: 32, lift: 9, fx: ['fx_leg_r', 'fx_leg_l', 'fx_arm_r', 'fx_arm_l'], pose: { head: [-10, -10, 0], arm_r: [-8, 0, 22], arm_l: [-8, 0, -22], leg_r: [4, 0, 3], leg_l: [-10, 0, -3] } },
	},
	{
		n: 7, file: '07_mark6_silver_centurion.png', build: buildMark6, title: 'MARK 6 - SILVER CENTURION', sub: 'IRON MAN 200 (1985)',
		footer: 'SILVER + RED - FLARED ANGULAR SHOULDERS - TRIANGULAR CHEST BEAM - ANGULAR EYES',
		action: { label: 'ACTION: CHEST BEAM', yaw: -38, fx: ['fx_chest'], maxW: 1600, fitIgnoreFx: false, pose: { root: [-5, 0, 0], arm_r: [44, 0, 14], arm_l: [44, 0, -14], leg_r: [0, 0, 9], leg_l: [0, 0, -9] } },
	},
	{
		n: 8, file: '08_mark7_space.png', build: buildMark7, title: 'MARK 7 - SPACE ARMOUR', sub: 'IRON MAN 142 (1981)',
		footer: 'PRESSURISED WHITE/RED PLATE - GOLD VISOR + TRIM - THRUSTER PACK - BOOT THRUSTERS - JOINT RINGS',
		action: { label: 'ACTION: ORBITAL FLIGHT', yaw: 58, pitch: 2, lift: 4, fx: ['fx_leg_r', 'fx_leg_l', 'fx_back'], maxW: 1150, pose: { root: [48, 0, 0], head: [-34, 0, 0], arm_r: [-150, 0, 10], arm_l: [14, 0, -16], leg_r: [10, 0, 3], leg_l: [-6, 0, -3] } },
	},
	{
		n: 9, file: '09_mark8_hulkbuster.png', build: buildHulkbuster, title: 'MARK 8 - HULKBUSTER', sub: 'IRON MAN 304 STYLE - MODULAR EXOSUIT, ABOUT 3 BLOCKS, WORN AROUND THE PLAYER',
		footer: 'RED / GOLD / SILVER - OVERSIZED FISTS - TRIANGULAR EMITTER - PISTON LEGS - EXPOSED HYDRAULICS',
		stand: { arm_r: [0, 0, 7], arm_l: [0, 0, -7], leg_r: [0, 0, 2], leg_l: [0, 0, -2] },
		action: { label: 'ACTION: HULKBUSTER PUNCH', yaw: -58, maxW: 1400, fitIgnoreFx: false, fx: ['fx_arm_r'], pose: { root: [10, 14, 0], head: [-8, -10, 0], arm_r: [-94, 0, 0], arm_l: [30, 0, -14], leg_r: [-22, 0, 2], leg_l: [18, 0, -2] } },
	},
];

fs.mkdirSync(OUT, { recursive: true });
const built = {};
for (const A of ARMOURS) {
	if (!WANT(A.n) && !WANT('lineup')) continue;
	const t0 = Date.now();
	const M = A.build();
	built[A.n] = M;
	if (A.n === 9) M.heightBlocks = 3;
	console.log(`${M.name}: ${M.cubes().length} cubes, tex ${M.TW}x${M.TH} (${Date.now() - t0} ms)`);
	if (WANT(A.n)) render(M, A.file, A.title, A.sub, A.action, { footer: A.footer, stand: A.stand });
}

// ---------------------------------------------------------------- lineup
function silhouette() {
	const M = new Model('player', { TW: 256, D: 1 });
	const r = M.bone('root', null, [0, 0, 0]);
	M.box(r, -4, 0, -2, -0.05, 12, 2, 'sil'); M.box(r, 0.05, 0, -2, 4, 12, 2, 'sil');
	M.box(r, -4, 12, -2, 4, 24, 2, 'sil'); M.box(r, -8, 12, -2, -4.05, 24, 2, 'sil'); M.box(r, 4.05, 12, -2, 8, 24, 2, 'sil');
	M.box(r, -4, 24, -4, 4, 32, 4, 'sil');
	M.heightBlocks = 2; M.layout(); M.paint(() => [[60, 64, 72], 255]);
	return M;
}
if (WANT('lineup')) {
	const row = [{ M: silhouette(), name: 'PLAYER', h: '2 BLOCKS', w: 1.2, sil: true }];
	const names = ['MARK 1', 'MARK 1 V2', 'MARK 2', 'MARK 3', 'MARK 4 HYDRO', 'MARK 5 STEALTH', 'MARK 6 CENTURION', 'MARK 7 SPACE', 'MARK 8 HULKBUSTER'];
	for (let i = 1; i <= 9; i++) { const M = built[i]; const b = M.bounds(M.world(P.stand), c => !isFx(c)); row.push({ M, name: names[i - 1], h: ((b.hi[1] - b.lo[1]) / 16).toFixed(1) + ' BLOCKS', w: (b.hi[0] - b.lo[0]) / 16 + 0.05 }); }
	let x = 0; const items = [];
	for (const r of row) { x += r.w / 2; r.x = x; items.push({ model: r.M, pose: r.sil ? {} : (r.M.name.includes('hulk') ? ARMOURS[8].stand : P.stand), root: r.sil ? [0, 0, 0] : planted(r.M, P.stand), pos: [x, 0, 0], hide: isFx, silhouette: r.sil ? [56, 60, 70] : null }); x += r.w / 2 + 0.55; }
	const total = x - 0.55;
	const H = 1100, top = 84, ppu = 270;
	const cam = { yaw: 0, pitch: 4 };
	const Wpx = Math.round(total * ppu + 160);
	const origin = [80, H - 140];
	const img = L.renderPanel({ items, faces: L.gather(items), W: Wpx, H, cam, ppu, origin, ss: 2, floor: [{ x: total / 2, z: 0, r: total * 0.62, k: 0.4 }], bg: { top: [132, 136, 146], bottom: [82, 86, 96] }, bloom: 0, post: bloomPost(H * 0.02, H * 0.06, 1.3, 1.0), aoR: 0.16, edgeThr: 0.06, shadowSoft: 0.14, contactH: 0.3 });
	const cv = new L.Canvas(Wpx, H + top, [26, 28, 34]);
	cv.blit(img, Wpx, H, 0, top);
	cv.rect(0, 0, Wpx, top, [26, 28, 34]);
	cv.text('IRON MAN - COMIC ARMOURS', 28, 18, 5, [240, 244, 250]);
	cv.text('LINEUP AT TRUE SCALE - RULER IN BLOCKS', 28 + 24 * 30 + 28, 30, 3, [160, 190, 220]);
	for (let b = 0; b <= 3; b++) { const yy = top + origin[1] - Math.round(b * ppu * Math.cos(cam.pitch * Math.PI / 180)); cv.rect(0, yy, 22, 2, [230, 236, 244]); if (b) cv.text(String(b), 28, yy - 20, 2, [230, 236, 244]); }
	for (const r of row) {
		const sx = origin[0] + r.x * ppu;
		cv.text(r.name, sx, top + H - 100, 2, [240, 244, 250], { align: 'center' });
		cv.text(r.h, sx, top + H - 74, 2, [160, 196, 236], { align: 'center' });
	}
	cv.save(path.join(OUT, '00_lineup.png'));
	console.log('wrote 00_lineup.png');
}
