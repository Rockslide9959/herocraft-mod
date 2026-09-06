// "changes 22": redraw every Iron Man crafting component sprite.
//
// The originals were all near-identical flat rounded squares in slightly different greys and golds --
// literally ~110 bytes of PNG each, i.e. one fill and a border. In a Fabricator tray holding nine
// different components you could not tell a Servo Motor from a Stark Circuit without hovering.
//
// Every sprite below is now separated on THREE axes at once, so they stay distinguishable at 16x16 in
// a crowded inventory row:
//   * silhouette  -- disc / cylinder / flat plate / stacked sheets / board / rack / cone / ring ...
//   * hue         -- copper, steel, gold, PCB green, Stark gold-on-blue, reactor cyan, missile red ...
//   * a highlight -- exactly one bright accent per item, in a different place on each.
//
// Same hand-rolled PNG encoder the other scripts in this folder use (Node zlib + a CRC32 table): this
// project has no image library and no Python. Nothing here is derived from Minecraft's own textures.
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const OUT = 'src/main/resources/assets/projecthero/textures/item';

// ---------------------------------------------------------------- PNG encoding

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
};
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
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))]);
}

// ---------------------------------------------------------------- tiny canvas

class C {
  constructor(w = 16, h = 16) {
    this.w = w; this.h = h;
    this.data = Buffer.alloc(w * h * 4); // transparent
  }
  set(x, y, c) {
    x = Math.round(x); y = Math.round(y);
    if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2];
    this.data[i + 3] = c.length > 3 ? c[3] : 255;
  }
  rect(x, y, w, h, c) {
    for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, c);
  }
  /** 1px outline just outside nothing -- draws the border ON the given rect. */
  frame(x, y, w, h, c) {
    for (let dx = 0; dx < w; dx++) { this.set(x + dx, y, c); this.set(x + dx, y + h - 1, c); }
    for (let dy = 0; dy < h; dy++) { this.set(x, y + dy, c); this.set(x + w - 1, y + dy, c); }
  }
  /** Filled circle centred on (cx, cy) with radius r (pixel centres at +0.5). */
  disc(cx, cy, r, c) {
    for (let y = 0; y < this.h; y++) {
      for (let x = 0; x < this.w; x++) {
        const dx = x + 0.5 - cx, dy = y + 0.5 - cy;
        if (dx * dx + dy * dy <= r * r) this.set(x, y, c);
      }
    }
  }
  /** Circle outline: pixels whose distance falls in [r-t, r]. */
  ring(cx, cy, r, t, c) {
    for (let y = 0; y < this.h; y++) {
      for (let x = 0; x < this.w; x++) {
        const dx = x + 0.5 - cx, dy = y + 0.5 - cy;
        const d = Math.sqrt(dx * dx + dy * dy);
        if (d <= r && d >= r - t) this.set(x, y, c);
      }
    }
  }
  line(x0, y0, x1, y1, c) {
    const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    for (let i = 0; i <= steps; i++) {
      this.set(x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps, c);
    }
  }
  /** Filled triangle (used for the arc reactor's core and the thruster cone). */
  tri(p0, p1, p2, c) {
    const area = (a, b, p) => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    for (let y = 0; y < this.h; y++) {
      for (let x = 0; x < this.w; x++) {
        const p = [x + 0.5, y + 0.5];
        const d0 = area(p0, p1, p), d1 = area(p1, p2, p), d2 = area(p2, p0, p);
        const neg = d0 < 0 || d1 < 0 || d2 < 0;
        const pos = d0 > 0 || d1 > 0 || d2 > 0;
        if (!(neg && pos)) this.set(x, y, c);
      }
    }
  }
  save(name) {
    fs.mkdirSync(OUT, { recursive: true });
    const file = path.join(OUT, name + '.png');
    fs.writeFileSync(file, encode(this.w, this.h, this.data));
    console.log('wrote ' + file);
  }
}

// ---------------------------------------------------------------- palettes

