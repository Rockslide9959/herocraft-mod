// v0.17.3 Beast Boy HQ round: the box-model atlases of the T-Rex, pterodactyl, snake, fly, hawk and bat.
//   textures:  src/main/resources/assets/projecthero/textures/entity/beastboy/art/{trex,ptero,snake,fly,hawk,bat}.png
//   UV tables: src/client/java/com/projecthero/mod/client/beastboy/model/{TRex,Ptero,Snake,Fly,Hawk,Bat}Uv.java (generated, don't edit)
// Each animal is a list of parts (boxes w x h x d in texels, or planes with h = 0). The script packs them, paints every face as pixel
// art in Beast Boy's greens (dark back, lighter flanks, pale belly) from the texel's 3D spot on the box, and writes the part table
// {u, v, w, h, d, texW, texH, seeThrough} the models draw with (client/beastboy/model/Blocky: the vanilla cube unwrap, +Z = front).
// Usage: node scratchpad/beastboy/gen_reptiles_flyers_tex.js
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');
const ROOT = path.join(__dirname, '..', '..');
const TEX_DIR = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/beastboy/art');
const JAVA_DIR = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/beastboy/model');

// ------------------------------------------------------------------------------------------------------------------- colour
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const mixc = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return [0, 1, 2, 3].map(i => a[i] + (b[i] - a[i]) * t); };
function rnd(x, y, s = 1) { let n = (x * 374761393 + y * 668265263 + s * 1442695041) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967295; }
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5].map(v => (v + 0.5) / 16);
// Beast Boy green ramp: 0 ink .. 9 highlight
const G = [0x0F2716, 0x1B4420, 0x275D27, 0x3A7F32, 0x4F9E3E, 0x66B84B, 0x86CD62, 0xA8DD84, 0xC9E9A3, 0xE6F6CE].map(hex);
const IVORY = [0xFBF8E6, 0xE9E4C2, 0xC9C29A, 0x9C9670].map(hex); // teeth / claws, light -> dark
const RED = [0xF08A93, 0xD0616E, 0xA83E4C, 0x7A2433, 0x4A1220].map(hex); // mouth, light -> dark
const BLACK = hex(0x070C08), WHITE = hex(0xFFFFFF);
const CLEAR = [0, 0, 0, 0];
/** A ramp colour at fractional tone t, ordered-dithered by texel (i, j); jitter adds painterly speckle. */
function tone(t, i, j, jitter = 0.3, seed = 1, ramp = G) {
  t += (rnd(i, j, seed) - 0.5) * jitter;
  const f = t - Math.floor(t);
  let k = Math.floor(t) + (f > BAYER[(j & 3) * 4 + (i & 3)] ? 1 : 0);
  k = Math.max(0, Math.min(ramp.length - 1, k));
  return ramp[k];
}
const frac = x => x - Math.floor(x);
const lerp = (a, b, t) => a + (b - a) * t;

