// v0.16.2 Spider-Man suit item icons (original suit + Suit v2), 16x16 vanilla-style.
// Colours sampled from textures/armor/spider_man.png and spider_man_v2.png.
// Usage: node gen_suit_icons.js <assets/projecthero dir> <preview.png>
const P = require('./png.js');
const path = require('path');
const [, , ASSETS, PREVIEW] = process.argv;

const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

// ramps: [outline, dark, shade, mid, light, highlight]
const SUITS = {
  orig: { // Homecoming-style: bright crimson + teal-navy, black trim, small emblem
    R: ['#4c0619', '#8c0c2c', '#b21039', '#c90f3e', '#dc0f44', '#f2506f'],
    B: ['#011221', '#022441', '#052f4f', '#084162', '#0d4968', '#2b6d8e'],
    w: '#5e0720', K: '#0d0709', E: '#e4e4e4', e: '#c9d9e1', S: '#ffffff',
  },
  v2: { // classic: deeper red with black webbing + dark navy
    R: ['#2e070b', '#530e14', '#861923', '#b72535', '#d23d4e', '#e8707e'],
    B: ['#040a16', '#0b1a32', '#0e2244', '#112950', '#203b60', '#3a5a84'],
    w: '#330a10', K: '#0a0b10', E: '#f0f3f6', e: '#c4c0e2', S: '#ffffff',
  },
};

// Build a 16x16 grid from rows; 'odd' mirrors col i -> 14-i (cols 0..7 given), 'even' mirrors i -> 15-i.
function sym(rows, mode) {
  return rows.map(r => {
    const a = r.padEnd(8, '.').split('');
    const out = new Array(16).fill('.');
    for (let i = 0; i < 8; i++) {
      out[i] = a[i];
      const j = mode === 'odd' ? 14 - i : 15 - i;
      if (j !== i && j < 16 && out[j] === '.') out[j] = a[i];
    }
    return out.join('');
  });
}
// overlay asymmetric tweaks: list of [x,y,ch]
function poke(g, list) { g = g.map(r => r.split('')); for (const [x, y, c] of list) g[y][x] = c; return g.map(r => r.join('')); }

// o/p = forced red/blue outline (internal seams), v = subtle red web line
const MAT = { o: 'R', p: 'B', v: 'R', R: 'R', H: 'R', r: 'R', d: 'R', B: 'B', L: 'B', b: 'B', n: 'B' };
const FIX = { o: 0, p: 0, v: 1, R: null, H: 5, r: 2, d: 1, B: null, L: 5, b: 2, n: 1 };

function render(grid, pal) {
  const px = Buffer.alloc(16 * 16 * 4);
  const at = (x, y) => (x < 0 || y < 0 || x > 15 || y > 15) ? '.' : grid[y][x];
  const filled = (x, y) => at(x, y) !== '.';
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = at(x, y); if (c === '.') continue;
    const edge = !filled(x - 1, y) || !filled(x + 1, y) || !filled(x, y - 1) || !filled(x, y + 1);
    let col;
    if (MAT[c]) {
      const ramp = pal[MAT[c]];
      if (edge) col = ramp[0];
      else if (FIX[c] != null) col = ramp[FIX[c]];
      else {
        let lv = 3;
        const nearTL = !filled(x - 2, y) || !filled(x, y - 2) || !filled(x - 1, y - 1);
        const nearBR = !filled(x + 2, y) || !filled(x, y + 2) || !filled(x + 1, y + 1);
        if (nearTL && !nearBR) lv = 4;
        if (nearBR && !nearTL) lv = 2;
        col = ramp[lv];
      }
    } else if (c === 'w') col = edge ? pal.R[0] : pal.w;
    else if (c === 'K') col = pal.K;
    else if (c === 'E' || c === 'e' || c === 'S') col = edge ? pal.K : pal[c];
    else throw new Error('bad char ' + c);
    px.set([...hex(col), 255], (y * 16 + x) * 4);
  }
  return px;
}

