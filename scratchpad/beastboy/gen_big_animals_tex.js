// v0.17.3 Beast Boy HQ round: the big animals (rhino, elephant, gorilla, the Beast) as blocky Minecraft-style models.
// This script is the source of truth for their box layout: for each animal it
//   * defines bones (pivots) and boxes in a friendly space (pixels, +Y up, ground at 0, +Z = the animal's front, +X its left),
//   * packs every box's vanilla box-UV net into a texture,
//   * paints the texture as pixel art in Beast Boy's greens (dark back, mid flanks, pale belly; wrinkles, fur, eyes...),
//   * writes the PNG to textures/entity/beastboy/art/<animal>.png and the geometry to client/beastboy/model/<Animal>Geo.java
//     (vanilla model space: y down from 24 px above the ground, front -Z).
// Box coordinates are absolute (friendly space) in the bone's own frame before its rest rotation.
// Usage: node scratchpad/beastboy/gen_big_animals_tex.js [rhino|elephant|gorilla|beast ...]   (DEBUG=1 paints faces by side)
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');
const ROOT = path.join(__dirname, '..', '..');
const TEXDIR = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/beastboy/art');
const JDIR = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/beastboy/model');
const DEBUG = !!process.env.DEBUG;

// ------------------------------------------------------------------------------------------------ colour + noise
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const mix = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return [0, 1, 2, 3].map(i => a[i] + (b[i] - a[i]) * t); };
const clamp01 = t => Math.max(0, Math.min(1, t));
function rnd(x, y, s = 1) { let n = (Math.floor(x) * 374761393 + Math.floor(y) * 668265263 + s * 1442695041) | 0; n = Math.imul(n ^ (n >>> 13), 1274126177); return ((n ^ (n >>> 16)) >>> 0) / 4294967295; }
function vnoise(x, y, s, cell) {
  const gx = x / cell, gy = y / cell, x0 = Math.floor(gx), y0 = Math.floor(gy), fx = gx - x0, fy = gy - y0;
  const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
  const h = (i, j) => rnd(i, j, s);
  return (h(x0, y0) * (1 - sx) + h(x0 + 1, y0) * sx) * (1 - sy) + (h(x0, y0 + 1) * (1 - sx) + h(x0 + 1, y0 + 1) * sx) * sy;
}
// Beast Boy's greens, back (0) to belly (1)
const RAMP = [0x1F4A1D, 0x2A5F26, 0x357330, 0x42873A, 0x529C44, 0x66B04F, 0x7EC15E, 0x9CD07A, 0xB9DF98, 0xD2EBB4].map(hex);
function ramp(t) {
  t = clamp01(t) * (RAMP.length - 1);
  const i = Math.min(RAMP.length - 2, Math.floor(t));
  return mix(RAMP[i], RAMP[i + 1], t - i);
}
// a banded (posterised) ramp for clean pixel art: 19 tones, band edges broken up by a little per-pixel jitter
const BANDS = 18;
function rampPx(t, x, y) {
  const j = (rnd(x, y, 7) - 0.5) * 0.7;
  const i = Math.max(0, Math.min(BANDS, Math.round(clamp01(t) * BANDS + j)));
  return ramp(i / BANDS);
}
const shade = (c, k) => [c[0] * k, c[1] * k, c[2] * k, c[3]];

