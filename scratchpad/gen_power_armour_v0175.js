// v0.17.5 Power Armour: every resource. Node + zlib only.
//  - the voxel suit layers (128x128, player-skin layout at 2x, one texel = half a model pixel):
//      frame.png (shared under-suit skeleton, coloured), <tier>_plates.png (greyscale x primary paint),
//      <tier>_trim.png (greyscale x secondary paint), <tier>_glow.png (white x glow paint)
//    every texel is painted from its 3D position on the body (part, face, x, y, z), so seams line up with the hinged panels in
//    client/powerarmour/SuitPanel.java (keep the two in step)
//  - modules.png (8x4 swatch cells for module boxes and pistons)
//  - item icons (frames, platings, board, cores, 23 modules), the Core Charger block textures + models + blockstate + loot
//  - recipes (board, platings, frames, smithing tier upgrades, cores, charger, modules) and the pickaxe tag (merged)
// Run from the repo (or worktree) root:   node scratchpad/gen_power_armour_v0175.js [root]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || process.cwd();
const A = path.join(root, 'src/main/resources/assets/projecthero');
const D = path.join(root, 'src/main/resources/data/projecthero');
const MC = path.join(root, 'src/main/resources/data/minecraft');

// ------------------------------------------------------------------------------------------------ png
const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function png(w, h, px) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
function canvas(w, h) {
  const px = Buffer.alloc(w * h * 4);
  return {
    w, h, px,
    set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= w || y >= h || !c) return; const i = (y * w + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = a; },
    get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
    alpha(x, y) { return x < 0 || y < 0 || x >= w || y >= h ? 0 : px[(y * w + x) * 4 + 3]; },
    png() { return png(w, h, px); },
  };
}
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');
// deterministic hash noise
const hash = (a, b, c = 0) => { let h = (a * 374761393 + b * 668265263 + c * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };

// ------------------------------------------------------------------------------------------------ the body (matches SuitPanel.Part)
const PARTS = {
  head: { x0: -4, y0: -8, z0: -4, w: 8, h: 8, d: 8, u: 0, v: 0 },
  body: { x0: -4, y0: 0, z0: -2, w: 8, h: 12, d: 4, u: 16, v: 16 },
  rarm: { x0: -3, y0: -2, z0: -2, w: 4, h: 12, d: 4, u: 40, v: 16 },
  larm: { x0: -1, y0: -2, z0: -2, w: 4, h: 12, d: 4, u: 32, v: 48 },
  rleg: { x0: -2, y0: 0, z0: -2, w: 4, h: 12, d: 4, u: 0, v: 16 },
  lleg: { x0: -2, y0: 0, z0: -2, w: 4, h: 12, d: 4, u: 16, v: 48 },
};
const FACES = ['N', 'S', 'W', 'E', 'D', 'U'];
function faceRect(p, f) {
  const u = p.u * 2, v = p.v * 2, w = p.w * 2, h = p.h * 2, d = p.d * 2;
  switch (f) {
    case 'N': return [u + d, v + d, w, h];
    case 'S': return [u + 2 * d + w, v + d, w, h];
    case 'W': return [u, v + d, d, h];
    case 'E': return [u + d + w, v + d, d, h];
    case 'D': return [u + d, v, w, d];
    default: return [u + d + w, v, w, d];
  }
}
// visits every texel of every face with its 3D centre (part space px) and face-plane coords (fu, fv in px, fw, fh)
function eachTexel(fn) {
  for (const [name, p] of Object.entries(PARTS)) {
    const x1 = p.x0 + p.w, y1 = p.y0 + p.h, z1 = p.z0 + p.d;
    for (const f of FACES) {
      const [ox, oy, gw, gh] = faceRect(p, f);
      for (let a = 0; a < gw; a++) for (let b = 0; b < gh; b++) {
        const fu = (a + 0.5) / 2, fv = (b + 0.5) / 2;
        let x, y, z;
        switch (f) {
          case 'N': x = p.x0 + fu; y = p.y0 + fv; z = p.z0; break;
          case 'S': x = x1 - fu; y = p.y0 + fv; z = z1; break;
          case 'W': z = z1 - fu; y = p.y0 + fv; x = p.x0; break;
          case 'E': z = p.z0 + fu; y = p.y0 + fv; x = x1; break;
          case 'D': x = p.x0 + fu; z = z1 - fv; y = p.y0; break;
          default: x = p.x0 + fu; z = z1 - fv; y = y1; break;
        }
        fn({ part: name, p, f, a, b, ox, oy, gw, gh, fu, fv, fw: gw / 2, fh: gh / 2, x, y, z, tx: ox + a, ty: oy + b });
      }
    }
  }
}
const arm = t => t.part === 'rarm' || t.part === 'larm';
const leg = t => t.part === 'rleg' || t.part === 'lleg';
const outerSide = t => (t.part === 'rarm' || t.part === 'rleg') ? t.f === 'W' : (t.part === 'larm' || t.part === 'lleg') ? t.f === 'E' : false;
const near = (v, c, r) => Math.abs(v - c) <= r;

// ------------------------------------------------------------------------------------------------ joints + seams (all tiers)
// where the plates leave the frame showing (in 3D), and the 1-texel seams between hinged panels (SuitPanel regions)
function isJoint(t) {
  const { x, y, z } = t;
  if (t.part === 'head') return t.f === 'U' && Math.abs(x) < 3 && Math.abs(z) < 3; // neck hole
  if (t.part === 'body') {
    if (y > 8.75 && y < 9.5) return true; // belt line
    if (t.f === 'U') return Math.abs(x) < 3.5 && Math.abs(z) < 1.5;
    return false;
  }
  if (arm(t)) {
    if (y > 0.5 && y < 1.0) return true; // under the pauldron
    if (y > 3.5 && y < 4.5) return true; // elbow
    if (y > 7.5 && y < 8.0) return true; // wrist
    if (t.f === 'D' && false) return true;
    return false;
  }
  if (leg(t)) {
    if (y < 0.5 && t.f !== 'U') return true; // hip
    if (y > 5.0 && y < 5.75 && !(t.f === 'N' && near(x, 0, 1.4))) return true; // knee (knee cap stays)
    if (y > 9.25 && y < 9.75) return true; // ankle
    if (t.f === 'D') return true; // top of the thigh is inside the body
    return false;
  }
  return false;
}
function isSeam(t) {
  const { x, y } = t;
  if (t.part === 'body') {
    if ((t.f === 'N' || t.f === 'S') && near(x, 0, 0.26) && y < 9) return true; // chest / back split
    if (t.f === 'N' && near(y, 7, 0.26)) return true; // chest / abdomen
    if ((t.f === 'N' || t.f === 'S') && (near(x, -4, 0.26) || near(x, 4, 0.26))) return true; // doors off the sides
    return false;
  }
  if (t.part === 'head') {
    if (t.f === 'N' && near(y, -6.2, 0.26)) return true; // faceplate line
    return false;
  }
  if (leg(t)) {
    if (t.f === 'N' && near(x, 0, 0.26) && y < 9.25) return true; // the leg front splits
    return false;
  }
  return false;
}

// ------------------------------------------------------------------------------------------------ frame layer (shared)
const FR = { base: [62, 66, 74], rib: [104, 110, 120], hi: [150, 156, 166], dark: [30, 32, 36], cable: [44, 40, 38], piston: [196, 168, 70], rubber: [36, 36, 40], glowdot: [90, 200, 255] };
function frameTexel(t) {
  const { x, y, z, fu, fv, fw, fh } = t;
  const n = hash(t.tx, t.ty, 7);
  const edge = Math.min(fu, fw - fu, fv, fh - fv);
  const shade = c => c.map(v => v * (0.9 + n * 0.18));
  if (t.part === 'head') {
    // a cage: edge rails, a crown ring, a jaw rim; the face stays open
    if (t.f === 'N') { if (fv > 6.5 || edge < 0.6) return shade(FR.rib); return null; }
    if (t.f === 'U') return Math.abs(x) < 3 && Math.abs(z) < 3 ? null : shade(FR.base);
    if (edge < 0.6) return shade(FR.rib);
    if (near(fv, 3.5, 0.26) || (t.f === 'D' && (near(x, 0, 0.26) || near(z, 0, 0.26)))) return shade(FR.base);
    if (t.f === 'S' && near(x, 0, 0.75)) return shade(FR.cable);
    return null;
  }
  if (t.part === 'body') {
    if (t.f === 'D' || t.f === 'U') return shade(t.f === 'D' && near(x, 0, 1.2) ? FR.dark : FR.base);
    if (t.f === 'W' || t.f === 'E') { // sides: solid with a cable channel
      if (near(fu, fw / 2, 0.5)) return shade(FR.cable);
      if (y > 8.75 && y < 9.5) return shade(FR.piston);
      return shade(Math.floor(fv * 2) % 4 === 0 ? FR.rib : FR.base);
    }
    // front / back: side rails, ribs every 2 px, a sternum / spine
    if (edge < 0.6) return shade(FR.rib);
    if (y > 8.75 && y < 9.5) return shade(FR.piston); // the belt
    if (near(x, 0, t.f === 'S' ? 0.9 : 0.5)) return shade(t.f === 'S' ? FR.dark : FR.rib);
    if (Math.floor(y * 2) % 4 === 1 && Math.abs(x) < 3.6) return shade(FR.base);
    if (t.f === 'N' && near(y, 3, 0.8) && near(x, 0, 1.2)) return FR.dark; // core housing
    return null;
  }
  // limbs: edge rails, rings, solid joints, glove / boot
  const hand = arm(t) && y > 7.75, boot = leg(t) && y > 9.5;
  if (hand || boot || t.f === 'U') return shade(hand && Math.floor(fv * 2) % 3 === 0 ? FR.hi : FR.rubber);
  if (arm(t) && y < 0.75) return shade(FR.base); // shoulder joint
  if (arm(t) && y > 3.25 && y < 4.75) return shade(near(fu, fw / 2, 0.5) ? FR.piston : FR.base); // elbow
  if (leg(t) && y > 4.75 && y < 6.0) return shade(near(fu, fw / 2, 0.5) ? FR.piston : FR.base); // knee
  if (t.f === 'D') return shade(FR.base);
  if (edge < 0.6) return shade(FR.rib);
  if (Math.floor(fv * 2) % 6 === 0) return shade(FR.base);
  if (outerSide(t) && near(fu, fw / 2, 0.3)) return shade(FR.cable);
  return null;
}

// ------------------------------------------------------------------------------------------------ per-tier plates / trim / glow
// each returns a grey value (0..255) or null; trim and glow only where a plate is
const TIERS = ['iron', 'gold', 'hero_alloy', 'diamond', 'netherite', 'vibranium', 'celestial'];

function plateCovered(t) { // the plate silhouette before tier cut-outs
  if (isJoint(t) || isSeam(t)) return false;
  const { x, y, z } = t;
  if (t.part === 'head') { if (t.f === 'U') return !(Math.abs(x) < 3.2 && Math.abs(z) < 3.2); return true; }
  if (t.part === 'body') { if (t.f === 'U') return false; return true; }
  if (arm(t)) { if (t.f === 'U') return false; return true; }
  if (leg(t)) { if (t.f === 'U') return true; return true; }
  return false;
}
// bevel: light from up-front-left
function bevel(t) {
  const { fu, fv, fw, fh } = t;
  const edge = Math.min(fu, fw - fu, fv, fh - fv);
  let v = 0;
  if (fv < 0.6) v += 22; else if (fh - fv < 0.6) v -= 26;
  if (fu < 0.6) v += 10; else if (fw - fu < 0.6) v -= 12;
  return v + (edge > 1 ? 0 : 0);
}
// distance (px) to the nearest plate border on this face (joint / seam / face edge), for edge trims
function borderDist(t) {
  let best = Math.min(t.fu, t.fw - t.fu, t.fv, t.fh - t.fv);
  // probe joints / seams along the face axes
  for (const [du, dv] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    for (let k = 1; k <= 3; k++) {
      const q = probe(t, du * k * 0.5, dv * k * 0.5);
      if (!q) break;
      if (isJoint(q) || isSeam(q)) { best = Math.min(best, k * 0.5 - 0.25); break; }
    }
  }
  return best;
}
function probe(t, du, dv) {
  const fu = t.fu + du, fv = t.fv + dv;
  if (fu < 0 || fv < 0 || fu > t.fw || fv > t.fh) return null;
  const p = t.p, x1 = p.x0 + p.w, y1 = p.y0 + p.h, z1 = p.z0 + p.d;
  let x, y, z;
  switch (t.f) {
    case 'N': x = p.x0 + fu; y = p.y0 + fv; z = p.z0; break;
    case 'S': x = x1 - fu; y = p.y0 + fv; z = z1; break;
    case 'W': z = z1 - fu; y = p.y0 + fv; x = p.x0; break;
    case 'E': z = p.z0 + fu; y = p.y0 + fv; x = x1; break;
    case 'D': x = p.x0 + fu; z = z1 - fv; y = p.y0; break;
    default: x = p.x0 + fu; z = z1 - fv; y = y1; break;
  }
  return Object.assign({}, t, { fu, fv, x, y, z });
}

// glow shapes: visor, core, strips -- per tier
function glowShape(tier, t) {
  const { x, y, z, f } = t;
  if (t.part === 'head' && f === 'N') {
    switch (tier) {
      case 'iron': return near(y, -4.6, 0.3) && Math.abs(x) < 3.2;
      case 'gold': return (near(y, -4.7, 0.3) && Math.abs(x) < 3.0) || (near(x, 0, 0.3) && y > -4.7 && y < -2.2);
      case 'hero_alloy': { const vy = -4.2 - Math.abs(x) * 0.35; return near(y, vy, 0.3) && Math.abs(x) < 3.2 && Math.abs(x) > 0.3; }
      case 'diamond': { const ex = Math.abs(Math.abs(x) - 1.8), ey = Math.abs(y + 4.6); return ex + ey < 0.9; }
      case 'netherite': return near(y, -4.4, 0.26) && Math.abs(x) < 2.6;
      case 'vibranium': { const ex = Math.abs(x); return ex > 0.6 && ex < 3.0 && near(y, -4.6 + (ex - 0.6) * 0.35, 0.28); }
      case 'celestial': { const r = Math.hypot(x, y + 3.6); return (r > 2.2 && r < 2.75 && y < -3.6) || (near(x, 0, 0.26) && near(y, -1.4, 0.26)); }
    }
  }
  if (t.part === 'body' && f === 'N') { // the core
    const r = Math.hypot(x, y - 3);
    switch (tier) {
      case 'iron': case 'gold': return r < 1.05 && !near(x, 0, 0.26);
      case 'hero_alloy': return y > 2 && y < 4.2 && Math.abs(x) < (4.2 - y) * 0.9 && Math.abs(x) > 0.26;
      case 'diamond': return Math.abs(x) + Math.abs(y - 3) < 1.3 && !near(x, 0, 0.26);
      case 'netherite': return (r < 0.9 && !near(x, 0, 0.26)) || crack(t);
      case 'vibranium': return (r < 0.8 && !near(x, 0, 0.26)) || (y > 5 && y < 6.6 && near(Math.abs(x), 2.4 - (y - 5) * 0.6, 0.26));
      case 'celestial': return (r < 1.2 && r > 0.55 && !near(x, 0, 0.26)) || star(t);
    }
  }
  if (arm(t) && f === 'N' && y > 4.8 && y < 7.2) { // forearm light
    if (tier === 'netherite') return crack(t);
    if (tier === 'celestial') return star(t) || (near(t.fu, t.fw / 2, 0.26) && y > 5.4 && y < 6.8);
    if (tier === 'vibranium') return near(t.fu, t.fw / 2, 0.26) && y > 5 && y < 7;
    return near(t.fu, t.fw / 2, 0.26) && y > 5.6 && y < 6.6;
  }
  if (leg(t) && f === 'N' && y > 6.5 && y < 8.8) { // shin light
    if (tier === 'netherite') return crack(t);
    if (tier === 'celestial') return star(t);
    return Math.abs(x) > 0.5 && Math.abs(x) < 1.0 && y > 7 && y < 8.4;
  }
  if (t.part === 'body' && f === 'S' && tier !== 'iron' && tier !== 'gold') { // spine lights
    return Math.abs(x) > 0.5 && Math.abs(x) < 1.0 && y > 2 && y < 7.5 && Math.floor(y * 2) % 3 !== 0;
  }
  return false;
}
function crack(t) { // molten cracks (netherite)
  const k = Math.sin(t.x * 2.3 + t.y * 1.1 + t.z * 1.7) + Math.sin(t.y * 3.1 - t.x * 0.7);
  return Math.abs(k) < 0.09 && hash(t.tx, t.ty, 3) > 0.25;
}
function star(t) { // scattered stars (celestial)
  return hash(Math.floor(t.tx / 3), Math.floor(t.ty / 3), 11) > 0.93 && (t.tx % 3 === 1) && (t.ty % 3 === 1);
}

// tier plate patterns: return { cut: bool, v: grey }
function platePattern(tier, t) {
  const { x, y, z, fu, fv, fw, fh, tx, ty } = t;
  const n = hash(tx, ty, 1);
  const bd = borderDist(t);
  let v = 214 + bevel(t) + (n - 0.5) * 10;
  let cut = false;
  switch (tier) {
    case 'iron': { // heavy rectangles, rivets on the borders, brushed
      v += (hash(Math.floor(tx / 4), ty, 2) - 0.5) * 14;
      if (bd < 0.9 && bd > 0.3 && (Math.round(fu * 2) + Math.round(fv * 2)) % 4 === 0) v = 250;
      if (arm(t) && y > 4.5 && y < 5.0) v -= 40;
      break;
    }
    case 'gold': { // smaller plates, filigree engraving, a bright shine band, ornamental cut-outs
      const swirl = Math.sin(fu * 2.2 + Math.sin(fv * 1.7) * 1.5) * Math.cos(fv * 1.9);
      if (Math.abs(swirl) < 0.08) v -= 55;
      if (near((fu + fv) % 3, 1.5, 0.3)) v += 18;
      if (bd > 1.2 && (arm(t) || leg(t)) && (t.f === 'W' || t.f === 'E') && near(fv % 2.5, 1.25, 0.26) && near(fu, fw / 2, 0.8)) cut = true;
      v += 10;
      break;
    }
    case 'hero_alloy': { // angular: chamfered corners, chevron panel lines
      const ch = Math.min(fu, fw - fu) + Math.min(fv, fh - fv);
      if (ch < 0.9 && (arm(t) || leg(t))) cut = true;
      const chev = (fv + Math.abs(fu - fw / 2) * 0.8) % 3;
      if (near(chev, 0.25, 0.25) && bd > 0.6) v -= 48;
      v += (fv < fh * 0.4 ? 8 : -6);
      break;
    }
    case 'diamond': { // faceted: alternating triangle facets, gaps between facet tiles on the limbs
      const cu = Math.floor(fu / 1.5), cv = Math.floor(fv / 1.5);
      const lu = fu - cu * 1.5, lv = fv - cv * 1.5;
      const upper = lu > lv;
      v = (upper ? 236 : 196) + ((cu + cv) % 2 ? 10 : -6) + bevel(t) * 0.5 + (n - 0.5) * 6;
      if ((arm(t) || leg(t)) && (lu < 0.3 || lv < 0.3) && bd > 0.4) cut = hash(cu, cv, t.part.length) > 0.55;
      break;
    }
    case 'netherite': { // layered: overlapping horizontal bands, dark lower lips, rough
      const band = fv % 1.5;
      v = 196 + (band < 0.5 ? 22 : band > 1.0 ? -34 : 0) + (n - 0.5) * 24 + bevel(t) * 0.6;
      if ((arm(t) || leg(t)) && band > 1.25 && t.f !== 'D') cut = hash(Math.floor(fu), Math.floor(fv / 1.5), 5) > 0.35;
      break;
    }
    case 'vibranium': { // satin with an engraved hex grid; hex gaps on the limbs
      const hx = fu / 0.9, hy = fv / 0.78;
      const row = Math.floor(hy), off = row % 2 ? 0.5 : 0;
      const cx = hx - off - Math.floor(hx - off) - 0.5, cy = hy - row - 0.5;
      const dh = Math.max(Math.abs(cx) * 1.15 + Math.abs(cy) * 0.6, Math.abs(cy) * 1.2);
      v = 206 + bevel(t) * 0.7 + (n - 0.5) * 6 + (fv < fh * 0.5 ? 10 : 0);
      if (dh > 0.62) { v -= 46; if ((arm(t) || leg(t)) && bd > 0.5 && hash(Math.floor(hx), row, 9) > 0.6) cut = true; }
      break;
    }
    case 'celestial': { // pearl: soft gradients, tiny sparkles, sleek chamfers
      v = 232 + bevel(t) * 0.6 + Math.sin(fu * 0.9 + fv * 0.6) * 8 + (n > 0.985 ? 23 : 0);
      const ch = Math.min(fu, fw - fu) + Math.min(fv, fh - fv);
      if (ch < 0.6 && (arm(t) || leg(t))) cut = true;
      break;
    }
  }
  // the visor recess and the core housing are dark on every tier
  if (t.part === 'head' && t.f === 'N' && y > -5.4 && y < -3.4 && Math.abs(x) < 3.4) v = Math.min(v, 70 + n * 14);
  if (t.part === 'body' && t.f === 'N' && Math.hypot(x, y - 3) < 1.5) v = Math.min(v, 80);
  return { cut, v };
}

function trimPattern(tier, t) {
  const { x, y, z, fu, fv, fw, fh, tx, ty } = t;
  const bd = borderDist(t);
  const n = hash(tx, ty, 4);
  const base = 220 + bevel(t) * 0.8 + (n - 0.5) * 10;
  // shared bones: helmet crest, chest ridges, pauldron rims, shin ridges, boot cuffs
  const crest = t.part === 'head' && (t.f === 'D' || t.f === 'S') && near(x, 0, 0.26) && (t.f === 'D' || y < -3);
  const pauldron = arm(t) && y > -0.4 && y < 0.5 && t.f !== 'D';
  const cuff = leg(t) && y > 9.75 && y < 10.3 && t.f !== 'U';
  const shin = leg(t) && t.f === 'N' && near(Math.abs(x), 0.75, 0.26) && y > 5.9 && y < 9;
  switch (tier) {
    case 'iron': {
      if (crest || pauldron || cuff) return base;
      if (bd < 0.3 && t.part === 'body' && t.f === 'N' && y < 7) return base - 20;
      if (bd < 0.3 && (arm(t) || leg(t)) && (t.f === 'N') && fv < 0.6) return base;
      return null;
    }
    case 'gold': {
      if (crest || pauldron || cuff || shin) return base + 10;
      if (bd < 0.3) return base;
      if (t.part === 'body' && t.f === 'N' && near(Math.hypot(x, y - 3), 1.8, 0.26)) return base + 15;
      return null;
    }
    case 'hero_alloy': { // twin racing stripes down the chest, back, limbs
      if (crest || pauldron) return base;
      if ((t.part === 'body' && (t.f === 'N' || t.f === 'S')) && (near(Math.abs(x), 1.6, 0.26) || near(Math.abs(x), 2.3, 0.26)) && y > 4.4) return base;
      if ((arm(t) || leg(t)) && outerSide(t) && near(fu, fw / 2, 0.5) && !(y > 3.4 && y < 4.6)) return base;
      if (t.part === 'head' && t.f === 'N' && near(y, -6.7, 0.26)) return base;
      return null;
    }
    case 'diamond': { // crystal ridges
      if (crest || cuff) return base + 15;
      if (pauldron && (Math.round(fu * 2) % 2 === 0)) return base + 15;
      if (t.part === 'body' && t.f === 'N' && y < 6.6 && near((Math.abs(x) + y) % 3, 0.25, 0.26)) return base;
      if ((arm(t) || leg(t)) && t.f === 'N' && near((fu + fv) % 2, 0.25, 0.26) && fv < 2) return base;
      return null;
    }
    case 'netherite': { // spikes and heavy ridges
      if (crest || pauldron || cuff || shin) return base - 10;
      if (arm(t) && outerSide(t) && y > 4.6 && y < 7.4 && near(fv % 1.0, 0.5, 0.26) && near(fu, fw / 2, 0.8)) return base;
      if (t.part === 'body' && t.f === 'N' && near(y, 0.6, 0.3)) return base;
      return null;
    }
    case 'vibranium': { // silver edges round every plate, a collar
      if (crest || pauldron || cuff) return base + 20;
      if (bd < 0.3 && t.part !== 'head') return base + 10;
      if (t.part === 'body' && (t.f === 'N' || t.f === 'S' || t.f === 'D') && y < 0.8) return base + 20;
      return null;
    }
    case 'celestial': { // gold filigree constellations
      if (crest || pauldron || cuff || shin) return base + 20;
      if (t.part === 'body' && t.f === 'N' && (near(Math.hypot(x, y - 3), 2.1, 0.26) || (near(x, 0, 0.26) && y < 1.2))) return base + 20;
      const k = Math.sin(fu * 1.4 + fv * 0.7) * Math.cos(fv * 1.1 - fu * 0.4);
      if ((arm(t) || leg(t) || t.part === 'body') && Math.abs(k) < 0.05 && hash(Math.floor(fu), Math.floor(fv), 13) > 0.4) return base + 10;
      return null;
    }
  }
  return null;
}

function buildTier(tier) {
  const P = canvas(128, 128), T = canvas(128, 128), G = canvas(128, 128);
  eachTexel(t => {
    if (!plateCovered(t)) return;
    const pp = platePattern(tier, t);
    if (pp.cut) return;
    P.set(t.tx, t.ty, [pp.v, pp.v, pp.v]);
    if (glowShape(tier, t)) {
      G.set(t.tx, t.ty, [255, 255, 255]);
      return;
    }
    const tv = trimPattern(tier, t);
    if (tv != null) T.set(t.tx, t.ty, [tv, tv, tv]);
  });
  const dir = path.join(A, 'textures/entity/power_armour');
  write(path.join(dir, `${tier}_plates.png`), P.png());
  write(path.join(dir, `${tier}_trim.png`), T.png());
  write(path.join(dir, `${tier}_glow.png`), G.png());
}

function buildFrame() {
  const F = canvas(128, 128);
  eachTexel(t => { const c = frameTexel(t); if (c) F.set(t.tx, t.ty, c); });
  write(path.join(A, 'textures/entity/power_armour/frame.png'), F.png());
}

function buildModules() {
  // 8 x 4 cells of 4 px: dark, mid, light metal, black, red, hazard, solar, copper / glow, vent, rubber, glass
  const M = canvas(32, 32);
  const cells = [[70, 74, 82], [128, 134, 144], [200, 206, 214], [26, 26, 30], [196, 46, 40], [230, 190, 40], [40, 70, 150], [196, 120, 60],
    [255, 255, 255], [60, 62, 68], [40, 40, 44], [150, 210, 240]];
  cells.forEach((c, i) => {
    const cx = (i % 8) * 4, cy = Math.floor(i / 8) * 4;
    for (let x = 0; x < 4; x++) for (let y = 0; y < 4; y++) {
      let k = 1 + (hash(cx + x, cy + y, 21) - 0.5) * 0.12;
      if (i === 5 && (x + y) % 4 < 2) k = 0.25; // hazard stripes
      if (i === 9 && y % 2 === 0) k = 0.55; // vent slats
      if (i === 6 && (x === 0 || y === 0)) k = 0.6; // solar grid
      M.set(cx + x, cy + y, c.map(v => v * k));
    }
  });
  write(path.join(A, 'textures/entity/power_armour/modules.png'), M.png());
}

// ------------------------------------------------------------------------------------------------ icons
const TIER_COL = {
  iron: { p: [184, 188, 194], s: [90, 95, 102], g: [255, 179, 71] },
  gold: { p: [242, 198, 65], s: [156, 106, 26], g: [255, 242, 160] },
  hero_alloy: { p: [70, 80, 95], s: [58, 160, 255], g: [150, 214, 255] },
  diamond: { p: [111, 230, 220], s: [232, 255, 255], g: [159, 255, 255] },
  netherite: { p: [74, 66, 70], s: [138, 90, 58], g: [255, 90, 31] },
  vibranium: { p: [58, 42, 80], s: [200, 205, 220], g: [176, 112, 255] },
  celestial: { p: [238, 234, 246], s: [242, 192, 58], g: [150, 230, 255] },
};
const mul = (c, k) => c.map(v => v * k);
function iconFrame(tier) {
  const c = canvas(16, 16), C = TIER_COL[tier], fr = [56, 60, 66];
  const rows = [
    '     hhhh     ',
    '    hhhhhh    ',
    '    hvvvvh    ',
    '    hhhhhh    ',
    '  ppppkkpppp  ',
    ' ppPppkkppPpp ',
    ' pp ppppppp pp',
    ' pp  pttpp  pp',
    ' gg ffffff  gg',
    '    pp  pp    ',
    '    pp  pp    ',
    '    PP  PP    ',
    '    pp  pp    ',
    '   bbb  bbb   ',
  ];
  const pal = { h: C.p, v: C.g, p: C.p, P: mul(C.p, 1.18), k: C.g, t: C.s, f: fr, g: fr, b: mul(C.s, 0.8) };
  rows.forEach((r, y) => [...r].forEach((ch, x) => { const col = pal[ch]; if (col) c.set(x + 1, y + 1, ch === 'p' && x < 7 ? mul(col, 1.08) : col); }));
  // outline
  outline(c, [20, 20, 24]);
  return c;
}
function outline(c, col) {
  const add = [];
  for (let y = 0; y < c.h; y++) for (let x = 0; x < c.w; x++) if (!c.alpha(x, y) && (c.alpha(x - 1, y) || c.alpha(x + 1, y) || c.alpha(x, y - 1) || c.alpha(x, y + 1))) add.push([x, y]);
  add.forEach(([x, y]) => c.set(x, y, col));
}
function iconPlating(tier) {
  const c = canvas(16, 16), C = TIER_COL[tier];
  for (let y = 3; y <= 12; y++) for (let x = 2; x <= 13; x++) {
    if ((x === 2 || x === 13) && (y === 3 || y === 12)) continue;
    let k = 1 + (y < 6 ? 0.12 : y > 10 ? -0.15 : 0) + (x < 4 ? 0.06 : x > 11 ? -0.08 : 0);
    let col = mul(C.p, k);
    if (y === 7 || y === 8) col = mul(C.s, 1);
    if ((x === 4 || x === 11) && (y === 4 || y === 11)) col = mul(C.p, 1.4);
    c.set(x, y, col);
  }
  outline(c, [20, 20, 24]);
  return c;
}
function iconBoard() {
  const c = canvas(16, 16);
  for (let y = 3; y <= 12; y++) for (let x = 2; x <= 13; x++) c.set(x, y, [30, 110, 50]);
  for (let x = 3; x <= 12; x++) { c.set(x, 5, [200, 50, 40]); c.set(x, 10, [200, 50, 40]); }
  for (let y = 5; y <= 10; y++) { c.set(5, y, [200, 50, 40]); c.set(10, y, [200, 50, 40]); }
  for (let y = 7; y <= 8; y++) for (let x = 7; x <= 8; x++) c.set(x, y, [40, 40, 44]);
  for (let x = 3; x <= 12; x += 3) { c.set(x, 2, [230, 190, 60]); c.set(x, 13, [230, 190, 60]); }
  c.set(7, 7, [255, 80, 60]);
  outline(c, [14, 40, 20]);
  return c;
}
function iconCore(kind) {
  const c = canvas(16, 16);
  if (kind === 'fusion_cell') {
    for (let y = 2; y <= 13; y++) for (let x = 5; x <= 10; x++) c.set(x, y, y < 4 || y > 11 ? [150, 156, 166] : (x === 5 || x === 10) ? [90, 96, 106] : [80, 220, 255]);
    for (let y = 5; y <= 10; y++) c.set(7, y, [220, 255, 255]);
  } else if (kind === 'arc_core') {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const r = Math.hypot(x - 7.5, y - 7.5); if (r < 6.2) c.set(x, y, r > 4.8 ? [140, 146, 158] : r > 2.4 ? [90, 170, 255] : [235, 250, 255]); }
  } else {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x - 7.5, dy = y - 7.5, a = Math.atan2(dy, dx), r = Math.hypot(dx, dy);
      const lim = 3 + 3.6 * Math.pow(Math.abs(Math.cos(a * 2.5)), 3);
      if (r < lim) c.set(x, y, r < 2 ? [255, 255, 240] : [255, 214, 90].map((v, i) => v - r * (i === 2 ? 4 : 6)));
    }
  }
  outline(c, [20, 20, 30]);
  return c;
}
const PART_COL = { helmet: [70, 130, 230], chest: [230, 140, 50], arms: [210, 60, 50], back: [150, 80, 220], legs: [70, 180, 90] };
const MODULES = {
  night_visor: ['helmet', '..##..|.#..#.|##..##|##..##|.#..#.|..##..', [110, 255, 110]],
  ore_scanner: ['helmet', '#....#|.#..#.|..##..|..##..|.#..#.|#....#', [255, 176, 64]],
  target_lock: ['helmet', '..##..|.#..#.|#.##.#|#.##.#|.#..#.|..##..', [255, 60, 60]],
  rebreather: ['helmet', '.####.|#....#|#.##.#|#.##.#|#....#|.####.', [120, 200, 255]],
  reactive_plating: ['chest', '#.#.#.|.#.#.#|#.#.#.|.#.#.#|#.#.#.|.#.#.#', [255, 220, 120]],
  capacitor_bank: ['chest', '##..##|##..##|##..##|##..##|##..##|##..##', [255, 160, 80]],
  overclock: ['chest', '..#...|.##...|######|...##.|...#..|......', [255, 112, 32]],
  solar_plate: ['chest', '######|#.#.#.|######|#.#.#.|######|......', [90, 140, 255]],
  breacher_plating: ['chest', '..##..|.####.|######|######|##..##|#....#', [220, 220, 220]],
  gatling: ['arms', '######|#.....|######|#.....|######|......', [255, 210, 96]],
  flamer: ['arms', '..#...|.###..|.####.|######|######|.####.', [255, 120, 40]],
  tesla_coil: ['arms', '..##..|######|..##..|######|..##..|######', [140, 220, 255]],
  rail_cannon: ['arms', '######|......|######|######|......|######', [120, 230, 255]],
  power_fist: ['arms', '.####.|######|######|######|.####.|..##..', [255, 200, 160]],
  magnetic_clamp: ['arms', '##..##|##..##|##..##|##..##|.####.|..##..', [255, 80, 80]],
  missile_pod: ['back', '#.#.#.|#.#.#.|######|######|######|######', [255, 70, 60]],
  shield_projector: ['back', '.####.|##..##|#....#|#....#|##..##|.####.', [120, 220, 255]],
  repair_drone: ['back', '#....#|.####.|.#..#.|.####.|#....#|......', [96, 255, 144]],
  sentry_turret: ['back', '..#...|..#...|.####.|######|#....#|#....#', [255, 64, 64]],
  thruster_pack: ['back', '##..##|##..##|##..##|##..##|.#..#.|.#..#.', [255, 176, 96]],
  servo_boosters: ['legs', '#....#|##..##|.####.|..##..|.####.|##..##', [255, 255, 255]],
  seismic_plates: ['legs', '......|......|######|######|#.#.#.|######', [230, 190, 40]],
  stabilisers: ['legs', '#....#|#....#|######|..##..|..##..|.####.', [200, 200, 220]],
};
function iconModule(id) {
  const [part, glyph, gc] = MODULES[id];
  const c = canvas(16, 16), pc = PART_COL[part];
  for (let y = 2; y <= 13; y++) for (let x = 2; x <= 13; x++) {
    if ((x === 2 || x === 13) && (y === 2 || y === 13)) continue;
    let col = (x === 2 || y === 2) ? [150, 156, 166] : (x === 13 || y === 13) ? [60, 64, 72] : [96, 102, 112];
    if (y === 3 && x > 3 && x < 12) col = pc;
    c.set(x, y, col);
  }
  glyph.split('|').forEach((r, y) => [...r].forEach((ch, x) => { if (ch === '#') c.set(5 + x, 6 + y, gc); }));
  outline(c, [20, 20, 24]);
  return c;
}
function chargerTex() {
  const side = canvas(16, 16), top = canvas(16, 16), front = canvas(16, 16), on = canvas(16, 16);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const edge = x === 0 || y === 0 || x === 15 || y === 15;
    const n = hash(x, y, 31) * 14;
    side.set(x, y, edge ? [50, 54, 60] : (y === 5 || y === 10) ? [70, 74, 82] : [104 + n, 110 + n, 120 + n]);
    top.set(x, y, edge ? [50, 54, 60] : Math.hypot(x - 7.5, y - 7.5) < 4 ? [60, 64, 70] : [120 + n, 126 + n, 136 + n]);
    if (Math.hypot(x - 7.5, y - 7.5) < 2.2) top.set(x, y, [80, 200, 255]);
    front.set(x, y, side.get(x, y));
    on.set(x, y, side.get(x, y));
  }
  for (let y = 3; y <= 9; y++) for (let x = 4; x <= 11; x++) { front.set(x, y, [24, 26, 30]); on.set(x, y, [24, 26, 30]); }
  for (let y = 4; y <= 8; y++) { front.set(7, y, [60, 70, 80]); front.set(8, y, [60, 70, 80]); on.set(7, y, [120, 240, 255]); on.set(8, y, [200, 255, 255]); }
  for (let x = 4; x <= 11; x++) { front.set(x, 12, [200, 60, 50]); on.set(x, 12, [255, 210, 60]); }
  return { side, top, front, on };
}

