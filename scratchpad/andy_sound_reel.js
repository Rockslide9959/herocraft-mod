// v0.17.3 Andy sounds: renders the listening reel -- every projecthero:andy.* event, all its takes (vanilla takes at their
// sounds.json pitch / volume, resampled the way the game plays them), loops played twice round to show the seam, then a
// simulated Normal-speed regrowth mixed the way AndySounds layers it (bone 0-3 s, muscle 2-6 s, skin 4-8.5 s, accents,
// the snap). Black frame with the event name on screen while it plays.
//   node scratchpad/andy_sound_reel.js <ffmpeg with libvorbis + drawtext> <out.mp4> [event prefix, e.g. andy.regrow.]
// The levels / rates in the simulated regrowth mirror AndySounds (BASE_VOLUME, ACCENT_RATE, ACCENT_VOLUME): keep them in step.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');
const { EVENTS } = require('./andy_sounds_json.js');

const FF = process.argv[2];
const OUT = process.argv[3];
const PREFIX = process.argv[4] || 'andy.';
const SR = 44100;
const ASSETS = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds');
const LOOM = path.join(os.homedir(), '.gradle', 'caches', 'fabric-loom', 'assets');
const index = JSON.parse(fs.readFileSync(path.join(LOOM, 'indexes', '1.21.1-17.json'), 'utf8')).objects;
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'andy_reel_'));

let seed = 4242;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };

function decode(file) {
	const r = spawnSync(FF, ['-hide_banner', '-loglevel', 'error', '-i', file, '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-'], { maxBuffer: 1 << 28 });
	if (r.status !== 0) throw new Error('decode ' + file + ': ' + r.stderr);
	const b = r.stdout;
	return new Float32Array(b.buffer.slice(b.byteOffset, b.byteOffset + b.length));
}
function fileOf(name) {
	const [ns, p] = name.split(':');
	if (ns === 'projecthero') return path.join(ASSETS, p + '.ogg');
	const h = index['minecraft/sounds/' + p + '.ogg'].hash;
	return path.join(LOOM, 'objects', h.slice(0, 2), h);
}
/** The game's pitch: a playback-rate change (linear resample), and its volume. */
function take(s, pitch = 1, volume = 1) {
	const x = decode(fileOf(s.name));
	const p = pitch * (s.pitch || 1), v = volume * (s.volume !== undefined ? s.volume : 1);
	const n = Math.floor(x.length / p);
	const y = new Float32Array(n);
	for (let i = 0; i < n; i++) {
		const t = i * p, k = Math.floor(t), f = t - k;
		y[i] = ((x[k] || 0) * (1 - f) + (x[k + 1] || 0) * f) * v;
	}
	return y;
}

const track = [];
let cursor = 0;
const cards = [];
function append(x, gap = 0.35) { track.push({ at: cursor, x }); cursor += x.length / SR + gap; }
function card(title, from) { cards.push({ title, from, to: cursor }); }
function silence(sec) { cursor += sec; }

const LOOPS = new Set(['andy.regrow.bone_push', 'andy.regrow.muscle', 'andy.regrow.skin', 'andy.blood.jet_loop', 'andy.boost.burn_loop', 'andy.punch.stretch']);
silence(0.8);
for (const [ev, [sub, , list]] of Object.entries(EVENTS)) {
	if (!ev.startsWith(PREFIX)) continue;
	const from = cursor - 0.3;
	if (LOOPS.has(ev)) {
		// two takes, each twice round (the seam is in the middle)
		for (const s of list.slice(0, 2)) {
			const a = take(s);
			const y = new Float32Array(a.length * 2);
			y.set(a, 0); y.set(a, a.length);
			append(y, 0.5);
		}
	} else {
		for (const s of list) append(take(s, 1, ev.startsWith('andy.regrow.') ? 0.7 : 0.9), 0.35);
	}
	card(`${ev}  (${list.length} takes)  -  ${sub}`, from);
	silence(0.6);
}

