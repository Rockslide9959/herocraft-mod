// v0.17.3 hq round: the land mammals' box specs (see gen_land_mammals_tex.js for the conventions). Friendly coords: +Y up, +Z front,
// +X the animal's left, ground y = 0, 1 unit = 1/32 block. Bone names are what the Java animators look up.
const G = require('./gen_land_mammals_tex.js');
const { bone, box, foreLeg, hindLeg, tailChain, stdPainters, hex, mix, mul, smooth, rnd, vnoise3, furColour, furDetail, shadeT, SPECS } = G;

// common painters for heads: muzzle (pale, a dark lip line, the mouth roof underneath) and jaw (pale, tongue + mouth on top)
function headPainters(P, pal) {
  return {
    muzzle: ctx => {
      if (ctx.f === 'bottom') return P.mouth(ctx);
      if ((ctx.f === 'left' || ctx.f === 'right' || ctx.f === 'front') && ctx.r === ctx.fh - 1) return pal.ink;
      return P.light(ctx);
    },
    jaw: ctx => {
      if (ctx.f === 'top') return P.mouth({ ...ctx, b: { ...ctx.b, tongue: true } });
      return P.light(ctx);
    },
  };
}

// =============================================================================================================== WOLF
SPECS.Wolf = () => {
  const pal = {
    ink: hex(0x123018), back: hex(0x2A672A), flank: hex(0x4C9D3E), flankHi: hex(0x6BBB51), belly: hex(0xBEE19A), bellyHi: hex(0xDAF0BB), seam: 0.66,
    iris: hex(0x8BE04F), irisDk: hex(0x3E9A2C),
    // a darker saddle stripe down the spine
    pattern: (ctx, t) => (ctx.f === 'top' && Math.abs(ctx.P[0]) < 2.2 && !ctx.b.noSaddle ? { c: hex(0x1F521F), k: 0.55 } : null),
  };
  const P = stdPainters(pal);
  Object.assign(P, headPainters(P, pal));
  const legBand = [20, -6];
  const eye = (side) => box([side < 0 ? -4.7 : 1.7, 0.5, 5.6], [3, 3, 1], 'eye', { side });
  const lid = (side) => bone(side < 0 ? 'lidR' : 'lidL', [side * 3.2, 3.7, 6.75], [box([-1.5, -3.1, -0.85], [3, 3, 1], 'lid', { g: 0.08 })]);
  const ear = (side) => bone(side < 0 ? 'earR' : 'earL', [side * 3.1, 4, 0.5], [
    box([-2, 0, -0.5], [4, 2, 1], 'innerEar'),
    box([-1.5, 2, -0.5], [3, 2, 1], 'innerEar'),
    box([-1, 4, -0.5], [2, 1, 1], 'fur'),
    box([-0.5, 5, -0.5], [1, 1, 1], 'fur'),
  ]);
  const head = bone('head', [0, 3, 5], [
    box([-5, -4, -2], [10, 8, 8], 'fur', { band: [28, 20], noSaddle: true }),        // skull
    box([-2.5, -4, 6], [5, 4, 7], 'muzzle', { band: [24, 20] }),                    // muzzle
    box([-1.5, 0, 6], [3, 1, 6], 'fur', { band: [25, 22], noSaddle: true }),         // the bridge of the nose
    box([-1.5, -1, 12.5], [3, 2, 1], 'nose'),
    box([-6, -6, -1], [12, 6, 5], 'ruff', { band: [24, 17] }),                       // cheek ruff
    box([-2.2, -5, 11], [1, 1, 1], 'tooth'), box([1.2, -5, 11], [1, 1, 1], 'tooth'), // upper fangs
    eye(-1), eye(1),
  ], [
    lid(-1), lid(1), ear(-1), ear(1),
    bone('jaw', [0, -4, 4], [
      box([-2, -2, 0], [4, 2, 8], 'jaw', { band: [21, 17] }),
      box([-1.8, 0, 6.5], [1, 1, 1], 'tooth'), box([0.8, 0, 6.5], [1, 1, 1], 'tooth'),
    ], [bone('tongue', [0, 0, 5], [box([-1, -1, -1], [2, 1, 4], 'tongue')])]),
  ]);
  const neck = bone('neck', [0, 3, 11], [box([-3.5, -4, -2], [7, 8, 8], 'shag', { band: [25, 15] })], [head]);
  const chest = bone('chest', [0, 0, -1], [
    box([-5, -7, -1], [10, 13, 13], 'fur', { band: [24, 11] }),
    box([-4, -9, 11], [8, 11, 3], 'ruff', { band: [20, 9] }),
    box([-5.5, -1, 5], [11, 9, 8], 'shag', { band: [26, 16] }),
  ], [
    neck,
    foreLeg('LF', 1, [3.2, -2.5, 8.5], [6, 5, 3], [4, 5, 3, 3, 3, 4, 5], { band: legBand, claws: 3 }),
    foreLeg('RF', -1, [-3.2, -2.5, 8.5], [6, 5, 3], [4, 5, 3, 3, 3, 4, 5], { band: legBand, claws: 3 }),
  ]);
  const hips = bone('hips', [0, 0, -1], [
    box([-4.5, -5, -14], [9, 10, 14], 'fur', { band: [23, 13] }),
  ], [
    hindLeg('LH', 1, [3, -1, -9], [6, 6, 4], [4, 6, 3, 3, 2, 4, 5], { band: legBand, claws: 3 }),
    hindLeg('RH', -1, [-3, -1, -9], [6, 6, 4], [4, 6, 3, 3, 2, 4, 5], { band: legBand, claws: 3 }),
    tailChain('tail', [0, 3.5, -13.5], [[3, 3], [4, 4], [5, 5], [5, 5], [4, 4], [3, 3]], 3, ['fur', 'fur', 'fur', 'fur', 'fur', 'light']),
  ]);
  return { name: 'Wolf', tex: 'wolf', paint: P, root: bone('body', [0, 18, 0], [], [chest, hips]) };
};

