// v0.17.3 hq round: Beast Boy's land mammals (wolf, cheetah, tiger, bear, mouse) as blocky Minecraft-style box models.
// One spec per animal (bones, pivots, boxes) is the single source of truth. This script:
//   1. packs every box's box-UV region into the animal's texture,
//   2. paints the texture as pixel art from each texel's 3D rest position (so countershading, spots and stripes run continuously
//      across boxes): dark back, lighter flanks, pale belly, fur noise, face details (eyes with pupils + highlights, nose, teeth, claws),
//   3. writes src/main/resources/assets/projecthero/textures/entity/beastboy/art/<animal>.png and
//      src/client/java/com/projecthero/mod/client/beastboy/model/<Animal>Parts.java (the LayerDefinition the animator poses).
// Units: 1 = 1/32 block (twice vanilla's texel density). Spec coords are "friendly": +Y up, +Z the animal's front, +X its left, the
// ground at y = 0. The Java converts to vanilla model space (Y down, front -Z): pivot (x, -y, -z), box min (x, -(y+h), -(z+d)),
// rotation (rx, -ry, -rz); the renderer scales by (1, -1, -1) / 32.
// Usage: node scratchpad/beastboy/gen_land_mammals_tex.js [animal...]
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');
const ROOT = path.join(__dirname, '..', '..');
const TEX_DIR = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/beastboy/art');
const JAVA_DIR = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/beastboy/model');

// ------------------------------------------------------------------------------------------------------------ colour + noise
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return [0, 1, 2, 3].map(i => (a[i] ?? 255) + ((b[i] ?? 255) - (a[i] ?? 255)) * t); };
const mul = (c, k) => [c[0] * k, c[1] * k, c[2] * k, c[3] ?? 255];
const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const frac = x => x - Math.floor(x);
function rnd(x, y, z, s = 1) {
  let n = (Math.floor(x) * 374761393 + Math.floor(y) * 668265263 + Math.floor(z) * 1440662683 + s * 2147483647) | 0;
  n = Math.imul(n ^ (n >>> 13), 1274126177);
  return ((n ^ (n >>> 16)) >>> 0) / 4294967295;
}
function vnoise3(x, y, z, s) {
  const x0 = Math.floor(x), y0 = Math.floor(y), z0 = Math.floor(z), fx = x - x0, fy = y - y0, fz = z - z0;
  const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy), sz = fz * fz * (3 - 2 * fz);
  const h = (i, j, k) => rnd(x0 + i, y0 + j, z0 + k, s);
  const l = (a, b, t) => a + (b - a) * t;
  return l(l(l(h(0, 0, 0), h(1, 0, 0), sx), l(h(0, 1, 0), h(1, 1, 0), sx), sy), l(l(h(0, 0, 1), h(1, 0, 1), sx), l(h(0, 1, 1), h(1, 1, 1), sx), sy), sz);
}

// ------------------------------------------------------------------------------------------------------------ spec helpers
// bone(name, pivot (relative to the parent's pivot), boxes, children, rest rotation [pitch, yaw, roll] friendly)
const bone = (name, pivot, boxes = [], children = [], rot = [0, 0, 0]) => ({ name, pivot, boxes, children, rot });
// box(min corner relative to the bone pivot, integer size, paint role, extra options: band [yTop, yBottom] world, g inflate, tone...)
const box = (o, s, paint, opt = {}) => ({ o, s, paint, ...opt });

