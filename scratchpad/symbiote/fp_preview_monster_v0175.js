// v0.17.5 Symbiote Monster polish: a software preview of the FIRST-PERSON monster arms (SymbioteMonsterFirstPerson), with the same
// transform chain as the Java (camera space, 70 degree hand FOV, 854 x 480), so the arm pose and every move's motion can be tuned
// without a client.
//   node scratchpad/symbiote/fp_preview_monster_v0175.js <out dir>
// Writes fp_idle.png and a sheet per move (fp_<move>.png). Keep pose() in step with SymbioteMonsterFirstPerson.armPose().
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');
const OUT = process.argv[2] || '.';
fs.mkdirSync(OUT, { recursive: true });
const M = require('./gen_symbiote_monster_v0175.js');
const { bones, B, toModel, faceCorners, FACES, AW, AH } = M;
const tex = M.classicTex;
const W = 854, H = 480, TANH = Math.tan(35 * Math.PI / 180);
const D = Math.PI / 180;

// ---- joml-style rotations (right-handed)
const rx = (a, [x, y, z]) => { const c = Math.cos(a * D), s = Math.sin(a * D); return [x, y * c - z * s, y * s + z * c]; };
const ry = (a, [x, y, z]) => { const c = Math.cos(a * D), s = Math.sin(a * D); return [x * c + z * s, y, -x * s + z * c]; };
const rz = (a, [x, y, z]) => { const c = Math.cos(a * D), s = Math.sin(a * D); return [x * c - y * s, x * s + y * c, z]; };

const POSE = require('./fp_pose_monster_v0175.js');

function keep(n, s) {
	return n === 'forearm_' + s || n === 'hand_' + s || n.startsWith('finger_' + s) || n === 'thumb_' + s || n.startsWith('spur_' + s);
}

/** Camera-space triangles for one arm. */
function armTris(right, ap) {
	const s = right ? 'r' : 'l';
	const pose = {};
	for (const b of bones) {
		if (b.name.startsWith('finger') || b.name.startsWith('thumb')) pose[b.name] = [0, 0, 0];
		else pose[b.name] = b.rotation.map((v) => -v);
	}
	// bone-level extras (bedrock degrees): the wrist and the finger curl / spread
	const hb = pose['hand_' + s];
	pose['hand_' + s] = [hb[0] + ap.wrist[0], hb[1] + ap.wrist[1] * (right ? 1 : -1), hb[2] + ap.wrist[2] * (right ? 1 : -1)];
	for (let k = 0; k < 4; k++) {
		pose['finger_' + s + k] = [ap.curl, 0, (k - 1.5) * ap.spread * (right ? 1 : -1)];
		pose['finger_' + s + k + 'c'] = [ap.clawCurl, 0, 0];
	}
	pose['thumb_' + s] = [ap.curl * 0.6, 0, 0];
	const tris = [];
	for (const b of bones) {
		if (!keep(b.name, s)) continue;
		const claw = /^finger_.\dc$/.test(b.name);
		for (const c of b.cubes) for (const f of FACES) {
			const uvr = c.uv[f];
			const corners = faceCorners(c, f).map((q) => {
				if (claw) q = q.map((v, i) => b.pivot[i] + (v - b.pivot[i]) * (ap.claw || 1.7));
				let p = toModel(b, q, pose);
				p = [-p[0], p[1], p[2]]; // GeckoLib's mirror
				p = [p[0] + (right ? -27.5 : 27.5), p[1] - 30, p[2]].map((v) => v / 16);
				p = ry(180, p);
				p = rx(90, p);
				p = rz(ap.roll * (right ? 1 : -1), p);
				p = rx(ap.pitch, p);
				p = ry(ap.yaw * (right ? 1 : -1), p);
				p = p.map((v) => v * ap.scale);
				return [p[0] + ap.x * (right ? 1 : -1), p[1] + ap.y, p[2] + ap.z];
			});
			const uvs = [[uvr.uv[0], uvr.uv[1]], [uvr.uv[0] + uvr.uv_size[0], uvr.uv[1]], [uvr.uv[0] + uvr.uv_size[0], uvr.uv[1] + uvr.uv_size[1]], [uvr.uv[0], uvr.uv[1] + uvr.uv_size[1]]];
			tris.push({ corners, uvs });
		}
	}
	return tris;
}

