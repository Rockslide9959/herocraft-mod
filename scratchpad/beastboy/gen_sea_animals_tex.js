// v0.17.3 Beast Boy HQ round: the blocky sea animals (shark, dolphin, whale, octopus).
// ONE source of truth for each animal: its boxes (absolute positions, Minecraft box UV) and how each box is painted.
// Writes:  textures/entity/beastboy/art/{shark,dolphin,whale,octopus}.png   (pixel art, 1 texel = 1 model pixel)
//          src/client/java/com/projecthero/mod/client/beastboy/model/SeaShapes.java   (the matching ModelPart trees, GENERATED)
// Usage: node scratchpad/beastboy/gen_sea_animals_tex.js
//
// Authoring space (here): pixels, +Y up from the ground, +Z forward, +X the animal's LEFT. Boxes: box(part, cx, cy, cz, w, h, d, style, opts)
// with integer sizes (centre may be on half pixels). Parts: pivot (absolute) and parent; "local" parts (octopus arm chains) give their
// boxes and child pivots relative to their own pivot along their own axes instead. Java space is vanilla model space (Y down, front -Z,
// y = 24 at the ground); the conversion happens on output.
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');
const ROOT = path.join(__dirname, '..', '..');
const TEXDIR = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/beastboy/art');
const JAVA = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/beastboy/model/SeaShapes.java');

// ------------------------------------------------------------------------------------------------ colour helpers
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return [0, 1, 2, 3].map(i => (a[i] ?? 255) + ((b[i] ?? 255) - (a[i] ?? 255)) * t); };
const shade = (c, k) => [c[0] * k, c[1] * k, c[2] * k, c[3] ?? 255];
const CLEAR = [0, 0, 0, 0];
function rnd(x, y, s = 1) { let n = (Math.imul(Math.round(x * 7.31), 374761393) + Math.imul(Math.round(y * 5.17), 668265263) + Math.imul(s, 1442695041)) | 0; n = Math.imul(n ^ (n >>> 13), 1274126177); n ^= n >>> 16; return (n >>> 0) / 4294967295; }
const noisy = (c, x, y, s, amt) => shade(c, 1 + (rnd(x, y, s) - 0.5) * amt);

// ------------------------------------------------------------------------------------------------ model definition
const animals = [];
function animal(name, pal) { const a = { name, pal, parts: [], byName: {} }; animals.push(a); return a; }
function part(a, name, parent, pivot, o = {}) {
  const p = { name, parent: parent ? a.byName[parent] : null, pivot, rot: o.rot || [0, 0, 0], local: !!o.local, boxes: [], children: [] };
  if (p.parent) p.parent.children.push(p);
  a.parts.push(p); a.byName[name] = p; return p;
}
function box(p, cx, cy, cz, w, h, d, style, o = {}) {
  for (const s of [w, h, d]) if (Math.round(s) !== s) throw new Error(p.name + ': box size must be integer ' + [w, h, d]);
  p.boxes.push({ cx, cy, cz, w, h, d, style, o, x0: cx - w / 2, x1: cx + w / 2, y0: cy - h / 2, y1: cy + h / 2, z0: cz - d / 2, z1: cz + d / 2 });
}
const mirror = (fn) => { for (const s of [-1, 1]) fn(s, s < 0 ? 'R' : 'L'); }; // s = side sign (+1 = left / +X)

// ------------------------------------------------------------------------------------------------ painting
// countershaded skin: dark back -> mid flank -> light flank -> (seam) -> pale belly, by absolute height against the animal's belly line
function skin(pal, x, y, z, face, b) {
  const o = b.o;
  const bl = (o.bl !== undefined ? o.bl : pal.bl(z, x)) + (pal.jag ? (rnd(Math.floor(z), 3, 11) - 0.5) * pal.jag : 0);
  const top = o.top !== undefined ? o.top : pal.top(z, x);
  let c;
  let yy = y;
  if (face === 'top') yy = Math.max(y, top);
  if (face === 'bottom' && b.y0 <= bl + 0.01) yy = bl - 1;
  if (yy < bl) {
    const k = Math.min(1, (bl - yy) / 4);
    c = mix(pal.bellyEdge, pal.belly, 0.35 + k * 0.65);
    if (face === 'bottom') c = mix(pal.belly, pal.bellyHi, 0.5);
    c = noisy(c, x * 3 + z, y * 3 + z, 5, pal.noise * 0.6);
  } else {
    const k = Math.max(0, Math.min(1, (yy - bl) / Math.max(1, top - bl)));
    c = k < 0.45 ? mix(pal.flank, pal.mid, k / 0.45) : mix(pal.mid, pal.back, (k - 0.45) / 0.55);
    if (pal.seam && yy - bl < 1) c = mix(c, pal.seam, 0.65);
    c = noisy(c, x * 3 + z * 1.7, y * 2 + z, 7, pal.noise);
    if (pal.spots && face !== 'bottom' && rnd(Math.floor(x * 2), Math.floor(z * 2) + Math.floor(y * 2) * 31, 13) < pal.spots * k) c = shade(c, 0.82);
  }
  if (o.dark) c = shade(c, 1 - o.dark);
  if (o.light) c = mix(c, pal.belly, o.light);
  return c;
}