// ------------------------------------------------------------------------------------------------ data
const item = id => ({ item: id });
const shaped = (pattern, key, result, count = 1) => ({ type: 'minecraft:crafting_shaped', category: 'equipment', pattern, key, result: { id: result, count } });
const shapeless = (ings, result, count = 1) => ({ type: 'minecraft:crafting_shapeless', category: 'equipment', ingredients: ings.map(item), result: { id: result, count } });
const MAT = { iron: 'minecraft:iron_ingot', gold: 'minecraft:gold_ingot', hero_alloy: 'projecthero:hero_alloy_ingot', diamond: 'minecraft:diamond', vibranium: 'projecthero:vibranium_shard' };
const MODULE_RECIPES = {
  night_visor: [[' G ', 'TBT'], { G: 'minecraft:golden_carrot' }],
  ore_scanner: [[' C ', 'TBT'], { C: 'minecraft:compass' }],
  target_lock: [[' S ', 'ABA'], { S: 'minecraft:spyglass' }],
  rebreather: [[' P ', 'TBT'], { P: 'minecraft:pufferfish' }],
  reactive_plating: [['AGA', 'TBT'], { G: 'minecraft:gunpowder' }],
  capacitor_bank: [['CRC', 'CBC'], { C: 'minecraft:copper_ingot', R: 'minecraft:redstone_block' }],
  overclock: [['RGR', 'ABA'], { R: 'minecraft:redstone_block', G: 'minecraft:glowstone' }],
  solar_plate: [['DDD', 'TBT'], { D: 'minecraft:daylight_detector' }],
  breacher_plating: [['AAA', 'IBI'], { I: 'minecraft:iron_block' }],
  gatling: [['III', 'ABA'], { I: 'minecraft:iron_ingot' }],
  flamer: [['FFF', 'ABA'], { F: 'minecraft:fire_charge' }],
  tesla_coil: [['CLC', 'ABA'], { C: 'minecraft:copper_block', L: 'minecraft:lightning_rod' }],
  rail_cannon: [['VRV', 'ABA'], { V: 'projecthero:vibranium_shard', R: 'minecraft:powered_rail' }],
  power_fist: [['PPP', 'ABA'], { P: 'minecraft:piston' }],
  magnetic_clamp: [['IRI', 'TBT'], { I: 'minecraft:iron_block', R: 'minecraft:redstone' }],
  missile_pod: [['FFF', 'ABA'], { F: 'minecraft:firework_rocket' }],
  shield_projector: [['SGS', 'ABA'], { S: 'minecraft:shield', G: 'minecraft:glass' }],
  repair_drone: [['PTP', 'ABA'], { P: 'minecraft:phantom_membrane' }],
  sentry_turret: [['DTD', 'ABA'], { D: 'minecraft:dispenser' }],
  thruster_pack: [['RFR', 'ABA'], { R: 'minecraft:blaze_rod', F: 'minecraft:firework_rocket' }],
  servo_boosters: [['PSP', 'TBT'], { P: 'minecraft:piston', S: 'minecraft:sugar' }],
  seismic_plates: [['OOO', 'TBT'], { O: 'minecraft:obsidian' }],
  stabilisers: [['IHI', 'TBT'], { I: 'minecraft:iron_ingot', H: 'minecraft:heavy_weighted_pressure_plate' }],
};