// ------------------------------------------------------------------------------------------------ model building
class Model {
  constructor(name, cls, opts) {
    this.name = name; this.cls = cls; this.bones = []; this.byName = {};
    this.backY = opts.backY; this.bellyY = opts.bellyY; this.detail = opts.detail || (p => p.col);
    this.sizes = opts.sizes || [[128, 128], [256, 128], [256, 256]];
  }
  bone(name, parent, pivot, rot = [0, 0, 0]) {
    if (this.byName[name]) throw new Error('dup bone ' + name);
    const b = { name, parent, pivot, rot, boxes: [] };
    this.bones.push(b); this.byName[name] = b;
    const api = {
      box: (o, s, opt = {}) => { b.boxes.push({ bone: b, o, s, opt: Object.assign({ mat: 'hide' }, opt) }); return api; },
    };
    return api;
  }
  allBoxes() { return this.bones.flatMap(b => b.boxes); }
  pack() {
    const boxes = this.allBoxes();
    for (const bx of boxes) {
      const [w, h, d] = bx.s;
      if ([w, h, d].some(v => v !== Math.round(v) || v < 1)) throw new Error(this.name + ': box sizes must be whole: ' + bx.bone.name + ' ' + bx.s);
      bx.uw = 2 * (d + w); bx.uh = d + h;
    }
    const order = boxes.slice().sort((a, b) => b.uh - a.uh || b.uw - a.uw);
    for (const [W, H] of this.sizes) {
      let x = 0, y = 0, rowH = 0, ok = true;
      for (const bx of order) {
        if (x + bx.uw > W) { x = 0; y += rowH; rowH = 0; }
        if (bx.uw > W || y + bx.uh > H) { ok = false; break; }
        bx.u = x; bx.v = y; x += bx.uw; rowH = Math.max(rowH, bx.uh);
      }
      if (ok) { this.W = W; this.H = H; return; }
    }
    throw new Error(this.name + ': does not fit');
  }
  // the six faces of a box: name, rect in the texture, and the texel -> friendly-space position mapping
  faces(bx) {
    const [x0, y0, z0] = bx.o, [w, h, d] = bx.s, u = bx.u, v = bx.v;
    return [
      { f: 'top', r: [u + d, v, w, d], pos: (fx, fy) => [x0 + fx + .5, y0 + h, z0 + fy + .5] },
      { f: 'bottom', r: [u + d + w, v, w, d], pos: (fx, fy) => [x0 + fx + .5, y0, z0 + d - fy - .5] },
      { f: 'right', r: [u, v + d, d, h], pos: (fx, fy) => [x0, y0 + h - fy - .5, z0 + fx + .5] },
      { f: 'front', r: [u + d, v + d, w, h], pos: (fx, fy) => [x0 + fx + .5, y0 + h - fy - .5, z0 + d] },
      { f: 'left', r: [u + d + w, v + d, d, h], pos: (fx, fy) => [x0 + w, y0 + h - fy - .5, z0 + d - fx - .5] },
      { f: 'back', r: [u + 2 * d + w, v + d, w, h], pos: (fx, fy) => [x0 + w - fx - .5, y0 + h - fy - .5, z0] },
    ];
  }
  paint() {
    const W = this.W, H = this.H, px = Buffer.alloc(W * H * 4);
    for (const bx of this.allBoxes()) {
      for (const F of this.faces(bx)) {
        const [rx, ry, rw, rh] = F.r;
        for (let fy = 0; fy < rh; fy++) for (let fx = 0; fx < rw; fx++) {
          const pos = F.pos(fx, fy);
          const p = { m: this, bx, opt: bx.opt, face: F.f, fx, fy, fw: rw, fh: rh, x: pos[0], y: pos[1], z: pos[2], tx: rx + fx, ty: ry + fy };
          let c = DEBUG ? debugCol(p) : this.texel(p);
          const i = ((ry + fy) * W + rx + fx) * 4;
          px[i] = Math.round(Math.max(0, Math.min(255, c[0]))); px[i + 1] = Math.round(Math.max(0, Math.min(255, c[1])));
          px[i + 2] = Math.round(Math.max(0, Math.min(255, c[2]))); px[i + 3] = c[3] === undefined ? 255 : Math.round(c[3]);
        }
      }
    }
    return px;
  }
  // countershade amount at a texel: 0 = dark back, 1 = pale belly
  tone(p) {
    const o = p.opt;
    const down = (p.fy + 0.5) / p.fh;
    if (o.ft && o.ft[p.face] !== undefined) { // per-face override: a number, or [top, bottom] down the face
      const v = o.ft[p.face];
      return Array.isArray(v) ? v[0] + (v[1] - v[0]) * down : v;
    }
    if (o.sideZ && (p.face === 'left' || p.face === 'right')) { // pitched torsos: back edge dark, front edge light
      const k = clamp01((p.z - p.bx.o[2]) / p.bx.s[2]);
      return o.sideZ[0] + (o.sideZ[1] - o.sideZ[0]) * k + down * (o.sideZ[2] || 0);
    }
    if (o.t) { // explicit [top, bottom] range down the side faces
      if (p.face === 'top') return o.t[0] - 0.08;
      if (p.face === 'bottom') return o.t[1] + 0.1;
      return o.t[0] + (o.t[1] - o.t[0]) * ((p.fy + 0.5) / p.fh);
    }
    if (p.face === 'top') return 0.04 + clamp01((this.backY - p.y) / (this.backY - this.bellyY)) * 0.3;
    if (p.face === 'bottom') return o.belly ? 0.97 : 0.82;
    let t = clamp01((this.backY - p.y) / (this.backY - this.bellyY));
    t = t * t * (3 - 2 * t);
    // dark back, mid flanks; only the last rows above the belly line lighten toward the pale underside
    return 0.12 + t * 0.48 + Math.max(0, t - 0.8) * 1.2;
  }
  texel(p) {
    const MAT = MATS[p.opt.mat];
    if (!MAT) throw new Error('no material ' + p.opt.mat);
    let c = MAT(p);
    if (c[3] === 0) return c;
    c = this.detail(Object.assign(p, { col: c }));
    // edge accents on the skin / fur boxes: a rim of light along the top row of the sides, a darker bottom row
    if (EDGED.has(p.opt.mat) && p.face !== "top" && p.face !== "bottom" && p.fh >= 3) {
      if (p.fy === 0) c = mix(c, hex(0xE6F7D2), 0.14);
      else if (p.fy === p.fh - 1) c = shade(c, 0.86);
    }
    if (EDGED.has(p.opt.mat) && p.face === "top" && p.fw >= 3 && p.fh >= 3 && (p.fx === 0 || p.fy === 0 || p.fx === p.fw - 1 || p.fy === p.fh - 1)) c = mix(c, hex(0xE6F7D2), 0.08);
    if (p.opt.deco) c = p.opt.deco(Object.assign(p, { col: c })) || c;
    return c;
  }
  emit() {
    const ln = [];
    const f = n => { const s = (Math.round(n * 10000) / 10000).toString(); return (s.includes('.') ? s : s + '.0') + 'F'; };
    const pivotOf = b => b.pivot;
    const parentPivot = b => b.parent ? this.byName[b.parent].pivot : [0, 24, 0];
    ln.push('package com.projecthero.mod.client.beastboy.model;', '');
    ln.push('import net.minecraft.client.model.geom.PartPose;');
    ln.push('import net.minecraft.client.model.geom.builders.CubeDeformation;');
    ln.push('import net.minecraft.client.model.geom.builders.CubeListBuilder;');
    ln.push('import net.minecraft.client.model.geom.builders.LayerDefinition;');
    ln.push('import net.minecraft.client.model.geom.builders.MeshDefinition;');
    ln.push('import net.minecraft.client.model.geom.builders.PartDefinition;', '');
    ln.push('/**', ` * GENERATED by scratchpad/beastboy/gen_big_animals_tex.js (v0.17.3 Beast Boy HQ round) -- do not edit by hand, change the script.`,
      ` * The ${this.name}'s boxes in vanilla model space (y down from 24 px above the ground, front -Z); texture ${this.name}.png (${this.W}x${this.H}).`, ' */');
    ln.push(`final class ${this.cls} {`);
    ln.push(`\tstatic final int TEX_W = ${this.W}, TEX_H = ${this.H};`, '');
    ln.push('\t/** Every part: {name, parent name ("" = root)}, parents first. */');
    ln.push('\tstatic final String[][] TREE = {');
    for (const b of this.bones) ln.push(`\t\t\t{ "${b.name}", "${b.parent || ''}" },`);
    ln.push('\t};', '');
    ln.push(`\tprivate ${this.cls}() {`, '\t}', '');
    ln.push('\tstatic LayerDefinition layer() {');
    ln.push('\t\tMeshDefinition mesh = new MeshDefinition();');
    ln.push('\t\tPartDefinition root = mesh.getRoot();');
    for (const b of this.bones) {
      const pp = parentPivot(b), pv = pivotOf(b);
      const rel = [pv[0] - pp[0], -(pv[1] - pp[1]), -(pv[2] - pp[2])];
      const r = b.rot.map(a => a);
      let cubes = 'CubeListBuilder.create()';
      for (const bx of b.boxes) {
        const o = [bx.o[0] - pv[0], -(bx.o[1] - pv[1] + bx.s[1]), -(bx.o[2] - pv[2] + bx.s[2])];
        const inf = bx.opt.inflate || 0;
        cubes += `\n\t\t\t\t.texOffs(${bx.u}, ${bx.v}).addBox(${f(o[0])}, ${f(o[1])}, ${f(o[2])}, ${f(bx.s[0])}, ${f(bx.s[1])}, ${f(bx.s[2])}${inf ? `, new CubeDeformation(${f(inf)})` : ''})`;
      }
      const parentVar = b.parent ? 'p_' + b.parent : 'root';
      ln.push(`\t\tPartDefinition p_${b.name} = ${parentVar}.addOrReplaceChild("${b.name}", ${cubes},`);
      ln.push(`\t\t\t\tPartPose.offsetAndRotation(${f(rel[0])}, ${f(rel[1])}, ${f(rel[2])}, ${f(r[0])}, ${f(-r[1])}, ${f(-r[2])}));`);
    }
    ln.push(`\t\treturn LayerDefinition.create(mesh, TEX_W, TEX_H);`);
    ln.push('\t}', '}', '');
    return ln.join('\r\n');
  }
  write() {
    this.pack();
    const px = this.paint();
    png.write(path.join(TEXDIR, this.name + '.png'), this.W, this.H, px);
    let src = this.emit();
    // silence "unused" on bones nobody hangs children from
    src = src.replace(/\t\tPartDefinition (p_\w+) = /g, (m0, v) => (new RegExp('\\b' + v + '\\.').test(src) ? m0 : '\t\t'));
    fs.writeFileSync(path.join(JDIR, this.cls + '.java'), src);
    console.log('wrote', this.name, this.W + 'x' + this.H, this.allBoxes().length, 'boxes');
  }
}

