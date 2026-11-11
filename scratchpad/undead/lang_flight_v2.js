// v0.17.3 Undead flight speeds + boost burn: updates / adds these en_us keys IN PLACE (line by line, the rest of the file untouched).
// CRLF-safe, refuses duplicate keys, validates the JSON after. Run from the repo root: node scratchpad/undead/lang_flight_v2.js
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';

const UPDATE = {
  'projecthero.guide.undead.flight.body':
    'Double-tap jump in the air: your hands blow off with half your forearms and your feet with half your calves, and blood jets out of the four stumps, streaming back against the way you go. You fly on the same controls as the other flights (W along your look, A / D sideways, jump / sneak up and down, sprint to go faster, a hover when you let go): 10 blocks a second, 20 sprinting, for as long as you like. Hold right click to BOOST: 32 blocks a second along your look, but your body burns away as you go -- your legs first, then your arms, then your body, until after 10 seconds of boosting (added up over the flight) you are just a head. As just a head you fly at 25 blocks a second, boosting or not. Touch down or double-tap again to land; everything blown off or burnt away grows back once you are down. Every blood move works the same way: what you blow off is left lying where it came off, and grows back once the move is over.',
  'projecthero.undead.ability.flight.desc':
    'Double-tap jump in the air: your hands blow off with half your forearms and your feet with half your calves, and blood jets out of the four stumps to fly you anywhere -- W along your look, A / D sideways, jump / sneak up and down, 10 blocks a second (20 sprinting), a dead hover with nothing held. Hold right click to boost at 32 blocks a second while your body burns away (legs, arms, then body: 10 s of boosting to just a head); just a head you fly at 25. Land or double-tap again to drop out; everything grows back once you are down.',
};
const ADD_AFTER = [
  ['projecthero.undead.ability.flight.desc', 'projecthero.undead.ability.boost', 'Flight Boost'],
  ['projecthero.undead.ability.boost', 'projecthero.undead.ability.boost.desc',
    'Hold right click while flying: 32 blocks a second along your look, and your body burns away -- legs, then arms, then body, 10 seconds of boosting in all to just a head (25 blocks a second). What burns grows back after you land. Right click does nothing else while you fly.'],
  ['projecthero.undead.ability.boost.desc', 'hud.projecthero.undead.burn', 'Burn'],
  ['hud.projecthero.undead.burn', 'hud.projecthero.undead.burn_head', 'Just a head'],
];

let raw = fs.readFileSync(L, 'utf8');
const crlf = raw.includes('\r\n');
let lines = raw.replace(/\r\n/g, '\n').split('\n');
const keyOf = l => { const m = /^  "((?:[^"\\]|\\.)+)":/.exec(l); return m ? m[1] : null; };
const keys = lines.map(keyOf).filter(k => k);
if (new Set(keys).size !== keys.length) throw new Error('en_us already has duplicate keys');
const idx = k => lines.findIndex(l => keyOf(l) === k);
const line = (k, v, comma) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + (comma ? ',' : '');
for (const [k, v] of Object.entries(UPDATE)) {
  const i = idx(k);
  if (i < 0) throw new Error('missing ' + k);
  lines[i] = line(k, v, lines[i].trimEnd().endsWith(','));
}
let added = 0;
for (const [after, k, v] of ADD_AFTER) {
  const e = idx(k);
  if (e >= 0) { lines[e] = line(k, v, lines[e].trimEnd().endsWith(',')); continue; }
  const i = idx(after);
  if (i < 0) throw new Error('missing anchor ' + after);
  const hadComma = lines[i].trimEnd().endsWith(',');
  if (!hadComma) lines[i] = lines[i].trimEnd() + ',';
  lines.splice(i + 1, 0, line(k, v, hadComma));
  added++;
}
let out = lines.join('\n');
const parsed = JSON.parse(out); // validates
const keys2 = lines.map(keyOf).filter(k => k);
if (new Set(keys2).size !== keys2.length) throw new Error('duplicate keys after edit');
for (const k of Object.keys(UPDATE)) if (parsed[k] !== UPDATE[k]) throw new Error('not applied ' + k);
fs.writeFileSync(L, crlf ? out.replace(/\n/g, '\r\n') : out);
console.log({ crlf, updated: Object.keys(UPDATE).length, added, total: Object.keys(parsed).length });
