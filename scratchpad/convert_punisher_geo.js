// Convert the supplied Punisher armour model into the mod's shared GeckoLib armour rig.
//
// The source (C:\Users\ethan\OneDrive\Desktop\3d minecraft models\Punisher\punisher_armor_model\
// punisher_armor.geo.json) is ALREADY authored to GeckoLib's armour bone names
// (armorHead/armorBody/armorRightArm/armorLeftArm/armorRightLeg/armorLeftLeg/armorRightBoot/
// armorLeftBoot) with top-level boot bones -- so unlike the Spider-Man convert there is nothing to
// rename or reparent. The only change is the project's z-fight rule (docs/ARMOR_MODELS.md): a suit
// cube must never sit at the exact vanilla-skin box size with inflate 0, so every cube gets a small
// uniform inflate bump. The identifier is set to geometry.punisher to match SuperheroArmorVisuals.
const fs = require('fs');
const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Punisher/punisher_armor_model/punisher_armor.geo.json';
const DST = 'src/main/resources/assets/herocraft/geo/punisher.geo.json';
const BUMP = 0.30;

const model = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const geo = model['minecraft:geometry'][0];
geo.description.identifier = 'geometry.punisher';

for (const bone of geo.bones) {
  if (!bone.cubes) continue;
  for (const cube of bone.cubes) {
    cube.inflate = (typeof cube.inflate === 'number' ? cube.inflate : 0) + BUMP;
  }
}

fs.mkdirSync('src/main/resources/assets/herocraft/geo', { recursive: true });
fs.writeFileSync(DST, JSON.stringify(model, null, 2));
console.log('wrote', DST, '-', geo.bones.length, 'bones');