function debugCol(p) {
  const C = { top: 0xff4040, bottom: 0x404040, right: 0x4040ff, left: 0x40ff40, front: 0xffff40, back: 0xff40ff };
  let c = hex(C[p.face]);
  if (p.fx === 0 && p.fy === 0) c = [255, 255, 255, 255];
  return c;
}

// ------------------------------------------------------------------------------------------------ materials
const INK = hex(0x10260F);
const EDGED = new Set(["hide", "limb"]);
const MATS = {
  hide: p => {
    let t = p.m.tone(p) + (p.opt.toneAdd || 0);
    t += (vnoise(p.tx, p.ty, 3, 4) - 0.5) * 0.06;
    return rampPx(t, p.tx, p.ty);
  },
  // limbs: flank green, darker at the top, a touch lighter low
  limb: p => {
    let t;
    if (p.face === 'top') t = 0.3; else if (p.face === 'bottom') t = 0.55;
    else t = 0.32 + 0.22 * ((p.fy + 0.5) / p.fh);
    t += (p.opt.toneAdd || 0) + (vnoise(p.tx, p.ty, 4, 4) - 0.5) * 0.06;
    return rampPx(t, p.tx, p.ty);
  },
  belly: p => rampPx(0.86 + (vnoise(p.tx, p.ty, 5, 3) - 0.5) * 0.1 - (p.face === 'top' ? 0.2 : 0), p.tx, p.ty),
  horn: p => {
    // ivory-green keratin, darker at the root (low local y), streaky
    const k = clamp01((p.y - p.bx.o[1]) / p.bx.s[1]);
    let c = mix(hex(0x7E9466), hex(0xC9D6A8), (p.opt.hk0 || 0) + k * (p.opt.hk1 || 0.35) + (rnd(p.tx, 0, 9) - 0.5) * 0.18);
    if (p.face === 'top') c = mix(c, hex(0xE4EDCB), 0.4);
    return c;
  },
  nail: p => {
    let c = mix(hex(0x9AA97E), hex(0xD6E0B8), p.face === 'top' ? 0.8 : 0.45 + (rnd(p.tx, p.ty, 11) - 0.5) * 0.2);
    if (p.face === 'bottom') c = hex(0x6E7D58);
    return c;
  },
  tooth: p => mix(hex(0xDDE8CF), hex(0xF8FFF0), p.face === 'top' ? 0.2 : 0.7),
  claw: p => {
    const k = clamp01((p.bx.o[1] + p.bx.s[1] - p.y) / p.bx.s[1]);
    return mix(hex(0x55614A), hex(0xE2EAD0), p.opt.clawUp ? 1 - k : k);
  },
  dark: p => mix(hex(0x0E1E0E), hex(0x1E361C), rnd(p.tx, p.ty, 13) * 0.5),
  mouth: p => mix(hex(0x5A1F24), hex(0x7A2E30), rnd(p.tx, p.ty, 14) * 0.6),
  tongue: p => mix(hex(0xB6505A), hex(0xD86E76), p.face === 'top' ? 0.7 : 0.2),
  inner: p => { // inner ear: pinkish green, darker deep inside
    const c = mix(hex(0x9E8C78), hex(0xC8B49A), vnoise(p.tx, p.ty, 15, 2));
    return mix(c, hex(0x4A3A30), p.opt.innerDark ? 0.4 : 0);
  },
  // bare leathery skin (gorilla face, chest, palms; Beast's lips)
  face: p => {
    let c = mix(hex(0x2C4A2E), hex(0x4A6D47), vnoise(p.tx, p.ty, 16, 2) * 0.7 + (p.face === 'top' ? 0.2 : 0));
    return c;
  },
  // eyes: only the outward face shows the eye, the rest is skin
  eye: p => eyePx(p),
  // fur fringe: jagged transparent bottom edge on the side faces, no bottom face
  fringe: p => {
    if (p.face === 'bottom') return [0, 0, 0, 0];
    const jag = p.opt.jag || 2;
    if (p.face !== 'top') {
      const cut = Math.floor(rnd(p.tx, 0, 17) * (jag + 1));
      if (p.fy >= p.fh - cut) return [0, 0, 0, 0];
    }
    let t = p.opt.t ? p.m.tone(p) : p.m.tone(p);
    t += (p.opt.toneAdd || 0) + (rnd(p.tx, 3, 19) - 0.5) * 0.25;
    return rampPx(t, p.tx, p.ty);
  },
  glow: p => hex(0xEFFFE0),
};

// eye patterns, front = toward +Z (pupil and glint toward the front, a dark rim)
function eyePx(p) {
  const o = p.opt;
  // fix2: front-facing eyes (gorilla / Beast) paint the front face; they used to get the side face like the rhino / elephant eyes, so
  // from the front they showed as plain skin
  const frontEye = o.pattern === 'front' || o.pattern === 'front3';
  const out = frontEye ? 'front' : o.side > 0 ? 'left' : o.side < 0 ? 'right' : 'front';
  if (p.face !== out) return MATS[o.skin || 'hide'](p);
  const pal = o.fierce
    ? { white: hex(0xF2FFE6), iris: hex(0xB8F05A), pupil: hex(0x0B140A), rim: hex(0x2B4A12), glint: hex(0xFFFFFF) }
    : { white: hex(0xEEF4E2), iris: hex(0x3E8E3A), pupil: hex(0x0B140A), rim: hex(0x1A3318), glint: hex(0xFFFFFF) };
  // normalised face coords with "front" toward +Z: f = 0 back .. 1 front, g = 0 top .. 1 bottom
  let f;
  if (out === 'left') f = 1 - (p.fx + 0.5) / p.fw; else if (out === 'right') f = (p.fx + 0.5) / p.fw; else f = 0.5;
  const g = (p.fy + 0.5) / p.fh;
  const W = p.fw, Hh = p.fh;
  const fi = out === 'left' ? W - 1 - p.fx : out === 'right' ? p.fx : p.fx; // 0 = back column
  const pattern = o.pattern || 'small';
  if (pattern === 'small') { // 2x2: [iris? glint] / [rim, pupil]
    if (fi === W - 1) return p.fy === 0 ? pal.glint : pal.pupil;
    return p.fy === 0 ? pal.pupil : pal.iris;
  }
  if (pattern === 'wide') { // 3x2 or wider: white back, iris, pupil + glint front
    if (fi === W - 1) return p.fy === 0 ? pal.glint : pal.pupil;
    if (fi === W - 2) return p.fy === 0 ? pal.pupil : pal.iris;
    return p.fy === 0 ? pal.white : mix(pal.white, pal.rim, 0.25);
  }
  if (pattern === 'front3') { // fix2: a 3x2 front-facing eye (gorilla) that reads in pixel art: pale sclera both sides of a dark pupil column, a white glint
    const i = o.side > 0 ? p.fx : W - 1 - p.fx; // 0 = inner (toward the nose)
    const sclera = hex(0xDCE6C8), scleraLo = hex(0xB4C29E), pupil = hex(0x0A120A), iris = hex(0x3A2A18);
    if (i === 1) return p.fy === 0 ? pupil : iris;
    if (i === 0) return p.fy === 0 ? pal.glint : scleraLo;
    return p.fy === 0 ? sclera : scleraLo;
  }
  if (pattern === 'front') { // a front-facing eye (gorilla / Beast): fx 0 = viewer's left; inner = toward x 0
    const inner = o.side > 0 ? p.fx === 0 : p.fx === W - 1;
    if (Hh === 1) return inner ? (o.fierce ? pal.iris : pal.pupil) : (o.fierce ? pal.white : pal.glint);
    if (o.fierce) {
      if (p.fy === 0) return inner ? pal.rim : pal.white;
      return inner ? pal.iris : pal.glint;
    }
    if (p.fy === 0) return inner ? pal.glint : pal.white;
    return inner ? pal.pupil : pal.iris;
  }
  return pal.pupil;
}