// ------------------------------------------------------------------------------------------------------------ the engine
function build(spec) {
  const boxes = [];
  (function walk(b, base) {
    const p = [base[0] + b.pivot[0], base[1] + b.pivot[1], base[2] + b.pivot[2]];
    b.world = p;
    for (const x of b.boxes) {
      x.bone = b.name;
      x.min = [p[0] + x.o[0], p[1] + x.o[1], p[2] + x.o[2]];
      x.max = [x.min[0] + x.s[0], x.min[1] + x.s[1], x.min[2] + x.s[2]];
      for (const k of x.s) if (k !== Math.round(k)) throw new Error(spec.name + ': box size must be integer in ' + b.name);
      boxes.push(x);
    }
    for (const c of b.children) walk(c, p);
  })(spec.root, [0, 0, 0]);
  // shelf packing, tallest first
  const order = boxes.slice().sort((a, b) => (b.s[2] + b.s[1]) - (a.s[2] + a.s[1]) || (b.s[0] + b.s[2]) - (a.s[0] + a.s[2]));
  for (const W of [128, 256, 512]) {
    let x = 0, y = 0, rowH = 0, ok = true;
    for (const b of order) {
      const w = 2 * (b.s[2] + b.s[0]), h = b.s[2] + b.s[1];
      if (w > W) { ok = false; break; }
      if (x + w > W) { x = 0; y += rowH; rowH = 0; }
      b.uv = [x, y];
      x += w;
      rowH = Math.max(rowH, h);
    }
    const H = y + rowH;
    if (ok && H <= W) {
      spec.W = W;
      spec.H = H <= W / 2 ? W / 2 : W;
      break;
    }
  }
  if (!spec.W) throw new Error(spec.name + ': does not fit 512');
  spec.boxes = boxes;
  return spec;
}

// texel -> world position for each face (see ModelPart.Cube's UV layout, rewritten in friendly coordinates)
function faces(b) {
  const [w, h, d] = b.s, [u, v] = b.uv, [x0, y0, z0] = b.min, [x1, y1, z1] = b.max;
  return [
    { f: 'top', u: u + d, v, fw: w, fh: d, pos: (c, r) => [x0 + c + 0.5, y1, z0 + r + 0.5], n: [0, 1, 0] },
    { f: 'bottom', u: u + d + w, v, fw: w, fh: d, pos: (c, r) => [x0 + c + 0.5, y0, z0 + r + 0.5], n: [0, -1, 0] },
    { f: 'right', u, v: v + d, fw: d, fh: h, pos: (c, r) => [x0, y1 - r - 0.5, z0 + c + 0.5], n: [-1, 0, 0] },
    { f: 'front', u: u + d, v: v + d, fw: w, fh: h, pos: (c, r) => [x0 + c + 0.5, y1 - r - 0.5, z1], n: [0, 0, 1] },
    { f: 'left', u: u + d + w, v: v + d, fw: d, fh: h, pos: (c, r) => [x1, y1 - r - 0.5, z1 - c - 0.5], n: [1, 0, 0] },
    { f: 'back', u: u + 2 * d + w, v: v + d, fw: w, fh: h, pos: (c, r) => [x1 - c - 0.5, y1 - r - 0.5, z0], n: [0, 0, -1] },
  ];
}

const NO_DECAL = new Set(['eye', 'lid', 'nose', 'tooth', 'claw', 'mouth', 'tongue', 'pad', 'beady', 'whisker', 'pinkNose']);
function segDist(p, a, b) {
  const ab = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], ap = [p[0] - a[0], p[1] - a[1], p[2] - a[2]];
  const l2 = ab[0] * ab[0] + ab[1] * ab[1] + ab[2] * ab[2];
  const t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, (ap[0] * ab[0] + ap[1] * ab[1] + ap[2] * ab[2]) / l2));
  const q = [a[0] + ab[0] * t - p[0], a[1] + ab[1] * t - p[1], a[2] + ab[2] * t - p[2]];
  return Math.hypot(q[0], q[1], q[2]);
}

