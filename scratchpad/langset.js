// Set lang keys in en_us.json without reformatting the file: existing keys are rewritten in place, new keys are
// inserted right after an anchor key (or appended at the end). Usage (from another script):
//   require('./langset.js')([{ anchor: 'some.existing.key', entries: { 'new.key': 'Text', ... } }, ...])
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

module.exports = function langset(groups) {
	let text = fs.readFileSync(FILE, 'utf8');
	const eol = text.includes('\r\n') ? '\r\n' : '\n';
	let lines = text.split(/\r?\n/);
	const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
	const indexOfKey = (k) => lines.findIndex(l => { const m = l.match(keyRe); return m && m[1] === k; });
	const render = (k, v) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`;
	for (const g of groups) {
		let at = g.anchor ? indexOfKey(g.anchor) : -1;
		for (const [k, v] of Object.entries(g.entries)) {
			const i = indexOfKey(k);
			if (i >= 0) {
				const keepComma = lines[i].trimEnd().endsWith(',');
				lines[i] = render(k, v).replace(/,$/, keepComma ? ',' : '');
				continue;
			}
			if (at < 0) {
				// append before the closing brace; make sure the previous entry ends with a comma
				let close = lines.length - 1;
				while (close > 0 && lines[close].trim() !== '}') close--;
				let prev = close - 1;
				while (prev > 0 && lines[prev].trim() === '') prev--;
				if (!lines[prev].trimEnd().endsWith(',') && lines[prev].trim() !== '{') lines[prev] = lines[prev].trimEnd() + ',';
				lines.splice(close, 0, render(k, v).replace(/,$/, ''));
				at = close;
				continue;
			}
			if (!lines[at].trimEnd().endsWith(',')) lines[at] = lines[at].trimEnd() + ',';
			const isLast = !lines[at + 1] || lines[at + 1].trim() === '}';
			lines.splice(at + 1, 0, isLast ? render(k, v).replace(/,$/, '') : render(k, v));
			at++;
		}
	}
	const out = lines.join(eol);
	JSON.parse(out); // must stay valid JSON
	fs.writeFileSync(FILE, out);
	console.log('lang updated');
};
