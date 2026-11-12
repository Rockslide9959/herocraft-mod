// v0.17.3 the Spin: the swatch atlas for Candidate A of the Ball Breaker model (client/spin/model/BallBreakerModel).
//   src/main/resources/assets/projecthero/textures/entity/spin/power/ball_breaker_model.png  (128x128, sixteen 32x32 swatches)
// Every primitive of the model maps one whole swatch, so each swatch is painted as a comic-shaded "material":
//   ROUND (ellipsoids / tubes: u = round the part, 0.5 = its front; v = top -> bottom) and PANEL (box faces) are lit from the
//   upper left with an inked rim and a hatched shadow side, like the manga / All-Star Battle colouring.
// Keep the swatch order in sync with BallBreakerModel.S_*.
// Usage: node scratchpad/spin/gen_ball_breaker_model_tex.js
const path = require('path');
const png = require('../pnglib.js');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/spin/power/ball_breaker_model.png');

const W = 128, H = 128, px = Buffer.alloc(W * H * 4);
const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t).concat([255]);
function rnd(x, y, s = 1) { let n = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967295; }
function set(x, y, c) { const i = (y * W + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = c[3] === undefined ? 255 : c[3]; }
// paint swatch k with fn(u, v, x, y) -> colour; u, v in 0..1 at texel centres
function swatch(k, fn) {
  const ox = (k % 4) * 32, oy = Math.floor(k / 4) * 32;
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) set(ox + x, oy + y, fn((x + 0.5) / 32, (y + 0.5) / 32, x, y));
}
// a 4-tone toon ramp: light in 0..1 -> ink / shadow / base / highlight
function ramp(tones, l) {
  const [ink, sh, base, mid, hi] = tones;
  if (l < 0.12) return ink;
  if (l < 0.38) return mix(sh, mid, (l - 0.12) / 0.26 * 0.25);
  if (l < 0.72) return mix(mid, base, (l - 0.38) / 0.34);
  if (l < 0.9) return base;
  return hi;
}

// palette (sampled from the All-Star Battle render: glossy yellow-lime, deep green shadows, magenta studs)
const LIME = [hex(0x15260A), hex(0x3F6B12), hex(0xB4DE2C), hex(0x7DAE1C), hex(0xF0FF9A)];
const LIME_DK = [hex(0x0E1A06), hex(0x274A0C), hex(0x6F9A18), hex(0x4C7812), hex(0xA9D03A)];
const GREY = [hex(0x141614), hex(0x2E322D), hex(0x80857D), hex(0x575B54), hex(0xB4B8B0)];
const PINK = [hex(0x3A0618), hex(0x7C0F3E), hex(0xE2367E), hex(0xB3205E), hex(0xFFB2D4)];
const SILVER = [hex(0x2A2E30), hex(0x7C8488), hex(0xE6ECEE), hex(0xB8C0C4), hex(0xFFFFFF)];

const S = { ROUND: 0, PANEL: 1, RIB: 2, STUD: 3, SILVER: 4, FACE: 5, DISC_TOP: 6, DISC_UNDER: 7, DARK: 8, CLAW: 9, ROUND_DK: 10, STALK: 11, BULB: 12, GLOW: 13, RIM: 14 };

// light of a round part at (u round, v down): lit from the front-left and above, dark at the sides / back (toon rim)
function roundLight(u, v) {
  const a = (u - 0.5) * Math.PI * 2;            // 0 = front
  const facing = Math.cos(a - (-0.45));         // key light from the figure's front-left
  const top = 1 - v * 0.55;
  return Math.max(0, Math.min(1, 0.3 + 0.55 * Math.max(0, facing * 0.8 + 0.2) * top + 0.2 * top));
}

// ROUND: tubes, domes, shoulder pads
swatch(S.ROUND, (u, v, x, y) => {
  let l = roundLight(u, v);
  // the glossy streak (a vertical band left of the front) and a hot spot near the top
  const streak = Math.abs(u - 0.41) < 0.035 && v > 0.12 && v < 0.82;
  if (streak) l = 0.95;
  let c = ramp(LIME, l);
  // cross-hatch in the core shadow (manga inking)
  if (l > 0.12 && l < 0.4 && ((x + y) % 4 === 0)) c = mix(c, LIME[0], 0.55);
  return mix(c, LIME[1], (rnd(x, y, 3) - 0.5) * 0.08);
});
swatch(S.ROUND_DK, (u, v, x, y) => {
  let l = roundLight(u, v);
  let c = ramp(LIME_DK, l);
  if (l > 0.12 && l < 0.4 && ((x + y) % 4 === 0)) c = mix(c, LIME_DK[0], 0.5);
  return c;
});

// PANEL: armour plates (box faces): inked border, lit upper-left, darker lower-right, a diagonal gloss stripe
swatch(S.PANEL, (u, v, x, y) => {
  if (x === 0 || y === 0 || x === 31 || y === 31) return LIME[0];
  let l = 0.85 - 0.55 * (u * 0.4 + v * 0.6);
  const d = u + v * 0.6;
  if (d > 0.28 && d < 0.36) l = 0.97;
  if (x === 1 || y === 1) l = Math.max(l, 0.93);
  if (x === 30 || y === 30) l = Math.min(l, 0.2);
  let c = ramp(LIME, l);
  if (l < 0.4 && (x - y + 64) % 4 === 0) c = mix(c, LIME[0], 0.5);
  return c;
});

// RIB: dark grey flexible hose, horizontal ridges (v along the hose); also lit round the hose like ROUND
swatch(S.RIB, (u, v, x, y) => {
  const lr = roundLight(u, 0.3);
  const k = y % 4;  // ridge profile: lit lip, face, shadow, groove
  const prof = [0.95, 0.62, 0.32, 0.06][k];
  const l = Math.max(0, Math.min(1, prof * (0.35 + 0.75 * lr)));
  let c = ramp(GREY, l);
  return mix(c, GREY[1], (rnd(x, y, 5) - 0.5) * 0.1);
});

