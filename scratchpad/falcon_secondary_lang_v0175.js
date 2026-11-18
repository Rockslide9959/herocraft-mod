// v0.17.5 Falcon as a Secondary power: rewrites the Falcon keys whose meaning changed and inserts the new ones after an
// anchor, key by key (never the whole file). Keeps the file's line endings; JSON.parse checked before writing.
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(file, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';

const replace = {
  'projecthero.guide.falcon.tier': 'Secondary power: a craftable suit you wear, on top of your Primary',
  'projecthero.guide.falcon.body': "Sam Wilson's EXO-7 Falcon rig: long segmented steel wings, twin jets, wrist guns and Redwing, his drone. A fast jet flier with a kit of guns, missiles, wing slashes and grabs. It is a suit, not something you are: wear it and you get the whole kit, take it off and you are back to your own power.",
  'projecthero.guide.falcon.gear.body': 'Craft and wear all four pieces: the EXO-7 Wing Pack (chest: an Elytra, 2 Netherite Scrap, 4 Iron Ingots and 2 Phantom Membranes), the Falcon Goggles (head: 2 Leather, a Gold Ingot, 2 Glass Panes and Redstone) and the Flight Suit leggings and boots (Leather, Iron and Red Dye; the leggings take Gray Dye too). With the full set on you get the Falcon kit, and the red-and-white suit and steel wings show over your own skin. Take any piece off and the kit goes. Redwing rides at your shoulder while the kit is on.',
  'projecthero.guide.falcon.commands.body': '/projecthero power grant falcon [player] puts the full gear on them, on top of whatever they have (nothing is replaced); /projecthero power revoke falcon [player] switches the kit off until a piece comes off and goes back on.',
  'message.projecthero.falcon.acquired': 'The gear is on: the Falcon kit has your keys.',
  'message.projecthero.falcon.acquired_hint': 'Double-tap jump to take off, sprint to fly faster, hold X to burn the jets. Take a piece off and your own power is back. Press I for the full kit.',
  'message.projecthero.falcon.gear_off': 'Gear off: your own power has the keys again',
  'item.projecthero.falcon_gear.set': 'Wear all four Falcon pieces for the Falcon kit, on top of your own power.',
};

const insertAfter = 'projecthero.guide.falcon.passives.body';
const add = [
  ['projecthero.guide.falcon.stacking', 'On top of your Primary'],
  ['projecthero.guide.falcon.stacking.body', "The Falcon gear is a Secondary power. It never replaces your Primary power, and you don't need one to use it. While the full set is on, the Falcon kit takes R, G, Z, X, C and V and the double-tap jump, the Falcon HUD replaces your power's, and H and N wait. Your Primary's passives keep working. Take a piece off and your own keys and HUD come straight back, with nothing lost. Power Armour comes first: stepping into a frame stores the gear and hands it back when you step out. The kit also rests while another suit or transformation is up (Hulk, a Titan, an Omnitrix alien, Max Steel, Shazam, a Miraculous, the Symbiote suit, Thor's Armour, Moon Knight, Black Panther's suit) and comes back on its own when that ends. Saves from before 0.17.5 that had the Falcon as their Primary keep the gear and the kit; the Primary slot is simply free again."],
  ['message.projecthero.falcon.dormant', 'The Falcon kit rests while your other suit is on'],
  ['message.projecthero.falcon.resumed', 'The Falcon kit is back online'],
  ['message.projecthero.falcon.migrated', 'Project Hero 0.17.5: the Falcon gear is now a Secondary power. It stacks on top of your Primary instead of replacing it, so your Primary slot is free again. You keep the gear and the kit.'],
  ['projecthero.jei.falcon_gear', 'Falcon gear: wear all four pieces (Goggles, EXO-7 Wing Pack, Flight Suit leggings and boots) for the Falcon kit. It is a Secondary power: it rides on top of your Primary power (or none), takes the six ability keys while worn, and gives them back when a piece comes off.'],
];

const esc = s => JSON.stringify(s);
for (const [k, v] of Object.entries(replace)) {
  const re = new RegExp('("' + k.replace(/\./g, '\\.') + '"\\s*:\\s*)"(?:[^"\\\\]|\\\\.)*"');
  if (!re.test(text)) throw new Error('missing key ' + k);
  text = text.replace(re, (m, p1) => p1 + esc(v));
}
const anchorRe = new RegExp('^.*"' + insertAfter.replace(/\./g, '\\.') + '"\\s*:.*$', 'm');
const m = text.match(anchorRe);
if (!m) throw new Error('missing anchor');
const lines = add.filter(([k]) => !text.includes('"' + k + '"')).map(([k, v]) => '  ' + esc(k) + ': ' + esc(v) + ',');
if (lines.length) {
  let anchorLine = m[0].replace(/\r$/, '');
  const fixed = anchorLine.endsWith(',') ? anchorLine : anchorLine + ',';
  text = text.replace(m[0], fixed + (m[0].endsWith('\r') ? '\r' : '') + EOL + lines.join(EOL).replace(/,$/, anchorLine.endsWith(',') ? ',' : ''));
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log('ok', Object.keys(replace).length, 'replaced,', lines.length, 'added');
