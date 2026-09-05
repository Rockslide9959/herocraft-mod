// v0.8.7 detailed firearm rebuild. Barrel along -Z. Based on the user reference sheet.
const { buildGun } = require('./gunkit2');

const BLK = [42, 43, 48], GUN = [72, 75, 82], DGUN = [58, 60, 67], STEEL = [128, 132, 140];
const WOOD = [88, 62, 40], TAN = [140, 110, 72], POLY = [60, 62, 69];

const rotX = (o, a) => ({ origin: o, axis: 'x', angle: a });

const disp = (fp) => {
  const frot = fp.rot || [0, 10, 0], ftr = fp.tr || [-8, -6, -10], fsc = fp.sc || [0.55, 0.55, 0.55];
  const trot = fp.trot || [0, -90, 0], ttr = fp.ttr || [0, 4, 1], tsc = fp.tsc || [0.5, 0.5, 0.5];
  return {
    thirdperson_righthand: { rotation: trot, translation: ttr, scale: tsc },
    thirdperson_lefthand:  { rotation: [trot[0], -trot[1], -trot[2]], translation: [ttr[0], ttr[1], ttr[2]], scale: tsc },
    firstperson_righthand: { rotation: frot, translation: ftr, scale: fsc },
    firstperson_lefthand:  { rotation: [frot[0], -frot[1], -frot[2]], translation: [-ftr[0], ftr[1], ftr[2]], scale: fsc },
    ground: { rotation: [90, 0, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
    gui:    { rotation: [22, -142, 0], translation: [0, 0, 0], scale: fp.gui || [1.0, 1.0, 1.0] },
    fixed:  { rotation: [0, -90, 0], translation: [0, 0, 0], scale: [0.62, 0.62, 0.62] },
    head:   { rotation: [0, 0, 0], translation: [0, 13, 0], scale: [1, 1, 1] },
  };
};

// -------------------------------------------------- PISTOL
buildGun('punisher_pistol', [
  { name: 'frame',   from: [6, 6.4, 6],    to: [10, 9, 14],     base: POLY },
  { name: 'slide',   from: [5.8, 9, 5.4],  to: [10.2, 10.9, 14.6], base: BLK, serrate: true },
  { name: 'muzzle',  from: [6.9, 9.2, 4],  to: [9.1, 10.5, 6],  base: STEEL, smooth: true },
  { name: 'grip',    from: [6.2, 2, 10.8], to: [9.8, 7, 13.6],  base: POLY, ribs: true, skull: true,
    },
  { name: 'guard',   from: [6.7, 5.4, 9.3], to: [9.3, 7, 11.6], base: BLK, smooth: true },
  { name: 'trigger', from: [7.3, 5.7, 9.9], to: [8.7, 6.9, 10.6], base: DGUN, smooth: true },
  { name: 'rsight',  from: [6.4, 10.9, 12.8], to: [9.6, 11.6, 13.8], base: BLK, smooth: true },
  { name: 'fsight',  from: [7.3, 10.9, 5.6], to: [8.7, 11.5, 6.4], base: BLK, smooth: true },
], disp({ sc: [0.72, 0.72, 0.72], tr: [-10, -4, -12], rot: [-4, 13, 2], gui: [1.5, 1.5, 1.5] }), 128, 64);

// -------------------------------------------------- ASSAULT RIFLE
buildGun('punisher_assault_rifle', [
  { name: 'receiver',  from: [6, 6.4, 6],     to: [10, 9.6, 13],   base: DGUN },
  { name: 'upperrail', from: [6.2, 9.6, 3.5], to: [9.8, 10.8, 14], base: BLK, serrate: true },
  { name: 'handguard', from: [5.9, 6.7, 1],   to: [10.1, 9.3, 6.5], base: BLK },
  { name: 'barrel',    from: [7.2, 7.4, -1],  to: [8.8, 9, 2.5],   base: STEEL, smooth: true },
  { name: 'muzzle',    from: [6.9, 7.2, -2.6], to: [9.1, 9.2, -0.6], base: BLK, smooth: true },
  { name: 'magazine',  from: [6.4, 1.6, 9.3], to: [9.6, 7, 12.6],  base: POLY, ribs: true,
    rotation: rotX([8, 7, 11], -22.5) },
  { name: 'grip',      from: [6.5, 2.2, 12.4], to: [9.5, 7, 14.6], base: POLY, ribs: true,
    rotation: rotX([8, 7, 13.5], 22.5) },
  { name: 'stocktube', from: [7.2, 7.7, 13],  to: [8.8, 9.1, 15],  base: DGUN, smooth: true },
  { name: 'stock',     from: [6.3, 6.6, 14.5], to: [9.7, 9.6, 18.5], base: BLK },
  { name: 'carryhandle', from: [6.6, 10.8, 10.5], to: [9.4, 12, 13], base: BLK, smooth: true },
  { name: 'fsightpost', from: [7.4, 10.8, 3.2], to: [8.6, 12.2, 4.4], base: BLK, smooth: true },
], disp({ sc: [0.5, 0.5, 0.5], tr: [-10, -3, -12], rot: [-3, 12, 2], gui: [0.78, 0.78, 0.78] }), 128, 128);

// -------------------------------------------------- SHOTGUN (wood)
buildGun('punisher_shotgun', [
  { name: 'receiver', from: [6, 6.4, 6],     to: [10, 9, 12],     base: DGUN },
  { name: 'barrel',   from: [7, 8, 0.3],     to: [9, 9.7, 7],     base: STEEL, smooth: true },
  { name: 'tube',     from: [7, 6.5, 1.8],   to: [9, 8, 8],       base: DGUN, shells: true },
  { name: 'pump',     from: [6.6, 5.9, 2.8], to: [9.4, 7.3, 6.4], base: WOOD },
  { name: 'stock',    from: [6.3, 5.4, 11.8], to: [9.7, 9.3, 16.6], base: WOOD, accent: TAN, accentAt: 0.2 },
  { name: 'grip',     from: [6.5, 2.6, 10.8], to: [9.5, 7, 13.4], base: WOOD, ribs: true,
    rotation: rotX([8, 7, 12], 22.5) },
  { name: 'guard',    from: [6.8, 5.4, 8.8], to: [9.2, 6.9, 11],  base: BLK, smooth: true },
  { name: 'bead',     from: [7.6, 9.7, 0.6], to: [8.4, 10.3, 1.4], base: STEEL, smooth: true },
], disp({ sc: [0.52, 0.52, 0.52], tr: [-10, -3, -12], rot: [-3, 12, 2], gui: [0.82, 0.82, 0.82] }), 128, 128);

// -------------------------------------------------- SNIPER (big scope + blue lens)
buildGun('punisher_sniper', [
  { name: 'receiver',   from: [6, 6.3, 6],     to: [10, 9, 13],    base: BLK },
  { name: 'barrel',     from: [7.2, 7.4, -3],  to: [8.8, 9, 6],    base: STEEL, smooth: true },
  { name: 'shroud',     from: [6.8, 7.1, 1.8], to: [9.2, 9.3, 8],  base: DGUN },
  { name: 'muzzlebrk',  from: [6.9, 7.2, -4.6], to: [9.1, 9.2, -2.6], base: BLK },
  { name: 'magazine',   from: [6.5, 3.3, 9.8], to: [9.5, 7, 12.4], base: POLY, ribs: true },
  { name: 'grip',       from: [6.6, 2.3, 11.8], to: [9.4, 7, 13.9], base: POLY, ribs: true,
    rotation: rotX([8, 7, 13], 22.5) },
  { name: 'stock',      from: [6.2, 5.9, 13],  to: [9.8, 9.6, 19.5], base: BLK },
  { name: 'cheek',      from: [6.4, 9.6, 14],  to: [9.6, 10.8, 18.5], base: BLK, smooth: true },
  { name: 'scopetube',  from: [6.7, 9.3, 5.5], to: [9.3, 11.4, 15], base: BLK, smooth: true },
  { name: 'scope_front', from: [6.85, 9.45, 4.3], to: [9.15, 11.25, 5.5], base: BLK, glass: true },
  { name: 'scope_rear', from: [6.85, 9.45, 15], to: [9.15, 11.25, 16.1], base: BLK, glass: true },
  { name: 'mount_f',    from: [7, 9, 6.5],     to: [9, 9.5, 7.6],  base: DGUN, smooth: true },
  { name: 'mount_r',    from: [7, 9, 12.5],    to: [9, 9.5, 13.6], base: DGUN, smooth: true },
], disp({ sc: [0.46, 0.46, 0.46], tr: [-10, -3, -13], rot: [-2, 11, 2], gui: [0.66, 0.66, 0.66] }), 128, 128);

console.log('guns v3 done');
