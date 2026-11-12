// v0.17.3 Raven: every texture of the power, drawn in code.
//
//   node scratchpad/raven/build_raven_textures.js
//
// Writes (assets/projecthero/textures/...):
//   entity/raven/white.png            16x16 white (vertex-coloured effects)
//   entity/raven/shadow.png           64x64 the Wrath's shadow shell (near-black, faint violet veins), player skin layout-agnostic
//   entity/raven/soul_self.png        64x64 the raven soul-self (SoulSelfRenderer's UVs; white eyes at 56,0)
//   entity/raven/cloak_<c>.png        64x64 the cape: left half outside, right half lining (MoonKnightCapeLayer.drawCape's layout)
//   models/armor/raven_cloak_layer_1.png  64x32 the leotard under the cloak (body + arms, gem belt)
//   item/book_of_azarath.png          16x16
//   item/raven_cloak.png              16x16
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');

const ROOT = path.resolve(__dirname, '..', '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures');

function canvas(w, h) {
  const px = Buffer.alloc(w * h * 4);
  return {
    w, h, px,
    set(x, y, [r, g, b, a = 255]) {
      if (x < 0 || y < 0 || x >= w || y >= h) return;
      const i = (y * w + x) * 4;
      px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
    },
    rect(x0, y0, x1, y1, f) {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) this.set(x, y, typeof f === 'function' ? f(x, y) : f);
    },
    save(rel) {
      const out = path.join(TEX, rel);
      fs.mkdirSync(path.dirname(out), { recursive: true });
      png.write(out, w, h, px);
      console.log('wrote', rel);
    },
  };
}

// a small deterministic noise
function hash(x, y, s = 0) {
  let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0;
  h = (h ^ (h >>> 13)) * 1274126177 | 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));

// ------------------------------------------------------------------ white
{ const c = canvas(16, 16); c.rect(0, 0, 16, 16, [255, 255, 255]); c.save('entity/raven/white.png'); }

// ------------------------------------------------------------------ shadow shell
{
  const c = canvas(64, 64);
  c.rect(0, 0, 64, 64, (x, y) => {
    const vein = Math.max(0, Math.sin(x * 0.7 + Math.sin(y * 0.3) * 3) - 0.85) * 6;
    const n = hash(x, y, 3) * 6;
    return [clamp(10 + n + vein * 40), clamp(4 + n * 0.5 + vein * 10), clamp(16 + n + vein * 70), 255];
  });
  c.save('entity/raven/shadow.png');
}

// ------------------------------------------------------------------ soul-self raven
{
  const c = canvas(64, 64);
  const base = [20, 8, 30], feather = [48, 20, 86], edge = [112, 52, 190];
  c.rect(0, 0, 64, 64, (x, y) => {
    const n = hash(x, y, 7);
    return mix(base, feather, n * 0.5);
  });
  // feather streaks along the wings (inner 0..52 x 42..53, tips 0..40 x 53..62) and the tail (0..32 x 32..41)
  const streaks = (x0, y0, x1, y1, step) => {
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const along = (x % step) / step;
      const t = along < 0.2 ? 0.8 : 0.25 + hash(x, y, 9) * 0.25;
      c.set(x, y, mix(base, along < 0.2 ? edge : feather, t));
    }
  };
  streaks(0, 42, 52, 53, 4);
  streaks(0, 53, 40, 62, 3);
  streaks(0, 32, 32, 41, 3);
  // a lighter ridge down the body's back and the head's crown
  c.rect(6, 0, 12, 20, (x, y) => mix(base, edge, 0.25 + hash(x, y, 2) * 0.2));
  c.rect(5, 20, 10, 30, (x, y) => mix(base, edge, 0.2 + hash(x, y, 4) * 0.15));
  // the beak: darker
  c.rect(20, 20, 32, 26, (x, y) => mix([8, 4, 12], base, hash(x, y, 5) * 0.4));
  // the eyes: white
  c.rect(56, 0, 64, 8, [255, 255, 255]);
  c.save('entity/raven/soul_self.png');
}

