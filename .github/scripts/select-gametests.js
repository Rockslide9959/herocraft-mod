#!/usr/bin/env node
// v0.15.21: which gametests a push needs (build.yml "plan" job).
//
//   node .github/scripts/select-gametests.js <base sha> <head sha>
//
// Writes mode / filter / shards / reason to $GITHUB_OUTPUT (and prints them):
//   none   -- nothing the server-side suite can see changed (docs, client-only code, textures, models...)
//   subset -- only the test classes that touch what changed (-PtestFilter), on one runner
//   full   -- the whole suite, split over FULL_SHARDS runners
//
// The rules lean towards running MORE: anything shared (core packages, build files, mixin configs, the test helpers),
// an unknown base commit, or a huge diff means the full suite. Release tags always get a full run (release.yml only
// skips its own run when master already ran the FULL suite on that exact commit).

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const FULL_SHARDS = 6;
const TEST_DIR = 'src/gametest/java/com/projecthero/mod/gametest';
const MOD_PKG = 'src/main/java/com/projecthero/mod/';
// shared by every power: a change here can break anything
const CORE_PACKAGES = new Set(['hero', 'attachment', 'network', 'mixin', 'combat', 'config', 'entity', 'item', 'command',
	'event', 'power', 'flight', 'armor', 'sound', 'diagnostics', 'worldgen']);
const MAX_SUBSET_CLASSES = 45;

function output(mode, reason, filter = '') {
	const shards = mode === 'full' ? FULL_SHARDS : 1;
	const lines = [
		`mode=${mode}`,
		`filter=${filter}`,
		`shards=${JSON.stringify(Array.from({ length: shards }, (_, i) => i + 1))}`,
		`shard_count=${shards}`,
		`reason=${reason}`,
	];
	console.log(lines.join('\n'));
	if (process.env.GITHUB_OUTPUT) {
		fs.appendFileSync(process.env.GITHUB_OUTPUT, lines.join('\n') + '\n');
	}
	process.exit(0);
}

const [base, head] = process.argv.slice(2);
if (process.env.FORCE_FULL === 'true') {
	output('full', 'full run requested');
}
if (!base || /^0+$/.test(base) || !head) {
	output('full', 'no base commit (new branch)');
}
try {
	if (execSync(`git cat-file -t ${base}`, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() !== 'commit') {
		throw new Error('not a commit');
	}
} catch (e) {
	output('full', `base ${base} not in history (force-push?)`);
}

const files = execSync(`git diff --name-only ${base} ${head}`, { encoding: 'utf8' }).split('\n').filter(Boolean);
if (files.length === 0) {
	output('none', 'no file changes');
}

const testSources = fs.readdirSync(TEST_DIR).filter(f => f.endsWith('.java'))
	.map(f => ({ name: f.replace(/\.java$/, ''), text: fs.readFileSync(path.join(TEST_DIR, f), 'utf8') }));
const selected = new Set();
const why = [];

function selectWhere(pred) {
	for (const t of testSources) {
		if (t.name.endsWith('GameTests') && pred(t)) {
			selected.add(t.name);
		}
	}
}

function onlyVersionChanged(file) {
	const diff = execSync(`git diff -U0 ${base} ${head} -- "${file}"`, { encoding: 'utf8' });
	return diff.split('\n').filter(l => /^[+-](?![+-])/.test(l)).every(l => /^[+-]\s*version\s*=/.test(l));
}

for (const f of files) {
	// never seen by the dedicated test server
	if (/^(docs\/|scratchpad\/|\.claude\/)/.test(f) || /\.(md|txt)$/i.test(f) || f.startsWith('src/client/')) {
		continue;
	}
	if (/^src\/main\/resources\/assets\/[^/]+\/(textures|models|geo|animations|blockstates|shaders|particles|font|atlases|items|sounds)\//.test(f)) {
		continue;
	}
	if (f === 'gradle.properties' && onlyVersionChanged(f)) {
		continue; // the version bump every release does
	}
	if (f === '.github/scripts/select-gametests.js') {
		continue;
	}
	// a test class: just that class -- unless it is a shared helper
	if (f.startsWith(TEST_DIR + '/') && f.endsWith('GameTests.java') && !f.includes('/mixin/')) {
		const name = path.basename(f, '.java');
		if (fs.existsSync(f)) {
			selected.add(name);
		}
		continue;
	}
	// the gametest entrypoint list: a new test class only adds a line -- run the added classes; anything else is shared
	if (f === 'src/gametest/resources/fabric.mod.json') {
		const changed = execSync(`git diff -U0 ${base} ${head} -- "${f}"`, { encoding: 'utf8' }).split('\n')
			.filter(l => /^[+-](?![+-])/.test(l));
		const entry = /^[+-]\s*"com\.projecthero\.mod\.gametest\.([A-Za-z0-9]+GameTests)",?\s*$/;
		if (changed.every(l => entry.test(l) || /^[+-]\s*$/.test(l))) {
			changed.filter(l => l.startsWith('+')).forEach(l => {
				const c = l.match(entry);
				if (c) {
					selected.add(c[1]);
				}
			});
			continue;
		}
		output('full', `gametest fabric.mod.json changed beyond the entrypoint list`);
	}
	// a network payload: the tests that use that payload class
	// (a power's own /command file works the same way)
	const net = f.match(/^src\/main\/java\/com\/projecthero\/mod\/(?:network\/([A-Za-z0-9]+Payload)|command\/((?!ProjectHero)[A-Za-z0-9]+Command))\.java$/);
	if (net) {
		net[1] = net[1] || net[2];
	}
	if (net) {
		const before = selected.size;
		selectWhere(t => t.text.includes(net[1]));
		why.push(`${net[1]} (+${selected.size - before})`);
		continue;
	}
	// feature code: every test class that references the feature's package
	const m = f.startsWith(MOD_PKG) ? f.slice(MOD_PKG.length).match(/^([a-z0-9_]+)\//) : null;
	if (m && !CORE_PACKAGES.has(m[1])) {
		const pkg = `com.projecthero.mod.${m[1]}.`;
		const before = selected.size;
		selectWhere(t => t.text.includes(pkg));
		why.push(`${m[1]} (+${selected.size - before})`);
		continue;
	}
	// a data / lang / sound file: the tests that name it
	const r = f.match(/^src\/main\/resources\/(assets|data)\/.+\/([^/]+)\.(json|nbt|mcfunction)$/);
	if (r) {
		const key = r[2];
		const before = selected.size;
		selectWhere(t => t.text.includes(key));
		why.push(`${key} (+${selected.size - before})`);
		continue;
	}
	// anything else (build files, core packages, mixin configs, fabric.mod.json, helpers, workflows): all of it
	output('full', `shared file changed: ${f}`);
}

if (selected.size === 0) {
	output('none', 'no gametest touches the changed files');
}
if (selected.size > MAX_SUBSET_CLASSES) {
	output('full', `${selected.size} test classes affected`);
}
const names = [...selected].sort();
output('subset', `${names.length} classes: ${why.join(', ') || names.join(', ')}`.slice(0, 900),
	`^(?:${names.map(n => n.toLowerCase()).join('|')})\\.`);
