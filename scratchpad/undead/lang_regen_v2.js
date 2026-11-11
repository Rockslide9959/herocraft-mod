// v0.17.3 one-sweep regrowth / 30-block head throw: edits three Undead lang values in place (CRLF-safe, refuses duplicate keys, validates).
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let raw = fs.readFileSync(L, 'utf8');
const crlf = raw.includes('\r\n');
let t = raw.replace(/\r\n/g, '\n');
const keys = [...t.matchAll(/^  "([^"]+)":/mg)].map(m => m[1]);
if (new Set(keys).size !== keys.length) throw new Error('en_us has duplicate keys');
const lang = JSON.parse(t);
const edits = {
  'projecthero.guide.undead.regen.body': [
    ["A lost segment waits a second and a half, then grows back in about a second (head 0.85s, body 1s, arm 0.9s, leg 1s, hand 0.6s, foot 0.55s): first bone",
     "A lost segment waits a second and a half, then grows back. A lost arm and its hand (or leg and its foot) grow back together in exactly one second, as one growth from the shoulder or hip right out to the fingertips or toes; the head takes 0.75s, the body 0.9s, and a hand or foot lost on its own about half a second. First bone"],
    ["nothing starts before what it grows from has grown out to it, so a hand only starts once its arm's bone has reached the wrist, a foot once its leg's has reached the ankle, the legs once",
     "nothing starts before what it grows from has grown out to it: a hand comes out of its arm as the growth reaches the wrist (never floating on its own), a foot as it reaches the ankle, the legs once"],
  ],
  'projecthero.guide.undead.headonly.body': [
    ["while the legs grow), then your hands and feet.", "while the legs grow), each one growing out to its hand or foot in one go."],
  ],
  'projecthero.undead.ability.head_throw.desc': [
    ["Let go of X to throw it about 25 blocks", "Let go of X to throw it up to about 30 blocks"],
  ],
};
for (const [k, reps] of Object.entries(edits)) {
  if (!(k in lang)) throw new Error('missing ' + k);
  let v = lang[k];
  for (const [a, b] of reps) {
    if (!v.includes(a)) { if (v.includes(b)) continue; throw new Error('text not found in ' + k + ': ' + a); }
    v = v.replace(a, b);
  }
  const oldLine = '  ' + JSON.stringify(k) + ': ' + JSON.stringify(lang[k]);
  const idx = t.indexOf(oldLine);
  if (idx < 0) throw new Error('line not found verbatim for ' + k);
  t = t.slice(0, idx) + '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + t.slice(idx + oldLine.length);
}
const back = JSON.parse(t);
const keys2 = [...t.matchAll(/^  "([^"]+)":/mg)].map(m => m[1]);
if (new Set(keys2).size !== keys2.length || keys2.length !== keys.length) throw new Error('key count changed');
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok', Object.keys(back).length, 'keys');