// mirrored x helpers: X(s, x0, w) = the box origin for side s (+1 left, -1 right) of a box drawn on the left at x0..x0+w
const X = (s, x0, w) => (s > 0 ? x0 : -x0 - w);
const SX = (s, x) => s * x;
const LR = s => (s > 0 ? 'L' : 'R');
const sideFace = p => p.face === 'left' || p.face === 'right';

// thick wrinkled skin (rhino, elephant): pits, bumps, wrinkle rows, fold lines
function skinDetail(p, k = {}) {
  let c = p.col;
  const r = rnd(p.tx, p.ty, 21);
  if (r < (k.pit || 0.07)) c = shade(c, 0.87); else if (r > 0.97) c = mix(c, hex(0xE0F2C8), 0.16);
  if (p.opt.wrinkle && p.face !== 'top' && p.face !== 'bottom') {
    const per = p.opt.wrinkle;
    const yy = p.fy + Math.round((vnoise(p.tx, 0, 23, 3) - 0.5) * 1.6);
    if (((yy % per) + per) % per === per - 1 && rnd(p.tx, p.ty, 24) > 0.15) c = shade(c, 0.76);
  }
  if (p.opt.folds && (sideFace(p) || p.face === 'top')) {
    for (const zf of p.opt.folds) {
      const dz = Math.abs(p.z - zf) - (vnoise(p.y, zf, 25, 3) - 0.5) * 0.8;
      if (dz < 0.55) c = shade(c, 0.72); else if (dz < 1.3 && p.z > zf) c = mix(c, hex(0xD8F0BE), 0.1);
    }
  }
  if (k.crackle && p.face !== 'bottom') { // elephant hide: a net of fine cracks
    const n = vnoise(p.tx, p.ty, 31, 3);
    if (Math.abs(n - 0.5) < 0.045) c = shade(c, 0.82);
  }
  if (p.opt.plate && p.face !== 'top' && p.face !== 'bottom') {
    if (p.fy === p.fh - 1) c = shade(c, 0.7);
    if (sideFace(p) && (p.fx === 0 || p.fx === p.fw - 1)) c = shade(c, 0.8);
  }
  return c;
}

// fur: strands running down the faces, lighter tips, dark roots between clumps
function furDetail(p, k = {}) {
  let c = p.col;
  const n = vnoise(p.tx * 1.0, p.ty * 0.3, k.seed || 41, 2);
  if (n > 0.72) c = mix(c, hex(0xD0EEB0), 0.16); else if (n < 0.26) c = shade(c, 0.82);
  if (rnd(p.tx, p.ty, 43) < 0.05) c = shade(c, 0.85);
  return c;
}