// ------------------------------------------------------------------------------------------------------------------- atlas
class Atlas {
  constructor(name, cls, minW = 64) { this.name = name; this.cls = cls; this.parts = []; this.minW = minW; }
  /** A box part; paint(c) gets {face, x, y, z (texel centre in box space, 0..w/h/d, +Z front), w, h, d, i, j (atlas texel)}. */
  box(id, w, h, d, paint, opts = {}) { const p = { id, w, h, d, paint, see: !!opts.see }; this.parts.push(p); return p; }
  plane(id, w, d, paint, opts = {}) { return this.box(id, w, 0, d, paint, opts); }
  pack() {
    const items = this.parts.map(p => ({ p, rw: p.h === 0 ? p.w : 2 * (p.d + p.w), rh: p.h === 0 ? p.d : p.d + p.h }));
    items.sort((a, b) => b.rh - a.rh || b.rw - a.rw);
    for (let TW = this.minW; TW <= 1024; TW *= 2) {
      let x = 0, y = 0, rowH = 0, ok = true;
      for (const it of items) {
        if (it.rw > TW) { ok = false; break; }
        if (x + it.rw > TW) { x = 0; y += rowH; rowH = 0; }
        it.x = x; it.y = y; x += it.rw; rowH = Math.max(rowH, it.rh);
      }
      const used = y + rowH;
      let TH = 16; while (TH < used) TH *= 2;
      if (ok && TH <= TW * 2) { this.TW = TW; this.TH = TH; this.items = items; return; }
    }
    throw new Error('cannot pack ' + this.name);
  }
  build() {
    this.pack();
    const W = this.TW, H = this.TH, px = Buffer.alloc(W * H * 4);
    const put = (i, j, c) => { const k = (j * W + i) * 4; for (let n = 0; n < 4; n++) px[k + n] = Math.max(0, Math.min(255, Math.round(c[n] === undefined ? 255 : c[n]))); };
    for (const it of this.items) {
      const p = it.p, w = p.w, h = p.h, d = p.d;
      const face = (name, u0, v0, fw, fh, pos) => {
        for (let b = 0; b < fh; b++) for (let a = 0; a < fw; a++) {
          const q = pos(a + 0.5, b + 0.5);
          const c = p.paint({ face: name, x: q[0], y: q[1], z: q[2], w, h, d, i: u0 + a, j: v0 + b, a, b });
          put(u0 + a, v0 + b, c);
        }
      };
      if (h === 0) { // plane: top region only, packed at (x, y); exported u = x - d
        p.u = it.x - d; p.v = it.y;
        face('top', it.x, it.y, w, d, (a, b) => [a, 0, b]);
        continue;
      }
      const U = it.x, V = it.y;
      p.u = U; p.v = V;
      face('top', U + d, V, w, d, (a, b) => [a, h, b]);
      face('bottom', U + d + w, V, w, d, (a, b) => [a, 0, d - b]);
      face('right', U, V + d, d, h, (a, b) => [0, h - b, a]);
      face('front', U + d, V + d, w, h, (a, b) => [a, h - b, d]);
      face('left', U + d + w, V + d, d, h, (a, b) => [w, h - b, d - a]);
      face('back', U + 2 * d + w, V + d, w, h, (a, b) => [w - a, h - b, 0]);
    }
    png.write(path.join(TEX_DIR, this.name + '.png'), W, H, px);
    const NL = '\r\n';
    let j = 'package com.projecthero.mod.client.beastboy.model;' + NL + NL;
    j += '/** GENERATED by scratchpad/beastboy/gen_reptiles_flyers_tex.js (v0.17.3 HQ round): ' + this.name + '.png part table {u, v, w, h, d, texW, texH, seeThrough}. Don\'t edit. */' + NL;
    j += 'final class ' + this.cls + ' {' + NL;
    j += '\tstatic final net.minecraft.resources.ResourceLocation TEX = com.projecthero.mod.ProjectHeroMod.id("textures/entity/beastboy/art/' + this.name + '.png");' + NL;
    for (const p of this.parts) j += '\tstatic final int[] ' + p.id + ' = { ' + [p.u, p.v, p.w, p.h, p.d, W, H, p.see ? 1 : 0].join(', ') + ' };' + NL;
    j += NL + '\tprivate ' + this.cls + '() {' + NL + '\t}' + NL + '}' + NL;
    fs.writeFileSync(path.join(JAVA_DIR, this.cls + '.java'), j);
    console.log(this.name + '.png ' + W + 'x' + H + ', ' + this.parts.length + ' parts');
  }
}

// ------------------------------------------------------------------------------------------------------------------- painters
const side = c => c.face === 'left' || c.face === 'right';
/** Height 0 (bottom) .. 1 (top) of the texel within the box. */
const hy = c => c.h > 0 ? c.y / c.h : 1;
/** Countershade tone: top faces darkest, sides blend top -> bottom, bottom faces palest. */
function csTone(c, top, bot) {
  if (c.face === 'top') return top - 0.4;
  if (c.face === 'bottom') return bot + 0.4;
  return lerp(bot, top, Math.pow(hy(c), 0.9));
}
/** Pebbly scales: a staggered 2-texel cell grid, every other cell a shade lighter, the cell's lower edge darker. */
function pebble(c, k = 0.45) {
  const gx = Math.floor((c.i + (Math.floor(c.j / 2) & 1)) / 2), gy = Math.floor(c.j / 2);
  return ((gx + gy) & 1 ? k * 0.5 : -k * 0.2) + ((c.j & 1) ? -k * 0.25 : 0);
}
/** Char at a texel of a grid stamped on the side faces: col runs front -> back from zf0, row top -> down from yt0. */
function sideStamp(c, zf0, yt0, rows) {
  if (!side(c)) return null;
  const col = Math.floor(c.d - c.z) - zf0, row = Math.floor(c.h - c.y) - yt0;
  if (row < 0 || row >= rows.length || col < 0 || col >= rows[row].length) return null;
  const ch = rows[row][col];
  return ch === '.' ? null : ch;
}
function frontStamp(c, x0, yt0, rows) {
  if (c.face !== 'front') return null;
  const col = Math.floor(c.x) - x0, row = Math.floor(c.h - c.y) - yt0;
  if (row < 0 || row >= rows.length || col < 0 || col >= rows[row].length) return null;
  const ch = rows[row][col];
  return ch === '.' ? null : ch;
}
function topStamp(c, x0, zf0, rows) {
  if (c.face !== 'top') return null;
  const col = Math.floor(c.x) - x0, row = Math.floor(c.d - c.z) - zf0;
  if (row < 0 || row >= rows.length || col < 0 || col >= rows[row].length) return null;
  const ch = rows[row][col];
  return ch === '.' ? null : ch;
}
/** Generic scaly green: countershaded tone + pebbles + optional dark bands across the back every `stripe` texels along z. */
function scaly(top, bot, opts = {}) {
  return c => {
    let t = csTone(c, top, bot) + pebble(c, opts.peb === undefined ? 0.45 : opts.peb);
    const low = opts.stripeLow || 0.4;
    if (opts.stripe && c.face !== 'bottom' && hy(c) > low) {
      const zz = (opts.zOff || 0) + c.z;
      const reach = (hy(c) - low) / (1 - low); // bands taper down the flanks
      const wdt = (opts.stripeW || 0.28) * (c.face === 'top' ? 1 : 0.4 + 0.6 * reach);
      if (frac(zz / opts.stripe) < wdt) t -= 1.6;
    }
    return tone(t, c.i, c.j, opts.jit === undefined ? 0.35 : opts.jit, opts.seed || 3);
  };
}
const ivory = c => { const t = c.face === 'bottom' ? 2 : c.face === 'top' ? 0 : 1 - hy(c) * 0.9 + (side(c) ? 0.6 : 0); return tone(t, c.i, c.j, 0.2, 5, IVORY); };
/** A claw / talon pointing along +Z: pale at the front tip, darker at the root. */
function clawPaint(dark) {
  return c => {
    const k = 1 - c.z / Math.max(1, c.d);
    const ramp = dark ? [hex(0x5E6E58), hex(0x34412F), hex(0x1A2419), hex(0x0B110B)] : IVORY;
    return tone(k * 3 + (c.face === 'bottom' ? 0.5 : 0), c.i, c.j, 0.2, 6, ramp);
  };
}
const mouthPaint = c => tone(1.2 + (side(c) ? 1 : 0) + (rnd(c.i, c.j, 8) - 0.5), c.i, c.j, 0.3, 8, RED);

