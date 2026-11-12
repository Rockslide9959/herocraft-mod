// v0.17.3 Beast Boy: the swatch atlas for the custom animal bodies (client/beastboy/model/BeastMesh).
//   src/main/resources/assets/projecthero/textures/entity/beastboy/art/beast_art.png  (256x256, 8x8 grid of 32x32 swatches)
// Every primitive maps one whole swatch. Mapping conventions (see BeastMesh):
//   loft (tubes):   u round the tube, 0 = top (back of the animal), 0.25 = left side, 0.5 = underside (belly); v along the tube
//   ellipsoid:      u round from the back (0) through the front (0.5); v = 0 top pole -> 1 bottom pole
//   ribbon / patch: u along, v across
// Painted like the 2003 Teen Titans show: flat cartoon greens, darker green stripes / spots, pale bellies, soft ink edges.
// Keep the swatch numbers in sync with BeastMesh.
// Usage: node scratchpad/beastboy/gen_beast_art_tex.js
const path = require('path');
const png = require('../pnglib.js');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/beastboy/art/beast_art.png');

const W = 256, H = 256, px = Buffer.alloc(W * H * 4);
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => { t = Math.max(0, Math.min(1, t)); return [0, 1, 2, 3].map(i => (a[i] === undefined ? 255 : a[i]) + ((b[i] === undefined ? 255 : b[i]) - (a[i] === undefined ? 255 : a[i])) * t); };
const frac = x => x - Math.floor(x);
const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
function rnd(x, y, s = 1) { let n = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967295; }
// smooth value noise in swatch texel space (wraps every 32 texels so swatches tile round tubes)
function vnoise(x, y, s, cell) {
  const n = 32 / cell;
  const gx = x / cell, gy = y / cell, x0 = Math.floor(gx), y0 = Math.floor(gy), fx = gx - x0, fy = gy - y0;
  const h = (i, j) => rnd(((i % n) + n) % n, ((j % n) + n) % n, s);
  const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
  return (h(x0, y0) * (1 - sx) + h(x0 + 1, y0) * sx) * (1 - sy) + (h(x0, y0 + 1) * (1 - sx) + h(x0 + 1, y0 + 1) * sx) * sy;
}
function set(x, y, c) { const i = (y * W + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = c[3] === undefined ? 255 : clamp(c[3]); }
function swatch(k, fn) {
  const ox = (k % 8) * 32, oy = Math.floor(k / 8) * 32;
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) set(ox + x, oy + y, fn((x + 0.5) / 32, (y + 0.5) / 32, x, y));
}

// Beast Boy greens
const INK = hex(0x0F2A0C), DARK = hex(0x2A6420), BASE = hex(0x4FA83A), LIGHT = hex(0x76C553), BELLY = hex(0xB4DE7C), HI = hex(0xD9F3A6);
const STRIPE = hex(0x2B6B22);
const FUR_DK = hex(0x173F14), FUR = hex(0x2F7426), FUR_HI = hex(0x4E9B3C);
const FUR_LT_DK = hex(0x3C8530), FUR_LT = hex(0x67B24C), FUR_LT_HI = hex(0x93D06D);

const S = {
  SKIN: 0, SKIN_DK: 1, BELLY: 2, COUNTER: 3, COUNTER_V: 4, STRIPED: 5, STRIPED_V: 6, SCALES: 7, WRINKLE: 8, WRINKLE_V: 9, FUR: 10, FUR_LT: 11,
  FACE: 12, DARK: 13, MOUTH: 14, TOOTH: 15, CLAW: 16, HORN: 17, EYE: 18, EYE_SLIT: 19, EYE_FIERCE: 20, MEMBRANE: 21, FLYWING: 22, TENTACLE: 23,
  GROOVE: 24, SPOT: 25, SPOT_V: 26, INNER_EAR: 27, MANE: 28, COMPOUND: 29, GLOW: 30, BLACK: 31, SCALES_V: 32, WRINKLE_LT: 33, SHARK: 34, FIN: 35,
};

const skinNoise = (x, y, s) => (vnoise(x, y, s, 8) - 0.5) * 0.22 + (vnoise(x, y, s + 7, 4) - 0.5) * 0.1;
function skin(x, y, base, s = 1) { const n = skinNoise(x, y, s); return n > 0 ? mix(base, LIGHT, n * 0.9) : mix(base, DARK, -n * 0.9); }

