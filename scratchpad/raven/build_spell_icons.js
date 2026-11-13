// v0.17.3 Raven revamp: the Spell Wheel's ten icons, drawn in code as one 160x16 atlas (16 px each, RavenSpell order).
//
//   node scratchpad/raven/build_spell_icons.js
//
// Writes assets/projecthero/textures/gui/raven/spells.png, plus a 10x preview sheet to scratchpad/raven/spells_preview.png.
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');

const ROOT = path.resolve(__dirname, '..', '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/gui/raven/spells.png');
const W = 160, H = 16;
const px = Buffer.alloc(W * H * 4);

// palette (her dark purple and black, a pale soul white, a few accents)
const C = {
  ink: [12, 6, 18], deep: [34, 14, 52], purple: [92, 38, 150], violet: [146, 80, 214], lilac: [196, 160, 246],
  white: [240, 236, 255], red: [200, 30, 50], stone: [118, 110, 104], stoneD: [78, 72, 70], dirt: [120, 84, 52],
  heart: [232, 214, 250], gold: [230, 200, 120],
};

let ox = 0;
function set(x, y, c, a = 255) {
  if (x < 0 || y < 0 || x >= 16 || y >= 16) return;
  const i = (y * W + ox + x) * 4;
  // simple over-blend
  const sa = a / 255, da = px[i + 3] / 255, oa = sa + da * (1 - sa);
  for (let k = 0; k < 3; k++) px[i + k] = Math.round((c[k] * sa + px[i + k] * da * (1 - sa)) / (oa || 1));
  px[i + 3] = Math.round(oa * 255);
}
function rows(map, keys) {
  map.forEach((row, y) => [...row].forEach((ch, x) => { if (keys[ch]) set(x, y, keys[ch]); }));
}
function disc(cx, cy, r, c, a = 255) {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r) set(x, y, c, a);
}
function ring(cx, cy, r0, r1, c, a = 255) {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
    if (d >= r0 && d <= r1) set(x, y, c, a);
  }
}
function line(x0, y0, x1, y1, c, a = 255) {
  const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1;
  for (let i = 0; i <= n; i++) set(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), c, a);
}
function outline(c) {
  // a 1-px dark outline round everything drawn in this cell (keeps icons readable on any slice colour)
  const solid = [];
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) solid.push(px[(y * W + ox + x) * 4 + 3] > 40);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (solid[y * 16 + x]) continue;
    const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => {
      const xx = x + dx, yy = y + dy;
      return xx >= 0 && yy >= 0 && xx < 16 && yy < 16 && solid[yy * 16 + xx];
    });
    if (n) set(x, y, c, 220);
  }
}

// 0 Void Vortex: a spiral round a black eye
ox = 0;
disc(8, 8, 7.2, C.deep, 200);
for (let t = 0; t < 3 * Math.PI * 2; t += 0.05) {
  const r = 1.2 + t * 0.33;
  if (r > 7) break;
  set(Math.round(8 + Math.cos(t) * r - 0.5), Math.round(8 + Math.sin(t) * r - 0.5), t > 9 ? C.violet : C.lilac);
}
disc(8, 8, 2.0, C.ink);
set(7, 7, C.white);
outline(C.ink);

// 1 Shadow Binding: claws curling up out of a dark pool
ox = 16;
for (let x = 1; x < 15; x++) { set(x, 14, C.ink); set(x, 13, C.deep); }
set(0, 14, C.ink, 140); set(15, 14, C.ink, 140);
// three claws, each a stroke that rises then hooks over, with a violet edge on the inside of the curve
const claw = (x, h, dir) => {
  let px0 = x, py0 = 12;
  for (let i = 0; i <= h; i++) {
    const t = i / h;
    const xx = Math.round(x + dir * t * t * 4);
    const yy = Math.round(12 - t * h);
    set(xx, yy, C.ink);
    if (t < 0.55) set(xx - dir, yy, C.ink);
    if (t > 0.3) set(xx + dir, yy + 1, C.violet);
    px0 = xx; py0 = yy;
  }
  set(px0 + dir, py0 + 1, C.lilac);
};
claw(3, 9, 1); claw(8, 11, 1); claw(12, 8, -1);
outline(C.deep);

// 2 Soul Detonation: a marked sigil, bursting
ox = 32;
for (let a = 0; a < 8; a++) {
  const ang = a * Math.PI / 4;
  line(8 + Math.cos(ang) * 4, 8 + Math.sin(ang) * 4, 8 + Math.cos(ang) * 7.4, 8 + Math.sin(ang) * 7.4, a % 2 ? C.purple : C.violet);
}
ring(8, 8, 3.0, 4.3, C.lilac);
disc(8, 8, 2.3, C.ink);
set(7, 7, C.white); set(8, 7, C.white); set(7, 8, C.violet); set(8, 8, C.violet);
outline(C.ink);

// 3 Soul Devour: a drain beam pulling a soul out of a target
ox = 48;
for (let x = 1; x < 12; x++) {
  const y = 8 + Math.round(Math.sin(x * 0.9) * 1.2);
  set(x, y, C.violet); set(x, y - 1, C.purple, 180); set(x, y + 1, C.purple, 180);
}
disc(12.5, 8, 3.2, C.ink);
disc(12.5, 7.5, 1.9, C.lilac);
set(12, 7, C.ink); set(13, 7, C.ink);
disc(2.5, 8, 2.2, C.white);
outline(C.ink);

