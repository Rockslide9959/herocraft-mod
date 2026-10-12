#!/usr/bin/env node
// v0.16.1: prints the names of the failed tests in a fabric gametest JUnit report, one per line (used by
// run-gametests.sh to re-run them). A passed test is a self-closing <testcase .../>; a failed one holds a <failure>.
// Exits 2 (so the slice fails outright) if a failed test's name could not be turned into a filter safely -- a failed
// test must never be left out of the re-run.
//
//   node .github/scripts/failed-gametests.js build/reports/gametest.xml
const fs = require('fs');

const xml = fs.readFileSync(process.argv[2], 'utf8');
const names = new Set();
for (const m of xml.matchAll(/<testcase\b[^>]*?\bname="([^"]*)"[^>]*?(?<!\/)>\s*<(?:failure|error)\b/g)) {
	if (!/^[a-z0-9_.]+$/i.test(m[1])) {
		console.error(`unexpected failed test name: ${m[1]}`);
		process.exit(2);
	}
	names.add(m[1]);
}
if (names.size > 0) {
	console.log([...names].join('\n'));
}
