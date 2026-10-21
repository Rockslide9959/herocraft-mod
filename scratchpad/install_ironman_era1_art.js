// v0.16.7 Iron Man Era I (the Gantry Age) -- ART install. Repeatable; run from the repo root (or a worktree):
//   node scratchpad/install_ironman_era1_art.js
//
// 1. Skins. The user-approved 64x64 concept skins + glowmasks (made by scratchpad/gen_ironman_era1_concepts.js into the
//    main checkout's untracked scratchpad/concepts_ironman_v2/skins/) become the armour textures:
//      e1_mk1 -> mark_1 (Mk I Cave-Forged)     e1_mk2 -> mark_1_v2 (Mk II Golden Avenger)   e1_mk3 -> mark_iii (Mk III Classic)
//      e1_mk4 -> mark_4 (Mk IV Hydro)           e1_mk5 -> mark_v (Mk V Stealth)
//    A tracked copy of the ten PNGs lives in scratchpad/ironman_era1_skins/ (used when the main checkout's copy is absent,
//    and refreshed from it when present).
// 2. THIN geos, matching the approved concept rig (thinRig): base layer +0.08 (helmet +0.1), outer layer +0.22, helmet outer
//    +0.32. The limbs keep the player's 4 x 4 cross-section (the wearer's own base skin is still drawn underneath -- only
//    the outer layer is hidden -- so anything narrower would let a wide-armed skin poke through). Same bones as before
//    (the shared skin rig: helmet / faceplate / chest / upper arms / thigh + boot split, plus the empty bones the
//    assembly / build code drives). The Mk IV Hydro: the helmet's outer layer blown up to +1.7 (the glass dome -- its hat
//    layer is mostly empty, so with the cutout armour render type it reads as glass edges + glints) and two small
//    pauldron cubes on the shoulder bones, every face mapped to the sleeve top. The Mk II keeps sharing the Mk I's geo
//    (SuperheroArmorVisuals maps mark_1_v2 -> geo/mark_1.geo.json; the two only differ in texture).
// 3. Item icons (16x16 iron_man_mark_{1,1_v2,3,4,5}_{helmet,chestplate,leggings,boots}.png) cut from each skin's front
//    view, the Mk IV helmet drawn inside its glass dome.
'use strict';
const fs = require('fs');
const path = require('path');
const P = require('./pngkit.js');

const here = __dirname.split(path.sep).join('/');
const wt = here.indexOf('/.claude/worktrees/');
const MAIN = wt >= 0 ? here.slice(0, wt) : path.join(__dirname, '..');
const MAIN_SKINS = path.join(MAIN, 'scratchpad/concepts_ironman_v2/skins');
const LOCAL_SKINS = path.join(__dirname, 'ironman_era1_skins');
const RES = path.join(__dirname, '../src/main/resources/assets/projecthero');

const SUITS = [
	{ skin: 'e1_mk1', id: 'mark_1', item: 'mark_1', geo: 'mark_1' },
	{ skin: 'e1_mk2', id: 'mark_1_v2', item: 'mark_1_v2', geo: null }, // shares geo/mark_1.geo.json
	{ skin: 'e1_mk3', id: 'mark_iii', item: 'mark_3', geo: 'mark_iii' },
	{ skin: 'e1_mk4', id: 'mark_4', item: 'mark_4', geo: 'mark_4', hydro: true },
	{ skin: 'e1_mk5', id: 'mark_v', item: 'mark_5', geo: 'mark_v' },
];

// ------------------------------------------------------------------ 1. skins
fs.mkdirSync(LOCAL_SKINS, { recursive: true });
for (const s of SUITS) {
	for (const suffix of ['', '_glowmask']) {
		const name = s.skin + suffix + '.png';
		const main = path.join(MAIN_SKINS, name), local = path.join(LOCAL_SKINS, name);
		if (fs.existsSync(main) && path.resolve(main) !== path.resolve(local)) {
			fs.copyFileSync(main, local);
		}
		if (!fs.existsSync(local)) {
			throw new Error('missing skin ' + name + ' (neither ' + main + ' nor ' + local + ')');
		}
		fs.copyFileSync(local, path.join(RES, 'textures/armor', s.id + suffix + '.png'));
		console.log('installed textures/armor/' + s.id + suffix + '.png <- ' + name);
	}
}