function paintFace(a, b, face, i, j, fw, fh) {
  // absolute (or part-local) centre of texel (i, j) on the face; see the UV notes in SeaShapes' header
  let x, y, z;
  switch (face) {
    case 'top': x = b.x0 + i + 0.5; z = b.z0 + j + 0.5; y = b.y1; break;
    case 'bottom': x = b.x0 + i + 0.5; z = b.z0 + j + 0.5; y = b.y0; break;
    case 'right': z = b.z0 + i + 0.5; y = b.y1 - j - 0.5; x = b.x0; break;
    case 'front': x = b.x0 + i + 0.5; y = b.y1 - j - 0.5; z = b.z1; break;
    case 'left': z = b.z1 - i - 0.5; y = b.y1 - j - 0.5; x = b.x1; break;
    case 'back': x = b.x1 - i - 0.5; y = b.y1 - j - 0.5; z = b.z0; break;
  }
  const ctx = { face, i, j, fw, fh, x, y, z, b, pal: a.pal };
  const st = STYLES[b.style];
  if (!st) throw new Error('style ' + b.style);
  let c = st(ctx);
  if (b.o.deco) { const d = b.o.deco(ctx, c); if (d) c = d; }
  if (a.pal.deco) { const d = a.pal.deco(ctx, c); if (d) c = d; }
  // bevel: the top row of a side face catches the light, the bottom row of a dark face falls into shadow
  if (c[3] > 0 && !b.o.flat && ['right', 'left', 'front', 'back'].includes(face) && fh >= 3) {
    if (j === 0) c = mix(c, [255, 255, 230, 255], 0.1);
  }
  return c;
}

const STYLES = {
  skin: ctx => skin(ctx.pal, ctx.x, ctx.y, ctx.z, ctx.face, ctx.b),
  belly: ctx => noisy(mix(ctx.pal.belly, ctx.pal.bellyHi, 0.4), ctx.x * 3, ctx.z * 3 + ctx.y, 9, ctx.pal.noise * 0.5),
  solid: ctx => noisy(hex(ctx.b.o.c), ctx.x * 3 + ctx.z, ctx.y * 5, 17, ctx.b.o.n ?? 0.06),
  mouth: ctx => {
    const M = hex(0x7A2333), D = hex(0x4A1420), T = hex(0xB84A5E);
    if (ctx.face === 'top') { // tongue / gum floor: pink middle, dark edges
      const e = Math.min(ctx.i, ctx.fw - 1 - ctx.i);
      return noisy(e === 0 ? D : (e === 1 ? M : T), ctx.i, ctx.j, 19, 0.08);
    }
    if (ctx.face === 'bottom') { // roof: dark with pale ridges
      const e = Math.min(ctx.i, ctx.fw - 1 - ctx.i);
      if (e === 0) return null;
      return noisy(ctx.j % 2 === 0 ? D : M, ctx.i, ctx.j, 21, 0.08);
    }
    return noisy(D, ctx.i, ctx.j, 23, 0.1);
  },
  // a strip of teeth on a 0-thickness plane: the gum row then pointed tips (down for the upper jaw, up for the lower)
  teeth: ctx => {
    const up = ctx.b.o.up, W = hex(0xF6F3E4), S = hex(0xD3CCB0);
    if (!['right', 'left', 'front', 'back'].includes(ctx.face)) return CLEAR;
    const row = up ? ctx.fh - 1 - ctx.j : ctx.j; // 0 = the gum side
    const along = ctx.i;
    if (row === 0) return along % 2 === 0 ? W : S;
    if (row === 1) return along % 2 === 0 ? (ctx.b.o.big ? S : W) : CLEAR;
    return CLEAR;
  },
  // an eye on a small box: dark iris, pupil, a white catch-light; outward face only, the rest skin coloured
  eye: ctx => {
    const o = ctx.b.o, out = o.side > 0 ? 'left' : 'right';
    if (ctx.face !== out && !(o.front && ctx.face === 'front')) return skin(ctx.pal, ctx.x, ctx.y, ctx.z, ctx.face, ctx.b);
    const fw = ctx.fw, fh = ctx.fh;
    // texel position from the front edge of the eye
    const fi = ctx.face === 'left' ? ctx.i : (ctx.face === 'right' ? fw - 1 - ctx.i : ctx.i);
    return o.paint(fi, ctx.j, fw, fh);
  },
  arm: ctx => { // octopus arm segment: mottled top, pale underside with sucker rings
    const P = ctx.pal, f = ctx.face;
    const base = noisy(P.armTop, ctx.i * 3 + ctx.b.o.seg * 11, ctx.j * 5, 31, 0.1);
    if (f === 'top') return rnd(ctx.i + ctx.b.o.seg * 7, ctx.j + ctx.b.o.arm * 13, 33) < 0.18 ? shade(base, 0.8) : base;
    if (f === 'bottom') {
      // suckers: a ring of pale texels round a darker cup, two staggered rows when there's room
      const w = ctx.fw, d = ctx.fh;
      if (w >= 3) {
        const ring = (ctx.i === 1 && (ctx.j % 3) === 1) ? P.cup : P.sucker;
        return noisy(ring, ctx.i, ctx.j, 35, 0.06);
      }
      return noisy((ctx.i + ctx.j + ctx.b.o.seg) % 2 === 0 ? P.sucker : P.underArm, ctx.i, ctx.j, 37, 0.06);
    }
    if (f === 'front' || f === 'back') return mix(base, P.underArm, 0.3);
    // sides: top rows skin, bottom row pale, with the sucker rims peeking at the bottom edge
    const t = ctx.fh <= 1 ? 0.5 : ctx.j / (ctx.fh - 1);
    if (t > 0.7) return noisy(ctx.i % 2 === 0 ? P.sucker : P.underArm, ctx.i, ctx.j, 39, 0.06);
    return mix(base, P.armSide, t);
  },
  baleen: ctx => {
    if (!['right', 'left'].includes(ctx.face)) return CLEAR;
    const fringe = ctx.j === ctx.fh - 1;
    if (fringe && ctx.i % 2 === 1) return CLEAR;
    const c = ctx.i % 2 === 0 ? hex(0x3B3A2C) : hex(0x5E5A42);
    return noisy(fringe ? hex(0x9B9578) : c, ctx.i, ctx.j, 41, 0.08);
  },
  puff: ctx => { // ink / spray cloud cube: soft noisy blobs
    const o = ctx.b.o, c = hex(o.c);
    const r = rnd(ctx.i, ctx.j + (['top', 'bottom', 'right', 'front', 'left', 'back'].indexOf(ctx.face)) * 17, 43);
    return shade(c, 0.85 + r * 0.3);
  },
};

