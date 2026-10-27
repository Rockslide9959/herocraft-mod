// Applies a simple replacement patch file (CRLF-safe). Format:
//   @@@ FILE relative/path
//   <<<<
//   old text
//   ====
//   new text
//   >>>>
// (any number of blocks per file; an empty old text with "@@@ NEW path" writes the whole new text as a new file)
//   node scratchpad/apply_patch.js scratchpad/some.patch
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const text = fs.readFileSync(process.argv[2], 'utf8').replace(/\r\n/g, '\n');
const lines = text.split('\n');
let file = null, mode = null, old = [], neu = [], isNew = false, ok = 0;
const files = {};
function load(f) {
	if (!(f in files)) {
		const p = path.join(root, f);
		files[f] = fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : null;
	}
	return files[f];
}
for (const line of lines) {
	if (line.startsWith('@@@ FILE ') || line.startsWith('@@@ NEW ')) {
		isNew = line.startsWith('@@@ NEW ');
		file = line.slice(isNew ? 8 : 9).trim();
		if (isNew) { files[file] = ''; mode = 'new'; neu = []; }
		continue;
	}
	if (mode === 'new' && !line.startsWith('@@@')) {
		neu.push(line);
		files[file] = neu.join('\n');
		continue;
	}
	if (line === '<<<<') { mode = 'old'; old = []; neu = []; continue; }
	if (line === '====' && mode === 'old') { mode = 'neu'; continue; }
	if (line === '>>>>' && mode === 'neu') {
		mode = null;
		let s = load(file);
		if (s == null) { console.log('NO FILE ' + file); process.exit(1); }
		const crlf = s.includes('\r\n');
		let n = s.replace(/\r\n/g, '\n');
		const o = old.join('\n');
		const idx = n.indexOf(o);
		if (idx < 0) { console.log('MISSING in ' + file + ':\n' + o.slice(0, 200)); process.exit(1); }
		if (n.indexOf(o, idx + 1) >= 0) { console.log('AMBIGUOUS in ' + file + ':\n' + o.slice(0, 200)); process.exit(1); }
		n = n.slice(0, idx) + neu.join('\n') + n.slice(idx + o.length);
		files[file] = crlf ? n.replace(/\n/g, '\r\n') : n;
		ok++;
		continue;
	}
	if (mode === 'old') old.push(line);
	else if (mode === 'neu') neu.push(line);
}
for (const f of Object.keys(files)) {
	if (files[f] != null) {
		const p = path.join(root, f);
		fs.mkdirSync(path.dirname(p), { recursive: true });
		let out = files[f];
		if (!out.endsWith('\n')) out += '\n';
		fs.writeFileSync(p, out);
	}
}
console.log('applied ' + ok + ' replacements in ' + Object.keys(files).length + ' files');
