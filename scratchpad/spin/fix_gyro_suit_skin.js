// node scratchpad/spin/fix_gyro_suit_skin.js <textures/entity/spin dir> <backup dir>
//
// v0.17.4 (user, after trying Gyro's suit: "make the gyro skin fit on regular player models", "make a cape model and remove the
// cape from the back of the skin"). Rewrites gyro_suit_wide.png / gyro_suit_slim.png and writes gyro_cape.png (vanilla 64x32 cape
// layout, drawn by client/spin/GyroCapeLayer). The PNGs are borrowed art (planetminecraft, licence unchecked) and are NOT in git;
// only this script is.
//
// Always reads the ORIGINAL skins from <backup dir> (copied there on the first run), so it can be re-run safely.
//  1. Cape out: the long green cape painted down the back is removed -- the body back becomes the coat (the cape's folds darkened to
//     the coat's olive) over a belt and trousers; on the legs' backs / outer sides each cape pixel takes the mirrored trouser pixel
//     from the front / inner side; the cape's streaks on the trouser overlay layer are cleared.
//  2. Sleeves filled: the costume arms had holes (rows and back columns where the wearer's own arm showed through), so from behind
//     the sleeve looked like a loose panel floating off the shoulder. Every side row of the base arm is now solid (empty rows copy
//     the row above, single holes take the nearest pixel round the arm), plus the shoulder and the hand.
//  3. Wide = the slim skin with every arm face widened 3 -> 4 columns (the old wide file was a broken conversion: one face too
//     narrow, the back face half empty).
//  4. v0.17.5: the green out of the dark coat (body back/sides, arm shoulders, left arm bands, sleeve specks).
//  5. v0.17.5: gyro_cape.png is an atlas -- the cape split into two tapering tails below the waist, plus the hat brim box.
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');

const [, , texDir, backupDir] = process.argv;
if (!texDir || !backupDir) {
  console.error('usage: node fix_gyro_suit_skin.js <textures/entity/spin dir> <backup dir>');
  process.exit(1);
}
fs.mkdirSync(backupDir, { recursive: true });
for (const n of ['gyro_suit_wide.png', 'gyro_suit_slim.png']) {
  const b = path.join(backupDir, n);
  if (!fs.existsSync(b)) fs.copyFileSync(path.join(texDir, n), b);
}

const src = png.read(path.join(backupDir, 'gyro_suit_slim.png'));
if (src.W !== 64 || src.H !== 64) throw new Error('expected a 64x64 skin');

class Img {
  constructor(W, H, px) { this.W = W; this.H = H; this.px = px ? Buffer.from(px) : Buffer.alloc(W * H * 4); }
  get(x, y) { const i = (y * this.W + x) * 4; return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]]; }
  set(x, y, c) { const i = (y * this.W + x) * 4; for (let k = 0; k < 4; k++) this.px[i + k] = c[k]; }
  op(x, y) { return this.px[(y * this.W + x) * 4 + 3] > 0; }
}
const hex = c => c.slice(0, 3).map(v => v.toString(16).padStart(2, '0')).join('');
const rgb = h => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 255];
const CLEAR = [0, 0, 0, 0];

// the cape's palette (its folds and highlights)
const CAPE = new Set(['526621', '727a34', '828e54', '879e55', '9db96f', '8bd76e', '4f7a3f', '8eab5f']);
const isCape = (img, x, y) => img.op(x, y) && CAPE.has(hex(img.get(x, y)));
// cape fold -> coat olive (the coat's own colours on the body's sides)
const COAT = { '879e55': '727a34', '828e54': '526621', '727a34': '526621', '526621': '464623', '9db96f': '727a34', '8bd76e': '727a34', '4f7a3f': '464623', '8eab5f': '727a34' };
const TROUSER = rgb('553b0f'), BELT = rgb('6d5731'), BELT_LIGHT = rgb('917038');

const s = new Img(64, 64, src.px);
const orig = new Img(64, 64, src.px);

