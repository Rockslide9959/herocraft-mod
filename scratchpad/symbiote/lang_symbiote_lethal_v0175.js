// v0.17.5 Symbiote Lethal Protector suit style: adds / updates the en_us keys one by one (never rewrites the file wholesale: other branches
// touch it too). Keeps the file's line endings and 2-space indent. Every key here is set (added if new, replaced if it exists).
//   node scratchpad/symbiote/lang_symbiote_lethal_v0175.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // the screen: now picks the suit style as well as the colours
  'screen.projecthero.symbiote_colours': 'Symbiote Suit Style',
  'screen.projecthero.symbiote_colours.open': 'Suit Style',
  'screen.projecthero.symbiote_colours.reset': 'Reset Colours',
  'screen.projecthero.symbiote_colours.hint': 'Pick a suit, then a part, and drag the bars or type a hex colour. Everyone sees your suit.',
  'screen.projecthero.symbiote_colours.style.missing': 'The Lethal Protector suit is not in this build yet: it is made from a borrowed skin that is still waiting on permission.',
  // the two styles
  'projecthero.symbiote.style.classic': 'Classic',
  'projecthero.symbiote.style.classic.short': 'Classic',
  'projecthero.symbiote.style.classic.desc': 'The Classic suit: sleek, glossy and alive, with big angular eyes and the spider emblem wrapping round the body.',
  'projecthero.symbiote.style.lethal_protector': 'Lethal Protector',
  'projecthero.symbiote.style.lethal_protector.short': 'Lethal',
  'projecthero.symbiote.style.lethal_protector.desc': 'Lethal Protector: the monster. Its jaw opens on rows of fangs, it drools, and its tongue lolls out and lashes when you fight.',
  // the guide
  'projecthero.guide.symbiote.s.suit.colours': 'Your colours: open Your Power (I) and press Suit Style to recolour the body, the emblem and the eyes. Pick a preset (Classic, Anti-Venom, Carnage, Scream, Agony, Riot, Toxin) or mix any colour. The tendrils, spikes and blade match. Everyone sees it, and it stays through death.',
  'projecthero.guide.symbiote.s.suit.style': 'Your suit: on the same screen pick Classic or Lethal Protector. Lethal Protector has a real mouth: it breathes and drools, its tongue lolls out now and then, it snarls when you swing, get hurt or use a Symbiote move, and its tongue lashes out when you land a hit or strike, grab or grapple with a tendril. Both suits take your colours; the mouth keeps its own.',
  // JEI
  'projecthero.jei.symbiote_host_suit': 'The living suit of a Symbiote host. It forms on you when you press H. Change it from Your Power (I) -> Suit Style: the Classic suit or Lethal Protector (with a jaw and tongue that move), in any colours you like for the body, emblem and eyes.',
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