// ------------------------------------------------------------------ 2. thin geos
const BASE = 0.08, HEAD = 0.1, OUTER = 0.22, HAT = 0.32, PANTS_TOP = 0.12;
const r4 = v => Math.round(v * 10000) / 10000;
const faceUv = (u, v, w, h) => ({ uv: [u, v], uv_size: [w, h] });

function headCubes(hat) {
	const side = (o, inflate) => ({ origin: [-4, 24, -4], size: [8, 8, 8], inflate, uv: {
		east: faceUv(o, 8, 8, 8), west: faceUv(o + 16, 8, 8, 8), south: faceUv(o + 24, 8, 8, 8),
		up: faceUv(o + 8, 0, 8, 8), down: faceUv(o + 16, 0, 8, 8) } });
	const front = (o, inflate) => ({ origin: [-4, 24, -4], size: [8, 8, 0], inflate, uv: {
		north: faceUv(o + 8, 8, 8, 8), south: faceUv(o + 8, 8, 8, 8) } });
	return { helmet: [side(0, HEAD), side(32, hat)], faceplate: [front(0, HEAD), front(32, hat)] };
}

// a leg layer as explicit thigh + boot boxes (so leggings and boots are separate pieces), split at a third of the height so
// the 8 thigh / 4 boot texel rows stay square. x0 / x1: the leg's x span (pivot space), g: inflation, top: top inflation.
function legLayer(x0, x1, g, top, u, v, upU, upV, downU, downV) {
	const y0 = -g, y1 = 12 + top, h = y1 - y0, split = y0 + h / 3;
	const lo = x0 - g, hi = x1 + g, z = 2 + g;
	const faces = (row, rows) => ({ east: faceUv(u, v + row, 4, rows), north: faceUv(u + 4, v + row, 4, rows),
		west: faceUv(u + 8, v + row, 4, rows), south: faceUv(u + 12, v + row, 4, rows) });
	const thigh = { origin: [r4(lo), r4(split), r4(-z)], size: [r4(hi - lo), r4(y1 - split), r4(2 * z)],
		uv: Object.assign(faces(4, 8), { up: faceUv(upU, upV, 4, 4) }) };
	const boot = { origin: [r4(lo), r4(y0), r4(-z)], size: [r4(hi - lo), r4(split - y0), r4(2 * z)],
		uv: Object.assign(faces(12, 4), { down: faceUv(downU, downV, 4, 4) }) };
	return { thigh, boot };
}

function bone(name, parent, pivot, cubes) {
	const b = { name };
	if (parent) b.parent = parent;
	b.pivot = pivot;
	if (cubes && cubes.length) b.cubes = cubes;
	return b;
}

