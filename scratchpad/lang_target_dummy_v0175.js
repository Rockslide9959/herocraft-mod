// v0.17.5 Target Dummy: adds the en_us keys key by key (never rewrites the file: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_target_dummy_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const S = 'projecthero.target_dummy.source.';
const K = {
  'item.projecthero.target_dummy': 'Target Dummy',
  'entity.projecthero.target_dummy': 'Target Dummy',
  'item.projecthero.target_dummy.desc': 'Right-click a block to place it. It never dies: hit it with anything to see damage numbers, DPS and totals. Right-click armour onto it to test armour. Sneak + right-click picks it up.',
  'item.projecthero.target_dummy.wearing': 'Wearing %s',
  'item.projecthero.target_dummy.cooldown': 'Hit cooldown: on (takes hits like a mob)',
  'projecthero.target_dummy.cooldown_on': 'Target Dummy: hit cooldown ON (takes hits like a mob, half a second apart)',
  'projecthero.target_dummy.cooldown_off': 'Target Dummy: hit cooldown OFF (every hit counts)',
  // the readout
  'projecthero.target_dummy.hud.idle': 'Target Dummy',
  'projecthero.target_dummy.hud.idle_hint': 'Hit it to test damage',
  'projecthero.target_dummy.hud.combat': 'Combat  %s s',
  'projecthero.target_dummy.hud.summary': 'Summary  %s s',
  'projecthero.target_dummy.hud.dps_total': 'DPS %s   Total %s',
  'projecthero.target_dummy.hud.hits_biggest': 'Hits %s   Biggest %s',
  'projecthero.target_dummy.hud.last': 'Last %s  %s',
  'projecthero.target_dummy.hud.last_raw': 'Last hit %s raw, %s through',
  'projecthero.target_dummy.hud.total_raw': 'Raw total %s, armour took %s',
  'projecthero.target_dummy.hud.cooldown': 'Hit cooldown on',
  // where a hit came from
  [S + 'fist']: 'Fist',
  [S + 'ability']: '%s ability',
  [S + 'direct']: 'True damage',
  [S + 'poison']: 'Poison',
  [S + 'inFire']: 'Fire',
  [S + 'onFire']: 'Burning',
  [S + 'lava']: 'Lava',
  [S + 'hotFloor']: 'Magma block',
  [S + 'magic']: 'Magic',
  [S + 'indirectMagic']: 'Magic',
  [S + 'wither']: 'Wither',
  [S + 'witherSkull']: 'Wither skull',
  [S + 'explosion']: 'Explosion',
  [S + 'explosion.player']: 'Explosion',
  [S + 'badRespawnPoint']: 'Explosion',
  [S + 'fireworks']: 'Fireworks',
  [S + 'lightningBolt']: 'Lightning',
  [S + 'freeze']: 'Freezing',
  [S + 'thorns']: 'Thorns',
  [S + 'mob']: 'Mob attack',
  [S + 'player']: 'Attack',
  [S + 'arrow']: 'Arrow',
  [S + 'trident']: 'Trident',
  [S + 'fireball']: 'Fireball',
  [S + 'thrown']: 'Thrown',
  [S + 'sonic_boom']: 'Sonic boom',
  [S + 'dragonBreath']: 'Dragon breath',
  [S + 'cactus']: 'Cactus',
  [S + 'sweetBerryBush']: 'Berry bush',
  [S + 'generic']: 'Damage',
  [S + 'fallingBlock']: 'Falling block',
  [S + 'anvil']: 'Anvil',
  [S + 'sting']: 'Sting',
  [S + 'campfire']: 'Campfire',
  [S + 'projecthero.alien_fire']: 'Alien fire',
  [S + 'projecthero.alien_strike']: 'Alien strike',
  [S + 'projecthero.golden_spin']: 'Golden Spin',
  [S + 'projecthero.kryptonite_strike']: 'Kryptonite',
  [S + 'projecthero.oathbreaker_execution']: 'Execution',
  [S + 'projecthero.omega_beam']: 'Omega Beam',
  [S + 'projecthero.omnitrix_extraction']: 'Omnitrix',
  [S + 'projecthero.serum_rejection']: 'Serum rejection',
  [S + 'projecthero.shazam_lightning']: 'Shazam lightning',
  // Guidebook (a section of the Devices chapter) + JEI info page
  'projecthero.guide.devices.target_dummy': 'Target Dummy',
  'projecthero.guide.devices.target_dummy.body': 'Target Dummy: a straw training dummy on a post for testing powers and gear. Right-click a block to stand it there, facing you. It never dies and never moves: every hit (swords, arrows, beams, explosions, fire, poison and every power) is measured and shrugged off, and punching, fire or explosions never break it. Only Sneak + right-click picks it back up, with its name and armour. Recipe: wool on top, sticks either side of a hay bale, a fence below.',
  'projecthero.guide.devices.target_dummy.stats': 'Each hit floats a number above it, coloured by kind: white melee, green projectile, purple magic, orange fire, red explosion, cyan lightning, blue power, gold and bigger for a critical hit. It sways harder the harder it is hit. Above its head: combat time, DPS, total damage, hits, biggest hit and the last hit with what dealt it ("Diamond Sword", "Arrow", "R ability", "Burning"). With armour on it also shows raw damage against what got through. A combat ends 5 s after the last hit; the summary stays up 5 s more, or while you look at it.',
  'projecthero.guide.devices.target_dummy.controls': 'Right-click a piece of armour onto it to test armour, and right-click it empty-handed to take off the piece you click. Right-click it empty-handed where it wears nothing to switch the hit cooldown: off (the default) every hit counts, even several in one tick; on, it takes hits the way a mob does (a full hit only every half second), so per-tick beams show what they really deal to mobs.',
};

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(text);
const add = [];
let replaced = 0;
for (const [k, v] of Object.entries(K)) {
  if (k in existing) {
    if (force && existing[k] !== v) {
      const re = new RegExp('(\\n  ' + JSON.stringify(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': )(".*?(?<!\\\\)")(,?)(\\r?)$', 'm');
      text = text.replace(re, (m, a, b, c, d) => a + JSON.stringify(v) + c + d);
      replaced++;
    }
    continue;
  }
  add.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)}`);
}
if (add.length) {
  const close = text.lastIndexOf('}');
  let body = text.slice(0, close).replace(/\s+$/, '');
  body += ',' + eol + add.join(',' + eol) + eol;
  text = body + '}' + text.slice(close + 1);
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log(`en_us: +${add.length} keys, ${replaced} replaced`);