// ------------------------------------------------------------------------------------------------ the animals
const G = { // Beast Boy greens
  ink: hex(0x12301A), back: hex(0x285A28), mid: hex(0x47913A), flank: hex(0x6BBA4D), light: hex(0x8BCF63), belly: hex(0xD5EDB4),
  bellyHi: hex(0xE8F7D2), bellyEdge: hex(0xB4D894),
};

// ============================== SHARK (great white, about 2.9 blocks nose to tail tip; hitbox 1.2 x 0.8)
{
  const a = animal('shark', {
    back: hex(0x264F27), mid: hex(0x3F8236), flank: hex(0x5FA846), seam: hex(0x2B5426), belly: hex(0xE2F2C6), bellyHi: hex(0xF0F9DF),
    bellyEdge: hex(0xC2DEA2), noise: 0.07, jag: 1.3,
    bl: (z) => z > 8 ? 6.6 : (z > -6 ? 5.6 : 6.8),
    top: (z) => z > 8 ? 12 : (z > -6 ? 13 : 11),
    deco: (c) => {
      const { x, y, z, face } = c;
      // gill slits on the torso flanks
      if (c.b.o.gills && (face === 'left' || face === 'right')) {
        for (const gz of [7.5, 5.5, 3.5]) {
          const lean = (y - 6) * 0.3;
          if (Math.abs(z - (gz - lean)) < 0.5 && y > 5 && y < 10.2) return shade(skinOf(c), y < 6 ? 0.78 : 0.62);
        }
      }
      // ampullae: little dark pores on the snout
      if (c.b.o.pores && face !== 'bottom' && rnd(Math.floor(x * 2), Math.floor(y * 2) + Math.floor(z * 2) * 17, 51) < 0.06 && y > 7) return shade(skinOf(c), 0.7);
      return null;
    },
  });
  function skinOf(c) { return skin(a.pal, c.x, c.y, c.z, c.face, c.b); }
  part(a, 'body', null, [0, 7, 0]);
  box(a.byName.body, 0, 7, 1, 10, 10, 14, 'skin', { gills: true });
  box(a.byName.body, 0, 12.5, 1, 6, 1, 10, 'skin');           // back ridge
  box(a.byName.body, 0, 1.5, 1, 8, 1, 10, 'skin');            // belly
  part(a, 'head', 'body', [0, 7, 8]);
  box(a.byName.head, 0, 8, 12, 9, 8, 8, 'skin', { pores: true, roof: true, deco: c => c.face === 'bottom' && Math.abs(c.x) < 4 && c.z > 9 ? STYLES.mouth(c) : null });
  box(a.byName.head, 0, 8.5, 18, 7, 6, 4, 'skin', { pores: true });
  box(a.byName.head, 0, 8.5, 21, 5, 4, 2, 'skin', { pores: true, deco: c => (c.face === 'front' && c.j === 2 && (c.i === 0 || c.i === 4)) ? hex(0x1C3A1C) : null });
  mirror((s, n) => box(a.byName.head, s * 4.2, 3, 12.5, 0, 2, 7, 'teeth', { big: true }));
  box(a.byName.head, 0, 3, 16.1, 8, 2, 0, 'teeth', { big: true });
  part(a, 'jaw', 'head', [0, 4, 9]);
  box(a.byName.jaw, 0, 3, 12.5, 8, 2, 7, 'skin', { bl: 5, flat: true, deco: c => c.face === 'top' ? STYLES.mouth(c) : (c.face !== 'bottom' && c.face !== 'back' && c.j === 0 ? hex(0x4A1420) : null) });
  mirror((s, n) => box(a.byName.jaw, s * 3.6, 4.5, 12.5, 0, 1, 6, 'teeth', { up: true }));
  box(a.byName.jaw, 0, 4.5, 15.6, 7, 1, 0, 'teeth', { up: true });
  mirror((s, n) => {
    const eyePaint = (i, j, fw, fh) => {
      if (i === 1 && j === 0) return hex(0xF4FFF0);              // catch-light (front-top)
      if (i === 1 && j === 1) return hex(0x050A06);              // pupil
      return hex(0x14261A);
    };
    part(a, 'eye' + n, 'head', [s * 4.5, 10, 14]);
    box(a.byName['eye' + n], s * 4.5, 9.5, 14, 1, 3, 3, 'eye', { side: s, paint: eyePaint });
    part(a, 'lid' + n, 'head', [s * 4.5, 11, 14]);              // blink lid, hangs from the top of the eye
    box(a.byName['lid' + n], s * 4.5, 9.5, 14, 1, 3, 3, 'skin', { bl: 6, inflate: 0.12 });
    part(a, 'nic' + n, 'head', [s * 4.5, 8, 14]);               // nictitating membrane (rolls up during the bite)
    box(a.byName['nic' + n], s * 4.5, 9.5, 14, 1, 3, 3, 'solid', { c: 0xDCE8D2, inflate: 0.08 });
    part(a, 'fin' + n, 'body', [s * 5, 4, 5]);
    box(a.byName['fin' + n], s * 8, 3.5, 4, 6, 1, 6, 'skin', { bl: 3.5 });
    part(a, 'finTip' + n, 'fin' + n, [s * 11, 3.5, 3]);
    box(a.byName['finTip' + n], s * 13, 3.5, 1.5, 4, 1, 3, 'skin', { bl: 3.5, dark: 0.12 });
  });
  part(a, 'dorsal', 'body', [0, 13, 2]);
  box(a.byName.dorsal, 0, 14, 2, 1, 2, 8, 'skin', { top: 12 });
  box(a.byName.dorsal, 0, 16, 0.5, 1, 2, 6, 'skin', { top: 12, dark: 0.05 });
  box(a.byName.dorsal, 0, 18, -1, 1, 2, 4, 'skin', { top: 12, dark: 0.1 });
  box(a.byName.dorsal, 0, 19.5, -2.5, 1, 1, 2, 'skin', { top: 12, dark: 0.18 });
  part(a, 'tail1', 'body', [0, 7, -6]);
  box(a.byName.tail1, 0, 7.5, -10, 7, 7, 8, 'skin');
  mirror((s, n) => box(a.byName.tail1, s * 4, 4, -9, 2, 1, 3, 'skin', { bl: 4 }));  // pelvic fins
  part(a, 'tail2', 'tail1', [0, 7.5, -14]);
  box(a.byName.tail2, 0, 7.5, -17.5, 4, 4, 7, 'skin');
  box(a.byName.tail2, 0, 10, -16, 1, 1, 3, 'skin', { dark: 0.1 });   // second dorsal
  box(a.byName.tail2, 0, 5, -16.5, 1, 1, 3, 'skin', { bl: 4 });      // anal fin
  mirror((s, n) => box(a.byName.tail2, s * 2.5, 7.5, -19, 1, 1, 4, 'skin'));  // keels
  part(a, 'caudal', 'tail2', [0, 7.5, -21]);
  const cd = a.byName.caudal;
  box(cd, 0, 7.5, -22, 2, 3, 2, 'skin');
  box(cd, 0, 10, -23.5, 1, 3, 3, 'skin', { top: 11 });
  box(cd, 0, 12.5, -25.5, 1, 2, 3, 'skin', { top: 11, dark: 0.06 });
  box(cd, 0, 14.5, -27.5, 1, 2, 2, 'skin', { top: 11, dark: 0.12 });
  box(cd, 0, 16, -29, 1, 1, 2, 'skin', { top: 11, dark: 0.2 });
  box(cd, 0, 5, -23.5, 1, 2, 3, 'skin', { bl: 3 });
  box(cd, 0, 3, -25, 1, 2, 2, 'skin', { bl: 3, dark: 0.1 });
}

