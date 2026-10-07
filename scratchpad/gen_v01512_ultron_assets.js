// v0.15.12 Ultron Uprising asset generator.
// usage: node gen_v01512_ultron_assets.js <repo or worktree root>
// - copies the six Skindex skins from scratchpad/ultron_skins (main checkout) into textures/entity/ultron/<name>.png as
//   RGBA (the sniper skin is a palette PNG), filling any transparent BASE-layer texel of a real face with the nearest
//   opaque texel of the same face (the Mark 8 lesson), and writes <name>_eyes.png: the strongly red texels alone (drawn
//   full-bright by the renderer's eyes layer);
// - draws the procedural block / item textures (Ultron Beacon idle + active, Ultron Core, pylon segment + head,
//   Vibranium Plating, Mind Stone).
const png = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/nova_skins/png.js');
const fs = require('fs');
const path = require('path');
const root = process.argv[2];
const skinsDir = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/ultron_skins/';
const tex = path.join(root, 'src/main/resources/assets/projecthero/textures');
const entDir = path.join(tex, 'entity/ultron');
fs.mkdirSync(entDir, { recursive: true });
fs.mkdirSync(path.join(tex, 'block'), { recursive: true });
fs.mkdirSync(path.join(tex, 'item'), { recursive: true });

const SKINS = { prime: 'prime_6870675.png', sentry: 'sentry_giant_23814202.png', drone: 'drone_23128749.png',
  sentinel_drone: 'melee_drone_5830525.png', heavy: 'heavy_6722436.png', sniper: 'sniper_23173990.png' };

function faces(x0, y0, w, h, d) {
  return [[x0 + d, y0, w, d], [x0 + d + w, y0, w, d], [x0, y0 + d, d, h], [x0 + d, y0 + d, w, h], [x0 + d + w, y0 + d, d, h], [x0 + 2 * d + w, y0 + d, w, h]];
}

for (const [name, file] of Object.entries(SKINS)) {
  const im = png.decode(fs.readFileSync(skinsDir + file));
  const px = Buffer.from(im.px);
  const A = (x, y) => px[(y * 64 + x) * 4 + 3];
  const slim = A(54, 20) === 0;
  const aw = slim ? 3 : 4;
  const boxes = [faces(0, 0, 8, 8, 8), faces(16, 16, 8, 12, 4), faces(40, 16, aw, 12, 4), faces(0, 16, 4, 12, 4),
    faces(32, 48, aw, 12, 4), faces(16, 48, 4, 12, 4)];
  let filled = 0;
  for (const box of boxes) for (const [fx, fy, fw, fh] of box) {
    for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) {
      if (A(x, y) === 255) continue;
      let best = null, bd = 1e9;
      for (let yy = fy; yy < fy + fh; yy++) for (let xx = fx; xx < fx + fw; xx++) {
        if (A(xx, yy) !== 255) continue;
        const d = (xx - x) ** 2 + (yy - y) ** 2;
        if (d < bd) { bd = d; best = [xx, yy]; }
      }
      const o = (y * 64 + x) * 4;
      if (best) { const s = (best[1] * 64 + best[0]) * 4; px[o] = px[s]; px[o + 1] = px[s + 1]; px[o + 2] = px[s + 2]; }
      else { px[o] = 90; px[o + 1] = 92; px[o + 2] = 98; }
      px[o + 3] = 255;
      filled++;
    }
  }
  const eyes = Buffer.alloc(64 * 64 * 4);
  let red = 0;
  for (let i = 0; i < 64 * 64; i++) {
    const r = px[i * 4], g = px[i * 4 + 1], b = px[i * 4 + 2], a = px[i * 4 + 3];
    if (a > 0 && r >= 150 && r > g * 2.0 && r > b * 2.0) {
      eyes[i * 4] = Math.min(255, r + 30); eyes[i * 4 + 1] = Math.min(255, g + 10); eyes[i * 4 + 2] = Math.min(255, b + 10); eyes[i * 4 + 3] = 255;
      red++;
    }
  }
  fs.writeFileSync(path.join(entDir, name + '.png'), png.encode(64, 64, px));
  fs.writeFileSync(path.join(entDir, name + '_eyes.png'), png.encode(64, 64, eyes));
  console.log(name, 'slim', slim, 'filled', filled, 'glowing texels', red);
}

function canvas(w, h, fill) { const b = Buffer.alloc(w * h * 4); for (let i = 0; i < w * h; i++) { b[i * 4] = fill[0]; b[i * 4 + 1] = fill[1]; b[i * 4 + 2] = fill[2]; b[i * 4 + 3] = fill[3] ?? 255; } return b; }
function set(b, w, x, y, c) { if (x < 0 || y < 0 || x >= w) return; const o = (y * w + x) * 4; b[o] = c[0]; b[o + 1] = c[1]; b[o + 2] = c[2]; b[o + 3] = c[3] ?? 255; }
function noise(b, w, h, amt, seed) { let s = seed; for (let i = 0; i < w * h; i++) { s = (s * 1103515245 + 12345) & 0x7fffffff; const n = (s % (amt * 2 + 1)) - amt; for (let k = 0; k < 3; k++) b[i * 4 + k] = Math.max(0, Math.min(255, b[i * 4 + k] + n)); } }
function save(rel, w, h, b) { fs.writeFileSync(path.join(tex, rel), png.encode(w, h, b)); }
const STEEL = [74, 78, 86], DARK = [38, 40, 46], RIM = [118, 124, 134], RED = [230, 24, 18], RED_D = [140, 10, 8], HOT = [255, 140, 120];
function frame(b, c) { for (let i = 0; i < 16; i++) { set(b, 16, i, 0, c); set(b, 16, i, 15, c); set(b, 16, 0, i, c); set(b, 16, 15, i, c); } }

