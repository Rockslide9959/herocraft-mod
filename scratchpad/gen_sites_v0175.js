// v0.17.5 Tier 2 Sites: the three guard skins, recoloured from the Syndicate's shipped Skindex skins so the guards keep
// the Syndicate's player-model bodies -- site_agent (from thug_c), site_soldier (from masked), site_cultist (from hood).
// Procedural PNGs with Node zlib. Run from the repo (or worktree) root:  node scratchpad/gen_sites_v0175.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const T = path.join(root, 'src/main/resources/assets/projecthero/textures/entity/syndicate');

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePng(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
function decodePng(file) {
  const b = fs.readFileSync(file);
  let o = 8, w, h, depth, type, plte = null, trns = null; const idat = [];
  while (o < b.length) {
    const len = b.readUInt32BE(o); const ty = b.toString('ascii', o + 4, o + 8); const d = b.subarray(o + 8, o + 8 + len);
    if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; if (d[12]) throw new Error('interlaced ' + file); }
    else if (ty === 'PLTE') plte = d; else if (ty === 'tRNS') trns = d; else if (ty === 'IDAT') idat.push(d);
    o += 12 + len;
  }
  if (depth !== 8) throw new Error('bit depth ' + depth + ' ' + file);
  const ch = { 6: 4, 2: 3, 3: 1, 0: 1, 4: 2 }[type];
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * ch; const out = Buffer.alloc(w * h * 4); const prev = Buffer.alloc(stride); const cur = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x]; const a = x >= ch ? cur[x - ch] : 0; const up = prev[x]; const c = x >= ch ? prev[x - ch] : 0;
      let r;
      if (f === 0) r = v; else if (f === 1) r = v + a; else if (f === 2) r = v + up; else if (f === 3) r = v + ((a + up) >> 1);
      else { const p = a + up - c; const pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c); r = v + (pa <= pb && pa <= pc ? a : pb <= pc ? up : c); }
      cur[x] = r & 255;
    }
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      if (type === 6) { cur.copy(out, i, x * 4, x * 4 + 4); }
      else if (type === 2) { out[i] = cur[x * 3]; out[i + 1] = cur[x * 3 + 1]; out[i + 2] = cur[x * 3 + 2]; out[i + 3] = 255; }
      else if (type === 3) { const p = cur[x]; out[i] = plte[p * 3]; out[i + 1] = plte[p * 3 + 1]; out[i + 2] = plte[p * 3 + 2]; out[i + 3] = trns && p < trns.length ? trns[p] : 255; }
      else if (type === 0) { out[i] = out[i + 1] = out[i + 2] = cur[x]; out[i + 3] = 255; }
      else { out[i] = out[i + 1] = out[i + 2] = cur[x * 2]; out[i + 3] = cur[x * 2 + 1]; }
    }
    cur.copy(prev);
  }
  return { w, h, px: out };
}
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const lum = (r, g, b) => 0.299 * r + 0.587 * g + 0.114 * b;
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
function load(name) {
  const img = decodePng(path.join(T, name + '.png'));
  const px = Buffer.from(img.px);
  return {
    w: img.w, h: img.h,
    get(x, y) { const i = (y * img.w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
    set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= img.w || y >= img.h) return; const i = (y * img.w + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = a; },
    each(fn) { for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) { const [r, g, b, a] = this.get(x, y); if (a === 0) continue; const o = fn(x, y, r, g, b, a); if (o) this.set(x, y, o, o[3] === undefined ? a : o[3]); } },
    save(name) { fs.writeFileSync(path.join(T, name + '.png'), encodePng(img.w, img.h, px)); console.log('wrote', name); },
  };
}
const isSkinTone = (r, g, b) => r > 120 && r > g && g > b && r - b > 30 && r - b < 130 && g > 70;
// regions of a 64x64 skin
const isHead = (x, y) => y < 16;                       // head + hat overlay
const isBodyFront = (x, y) => x >= 20 && x <= 27 && y >= 20 && y <= 31;

