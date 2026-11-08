// v0.17.3 Andy sounds: writes the projecthero:andy.* events into sounds.json and their subtitles into en_us.json.
// Synthesised takes (scratchpad/synth_andy_sounds.js -> sounds/andy/*.ogg) plus a few vanilla files at a changed pitch /
// volume as extra variants (checked against the 1.21.1 asset index when it is in the Loom cache). Text insertion keeps each
// file's own line endings; refuses a key that is already there; validates both files as JSON afterwards.
//   node scratchpad/andy_sounds_json.js
//   node scratchpad/andy_sounds_json.js --update [andy.event ...]   (rewrites existing events in place, all if none named)
const fs = require('fs');
const os = require('os');
const path = require('path');

const ASSETS = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const SOUNDS = path.join(ASSETS, 'sounds.json');
const LANG = path.join(ASSETS, 'lang', 'en_us.json');

const syn = (file, n) => Array.from({ length: n }, (_, i) => ({ name: 'projecthero:andy/' + file + (i + 1) }));
const van = (file, volume, pitch) => ({ name: 'minecraft:' + file, volume, pitch });

/** event -> [subtitle, attenuation distance, sounds]. */
const EVENTS = {
	'andy.regrow.bone_push': ['Bone grinds outward', 12, syn('regrow_bone_push', 3)],
	'andy.regrow.bone_crack': ['Bone cracks into place', 12, [...syn('regrow_bone_crack', 5),
		van('block/bone_block/break1', 0.35, 1.45), van('block/bone_block/break4', 0.35, 1.3)]],
	// regrowth v2: no honey-block slides (a dense granular crackle) or frogspawn (bubbly) -- only compact squishes / leather
	'andy.regrow.muscle': ['Flesh knits wetly', 12, syn('regrow_muscle', 3)],
	'andy.regrow.squelch': ['Flesh squelches', 12, [...syn('regrow_squelch', 5),
		van('mob/slime/small1', 0.45, 0.6), van('mob/slime/small3', 0.45, 0.6), van('block/mud/step3', 0.5, 0.6), van('block/mud/step5', 0.45, 0.65)]],
	'andy.regrow.skin': ['Skin stretches over', 12, syn('regrow_skin', 3)],
	'andy.regrow.skin_stretch': ['Skin pulls tight', 12, [...syn('regrow_skin_stretch', 5),
		van('item/armor/equip_leather2', 0.35, 0.85), van('item/armor/equip_leather4', 0.3, 0.8)]],
	'andy.regrow.finish': ['Body part re-forms', 12, [...syn('regrow_finish', 4), van('mob/slime/big2', 0.4, 0.8), van('mob/slime/big4', 0.35, 0.8)]],
	'andy.part.break': ['Body part tears off', 20, syn('part_break', 5)],
	'andy.blood.burst': ['Blood bursts', 24, syn('blood_burst', 4)],
	'andy.blood.spray': ['Blood spurts', 16, syn('blood_spray', 4)],
	'andy.blood.drip': ['Blood drips', 8, [...syn('blood_drip', 5),
		van('block/pointed_dripstone/drip_water3', 0.4, 0.62), van('block/pointed_dripstone/drip_water7', 0.4, 0.58)]],
	'andy.blood.splat': ['Blood splatters', 10, [...syn('blood_splat', 5), van('block/mud/step4', 0.4, 1.35), van('block/honeyblock/step2', 0.4, 1.2)]],
	'andy.blood.jet_loop': ['Blood jets roar', 24, syn('blood_jet_loop', 3)],
	'andy.blood.jet_start': ['Blood jets burst out', 24, syn('blood_jet_start', 3)],
	'andy.blood.jet_stop': ['Blood jets sputter out', 24, syn('blood_jet_stop', 3)],
	'andy.boost.burn_loop': ['Flesh sizzles away', 24, syn('boost_burn_loop', 3)],
	'andy.head.roll': ['Head thuds and rolls', 14, syn('head_roll', 4)],
	'andy.launch.whoosh': ['Body part flies', 20, syn('launch_whoosh', 4)],
	'andy.bullet.parts': ['Flesh bullet pops', 18, syn('bullet_parts', 4)],
	'andy.bullet.crimson': ['Crimson Bullet fires', 24, syn('bullet_crimson', 4)],
	'andy.punch.stretch': ['Sinew stretches', 16, syn('punch_stretch', 3)],
	'andy.punch.fire': ['Stretched arm snaps forward', 20, syn('punch_fire', 4)],
};
module.exports = { EVENTS };