function paint(spec) {
  const W = spec.W, H = spec.H, px = Buffer.alloc(W * H * 4);
  for (const b of spec.boxes) {
    const painter = spec.paint[b.paint];
    if (!painter) throw new Error(spec.name + ': no painter ' + b.paint);
    for (const F of faces(b)) {
      for (let r = 0; r < F.fh; r++) for (let c = 0; c < F.fw; c++) {
        const P = F.pos(c, r);
        let col = painter({ b, f: F.f, n: F.n, P, c, r, fw: F.fw, fh: F.fh, spec });
        if (!col) continue;
        // decals: dark lines / patches placed in 3D (tear marks, brows), on fur-like parts only
        if (spec.decals && col[3] !== 0 && !NO_DECAL.has(b.paint)) {
          for (const d of spec.decals) {
            if (segDist(P, d.a, d.b) < d.r) col = mix(col, d.c, d.k ?? 1);
          }
        }
        const i = ((F.v + r) * W + F.u + c) * 4;
        px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = col[3] === undefined ? 255 : clamp(col[3]);
      }
    }
  }
  png.write(path.join(TEX_DIR, spec.tex + '.png'), W, H, px);
}

const fmt = n => { const s = (Math.round(n * 1000) / 1000).toString(); return (s.includes('.') ? s : s + '.0') + 'F'; };
function java(spec) {
  const L = [];
  const cls = spec.name + 'Parts';
  L.push('package com.projecthero.mod.client.beastboy.model;', '');
  L.push('import net.minecraft.client.model.geom.PartPose;');
  L.push('import net.minecraft.client.model.geom.builders.CubeDeformation;');
  L.push('import net.minecraft.client.model.geom.builders.CubeListBuilder;');
  L.push('import net.minecraft.client.model.geom.builders.LayerDefinition;');
  L.push('import net.minecraft.client.model.geom.builders.MeshDefinition;');
  L.push('import net.minecraft.client.model.geom.builders.PartDefinition;', '');
  L.push('/**');
  L.push(' * GENERATED by scratchpad/beastboy/gen_land_mammals_tex.js (v0.17.3 hq round) -- edit the spec there, not this file. Beast Boy\'s');
  L.push(' * ' + spec.name.toLowerCase() + ' as box parts in vanilla model space (Y down, front -Z), 1 unit = 1/32 block; texture ' + spec.tex + '.png ' + spec.W + 'x' + spec.H + '.');
  L.push(' */');
  L.push('final class ' + cls + ' {');
  L.push('\tprivate ' + cls + '() {', '\t}', '');
  L.push('\tstatic LayerDefinition create() {');
  L.push('\t\tMeshDefinition mesh = new MeshDefinition();');
  L.push('\t\tPartDefinition root = mesh.getRoot();');
  let k = 0;
  (function walk(b, parentVar) {
    const v = 'p' + (k++);
    let cubes = 'CubeListBuilder.create()';
    for (const x of b.boxes) {
      const [w, h, d] = x.s;
      cubes += '.texOffs(' + x.uv[0] + ', ' + x.uv[1] + ').addBox(' + fmt(x.o[0]) + ', ' + fmt(-(x.o[1] + h)) + ', ' + fmt(-(x.o[2] + d)) + ', ' + fmt(w) + ', '
        + fmt(h) + ', ' + fmt(d) + ', new CubeDeformation(' + fmt(x.g || 0) + '))';
    }
    const pose = 'PartPose.offsetAndRotation(' + fmt(b.pivot[0]) + ', ' + fmt(-b.pivot[1]) + ', ' + fmt(-b.pivot[2]) + ', ' + fmt(b.rot[0]) + ', ' + fmt(-b.rot[1]) + ', '
      + fmt(-b.rot[2]) + ')';
    if (b === spec.root) {
      L.push('\t\tPartDefinition ' + v + ' = root.addOrReplaceChild("' + b.name + '", ' + cubes + ', ' + pose + ');');
    } else {
      L.push('\t\tPartDefinition ' + v + ' = ' + parentVar + '.addOrReplaceChild("' + b.name + '", ' + cubes + ', ' + pose + ');');
    }
    for (const c of b.children) walk(c, v);
  })(spec.root, 'root');
  L.push('\t\treturn LayerDefinition.create(mesh, ' + spec.W + ', ' + spec.H + ');');
  L.push('\t}');
  const names = [];
  (function walk(b) { names.push(b.name); b.children.forEach(walk); })(spec.root);
  L.push('');
  L.push('\t/** Every bone, parents first. */');
  L.push('\tstatic final String[] NAMES = { ' + names.map(n => '"' + n + '"').join(', ') + ' };');
  if (spec.consts) {
    L.push('');
    for (const [n, val] of Object.entries(spec.consts)) L.push('\tstatic final float ' + n + ' = ' + fmt(val) + ';');
  }
  L.push('}', '');
  // javac would choke on "-0.0F" style noise? it doesn't; keep CRLF like the rest of the project
  fs.writeFileSync(path.join(JAVA_DIR, cls + '.java'), L.join('\r\n').replace(/-0\.0F/g, '0.0F'));
}