// countershade: t = 0 at the back / top, 1 at the belly. Back slightly darker, a soft ink seam, pale belly.
function counter(t, x, y, s = 1, edge = 0.6) {
  const n = (vnoise(x, y, s + 3, 8) - 0.5) * 0.07;
  if (t < edge - 0.04 + n) return skin(x, y, mix(DARK, BASE, 0.55 + t * 0.75), s);
  if (t < edge + n) return mix(BASE, INK, 0.35);
  return mix(BELLY, HI, (t - edge) * 0.8 + (rnd(x, y, s) - 0.5) * 0.06);
}
const tLoft = u => 1 - Math.abs(u - 0.5) * 2;   // loft u -> 0 top, 1 belly

swatch(S.SKIN, (u, v, x, y) => skin(x, y, BASE, 1));
swatch(S.SKIN_DK, (u, v, x, y) => skin(x, y, mix(DARK, BASE, 0.35), 2));
swatch(S.BELLY, (u, v, x, y) => mix(BELLY, HI, vnoise(x, y, 5, 8) * 0.5));
swatch(S.COUNTER, (u, v, x, y) => counter(tLoft(u), x, y, 3));
swatch(S.COUNTER_V, (u, v, x, y) => counter(v, x, y, 4));

// stripes over the back of a tube: one tapering dark band per v-repeat
swatch(S.STRIPED, (u, v, x, y) => {
  const t = tLoft(u);
  let c = counter(t, x, y, 5);
  const wv = 0.17 * (1 - t / 0.56) + (vnoise(x, y, 9, 4) - 0.5) * 0.06;
  if (t < 0.56 && Math.abs(v - 0.5) < wv) c = mix(STRIPE, INK, 0.15 + (rnd(x, y, 6) - 0.5) * 0.1);
  return c;
});
// stripes over an ellipsoid's top half, as bands round u
swatch(S.STRIPED_V, (u, v, x, y) => {
  let c = counter(v, x, y, 6);
  const f = frac(u * 6 + 0.08 * Math.sin(v * 9));
  const wv = 0.16 * (1 - v / 0.56);
  if (v < 0.56 && Math.abs(f - 0.5) < wv) c = mix(STRIPE, INK, 0.15);
  return c;
});

// snake scales: dark-edged diamonds over the back, broad belly scutes underneath
function scales(t, u, v, x, y, s) {
  let c = counter(t, x, y, s, 0.66);
  if (t < 0.6) {
    const du = Math.abs(frac(u * 8) - 0.5), dv = Math.abs(frac(v * 2) - 0.5);
    const d = du + dv;
    if (d > 0.42 && d < 0.5) c = mix(c, INK, 0.45);
    else if (d < 0.2 && t < 0.35) c = mix(STRIPE, INK, 0.2);
  } else if (t > 0.7 && frac(v * 2) < 0.12) c = mix(c, DARK, 0.45);
  return c;
}
swatch(S.SCALES, (u, v, x, y) => scales(tLoft(u), u, v, x, y, 7));
swatch(S.SCALES_V, (u, v, x, y) => scales(v, u, v * 1.5, x, y, 8));

// wrinkled hide (rhino, elephant): fainter countershade with crease lines across
function wrinkle(t, x, y, s, base) {
  let c = t < 0.68 ? skin(x, y, base, s) : mix(base, BELLY, 0.45 + (t - 0.68));
  const line = Math.sin((y + vnoise(x, y, s, 8) * 7) * 0.6);
  if (line > 0.93) c = mix(c, INK, 0.28);
  else if (line > 0.75) c = mix(c, DARK, 0.12);
  if (rnd(x, y, s + 4) > 0.94) c = mix(c, DARK, 0.25);
  return c;
}
swatch(S.WRINKLE, (u, v, x, y) => wrinkle(tLoft(u), x, y, 9, BASE));
swatch(S.WRINKLE_V, (u, v, x, y) => wrinkle(v, x, y, 10, BASE));
swatch(S.WRINKLE_LT, (u, v, x, y) => wrinkle(v * 0.5, x, y, 11, mix(BASE, LIGHT, 0.5)));

