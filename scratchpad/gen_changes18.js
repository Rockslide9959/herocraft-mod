// "changes 18" textures, hand-rolled PNG (node zlib only -- no image libs on this machine).
//  1. dim the red on mark_vii.png (the movie Mark 7 red should read darker / dimmed)
//  2. a 16x16 power_suppressor.png item sprite
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const ARMOR = 'src/main/resources/assets/herocraft/textures/armor';
const ITEM = 'src/main/resources/assets/herocraft/textures/item';

// ---------- minimal PNG codec (8-bit RGBA, colour type 6) ----------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function decode(file) {
  const b = fs.readFileSync(file);
  const w = b.readUInt32BE(16), h = b.readUInt32BE(20);
  const bitDepth = b[24], colorType = b[25];
  if (bitDepth !== 8 || colorType !== 6) throw new Error(`${file}: expected 8-bit RGBA, got depth ${bitDepth} type ${colorType}`);
  let off = 8, idat = [];
  while (off < b.length) {
    const len = b.readUInt32BE(off);
    const type = b.toString('ascii', off + 4, off + 8);
    if (type === 'IDAT') idat.push(b.subarray(off + 8, off + 8 + len));
    off += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * 4;
  const out = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    const cur = Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= 4 ? cur[i - 4] : 0;
      const bb = prev[i];
      const c = i >= 4 ? prev[i - 4] : 0;
      let v = line[i];
      if (ft === 1) v = (v + a) & 0xff;
      else if (ft === 2) v = (v + bb) & 0xff;
      else if (ft === 3) v = (v + ((a + bb) >> 1)) & 0xff;
      else if (ft === 4) {
        const p = a + bb - c, pa = Math.abs(p - a), pb = Math.abs(p - bb), pc = Math.abs(p - c);
        v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? bb : c)) & 0xff;
      }
      cur[i] = v;
    }
    cur.copy(out, y * stride);
    prev = cur;
  }
  return { w, h, data: out };
}
function encode(w, h, data) {
  const stride = w * 4;
  const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (stride + 1)] = 0;
    data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ---------- 1. dim the red on mark_vii ----------
{
  const src = path.join(ARMOR, 'mark_vii.png');
  const { w, h, data } = decode(src);
  for (let i = 0; i < data.length; i += 4) {
    const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
    if (a === 0) continue;
    // "reddish" = red clearly dominant. Pull it down toward a dark, dimmed crimson.
    if (r > 70 && r > g * 1.25 && r > b * 1.25) {
      data[i] = Math.round(r * 0.60);
      data[i + 1] = Math.round(g * 0.80);
      data[i + 2] = Math.round(b * 0.80);
    }
  }
  fs.writeFileSync(src, encode(w, h, data));
  console.log('dimmed mark_vii.png red');
}

// ---------- 2. power_suppressor.png (16x16) ----------
{
  const w = 16, h = 16;
  const d = Buffer.alloc(w * h * 4);
  const put = (x, y, r, g, b, a = 255) => {
    if (x < 0 || y < 0 || x >= w || y >= h) return;
    const o = (y * w + x) * 4;
    d[o] = r; d[o + 1] = g; d[o + 2] = b; d[o + 3] = a;
  };
  const cx = 7.5, cy = 7.5;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const dist = Math.hypot(x - cx, y - cy);
      if (dist <= 6.6) {
        // dark suppressor housing with a faint ring
        let r = 34, g = 38, b = 46;
        if (dist > 5.4) { r = 58; g = 64; b = 78; }
        if (dist <= 2.4) { r = 42; g = 74; b = 96; } // dead reactor core, dimmed blue
        put(x, y, r, g, b);
      }
    }
  }
  // red "suppressed" diagonal slash
  for (let t = -7; t <= 7; t++) {
    const x = Math.round(cx + t), y = Math.round(cy + t);
    put(x, y, 150, 40, 40);
    put(x + 1, y, 120, 32, 32);
  }
  fs.writeFileSync(path.join(ITEM, 'power_suppressor.png'), encode(w, h, d));
  console.log('wrote power_suppressor.png');
}
