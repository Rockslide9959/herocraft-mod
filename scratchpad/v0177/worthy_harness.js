// v0.17.7: installs / removes the Thor x Super Soldier film harness (scratchpad/WorthyFilmHarness.v0177.java.txt) for one run.
//   node scratchpad/v0177/worthy_harness.js install | remove
const fs = require('fs');
const src = fs.readFileSync('scratchpad/WorthyFilmHarness.v0177.java.txt', 'utf8').replace(/\r\n/g, '\n');
const javaPath = 'src/client/java/com/projecthero/mod/client/WorthyFilmHarness.java';
const mixPath = 'src/client/java/com/projecthero/mod/client/mixin/WorthyFilmHarnessCameraMixin.java';
const modJson = 'src/main/resources/fabric.mod.json';
const mixJson = 'src/client/resources/projecthero.client.mixins.json';
const ENTRY = '"com.projecthero.mod.client.WorthyFilmHarness"';
const MIX = '"WorthyFilmHarnessCameraMixin"';
function editJson(file, fn) {
	let t = fs.readFileSync(file, 'utf8');
	const nl = t.includes('\r\n') ? '\r\n' : '\n';
	t = fn(t.replace(/\r\n/g, '\n')).replace(/\n/g, nl);
	fs.writeFileSync(file, t);
}
if (process.argv[2] === 'remove') {
	for (const f of [javaPath, mixPath]) if (fs.existsSync(f)) fs.unlinkSync(f);
	editJson(modJson, t => t.replace(',\n\t\t\t' + ENTRY, ''));
	editJson(mixJson, t => t.replace('"client": [' + MIX + ']', '"client": []'));
	console.log('removed');
	process.exit(0);
}
const parts = src.split(/\/\/ ===== .* =====\n/);
fs.writeFileSync(javaPath, parts[1].replace(/\n/g, '\r\n'));
fs.writeFileSync(mixPath, parts[2].replace(/\n/g, '\r\n'));
editJson(modJson, t => t.includes(ENTRY) ? t : t.replace('"com.projecthero.mod.client.ProjectHeroModClient"',
	'"com.projecthero.mod.client.ProjectHeroModClient",\n\t\t\t' + ENTRY));
editJson(mixJson, t => {
	if (t.includes(MIX)) return t;
	if (!t.includes('"client": []')) throw new Error('client mixin list not empty -- add the camera mixin by hand');
	return t.replace('"client": []', '"client": [' + MIX + ']');
});
console.log('installed');
