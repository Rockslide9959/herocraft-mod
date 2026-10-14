// v0.16.2: the web line's braided-silk texture (16 wide = across the strand, 64 long = along it, tiles along v).
const zlib = require("zlib"), fs = require("fs");
const W = 16, H = 64, out = process.argv[2];
const px = Buffer.alloc((W * 4 + 1) * H);
function crc(buf) { let c, t = []; for (let n = 0; n < 256; n++) { c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; }
  c = 0xffffffff; for (const b of buf) c = t[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]);
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); }
let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
for (let y = 0; y < H; y++) {
  px[y * (W * 4 + 1)] = 0;
  for (let x = 0; x < W; x++) {
    const u = (x + 0.5) / W - 0.5;               // -0.5..0.5 across
    const edge = Math.max(0, 1 - Math.abs(u) / 0.5);
    let a = Math.min(1, Math.pow(edge, 0.55) * 1.25); // soft edges, solid core
    let v = 236;                                  // off-white silk
    // two fibres braiding round each other (period 32 along the strand)
    const ph = (y / 32) * Math.PI * 2;
    const f1 = Math.abs(u - 0.32 * Math.sin(ph)), f2 = Math.abs(u - 0.32 * Math.sin(ph + Math.PI));
    const front1 = Math.cos(ph) > 0, front2 = !front1;
    if (f1 < 0.085) v = front1 ? 255 : 196;       // the fibre over the top is bright, the one passing under is shaded
    if (f2 < 0.085) v = front2 ? 255 : 196;
    if (Math.abs(f1 - 0.085) < 0.03 || Math.abs(f2 - 0.085) < 0.03) v = Math.min(v, 205); // fibre outlines
    v += Math.round((rnd() - 0.5) * 14);          // a little grain
    if (Math.abs(u) > 0.42) a *= 0.55;            // fuzz at the edges
    const i = y * (W * 4 + 1) + 1 + x * 4;
    px[i] = Math.max(0, Math.min(255, v)); px[i + 1] = Math.max(0, Math.min(255, v + 2)); px[i + 2] = Math.max(0, Math.min(255, v + 6));
    px[i + 3] = Math.round(Math.max(0, Math.min(1, a)) * 255);
  }
}
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
fs.writeFileSync(out, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(px)), chunk("IEND", Buffer.alloc(0))]));
console.log("wrote " + out);
