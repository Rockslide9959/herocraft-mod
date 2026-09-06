// Phase-2 Punisher assets: textures for the assault rifle / shotgun / sniper (64x64 each, mapped by
// their item models), the three new ammo sprites + Gun Barrel + Weapon Scope (16x16), and the
// full-screen sniper scope overlay (256x256, GUI). Hand-rolled PNG encoder -- no image lib.
const fs = require('fs');
const zlib = require('zlib');
const ITEM = 'src/main/resources/assets/projecthero/textures/item';
const GUI = 'src/main/resources/assets/projecthero/textures/gui';

const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0);
  const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, c]); };
function encode(w, h, data) {
  const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; data.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
const clamp = v => Math.max(0, Math.min(255, v | 0));
class C {
  constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
  set(x, y, c) { x |= 0; y |= 0; if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c.length > 3 ? c[3] : 255; }
  rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
  noise(x, y, w, h, base, amp) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
    const n = ((Math.sin((x + i) * 12.9898 + (y + j) * 78.233) * 43758.5) % 1 + 1) % 1;
    const k = (n - 0.5) * amp; this.set(x + i, y + j, [clamp(base[0] + k), clamp(base[1] + k), clamp(base[2] + k), 255]); } }
  png() { return encode(this.w, this.h, this.d); }
}

const BLACK = [20, 20, 22], DARK = [34, 35, 39], MID = [50, 51, 56], HI = [78, 80, 86];
const WOOD = [58, 42, 30], STEEL = [120, 122, 128], BRASS = [190, 150, 70], WHITE = [230, 230, 228];

// ---- weapon textures. Layout regions used by the item models (see punisher_*.json):
//   0,0  = receiver band (28x6)
//   0,8  = barrel / long band (32x5)
//   0,16 = magazine / grip (10x16)
//   0,34 = stock / pump (16x8)
//   40,0 = optic / detail (12x10)
function gunTex(name, receiverCol, accent) {
  const g = new C(64, 64);
  g.noise(0, 0, 28, 6, receiverCol, 8); g.rect(0, 0, 28, 1, HI); g.rect(0, 5, 28, 1, BLACK);
  g.noise(0, 8, 32, 5, DARK, 6); g.rect(0, 8, 32, 1, MID); for (let x = 2; x < 30; x += 5) g.rect(x, 10, 1, 3, BLACK); // vents
  g.noise(0, 16, 10, 16, MID, 10); for (let y = 18; y < 30; y += 2) g.rect(1, y, 8, 1, BLACK); // mag ribs
  g.noise(0, 34, 16, 8, accent, 10); g.rect(0, 34, 16, 1, HI);
  g.noise(40, 0, 12, 10, BLACK, 6); g.rect(41, 1, 10, 1, [60, 62, 68]);
  fs.writeFileSync(`${ITEM}/${name}.png`, g.png());
}
gunTex('punisher_assault_rifle', DARK, MID);
gunTex('punisher_shotgun', [40, 40, 44], WOOD);
gunTex('punisher_sniper', BLACK, DARK);

// ---- ammo sprites 16x16
function ammoBox(name, casing, tip) {
  const a = new C(16, 16);
  a.noise(2, 6, 12, 8, [64, 52, 38], 10); a.rect(2, 6, 12, 1, [92, 76, 54]); a.rect(2, 13, 12, 1, [40, 33, 24]);
  for (let i = 0; i < 4; i++) { const x = 3 + i * 3; a.rect(x, 3, 2, 4, casing); a.rect(x, 2, 2, 1, tip); }
  fs.writeFileSync(`${ITEM}/${name}.png`, a.png());
}
ammoBox('rifle_ammo', [180, 145, 68], [150, 118, 52]);
// shotgun shell: single, dark red casing + brass base
{
  const s = new C(16, 16);
  s.rect(5, 3, 6, 9, [150, 40, 40]); s.rect(5, 3, 6, 1, [190, 70, 70]); s.rect(5, 11, 6, 3, BRASS); s.rect(5, 13, 6, 1, [140, 108, 50]);
  fs.writeFileSync(`${ITEM}/shotgun_shell.png`, s.png());
}
ammoBox('sniper_ammo', [150, 152, 158], [110, 112, 118]);

// ---- crafting components 16x16
{
  const b = new C(16, 16); // gun barrel
  b.rect(2, 6, 12, 4, STEEL); b.rect(2, 6, 12, 1, [160, 162, 168]); b.rect(2, 9, 12, 1, [70, 72, 78]);
  b.rect(1, 5, 3, 6, DARK); b.rect(13, 7, 2, 2, BLACK);
  fs.writeFileSync(`${ITEM}/gun_barrel.png`, b.png());
}
{
  const sc = new C(16, 16); // weapon scope
  sc.rect(2, 6, 12, 4, BLACK); sc.rect(2, 6, 12, 1, [50, 52, 58]);
  sc.rect(1, 5, 2, 6, DARK); sc.rect(13, 5, 2, 6, DARK);
  sc.rect(4, 7, 3, 2, [40, 60, 120]); // blue lens
  fs.writeFileSync(`${ITEM}/weapon_scope.png`, sc.png());
}

// ---- sniper scope overlay 256x256 (GUI). Transparent circle, black surround, crosshair, blue ring.
{
  const S = 256, o = new C(S, S), cx = 127.5, cy = 127.5, r = 118;
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const dx = x - cx, dy = y - cy, dist = Math.sqrt(dx * dx + dy * dy);
    if (dist > r) { o.set(x, y, [8, 8, 10, 255]); }                       // black surround
    else if (dist > r - 3) { o.set(x, y, [15, 15, 18, 255]); }            // inner rim
    else if (dist > r - 6) { o.set(x, y, [30, 36, 90, 200]); }            // blue-purple lens ring
    else { o.set(x, y, [0, 0, 0, 0]); }                                   // clear
  }
  // crosshair -- thin lines with a central gap, plus tick marks
  for (let x = 6; x < S - 6; x++) { if (Math.abs(x - cx) > 10) o.set(x, cy | 0, [10, 10, 12, 235]); }
  for (let y = 6; y < S - 6; y++) { if (Math.abs(y - cy) > 10) o.set(cx | 0, y, [10, 10, 12, 235]); }
  for (let k = -60; k <= 60; k += 20) { if (k === 0) continue;
    o.rect((cx + k) | 0, (cy - 2) | 0, 1, 4, [10, 10, 12, 235]);
    o.rect((cx - 2) | 0, (cy + k) | 0, 4, 1, [10, 10, 12, 235]); }
  fs.writeFileSync(`${GUI}/sniper_scope.png`, o.png());
}

console.log('phase 2 textures written');