// fur: strands running along v
function fur(x, y, dk, base, hi, s) {
  // soft clumps with short, staggered strands (no long stripes, so tubes don't read as cucumbers)
  const clump = vnoise(x, y, s + 2, 8);
  let c = clump > 0.5 ? mix(base, hi, (clump - 0.5) * 0.7) : mix(base, dk, (0.5 - clump) * 0.7);
  const row = Math.floor((y + rnd(x, 0, s) * 6) / 6);
  const r = rnd(x, row, s + 5);
  if (r > 0.82) c = mix(c, hi, 0.35);
  else if (r < 0.14) c = mix(c, dk, 0.45);
  return c;
}
swatch(S.FUR, (u, v, x, y) => fur(x, y, FUR_DK, FUR, FUR_HI, 12));
swatch(S.FUR_LT, (u, v, x, y) => fur(x, y, FUR_LT_DK, FUR_LT, FUR_LT_HI, 13));
swatch(S.MANE, (u, v, x, y) => { const c = fur(x, y, hex(0x0E2C0C), hex(0x23601D), hex(0x3E8A32), 14); return y % 8 === 7 ? mix(c, INK, 0.3) : c; });
swatch(S.FACE, (u, v, x, y) => { const c = mix(hex(0x3F8436), hex(0x5DA54A), vnoise(x, y, 15, 8)); return rnd(x, y, 15) > 0.9 ? mix(c, DARK, 0.35) : c; });

swatch(S.DARK, (u, v, x, y) => mix(hex(0x0D260B), hex(0x1E4718), vnoise(x, y, 16, 8)));
swatch(S.MOUTH, (u, v, x, y) => mix(hex(0x3A1A1E), hex(0x6A3036), vnoise(x, y, 17, 8) * 0.8));
swatch(S.TOOTH, (u, v, x, y) => mix(hex(0xF4FFEC), hex(0xBFD8B0), v * 0.25 + (u > 0.85 || u < 0.15 ? 0.35 : 0)));
swatch(S.CLAW, (u, v, x, y) => { let c = mix(hex(0x9DBB86), hex(0xF2FBE8), v); if (u < 0.12 || u > 0.88) c = mix(c, hex(0x5E7A50), 0.5); return c; });
swatch(S.HORN, (u, v, x, y) => { let c = mix(hex(0xA9C487), hex(0xEEF6D8), v * 0.9 + (vnoise(x, y, 18, 4) - 0.5) * 0.15); if (frac(v * 5 + 0.3 * u) < 0.07) c = mix(c, hex(0x7E9A62), 0.6); return c; });

// eyes: ellipsoid mapping, the iris faces +Z (u 0.5, v 0.5)
function eye(u, v, iris, irisDk, pupilR, irisR, slit) {
  const du = (u - 0.5) * 2.3, dv = (v - 0.5) * 2.0, d = Math.hypot(du, dv);
  if (d > 0.82) return INK;
  if (d > irisR) return mix(hex(0xF4FFEE), hex(0xC8E8C0), smooth(irisR, 0.8, d));
  // highlight
  if (Math.hypot(du + irisR * 0.38, dv + irisR * 0.42) < irisR * 0.22) return hex(0xFFFFFF);
  const inPupil = slit ? (Math.abs(du) < pupilR * 0.4 && Math.abs(dv) < irisR * 0.92) : d < pupilR;
  if (inPupil) return hex(0x050A05);
  if (d > irisR - 0.04) return INK;
  return mix(iris, irisDk, d / irisR);
}
swatch(S.EYE, (u, v) => eye(u, v, hex(0x3FA237), hex(0x1E5C1A), 0.17, 0.36, false));
swatch(S.EYE_SLIT, (u, v) => eye(u, v, hex(0xC5E04A), hex(0x6E9A22), 0.14, 0.5, true));
swatch(S.EYE_FIERCE, (u, v) => eye(u, v, hex(0x9AF06A), hex(0x3FA237), 0.08, 0.22, false));

