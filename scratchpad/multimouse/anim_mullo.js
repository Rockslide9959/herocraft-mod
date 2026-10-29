// v0.17.1: Mullo's GeckoLib clips -- Sass's six clips copied as animation.kwami.mullo.* and adjusted for a mouse: the
// curly tail swishes (quick side-to-side flicks instead of the snake's slow sway), a little hop in the idle, and the
// head gives a quick ear twitch now and then. ONLY appends the new mullo keys to kwami.animation.json (text insertion
// before the closing braces, the file's CRLF / tab style kept); nothing else in the file is touched.
// Usage (repo root): node scratchpad/multimouse/anim_mullo.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'animations', 'miraculous', 'kwami.animation.json');
const text = fs.readFileSync(file, 'utf8');
const j = JSON.parse(text);
if (j.animations['animation.kwami.mullo.idle']) {
	console.log('mullo clips already there');
	process.exit(0);
}
const r3 = (v) => Math.round(v * 1000) / 1000;
/** A swish: keyframes every `step` s over `len` s, x / y / z rotation from functions of t. */
function keys(len, step, fx, fy, fz) {
	const out = {};
	for (let t = 0; t <= len + 1e-6; t += step) {
		const k = Number.isInteger(r3(t)) ? r3(t) + ".0" : String(r3(t));
		out[k] = [r3(fx(t)), r3(fy(t)), r3(fz(t))];
	}
	return out;
}
const TAU = Math.PI * 2;
const clips = ['idle', 'wave', 'eat', 'tired', 'into_jewel', 'pop_out'];
const add = {};
for (const c of clips) {
	const src = j.animations['animation.kwami.sass.' + c];
	const a = JSON.parse(JSON.stringify(src));
	const len = a.animation_length;
	// the mouse tail: flicks side to side, curled up a little
	const flick = c === 'tired' ? 0.5 : c === 'eat' ? 1.5 : c === 'into_jewel' || c === 'pop_out' ? 2 : 1;
	const amp = c === 'tired' ? 6 : 16;
	const per = len / Math.max(1, Math.round(len * flick)); // whole cycles over the clip, so it loops cleanly
	a.bones.tail = a.bones.tail || {};
	a.bones.tail.rotation = keys(len, Math.min(0.1, len / 6), (t) => (c === 'tired' ? 18 : 8) + 4 * Math.sin(TAU * t / per),
		(t) => amp * Math.sin(TAU * t / per), (t) => 0.4 * amp * Math.sin(TAU * t / per + 1.2));
	if (c === 'idle' && a.bones.head && a.bones.head.rotation) {
		// an ear twitch: a quick head flick at 1.6 s and 3.8 s (on top of the copied head keys)
		const hr = a.bones.head.rotation;
		const at = (t) => {
			let best = null, bd = 1e9;
			for (const k of Object.keys(hr)) { const d = Math.abs(Number(k) - t); if (d < bd) { bd = d; best = hr[k]; } }
			return Array.isArray(best) ? best : (best && best.post) || [0, 0, 0];
		};
		for (const [t0, s] of [[1.6, 1], [3.8, -1]]) {
			const b = at(t0);
			hr[String(r3(t0 + 0.05))] = [b[0], b[1], r3(b[2] + s * 9)];
			hr[String(r3(t0 + 0.1))] = [b[0], b[1], r3(b[2] - s * 5)];
			hr[String(r3(t0 + 0.15))] = [b[0], b[1], b[2]];
		}
		const sorted = {};
		for (const k of Object.keys(hr).sort((x, y) => Number(x) - Number(y))) sorted[k] = hr[k];
		a.bones.head.rotation = sorted;
	}
	add['animation.kwami.mullo.' + c] = a;
}
// serialise each clip in the file's own style (tabs, CRLF), indented two levels
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let block = '';
for (const [k, v] of Object.entries(add)) {
	const body = JSON.stringify(v, null, '\t').split('\n').map((l, i) => (i === 0 ? l : '\t\t' + l)).join(eol);
	block += ',' + eol + '\t\t' + JSON.stringify(k) + ': ' + body;
}
// insert after the last clip: before the closing "}" of "animations" (the file ends "\t\t}" EOL "\t}" EOL "}" EOL)
const closeAnims = text.lastIndexOf(eol + '\t}');
if (closeAnims < 0) {
	throw new Error('could not find the end of "animations"');
}
const out = text.slice(0, closeAnims) + block + text.slice(closeAnims);
JSON.parse(out);
fs.writeFileSync(file, out);
console.log('added', Object.keys(add).length, 'mullo clips');
