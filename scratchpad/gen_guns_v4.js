// v0.8.9 clean firearm rebuild. Flat-colour parts + MC face shading. Barrel -Z, grip -Y.
const { buildGun } = require('./gunkit4');

const BLK = [38, 39, 44], DGUN = [56, 58, 65], STEEL = [120, 124, 132], POLY = [64, 66, 73];
const WOOD = [92, 64, 40];
const rx = (o, a) => ({ origin: o, axis: 'x', angle: a });

// firstperson: solved from world = armT * translate(json/16) * rotXYZ * scale * translate(-0.5) * (v/16)
// (NO center-back). thirdperson kept simple.
function disp(fp) {
  const fr = fp.frot || [-3, 12, 0], ft = fp.ftr || [-4, 4, -1], fs = fp.fsc || [0.7, 0.7, 0.7];
  return {
    thirdperson_righthand: { rotation: [0, -90, 0], translation: [0, 3.5, 1.5], scale: [0.5, 0.5, 0.5] },
    thirdperson_lefthand:  { rotation: [0, 90, 0], translation: [0, 3.5, 1.5], scale: [0.5, 0.5, 0.5] },
    firstperson_righthand: { rotation: fr, translation: ft, scale: fs },
    firstperson_lefthand:  { rotation: [fr[0], -fr[1], -fr[2]], translation: [-ft[0], ft[1], ft[2]], scale: fs },
    ground: { rotation: [90, 0, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
    gui:    { rotation: [24, -138, 0], translation: [0, 0, 0], scale: fp.gui || [1.0, 1.0, 1.0] },
    fixed:  { rotation: [0, -90, 0], translation: [0, 0, 0], scale: [0.6, 0.6, 0.6] },
    head:   { rotation: [0, 0, 0], translation: [0, 13, 0], scale: [1, 1, 1] },
  };
}

buildGun('punisher_pistol', [
  { name: 'frame',   from: [6, 6.5, 5],    to: [10, 9, 13],    base: POLY },
  { name: 'slide',   from: [5.8, 9, 4.5],  to: [10.2, 10.8, 13.5], base: BLK },
  { name: 'muzzle',  from: [7, 9.2, 3],    to: [9, 10.4, 5],   base: STEEL },
  { name: 'grip',    from: [6.2, 2.5, 10], to: [9.8, 7, 12.8], base: POLY, motif: 'skull', motifFace: 'east' },
  { name: 'guard',   from: [6.8, 5.5, 8.5], to: [9.2, 7, 10.5], base: BLK },
  { name: 'trigger', from: [7.4, 5.8, 9],  to: [8.6, 6.9, 9.7], base: DGUN },
  { name: 'rsight',  from: [6.5, 10.8, 12], to: [9.5, 11.4, 12.8], base: BLK },
  { name: 'fsight',  from: [7.4, 10.8, 4.4], to: [8.6, 11.4, 5.1], base: BLK },
], disp({ ftr: [-4, 4, -1], fsc: [0.72, 0.72, 0.72], frot: [-3, 13, 0], gui: [1.5, 1.5, 1.5] }));

buildGun('punisher_assault_rifle', [
  { name: 'receiver',  from: [6, 6.5, 5],    to: [10, 9.5, 12],  base: DGUN },
  { name: 'handguard', from: [5.9, 6.8, 0],  to: [10.1, 9.3, 5.5], base: BLK },
  { name: 'barrel',    from: [7.2, 7.5, -2.5], to: [8.8, 9, 0.5], base: STEEL },
  { name: 'muzzle',    from: [6.9, 7.3, -4], to: [9.1, 9.2, -2],  base: BLK },
  { name: 'upperrail', from: [6.3, 9.5, 3],  to: [9.7, 10.5, 13], base: BLK },
  { name: 'carryhandle', from: [6.7, 10.5, 9], to: [9.3, 11.6, 12], base: BLK },
  { name: 'fsight',    from: [7.4, 10.5, 1], to: [8.6, 11.8, 2],  base: BLK },
  { name: 'magazine',  from: [6.4, 2, 8],    to: [9.6, 6.8, 11],  base: POLY, rotation: rx([8, 7, 9.5], -22.5) },
  { name: 'grip',      from: [6.5, 2.5, 11], to: [9.5, 6.9, 13],  base: POLY, rotation: rx([8, 7, 12], 22.5) },
  { name: 'stocktube', from: [7.2, 7.6, 12], to: [8.8, 9, 14],    base: DGUN },
  { name: 'stock',     from: [6.3, 6.6, 13.5], to: [9.7, 9.6, 17.5], base: BLK },
], disp({ ftr: [-4, 4.5, 0], fsc: [0.5, 0.5, 0.5], frot: [-2, 11, 0], gui: [0.78, 0.78, 0.78] }));

buildGun('punisher_shotgun', [
  { name: 'receiver', from: [6, 6.5, 5],    to: [10, 9, 11],    base: DGUN },
  { name: 'barrel',   from: [7, 8, -0.5],   to: [9, 9.7, 6],    base: STEEL },
  { name: 'tube',     from: [7, 6.5, 0.5],  to: [9, 8, 7],      base: DGUN, motif: 'shells', motifFace: 'east' },
  { name: 'pump',     from: [6.6, 6, 1.5],  to: [9.4, 7.3, 5],  base: WOOD, motif: 'wood', motifFace: 'up' },
  { name: 'stock',    from: [6.3, 5.5, 11], to: [9.7, 9.3, 16], base: WOOD, motif: 'wood', motifFace: 'east' },
  { name: 'grip',     from: [6.5, 3, 10],   to: [9.5, 7, 12.5], base: WOOD, rotation: rx([8, 7, 11.2], 22.5) },
  { name: 'guard',    from: [6.9, 5.5, 8.2], to: [9.1, 6.9, 10.2], base: BLK },
  { name: 'bead',     from: [7.6, 9.7, -1], to: [8.4, 10.3, -0.3], base: STEEL, motif: 'dot', motifFace: 'north' },
], disp({ ftr: [-4, 4.5, -0.5], fsc: [0.52, 0.52, 0.52], frot: [-2, 11, 0], gui: [0.82, 0.82, 0.82] }));

buildGun('punisher_sniper', [
  { name: 'receiver',  from: [6, 6.3, 5],   to: [10, 9, 12],    base: BLK },
  { name: 'barrel',    from: [7.2, 7.5, -4], to: [8.8, 9, 5],   base: STEEL },
  { name: 'shroud',    from: [6.8, 7.2, 0.5], to: [9.2, 9.3, 7], base: DGUN },
  { name: 'muzzlebrk', from: [6.9, 7.3, -5.5], to: [9.1, 9.2, -3.5], base: BLK },
  { name: 'magazine',  from: [6.5, 3.5, 9], to: [9.5, 7, 11.5], base: POLY },
  { name: 'grip',      from: [6.6, 2.5, 11], to: [9.4, 7, 13],  base: POLY, rotation: rx([8, 7, 12], 22.5) },
  { name: 'stock',     from: [6.2, 6, 12],  to: [9.8, 9.6, 18.5], base: BLK },
  { name: 'cheek',     from: [6.4, 9.6, 13], to: [9.6, 10.7, 17.5], base: BLK },
  { name: 'scopetube', from: [6.7, 9.3, 4.5], to: [9.3, 11.4, 14], base: BLK },
  { name: 'scope_f',   from: [6.9, 9.5, 3.3], to: [9.1, 11.2, 4.5], base: BLK, motif: 'lens', motifFace: 'north' },
  { name: 'scope_r',   from: [6.9, 9.5, 14], to: [9.1, 11.2, 15], base: BLK, motif: 'lens', motifFace: 'south' },
  { name: 'mount_f',   from: [7, 9, 5.5],   to: [9, 9.5, 6.6],  base: DGUN },
  { name: 'mount_r',   from: [7, 9, 11.5],  to: [9, 9.5, 12.6], base: DGUN },
], disp({ ftr: [-4, 4.5, 1], fsc: [0.46, 0.46, 0.46], frot: [-1, 10, 0], gui: [0.66, 0.66, 0.66] }));

console.log('guns v4 done');
