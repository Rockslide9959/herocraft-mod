// v0.17.3 Starfire: generates the power's textures.
//   textures/entity/starfire/starfire_suit_wide.png / _slim.png -- the costume skin (64x64 player layout, costume only, transparent
//       where the wearer's own skin shows): purple crop top with the silver collar and green gem, bare midriff, silver belt,
//       purple skirt (flared on the outer layer), purple thigh-high boots with silver knee plates, silver forearm bracers with gems.
//   textures/item/tamaranean_starstone.png, starfire_suit_chestplate/leggings/boots.png -- 16x16 icons.
// Usage: node scratchpad/starfire/gen_starfire_textures.js
const path = require('path');
const fs = require('fs');
const png = require('../pnglib.js');
const ROOT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures');
const ENT = path.join(ROOT, 'entity/starfire');
const ITEM = path.join(ROOT, 'item');
fs.mkdirSync(ENT, { recursive: true });

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function hex(h, a = 255) { return [(h >> 16) & 255, (h >> 8) & 255, h & 255, a]; }
function set(im, x, y, c) {
  if (x < 0 || y < 0 || x >= im.w || y >= im.h || !c) return;
  const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3] === undefined ? 255 : c[3];
}
function shade(c, f) { return [0, 1, 2].map(i => Math.max(0, Math.min(255, Math.round(c[i] * f)))).concat([c[3]]); }
function rnd(x, y, s = 1) { let n = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967295; }
function grain(c, x, y, s, amt = 0.06) { return shade(c, 1 + (rnd(x, y, s) - 0.5) * 2 * amt); }
// a cuboid's faces in the 64x64 layout
function box(u, v, w, h, d) {
  return { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] };
}
function face(im, f, fn) { const [u, v, w, h] = f; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(im, u + x, v + y, fn(x, y, w, h)); }
function sides(b) { return ['right', 'front', 'left', 'back']; }

const PURPLE = hex(0x8A3CC0), PURPLE_D = hex(0x5A2286), PURPLE_L = hex(0xB06AE6);
const SILVER = hex(0xCDD2DE), SILVER_D = hex(0x8B90A2), SILVER_L = hex(0xF2F4FA);
const GEM = hex(0x4CFF5A), GEM_D = hex(0x1C9A2C), GEM_L = hex(0xC8FFC8);

function purple(x, y, s) { return grain(PURPLE, x, y, s); }

function suit(slim) {
  const im = img(64, 64);
  const aw = slim ? 3 : 4;
  // ---------------- body (base): crop top rows 0-6, bare midriff 7-9, silver belt 10-11
  const body = box(16, 16, 8, 12, 4);
  for (const k of sides()) {
    face(im, body[k], (x, y, w, h) => {
      if (y === 0) return grain(SILVER, x, y, 11); // the collar ring
      if (y <= 6) {
        if (k === 'front') {
          if (y <= 2 && (x === 3 || x === 4)) return y === 1 ? GEM_L : GEM; // the gem at the collar
          if (y === 1 && (x === 2 || x === 5)) return SILVER_D;
          if (y === 6) return PURPLE_D; // the hem
          if (x === 0 || x === w - 1) return PURPLE_D;
          return y <= 2 ? purple(x, y, 12) : shade(purple(x, y, 13), y >= 3 && y <= 4 && x >= 1 && x <= 6 ? 1.12 : 1.0);
        }
        return y === 6 ? PURPLE_D : purple(x, y, 14);
      }
      if (y >= 10) return y === 10 ? grain(SILVER_L, x, y, 15) : grain(SILVER_D, x, y, 16);
      return null; // midriff: her own skin
    });
  }
  face(im, body.top, (x, y) => (y === 0 || y === 3 || x === 0 || x === 7) ? grain(SILVER, x, y, 17) : (x >= 2 && x <= 5 ? null : grain(SILVER_D, x, y, 18)));
  // jacket (outer): a raised collar at the front + the gem standing proud
  const jacket = box(16, 32, 8, 12, 4);
  face(im, jacket.front, (x, y) => y === 0 ? (x === 3 || x === 4 ? GEM : SILVER_L) : null);
  face(im, jacket.back, (x, y) => y === 0 ? SILVER : null);
  // ---------------- arms: bare upper arm, silver bracers rows 6-10 with a gem, bare hand row 11
  const arm = (u, v, right) => {
    const b = box(u, v, aw, 12, 4);
    for (const k of sides()) {
      face(im, b[k], (x, y, w) => {
        if (y === 2) return grain(SILVER_D, x, y, 20); // a thin armband
        if (y >= 6 && y <= 10) {
          if (k === 'front' && y === 8 && x >= Math.floor((w - 2) / 2) && x <= Math.floor((w - 2) / 2) + 1) return GEM;
          return y === 6 || y === 10 ? grain(SILVER_L, x, y, 21) : grain(SILVER, x, y, 22);
        }
        return null;
      });
    }
    face(im, b.bottom, () => null);
  };
  arm(40, 16, true);
  arm(32, 48, false);
  // ---------------- legs (base): skirt rows 0-2, bare thigh row 3, boot tops rows 4-6, boots 7-11 with a silver knee plate
  const leg = (u, v) => {
    const b = box(u, v, 4, 12, 4);
    for (const k of sides()) {
      face(im, b[k], (x, y) => {
        if (y <= 2) return y === 2 ? PURPLE_D : purple(x, y, 30);
        if (y === 3) return null;
        if (y === 4) return grain(PURPLE_L, x, y, 31);
        if (k === 'front' && (y === 7 || y === 8)) return y === 7 ? SILVER_L : SILVER; // the knee plate
        if (y === 11) return PURPLE_D;
        return purple(x, y, 32);
      });
    }
    face(im, b.bottom, (x, y) => grain(PURPLE_D, x, y, 33));
  };
  leg(0, 16);
  leg(16, 48);
  // pants (outer): the skirt's flare over rows 0-2
  for (const [u, v] of [[0, 32], [0, 48]]) {
    const b = box(u, v, 4, 12, 4);
    for (const k of sides()) face(im, b[k], (x, y) => y <= 2 ? (y === 2 ? PURPLE_D : grain(PURPLE, x, y, 40)) : null);
  }
  return im;
}