const OUTLINE = [22, 20, 26];
const STEEL_D = [86, 92, 102];
const STEEL = [140, 148, 160];
const STEEL_L = [196, 204, 214];
const COPPER_D = [138, 72, 33];
const COPPER = [200, 114, 52];
const COPPER_L = [240, 168, 96];
const GOLD_D = [150, 108, 22];
const GOLD = [216, 166, 44];
const GOLD_L = [252, 224, 122];
const PCB_D = [22, 78, 46];
const PCB = [38, 128, 72];
const PCB_L = [96, 200, 128];
const CYAN_D = [22, 110, 132];
const CYAN = [86, 208, 236];
const CYAN_L = [214, 252, 255];
const BLUE_D = [30, 52, 108];
const BLUE = [58, 100, 200];
const BLUE_L = [140, 190, 255];
const RED_D = [116, 26, 26];
const RED = [190, 48, 44];
const RED_L = [250, 122, 96];
const PURPLE = [148, 96, 214];
const PURPLE_L = [216, 176, 255];
const MAGENTA = [200, 62, 168];
const MAGENTA_L = [255, 158, 226];
const DARK = [44, 44, 52];
const DARK_L = [72, 74, 86];

// ================================================================ basic components

// COPPER WIRING -- a loose coil of orange wire. Silhouette: three concentric open loops.
{
  const c = new C();
  c.ring(8, 8, 7, 1, OUTLINE);
  c.ring(8, 8, 6.4, 1.6, COPPER_D);
  c.ring(8, 8, 5, 1.6, COPPER);
  c.ring(8, 8, 3.4, 1.6, COPPER_L);
  c.ring(8, 8, 1.8, 1.4, COPPER_D);
  // the loose end trailing off to the lower right, so it never reads as a solid disc
  c.line(11, 12, 14, 14, COPPER);
  c.set(15, 14, COPPER_L);
  c.save('copper_wiring');
}

// METAL PLATING -- a flat riveted steel plate seen face-on. Silhouette: wide rectangle, 4 rivets.
{
  const c = new C();
  c.rect(2, 4, 12, 8, STEEL);
  c.frame(2, 4, 12, 8, OUTLINE);
  c.rect(3, 5, 10, 2, STEEL_L);   // top highlight band
  c.rect(3, 9, 10, 2, STEEL_D);   // bottom shadow band
  for (const [x, y] of [[4, 6], [11, 6], [4, 9], [11, 9]]) {
    c.set(x, y, OUTLINE);
    c.set(x, y - 1, STEEL_L);
  }
  c.save('metal_plating');
}

// BASIC CIRCUIT -- a plain green PCB. Silhouette: square board, straight traces, one black chip.
{
  const c = new C();
  c.rect(2, 2, 12, 12, PCB);
  c.frame(2, 2, 12, 12, OUTLINE);
  c.line(3, 5, 12, 5, PCB_L);
  c.line(3, 11, 12, 11, PCB_L);
  c.line(12, 5, 12, 11, PCB_L);
  c.line(4, 3, 4, 5, PCB_D);
  c.rect(6, 7, 5, 3, DARK);       // the chip
  c.rect(6, 7, 5, 1, DARK_L);
  for (let x = 6; x < 11; x += 2) { c.set(x, 6, STEEL_L); c.set(x, 10, STEEL_L); }
  c.save('basic_circuit');
}

// MECHANICAL PARTS -- a gear and a bolt. Silhouette: a solid toothed wheel plus a small nut.
{
  const c = new C();
  // teeth first, so the body drawn over them keeps a clean rim
  for (const [dx, dy] of [[0, -5], [0, 5], [-5, 0], [5, 0], [-4, -4], [3, -4], [-4, 3], [3, 3]]) {
    c.rect(6 + dx, 6 + dy, 2, 2, STEEL);
    c.frame(6 + dx, 6 + dy, 2, 2, OUTLINE);
  }
  c.disc(6.5, 6.5, 5, OUTLINE);
  c.disc(6.5, 6.5, 4.2, STEEL);
  c.disc(6.5, 6.5, 3.2, STEEL_L);
  c.disc(6.5, 6.5, 2, OUTLINE);   // hub bore, punched right through
  c.disc(6.5, 6.5, 1.3, DARK);
  // nut, lower right, overlapping the gear so the two shapes read as a pile of parts
  c.disc(12.5, 12.5, 3.4, OUTLINE);
  c.disc(12.5, 12.5, 2.7, COPPER);
  c.disc(12.5, 12.5, 1.1, DARK);
  c.set(11, 11, COPPER_L);
  c.save('mechanical_parts');
}