// ================================================================================================================== T-REX
{
  const A = new Atlas('trex', 'TRexUv', 128);
  const body = (zOff) => scaly(1.8, 7.6, { stripe: 6, stripeW: 0.34, stripeLow: 0.45, zOff });
  A.box('TORSO', 14, 14, 22, body(0));
  A.box('BELLY', 12, 5, 16, c => {
    let t = c.face === 'top' ? 4 : csTone(c, 6.8, 8.6);
    if (c.face === 'bottom' && (Math.floor(c.z) % 3 === 0)) t -= 0.9; // belly scute bands
    return tone(t, c.i, c.j, 0.25, 4);
  });
  A.box('CHEST', 12, 12, 8, c => {
    let t = csTone(c, 2.2, 8.2) + pebble(c, 0.35);
    if (c.face === 'front' && hy(c) < 0.6 && Math.floor(c.y) % 2 === 0) t -= 0.5; // throat folds
    return tone(t, c.i, c.j, 0.3, 9);
  });
  A.box('NECK1', 10, 10, 8, body(3));
  A.box('NECK2', 9, 9, 7, body(1));
  A.box('SCUTE', 2, 2, 3, c => tone(c.face === 'top' ? 3.2 : 1.2 + hy(c) * 1.5, c.i, c.j, 0.3, 11));
  A.box('SCUTE_S', 2, 1, 2, c => tone(c.face === 'top' ? 2.8 : 1.4, c.i, c.j, 0.3, 12));
  // head
  const EYE_T = ['kkkkk', 'kwyyk', 'kyppk', 'kyypk', '.kkk.'];
  const EPAL = { k: G[0], w: WHITE, y: hex(0xE8E04A), Y: hex(0xB7A92A), p: BLACK };
  A.box('SKULL', 13, 11, 11, c => {
    const s = sideStamp(c, 1, 2, EYE_T);
    if (s) return s === 'y' && c.y < c.h - 4.5 ? EPAL.Y : EPAL[s];
    if (c.face === 'bottom') return mouthPaint(c);
    let t = csTone(c, 2.0, 7.4) + pebble(c, 0.35);
    if (c.face === 'top' && Math.floor(c.x) >= 5 && Math.floor(c.x) <= 7) t -= 1.2; // a dark crown stripe
    if (side(c) && Math.floor(c.y) === 0) t = 0.6;
    if (side(c) && Math.floor(c.h - c.y) === 1 && c.d - c.z < 7) t -= 0.8; // brow shadow
    return tone(t, c.i, c.j, 0.3, 13);
  });
  A.box('SNOUT', 10, 7, 11, c => {
    if (c.face === 'bottom') return mouthPaint(c);
    const n1 = topStamp(c, 2, 0, ['k....k', 'K....K']) || frontStamp(c, 2, 1, ['K....K']);
    if (n1) return n1 === 'k' ? G[0] : G[1];
    let t = csTone(c, 2.6, 7.6) + pebble(c, 0.35);
    if (side(c) && Math.floor(c.y) === 0) t = 0.6; // lip line
    if (side(c) && Math.floor(c.y) === 1) t -= 0.6;
    if (c.face === 'top' && frac(c.z / 3) < 0.34) t -= 0.5; // snout ridge bumps
    return tone(t, c.i, c.j, 0.3, 14);
  });
  A.box('NOSE_RIDGE', 6, 2, 8, scaly(2.0, 4, { peb: 0.5 }));
  A.box('BROW', 3, 2, 6, c => tone(c.face === 'top' ? 2.5 : 1.3 + hy(c), c.i, c.j, 0.35, 15));
  A.box('CHEEK', 2, 5, 6, c => tone(csTone(c, 3.6, 7.2) + pebble(c, 0.3), c.i, c.j, 0.3, 16));
  A.box('LID', 1, 5, 5, c => tone(2.6 - (1 - hy(c)) * 0.8, c.i, c.j, 0.3, 17));
  A.box('TOOTH', 1, 2, 1, ivory);
  A.box('TOOTH_BIG', 1, 3, 1, ivory);
  A.box('JAW', 9, 4, 18, c => {
    if (c.face === 'top') return Math.abs(c.x - c.w / 2) < 3.2 ? mouthPaint(c) : tone(1.0, c.i, c.j, 0.3, 18, RED);
    let t = csTone(c, 3.4, 8.2) + pebble(c, 0.3);
    if (side(c) && Math.floor(c.h - c.y) === 0) t = 0.8; // lower lip
    if (c.face === 'bottom' && Math.floor(c.z) % 3 === 0) t -= 0.6;
    return tone(t, c.i, c.j, 0.3, 19);
  });
  A.box('TONGUE', 5, 1, 12, c => tone(c.face === 'top' && Math.abs(c.x - 2.5) < 0.6 ? 2.2 : 1.1, c.i, c.j, 0.3, 20, RED));
  A.box('THROAT', 8, 4, 8, c => tone(csTone(c, 6.6, 8.4) - (Math.floor(c.z) % 2 ? 0.4 : 0), c.i, c.j, 0.25, 21));
  // arms
  A.box('UPPER_ARM', 3, 5, 3, scaly(3.2, 6.4, { peb: 0.35 }));
  A.box('FOREARM', 2, 4, 2, scaly(3.6, 6.4, { peb: 0.3 }));
  A.box('FINGER', 1, 1, 3, scaly(4, 6, { peb: 0.2 }));
  A.box('CLAW_S', 1, 1, 2, clawPaint(false));
  // legs
  A.box('THIGH', 7, 13, 10, c => {
    let t = csTone(c, 2.0, 6.6) + pebble(c, 0.4);
    if (c.face !== 'bottom' && hy(c) > 0.55 && frac(c.z / 5 + 0.1) < 0.3) t -= 1.3; // stripes over the haunch
    if (c.face === 'front' && hy(c) < 0.3) t += 0.6;
    return tone(t, c.i, c.j, 0.35, 22);
  });
  A.box('SHIN', 5, 12, 5, scaly(3.2, 5.2, { peb: 0.45 }));
  A.box('META', 4, 8, 4, c => tone(4.6 + (c.face === 'back' ? -0.6 : 0) + (Math.floor(c.y) % 2 === 0 && c.face === 'front' ? -0.8 : 0), c.i, c.j, 0.3, 23));
  A.box('FOOT', 6, 3, 7, c => tone((c.face === 'top' ? 4.4 : 4.9) + (Math.floor(c.z) % 2 && c.face === 'top' ? -0.7 : 0) + pebble(c, 0.2), c.i, c.j, 0.3, 24));
  A.box('TOE', 2, 2, 5, c => tone((c.face === 'top' ? 4.8 : 5.4) + (Math.floor(c.z) % 2 && c.face === 'top' ? -0.8 : 0), c.i, c.j, 0.3, 25));
  A.box('CLAW', 2, 2, 3, clawPaint(false));
  // tail
  const tails = [[12, 11, 9], [10, 9, 9], [8, 8, 9], [7, 6, 8], [5, 5, 8], [4, 4, 7], [3, 3, 7], [2, 2, 6]];
  tails.forEach((t, k) => A.box('TAIL' + k, t[0], t[1], t[2], scaly(1.8, 7.4, { stripe: 6, stripeW: 0.34, stripeLow: 0.4, zOff: k * 3 })));
  A.build();
}

