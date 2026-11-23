// v0.17.7 Ankhatep, the Bound King (Ancient Temple boss): GeckoLib model, two textures (bound / unbound) with their glow
// masks, and every animation clip. Authored at full size in model pixels (16 px = 1 block): ~53 px = 3.3 blocks.
//   - geo/ankhatep.geo.json: a gilded death-mask under a lapis-and-gold striped nemes with a white atef-style crown and
//     a gold uraeus, a braided false beard, a broad bead collar and gold pectoral over grave wrappings, a pleated kilt
//     with a striped apron, a tattered lapis royal cloak, gold arm rings and bracers, sandals, loose bandage strips,
//     a crook-headed ebony staff with a soul gem (right hand), a green curse flame (left hand), shackles on both wrists
//     with chains to the floor (chain_r / chain_l; broken stubs broken_r / broken_l once he tears them apart).
//   - textures at 2 texels per model pixel, one atlas rect per face (detailed, nothing stretched).
//   - animations: idle_bound / walk_bound / idle / walk + sand_wave, lance, summon, curse, strike, break_chains,
//     stagger, death -- contact frames match AnkhatepBoss's tick constants (checked at the end).
// Usage (repo root): node scratchpad/v0177/gen_ankhatep_v0177.js
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const ROOT = path.resolve(__dirname, '..', '..');
const A = path.join(ROOT, 'src/main/resources/assets/projecthero');
const json = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 1) + '\n'); };

let seed = 31337;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const jit = (c, n) => { const d = (rnd() - 0.5) * n; return c.map(v => clamp(v + d)); };

// ================================================================ the model
const bones = [];
const bone = (name, parent, pivot) => { const b = { name, parent, pivot, cubes: [] }; bones.push(b); return b; };
const cube = (b, from, size, mat, opts = {}) => { b.cubes.push({ from, size, mat, ...opts }); };
const M = side => (a, w) => side > 0 ? a : -a - w; // mirror a min-x for width w