function buildGeo(identifier, hydro) {
	const head = headCubes(hydro ? 1.7 : HAT);
	// GeckoLib's armour renderer offsets the leg bones 0.1 outward (rightLeg.x + 2), so the wearer's legs sit at x -4..0 (right) / 0..4 (left) in geo space
	const rBase = legLayer(-4, 0, BASE, BASE, 0, 16, 4, 16, 8, 16);
	const rOut = legLayer(-4, 0, OUTER, PANTS_TOP, 0, 32, 4, 32, 8, 32);
	const lBase = legLayer(0, 4, BASE, BASE, 16, 48, 20, 48, 24, 48);
	const lOut = legLayer(0, 4, OUTER, PANTS_TOP, 0, 48, 4, 48, 8, 48);
	// Mk IV pauldrons: a small flared plate over each shoulder, every face the sleeve top (the concept's HYDRO_RIG)
	const pauldron = (x0, x1, u, v) => {
		const f = faceUv(u, v, 4, 4);
		return { origin: [x0, 22.0, -2.45], size: [r4(x1 - x0), 2.55, 4.9],
			uv: { north: f, east: f, south: f, west: f, up: f, down: f } };
	};
	const bones = [
		bone('armorHead', null, [0, 24, 0]),
		bone('helmet', 'armorHead', [0, 24, 0], head.helmet),
		bone('faceplate', 'armorHead', [0, 32, -4], head.faceplate),
		bone('helmet_brow', 'armorHead', [0, 32, -4]),
		bone('armorBody', null, [0, 24, 0]),
		bone('chest', 'armorBody', [0, 24, 0], [
			{ origin: [-4, 12, -2], size: [8, 12, 4], inflate: BASE, uv: [16, 16] },
			{ origin: [-4, 12, -2], size: [8, 12, 4], inflate: OUTER, uv: [16, 32] }]),
		bone('chest_armor', 'armorBody', [0, 23, -2]),
		bone('arc_reactor', 'armorBody', [0, 21, -2]),
		bone('waist', 'armorBody', [0, 17, 0]),
		bone('back_panel', 'armorBody', [0, 23, 2]),
		bone('armorRightArm', null, [-5, 22, 0]),
		bone('right_upper_arm', 'armorRightArm', [-5, 24, 0], [
			{ origin: [-8, 12, -2], size: [4, 12, 4], inflate: BASE, uv: [40, 16] },
			{ origin: [-8, 12, -2], size: [4, 12, 4], inflate: OUTER, uv: [40, 32] }]),
		bone('right_shoulder', 'armorRightArm', [-5, 23, 0], hydro ? [pauldron(-8.45, -3.6, 44, 32)] : null),
		bone('right_gauntlet', 'armorRightArm', [-5, 14, 0]),
		bone('armorLeftArm', null, [5, 22, 0]),
		bone('left_upper_arm', 'armorLeftArm', [5, 24, 0], [
			{ origin: [4, 12, -2], size: [4, 12, 4], inflate: BASE, uv: [32, 48] },
			{ origin: [4, 12, -2], size: [4, 12, 4], inflate: OUTER, uv: [48, 48] }]),
		bone('left_shoulder', 'armorLeftArm', [5, 23, 0], hydro ? [pauldron(3.6, 8.45, 52, 48)] : null),
		bone('left_gauntlet', 'armorLeftArm', [5, 14, 0]),
		bone('armorRightLeg', null, [-1.9, 12, 0]),
		bone('right_thigh', 'armorRightLeg', [-1.9, 12, 0], [rBase.thigh, rOut.thigh]),
		bone('right_thigh_plate', 'right_thigh', [-1.9, 12, 0]),
		bone('right_knee', 'armorRightLeg', [-1.9, 8, 0]),
		bone('armorLeftLeg', null, [1.9, 12, 0]),
		bone('left_thigh', 'armorLeftLeg', [1.9, 12, 0], [lBase.thigh, lOut.thigh]),
		bone('left_thigh_plate', 'left_thigh', [1.9, 12, 0]),
		bone('left_knee', 'armorLeftLeg', [1.9, 8, 0]),
		bone('armorRightBoot', null, [-1.9, 12, 0]),
		bone('right_boot', 'armorRightBoot', [-1.9, 3, 0], [rBase.boot, rOut.boot]),
		bone('armorLeftBoot', null, [1.9, 12, 0]),
		bone('left_boot', 'armorLeftBoot', [1.9, 3, 0], [lBase.boot, lOut.boot]),
	];
	return { format_version: '1.12.0', 'minecraft:geometry': [{
		description: { identifier, texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3.5,
			visible_bounds_offset: [0, 1.25, 0] },
		bones }] };
}

for (const s of SUITS) {
	if (!s.geo) continue;
	const file = path.join(RES, 'geo', s.geo + '.geo.json');
	fs.writeFileSync(file, JSON.stringify(buildGeo('geometry.' + s.geo, !!s.hydro), null, '\t') + '\n');
	console.log('wrote geo/' + s.geo + '.geo.json' + (s.hydro ? ' (glass dome + pauldrons)' : '') + ' -- thin rig');
}