function main() {
  buildFrame();
  TIERS.forEach(buildTier);
  buildModules();
  const tex = (rel, c) => write(path.join(A, 'textures', rel + '.png'), c.png());
  const itemModel = (id, texture) => json(path.join(A, 'models/item', id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + (texture || id) } });
  TIERS.forEach(t => {
    tex('item/power_armour_frame_' + t, iconFrame(t)); itemModel('power_armour_frame_' + t);
    tex('item/power_armour_plating_' + t, iconPlating(t)); itemModel('power_armour_plating_' + t);
  });
  tex('item/redstone_circuit_board', iconBoard()); itemModel('redstone_circuit_board');
  ['fusion_cell', 'arc_core', 'star_core'].forEach(k => { tex('item/' + k, iconCore(k)); itemModel(k); });
  Object.keys(MODULES).forEach(k => { tex('item/pa_module_' + k, iconModule(k)); itemModel('pa_module_' + k); });
  // the Core Charger
  const ch = chargerTex();
  tex('block/core_charger_side', ch.side); tex('block/core_charger_top', ch.top); tex('block/core_charger_front', ch.front); tex('block/core_charger_front_on', ch.on);
  json(path.join(A, 'models/block/core_charger.json'), { parent: 'minecraft:block/orientable', textures: { top: 'projecthero:block/core_charger_top', front: 'projecthero:block/core_charger_front', side: 'projecthero:block/core_charger_side' } });
  json(path.join(A, 'models/block/core_charger_on.json'), { parent: 'minecraft:block/orientable', textures: { top: 'projecthero:block/core_charger_top', front: 'projecthero:block/core_charger_front_on', side: 'projecthero:block/core_charger_side' } });
  json(path.join(A, 'models/item/core_charger.json'), { parent: 'projecthero:block/core_charger' });
  const variants = {};
  [['north', 0], ['east', 90], ['south', 180], ['west', 270]].forEach(([f, r]) => {
    variants[`facing=${f},lit=false`] = r ? { model: 'projecthero:block/core_charger', y: r } : { model: 'projecthero:block/core_charger' };
    variants[`facing=${f},lit=true`] = r ? { model: 'projecthero:block/core_charger_on', y: r } : { model: 'projecthero:block/core_charger_on' };
  });
  json(path.join(A, 'blockstates/core_charger.json'), { variants });
  json(path.join(D, 'loot_table/blocks/core_charger.json'), { type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:core_charger' }], conditions: [{ condition: 'minecraft:survives_explosion' }] }] });
  // pickaxe tag (merged, never overwritten)
  const tagFile = path.join(MC, 'tags/block/mineable/pickaxe.json');
  let tag = { replace: false, values: [] };
  if (fs.existsSync(tagFile)) tag = JSON.parse(fs.readFileSync(tagFile, 'utf8'));
  if (!tag.values.includes('projecthero:core_charger')) { tag.values.push('projecthero:core_charger'); json(tagFile, tag); }
  // recipes
  const R = path.join(D, 'recipe');
  json(path.join(R, 'redstone_circuit_board.json'), shaped(['RGR', 'CIC', 'RGR'], { R: item('minecraft:redstone'), G: item('minecraft:gold_nugget'), C: item('minecraft:copper_ingot'), I: item('minecraft:iron_ingot') }, 'projecthero:redstone_circuit_board'));
  Object.entries(MAT).forEach(([t, m]) => json(path.join(R, 'power_armour_plating_' + t + '.json'), shaped(['MM', 'MM'], { M: item(m) }, 'projecthero:power_armour_plating_' + t)));
  json(path.join(R, 'power_armour_plating_netherite.json'), shapeless(['minecraft:netherite_ingot', 'minecraft:diamond', 'minecraft:diamond'], 'projecthero:power_armour_plating_netherite'));
  json(path.join(R, 'power_armour_plating_celestial.json'), shapeless(['projecthero:celestial_core', 'projecthero:vibranium_shard', 'projecthero:vibranium_shard'], 'projecthero:power_armour_plating_celestial'));
  TIERS.forEach((t, i) => {
    json(path.join(R, 'power_armour_frame_' + t + '.json'), shaped(['PBP', 'P P', 'P P'], { P: item('projecthero:power_armour_plating_' + t), B: item('projecthero:redstone_circuit_board') }, 'projecthero:power_armour_frame_' + t));
    if (i > 0) {
      json(path.join(R, 'power_armour_frame_' + t + '_smithing.json'), {
        type: 'minecraft:smithing_transform', template: item('projecthero:redstone_circuit_board'),
        base: item('projecthero:power_armour_frame_' + TIERS[i - 1]), addition: item('projecthero:power_armour_plating_' + t),
        result: { id: 'projecthero:power_armour_frame_' + t, count: 1 },
      });
    }
  });
  json(path.join(R, 'fusion_cell.json'), shaped(['IRI', 'RGR', 'IRI'], { I: item('minecraft:iron_ingot'), R: item('minecraft:redstone'), G: item('minecraft:glowstone_dust') }, 'projecthero:fusion_cell'));
  json(path.join(R, 'arc_core.json'), shaped(['AQA', 'QFQ', 'AQA'], { A: item('projecthero:hero_alloy_ingot'), Q: item('minecraft:quartz'), F: item('projecthero:fusion_cell') }, 'projecthero:arc_core'));
  json(path.join(R, 'star_core.json'), shaped(['VGV', 'GCG', 'VAV'], { V: item('projecthero:vibranium_shard'), G: item('minecraft:glowstone_dust'), C: item('projecthero:celestial_core'), A: item('projecthero:arc_core') }, 'projecthero:star_core'));
  json(path.join(R, 'core_charger.json'), shaped(['III', 'RFR', 'ICI'], { I: item('minecraft:iron_ingot'), R: item('minecraft:redstone'), F: item('minecraft:furnace'), C: item('projecthero:redstone_circuit_board') }, 'projecthero:core_charger'));
  Object.entries(MODULE_RECIPES).forEach(([id, [pattern, key]]) => {
    const k = { B: item('projecthero:redstone_circuit_board') };
    if (pattern.join('').includes('T')) k.T = item('projecthero:salvaged_tech');
    if (pattern.join('').includes('A')) k.A = item('projecthero:hero_alloy_ingot');
    Object.entries(key).forEach(([c, v]) => { k[c] = item(v); });
    json(path.join(R, 'pa_module_' + id + '.json'), shaped(pattern, k, 'projecthero:pa_module_' + id));
  });
  console.log('power armour: layers for ' + TIERS.length + ' tiers, ' + Object.keys(MODULES).length + ' modules, icons, charger, recipes');
}
main();
