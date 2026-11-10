// v0.17.3: the Saiyan moves' names, descriptions and messages. Keys under projecthero.saiyan.move.* and
// message.projecthero.saiyan.* so they don't collide with the core's ability keys. CRLF-safe, refuses duplicates.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
const add = {
  'projecthero.saiyan.move.combo': 'Saiyan Combo',
  'projecthero.saiyan.move.combo.desc': 'Left click: three quick hits (4 each), then a launching kick (8). Damage grows with your form.',
  'projecthero.saiyan.move.feint': 'Feint Strike',
  'projecthero.saiyan.move.feint.desc': 'A stunning jab (6) that slows them for a moment, then a kick (9) that breaks a raised shield. 8 Ki, 8s cooldown.',
  'projecthero.saiyan.move.ki_beam': 'Ki Beam',
  'projecthero.saiyan.move.ki_beam.desc': 'Hold to gather Ki between your hands, let go to fire a beam for 2 seconds (up to 30 damage at full charge). It clashes with other beams. 15 Ki plus a little each tick, 8s cooldown.',
  'projecthero.saiyan.move.spirit_orb': 'Spirit Orb',
  'projecthero.saiyan.move.spirit_orb.desc': 'Base form only. Hold to raise a giant ball of energy over your head (you can not move), let go to throw it: a blast up to 7 blocks wide, up to 45 damage. 50 Ki, 45s cooldown.',
  'projecthero.saiyan.move.barrage': 'Barrage',
  'projecthero.saiyan.move.barrage.desc': 'A storm of punches for a second and a half, then a kick that sends them flying (10). 10 Ki, 7s cooldown.',
  'projecthero.saiyan.move.sky_combo': 'Sky Combo',
  'projecthero.saiyan.move.sky_combo.desc': 'Kick an enemy into the air, chase them up, kick twice more, then hammer them into the ground with a shockwave. 15 Ki, 14s cooldown.',
  'projecthero.saiyan.move.dragon_rush': 'Dragon Rush',
  'projecthero.saiyan.move.dragon_rush.desc': 'Super Saiyan 3 and up. Draw back, then rush forward with a punch that hits like a truck (26). 30 Ki, 15s cooldown.',
  'projecthero.saiyan.move.instant_step': 'Instant Step',
  'projecthero.saiyan.move.instant_step.desc': 'Vanish and appear where you look, or behind the creature you look at. 10 Ki, 5s cooldown.',
  'message.projecthero.saiyan.cooldown': '%s is ready in %ss',
  'message.projecthero.saiyan.no_ki': 'Not enough Ki',
  'message.projecthero.saiyan.orb_base_only': 'The Spirit Orb can only be gathered in base form',
  'message.projecthero.saiyan.rush_ssj3': 'Dragon Rush needs Super Saiyan 3 or stronger',
  'message.projecthero.saiyan.no_target': 'No one in reach',
  'message.projecthero.saiyan.no_room': 'No room to step there',
};
const lines = [];
for (const [k, v] of Object.entries(add)) {
  if (k in j) throw new Error('duplicate key ' + k);
  lines.push('  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
}
const anchor = t.lastIndexOf('\n}');
const before = t.slice(0, anchor).replace(/\s*$/, '');
t = before + (before.endsWith(',') ? '' : ',') + '\n' + lines.join('\n').replace(/,$/, '') + '\n}\n';
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok', lines.length);