// ================================================================================================ RHINO
function rhino() {
  const M = new Model('rhino', 'RhinoGeo', {
    backY: 26, bellyY: 9,
    detail: p => (['hide', 'limb'].includes(p.opt.mat) ? skinDetail(p) : p.col),
  });
  const B = M.bone.bind(M);
  B('body', null, [0, 16, -10])
    .box([-9, 10, -15], [18, 13, 26], { folds: [0.5, -6.5] })
    .box([-10, 9, 1], [20, 15, 11], { plate: true, toneAdd: -0.03 })
    .box([-10, 11, -17], [20, 12, 10], { plate: true, toneAdd: -0.03 })
    .box([-8, 12, -19], [16, 9, 2], { toneAdd: -0.02 })
    .box([-8, 8, -12], [16, 3, 22], { t: [0.55, 0.92] })
    .box([-6, 24, 0], [12, 2, 9], { toneAdd: -0.05 })
    .box([-6.5, 12, 11], [13, 11, 6], { folds: [13, 15.5] });
  B('head', 'body', [0, 19, 15])
    .box([-5.5, 13, 14], [11, 10, 10], {
      deco: p => {
        if (sideFace(p) && p.z > 19.5 && p.z < 21.5 && p.y > 17 && p.y < 19) return p.y < 18 ? shade(p.col, 0.8) : shade(p.col, 0.6);
        if (sideFace(p) && Math.abs(p.z - (16.5 + (23 - p.y) * 0.35)) < 0.5 && p.y < 17) return shade(p.col, 0.78);
      },
    })
    .box([-4.5, 11, 24], [9, 8, 6], {
      deco: p => {
        if (p.face === 'front' && p.fy === 5 && (p.fx === 1 || p.fx === p.fw - 2)) return hex(0x142A12);
        if (p.face === 'front' && p.fy === 4 && (p.fx === 1 || p.fx === p.fw - 2)) return shade(p.col, 0.78);
        if (sideFace(p) && p.fy === p.fh - 1 && p.z > 26) return shade(p.col, 0.7);
      },
    })
    .box([-5, 9, 26], [10, 3, 5], { t: [0.5, 0.8], deco: p => { if (p.face !== 'top' && p.fy === p.fh - 1) return shade(p.col, 0.72); } });
  B('jaw', 'head', [0, 13, 17]).box([-4, 10, 16.5], [8, 4, 9], { t: [0.6, 0.9], deco: p => { if (p.face === 'top') return MATS.mouth(p); } });
  B('hornA', 'head', [0, 19, 27]).box([-2, 18.5, 25], [4, 4, 4], { mat: 'horn', hk0: 0, hk1: 0.25 });
  B('hornB', 'hornA', [0, 22.5, 27], [-0.22, 0, 0]).box([-1.5, 22.5, 25.5], [3, 4, 3], { mat: 'horn', hk0: 0.25, hk1: 0.25 });
  B('hornC', 'hornB', [0, 26.5, 27], [-0.32, 0, 0]).box([-1, 26.5, 26], [2, 3, 2], { mat: 'horn', hk0: 0.5, hk1: 0.25 });
  B('hornD', 'hornC', [0, 29.5, 27], [-0.4, 0, 0]).box([-0.5, 29.5, 26.5], [1, 2, 1], { mat: 'horn', hk0: 0.75, hk1: 0.25 });
  B('horn2A', 'head', [0, 23, 21]).box([-1.5, 22.5, 19.5], [3, 3, 3], { mat: 'horn', hk0: 0.1, hk1: 0.3 });
  B('horn2B', 'horn2A', [0, 25.5, 21], [-0.25, 0, 0]).box([-1, 25.5, 20], [2, 2, 2], { mat: 'horn', hk0: 0.45, hk1: 0.3 });
  for (const s of [1, -1]) {
    const L = LR(s);
    B('cheek' + L, 'head', [SX(s, 5), 15, 18]).box([X(s, 4.5, 2), 13, 15], [2, 5, 6], { deco: p => { if (sideFace(p) && (p.fy === 1 || p.fy === 3) && p.fx > 0 && p.fx < p.fw - 1) return shade(p.col, 0.78); } });
    B('ear' + L, 'head', [SX(s, 4.5), 22.5, 15.5], [0, 0, s * 0.35])
      .box([X(s, 3.5, 3), 22.5, 14.5], [3, 5, 2], { deco: p => { if (p.face === 'front' && p.fx === 1 && p.fy >= 1) return MATS.inner(Object.assign({}, p, { opt: { innerDark: p.fy > 2 } })); } });
    B('eye' + L, 'head', [SX(s, 5.5), 18, 20.5]).box([X(s, 5, 1), 17, 19.5], [1, 2, 2], { mat: 'eye', side: s });
    B('brow' + L, 'head', [SX(s, 5.5), 19.5, 20.5]).box([X(s, 4.5, 2), 19, 19], [2, 1, 3], { toneAdd: -0.05 });
    B('legF' + L, 'body', [SX(s, 6.5), 14, 6.5]).box([X(s, 3.5, 6), 7, 3.5], [6, 8, 6], { mat: 'limb' });
    B('shinF' + L, 'legF' + L, [SX(s, 6.5), 7.5, 6.5])
      .box([X(s, 4, 5), 2, 4], [5, 6, 5], { mat: 'limb', wrinkle: 2, toneAdd: 0.04 })
      .box([X(s, 4, 5), 6.5, 4], [5, 1, 5], { mat: 'limb', inflate: 0.4, toneAdd: -0.04 });
    B('footF' + L, 'shinF' + L, [SX(s, 6.5), 2.5, 6.5])
      .box([X(s, 3.5, 6), 0, 3.5], [6, 2, 7], { mat: 'limb', toneAdd: 0.06 })
      .box([X(s, 3.7, 1), 0, 10.3], [1, 1, 1], { mat: 'nail' })
      .box([X(s, 6, 1), 0, 10.6], [1, 1, 1], { mat: 'nail' })
      .box([X(s, 8.3, 1), 0, 10.3], [1, 1, 1], { mat: 'nail' });
    B('legH' + L, null, [SX(s, 6), 15, -11]).box([X(s, 2.5, 7), 8, -14.5], [7, 8, 7], { mat: 'limb', toneAdd: -0.05 });
    B('shinH' + L, 'legH' + L, [SX(s, 6), 8.5, -11])
      .box([X(s, 3.5, 5), 2, -13.5], [5, 7, 5], { mat: 'limb', wrinkle: 2, toneAdd: 0.04 })
      .box([X(s, 3.5, 5), 7.5, -13.5], [5, 1, 5], { mat: 'limb', inflate: 0.4, toneAdd: -0.04 });
    B('footH' + L, 'shinH' + L, [SX(s, 6), 2.5, -11])
      .box([X(s, 3, 6), 0, -14], [6, 2, 7], { mat: 'limb', toneAdd: 0.06 })
      .box([X(s, 3.2, 1), 0, -7.2], [1, 1, 1], { mat: 'nail' })
      .box([X(s, 5.5, 1), 0, -6.9], [1, 1, 1], { mat: 'nail' })
      .box([X(s, 7.8, 1), 0, -7.2], [1, 1, 1], { mat: 'nail' });
  }
  B('tail1', 'body', [0, 21.5, -18]).box([-1, 17.5, -19], [2, 4, 2], { mat: 'limb', toneAdd: -0.1 });
  B('tail2', 'tail1', [0, 17.5, -18]).box([-0.5, 14, -18.5], [1, 4, 1], { mat: 'limb', toneAdd: -0.1 });
  B('tuft', 'tail2', [0, 14, -18]).box([-1, 11, -19], [2, 3, 2], { mat: 'fringe', jag: 2, t: [0.0, 0.1] });
  return M;
}

