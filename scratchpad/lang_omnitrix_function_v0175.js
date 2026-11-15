// v0.17.5 Omnitrix function mode + the Master Control code: adds the en_us keys key by key (never rewrites the file: other
// branches touch it too). Keeps the file's line endings and 2-space indent. Keys in REPLACE are rewritten in place (their text
// changed with the ritual's removal); keys in REMOVE (the v0.17.4 ritual's steps) are deleted line by line.
//   node scratchpad/lang_omnitrix_function_v0175.js [root]
const fs = require('fs');
const path = require('path');
const root = process.argv[2] || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // function mode, the readout, the HUD
  'hud.projecthero.omnitrix.function.title': 'FUNCTION MODE',
  'hud.projecthero.omnitrix.function.known': 'Master Control: %s',
  'hud.projecthero.omnitrix.function.help': 'Scroll: digit   Middle-click: next   Shift+Middle: back   Shift+Right-click: leave',
  'hud.projecthero.omnitrix.function.locked': 'LOCKED %s s',
  'hud.projecthero.omnitrix.function.denied': 'ACCESS DENIED',
  'hud.projecthero.omnitrix.function.accepted': 'CODE ACCEPTED',
  'hud.projecthero.omnitrix.self_destruct_code': 'SELF-DESTRUCT SEQUENCE',
  'hud.projecthero.omnitrix.self_destruct_left': 'Re-enter the code to cancel: %s s',
  'hud.projecthero.omnitrix.alt.key.shift_use': 'Shift+Right-click',
  'hud.projecthero.omnitrix.alt.function': 'Function mode: enter a code',
  'hud.projecthero.omnitrix.alt.key.middle': 'Middle-click',
  'hud.projecthero.omnitrix.alt.next_digit': 'Next digit (enter on the sixth); Shift: back',
  'hud.projecthero.omnitrix.alt.digit': 'Change the digit',
  'hud.projecthero.omnitrix.alt.leave_function': 'Leave function mode',
  // the server's answers
  'message.projecthero.omnitrix.code.locked': 'The watch is locked: %s s',
  'message.projecthero.omnitrix.code.wrong': 'ACCESS DENIED - locked for %s s',
  'message.projecthero.omnitrix.code.alien_found': 'DNA UNLOCKED: %s is on your dial',
  'message.projecthero.omnitrix.code.alien_known': '%s is already on your dial',
  'message.projecthero.omnitrix.code.flicker': 'The Omnitrix flickers...',
  'message.projecthero.omnitrix.code.fragment': 'The fragment glows against the watch...',
  'message.projecthero.omnitrix.code.digit': 'Master Control digit %s is %s  [ %s ]',
  'message.projecthero.omnitrix.code.alien': 'a hidden DNA code surfaces: %s',
  'message.projecthero.omnitrix.fragment.no_watch': 'Nothing happens. It needs an Omnitrix to read it.',
  'message.projecthero.omnitrix.fragment.nothing': 'Your watch already knows everything this fragment holds.',
  'message.projecthero.omnitrix.self_destruct.armed': 'SELF-DESTRUCT SEQUENCE ACTIVATED',
  'message.projecthero.omnitrix.self_destruct.cancelled': 'Self-destruct cancelled.',
  'message.projecthero.omnitrix.self_destruct.no_transform': 'The watch refuses to transform during the self-destruct countdown!',
  'message.projecthero.omnitrix.self_destruct.fizzle': '...pfft. The Omnitrix overheats.',
  'message.projecthero.omnitrix.self_destruct.fizzle_chat': 'The Omnitrix sputters, coughs out a puff of smoke and goes dark for a moment. Nothing explodes. It seems almost embarrassed.',
  // the Data Fragment
  'item.projecthero.omnitrix_data_fragment': 'Omnitrix Data Fragment',
  'item.projecthero.omnitrix_data_fragment.hint': 'A sliver of glowing Galvan data crystal, found in Hero Vaults (more often in the better ones).',
  'item.projecthero.omnitrix_data_fragment.use': 'Use while wearing an Omnitrix: the watch reads it and shows you a digit of your Master Control code - or, rarely, a hidden DNA code.',
  // the Guidebook's Master Control note (rewritten: function mode and the code)
  'projecthero.guide.omnitrix.master.function': 'You have found part of it, so you may keep this. The watch has a second face. Raise it and let the core rise, then hold Shift and right-click: the core sinks back, the face goes plain, and it waits for numbers.',
  'projecthero.guide.omnitrix.master.controls': 'Entering a code',
  'projecthero.guide.omnitrix.master.key.scroll': 'Scroll',
  'projecthero.guide.omnitrix.master.do.scroll': 'change the blinking digit (0-9)',
  'projecthero.guide.omnitrix.master.key.middle': 'Middle-click',
  'projecthero.guide.omnitrix.master.do.middle': 'next digit; on the sixth it enters the code',
  'projecthero.guide.omnitrix.master.key.back': 'Shift+Middle-click',
  'projecthero.guide.omnitrix.master.do.back': 'back one digit',
  'projecthero.guide.omnitrix.master.key.leave': 'Shift+Right-click',
  'projecthero.guide.omnitrix.master.do.leave': 'leave (or lower the watch with R)',
  'projecthero.guide.omnitrix.master.code': 'Master Control answers to six numbers, and they are yours alone: no other wearer\'s watch has the same. They are written nowhere.',
  'projecthero.guide.omnitrix.master.finding': 'The watch lets them slip, one at a time and with their place: a flicker as it changes you, or after a long while spent as an alien. The Data Fragments left in old Hero Vaults hold pieces too - hold one to the watch.',
  'projecthero.guide.omnitrix.master.wrong': 'Enter the wrong numbers and it buzzes, lights the ones already in their right place green, and will not listen for ten seconds.',
  'projecthero.guide.omnitrix.master.off': 'Enter them again while Master Control is awake and it goes back to sleep.',
  'projecthero.guide.omnitrix.master.found': 'What your watch has let slip',
  'projecthero.guide.omnitrix.master.found.none': 'Nothing yet.',
  'projecthero.guide.omnitrix.master.found.code': 'Master Control:  %s',
  'projecthero.guide.omnitrix.master.found.count': '%s of 6 digits found',
  'projecthero.guide.omnitrix.master.found.alien_unknown': 'Unknown DNA:  %s',
  'projecthero.guide.omnitrix.master.found.alien_known': '%s:  %s  (on your dial)',
  'projecthero.guide.omnitrix.master.hidden': 'It keeps other numbers as well: DNA it has hidden from you. Those surface whole, and rarely - most often once you know all six of the first.',
  'projecthero.guide.omnitrix.master.number': 'Every hero has a number. The boy who wore the first of these carried his in his name. Say it to the watch three times over and it will begin to count down - so say it again before it reaches nothing.',
};

