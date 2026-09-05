// "changes 19": rebuild mark_vii.png cleanly -- ONLY the red is dimmed; every other colour
// (gold, silver, blue) is exactly as it was. Reconstructs from the untouched mark_iii.png +
// mark_6.png (for the silver forearm plates) rather than trying to un-dim the "changes 18" file.
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');
const ARMOR = 'src/main/resources/assets/herocraft/textures/armor';

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  return t;
})();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
};
function decode(file) {
  const b = fs.readFileSync(file);
  const w = b.readUInt32BE(16), h = b.readUInt32BE(20);
  if (b[24] !== 8 || b[25] !== 6) throw new Error(`${file}: not 8-bit RGBA`);
  let off = 8, idat = [];
  while (off < b.length) {
    const len = b.readUInt32BE(off), type = b.toString('ascii', off + 4, off + 8);
    if (type === 'IDAT') idat.push(b.subarray(off + 8, off + 8 + len));
    off += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = w * 4, out = Buffer.alloc(w * h * 4);
  let prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const ft = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    const cur = Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) {
      const a = i >= 4 ? cur[i - 4] : 0, bb = prev[i], c = i >= 4 ? prev[i - 4] : 0;
      let v = line[i];
      if (ft === 1) v = (v + a) & 0xff;
      else if (ft === 2) v = (v + bb) & 0xff;
      else if (ft === 3) v = (v + ((a + bb) >> 1)) & 0xff;
      else if (ft === 4) { const p = a + bb - c, pa = Math.abs(p - a), pb = Math.abs(p - bb), pc = Math.abs(p - c); v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? bb : c)) & 0xff; }
      cur[i] = v;
    }
    cur.copy(out, y * stride); prev = cur;
  }
  return { w, h, data: out };
}
function encode(w, h, data) {
  const stride = w * 4, raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

const base = decode(path.join(ARMOR, 'mark_iii.png'));   // untouched crimson design
const m6 = decode(path.join(ARMOR, 'mark_6.png'));        // = mark_iii + silver forearms (+ knee/reactor)
const { w, h } = base;
const out = Buffer.from(base.data);

let silverBlit = 0, dimmed = 0;
for (let i = 0; i < out.length; i += 4) {
  // 1. take mark_6's pixel only where it differs from mark_iii AND is a silver/grey plate
  //    (this is the "silver forearms" from "changes 17"; skips mark_6's coloured knee line / reactor)
  const dr = Math.abs(m6.data[i] - base.data[i]) + Math.abs(m6.data[i + 1] - base.data[i + 1]) + Math.abs(m6.data[i + 2] - base.data[i + 2]);
  const sr = m6.data[i], sg = m6.data[i + 1], sb = m6.data[i + 2], sa = m6.data[i + 3];
  if (dr > 18 && sa > 0 && Math.abs(sr - sg) < 22 && Math.abs(sg - sb) < 22 && sr > 110) {
    out[i] = sr; out[i + 1] = sg; out[i + 2] = sb; out[i + 3] = sa;
    silverBlit++;
  }
  // 2. dim ONLY true red (red clearly dominant, both other channels low). Gold (high r+g),
  //    silver (r~g~b) and blue are left exactly as they were.
  const r = out[i], g = out[i + 1], b = out[i + 2], a = out[i + 3];
  if (a > 0 && r >= 90 && g < r * 0.55 && b < r * 0.55) {
    out[i] = Math.round(r * 0.55);
    dimmed++;
  }
}
fs.writeFileSync(path.join(ARMOR, 'mark_vii.png'), encode(w, h, out));
console.log(`mark_vii.png rebuilt: ${silverBlit} silver px from mark_6, ${dimmed} red px dimmed`);
