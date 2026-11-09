// v0.17.3: Andy's bone clone texture -- a real skeleton on the 64x64 player layout (skull face, spine, ribs, pelvis, arm and leg
// bones with knobbly joints, finger and toe bones), everything between the bones fully transparent so you see through the gaps.
// Run from the repo root: node scratchpad/andy/bone_clone_skin.js
const fs = require('fs');
const zlib = require('zlib');
const W = 64, H = 64;
const px = new Uint8Array(W * H * 4); // all transparent
const IV = [0xE8, 0xE0, 0xC8], SH = [0xC9, 0xBE, 0xA0], DK = [0x9A, 0x8C, 0x6E], HOLE = [0x24, 0x10, 0x10], BL = [0x8A, 0x10, 0x14];
function set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= W || y >= H) return; const i = (y * W + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; }
function rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) set(x + i, y + j, c); }
let seed = 7; function rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }
// a little shading on a bone pixel
function bone(x, y) { const r = rnd(); set(x, y, r < 0.18 ? SH : r < 0.22 ? DK : IV); }
function boneRect(x, y, w, h) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) bone(x + i, y + j); }

// ---- head (skull): solid bone on every face, face details on the front
boneRect(8, 0, 8, 8); boneRect(16, 0, 8, 8); // top, bottom
boneRect(0, 8, 8, 8); boneRect(8, 8, 8, 8); boneRect(16, 8, 8, 8); boneRect(24, 8, 8, 8);
// front face 8..15 x, 8..15 y: eye sockets, nose, teeth
rect(9, 10, 2, 2, HOLE); rect(13, 10, 2, 2, HOLE); set(10, 12, DK); set(13, 12, DK);
set(11, 12, HOLE); set(12, 12, HOLE); set(11, 13, SH); set(12, 13, SH);
for (let i = 9; i <= 14; i++) set(i, 14, i % 2 ? IV : HOLE);
for (let i = 9; i <= 14; i++) set(i, 15, SH);
rect(8, 15, 1, 1, DK); rect(15, 15, 1, 1, DK);
// cheek cracks and a blood stain from the card above the left eye
set(9, 9, DK); set(14, 13, DK); set(13, 9, BL); set(14, 9, BL); set(14, 8, BL);
// the bottom of the jaw open at the back
rect(24, 13, 8, 3, SH);

// ---- body: spine, ribs, pelvis (front 20..27 x 20..31), back (32..39)
function torso(x0, front) {
  for (let y = 20; y < 32; y++) { bone(x0 + 3, y); bone(x0 + 4, y); } // spine / sternum
  for (const ry of [21, 23, 25]) { for (let x = 0; x < 8; x++) bone(x0 + x, ry); set(x0, ry + 1, SH); set(x0 + 7, ry + 1, SH); }
  if (front) { set(x0 + 1, 27, SH); set(x0 + 6, 27, SH); }
  else { boneRect(x0, 20, 2, 3); boneRect(x0 + 6, 20, 2, 3); } // shoulder blades
  boneRect(x0, 29, 8, 3); set(x0 + 2, 30, HOLE); set(x0 + 5, 30, HOLE); // pelvis
  boneRect(x0, 20, 8, 1); // collarbones
}
torso(20, true); torso(32, false);
// body sides 16..19 and 28..31: rib ends and the pelvis
for (const sx of [16, 28]) { for (const ry of [21, 23, 25]) rect(sx, ry, 4, 1, IV); boneRect(sx, 29, 4, 3); boneRect(sx, 20, 4, 1); }
boneRect(20, 16, 8, 4); boneRect(28, 16, 8, 4); // top (shoulders) and bottom (hips)

// ---- a limb: four side faces of 4 x 12 at x0..x0+15, y0..y0+11, with top and bottom caps above
function limb(x0, y0, w, hand) {
  // caps: top at (x0+w, y0-4), bottom at (x0+2w, y0-4)
  boneRect(x0 + w, y0 - 4, w, 4); boneRect(x0 + 2 * w, y0 - 4, w, 4);
  const faces = [x0, x0 + w, x0 + 2 * w, x0 + 3 * w];
  const fw = [w, w, w, w];
  for (let f = 0; f < 4; f++) {
    const fx = faces[f], ww = fw[f];
    const c0 = Math.floor((ww - 2) / 2);
    for (let y = 0; y < 12; y++) { bone(fx + c0, y0 + y); bone(fx + c0 + 1, y0 + y); } // the shaft, 2 px
    boneRect(fx, y0, ww, 2); // the knob at the shoulder / hip
    boneRect(fx, y0 + 5, ww, 2); // elbow / knee
    if (hand) { for (let i = 0; i < ww; i += 2) { set(fx + i, y0 + 10, IV); set(fx + i, y0 + 11, IV); } boneRect(fx, y0 + 9, ww, 1); }
    else { boneRect(fx, y0 + 10, ww, 2); set(fx + 1, y0 + 11, SH); }
    // a streak of dried blood on some faces
    if (f === 1) { set(fx + c0, y0 + 3, BL); set(fx + c0, y0 + 4, BL); }
  }
}
limb(40, 20, 4, true);   // right arm (wide; slim uses the same texels, its 3 px faces still land on bone)
limb(32, 52, 4, true);   // left arm
limb(0, 20, 4, false);   // right leg
limb(16, 52, 4, false);  // left leg

// ---- write the PNG
function crc32(buf) { let c, crc = 0xffffffff; for (let n = 0; n < buf.length; n++) { c = (crc ^ buf[n]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, crc]); }
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const raw = Buffer.alloc((W * 4 + 1) * H); for (let y = 0; y < H; y++) { raw[y * (W * 4 + 1)] = 0; Buffer.from(px.buffer, y * W * 4, W * 4).copy(raw, y * (W * 4 + 1) + 1); }
const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
fs.writeFileSync('src/main/resources/assets/projecthero/textures/entity/andy/bone_clone.png', png);
console.log('ok', png.length);
