// v0.13.15 guide / menu text: set lang values by key, keeping the file's own layout and line endings.
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const set = {
  'projecthero.guide.hulk.rage.body': 'Your Rage bar (0-100) sits in the bottom-right HUD as "Rage 62%". It fills when you are hurt (2.5 per point of damage) and when you fight (0.8 per point you deal). Left alone for 15 seconds it slowly calms back down (0.5 a second). Past 75 Banner starts to give off green gamma -- more of it, with a quickening heartbeat, the closer he gets to 100 -- and the bar throbs. After the Hulk shrinks back the bar reads "Exhausted 8s" and runs down with the timer.',
  'projecthero.guide.hulk.change.body': 'There are two ways the Hulk comes out. By choice: at 75 Rage press H -- a quick change (a second and a half), and he is yours to command for as long as he lasts; you never have to fight him for control. Against your will: at 100 Rage (or when the Hulk refuses to die) Banner drops to his knees clutching his head and the change takes him slowly -- about five seconds of growing while the Hulk takes him over, then he rises and roars. You can\'t move, fight or be hurt while it happens, and an unwilling Hulk has to be kept under control (see Keeping control). As the Hulk, Rage burns down 1 a second -- but every hit you take pours 1.5 per point of damage back in, so the angrier the fight the longer he stays. At 0 you shrink back to Banner, exhausted: Weakness and Slowness for 8 seconds, and no Rage builds until it passes.',
  'projecthero.guide.hulk.control.body': 'Only a Hulk who came out on his own (Rage hit 100, or he refused to die) fights you for control -- one you let out with H never does. You control him as long as he keeps hitting things. Go 8 seconds without landing a hit and control starts to slip: a key prompt appears (forward, left, back or right) -- press it in time to claw control back; the wrong key or none loses more. At 0 control the Hulk RAMPAGES on his own for 15 seconds: he goes for the nearest living thing, pounds it, leaps about and smashes the ground and anything soft in his way, and your abilities are locked out. Then you wrestle back control. Anyone riding him is thrown off.',
  'projecthero.guide.hulk.looks.body': 'While he is out you are drawn as the Hulk model, 1.8 times your size, with his own idle, walk, run, clap, smash, leap and transformation animations. The change is seamless: the Hulk phases onto you as you grow and back off you as you shrink. The unwilling change has its own animation -- on your knees, shaking, then up into a roar. Your armour, cape and held items are hidden, and your first-person arm is green. Changing roars and shakes the ground.',
  'projecthero.guide.hulk.death_save.body': 'Once every 3 minutes, a hit that would kill a Gamma player doesn\'t: the Hulk bursts out (or, if he\'s already out, comes back roaring) at full health with a full rage bar. That counts as the unwilling change, so you will have to keep him under control. The small dot beside the rage bar is bright when it\'s ready. /kill and the void still kill.',
  'projecthero.hulk.ability.transform.desc': 'At 75+ rage: the willing change -- quick, and you stay in control the whole time. At 100 he comes out on his own: the slow, unwilling change on your knees, and then you have to keep control. Rage burns down 1 a second; every hit he takes pours some back.',
};
const nl = s.includes('\r\n') ? '\r\n' : '\n';
for (const [k, v] of Object.entries(set)) {
  const re = new RegExp('^(\\s*)"' + k.replace(/\./g, '\\.') + '": ".*?",?$', 'm');
  const m = s.match(re);
  if (!m) throw new Error('missing key ' + k);
  const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
  s = s.replace(re, m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma);
}
JSON.parse(s);
fs.writeFileSync(file, s);
console.log('ok', Object.keys(set).length, nl === '\r\n' ? 'crlf' : 'lf');
