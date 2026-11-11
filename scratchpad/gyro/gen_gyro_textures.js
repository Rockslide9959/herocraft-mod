// v0.17.3 Gyro Zeppeli (POWER): generates the power's textures into
//   src/main/resources/assets/projecthero/textures/entity/gyro/power/
//   gyro_wide.png / gyro_slim.png  -- Gyro's 64x64 player skin (hat, blond hair, green eyes, "GO!GO!" gold teeth,
//                                     teal coat over a dark shirt, belt with two steel-ball pouches, boots)
//   gyro_hat.png                   -- 32x32 texture for the hat's brim + crown geometry (GyroLayer)
//   ball_breaker.png               -- 64x64 skin of the pale, armour-plated Ball Breaker figure
//   spin_swirl.png                 -- 64x64 tileable golden spiral stripes (the twisting skin of a spun creature)
//   aged_cracks.png                -- 64x64 grey wash with dark cracks (Ball Breaker's aging)
// Usage: node scratchpad/gyro/gen_gyro_textures.js
const path = require('path');
const png = require('../pnglib.js');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/gyro/power');
require('fs').mkdirSync(OUT, { recursive: true });

// ---------------------------------------------------------------- helpers
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function hex(h, a = 255) { return [(h >> 16) & 255, (h >> 8) & 255, h & 255, a]; }
function set(im, x, y, c) {
  if (x < 0 || y < 0 || x >= im.w || y >= im.h || !c) return;
  const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3] === undefined ? 255 : c[3];
}
function shade(c, f) { return [Math.max(0, Math.min(255, Math.round(c[0] * f))), Math.max(0, Math.min(255, Math.round(c[1] * f))), Math.max(0, Math.min(255, Math.round(c[2] * f))), c[3]]; }
function mix(a, b, t) { return [0, 1, 2].map(i => Math.round(a[i] + (b[i] - a[i]) * t)).concat([a[3]]); }
// deterministic noise
function rnd(x, y, s = 1) { let n = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967295; }
function grain(c, x, y, s, amt = 0.08) { return shade(c, 1 + (rnd(x, y, s) - 0.5) * 2 * amt); }

// a cuboid's six faces in the 64x64 player layout: box(u, v, w, h, d)
function box(u, v, w, h, d) {
  return {
    top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
    right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h],
  };
}
// paint a face: fn(x, y, w, h) -> colour (local coords, y down)
function face(im, f, fn) { const [u, v, w, h] = f; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) set(im, u + x, v + y, fn(x, y, w, h)); }
function allFaces(im, b, fn) { for (const k of Object.keys(b)) face(im, b[k], (x, y, w, h) => fn(k, x, y, w, h)); }

// ---------------------------------------------------------------- palette
const SKIN = hex(0xE8B48E), SKIN_D = hex(0xC98E6A), SKIN_L = hex(0xF4CBA8);
const HAIR = hex(0xE6C25A), HAIR_D = hex(0xB88E2E), HAIR_L = hex(0xF7E08E);
const EYE = hex(0x2E8A4C), EYE_W = hex(0xF2F2EA), BROW = hex(0x9C7426);
const GOLD = hex(0xF2C230), GOLD_D = hex(0xA87A12), MOUTH = hex(0x5A1E1A);
const HAT = hex(0x2C5450), HAT_D = hex(0x1C3836), HAT_L = hex(0x3F726C), BAND = hex(0xD9B23A);
const COAT = hex(0x3C6F66), COAT_D = hex(0x284C46), COAT_L = hex(0x5A9086);
const SHIRT = hex(0x24262E), SHIRT_L = hex(0x3A3D48);
const SCARF = hex(0xE9E2C8), SCARF_D = hex(0xB9B096);
const BELT = hex(0x6B4426), BELT_D = hex(0x45291A), BUCKLE = hex(0xD8C070);
const POUCH = hex(0x7A5230), BALL = hex(0x5BAE5E), BALL_D = hex(0x2E6A38), BALL_L = hex(0xA8E2A0);
const PANTS = hex(0x3A3F3C), PANTS_D = hex(0x262A28);
const BOOT = hex(0x5A3A22), BOOT_D = hex(0x3A2414), BOOT_L = hex(0x7A5234);