const REPLACE = {
  'projecthero.omnitrix.info.rule.master': 'Master Control is hidden in the watch behind a six-digit code that is yours alone. Prime the watch and Shift+Right-click for its function mode; the watch reveals the digits slowly as you use it.',
  'projecthero.guide.omnitrix.master.tier': 'A note folded small, found inside the watch\'s casing',
  'projecthero.guide.omnitrix.master.sign': '-- its maker, who does not repeat himself',
};

const REMOVE = ['projecthero.guide.omnitrix.master.note', 'projecthero.guide.omnitrix.master.step1', 'projecthero.guide.omnitrix.master.step2',
  'projecthero.guide.omnitrix.master.step3', 'projecthero.guide.omnitrix.master.step4', 'projecthero.guide.omnitrix.master.step5',
  'projecthero.guide.omnitrix.master.step6', 'projecthero.guide.omnitrix.master.warning', 'projecthero.guide.omnitrix.master.sleep'];

const esc = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(text);
let replaced = 0, removed = 0;
for (const [k, v] of Object.entries(REPLACE)) {
  if (!(k in existing) || existing[k] === v) continue;
  const re = new RegExp('(\\n  ' + esc(JSON.stringify(k)) + ': )(".*?(?<!\\\\)")(,?)(\\r?)$', 'm');
  text = text.replace(re, (m, a, b, c, d) => a + JSON.stringify(v) + c + d);
  replaced++;
}
for (const k of REMOVE) {
  if (!(k in existing)) continue;
  // a whole line "  "key": "...",\r\n" -- never the last entry of the file (these sit mid-file)
  const re = new RegExp('\\n  ' + esc(JSON.stringify(k)) + ': ".*?(?<!\\\\)",(\\r?)(?=\\n)', 'm');
  const before = text.length;
  text = text.replace(re, '');
  if (text.length !== before) removed++;
}
const add = [];
const now = JSON.parse(text);
for (const [k, v] of Object.entries(K)) {
  if (!(k in now)) add.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)}`);
}
if (add.length) {
  const close = text.lastIndexOf('}');
  let body = text.slice(0, close).replace(/\s+$/, '');
  body += ',' + eol + add.join(',' + eol) + eol;
  text = body + '}' + text.slice(close + 1);
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log(`en_us: +${add.length} keys, ${replaced} replaced, ${removed} removed`);
