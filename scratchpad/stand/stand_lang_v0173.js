// v0.17.3 Star Platinum rework: the stand lang keys in en_us.json (CRLF file). Run from the repo root.
// Normalises line endings, refuses duplicate keys (in the file before and after), touches only "stand" keys, validates the JSON.
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(f, 'utf8');
const lf = raw.replace(/\r\n/g, '\n');

function keysOf(text) {
  const keys = [];
  for (const line of text.split('\n')) {
    const m = line.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/);
    if (m) keys.push(m[1]);
  }
  return keys;
}
function dupes(keys) {
  const seen = new Set(), d = [];
  for (const k of keys) { if (seen.has(k)) d.push(k); seen.add(k); }
  return d;
}
const before = dupes(keysOf(lf));
if (before.length) throw new Error('duplicate keys already in the file: ' + before.join(', '));

const set = {
  'projecthero.stand.move.summon.desc': 'Star Platinum steps out of your body and floats behind you, or sinks back into you. Between moves it hovers at your back copying your arms, its legs hanging loose. Any move summons it if it is away.',
  'projecthero.stand.move.punch': 'Stand Punch',
  'projecthero.stand.move.punch.desc': 'Left-click with an empty hand while Star Platinum is out: it punches for you, at anything, out in front. On a creature it is a 3-hit chain with extra reach (8, 8, then a 12 knockback blow).',
  'projecthero.stand.move.mining': 'Stand Mining',
  'projecthero.stand.move.mining.desc': 'Mine with an empty hand while Star Platinum is out and it breaks the block for you, punching it, as fast as iron tools and with the normal drops (anything an iron pickaxe, axe or shovel could harvest). Unbreakable blocks stay unbreakable.',
  'projecthero.stand.move.platinum_smash': 'Platinum Smash',
  'projecthero.stand.move.platinum_smash.desc': 'Tap R: a 4-hit combo, one hit a tap. 8, 8 and 8 damage, each stunning what it hits in place for 0.5 s, then a 12-damage knockback blow. 4 Stamina a hit. 3 s cooldown after the last hit, or once 2 s pass with the combo unfinished.',
  'projecthero.stand.move.ora_barrage': 'ORA Barrage',
  'projecthero.stand.move.ora_barrage.desc': 'Hold R: ORA ORA ORA. Every punch deals 8 damage; whatever it hits is stunned and held in place, then launched 5 blocks when the barrage ends. Knocks projectiles away. 3 Stamina a second while it runs (up to 5 s), 5 s cooldown.',
  'projecthero.stand.move.star_finger': 'Star Finger',
  'projecthero.stand.move.star_finger.desc': 'Star Platinum points and its fingers stretch out 30 blocks over 1 second, dealing 22 damage to every enemy they run through. 15 Stamina, 8 s cooldown.',
  'projecthero.stand.move.ora_chase': 'Ora Chase',
  'projecthero.stand.move.ora_chase.desc': 'Send Star Platinum forward, up to 10 blocks at 3 blocks a second; it flies wherever you point the mouse. It grabs the first creature it reaches by the neck, lifts it and barrages it one-handed for 3 s (15 damage), then throws it 10 blocks (10 more). Nothing in 10 blocks: it comes back. 15 Stamina, 8 s cooldown.',
  'projecthero.stand.move.bearing_flick': 'Ball Bearing Flick',
  'projecthero.stand.move.bearing_flick.desc': 'Star Platinum flicks a steel ball bearing like a bullet: 20 damage to the first thing it hits. 4 Stamina, 5 s cooldown.',
  'projecthero.stand.move.stand_jump': 'Stand Jump',
  'projecthero.stand.move.stand_jump.desc': 'Star Platinum reinforces your legs: jump about 15 blocks the way you are looking. No fall damage from it. 4 Stamina, 2 s cooldown.',
  'projecthero.stand.move.ora_rush': 'Ora Rush',
  'projecthero.stand.move.ora_rush.desc': 'Dash about 8 blocks forward with Star Platinum out in front, its fists hammering everything you pass (6 damage a hit, many hits) and shoving them along. 10 Stamina, 5 s cooldown.',
  'projecthero.stand.move.star_breaker': 'Star Breaker',
  'projecthero.stand.move.star_breaker.desc': 'Hold to wind up (up to 1.5 s), let go: one huge punch for 30 damage. The longer the wind-up, the further it launches the target; well charged it breaks a raised shield and smashes weak blocks.',
  'projecthero.stand.move.the_world': 'Star Platinum: The World',
  'projecthero.stand.move.the_world.desc': 'ULTIMATE. Time stops across the whole world: every creature, player, projectile and falling block freezes; only you move. It drains 10 Stamina a second and lasts until the bar runs dry or you press Shift+Z again. You fly at 10 blocks a second, your moves cost nothing, and you deal 30% more damage, all of it landing at once when time resumes. Afterwards Stamina refills at half speed for 20 s. Needs 10 Stamina, 90 s cooldown.',
  'projecthero.stand.move.stand_guard': 'Stand Guard',
  'projecthero.stand.move.stand_guard.desc': 'Hold V: Star Platinum steps in front of you and blocks 60% of damage from the front, catching arrows out of the air. No time limit and no Stamina; letting go puts it on a 6 s cooldown.',
  'projecthero.stand.move.stand_grab': 'Stand Grab',
  'projecthero.stand.move.stand_grab.desc': 'Star Platinum grabs the creature in front of you and holds it as long as you like. V throws it, Shift+V sets it down gently, and R punches it: a 5-hit combo, 30 damage in all, the fifth knocking it out of the hand. 3 s cooldown after.',
  'projecthero.stand.move.super_inhale': 'Super Inhale',
  'projecthero.stand.move.super_inhale.desc': 'For 2 s it inhales, pulling creatures, items and XP toward you and clearing lingering potion clouds. Creatures pulled in are left winded (Weakness). 4 s cooldown.',
  'projecthero.stand.move.ora_uppercut': 'ORAAA Uppercut',
  'projecthero.stand.move.ora_uppercut.desc': 'A rising punch for 22 damage that launches the target high into the air. Follow up with Star Finger or a Stand Jump.',
  'hud.projecthero.stand.tap_hold': '%s / hold: %s',
  'entity.projecthero.ball_bearing': 'Ball Bearing',
  'message.projecthero.stand.acquired_hint': 'H summons it, Shift+N opens the Stand Menu, hold Left-Alt for the moves. Only other Stand users can see it.',
  'projecthero.guide.stand.stats.body': 'Power A, Speed A, Range C, Stamina A, Precision A, Potential A. Range C means its fists only reach about 3 blocks from you; Star Finger (30 blocks), Ora Chase (10), the ball bearing and thrown things cover more.',
  'projecthero.guide.stand.stamina.body': 'The purple bar (100). It refills 3 a second, always (half that for 20 s after a time stop). Moves cost a little each; the ORA Barrage drains 3 a second while it runs and a time stop 10 a second.',
  'projecthero.guide.stand.summon.body': 'H calls Star Platinum out: it steps out of your body and floats behind you, copying your arms, its legs hanging loose as it glides after you. H again and it sinks back into you. It moves in front of you to fight, then floats back. With it out, left-clicking with an empty hand makes it punch for you (on a creature, a 3-hit chain with extra reach), and mining with an empty hand it breaks the block like iron tools.',
  'projecthero.guide.stand.time_stop.body': 'Shift+Z stops time across the whole world, with no limit to its reach: creatures, projectiles, other players and falling blocks all freeze; only you move. It drains 10 Stamina a second and lasts until the bar runs dry or you press Shift+Z again. Inside it you fly at 10 blocks a second, your moves cost no Stamina and you deal 30% more damage. Every hit you land is held back and lands all at once when time resumes (another player takes at most 30% of their max health; bosses take less). Something you throw or shoot stops in mid-air and flies on when time resumes. You can\'t open chests or break blocks in stopped time. Afterwards Stamina refills at half speed for 20 s. It needs 10 Stamina and has a 90 s cooldown. Only one time stop can run at once.',
  'projecthero.guide.stand.menu.body': 'Shift+N: the stat card, the pose N strikes (Jotaro\'s Point), the move list, and settings for the floating ORA text and the barrage\'s auto-face.',
  'screen.projecthero.stand_menu.ora_text.tip': 'Floating ORA ORA ORA over the barrage (other Stand users see it too).',
  'screen.projecthero.stand_menu.auto_face': 'Barrage auto-face: %s',
  'screen.projecthero.stand_menu.auto_face.tip': 'Platinum Smash and the ORA Barrage turn to the nearest enemy in front of you.',
};
const remove = [
  'projecthero.stand.move.power_grab', 'projecthero.stand.move.power_grab.desc',
  'projecthero.stand.move.stand_leap', 'projecthero.stand.move.stand_leap.desc',
  'projecthero.stand.move.stand_dash', 'projecthero.stand.move.stand_dash.desc',
  'projecthero.stand.move.precision_sight', 'projecthero.stand.move.precision_sight.desc',
];
for (const k of [...Object.keys(set), ...remove]) {
  if (!/stand|ball_bearing/.test(k)) throw new Error('not a stand key: ' + k);
  if (/undead/.test(k)) throw new Error('undead key: ' + k);
}