// ---------------------------------------------------------------- Gyro's skin
function gyroSkin(slim) {
  const im = img(64, 64);
  const aw = slim ? 3 : 4;
  // ---- head
  const head = box(0, 0, 8, 8, 8);
  allFaces(im, head, (k, x, y, w, h) => {
    if (k === 'top') return grain(y < 2 ? HAIR_D : HAIR, x, y, 1, 0.12);
    if (k === 'bottom') return grain(SKIN_D, x, y, 2);
    if (k === 'back') return grain(y < 7 ? (x % 3 === 1 ? HAIR_L : HAIR) : HAIR_D, x, y, 3, 0.1);
    if (k === 'right' || k === 'left') {
      // hair down the side, an ear, sideburn
      const earX = k === 'right' ? 4 : 3;
      if (y < 3) return grain(HAIR, x, y, 4, 0.1);
      if (y >= 3 && y <= 5 && x === earX) return SKIN_D;
      const front = k === 'right' ? x >= 6 : x <= 1;
      if (front && y < 7) return grain(SKIN, x, y, 5, 0.05);
      return grain(x % 2 ? HAIR : HAIR_D, x, y, 6, 0.08);
    }
    // front: fringe, brows, green eyes, nose, the GO!GO! grin
    if (y < 2) return grain(y === 0 || x === 0 || x === 7 ? HAIR_D : HAIR, x, y, 7, 0.1);
    if (y === 2) return (x === 0 || x === 7) ? HAIR : (x === 1 || x === 2 || x === 5 || x === 6) ? BROW : SKIN;
    if (y === 3) {
      if (x === 0 || x === 7) return HAIR_D;
      if (x === 1 || x === 5) return EYE_W;
      if (x === 2 || x === 6) return EYE;
      return SKIN;
    }
    if (y === 4) return (x === 0 || x === 7) ? HAIR_D : (x === 3 || x === 4) ? SKIN_D : SKIN;
    if (y === 5) return (x === 0 || x === 7) ? SKIN_D : (x >= 2 && x <= 5) ? MOUTH : SKIN;
    if (y === 6) {
      // the gold grill: "GO GO" read as gold teeth with dark letter notches
      if (x === 0 || x === 7) return SKIN_D;
      if (x >= 1 && x <= 6) return (x === 2 || x === 5) ? GOLD_D : GOLD;
      return SKIN;
    }
    return (x === 0 || x === 7) ? SKIN_D : (x >= 2 && x <= 5) ? SKIN_D : SKIN; // chin
  });
  // ---- hat layer: the crown of his hat (the brim is geometry), long hair at the back and sides
  const hat = box(32, 0, 8, 8, 8);
  allFaces(im, hat, (k, x, y, w, h) => {
    if (k === 'top') {
      const edge = x === 0 || y === 0 || x === 7 || y === 7;
      return grain(edge ? HAT_D : ((x + y) % 5 === 0 ? HAT_L : HAT), x, y, 11, 0.07);
    }
    if (k === 'bottom') return null;
    if (y <= 1) return grain(y === 0 ? HAT_D : HAT, x, y, 12, 0.06); // crown
    if (y === 2) {
      // the band with its gold studs (the "GO!GO!" letters on the front)
      if (k === 'front') return (x % 2 === 0) ? BAND : shade(BAND, 0.7);
      return x % 3 === 0 ? BAND : shade(BAND, 0.75);
    }
    if (k === 'back') return y >= 3 ? grain(x % 2 ? HAIR : HAIR_L, x, y, 13, 0.1) : null; // long hair
    if (k === 'right' || k === 'left') {
      const back = k === 'right' ? x <= 3 : x >= 4;
      return back && y >= 3 ? grain(HAIR, x, y, 14, 0.12) : null;
    }
    return null;
  });
  // ---- body: teal coat open over a dark shirt, a cream scarf, the belt with two ball pouches
  const body = box(16, 16, 8, 12, 4);
  allFaces(im, body, (k, x, y, w, h) => {
    if (k === 'top') return grain(SCARF, x, y, 21, 0.06);
    if (k === 'bottom') return PANTS_D;
    if (k === 'front') {
      if (y <= 1) return grain(y === 1 && (x === 0 || x === 7) ? SCARF_D : SCARF, x, y, 22, 0.05); // scarf
      if (y >= 8 && y <= 9) { // belt
        if (x === 3 || x === 4) return y === 8 ? BUCKLE : shade(BUCKLE, 0.75);
        return grain(y === 8 ? BELT : BELT_D, x, y, 23, 0.08);
      }
      if (y > 9) return grain(x === 0 || x === 7 ? COAT_D : (x === 3 || x === 4 ? PANTS_D : PANTS), x, y, 24, 0.06);
      // the open coat: panels at the sides, the shirt between
      if (x <= 1 || x >= 6) return grain(x === 1 || x === 6 ? COAT_L : COAT, x, y, 25, 0.06);
      return grain(y === 2 ? SHIRT_L : SHIRT, x, y, 26, 0.08);
    }
    if (k === 'back') {
      if (y === 0) return SCARF_D;
      if (y >= 8 && y <= 9) return grain(y === 8 ? BELT : BELT_D, x, y, 27, 0.08);
      if (y > 9) return grain(COAT_D, x, y, 28, 0.06);
      return grain(x === 3 || x === 4 ? COAT_D : COAT, x, y, 29, 0.06); // centre seam
    }
    // sides: coat, belt, ball pouch
    if (y >= 8 && y <= 9) return grain(BELT, x, y, 30, 0.08);
    return grain(y > 9 ? COAT_D : COAT, x, y, 31, 0.06);
  });
  // jacket layer: coat lapels and tails, the pouches proud of the belt
  const jacket = box(16, 32, 8, 12, 4);
  allFaces(im, jacket, (k, x, y, w, h) => {
    if (k === 'top' || k === 'bottom') return null;
    if (k === 'front') {
      if ((x === 0 || x === 7) && y >= 2 && y <= 11) return grain(x === 0 ? COAT_L : COAT, x, y, 41, 0.06); // lapel edges
      if ((x === 1 || x === 6) && y >= 8 && y <= 10) { // the two pouches with a ball each
        return y === 9 ? BALL : (y === 8 ? POUCH : shade(POUCH, 0.7));
      }
      if (y >= 10 && (x <= 1 || x >= 6)) return grain(COAT_D, x, y, 42, 0.06); // coat tails
      return null;
    }
    if (k === 'back') return y >= 10 ? grain(COAT_D, x, y, 43, 0.06) : null; // tails
    if (y >= 8 && y <= 10 && x >= 1 && x <= 2) return y === 9 ? BALL : POUCH; // side pouches
    return null;
  });
  // ---- arms: coat sleeves with a cuff, bare hands
  function arm(u, v) {
    const b = box(u, v, aw, 12, 4);
    allFaces(im, b, (k, x, y, w, h) => {
      if (k === 'top') return grain(COAT, x, y, 51 + u, 0.06);
      if (k === 'bottom') return SKIN_D;
      if (y >= 10) return grain(y === 11 ? SKIN_D : SKIN, x, y, 52 + u, 0.05); // hand
      if (y === 9) return grain(COAT_D, x, y, 53 + u, 0.05); // cuff
      if (y === 0 && k !== 'back') return COAT_L;
      return grain(x === 0 ? COAT_D : COAT, x, y, 54 + u, 0.06);
    });
  }
  arm(40, 16); // right
  arm(32, 48); // left
  // sleeve overlays: a turned-back gold-trimmed cuff
  function sleeve(u, v) {
    const b = box(u, v, aw, 12, 4);
    allFaces(im, b, (k, x, y) => {
      if (k === 'top' || k === 'bottom') return null;
      if (y === 8) return x % 2 === 0 ? BAND : shade(BAND, 0.8);
      if (y === 9) return grain(COAT_L, x, y, 61 + u, 0.05);
      return null;
    });
  }
  sleeve(40, 32);
  sleeve(48, 48);
  // ---- legs: dark trousers, tall riding boots
  function leg(u, v) {
    const b = box(u, v, 4, 12, 4);
    allFaces(im, b, (k, x, y, w, h) => {
      if (k === 'top') return PANTS;
      if (k === 'bottom') return BOOT_D;
      if (y >= 6) return grain(y === 6 ? BOOT_L : (y === 11 ? BOOT_D : BOOT), x, y, 71 + u, 0.08); // boots
      return grain(x === 0 || x === 3 ? PANTS_D : PANTS, x, y, 72 + u, 0.07);
    });
  }
  leg(0, 16);
  leg(16, 48);
  // trouser overlays: boot tops folded down
  function pants(u, v) {
    const b = box(u, v, 4, 12, 4);
    allFaces(im, b, (k, x, y) => {
      if (k === 'top' || k === 'bottom') return null;
      if (y === 6) return BOOT_L;
      if (y === 5 && k === 'front' && (x === 1 || x === 2)) return BUCKLE; // a spur strap glint
      return null;
    });
  }
  pants(0, 32);
  pants(0, 48);
  return im;
}

