// v0.17.3 NBC-style regeneration: lang -- CRLF-safe, refuses duplicate keys, validates the JSON.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
let j = JSON.parse(t);
const raw = k => JSON.stringify(k) + ': ' + JSON.stringify(j[k]);
function set(k, v) {
  if (!(k in j)) throw new Error('no key ' + k);
  const old = raw(k);
  const n = (t.split(old).length - 1);
  if (n !== 1) throw new Error('raw ' + k + ' x' + n);
  t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(v)); j[k] = v;
}
function del(k) {
  if (!(k in j)) return;
  const line = '  ' + raw(k) + ',\n';
  if (!t.includes(line)) throw new Error('del ' + k);
  t = t.replace(line, ''); delete j[k];
}
function add(after, pairs) {
  const anchor = '  ' + raw(after) + ',\n';
  if (!t.includes(anchor)) throw new Error('anchor ' + after);
  let ins = '';
  for (const [k, v] of pairs) {
    if (k in j) throw new Error('duplicate ' + k);
    ins += '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n';
    j[k] = v;
  }
  t = t.replace(anchor, anchor + ins);
}
set('message.projecthero.andy.acquired_hint', 'Every part of you breaks and regrows in about a second. Tap V to force your body back together, Shift+V to turn auto-regrowth on or off, Shift + hold V for the Regen Menu, hold V to tear off an arm as a bone sword.');
del('message.projecthero.andy.speed');
del('message.projecthero.andy.reform_no_food');
for (const s of ['slow', 'normal', 'fast', 'burst', 'starving', 'off', 'empty']) del('projecthero.andy.speed.' + s);
del('item.projecthero.andy_suit_chestplate.tooltip');
del('message.projecthero.andy.auto_feed');
set('projecthero.andy.ability.regen_speed', 'Toggle Regen');
set('projecthero.andy.ability.regen_speed.desc', 'Shift+V: auto-regrowth on or off. On, every lost part grows straight back, one after another (your body once your head is there, your arms and legs once the body is, your hands and feet once their limbs are), about a second a part, free. Off, nothing starts growing back on its own (a part already growing finishes): grow your parts a stage at a time in the Regen Menu (Shift + hold V).');
set('projecthero.andy.ability.reform.desc', j['projecthero.andy.ability.reform.desc'].replace(/ It costs the food[^.]*\.[^.]*\./, ' Free.').replace(/ It costs the food[\s\S]*$/, ' Free. 20s cooldown.'));
set('projecthero.andy.ability.crimson_bullet.desc', j['projecthero.andy.ability.crimson_bullet.desc'].replace(', and your regeneration runs 2.5x slower for 15s.', '.'));
set('projecthero.guide.andy.suit.body', 'His suit: chestplate, leggings and boots (no helmet: your own face and hair show), with iron armour\'s protection. Craft it: iron, bones and red dye in the iron armour shapes. Worn, it mends itself, one point every 2 seconds. Like any armour on you, it falls off a part you lose: the chestplate with your torso, the leggings with a leg, the boots with a foot (and you can\'t put it back on until the part has grown back); losing an arm tears that shoulder pad off the chestplate.');
set('projecthero.guide.andy.commands.body', '/projecthero power grant andy [player]. /projecthero power tune andy break <head|body|right_arm|left_arm|right_leg|left_leg|all>, restore, regen <on|off>.');
set('projecthero.guide.andy.regen.body', 'Your body is ten segments: head, body, both arms and both legs (down to the wrist and ankle), both hands and both feet. A lost segment grows straight back, one frame a tick: about a second each (head 0.85s, body 1s, arm 0.9s, leg 1s, hand 0.6s, foot 0.55s), first as bone, then flesh, then skin. They grow in order, like a real body: the body once the head has started, the arms and legs once the body is a third grown, each hand and foot once its limb is a quarter grown. It is free: no hunger. After a hit your wounds knit and your health comes back on its own. Shift+V turns auto-regrowth off: then nothing starts growing back by itself and you grow your parts a stage at a time in the Regen Menu. Tap V to Reform: every missing or regrowing part whole on the spot, 3 hearts healed and fire, poison and wither gone (20 second cooldown, works with auto-regrowth off).');
set('projecthero.guide.andy.headonly.body', 'With only your head left you roll about on the ground as a head while the rest grows back in order: your torso out of your neck first, your arms and legs once it is a third grown (lying on it, you belly-crawl and then walk on your hands while the legs grow), then your hands and feet. From just a head you are walking again in about 2.5 seconds.');
add('hud.projecthero.andy.regrowth', [
  ['projecthero.andy.regen.auto', 'Auto'],
  ['projecthero.andy.regen.manual', 'Manual'],
  ['hud.projecthero.andy.menu', 'Regen Menu'],
  ['message.projecthero.andy.regen_auto_on', 'Auto-regrowth on: everything grows back'],
  ['message.projecthero.andy.regen_auto_off', 'Auto-regrowth off: grow your parts in the Regen Menu (Shift + hold V)'],
  ['message.projecthero.andy.menu_open', 'Regen Menu: R/G arms, Z/X legs, C body (Shift: hands, feet, head); V eject head, hold V whole body'],
  ['message.projecthero.andy.menu_closed', 'Regen Menu closed'],
  ['message.projecthero.andy.menu_needs', 'Grow your %s further first'],
  ['message.projecthero.andy.armour_refused', 'Nothing to wear it on until that part has grown back'],
  ['projecthero.andy.seg.-1', 'nothing'],
  ['projecthero.andy.seg.0', 'head'],
  ['projecthero.andy.seg.1', 'body'],
  ['projecthero.andy.seg.2', 'right arm'],
  ['projecthero.andy.seg.3', 'left arm'],
  ['projecthero.andy.seg.4', 'right leg'],
  ['projecthero.andy.seg.5', 'left leg'],
  ['projecthero.andy.seg.6', 'right hand'],
  ['projecthero.andy.seg.7', 'left hand'],
  ['projecthero.andy.seg.8', 'right foot'],
  ['projecthero.andy.seg.9', 'left foot'],
  ['projecthero.andy.ability.regen_menu', 'Regen Menu'],
  ['projecthero.andy.ability.regen_menu.desc', 'Shift + hold V: the ability keys grow or strip your segments. R right arm, G left arm, Z right leg, X left leg, C body; with Shift: right hand, left hand, right foot, left foot, head. With auto-regrowth off each press grows the part to its next stage: bone, then flesh, then whole (so you can keep a part at bone). A press on a whole part strips it off, with everything that grows from it (the head only back to bone). V: Eject Head; hold V: Regenerate Whole Body. Shift + hold V again closes it.'],
  ['projecthero.andy.ability.regen_eject', 'Eject Head'],
  ['projecthero.andy.ability.regen_eject.desc', 'In the Regen Menu, tap V: everything but your head comes off and the head shoots where you look (the Head Launch).'],
  ['projecthero.andy.ability.regen_whole', 'Regenerate Whole Body'],
  ['projecthero.andy.ability.regen_whole.desc', 'In the Regen Menu, hold V for 2 seconds: everything grows back now, auto-regrowth on or off.'],
  ...[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].flatMap(i => {
    const n = ['Head', 'Body', 'Right Arm', 'Left Arm', 'Right Leg', 'Left Leg', 'Right Hand', 'Left Hand', 'Right Foot', 'Left Foot'][i];
    return [['projecthero.andy.ability.regen_seg_' + i, n],
      ['projecthero.andy.ability.regen_seg_' + i + '.desc', 'Regen Menu: grow your ' + n.toLowerCase() + ' to its next stage (bone, flesh, whole) with auto-regrowth off, or strip it off if it is whole.']];
  }),
]);
JSON.parse(t);
// no duplicate keys anywhere
const seen = new Set();
for (const m of t.matchAll(/^  ("(?:[^"\\]|\\.)*"):/gm)) { const k = JSON.parse(m[1]); if (seen.has(k)) throw new Error('dup ' + k); seen.add(k); }
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok', Object.keys(j).length);
