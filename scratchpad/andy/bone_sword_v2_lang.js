// v0.17.3: Andy -- lang for hold-V Bone Sword, Shift+V regen speed, tap-V Tear Off. Key by key; CRLF kept; no duplicates; JSON checked.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
// refuse a file that already has duplicate keys
{
  const seen = new Set();
  for (const m of t.matchAll(/^\s*"((?:[^"\\]|\\.)*)"\s*:/gm)) {
    if (seen.has(m[1])) throw new Error('duplicate key already in file: ' + m[1]);
    seen.add(m[1]);
  }
}
const set = (k, v) => {
  if (!(k in j)) throw new Error('no key ' + k);
  const old = JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
  if (!t.includes(old)) throw new Error('raw ' + k);
  t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(v));
  j[k] = v;
};
const add = (after, k, v) => {
  if (k in j) throw new Error('already there: ' + k);
  const anchor = JSON.stringify(after) + ': ' + JSON.stringify(j[after]);
  if (!t.includes(anchor)) throw new Error('anchor ' + after);
  const line = t.slice(t.lastIndexOf('\n', t.indexOf(anchor)) + 1, t.indexOf(anchor));
  t = t.replace(anchor + ',', anchor + ',\n' + line + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
  j[k] = v;
};

set('projecthero.andy.ability.regen_speed.desc', 'Shift+V cycles Normal, Fast and Burst. Each part pays its food as it starts regrowing (saturation first, then the hunger bar): your whole body costs 10 food on Normal (about 8.5s a limb), 15 on Fast (about 3.4s) and 20 on Burst (about 1.7s). A part you can not pay for grows at half the Normal speed.');
set('projecthero.andy.ability.bone_sword.desc', 'Hold V for 2 seconds: you reach over, grab your off arm and wrench at it until it tears off, then crush it in your grip until it bursts, leaving a sword of bone with a guard of blood in your hand. 9 damage a hit, a sweep, a little more reach, and a new set of moves on your keys. The arm grows back once you put the sword away (V, or after 60s). Let go of V early and you let go of the arm. Needs both arms. 3s cooldown after.');
add('projecthero.andy.ability.bone_sword.desc', 'projecthero.andy.ability.bone_sword_away', 'Put Away');
add('projecthero.andy.ability.bone_sword_away', 'projecthero.andy.ability.bone_sword_away.desc', 'The sword bursts into blood and bone and the arm grows back. 3s before you can draw it again.');
add('projecthero.andy.ability.bone_sword_away.desc', 'projecthero.andy.ability.tear_off', 'Tear Off');
add('projecthero.andy.ability.tear_off', 'projecthero.andy.ability.tear_off.desc', 'Tap V: rip off your most damaged arm or leg (your off arm if none is hurt) and throw it away, and with it go fire, poison, wither, slowness and every other harmful effect on you. It grows back as usual, and costs its food like any regrowth. Never your last hand. 8s cooldown.');
add('message.projecthero.andy.no_spare_arm', 'message.projecthero.andy.nothing_to_tear', 'Nothing to tear off with: you need a hand');
add('hud.projecthero.andy.bone_sword', 'hud.projecthero.andy.hold', 'Hold: %s');

const g = j['projecthero.guide.andy.regen.body'];
const gOld = 'Tap V to cycle the speed:';
if (!g.includes(gOld)) throw new Error('guide regen text');
set('projecthero.guide.andy.regen.body', g.replace(gOld, 'Shift+V cycles the speed:'));
set('projecthero.guide.andy.bone_sword.body', 'Hold V: you reach across, grab your off arm at the shoulder and wrench at it, straining, while it slowly gives (the V box fills red). After 2 seconds it tears off; you grip it hard until it bursts in your hand in a spray of blood and bone, and what is left is a sword: the arm\'s bone is the blade, streaked with blood that drips off it, with a crossguard of congealed blood and the torn skin wrapped round the grip. Let go of V before then and you let go of the arm. Left click hits for 9 and sweeps like a sword, with a little more reach. While it is out your keys change to the sword\'s own moves (see Bone Sword moves) and the arm does not grow back. Tap V, or after 60 seconds, and the sword bursts into blood and bone and the arm regrows. You need both arms to draw it. A plain tap of V is Tear Off instead: you rip off your most damaged limb, and whatever is burning or poisoning you goes with it.');

JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
