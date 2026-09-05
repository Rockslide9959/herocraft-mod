// v0.8.6 firearm model rebuild -- proper box-UV unwraps + fully-painted gunmetal sheets.
const { buildGun } = require('./gunkit');

const BLK = [26, 26, 30], GUN = [46, 47, 52], DGUN = [36, 37, 42], STEEL = [96, 98, 104];
const WOOD = [64, 46, 32], TAN = [140, 120, 90];

// Barrel runs along +X. Grip hangs down -Y. Slide/optic on +Y.
const HAND = {
  thirdperson_righthand: { rotation: [0, 0, 0], translation: [0, 3.5, 0.5], scale: [0.55, 0.55, 0.55] },
  thirdperson_lefthand:  { rotation: [0, 0, 0], translation: [0, 3.5, 0.5], scale: [0.55, 0.55, 0.55] },
  firstperson_righthand: { rotation: [3, 92, 0], translation: [1.5, 1.5, -2.5], scale: [0.62, 0.62, 0.62] },
  firstperson_lefthand:  { rotation: [3, -92, 0], translation: [1.5, 1.5, -2.5], scale: [0.62, 0.62, 0.62] },
  ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
  gui:    { rotation: [26, 135, 0], translation: [0, -1, 0], scale: [1.05, 1.05, 1.05] },
  fixed:  { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.75, 0.75, 0.75] },
  head:   { rotation: [0, 0, 0], translation: [0, 13, 0], scale: [1, 1, 1] },
};
const longGun = (fp) => ({ ...HAND, firstperson_righthand: { ...HAND.firstperson_righthand, translation: fp },
  firstperson_lefthand: { ...HAND.firstperson_lefthand, translation: fp },
  gui: { rotation: [22, 135, 0], translation: [0, -1, 0], scale: [0.72, 0.72, 0.72] } });

// -------- pistol
buildGun('punisher_pistol', [
  { name: 'frame',   from: [5, 7, 7],     to: [11.5, 9, 9],    base: DGUN },
  { name: 'slide',   from: [5, 9, 6.9],   to: [12.5, 10.6, 9.1], base: BLK },
  { name: 'barrel',  from: [12.5, 9.2, 7.4], to: [14.5, 10.2, 8.6], base: STEEL },
  { name: 'grip',    from: [5, 3, 7.2],   to: [7.2, 7, 8.9],   base: DGUN,
    rotation: { origin: [6, 7, 8], axis: 'z', angle: 22.5 }, opts: { skull: true } },
  { name: 'guard',   from: [6.4, 6, 7.6], to: [7.6, 7.2, 8.4], base: BLK },
], HAND, 64, 64);

// -------- assault rifle
buildGun('punisher_assault_rifle', [
  { name: 'receiver',  from: [3, 7, 7],      to: [11, 9.2, 9],    base: DGUN },
  { name: 'handguard', from: [8, 7.1, 7.2],  to: [13, 8.9, 8.8],  base: BLK },
  { name: 'barrel',    from: [13, 7.7, 7.5], to: [16.5, 8.5, 8.5], base: STEEL },
  { name: 'stock',     from: [-1, 7, 7.3],   to: [3, 9, 8.7],     base: BLK },
  { name: 'magazine',  from: [5.5, 3, 7.3],  to: [7.5, 7, 8.7],   base: DGUN,
    rotation: { origin: [6.5, 7, 8], axis: 'z', angle: -22.5 } },
  { name: 'grip',      from: [3.4, 3, 7.4],  to: [5.2, 7, 8.6],   base: BLK,
    rotation: { origin: [4.3, 7, 8], axis: 'z', angle: 22.5 } },
  { name: 'sight',     from: [4, 9.2, 7.8],  to: [5, 10, 8.2],    base: BLK },
], longGun([2.5, 1.2, -3.5]), 128, 64);

// -------- shotgun (wood furniture)
buildGun('punisher_shotgun', [
  { name: 'receiver', from: [3, 7, 7],      to: [10, 9, 9],      base: DGUN },
  { name: 'barrel',   from: [10, 7.6, 7.5], to: [16.5, 8.6, 8.5], base: STEEL },
  { name: 'pump',     from: [9, 6.4, 7.2],  to: [12.5, 7.4, 8.8], base: WOOD },
  { name: 'stock',    from: [-1, 6.6, 7.3], to: [3, 9, 8.7],     base: WOOD, opts: { accent: TAN, accentH: 1 } },
  { name: 'grip',     from: [3.4, 3.4, 7.4], to: [5.2, 7, 8.6],  base: WOOD,
    rotation: { origin: [4.3, 7, 8], axis: 'z', angle: 22.5 } },
  { name: 'bead',     from: [15.5, 8.6, 7.9], to: [16, 9.1, 8.1], base: STEEL },
], longGun([2.5, 1.0, -3.5]), 128, 64);

// -------- sniper (long barrel + big scope)
buildGun('punisher_sniper', [
  { name: 'receiver',   from: [2, 7, 7],       to: [10, 9, 9],      base: BLK },
  { name: 'barrel',     from: [10, 7.6, 7.6],  to: [18.5, 8.4, 8.4], base: STEEL },
  { name: 'shroud',     from: [7, 7.3, 7.3],   to: [12, 8.7, 8.7],  base: DGUN },
  { name: 'stock',      from: [-2, 6.7, 7.3],  to: [2, 9.2, 8.7],   base: BLK },
  { name: 'magazine',   from: [5, 4, 7.4],     to: [6.8, 7, 8.6],   base: DGUN },
  { name: 'grip',       from: [3, 3.2, 7.4],   to: [4.8, 7, 8.6],   base: BLK,
    rotation: { origin: [3.9, 7, 8], axis: 'z', angle: 22.5 } },
  { name: 'scope_body', from: [4.5, 9, 7.2],   to: [10, 10.4, 8.8], base: BLK },
  { name: 'scope_lens', from: [10, 9.2, 7.4],  to: [10.6, 10.2, 8.6], base: [40, 56, 120] },
  { name: 'scope_mnt',  from: [5.5, 8.6, 7.7], to: [9, 9, 8.3],     base: DGUN },
], longGun([3, 0.6, -3.5]), 128, 64);

console.log('guns v2 done');
