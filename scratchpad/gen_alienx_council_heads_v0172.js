// v0.17.2 (user: "use this for bellicus and serenas faces: https://www.planetminecraft.com/skin/bellicus-and-serena-ben-10-alien-force/
// ... use the one side of the head for bellicus and other side for serena, split it into 2 head models and have those be the floating
// heads in alien xs head please"): the Celestial Council heads' skins.
// node scratchpad/gen_alienx_council_heads_v0172.js  (writes into src/main/resources/assets/projecthero/textures/entity/omnitrix)
//
// The source skin (embedded below, 64x64 palette PNG) is a head-only skin with Bellicus' face on the head's RIGHT side (u 0..8,
// v 8..16; hat 32..40) and Serena's on its LEFT side (u 16..24; hat 48..56); the rest of the head is see-through apart from thin
// edge strips. Each council head gets its own 64x64 player-layout skin: that face moved to the head's FRONT (base 8..16 and hat
// 40..48), its see-through eye holes made solid black, and the other five faces filled with the face's own greens (darker behind
// and below) so it reads as one whole head. Four variants each: normal, _blink (eyes shut), _talk (mouth open), _blink_talk.
const fs = require("fs"), zlib = require("zlib"), path = require("path");
const R = "src/main/resources/assets/projecthero/textures/entity/omnitrix";
const SRC = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAMAAACdt4HsAAAABGdBTUEAALGPC/xhBQAAACBjSFJNAAB6JgAAgIQAAPoAAACA6AAAdTAAAOpgAAA6mAAAF3CculE8AAAAS1BMVEUAAAAA1AsAzAsAxQsAvwsAtQoAqAkAogkb2CUb0CQbyCQAnAkAlgkAswkg2ioL1hYLzhYAvwoAvAoAugoO1hgAswoAuwoAygoAwgo0bEu0AAAAAXRSTlMAQObYZgAAAO5JREFUeNrtkVuOgzAMRa8NpJ2h6TxKsfe/0saKIRJVRem3j0Lux0ksHGNBa/RDzQ5HEb+onxbQGiyeO/6ZefMHRz3uLvwg7/m9Fuj9Fmi+/f/RJ4/IHQoqOt/Lthlj32PB/IECfcddWUsBfS5gHg0hUS2buGWi+rlG9WiYRyOdzl9Cp5TcEv3+EJdwjerxEsL4rSOWC8w5Xyln5lVfgMuIRrbVmIAzlZjcFnclS9cbv2WCJOspiawtMLUWIBDzZt0v2YakKZVN2yPm3B5RMasdwksGm6PoMKxjJO5oHeMAmN/OPwiCIAiCIAiCYJ8H4HgL3/aF/MkAAAAASUVORK5CYII=";

// ---- decode the (8-bit palette) source
function decode(b64) {
	const buf = Buffer.from(b64, "base64");
	const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
	let o = 8, idat = [], plte, trns;
	while (o < buf.length) { const l = buf.readUInt32BE(o), t = buf.slice(o + 4, o + 8).toString(), d = buf.slice(o + 8, o + 8 + l); if (t === "IDAT") idat.push(d); if (t === "PLTE") plte = d; if (t === "tRNS") trns = d; o += 12 + l; }
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const px = [];
	let prev = Buffer.alloc(w);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (w + 1)], line = Buffer.from(raw.slice(y * (w + 1) + 1, (y + 1) * (w + 1)));
		for (let i = 0; i < w; i++) { const a = i ? line[i - 1] : 0, b = prev[i], c = i ? prev[i - 1] : 0; let v = line[i];
			if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1; else if (f === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
			line[i] = v & 255; }
		prev = line;
		for (let x = 0; x < w; x++) { const k = line[x]; px.push([plte[k * 3], plte[k * 3 + 1], plte[k * 3 + 2], trns && k < trns.length ? trns[k] : 255]); }
	}
	return (x, y) => px[y * w + x];
}