// ---------------------------------------------------------------- designs
// Letters: R red (auto-shaded), H red highlight, r red shade, d red dark; B blue, L blue light,
// b blue shade, n blue dark; w web line; K black; E lens, e lens shade, S lens glint; '.' empty.
// Any filled pixel touching empty space becomes its material's outline colour automatically.
const HELMET = {
  // Homecoming-style: thick black lens frames, sparse dark webbing, bright crimson
  orig: poke(sym([
    '........',
    '....RRRR',
    '..RRRRRw',
    '.RRRwRRw',
    '.RRRRwRw',
    '.RwRRRww',
    '.RKKRRRw',
    '.KKEKKRw',
    '.KEEEEKR',
    '.KEEEeKR',
    '.RKKeeKR',
    '.RwRKKRw',
    '..RwRRRw',
    '...RRwRw',
    '.....RRR',
    '........',
  ], 'odd'), [[3, 2, 'H'], [4, 2, 'H'], [2, 3, 'H'], [2, 4, 'H'], [3, 8, 'S'], [11, 8, 'S'],
    [10, 12, 'r'], [9, 13, 'r'], [11, 11, 'r']]),
  // classic: full black web (spokes + rings), thin lens outline, deeper red
  v2: poke(sym([
    '........',
    '....RRRw',
    '..RRRwRw',
    '.RwRRwww',
    '.RRwwRRw',
    '.wwRRwRw',
    '.RKKRwRw',
    '.RKEKKRw',
    '.KEEEEKw',
    '.KEEEeKR',
    '.wKEeeKw',
    '.RwKKKwR',
    '..wRRwRw',
    '...wRRww',
    '.....RRw',
    '........',
  ], 'odd'), [[3, 2, 'H'], [4, 2, 'H'], [2, 3, 'H'], [2, 4, 'H'], [3, 8, 'S'], [11, 8, 'S'],
    [10, 12, 'r'], [9, 12, 'r'], [12, 11, 'r']]),
};

const CHEST = {
  // follows spider_man.png front torso: small black emblem, black trim lines, teal flanks
  orig: poke(sym([
    '........',
    '..RRRR..',
    '.RRRRRR.',
    'RRRRRRRR',
    'RRRoRRRR',
    'RRRoRKRK',
    'RRRoRRKK',
    'BBBoRKRK',
    'BBBoBKRR',
    'BBBoBKRR',
    '....BKRR',
    '....RRRR',
    '....RRRR',
    '....BBBB',
    '........',
    '........',
  ], 'even'), [[2, 2, 'H'], [3, 2, 'H'], [1, 4, 'H'], [1, 5, 'H'], [4, 4, 'H'], [1, 7, 'L'], [4, 8, 'L'], [4, 9, 'L'],
    [14, 6, 'r'], [14, 8, 'b'], [11, 9, 'b'], [11, 10, 'b'], [10, 11, 'r'], [9, 12, 'r'], [10, 12, 'r'], [6, 11, 'H']]),
  // follows spider_man_v2.png: webbed red top, big black spider whose legs run down, navy flanks
  v2: poke(sym([
    '........',
    '..RRRR..',
    '.RRRwRR.',
    'RRRwRKRR',
    'RRwoRRKK',
    'RwRoRRRK',
    'BBBoBRKK',
    'BBBoBKRK',
    'BBBoBBRK',
    'BBBoBBRK',
    '....BBRR',
    '....RRwR',
    '....wwww',
    '....BBBB',
    '........',
    '........',
  ], 'even'), [[2, 2, 'H'], [1, 4, 'H'], [4, 4, 'H'], [1, 7, 'L'], [4, 7, 'L'], [4, 8, 'L'],
    [14, 5, 'r'], [14, 8, 'b'], [11, 9, 'b'], [10, 11, 'r']]),
};

const LEGS = {
  orig: poke(sym([
    '........',
    '..RRRRRR',
    '..RRRRRR',
    '..BBBBBB',
    '..BBBBBB',
    '..BBBB..',
    '..BBBB..',
    '..BBBB..',
    '..BBBB..',
    '..BBBB..',
    '..KKKK..',
    '..RRRR..',
    '..RRRR..',
    '..RRRR..',
    '........',
    '........',
  ], 'even'), [[3, 2, 'H'], [4, 2, 'H'], [3, 4, 'L'], [3, 5, 'L'], [3, 6, 'L'], [10, 4, 'L'], [10, 5, 'L'],
    [3, 11, 'H'], [10, 11, 'H'], [12, 4, 'b'], [12, 7, 'b'], [5, 8, 'b'], [12, 9, 'b'],
    [12, 12, 'r'], [5, 12, 'r']]),
  v2: poke(sym([
    '........',
    '..RRRRRR',
    '..RvRRvR',
    '..BBBBBB',
    '..BBBBBB',
    '..BBBB..',
    '..BBBB..',
    '..BBBB..',
    '..BBBB..',
    '..BBBB..',
    '..RRvR..',
    '..vvvv..',
    '..RRvR..',
    '..RRvR..',
    '........',
    '........',
  ], 'even'), [[3, 4, 'L'], [3, 5, 'L'], [10, 4, 'L'], [10, 5, 'L'],
    [12, 4, 'b'], [12, 7, 'b'], [5, 8, 'b'], [12, 9, 'b'], [12, 12, 'r']]),
};

