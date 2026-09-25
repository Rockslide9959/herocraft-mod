// Generates the Titan Shifter assets (v0.12.31): geo model, animations, normal + hardened textures, serum icon.
// Original generic Titan design (muscular humanoid, loincloth, exposed-muscle striations) -- not any copyrighted character.
const fs = require('fs');
const zlib = require('zlib');
const ROOT = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/src/main/resources/assets/projecthero/';

// ---------------- PNG ----------------
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0); const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0); return Buffer.concat([len, td, crc]); };
function encode(w, h, data) {
	const stride = w * 4;
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
	const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ---------------- deterministic noise ----------------
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2246822519) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

// ---------------- model ----------------
// cube: [name, [x,y,z] origin, [w,h,d] size, material]
const TEXEL = 3; // model pixels per texel
const bones = [];
function bone(name, parent, pivot, cubes) { bones.push({ name, parent, pivot, cubes: cubes || [] }); }
const C = (name, o, s, mat, extra) => ({ name, origin: o, size: s, mat, ...(extra || {}) });

bone('root', null, [0, 0, 0]);
for (const side of [-1, 1]) {
	const n = side < 0 ? 'right' : 'left';
	const x = side * 16;
	bone(n + '_leg', 'root', [x, 78, 0], [C(n + '_thigh', [x - 13, 44, -14], [26, 34, 28], 'skin')]);
	bone(n + '_shin', n + '_leg', [x, 44, 0], [C(n + '_calf', [x - 11, 8, -12], [22, 36, 24], 'skin')]);
	bone(n + '_foot', n + '_shin', [x, 8, -2], [C(n + '_foot', [x - 13, 0, -34], [26, 8, 42], 'skin', { toes: true })]);
}
bone('waist', 'root', [0, 78, 0], [
	C('hips', [-32, 72, -18], [64, 14, 36], 'skin'),
	C('cloth_front', [-30, 56, -21], [60, 24, 4], 'cloth'),
	C('cloth_back', [-30, 56, 17], [60, 24, 4], 'cloth'),
	C('cloth_belt', [-33, 78, -19], [66, 5, 38], 'belt'),
]);
bone('torso', 'waist', [0, 86, 0], [
	C('abdomen', [-28, 86, -17], [56, 20, 34], 'skin', { abs: true }),
	C('chest', [-34, 104, -19], [68, 32, 38], 'skin'),
	C('pec_right', [-32, 108, -24], [30, 20, 6], 'skin', { pec: true }),
	C('pec_left', [2, 108, -24], [30, 20, 6], 'skin', { pec: true }),
	C('traps', [-24, 134, -14], [48, 9, 28], 'skin'),
	C('back', [-30, 108, 19], [60, 24, 5], 'skin'),
]);
bone('head', 'torso', [0, 138, 0], [
	C('skull', [-16, 138, -16], [32, 32, 32], 'skin', { face: true }),
	C('jaw', [-13, 133, -19], [26, 8, 10], 'skin'),
	C('brow', [-16, 156, -19], [32, 5, 3], 'skin'),
	C('nose', [-4, 146, -20], [8, 10, 4], 'skin'),
	C('ear_right', [-19, 146, -3], [3, 10, 8], 'skin'),
	C('ear_left', [16, 146, -3], [3, 10, 8], 'skin'),
	C('hair_top', [-17, 168, -17], [34, 7, 34], 'hair'),
	C('hair_back', [-17, 140, 15], [34, 30, 3], 'hair'),
]);
for (const side of [-1, 1]) {
	const n = side < 0 ? 'right' : 'left';
	const x = side * 44;
	bone(n + '_arm', 'torso', [x, 130, 0], [
		C(n + '_shoulder', [x - 12, 120, -13], [24, 18, 26], 'skin'),
		C(n + '_bicep', [x - 10, 98, -11], [20, 32, 22], 'skin'),
	]);
	bone(n + '_forearm', n + '_arm', [x, 98, 0], [C(n + '_forearm', [x - 9, 66, -10], [18, 32, 20], 'skin')]);
	bone(n + '_hand', n + '_forearm', [x, 66, 0], [C(n + '_fist', [x - 10, 50, -13], [20, 16, 26], 'skin', { fist: true })]);
}
bone('steam_right', 'torso', [-44, 142, 0]);
bone('steam_left', 'torso', [44, 142, 0]);