// ================================================================ advanced components

// TITANIUM-GOLD ALLOY -- a chunky ingot, half gold and half titanium. Silhouette: a deep block with
// a sloped top face, split straight down the middle so the two metals are unmissable.
{
  const c = new C();
  // top face (the slope), rows 4-6, narrowing upward
  for (let y = 4; y <= 6; y++) {
    const inset = 6 - y + 1;
    for (let x = 2 + inset; x <= 13 - inset; x++) c.set(x, y, x <= 7 ? GOLD_L : STEEL_L);
  }
  // front face, rows 7-12
  for (let y = 7; y <= 12; y++) {
    for (let x = 2; x <= 13; x++) c.set(x, y, x <= 7 ? (y > 10 ? GOLD_D : GOLD) : (y > 10 ? STEEL_D : STEEL));
  }
  // outline
  for (let y = 4; y <= 6; y++) {
    const inset = 6 - y + 1;
    c.set(2 + inset - 1, y, OUTLINE); c.set(13 - inset + 1, y, OUTLINE);
    if (y === 4) for (let x = 2 + inset; x <= 13 - inset; x++) c.set(x, y - 1, OUTLINE);
  }
  for (let y = 7; y <= 12; y++) { c.set(1, y, OUTLINE); c.set(14, y, OUTLINE); }
  for (let x = 1; x <= 14; x++) c.set(x, 13, OUTLINE);
  c.line(7, 4, 7, 12, [120, 96, 40]);   // the seam between the two metals
  c.save('titanium_gold_alloy');
}

// TITANIUM-GOLD PLATE -- three thin sheets stacked. Silhouette: horizontal layers, unmistakably flat.
{
  const c = new C();
  const sheet = (y, main, hi) => {
    c.rect(2, y, 12, 3, main);
    c.frame(2, y, 12, 3, OUTLINE);
    c.line(3, y + 1, 12, y + 1, hi);
  };
  sheet(3, GOLD_D, GOLD);
  sheet(7, GOLD, GOLD_L);
  sheet(11, STEEL, STEEL_L);
  c.save('titanium_gold_plate');
}

// SERVO MOTOR -- a barrel motor lying on its side with a drive shaft. Silhouette: cylinder + stub.
{
  const c = new C();
  c.rect(1, 4, 11, 9, STEEL);
  c.frame(1, 4, 11, 9, OUTLINE);
  c.rect(2, 5, 9, 2, STEEL_L);          // top highlight along the barrel
  c.rect(2, 11, 9, 1, STEEL_D);         // underside shadow
  c.rect(2, 7, 9, 3, RED);              // the motor's red band -- its one loud accent
  c.rect(2, 7, 9, 1, RED_L);
  c.rect(1, 4, 3, 9, STEEL_D);          // end cap (left)
  c.frame(1, 4, 3, 9, OUTLINE);
  c.rect(2, 5, 1, 7, STEEL_L);
  c.rect(12, 7, 4, 3, STEEL_L);         // drive shaft sticking out the right
  c.frame(12, 7, 4, 3, OUTLINE);
  c.save('servo_motor');
}

// MICRO THRUSTER -- a nozzle cone firing downward. Silhouette: tapered cone + a blue flame.
{
  const c = new C();
  c.rect(5, 1, 6, 3, STEEL_D);          // mount collar
  c.frame(5, 1, 6, 3, OUTLINE);
  for (let y = 4; y < 10; y++) {        // the flaring bell
    const half = 2 + (y - 4);
    for (let x = 8 - half; x <= 7 + half; x++) c.set(x, y, y < 7 ? STEEL : STEEL_D);
    c.set(8 - half, y, OUTLINE);
    c.set(7 + half, y, OUTLINE);
  }
  c.line(6, 5, 9, 5, STEEL_L);
  c.tri([4.5, 10], [11.5, 10], [8, 15.5], CYAN);   // exhaust plume
  c.tri([6, 10], [10, 10], [8, 14], CYAN_L);
  c.save('micro_thruster');
}

