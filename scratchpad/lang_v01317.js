// v0.13.17 guide / menu text: set (or add) lang values by key, keeping the file's layout.
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const set = {
  'projecthero.guide.hulk.rage.body': 'Your Rage bar (0-100) sits in the bottom-right HUD as "Rage 62%". Every point of damage you take is 1% rage (take 5 damage, gain 5%) -- as Banner, the damage you deal yourself builds nothing. Go 5 seconds without being hurt and Banner cools off at 2% a second. Past 75 Banner starts to give off green gamma -- more of it, with a quickening heartbeat, the closer he gets to 100 -- and the bar throbs. After the Hulk shrinks back the bar reads "Exhausted 8s" and runs down with the timer.',
  'projecthero.guide.hulk.change.body': 'There are two ways the Hulk comes out. By choice: at 75 Rage press H -- a quick change (a second and a half), and he is yours to command for as long as he lasts; you never have to fight him for control. Against your will: at 100 Rage (or when the Hulk refuses to die) Banner drops to his knees clutching his head and the change takes him slowly -- about five seconds of growing while the Hulk takes him over, then he rises and roars. You can\'t move, fight or be hurt while it happens, and an unwilling Hulk has to be kept under control (see Keeping control). As the Hulk, every hit you take still adds 1% per point of damage and every hit you land adds 2% (punches, and each thing your abilities hit). Only once you have been out of combat for 5 seconds does his rage burn down, 0.75% a second. At 0 you shrink back to Banner, exhausted: Weakness and Slowness for 8 seconds, and no Rage builds until it passes.',
  'projecthero.guide.hulk.death_save.body': 'Banner cannot be killed: every hit that would kill a Gamma player as Banner brings the Hulk bursting out instead, at full health with a full rage bar -- no cooldown, every time. It counts as the unwilling change, so you will have to keep him under control. To kill a Gamma player you have to beat the Hulk: a Hulk who takes a killing blow dies. The small dot beside the rage bar is bright while you are protected (Banner) and dark as the Hulk. /kill and the void still kill, and so does dying inside a Titan or in All Might\'s Power Form (the Hulk can\'t come out there).',
  'projecthero.hulk.ability.grab.desc': 'V picks up the mob you are looking at (6 blocks) and holds it overhead; V again throws it -- it smashes into whatever it hits (16 to it and everything round it). Shift+V while holding one crushes it for 26. Squad-mates can be picked up too: carried safely overhead, set down with Shift+V (or they sneak to get down), or thrown -- they bowl over enemies and land without fall damage. Bosses and anything too big can\'t be picked up. 8 s cooldown.',
  'projecthero.guide.hulk.sprint_smash.body': 'Sprinting as the Hulk into soft blocks (dirt, wood, stone, ores -- anything up to hardness 3, and cobwebs) smashes straight through them. Webs never slow the Hulk down, and sprinting tears them apart. Never chests or other block entities. The server can turn it off in config/projecthero_hulk.json.',
  'projecthero.guide.hulk.riding.body': 'A squad-mate can right-click the Hulk to climb onto his back -- one at a time. Sneak to hop off. The rider is thrown off when he changes back or starts to rampage. The Hulk can also pick a squad-mate up with V (see Grab).',
  'projecthero.guide.hulk.control.body': 'Only a Hulk who came out on his own (Rage hit 100, or he refused to die) fights you for control -- one you let out with H never does. You control him as long as he keeps hitting things. Go 8 seconds without landing a hit and control starts to slip: a key prompt appears (forward, left, back or right) -- press it in time to claw control back; the wrong key or none loses more. At 0 control the Hulk RAMPAGES on his own for 15 seconds: he hunts down the nearest living thing out in the open up to 100 blocks away (never anything down in caves), leaps to it, pounds it, and smashes the ground and anything soft in his way, and your abilities are locked out. Then you wrestle back control. Anyone riding him is thrown off.',
  'message.projecthero.hulk.carried': '%s picked you up! Sneak to get down.',
};
const nl = s.includes('\r\n') ? '\r\n' : '\n';
for (const [k, v] of Object.entries(set)) {
  const re = new RegExp('^(\\s*)"' + k.replace(/\./g, '\\.') + '": ".*?",?$', 'm');
  const m = s.match(re);
  if (!m) {
    // new key: add after the death-save message
    const anchor = /^(\s*)"message\.projecthero\.hulk\.death_save": ".*?",$/m;
    const a = s.match(anchor);
    if (!a) throw new Error('no anchor for ' + k);
    s = s.replace(anchor, a[0] + nl + a[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
    continue;
  }
  const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
  s = s.replace(re, m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma);
}
JSON.parse(s);
fs.writeFileSync(file, s);
console.log('ok', Object.keys(set).length);
