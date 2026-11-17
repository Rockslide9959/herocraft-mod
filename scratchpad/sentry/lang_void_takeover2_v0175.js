// v0.17.5 Void takeover rework 2 (2026-11-17): adds / updates the en_us keys one by one (never rewrites the file wholesale: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Every key here is set (added if new, replaced if it exists).
//   node scratchpad/sentry/lang_void_takeover2_v0175.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  "projecthero.guide.sentry.takeover.body": "At 100 Void your body is not yours. Your whole body turns black, suit and cape too, with two tiny white eyes. The camera pulls far out behind you and the Void flies you on its own: it rises about 50 blocks above the ground and hangs there, looking down at the world it is eating. It only comes down, slowly, for players and strong enemies (bosses, and anything with 40 or more health or heavy armour) within 64 blocks: then it fights with the Void Form moves at full strength and goes back up when they are gone. It attacks everyone that way, your squad and your pets too. Weaker creatures are left to the spreading destruction, which hurts them every second. It never gets stuck: walls in its way, and any hole it ends up in, fade away in front of it. Creatures it kills are absorbed and heal it.",
  "projecthero.guide.sentry.world.body": "A disc of destruction spreads over the ground under the Void, 8 blocks wide at first and growing all the time, up to 56 blocks out. Each block slowly fades to black and then dissolves into nothing; trees, plants and buildings go, and the ground is eaten a few layers deep, deepest under the Void. The sky goes dark around it and the clock shows midnight near it (only on screen). About a minute after you get control back, everything it ate grows back. It never eats bedrock, chests and other containers, portals, water, or anything inside spawn protection. Server owners can make it permanent with /gamerule projectheroVoidGriefing true.",
};

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const esc = (s) => JSON.stringify(s);
let added = 0, changed = 0;
for (const [k, v] of Object.entries(K)) {
  const re = new RegExp('^(\\s*)' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*:\\s*"(?:[^"\\\\]|\\\\.)*"(,?)\\r?$', 'm');
  const m = text.match(re);
  if (m) {
    const line = m[1] + esc(k) + ': ' + esc(v) + m[2];
    if (m[0].replace(/\r$/, '') !== line) { text = text.replace(re, line); changed++; }
  } else {
    // add before the closing brace: give the last entry a comma first
    const close = text.lastIndexOf('}');
    let head = text.slice(0, close).replace(/\s*$/, '');
    if (!head.endsWith(',') && !head.endsWith('{')) head += ',';
    text = head + eol + '  ' + esc(k) + ': ' + esc(v) + eol + '}' + text.slice(close + 1);
    added++;
  }
}
JSON.parse(text); // must still be valid
fs.writeFileSync(file, text);
console.log(`en_us: ${added} added, ${changed} changed`);