// REPULSOR -- the palm emitter: a dark housing around a blinding white-cyan core.
{
  const c = new C();
  c.ring(8, 8, 7.5, 1, OUTLINE);
  c.disc(8, 8, 7, STEEL_D);
  c.ring(8, 8, 7, 1.2, DARK);
  c.disc(8, 8, 5, CYAN_D);
  c.ring(8, 8, 5, 1, STEEL_L);
  c.disc(8, 8, 3.4, CYAN);
  c.disc(8, 8, 1.8, CYAN_L);
  // four emitter vanes across the lens -- reads as an emitter, not a plain bullseye
  c.set(8, 4, CYAN_L); c.set(8, 11, CYAN_L); c.set(4, 8, CYAN_L); c.set(11, 8, CYAN_L);
  c.save('repulsor');
}

// FLIGHT STABILIZER -- a gyroscope: two crossed rings around an amethyst core.
{
  const c = new C();
  c.ring(8, 8, 7.5, 1, OUTLINE);
  c.ring(8, 8, 7, 1.6, STEEL_L);          // outer gimbal
  c.ring(8, 8, 5.6, 1, OUTLINE);
  // inner gimbal: a wide, flat ellipse, so the two rings read as being at right angles
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const dx = (x + 0.5 - 8) / 5.2, dy = (y + 0.5 - 8) / 2.6;
      const d = Math.sqrt(dx * dx + dy * dy);
      if (d <= 1.02 && d >= 0.55) c.set(x, y, d > 0.85 ? OUTLINE : STEEL);
    }
  }
  c.disc(8, 8, 3, OUTLINE);
  c.disc(8, 8, 2.4, PURPLE);
  c.disc(8, 8, 1.2, PURPLE_L);
  c.save('flight_stabilizer');
}

// TARGETING MODULE -- a lens behind a crosshair. Silhouette: square housing + reticle arms.
{
  const c = new C();
  c.rect(2, 2, 12, 12, DARK);
  c.frame(2, 2, 12, 12, OUTLINE);
  c.disc(8, 8, 4.6, BLUE_D);
  c.ring(8, 8, 4.6, 1, STEEL);
  c.disc(8, 8, 2.4, PCB_L);           // the green "eye"
  c.set(7, 7, CYAN_L);
  // reticle arms reaching to the housing edge
  c.line(8, 2, 8, 4, RED_L); c.line(8, 11, 8, 13, RED_L);
  c.line(2, 8, 4, 8, RED_L); c.line(11, 8, 13, 8, RED_L);
  c.save('targeting_module');
}

// STARK CIRCUIT -- gold-trace board with a blue die. Deliberately the inverse of the basic circuit:
// gold on dark instead of green, chip centred and glowing rather than flat black.
{
  const c = new C();
  c.rect(2, 2, 12, 12, DARK);
  c.frame(2, 2, 12, 12, OUTLINE);
  c.line(3, 4, 12, 4, GOLD);
  c.line(3, 12, 12, 12, GOLD);
  c.line(3, 4, 3, 12, GOLD_D);
  c.line(12, 4, 12, 12, GOLD_D);
  c.line(5, 6, 5, 10, GOLD_D);
  c.line(10, 6, 10, 10, GOLD_D);
  c.rect(6, 6, 4, 4, BLUE);
  c.frame(6, 6, 4, 4, BLUE_D);
  c.set(7, 7, BLUE_L);
  for (let y = 6; y < 10; y += 2) { c.set(5, y, GOLD_L); c.set(10, y, GOLD_L); }
  c.save('stark_circuit');
}

// SUIT COMPUTER -- a slab with a lit readout screen. Silhouette: tall unit, blue screen, side ports.
{
  const c = new C();
  c.rect(3, 1, 10, 14, DARK_L);
  c.frame(3, 1, 10, 14, OUTLINE);
  c.rect(4, 2, 8, 8, BLUE_D);        // screen
  c.frame(4, 2, 8, 8, OUTLINE);
  c.line(5, 4, 10, 4, CYAN_L);       // readout lines
  c.line(5, 6, 8, 6, CYAN);
  c.line(5, 8, 9, 8, CYAN);
  for (let x = 4; x < 12; x += 2) c.set(x, 12, GOLD);   // connector pins
  c.rect(4, 13, 8, 1, STEEL_D);
  c.save('suit_computer');
}