// ------------------------------------------------------------------------------------------------------------ shared painters
// A palette: ink, back, flank, flankHi, belly, bellyHi, light (muzzle / socks)
function furColour(pal, t, P, seed, seamJitter = 0.08) {
  const j = (vnoise3(P[0] * 0.5, P[1] * 0.5, P[2] * 0.5, seed + 5) - 0.5) * seamJitter * 2;
  const tt = t + j;
  let c = mix(pal.back, pal.flank, smooth(0.08, 0.5, tt));
  c = mix(c, pal.flankHi, smooth(0.42, 0.6, tt) * 0.45);
  c = mix(c, pal.belly, smooth(pal.seam - 0.05, pal.seam + 0.05, tt));
  c = mix(c, pal.bellyHi, smooth(pal.seam + 0.1, 1, tt) * 0.6);
  return c;
}
// fur texture on top: clumps (stretched along the fur direction), single-texel speckles, a lit top row / shaded bottom row per face
function furDetail(c, ctx, seed, amt = 1, dir = 'down') {
  const P = ctx.P;
  const sx = dir === 'down' ? [0.7, 0.28, 0.7] : [0.7, 0.7, 0.28];
  const clump = vnoise3(P[0] * sx[0], P[1] * sx[1], P[2] * sx[2], seed) - 0.5;
  let k = 1 + clump * 0.2 * amt;
  const sp = rnd(P[0] * 2, P[1] * 2, P[2] * 2, seed + 1);
  if (sp > 0.94) k += 0.09 * amt; else if (sp < 0.06) k -= 0.1 * amt;
  if (ctx.f !== 'top' && ctx.f !== 'bottom') {
    if (ctx.r === 0 && ctx.fh > 2) k += 0.07;
    if (ctx.r === ctx.fh - 1 && ctx.fh > 2) k -= 0.1;
  }
  return mul(c, k);
}
// t for countershading: top faces 0, bottom faces 1, sides by height inside the box's band
function shadeT(ctx) {
  const b = ctx.b;
  const band = b.band || [b.max[1], b.min[1]];
  if (ctx.f === 'top') return b.topT ?? 0.02;
  if (ctx.f === 'bottom') return b.botT ?? 1;
  return Math.max(0, Math.min(1, (band[0] - ctx.P[1]) / (band[0] - band[1])));
}