// ============================== DOLPHIN (bottlenose, about 1.9 blocks; hitbox 0.9 x 0.6)
{
  const a = animal('dolphin', {
    back: hex(0x2F6A2E), mid: hex(0x4F9B40), flank: hex(0x7CC35B), belly: hex(0xE4F4CB), bellyHi: hex(0xF2FBE4), bellyEdge: hex(0xA9D58A),
    noise: 0.05, jag: 0,
    bl: (z) => z > 6 ? 3.6 : (z > -5 ? 3.2 : 4.6),
    top: (z) => z > 6 ? 9 : 9.5,
    deco: (c) => {
      // the soft cape: a darker saddle along the back
      if (c.b.style === 'skin' && c.face !== 'bottom' && c.y > 7.6 && c.z < 6 && c.z > -10) return shade(skin(c.pal, c.x, c.y, c.z, c.face, c.b), 0.9);
      return null;
    },
  });
  part(a, 'body', null, [0, 5, 0]);
  box(a.byName.body, 0, 5, 0.5, 7, 7, 11, 'skin');
  box(a.byName.body, 0, 9, 0.5, 5, 1, 9, 'skin');
  box(a.byName.body, 0, 1, 0.5, 5, 1, 9, 'belly');
  part(a, 'head', 'body', [0, 5, 6]);
  box(a.byName.head, 0, 5.5, 9, 6, 5, 6, 'skin', { deco: c => {
    if (c.face === 'top' && Math.abs(c.x) < 1 && Math.abs(c.z - 7.5) < 1) return hex(0x1A3A1A);               // blowhole
    if (c.face === 'bottom' && Math.abs(c.x) < 2 && c.z > 9) return STYLES.mouth(c);
    return null;
  } });
  box(a.byName.head, 0, 6.5, 12.5, 5, 3, 1, 'skin');           // the melon (forehead bulge)
  box(a.byName.head, 0, 5, 13.5, 4, 2, 1, 'skin');
  box(a.byName.head, 0, 4, 14.5, 3, 1, 3, 'skin', { bl: 3.6 });   // upper beak
  part(a, 'jaw', 'head', [0, 3, 9]);
  box(a.byName.jaw, 0, 2.5, 12, 4, 1, 6, 'skin', { bl: 3.2, deco: c => c.face === 'top' ? STYLES.mouth(c) : ((c.face === 'left' || c.face === 'right') && c.i > 0 ? hex(0x3F7A3A) : null) });
  box(a.byName.jaw, 0, 2.5, 15.5, 3, 1, 1, 'skin', { bl: 3.2, deco: c => c.face === 'top' ? STYLES.mouth(c) : null });
  mirror((s, n) => box(a.byName.jaw, s * 1.6, 3.5, 13, 0, 1, 5, 'teeth', { up: true }));
  mirror((s, n) => {
    part(a, 'eye' + n, 'head', [s * 3, 6, 10]);
    box(a.byName['eye' + n], s * 3.05, 5.5, 10, 1, 2, 2, 'eye', { side: s, paint: (i, j) => (i === 1 && j === 0) ? hex(0xF4FFF0) : (j === 1 && i === 0 ? hex(0x2E4E2C) : hex(0x0B140C)) });
    part(a, 'lid' + n, 'head', [s * 3, 6.5, 10]);
    box(a.byName['lid' + n], s * 3.05, 5.5, 10, 1, 2, 2, 'skin', { inflate: 0.12 });
    part(a, 'fin' + n, 'body', [s * 3.5, 2.5, 3]);
    box(a.byName['fin' + n], s * 5.5, 2.5, 2, 4, 1, 3, 'skin', { bl: 2.5 });
    box(a.byName['fin' + n], s * 8, 2.5, 0.5, 1, 1, 2, 'skin', { bl: 2.5, dark: 0.1 });
  });
  part(a, 'dorsal', 'body', [0, 9.5, -1]);
  box(a.byName.dorsal, 0, 10.5, 0, 1, 2, 5, 'skin', { top: 9 });
  box(a.byName.dorsal, 0, 12, -1.5, 1, 1, 3, 'skin', { top: 9, dark: 0.08 });
  box(a.byName.dorsal, 0, 13, -3, 1, 1, 2, 'skin', { top: 9, dark: 0.15 });
  part(a, 'tail1', 'body', [0, 5.5, -5]);
  box(a.byName.tail1, 0, 5.5, -8, 5, 5, 6, 'skin');
  part(a, 'tail2', 'tail1', [0, 5.5, -11]);
  box(a.byName.tail2, 0, 5.5, -13.5, 3, 3, 5, 'skin');
  box(a.byName.tail2, 0, 7.5, -13, 1, 1, 3, 'skin');           // keel ridge
  part(a, 'flukes', 'tail2', [0, 5.5, -16]);
  box(a.byName.flukes, 0, 5.5, -17.5, 4, 1, 3, 'skin', { bl: 5.5 });
  mirror((s, n) => {
    box(a.byName.flukes, s * 3.5, 5.5, -18.5, 3, 1, 3, 'skin', { bl: 5.5 });
    box(a.byName.flukes, s * 5.5, 5.5, -20, 1, 1, 2, 'skin', { bl: 5.5, dark: 0.1 });
  });
}