const root = bone('root', null, [0, 0, 0]);
// ---- legs (wrapped), gold sandals
for (const side of [1, -1]) {
	const n = side > 0 ? 'right' : 'left';
	const lo = M(side);
	const leg = bone(`${n}_leg`, 'root', [side * 2.6, 20, 0]);
	cube(leg, [lo(0.6, 4), 10, -2], [4, 10, 4], 'wrap');                  // thigh
	cube(leg, [lo(0.8, 3.6), 1.5, -1.8], [3.6, 8.5, 3.6], 'wrap');        // shin
	cube(leg, [lo(0.5, 4.2), 7.5, -2.1], [4.2, 1.2, 4.2], 'gold');        // knee band
	cube(leg, [lo(0.4, 4.4), 0, -3.2], [4.4, 1.5, 5.4], 'sandal');        // sandal
	cube(leg, [lo(1.2, 1), 5, -2.05], [1, 5, 0.3], 'strip');              // a loose strip
}
// ---- hips: kilt, belt, apron
const hips = bone('hips', 'root', [0, 21, 0]);
cube(hips, [-5.6, 13.5, -3.2], [11.2, 8, 6.4], 'linen');                // pleated kilt
cube(hips, [-5.8, 20, -3.4], [11.6, 2, 6.8], 'gold');                   // belt
cube(hips, [-1.8, 9.5, -3.7], [3.6, 10.5, 0.6], 'apron');               // striped apron
cube(hips, [-0.8, 19.6, -3.9], [1.6, 1.6, 0.4], 'gem');                 // belt jewel
// ---- torso: wrapped chest, collar, pectoral, cloak
const torso = bone('torso', 'hips', [0, 22, 0]);
cube(torso, [-4.6, 21.5, -2.6], [9.2, 11, 5.2], 'wrap');                // chest
cube(torso, [-5.2, 26, -2.9], [10.4, 1.2, 5.8], 'wrapdark');            // a band of darker wraps
cube(torso, [-6.4, 30.5, -3.6], [12.8, 2.6, 7.2], 'collar');            // broad collar (usekh)
cube(torso, [-5.2, 28, -3.7], [10.4, 2.6, 0.5], 'collar');              // its front fall
cube(torso, [-1.6, 25.4, -3.4], [3.2, 2.6, 0.6], 'pectoral');           // gold scarab pectoral
cube(torso, [-0.6, 26.1, -3.75], [1.2, 1.2, 0.4], 'gem');
const cloak = bone('cloak', 'torso', [0, 33, 3]);
cube(cloak, [-6, 9, 3.2], [12, 24, 0.6], 'cloak');
cube(cloak, [-6.6, 31.5, 2.8], [13.2, 1.5, 1], 'gold');                 // the clasp bar
// ---- head: neck, death mask, nemes, crown, uraeus, beard
const head = bone('head', 'torso', [0, 33, 0]);
cube(head, [-1.4, 32.5, -1.4], [2.8, 2.5, 2.8], 'wrap');                // neck
cube(head, [-3.6, 35, -3.8], [7.2, 8, 7.2], 'mask');                    // the face / mask
cube(head, [-4.3, 41.5, -4.3], [8.6, 2.2, 8.6], 'nemes');               // nemes crown band
cube(head, [-4.4, 34.5, -3.4], [1.2, 7, 6.2], 'nemes');                 // nemes sides
cube(head, [3.2, 34.5, -3.4], [1.2, 7, 6.2], 'nemes');
cube(head, [-5.2, 28.5, -3.6], [2, 7.5, 1.4], 'nemes');                 // the lappets falling on the chest
cube(head, [3.2, 28.5, -3.6], [2, 7.5, 1.4], 'nemes');
cube(head, [-2.8, 30, 3.2], [5.6, 6.5, 1], 'nemes');                    // the queue at the back
cube(head, [-0.8, 30.5, -4.4], [1.6, 4.5, 1.4], 'beard');               // false beard
const crown = bone('crown', 'head', [0, 43.5, 0]);
cube(crown, [-2.6, 43.5, -2.6], [5.2, 4.5, 5.2], 'crown');
cube(crown, [-1.8, 48, -1.8], [3.6, 3, 3.6], 'crown');
cube(crown, [-0.9, 51, -0.9], [1.8, 1.4, 1.8], 'gold');
cube(crown, [-3.6, 44, -0.5], [1, 6, 1], 'gold');                       // the plumes' gold edges
cube(crown, [2.6, 44, -0.5], [1, 6, 1], 'gold');
cube(crown, [-0.6, 42.6, -4.9], [1.2, 2.6, 1], 'uraeus');               // the cobra
cube(crown, [-0.4, 44.6, -4.6], [0.8, 0.8, 0.6], 'gem');
// ---- arms
for (const side of [1, -1]) {
	const n = side > 0 ? 'right' : 'left';
	const lo = M(side);
	const arm = bone(`${n}_arm`, 'torso', [side * 6, 31, 0]);
	cube(arm, [lo(4.4, 4.2), 28, -2.2], [4.2, 4.2, 4.4], 'collar');       // shoulder beads
	cube(arm, [lo(4.8, 3.4), 23.5, -1.7], [3.4, 7, 3.4], 'wrap');         // upper arm
	cube(arm, [lo(4.6, 3.8), 26, -1.9], [3.8, 1.1, 3.8], 'gold');         // arm ring
	cube(arm, [lo(6.1, 0.8), 19, -1.2], [0.8, 6, 0.3], 'strip');
	const fore = bone(`${n}_forearm`, `${n}_arm`, [side * 6.5, 23.5, 0]);
	cube(fore, [lo(4.9, 3.2), 17.8, -1.6], [3.2, 6, 3.2], 'wrap');        // forearm
	cube(fore, [lo(4.6, 3.8), 18.2, -1.9], [3.8, 2.6, 3.8], 'cuff');      // the shackle
	cube(fore, [lo(4.9, 3.2), 15.6, -1.6], [3.2, 2.4, 3.2], 'hand');      // hand
	// the chain from the shackle to the floor, hanging a little outwards
	const ch = bone(`chain_${n[0]}`, `${n}_forearm`, [side * 6.5, 18.6, 0]);
	for (let k = 0; k < 9; k++) {
		const y = 16.2 - k * 2.0, x = side * (6.6 + k * 0.42);
		const wide = k % 2 === 0;
		cube(ch, [x - (wide ? 0.6 : 0.25), y, wide ? -0.25 : -0.6], [wide ? 1.2 : 0.5, 2.2, wide ? 0.5 : 1.2], 'iron');
	}
	const br = bone(`broken_${n[0]}`, `${n}_forearm`, [side * 6.5, 18.6, 0]);
	for (let k = 0; k < 3; k++) {
		const y = 16.2 - k * 2.0, x = side * (6.8 + k * 0.6);
		const wide = k % 2 === 0;
		cube(br, [x - (wide ? 0.6 : 0.25), y, wide ? -0.25 : -0.6], [wide ? 1.2 : 0.5, 2.2, wide ? 0.5 : 1.2], 'iron');
	}
}
// the staff (right hand), crook head with a soul gem
const staff = bone('staff', 'right_forearm', [6.5, 16.8, 0]);
cube(staff, [6, -2, -0.6], [1.2, 36, 1.2], 'staff');
for (const y of [6, 14, 22, 30]) cube(staff, [5.8, y, -0.8], [1.6, 1, 1.6], 'gold');
cube(staff, [5.5, 34, -1.1], [2.2, 2.2, 2.2], 'gold');                  // the head's collar
cube(staff, [5.6, 36, -1.2], [2.4, 2.4, 2.4], 'gem');                   // the soul gem
cube(staff, [6, 38.4, -0.6], [1.2, 3.2, 1.2], 'gold');                  // the crook
cube(staff, [6, 40.6, -3.4], [1.2, 1.2, 3], 'gold');
cube(staff, [6, 38.4, -3.6], [1.2, 2.4, 1.2], 'gold');
// the curse flame (left hand)
const flame = bone('curse_flame', 'left_forearm', [-6.5, 14.5, -0.5]);
cube(flame, [-7.7, 12.4, -1.7], [2.4, 2.4, 2.4], 'flame');
cube(flame, [-7.2, 14.4, -1.2], [1.4, 1.6, 1.4], 'flame');

