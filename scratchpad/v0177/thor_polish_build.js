// v0.17.7 Thor polish: builds scratchpad/ThorPolishHarness.v0177.java.txt from the Thor skin harness + a new plan, and the
// install / film scripts beside it (thor_polish_harness.js, thor_polish_film.js) from the skin ones.
//   node scratchpad/v0177/thor_polish_build.js <plan.java.txt>
const fs = require('fs');
let s = fs.readFileSync('scratchpad/ThorSkinHarness.v0177.java.txt', 'utf8').replace(/\r\n/g, '\n');
const plan = fs.readFileSync(process.argv[2], 'utf8').replace(/\r\n/g, '\n');
const a = s.indexOf('\tprivate static void plan() {');
const b = s.indexOf('\tprivate static void tick(Minecraft mc) {');
s = s.slice(0, a) + plan + s.slice(b);
s = s.replace(/ThorSkinHarness/g, 'ThorPolishHarness').replace(/thorskin/g, 'thorpolish').replace(/THORSKIN/g, 'THORPOLISH')
	.replace('(v0.17.7 Thor skin + red cape)', '(v0.17.7 Thor polish: pixel suit-up, blue XP bar, flight trail, hard landing)')
	.replace('thor_skin_harness.js', 'thor_polish_harness.js').replace('thor_skin_harness.js', 'thor_polish_harness.js')
	.replace('thor_skin_film.js', 'thor_polish_film.js')
	.replace('\t\tList<Runnable> todo = AT.remove(s);', '\t\tdive(mc);\n\t\tList<Runnable> todo = AT.remove(s);');
fs.writeFileSync('scratchpad/ThorPolishHarness.v0177.java.txt', s);

let h = fs.readFileSync('scratchpad/v0177/thor_skin_harness.js', 'utf8').replace(/ThorSkinHarness/g, 'ThorPolishHarness')
	.replace(/Thor skin harness/g, 'Thor polish harness').replace(/thor_skin_harness/g, 'thor_polish_harness');
fs.writeFileSync('scratchpad/v0177/thor_polish_harness.js', h);
let f = fs.readFileSync('scratchpad/v0177/thor_skin_film.js', 'utf8').replace(/run\/thorskin_out/g, 'run/thorpolish_out')
	.replace(/v0177\/thor_skin/g, 'v0177/thor_polish').replace('Thor skin + cape films', 'Thor polish films').replace(/thor_skin_film/g, 'thor_polish_film');
fs.writeFileSync('scratchpad/v0177/thor_polish_film.js', f);
console.log('built');