// ============================== WHALE (humpback, about 7 blocks at the drawn 0.85 scale; carries riders on top)
{
  const a = animal('whale', {
    back: hex(0x1F4A25), mid: hex(0x2F6630), flank: hex(0x46863F), belly: hex(0xD6ECB6), bellyHi: hex(0xE9F6D5), bellyEdge: hex(0x8FBC78),
    noise: 0.06, jag: 2.0, spots: 0.11,
    bl: (z) => z > 17 ? 9 : (z > -17 ? 9 : (z > -39 ? 11 : 14.5)),
    top: (z) => z > 17 ? 27 : (z > -17 ? 31 : (z > -39 ? 28 : 23)),
    deco: (c) => {
      // ventral grooves: pale pleats with dark lines, chin to navel
      if (c.face === 'bottom' && c.b.o.grooves) return Math.round(c.x) % 2 === 0 ? hex(0x7FA86C) : noisy(hex(0xDDEFC4), c.x, c.z, 61, 0.05);
      if ((c.face === 'left' || c.face === 'right') && c.b.o.grooves && c.y < 4) return c.j % 2 === 0 ? hex(0x7FA86C) : hex(0xC8E2AE);
      // pale scrape scars along the flanks
      if ((c.face === 'left' || c.face === 'right') && c.b.style === 'skin' && c.y > 12) {
        const k = Math.floor(c.z / 9), y0 = 16 + rnd(k, 2, 81) * 10;
        if (rnd(k, 5, 83) < 0.45 && Math.abs(c.y - (y0 + (c.z - k * 9) * 0.35)) < 0.5) return mix(skin(c.pal, c.x, c.y, c.z, c.face, c.b), c.pal.belly, 0.45);
      }
      // barnacle clusters on the chin and flippers
      if (c.b.o.barnacles && rnd(Math.floor(c.x), Math.floor(c.z) * 13 + Math.floor(c.y) * 7, 63) < 0.05) return hex(0xC7CFB8);
      return null;
    },
  });
  const P = n => a.byName[n];
  part(a, 'body', null, [0, 16, 0]);
  box(P('body'), 0, 16, 0, 34, 26, 34, 'skin', { grooves: true });
  box(P('body'), 0, 16, 0, 28, 30, 32, 'skin', { grooves: true });
  box(P('body'), 0, 31.5, -2, 20, 1, 26, 'skin');              // the back ridge riders sit on
  part(a, 'head', 'body', [0, 16, 17]);
  box(P('head'), 0, 16.5, 29, 24, 17, 24, 'skin', { deco: c => c.face === 'bottom' && Math.abs(c.x) < 11.5 && c.z > 18 ? STYLES.mouth(c) : null });
  box(P('head'), 0, 17.5, 29, 20, 17, 22, 'skin');
  box(P('head'), 0, 13.5, 45, 18, 11, 8, 'skin', { deco: c => c.face === 'bottom' ? STYLES.mouth(c) : null });  // rostrum
  box(P('head'), 0, 11.5, 51, 12, 7, 4, 'skin', { deco: c => c.face === 'bottom' ? STYLES.mouth(c) : null });
  box(P('head'), 0, 26.5, 24, 6, 1, 4, 'skin', { deco: c => c.face === 'top' && (c.i === 1 || c.i === 4) && c.j > 0 && c.j < 3 ? hex(0x0E2412) : null }); // splash guard + blowholes
  for (const [x, z] of [[-3, 30], [3, 33], [-3, 36], [3, 39], [-3, 43], [3, 46], [0, 47.5], [-5, 44], [4, 50.5], [-4, 51], [-7, 32], [7, 35], [-7, 38], [6, 44]]) {
    const yTop = z >= 50 ? 15 : (z >= 42 ? 19 : 26);
    box(P('head'), x, yTop + 1, z, 2, 2, 2, 'skin', { dark: 0.1 });  // tubercles
  }
  mirror((s, n) => box(P('head'), s * 10.5, 6, 30, 0, 4, 20, 'baleen'));
  part(a, 'jaw', 'head', [0, 8, 18]);
  box(P('jaw'), 0, 4.5, 35, 25, 7, 34, 'skin', { grooves: true, barnacles: true, bl: 9, deco: c => c.face === 'top' && Math.abs(c.x) < (c.z < 41 ? 11.5 : (c.z < 49 ? 8.5 : 5.5)) ? STYLES.mouth(c) : null });
  box(P('jaw'), 0, 4, 53.5, 13, 6, 3, 'skin', { barnacles: true, bl: 9 });
  for (const [x, z] of [[-12.5, 30], [12.5, 30], [-12.5, 38], [12.5, 38], [-12.5, 46], [12.5, 46], [-6.5, 54], [6.5, 54]]) {
    box(P('jaw'), x, 6, z, 2, 2, 2, 'skin', { bl: 9, dark: 0.1 });
  }
  mirror((s, n) => {
    part(a, 'eye' + n, 'head', [s * 12, 12, 21]);
    box(P('eye' + n), s * 12.1, 11.5, 21, 1, 3, 3, 'eye', { side: s, paint: (i, j) => (i === 1 && j === 0) ? hex(0xF4FFF0) : (j === 2 ? hex(0x3D6A3A) : (i === 1 ? hex(0x050A06) : hex(0x16301A))) });
    part(a, 'lid' + n, 'head', [s * 12, 13, 21]);
    box(P('lid' + n), s * 12.1, 11.5, 21, 1, 3, 3, 'skin', { bl: 9, inflate: 0.12 });
    part(a, 'flipper' + n, 'body', [s * 16, 8, 12]);
    box(P('flipper' + n), s * 22, 7.5, 10, 12, 3, 9, 'skin', { bl: 7.5, barnacles: true, light: 0.2 });
    part(a, 'flipperTip' + n, 'flipper' + n, [s * 28, 7.5, 10]);
    box(P('flipperTip' + n), s * 36, 7.5, 8.5, 16, 2, 7, 'skin', { bl: 7.5, light: 0.3 });
    box(P('flipperTip' + n), s * 46, 7.5, 7.5, 4, 1, 4, 'skin', { bl: 7.5, light: 0.45 });
    for (const x of [24, 31, 37, 43]) box(P(x < 28 ? 'flipper' + n : 'flipperTip' + n), s * x, 7.5, x < 28 ? 15 : 12.5, 2, 2, 1, 'skin', { bl: 6, light: 0.5 }); // leading-edge knobs
  });
  part(a, 'tail1', 'body', [0, 17, -17]);
  box(P('tail1'), 0, 18, -28, 24, 20, 22, 'skin');
  box(P('tail1'), 0, 18, -28, 20, 22, 20, 'skin');
  box(P('tail1'), 0, 30, -31, 2, 2, 8, 'skin', { dark: 0.05 });  // dorsal hump + fin
  box(P('tail1'), 0, 32, -33, 2, 2, 4, 'skin', { dark: 0.12 });
  part(a, 'tail2', 'tail1', [0, 17, -39]);
  box(P('tail2'), 0, 18, -48, 14, 13, 18, 'skin');
  box(P('tail2'), 0, 25, -47, 2, 1, 14, 'skin', { dark: 0.08 });  // knuckles along the tail stock
  part(a, 'tail3', 'tail2', [0, 17, -57]);
  box(P('tail3'), 0, 17, -62, 8, 7, 10, 'skin');
  part(a, 'fluke', 'tail3', [0, 17, -67]);
  const fk = { bl: 17, deco: c => c.face === 'bottom' ? (rnd(Math.floor(c.x), Math.floor(c.z), 65) < 0.2 ? hex(0x2F5A2E) : hex(0xE6F4D2)) : null };
  box(P('fluke'), 0, 17, -69, 10, 2, 4, 'skin', fk);
  mirror((s, n) => {
    box(P('fluke'), s * 9, 17, -71, 8, 2, 6, 'skin', fk);
    box(P('fluke'), s * 16, 17, -74, 6, 2, 6, 'skin', fk);
    box(P('fluke'), s * 21, 17, -76.5, 4, 1, 5, 'skin', { ...fk, dark: 0.08, deco: c => c.face === 'bottom' ? hex(0xE6F4D2) : (c.face === 'back' && c.i % 2 ? hex(0x183A1D) : null) });
  });
  part(a, 'spray', null, [0, 0, 0]);
  box(P('spray'), 0, 0, 0, 4, 4, 4, 'puff', { c: 0xEAF6FF });
}

