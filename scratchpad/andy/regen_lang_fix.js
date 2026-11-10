// v0.17.3: Andy guide text matches the NBC-style regeneration: the Regen Menu key, the regrowth look option, and how a
// torn-off shoulder pad comes back. CRLF kept, duplicate keys refused, JSON validated.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const keys = [...t.matchAll(/^\s*"([^"]+)":/gm)].map(m => m[1]);
const dup = keys.filter((k, i) => keys.indexOf(k) !== i);
if (dup.length) throw new Error('duplicate keys: ' + dup.slice(0, 5).join(', '));
const j = JSON.parse(t);
function swap(k, from, to) {
  const v = j[k];
  if (typeof v !== 'string' || !v.includes(from)) throw new Error('anchor ' + k);
  const old = JSON.stringify(k) + ': ' + JSON.stringify(v);
  if (!t.includes(old)) throw new Error('raw ' + k);
  const nv = v.replace(from, to);
  t = t.replace(old, JSON.stringify(k) + ': ' + JSON.stringify(nv));
  j[k] = nv;
}
swap('projecthero.guide.andy.regen.body',
  'you grow your parts a stage at a time in the Regen Menu.',
  'you grow your parts a stage at a time in the Regen Menu (Shift + hold V): each part\'s key takes it to bone, then flesh, then whole, and on a whole part it strips it off. Type /andystyle 3d or /andystyle stepped to pick how regrowth looks to you.');
swap('projecthero.guide.andy.suit.body',
  'losing an arm tears that shoulder pad off the chestplate.',
  'losing an arm tears that shoulder pad off the chestplate: a little less armour, and the pad stays off after the arm grows back until the chestplate is repaired (the suit\'s own mending does that).');
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