// STUD: magenta gem, lit cap, specular dot up-left of its front
swatch(S.STUD, (u, v, x, y) => {
  let l = roundLight(u, v * 0.9 + 0.05) * 1.05;
  const du = (u - 0.42) * 2.2, dv = v - 0.32;
  if (du * du + dv * dv < 0.012) return PINK[4];
  if (du * du + dv * dv < 0.03) l = Math.max(l, 0.85);
  if (v > 0.93) l = 0.05;
  return ramp(PINK, l);
});
// BULB: paler pink (the manga's stalk bulbs)
swatch(S.BULB, (u, v, x, y) => {
  let l = roundLight(u, v);
  const du = (u - 0.42) * 2.2, dv = v - 0.3;
  if (du * du + dv * dv < 0.015) return hex(0xFFFFFF);
  return mix(ramp(PINK, l), hex(0xFFD0E4), 0.35);
});

// SILVER: the chest's zigzag trim (bright metal with a dark lower edge)
swatch(S.SILVER, (u, v, x, y) => {
  let l = 0.95 - v * 0.75;
  if (y < 3) l = 1;
  if (y > 27) l = 0.08;
  return ramp(SILVER, l);
});

// FACE: the smooth face shield (u across it, v top -> bottom of its U): glossy lime, brightest upper-left of centre with a soft
// vertical gloss, falling to deep green at the sides and the bottom, outlined by a dark inked seam with a thin lit lip inside it
// (as in the All-Star Battle render, where the shield reads as a smooth rounded mask)
swatch(S.FACE, (u, v, x, y) => {
  const e = Math.min(x, 31 - x, y, 31 - y);
  if (e === 0 || e === 1) return LIME[0];
  if (e === 2) return (y > 26 || x > 26) ? LIME[1] : LIME[3];
  const dx = (u - 0.42) * 1.6, dy = (v - 0.3) * 1.1;
  let l = 0.98 - (dx * dx + dy * dy) * 0.95;
  if (Math.abs(u - 0.33) < 0.03 && v > 0.12 && v < 0.7) l = 1;   // the gloss streak
  if (v > 0.82) l = Math.min(l, 0.42);                            // the lower edge rolls under into shadow
  let c = ramp(LIME, Math.max(0, Math.min(1, l)));
  if (l < 0.38 && (x + y) % 4 === 0) c = mix(c, LIME[0], 0.45);
  return mix(c, LIME[1], (rnd(x, y, 11) - 0.5) * 0.06);
});

// DISC_TOP: the saucer tops: planar u, v (centre 0.5, 0.5); bright lime dish with a lit ring and an inked rim
swatch(S.DISC_TOP, (u, v, x, y) => {
  const dx = u - 0.5, dy = v - 0.5, r = Math.sqrt(dx * dx + dy * dy) * 2;
  if (r > 0.94) return LIME[0];
  // light falls off toward the lower right; a bright crescent near the upper-left rim
  let l = 0.78 - (dx * 0.5 + dy * 0.6);
  if (r > 0.72 && r < 0.86 && dx + dy < 0) l = 1;
  if (r > 0.86) l = 0.3;
  if (r < 0.3) l += 0.06;
  let c = ramp(LIME, Math.max(0, Math.min(1, l)));
  if (l < 0.4 && (x + y) % 4 === 0) c = mix(c, LIME[0], 0.4);
  return c;
});
// DISC_UNDER: the dark underside
swatch(S.DISC_UNDER, (u, v, x, y) => {
  const dx = u - 0.5, dy = v - 0.5, r = Math.sqrt(dx * dx + dy * dy) * 2;
  if (r > 0.94) return LIME[1];
  return mix(hex(0x26420C), hex(0x0C1604), Math.min(1, r * 0.9 + (rnd(x, y, 7) - 0.5) * 0.1));
});
// RIM: disc edge band (lime, lighter top lip, inked bottom)
swatch(S.RIM, (u, v, x, y) => {
  const lr = roundLight(u, 0.2);
  let l = v < 0.25 ? 0.95 : (v > 0.8 ? 0.08 : 0.3 + 0.5 * lr);
  return ramp(LIME, l);
});

// DARK: the green-black under-suit showing at joints and the waist
swatch(S.DARK, (u, v, x, y) => {
  const lr = roundLight(u, v);
  return mix(hex(0x0C1606), hex(0x2F5212), lr * 0.9 + (rnd(x, y, 9) - 0.5) * 0.08);
});

// CLAW: lime finger tapering to a pale, inked point (v = 0 at the knuckle, 1 at the tip)
swatch(S.CLAW, (u, v, x, y) => {
  const l = roundLight(u, 0.3);
  let c = ramp(LIME, l);
  if (v > 0.7) c = mix(c, hex(0xF4FFD0), (v - 0.7) / 0.3 * (0.4 + 0.6 * l));
  if (y % 11 === 10) c = mix(c, LIME[0], 0.6);  // knuckle creases
  return c;
});

// STALK: the pale silver stems the face studs sit on (smooth, no banding along their length)
swatch(S.STALK, (u, v, x, y) => {
  const l = roundLight(u, 0.3);
  return mix(hex(0x6E7A76), hex(0xF4F8F2), Math.min(1, 0.15 + l));
});

// GLOW: soft white, for the additive golden-glow pass (tinted by vertex colour)
swatch(S.GLOW, (u, v, x, y) => [255, 255, 255, 255]);

png.write(OUT, W, H, px);
console.log('wrote', OUT);