// =============================================================================================================== CHEETAH
// solid round spots on a jittered 3D grid (fewer on the belly), smaller on the head and legs
function spots(P, cell, rad, seed) {
  const cx = Math.floor(P[0] / cell), cy = Math.floor(P[1] / cell), cz = Math.floor(P[2] / cell);
  let best = 1e9;
  for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) for (let k = -1; k <= 1; k++) {
    const x = cx + i, y = cy + j, z = cz + k;
    const q = [(x + 0.2 + rnd(x, y, z, seed) * 0.6) * cell, (y + 0.2 + rnd(x, y, z, seed + 1) * 0.6) * cell, (z + 0.2 + rnd(x, y, z, seed + 2) * 0.6) * cell];
    const r = rad * (0.75 + rnd(x, y, z, seed + 3) * 0.5);
    best = Math.min(best, Math.hypot(P[0] - q[0], P[1] - q[1], P[2] - q[2]) / r);
  }
  return best;
}
SPECS.Cheetah = () => {
  const spot = hex(0x1C4419);
  const pal = {
    ink: hex(0x17351A), back: hex(0x5A9A31), flank: hex(0x8CC954), flankHi: hex(0xA6D96C), belly: hex(0xE2F3BE), bellyHi: hex(0xF1FADF), seam: 0.7,
    iris: hex(0xB6EC5A), irisDk: hex(0x5DA530),
    pattern: (ctx, t) => {
      if (t > 0.74 || ctx.b.noSpots) return null;
      const fine = ctx.b.fine ? 0.62 : 1;
      return spots(ctx.P, 3.4 * fine, 1.05 * fine, 31) < 1 ? { c: spot, k: 0.92 } : null;
    },
  };
  pal.lightPattern = (ctx, t) => (ctx.b.spotsOnLight && spots(ctx.P, 2.2, 0.62, 33) < 1 ? { c: spot, k: 0.75 } : null);
  const P = stdPainters(pal);
  Object.assign(P, headPainters(P, pal));
  // the tail: spotted, then dark rings toward the end and a dark tip
  P.tailRing = ctx => {
    const z = ctx.P[2];
    const base = P.fur({ ...ctx, b: { ...ctx.b, fine: true } });
    if (z < -36 && (Math.floor((-z - 36) / 2.2) % 2 === 0 || z < -44)) return furDetail(mix(spot, pal.ink, 0.3), ctx, 41, 0.4);
    return base;
  };
  const legBand = [22, 0];
  const legOpt = { band: legBand, claws: 0 };
  const eye = side => box([side < 0 ? -3.4 : 0.4, 0, 3.6], [3, 2, 1], 'eye', { side });
  const lid = side => bone(side < 0 ? 'lidR' : 'lidL', [side * 1.9, 2.15, 4.75], [box([-1.5, -2.1, -0.85], [3, 2, 1], 'lid', { g: 0.08 })]);
  const ear = side => bone(side < 0 ? 'earR' : 'earL', [side * 2.4, 3, -0.5], [box([-1, 0, -0.5], [2, 2, 1], 'innerEar'), box([-0.5, 2, -0.5], [1, 1, 1], 'fur')]);
  const head = bone('head', [0, 2, 4], [
    box([-3.5, -3, -2], [7, 6, 6], 'fur', { band: [24, 18], fine: true }),
    box([-2, -3, 4], [4, 3, 3], 'muzzle', { band: [21, 18] }),
    box([-1, -1, 7], [2, 1, 1], 'nose'),
    box([-4, -3.5, -1], [8, 3, 3], 'ruff', { band: [20, 16] }),
    box([-1.4, -4, 5.8], [1, 1, 1], 'tooth'), box([0.4, -4, 5.8], [1, 1, 1], 'tooth'),
    eye(-1), eye(1),
  ], [lid(-1), lid(1), ear(-1), ear(1),
    bone('jaw', [0, -3, 3.5], [box([-1.5, -1, 0], [3, 1, 3], 'jaw', { band: [18, 16] })])]);
  const neck = bone('neck', [0, 2, 10], [box([-2.5, -4, -2], [5, 6, 6], 'fur', { band: [21, 15], fine: true })], [head]);
  const chest = bone('chest', [0, 0, 0], [
    box([-3.5, -6, -1], [7, 10, 12], 'fur', { band: [21, 11] }),
    box([-4, 0, 4], [8, 5, 6], 'fur', { band: [22, 17] }),
    box([-3, -6.5, 9], [6, 7, 3], 'light', { band: [16, 10], spotsOnLight: true }),
  ], [
    neck,
    foreLeg('LF', 1, [2.6, -3, 7], [5, 5, 3], [3, 4, 2, 2, 2, 3, 4], { ...legOpt, upperPaint: 'fur' }),
    foreLeg('RF', -1, [-2.6, -3, 7], [5, 5, 3], [3, 4, 2, 2, 2, 3, 4], { ...legOpt }),
  ]);
  const hips = bone('hips', [0, 0, -1], [
    box([-3, -3, -9], [6, 6, 9], 'fur', { band: [20, 14] }),
    box([-3.5, -4, -17], [7, 8, 9], 'fur', { band: [21, 13] }),
  ], [
    hindLeg('LH', 1, [2.6, -1, -13], [5, 6, 5], [4, 6, 2, 3, 2, 3, 4], { ...legOpt }),
    hindLeg('RH', -1, [-2.6, -1, -13], [5, 6, 5], [4, 6, 2, 3, 2, 3, 4], { ...legOpt }),
    tailChain('tail', [0, 3, -17], [[3, 3], [2, 2], [2, 2], [2, 2], [2, 2], [2, 2], [2, 2], [2, 2]], 3, ['tailRing']),
  ]);
  // the tear marks: inner eye corner down the side of the muzzle to the lip
  const decals = [-1, 1].map(s => ({ a: [s * 0.9, 21.3, 18.05], b: [s * 2.0, 18.2, 21.05], r: 0.75, c: pal.ink, k: 0.95 }));
  return { name: 'Cheetah', tex: 'cheetah', paint: P, decals, root: bone('body', [0, 17, 0], [], [chest, hips]) };
};