// ================================================================================================================== PTERODACTYL
{
  const A = new Atlas('ptero', 'PteroUv', 64);
  const fuzz = (top, bot, seed) => c => tone(csTone(c, top, bot) + ((c.i * 7 + c.j * 3) % 5 === 0 ? 0.6 : 0), c.i, c.j, 0.45, seed);
  A.box('TORSO', 6, 6, 10, fuzz(2.2, 7.8, 30));
  A.box('HIPS', 5, 4, 5, fuzz(2.4, 7.4, 31));
  A.box('TAIL', 1, 1, 4, fuzz(2.6, 5, 32));
  A.box('NECK1', 3, 3, 6, fuzz(2.6, 7.6, 33));
  A.box('NECK2', 3, 3, 5, fuzz(2.8, 7.6, 34));
  const EYE_P = ['kkk', 'woy', 'kpk'];
  A.box('SKULL', 4, 4, 5, c => {
    const s = sideStamp(c, 1, 1, EYE_P);
    if (s) return { k: G[0], w: WHITE, o: hex(0xF0B53C), y: hex(0xC98A1E), p: BLACK }[s];
    return tone(csTone(c, 2.6, 7.6), c.i, c.j, 0.3, 35);
  });
  A.box('CREST', 1, 3, 10, c => {
    const k = 1 - c.z / c.d; // 0 at the skull, 1 at the tip (the crest is drawn pointing back)
    let t = 1.2 + k * 3.4 + (Math.floor(c.z) % 3 === 0 ? -0.6 : 0);
    if (hy(c) > 0.7) t += 0.8;
    return tone(t, c.i, c.j, 0.3, 36);
  });
  const beak = (seed) => c => {
    const k = c.z / c.d;
    let t = 6.4 - k * 1.2 + (c.face === 'top' ? -0.6 : 0);
    if (c.face === 'bottom') t = 3;
    return tone(t, c.i, c.j, 0.25, seed);
  };
  A.box('BEAK1', 3, 2, 6, beak(37));
  A.box('BEAK2', 2, 1, 6, beak(38));
  A.box('LJAW1', 3, 1, 6, c => c.face === 'top' ? mouthPaint(c) : tone(6.6, c.i, c.j, 0.25, 39));
  A.box('LJAW2', 2, 1, 5, c => c.face === 'top' ? mouthPaint(c) : tone(6.2, c.i, c.j, 0.25, 40));
  const bonePaint = (seed) => c => tone(c.face === 'top' ? 2.4 : c.face === 'bottom' ? 6.6 : (c.face === 'front' ? 3.8 : 4.6), c.i, c.j, 0.3, seed);
  A.box('HUMERUS', 6, 2, 3, bonePaint(41));
  A.box('FOREARM', 8, 2, 2, bonePaint(42));
  A.box('WRIST', 2, 2, 2, bonePaint(43));
  A.box('FING1', 10, 1, 1, bonePaint(44));
  A.box('FING2', 8, 1, 1, bonePaint(45));
  A.box('FING3', 6, 1, 1, bonePaint(46));
  A.box('FINGERLET', 1, 1, 2, clawPaint(true));
  // membrane panel: u along the span (x), the leading edge at the region's bottom row (z = d, the panel's front)
  A.plane('MEMBRANE', 32, 16, c => {
    const span = c.x / c.w, chord = 1 - c.z / c.d; // chord 0 at the leading edge
    let t = 6.0 - chord * 1.4 - span * 0.4;
    if (frac(chord * 6 + span * 4) < 0.12) t -= 0.7; // actinofibrils raking toward the tip
    if (chord < 0.08) t = 3.0; // shadow under the arm
    if (chord > 0.92) t = 2.4; // dark trailing rim
    if (Math.abs(frac(span * 5 + chord * 0.5) - 0.5) < 0.04 && chord < 0.7) t -= 0.8; // veins
    return tone(t, c.i, c.j, 0.35, 47);
  });
  A.box('THIGH', 2, 4, 2, fuzz(3, 6, 48));
  A.box('SHIN', 1, 4, 1, c => tone(5.6, c.i, c.j, 0.3, 49));
  A.box('FOOT', 2, 1, 3, c => tone(5.8, c.i, c.j, 0.3, 50));
  A.box('TALON', 1, 1, 1, clawPaint(true));
  A.build();
}