// ================================================================================================ ELEPHANT
function elephant() {
  const M = new Model('elephant', 'ElephantGeo', {
    backY: 41, bellyY: 9,
    detail: p => (['hide', 'limb'].includes(p.opt.mat) ? skinDetail(p, { crackle: true, pit: 0.05 }) : p.col),
  });
  const B = M.bone.bind(M);
  B('body', null, [0, 28, -12])
    .box([-12, 16, -18], [24, 21, 34], { folds: [-9.5, -2, 6, 12.5] })
    .box([-12.5, 17, 2], [25, 22, 13], { toneAdd: -0.02 })
    .box([-11, 18, -20], [22, 17, 6], { toneAdd: -0.02 })
    .box([-10, 14, -12], [20, 3, 24], { t: [0.55, 0.92] })
    .box([-9, 22, 15], [18, 14, 8], { wrinkle: 3 })
    .box([-9, 38, 3], [18, 3, 11], { toneAdd: -0.04 })
    .box([-12.5, 15, -10], [25, 7, 20], { toneAdd: 0.02 });
  B('head', 'body', [0, 32, 22])
    .box([-8, 25, 21], [16, 15, 11], { deco: p => { if (sideFace(p) && p.z > 26.5 && p.z < 29.5 && p.y > 30 && p.y < 32) return p.y < 31 ? shade(p.col, 0.8) : shade(p.col, 0.62); } })
    .box([-6.5, 40, 22], [13, 2, 8], { toneAdd: -0.04 })
    .box([-7, 35, 31], [14, 3, 2], { toneAdd: -0.02, wrinkle: 2 })
    .box([-5, 22, 30], [10, 8, 4], { wrinkle: 2 });
  B('jaw', 'head', [0, 23, 29]).box([-3, 19, 27], [6, 3, 5], { t: [0.55, 0.85], deco: p => { if (p.face === 'top') return MATS.mouth(p); } });
  // the trunk: seven links, tapering, ringed
  const tr = [[0, 27, 33, -3, 22, 30.5, 6, 6, 5], [0, 22.5, 33, -2.5, 17.5, 30.5, 5, 5, 5], [0, 18, 33, -2, 13, 31, 4, 5, 4],
    [0, 13.5, 33, -2, 9, 31, 4, 5, 4], [0, 9.5, 33, -1.5, 5.5, 31.5, 3, 4, 3], [0, 6, 33, -1.5, 2.5, 31.5, 3, 4, 3], [0, 3, 33, -2, 0.5, 31, 4, 3, 4]];
  tr.forEach((q, i) => {
    B('trunk' + (i + 1), i === 0 ? 'head' : 'trunk' + i, [q[0], q[1], q[2]]).box([q[3], q[4], q[5]], [q[6], q[7], q[8]], {
      wrinkle: 2, ft: { front: [0.45, 0.62], back: [0.55, 0.7] },
      deco: i === 6 ? (p => { if (p.face === 'bottom' && p.fy > 0 && p.fy < p.fh - 1 && (p.fx === 1 || p.fx === 2)) return hex(0x132812); }) : undefined,
    });
  });
  for (const s of [1, -1]) {
    const L = LR(s);
    B('eye' + L, 'head', [SX(s, 8), 32, 28.5]).box([X(s, 7.5, 1), 31, 27], [1, 2, 3], { mat: 'eye', side: s, pattern: 'wide' });
    B('brow' + L, 'head', [SX(s, 8), 33.5, 28.5]).box([X(s, 7, 2), 33, 26.5], [2, 1, 4], { toneAdd: -0.06 });
    B('tusk' + L + '1', 'head', [SX(s, 3.5), 23, 29], [-0.3, 0, 0]).box([X(s, 2.5, 2), 18, 28], [2, 5, 2], { mat: 'tooth' });
    B('tusk' + L + '2', 'tusk' + L + '1', [SX(s, 3.5), 18, 29], [-0.55, 0, 0]).box([X(s, 2.5, 2), 13.5, 28], [2, 5, 2], { mat: 'tooth', inflate: -0.2 });
    B('tusk' + L + '3', 'tusk' + L + '2', [SX(s, 3.5), 13.5, 29], [-0.6, 0, 0]).box([X(s, 3, 1), 10.5, 28.5], [1, 3, 1], { mat: 'tooth' });
    const inner = s > 0 ? 'right' : 'left';
    const earDeco = p => {
      if (p.face === inner) { // the inner face: pink-green with veins
        let c = MATS.inner(Object.assign({}, p, { opt: {} }));
        if (Math.abs(vnoise(p.tx, p.ty, 51, 3) - 0.5) < 0.06) c = shade(c, 0.78);
        return c;
      }
      if (sideFace(p) && (p.fy === p.fh - 1 || p.fx === p.fw - 1 || p.fx === 0)) return shade(p.col, 0.7);
      if (sideFace(p) && Math.abs(vnoise(p.tx, p.ty, 53, 4) - 0.5) < 0.05) return shade(p.col, 0.84);
    };
    B('ear' + L, 'head', [SX(s, 9), 38, 27])
      .box([X(s, 9, 1), 20, 13], [1, 18, 14], { deco: earDeco, toneAdd: 0.08, ft: { top: 0.2 } })
      .box([X(s, 9, 1), 38, 15], [1, 2, 11], { deco: earDeco, toneAdd: 0.08 })
      .box([X(s, 9, 1), 16, 16], [1, 4, 10], { deco: earDeco, toneAdd: 0.08 })
      .box([X(s, 9, 1), 23, 11], [1, 13, 2], { deco: earDeco, toneAdd: 0.08 });
    B('legF' + L, 'body', [SX(s, 8.5), 24, 8]).box([X(s, 5, 7), 13, 4.5], [7, 12, 7], { mat: 'limb', wrinkle: 3 });
    B('shinF' + L, 'legF' + L, [SX(s, 8.5), 13.5, 8])
      .box([X(s, 5.5, 6), 3, 5], [6, 11, 6], { mat: 'limb', wrinkle: 2, toneAdd: 0.04 })
      .box([X(s, 5.5, 6), 12.5, 5], [6, 1, 6], { mat: 'limb', inflate: 0.45, toneAdd: -0.05 });
    B('footF' + L, 'shinF' + L, [SX(s, 8.5), 3.5, 8])
      .box([X(s, 4.5, 8), 0, 4], [8, 3, 8], { mat: 'limb', toneAdd: 0.08 })
      .box([X(s, 4.8, 1), 0, 11.8], [1, 1, 1], { mat: 'nail' }).box([X(s, 6.9, 1), 0, 12.1], [1, 1, 1], { mat: 'nail' })
      .box([X(s, 9.1, 1), 0, 12.1], [1, 1, 1], { mat: 'nail' }).box([X(s, 11.2, 1), 0, 11.8], [1, 1, 1], { mat: 'nail' });
    B('legH' + L, null, [SX(s, 8), 25, -12]).box([X(s, 4.5, 7), 13, -15.5], [7, 13, 7], { mat: 'limb', wrinkle: 3, toneAdd: -0.04 });
    B('shinH' + L, 'legH' + L, [SX(s, 8), 13.5, -12])
      .box([X(s, 5, 6), 3, -15], [6, 11, 6], { mat: 'limb', wrinkle: 2, toneAdd: 0.04 })
      .box([X(s, 5, 6), 12.5, -15], [6, 1, 6], { mat: 'limb', inflate: 0.45, toneAdd: -0.05 });
    B('footH' + L, 'shinH' + L, [SX(s, 8), 3.5, -12])
      .box([X(s, 4, 8), 0, -16], [8, 3, 8], { mat: 'limb', toneAdd: 0.08 })
      .box([X(s, 4.3, 1), 0, -8.2], [1, 1, 1], { mat: 'nail' }).box([X(s, 6.4, 1), 0, -7.9], [1, 1, 1], { mat: 'nail' })
      .box([X(s, 8.6, 1), 0, -7.9], [1, 1, 1], { mat: 'nail' }).box([X(s, 10.7, 1), 0, -8.2], [1, 1, 1], { mat: 'nail' });
  }
  B('tail1', 'body', [0, 34, -20]).box([-0.5, 28, -21], [1, 6, 1], { mat: 'limb', toneAdd: -0.1 });
  B('tail2', 'tail1', [0, 28, -20.5]).box([-0.5, 23, -21], [1, 5, 1], { mat: 'limb', toneAdd: -0.1 });
  B('tuft', 'tail2', [0, 23, -20.5]).box([-1, 20, -21.5], [2, 3, 2], { mat: 'fringe', jag: 2, t: [0.0, 0.1] });
  return M;
}