// ---- atlas: one rect per face at D texels per model pixel
const DENS = 2;
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
const faceDims = (s, f) => (f === 'north' || f === 'south') ? [s[0], s[1]] : (f === 'east' || f === 'west') ? [s[2], s[1]] : [s[0], s[2]];
const rects = [];
for (const b of bones) for (const c of b.cubes) {
	c.uv = {};
	for (const f of FACES) {
		const [w, h] = faceDims(c.size, f).map(v => Math.max(1, Math.round(v * DENS)));
		const r = { c, f, w, h, bone: b.name };
		c.uv[f] = r;
		rects.push(r);
	}
}
const TW = 256;
let TH;
{
	const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
	let x = 0, y = 0, rowH = 0;
	for (const r of order) {
		if (x + r.w > TW) { x = 0; y += rowH; rowH = 0; }
		r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
	}
	TH = Math.ceil((y + rowH) / 16) * 16;
}

// ---- painting
const GOLD = [226, 180, 58], GOLD_D = [148, 102, 26], GOLD_L = [255, 236, 150];
const LAPIS = [36, 62, 156], LAPIS_D = [18, 30, 86];
const WRAP = [196, 180, 144], WRAP_D = [128, 112, 84], ROT = [56, 44, 36];
const LINEN = [234, 228, 210], LINEN_D = [184, 174, 150];
const CARN = [176, 52, 40], TURQ = [64, 182, 170];
const SOUL = [120, 255, 140], SOUL_HOT = [220, 255, 220];
const EBONY = [42, 30, 24];
const IRON = [64, 62, 68], RUST = [120, 70, 40];
function painter(unbound) {
	const tex = Buffer.alloc(TW * TH * 4);
	const glow = Buffer.alloc(TW * TH * 4);
	const put = (buf, x, y, c, a = 255) => { const i = (y * TW + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = a; };
	seed = 31337;
	for (const r of rects) {
		const { c, f, w, h, u, v } = r;
		const side = f !== 'up' && f !== 'down';
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
			let col = [255, 0, 255], a = 255, g = null;
			switch (c.mat) {
				case 'wrap': case 'wrapdark': {
					const band = Math.floor((y + x * 0.45) / 3);
					col = mix(WRAP, WRAP_D, (band % 2 ? 0.28 : 0.05) + rnd() * 0.2 + (c.mat === 'wrapdark' ? 0.35 : 0));
					if (Math.floor(y + x * 0.45) % 3 === 0) col = mix(col, WRAP_D, 0.45);   // the edge of each turn
					if (rnd() < 0.035) col = ROT;                                         // rot showing through
					if (unbound && side && rnd() < 0.06) { col = mix(SOUL, [40, 120, 60], 0.3); g = col; } // the curse runes awake
					if (unbound && side && rnd() < 0.05) col = ROT;                       // torn
					break;
				}
				case 'strip': col = mix(WRAP, WRAP_D, 0.3 + rnd() * 0.3); if (y > h - 3 && rnd() < 0.6) a = 0; break;
				case 'gold': col = mix(GOLD, GOLD_D, rnd() * 0.3 + (edge ? 0.3 : 0)); if (y % 3 === 0 && side) col = mix(col, GOLD_L, 0.4); if (rnd() < 0.04) col = GOLD_L; break;
				case 'cuff': col = (y % 3 === 1) ? mix(IRON, [30, 30, 34], 0.4) : mix(GOLD, GOLD_D, 0.3 + rnd() * 0.3); if (rnd() < 0.05) col = RUST; break;
				case 'iron': col = mix(IRON, [30, 30, 34], rnd() * 0.5); if (rnd() < 0.15) col = RUST; if (edge) col = mix(col, [120, 118, 126], 0.35); break;
				case 'linen': col = mix(LINEN, LINEN_D, (x % 3 === 0 && (f === 'north' || f === 'south')) ? 0.55 : rnd() * 0.2); if (y > h - 2) col = mix(col, WRAP_D, 0.3); break;
				case 'apron': { const s = Math.floor(x / 2) % 3; col = [GOLD, LAPIS, CARN][s]; if (y % 4 === 0) col = GOLD_D; if (y > h - 3) col = GOLD; break; }
				case 'collar': { const row = Math.floor(y / 2) % 6; col = [GOLD, LAPIS, CARN, GOLD, TURQ, LAPIS_D][row]; if ((x + Math.floor(y / 2)) % 2 === 0) col = mix(col, [255, 255, 255], 0.12); if (row === 2 && x % 4 === 0) g = [255, 120, 90]; break; }
				case 'pectoral': col = mix(GOLD, GOLD_D, rnd() * 0.3); if (f === 'north' && x >= 1 && x < w - 1 && y >= 1 && y < h - 1) col = (x + y) % 2 ? [60, 160, 120] : TURQ; break;
				case 'gem': col = mix(SOUL_HOT, SOUL, rnd() * 0.6); g = col; break;
				case 'flame': col = mix(SOUL_HOT, SOUL, rnd()); g = col; if (rnd() < 0.2) a = 0; break;
				case 'mask': {
					col = mix(GOLD, GOLD_D, rnd() * 0.25 + (edge ? 0.25 : 0));
					if (f === 'north') {
						// the death mask's face, w x h = 14 x 16 texels
						const cx = (w - 1) / 2;
						const eyeRow = Math.round(h * 0.38);
						for (const ex of [cx - 3.5, cx + 3.5]) {
							const dx = x - ex, dy = y - eyeRow;
							if (Math.abs(dx) <= 2.5 && Math.abs(dy) <= 1) col = [20, 18, 30];           // kohl-lined sockets
							if (Math.abs(dx) <= 1 && dy === 0) { col = unbound ? SOUL_HOT : SOUL; g = col; } // the green fire inside
							if (dy === -2 && Math.abs(dx) <= 2.5) col = LAPIS_D;                          // brows
							if (dy === 2 && dx > 0.5 && dx <= 3.5 && ex > cx) col = LAPIS_D;               // the kohl tail
							if (dy === 2 && dx < -0.5 && dx >= -3.5 && ex < cx) col = LAPIS_D;
						}
						if (Math.abs(x - cx) <= 0.5 && y > eyeRow && y < eyeRow + 4) col = mix(GOLD_D, GOLD, 0.3); // nose ridge
						if (y === eyeRow + 5 && Math.abs(x - cx) <= 1.5) col = [120, 40, 30];                   // lips
						if (unbound && x > cx + 1 && y > eyeRow + 1 && (x - y) % 4 === 0) col = [40, 30, 26];   // cracks in phase 2
					}
					break;
				}
				case 'nemes': { const st = Math.floor(((f === 'up' || f === 'down') ? x : y) / 2) % 2; col = st ? mix(LAPIS, LAPIS_D, rnd() * 0.3) : mix(GOLD, GOLD_D, rnd() * 0.3); break; }
				case 'beard': col = Math.floor(y / 2) % 2 ? LAPIS : GOLD; break;
				case 'crown': { col = mix([238, 232, 218], [200, 192, 176], rnd() * 0.3); if (Math.floor(x / 2) % 4 === 0 && side) col = mix(GOLD, GOLD_D, 0.3); if (y === h - 1) col = GOLD_D; break; }
				case 'uraeus': col = mix(GOLD, GOLD_D, rnd() * 0.3); if (f === 'north' && y === 1) col = CARN; break;
				case 'hand': col = mix([94, 74, 58], [60, 46, 36], rnd() * 0.4); if (y % 3 === 0) col = mix(col, WRAP, 0.4); break;
				case 'sandal': col = (y === 0 && side) ? GOLD : mix([110, 72, 40], [70, 44, 24], rnd() * 0.4); if (f === 'up') col = mix(GOLD, GOLD_D, rnd() * 0.3); break;
				case 'staff': col = mix(EBONY, [20, 14, 12], rnd() * 0.4); if (y % 12 === 0) col = [70, 50, 36]; if (unbound && side && y % 7 === 3) { col = SOUL; g = SOUL; } break;
				case 'cloak': {
					col = mix(LAPIS_D, [10, 16, 50], rnd() * 0.4);
					if (side) {
						if (x <= 1 || x >= w - 2) col = mix(GOLD, GOLD_D, 0.3);                      // gold border
						if ((x * 7 + y * 13) % 41 === 0) col = GOLD_L;                                // stars
						const ragged = unbound ? 9 : 5;
						if (y >= h - ragged && rnd() < (y - (h - ragged) + 1) / (ragged + 1)) a = 0;   // a torn hem
						if (unbound && rnd() < 0.02) a = 0;
					}
					break;
				}
			}
			put(tex, u + x, v + y, col, a);
			if (g && a) put(glow, u + x, v + y, g);
		}
	}
	return { tex, glow };
}
fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
for (const [name, unbound] of [['ankhatep', false], ['ankhatep_unbound', true]]) {
	const { tex, glow } = painter(unbound);
	fs.writeFileSync(path.join(A, 'textures/entity', name + '.png'), encode(TW, TH, tex));
	fs.writeFileSync(path.join(A, 'textures/entity', name + '_glowmask.png'), encode(TW, TH, glow));
}