// ------------------------------------------------------------------ 3. item icons
function canvas(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function put(im, x, y, c) {
	if (x < 0 || y < 0 || x >= im.w || y >= im.h || !c[3]) return;
	const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = 255;
}
const shade = (c, k) => [Math.min(255, Math.round(c[0] * k)), Math.min(255, Math.round(c[1] * k)), Math.min(255, Math.round(c[2] * k)), c[3]];
// the suit's front view, 1 px per texel: 16 wide (arm 4 + body 8 + arm 4) x 32 tall (head 8, body 12, legs 12)
function frontView(sk, withHat) {
	const fv = canvas(16, 32);
	const blit = (u, v, w, h, dx, dy) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(fv, dx + x, dy + y, get(sk, u + x, v + y)); };
	blit(8, 8, 8, 8, 4, 0); if (withHat) blit(40, 8, 8, 8, 4, 0);
	blit(20, 20, 8, 12, 4, 8); blit(20, 36, 8, 12, 4, 8);
	blit(44, 20, 4, 12, 0, 8); blit(44, 36, 4, 12, 0, 8);
	blit(36, 52, 4, 12, 12, 8); blit(52, 52, 4, 12, 12, 8);
	blit(4, 20, 4, 12, 4, 20); blit(4, 36, 4, 12, 4, 20);
	blit(20, 52, 4, 12, 8, 20); blit(4, 52, 4, 12, 8, 20);
	return fv;
}
// copy a region of src scaled (nearest) into dst, with optional per-pixel shade at the edges for a little depth
function stamp(dst, src, sx, sy, sw, sh, dx, dy, dw, dh) {
	for (let y = 0; y < dh; y++) for (let x = 0; x < dw; x++) {
		let c = get(src, sx + Math.floor(x * sw / dw), sy + Math.floor(y * sh / dh));
		if (!c[3]) continue;
		if (x === 0 || y === dh - 1) c = shade(c, 0.78); else if (y === 0 || x === dw - 1) c = shade(c, 1.12);
		put(dst, dx + x, dy + y, c);
	}
}
function writeIcon(name, im) {
	// v0.16.7 icons pass: the armour item icons are now hand-drawn by scratchpad/icons_v0167.js --
	// don't overwrite them with these flat skin cut-outs (pass --icons to force the old behaviour).
	if (!process.argv.includes('--icons')) return;
	fs.writeFileSync(path.join(RES, 'textures/item', name + '.png'), P.encode(im.w, im.h, im.px));
	console.log('wrote textures/item/' + name + '.png');
}
const GLASS = [196, 232, 240, 255], GLASS_DIM = [140, 188, 204, 255], GLINT = [250, 255, 255, 255];

for (const s of SUITS) {
	const sk = P.decode(path.join(LOCAL_SKINS, s.skin + '.png'));
	const fv = frontView(sk, !s.hydro);
	const pre = 'iron_man_' + s.item + '_';
	// helmet: the head front at 2x (the Hydro: the red + gold helmet at 1.5x inside its glass dome on a gold collar)
	const helm = canvas(16, 16);
	if (s.hydro) {
		stamp(helm, fv, 4, 0, 8, 8, 2, 2, 12, 12);
		const collar = [226, 170, 52, 255];
		for (let x = 0; x < 16; x++) { put(helm, x, 0, x === 0 || x === 15 ? GLASS_DIM : GLASS); put(helm, x, 14, shade(collar, 1.1)); put(helm, x, 15, shade(collar, 0.75)); }
		for (let y = 1; y < 14; y++) { put(helm, 0, y, GLASS); put(helm, 15, y, GLASS_DIM); }
		for (const [x, y] of [[2, 1], [3, 1], [1, 2], [1, 3], [13, 2], [12, 1]]) put(helm, x, y, GLINT);
	} else {
		stamp(helm, fv, 4, 0, 8, 8, 0, 0, 16, 16);
	}
	writeIcon(pre + 'helmet', helm);
	// chestplate: torso + arms (16 x 12) at 1x, centred
	const chest = canvas(16, 16);
	stamp(chest, fv, 0, 8, 4, 12, 0, 2, 4, 12); stamp(chest, fv, 12, 8, 4, 12, 12, 2, 4, 12); stamp(chest, fv, 4, 8, 8, 12, 4, 2, 8, 12);
	writeIcon(pre + 'chestplate', chest);
	// leggings: the thighs (8 rows) at 2x
	const legs = canvas(16, 16);
	stamp(legs, fv, 4, 20, 4, 8, 0, 0, 8, 16); stamp(legs, fv, 8, 20, 4, 8, 8, 0, 8, 16);
	writeIcon(pre + 'leggings', legs);
	// boots: the boot rows (4) at 2x
	const boots = canvas(16, 16);
	stamp(boots, fv, 4, 28, 4, 4, 0, 4, 8, 8); stamp(boots, fv, 8, 28, 4, 4, 8, 4, 8, 8);
	writeIcon(pre + 'boots', boots);
}
console.log('done');