// ================================================================================================================== SNAKE
{
  const A = new Atlas('snake', 'SnakeUv', 64);
  // body segment: saddle diamonds along the spine (alternating A / B so the pattern runs), flank scales, belly scutes
  const seg = (phase, seed) => c => {
    if (c.face === 'bottom') return tone(8.2 - (Math.floor(c.z) % 2 ? 0.8 : 0), c.i, c.j, 0.2, seed);
    let t = csTone(c, 2.4, 7.2);
    const cx = c.w / 2;
    if (c.face === 'top') {
      const dz = Math.abs(frac(c.z / c.d + phase) - 0.5) * 2, dx = Math.abs(c.x - cx) / cx;
      const dia = dx + dz;
      if (dia < 0.95 && dia > 0.6) t = 0.8; else if (dia <= 0.6) t = 4.4;
    } else if (side(c)) {
      if (hy(c) > 0.55 && Math.abs(frac(c.z / c.d + phase + 0.5) - 0.5) < 0.18) t = 1.6; // flank blotches between saddles
      if (Math.floor(c.y) === 0) t = 7.6;
    }
    t += ((c.i + c.j) & 1) ? 0.25 : -0.15; // tiny scales
    return tone(t, c.i, c.j, 0.3, seed);
  };
  A.box('SEG_A', 4, 3, 4, seg(0, 51));
  A.box('SEG_B', 4, 3, 4, seg(0.5, 52));
  A.box('NECK', 4, 3, 4, c => tone(csTone(c, 3, 8) + (((c.i + c.j) & 1) ? 0.25 : -0.15), c.i, c.j, 0.3, 53));
  const EYE_S = ['kk', 'yp', 'yp'];
  A.box('HEAD', 5, 3, 5, c => {
    const s = sideStamp(c, 1, 0, EYE_S);
    if (s) return { k: G[0], y: hex(0xEDE65A), p: BLACK }[s];
    if (c.face === 'bottom') return mouthPaint(c);
    let t = csTone(c, 2.6, 7.4);
    if (c.face === 'top') { // head plates
      if (Math.floor(c.x) % 2 === 0 && Math.floor(c.z) % 2 === 0) t += 0.5;
      if (Math.abs(c.x - 2.5) < 0.6) t -= 0.6;
    }
    if (side(c) && Math.floor(c.y) === 0) t = 0.8;
    return tone(t, c.i, c.j, 0.3, 54);
  });
  A.box('SNOUT', 3, 2, 3, c => {
    if (c.face === 'bottom') return mouthPaint(c);
    if (frontStamp(c, 0, 0, ['k.k'])) return G[0];
    let t = csTone(c, 3, 7.2);
    if (side(c) && Math.floor(c.y) === 0) t = 0.8;
    return tone(t, c.i, c.j, 0.3, 55);
  });
  A.box('BROW', 1, 1, 3, c => tone(1.6, c.i, c.j, 0.3, 56));
  A.box('JAW', 4, 1, 7, c => c.face === 'top' ? mouthPaint(c) : tone(c.face === 'bottom' ? 8 : 7, c.i, c.j, 0.2, 57));
  A.box('FANG', 1, 2, 1, ivory);
  // forked tongue (plane 3 x 6): a stem, splitting into two tips at the front
  A.plane('TONGUE', 3, 6, c => {
    const zf = c.d - c.z; // 0 at the front
    if (zf < 2.5) return Math.floor(c.x) === 1 ? CLEAR : tone(1.6, c.i, c.j, 0.2, 58, RED);
    return Math.floor(c.x) === 1 ? tone(2.2, c.i, c.j, 0.2, 58, RED) : CLEAR;
  });
  A.box('TAIL_TIP', 2, 2, 4, c => tone(csTone(c, 2.4, 7), c.i, c.j, 0.3, 59));
  A.build();
}