function writePng(file, w, h, get) {
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = get(x, y), o = y * (w * 4 + 1) + 1 + x * 4; raw[o] = c[0]; raw[o + 1] = c[1]; raw[o + 2] = c[2]; raw[o + 3] = c[3]; }
	const crcT = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
	const crc = b => { let c = -1; for (const v of b) c = crcT[(c ^ v) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
	const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	fs.writeFileSync(path.join(R, file), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]));
}

const hash = (x, y, s) => { let n = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967296; };
const shade = (c, k) => [Math.round(c[0] * k), Math.min(255, Math.round(c[1] * k)), Math.round(c[2] * k), 255];
const EYE = [8, 18, 8, 255], CLEAR = [0, 0, 0, 0];

const src = decode(SRC);

/**
 * One council head. face = the source side's base u, hat = its hat u (v 8..16 both). eyes / mouth = texel cells (x, y in 0..7) of
 * the face, for the blink and talk variants.
 */
function head(name, face, hat, eyes, mouth, mouthOpen, seed) {
	// the face's own greens: its top two rows (forehead / skull) make the plain faces
	const skull = [];
	for (let y = 0; y < 2; y++) for (let x = 0; x < 8; x++) { const c = src(face + x, 8 + y); if (c[3]) skull.push(c); }
	const plain = (x, y, k, s) => shade(skull[Math.floor(hash(x, y, s) * skull.length)], k);
	for (const blink of [false, true]) for (const talk of [false, true]) {
		const img = [];
		for (let i = 0; i < 64 * 64; i++) img.push(CLEAR);
		const set = (x, y, c) => { img[y * 64 + x] = c; };
		// base head: top 8..16 x 0..8, bottom 16..24 x 0..8; right 0..8, front 8..16, left 16..24, back 24..32 (v 8..16)
		for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
			set(8 + x, y, plain(x, y, 1.0, seed + 1)); // top
			set(16 + x, y, plain(x, y, 0.72, seed + 2)); // bottom
			set(x, 8 + y, plain(x, y, 0.9, seed + 3)); // right
			set(16 + x, 8 + y, plain(x, y, 0.9, seed + 4)); // left
			set(24 + x, 8 + y, plain(x, y, 0.82, seed + 5)); // back
			const f = src(face + x, 8 + y);
			set(8 + x, 8 + y, f[3] ? f : EYE); // the face, its see-through eyes solid black
			const h = src(hat + x, 8 + y);
			set(40 + x, 8 + y, h[3] ? h : CLEAR); // its hat-layer relief
		}
		const cell = ([x, y], c) => { set(8 + x, 8 + y, c); set(40 + x, 8 + y, CLEAR); };
		if (blink) {
			// lids down: the eye cells take the skin just above them, a shade darker
			for (const [x, y] of eyes) { const up = src(face + x, 8 + y - 1); cell([x, y], shade(up[3] ? up : skull[0], 0.85)); }
		}
		if (talk) {
			for (const m of mouth) cell(m, EYE);
			for (const m of mouthOpen) cell(m, shade(src(face + m[0], 8 + m[1]), 0.45));
		}
		writePng(name + (blink ? "_blink" : "") + (talk ? "_talk" : "") + ".png", 64, 64, (x, y) => img[y * 64 + x]);
	}
}

// Bellicus: the right side. Eyes are the see-through cells on row 4 (x 1-2, 5-6); the frown is row 6 (x 3-4).
head("alien_x_bellicus", 0, 32, [[1, 4], [2, 4], [5, 4], [6, 4]], [[3, 6], [4, 6]], [[2, 6], [5, 6]], 11);
// Serena: the left side. Eyes row 4 (x 1-2, 5-6); her small mouth row 6 (x 3-4).
head("alien_x_serena", 16, 48, [[1, 4], [2, 4], [5, 4], [6, 4]], [[3, 6], [4, 6]], [], 23);
console.log("wrote the council head skins");
