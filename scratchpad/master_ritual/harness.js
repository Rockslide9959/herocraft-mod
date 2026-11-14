// v0.17.4 Master Control ritual harness: installs / removes MasterRitualHarness and writes its flag.
//
//   node scratchpad/master_ritual/harness.js install
//   node scratchpad/master_ritual/harness.js flag success|miss [guide]
//   node scratchpad/master_ritual/harness.js uninstall          (before committing)
//
// Then: node scratchpad/low_graphics_options.js run/options.txt  &&  ./gradlew runClient --args="--username RitualFilm"
// The client performs the ritual through real inputs, films every tick to run/screenshots/rit_<mode>_NNNN.png and quits.
// Then: node scratchpad/master_ritual/film.js <mode>   -> docs/screenshots/v0174/master_control/<mode>.mp4 + _sheet.jpg
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/MasterRitualHarness.java');
const INIT = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java');
const HOOK = 'com.projecthero.mod.client.MasterRitualHarness.init(); // TEMP-HARNESS';
const FLAG = path.join(ROOT, 'run/master_ritual.flag');

const [cmd, ...a] = process.argv.slice(2);
if (cmd === 'install') {
  fs.copyFileSync(path.join(__dirname, 'MasterRitualHarness.java.txt'), SRC);
  let s = fs.readFileSync(INIT, 'utf8');
  if (!s.includes(HOOK)) {
    const eol = s.includes('\r\n') ? '\r\n' : '\n';
    const anchor = /(\r?\n)([ \t]*)ModKeyBindings\.initialize\(\);/;
    if (!anchor.test(s)) throw new Error('anchor not found');
    s = s.replace(anchor, (m, nl, ind) => `${m}${eol}${ind}${HOOK}`);
    fs.writeFileSync(INIT, s);
  }
  console.log('harness installed');
} else if (cmd === 'uninstall') {
  if (fs.existsSync(SRC)) fs.unlinkSync(SRC);
  let s = fs.readFileSync(INIT, 'utf8');
  s = s.replace(/\r?\n[ \t]*com\.projecthero\.mod\.client\.MasterRitualHarness\.init\(\); \/\/ TEMP-HARNESS/g, '');
  fs.writeFileSync(INIT, s);
  if (fs.existsSync(FLAG)) fs.unlinkSync(FLAG);
  console.log('harness removed');
} else if (cmd === 'flag') {
  const o = { mode: a[0] || 'success', guide: a[1] === 'guide' };
  fs.mkdirSync(path.dirname(FLAG), { recursive: true });
  fs.writeFileSync(FLAG, JSON.stringify(o) + '\n');
  console.log('run/master_ritual.flag =', JSON.stringify(o));
} else {
  console.log('usage: harness.js install | uninstall | flag success|miss [guide]');
  process.exit(1);
}