// ADVANCED ARC REACTOR -- the triangle-in-a-circle, blazing cyan. The brightest sprite in the set.
{
  const c = new C();
  c.ring(8, 8, 7.5, 1, OUTLINE);
  c.disc(8, 8, 7, STEEL);
  c.ring(8, 8, 7, 1.4, STEEL_D);
  c.disc(8, 8, 5.4, DARK);
  c.ring(8, 8, 5.4, 1, GOLD);
  c.disc(8, 8, 4.4, CYAN_D);
  c.tri([8, 3.4], [12.2, 10.8], [3.8, 10.8], CYAN);
  c.tri([8, 5.4], [10.9, 10.2], [5.1, 10.2], CYAN_L);
  c.save('advanced_arc_reactor');
}

// REACTOR CORE -- a fuel canister, not a reactor face. Silhouette: upright cell with ribbed body.
{
  const c = new C();
  c.rect(4, 1, 8, 2, STEEL_L);        // cap
  c.frame(4, 1, 8, 2, OUTLINE);
  c.rect(3, 3, 10, 11, STEEL_D);
  c.frame(3, 3, 10, 11, OUTLINE);
  c.rect(5, 5, 6, 7, PCB_D);          // the glowing window (green, so it never reads as the reactor)
  c.frame(5, 5, 6, 7, OUTLINE);
  c.rect(6, 6, 4, 5, PCB);
  c.rect(6, 6, 4, 2, PCB_L);
  c.line(3, 4, 12, 4, STEEL);         // ribs
  c.line(3, 13, 12, 13, STEEL);
  c.save('reactor_core');
}

// MISSILE MODULE -- a rack of three red-tipped missiles. Silhouette: parallel darts, unmistakable.
{
  const c = new C();
  c.rect(1, 2, 3, 12, DARK);          // rack spine on the left
  c.frame(1, 2, 3, 12, OUTLINE);
  const missile = (y) => {
    c.rect(4, y, 8, 3, STEEL);
    c.frame(4, y, 8, 3, OUTLINE);
    c.line(5, y + 1, 10, y + 1, STEEL_L);
    c.rect(12, y, 3, 3, RED);         // warhead
    c.frame(12, y, 3, 3, OUTLINE);
    c.set(13, y + 1, RED_L);
  };
  missile(2); missile(7); missile(12);
  c.save('missile_module');
}

// MODULAR ARMOR CONTROLLER -- a rounded control box: a dial, a status LED and a vent grille.
{
  const c = new C();
  c.rect(2, 3, 12, 10, STEEL_D);
  c.frame(2, 3, 12, 10, OUTLINE);
  c.rect(3, 4, 10, 1, STEEL_L);
  c.disc(6, 8, 3, DARK);              // the dial
  c.ring(6, 8, 3, 1, STEEL_L);
  c.line(6, 8, 6, 6, GOLD_L);         // dial pointer
  c.disc(11, 6, 1.6, RED);            // status LED
  c.set(11, 6, RED_L);
  for (let y = 9; y < 12; y++) c.line(9, y, 12, y, DARK_L);  // vent grille
  c.save('modular_armor_controller');
}

// NANOTECH MATRIX -- a lattice of magenta nodes on a dark field. Silhouette: a grid, not a solid.
{
  const c = new C();
  c.rect(2, 2, 12, 12, DARK);
  c.frame(2, 2, 12, 12, OUTLINE);
  const nodes = [[5, 5], [10, 5], [5, 10], [10, 10], [7, 7]];
  const STRUT = [96, 34, 84];
  for (const [x, y] of nodes) {
    for (const [x2, y2] of nodes) c.line(x, y, x2, y2, STRUT);
  }
  for (const [x, y] of nodes) {
    c.disc(x + 0.5, y + 0.5, 1.6, MAGENTA);
    c.set(x, y, MAGENTA_L);
  }
  c.save('nanotech_matrix');
}

console.log('done');