// =============================================================================================================== TIGER
// stripes: bands across the body (by z) that taper down the flanks, wobble and fork; across the legs (by y); rings on the tail
function stripe(P, t, period, width, seed, along = 2) {
  const w = (vnoise3(P[0] * 0.25, P[1] * 0.25, P[2] * 0.25, seed) - 0.5) * 1.6;
  const coord = (along === 2 ? P[2] + P[1] * 0.35 : P[1] + P[2] * 0.15) + w;
  const f = ((coord / period) % 1 + 1) % 1;
  const half = width * (1 - t * 1.05) / period;
  const idx = Math.floor(coord / period);
  // every other stripe is shorter (stops higher up the flank) and some fork into two thin ones
  if (idx % 2 && t > 0.42) return false;
  if (rnd(idx, 0, 0, seed + 4) > 0.6 && t > 0.25 && Math.abs(f - 0.5) < half * 0.35) return false;
  return Math.abs(f - 0.5) < half;
}
SPECS.Tiger = () => {
  const ink = hex(0x112A10), strk = hex(0x15331A);
  const pal = {
    ink, back: hex(0x3E8C2E), flank: hex(0x6FC04A), flankHi: hex(0x8ED066), belly: hex(0xDFF2C3), bellyHi: hex(0xF0F9E2), seam: 0.66,
    iris: hex(0xC9F06A), irisDk: hex(0x6AAA32),
    pattern: (ctx, t) => {
      if (ctx.b.noStripes || t > 0.86) return null;
      const leg = ctx.b.leg;
      const ok = leg ? stripe(ctx.P, Math.min(0.7, t * 0.6), 4.2, 0.9, 51, 1) : stripe(ctx.P, t, ctx.b.tight ? 3.4 : 4.6, 1.15, 52);
      return ok ? { c: strk, k: 0.95 } : null;
    },
    patternOnShag: true,
  };
  pal.lightPattern = (ctx, t) => (ctx.b.stripesOnLight && stripe(ctx.P, 0.3, 3.4, 0.7, 53) ? { c: strk, k: 0.85 } : null);
  const P = stdPainters(pal);
  Object.assign(P, headPainters(P, pal));
  P.tailT = ctx => {
    const z = ctx.P[2];
    if (z < -48) return furDetail(mix(strk, ink, 0.2), ctx, 61, 0.4);
    const c = P.fur(ctx);
    return (Math.floor((-z) / 2.2) % 3 === 0) ? furDetail(mix(strk, ink, 0.2), ctx, 62, 0.4) : c;
  };
  // round ears with the white spot on the back
  P.tigerEar = ctx => {
    if (ctx.f === 'back' && ctx.r >= 1 && ctx.c >= 1 && ctx.c < ctx.fw - 1) return hex(0xEEF8E0);
    if (ctx.f === 'back') return pal.ink;
    return P.innerEar(ctx);
  };
  const legOpt = (side) => ({ band: [27, 0], claws: 4, side });
  const eye = side => box([side < 0 ? -4.8 : 1.8, 1, 6.6], [3, 3, 1], 'eye', { side });
  const lid = side => bone(side < 0 ? 'lidR' : 'lidL', [side * 3.3, 4.2, 7.75], [box([-1.5, -3.1, -0.85], [3, 3, 1], 'lid', { g: 0.08 })]);
  const ear = side => bone(side < 0 ? 'earR' : 'earL', [side * 3.5, 5, 0], [box([-1.5, 0, -0.5], [3, 2, 1], 'tigerEar'), box([-1, 2, -0.5], [2, 1, 1], 'tigerEar')]);
  const head = bone('head', [0, 1, 6], [
    box([-5, -4, -2], [10, 9, 9], 'fur', { band: [29, 20], tight: true }),
    box([-3, -5, 7], [6, 5, 5], 'muzzle', { band: [24, 19] }),
    box([-2, 0, 7], [4, 1, 4], 'fur', { band: [25, 22], noStripes: true }),
    box([-1.5, -1, 12], [3, 2, 1], 'nose'),
    box([-6.5, -5, -1], [13, 6, 6], 'ruff', { band: [25, 18] }),
    box([-2.4, -6, 10.5], [1, 2, 1], 'tooth'), box([1.4, -6, 10.5], [1, 2, 1], 'tooth'),
    eye(-1), eye(1),
  ], [lid(-1), lid(1), ear(-1), ear(1),
    bone('jaw', [0, -4, 6], [box([-2.5, -2, 0], [5, 2, 6], 'jaw', { band: [20, 17] }),
      box([-2, 0, 4.6], [1, 1, 1], 'tooth'), box([1, 0, 4.6], [1, 1, 1], 'tooth')])]);
  const neck = bone('neck', [0, 1, 12], [box([-4, -5, -1], [8, 9, 8], 'fur', { band: [27, 18], tight: true })], [head]);
  const chest = bone('chest', [0, 0, 0], [
    box([-5.5, -9, -2], [11, 14, 15], 'fur', { band: [27, 13] }),
    box([-6, 0, 4], [12, 6, 8], 'fur', { band: [28, 22] }),
    box([-4.5, -9.5, 11], [9, 9, 3], 'light', { band: [20, 12], stripesOnLight: true }),
  ], [
    neck,
    foreLeg('LF', 1, [4, -3.5, 8], [7, 7, 3], [5, 6, 4, 4, 4, 5, 6], { ...legOpt(1), upperPaint: 'fur' }),
    foreLeg('RF', -1, [-4, -3.5, 8], [7, 7, 3], [5, 6, 4, 4, 4, 5, 6], { ...legOpt(-1) }),
  ]);
  const hips = bone('hips', [0, 0, -2], [
    box([-5, -7, -12], [10, 11, 13], 'fur', { band: [26, 15] }),
    box([-5.5, -7, -20], [11, 12, 9], 'fur', { band: [27, 15] }),
  ], [
    hindLeg('LH', 1, [4, -2, -15], [7, 7, 5], [5, 8, 4, 4, 3, 5, 6], { ...legOpt(1) }),
    hindLeg('RH', -1, [-4, -2, -15], [7, 7, 5], [5, 8, 4, 4, 3, 5, 6], { ...legOpt(-1) }),
    tailChain('tail', [0, 4, -21], [[4, 4], [3, 3], [3, 3], [3, 3], [3, 3], [3, 3], [3, 3]], 4, ['tailT']),
  ]);
  // leg boxes stripe across (by height)
  (function mark(b) { if (/^(upper|fore|wrist|thigh|shin|meta)/.test(b.name)) b.boxes.forEach(x => (x.leg = true)); b.children.forEach(mark); })(chest);
  (function mark(b) { if (/^(upper|fore|wrist|thigh|shin|meta)/.test(b.name)) b.boxes.forEach(x => (x.leg = true)); b.children.forEach(mark); })(hips);
  // face: dark lines from the eyes back over the cheeks, and the forehead's stripes
  const decals = [];
  for (const s of [-1, 1]) {
    decals.push({ a: [s * 5, 26, 21], b: [s * 6.6, 22.5, 17], r: 0.6, c: strk, k: 0.9 });
    decals.push({ a: [s * 6.6, 24, 20.5], b: [s * 6.6, 21, 16.5], r: 0.55, c: strk, k: 0.9 });
    decals.push({ a: [s * 1.2, 29, 22], b: [s * 2.6, 29, 17], r: 0.55, c: strk, k: 0.9 });
  }
  decals.push({ a: [0, 29, 23], b: [0, 29, 19], r: 0.5, c: strk, k: 0.9 });
  return { name: 'Tiger', tex: 'tiger', paint: P, decals, root: bone('body', [0, 22, 0], [], [chest, hips]) };
};