// ============================== OCTOPUS (about 2 blocks across with the arms spread; hitbox 1.0 x 0.9)
{
  const a = animal('octopus', {
    back: hex(0x3B7E31), mid: hex(0x51A042), flank: hex(0x6EBA55), belly: hex(0xB5DE96), bellyHi: hex(0xCBEBB0), bellyEdge: hex(0x92C878),
    armTop: hex(0x4A973D), armSide: hex(0x7DBF60), underArm: hex(0xB9E09E), sucker: hex(0xE8F6D6), cup: hex(0x8FBE7A),
    noise: 0.08, jag: 0, spots: 0.22,
    bl: () => 4.5, top: () => 19,
  });
  const P = n => a.byName[n];
  part(a, 'head', null, [0, 3.5, 0]);
  box(P('head'), 0, 7, 0, 8, 7, 8, 'skin', { deco: c => c.face === 'bottom' ? hex(0x2F5E2A) : null });
  box(P('head'), 0, 3.5, 0, 10, 1, 10, 'skin', { bl: 3.6, deco: c => c.face === 'bottom' ? (Math.abs(c.x) < 1 && Math.abs(c.z) < 1 ? hex(0x2A2416) : noisy(c.pal.underArm, c.x * 3, c.z * 3, 71, 0.06)) : null }); // web skirt (beak in the middle underneath)
  box(P('head'), 4.5, 5.5, -1.5, 1, 2, 3, 'skin', { bl: 4 });   // siphon base
  part(a, 'siphon', 'head', [5, 5.5, -2]);
  box(P('siphon'), 5.5, 5.5, -3.5, 2, 2, 3, 'skin', { bl: 4, deco: c => c.face === 'back' ? hex(0x1E3D1C) : null });
  mirror((s, n) => {
    part(a, 'eyeBump' + n, 'head', [s * 4, 9, 1]);
    box(P('eyeBump' + n), s * 4.5, 10, 1, 3, 3, 3, 'eye', { side: s, paint: (i, j) => j === 1 ? hex(0x0A0A08) : (i === 2 && j === 0 ? hex(0xFFFFF0) : hex(0xE3CF57)) });
    box(P('eyeBump' + n), s * 4.5, 11.75, 1, 2, 1, 2, 'skin', { dark: 0.05 });   // brow papilla
    part(a, 'lid' + n, 'eyeBump' + n, [s * 4.5, 11.5, 1]);
    box(P('lid' + n), s * 4.5, 10, 1, 3, 3, 3, 'skin', { inflate: 0.12 });
  });
  part(a, 'mantle', 'head', [0, 10, -1]);
  box(P('mantle'), 0, 14.5, -4, 9, 9, 10, 'skin');
  box(P('mantle'), 0, 14.5, -4, 7, 11, 8, 'skin');
  part(a, 'mantle2', 'mantle', [0, 14.5, -9]);
  box(P('mantle2'), 0, 14.5, -12, 7, 7, 6, 'skin');
  box(P('mantle2'), 0, 14.5, -15.5, 5, 5, 1, 'skin');
  // eight arms: chains of tapering boxes, each segment the child of the one before (local axes: +Z along the arm)
  const L = [3, 3, 3, 3, 3, 3, 2, 2, 2], W = [5, 4, 4, 3, 3, 3, 2, 2, 1];
  for (let k = 0; k < 8; k++) {
    const ang = (k + 0.5) / 8 * Math.PI * 2;
    const root = 'arm' + k + '_0';
    part(a, root, 'head', [Math.sin(ang) * 2.2, 4, Math.cos(ang) * 2.2], { rot: [0, -ang, 0] });
    for (let sIdx = 0; sIdx < L.length; sIdx++) {
      const nm = 'arm' + k + '_' + sIdx;
      if (sIdx > 0) part(a, nm, 'arm' + k + '_' + (sIdx - 1), [0, 0, L[sIdx - 1]], { local: true });
      if (sIdx === 0) a.byName[nm].local = true;
      box(P(nm), 0, 0, L[sIdx] / 2, W[sIdx], W[sIdx], L[sIdx], 'arm', { seg: sIdx, arm: k });
    }
    // the root part's pivot is absolute (on the crown); its boxes and children are along its own axes
    a.byName[root].rootLocal = true;
  }
  part(a, 'ink', null, [0, 0, 0]);
  box(P('ink'), 0, 0, 0, 8, 8, 8, 'puff', { c: 0x22382C });
}