// ---------------------------------------------------------------- the hat (brim + crown) texture, 32x32
// GyroLayer's model: crown box 9x3x9 at texOffs(0,0); brim box 12x1x12 at texOffs(0,12) (flat, uv 0..)
function hatTex() {
  const im = img(64, 32);
  const crown = box(0, 0, 9, 3, 9);
  allFaces(im, crown, (k, x, y, w, h) => {
    if (k === 'top') return grain((x + y) % 4 === 0 ? HAT_L : HAT, x, y, 81, 0.07);
    if (k === 'bottom') return HAT_D;
    if (y === 2) return (x % 2 === 0) ? BAND : shade(BAND, 0.7);
    return grain(y === 0 ? HAT_L : HAT, x, y, 82, 0.06);
  });
  const brim = box(0, 12, 12, 1, 12);
  allFaces(im, brim, (k, x, y, w, h) => {
    if (k === 'top') {
      const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
      return grain(edge ? HAT_D : HAT, x, y, 83, 0.07);
    }
    if (k === 'bottom') return grain(HAT_D, x, y, 84, 0.07);
    return HAT_D;
  });
  return im;
}

// ---------------------------------------------------------------- Ball Breaker
function breakerSkin() {
  const im = img(64, 64);
  const PLATE = hex(0xEDE7D6), PLATE_D = hex(0xBDB6A2), SEAM = hex(0x7E786A), GLOW = hex(0xFFD24A), DARK = hex(0x3A3630);
  function plated(k, x, y, w, h, s) {
    const seam = (y % 4 === 0) || (k === 'front' && x === Math.floor(w / 2));
    const base = seam ? SEAM : (y % 4 === 1 ? PLATE : PLATE_D);
    return grain(base, x, y, s, 0.05);
  }
  allFaces(im, box(0, 0, 8, 8, 8), (k, x, y, w, h) => {
    if (k === 'front') {
      if (y === 3 && x >= 1 && x <= 6) return x === 2 || x === 5 ? GLOW : DARK; // the visor slit with two burning eyes
      if (y >= 5 && y <= 6 && x >= 2 && x <= 5) return (x + y) % 2 ? SEAM : PLATE_D; // grille
      return grain(y < 2 ? PLATE : PLATE_D, x, y, 91, 0.05);
    }
    if (k === 'top') return grain((x === 3 || x === 4) ? PLATE : PLATE_D, x, y, 92, 0.05); // a crest
    return plated(k, x, y, w, h, 93);
  });
  allFaces(im, box(32, 0, 8, 8, 8), (k, x, y) => (k === 'top' && (x === 3 || x === 4)) ? PLATE : null); // crest proud
  allFaces(im, box(16, 16, 8, 12, 4), (k, x, y, w, h) => {
    if (k === 'front' && y >= 3 && y <= 5 && x >= 3 && x <= 4) return GLOW; // the core
    return plated(k, x, y, w, h, 94);
  });
  for (const [u, v] of [[40, 16], [32, 48]]) allFaces(im, box(u, v, 4, 12, 4), (k, x, y, w, h) => plated(k, x, y, w, h, 95 + u));
  for (const [u, v] of [[0, 16], [16, 48]]) allFaces(im, box(u, v, 4, 12, 4), (k, x, y, w, h) => plated(k, x, y, w, h, 97 + u));
  // shoulder pauldrons on the sleeve layers
  for (const [u, v] of [[40, 32], [48, 48]]) allFaces(im, box(u, v, 4, 12, 4), (k, x, y) => y <= 2 && k !== 'bottom' ? (y === 2 ? SEAM : PLATE) : null);
  return im;
}

