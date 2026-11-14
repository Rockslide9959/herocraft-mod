// Installs / removes the every-tick sequence harness, and writes its flag (docs/OMNITRIX_SEQUENCES.md).
//
//   node scratchpad/omnitrix_seq/harness.js install                 copy OmnitrixSeqHarness into src/client + hook it in
//   node scratchpad/omnitrix_seq/harness.js flag <alien> [mode] [from] [to] [tag] [t1,t2,...]
//                                                                     write run/omnitrix_seq.flag (mode self|watch)
//   node scratchpad/omnitrix_seq/harness.js uninstall               remove the hook, the class and the flag (do this before committing)
//
// Then, for each film:  node scratchpad/low_graphics_options.js run/options.txt  &&  ./gradlew runClient --args="--username SeqFilm"
// (the client films, then quits by itself; frames land in run/screenshots/seq_<tag>_NNN.png).
const fs = require('fs');
const path = require('path');
const ROOT = path.resolve(__dirname, '..', '..');
const SRC = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/OmnitrixSeqHarness.java');
const INIT = path.join(ROOT, 'src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java');
const HOOK = 'com.projecthero.mod.client.OmnitrixSeqHarness.init(); // TEMP-HARNESS';
const FLAG = path.join(ROOT, 'run/omnitrix_seq.flag');

const [cmd, ...a] = process.argv.slice(2);
if (cmd === 'install') {
  fs.copyFileSync(path.join(__dirname, 'OmnitrixSeqHarness.java.txt'), SRC);
  let s = fs.readFileSync(INIT, 'utf8');
  if (!s.includes(HOOK)) {
    const eol = s.includes('\r\n') ? '\r\n' : '\n';
    const anchor = /(\r?\n)([ \t]*)ModKeyBindings\.initialize\(\);/;
    if (!anchor.test(s)) throw new Error('anchor ModKeyBindings.initialize(); not found in ProjectHeroModClient');
    s = s.replace(anchor, (m, nl, ind) => `${m}${eol}${ind}${HOOK}`);
    fs.writeFileSync(INIT, s);
  }
  console.log('harness installed (src/client/.../OmnitrixSeqHarness.java + one line in ProjectHeroModClient)');
} else if (cmd === 'uninstall') {
  if (fs.existsSync(SRC)) fs.unlinkSync(SRC);
  let s = fs.readFileSync(INIT, 'utf8');
  s = s.replace(/\r?\n[ \t]*com\.projecthero\.mod\.client\.OmnitrixSeqHarness\.init\(\); \/\/ TEMP-HARNESS/g, '');
  fs.writeFileSync(INIT, s);
  if (fs.existsSync(FLAG)) fs.unlinkSync(FLAG);
  console.log('harness removed');
} else if (cmd === 'flag') {
  const [alien, mode = 'self', from = '0', to = '-1', tag] = a;
  if (!alien) throw new Error('flag <alien> [mode] [from] [to] [tag]');
  fs.mkdirSync(path.dirname(FLAG), { recursive: true });
  const o = { alien, mode, from: +from, to: +to, tag: tag || `${alien}_${mode}`, seed: 424242 };
  // optional 6th argument: a comma list of ticks to film instead of the whole range (key-beat checks)
  if (a[5]) o.ticks = a[5].split(',').map(Number);
  fs.writeFileSync(FLAG, JSON.stringify(o) + '\n');
  console.log('run/omnitrix_seq.flag =', JSON.stringify(o));
} else {
  console.log('usage: harness.js install | uninstall | flag <alien> [mode] [from] [to] [tag]');
  process.exit(1);
}