// ================================================================================================ GORILLA
// The torso is built upright (Y = up the spine) and pitched forward by the animation, so its countershading is set per face:
// back (-Z) faces up = dark, the front (chest) faces down = light, the sides darken toward the back edge.
function gorilla() {
  const M = new Model('gorilla', 'GorillaGeo', {
    backY: 30, bellyY: 8,
    detail: p => {
      if (['hide', 'limb', 'fringe'].includes(p.opt.mat)) {
        let c = furDetail(p);
        if (p.opt.saddle && (p.face === 'back' || p.face === 'top')) { if (p.face === 'back' && p.fx > 1 && p.fx < p.fw - 2) c = mix(c, hex(0xB8DDA0), 0.12); } // a hint of the lighter "silver" saddle
        return c;
      }
      if (p.opt.mat === 'face' && rnd(p.tx, p.ty, 61) < 0.08) return shade(p.col, 1.18);
      return p.col;
    },
  });
  const B = M.bone.bind(M);
  const torso = (top, front) => ({ sideZ: [0.12, 0.4, 0.12], ft: { back: [0.08, 0.16], top, front, bottom: 0.55 } });
  B('body', null, [0, 14, -3])
    .box([-5.5, 12, -7], [11, 6, 8], Object.assign(torso(0.3, [0.45, 0.55]), { saddle: true }))
    .box([-6.5, 17, -7], [13, 6, 10], Object.assign(torso(0.25, [0.5, 0.6]), { saddle: true }))
    .box([-8, 22, -7.5], [16, 8, 10], torso(0.14, [0.4, 0.45]))
    .box([-10, 26, -6.5], [20, 6, 8], torso(0.1, [0.3, 0.4]))
    .box([-5, 31, -6], [10, 3, 6], torso(0.08, 0.2))
    .box([-6, 23, 2.5], [12, 6, 1], { mat: 'face', deco: p => { if (p.face === 'front' && (p.fx === 5 || p.fx === 6) && p.fy < 5) return shade(p.col, 0.8); } });
  B('head', 'body', [0, 31, 0])
    .box([-4, 30, -1], [8, 7, 7], { ft: { top: 0.1, back: 0.15, front: 0.35, bottom: 0.5 }, sideZ: [0.15, 0.35, 0.1] })
    .box([-2.5, 37, -1.5], [5, 2, 6], { ft: { top: 0.05, front: 0.15, back: 0.1 }, sideZ: [0.08, 0.15] })
    .box([-4.5, 34, 5], [9, 2, 4], { mat: 'face', deco: p => { if (p.face === 'top') return shade(p.col, 0.85); if (p.face === 'bottom') return shade(p.col, 0.55); } })
    .box([-3.5, 31, 5], [7, 3, 3], { mat: 'face' })
    .box([-3.5, 28, 5], [7, 3, 4], { mat: 'face', deco: p => { if (p.face === 'front' && p.fy === 2) return hex(0x102010); return shade(p.col, 1.18); } })
    .box([-1.5, 31, 8], [3, 2, 1], { mat: 'face', deco: p => { if (p.face === 'front' && p.fy === 1 && (p.fx === 0 || p.fx === 2)) return hex(0x0A160A); if (p.face === 'front' && p.fy === 0) return shade(p.col, 1.2); } });
  B('jaw', 'head', [0, 29, 3])
    .box([-3, 26, 2], [6, 3, 6], { mat: 'face', deco: p => { if (p.face === 'top') return MATS.mouth(p); if (p.face === 'front' && p.fy === 0) return hex(0xE6EED8); } })
    .box([1.2, 28.6, 6.6], [1, 1, 1], { mat: 'tooth' }).box([-2.2, 28.6, 6.6], [1, 1, 1], { mat: 'tooth' });
  for (const s of [1, -1]) {
    const L = LR(s);
    // fix2: the eyes were 2x2, set back under the brow and half behind the nose (unreadable); now 3x2, out beside the nose and flush
    // with the brow / muzzle front so they show from the front and 3/4
    B('eye' + L, 'head', [SX(s, 2.5), 33, 8.52]).box([X(s, 1, 3), 32, 8.02], [3, 2, 1], { mat: 'eye', side: s, pattern: 'front3', skin: 'face' });
    B('ear' + L, 'head', [SX(s, 4.5), 33, 2]).box([X(s, 4, 1), 32, 1], [1, 2, 2], { mat: 'face' });
    B('cheek' + L, 'head', [SX(s, 4), 31, 3]).box([X(s, 3.5, 1), 28, 2], [1, 5, 4], { mat: 'fringe', jag: 2, ft: { top: 0.2, front: [0.3, 0.4], back: [0.2, 0.3] }, sideZ: [0.2, 0.35] });
    B('arm' + L, 'body', [SX(s, 10), 29, -2.5])
      .box([X(s, 7.5, 6), 26, -6], [6, 6, 7], { sideZ: [0.12, 0.35], ft: { top: 0.1, back: 0.12, front: 0.35 } })
      .box([X(s, 8, 5), 17, -5], [5, 10, 5], { mat: 'limb' })
      .box([X(s, 8.5, 4), 14, -6.5], [4, 4, 2], { mat: 'fringe', jag: 2, t: [0.25, 0.4] });
    B('fore' + L, 'arm' + L, [SX(s, 10.5), 17.5, -2.5])
      .box([X(s, 7.5, 6), 7, -5.5], [6, 11, 6], { mat: 'limb', toneAdd: -0.03 })
      .box([X(s, 8, 5), 9, -7], [5, 8, 2], { mat: 'fringe', jag: 3, t: [0.2, 0.35] });
    B('hand' + L, 'fore' + L, [SX(s, 10.5), 7.5, -2.5])
      .box([X(s, 7.5, 6), 3, -5], [6, 5, 5], { mat: 'face' })
      .box([X(s, 7.5, 6), 1, -1], [6, 3, 2], { mat: 'face', deco: p => { if ((p.face === 'front' || p.face === 'bottom') && p.fx % 2 === 1 && p.fx < 5) return shade(p.col, 0.6); if (p.face === 'front' && p.fy === 0) return shade(p.col, 1.25); } })
      .box([X(s, 7, 1), 3, -3], [1, 2, 2], { mat: 'face' });
    B('leg' + L, null, [SX(s, 4.5), 14, -4]).box([X(s, 2, 5), 8, -7], [5, 7, 6], { mat: 'limb', toneAdd: -0.04 });
    B('shin' + L, 'leg' + L, [SX(s, 4.5), 8.5, -4]).box([X(s, 2.5, 4), 2, -6.5], [4, 7, 5], { mat: 'limb' });
    B('foot' + L, 'shin' + L, [SX(s, 4.5), 2.5, -4]).box([X(s, 2, 5), 0, -6], [5, 2, 8], { mat: 'face', deco: p => { if (p.face === 'front' && p.fx % 2 === 0 && p.fy === 1) return shade(p.col, 0.6); if (p.face === 'top' && p.fy > 5 && p.fx % 2 === 0) return shade(p.col, 0.75); } });
  }
  return M;
}