const BOOTS = {
  orig: poke(sym([
    '........',
    '........',
    '........',
    '........',
    '........',
    '.BBBBB..',
    '.BBBBB..',
    '.RRRRR..',
    '.RRRRR..',
    '.RRRRR..',
    '.RRRRR..',
    'RRRRRR..',
    'RRRRRR..',
    'dddddd..',
    '........',
    '........',
  ], 'even'), [[2, 6, 'L'], [11, 6, 'L'], [2, 8, 'H'], [11, 8, 'H'], [2, 9, 'H'], [1, 11, 'H'], [11, 9, 'H'],
    [4, 12, 'r'], [14, 12, 'r'], [4, 10, 'r'], [13, 10, 'r'], [3, 6, 'B'], [4, 6, 'b'], [13, 6, 'b']]),
  v2: poke(sym([
    '........',
    '........',
    '........',
    '........',
    '........',
    '.RRvRR..',
    '.RRvRR..',
    '.vvvvv..',
    '.RRvRR..',
    '.RRvRR..',
    '.vvvvv..',
    'RRRvRR..',
    'RRRvRR..',
    'dddddd..',
    '........',
    '........',
  ], 'even'), [[2, 6, 'H'], [11, 6, 'H'], [1, 11, 'H'], [14, 11, 'H'], [4, 12, 'r'], [14, 12, 'r']]),
};

const ITEMS = [
  ['spider_man_suit_helmet', HELMET.orig, 'orig'], ['spider_man_suit_chestplate', CHEST.orig, 'orig'],
  ['spider_man_suit_leggings', LEGS.orig, 'orig'], ['spider_man_suit_boots', BOOTS.orig, 'orig'],
  ['spider_man_suit_v2_helmet', HELMET.v2, 'v2'], ['spider_man_suit_v2_chestplate', CHEST.v2, 'v2'],
  ['spider_man_suit_v2_leggings', LEGS.v2, 'v2'], ['spider_man_suit_v2_boots', BOOTS.v2, 'v2'],
];

const imgs = [];
for (const [name, grid, suit] of ITEMS) {
  if (grid.length !== 16) throw new Error(name + ' rows ' + grid.length);
  const px = render(grid, SUITS[suit]);
  imgs.push(px);
  P.write(path.join(ASSETS, 'textures/item', name + '.png'), 16, 16, px);
}

// preview: 4 columns x 2 rows at 8x (or argv[4]), on a slot-grey background with a gap
const S = +(process.argv[4] || 8), CELL = 16 * S + 16, W = CELL * 4 + 16, H = CELL * 2 + 16;
const out = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) out.set([139, 139, 139, 255], i * 4);
imgs.forEach((px, k) => {
  const ox = 16 + (k % 4) * CELL, oy = 16 + Math.floor(k / 4) * CELL;
  for (let y = 0; y < 16 * S; y++) for (let x = 0; x < 16 * S; x++) {
    const i = ((y / S | 0) * 16 + (x / S | 0)) * 4;
    const o = ((oy + y) * W + ox + x) * 4;
    if (px[i + 3]) out.set([px[i], px[i + 1], px[i + 2], 255], o);
  }
  // 1x actual-size copy in the corner
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const i = (y * 16 + x) * 4; const o = ((oy + 16 * S - 16 + y) * W + ox + 16 * S - 16 + x) * 4;
    if (px[i + 3]) out.set([px[i], px[i + 1], px[i + 2], 255], o);
  }
});
P.write(PREVIEW, W, H, out);
console.log('ok');