for (const active of [false, true]) {
  const side = canvas(16, 16, DARK); noise(side, 16, 16, 5, 7); frame(side, RIM);
  for (let y = 2; y < 14; y++) { set(side, 16, 2, y, STEEL); set(side, 16, 13, y, STEEL); }
  const r = active ? RED : RED_D;
  for (let y = 3; y < 13; y++) { set(side, 16, 7, y, r); set(side, 16, 8, y, r); }
  for (let x = 5; x < 11; x++) { set(side, 16, x, 7, r); set(side, 16, x, 8, r); }
  if (active) { set(side, 16, 7, 7, HOT); set(side, 16, 8, 8, HOT); set(side, 16, 7, 8, HOT); set(side, 16, 8, 7, HOT); }
  save('block/ultron_beacon' + (active ? '_active' : '') + '.png', 16, 16, side);
  const top = canvas(16, 16, DARK); noise(top, 16, 16, 5, 9); frame(top, RIM);
  for (let a = 0; a < 32; a++) { const t = a / 32 * Math.PI * 2; set(top, 16, Math.round(7.5 + Math.cos(t) * 5), Math.round(7.5 + Math.sin(t) * 5), r); }
  for (let y = 6; y < 10; y++) for (let x = 6; x < 10; x++) set(top, 16, x, y, active ? HOT : RED_D);
  save('block/ultron_beacon_top' + (active ? '_active' : '') + '.png', 16, 16, top);
}
{
  const s = canvas(16, 16, STEEL); noise(s, 16, 16, 6, 3); frame(s, DARK);
  for (let x = 1; x < 15; x++) { set(s, 16, x, 4, RIM); set(s, 16, x, 11, DARK); }
  for (let x = 6; x < 10; x++) set(s, 16, x, 7, RED);
  save('block/ultron_core.png', 16, 16, s);
  const t = canvas(16, 16, DARK); noise(t, 16, 16, 4, 5); frame(t, RIM);
  for (let y = 5; y < 11; y++) for (let x = 5; x < 11; x++) set(t, 16, x, y, RED_D);
  save('block/ultron_core_top.png', 16, 16, t);
}
{
  const s = canvas(16, 16, [52, 55, 62]); noise(s, 16, 16, 6, 11);
  for (let y = 0; y < 16; y++) { set(s, 16, 0, y, DARK); set(s, 16, 15, y, DARK); set(s, 16, 4, y, RED); set(s, 16, 11, y, RED); set(s, 16, 5, y, RED_D); set(s, 16, 10, y, RED_D); }
  for (let x = 0; x < 16; x++) { set(s, 16, x, 0, RIM); set(s, 16, x, 15, DARK); }
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13], [7, 7], [8, 8]]) set(s, 16, x, y, RIM);
  save('block/ultron_pylon_segment.png', 16, 16, s);
  const e = canvas(16, 16, DARK); noise(e, 16, 16, 4, 13); frame(e, RIM);
  for (let y = 4; y < 12; y++) for (let x = 4; x < 12; x++) set(e, 16, x, y, RED_D);
  for (let y = 6; y < 10; y++) for (let x = 6; x < 10; x++) set(e, 16, x, y, RED);
  save('block/ultron_pylon_end.png', 16, 16, e);
  const h = canvas(16, 16, [66, 70, 78]); noise(h, 16, 16, 6, 17); frame(h, DARK);
  for (let x = 3; x < 13; x++) { set(h, 16, x, 6, RED); set(h, 16, x, 7, HOT); set(h, 16, x, 8, RED); }
  save('block/ultron_pylon_head.png', 16, 16, h);
}
{
  const b = canvas(16, 16, [0, 0, 0, 0]);
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const hex = ((x + (y % 4 < 2 ? 0 : 2)) % 4 === 0) || y % 4 === 0;
    set(b, 16, x, y, hex ? [70, 62, 96] : [104, 96, 138]);
  }
  for (let i = 2; i < 14; i++) { set(b, 16, i, 2, [180, 172, 214]); set(b, 16, 2, i, [160, 150, 200]); set(b, 16, i, 13, [40, 34, 60]); set(b, 16, 13, i, [48, 40, 70]); }
  set(b, 16, 4, 4, [230, 226, 255]); set(b, 16, 5, 4, [210, 204, 245]);
  save('item/vibranium_plating.png', 16, 16, b);
}
{
  const b = canvas(16, 16, [0, 0, 0, 0]);
  const cx = 7.5, cy = 7.5;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.abs(x - cx) + Math.abs(y - cy) * 0.85;
    if (d > 6.2) continue;
    const shade = x + y < 15 ? 1.0 : 0.72;
    const core = d < 2.4;
    const c = core ? [255, 248, 170] : [Math.round(250 * shade), Math.round(200 * shade), Math.round(40 * shade)];
    set(b, 16, x, y, d > 5.4 ? [150, 105, 10] : c);
  }
  set(b, 16, 5, 4, [255, 255, 230]); set(b, 16, 6, 4, [255, 255, 210]); set(b, 16, 5, 5, [255, 255, 210]);
  save('item/mind_stone.png', 16, 16, b);
}
console.log('done');
