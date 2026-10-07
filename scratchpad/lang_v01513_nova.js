// v0.15.13 Nova lang keys. Idempotent: adds missing keys before the closing brace of en_us.json and rewrites existing
// ones in place. Run from the repo (or worktree) root: node scratchpad/lang_v01513_nova.js [root]
const fs = require('fs');
const path = require('path');
const root = process.argv[2] || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const L = {
  'item.projecthero.nova_corps_helmet': 'Nova Corps Helmet',
  'item.projecthero.nova_corps_helmet.tooltip': 'The Nova Force still hums inside it',
  'item.projecthero.nova_corps_helmet.tooltip2': 'Use: become Nova (replaces your hero power)',
  'item.projecthero.nova_centurion_spawn_egg': 'Nova Corps Centurion Spawn Egg',
  'entity.projecthero.nova_centurion': 'Dying Nova Corps Centurion',
  'entity.projecthero.nova_centurion.speaker': '[Centurion] ',
  'entity.projecthero.nova_centurion.say.idle1': '...you... come closer... please...',
  'entity.projecthero.nova_centurion.say.idle2': 'The pod went down hard... Xandar must be warned...',
  'entity.projecthero.nova_centurion.say.idle3': 'The Nova Force... it cannot die with me...',
  'entity.projecthero.nova_centurion.say.idle4': '...Corpsman down... is anyone... there...',
  'entity.projecthero.nova_centurion.say.handoff': 'Take it... the helmet. The Force will choose you... Protect them... all of them...',
  'entity.projecthero.nova_centurion.say.already': 'You already carry the Force... go... find the others...',

  'message.projecthero.nova.title': 'NOVA',
  'message.projecthero.nova.subtitle': 'The Nova Force chooses you',
  'message.projecthero.nova.acquired': 'You carry the Nova Force -- you are Nova of the Nova Corps.',
  'message.projecthero.nova.acquired_hint': 'H puts the uniform on. Double-tap jump in the air to fly. R G Z X C V (and Shift+key) are your powers.',
  'message.projecthero.nova.not_suited': 'Put the Nova Corps uniform on first (H)',
  'message.projecthero.nova.cooldown': '%s is ready in %s s',
  'message.projecthero.nova.low_force': 'Not enough Nova Force (%s needed, %s left)',
  'message.projecthero.nova.blast_empty': 'The Nova Force runs dry -- the beam fades',
  'message.projecthero.nova.slam_needs_air': 'Gravity Slam needs you up in the air',
  'message.projecthero.nova.overload_needs_full': 'NOVA OVERLOAD needs a full bar (%s / 100)',
  'message.projecthero.nova.overload': 'NOVA OVERLOAD!',
  'message.projecthero.nova.launch_no_target': 'No enemy close enough to grab',
  'message.projecthero.nova.scan': 'Worldmind: %s creatures found -- strongest: %s',
  'message.projecthero.nova.scan_none': 'none',
  'message.projecthero.nova_helmet.already': 'You already carry the Nova Force',
  'message.projecthero.nova_helmet.received': 'The Nova Corps Helmet -- use it to take up the Nova Force',

  'hud.projecthero.nova.title': 'NOVA',
  'hud.projecthero.nova.force': 'Nova Force: %s / %s',
  'hud.projecthero.nova.overload': 'OVERLOAD  %s s',
  'hud.projecthero.nova.suit_hint': 'H: suit up',
  'hud.projecthero.nova.flying': 'Flying',
  'hud.projecthero.nova.shift': 'Shift moves',
  'projecthero.squad.identity.nova': 'Nova',

  'projecthero.nova.ability.nova_blast': 'Nova Blast',
  'projecthero.nova.ability.nova_blast.desc': 'Hold: a golden beam from your hand, 8 damage a second for as long as you hold it (up to 8 s, 32 blocks). 6 Nova Force a second, 1.5 s cooldown after.',
  'projecthero.nova.ability.bolt_volley': 'Nova Bolt Volley',
  'projecthero.nova.ability.bolt_volley.desc': 'Five homing golden bolts, 6 damage each; they seek the enemies nearest your crosshair. 20 Force, 6 s cooldown.',
  'projecthero.nova.ability.gravimetric_pulse': 'Gravimetric Pulse',
  'projecthero.nova.ability.gravimetric_pulse.desc': 'A 6-block shockwave all around you: 10 damage and everything is thrown into the air. 20 Force, 8 s cooldown.',
  'projecthero.nova.ability.gravity_slam': 'Gravity Slam',
  'projecthero.nova.ability.gravity_slam.desc': 'From the air (2+ blocks up): dive straight down into an 8-block crater shockwave -- 6 to 18 damage, more the higher you fell from. No blocks are broken. 25 Force, 12 s cooldown.',
  'projecthero.nova.ability.force_shield': 'Force Shield',
  'projecthero.nova.ability.force_shield.desc': 'A golden bubble for 4 s that absorbs every arrow, projectile and melee blow. 25 Force, 15 s cooldown.',
  'projecthero.nova.ability.nova_overload': 'NOVA OVERLOAD',
  'projecthero.nova.ability.nova_overload.desc': 'The ultimate. Needs a full bar and takes all of it: for 10 s every move is free, deals +50% and its cooldown is halved -- then a 30-damage nova burst 10 blocks round. 90 s cooldown.',
  'projecthero.nova.ability.comet_dash': 'Comet Dash',
  'projecthero.nova.ability.comet_dash.desc': 'Ram 20 blocks along your look, 14 damage to everything in the way (flying: any direction). 15 Force, 6 s cooldown.',
  'projecthero.nova.ability.orbital_launch': 'Orbital Launch',
  'projecthero.nova.ability.orbital_launch.desc': 'Grab the nearest enemy within 8 blocks, rocket 30 blocks up with it and spike it into the ground: 20 damage on impact, plus the fall. 30 Force, 20 s cooldown.',
  'projecthero.nova.ability.gravity_well': 'Gravity Well',
  'projecthero.nova.ability.gravity_well.desc': 'Open a singularity where you aim (up to 24 blocks): for 3 s it drags every mob within 8 blocks in, then collapses for 20 damage. 30 Force, 14 s cooldown.',
  'projecthero.nova.ability.gravity_lock': 'Gravity Lock',
  'projecthero.nova.ability.gravity_lock.desc': 'Every mob within 10 blocks is lifted 3 blocks and frozen in the air for 4 s, then dropped. 35 Force, 20 s cooldown.',
  'projecthero.nova.ability.worldmind_scan': 'Worldmind Scan',
  'projecthero.nova.ability.worldmind_scan.desc': 'A 48-block pulse: every creature is outlined for 10 s (only you see it) and the strongest is marked red -- it takes +25% damage from everything. 15 Force, 20 s cooldown.',
  'projecthero.nova.ability.force_transfer': 'Nova Force Transfer',
  'projecthero.nova.ability.force_transfer.desc': 'Heal yourself and every squadmate within 8 blocks for 6 hearts. 40 Force (40% of the bar), 20 s cooldown.',

  'projecthero.guide.nova': 'Nova',
  'projecthero.guide.nova.tier': 'Hero-Tier Power -- Primary',
  'projecthero.guide.nova.body': 'Richard Rider, Nova of the Nova Corps. The Nova Force lets you fly at 40 blocks a second, fire golden beams and bend gravity, and the Worldmind in your helmet senses every creature around you.',
  'projecthero.guide.nova.origin': 'How to get it',
  'projecthero.guide.nova.origin.body': 'Somewhere in the world a Nova Corps pod has crashed: a small scorched crater with a gold-and-blue pod nose-down in it and a dying Centurion slumped against the hull. Right-click him and he hands you the Nova Corps Helmet, then fades away; use the helmet to take up the Nova Force. It is a Primary power, so it replaces the hero power you had. Crash sites are rare (about as rare as the Kryptonite crater); /locate structure projecthero:nova_pod_site finds the nearest.',
  'projecthero.guide.nova.suit': 'The uniform (H)',
  'projecthero.guide.nova.suit.body': 'H puts the Nova Corps uniform on or takes it off -- golden energy wraps you from the feet up. Your moves, flight and passives only work while it is on. Shift+H still opens the power wheel. The uniform is not an item: it can never be dropped or lost, and armour you wear is hidden under it (it still protects you).',
  'projecthero.guide.nova.force': 'The Nova Force',
  'projecthero.guide.nova.force.body': 'A 100-point bar that refills 4 a second, all the time. Every move spends some of it, flying fast (sprinting) drains 2 a second, and NOVA OVERLOAD needs it completely full. The bar outline turns cyan when it is full.',
  'projecthero.guide.nova.flight': 'Flight',
  'projecthero.guide.nova.flight.body': 'Double-tap jump in the air while suited. 20 blocks a second, 40 sprinting: look where you want to go, Space and Sneak to rise and sink, let go to hover. A golden trail follows you; touching the ground lands you.',
  'projecthero.guide.nova.passives': 'While suited',
  'projecthero.guide.nova.passives.body': '60% less damage from everything, no fall damage, and the Worldmind outlines every mob within 32 blocks in gold -- through walls, and only you see it.',
  'projecthero.guide.nova.controls': 'Controls',
  'projecthero.guide.nova.commands': 'Commands',
  'projecthero.guide.nova.commands.body': '/projecthero power grant nova [player] -- give the power. /projecthero nova force <0-100> -- set your Nova Force. /projecthero nova site -- build a crashed pod (with its Centurion) in front of you.',
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
console.log('nova lang: added ' + added + ', updated ' + changed);