const esc = s => JSON.stringify(s);
let lines = lf.split('\n');
// remove
lines = lines.filter(line => {
  const m = line.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/);
  return !(m && remove.includes(m[1]));
});
// replace existing
const pending = new Set(Object.keys(set));
lines = lines.map(line => {
  const m = line.match(/^(\s*)"((?:[^"\\]|\\.)*)"\s*:\s*.*?(,?)\s*$/);
  if (m && pending.has(m[2])) {
    pending.delete(m[2]);
    return m[1] + esc(m[2]) + ': ' + esc(set[m[2]]) + m[3];
  }
  return line;
});
// add the new ones after the last projecthero.stand.move.* key
let anchor = -1;
lines.forEach((line, i) => { if (/^\s*"projecthero\.stand\.move\./.test(line)) anchor = i; });
if (anchor < 0) throw new Error('no anchor');
const add = [...pending].map(k => '  ' + esc(k) + ': ' + esc(set[k]) + ',');
if (add.length && !lines[anchor].trim().endsWith(',')) lines[anchor] += ',';
lines.splice(anchor + 1, 0, ...add);
let out = lines.join('\n');
// a trailing comma before the closing brace would be invalid
out = out.replace(/,(\s*\n\})/, '$1');
const parsed = JSON.parse(out);
const after = dupes(keysOf(out));
if (after.length) throw new Error('duplicate keys after the edit: ' + after.join(', '));
for (const k of Object.keys(set)) if (parsed[k] !== set[k]) throw new Error('not set: ' + k);
for (const k of remove) if (k in parsed) throw new Error('not removed: ' + k);
fs.writeFileSync(f, out.replace(/\n/g, '\r\n'));
console.log('stand lang: set ' + Object.keys(set).length + ' (new ' + add.length + '), removed ' + remove.length);