// =============================================================================================================== BEAR
SPECS.Bear = () => {
  const pal = {
    ink: hex(0x0E2410), back: hex(0x255A24), flank: hex(0x3F8834), flankHi: hex(0x55A043), belly: hex(0x7DB862), bellyHi: hex(0x93C878), seam: 0.74,
    iris: hex(0x9BE05A), irisDk: hex(0x3E8A2C), pawFur: hex(0x3F8334),
  };
  const P = stdPainters(pal);
  Object.assign(P, headPainters(P, pal));
  // tan-green muzzle; a pale crescent on the chest
  P.light = ctx => {
    const t = shadeT(ctx);
    return furDetail(mix(hex(0x8DC46C), hex(0xB4DA92), smooth(0, 1, t)), ctx, 71, 0.7);
  };
  const furBase = P.fur;
  P.fur = ctx => {
    if (ctx.b.crescent && ctx.f === 'front') {
      const x = Math.abs(ctx.P[0]), y = ctx.P[1];
      const yc = 30 - x * 0.55;
      if (y < yc && y > yc - 2.2 && x < 7) return furDetail(hex(0xB9DC98), ctx, 72, 0.6);
    }
    return furBase(ctx);
  };
  const legOpt = (side) => ({ band: [42, 0], claws: 4, pawH: 3, side });
  const eye = side => box([side < 0 ? -4.6 : 2.6, 0.5, 9.6], [2, 2, 1], 'eye', { side });
  const lid = side => bone(side < 0 ? 'lidR' : 'lidL', [side * 3.6, 2.65, 10.75], [box([-1, -2.1, -0.85], [2, 2, 1], 'lid', { g: 0.08 })]);
  const ear = side => bone(side < 0 ? 'earR' : 'earL', [side * 5, 5, 1], [box([-1.5, 0, -1], [3, 3, 2], 'innerEar'), box([-1, 3, -0.5], [2, 1, 1], 'fur')]);
  const head = bone('head', [0, 0, 6], [
    box([-7, -7, -2], [14, 12, 12], 'fur', { band: [37, 25] }),
    box([-4, -7, 10], [8, 6, 8], 'muzzle', { band: [31, 25] }),
    box([-3, -1, 10], [6, 1, 5], 'fur', { band: [32, 29] }),
    box([-2.5, -3, 18], [5, 3, 1], 'nose'),
    box([-8, -8, 0], [16, 8, 7], 'shag', { band: [34, 24] }),
    box([-3.2, -9, 16], [1, 2, 1], 'tooth'), box([2.2, -9, 16], [1, 2, 1], 'tooth'),
    // heavy brow
    box([-5.5, 2.5, 9.5], [11, 2, 1], 'fur', { band: [35, 33] }),
    eye(-1), eye(1),
  ], [lid(-1), lid(1), ear(-1), ear(1),
    bone('jaw', [0, -7, 9], [box([-3.5, -3, 0], [7, 3, 8], 'jaw', { band: [25, 22] }),
      box([-3, 0, 6.5], [1, 1, 1], 'tooth'), box([2, 0, 6.5], [1, 1, 1], 'tooth')])]);
  const neck = bone('neck', [0, 2, 13], [box([-6, -7, -3], [12, 12, 10], 'shag', { band: [37, 25] })], [head]);
  const chest = bone('chest', [0, 0, 0], [
    box([-9, -12, -6], [18, 22, 20], 'fur', { band: [40, 18], crescent: true }),
    box([-8, 6, 0], [16, 8, 12], 'shag', { band: [44, 36] }),
  ], [
    neck,
    foreLeg('LF', 1, [6, -2, 7], [12, 10, 3], [7, 9, 6, 6, 6, 8, 9], { ...legOpt(1), upperPaint: 'shag' }),
    foreLeg('RF', -1, [-6, -2, 7], [12, 10, 3], [7, 9, 6, 6, 6, 8, 9], { ...legOpt(-1), upperPaint: 'shag' }),
  ]);
  const hips = bone('hips', [0, 0, -5], [
    box([-8.5, -11, -23], [17, 19, 23], 'fur', { band: [38, 19] }),
  ], [
    hindLeg('LH', 1, [6, -4, -17], [11, 9, 4], [8, 11, 6, 6, 5, 7, 8], { ...legOpt(1), thighPaint: 'shag' }),
    hindLeg('RH', -1, [-6, -4, -17], [11, 9, 4], [8, 11, 6, 6, 5, 7, 8], { ...legOpt(-1), thighPaint: 'shag' }),
    bone('tail0', [0, 4, -23], [box([-2, -1, -4], [4, 4, 5], 'shag')]),
  ]);
  const belly = box([-7, -14, -20], [14, 4, 28], 'shag', { band: [20, 16] });
  return { name: 'Bear', tex: 'bear', paint: P, root: bone('body', [0, 30, 0], [belly], [chest, hips]) };
};