function render(frame, out) {
	const px = Buffer.alloc(W * H * 4);
	const zb = new Float32Array(W * H).fill(1e9);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const i = (y * W + x) * 4;
		const sky = y < H * 0.55;
		px[i] = sky ? 168 : 92; px[i + 1] = sky ? 198 : 128; px[i + 2] = sky ? 255 : 64; px[i + 3] = 255;
	}
	const L = [0.3, 0.9, 0.3];
	for (const right of [true, false]) {
		for (const { corners, uvs } of armTris(right, frame[right ? 'r' : 'l'])) {
			const e1 = corners[1].map((v, i) => v - corners[0][i]), e2 = corners[3].map((v, i) => v - corners[0][i]);
			let n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
			const nl = Math.hypot(...n) || 1;
			n = n.map((v) => v / nl);
			const lit = 0.55 + 0.45 * Math.abs(n[0] * L[0] + n[1] * L[1] + n[2] * L[2]);
			const S = corners.map((q) => {
				const z = Math.min(-0.05, q[2]);
				return [W / 2 + (q[0] / -z) / TANH * (H / 2), H / 2 - (q[1] / -z) / TANH * (H / 2), -z];
			});
			for (const tri of [[0, 1, 2], [0, 2, 3]]) {
				const [a, bq, cq] = tri.map((i) => S[i]);
				const [ta, tb, tc] = tri.map((i) => uvs[i]);
				const minx = Math.max(0, Math.floor(Math.min(a[0], bq[0], cq[0]))), maxx = Math.min(W - 1, Math.ceil(Math.max(a[0], bq[0], cq[0])));
				const miny = Math.max(0, Math.floor(Math.min(a[1], bq[1], cq[1]))), maxy = Math.min(H - 1, Math.ceil(Math.max(a[1], bq[1], cq[1])));
				const den = (bq[1] - cq[1]) * (a[0] - cq[0]) + (cq[0] - bq[0]) * (a[1] - cq[1]);
				if (Math.abs(den) < 1e-9) continue;
				for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
					const X = x + 0.5, Y = y + 0.5;
					const w0 = ((bq[1] - cq[1]) * (X - cq[0]) + (cq[0] - bq[0]) * (Y - cq[1])) / den;
					const w1 = ((cq[1] - a[1]) * (X - cq[0]) + (a[0] - cq[0]) * (Y - cq[1])) / den;
					const w2 = 1 - w0 - w1;
					if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4) continue;
					const z = w0 * a[2] + w1 * bq[2] + w2 * cq[2];
					const zi = y * W + x;
					if (z >= zb[zi]) continue;
					zb[zi] = z;
					const u = Math.min(AW - 1, Math.max(0, Math.floor(w0 * ta[0] + w1 * tb[0] + w2 * tc[0])));
					const v = Math.min(AH - 1, Math.max(0, Math.floor(w0 * ta[1] + w1 * tb[1] + w2 * tc[1])));
					const ti = (v * AW + u) * 4;
					px[zi * 4] = Math.min(255, tex[ti] * lit + 18);
					px[zi * 4 + 1] = Math.min(255, tex[ti + 1] * lit + 18);
					px[zi * 4 + 2] = Math.min(255, tex[ti + 2] * lit + 18);
				}
			}
		}
	}
	// crosshair + the keep-clear box round it
	for (let k = -8; k <= 8; k++) for (const [x, y] of [[W / 2 + k, H / 2], [W / 2, H / 2 + k]]) {
		const i = (Math.round(y) * W + Math.round(x)) * 4; px[i] = 255; px[i + 1] = 255; px[i + 2] = 255;
	}
	return px;
}

function sheet(frames, cols, out) {
	const sw = W / 2, sh = H / 2;
	const rows = Math.ceil(frames.length / cols);
	const big = Buffer.alloc(sw * cols * sh * rows * 4);
	frames.forEach((px, k) => {
		const ox = (k % cols) * sw, oy = Math.floor(k / cols) * sh;
		for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
			const si = ((y * 2) * W + x * 2) * 4, di = ((oy + y) * sw * cols + ox + x) * 4;
			px.copy(big, di, si, si + 4);
		}
	});
	PNG.write(out, sw * cols, sh * rows, big);
}

PNG.write(path.join(OUT, 'fp_idle.png'), W, H, render(POSE.pose('none', 0, 0, 1), null));
const LEN = { swing: 6, swipe: 20, pound: 24, roar: 30, devour: 30, pull: 20, leap: 12, land: 14 };
for (const move of Object.keys(LEN)) {
	const len = LEN[move];
	const frames = [];
	for (let k = 0; k < 12; k++) {
		const t = k * len / 11;
		frames.push(render(POSE.pose(move, t, t, 1), null));
	}
	sheet(frames, 4, path.join(OUT, 'fp_' + move + '.png'));
}
console.log('fp previews in', OUT);