// ---- simulated regrowths, layered the way AndySounds does it (levels / rates mirror its BASE_VOLUME / ACCENT_RATE /
// ACCENT_VOLUME): Normal (8.5 s) and Fast (3.4 s; the layer rate clamps the accent rate at 2.2x and lifts loop level / pitch)
function simulate(len, label) {
	const from = cursor - 0.3;
	const speed = 8.5 / len;
	const out = new Float32Array(Math.ceil((len + 1.2) * SR));
	const win = [[0, 3 / 8.5], [2 / 8.5, 6 / 8.5], [4 / 8.5, 1]].map(([a, b]) => [a * len, b * len]);
	const vols = [0.45, 0.4, 0.32].map(v => v * (0.85 + 0.15 * Math.min(2, speed)));
	const loopPitch = 0.94 + 0.06 * Math.min(3, speed);
	const beds = ['andy.regrow.bone_push', 'andy.regrow.muscle', 'andy.regrow.skin'].map(e => {
		const list = EVENTS[e][2];
		return take(list[Math.floor(rnd() * list.length)], loopPitch);
	});
	const acc = ['andy.regrow.bone_crack', 'andy.regrow.squelch', 'andy.regrow.skin_stretch'];
	const accRate = [2.4, 1.2, 0.7].map(r => r * Math.min(2.2, speed));
	const envAt = (k, t) => { const f = (t - win[k][0]) / (win[k][1] - win[k][0]); return f > 0 && f < 1 ? Math.min(1, f / 0.08) * Math.min(1, (1 - f) / 0.12) : 0; };
	for (let k = 0; k < 3; k++) {
		for (let i = 0; i < len * SR; i++) out[i] += beds[k][i % beds[k].length] * vols[k] * envAt(k, i / SR);
		for (let t = 0; t < len; t += 0.05) {
			if (rnd() < accRate[k] * envAt(k, t) * 0.05) {
				const list = EVENTS[acc[k]][2];
				const a = take(list[Math.floor(rnd() * list.length)], 0.9 + 0.2 * rnd(), 0.45 + 0.3 * rnd());
				const o = Math.round(t * SR);
				for (let i = 0; i < a.length && o + i < out.length; i++) out[o + i] += a[i];
			}
		}
	}
	const first = [['andy.regrow.bone_crack', 0.0, 0.75, 0.9], ['andy.regrow.squelch', win[1][0], 0.8, 0.85], ['andy.regrow.finish', len, 0.8, 1]];
	for (const [e, t, v, p] of first) {
		const list = EVENTS[e][2];
		const a = take(list[Math.floor(rnd() * list.length)], p, v);
		const o = Math.round(t * SR);
		for (let i = 0; i < a.length && o + i < out.length; i++) out[o + i] += a[i];
	}
	append(out, 0.8);
	card(label, from);
}
if ('andy.regrow.'.startsWith(PREFIX) || PREFIX.startsWith('andy.regrow')) {
	simulate(8.5, 'Regrowth, simulated (Normal 8.5 s): bone 0-3 s, muscle 2-6 s, skin 4-8.5 s, accents, the snap');
	simulate(8.5, 'Regrowth again (Normal, other random takes)');
	simulate(3.4, 'Regrowth, simulated (Fast 3.4 s, V toggle)');
}

// ---- render
const total = cursor + 0.5;
const mixd = new Float32Array(Math.ceil(total * SR));
for (const { at, x } of track) { const o = Math.round(at * SR); for (let i = 0; i < x.length && o + i < mixd.length; i++) mixd[o + i] += x[i]; }
let peak = 0; for (const v of mixd) peak = Math.max(peak, Math.abs(v));
const g = peak > 0.98 ? 0.98 / peak : 1;
const wavFile = path.join(TMP, 'reel.wav');
const b = Buffer.alloc(44 + mixd.length * 2);
b.write('RIFF', 0); b.writeUInt32LE(36 + mixd.length * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12); b.writeUInt32LE(16, 16);
b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32);
b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(mixd.length * 2, 40);
for (let i = 0; i < mixd.length; i++) b.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(mixd[i] * g * 32767))), 44 + i * 2);
fs.writeFileSync(wavFile, b);

// title cards: one drawtext per segment, enabled while it plays (text through textfile to dodge escaping)
fs.copyFileSync('C:/Windows/Fonts/arial.ttf', path.join(TMP, 'font.ttf'));
fs.copyFileSync('C:/Windows/Fonts/arialbd.ttf', path.join(TMP, 'fontb.ttf'));
const filters = ["drawtext=fontfile=fontb.ttf:textfile=head.txt:fontcolor=0xB02020:fontsize=34:x=(w-tw)/2:y=60"];
fs.writeFileSync(path.join(TMP, 'head.txt'), PREFIX === 'andy.' ? 'Project Hero - Andy sounds (v0.17.3)' : 'Project Hero - Andy regrowth sounds v2 (v0.17.3)');
cards.forEach((c, i) => {
	const [name, ...rest] = c.title.split('  -  ');
	fs.writeFileSync(path.join(TMP, `t${i}a.txt`), name);
	fs.writeFileSync(path.join(TMP, `t${i}b.txt`), rest.join(' - ') || ' ');
	const en = `enable='between(t,${Math.max(0, c.from).toFixed(2)},${c.to.toFixed(2)})'`;
	filters.push(`drawtext=fontfile=font.ttf:textfile=t${i}a.txt:fontcolor=white:fontsize=30:x=(w-tw)/2:y=(h/2)-40:${en}`);
	filters.push(`drawtext=fontfile=font.ttf:textfile=t${i}b.txt:fontcolor=0xBBBBBB:fontsize=24:x=(w-tw)/2:y=(h/2)+10:${en}`);
});
fs.writeFileSync(path.join(TMP, 'vf.txt'), filters.join(',\n'));
fs.mkdirSync(path.dirname(OUT), { recursive: true });
const r = spawnSync(FF, ['-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', `color=c=black:s=1280x720:r=10:d=${total.toFixed(2)}`, '-i', 'reel.wav',
	'-filter_script:v', 'vf.txt', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-tune', 'stillimage', '-c:a', 'aac', '-b:a', '192k', '-shortest', path.resolve(OUT)],
	{ cwd: TMP });
if (r.status !== 0) throw new Error(r.stderr.toString());
console.log('reel', total.toFixed(1) + ' s,', cards.length, 'cards ->', OUT);
fs.writeFileSync(path.join(TMP, 'cards.json'), JSON.stringify(cards, null, 1));
console.log(cards.map(c => c.from.toFixed(1).padStart(6) + '  ' + c.title).join('\n'));