if (require.main === module) {
	// vanilla files must exist (the Loom cache's 1.21.1 asset index)
	const index = path.join(os.homedir(), '.gradle', 'caches', 'fabric-loom', 'assets', 'indexes', '1.21.1-17.json');
	if (fs.existsSync(index)) {
		const objects = JSON.parse(fs.readFileSync(index, 'utf8')).objects;
		for (const [ev, [, , list]] of Object.entries(EVENTS)) {
			for (const s of list) {
				if (s.name.startsWith('minecraft:') && !objects['minecraft/sounds/' + s.name.slice(10) + '.ogg']) {
					throw new Error('missing vanilla sound ' + s.name + ' in ' + ev);
				}
				if (s.name.startsWith('projecthero:') && !fs.existsSync(path.join(ASSETS, 'sounds', s.name.slice(12) + '.ogg'))) {
					throw new Error('missing synthesised file ' + s.name + ' in ' + ev);
				}
			}
		}
	}

	function insert(file, makeLines) {
		const raw = fs.readFileSync(file, 'utf8');
		const eol = raw.includes('\r\n') ? '\r\n' : '\n';
		const before = JSON.parse(raw);
		const lines = makeLines(before);
		if (lines.length === 0) {
			console.log(path.basename(file), 'nothing to add');
			return;
		}
		const body = raw.replace(/\s*$/, '');
		if (!body.endsWith('}')) throw new Error('unexpected end of ' + file);
		const head = body.slice(0, -1).replace(/\s*$/, '');
		const out = head + ',' + eol + lines.join(',\n').split('\n').join(eol) + eol + '}' + eol;
		const parsed = JSON.parse(out);
		if (Object.keys(parsed).length !== Object.keys(before).length + lines.length) throw new Error('key count mismatch in ' + file);
		// duplicate keys don't survive JSON.parse silently: count raw occurrences of each new key
		fs.writeFileSync(file, out);
		console.log(path.basename(file), '+' + lines.length, 'eol', eol === '\r\n' ? 'CRLF' : 'LF');
	}

	const entry = (ev, dist, list) => {
		const sounds = list.map(s => {
			const o = { name: s.name, attenuation_distance: dist };
			if (s.volume !== undefined) o.volume = s.volume;
			if (s.pitch !== undefined) o.pitch = s.pitch;
			return o;
		});
		return JSON.stringify({ subtitle: 'subtitles.projecthero.' + ev, sounds }, null, 2).split('\n').map((l, i) => (i === 0 ? l : '  ' + l)).join('\n');
	};

	if (process.argv.includes('--update')) {
		// rewrite the given (or every) andy.* event in place, keeping the file's order and line endings; subtitles untouched
		const only = process.argv.slice(2).filter(a => !a.startsWith('--'));
		const raw = fs.readFileSync(SOUNDS, 'utf8');
		const eol = raw.includes('\r\n') ? '\r\n' : '\n';
		let text = raw.split('\r\n').join('\n');
		const before = JSON.parse(text);
		for (const [ev, [, dist, list]] of Object.entries(EVENTS)) {
			if (only.length && !only.includes(ev)) continue;
			const head = '\n  ' + JSON.stringify(ev) + ': {';
			const at = text.indexOf(head);
			if (at < 0 || text.indexOf(head, at + 1) >= 0) throw new Error('sounds.json needs exactly one ' + ev);
			let i = at + head.length - 1, depth = 0;
			do { const c = text[i++]; if (c === '{') depth++; else if (c === '}') depth--; } while (depth > 0);
			text = text.slice(0, at + head.length - 1) + entry(ev, dist, list) + text.slice(i);
			console.log('updated', ev);
		}
		const after = JSON.parse(text);
		if (Object.keys(after).join() !== Object.keys(before).join()) throw new Error('key set changed');
		fs.writeFileSync(SOUNDS, text.split('\n').join(eol));
		console.log('ok');
		process.exit(0);
	}

	insert(SOUNDS, existing => {
		const out = [];
		for (const [ev, [, dist, list]] of Object.entries(EVENTS)) {
			if (existing[ev]) throw new Error('sounds.json already has ' + ev);
			out.push('  ' + JSON.stringify(ev) + ': ' + entry(ev, dist, list));
		}
		return out;
	});
	insert(LANG, existing => {
		const out = [];
		for (const [ev, [sub]] of Object.entries(EVENTS)) {
			const key = 'subtitles.projecthero.' + ev;
			if (key in existing) throw new Error('en_us.json already has ' + key);
			out.push('  ' + JSON.stringify(key) + ': ' + JSON.stringify(sub));
		}
		return out;
	});
	// final duplicate-key scan on the raw text of both files
	for (const f of [SOUNDS, LANG]) {
		const raw = fs.readFileSync(f, 'utf8');
		const seen = new Set();
		for (const m of raw.matchAll(/^ {2}"((?:[^"\\]|\\.)*)"\s*:/gm)) {
			if (seen.has(m[1])) throw new Error('duplicate top-level key ' + m[1] + ' in ' + f);
			seen.add(m[1]);
		}
	}
	console.log('ok');
}