// ---- geo json
const round = n => Math.round(n * 1000) / 1000;
{
	const seen = new Set();
	for (const b of bones) {
		if (b.parent && !seen.has(b.parent)) throw new Error(`bone ${b.name}: parent ${b.parent} not declared first`);
		seen.add(b.name);
	}
}
json(path.join(A, 'geo/ankhatep.geo.json'), {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.ankhatep', texture_width: TW, texture_height: TH, visible_bounds_width: 4, visible_bounds_height: 5, visible_bounds_offset: [0, 2, 0] },
		bones: bones.map(b => {
			const out = { name: b.name };
			if (b.parent) out.parent = b.parent;
			out.pivot = b.pivot.map(round);
			if (b.cubes.length) out.cubes = b.cubes.map(c => {
				const uv = {};
				for (const f of FACES) { const r = c.uv[f]; uv[f] = { uv: [r.u, r.v], uv_size: [r.w, r.h] }; }
				return { origin: c.from.map(round), size: c.size.map(round), uv };
			});
			return out;
		}),
	}],
});
console.log(`geo: ${bones.length} bones, ${bones.reduce((n, b) => n + b.cubes.length, 0)} cubes, texture ${TW}x${TH}`);

// ================================================================ animations
// Convention (as the mod's other clips): positive X tips a bone's top forward, so for things hanging from their pivot
// (arms, legs) a NEGATIVE angle swings the free end forward. The staff hangs from the right hand, pointing up.
const REST = {
	root: [0, 0, 0], hips: [0, 0, 0], torso: [0, 0, 0], head: [0, 0, 0], crown: [0, 0, 0], cloak: [6, 0, 0],
	right_arm: [-10, 0, 4], right_forearm: [-30, 0, 0], staff: [30, 0, 0],
	left_arm: [-6, 0, -6], left_forearm: [-36, 0, 0], curse_flame: [0, 0, 0],
	right_leg: [0, 0, 0], left_leg: [0, 0, 0], flame_s: [1, 1, 1],
};
const DERIVED = ['chain_r', 'chain_l', 'broken_r', 'broken_l'];
const TRACKS = [...Object.keys(REST), ...DERIVED];
const pose = (o = {}) => {
	const p = {};
	for (const k of TRACKS) p[k] = o[k] !== undefined ? (typeof o[k] === 'number' ? [o[k], 0, 0] : o[k]) : REST[k];
	if (typeof o.flame_s === 'number') p.flame_s = [o.flame_s, o.flame_s, o.flame_s];
	// the chains (and their broken stubs) always hang straight down, whatever the arm does
	for (const [ch, arm, fore] of [['chain_r', 'right_arm', 'right_forearm'], ['chain_l', 'left_arm', 'left_forearm'], ['broken_r', 'right_arm', 'right_forearm'], ['broken_l', 'left_arm', 'left_forearm']]) {
		p[ch] = [-(p[arm][0] + p[fore][0]) - p.torso[0], 0, -p[arm][2]];
	}
	return p;
};
const R = pose();
const animations = {};
const kf = entries => { const out = {}; for (const [t, v, e] of entries) { const r = v.map(n => Math.round(n * 1000) / 1000); out[t.toFixed(2)] = e ? { vector: r, easing: e } : r; } return out; };
function clip(name, length, loop, frames) {
	const bonesOut = {};
	for (const track of TRACKS) {
		const entries = frames.map(([t, p, e]) => [t, p[track], e]);
		const def = track.endsWith('_s') ? 1 : 0;
		if (entries.every(([, v]) => v.every((n, i) => n === entries[0][1][i])) && entries[0][1].every(n => n === def) && !(track in REST && REST[track].some(n => n !== def)) && !DERIVED.includes(track)) continue;
		const boneName = track === 'flame_s' ? 'curse_flame' : track;
		const channel = track === 'root' ? 'position' : track.endsWith('_s') ? 'scale' : 'rotation';
		bonesOut[boneName] = bonesOut[boneName] || {};
		bonesOut[boneName][channel] = kf(entries);
	}
	const a = { animation_length: length, bones: bonesOut };
	if (loop === true) a.loop = true; else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	animations['animation.ankhatep.' + name] = a;
}
const sec = ticks => ticks / 20;

