// v0.13.21: Green Lantern bullets in docs/CURSEFORGE_DESCRIPTION.md (the file mixes CRLF and LF -- each edited line
// keeps its own ending). Run from the repo root: node scratchpad/docs_v01321_greenlantern.js
const fs = require('fs');
const FILE = 'docs/CURSEFORGE_DESCRIPTION.md';
const text = fs.readFileSync(FILE, 'utf8');
const lines = text.split('\n'); // a CRLF line keeps its '\r'
function replaceLine(from, to) {
	const i = lines.findIndex(l => l.replace(/\r$/, '') === from);
	if (i < 0) throw new Error('missing line: ' + from);
	const cr = lines[i].endsWith('\r') ? '\r' : '';
	lines[i] = to.split('\n').map(l => l + cr).join('\n');
}
replaceLine('- A 10,000-point **Ring Charge** fuels beams, a Construct Fist, a War Hammer, flight, shields, a Protective Dome and',
	'- A 10,000-point **Ring Charge** fuels beams, a Construct Fist, a War Hammer, **directional flight** (fly wherever you\n' +
	'  look), shields, a Protective Dome and');
replaceLine('  **14 hard-light constructs**.',
	'  **14 hard-light constructs** of glowing green light -- walls, walkable ramps, a spinning turret, a travelling\n' +
	'  battering ram, a blade or drill on your fist and more.\n' +
	'- Your suit sweeps on one row of light at a time, and the ring glows on your right hand.');
fs.writeFileSync(FILE, lines.join('\n'));
console.log('docs updated');