// =============================================================================================================== MOUSE
SPECS.Mouse = () => {
  const pal = {
    ink: hex(0x173A19), back: hex(0x4A973F), flank: hex(0x74BE5A), flankHi: hex(0x8DCC72), belly: hex(0xDAF0BF), bellyHi: hex(0xEAF7DA), seam: 0.6,
    pawFur: hex(0xE0C4BC),
  };
  const P = stdPainters(pal);
  Object.assign(P, headPainters(P, pal));
  const pink = hex(0xE3BDB5), pinkDk = hex(0xC9958E);
  P.beady = ctx => {
    if (ctx.f !== ctx.b.eyeFace) return hex(0x0A120B);
    const hl = ctx.r === 0 && ctx.c === (ctx.b.side < 0 ? ctx.fw - 1 : 0);
    return hl ? hex(0xFFFFFF) : (ctx.r === ctx.fh - 1 ? hex(0x1D2B1F) : hex(0x0A120B));
  };
  P.pinkNose = () => pinkDk;
  P.pinkEar = ctx => {
    if (ctx.f !== 'front') return P.fur(ctx);
    const e = ctx.c === 0 || ctx.c === ctx.fw - 1 || ctx.r === 0 || ctx.r === ctx.fh - 1;
    return e ? furDetail(pal.flank, ctx, 81, 0.4) : mix(pink, pinkDk, rnd(ctx.c, ctx.r, 0, 82) * 0.4 + (ctx.r / ctx.fh) * 0.3);
  };
  P.pinkTail = ctx => mix(pink, pinkDk, (ctx.f === 'bottom' ? 0 : 0.3) + (ctx.r % 2) * 0.15);
  P.whisker = ctx => (ctx.r === 0 ? hex(0x3A6A40) : [0, 0, 0, 0]);
  const legOpt = { band: [9, 0], pawH: 1 };
  const eye = side => box([side < 0 ? -2.7 : 1.7, 0, 1.5], [1, 2, 2], 'beady', { side, eyeFace: side < 0 ? 'right' : 'left' });
  const lid = side => bone(side < 0 ? 'lidR' : 'lidL', [side * 2.35, 2.1, 2.5], [box([-0.5, -2.1, -1], [1, 2, 2], 'lid', { g: 0.06 })]);
  const ear = side => bone(side < 0 ? 'earR' : 'earL', [side * 1.8, 1.6, -0.2], [box([-2, 0, -0.5], [4, 4, 1], 'pinkEar')]);
  const whisk = side => bone(side < 0 ? 'whiskR' : 'whiskL', [side * 1.5, -1.2, 5.6], [box([side < 0 ? -4 : 0, -1, 0], [4, 2, 0], 'whisker'),
    box([side < 0 ? -4 : 0, -2, -0.4], [4, 2, 0], 'whisker')]);
  const head = bone('head', [0, 1, 4], [
    box([-2.5, -3, -1], [5, 5, 5], 'fur', { band: [8, 3] }),
    box([-1.5, -3, 4], [3, 3, 2], 'light', { band: [6, 3] }),
    box([-0.5, -1, 6], [1, 1, 1], 'pinkNose'),
    box([-0.5, -3.2, 5.1], [1, 1, 1], 'tooth'),
    eye(-1), eye(1),
  ], [lid(-1), lid(1), ear(-1), ear(1), whisk(-1), whisk(1),
    bone('jaw', [0, -3, 3.5], [box([-1, -1, 0], [2, 1, 2], 'jaw', { band: [3, 2] })])]);
  const front = box([-2.5, -3, 0], [5, 5, 5], 'fur', { band: [7, 2] });
  const rear = box([-3.5, -4, -7], [7, 7, 8], 'fur', { band: [8, 1] });
  const root = bone('body', [0, 5, 0], [front, rear], [
    head,
    foreLeg('LF', 1, [1.8, -1, 3], [2, 1, 1], [2, 2, 1, 1, 1, 2, 2], { ...legOpt }),
    foreLeg('RF', -1, [-1.8, -1, 3], [2, 1, 1], [2, 2, 1, 1, 1, 2, 2], { ...legOpt }),
    hindLeg('LH', 1, [2.5, -2, -5], [2, 2, 1], [3, 4, 1, 1, 1, 2, 3], { ...legOpt }),
    hindLeg('RH', -1, [-2.5, -2, -5], [2, 2, 1], [3, 4, 1, 1, 1, 2, 3], { ...legOpt }),
    tailChain('tail', [0, -1, -7], [[1, 1], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1]], 2, ['pinkTail']),
  ]);
  return { name: 'Mouse', tex: 'mouse', paint: P, root };
};

module.exports = {};