// 4 Debris Storm: rocks orbiting
ox = 64;
ring(8, 8, 5.2, 6.0, C.violet, 160);
const rock = (x, y, c, d) => { for (let yy = 0; yy < 3; yy++) for (let xx = 0; xx < 3; xx++) set(x + xx, y + yy, (xx + yy) % 3 === 0 ? d : c); };
rock(2, 3, C.stone, C.stoneD); rock(11, 2, C.dirt, C.stoneD); rock(12, 10, C.stone, C.stoneD); rock(3, 11, C.dirt, C.stoneD);
disc(8, 8, 1.8, C.ink); set(7, 7, C.violet);
outline(C.ink);

// 5 Dimensional Rift: a tall purple portal
ox = 80;
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
  const e = ((x + 0.5 - 8) / 4.6) ** 2 + ((y + 0.5 - 8) / 7.4) ** 2;
  if (e <= 1) set(x, y, e > 0.72 ? C.lilac : e > 0.45 ? C.violet : e > 0.2 ? C.purple : C.ink);
}
set(7, 5, C.white, 200); set(9, 10, C.white, 160);
outline(C.ink);

// 6 Soul Restoration: a pale heart held in dark hands
ox = 96;
rows([
  '................',
  '................',
  '...WWW...WWW....',
  '..WHHHW.WHHHW...',
  '..WHHHHWHHHHW...',
  '..WHHHHHHHHHW...',
  '...WHHHHHHHW....',
  '....WHHHHHW.....',
  '.....WHHHW......',
  '......WHW.......',
  '.......W........',
  '................',
  '.KK.........KK..',
  '.KKKK.....KKKK..',
  '..KKKKKKKKKKK...',
  '................',
], { W: C.lilac, H: C.heart, K: C.ink });
set(5, 4, C.white); set(6, 4, C.white);
outline(C.deep);

// 7 Telekinetic Pull: arrows drawn inward to a dark palm
ox = 112;
disc(8, 8, 2.4, C.ink); set(7, 7, C.violet);
const arrow = (x0, y0, dx, dy) => {
  for (let i = 0; i < 4; i++) set(x0 + dx * i, y0 + dy * i, C.violet);
  const hx = x0 + dx * 3, hy = y0 + dy * 3;
  set(hx - dy - dx, hy + dx - dy, C.lilac); set(hx + dy - dx, hy - dx - dy, C.lilac);
};
arrow(1, 1, 1, 1); arrow(14, 1, -1, 1); arrow(1, 14, 1, -1); arrow(14, 14, -1, -1);
set(4, 13, C.gold); set(12, 3, C.gold);
outline(C.ink);

// 8 Astral Sanctuary: a dome over the ground
ox = 128;
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
  const d = Math.hypot(x + 0.5 - 8, (y + 0.5 - 13) * 1.15);
  if (y <= 13 && d <= 7.2) set(x, y, d > 6.1 ? C.lilac : C.purple, d > 6.1 ? 255 : 120);
}
for (let x = 0; x < 16; x++) set(x, 14, C.ink);
set(7, 11, C.white); set(8, 11, C.white); set(7, 12, C.white); set(8, 12, C.white); // the figure inside
set(5, 6, C.white, 200);
outline(C.ink);

// 9 Shadow Veil: a hooded figure dissolving into shadow
ox = 144;
rows([
  '................',
  '.....KKKKK......',
  '....KKKKKKK.....',
  '...KKK...KKK....',
  '...KK.E.E.KK....',
  '...KK.....KK....',
  '...KKK...KKK....',
  '..KKKKKKKKKKK...',
  '..KKKKKKKKKKK...',
  '.KKKKKKKKKKKKK..',
  '.KK.KKKKKKK.KK..',
  '.K..K.KKK.K..K..',
  '....K..K..K.....',
  '.......K........',
  '................',
  '................',
], { K: C.deep, E: C.white });
for (let y = 9; y < 15; y++) for (let x = 0; x < 16; x++) if ((x * 7 + y * 3) % 5 === 0) set(x, y, C.ink, 160);
outline(C.violet);

fs.mkdirSync(path.dirname(OUT), { recursive: true });
png.write(OUT, W, H, px);
console.log('wrote', path.relative(ROOT, OUT));

// a 10x preview on a mid-grey for checking
const S = 10, PW = W * S, PH = H * S;
const pv = Buffer.alloc(PW * PH * 4);
for (let y = 0; y < PH; y++) for (let x = 0; x < PW; x++) {
  const i = ((y / S | 0) * W + (x / S | 0)) * 4, o = (y * PW + x) * 4, a = px[i + 3] / 255;
  const bg = ((x / S | 0) / 16 | 0) % 2 ? 60 : 44;
  for (let k = 0; k < 3; k++) pv[o + k] = Math.round(px[i + k] * a + bg * (1 - a));
  pv[o + 3] = 255;
}
png.write(path.join(__dirname, 'spells_preview.png'), PW, PH, pv);
