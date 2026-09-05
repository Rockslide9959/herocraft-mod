// Emits the three Phase-2 firearm item models (assault rifle, shotgun, sniper). Each is a blocky
// voxel gun: receiver + barrel (+ vents baked into the texture) + magazine + stock + optic, barrel
// along +X. Display transforms are a by-eye first pass -- a shouldered long-gun pose -- and are
// expected to need one in-world screenshot tune (like the Mjolnir model). Geometry is final.
const fs = require('fs');
const OUT = 'src/main/resources/assets/herocraft/models/item';

// texture regions (64x64): [u0,v0,u1,v1]
const R = {
  receiver: [0, 0, 28, 6],
  barrel:   [0, 8, 32, 13],
  mag:      [0, 16, 10, 32],
  stock:    [0, 34, 16, 42],
  optic:    [40, 0, 52, 10],
};
function box(name, from, to, region, rot) {
  const [u0, v0, u1, v1] = region;
  const f = {};
  for (const face of ['north', 'south', 'east', 'west', 'up', 'down']) {
    f[face] = { uv: [u0, v0, u1, v1], texture: '#gun' };
  }
  const e = { name, from, to, faces: f };
  if (rot) e.rotation = rot;
  return e;
}
function model(elements, display) {
  return JSON.stringify({
    credit: 'Punisher firearm -- original HeroCraft voxel model (Phase 2). Barrel along +X. Display block is a by-eye first pass for a shouldered long-gun pose; expect one in-world screenshot tune. Geometry is final.',
    texture_size: [64, 64],
    textures: { gun: 'herocraft:item/PLACEHOLDER', particle: 'herocraft:item/PLACEHOLDER' },
    elements,
    display,
  }, null, 2);
}

const longGunDisplay = {
  thirdperson_righthand: { rotation: [0, -90, 0], translation: [-2, 4, 0], scale: [0.5, 0.5, 0.5] },
  thirdperson_lefthand:  { rotation: [0, 90, 0],  translation: [-2, 4, 0], scale: [0.5, 0.5, 0.5] },
  firstperson_righthand: { rotation: [0, -90, 0], translation: [-6, 3.5, 3], scale: [0.5, 0.5, 0.5] },
  firstperson_lefthand:  { rotation: [0, 90, 0],  translation: [-6, 3.5, 3], scale: [0.5, 0.5, 0.5] },
  ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.4, 0.4, 0.4] },
  gui:    { rotation: [25, -140, 0], translation: [0, -1, 0], scale: [0.7, 0.7, 0.7] },
  fixed:  { rotation: [0, 90, 0], translation: [0, 0, 0], scale: [0.55, 0.55, 0.55] },
  head:   { rotation: [0, 0, 0], translation: [0, 13, 0], scale: [0.8, 0.8, 0.8] },
};

// ---------------- assault rifle
{
  const e = [
    box('receiver', [2, 6.5, 7], [11, 8.7, 9], R.receiver),
    box('barrel', [11, 7.2, 7.5], [16, 8.4, 8.5], R.barrel),
    box('handguard', [8, 6.8, 7.3], [12, 8.2, 8.7], R.barrel),
    box('magazine', [5.5, 3.5, 7.3], [7.3, 7, 8.7], R.mag, { origin: [6.4, 7, 8], axis: 'z', angle: -22.5 }),
    box('grip', [3.5, 3.5, 7.4], [5, 6.6, 8.6], R.mag, { origin: [4.2, 6.6, 8], axis: 'z', angle: 22.5 }),
    box('stock', [0, 6.6, 7.4], [2, 8.4, 8.6], R.stock),
    box('sight', [4, 8.7, 7.8], [5, 9.4, 8.2], R.optic),
    box('sight_front', [13, 8.4, 7.8], [13.6, 9.1, 8.2], R.optic),
  ];
  fs.writeFileSync(`${OUT}/punisher_assault_rifle.json`, model(e, longGunDisplay)
    .replace(/PLACEHOLDER/g, 'punisher_assault_rifle'));
}

// ---------------- shotgun (pump under the barrel)
{
  const e = [
    box('receiver', [3, 6.5, 7], [10, 8.6, 9], R.receiver),
    box('barrel', [10, 7.3, 7.5], [16, 8.5, 8.5], R.barrel),
    box('pump', [9, 6.4, 7.3], [12.5, 7.2, 8.7], R.stock),
    box('tube', [10, 6.6, 7.6], [15, 7.3, 8.4], R.barrel),
    box('grip', [3.5, 3.5, 7.4], [5, 6.6, 8.6], R.mag, { origin: [4.2, 6.6, 8], axis: 'z', angle: 22.5 }),
    box('stock', [0, 6.3, 7.4], [3, 8.4, 8.6], R.stock),
    box('bead', [15, 8.5, 7.9], [15.5, 8.9, 8.1], R.optic),
  ];
  fs.writeFileSync(`${OUT}/punisher_shotgun.json`, model(e, longGunDisplay)
    .replace(/PLACEHOLDER/g, 'punisher_shotgun'));
}

// ---------------- sniper (long barrel + big scope)
{
  const e = [
    box('receiver', [2, 6.5, 7], [10, 8.6, 9], R.receiver),
    box('barrel', [10, 7.3, 7.6], [18, 8.3, 8.4], R.barrel),
    box('barrel_shroud', [7, 7.1, 7.4], [12, 8.5, 8.6], R.barrel),
    box('magazine', [5, 4.2, 7.4], [6.8, 7, 8.6], R.mag),
    box('grip', [3, 3.2, 7.4], [4.6, 6.6, 8.6], R.mag, { origin: [3.8, 6.6, 8], axis: 'z', angle: 22.5 }),
    box('stock', [-1, 6.3, 7.4], [2, 8.6, 8.6], R.stock),
    box('scope_body', [4.5, 8.7, 7.3], [9.5, 10, 8.7], R.optic),
    box('scope_front', [9.5, 8.9, 7.5], [10.3, 9.8, 8.5], R.optic),
    box('scope_mount_a', [5, 8.4, 7.7], [5.6, 8.8, 8.3], R.optic),
    box('scope_mount_b', [8.4, 8.4, 7.7], [9, 8.8, 8.3], R.optic),
  ];
  const d = JSON.parse(JSON.stringify(longGunDisplay));
  d.firstperson_righthand.translation = [-7, 3, 3];
  d.firstperson_lefthand.translation = [-7, 3, 3];
  fs.writeFileSync(`${OUT}/punisher_sniper.json`, model(e, d).replace(/PLACEHOLDER/g, 'punisher_sniper'));
}

// ---------------- sprite models (ammo + components)
for (const [name, tex] of [
  ['rifle_ammo', 'rifle_ammo'], ['shotgun_shell', 'shotgun_shell'], ['sniper_ammo', 'sniper_ammo'],
  ['gun_barrel', 'gun_barrel'], ['weapon_scope', 'weapon_scope'],
]) {
  fs.writeFileSync(`${OUT}/${name}.json`,
    JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: `herocraft:item/${tex}` } }, null, 2));
}

console.log('phase 2 models written');