// ---------------------------------------------------------------- 1. the cape out
// body back (32..39, 20..31): coat, belt, trousers
for (let y = 20; y < 32; y++) for (let x = 32; x < 40; x++) {
  if (y <= 28) {
    const h = hex(orig.get(x, y));
    s.set(x, y, COAT[h] ? rgb(COAT[h]) : rgb('526621'));
  } else if (y === 29) {
    s.set(x, y, BELT);
  } else if (y === 30) {
    s.set(x, y, x === 34 || x === 37 ? BELT_LIGHT : BELT);
  } else {
    // the front's bottom trouser row, mirrored (front x20..27)
    const f = orig.get(20 + (39 - x), 31);
    s.set(x, y, f[3] ? f : TROUSER);
  }
}
// legs: {front, inner, outer, back} face x origins + the first side row
const LEGS = [{ front: 4, inner: 8, outer: 0, back: 12, y0: 20 }, { front: 20, inner: 16, outer: 24, back: 28, y0: 52 }];
for (const L of LEGS) {
  for (let y = L.y0; y < L.y0 + 12; y++) {
    for (let c = 0; c < 4; c++) {
      for (const [face, from] of [[L.back, L.front], [L.outer, L.inner]]) {
        const x = face + c;
        if (!isCape(orig, x, y)) continue;
        const fx = from + (3 - c);
        s.set(x, y, orig.op(fx, y) && !isCape(orig, fx, y) ? orig.get(fx, y) : TROUSER);
      }
    }
  }
}
// trouser overlay layer: the cape's streaks (and the tan tip of one) go
for (const [ox, oy] of [[0, 32], [0, 48]]) {
  for (let y = oy; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) {
    const h = hex(s.get(x, y));
    if (s.op(x, y) && (h === '9db96f' || h === '8eab5f' || h === 'a77750')) s.set(x, y, CLEAR);
  }
}

// ---------------------------------------------------------------- 2. solid sleeves (slim layout)
// slim arm box at (ox, oy): top (ox+4, oy) 3x4, bottom (ox+7, oy) 3x4; side strip at y oy+4: right 4, front 3, left 4, back 3 = 14
function fillArm(img, ox, oy) {
  const ring = [];
  for (let x = ox; x < ox + 14; x++) ring.push(x);
  let prev = null;
  for (let y = oy + 4; y < oy + 16; y++) {
    const opaque = ring.filter(x => img.op(x, y)).length;
    if (opaque < 4 && prev) {
      for (const x of ring) img.set(x, y, img.get(x, prev));
    } else {
      const row = ring.map(x => img.get(x, y));
      for (let i = 0; i < ring.length; i++) {
        if (row[i][3]) continue;
        for (let d = 1; d < ring.length; d++) {
          const l = row[(i - d + ring.length) % ring.length], r = row[(i + d) % ring.length];
          if (l[3]) { img.set(ring[i], y, l); break; }
          if (r[3]) { img.set(ring[i], y, r); break; }
        }
      }
    }
    prev = y;
  }
  // shoulder (top): holes take the face's own colour, else the first side row above them
  for (let y = oy; y < oy + 4; y++) for (let x = ox + 4; x < ox + 7; x++) {
    if (img.op(x, y)) continue;
    let c = null;
    for (let yy = oy; yy < oy + 4 && !c; yy++) for (let xx = ox + 4; xx < ox + 7 && !c; xx++) if (img.op(xx, yy)) c = img.get(xx, yy);
    img.set(x, y, c || img.get(x, oy + 4));
  }
  // hand (bottom): the glove, i.e. the last side row's commonest colour
  const count = {};
  for (const x of ring) { const h = hex(img.get(x, oy + 15)); count[h] = (count[h] || 0) + 1; }
  const glove = rgb(Object.entries(count).sort((a, b) => b[1] - a[1])[0][0]);
  for (let y = oy; y < oy + 4; y++) for (let x = ox + 7; x < ox + 10; x++) img.set(x, y, glove);
}
fillArm(s, 40, 16);
fillArm(s, 32, 48);