// ---------------------------------------------------------------- the spin swirl (tileable diagonal gold stripes)
function swirlTex() {
  const im = img(64, 64);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const d = (x + y * 2) % 32; // diagonal bands, wraps cleanly at 64
    let a = 0;
    if (d < 3) a = 220; else if (d < 6) a = 120; else if (d >= 16 && d < 18) a = 90;
    if (a) set(im, x, y, [255, 214, 90, a]);
  }
  return im;
}

// ---------------------------------------------------------------- aged cracks
function cracksTex() {
  const im = img(64, 64);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) set(im, x, y, [150, 146, 140, 150]); // grey wash
  // random-walk cracks
  let seed = 7;
  const r = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
  for (let c = 0; c < 26; c++) {
    let x = Math.floor(r() * 64), y = Math.floor(r() * 64);
    const len = 6 + Math.floor(r() * 14);
    let dx = r() < 0.5 ? 1 : -1, dy = r() < 0.5 ? 1 : -1;
    for (let i = 0; i < len; i++) {
      set(im, (x + 64) % 64, (y + 64) % 64, [48, 44, 40, 235]);
      if (r() < 0.5) x += dx; else y += dy;
      if (r() < 0.15) dx = -dx;
      if (r() < 0.15) dy = -dy;
    }
  }
  return im;
}

function save(name, im) { png.write(path.join(OUT, name), im.w, im.h, im.px); console.log('wrote', name); }
save('gyro_wide.png', gyroSkin(false));
save('gyro_slim.png', gyroSkin(true));
save('gyro_hat.png', hatTex());
save('ball_breaker.png', breakerSkin());
save('spin_swirl.png', swirlTex());
save('aged_cracks.png', cracksTex());