function stdPainters(pal, extra = {}) {
  const P = {
    fur: ctx => {
      const t = shadeT(ctx);
      let c = furColour(pal, t, ctx.P, 11);
      if (pal.pattern) { const m = pal.pattern(ctx, t); if (m) c = mix(c, m.c, m.k ?? 1); }
      return furDetail(c, ctx, 12);
    },
    // lighter fur (muzzle, chest ruff, socks)
    light: ctx => {
      const t = shadeT(ctx);
      let c = mix(mix(pal.flankHi, pal.belly, 0.55), pal.bellyHi, smooth(0.2, 1, t));
      if (pal.lightPattern) { const m = pal.lightPattern(ctx, t); if (m) c = mix(c, m.c, m.k ?? 1); }
      return furDetail(c, ctx, 14, 0.8);
    },
    // shaggy ruff / mane: long strands, darker roots, light tips toward the bottom of each face
    shag: ctx => {
      const t = shadeT(ctx);
      let c = furColour(pal, t * 0.85, ctx.P, 15);
      if (pal.pattern && pal.patternOnShag) { const m = pal.pattern(ctx, t); if (m) c = mix(c, m.c, m.k ?? 1); }
      const strand = rnd(ctx.P[0] * 2 + ctx.P[2] * 2, 0, 0, 16);
      let k = 0.88 + strand * 0.22;
      if (ctx.f !== 'top' && ctx.f !== 'bottom') {
        // ragged hanging tips on the last rows
        if (ctx.r >= ctx.fh - 2 && strand < 0.45) return [0, 0, 0, 0];
        if (ctx.r === ctx.fh - 3 && strand < 0.2) k -= 0.1;
      }
      return furDetail(mul(c, k), ctx, 17, 0.6);
    },
    // pale ruff (chest / cheeks): same ragged strands, belly colours
    ruff: ctx => {
      const t = shadeT(ctx);
      let c = mix(pal.flankHi, pal.belly, 0.4 + 0.6 * smooth(0, 0.6, t));
      const strand = rnd(ctx.P[0] * 2 + ctx.P[2] * 2, ctx.P[0] * 2 - ctx.P[2] * 2, 0, 18);
      if (ctx.f !== 'top' && ctx.f !== 'bottom' && ctx.r >= ctx.fh - 2 && strand < 0.45) return [0, 0, 0, 0];
      return furDetail(mul(c, 0.9 + strand * 0.16), ctx, 19, 0.6);
    },
    eye: ctx => eyePixel(ctx, pal),
    lid: ctx => {
      let c = mix(pal.back, pal.flank, 0.4);
      if (ctx.f !== 'top' && ctx.f !== 'bottom' && ctx.r === ctx.fh - 1) c = pal.ink;
      return furDetail(c, ctx, 21, 0.5);
    },
    nose: ctx => {
      let c = hex(0x172219);
      if (ctx.f === 'front') {
        if (ctx.r === 0 && ctx.c === 1) c = hex(0x5E7A63);
        else if (ctx.r === 0) c = hex(0x2A3A2C);
        if (ctx.r === ctx.fh - 1 && (ctx.c === 0 || ctx.c === ctx.fw - 1)) c = hex(0x070B08);
      } else if (ctx.f === 'top') c = hex(0x2E4031);
      return c;
    },
    tooth: ctx => (ctx.f === 'top' ? hex(0xD8DCC4) : ctx.r === ctx.fh - 1 && ctx.fh > 1 ? hex(0xC9CEB0) : hex(0xF3F5E4)),
    claw: ctx => mix(hex(0x2B3A2C), hex(0xA9B5A0), ctx.f === 'front' ? 0.8 : ctx.r / Math.max(1, ctx.fh - 1) * 0.7),
    mouth: ctx => {
      if (ctx.f === 'bottom' || ctx.f === 'top') {
        // roof / floor: dark with ridges; the tongue down the middle of a floor
        let c = hex(0x3A2228);
        if (ctx.b.tongue && Math.abs(ctx.c - (ctx.fw - 1) / 2) < ctx.fw / 2 - 0.9) c = mix(hex(0xC0606C), hex(0xE08A92), rnd(ctx.c, ctx.r, 0, 22) * 0.5);
        if (!ctx.b.tongue && ctx.r % 2 === 0) c = mix(c, hex(0x5A3038), 0.6);
        return c;
      }
      return hex(0x2A181C);
    },
    tongue: ctx => {
      let c = mix(hex(0xC9646F), hex(0xE69AA0), ctx.f === 'top' ? 0.5 : 0.1);
      if (ctx.f === 'top' && Math.abs(ctx.c - (ctx.fw - 1) / 2) < 0.6) c = hex(0xA44E5A);
      return c;
    },
    innerEar: ctx => {
      if (ctx.f !== 'front') return P.fur(ctx);
      const edge = ctx.c === 0 || ctx.c === ctx.fw - 1 || ctx.r === 0;
      if (edge) return furDetail(mix(pal.back, pal.flank, 0.5), ctx, 23, 0.5);
      return mix(hex(0xC9E2A8), hex(0xD9B8AE), smooth(0, ctx.fh, ctx.r) * 0.7 + rnd(ctx.c, ctx.r, 0, 24) * 0.15);
    },
    pad: ctx => mix(hex(0x1E2D1D), hex(0x34473A), rnd(ctx.c, ctx.r, ctx.P[2], 25) * 0.4),
    // paw: socks colour with dark toe gaps on the front and a dark pad underneath
    paw: ctx => {
      if (ctx.f === 'bottom') return P.pad(ctx);
      let c = mix(mix(pal.flankHi, pal.belly, 0.5), pal.bellyHi, 0.3);
      if (pal.pawFur) c = pal.pawFur;
      if (ctx.f === 'front' && ctx.fw >= 3 && ctx.r >= ctx.fh - 2) {
        const gaps = ctx.fw >= 4 ? [Math.round(ctx.fw / 3) - 0.5, Math.round(2 * ctx.fw / 3) - 0.5] : [(ctx.fw - 1) / 2];
        for (const g of gaps) if (Math.abs(ctx.c - g) < 0.6) c = mul(c, 0.62);
      }
      if (ctx.f === 'top' && ctx.r >= ctx.fh - 2 && ctx.fw >= 4 && (ctx.c === Math.round(ctx.fw / 3) || ctx.c === Math.round(2 * ctx.fw / 3) - 1)) c = mul(c, 0.8);
      return furDetail(c, ctx, 26, 0.5);
    },
    ink: () => pal.ink,
  };
  return Object.assign(P, extra);
}

