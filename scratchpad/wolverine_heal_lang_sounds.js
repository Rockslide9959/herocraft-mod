// v0.17.3 Wolverine heal sounds: adds the three sound events to sounds.json and their subtitles to en_us.json, as text
// edits right after the existing wolverine_sniff entries (the rest of each file -- its escapes, its order -- untouched).
// Both files are CRLF: they are normalised to LF for the edit and written back as CRLF. Refuses to run if any top-level
// key is duplicated (before or after), validates both files as JSON, and is idempotent (keys already present are skipped).
//   node scratchpad/wolverine_heal_lang_sounds.js
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const LANG = path.join(ROOT, 'lang', 'en_us.json');
const SOUNDS = path.join(ROOT, 'sounds.json');

const EVENTS = {
	wolverine_heal_crackle: ['Wolverine\'s wounds knit shut', 4],
	wolverine_heal_sizzle: ['Wolverine\'s burns sizzle closed', 3],
	wolverine_heal_surge: ['Wolverine\'s body regenerates', 2],
};

function topKeys(text) {
	// top-level keys sit at exactly two spaces of indent in both files
	const keys = [];
	for (const m of text.matchAll(/^ {2}"((?:[^"\\]|\\.)*)"\s*:/gm)) keys.push(m[1]);
	return keys;
}
function assertNoDuplicates(text, file) {
	const seen = new Set();
	const dup = [];
	for (const k of topKeys(text)) { if (seen.has(k)) dup.push(k); seen.add(k); }
	if (dup.length) throw new Error(file + ': duplicate keys ' + dup.join(', '));
	JSON.parse(text);
}
function edit(file, fn) {
	const lf = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
	assertNoDuplicates(lf, file);
	const out = fn(lf);
	assertNoDuplicates(out, file);
	fs.writeFileSync(file, out.replace(/\n/g, '\r\n'));
}

edit(SOUNDS, text => {
	const have = new Set(topKeys(text));
	const add = Object.entries(EVENTS).filter(([k]) => !have.has(k));
	if (!add.length) return text;
	const block = add.map(([k, [, n]]) => {
		const takes = Array.from({ length: n }, (_, i) => `      {\n        "name": "projecthero:wolverine/${k.replace('wolverine_', '')}${i + 1}"\n      }`).join(',\n');
		return `  "${k}": {\n    "subtitle": "subtitles.projecthero.${k}",\n    "sounds": [\n${takes}\n    ]\n  },\n`;
	}).join('');
	const anchor = text.indexOf('  "wolverine_sniff": {');
	if (anchor < 0) throw new Error('wolverine_sniff not found in sounds.json');
	const end = text.indexOf('\n  },\n', anchor);
	if (end < 0) throw new Error('end of wolverine_sniff not found');
	const at = end + '\n  },\n'.length;
	return text.slice(0, at) + block + text.slice(at);
});

edit(LANG, text => {
	const have = new Set(topKeys(text));
	const add = Object.entries(EVENTS).filter(([k]) => !have.has('subtitles.projecthero.' + k));
	if (!add.length) return text;
	const block = add.map(([k, [sub]]) => `  "subtitles.projecthero.${k}": ${JSON.stringify(sub)},\n`).join('');
	const anchor = text.indexOf('  "subtitles.projecthero.wolverine_sniff":');
	if (anchor < 0) throw new Error('wolverine_sniff subtitle not found in en_us.json');
	const at = text.indexOf('\n', anchor) + 1;
	return text.slice(0, at) + block + text.slice(at);
});
console.log('sounds.json + en_us.json updated (CRLF kept, no duplicate keys, valid JSON)');