// wing membrane: veins fanning from the u = 0 edge (the arm) across, darker trailing edge
swatch(S.MEMBRANE, (u, v, x, y) => {
  let c = mix(hex(0x8CCF68), hex(0x6DB54E), v * 0.6 + (vnoise(x, y, 19, 8) - 0.5) * 0.2);
  const vein = frac(v * 4 + u * 0.6);
  if (vein < 0.06) c = mix(c, DARK, 0.45);
  if (v > 0.93) c = mix(c, DARK, 0.6);
  return c;
});
swatch(S.FLYWING, (u, v, x, y) => {
  let c = [200, 240, 190, 150];
  const vein = Math.min(frac(v * 3 + u * 0.4), frac(u * 2.5));
  if (vein < 0.05) c = [40, 90, 34, 210];
  if (v < 0.06 || v > 0.95) c = [40, 90, 34, 220];
  return c;
});
// tentacle: spotted green on top, two rows of pale suckers underneath
swatch(S.TENTACLE, (u, v, x, y) => {
  const t = tLoft(u);
  let c = counter(t, x, y, 20, 0.62);
  if (t < 0.55 && vnoise(x, y, 21, 4) > 0.7) c = mix(c, STRIPE, 0.7);
  if (t > 0.62) {
    for (const cu of [0.43, 0.57]) {
      const d = Math.hypot((u - cu) * 6, (frac(v * 2) - 0.5) * 2.2);
      if (d < 0.55) c = d < 0.28 ? mix(hex(0x87B866), INK, 0.2) : mix(hex(0xE4F6C4), hex(0xC8E6A4), d);
    }
  }
  return c;
});
// whale throat grooves along the belly
swatch(S.GROOVE, (u, v, x, y) => {
  const t = tLoft(u);
  let c = counter(t, x, y, 22, 0.55);
  if (t > 0.6 && frac(u * 18) < 0.18) c = mix(c, mix(BASE, DARK, 0.4), 0.6);
  return c;
});
// spots over the back
function spots(t, x, y, s, edge) {
  let c = counter(t, x, y, s, edge);
  if (t < edge - 0.05) { const n = vnoise(x, y, s + 9, 5); if (n > 0.68) c = mix(STRIPE, INK, n > 0.8 ? 0.2 : 0); else if (rnd(x, y, s) > 0.97) c = mix(c, LIGHT, 0.6); }
  return c;
}
swatch(S.SPOT, (u, v, x, y) => spots(tLoft(u), x, y, 23, 0.6));
swatch(S.SPOT_V, (u, v, x, y) => spots(v, x, y, 24, 0.62));
swatch(S.INNER_EAR, (u, v, x, y) => {
  const d = Math.hypot((u - 0.5) * 2.4, (v - 0.5) * 1.9);
  if (d < 0.62) return mix(hex(0xD6EBA6), hex(0xB7D98B), d / 0.62 + (vnoise(x, y, 25, 4) - 0.5) * 0.2);
  if (d < 0.7) return mix(BASE, INK, 0.3);
  return skin(x, y, BASE, 25);
});
// compound eye: hex cells
swatch(S.COMPOUND, (u, v, x, y) => {
  const row = Math.floor(y / 3), off = (row % 2) * 1.5;
  const cx = frac((x + off) / 3), cy = frac(y / 3);
  let c = mix(hex(0x1D5F26), hex(0x3E9A3A), vnoise(x, y, 26, 8));
  if (cx < 0.25 || cy < 0.25) c = mix(c, INK, 0.5);
  if (Math.hypot(u - 0.42, v - 0.32) < 0.12) c = mix(c, hex(0xE8FFD8), 0.6);
  return c;
});
swatch(S.GLOW, () => [255, 255, 255, 255]);
swatch(S.BLACK, (u, v, x, y) => mix(hex(0x050905), hex(0x15241A), u > 0.35 && u < 0.45 && v < 0.4 ? 1 : 0));
// shark: blue-ish deep green back, sharp countershade line, a few gill-slit marks drawn by the model instead
swatch(S.SHARK, (u, v, x, y) => {
  const t = tLoft(u);
  const n = (vnoise(x, y, 27, 8) - 0.5) * 0.08;
  if (t < 0.52 + n) return skin(x, y, mix(hex(0x2E7A3A), BASE, t * 1.2), 27);
  if (t < 0.56 + n) return mix(BASE, INK, 0.3);
  return mix(BELLY, HI, (t - 0.56));
});
// fins: darker toward the edge
swatch(S.FIN, (u, v, x, y) => { const t = tLoft(u); let c = skin(x, y, mix(hex(0x2E7A3A), BASE, 0.4), 28); if (v > 0.8) c = mix(c, DARK, (v - 0.8) * 3); return t > 0.6 ? mix(c, BELLY, 0.35) : c; });

png.write(OUT, W, H, px);
console.log('wrote', OUT);