// ------------------------------------------------------------------------------------------------ packing + output
function areaOf(b) { return { w: 2 * (b.d + b.w), h: b.d + b.h }; }
function pack(a) {
  const boxes = [];
  for (const p of a.parts) for (const b of p.boxes) { const s = areaOf(b); if (s.w > 0 && s.h > 0) boxes.push({ b, ...s }); }
  boxes.sort((p, q) => q.h - p.h || q.w - p.w);
  for (const TW of [64, 128, 256, 512]) {
    for (const TH of [TW / 2, TW]) {
      let x = 0, y = 0, row = 0, ok = true;
      for (const e of boxes) {
        if (e.w > TW) { ok = false; break; }
        if (x + e.w > TW) { x = 0; y += row; row = 0; }
        if (y + e.h > TH) { ok = false; break; }
        e.b.u = x; e.b.v = y; x += e.w; row = Math.max(row, e.h);
      }
      if (ok) return { TW, TH, boxes };
    }
  }
  throw new Error('does not fit: ' + a.name);
}

function paint(a, TW, TH, boxes) {
  const px = Buffer.alloc(TW * TH * 4);
  const put = (x, y, c) => { if (x < 0 || y < 0 || x >= TW || y >= TH) return; const i = (y * TW + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = clamp(c[3] ?? 255); };
  for (const { b } of boxes) {
    const { u, v, w, h, d } = b;
    const faces = [
      ['top', u + d, v, w, d], ['bottom', u + d + w, v, w, d],
      ['right', u, v + d, d, h], ['front', u + d, v + d, w, h], ['left', u + d + w, v + d, d, h], ['back', u + d + w + d, v + d, w, h],
    ];
    for (const [f, fx, fy, fw, fh] of faces) {
      for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) {
        let c = paintFace(a, b, f, i, j, fw, fh);
        if (!c) c = STYLES.skin({ pal: a.pal, x: 0, y: 0, z: 0, face: f, b });
        put(fx + i, fy + j, c);
      }
    }
  }
  return px;
}