// ---------------- atlas packing ----------------
const FACES = ['north', 'east', 'south', 'west', 'up', 'down'];
function faceDims(size, face) {
	const [w, h, d] = size;
	if (face === 'north' || face === 'south') return [w, h];
	if (face === 'east' || face === 'west') return [d, h];
	return [w, d];
}
const rects = [];
for (const b of bones) for (const c of b.cubes) for (const f of FACES) {
	const [fw, fh] = faceDims(c.size, f);
	rects.push({ cube: c, face: f, tw: Math.ceil(fw / TEXEL) + 1, th: Math.ceil(fh / TEXEL) + 1, fw, fh });
}
function pack(W) {
	const sorted = [...rects].sort((a, b) => b.th - a.th || b.tw - a.tw);
	let x = 0, y = 0, rowH = 0;
	for (const r of sorted) {
		if (x + r.tw > W) { x = 0; y += rowH; rowH = 0; }
		r.u = x; r.v = y; x += r.tw; rowH = Math.max(rowH, r.th);
	}
	return y + rowH;
}
let W = 128, H;
for (; ; W *= 2) { H = pack(W); if (H <= W) break; }
// round H up to a power of two
let TH = 1; while (TH < H) TH *= 2;
console.log('atlas', W, 'x', TH, 'faces', rects.length);