// ------------------------------------------------------------------ the cape
function cape(name, main, lining, hem) {
  const c = canvas(64, 64);
  c.rect(0, 0, 32, 64, (x, y) => {
    const fold = 0.5 + 0.5 * Math.sin((x / 32) * Math.PI * 5);
    const shade = 0.78 + fold * 0.22 - (y / 64) * 0.08;
    const n = (hash(x, y, 11) - 0.5) * 0.05;
    let col = main.map((v) => clamp(v * (shade + n)));
    if (y >= 60) col = mix(col, hem, 0.55);
    if (y < 2) col = mix(col, [0, 0, 0], 0.25);
    return [...col, 255];
  });
  c.rect(32, 0, 64, 64, (x, y) => {
    const n = (hash(x, y, 13) - 0.5) * 0.05;
    const shade = 0.7 + 0.12 * Math.sin(((x - 32) / 32) * Math.PI * 5) + n;
    return [...lining.map((v) => clamp(v * shade)), 255];
  });
  c.save('entity/raven/cloak_' + name + '.png');
}
cape('blue', [44, 62, 148], [22, 24, 52], [70, 92, 190]);
cape('white', [230, 228, 238], [150, 146, 170], [255, 255, 255]);
cape('red', [94, 22, 34], [36, 8, 14], [150, 30, 40]);
cape('black', [16, 8, 20], [6, 2, 8], [90, 10, 20]);

// ------------------------------------------------------------------ the leotard (armour layer 1: body 16..40 x 16..32, arms 40..56 x 16..32)
{
  const c = canvas(64, 32);
  const suit = [22, 22, 40];
  const tone = (x, y) => {
    const n = (hash(x, y, 17) - 0.5) * 8;
    return [clamp(suit[0] + n), clamp(suit[1] + n), clamp(suit[2] + n * 1.4), 255];
  };
  c.rect(16, 16, 40, 32, tone);
  c.rect(40, 16, 56, 32, tone);
  // the belt of red gems (body front 20..28, row 27)
  for (let x = 20; x < 28; x++) c.set(x, 27, x % 2 === 0 ? [196, 160, 70, 255] : [70, 60, 40, 255]);
  for (const x of [21, 23, 25, 27]) c.set(x, 27, [200, 24, 40, 255]);
  // the gauntlet cuffs
  c.rect(44, 29, 48, 32, [40, 40, 64, 255]);
  c.rect(48, 29, 52, 32, [40, 40, 64, 255]);
  c.save('models/armor/raven_cloak_layer_1.png');
}

// ------------------------------------------------------------------ item icons
{
  const c = canvas(16, 16);
  const cover = [58, 30, 82], edge = [30, 14, 44], pages = [214, 204, 180], gold = [214, 170, 60], gem = [210, 30, 50];
  c.rect(3, 2, 13, 14, (x, y) => [...mix(cover, edge, x === 3 || y === 2 || y === 13 ? 0.7 : hash(x, y, 1) * 0.25), 255]);
  c.rect(13, 3, 14, 14, [...pages, 255]);
  c.rect(4, 13, 14, 14, [...pages, 255]);
  // the emblem: a gold ring with the red gem
  for (const [x, y] of [[7, 5], [8, 5], [9, 5], [6, 6], [10, 6], [6, 7], [10, 7], [6, 8], [10, 8], [7, 9], [8, 9], [9, 9]]) c.set(x, y, [...gold, 255]);
  c.set(8, 7, [...gem, 255]); c.set(8, 6, [...mix(gem, [255, 255, 255], 0.3), 255]); c.set(7, 7, [...gem, 255]); c.set(9, 7, [...gem, 255]); c.set(8, 8, [...gem, 255]);
  c.rect(5, 11, 11, 12, [...gold, 200]);
  c.save('item/book_of_azarath.png');
}
{
  const c = canvas(16, 16);
  const blue = [44, 62, 148], dark = [20, 28, 70], light = [80, 104, 200];
  // a hooded cloak, front view: the hood's point and the cloak flaring down
  const rows = ['......XX........', '.....XXXX.......', '....XXOOXX......', '....XOOOOX......', '....XOooOX......', '...XXOOOOXX.....',
    '...XXXooXXX.....', '..XXXXooXXXX....', '..XXXXooXXXX....', '.XXXXXooXXXXX...', '.XXXXXooXXXXX...', 'XXXXXXooXXXXXX..',
    'XXXXXXooXXXXXX..', 'XXXXXXXXXXXXXX..', '.XX.XX.XX.XX.X..', '................'];
  rows.forEach((row, y) => [...row].forEach((ch, x) => {
    const xx = x + 1;
    if (ch === 'X') c.set(xx, y, [...mix(blue, x < 7 ? light : dark, 0.2 + hash(x, y, 3) * 0.2), 255]);
    else if (ch === 'O') c.set(xx, y, [12, 10, 24, 255]);
    else if (ch === 'o') c.set(xx, y, [22, 22, 40, 255]);
  }));
  c.set(7, 3, [210, 30, 50, 255]); // the gem under the hood
  c.save('item/raven_cloak.png');
}
