// v0.17.5: gamerule projectheroCrimes -- adds/updates en_us keys one by one (CRLF-safe), never rewrites other keys.
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(f, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
const lines = s.split(/\r?\n/);
function set(k, v) {
	const prefix = '  ' + JSON.stringify(k) + ':';
	const line = '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v);
	const i = lines.findIndex((l) => l.startsWith(prefix));
	if (i >= 0) {
		lines[i] = line + (lines[i].trimEnd().endsWith(',') ? ',' : '');
		return;
	}
	// append before the closing brace
	let end = lines.length - 1;
	while (end > 0 && lines[end].trim() !== '}') end--;
	let last = end - 1;
	while (last > 0 && lines[last].trim() === '') last--;
	if (!lines[last].trimEnd().endsWith(',')) lines[last] = lines[last] + ',';
	lines.splice(end, 0, line);
}
set('gamerule.projectheroCrimes', 'Street crimes');
set('gamerule.projectheroCrimes.description', 'Muggings, break-ins, getaways and arson start on their own near villages. Off stops new ones; the /projecthero crime commands still work.');
set('projecthero.guide.crime.commands.body', '/projecthero crime start <mugging|break_in|getaway|arson> starts one at the nearest village (or in front of you). end stops them all, trigger rolls a natural one now, status lists them. Turn natural crimes off with /gamerule projectheroCrimes false. Frequency, kinds and rewards are in config/projecthero_crime.json.');
const out = lines.join(nl);
JSON.parse(out);
fs.writeFileSync(f, out);
console.log('ok');
