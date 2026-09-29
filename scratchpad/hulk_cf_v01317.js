// v0.13.17: CurseForge Hulk bullets (rage rules, death save, teammates, webs, rampage range).
const fs = require('fs');
const f = 'docs/CURSEFORGE_DESCRIPTION.md';
let s = fs.readFileSync(f, 'utf8');
const i = s.indexOf('- **Rage (0-100):** fills when you are hurt and when you fight;');
const j = s.indexOf('- The Hulk can\'t lift Mjolnir', i);
if (i < 0 || j < 0) throw new Error('section not found');
const nl = s.slice(i, j).includes('\r\n') ? '\r\n' : '\n';
const body = [
  '- **Rage (0-100):** every point of damage you take is **1% rage**; as Banner you cool off **2% a second** after 5 s',
  '  without being hurt. Past **75** Banner starts pouring off green gamma. At **75** press **H** to let the Hulk out -- a',
  '  quick change, and he is **yours to command**. At **100** he comes out on his own: Banner **drops to his knees**',
  '  clutching his head and the Hulk slowly takes him over, then rises roaring. The Hulk **phases onto you as you grow** and',
  '  back off as you shrink. As the Hulk, every hit you land adds **2%**, and rage only burns down (0.75% a second) once',
  '  you have been out of combat for 5 s.',
  '- **The Hulk:** your own Hulk model at 1.8x size, +50% speed, **20-damage punches** with big knockback, +40 health,',
  '  **diamond-level armour of his own**, fast regeneration, **immune to fire, arrows and falls**, tough against lava and',
  '  explosions, **never caught in webs** (sprinting tears them down), and hands that dig like stone tools. Armour you wear',
  '  bursts off when he comes out.',
  '- **R Power Punch** (30) · **G Ground Smash** (30 + a crater) · **Z Thunderclap** (22) · **Shift+Z HULK SMASH** (hold 5 s:',
  '  100 damage, a huge crater) · **X Super Leap** (up to 70 blocks) · **C Charge** (8 s rampaging run, 20 to everything in',
  '  the way) · **V Grab** (pick up and throw a mob, Shift+V crush it, or Shift+V tear up a chunk of earth to throw -- and',
  '  **carry or throw your squad-mates**, who land unhurt).',
  '- **The Hulk refuses to die:** Banner **can\'t be killed** -- every fatal hit brings the Hulk bursting out at full',
  '  health, no cooldown. To kill a Gamma player you have to beat the Hulk.',
  '- **Keep control:** only a Hulk who came out **on his own** fights you -- stop hitting things and he starts to take over;',
  '  answer the key prompts or he goes on a **rampage**, hunting anything out in the open up to **100 blocks** away. Hold',
  '  **N** to calm down with a breathing minigame. Squad-mates can **ride his back**.',
  ''].join(nl);
s = s.slice(0, i) + body + s.slice(j);
fs.writeFileSync(f, s);
console.log('ok');
