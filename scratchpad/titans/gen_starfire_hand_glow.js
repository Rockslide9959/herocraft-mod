// v0.17.3 tester round: Starfire's hand glow texture (StarfireGlowLayer.HAND_GLOW). 64x64 px = a 16x16 UV sheet at 4 px per unit:
// the wide hand box (4x4x4) at UV (0,0), the slim one (3x4x4) at UV (0,8). Each face: white at the centre fading to black at its
// edges (drawn additively, so black = nothing), so the glow shells have no hard square outline.
// Usage: node scratchpad/titans/gen_starfire_hand_glow.js
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');
const S = 4, W = 64, H = 64;
const px = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) px[i * 4 + 3] = 255; // opaque black
function box(u, v, w, h, d) {
  return [[u + d, v, w, d], [u + d + w, v, w, d], [u, v + d, d, h], [u + d, v + d, w, h], [u + d + w, v + d, d, h], [u + 2 * d + w, v + d, w, h]];
}
function face([u, v, w, h]) {
  const x0 = u * S, y0 = v * S, fw = w * S, fh = h * S;
  for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
    // 0 at the edge .. 1 at the centre (per axis): a rounded-rect falloff
    const dx = 1 - Math.abs((x + 0.5) / fw * 2 - 1), dy = 1 - Math.abs((y + 0.5) / fh * 2 - 1);
    const e = Math.min(dx, dy) * 0.6 + dx * dy * 0.4;
    const k = Math.pow(Math.max(0, Math.min(1, e * 1.25)), 1.35);
    const c = Math.round(255 * k);
    const i = ((y0 + y) * W + x0 + x) * 4;
    px[i] = px[i + 1] = px[i + 2] = c;
  }
}
box(0, 0, 4, 4, 4).forEach(face);
box(0, 8, 3, 4, 4).forEach(face);
const dir = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/starfire');
fs.mkdirSync(dir, { recursive: true });
const out = path.join(dir, 'hand_glow.png');
png.write(out, W, H, px);
console.log('wrote', out);