// ================================================================================================================== FLY
{
  const A = new Atlas('fly', 'FlyUv', 64);
  const MET = [0x071209, 0x113A1C, 0x1F5E2E, 0x2E8040, 0x47A857, 0x80DA8A, 0xC4F6C8].map(hex);
  A.box('THORAX', 6, 6, 6, c => {
    let t = c.face === 'bottom' ? 1.4 : 3.2 - (1 - hy(c)) * 0.8;
    if (c.face === 'top' && [1, 4].includes(Math.floor(c.x))) t = 1.0; // dorsal stripes
    if ((c.face === 'top' || side(c)) && rnd(c.i, c.j, 60) > 0.86) t = 0.2; // bristle sockets
    if (c.face === 'top' && Math.floor(c.x) === 2 && c.z > 3) t = 5;
    return tone(t, c.i, c.j, 0.4, 60, MET);
  });
  A.box('ABDOMEN', 6, 5, 8, c => {
    let t = c.face === 'bottom' ? 2 : 3.4;
    if (c.face !== 'bottom' && Math.floor(c.z) % 3 === 0) t = 1.0; // segment bands
    if (c.face === 'top' && Math.floor(c.z) % 3 === 1 && (Math.floor(c.x) === 2 || Math.floor(c.x) === 3)) t = 5.2; // sheen
    if (rnd(c.i, c.j, 61) > 0.9) t = 0.3;
    return tone(t, c.i, c.j, 0.35, 61, MET);
  });
  A.box('ABD_TIP', 4, 3, 3, c => tone((c.face === 'bottom' ? 2 : 3.0) - (Math.floor(c.z) % 3 === 0 ? 1.6 : 0), c.i, c.j, 0.35, 69, MET));
  A.box('HEAD', 6, 5, 4, c => {
    let t = 4.5;
    if (c.face === 'front') t = 6.8 - Math.abs(c.x - 3) * 0.6; // pale face plate
    if (c.face === 'top') t = 2.2;
    return tone(t, c.i, c.j, 0.3, 62);
  });
  // compound eyes: a checker of facets, a sheen on top
  A.box('EYE', 3, 4, 4, c => {
    const cell = (Math.floor(c.a) + Math.floor(c.b)) & 1;
    let col = mixc(hex(0x2FA03A), hex(0x0E4A18), cell ? 0.2 : 0.55);
    if (c.face === 'top' || (side(c) && c.y > c.h - 1.2)) col = mixc(col, hex(0xDFFFD0), 0.45);
    if (rnd(c.i, c.j, 63) > 0.92) col = mixc(col, hex(0xDFFFD0), 0.5);
    return col;
  });
  A.box('PROBOSCIS', 1, 3, 1, c => tone(2, c.i, c.j, 0.2, 64));
  A.box('PAD', 2, 1, 2, c => tone(5, c.i, c.j, 0.2, 65));
  A.box('ANTENNA', 1, 2, 1, c => tone(1, c.i, c.j, 0.2, 66));
  A.box('LEGSEG', 1, 4, 1, c => tone(c.face === 'front' ? 1.4 : 0.6, c.i, c.j, 0.4, 67, [hex(0x050906), hex(0x16261A), hex(0x334A36)]));
  A.box('HALTERE', 1, 1, 1, c => tone(7, c.i, c.j, 0.2, 68));
  A.box('BRISTLE', 1, 2, 1, () => BLACK);
  // see-through veined wing: u along the span (x), the leading edge at the panel's front (z = d)
  A.plane('WING', 11, 5, c => {
    const span = c.x / c.w, back = 1 - c.z / c.d; // back 0 at the leading edge
    const tipCut = Math.hypot((span - 0.42) / 0.6, (back - 0.5) / 0.56);
    if (tipCut > 1) return CLEAR;
    let col = [210 + 25 * Math.sin(span * 6 + back * 4), 240, 215 - 25 * Math.sin(span * 6 + back * 4), 96];
    for (const v0 of [0.1, 0.35, 0.6]) if (Math.abs(back - (v0 + span * span * 0.25)) < 0.05) col = [30, 58, 32, 225];
    if (back < 0.09 || span < 0.12) col = [24, 46, 26, 235];
    if (Math.abs(span - 0.6) < 0.08 && back > 0.3 && back < 0.65) col = [30, 58, 32, 225];
    return col;
  }, { see: true });
  A.build();
}