function save(dir, name, im) { png.write(path.join(dir, name), im.w, im.h, im.px); console.log('wrote', name); }

save(ENT, 'starfire_suit_wide.png', suit(false));
save(ENT, 'starfire_suit_slim.png', suit(true));

// ---------------- icons
function icon(draw) { const im = img(16, 16); draw(im); return im; }
// the Starstone: a green, glowing, faceted star-crystal
save(ITEM, 'tamaranean_starstone.png', icon(im => {
  const c = 7.5;
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = x - c, dy = y - c;
    const r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    const star = 4.2 + 2.6 * Math.pow(Math.abs(Math.cos(a * 2.5)), 3); // a five-pointed star
    if (r <= star) {
      const t = r / star;
      let col = t < 0.35 ? GEM_L : t < 0.7 ? GEM : GEM_D;
      if (dx + dy < -2 && t > 0.3) col = shade(col, 1.15);
      if (r > star - 0.9) col = hex(0x0E5A18);
      set(im, x, y, col);
    } else if (r < star + 1.6) {
      set(im, x, y, hex(0x7CFF6A, 70)); // the glow
    }
  }
  set(im, 6, 5, hex(0xFFFFFF)); set(im, 7, 6, hex(0xFFFFFF));
}));
save(ITEM, 'starfire_suit_chestplate.png', icon(im => {
  for (let y = 3; y <= 11; y++) for (let x = 3; x <= 12; x++) {
    if (y >= 3 && y <= 4 && x >= 6 && x <= 9) continue; // the neck
    set(im, x, y, y === 3 || y === 11 ? PURPLE_D : grain(PURPLE, x, y, 50));
  }
  for (let x = 4; x <= 11; x++) set(im, x, 4, SILVER);
  set(im, 7, 5, GEM); set(im, 8, 5, GEM_L); set(im, 7, 6, GEM_D); set(im, 8, 6, GEM);
  for (let y = 4; y <= 9; y++) { set(im, 2, y, SILVER_D); set(im, 13, y, SILVER_D); }
}));
save(ITEM, 'starfire_suit_leggings.png', icon(im => {
  for (let x = 4; x <= 11; x++) { set(im, x, 2, SILVER_L); set(im, x, 3, SILVER_D); }
  for (let y = 4; y <= 8; y++) for (let x = 3 + Math.floor((y - 4) / 2); x <= 12 - Math.floor((y - 4) / 2); x++) set(im, x, y, y === 8 ? PURPLE_D : grain(PURPLE, x, y, 51));
  for (let y = 9; y <= 13; y++) { set(im, 5, y, hex(0xE0B090)); set(im, 10, y, hex(0xE0B090)); }
}));
save(ITEM, 'starfire_suit_boots.png', icon(im => {
  for (const ox of [3, 9]) {
    for (let y = 2; y <= 13; y++) for (let x = ox; x <= ox + 3; x++) set(im, x, y, y === 2 ? PURPLE_L : grain(PURPLE, x, y, 52 + ox));
    set(im, ox + 1, 6, SILVER_L); set(im, ox + 2, 6, SILVER_L); set(im, ox + 1, 7, SILVER); set(im, ox + 2, 7, SILVER);
    for (let x = ox; x <= ox + 4; x++) set(im, x, 13, PURPLE_D);
  }
}));
