// v0.16.1 Galactus: installs the user's Silver Surfer skin (Planet Minecraft skin 6740557, "Silver Surfer") as
// textures/entity/galactus/silver_surfer.png, reports whether it is a slim (Alex) or wide (Steve) arm skin, and writes a
// small full-bright glowmask (silver_surfer_glowmask.png) for the eyes: the brightest near-white pixels of the face.
// Run from the worktree root: node scratchpad/gen_galactus_surfer_skin.js [path-to-skin]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.cwd();
const src = process.argv[2] || path.join(root, '../../../scratchpad/galactus_refs/silver_surfer_pmc_6740557.png');
const out = path.join(root, 'src/main/resources/assets/projecthero/textures/entity/galactus');

function decode(buf) {
  let p = 8, w, h, bitDepth, colorType, idat = [], palette = null, trns = null;
  while (p < buf.length) {
    const len = buf.readUInt32BE(p), type = buf.toString('ascii', p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + len);
    if (type === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); bitDepth = d[8]; colorType = d[9]; }
    else if (type === 'PLTE') palette = d;
    else if (type === 'tRNS') trns = d;
    else if (type === 'IDAT') idat.push(d);
    p += 12 + len;
  }
  if (bitDepth !== 8) throw new Error('bit depth ' + bitDepth + ' unsupported');
  const ch = { 6: 4, 2: 3, 3: 1, 0: 1, 4: 2 }[colorType];
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * ch, px = Buffer.alloc(w * h * 4), cur = Buffer.alloc(stride), prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const a = x >= ch ? cur[x - ch] : 0, b = prev[x], c = x >= ch ? prev[x - ch] : 0, v = raw[y * (stride + 1) + 1 + x];
      let r;
      if (f === 0) r = v; else if (f === 1) r = v + a; else if (f === 2) r = v + b; else if (f === 3) r = v + ((a + b) >> 1);
      else { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); r = v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c); }
      cur[x] = r & 255;
    }
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      if (colorType === 6) { px[i] = cur[x * 4]; px[i + 1] = cur[x * 4 + 1]; px[i + 2] = cur[x * 4 + 2]; px[i + 3] = cur[x * 4 + 3]; }
      else if (colorType === 2) { px[i] = cur[x * 3]; px[i + 1] = cur[x * 3 + 1]; px[i + 2] = cur[x * 3 + 2]; px[i + 3] = 255; }
      else if (colorType === 3) { const k = cur[x]; px[i] = palette[k * 3]; px[i + 1] = palette[k * 3 + 1]; px[i + 2] = palette[k * 3 + 2]; px[i + 3] = trns && k < trns.length ? trns[k] : 255; }
      else if (colorType === 4) { px[i] = px[i + 1] = px[i + 2] = cur[x * 2]; px[i + 3] = cur[x * 2 + 1]; }
      else { px[i] = px[i + 1] = px[i + 2] = cur[x]; px[i + 3] = 255; }
    }
    cur.copy(prev);
  }
  return { w, h, px };
}

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const skin = decode(fs.readFileSync(src));
if (skin.w !== 64 || skin.h !== 64) throw new Error('expected a 64x64 skin, got ' + skin.w + 'x' + skin.h);
const A = (x, y) => skin.px[(y * 64 + x) * 4 + 3];
// slim skins leave the last 2 columns of the right arm's front/back + the arm-top strip transparent (x 54..55, y 20..31)
let opaque = 0;
for (let y = 20; y < 32; y++) for (const x of [54, 55]) if (A(x, y) > 0) opaque++;
const slim = opaque === 0;
console.log('arms: ' + (slim ? 'SLIM (Alex)' : 'WIDE (Steve)') + ' (' + opaque + ' opaque px in the wide-only columns)');
fs.mkdirSync(out, { recursive: true });
fs.copyFileSync(src, path.join(out, 'silver_surfer.png'));
// eyes: the face front is x 8..15, y 8..15; glow the near-white pixels in the eye band (y 11..13)
const glow = Buffer.alloc(64 * 64 * 4);
let lit = 0;
for (let y = 10; y <= 13; y++) for (let x = 8; x <= 15; x++) {
  const i = (y * 64 + x) * 4, r = skin.px[i], g = skin.px[i + 1], b = skin.px[i + 2];
  if (skin.px[i + 3] > 0 && r > 225 && g > 225 && b > 225) { glow[i] = r; glow[i + 1] = g; glow[i + 2] = b; glow[i + 3] = 255; lit++; }
}
fs.writeFileSync(path.join(out, 'silver_surfer_glowmask.png'), png(64, 64, glow));
console.log('glowmask: ' + lit + ' eye pixels');