// ================================================================================================================== HAWK
{
  const A = new Atlas('hawk', 'HawkUv', 64);
  /** Little feather scallops in rows over a countershaded base. */
  const plumage = (top, bot, seed, streaks) => c => {
    let t = csTone(c, top, bot);
    const row = Math.floor(c.j / 2), off = row & 1;
    if (((c.i + off) % 3) === 0 && (c.j & 1)) t -= 0.7; // scallop edges
    if ((c.j & 1) === 0 && (c.i + off) % 3 === 1) t += 0.3;
    if (streaks && side(c) && hy(c) < 0.55 && (c.i % 3 === 0) && rnd(c.i, row, seed) > 0.4) t = 2.2; // belly streaks
    if (streaks && c.face === 'front' && hy(c) < 0.65 && (c.i % 2 === 0) && rnd(c.i, row, seed) > 0.55) t = 3;
    return tone(t, c.i, c.j, 0.25, seed);
  };
  A.box('BODY', 6, 6, 9, plumage(2.2, 8.2, 70, true));
  A.box('CHEST', 5, 5, 3, plumage(5.5, 8.4, 71, true));
  A.box('RUMP', 4, 3, 3, plumage(2.4, 7.4, 72, false));
  const EYE_H = ['kkk', 'woy', 'ypk'];
  A.box('HEAD', 5, 5, 5, c => {
    const s = sideStamp(c, 1, 1, EYE_H);
    if (s) return { k: G[0], w: WHITE, o: hex(0xF6DA44), y: hex(0xC9A21E), p: BLACK }[s];
    let t = csTone(c, 2.0, 8.4);
    if (side(c) && Math.floor(c.h - c.y) === 3 && c.d - c.z > 3.5) t = 1.0; // dark stripe behind the eye
    if (side(c) && Math.floor(c.h - c.y) === 0) t = 1.6;
    if (c.face === 'front' && hy(c) < 0.45) t = 8.2; // pale throat
    if (((c.i + (c.j >> 1)) % 3) === 0 && (c.j & 1)) t -= 0.4;
    return tone(t, c.i, c.j, 0.25, 73);
  });
  A.box('BROW', 5, 1, 2, c => tone(c.face === 'top' ? 2.2 : 1.2, c.i, c.j, 0.2, 74));
  const BEAK = [0xB8C6B0, 0x7F927A, 0x46574A, 0x1B261D].map(hex);
  A.box('BEAK', 2, 2, 2, c => {
    if (c.z < 0.7 && side(c)) return hex(0xD8E04A); // yellow-green cere at the base
    return tone(c.z / c.d * 2.4, c.i, c.j, 0.2, 75, BEAK);
  });
  A.box('HOOK', 1, 2, 1, c => tone(2.6, c.i, c.j, 0.2, 76, BEAK));
  A.box('BEAK_LOW', 2, 1, 2, c => c.face === 'top' ? mouthPaint(c) : tone(1.4, c.i, c.j, 0.2, 77, BEAK));
  // feathers (planes): u along the feather from its root (x = 0) to the tip, v across (z); dark bars, a pale fringe, a cut tip
  const featherP = (base, barred, seed, tip = 0.3) => c => {
    const u = c.x / c.w, v = 1 - c.z / c.d;
    let half = 1;
    if (u > 1 - tip) half = Math.sqrt(Math.max(0, 1 - Math.pow((u - (1 - tip)) / tip, 2)));
    if (Math.abs(v - 0.5) * 2 > half + 0.02) return CLEAR;
    let t = base - u * 0.5;
    if (barred && Math.floor(c.x) % 3 === 2) t -= 1.2;
    if (Math.abs(v - 0.5) * 2 > half - 0.25) t += 0.8; // pale fringe
    if (u > 0.85) t -= 0.8;
    return tone(t, c.i, c.j, 0.3, seed);
  };
  A.plane('PRIMARY', 10, 2, featherP(3.0, true, 78));
  A.plane('SECONDARY', 7, 2, featherP(3.6, true, 79));
  A.plane('TAILF', 9, 2, c => {
    const r = featherP(5.2, false, 80)(c);
    if (r[3] === 0) return r;
    const u = c.x / c.w;
    if (u > 0.7 && u < 0.84) return tone(1.4, c.i, c.j, 0.2, 81);
    if (u >= 0.9) return tone(8, c.i, c.j, 0.2, 82);
    return r;
  });
  // wing arm: the covert panels (along +X), scalloped rows of small feathers
  A.box('ARM_IN', 5, 1, 5, plumage(2.6, 5.6, 83, false));
  A.box('ARM_OUT', 5, 1, 3, plumage(2.4, 5.2, 84, false));
  A.box('THIGH', 3, 3, 3, c => tone(csTone(c, 6, 8) - ((c.j & 1) ? 0.5 : 0), c.i, c.j, 0.35, 85));
  const LEGY = [0xEFF27A, 0xD5E25A, 0xA9BC36, 0x6F7E1E].map(hex);
  A.box('TARSUS', 1, 4, 1, c => tone(1 + (Math.floor(c.y) % 2 ? 0.8 : 0), c.i, c.j, 0.2, 86, LEGY));
  A.box('TOE', 1, 1, 3, c => tone(1 + (Math.floor(c.z) % 2 ? 0.8 : 0), c.i, c.j, 0.2, 87, LEGY));
  A.box('TALON', 1, 1, 2, clawPaint(true));
  A.build();
}

