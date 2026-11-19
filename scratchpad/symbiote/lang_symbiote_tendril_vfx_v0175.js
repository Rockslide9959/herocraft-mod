// v0.17.5 Symbiote tendril-vfx round: the colour screen's new keys (Mouth part, RGB mode, black / white chips, tooltips) and the
// updated hint, key by key after an anchor (never the whole file). Keeps line endings; JSON.parse checked before writing. Re-runnable.
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(file, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';
const esc = (s) => JSON.stringify(s);

const replace = {
  'screen.projecthero.symbiote_colours.hint': "Pick a part, then drag the bars, scroll or use the arrow keys on one, or type a hex colour. Any colour works, pure black and white too. Everyone sees your suit.",
};
const insertAfter = 'screen.projecthero.symbiote_colours.eyes';
const add = [
  ['screen.projecthero.symbiote_colours.mouth', 'Mouth'],
  ['screen.projecthero.symbiote_colours.body.tip', 'The living suit itself. The lips take this colour too.'],
  ['screen.projecthero.symbiote_colours.emblem.tip', 'The spider emblem on the chest and back, drawn in exactly this colour.'],
  ['screen.projecthero.symbiote_colours.eyes.tip', 'The lenses, drawn in exactly this colour. Light eyes glow a little at night; dark eyes do not.'],
  ['screen.projecthero.symbiote_colours.mouth.tip', 'The tongue and the inside of the mouth. The teeth stay white.'],
  ['screen.projecthero.symbiote_colours.red', 'Red'],
  ['screen.projecthero.symbiote_colours.green', 'Green'],
  ['screen.projecthero.symbiote_colours.blue', 'Blue'],
  ['screen.projecthero.symbiote_colours.mode.rgb', 'RGB'],
  ['screen.projecthero.symbiote_colours.mode.hsb', 'HSB'],
  ['screen.projecthero.symbiote_colours.mode.tip', 'Switch the bars between Hue / Saturation / Brightness and Red / Green / Blue (0 to 255).'],
  ['screen.projecthero.symbiote_colours.black', 'Pure black (#000000) for this part.'],
  ['screen.projecthero.symbiote_colours.white', 'Pure white (#FFFFFF) for this part.'],
  ['screen.projecthero.symbiote_colours.bar.tip', 'Drag, scroll, or use the left and right arrow keys for one exact step.'],
];

for (const [k, v] of Object.entries(replace)) {
  const re = new RegExp('("' + k.replace(/\./g, '\\.') + '"\\s*:\\s*)"(?:[^"\\\\]|\\\\.)*"');
  if (!re.test(text)) throw new Error('missing ' + k);
  text = text.replace(re, (m, a) => a + esc(v));
}
const lines = text.split(EOL);
let at = lines.findIndex((l) => l.includes('"' + insertAfter + '"'));
if (at < 0) throw new Error('anchor missing');
const indent = lines[at].match(/^\s*/)[0];
for (const [k, v] of add) {
  const existing = lines.findIndex((l) => l.includes('"' + k + '"'));
  const line = indent + esc(k) + ': ' + esc(v) + ',';
  if (existing >= 0) { lines[existing] = line; continue; }
  lines.splice(++at, 0, line);
}
text = lines.join(EOL);
JSON.parse(text);
fs.writeFileSync(file, text);
console.log('lang ok');
