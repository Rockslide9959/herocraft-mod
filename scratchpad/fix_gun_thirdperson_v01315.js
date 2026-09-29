// v0.13.15: third-person gun transforms. The old [0,-90,0] rotation turned the barrel (-Z in the model) to point
// out of the player's right side. In the vanilla third-person hand frame (after ItemInHandLayer's Rx(-90)·Ry(180)):
// +Y = forward, +Z = up, so Rx(alpha) with alpha ~ 90 points the barrel forward and the sights up. alpha < 90 tilts
// the barrel down to cancel the ITEM arm pose's ~18 deg forward raise. Translation puts the grip in the fist.
const fs = require('fs');
const dir = 'src/main/resources/assets/projecthero/models/item/';
const guns = { punisher_pistol: 0.62, punisher_assault_rifle: 0.55, punisher_shotgun: 0.55, punisher_sniper: 0.52 };
const ALPHA = 74;
const target = [0, -0.5, 0.3]; // px in the pre-display hand frame (+Y forward, +Z up): grip centred in the fist (checked in-client, v0.13.15 harness)
const rad = ALPHA * Math.PI / 180;
function rotX([x, y, z]) { return [x, y * Math.cos(rad) - z * Math.sin(rad), y * Math.sin(rad) + z * Math.cos(rad)]; }
for (const [id, s] of Object.entries(guns)) {
  const file = dir + id + '.json';
  const m = JSON.parse(fs.readFileSync(file, 'utf8'));
  const g = m.elements.find(e => e.name === 'grip');
  const c = [0, 1, 2].map(i => (g.from[i] + g.to[i]) / 2);
  const r = rotX(c.map(v => (v - 8) * s)); // px
  const t = [0, 1, 2].map(i => +(target[i] - r[i]).toFixed(2));
  const tp = { rotation: [ALPHA, 0, 0], translation: t, scale: [s, s, s] };
  m.display.thirdperson_righthand = tp;
  m.display.thirdperson_lefthand = JSON.parse(JSON.stringify(tp));
  // item frames / armour stands in "fixed": lie the gun flat, barrel sideways is right there
  fs.writeFileSync(file, JSON.stringify(m, null, 2) + '\n');
  const muzzle = rotX([0, 0, -1]);
  console.log(id, JSON.stringify(tp), 'barrel dir (x,fwd,up)=', muzzle.map(v => v.toFixed(2)).join(','));
}