// ================================================================================================ BEAST
function beast() {
  const M = new Model('beast', 'BeastGeo', {
    backY: 40, bellyY: 12,
    detail: p => (['hide', 'limb', 'fringe'].includes(p.opt.mat) ? furDetail(p, { seed: 71 }) : p.col),
  });
  const B = M.bone.bind(M);
  const torso = (top, front) => ({ sideZ: [0.14, 0.42, 0.1], ft: { back: [0.06, 0.14], top, front, bottom: 0.6 }, toneAdd: 0.04 });
  const claw = { mat: 'claw', clawUp: false };
  B('body', null, [0, 19, -2])
    .box([-5, 17, -5], [10, 6, 7], torso(0.3, [0.5, 0.6]))
    .box([-5.5, 22, -5], [11, 5, 8], torso(0.28, [0.55, 0.62]))
    .box([-8, 26, -6], [16, 9, 11], torso(0.18, [0.45, 0.5]))
    .box([-12, 31, -5.5], [24, 7, 10], torso(0.1, [0.32, 0.42]))
    .box([-6, 37, -5], [12, 4, 8], torso(0.06, 0.2))
    .box([-6, 27, 4.5], [12, 7, 1], { mat: 'fringe', jag: 2, ft: { front: [0.68, 0.8], top: 0.6, left: 0.65, right: 0.65, back: 0.6 } })
    .box([-4, 21, 2.5], [8, 6, 1], { ft: { front: [0.66, 0.74], left: 0.6, right: 0.6, top: 0.6 }, deco: p => { if (p.face === 'front' && (p.fx === 3 || p.fx === 4 || p.fy === 2)) return shade(p.col, 0.82); } })
    .box([-7, 30, -7.5], [14, 10, 3], { mat: 'fringe', jag: 3, ft: { back: [0.0, 0.08], top: 0.04, left: 0.08, right: 0.08, front: 0.1 } })
    .box([-2, 23, -6.5], [4, 8, 2], { mat: 'fringe', jag: 2, ft: { back: [0.02, 0.1], top: 0.05, left: 0.08, right: 0.08 } });
  B('head', 'body', [0, 37, 2])
    .box([-4.5, 35, 0], [9, 7, 7], { ft: { top: 0.08, back: 0.12, front: 0.3, bottom: 0.55 }, sideZ: [0.12, 0.3, 0.12] })
    .box([-4.5, 40, 6], [9, 2, 2], { ft: { top: 0.06, front: 0.1, bottom: 0.05 }, sideZ: [0.08, 0.1] })
    .box([-2.5, 35.5, 7], [5, 4, 6], {
      ft: { top: 0.3, front: [0.35, 0.55], bottom: 0.7 }, sideZ: [0.3, 0.4, 0.3],
      deco: p => { if (sideFace(p) && p.fy === p.fh - 1 && p.z > 8) return hex(0x3A1418); if (p.face === 'top' && p.fx === 2 && p.fy < 3) return shade(p.col, 0.85); },
    })
    .box([-1.5, 39, 12.4], [3, 1, 1], { mat: 'dark', deco: p => { if (p.face === 'top' && p.fx === 1) return hex(0x5E705A); } })
    .box([1.3, 34.5, 11.6], [1, 1, 1], { mat: 'tooth' }).box([-2.3, 34.5, 11.6], [1, 1, 1], { mat: 'tooth' })
    .box([0.4, 34.8, 9.4], [1, 1, 1], { mat: 'tooth', inflate: -0.15 }).box([-1.4, 34.8, 9.4], [1, 1, 1], { mat: 'tooth', inflate: -0.15 });
  B('jaw', 'head', [0, 36, 4])
    .box([-2.5, 33.5, 3.5], [5, 2, 9], { ft: { top: 0.0, front: 0.6, bottom: 0.72 }, sideZ: [0.5, 0.6], deco: p => { if (p.face === 'top') return MATS.mouth(p); if (sideFace(p) && p.fy === 0 && p.z > 6) return hex(0x3A1418); } })
    .box([1.2, 35.5, 11.2], [1, 1, 1], { mat: 'tooth' }).box([-2.2, 35.5, 11.2], [1, 1, 1], { mat: 'tooth' })
    .box([-1.5, 34.4, 5.5], [3, 1, 5], { mat: 'tongue', inflate: -0.1 });
  for (const s of [1, -1]) {
    const L = LR(s);
    // fix2: fully under the brow (its top row was hidden behind it) and flush with the brow front
    B('eye' + L, 'head', [SX(s, 3.5), 39, 7.55]).box([X(s, 2.5, 2), 38, 7.05], [2, 2, 1], { mat: 'eye', side: s, pattern: 'front', fierce: true, skin: 'hide' });
    B('cheek' + L, 'head', [SX(s, 5), 36, 3]).box([X(s, 4, 2), 33, 0], [2, 6, 6], { mat: 'fringe', jag: 2, ft: { top: 0.2, front: [0.3, 0.45], back: [0.15, 0.3] }, sideZ: [0.15, 0.35] });
    const inner = p => { if (p.face === 'front' && p.fx > 0 && p.fx < p.fw - 1 && p.fy > 0) return MATS.inner(Object.assign({}, p, { opt: { innerDark: true } })); };
    B('ear' + L, 'head', [SX(s, 3.5), 41.5, 2], [-0.45, 0, s * 0.3])
      .box([X(s, 2, 3), 41, 1.5], [3, 4, 1], { ft: { front: 0.3, back: 0.1, top: 0.08 }, sideZ: [0.1, 0.2], deco: inner })
      .box([X(s, 2.5, 2), 45, 1.5], [2, 2, 1], { ft: { front: 0.2, back: 0.06, top: 0.04 }, sideZ: [0.05, 0.12] });
    B('arm' + L, 'body', [SX(s, 11), 35, 0])
      .box([X(s, 9, 6), 32, -3.5], [6, 6, 7], { sideZ: [0.1, 0.32], ft: { top: 0.06, back: 0.1, front: 0.3 }, toneAdd: 0.04 })
      .box([X(s, 9.5, 5), 23, -2.5], [5, 10, 5], { mat: 'limb', toneAdd: 0.02 })
      .box([X(s, 10, 4), 20, -4.5], [4, 4, 2], { mat: 'fringe', jag: 2, t: [0.15, 0.3] });
    B('fore' + L, 'arm' + L, [SX(s, 12), 23.5, 0])
      .box([X(s, 9, 6), 12, -3], [6, 12, 6], { mat: 'limb' })
      .box([X(s, 9.5, 5), 14, -4.5], [5, 9, 2], { mat: 'fringe', jag: 3, t: [0.12, 0.3] });
    const hand = B('hand' + L, 'fore' + L, [SX(s, 12), 12.5, 0])
      .box([X(s, 9, 6), 7, -2.5], [6, 5, 5], { mat: 'limb', toneAdd: 0.1 })
      .box([X(s, 8.3, 1), 6, -0.5], [1, 3, 1], { mat: 'limb', toneAdd: 0.12 }).box([X(s, 8.3, 1), 4, -0.5], [1, 2, 1], claw);
    for (let k = 0; k < 4; k++) {
      const fx = 9.2 + k * 1.45;
      hand.box([X(s, fx, 1), 4, 1], [1, 3, 1], { mat: 'limb', toneAdd: 0.12 }).box([X(s, fx, 1), 2, 1.2], [1, 2, 1], claw);
    }
    B('leg' + L, null, [SX(s, 5), 19, -2]).box([X(s, 2.5, 6), 11, -5.5], [6, 9, 7], { mat: 'limb' });
    B('shin' + L, 'leg' + L, [SX(s, 5.5), 11.5, -2]).box([X(s, 3, 5), 4.5, -4.5], [5, 7, 5], { mat: 'limb', toneAdd: 0.04 });
    B('meta' + L, 'shin' + L, [SX(s, 5.5), 5, -2]).box([X(s, 3.5, 4), 0, -3.5], [4, 5, 3], { mat: 'limb', toneAdd: 0.06 });
    B('toes' + L, 'meta' + L, [SX(s, 5.5), 0.5, -2])
      .box([X(s, 3, 5), -0.5, -2.5], [5, 2, 5], { mat: 'limb', toneAdd: 0.1, deco: p => { if (p.face === 'front' && p.fx % 2 === 1) return shade(p.col, 0.75); } })
      .box([X(s, 3.2, 1), -0.5, 2.5], [1, 1, 1], claw).box([X(s, 5, 1), -0.5, 2.7], [1, 1, 1], claw).box([X(s, 6.8, 1), -0.5, 2.5], [1, 1, 1], claw);
  }
  return M;
}

// ------------------------------------------------------------------------------------------------ main
const ALL = { rhino, elephant, gorilla, beast };
const want = process.argv.slice(2);
for (const [k, fn] of Object.entries(ALL)) {
  if (want.length && !want.includes(k)) continue;
  fn().write();
}