// ---------------- texture painting ----------------
const PALETTES = {
	normal: {
		skin: [196, 152, 128], skinDark: [150, 100, 84], muscle: [176, 96, 84],
		hair: [58, 42, 34], cloth: [112, 84, 58], clothDark: [80, 58, 40], belt: [70, 52, 38],
		eye: [255, 224, 120], eyeSock: [40, 20, 20], tooth: [236, 228, 210], mouth: [70, 22, 26], nail: [220, 200, 178],
	},
	hardened: {
		skin: [150, 190, 226], skinDark: [92, 130, 186], muscle: [190, 226, 250],
		hair: [70, 96, 150], cloth: [120, 150, 200], clothDark: [80, 108, 160], belt: [60, 84, 130],
		eye: [120, 255, 255], eyeSock: [20, 50, 90], tooth: [240, 250, 255], mouth: [30, 60, 110], nail: [230, 244, 255],
	},
};
function paint(kind) {
	const P = PALETTES[kind];
	const px = Buffer.alloc(W * TH * 4);
	const put = (x, y, c, a = 255) => { if (x < 0 || y < 0 || x >= W || y >= TH) return; const i = (y * W + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = a; };
	let seed = 1;
	for (const r of rects) {
		const c = r.cube;
		const w = r.tw, h = r.th;
		seed++;
		for (let ty = 0; ty < h; ty++) for (let tx = 0; tx < w; tx++) {
			let base;
			const n = hash(tx, ty, seed);
			const front = r.face === 'north', back = r.face === 'south';
			if (c.mat === 'skin') {
				base = P.skin.slice();
				const shade = (n - 0.5) * 16;
				base = base.map((v) => v + shade);
				if (kind === 'normal') {
					// muscle striations: darker vertical fibres, redder on the big muscle faces
					const fibre = hash(Math.floor(tx / 2), 7, seed) < 0.22;
					if ((front || back || r.face === 'east' || r.face === 'west') && fibre) base = base.map((v, i) => v * 0.86 + P.muscle[i] * 0.14);
					if (ty > h - 3 && (front || back)) base = base.map((v) => v * 0.93);
				} else {
					// crystal facets: light diagonal seams
					if ((tx + ty * 2 + seed) % 7 === 0) base = P.muscle.slice();
					if ((tx * 2 - ty + seed) % 11 === 0) base = P.skinDark.slice();
				}
				if (c.pec && (front) && ty > h * 0.72) base = base.map((v, i) => v * 0.8 + P.skinDark[i] * 0.2);
				if (c.abs && front) { if (ty % 5 === 4 || tx === Math.floor(w / 2)) base = base.map((v, i) => v * 0.72 + P.skinDark[i] * 0.28); }
				if (c.fist && (front) && ty > h * 0.4 && ty < h * 0.6 && tx % 5 === 2) base = P.skinDark.slice();
				if (c.toes && front && ty < h * 0.5 && tx % 4 === 3) base = P.skinDark.slice();
				if (c.toes && front && ty < 3 && tx % 4 !== 3) base = P.nail.slice();
			} else if (c.mat === 'hair') {
				base = P.hair.map((v) => v + (n - 0.5) * 22);
				if (hash(tx, Math.floor(ty / 3), seed + 9) < 0.2) base = base.map((v) => v * 0.7);
			} else if (c.mat === 'cloth') {
				base = P.cloth.map((v) => v + (n - 0.5) * 12);
				if (tx % 6 === 0) base = P.clothDark.slice();
				if (ty > h - 3) base = P.clothDark.slice();
			} else {
				base = P.belt.map((v) => v + (n - 0.5) * 10);
			}
			put(r.u + tx, r.v + ty, base);
		}
		// face details on the skull's front
		if (c.face && r.face === 'north') {
			const w0 = r.tw - 1, h0 = r.th - 1;
			const eyeY = Math.floor(h0 * 0.42);
			for (const ex of [Math.floor(w0 * 0.22), Math.floor(w0 * 0.62)]) {
				for (let dy = -1; dy <= 1; dy++) for (let dx = 0; dx < 3; dx++) put(r.u + ex + dx, r.v + eyeY + dy, P.eyeSock);
				put(r.u + ex + 1, r.v + eyeY, P.eye); put(r.u + ex + 2, r.v + eyeY, P.eye);
			}
			// wide, toothy grin across the lower face
			const my = Math.floor(h0 * 0.82);
			for (let dx = 2; dx < w0 - 2; dx++) { put(r.u + dx, r.v + my, P.mouth); put(r.u + dx, r.v + my - 1, dx % 2 ? P.tooth : P.mouth); }
		}
	}
	return encode(W, TH, px);
}

// ---------------- geo json ----------------
function geo() {
	const bonesJson = bones.map((b) => {
		const o = { name: b.name };
		if (b.parent) o.parent = b.parent;
		o.pivot = b.pivot;
		if (b.cubes.length) {
			o.cubes = b.cubes.map((c) => {
				const uv = {};
				for (const f of FACES) {
					const r = rects.find((q) => q.cube === c && q.face === f);
					uv[f] = { uv: [r.u, r.v], uv_size: [Math.round(r.fw / TEXEL * 100) / 100, Math.round(r.fh / TEXEL * 100) / 100] };
				}
				return { origin: c.origin, size: c.size, uv };
			});
		}
		return o;
	});
	return {
		format_version: '1.12.0',
		'minecraft:geometry': [{
			description: { identifier: 'geometry.titan_form', texture_width: W, texture_height: TH, visible_bounds_width: 10, visible_bounds_height: 14, visible_bounds_offset: [0, 5.5, 0] },
			bones: bonesJson,
		}],
	};
}

// ---------------- animations ----------------
// Convention (Blockbench/GeckoLib): negative X swings a hanging limb FORWARD, positive X swings it back / pitches the head down.
const A = {};
function anim(name, length, loop, boneFrames) {
	const bonesOut = {};
	for (const [bn, chans] of Object.entries(boneFrames)) {
		bonesOut[bn] = {};
		for (const [ch, frames] of Object.entries(chans)) {
			const o = {};
			for (const [t, v] of frames) o[t.toFixed(2)] = v;
			bonesOut[bn][ch] = o;
		}
	}
	const a = { animation_length: length, bones: bonesOut };
	if (loop === true) a.loop = true; else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	A['animation.titan.' + name] = a;
}

// idle -- slow breathing
anim('idle', 4, true, {
	root: { position: [[0, [0, 0, 0]], [2, [0, -0.8, 0]], [4, [0, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [2, [2.2, 0, 0]], [4, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [2, [1.012, 1.012, 1.012]], [4, [1, 1, 1]]] },
	head: { rotation: [[0, [0, 0, 0]], [2, [-1.5, 1.5, 0]], [4, [0, 0, 0]]] },
	right_arm: { rotation: [[0, [2, 0, 0]], [2, [5, 0, 0]], [4, [2, 0, 0]]] },
	left_arm: { rotation: [[0, [2, 0, 0]], [2, [5, 0, 0]], [4, [2, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [2, [-9, 0, 0]], [4, [-6, 0, 0]]] },
	left_forearm: { rotation: [[0, [-6, 0, 0]], [2, [-9, 0, 0]], [4, [-6, 0, 0]]] },
});

function gait(name, len, legSwing, shinBend, armSwing, elbow, lean, bob, twist) {
	const h = len / 2, q = len / 4;
	const legR = [[0, [-legSwing, 0, 0]], [q, [0, 0, 0]], [h, [legSwing, 0, 0]], [h + q, [0, 0, 0]], [len, [-legSwing, 0, 0]]];
	const legL = [[0, [legSwing, 0, 0]], [q, [0, 0, 0]], [h, [-legSwing, 0, 0]], [h + q, [0, 0, 0]], [len, [legSwing, 0, 0]]];
	// note: the shin folds while the leg swings forward (from +swing back position toward -swing front)
	const shinRfix = [[0, [6, 0, 0]], [q, [6, 0, 0]], [h, [10, 0, 0]], [h + q, [shinBend, 0, 0]], [len, [6, 0, 0]]];
	const shinLfix = [[0, [10, 0, 0]], [q, [shinBend, 0, 0]], [h, [6, 0, 0]], [h + q, [6, 0, 0]], [len, [10, 0, 0]]];
	const armR = [[0, [armSwing, 0, 0]], [h, [-armSwing, 0, 0]], [len, [armSwing, 0, 0]]];
	const armL = [[0, [-armSwing, 0, 0]], [h, [armSwing, 0, 0]], [len, [-armSwing, 0, 0]]];
	const elbR = [[0, [-elbow * 0.3, 0, 0]], [h, [-elbow, 0, 0]], [len, [-elbow * 0.3, 0, 0]]];
	anim(name, len, true, {
		root: { position: [[0, [0, -bob, 0]], [q, [0, bob * 0.5, 0]], [h, [0, -bob, 0]], [h + q, [0, bob * 0.5, 0]], [len, [0, -bob, 0]]] },
		right_leg: { rotation: legR }, left_leg: { rotation: legL },
		right_shin: { rotation: shinRfix }, left_shin: { rotation: shinLfix },
		right_arm: { rotation: armR }, left_arm: { rotation: armL },
		right_forearm: { rotation: elbR }, left_forearm: { rotation: [[0, [-elbow, 0, 0]], [h, [-elbow * 0.3, 0, 0]], [len, [-elbow, 0, 0]]] },
		torso: { rotation: [[0, [lean, twist, 0]], [h, [lean, -twist, 0]], [len, [lean, twist, 0]]] },
		head: { rotation: [[0, [-lean * 0.5, -twist * 0.6, 0]], [h, [-lean * 0.5, twist * 0.6, 0]], [len, [-lean * 0.5, -twist * 0.6, 0]]] },
	});
}
gait('walk', 1.2, 24, 42, 18, 16, 4, 1.6, 4);
gait('run', 0.8, 40, 70, 42, 62, 13, 3.2, 6);

// punch (hit lands at 0.3 s)
anim('punch', 0.6, false, {
	right_arm: { rotation: [[0, [0, 0, 0]], [0.15, [55, 0, 0]], [0.3, [-98, 0, 0]], [0.45, [-72, 0, 0]], [0.6, [0, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [0.15, [-80, 0, 0]], [0.3, [-4, 0, 0]], [0.6, [-6, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.15, [-30, 0, 0]], [0.6, [0, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.15, [-6, -22, 0]], [0.3, [12, 24, 0]], [0.6, [0, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.3, [-4, 10, 0]], [0.6, [0, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.3, [0, -2, -3]], [0.6, [0, 0, 0]]] },
});
// kick (hit at 0.35 s)
anim('kick', 0.7, false, {
	right_leg: { rotation: [[0, [0, 0, 0]], [0.2, [38, 0, 0]], [0.35, [-88, 0, 0]], [0.5, [-62, 0, 0]], [0.7, [0, 0, 0]]] },
	right_shin: { rotation: [[0, [6, 0, 0]], [0.2, [55, 0, 0]], [0.35, [0, 0, 0]], [0.7, [6, 0, 0]]] },
	right_foot: { rotation: [[0, [0, 0, 0]], [0.35, [-25, 0, 0]], [0.7, [0, 0, 0]]] },
	left_leg: { rotation: [[0, [0, 0, 0]], [0.35, [8, 0, 0]], [0.7, [0, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.2, [8, 0, 0]], [0.35, [-14, 0, 0]], [0.7, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.35, [-35, 0, 0]], [0.7, [0, 0, 0]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [0.35, [30, 0, 0]], [0.7, [0, 0, 0]]] },
});
// heavy punch -- the combo finisher (hit at 0.45 s): overhead swing
anim('heavy_punch', 0.9, false, {
	right_arm: { rotation: [[0, [0, 0, 0]], [0.25, [-165, 0, 0]], [0.45, [-92, 0, 0]], [0.6, [-70, 0, 0]], [0.9, [0, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [0.25, [-40, 0, 0]], [0.45, [-4, 0, 0]], [0.9, [-6, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.25, [-40, 0, 0]], [0.9, [0, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.25, [-14, -10, 0]], [0.45, [24, 14, 0]], [0.9, [0, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.45, [10, 0, 0]], [0.9, [0, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.25, [0, -3, 3]], [0.45, [0, -6, -5]], [0.9, [0, 0, 0]]] },
});
// heavy smash -- both fists overhead, then a crushing slam (impact at 1.0 s)
anim('smash', 1.5, false, {
	right_arm: { rotation: [[0, [0, 0, 0]], [0.5, [-120, 0, 0]], [0.85, [-172, 0, 0]], [1.0, [-95, 0, 0]], [1.15, [-45, 0, 0]], [1.5, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.5, [-120, 0, 0]], [0.85, [-172, 0, 0]], [1.0, [-95, 0, 0]], [1.15, [-45, 0, 0]], [1.5, [0, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [0.85, [-30, 0, 0]], [1.0, [-4, 0, 0]], [1.5, [-6, 0, 0]]] },
	left_forearm: { rotation: [[0, [-6, 0, 0]], [0.85, [-30, 0, 0]], [1.0, [-4, 0, 0]], [1.5, [-6, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.85, [-16, 0, 0]], [1.0, [30, 0, 0]], [1.5, [0, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.85, [-14, 0, 0]], [1.0, [14, 0, 0]], [1.5, [0, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.85, [0, -7, 4]], [1.0, [0, -11, -6]], [1.5, [0, 0, 0]]] },
	right_leg: { rotation: [[0, [0, 0, 0]], [0.85, [-14, 0, 0]], [1.0, [-22, 0, 0]], [1.5, [0, 0, 0]]] },
	left_leg: { rotation: [[0, [0, 0, 0]], [0.85, [-14, 0, 0]], [1.0, [-22, 0, 0]], [1.5, [0, 0, 0]]] },
	right_shin: { rotation: [[0, [6, 0, 0]], [0.85, [28, 0, 0]], [1.0, [40, 0, 0]], [1.5, [6, 0, 0]]] },
	left_shin: { rotation: [[0, [6, 0, 0]], [0.85, [28, 0, 0]], [1.0, [40, 0, 0]], [1.5, [6, 0, 0]]] },
});
// stomp (impact at 0.5 s)
anim('stomp', 0.9, false, {
	right_leg: { rotation: [[0, [0, 0, 0]], [0.3, [-72, 0, 0]], [0.5, [2, 0, 0]], [0.9, [0, 0, 0]]] },
	right_shin: { rotation: [[0, [6, 0, 0]], [0.3, [78, 0, 0]], [0.5, [4, 0, 0]], [0.9, [6, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.3, [-6, 0, 0]], [0.5, [10, 0, 0]], [0.9, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.3, [-30, 0, 0]], [0.5, [10, 0, 0]], [0.9, [0, 0, 0]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [0.3, [-30, 0, 0]], [0.5, [10, 0, 0]], [0.9, [0, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.3, [0, 1, 0]], [0.5, [0, -6, 0]], [0.9, [0, 0, 0]]] },
});
// leap (launch at 0.3 s)
anim('leap', 1.4, false, {
	root: { position: [[0, [0, 0, 0]], [0.3, [0, -16, 2]], [0.5, [0, 2, 0]], [1.4, [0, 0, 0]]] },
	right_leg: { rotation: [[0, [0, 0, 0]], [0.3, [-42, 0, 0]], [0.5, [12, 0, 0]], [0.9, [-18, 0, 0]], [1.4, [0, 0, 0]]] },
	left_leg: { rotation: [[0, [0, 0, 0]], [0.3, [-42, 0, 0]], [0.5, [12, 0, 0]], [0.9, [-8, 0, 0]], [1.4, [0, 0, 0]]] },
	right_shin: { rotation: [[0, [6, 0, 0]], [0.3, [78, 0, 0]], [0.5, [8, 0, 0]], [0.9, [40, 0, 0]], [1.4, [6, 0, 0]]] },
	left_shin: { rotation: [[0, [6, 0, 0]], [0.3, [78, 0, 0]], [0.5, [8, 0, 0]], [0.9, [30, 0, 0]], [1.4, [6, 0, 0]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [0.3, [40, 0, 0]], [0.55, [-125, 0, 0]], [1.1, [-70, 0, 0]], [1.4, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.3, [40, 0, 0]], [0.55, [-125, 0, 0]], [1.1, [-70, 0, 0]], [1.4, [0, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.3, [22, 0, 0]], [0.55, [-8, 0, 0]], [1.4, [0, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.3, [10, 0, 0]], [0.55, [-12, 0, 0]], [1.4, [0, 0, 0]]] },
});
// landing
anim('landing', 0.7, false, {
	root: { position: [[0, [0, -14, 0]], [0.15, [0, -17, 0]], [0.7, [0, 0, 0]]] },
	right_leg: { rotation: [[0, [-38, 0, 0]], [0.15, [-42, 0, 0]], [0.7, [0, 0, 0]]] },
	left_leg: { rotation: [[0, [-38, 0, 0]], [0.15, [-42, 0, 0]], [0.7, [0, 0, 0]]] },
	right_shin: { rotation: [[0, [70, 0, 0]], [0.15, [80, 0, 0]], [0.7, [6, 0, 0]]] },
	left_shin: { rotation: [[0, [70, 0, 0]], [0.15, [80, 0, 0]], [0.7, [6, 0, 0]]] },
	torso: { rotation: [[0, [24, 0, 0]], [0.15, [28, 0, 0]], [0.7, [0, 0, 0]]] },
	right_arm: { rotation: [[0, [-20, 0, 0]], [0.15, [25, 0, 0]], [0.7, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [-20, 0, 0]], [0.15, [25, 0, 0]], [0.7, [0, 0, 0]]] },
});
// roar (sound at 0.5 s)
const roarShake = []; for (let i = 0; i <= 12; i++) roarShake.push([0.5 + i * 0.05, [-30, (i % 2 ? 3 : -3), 0]]);
anim('roar', 1.6, false, {
	head: { rotation: [[0, [0, 0, 0]], [0.4, [-32, 0, 0]], ...roarShake, [1.6, [0, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.4, [-16, 0, 0]], [1.15, [-13, 0, 0]], [1.6, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [0.5, [1.05, 1.05, 1.05]], [1.1, [1.03, 1.03, 1.03]], [1.6, [1, 1, 1]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [0.4, [28, 0, 0]], [1.15, [28, 0, 0]], [1.6, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.4, [28, 0, 0]], [1.15, [28, 0, 0]], [1.6, [0, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [0.4, [-40, 0, 0]], [1.15, [-40, 0, 0]], [1.6, [-6, 0, 0]]] },
	left_forearm: { rotation: [[0, [-6, 0, 0]], [0.4, [-40, 0, 0]], [1.15, [-40, 0, 0]], [1.6, [-6, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.4, [0, -2, 2]], [1.6, [0, 0, 0]]] },
});
// hurt
anim('hurt', 0.45, false, {
	torso: { rotation: [[0, [0, 0, 0]], [0.1, [-10, 0, 0]], [0.45, [0, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.1, [-14, 0, 0]], [0.45, [0, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.1, [0, 0, 4]], [0.45, [0, 0, 0]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [0.1, [14, 0, 0]], [0.45, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.1, [14, 0, 0]], [0.45, [0, 0, 0]]] },
});
// regeneration -- fists to the chest, hunched, chest pulsing
anim('regeneration', 2.0, false, {
	right_arm: { rotation: [[0, [0, 0, 0]], [0.3, [-32, 0, 0]], [1.7, [-32, 0, 0]], [2.0, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.3, [-32, 0, 0]], [1.7, [-32, 0, 0]], [2.0, [0, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [0.3, [-105, 0, 0]], [1.7, [-105, 0, 0]], [2.0, [-6, 0, 0]]] },
	left_forearm: { rotation: [[0, [-6, 0, 0]], [0.3, [-105, 0, 0]], [1.7, [-105, 0, 0]], [2.0, [-6, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.3, [10, 0, 0]], [1.7, [10, 0, 0]], [2.0, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [0.6, [1.05, 1.05, 1.05]], [0.9, [1, 1, 1]], [1.2, [1.05, 1.05, 1.05]], [1.5, [1, 1, 1]], [1.8, [1.05, 1.05, 1.05]], [2.0, [1, 1, 1]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.3, [14, 0, 0]], [1.7, [14, 0, 0]], [2.0, [0, 0, 0]]] },
});
// hardening -- flex
anim('hardening', 1.1, false, {
	right_arm: { rotation: [[0, [0, 0, 0]], [0.35, [-22, 0, 0]], [0.85, [-22, 0, 0]], [1.1, [0, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [0.35, [-22, 0, 0]], [0.85, [-22, 0, 0]], [1.1, [0, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [0.35, [-115, 0, 0]], [0.85, [-115, 0, 0]], [1.1, [-6, 0, 0]]] },
	left_forearm: { rotation: [[0, [-6, 0, 0]], [0.35, [-115, 0, 0]], [0.85, [-115, 0, 0]], [1.1, [-6, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.4, [-6, 0, 0]], [1.1, [0, 0, 0]]], scale: [[0, [1, 1, 1]], [0.4, [1.07, 1.07, 1.07]], [0.85, [1.05, 1.05, 1.05]], [1.1, [1, 1, 1]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.4, [-12, 0, 0]], [1.1, [0, 0, 0]]] },
	root: { position: [[0, [0, 0, 0]], [0.4, [0, -3, 0]], [1.1, [0, 0, 0]]] },
});
// transformation -- a hunched shape swells up out of the flash
anim('transformation', 3.0, 'hold', {
	root: { scale: [[0, [0.25, 0.25, 0.25]], [1.0, [0.5, 0.5, 0.5]], [2.2, [1.05, 1.05, 1.05]], [3.0, [1, 1, 1]]] },
	torso: { rotation: [[0, [34, 0, 0]], [1.4, [26, 0, 0]], [2.4, [-6, 0, 0]], [3.0, [0, 0, 0]]] },
	head: { rotation: [[0, [24, 0, 0]], [1.6, [18, 0, 0]], [2.4, [-14, 0, 0]], [3.0, [0, 0, 0]]] },
	right_arm: { rotation: [[0, [-30, 0, 0]], [1.6, [-20, 0, 0]], [2.4, [22, 0, 0]], [3.0, [2, 0, 0]]] },
	left_arm: { rotation: [[0, [-30, 0, 0]], [1.6, [-20, 0, 0]], [2.4, [22, 0, 0]], [3.0, [2, 0, 0]]] },
	right_forearm: { rotation: [[0, [-90, 0, 0]], [1.6, [-60, 0, 0]], [3.0, [-6, 0, 0]]] },
	left_forearm: { rotation: [[0, [-90, 0, 0]], [1.6, [-60, 0, 0]], [3.0, [-6, 0, 0]]] },
	right_leg: { rotation: [[0, [-30, 0, 0]], [1.6, [-20, 0, 0]], [3.0, [0, 0, 0]]] },
	left_leg: { rotation: [[0, [-30, 0, 0]], [1.6, [-20, 0, 0]], [3.0, [0, 0, 0]]] },
	right_shin: { rotation: [[0, [60, 0, 0]], [1.6, [40, 0, 0]], [3.0, [6, 0, 0]]] },
	left_shin: { rotation: [[0, [60, 0, 0]], [1.6, [40, 0, 0]], [3.0, [6, 0, 0]]] },
});
// reversion -- shrinks back down in a cloud of steam
anim('reversion', 2.0, 'hold', {
	root: { scale: [[0, [1, 1, 1]], [1.0, [0.8, 0.8, 0.8]], [2.0, [0.25, 0.25, 0.25]]] },
	torso: { rotation: [[0, [0, 0, 0]], [1.2, [18, 0, 0]], [2.0, [34, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [1.2, [12, 0, 0]], [2.0, [24, 0, 0]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [2.0, [-30, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [2.0, [-30, 0, 0]]] },
	right_forearm: { rotation: [[0, [-6, 0, 0]], [2.0, [-90, 0, 0]]] },
	left_forearm: { rotation: [[0, [-6, 0, 0]], [2.0, [-90, 0, 0]]] },
});
// death -- the knees give, the head drops, the body slumps forward
anim('death', 3.0, 'hold', {
	root: { position: [[0, [0, 0, 0]], [0.5, [0, -8, 2]], [1.4, [0, -30, -4]], [2.4, [0, -44, -10]], [3.0, [0, -46, -10]]] },
	right_leg: { rotation: [[0, [0, 0, 0]], [0.5, [-25, 0, 0]], [1.4, [-62, 0, 0]], [3.0, [-72, 0, 0]]] },
	left_leg: { rotation: [[0, [0, 0, 0]], [0.5, [-20, 0, 0]], [1.4, [-56, 0, 0]], [3.0, [-68, 0, 0]]] },
	right_shin: { rotation: [[0, [6, 0, 0]], [0.5, [40, 0, 0]], [1.4, [92, 0, 0]], [3.0, [100, 0, 0]]] },
	left_shin: { rotation: [[0, [6, 0, 0]], [0.5, [34, 0, 0]], [1.4, [88, 0, 0]], [3.0, [98, 0, 0]]] },
	torso: { rotation: [[0, [0, 0, 0]], [0.5, [-8, 0, 0]], [1.4, [26, 0, 0]], [2.4, [44, 0, 0]], [3.0, [48, 0, 0]]] },
	head: { rotation: [[0, [0, 0, 0]], [0.5, [-12, 0, 0]], [1.4, [26, 0, 0]], [3.0, [42, 0, 0]]] },
	right_arm: { rotation: [[0, [0, 0, 0]], [1.4, [12, 0, 0]], [3.0, [26, 0, 0]]] },
	left_arm: { rotation: [[0, [0, 0, 0]], [1.4, [12, 0, 0]], [3.0, [26, 0, 0]]] },
});

// ---------------- item icon (16x16 vial of glowing serum) ----------------
function serumIcon() {
	const px = Buffer.alloc(16 * 16 * 4);
	const put = (x, y, c, a = 255) => { const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; };
	const glass = [190, 214, 226], glassD = [120, 150, 168], cork = [120, 86, 56], corkD = [86, 60, 40];
	const liq = [232, 92, 40], liqL = [255, 168, 60], liqD = [170, 50, 30];
	for (let y = 1; y <= 3; y++) for (let x = 6; x <= 9; x++) put(x, y, y === 1 ? corkD : cork);
	for (let y = 4; y <= 5; y++) { put(6, y, glassD); put(9, y, glassD); put(7, y, glass, 170); put(8, y, glass, 170); }
	for (let y = 6; y <= 14; y++) {
		const half = y < 8 ? 2 + (y - 6) : 4;
		for (let x = 8 - half; x < 8 + half; x++) {
			const edge = x === 8 - half || x === 7 + half || y === 14;
			put(x, y, edge ? glassD : (y === 8 ? liqL : x < 6 ? liqL : x > 9 ? liqD : liq));
		}
	}
	put(5, 9, [255, 240, 200], 230); put(5, 10, [255, 240, 200], 200);
	for (const [x, y] of [[3, 4], [12, 7], [2, 11], [13, 12]]) put(x, y, [255, 200, 90], 200);
	return encode(16, 16, px);
}

fs.writeFileSync(ROOT + 'geo/titan_form.geo.json', JSON.stringify(geo(), null, 1));
fs.writeFileSync(ROOT + 'animations/titan_form.animation.json', JSON.stringify({ format_version: '1.8.0', animations: A }, null, 1));
fs.writeFileSync(ROOT + 'textures/entity/titan_form.png', paint('normal'));
fs.writeFileSync(ROOT + 'textures/entity/titan_form_hardened.png', paint('hardened'));
fs.writeFileSync(ROOT + 'textures/item/titan_serum.png', serumIcon());
fs.writeFileSync(ROOT + 'models/item/titan_serum.json', JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/titan_serum' } }, null, 2));
console.log('done; animations:', Object.keys(A).length);
