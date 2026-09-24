// v0.11.15: Symbiote Vial item sprites (empty + filled), hand-rolled PNG via node zlib.
const fs = require('fs');
const zlib = require('zlib');
const ITEM = 'src/main/resources/assets/projecthero/textures/item';

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (const x of b) c = CRC[(c ^ x) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

function vial(filled) {
  const px = Buffer.alloc(16 * 16 * 4);
  const set = (x, y, r, g, b, a = 255) => { const i = (y * 16 + x) * 4; px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a; };
  // iron cap (rows 1-3)
  for (let x = 5; x <= 10; x++) { set(x, 1, 200, 200, 205); set(x, 2, 165, 165, 172); }
  for (let x = 4; x <= 11; x++) set(x, 3, 130, 130, 138);
  set(4, 3, 95, 95, 102); set(11, 3, 95, 95, 102);
  // neck (rows 4-5)
  for (let y = 4; y <= 5; y++) { set(6, y, 170, 210, 225, 200); set(9, y, 170, 210, 225, 200); }
  // body (rows 6-14): round flask outline
  const half = [2, 3, 4, 4, 4, 4, 4, 3, 2]; // half-width per row
  for (let i = 0; i < half.length; i++) {
    const y = 6 + i, hw = half[i];
    for (let x = 8 - hw - 0; x < 8 + hw; x++) {
      const edge = x === 8 - hw || x === 8 + hw - 1 || y === 14;
      if (edge) set(x, y, 190, 225, 240, 230);
      else if (filled) {
        const t = ((x * 7 + y * 13) % 5);
        const sheen = (x === 8 - hw + 1 && y > 7 && y < 12);
        if (sheen) set(x, y, 96, 70, 140);
        else set(x, y, 14 + t, 10 + t, 22 + t * 2);
      } else set(x, y, 215, 240, 250, 70);
    }
  }
  if (filled) { // liquid surface line + a purple glint
    for (let x = 5; x <= 10; x++) set(x, 8, 60, 30, 90);
    set(10, 10, 130, 90, 190);
  } else { // glass glint
    set(5, 9, 255, 255, 255, 200); set(5, 10, 255, 255, 255, 150);
  }
  return png(16, 16, px);
}
fs.writeFileSync(`${ITEM}/symbiote_vial.png`, vial(false));
fs.writeFileSync(`${ITEM}/symbiote_vial_filled.png`, vial(true));
console.log('vials written');
