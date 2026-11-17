// v0.17.5 Symbiote suit colours: adds / updates the en_us keys one by one (never rewrites the file wholesale: other branches
// touch it too). Keeps the file's line endings and 2-space indent. Every key here is set (added if new, replaced if it exists).
//   node scratchpad/symbiote/lang_symbiote_suit_v0175.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // the colour screen
  'screen.projecthero.symbiote_colours': 'Symbiote Suit Colours',
  'screen.projecthero.symbiote_colours.open': 'Suit Colours',
  'screen.projecthero.symbiote_colours.body': 'Body',
  'screen.projecthero.symbiote_colours.emblem': 'Emblem',
  'screen.projecthero.symbiote_colours.eyes': 'Eyes',
  'screen.projecthero.symbiote_colours.hue': 'Hue',
  'screen.projecthero.symbiote_colours.saturation': 'Saturation',
  'screen.projecthero.symbiote_colours.brightness': 'Brightness',
  'screen.projecthero.symbiote_colours.hex': 'Hex colour',
  'screen.projecthero.symbiote_colours.presets': 'Presets',
  'screen.projecthero.symbiote_colours.apply': 'Apply',
  'screen.projecthero.symbiote_colours.reset': 'Classic',
  'screen.projecthero.symbiote_colours.hint': 'Pick a part, then drag the bars or type a hex colour. Everyone sees your colours.',
  // presets (swatch tooltips)
  'projecthero.symbiote.colour.preset.classic': 'Classic: black body, white emblem and eyes',
  'projecthero.symbiote.colour.preset.anti_venom': 'Anti-Venom: white body, black emblem and eyes',
  'projecthero.symbiote.colour.preset.carnage': 'Carnage: red body, black emblem, white eyes',
  'projecthero.symbiote.colour.preset.scream': 'Scream: yellow body, red emblem',
  'projecthero.symbiote.colour.preset.agony': 'Agony: purple body, black emblem',
  'projecthero.symbiote.colour.preset.riot': 'Riot: grey body, blue emblem',
  'projecthero.symbiote.colour.preset.toxin': 'Toxin: blue body, red emblem',
  // the guide
  'projecthero.guide.symbiote.s.suit.colours': 'Your colours: open Your Power (I) and press Suit Colours to recolour the body, the emblem and the eyes. Pick a preset (Classic, Anti-Venom, Carnage, Scream, Agony, Riot, Toxin) or mix any colour. The tendrils, spikes and blade match. Everyone sees it, and it stays through death.',
  // JEI
  'projecthero.jei.symbiote_host_suit': 'The living suit of a Symbiote host. It forms on you when you press H. Recolour it from Your Power (I) -> Suit Colours: body, emblem and eyes, with seven presets or any colour you like.',
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