// ---- locomotion. Bound: hunched under the chains, arms low and heavy; unbound: upright, regal, the staff planted
const BOUND = { torso: 10, head: -6, right_arm: [-4, 0, 8], left_arm: [-2, 0, -8], right_forearm: -20, left_forearm: -24, staff: 20 };
clip('idle_bound', 4.0, true, [
	[0, pose(BOUND)],
	[2.0, pose({ ...BOUND, torso: 13, head: -9, right_arm: [-2, 0, 10], left_arm: [0, 0, -10], root: [0, -0.4, 0], flame_s: 1.3 }), 'easeinoutsine'],
	[4.0, pose(BOUND), 'easeinoutsine'],
]);
clip('walk_bound', 2.0, true, [
	[0, pose({ ...BOUND, right_leg: -14, left_leg: 14 })],
	[0.5, pose({ ...BOUND, root: [0, 0.5, 0] })],
	[1.0, pose({ ...BOUND, right_leg: 14, left_leg: -14 })],
	[1.5, pose({ ...BOUND, root: [0, 0.5, 0] })],
	[2.0, pose({ ...BOUND, right_leg: -14, left_leg: 14 })],
]);
clip('idle', 3.0, true, [
	[0, R],
	[1.5, pose({ torso: -2, head: 3, cloak: 10, left_forearm: -44, root: [0, 0.5, 0], flame_s: 1.4 }), 'easeinoutsine'],
	[3.0, R, 'easeinoutsine'],
]);
clip('walk', 1.4, true, [
	[0, pose({ right_leg: -22, left_leg: 22, left_arm: [14, 0, -6], cloak: 14 })],
	[0.35, pose({ root: [0, 0.8, 0], cloak: 18 })],
	[0.7, pose({ right_leg: 22, left_leg: -22, left_arm: [-20, 0, -6], cloak: 14 })],
	[1.05, pose({ root: [0, 0.8, 0], cloak: 18 })],
	[1.4, pose({ right_leg: -22, left_leg: 22, left_arm: [14, 0, -6], cloak: 14 })],
]);

