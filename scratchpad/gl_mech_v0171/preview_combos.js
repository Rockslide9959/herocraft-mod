// v0.17.1 construct combos: pose sheets of the Giga Drill / Hard-Light Katana clips with simple stand-ins for the drill
// (a cone of boxes down the right forearm) and the katana (a blade along fist_r's -Z, edge down), so the clips can be
// checked before the game. Usage: node scratchpad/gl_mech_v0171/preview_combos.js <out dir>
'use strict';
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const anims = require('./anims.js');
const { Model } = L;

const M = require('./gen_gl_mech.js').M;
const OUT = process.argv[2] || path.join(__dirname, '..', '..', 'build');

function poseWorld(frame) {
	const W = {};
	for (const b of M.bones) {
		const ch = frame[b.name] || {};
		const r = [b.rot[0] + ((ch.rot || [0, 0, 0])[0]), b.rot[1] + ((ch.rot || [0, 0, 0])[1]), b.rot[2] + ((ch.rot || [0, 0, 0])[2])];
		let local = L.pivotRot(b.pivot, r);
		if (ch.pos) local = L.affMul(L.aff([[1, 0, 0], [0, 1, 0], [0, 0, 1]], ch.pos), local);
		W[b.name] = b.parent ? L.affMul(W[b.parent], local) : local;
	}
	return W;
}

// stand-ins, authored in the right fist's json space
const K = new Model('katana', { TW: 256, D: 2 });
K.bone('root', null, [0, 0, 0]);
const kb = K.bone('k', 'root', [0, 0, 0]);
K.box(kb, -29, 31.5, -100, -27, 38, -12, 'blade');      // blade (edge at y 31.5, spine at 38)
K.box(kb, -28.6, 31, -100, -27.4, 32, -12, 'edge');
K.box(kb, -34, 29, -12, -22, 40.5, -10.5, 'tsuba');
K.box(kb, -29.6, 32.5, -10.5, -26.4, 37, 18, 'grip');
K.layout();
K.paint(c => c.mat === 'edge' ? [[255, 255, 255], 255, [200, 255, 220], 255] : c.mat === 'tsuba' ? [[30, 120, 60], 255, null]
	: c.mat === 'grip' ? [[220, 240, 225], 255, null] : [[110, 255, 160], 255, [80, 220, 130], 255]);
const D = new Model('drill', { TW: 256, D: 2 });
D.bone('root', null, [0, 0, 0]);
const db = D.bone('d', 'root', [0, 0, 0]);
for (let i = 0; i < 8; i++) {
	const r = 14 * (1 - i / 8), y0 = 41 - i * 8;
	D.box(db, -28 - r, y0 - 8, 1 - r, -28 + r, y0, 1 + r, i % 2 ? 'a' : 'b');
}
D.layout();
D.paint(c => c.mat === 'a' ? [[120, 255, 170], 255, [80, 220, 130], 255] : [[40, 180, 90], 255, null]);

function items(frame, prop) {
	const W = poseWorld(frame);
	const m = Object.create(M);
	m.world = () => W;
	m.cubes = () => prop === 'drill' ? M.cubes().filter(c => c.bone !== 'fist_r') : M.cubes();
	m.k = 1 / 16; m.heightBlocks = 6.4;
	const out = [{ model: m, pose: {} }];
	const P = prop === 'drill' ? D : K;
	const bone = prop === 'drill' ? 'forearm_r' : 'fist_r';
	const pm = Object.create(P);
	pm.world = () => { const o = {}; for (const b of P.bones) o[b.name] = W[bone]; return o; };
	pm.cubes = () => P.cubes();
	pm.k = 1 / 16; pm.heightBlocks = 2;
	out.push({ model: pm, pose: {} });
	return out;
}

const fl = [{ x: 0, z: 0, r: 3.2 }];
const bg = { top: [36, 44, 52], bottom: [12, 16, 20] };
const light = Object.assign({}, L.DEFAULT_LIGHT, { rimCol: [0.55, 1.0, 0.7], rimK: 0.45, amb: 0.42 });
const opts = { panelH: 700, minW: 2200, maxW: 3400, bg, bloom: 0.4, edgeThr: 0.05, light };
const at = (name, t, yaw = 35, prop = 'katana', pitch = 10) => ({ label: name.replace(/_/g, ' ') + ' ' + t.toFixed(2),
	items: items(anims.sample(name, t), prop), cam: { yaw, pitch }, floor: fl });
L.sheet(path.join(OUT, 'combo_katana_a.png'), 'KATANA', 'IDLE / DRAW / SHEATH', [
	at('katana_idle', 0, 35), at('katana_idle', 0, 100), at('katana_draw', 0.16, 40), at('katana_draw', 0.38, 40),
	at('katana_sheath', 0.14, 40), at('katana_walk', 0.15, 70),
], opts);
L.sheet(path.join(OUT, 'combo_katana_b.png'), 'KATANA', 'SLASHES', [
	at('katana_slash_1', 0.1, 30), at('katana_slash_1', 0.24, 30), at('katana_slash_2', 0.1, 30), at('katana_slash_2', 0.24, 30),
	at('katana_slash_3', 0.16, 30), at('katana_slash_3', 0.3, 30),
], opts);
L.sheet(path.join(OUT, 'combo_katana_c.png'), 'KATANA', 'IAIDO / SPIN', [
	at('katana_iaido', 0.14, 30), at('katana_iaido', 0.28, 30), at('katana_iaido', 0.28, 0, 'katana', 60),
	at('katana_spin', 0.1, 30), at('katana_spin', 0.3, 30), at('katana_spin', 0.3, 30, 'katana', 70),
], opts);
L.sheet(path.join(OUT, 'combo_drill.png'), 'GIGA DRILL', 'SPIN / CHARGE / BREAK', [
	at('drill_spin', 0, 40, 'drill'), at('drill_spin', 0, 95, 'drill'), at('drill_charge', 0, 40, 'drill'), at('drill_charge', 0, 95, 'drill'),
	at('drill_break', 0.3, 60, 'drill'),
], opts);
console.log('sheets in', OUT);
