// v0.15.15 Nova lang keys (helmet suit-up, held Force Field, Overload rework, damage / cost retune). Idempotent: adds
// missing keys before the closing brace of en_us.json and rewrites existing ones in place. Supersedes the same keys in
// lang_v01513_nova.js (run this one after it). Run from the repo (or worktree) root: node scratchpad/lang_v01515_nova.js [root]
const fs = require('fs');
const path = require('path');
const root = process.argv[2] || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const L = {
  'message.projecthero.nova.blast_empty': 'The Nova Force runs dry -- the beam fades',
  'message.projecthero.nova.shield_empty': 'The Nova Force runs dry -- the Force Field fades',

  'hud.projecthero.nova.overload': 'OVERLOAD: infinite Force  %s s',
  'hud.projecthero.nova.force_slow': '%s / %s  slow refill %ss',

  'projecthero.nova.ability.nova_blast.desc': 'Hold: a golden beam from your hand (32 blocks) that hits for 10 damage twice a second -- 20 a second -- for as long as you hold R, until the Nova Force runs out. 6 Force a second, 1.5 s cooldown after.',
  'projecthero.nova.ability.bolt_volley.desc': 'Five homing golden bolts, 8 damage each; they seek the enemies nearest your crosshair. 20 Force, 6 s cooldown.',
  'projecthero.nova.ability.gravimetric_pulse.desc': 'A 6-block shockwave all around you: 20 damage and everything is thrown into the air. 20 Force, 8 s cooldown.',
  'projecthero.nova.ability.gravity_slam.desc': 'From the air (2+ blocks up): dive straight down into an 8-block crater shockwave -- 10 to 30 damage, 1 more for every block you fell (full at 20). No blocks are broken. 25 Force, 12 s cooldown.',
  'projecthero.nova.ability.force_shield': 'Force Field',
  'projecthero.nova.ability.force_shield.desc': 'Hold: a golden bubble that absorbs every arrow, projectile and melee blow for as long as you hold Z. Drains 8 Force a second (needs 8 to raise), 1 s cooldown after.',
  'projecthero.nova.ability.nova_overload.desc': 'The ultimate. Needs a full bar and takes all of it: for 15 s every move deals double damage, the Nova Force is infinite and cooldowns are halved -- then a 30-damage nova burst 10 blocks round. Afterwards the bar is empty and refills at half speed for 60 s. 75 s cooldown.',
  'projecthero.nova.ability.comet_dash.desc': 'Ram 20 blocks along your look, 20 damage to everything in the way (flying: any direction). 15 Force, 6 s cooldown.',
  'projecthero.nova.ability.gravity_well.desc': 'Open a singularity where you aim (up to 24 blocks): for 3 s it drags every mob within 8 blocks in, then collapses for 35 damage. 25 Force, 25 s cooldown.',

  'projecthero.guide.nova.body': 'Richard Rider, Nova of the Nova Corps. The Nova Force lets you fly at 45 blocks a second, fire golden beams and bend gravity, and the Worldmind in your helmet senses every creature around you.',
  'projecthero.guide.nova.suit.body': 'H puts the Nova Corps uniform on or takes it off. Suiting up, you raise the Nova Corps Helmet in both hands and lower it onto your head, then the rest of the uniform materialises in gold from the neck down. Suiting down, the uniform fades from the feet up and you lift the helmet off. Your moves, flight and passives only work while it is on. Shift+H still opens the power wheel. The uniform is not an item: it can never be dropped or lost, and armour you wear is hidden under it (it still protects you).',
  'projecthero.guide.nova.force.body': 'A 100-point bar that refills 3 a second -- half that (1.5) while you fly. Every move spends some of it, flying fast (sprinting) drains 2 a second, and NOVA OVERLOAD needs it completely full. The bar outline turns cyan when it is full. After an Overload the bar is empty, dimmed, and refills at half speed for 60 s (a quarter speed while flying).',
  'projecthero.guide.nova.flight.body': 'Double-tap jump in the air while suited. 20 blocks a second, 45 sprinting: look where you want to go, Space and Sneak to rise and sink, let go to hover. A golden trail streams from your feet; touching the ground lands you.',
  'projecthero.guide.nova.passives.body': '+8 melee damage, 60% less damage from everything, no fall damage, and the Worldmind outlines every mob within 32 blocks in gold -- through walls, and only you see it.',
};

let text = fs.readFileSync(file, 'utf8');
const crlf = text.includes('\r\n');
text = text.replace(/\r\n/g, '\n');
const esc = (s) => JSON.stringify(s);
let added = 0;
let changed = 0;
for (const [k, v] of Object.entries(L)) {
  const re = new RegExp('^(\\s*)' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':\\s*".*?"(,?)$', 'm');
  if (re.test(text)) {
    text = text.replace(re, (m, ind, comma) => ind + esc(k) + ': ' + esc(v) + comma);
    changed++;
  } else {
    const end = text.lastIndexOf('}');
    const before = text.slice(0, end).replace(/\s*$/, '');
    text = before + ',\n  ' + esc(k) + ': ' + esc(v) + '\n}\n';
    added++;
  }
}
JSON.parse(text); // must still be valid
if (crlf) text = text.replace(/\n/g, '\r\n');
fs.writeFileSync(file, text, 'utf8');
console.log('nova v0.15.15 lang: added ' + added + ', updated ' + changed);