const F = n => { const s = (Math.round(n * 10000) / 10000).toString(); return (s.includes('.') ? s : s + '.0') + 'F'; };
function javaFor(a, TW, TH) {
  const lines = [];
  const name = a.name.charAt(0).toUpperCase() + a.name.slice(1);
  lines.push(`\t/** ${name}: ${a.parts.length} parts, ${TW}x${TH} texture (textures/entity/beastboy/art/${a.name}.png). */`);
  lines.push(`\tpublic static ModelPart ${a.name}() {`);
  lines.push('\t\tMeshDefinition mesh = new MeshDefinition();');
  lines.push('\t\tPartDefinition root = mesh.getRoot();');
  for (const p of a.parts) {
    const par = p.parent ? 'p_' + p.parent.name : 'root';
    // pivot offset relative to the parent, in model space (Y down, front -Z)
    let ox, oy, oz;
    if (p.local && !p.rootLocal) { ox = p.pivot[0]; oy = -p.pivot[1]; oz = -p.pivot[2]; }
    else if (!p.parent) { ox = p.pivot[0]; oy = 24 - p.pivot[1]; oz = -p.pivot[2]; }
    else { ox = p.pivot[0] - p.parent.pivot[0]; oy = -(p.pivot[1] - p.parent.pivot[1]); oz = -(p.pivot[2] - p.parent.pivot[2]); }
    let cubes = 'CubeListBuilder.create()';
    for (const b of p.boxes) {
      // box min corner relative to the pivot, model space
      let lx, ly, lz;
      if (p.local || p.rootLocal) { lx = b.x0; ly = -b.y1; lz = -b.z1; }
      else { lx = b.x0 - p.pivot[0]; ly = -(b.y1 - p.pivot[1]); lz = -(b.z1 - p.pivot[2]); }
      const def = b.o.inflate ? `, new CubeDeformation(${F(b.o.inflate)})` : '';
      cubes += `.texOffs(${b.u}, ${b.v}).addBox(${F(lx)}, ${F(ly)}, ${F(lz)}, ${F(b.w)}, ${F(b.h)}, ${F(b.d)}${def})`;
    }
    const r = p.rot;
    lines.push(`\t\tPartDefinition p_${p.name} = ${par}.addOrReplaceChild("${p.name}", ${cubes},`);
    lines.push(`\t\t\t\tPartPose.offsetAndRotation(${F(ox)}, ${F(oy)}, ${F(oz)}, ${F(r[0])}, ${F(r[1])}, ${F(r[2])}));`);
  }
  lines.push(`\t\treturn LayerDefinition.create(mesh, ${TW}, ${TH}).bakeRoot();`);
  lines.push('\t}');
  lines.push('');
  // a flat name -> part index
  lines.push(`\t/** Every part of {@link #${a.name}()} by name. */`);
  lines.push(`\tpublic static Map<String, ModelPart> ${a.name}Parts(ModelPart root) {`);
  lines.push('\t\tMap<String, ModelPart> m = new HashMap<>();');
  for (const p of a.parts) {
    const chain = [];
    for (let q = p; q; q = q.parent) chain.unshift(q.name);
    lines.push(`\t\tm.put("${p.name}", root${chain.map(c => `.getChild("${c}")`).join('')});`);
  }
  lines.push('\t\treturn m;');
  lines.push('\t}');
  return lines.join('\n');
}

const javaParts = [];
for (const a of animals) {
  const { TW, TH, boxes } = pack(a);
  const px = paint(a, TW, TH, boxes);
  png.write(path.join(TEXDIR, a.name + '.png'), TW, TH, px);
  javaParts.push(javaFor(a, TW, TH));
  console.log(a.name, TW + 'x' + TH, a.parts.length, 'parts', boxes.length, 'boxes');
}
const java = `package com.projecthero.mod.client.beastboy.model;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by scratchpad/beastboy/gen_sea_animals_tex.js -- do not edit by hand (edit the generator and re-run it; it rewrites this file
 * and the matching textures together, so box UVs and paint always agree).
 *
 * <p>v0.17.3 Beast Boy HQ round: the blocky sea animals as vanilla ModelPart trees (Minecraft box UV, 1 texel per pixel). Model space is
 * vanilla's: Y down, the animal's front toward -Z, y = 24 at the ground; part names are what the animators in {@code SharkModel},
 * {@code DolphinModel}, {@code WhaleModel} and {@code OctopusModel} look up.
 */
@SuppressWarnings("unused")
public final class SeaShapes {
\tprivate SeaShapes() {
\t}

${javaParts.join('\n\n')}
}
`;
fs.writeFileSync(JAVA, java);
console.log('wrote', JAVA);