// a cartoon eye on the front face of an eye box (or its side when ctx.b.eyeFace says so): ink lash line, sclera, iris, pupil, highlight
function eyePixel(ctx, pal) {
  const face = ctx.b.eyeFace || 'front';
  if (ctx.f !== face) return pal.ink;
  const w = ctx.fw, h = ctx.fh;
  // mirror so the pupil sits toward the nose: the animal's right eye (x < 0) looks inward = toward +x
  const inward = ctx.b.side < 0 ? (face === 'front' ? 1 : -1) : (face === 'front' ? -1 : 1);
  const cc = inward > 0 ? ctx.c : w - 1 - ctx.c; // 0 = outer corner .. w-1 = inner
  const r = ctx.r;
  if (r === 0 && h >= 3) return pal.ink;
  const iris = pal.iris || hex(0x6FD34A), irisDk = pal.irisDk || hex(0x2F8A2A);
  const sclera = pal.sclera || hex(0xF1F7E6);
  // iris occupies the inner columns; pupil in the middle of the iris; the highlight top-outer of the pupil
  const irisW = Math.max(2, Math.round(w * 0.67));
  const ic0 = w - irisW;
  if (cc < ic0) return r === h - 1 ? mix(sclera, hex(0xB9C8AA), 0.5) : sclera;
  const ir = cc - ic0;
  const pupilC = Math.floor((irisW - 1) / 2) + (irisW % 2 === 0 ? 1 : 0);
  if (r === (h >= 3 ? 1 : 0) && ir === pupilC - 1) return hex(0xFFFFFF);
  if (Math.abs(ir - pupilC) < 0.6 && r >= (h >= 3 ? 1 : 0)) return pal.pupil || hex(0x0B140C);
  if (pal.slitPupil && Math.abs(ir - pupilC) < 1.2 && r === h - 1) return pal.pupil || hex(0x0B140C);
  return r === h - 1 ? irisDk : iris;
}

