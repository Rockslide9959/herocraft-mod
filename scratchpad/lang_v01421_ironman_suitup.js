// v0.14.21 Iron Man suit-up revamp: en_us.json keys + sounds.json events. Idempotent (sets or adds by key, keeps the
// files' layout and line endings).  Run from the repo root:  node scratchpad/lang_v01421_ironman_suitup.js
const fs = require('fs');

// ---------------------------------------------------------------- lang
const LANG = 'src/main/resources/assets/projecthero/lang/en_us.json';
const set = {
  'item.projecthero.mark_v_suitcase.contents': 'Holds %s of 4 Mark V pieces',
  'entity.projecthero.iron_man_delivery_pod': 'Mark VII Delivery Pod',
  'entity.projecthero.iron_man_suit_part': 'Iron Man Armour Piece',
  'message.projecthero.subtitle.ironman_servo': 'Armour servos whir',
  'message.projecthero.subtitle.ironman_clamp': 'Armour plate locks on',
  'message.projecthero.subtitle.ironman_release': 'Armour plate unlatches',
  'message.projecthero.subtitle.ironman_faceplate_seal': 'Faceplate seals',
  'message.projecthero.subtitle.ironman_faceplate_open': 'Faceplate opens',
  'message.projecthero.subtitle.ironman_power_up': 'Suit powers up',
  'message.projecthero.subtitle.ironman_thruster': 'Thrusters roar',
  'message.projecthero.subtitle.ironman_pod_land': 'Delivery pod lands',
  'message.projecthero.subtitle.ironman_case_unfold': 'Suitcase unfolds',
  'projecthero.guide.iron_man.suit_up': 'Suiting up',
  'projecthero.guide.iron_man.suit_up.body': 'Every piece now builds onto you instead of popping on: its plates sweep into place behind a white-hot edge and lock home with a clank, and coming off they break away the same way in reverse. Arms out while the suit assembles; the faceplate swings shut at the end (H swings it open and shut). Called armour curves in from its platform and lines up on your body before it clamps on. At a Suit Platform, Deploy lifts each piece off the rack onto you (boots first) and sneak + right-click sends them back onto the rack one by one -- stay within a few blocks or it stops where it is (nothing is ever lost). The Mark VII is delivered by a pod that lands behind you, opens and fires the pieces onto you. The Mark V unfolds out of its suitcase in your hand and folds back into it. Enchantments and names on your armour stay with it wherever it goes.',
};
let s = fs.readFileSync(LANG, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
for (const [k, v] of Object.entries(set)) {
  // [ \t]* not \s*: with CRLF, ^ also matches between \r and \n, and \s* would swallow the \n into the indent
  const re = new RegExp('^([ \\t]*)"' + k.replace(/\./g, '\\.') + '": ".*?",?$', 'm');
  const m = s.match(re);
  if (m) {
    const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
    s = s.replace(re, () => m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma);
    continue;
  }
  const anchor = /^([ \t]*)"projecthero\.guide\.iron_man\.controls": ".*?",$/m;
  const a = s.match(anchor);
  if (!a) throw new Error('no anchor for ' + k);
  s = s.replace(anchor, () => a[0] + nl + a[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
}
JSON.parse(s);
fs.writeFileSync(LANG, s);

// ---------------------------------------------------------------- sounds (vanilla files only -- no OGG encoder here)
const SOUNDS = 'src/main/resources/assets/projecthero/sounds.json';
const snd = (name, volume, pitch) => ({ name, volume, pitch });
const events = {
  ironman_servo: [snd('minecraft:tile/piston/out', 0.5, 1.6), snd('minecraft:tile/piston/in', 0.5, 1.8),
    snd('minecraft:block/crafter/craft', 0.6, 1.4)],
  ironman_clamp: [snd('minecraft:item/armor/equip_netherite1', 1.0, 0.9), snd('minecraft:item/armor/equip_netherite2', 1.0, 0.9),
    snd('minecraft:item/armor/equip_netherite3', 1.0, 0.85), snd('minecraft:block/lodestone/lock1', 0.8, 1.2),
    snd('minecraft:block/lodestone/lock2', 0.8, 1.2)],
  ironman_release: [snd('minecraft:tile/piston/in', 0.6, 1.3), snd('minecraft:block/vault/eject1', 0.7, 1.3),
    snd('minecraft:block/vault/eject2', 0.7, 1.3)],
  ironman_faceplate_seal: [snd('minecraft:block/iron_trapdoor/close1', 0.8, 1.4), snd('minecraft:block/iron_trapdoor/close2', 0.8, 1.4),
    snd('minecraft:block/vault/insert', 0.8, 1.3)],
  ironman_faceplate_open: [snd('minecraft:block/iron_trapdoor/open1', 0.8, 1.4), snd('minecraft:block/iron_trapdoor/open2', 0.8, 1.4)],
  ironman_power_up: [snd('minecraft:block/beacon/power1', 0.7, 1.6), snd('minecraft:block/beacon/power2', 0.7, 1.6),
    snd('minecraft:block/beacon/power3', 0.7, 1.6)],
  ironman_thruster: [snd('minecraft:fireworks/launch1', 1.0, 0.6), snd('minecraft:mob/breeze/charge1', 0.8, 0.7),
    snd('minecraft:mob/breeze/charge2', 0.8, 0.7)],
  ironman_pod_land: [snd('minecraft:item/mace/smash_ground1', 1.0, 0.8), snd('minecraft:item/mace/smash_ground2', 1.0, 0.8),
    snd('minecraft:block/heavy_core/break1', 1.0, 0.9)],
  ironman_case_unfold: [snd('minecraft:block/vault/eject1', 0.8, 1.2), snd('minecraft:block/vault/eject3', 0.8, 1.2),
    snd('minecraft:tile/piston/out', 0.6, 1.4)],
};
let raw = fs.readFileSync(SOUNDS, 'utf8');
const crlf = raw.includes('\r\n');
const json = JSON.parse(raw);
for (const [k, list] of Object.entries(events)) {
  json[k] = { subtitle: 'message.projecthero.subtitle.' + k, sounds: list };
}
let out = JSON.stringify(json, null, 2);
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(SOUNDS, out);
console.log('ok', Object.keys(set).length, 'lang keys,', Object.keys(events).length, 'sound events');