// ---------------------------------------------------------------- 3. wide = the slim arms widened
const w = new Img(64, 64, s.px);
const WIDEN = [0, 1, 1, 2];
for (const [ox, oy] of [[40, 16], [40, 32], [32, 48], [48, 48]]) {
  for (let y = oy; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) w.set(x, y, CLEAR);
  const copyFace = (sx, dx, y0, h, wide) => {
    for (let y = y0; y < y0 + h; y++) for (let c = 0; c < (wide ? 4 : 4); c++) {
      const from = wide ? sx + WIDEN[c] : sx + c;
      w.set(dx + c, y, s.get(from, y));
    }
  };
  copyFace(ox + 4, ox + 4, oy, 4, true); // top
  copyFace(ox + 7, ox + 8, oy, 4, true); // bottom
  copyFace(ox, ox, oy + 4, 12, false); // right side (depth 4)
  copyFace(ox + 4, ox + 4, oy + 4, 12, true); // front
  copyFace(ox + 7, ox + 8, oy + 4, 12, false); // left side (depth 4)
  copyFace(ox + 11, ox + 12, oy + 4, 12, true); // back
}

// ---------------------------------------------------------------- 4. v0.17.5 the green out of the dark
// (user: "there still some green back on gyro, please remove all the green from the black of his skin"). The painted cape left green on
// the coat: the body's back and sides (olive), the arms' inner and back faces at the shoulder, the left arm's two bands and specks on the
// sleeve overlays. Every green pixel there becomes the coat's dark purple of the same lightness (overlay specks go, the dark base shows).
// A speck on each inner thigh takes the trousers' dark brown. Kept: the head (hat), the boots (their green soles and rims) and the
// coat's front lapels (body top front half + front face, x 20..27, y 18..31), which read as the cape's collar over the shoulders.
const DARK = ['1b0a21', '291430', '2d1f47', '463059'].map(rgb);
const isGreen = c => c[3] > 0 && c[1] >= c[0] && c[1] > c[2] + 20; // greens and olives (the coat's browns have red > green)
const lum = c => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
const toDark = c => { const l = lum(c); return DARK[l < 70 ? 0 : l < 95 ? 1 : l < 120 ? 2 : 3]; };
const GREEN_OUT = [ // [name, x, y, w, h, overlay, flat colour]
  ['body (top back half, bottom, sides, back)', 16, 16, 24, 16, false],
  ['right arm', 40, 16, 16, 16, false],
  ['left arm', 32, 48, 16, 16, false],
  ['jacket overlay', 16, 32, 24, 16, true],
  ['right sleeve overlay', 40, 32, 16, 16, true],
  ['left sleeve overlay', 48, 48, 16, 16, true],
  ['right leg (sides above the boot rim)', 0, 20, 16, 11, false, rgb('291909')],
  ['left leg (sides above the boot rim)', 16, 52, 16, 11, false, rgb('291909')],
];
const lapel = (x, y) => x >= 20 && x < 28 && y >= 18 && y < 32;
function greenOut(img, tag) {
  for (const [name, x0, y0, rw, rh, over, flat] of GREEN_OUT) {
    const hit = [];
    for (let y = y0; y < y0 + rh; y++) for (let x = x0; x < x0 + rw; x++) {
      if (lapel(x, y) || !isGreen(img.get(x, y))) continue;
      img.set(x, y, over ? CLEAR : flat || toDark(img.get(x, y)));
      hit.push([x, y]);
    }
    if (hit.length) {
      const xs = hit.map(p => p[0]), ys = hit.map(p => p[1]);
      console.log(`${tag}: ${name}: ${hit.length} px in x ${Math.min(...xs)}..${Math.max(...xs)}, y ${Math.min(...ys)}..${Math.max(...ys)}`);
    }
  }
}
greenOut(s, 'slim');
greenOut(w, 'wide');

png.write(path.join(texDir, 'gyro_suit_slim.png'), 64, 64, s.px);
png.write(path.join(texDir, 'gyro_suit_wide.png'), 64, 64, w.px);

