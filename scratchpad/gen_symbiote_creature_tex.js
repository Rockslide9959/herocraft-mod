// Generates the free Symbiote's goo texture and the animated Symbiote Meteorite block texture.
// Run from the repo root: node scratchpad/gen_symbiote_creature_tex.js
const png = require('./pnglib.js');
const path = require('path');
const root = path.resolve(__dirname, '..');
let seed = 1337;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
function makeNoise(w, h, cell) {
  const gw = Math.ceil(w / cell), gh = Math.ceil(h / cell);
  const g = []; for (let i = 0; i < gw * gh; i++) g.push(rnd());
  const at = (x, y) => g[(((y % gh) + gh) % gh) * gw + (((x % gw) + gw) % gw)];
  return (x, y) => {
    const fx = x / cell, fy = y / cell; const x0 = Math.floor(fx), y0 = Math.floor(fy);
    const tx = fx - x0, ty = fy - y0; const sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
    const a = at(x0, y0), b = at(x0 + 1, y0), c = at(x0, y0 + 1), d = at(x0 + 1, y0 + 1);
    return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy;
  };
}
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

// ---- entity goo texture: 64 x 32, u = around the blob, v = centre -> rim ----
{
  const W = 64, H = 32; const data = Buffer.alloc(W * H * 4);
  const n1 = makeNoise(W, H, 8), n2 = makeNoise(W, H, 4), n3 = makeNoise(W, H, 16);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const o = (y * W + x) * 4;
    const base = 14 + n1(x, y) * 14 + n2(x, y) * 7;
    const streak = Math.max(0, Math.sin((x / W) * Math.PI * 10 + n3(x, y) * 6) - 0.75) * 4;
    const vein = Math.max(0, 1 - Math.abs(n3(x, y) - 0.5) * 14);
    let r = base * 0.9 + vein * 22 + streak * 10, g = base * 0.75 + vein * 6 + streak * 4, b = base * 1.15 + vein * 34 + streak * 16;
    if (rnd() < 0.015) { r += 55; g += 50; b += 70; }
    // swatches the renderer samples for its wet highlights (see SymbioteEntityRenderer.SHEEN_*):
    // x 60..63 / y 0..3 = near-white, y 4..7 = violet sheen
    if (x >= 60 && y < 4) { r = 240; g = 236; b = 250; }
    else if (x >= 60 && y < 8) { r = 176; g = 120; b = 236; }
    data[o] = clamp(r); data[o + 1] = clamp(g); data[o + 2] = clamp(b); data[o + 3] = 255;
  }
  png.write(path.join(root, 'src/main/resources/assets/projecthero/textures/entity/symbiote_goo.png'), { w: W, h: H, data });
}

// ---- meteorite block: 16 x 16, 8 animated frames stacked vertically (pulsing violet veins) ----
{
  const S = 16, F = 8; const data = Buffer.alloc(S * S * F * 4);
  const n1 = makeNoise(S, S, 4), n2 = makeNoise(S, S, 2);
  const stone = [], vein = [];
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    let v = 22 + n1(x, y) * 26 + n2(x, y) * 12;
    if (rnd() < 0.08) v += 18;
    if (rnd() < 0.06) v -= 10;
    stone.push(v);
    vein.push(0);
  }
  // wandering cracks: a few random walks that branch, wrapping so the block tiles seamlessly
  const mark = (x, y, k) => { const i = (((y % S) + S) % S) * S + (((x % S) + S) % S); vein[i] = Math.max(vein[i], k); };
  const walk = (x, y, dx, dy, len) => {
    for (let s = 0; s < len; s++) {
      mark(x, y, 1);
      if (rnd() < 0.35) mark(x + (dy !== 0 ? 1 : 0), y + (dx !== 0 ? 1 : 0), 0.4);
      if (rnd() < 0.3) { const t = dx; dx = rnd() < 0.5 ? dy : -dy; dy = rnd() < 0.5 ? t : -t; if (dx === 0 && dy === 0) dx = 1; }
      if (rnd() < 0.5) { x += dx; } else { y += dy || (rnd() < 0.5 ? 1 : -1); }
      if (s === 4 && rnd() < 0.6) walk(x, y, -dy || 1, dx || 1, 4 + Math.floor(rnd() * 4));
    }
  };
  walk(2, 3, 1, 1, 14);
  walk(12, 1, -1, 1, 12);
  walk(6, 13, 1, -1, 9);
  walk(14, 8, -1, 0, 7);
  walk(4, 7, 1, -1, 5);
  for (let f = 0; f < F; f++) {
    const pulse = 0.55 + 0.45 * Math.sin((f / F) * Math.PI * 2);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const i = y * S + x; const o = ((f * S + y) * S + x) * 4;
      const v = stone[i];
      const edge = (x === 0 || y === 0) ? 6 : (x === S - 1 || y === S - 1) ? -6 : 0;
      let r = v * 0.95 + edge, g = v * 0.9 + edge, b = v * 1.08 + edge;
      const k = vein[i];
      if (k > 0) {
        const glow = k * (0.35 + 0.65 * pulse);
        r = r * (1 - glow) + (95 + 70 * pulse) * glow;
        g = g * (1 - glow) + (20 + 25 * pulse) * glow;
        b = b * (1 - glow) + (150 + 90 * pulse) * glow;
      }
      data[o] = clamp(r); data[o + 1] = clamp(g); data[o + 2] = clamp(b); data[o + 3] = 255;
    }
  }
  png.write(path.join(root, 'src/main/resources/assets/projecthero/textures/block/symbiote_meteorite.png'), { w: S, h: S * F, data });
  // a 4x upscaled preview of frame 0..7 side by side, for eyeballing
  const pv = { w: S * F, h: S, data: Buffer.alloc(S * F * S * 4) };
  for (let f = 0; f < F; f++) for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const s = ((f * S + y) * S + x) * 4, o = (y * S * F + f * S + x) * 4;
    data.copy(pv.data, o, s, s + 4);
  }
  if (process.argv[2]) png.write(process.argv[2], png.upscale(pv, 6));
}
console.log('ok');