// ------------------------------------------------------------------------------------------------------------ leg builders
// front leg: shoulder pivot (relative to its parent), bone lengths up/fore/pastern, widths; foot boxes and claws
function foreLeg(id, side, pivot, L, R, opt = {}) {
  const [l1, l2, l3] = L, [w1, d1, w2, d2, w3, pw, pd] = R;
  const ph = opt.pawH || 2;
  const claws = [];
  if (opt.claws) {
    const n = opt.claws, sp = pw / n;
    for (let i = 0; i < n; i++) claws.push(box([-pw / 2 + sp * (i + 0.5) - 0.5, -ph, pd - 1.5 + 0.2], [1, 1, 1], 'claw'));
  }
  const paw = bone('paw' + id, [0, -l3, 0], [box([-pw / 2, -ph, -1.5], [pw, ph, pd], 'paw', { band: opt.band }), ...claws]);
  return bone('upper' + id, pivot, [box([-w1 / 2, -l1 - 1, -d1 / 2], [w1, l1 + 2, d1], opt.upperPaint || 'fur', { band: opt.band, side })], [
    bone('fore' + id, [0, -l1, 0], [box([-w2 / 2, -l2 - 0.5, -d2 / 2], [w2, l2 + 1, d2], opt.forePaint || 'fur', { band: opt.band, side })], [
      bone('wrist' + id, [0, -l2, 0], [box([-w3 / 2, -l3, -w3 / 2], [w3, l3 + 1, w3], opt.lowPaint || 'fur', { band: opt.band, side })], [paw]),
    ]),
  ]);
}
// hind leg (digitigrade): hip pivot, thigh / shin / cannon lengths and widths, paw
function hindLeg(id, side, pivot, L, R, opt = {}) {
  const [l1, l2, l3] = L, [w1, d1, w2, d2, w3, pw, pd] = R;
  const ph = opt.pawH || 2;
  const claws = [];
  if (opt.claws) {
    const n = opt.claws, sp = pw / n;
    for (let i = 0; i < n; i++) claws.push(box([-pw / 2 + sp * (i + 0.5) - 0.5, -ph, pd - 1.5 + 0.2], [1, 1, 1], 'claw'));
  }
  return bone('thigh' + id, pivot, [box([-w1 / 2, -l1 - 1, -d1 / 2 - (opt.thighBack || 0)], [w1, l1 + 3, d1], opt.thighPaint || 'fur', { band: opt.band, side })], [
    bone('shin' + id, [0, -l1, 0], [box([-w2 / 2, -l2 - 0.5, -d2 / 2], [w2, l2 + 1, d2], 'fur', { band: opt.band, side })], [
      bone('meta' + id, [0, -l2, 0], [box([-w3 / 2, -l3 - 0.5, -w3 / 2], [w3, l3 + 1, w3], opt.lowPaint || 'fur', { band: opt.band, side })], [
        bone('hpaw' + id, [0, -l3, 0], [box([-pw / 2, -ph, -1.5], [pw, ph, pd], 'paw', { band: opt.band }), ...claws]),
      ]),
    ]),
  ]);
}
// a tail: a chain of segment bones pointing back (-Z), each box overlapping the previous a little
function tailChain(prefix, pivot, sizes, len, paints) {
  let child = null;
  for (let i = sizes.length - 1; i >= 0; i--) {
    const [w, h] = sizes[i];
    const b = bone(prefix + i, i === 0 ? pivot : [0, 0, -len], [box([-w / 2, -h / 2, -len], [w, h, len + 1], paints[i] || paints[paints.length - 1], { tail: true })],
      child ? [child] : []);
    child = b;
  }
  return child;
}

const SPECS = {};
module.exports = { bone, box, foreLeg, hindLeg, tailChain, stdPainters, hex, mix, mul, smooth, frac, rnd, vnoise3, furColour, furDetail, shadeT, SPECS };
require('./land_specs.js');

const want = process.argv.slice(2).map(s => s.toLowerCase());
for (const [name, mk] of Object.entries(SPECS)) {
  if (want.length && !want.includes(name.toLowerCase())) continue;
  const spec = build(mk());
  paint(spec);
  java(spec);
  console.log(name, spec.W + 'x' + spec.H, spec.boxes.length + ' boxes');
}
