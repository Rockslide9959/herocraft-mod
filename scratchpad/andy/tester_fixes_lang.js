// v0.17.3: Andy tester fixes -- lang (numbers, Off, empty stomach, alternating hands, head pickup, healing). Run from the repo root.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
let j = JSON.parse(t);
const raw = (k) => JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
function set(k, v) {
  if (!(k in j)) throw new Error('no key ' + k);
  const old = raw(k);
  if (t.split(old).length !== 2) throw new Error('raw not unique ' + k);
  t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(v)); j[k] = v;
}
function add(after, k, v) {
  if (k in j) throw new Error('duplicate ' + k);
  const old = raw(after);
  if (t.split(old).length !== 2) throw new Error('anchor ' + after);
  t = t.replace(old + ',\n', old + ',\n  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n'); j[k] = v;
}
function sub(k, a, b) { if (!j[k].includes(a)) throw new Error(k + ' missing ' + a); set(k, j[k].replace(a, b)); }

add('projecthero.andy.speed.starving', 'projecthero.andy.speed.off', 'Off');
add('projecthero.andy.speed.off', 'projecthero.andy.speed.empty', 'Empty stomach');

sub('projecthero.andy.ability.parts_bullet.desc', '7 damage', '6 damage');
sub('projecthero.andy.ability.parts_bullet.desc', 'then your other arm shoots.', 'your left and right hands take turns.');
sub('projecthero.andy.ability.crimson_bullet.desc', '12 damage and a big knockback, 1s cooldown', '10 damage and a big knockback, 0.75s cooldown');
sub('projecthero.andy.ability.stretch_punch.desc', '20 damage and a big knockback', '14 damage and a big knockback');
sub('projecthero.andy.ability.stretch_punch.desc', 'If that arm is gone, the other one is fired. 1s cooldown.', 'Your left and right arms take turns (if one is gone, the other is fired). 0.75s cooldown.');
set('projecthero.andy.ability.regen_speed.desc', 'Shift+V cycles Normal, Fast, Burst and Off. Each part pays its food as it starts regrowing (saturation first, then the hunger bar): your whole body costs 10 food on Normal (about 8.5s a limb), 15 on Fast (about 3.4s) and 20 on Burst (about 1.7s). The speed also sets how soon and how fast your wounds knit and your health comes back after a hit (Burst: 3 health a second). A part you can not pay for grows at half the Normal speed; with a completely empty stomach nothing but your head grows back until you eat. Off: nothing grows back or heals.');
sub('projecthero.andy.ability.dead_line.desc', '4 damage every 0.2s', 'about 4 damage every 0.2s (anywhere from 2 to 7, and now and then it misses)');
sub('projecthero.andy.ability.dead_line.desc', '3s cooldown after.', '2.5s cooldown after.');
set('projecthero.andy.ability.vortex_bullet.desc', 'Twist off an arm (left and right in turn) and fire it as a spinning drill: 13 damage, through up to 3 enemies, and it sticks in the wall it reaches. The arm regrows. 4s cooldown.');
set('projecthero.andy.ability.parts_bullet_head.desc', 'Launch your own head: 18 damage and a heavy knockback, then it drops and lies there. Your body stands headless (blind) until the head regrows, or until you or a friend right-click the head to put it straight back on. 8s cooldown.');
sub('projecthero.andy.ability.head_launch.desc', '12 damage and a heavy knockback', '18 damage and a heavy knockback');
sub('projecthero.andy.ability.head_launch.desc', '10s cooldown.', '8s cooldown.');
sub('projecthero.andy.ability.leg_launch.desc', '3s cooldown.', '2s cooldown.');
set('projecthero.andy.ability.crimson_crescent_moon.desc', 'Draw Kurikara out of your forearm in one quick slash: 11 damage to everything in front of you within 4.5 blocks. 1.75s cooldown.');
sub('projecthero.andy.ability.great_crescent_moon.desc', '20s cooldown.', '15s cooldown.');

sub('projecthero.guide.andy.regen.body', 'Shift+V cycles the speed: Normal', 'Shift+V cycles the speed: Off (nothing grows back or heals, just for fun), Normal');
sub('projecthero.guide.andy.regen.body', 'A part you can not pay for in full still grows, but at half the Normal speed whatever the mode.',
  'The speed also heals you: after a hit your wounds knit and your health comes back sooner and faster the faster the speed (Burst: 3 health a second, a little hunger for each). A part you can not pay for in full still grows, but at half the Normal speed whatever the mode; with no hunger and no saturation left at all nothing but your head grows back until you eat.');
sub('projecthero.guide.andy.headonly.body', 'You get faster with every stage, and you stay small until there is room to stand up.',
  'You get faster with every stage, and you stay small until there is room to stand up. While you are only a head anyone can pick you up: they right-click you with an empty hand and carry you under their arm; Sneak to get down (you climb down by yourself once your body is back).');
sub('projecthero.guide.andy.commands.body', 'regen <slow|normal|burst>', 'regen <normal|fast|burst|off>');
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