// ---- attacks (lengths / contact frames = AnkhatepBoss constants)
const T = { sand_wave: [48, 24], lance: [34, 16], summon: [40, 30], curse: [30, 20], strike: [18, 10], break_chains: [50, 30] };
// Sand Wave: the staff raised high over his head, then slammed down at 1.2 s
clip('sand_wave', sec(T.sand_wave[0]), false, [
	[0, R],
	[0.5, pose({ torso: -10, head: -8, right_arm: [-170, 0, 10], right_forearm: -10, staff: 10, left_arm: [-150, 0, -14], left_forearm: -10, cloak: 16, flame_s: 1.5 }), 'easeoutquad'],
	[sec(T.sand_wave[1]) - 0.1, pose({ torso: -14, head: -10, right_arm: [-178, 0, 6], right_forearm: -4, staff: 4, left_arm: [-160, 0, -10], left_forearm: -4, cloak: 20, root: [0, 1.2, 0], flame_s: 1.8 })],
	[sec(T.sand_wave[1]), pose({ torso: 26, head: 10, right_arm: [-40, 0, 6], right_forearm: -20, staff: 64, left_arm: [-30, 0, -10], left_forearm: -20, cloak: 2, root: [0, -2, 0] }), 'easeinquad'],
	[sec(T.sand_wave[1]) + 0.6, pose({ torso: 20, head: 6, right_arm: [-34, 0, 6], right_forearm: -20, staff: 60, root: [0, -1.5, 0] })],
	[sec(T.sand_wave[0]), R, 'easeinoutsine'],
]);
// Sand Lances: the staff swept low and thrust forward at 0.8 s
clip('lance', sec(T.lance[0]), false, [
	[0, R],
	[sec(T.lance[1]) - 0.15, pose({ torso: -6, right_arm: [20, 30, 20], right_forearm: -40, staff: 70, left_arm: [-40, 0, -20], left_forearm: -50, flame_s: 1.6 }), 'easeoutquad'],
	[sec(T.lance[1]), pose({ torso: 18, head: 6, right_arm: [-80, -10, 4], right_forearm: -6, staff: 90, left_arm: [-20, 0, -10], left_forearm: -30 }), 'easeinquad'],
	[sec(T.lance[0]), R, 'easeinoutsine'],
]);
// Raise the Guardians: both arms up, palms raised, held, then down
clip('summon', sec(T.summon[0]), false, [
	[0, R],
	[0.6, pose({ torso: -12, head: -14, right_arm: [-140, 0, 30], right_forearm: -20, staff: 20, left_arm: [-140, 0, -30], left_forearm: -20, cloak: 18, flame_s: 2.0 }), 'easeoutquad'],
	[sec(T.summon[1]), pose({ torso: -16, head: -18, right_arm: [-160, 0, 36], right_forearm: -10, staff: 12, left_arm: [-160, 0, -36], left_forearm: -10, cloak: 22, root: [0, 0.8, 0], flame_s: 2.4 })],
	[sec(T.summon[0]), R, 'easeinoutsine'],
]);
// Curse: the left hand thrust out with the green flame, the head tilted down
clip('curse', sec(T.curse[0]), false, [
	[0, R],
	[sec(T.curse[1]) - 0.3, pose({ torso: -4, head: 8, left_arm: [-30, 30, -20], left_forearm: -60, flame_s: 1.4 }), 'easeoutquad'],
	[sec(T.curse[1]), pose({ torso: 8, head: 12, left_arm: [-95, 10, -4], left_forearm: -4, flame_s: 2.6 }), 'easeinquad'],
	[sec(T.curse[0]), R, 'easeinoutsine'],
]);
// Staff strike: a backhand swing at 0.5 s
clip('strike', sec(T.strike[0]), false, [
	[0, R],
	[sec(T.strike[1]) - 0.15, pose({ torso: -6, right_arm: [-120, 40, 40], right_forearm: -20, staff: 20 }), 'easeoutquad'],
	[sec(T.strike[1]), pose({ torso: 14, right_arm: [-60, -40, -10], right_forearm: -10, staff: 80 }), 'easeinquad'],
	[sec(T.strike[0]), R, 'easeinoutsine'],
]);
// Breaking the chains: hunched, straining, arms pulled down and in; the snap at 1.5 s flings them wide; a roar
clip('break_chains', sec(T.break_chains[0]), false, [
	[0, pose(BOUND)],
	[0.6, pose({ ...BOUND, torso: 24, head: 14, right_arm: [10, 0, 20], left_arm: [10, 0, -20], right_forearm: -60, left_forearm: -60, root: [0, -1.5, 0] }), 'easeoutquad'],
	[sec(T.break_chains[1]) - 0.05, pose({ ...BOUND, torso: 30, head: 18, right_arm: [16, 0, 26], left_arm: [16, 0, -26], right_forearm: -70, left_forearm: -70, root: [0, -2.2, 0], flame_s: 2.0 })],
	[sec(T.break_chains[1]) + 0.1, pose({ torso: -22, head: -26, right_arm: [-60, 0, 90], left_arm: [-60, 0, -90], right_forearm: -6, left_forearm: -6, staff: 40, cloak: 30, root: [0, 1.0, 0], flame_s: 3.0 }), 'easeoutquad'],
	[sec(T.break_chains[0]) - 0.3, pose({ torso: -18, head: -22, right_arm: [-50, 0, 80], left_arm: [-50, 0, -80], right_forearm: -10, left_forearm: -10, staff: 40, cloak: 24 })],
	[sec(T.break_chains[0]), R, 'easeinoutsine'],
]);
// Stagger (the jars are broken): down on one knee, a hand on the floor, 5 s
clip('stagger', 5.0, false, [
	[0, R],
	[0.4, pose({ torso: 34, head: 20, right_leg: -80, left_leg: 10, right_arm: [-20, 0, 10], left_arm: [-50, 0, -20], left_forearm: -10, root: [0, -5, 0] }), 'easeoutbounce'],
	[4.4, pose({ torso: 30, head: 16, right_leg: -80, left_leg: 10, right_arm: [-20, 0, 10], left_arm: [-50, 0, -20], left_forearm: -10, root: [0, -5, 0] })],
	[5.0, R, 'easeinoutsine'],
]);
// Death: sinks to his knees, the staff falls, he crumbles forward into sand (3 s, the entity bursts at the end)
clip('death', 3.0, 'hold', [
	[0, R],
	[0.8, pose({ torso: 20, head: 20, right_leg: -90, left_leg: -90, right_arm: [10, 0, 20], left_arm: [10, 0, -20], staff: 100, root: [0, -8, 0] }), 'easeoutquad'],
	[2.2, pose({ torso: 50, head: 40, right_leg: -90, left_leg: -90, right_arm: [30, 0, 30], left_arm: [30, 0, -30], staff: 110, cloak: 40, root: [0, -9, 0] })],
	[3.0, pose({ torso: 80, head: 50, right_leg: -90, left_leg: -90, right_arm: [40, 0, 40], left_arm: [40, 0, -40], staff: 120, cloak: 60, root: [0, -12, 0] })],
]);
json(path.join(A, 'animations/ankhatep.animation.json'), { format_version: '1.8.0', animations });
// root positions are already in model px (no scale)
const BOSS = fs.readFileSync(path.join(ROOT, 'src/main/java/com/projecthero/mod/temple/entity/AnkhatepBoss.java'), 'utf8');
const constant = n => +(BOSS.match(new RegExp(`int ${n} = (\\d+);`)) || [])[1];
const checks = [['WAVE_WINDUP', T.sand_wave[1]], ['LANCE_WINDUP', T.lance[1]], ['SUMMON_WINDUP', T.summon[1]], ['CURSE_WINDUP', T.curse[1]], ['STRIKE_WINDUP', T.strike[1]],
	['CHAINS_SNAP', T.break_chains[1]], ['CHAINS_TICKS', T.break_chains[0]]];
for (const [n, v] of checks) if (constant(n) !== v) throw new Error(`${n} = ${constant(n)} in AnkhatepBoss but the clip uses ${v}`);
console.log(`animations: ${Object.keys(animations).length} clips, timings match AnkhatepBoss`);
