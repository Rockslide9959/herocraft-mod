// v0.13.17: Oathbreaker guide pages mention the phase damage scaling; CurseForge section brought up to date.
const fs = require('fs');
let f = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(f, 'utf8');
for (const [k, add] of [['projecthero.guide.oathbreaker.phase2.body', ' Everything he does now hits 10% harder.'],
                        ['projecthero.guide.oathbreaker.phase3.body', ' Everything he does now hits 25% harder.']]) {
  const re = new RegExp('^(\\s*"' + k.replace(/\./g, '\\.') + '": ")(.*?)(",?)\\s*$', 'm');
  const m = s.match(re);
  if (!m) throw new Error('missing ' + k);
  if (!m[2].includes(add.trim())) s = s.replace(re, m[1] + m[2] + add + m[3]);
}
JSON.parse(s);
fs.writeFileSync(f, s);

f = 'docs/CURSEFORGE_DESCRIPTION.md';
s = fs.readFileSync(f, 'utf8');
const i = s.indexOf('### The Oathbreaker (v0.13.7)');
const j = s.indexOf('---', i);
if (i < 0 || j < 0) throw new Error('no section');
const nl = s.slice(i, j).includes('\r\n') ? '\r\n' : '\n';
const body = [
  '### The Oathbreaker',
  'A summoned **4-block knight boss** -- the hardest fight in the mod. Craft a **Knight\'s Soul** (an Abyssal Core surrounded by 8 Grave',
  'Essence) and right-click it on a **Respawn Anchor** to call him forth. **4,000 HP** (more with more players nearby), a',
  'hidden **poise** meter you can break to stagger him, and **three phases**: the disciplined Knight (Stance Dash, Four-Strike',
  'Combo, Oath Guard parries, Leaping Cleave), the Forsworn at 60% (Soul Rend, Chains of the Forsworn, a fifth combo hit,',
  'feints) and the Oathless below 25% (Judgement slam, the unblockable Execution grab, phantom echoes of his swings).',
  '**v0.13.17:** every move hits ~30% harder, and he hits a further **10% harder in phase 2** and **25% harder in phase 3**.',
  'Defeating him drops a netherite sword, Grave Essence, a guaranteed Abyssal Core and the Broken Oath.',
  '', ''].join(nl);
s = s.slice(0, i) + body + s.slice(j);
fs.writeFileSync(f, s);
console.log('ok');
