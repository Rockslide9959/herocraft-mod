// v0.16.1 Spider-Man web structures: generates the strand texture (a soft silk fibre, drawn as camera-facing ribbons
// for tightropes and woven webs) and the web-wall texture (a radial orb web filling a square panel).
//   node scratchpad/gen_spiderman_web_textures.js
// Output: src/main/resources/assets/projecthero/textures/entity/spider_web_strand.png, spider_web_wall.png
const path = require('path');
const png = require('./pnglib.js');

const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity');

let seed = 7;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };

function canvas(w, h) {
	return { w, h, data: Buffer.alloc(w * h * 4) };
}

// premultiplied-style "over" blend of a white-ish silk colour at alpha a (0..1)
function plot(img, x, y, a, col) {
	if (x < 0 || y < 0 || x >= img.w || y >= img.h || a <= 0) return;
	const i = (y * img.w + x) * 4;
	const da = img.data[i + 3] / 255;
	const oa = a + da * (1 - a);
	for (let k = 0; k < 3; k++) {
		const dc = img.data[i + k];
		img.data[i + k] = Math.round(oa > 0 ? (col[k] * a + dc * da * (1 - a)) / oa : 0);
	}
	img.data[i + 3] = Math.round(oa * 255);
}

// anti-aliased thick line (distance to segment, smooth edge), supersampled
function line(img, x0, y0, x1, y1, width, alpha, col) {
	const minX = Math.floor(Math.min(x0, x1) - width - 1), maxX = Math.ceil(Math.max(x0, x1) + width + 1);
	const minY = Math.floor(Math.min(y0, y1) - width - 1), maxY = Math.ceil(Math.max(y0, y1) + width + 1);
	const dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy || 1;
	for (let y = minY; y <= maxY; y++) {
		for (let x = minX; x <= maxX; x++) {
			let cov = 0;
			for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) {
				const px = x + (sx + 0.5) / 4, py = y + (sy + 0.5) / 4;
				let t = ((px - x0) * dx + (py - y0) * dy) / l2; t = Math.max(0, Math.min(1, t));
				const ex = x0 + dx * t - px, ey = y0 + dy * t - py;
				if (Math.sqrt(ex * ex + ey * ey) <= width / 2) cov++;
			}
			plot(img, x, y, (cov / 16) * alpha, col);
		}
	}
}

const SILK = [236, 239, 247];

// ---- strand: 32 x 16, a fibre running along U with a soft falloff across V and a few finer twisted threads
{
	const img = canvas(32, 16);
	for (let y = 0; y < 16; y++) {
		const d = (y - 7.5) / 3.2;
		const core = Math.exp(-d * d);
		for (let x = 0; x < 32; x++) {
			// two twisted sub-threads crossing along the strand
			const tw1 = Math.exp(-Math.pow((y - (7.5 + 2.2 * Math.sin(x / 32 * Math.PI * 4))) / 1.1, 2));
			const tw2 = Math.exp(-Math.pow((y - (7.5 - 2.2 * Math.sin(x / 32 * Math.PI * 4))) / 1.1, 2));
			const a = Math.min(1, core * 0.75 + (tw1 + tw2) * 0.35);
			const shade = 0.9 + 0.1 * Math.sin(x * 0.9 + y);
			plot(img, x, y, a, SILK.map(c => Math.min(255, c * shade)));
		}
	}
	png.write(path.join(OUT, 'spider_web_strand.png'), img);
}

// ---- wall: 128 x 128, an orb web: 16 spokes, a spiral, anchor threads to the corners, a faint sticky sheen
{
	const S = 128, c = S / 2;
	const img = canvas(S, S);
	// faint sheet so the panel reads as a barrier even edge-on to the light
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		const r = Math.hypot(x - c, y - c) / c;
		plot(img, x, y, 0.06 + 0.05 * (1 - Math.min(1, r)), SILK);
	}
	const spokes = 16;
	const ang = [];
	for (let i = 0; i < spokes; i++) ang.push(i / spokes * Math.PI * 2 + (rnd() - 0.5) * 0.12);
	// frame: a polygon of threads near the border the spokes end on
	const frame = ang.map(a => {
		const r = c * (0.93 + rnd() * 0.05) / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
		return [c + Math.cos(a) * Math.min(r, c * 1.02), c + Math.sin(a) * Math.min(r, c * 1.02)];
	});
	for (let i = 0; i < spokes; i++) {
		const [x, y] = frame[i];
		line(img, c, c, x, y, 1.3, 0.95, SILK);
		const [nx, ny] = frame[(i + 1) % spokes];
		line(img, x, y, nx, ny, 1.2, 0.8, SILK);
	}
	// anchor threads to the corners
	for (const [x, y] of [[0, 0], [S, 0], [0, S], [S, S]]) {
		let best = frame[0], bd = 1e9;
		for (const f of frame) { const d = Math.hypot(f[0] - x, f[1] - y); if (d < bd) { bd = d; best = f; } }
		line(img, best[0], best[1], x, y, 1.1, 0.7, SILK);
	}
	// the spiral: radius creeps out per spoke step
	const step = 4.2;
	let r = 5;
	for (let turn = 0; turn < 60 && r < c * 0.9; turn++) {
		for (let i = 0; i < spokes; i++) {
			const a1 = ang[i], a2 = ang[(i + 1) % spokes] + (i === spokes - 1 ? Math.PI * 2 : 0);
			const r1 = r, r2 = r + step / spokes;
			const lim1 = Math.hypot(frame[i][0] - c, frame[i][1] - c) * 0.97;
			const lim2 = Math.hypot(frame[(i + 1) % spokes][0] - c, frame[(i + 1) % spokes][1] - c) * 0.97;
			if (r1 < lim1 && r2 < lim2) {
				// sag the spiral thread slightly toward the centre between spokes
				const mx = c + Math.cos((a1 + a2) / 2) * (r1 + r2) / 2 * 0.985, my = c + Math.sin((a1 + a2) / 2) * (r1 + r2) / 2 * 0.985;
				line(img, c + Math.cos(a1) * r1, c + Math.sin(a1) * r1, mx, my, 1.0, 0.85, SILK);
				line(img, mx, my, c + Math.cos(a2) * r2, c + Math.sin(a2) * r2, 1.0, 0.85, SILK);
			}
			r = r2;
		}
	}
	// a dense hub
	for (let k = 0; k < 6; k++) {
		const a = rnd() * Math.PI * 2, b = a + 1.5 + rnd();
		line(img, c + Math.cos(a) * 4, c + Math.sin(a) * 4, c + Math.cos(b) * 4, c + Math.sin(b) * 4, 1.2, 0.9, SILK);
	}
	png.write(path.join(OUT, 'spider_web_wall.png'), img);
}
// ---- v0.16.1 web shot glob: 64 x 16 box-UV sheet -- core (0,0), sheath (0,6), strands (18,0); white silk with grey fibres,
// the sheath holed like a net (cutout render: alpha 0 or 255)
{
	const img = canvas(64, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) {
		const fib = 0.86 + 0.14 * Math.sin(x * 2.1 + y * 1.3) * Math.cos(x * 0.7 - y * 2.2);
		const col = SILK.map(c => Math.min(255, Math.round(c * fib)));
		let a = 1;
		if (y >= 6 && y < 11 && x < 18) a = ((x + y) % 3 === 0 || (x * 7 + y * 3) % 5 === 0) ? 0 : 1;
		plot(img, x, y, a, col);
	}
	png.write(path.join(OUT, 'web_shot.png'), img);
}
console.log('web textures written');