// ---------------------------------------------------------------- 5. the cape + hat brim (64x32 atlas, client/spin/GyroCapeLayer)
// v0.17.5 (user: "his cape almost splits off into 2 capes ... make it look like that"): one panel across the shoulders that splits below
// the waist into two tapering tails. The outside of the whole cape is one virtual 10 x 18 picture (the skin's painted cape, stretched);
// each box samples it where it hangs. Boxes are 1 deep; a box (w, h) at uv (u, v): top (u+1, v) w x 1, bottom (u+1+w, v), edges (u, v+1)
// and (u+1+w, v+1) 1 x h, outside (u+1, v+1) w x h, inside (u+2+w, v+1) w x h.
//   top panel 10 x 9 at (0,0), x -5..5;   left tail 4x4 (0,10) x -5..-1, 3x3 (12,10) x -5..-2, 2x2 (22,10) x -5..-3;
//   right tail 4x4 (0,15) x 1..5, 3x3 (12,15) x 2..5, 2x2 (22,15) x 3..5;   hat brim 11 x 1 x 11 at (0,20).
const cape = new Img(64, 32);
const COLS = [0, 0, 1, 2, 3, 4, 5, 6, 7, 7];
const capePx = (cx, cy) => {
  const sy = cy < 12 ? cy : 8 + ((cy - 12) % 4);
  let c = orig.get(32 + COLS[cx], 20 + sy);
  if (!c[3] || !CAPE.has(hex(c))) c = rgb('727a34');
  return c;
};
const shade = (c, k) => [Math.round(c[0] * k), Math.round(c[1] * k), Math.round(c[2] * k), 255];
const EDGE = rgb('464623'), COLLAR = rgb('526621');
// one box: cx0 = its first column of the virtual cape, cy0 its first row, hem = its bottom is a free edge
function capeBox(u, v, bw, bh, cx0, cy0, hem) {
  for (let r = 0; r < bh; r++) for (let c = 0; c < bw; c++) {
    let col = capePx(cx0 + c, cy0 + r);
    if (cy0 + r === 0) col = shade(col, 0.8); // the collar's shadow
    if (hem && r === bh - 1) col = shade(col, 0.85); // the hem
    cape.set(u + 1 + c, v + 1 + r, col);
    cape.set(u + 2 + bw + (bw - 1 - c), v + 1 + r, shade(col, 0.7)); // inside, mirrored
  }
  for (let r = 0; r < bh; r++) { cape.set(u, v + 1 + r, EDGE); cape.set(u + 1 + bw, v + 1 + r, EDGE); }
  for (let c = 0; c < bw; c++) { cape.set(u + 1 + c, v, cy0 === 0 ? COLLAR : EDGE); cape.set(u + 1 + bw + c, v, EDGE); }
}
capeBox(0, 0, 10, 9, 0, 0, false);
capeBox(0, 10, 4, 4, 0, 9, false); capeBox(12, 10, 3, 3, 0, 13, false); capeBox(22, 10, 2, 2, 0, 16, true);
capeBox(0, 15, 4, 4, 6, 9, false); capeBox(12, 15, 3, 3, 7, 13, false); capeBox(22, 15, 2, 2, 8, 16, true);
// the hat brim (box 11 x 1 x 11: top (11,20), underside (22,20), rims along row 31): the hat's own browns (top), darker underneath, the rim's edge darker still
{
  const B = rgb('9b6742'), BL = rgb('b4825f'), BD = rgb('865a2c'), UNDER = rgb('694321'), RIM = rgb('694321');
  const u = 0, v = 20, n = 11;
  for (let z = 0; z < n; z++) for (let x = 0; x < n; x++) {
    const ring = Math.min(x, z, n - 1 - x, n - 1 - z); // 0 = the outer edge of the brim
    cape.set(u + n + x, v + z, ring === 0 ? BD : (x * 37 + z * 91) % 11 === 0 ? BL : B); // top
    cape.set(u + 2 * n + x, v + z, ring === 0 ? RIM : UNDER); // underside
  }
  // the four 1-px rims (west, north, east, south) in the row under the top/bottom faces
  for (let x = 0; x < 2 * n + 2 * n; x++) cape.set(u + x, v + n, RIM);
}
png.write(path.join(texDir, 'gyro_cape.png'), 64, 32, cape.px);
console.log('wrote gyro_suit_wide.png, gyro_suit_slim.png, gyro_cape.png in', texDir);