// ---- site_agent: a dark charcoal suit, white shirt, black tie, glasses, an earpiece
{
  const c = load('thug_c');
  c.each((x, y, r, g, b) => {
    if (isHead(x, y)) return null;
    if (isSkinTone(r, g, b)) return null; // hands
    const v = 22 + lum(r, g, b) * 0.16;
    return [v, v + 1, v + 6];
  });
  for (let y = 20; y < 32; y++) { c.set(23, y, [236, 236, 240]); c.set(24, y, [236, 236, 240]); }
  for (let y = 21; y < 29; y++) { c.set(23 + (y % 2), y, [14, 14, 18]); }
  c.set(23, 20, [20, 20, 24]); c.set(24, 20, [20, 20, 24]);
  // lapels
  for (let y = 20; y < 25; y++) { c.set(22, y, [40, 42, 50]); c.set(25, y, [40, 42, 50]); }
  // dark glasses (head front y 11..12)
  for (let x = 9; x <= 14; x++) c.set(x, 11, [10, 10, 12]);
  for (const x of [9, 10, 13, 14]) c.set(x, 12, [24, 24, 30]);
  // earpiece: a coil down the right side of the head (head right face x 0..7, y 8..15)
  c.set(4, 11, [200, 200, 205]); c.set(4, 12, [180, 180, 186]); c.set(5, 13, [160, 160, 166]); c.set(5, 14, [150, 150, 156]);
  // shoes: last two rows of each leg black
  for (let x = 0; x < 16; x++) { c.set(x, 30, [12, 12, 14]); c.set(x, 31, [8, 8, 10]); }
  for (let x = 16; x < 32; x++) { c.set(x, 62, [12, 12, 14]); c.set(x, 63, [8, 8, 10]); }
  c.save('site_agent');
}

// ---- site_soldier: olive camo fatigues, webbing, belt, boots, an olive helmet over the balaclava
{
  const c = load('masked');
  const olive = [86, 92, 54], dark = [54, 60, 34], brown = [104, 84, 52], tan = [140, 128, 90];
  c.each((x, y, r, g, b, a) => {
    if (isHead(x, y)) return null;
    if (isSkinTone(r, g, b)) return null;
    const n = hash(x >> 1, y >> 1, 7);
    const base = n < 0.25 ? dark : n < 0.42 ? brown : n < 0.5 ? tan : olive;
    const f = 0.85 + lum(r, g, b) / 255 * 0.3;
    return base.map(v => v * f);
  });
  // belt + webbing on the body front / back
  for (let x = 16; x < 40; x++) c.set(x, 26, [58, 44, 28]);
  c.set(23, 26, [170, 150, 90]); c.set(24, 26, [170, 150, 90]);
  for (let i = 0; i < 6; i++) { c.set(21 + i, 20 + i, [62, 50, 32]); c.set(26 - i, 20 + i, [62, 50, 32]); }
  // boots
  for (let x = 0; x < 16; x++) { c.set(x, 29, [30, 26, 20]); c.set(x, 30, [24, 20, 16]); c.set(x, 31, [18, 16, 12]); }
  for (let x = 16; x < 32; x++) { c.set(x, 61, [30, 26, 20]); c.set(x, 62, [24, 20, 16]); c.set(x, 63, [18, 16, 12]); }
  // helmet on the hat layer: top, and the top two rows of every side; a strap under the chin is implied by the mask
  const helm = (x, y) => { const n = hash(x, y, 3); return n < 0.3 ? [70, 76, 44] : [92, 98, 58]; };
  for (let x = 40; x < 48; x++) for (let y = 0; y < 8; y++) c.set(x, y, helm(x, y));
  for (let x = 32; x < 64; x++) { c.set(x, 8, helm(x, 8)); c.set(x, 9, helm(x, 9)); }
  for (let x = 40; x < 48; x++) c.set(x, 10, [60, 64, 38]); // brim over the eyes
  c.save('site_soldier');
}

// ---- site_cultist: a deep purple hooded robe with gold trim and a gold eye on the chest
{
  const c = load('hood');
  c.each((x, y, r, g, b) => {
    if (isSkinTone(r, g, b)) return null; // face, hands
    const l = lum(r, g, b) / 255;
    // eyes / dark shadow inside the hood stay dark; everything else is the robe
    return [40 + l * 60, 14 + l * 22, 58 + l * 70];
  });
  const gold = [214, 170, 54], goldDark = [150, 110, 30];
  // hem: bottom rows of the legs (the robe's skirt) and the cuffs
  for (let x = 0; x < 16; x++) c.set(x, 31, gold);
  for (let x = 16; x < 32; x++) c.set(x, 63, gold);
  for (let x = 40; x < 56; x++) c.set(x, 31, goldDark);
  for (let x = 32; x < 48; x++) c.set(x, 63, goldDark);
  // a gold sash down the body front and a sigil (an eye) on the chest
  for (let y = 20; y < 32; y++) c.set(23 + (y >= 26 ? 1 : 0), y, goldDark);
  for (const [x, y] of [[22, 22], [23, 21], [24, 21], [25, 22], [24, 23], [23, 23]]) c.set(x, y, gold);
  c.set(23, 22, [240, 220, 120]); c.set(24, 22, [40, 10, 50]);
  // hood rim on the hat layer front (x 40..47, y 8..9) when present
  for (let x = 40; x < 48; x++) { const [, , , a] = c.get(x, 8); if (a > 0) c.set(x, 8, goldDark); }
  c.save('site_cultist');
}