// ================================================================================================================== BAT
{
  const A = new Atlas('bat', 'BatUv', 64);
  const FUR = [0x0E2210, 0x1E4519, 0x2E6427, 0x3B7E30, 0x52983F, 0x6AAE52, 0x95C877, 0xBFDE9E].map(hex);
  const fur = (top, bot, seed) => c => {
    let t = csTone(c, top, bot) * 0.78;
    if (rnd(c.i, Math.floor(c.j / 2), seed) > 0.75) t += 0.8; // strand tufts
    if ((c.j & 1) && rnd(c.i, c.j, seed + 1) > 0.7) t -= 0.7;
    return tone(t, c.i, c.j, 0.35, seed, FUR);
  };
  A.box('BODY', 5, 6, 5, fur(2.2, 8.4, 90));
  A.box('CHEST', 4, 3, 2, fur(5, 8.4, 91));
  A.box('HEAD', 5, 4, 4, c => {
    const s = sideStamp(c, 0, 1, ['kk']) || frontStamp(c, 0, 0, ['wk.kw', 'pp.pp']);
    if (s) return { k: BLACK, w: hex(0xDDEEDD), p: BLACK }[s];
    return fur(3, 7, 92)(c);
  });
  A.box('SNOUT', 3, 2, 2, c => {
    if (frontStamp(c, 0, 0, ['k.k'])) return BLACK;
    if (c.face === 'bottom') return mouthPaint(c);
    return tone(csTone(c, 4.4, 6.6), c.i, c.j, 0.25, 93, FUR);
  });
  A.box('EAR', 2, 4, 1, c => {
    if (c.face === 'front' && c.x > 0.4 && c.x < 1.6 && c.y < 3.4) return tone(6 + (Math.floor(c.y) % 2 ? -1 : 0), c.i, c.j, 0.2, 94, FUR); // inner ear ridges
    return tone(2 + hy(c), c.i, c.j, 0.3, 95, FUR);
  });
  A.box('FANG', 1, 1, 1, ivory);
  A.box('JAW', 3, 1, 2, c => c.face === 'top' ? mouthPaint(c) : tone(5, c.i, c.j, 0.25, 96, FUR));
  const SKIN = [0x0C1A0D, 0x1B3A1C, 0x2A4F2A, 0x3D6A3B, 0x598A55].map(hex);
  const bone = seed => c => tone(c.face === 'top' ? 1.4 : c.face === 'bottom' ? 3.2 : 2.3, c.i, c.j, 0.3, seed, SKIN);
  A.box('ARM', 6, 1, 1, bone(97));
  A.box('FORE', 7, 1, 1, bone(98));
  A.box('FINGER', 7, 1, 1, bone(99));
  A.box('THUMB', 1, 1, 2, clawPaint(true));
  A.plane('MEMBRANE', 16, 12, c => {
    const span = c.x / c.w, chord = 1 - c.z / c.d;
    let t = 3.8 - Math.abs(chord - 0.45) * 1.6;
    if (frac(span * 5 + Math.sin(chord * 6) * 0.2) < 0.1) t -= 0.8;
    if (frac(chord * 7 + span) < 0.07) t -= 0.5;
    if (chord > 0.9) t = 1.4;
    return tone(t, c.i, c.j, 0.3, 100, SKIN);
  });
  A.box('LEG', 1, 3, 1, bone(101));
  A.box('FOOT', 2, 1, 2, c => tone(1.2, c.i, c.j, 0.2, 102, SKIN));
  A.build();
}
